package opentrap.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opentrap.core.Bytes;
import opentrap.core.HttpMessage;
import opentrap.core.HttpService;
import opentrap.core.Issue;
import opentrap.core.LogEntry;
import opentrap.core.ScanTask;
import opentrap.core.SuiteContext;
import opentrap.spider.Cdp.JsonValue;

/**
 * Drives a crawl-then-audit against the ZAP engine and maps everything it finds
 * into OpenTRAP's own model: crawled traffic into the site map and live passive
 * audit, and every ZAP alert into an OpenTRAP {@link Issue} in the issue tree.
 *
 * <p>The ZAP engine does the detecting; this class is only the bridge, nothing of
 * ZAP's data model leaks into OpenTRAP beyond what is translated here.
 */
public final class ZapScanner {

    private final SuiteContext ctx;
    private final ZapApi api;

    public ZapScanner(SuiteContext ctx, ZapApi api) {
        this.ctx = ctx;
        this.api = api;
    }

    /** One seed to crawl: the target service and the full seed URL. */
    public record Seed(HttpService service, String url) {}

    /** Single-seed convenience (the Spider button), delegating to the list form. */
    public void crawlAndAudit(HttpService seed, String seedUrl, boolean thenAudit,
                              ScanConfig cfg, ScanTask crawlTask) throws Exception {
        crawlAndAudit(java.util.List.of(new Seed(seed, seedUrl)), thenAudit, cfg, crawlTask);
    }

