package opentrap.proxy;

import opentrap.core.Bytes;
import opentrap.core.HttpMessage;
import opentrap.core.HttpService;
import opentrap.core.LogEntry;
import opentrap.core.Scope;
import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

/** The history and site map filter: its defaults, its toggles and the bar's wording. */
public final class HistoryFilterTest {

    private static final HttpService SVC = new HttpService("example.com", 443, true);

    private static LogEntry entry(String path, int status, String type) {
        LogEntry e = new LogEntry("proxy", SVC, new HttpMessage(Bytes.bytes("GET " + path + " HTTP/1.1\r\nHost: example.com\r\n\r\n")));
        if (status > 0) e.setResponse(new HttpMessage(Bytes.bytes("HTTP/1.1 " + status + " X\r\nContent-Type: " + type + "\r\n\r\n<p>")));
        return e;
    }

    public void run() {
        Harness.suite("history filter");

        Harness.test("the site map starts the way the reference does", () -> {
            HistoryFilter f = HistoryFilter.siteMapDefaults();
            Expect.equal(f.summary(),
                    "Hiding not found items; hiding CSS, image and general binary content; hiding 4xx responses; hiding empty folders",
                    "bar text");
            Expect.isFalse(f.test(entry("/missing", 404, "text/html")), "404 hidden");
            Expect.isFalse(f.test(entry("/style.css", 200, "text/css")), "CSS hidden");
            Expect.isFalse(f.test(entry("/forbidden", 403, "text/html")), "4xx hidden");
            Expect.isTrue(f.test(entry("/", 200, "text/html")), "a page shows");
        });

        Harness.test("the proxy history starts the way the reference does", () -> {
            HistoryFilter f = HistoryFilter.proxyDefaults();
            Expect.equal(f.summary(), "Hiding CSS and image content; hiding specific extensions", "bar text");
            Expect.isFalse(f.test(entry("/app.js", 200, "text/html")), "js extension hidden");
            Expect.isTrue(f.test(entry("/api/items", 200, "application/json")), "JSON shows");
            Expect.isTrue(f.test(entry("/missing", 404, "text/html")), "404 shows in the history");
        });

        Harness.test("scope, annotations and the clear text", () -> {
            HistoryFilter f = new HistoryFilter();
            Expect.equal(f.summary(), "Showing all items", "nothing active");
            Expect.isTrue(f.isEverything(), "everything");
            f.inScopeOnly = true;
            f.hideNotFound = true;
            f.scope = new Scope();
            Expect.equal(f.summary(), "Hiding out of scope and not found items", "joined wording");
            Expect.isFalse(f.test(entry("/", 200, "text/html")), "out of scope hidden");
            f.scope.includeUrl(SVC, "/", true);
            Expect.isTrue(f.test(entry("/", 200, "text/html")), "in scope shows");
            f.showAll();
            f.notesOnly = true;
            LogEntry noted = entry("/n", 200, "text/html");
            noted.setNotes("look here");
            Expect.isTrue(f.test(noted), "noted item shows");
            Expect.isFalse(f.test(entry("/", 200, "text/html")), "unnoted hidden");
            f.showAll();
            f.highlightedOnly = true;
            noted.setHighlight("red");
            Expect.isTrue(f.test(noted), "highlighted shows");
            Expect.isFalse(f.test(entry("/", 200, "text/html")), "plain hidden");
        });

        Harness.test("extension lists only apply when switched on", () -> {
            HistoryFilter f = new HistoryFilter();
            f.extShowOnly = "php";
            Expect.isTrue(f.test(entry("/a.html", 200, "text/html")), "list off");
            f.extShowOnlyOn = true;
            Expect.isFalse(f.test(entry("/a.html", 200, "text/html")), "list on");
            Expect.isTrue(f.test(entry("/a.php", 200, "text/html")), "listed extension shows");
        });

        Harness.test("a copy carries every setting", () -> {
            HistoryFilter f = HistoryFilter.siteMapDefaults();
            f.searchTerm = "token";
            f.searchRegex = true;
            HistoryFilter c = f.copy();
            Expect.equal(c.summary(), f.summary(), "same summary");
            Expect.equal(c.searchTerm, "token", "search term");
            Expect.isTrue(c.searchRegex, "regex flag");
        });
    }
}
