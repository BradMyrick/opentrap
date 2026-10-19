package opentrap.core;

import java.io.Serializable;
import java.util.Objects;

/** Identifies a remote endpoint: host, port, and whether TLS is used. */
public final class HttpService implements Serializable, Comparable<HttpService> {
    private static final long serialVersionUID = 1L;
    private final String host;
    private final int port;
    private final boolean secure;

    public HttpService(String host, int port, boolean secure) {
        this.host = host == null ? "" : host;
        this.port = port;
        this.secure = secure;
    }

    public String host()    { return host; }
    public int port()       { return port; }
    public boolean secure() { return secure; }
    public String protocol(){ return secure ? "https" : "http"; }

    public boolean defaultPort() { return port == (secure ? 443 : 80); }

    /** e.g. https://example.org or http://example.org:8080 */
    public String url() {
        return protocol() + "://" + host + (defaultPort() ? "" : ":" + port);
    }

    @Override public String toString() { return url(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HttpService s)) return false;
        return port == s.port && secure == s.secure && host.equalsIgnoreCase(s.host);
    }

    @Override public int hashCode() {
        return Objects.hash(host.toLowerCase(), port, secure);
    }

    @Override public int compareTo(HttpService o) {
        int c = host.compareToIgnoreCase(o.host);
        if (c != 0) return c;
        c = Integer.compare(port, o.port);
        if (c != 0) return c;
        return Boolean.compare(secure, o.secure);
    }

    public static HttpService of(String host, int port, boolean secure) {
        return new HttpService(host, port, secure);
    }

    /** Parse "https://host:port" style strings. Returns null when unparseable. */
    public static HttpService parse(String url) {
        try {
            java.net.URI u = java.net.URI.create(url.trim());
            String scheme = u.getScheme();
            if (scheme == null) return null;
            boolean sec = scheme.equalsIgnoreCase("https");
            int p = u.getPort();
            if (p == -1) p = sec ? 443 : 80;
            if (u.getHost() == null) return null;
            return new HttpService(u.getHost(), p, sec);
        } catch (Exception e) {
            return null;
        }
    }
}
