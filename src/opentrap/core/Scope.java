package opentrap.core;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Suite-wide target scope: include and exclude rules over protocol, host,
 * port and file, each a regex. Consulted by every tool, so it lives once here.
 *
 * <p>Read on proxy connection threads, scan threads and crawler threads while
 * being edited on the event dispatch thread, so the rule lists are copy-on-write.
 * They were plain lists, which meant editing scope while traffic flowed could
 * throw inside a connection and drop the request.
 *
 * <p>Each rule caches its compiled patterns. The scope is consulted several
 * times per request by the proxy, the passive scanner and the crawler, and
 * recompiling three regexes every time was measurable.
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

        // Compiled forms of the three patterns, rebuilt when the text changes.
        private transient Pattern hostPattern;
        private transient Pattern portPattern;
        private transient Pattern filePattern;
        private transient String compiledHost;
        private transient String compiledPort;
        private transient String compiledFile;

        public Rule() {}
        public Rule(String protocol, String host, String port, String file) {
            this.protocol = protocol; this.host = host; this.port = port; this.file = file;
        }

        boolean matches(HttpService svc, String path) {
            if (!enabled) return false;
            if (!"any".equalsIgnoreCase(protocol)) {
                boolean wantSecure = "https".equalsIgnoreCase(protocol);
                if (svc.secure() != wantSecure) return false;
            }
            if (!matchHost(svc.host())) return false;
            if (!matchPort(String.valueOf(svc.port()))) return false;
            if (!matchFile(path == null ? "" : path)) return false;
            return true;
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

        @Override public String toString() { return protocol + " " + host + " " + port + " " + file; }
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

    /** Add an include rule covering a host, or a path prefix below it. */
    public void includeUrl(HttpService svc, String path, boolean wholeHost) {
        Rule r = new Rule();
        r.protocol = svc.secure() ? "https" : "http";
        r.host = "^" + Pattern.quote(svc.host()) + "$";
        r.port = "^" + svc.port() + "$";
        r.file = wholeHost ? "" : "^" + Pattern.quote(path);
        include.add(r);
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
        Rule r = new Rule();
        r.protocol = "any";
        r.host = "^" + Pattern.quote(svc.host()) + "$";
        r.port = "";   // any port, so 80 and 443 both match
        r.file = "";
        include.add(r);
    }

    public void excludeUrl(HttpService svc, String path, boolean wholeHost) {
        Rule r = new Rule();
        r.protocol = svc.secure() ? "https" : "http";
        r.host = "^" + Pattern.quote(svc.host()) + "$";
        r.port = "^" + svc.port() + "$";
        r.file = wholeHost ? "" : "^" + Pattern.quote(path);
        exclude.add(r);
    }

    public void removeIncludeFor(HttpService svc, String path) {
        include.removeIf(r -> r.matches(svc, path));
    }

    public boolean isEmpty() { return include.isEmpty(); }
}
