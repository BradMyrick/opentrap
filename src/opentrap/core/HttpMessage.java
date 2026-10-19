package opentrap.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed HTTP request or response that always retains the original bytes.
 *
 * Nothing here normalises the message. A testing tool must be able to send
 * deliberately malformed traffic, so parsing is advisory and the raw byte
 * array is the source of truth.
 */
public class HttpMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private byte[] raw;
    private transient Integer bodyOffsetCache;

    public HttpMessage(byte[] raw) { this.raw = raw == null ? new byte[0] : raw; }

    public byte[] raw() { return raw; }

    public void setRaw(byte[] b) {
        this.raw = b == null ? new byte[0] : b;
        this.bodyOffsetCache = null;
    }

    public int length() { return raw.length; }

    /** Offset of the first body byte. Equals length() when there is no body. */
    public int bodyOffset() {
        if (bodyOffsetCache != null) return bodyOffsetCache;
        int i = Bytes.indexOf(raw, Bytes.CRLF2);
        if (i >= 0) { bodyOffsetCache = i + 4; return bodyOffsetCache; }
        // Tolerate bare-LF message framing.
        for (int k = 0; k + 1 < raw.length; k++) {
            if (raw[k] == 10 && raw[k + 1] == 10) { bodyOffsetCache = k + 2; return bodyOffsetCache; }
        }
        bodyOffsetCache = raw.length;
        return bodyOffsetCache;
    }

    public byte[] body() { return Bytes.slice(raw, bodyOffset(), raw.length); }

    public String headerText() { return Bytes.str(Bytes.slice(raw, 0, bodyOffset())); }

    public String bodyText() { return Bytes.str(body()); }

    /** Header lines excluding the start line. */
    public List<String> headerLines() {
        List<String> all = Bytes.lines(headerText());
        List<String> out = new ArrayList<>();
        for (int i = 1; i < all.size(); i++) {
            String l = all.get(i);
            if (l.isEmpty()) break;
            out.add(l);
        }
        return out;
    }

    public String startLine() {
        List<String> l = Bytes.lines(headerText());
        return l.isEmpty() ? "" : l.get(0);
    }

    public String header(String name) {
        String want = name.toLowerCase(Locale.ROOT) + ":";
        for (String l : headerLines()) {
            if (l.toLowerCase(Locale.ROOT).startsWith(want)) {
                return l.substring(want.length()).trim();
            }
        }
        return null;
    }

    public boolean hasHeader(String name) { return header(name) != null; }

    /** Replace a header's value, or append the header when absent. */
    public void setHeader(String name, String value) {
        List<String> lines = Bytes.lines(headerText());
        String want = name.toLowerCase(Locale.ROOT) + ":";
        boolean found = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.isEmpty()) {
                if (!found) { sb.append(name).append(": ").append(value).append("\r\n"); found = true; }
                sb.append("\r\n");
                // Everything after the blank line is body; stop rewriting headers.
                byte[] rest = Bytes.slice(raw, bodyOffset(), raw.length);
                setRaw(Bytes.concat(Bytes.bytes(sb.toString()), rest));
                return;
            }
            if (i > 0 && l.toLowerCase(Locale.ROOT).startsWith(want)) {
                if (!found) { sb.append(name).append(": ").append(value).append("\r\n"); found = true; }
            } else {
                sb.append(l).append("\r\n");
            }
        }
        if (!found) sb.append(name).append(": ").append(value).append("\r\n");
        sb.append("\r\n");
        setRaw(Bytes.concat(Bytes.bytes(sb.toString()), body()));
    }

    public void removeHeader(String name) {
        List<String> lines = Bytes.lines(headerText());
        String want = name.toLowerCase(Locale.ROOT) + ":";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.isEmpty()) break;
            if (i > 0 && l.toLowerCase(Locale.ROOT).startsWith(want)) continue;
            sb.append(l).append("\r\n");
        }
        sb.append("\r\n");
        setRaw(Bytes.concat(Bytes.bytes(sb.toString()), body()));
    }

    public void setBody(byte[] newBody) {
        setRaw(Bytes.concat(Bytes.slice(raw, 0, bodyOffset()), newBody));
    }

    /** Recompute Content-Length to match the actual body. */
    public void updateContentLength() {
        if (bodyOffset() >= raw.length && header("Content-Length") == null) return;
        setHeader("Content-Length", String.valueOf(body().length));
    }

    public boolean isRequest() {
        String s = startLine();
        return !s.toUpperCase(Locale.ROOT).startsWith("HTTP/");
    }

    // ---------------- request accessors ----------------

    public String method() {
        String s = startLine();
        int i = s.indexOf(' ');
        return i < 0 ? "" : s.substring(0, i);
    }

    /** Request target as it appears on the wire, query string included. */
    public String requestTarget() {
        String s = startLine();
        int a = s.indexOf(' ');
        if (a < 0) return "";
        int b = s.indexOf(' ', a + 1);
        return b < 0 ? s.substring(a + 1) : s.substring(a + 1, b);
    }

    public String path() {
        String t = requestTarget();
        if (t.startsWith("http://") || t.startsWith("https://")) {
            int i = t.indexOf('/', t.indexOf("//") + 2);
            t = i < 0 ? "/" : t.substring(i);
        }
        int q = t.indexOf('?');
        return q < 0 ? t : t.substring(0, q);
    }

    public String query() {
        String t = requestTarget();
        int q = t.indexOf('?');
        return q < 0 ? "" : t.substring(q + 1);
    }

    public void setRequestTarget(String target) {
        String s = startLine();
        int a = s.indexOf(' ');
        if (a < 0) return;
        int b = s.indexOf(' ', a + 1);
        String tail = b < 0 ? "" : s.substring(b);
        String newLine = s.substring(0, a + 1) + target + tail;
        byte[] rest = Bytes.slice(raw, lineEnd(0), raw.length);
        setRaw(Bytes.concat(Bytes.bytes(newLine), rest));
    }

    public void setMethod(String m) {
        String s = startLine();
        int a = s.indexOf(' ');
        if (a < 0) return;
        String newLine = m + s.substring(a);
        byte[] rest = Bytes.slice(raw, lineEnd(0), raw.length);
        setRaw(Bytes.concat(Bytes.bytes(newLine), rest));
    }

    private int lineEnd(int lineIndex) {
        int pos = 0, seen = 0;
        while (pos < raw.length) {
            if (raw[pos] == 10) {
                if (seen == lineIndex) return pos;
                seen++;
            }
            pos++;
        }
        return raw.length;
    }

    public String fileExtension() {
        String p = path();
        int slash = p.lastIndexOf('/');
        String last = slash < 0 ? p : p.substring(slash + 1);
        int dot = last.lastIndexOf('.');
        if (dot < 0 || dot == last.length() - 1) return "";
        String ext = last.substring(dot + 1);
        return ext.matches("[A-Za-z0-9]{1,8}") ? ext.toLowerCase(Locale.ROOT) : "";
    }

    // ---------------- response accessors ----------------

    public int statusCode() {
        String s = startLine();
        Matcher m = Pattern.compile("^HTTP/[0-9.]+\\s+(\\d{3})").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    public String statusText() {
        String s = startLine();
        Matcher m = Pattern.compile("^HTTP/[0-9.]+\\s+\\d{3}\\s*(.*)$").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    public String contentType() {
        String ct = header("Content-Type");
        if (ct == null) return "";
        int i = ct.indexOf(';');
        return (i < 0 ? ct : ct.substring(0, i)).trim().toLowerCase(Locale.ROOT);
    }

    /** Coarse MIME grouping used by the history and site map filters. */
    public String mimeType() {
        String ct = contentType();
        if (ct.isEmpty()) {
            byte[] b = body();
            if (b.length == 0) return "";
            return Bytes.isPrintableText(b, 256) ? "other text" : "other binary";
        }
        if (ct.contains("html")) return "HTML";
        if (ct.contains("javascript") || ct.contains("ecmascript")) return "script";
        if (ct.contains("json")) return "JSON";
        if (ct.contains("xml")) return "XML";
        if (ct.startsWith("image/")) return "images";
        if (ct.startsWith("audio/") || ct.startsWith("video/")) return "sound";
        if (ct.contains("css")) return "CSS";
        if (ct.startsWith("text/")) return "other text";
        return "other binary";
    }

    private static final Pattern TITLE =
        Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public String htmlTitle() {
        if (!"HTML".equals(mimeType())) return "";
        Matcher m = TITLE.matcher(bodyText());
        if (!m.find()) return "";
        return m.group(1).replaceAll("\\s+", " ").trim();
    }

    // ---------------- parameters ----------------

    public List<Param> parameters() {
        List<Param> out = new ArrayList<>();
        if (!isRequest()) return out;

        // Query string parameters, offsets relative to the whole message.
        String target = requestTarget();
        int targetStart = startLine().indexOf(' ') + 1;
        int q = target.indexOf('?');
        if (q >= 0) {
            parsePairs(target.substring(q + 1), targetStart + q + 1, Param.Type.URL, out, '&');
        }

        // Cookies.
        String cookie = header("Cookie");
        if (cookie != null) {
            int base = Bytes.indexOfIgnoreCase(raw, Bytes.bytes("Cookie:"), 0);
            if (base >= 0) {
                int vstart = base + "Cookie:".length();
                while (vstart < raw.length && (raw[vstart] == ' ' || raw[vstart] == '\t')) vstart++;
                parsePairs(cookie, vstart, Param.Type.COOKIE, out, ';');
            }
        }

        // URL-encoded body.
        String ct = contentType();
        if (ct.contains("x-www-form-urlencoded") || (ct.isEmpty() && body().length > 0 && bodyText().contains("="))) {
            parsePairs(bodyText(), bodyOffset(), Param.Type.BODY, out, '&');
        }
        return out;
    }

    private void parsePairs(String s, int base, Param.Type type, List<Param> out, char sep) {
        int i = 0;
        while (i <= s.length()) {
            int end = s.indexOf(sep, i);
            if (end < 0) end = s.length();
            String pair = s.substring(i, end);
            if (!pair.isBlank()) {
                int lead = 0;
                while (lead < pair.length() && pair.charAt(lead) == ' ') lead++;
                int eq = pair.indexOf('=');
                int pairStart = base + i + lead;
                if (eq < 0) {
                    out.add(new Param(pair.trim(), "", type,
                            pairStart, pairStart + pair.trim().length(),
                            pairStart + pair.trim().length(), pairStart + pair.trim().length()));
                } else {
                    String n = pair.substring(lead, eq);
                    String v = pair.substring(eq + 1);
                    int ns = pairStart;
                    int ne = ns + n.length();
                    int vs = ne + 1;
                    out.add(new Param(n, v, type, ns, ne, vs, vs + v.length()));
                }
            }
            if (end >= s.length()) break;
            i = end + 1;
        }
    }

    public boolean hasParameters() { return !parameters().isEmpty(); }

    public HttpMessage copy() { return new HttpMessage(raw.clone()); }

    @Override public String toString() {
        return isRequest() ? method() + " " + requestTarget() : startLine();
    }
}