    /**
     * Crawl every seed, then (when {@code thenAudit}) audit what was found, all
     * as one job: one crawl task and one audit task. The seeds are processed one
     * at a time on this single thread, so the configured thread count applies
     * once to the engine rather than being multiplied across targets.
     *
     * @param crawlTask a LIVE_ACTIVE task to report crawl progress on
     */
    public void crawlAndAudit(java.util.List<Seed> seeds, boolean thenAudit,
                              ScanConfig cfg, ScanTask crawlTask) throws Exception {
        // ---- crawl each seed into the one task ---------------------------
        try {
            api.setSpiderMaxDepth(cfg.crawlDepth());
            api.setSpiderMaxDuration(cfg.crawlDurationMin());
            api.setSpiderProcessForms(cfg.submitForms());
        } catch (Exception ignore) { /* use the engine defaults */ }

        int lastLoc = -1;
        for (Seed seed : seeds) {
            if (crawlTask.cancelled()) break;
            if (seeds.size() > 1) { crawlTask.log("Crawling " + seed.url()); ctx.taskChanged(crawlTask); }
            api.accessUrl(seed.url());
            // 0 = no per-node child limit: crawl every in-scope link within the
            // depth budget, instead of stopping after 20 links per page.
            final String sid = api.spiderScan(seed.url(), 0);
            boolean zapPaused = false;
            while (true) {
                // Stop the moment the task is deleted, so nothing keeps crawling.
                if (crawlTask.cancelled()) { stopQuietly(() -> api.spiderStop(sid)); break; }
                // Honour a paused task by pausing the engine's spider too.
                if (crawlTask.state() == ScanTask.State.PAUSED) {
                    if (!zapPaused) { stopQuietly(() -> api.spiderPause(sid)); zapPaused = true; }
                    Thread.sleep(500);
                    continue;
                }
                if (zapPaused) { stopQuietly(() -> api.spiderResume(sid)); zapPaused = false; }
                int st = api.spiderStatus(sid);
                drain("crawl", true, crawlTask);
                int loc = totalLocations(seeds);
                if (loc >= 0 && loc != lastLoc) {
                    crawlTask.setDiscoveredLocations(loc);
                    crawlTask.log("Discovered " + loc + " location(s)");
                    lastLoc = loc;
                }
                ctx.taskChanged(crawlTask);
                if (st >= 100) break;
                Thread.sleep(1000);
            }
            drain("crawl", true, crawlTask);
            // AJAX crawl (headless Chromium) for JavaScript-rendered routes.
            if (cfg.ajaxSpider() && !crawlTask.cancelled()) runAjaxCrawl(seed.url(), cfg, crawlTask);
        }

        drain("crawl", true, crawlTask);
        crawlTask.log(crawlTask.cancelled()
                ? "Crawl stopped"
                : "Crawl finished: " + crawlTask.requests() + " request(s)");
        crawlTask.setState(ScanTask.State.FINISHED);
        ctx.taskChanged(crawlTask);

        // A deleted crawl does not go on to start an audit.
        if (crawlTask.cancelled() || !thenAudit) return;

        // ---- audit: one task over every seed -----------------------------
        ScanTask auditTask = new ScanTask(ScanTask.Type.AUDIT,
                "Audit of " + targetLabel(seeds), "ZAP active rules, within scope");
        auditTask.setScope(seeds.size() == 1 ? seeds.get(0).service().url() : seeds.size() + " targets");
        auditTask.setDiscoveredLocations(crawlTask.discoveredLocations());
        try {
            int rules = 0;
            for (JsonValue s : api.activeScanners())
                if ("true".equals(ZapApi.str(s, "enabled"))) rules++;
            auditTask.setAuditItemsTotal(rules);
        } catch (Exception ignore) { /* leave at 0 if ZAP cannot report it */ }
        ctx.addTask(auditTask);
        try {
            // Apply the tier's audit settings once before starting.
            try {
                api.setAllAttackStrength(cfg.attackStrength());
                api.setAllAlertThreshold(cfg.alertThreshold());
                api.setInjectableParams(cfg.injectableMask());
                api.setInjectableRpc(cfg.rpcMask());
                api.setActiveDelayMs(cfg.delayMs());
                api.setActiveMaxScanDuration(cfg.scanDurationMin());
            } catch (Exception ignore) { /* use the engine defaults */ }
            auditTask.log("Auditing with the ZAP active rules (" + cfg.name()
                    + ": strength " + cfg.attackStrength() + ", threshold " + cfg.alertThreshold() + ")...");
            ctx.taskChanged(auditTask);

            Set<String> seen = new HashSet<>();
            int doneSeeds = 0;
            for (Seed seed : seeds) {
                if (auditTask.cancelled()) break;
                final String aid = api.ascanScan(seed.url());
                // Nothing crawled for this seed: skip it with a note rather than
                // driving the audit loop with a null id the API would reject.
                if (aid == null || aid.isBlank()) {
                    auditTask.log("Nothing to audit for " + seed.service().host()
                            + (crawlTask.discoveredLocations() <= 0 ? " (no crawlable content found)" : ""));
                    doneSeeds++;
                    continue;
                }
                boolean auditPaused = false;
                while (true) {
                    if (auditTask.cancelled()) { stopQuietly(() -> api.ascanStop(aid)); break; }
                    if (auditTask.state() == ScanTask.State.PAUSED) {
                        if (!auditPaused) { stopQuietly(() -> api.ascanPause(aid)); auditPaused = true; }
                        Thread.sleep(500);
                        continue;
                    }
                    if (auditPaused) { stopQuietly(() -> api.ascanResume(aid)); auditPaused = false; }
                    int st = api.ascanStatus(aid);
                    // Overall progress blends finished seeds with the current one.
                    auditTask.setItemsTotal(100);
                    auditTask.setItemsDone(Math.min(100, (doneSeeds * 100 + Math.max(0, st)) / seeds.size()));
                    pullAlerts(seed.service().url(), auditTask, seen);
                    drain("audit", false, auditTask);
                    ctx.taskChanged(auditTask);
                    if (st >= 100) break;
                    Thread.sleep(2000);
                }
                pullAlerts(seed.service().url(), auditTask, seen);
                drain("audit", false, auditTask);
                doneSeeds++;
            }
            auditTask.log(auditTask.cancelled()
                    ? "Audit stopped"
                    : "Audit finished: " + sourceCount(auditTask) + " finding(s)");
        } finally {
            auditTask.setState(ScanTask.State.FINISHED);
            ctx.taskChanged(auditTask);
        }
    }

