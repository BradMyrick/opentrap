package opentrap.proxy;

import opentrap.core.Bytes;
import opentrap.core.HttpMessage;

import java.io.Serializable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A match-and-replace rule over a request or response, header or body. */
public class MatchReplaceRule implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String REQ_HEADER  = "request header";
    public static final String REQ_BODY    = "request body";
    public static final String RESP_HEADER = "response header";
    public static final String RESP_BODY   = "response body";
    public static final String[] TYPES = { REQ_HEADER, REQ_BODY, RESP_HEADER, RESP_BODY };

    // WebSocket rules: which direction a text message must be travelling in.
    public static final String WS_TO_SERVER = "to server";
    public static final String WS_TO_CLIENT = "to client";
    public static final String WS_BOTH      = "both directions";
    public static final String[] WS_TYPES = { WS_BOTH, WS_TO_SERVER, WS_TO_CLIENT };

    public boolean enabled = true;
    public String type = REQ_HEADER;
    public String match = "";
    public String replace = "";
    public String comment = "";

    public MatchReplaceRule() {}

    public MatchReplaceRule(boolean enabled, String type, String match, String replace, String comment) {
        this.enabled = enabled; this.type = type; this.match = match;
        this.replace = replace; this.comment = comment;
    }

    public boolean appliesToRequest()  { return type.startsWith("request"); }
    public boolean isHeaderRule()      { return type.endsWith("header"); }

    public static void applyAll(List<MatchReplaceRule> rules, HttpMessage m, boolean isRequest) {
        if (m == null) return;
        for (MatchReplaceRule r : rules) {
            if (!r.enabled) continue;
            if (r.appliesToRequest() != isRequest) continue;
            r.apply(m);
        }
    }

    /** Run the WebSocket rules over one text message; returns the (possibly unchanged) text. */
    public static String applyAllWs(List<MatchReplaceRule> rules, String text, boolean toServer) {
        String out = text;
        for (MatchReplaceRule r : rules) {
            if (!r.enabled || r.match == null || r.match.isBlank()) continue;
            if (WS_TO_SERVER.equals(r.type) && !toServer) continue;
            if (WS_TO_CLIENT.equals(r.type) && toServer) continue;
            out = regexReplace(out, r.match, r.replace == null ? "" : r.replace, false);
        }
        return out;
    }

    private void apply(HttpMessage m) {
        if (isHeaderRule()) {
            String headers = m.headerText();
            // Preserve the trailing blank line that terminates the header block.
            String trimmed = headers.endsWith("\r\n\r\n")
                    ? headers.substring(0, headers.length() - 2) : headers;

            if (match == null || match.isBlank()) {
                if (replace != null && !replace.isBlank()) {
                    String ins = replace.endsWith("\r\n") ? replace : replace + "\r\n";
                    trimmed = trimmed.substring(0, trimmed.length() - 2) + ins + "\r\n";
                    m.setRaw(Bytes.concat(Bytes.bytes(trimmed), m.body()));
                }
                return;
            }
            String out = regexReplace(trimmed, match, replace == null ? "" : replace, true);
            // A rule with an empty replacement removes the matched header line.
            out = out.replaceAll("(\r\n)\r\n(?=.)", "$1");
            if (!out.endsWith("\r\n\r\n")) {
                if (out.endsWith("\r\n")) out = out + "\r\n";
                else out = out + "\r\n\r\n";
            }
            m.setRaw(Bytes.concat(Bytes.bytes(out), m.body()));
        } else {
            if (match == null || match.isBlank()) return;
            String body = m.bodyText();
            String out = regexReplace(body, match, replace == null ? "" : replace, false);
            m.setBody(Bytes.bytes(out));
        }
    }

    private static String regexReplace(String input, String pattern, String replacement, boolean multiline) {
        try {
            int flags = Pattern.CASE_INSENSITIVE | (multiline ? Pattern.MULTILINE : 0);
            return Pattern.compile(pattern, flags).matcher(input)
                    .replaceAll(Matcher.quoteReplacement(replacement));
        } catch (Exception e) {
            return input.replace(pattern, replacement);
        }
    }

    /** The rules the original shipped, all disabled by default. */
    public static java.util.List<MatchReplaceRule> defaults() {
        java.util.List<MatchReplaceRule> l = new java.util.ArrayList<>();
        l.add(new MatchReplaceRule(false, REQ_HEADER, "^User-Agent.*$",
                "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) OpenTRAP", "Emulate a desktop browser"));
        l.add(new MatchReplaceRule(false, REQ_HEADER, "^If-Modified-Since.*$", "", "Require non-cached response"));
        l.add(new MatchReplaceRule(false, REQ_HEADER, "^If-None-Match.*$", "", "Require non-cached response"));
        l.add(new MatchReplaceRule(false, REQ_HEADER, "^Referer.*$", "", "Hide Referer header"));
        l.add(new MatchReplaceRule(false, REQ_HEADER, "^Accept-Encoding.*$", "", "Require uncompressed responses"));
        l.add(new MatchReplaceRule(false, RESP_HEADER, "^Set-Cookie.*$", "", "Ignore cookies"));
        l.add(new MatchReplaceRule(false, RESP_HEADER, "^Strict-Transport-Security.*$", "", "Remove HSTS headers"));
        l.add(new MatchReplaceRule(false, RESP_HEADER, "", "X-Frame-Options: ALLOWALL", "Allow framing"));
        return l;
    }
}
