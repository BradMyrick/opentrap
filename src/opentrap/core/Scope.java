package opentrap.core;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Suite-wide target scope: include and exclude rules, consulted by every tool,
 * so it lives once here.
 *
 * <p>A rule is one of two kinds. A <b>prefix</b> rule holds a URL prefix such as
 * {@code https://example.com/app} and matches every URL under it, optionally
 * on subdomains too; it is what the site map's "add to scope" and the simple
 * scope table create. An <b>advanced</b> rule holds a regex for each of the
 * protocol, host, port and file, as the original did.
 *
 * <p>Read on proxy connection threads, scan threads and crawler threads while
 * being edited on the event dispatch thread, so the rule lists are copy-on-write.
 * They were plain lists, which meant editing scope while traffic flowed could
 * throw inside a connection and drop the request.
 *
 * <p>Each advanced rule caches its compiled patterns. The scope is consulted
 * several times per request by the proxy, the passive scanner and the crawler,
 * and recompiling three regexes every time was measurable.
 */
public class Scope implements Serializable {
    private static final long serialVersionUID = 1L;

    public static class Rule implements Serializable {
        private static final long serialVersionUID = 1L;
        public boolean enabled = true;
        public String protocol = "any";   // any | http | https
        public String host = "";
        public String port = "";
        public String file = "";

        // A prefix rule: the URL prefix, and whether subdomains of its host count.
        // Null for an advanced (regex) rule.
        public String prefix;
        public boolean subdomains;

        // Compiled forms of the three patterns, rebuilt when the text changes.
        private transient Pattern hostPattern;
        private transient Pattern portPattern;
        private transient Pattern filePattern;
        private transient String compiledHost;
        private transient String compiledPort;
        private transient String compiledFile;

        // The parsed prefix, rebuilt when the text changes.
        private transient String parsedFor;
        private transient String pScheme, pHost, pPath;
        private transient int pPort;

        public Rule() {}
        public Rule(String protocol, String host, String port, String file) {
            this.protocol = protocol; this.host = host; this.port = port; this.file = file;
        }

        /** A prefix rule over {@code prefix}, normalised. Returns null for text that is not a URL. */
        public static Rule prefix(String prefix, boolean subdomains) {
            String p = normalisePrefix(prefix);
            if (p == null) return null;
            Rule r = new Rule();
            r.prefix = p;
            r.subdomains = subdomains;
            return r;
        }

        public boolean isPrefix() { return prefix != null; }

        boolean matches(HttpService svc, String path) {
            if (!enabled) return false;
            if (prefix != null) return matchesPrefix(svc, path);
            if (!"any".equalsIgnoreCase(protocol)) {
                boolean wantSecure = "https".equalsIgnoreCase(protocol);
                if (svc.secure() != wantSecure) return false;
            }
            if (!matchHost(svc.host())) return false;
            if (!matchPort(String.valueOf(svc.port()))) return false;
            if (!matchFile(path == null ? "" : path)) return false;
            return true;
        }

        private synchronized boolean matchesPrefix(HttpService svc, String path) {
            if (!prefix.equals(parsedFor)) parsePrefix();
            if (pHost == null) return false;
            if (pScheme != null && svc.secure() != "https".equals(pScheme)) return false;
            String h = svc.host().toLowerCase(Locale.ROOT);
            if (!h.equals(pHost) && !(subdomains && h.endsWith("." + pHost))) return false;
            if (pPort > 0 && svc.port() != pPort) return false;
            if (pPath.isEmpty() || pPath.equals("/")) return true;
            // A plain prefix, as the reference matches it: /app covers /app,
            // /app/login and /application alike.
            return (path == null ? "/" : path).startsWith(pPath);
        }

        private void parsePrefix() {
            parsedFor = prefix;
            pScheme = null; pHost = null; pPath = ""; pPort = 0;
            try {
                String s = prefix;
                boolean hasScheme = s.contains("://");
                java.net.URI u = new java.net.URI(hasScheme ? s : "http://" + s);
                if (u.getHost() == null) return;
                if (hasScheme && u.getScheme() != null) pScheme = u.getScheme().toLowerCase(Locale.ROOT);
                pHost = u.getHost().toLowerCase(Locale.ROOT);
                pPort = u.getPort() > 0 ? u.getPort()
                        : (pScheme == null ? 0 : ("https".equals(pScheme) ? 443 : 80));
                pPath = u.getRawPath() == null ? "" : u.getRawPath();
            } catch (Exception e) {
                pHost = null;
            }
        }

        private synchronized boolean matchHost(String value) {
            if (host == null || host.isBlank()) return true;
            if (!host.equals(compiledHost)) { hostPattern = compile(host); compiledHost = host; }
            return apply(hostPattern, host, value);
        }

        private synchronized boolean matchPort(String value) {
            if (port == null || port.isBlank()) return true;
            if (!port.equals(compiledPort)) { portPattern = compile(port); compiledPort = port; }
            return apply(portPattern, port, value);
        }

        private synchronized boolean matchFile(String value) {
            if (file == null || file.isBlank()) return true;
            if (!file.equals(compiledFile)) { filePattern = compile(file); compiledFile = file; }
            return apply(filePattern, file, value);
        }

        private static Pattern compile(String pattern) {
            try {
                return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                return null;   // fall back to a literal comparison below
            }
        }

        /**
         * A rule whose regex does not compile falls back to a literal substring
         * test rather than matching nothing, so a half-typed pattern does not
         * silently take the whole target out of scope.
         */
        private static boolean apply(Pattern compiled, String source, String value) {
            if (compiled == null) {
                return value.toLowerCase(java.util.Locale.ROOT)
                        .contains(source.toLowerCase(java.util.Locale.ROOT));
            }
            return compiled.matcher(value).find();
        }

        /**
         * The rule in one readable line: the prefix itself, or for an advanced
         * rule the parts it constrains, such as {@code file matches logout}.
         */
        public String describe() {
            if (prefix != null) return prefix;
            StringBuilder b = new StringBuilder();
            if (!"any".equalsIgnoreCase(protocol)) b.append(protocol).append(" ");
            if (host != null && !host.isBlank()) b.append("host matches ").append(display(host)).append("; ");
            if (port != null && !port.isBlank()) b.append("port ").append(display(port)).append("; ");
            if (file != null && !file.isBlank()) b.append("file matches ").append(display(file)).append("; ");
            String s = b.toString().trim();
            if (s.endsWith(";")) s = s.substring(0, s.length() - 1);
            return s.isEmpty() ? "any URL" : s;
        }

        @Override public String toString() { return describe(); }

        /**
         * The rule as one regex over a whole URL ({@code scheme://host:port/path}),
         * which is how the scan engine takes a scope. A prefix rule converts
         * exactly. An advanced rule's three patterns are matched by search
         * against the host, port and path, so each is embedded between
         * wildcards; a leading {@code ^} or trailing {@code $} on the file
         * pattern keeps its anchoring. Null when the rule is disabled or has no
         * usable host.
         */
        public synchronized String urlRegex() {
            if (!enabled) return null;
            if (prefix != null) {
                if (!prefix.equals(parsedFor)) parsePrefix();
                if (pHost == null) return null;
                String scheme = pScheme == null ? "https?" : Pattern.quote(pScheme);
                String hostPart = (subdomains ? "(?:[^/:]+\\.)?" : "") + Pattern.quote(pHost);
                String portPart = pPort > 0 ? "(?::" + pPort + ")?" : "(?::\\d+)?";
                String pathPart = (pPath.isEmpty() || pPath.equals("/"))
                        ? "(?:/.*)?" : Pattern.quote(pPath) + ".*";
                return "(?i)^" + scheme + "://" + hostPart + portPart + pathPart + "$";
            }
            String scheme = "any".equalsIgnoreCase(protocol) ? "https?" : protocol.toLowerCase(Locale.ROOT);
            String hostPart;
            if (host == null || host.isBlank()) {
                hostPart = "[^/:]+";
            } else {
                boolean head = host.startsWith("^");
                boolean tail = host.endsWith("$") && !host.endsWith("\\$");
                hostPart = (head ? "" : "[^/:]*") + "(?:" + unanchored(host) + ")" + (tail ? "" : "[^/:]*");
            }
            String portPart;
            if (port == null || port.isBlank()) {
                portPart = "(?::\\d+)?";
            } else {
                // A port pattern that admits a default port must also accept a
                // URL that leaves the port out, as the engine writes them.
                boolean dflt = matchPort("80") || matchPort("443");
                portPart = "(?::\\d*(?:" + unanchored(port) + ")\\d*)" + (dflt ? "?" : "");
            }
            String pathPart;
            if (file == null || file.isBlank()) {
                pathPart = "(?:/.*)?";
            } else {
                String f = file;
                boolean head = f.startsWith("^");
                boolean tail = f.endsWith("$") && !f.endsWith("\\$");
                if (head) f = f.substring(1);
                if (tail) f = f.substring(0, f.length() - 1);
                pathPart = "(?=/)" + (head ? "" : ".*") + "(?:" + f + ")" + (tail ? "" : ".*");
            }
            return "(?i)^" + scheme + "://" + hostPart + portPart + pathPart + "$";
        }

        /** A search pattern without its outer anchors, ready to sit inside a group. */
        private static String unanchored(String re) {
            String s = re;
            if (s.startsWith("^")) s = s.substring(1);
            if (s.endsWith("$") && !s.endsWith("\\$")) s = s.substring(0, s.length() - 1);
            return s;
        }
    }

    /**
     * Tidy a typed prefix: trim, add {@code https://} when no scheme was given,
     * lower-case the host and drop a default port. Null when it is not a URL.
     */
    public static String normalisePrefix(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty()) return null;
        try {
            java.net.URI u = new java.net.URI(s.contains("://") ? s : "https://" + s);
            if (u.getHost() == null || u.getHost().isEmpty()) return null;
            String scheme = u.getScheme() == null ? "https" : u.getScheme().toLowerCase(Locale.ROOT);
            int port = u.getPort();
            boolean defaultPort = port <= 0 || (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
            String path = u.getRawPath() == null ? "" : u.getRawPath();
            if (path.equals("/")) path = "";
            return scheme + "://" + u.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port) + path;
        } catch (Exception e) {
            return null;
        }
    }

    private final List<Rule> include = new CopyOnWriteArrayList<>();
    private final List<Rule> exclude = new CopyOnWriteArrayList<>();

    public Scope() {
        // Same thoughtful default the original shipped: never walk into logout links.
        exclude.add(new Rule("any", "", "", "logout"));
        exclude.add(new Rule("any", "", "", "logoff"));
        exclude.add(new Rule("any", "", "", "exit"));
        exclude.add(new Rule("any", "", "", "signout"));
    }

    public List<Rule> include() { return include; }
    public List<Rule> exclude() { return exclude; }

    /**
     * Convert a stored scope pattern back to readable text, for display or for
     * seeding a URL field. Rules created automatically from a target wrap the
     * host or path in regex anchors and a {@code \Q...\E} literal quote, so the
     * raw pattern reads {@code ^\Qexample.com\E$}; strip those so the user sees
     * {@code example.com}. A hand-written regex has no {@code \Q...\E} and comes
     * back with only its anchors removed. Hosts and paths never contain the
     * literal text {@code \E}, so the markers can be removed directly.
     */
    public static String display(String pattern) {
        if (pattern == null) return "";
        String s = pattern;
        if (s.startsWith("^")) s = s.substring(1);
        if (s.endsWith("$")) s = s.substring(0, s.length() - 1);
        return s.replace("\\Q", "").replace("\\E", "");
    }

    public boolean isInScope(HttpService svc, String path) {
        if (svc == null) return false;
        boolean in = false;
        for (Rule r : include) if (r.matches(svc, path)) { in = true; break; }
        if (!in) return false;
        for (Rule r : exclude) if (r.matches(svc, path)) return false;
        return true;
    }

    public boolean isInScope(LogEntry e) {
        return e != null && isInScope(e.service(), e.request() == null ? "/" : e.request().path());
    }

    /** The prefix that names a host, or a path below it, as a rule would hold it. */
    public static String prefixFor(HttpService svc, String path, boolean wholeHost) {
        return normalisePrefix(svc.url() + (wholeHost || path == null ? "" : path));
    }

    /** Add an include rule covering a host, or a path prefix below it. */
    public void includeUrl(HttpService svc, String path, boolean wholeHost) {
        String p = prefixFor(svc, path, wholeHost);
        if (p == null) return;
        for (Rule r : include) if (p.equals(r.prefix)) { r.enabled = true; return; }
        include.add(Rule.prefix(p, false));
    }

    /**
     * Include a whole host regardless of scheme or port.
     *
     * <p>Used when spidering a target, where the tester means "test this host"
     * and should not be tripped up by http versus https. A scheme-specific rule
     * would, for example, put an http-only site out of scope the moment the
     * crawler fell back from https to http.
     */
    public void includeHost(HttpService svc) {
        // A prefix with no scheme matches any scheme and any port.
        String p = svc.host().toLowerCase(Locale.ROOT);
        for (Rule r : include) if (p.equals(r.prefix)) { r.enabled = true; return; }
        Rule r = new Rule();
        r.prefix = p;
        include.add(r);
    }

    public void excludeUrl(HttpService svc, String path, boolean wholeHost) {
        String p = prefixFor(svc, path, wholeHost);
        if (p == null) return;
        for (Rule r : exclude) if (p.equals(r.prefix)) { r.enabled = true; return; }
        exclude.add(Rule.prefix(p, false));
    }

    /**
     * Take a host or path out of scope the way the site map menu means it: an
     * include rule for exactly that prefix is removed; otherwise, when it is
     * in scope through a wider rule, an exclude rule for it is added.
     */
    public void removeUrl(HttpService svc, String path, boolean wholeHost) {
        String p = prefixFor(svc, path, wholeHost);
        if (p == null) return;
        boolean removed = include.removeIf(r -> p.equals(r.prefix));
        if (!removed && isInScope(svc, wholeHost ? "/" : path)) excludeUrl(svc, path, wholeHost);
    }

    public void removeIncludeFor(HttpService svc, String path) {
        include.removeIf(r -> r.matches(svc, path));
    }

    public boolean isEmpty() { return include.isEmpty(); }
}