    /** Short label for a job that may cover several targets. */
    private static String targetLabel(java.util.List<Seed> seeds) {
        if (seeds.isEmpty()) return "targets";
        String first = seeds.get(0).service().host();
        return seeds.size() == 1 ? first : first + " +" + (seeds.size() - 1) + " more";
    }

    /** Unique discovered locations across every seed host, or -1 if none known. */
    private int totalLocations(java.util.List<Seed> seeds) {
        int total = 0; boolean any = false;
        for (Seed seed : seeds) {
            int n = locations(seed.service().url());
            if (n >= 0) { total += n; any = true; }
        }
        return any ? total : -1;
    }

    /** Count issues this task has reported, across every target. */
    private int sourceCount(ScanTask task) {
        String src = "Scan " + task.number;
        int n = 0;
        for (Issue is : ctx.issues()) if (src.equals(is.source())) n++;
        return n;
    }

    /**
     * Run ZAP's AJAX spider (a real headless Chromium) over the seed, so that
     * JavaScript-rendered links and routes are discovered and fed to the audit.
     * Bounded by a time budget; absence of the add-on or a browser is not fatal.
     */
    private void runAjaxCrawl(String seedUrl, ScanConfig cfg, ScanTask crawlTask) {
        try {
            api.setAjaxBrowser("chrome-headless");
            api.setAjaxBrowsers(2);   // bound the headless-browser pool
            try {
                api.setAjaxMaxDuration(cfg.ajaxMaxDurationMin());
                api.setAjaxMaxCrawlDepth(cfg.ajaxMaxDepth());
                api.setAjaxMaxCrawlStates(cfg.ajaxMaxStates());
            } catch (Exception ignore) { /* use the engine defaults */ }
            crawlTask.log("Starting AJAX crawl (headless Chromium, executes JavaScript)...");
            ctx.taskChanged(crawlTask);
            // inScope=false: crawl from the seed directly (Crawljax stays on the
            // seed's domain). inScope=true would require a ZAP context we do not
            // define, and fails with "starting URI is not in scope".
            api.ajaxSpiderScan(seedUrl, false);

            // Local wall-clock backstop mirroring the engine's own time budget, so
            // the loop still ends even if the engine does not stop itself. An
            // unlimited budget (0) gets a generous local cap so a runaway SPA crawl
            // cannot block the scan for ever.
            int budgetMin = cfg.ajaxMaxDurationMin() > 0 ? cfg.ajaxMaxDurationMin() : 60;
            long budgetMs = budgetMin * 60_000L;
            long deadline = System.currentTimeMillis() + budgetMs;
            int lastLoc = -1;
            String baseurl = HttpService.parse(seedUrl).url();
            while ("running".equalsIgnoreCase(api.ajaxSpiderStatus())) {
                // A deleted task stops the AJAX spider (the finally block below).
                if (crawlTask.cancelled()) break;
                drain("crawl", true, crawlTask);
                int loc = locations(baseurl);
                if (loc >= 0 && loc != lastLoc) {
                    crawlTask.setDiscoveredLocations(loc);
                    crawlTask.log("Discovered " + loc + " location(s)");
                    lastLoc = loc;
                }
                ctx.taskChanged(crawlTask);
                if (System.currentTimeMillis() > deadline) {
                    crawlTask.log("AJAX crawl time budget reached; stopping");
                    try { api.ajaxSpiderStop(); } catch (Exception ignore) { }
                    break;
                }
                Thread.sleep(1500);
            }
            drain("crawl", true, crawlTask);
            crawlTask.log("AJAX crawl finished");
        } catch (Exception e) {
            // Add-on missing, no browser/driver, or the engine refused: carry on
            // with whatever the traditional spider found.
            crawlTask.log("AJAX crawl unavailable (" + e.getMessage()
                    + "); using traditional crawl results only");
        } finally {
            // Always stop the AJAX spider so ZAP releases its headless browsers,
            // even if the crawl was interrupted or threw.
            try { api.ajaxSpiderStop(); } catch (Exception ignore) { }
        }
        ctx.taskChanged(crawlTask);
    }

    /** A ZAP control call (stop/pause/resume) whose failure must never abort the
     *  scan loop: the engine may have already finished the scan being stopped. */
    private interface ZapCall { void run() throws Exception; }
    private static void stopQuietly(ZapCall c) {
        try { c.run(); } catch (Exception ignore) { }
    }

    /** Unique discovered locations under baseurl, or the last value on error. */
    private int locations(String baseurl) {
        try { return api.uniqueLocations(baseurl); } catch (Exception e) { return -1; }
    }

    /** Fetch alerts and add any not yet seen to the issue tree; recount badges. */
    private void pullAlerts(String baseurl, ScanTask task, Set<String> seen) throws Exception {
        for (JsonValue a : api.alerts(baseurl, 0, 100000)) {
            String id = ZapApi.str(a, "id");
            if (id != null && !seen.add(id)) continue;
            Issue is = mapAlert(api, a);
            if (is != null) { is.setSource("Scan " + task.number); ctx.addIssue(is); }
        }
        recount(task);
    }

    private void recount(ScanTask task) {
        String src = "Scan " + task.number;
        int h = 0, m = 0, l = 0, i = 0;
        for (Issue is : ctx.issues()) {
            if (!src.equals(is.source())) continue;
            switch (is.severity()) {
                case HIGH -> h++;
                case MEDIUM -> m++;
                case LOW -> l++;
                case INFORMATION -> i++;
            }
        }
        task.setCounts(h, m, l, i);
    }

    // ------------------------------------------------- alert -> Issue mapping

    /**
     * Translate one ZAP alert JSON object into an OpenTRAP {@link Issue}. When
     * {@code api} is non-null the alert's HTTP message is fetched and attached.
     */
    public static Issue mapAlert(ZapApi api, JsonValue a) {
        String name = ZapApi.str(a, "name");
        if (name == null || name.isEmpty()) name = ZapApi.str(a, "alert");
        if (name == null || name.isEmpty()) return null;

        String url = ZapApi.str(a, "url");
        HttpService svc = (url != null) ? HttpService.parse(url) : null;
        if (svc == null) return null;

        Issue.Builder b = Issue.builder(name)
                .severity(severity(ZapApi.str(a, "risk")))
                .confidence(confidence(ZapApi.str(a, "confidence")))
                .service(svc)
                .path(pathOf(url))
                .param(nz(ZapApi.str(a, "param")))
                .detail(detail(a))
                .background(nz(ZapApi.str(a, "other")))
                .remediation(nz(ZapApi.str(a, "solution")))
                .references(nz(ZapApi.str(a, "reference")))
                .evidence(nz(ZapApi.str(a, "evidence")))
                .cwe(toInt(ZapApi.str(a, "cweid")))
                .wasc(toInt(ZapApi.str(a, "wascid")))
                .moduleId(toInt(ZapApi.str(a, "pluginId")))
                .dedupe(Issue.Dedupe.PATH);

        JsonValue tags = a.get("tags");
        if (tags != null && tags.isObject() && !tags.keys().isEmpty()) {
            b.tags(new ArrayList<>(tags.keys()));
        }

        if (api != null) {
            String mid = ZapApi.str(a, "messageId");
            if (mid != null && !mid.isEmpty()) {
                try { attachMessage(api, b, mid); } catch (Exception ignore) { }
            }
        }
        return b.build();
    }

    private static String detail(JsonValue a) {
        StringBuilder sb = new StringBuilder(nz(ZapApi.str(a, "description")));
        String attack = ZapApi.str(a, "attack");
        if (attack != null && !attack.isEmpty()) {
            sb.append("\n\nAttack: ").append(attack);
        }
        return sb.toString();
    }

    private static void attachMessage(ZapApi api, Issue.Builder b, String messageId) throws Exception {
        JsonValue m = api.message(messageId);
        if (m == null) return;
        String reqH = ZapApi.str(m, "requestHeader");
        String resH = ZapApi.str(m, "responseHeader");
        if (reqH != null && !reqH.isEmpty()) {
            b.request(new HttpMessage(Bytes.bytes(norm(toOriginForm(reqH)) + nz(ZapApi.str(m, "requestBody")))));
        }
        if (resH != null && !resH.isEmpty()) {
            b.response(new HttpMessage(Bytes.bytes(norm(resH) + nz(ZapApi.str(m, "responseBody")))));
        }
    }

    // ------------------------------------------- live traffic record (logger)

    private int importedMessages = 0;

    /**
     * Stream every new message ZAP has recorded into our store, so the logger
     * holds a complete live record of everything the engine ran. Tagged by phase:
     * {@code crawl} pages also populate the site map; {@code audit} probes do not,
     * to keep the site map to real endpoints rather than every attack variant.
     * Batched so a flood of attack traffic cannot pull thousands of messages in
     * one call.
     */
    private void drain(String tool, boolean toSiteMap, ScanTask task) {
        try {
            int total = api.numberOfMessages();
            int added = 0;
            while (importedMessages < total) {
                int want = Math.min(total - importedMessages, 500);
                java.util.List<JsonValue> batch = api.messages(importedMessages, want);
                if (batch.isEmpty()) break;
                for (JsonValue m : batch) if (importMessage(m, tool, toSiteMap)) added++;
                importedMessages += batch.size();
            }
            if (added > 0 && task != null) task.addRequests(added);
        } catch (Exception ignore) { }
    }

    private boolean importMessage(JsonValue m, String tool, boolean toSiteMap) {
        String reqH = ZapApi.str(m, "requestHeader");
        if (reqH == null || reqH.isEmpty()) return false;
        String target = requestTargetUrl(reqH);
        HttpService s = (target != null) ? HttpService.parse(target) : null;
        if (s == null) return false;
        HttpMessage req = new HttpMessage(Bytes.bytes(norm(toOriginForm(reqH)) + nz(ZapApi.str(m, "requestBody"))));
        LogEntry e = new LogEntry(tool, s, req);
        String resH = ZapApi.str(m, "responseHeader");
        if (resH != null && !resH.isEmpty()) {
            e.setResponse(new HttpMessage(Bytes.bytes(norm(resH) + nz(ZapApi.str(m, "responseBody")))));
        }
        ctx.store().add(e);
        if (toSiteMap) ctx.siteMapChanged(e);
        return true;
    }

    /**
     * Rewrite a request header's start line from ZAP's absolute (proxy) form,
     * {@code POST http://host/path?q HTTP/1.1}, to the origin form the rest of the
     * tool uses, {@code POST /path?q HTTP/1.1}. Without this, "copy URL" and other
     * path logic would see an already-absolute target and double the host.
     */
    private static String toOriginForm(String requestHeader) {
        int nl = requestHeader.indexOf('\n');
        if (nl < 0) return requestHeader;
        String rawStart = requestHeader.substring(0, nl);     // may end with '\r'
        String rest = requestHeader.substring(nl + 1);
        boolean cr = rawStart.endsWith("\r");
        String start = cr ? rawStart.substring(0, rawStart.length() - 1) : rawStart;
        String[] parts = start.split(" ", 3);                 // METHOD TARGET VERSION
        if (parts.length == 3
                && (parts[1].startsWith("http://") || parts[1].startsWith("https://"))) {
            try {
                java.net.URI u = java.net.URI.create(parts[1]);
                String pq = (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath())
                        + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
                start = parts[0] + " " + pq + " " + parts[2];
            } catch (Exception ignore) { /* leave as-is */ }
        }
        return start + (cr ? "\r" : "") + "\n" + rest;
    }

    /** The absolute URL from a request header's start line (ZAP uses proxy form). */
    private static String requestTargetUrl(String requestHeader) {
        int nl = requestHeader.indexOf('\n');
        String start = (nl < 0) ? requestHeader : requestHeader.substring(0, nl);
        String[] parts = start.trim().split("\\s+");
        if (parts.length < 2) return null;
        String target = parts[1];
        return target.startsWith("http://") || target.startsWith("https://") ? target : null;
    }

    // --------------------------------------------------------------- helpers

    private static Issue.Severity severity(String risk) {
        if (risk == null) return Issue.Severity.INFORMATION;
        return switch (risk.trim().toLowerCase()) {
            case "high" -> Issue.Severity.HIGH;
            case "medium" -> Issue.Severity.MEDIUM;
            case "low" -> Issue.Severity.LOW;
            default -> Issue.Severity.INFORMATION;     // "Informational"
        };
    }

    private static Issue.Confidence confidence(String c) {
        if (c == null) return Issue.Confidence.TENTATIVE;
        return switch (c.trim().toLowerCase()) {
            case "high", "confirmed", "user confirmed" -> Issue.Confidence.CERTAIN;
            case "medium" -> Issue.Confidence.FIRM;
            default -> Issue.Confidence.TENTATIVE;     // "Low", "False Positive"
        };
    }

    private static String pathOf(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            String p = u.getRawPath();
            if (p == null || p.isEmpty()) p = "/";
            if (u.getRawQuery() != null) p += "?" + u.getRawQuery();
            return p;
        } catch (Exception e) {
            return "/";
        }
    }

    private static int toInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    private static String nz(String s) { return (s == null) ? "" : s; }

    /** Normalise a header block to end in a single blank line before the body. */
    private static String norm(String header) {
        return header.replaceAll("\\r?\\n+$", "") + "\r\n\r\n";
    }

    // ----------------------------------------------------------- diagnostics

    /**
     * Standalone check: attach to a running ZAP daemon and print how its alerts
     * for a base URL map into OpenTRAP issues. Not part of the app; run with
     *   java -cp build opentrap.engine.ZapScanner http://127.0.0.1:8090 "" http://host/
     */
    public static void main(String[] args) throws Exception {
        String base = args.length > 0 ? args[0] : "http://127.0.0.1:8090";
        String key = args.length > 1 ? args[1] : "";
        String baseurl = args.length > 2 ? args[2] : "http://testaspnet.vulnweb.com/";
        ZapApi api = new ZapApi(base, key);
        System.out.println("engine version: " + api.version());
        List<JsonValue> alerts = api.alerts(baseurl, 0, 100000);
        System.out.println("raw alerts: " + alerts.size());
        java.util.Map<String, int[]> bySev = new java.util.TreeMap<>();
        Issue sample = null;
        int mapped = 0;
        for (JsonValue a : alerts) {
            Issue is = mapAlert(api, a);
            if (is == null) continue;
            mapped++;
            bySev.computeIfAbsent(is.severity().label(), k -> new int[1])[0]++;
            if (sample == null && is.severity() == Issue.Severity.HIGH) sample = is;
        }
        System.out.println("mapped issues: " + mapped);
        bySev.forEach((s, c) -> System.out.println("  " + s + ": " + c[0]));
        if (sample == null && mapped > 0) {
            // fall back to any issue for the sample
            for (JsonValue a : alerts) { Issue is = mapAlert(api, a); if (is != null) { sample = is; break; } }
        }
        if (sample != null) {
            System.out.println("\nsample mapped issue:");
            System.out.println("  title      : " + sample.title());
            System.out.println("  severity   : " + sample.severity().label() + "  confidence: " + sample.confidence().label());
            System.out.println("  host/path  : " + sample.host() + sample.path());
            System.out.println("  param      : " + sample.param());
            System.out.println("  cwe/wasc   : " + sample.cwe() + " / " + sample.wasc() + "  module: " + sample.moduleId());
            System.out.println("  evidence   : " + trunc(sample.evidence(), 80));
            System.out.println("  request?   : " + (sample.request() != null));
            System.out.println("  response?  : " + (sample.response() != null));
            System.out.println("  detail     : " + trunc(sample.detail(), 120));
        }
    }

    private static String trunc(String s, int n) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ");
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
