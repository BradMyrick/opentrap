package opentrap.proxy;

import opentrap.core.HttpMessage;
import opentrap.core.HttpService;
import opentrap.core.Scope;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** One row of an interception rule chain. */
public class ProxyRule implements Serializable {
    private static final long serialVersionUID = 1L;

    public boolean enabled = true;
    public String booleanOp = "or";     // and | or
    public String matchItem = "URL";
    public String relationship = "matches";
    public String condition = "";

    public ProxyRule() {}

    public ProxyRule(boolean enabled, String op, String item, String rel, String cond) {
        this.enabled = enabled; this.booleanOp = op; this.matchItem = item;
        this.relationship = rel; this.condition = cond;
    }

    public static final String[] REQUEST_ITEMS = {
        "domain name", "ip address", "protocol", "HTTP method", "URL",
        "file extension", "request", "cookie", "any header", "body",
        "param name", "param value"
    };

    public static final String[] RESPONSE_ITEMS = {
        "domain name", "ip address", "protocol", "HTTP method", "URL",
        "file extension", "request", "cookie", "any header", "body",
        "param name", "param value", "response code", "content type", "page title"
    };

    public static String[] relationshipsFor(String item, boolean response) {
        switch (item) {
            case "ip address": return new String[]{"is in range", "is not in range"};
            case "protocol":   return new String[]{"is http", "is https"};
            case "URL":        return new String[]{"matches", "does not match", "is in target scope"};
            case "request":
                return response
                    ? new String[]{"contains parameters", "does not contain parameters",
                                   "was intercepted", "was not intercepted",
                                   "was modified", "was not modified"}
                    : new String[]{"contains parameters", "does not contain parameters"};
            case "cookie": case "any header":
                return response
                    ? new String[]{"in request matches", "in request does not match",
                                   "in response matches", "in response does not match"}
                    : new String[]{"matches", "does not match"};
            case "body":
                return response
                    ? new String[]{"of request matches", "of request does not match",
                                   "of response matches", "of response does not match"}
                    : new String[]{"matches", "does not match"};
            default: return new String[]{"matches", "does not match"};
        }
    }

    public static boolean takesCondition(String item) {
        return !"protocol".equals(item) && !"request".equals(item);
    }

    /** Evaluate this single rule. */
    public boolean test(HttpService svc, HttpMessage request, HttpMessage response,
                        String remoteIp, boolean wasIntercepted, boolean wasModified, Scope scope) {
        boolean negate = relationship.contains("does not") || relationship.startsWith("is not")
                      || relationship.startsWith("was not");
        boolean result = evaluate(svc, request, response, remoteIp, wasIntercepted, wasModified, scope);
        return negate != result;
    }

    private boolean evaluate(HttpService svc, HttpMessage request, HttpMessage response,
                             String remoteIp, boolean wasIntercepted, boolean wasModified, Scope scope) {
        switch (matchItem) {
            case "domain name":   return rx(svc == null ? "" : svc.host());
            case "ip address":    return ipInRange(remoteIp, condition);
            case "protocol":      return svc != null && svc.secure() == relationship.endsWith("https");
            case "HTTP method":   return rx(request == null ? "" : request.method());
            case "URL":
                if ("is in target scope".equals(relationship)) {
                    return scope != null && request != null && scope.isInScope(svc, request.path());
                }
                return rx(request == null ? "" : request.requestTarget());
            case "file extension": return rx(request == null ? "" : request.fileExtension());
            case "request":
                if (relationship.startsWith("was intercepted") || relationship.startsWith("was not intercepted"))
                    return wasIntercepted;
                if (relationship.startsWith("was modified") || relationship.startsWith("was not modified"))
                    return wasModified;
                return request != null && request.hasParameters();
            case "cookie": {
                HttpMessage m = relationship.startsWith("in response") ? response : request;
                if (m == null) return false;
                String h = relationship.startsWith("in response")
                        ? joinHeaders(m, "set-cookie") : String.valueOf(m.header("Cookie"));
                return rx(h);
            }
            case "any header": {
                HttpMessage m = relationship.startsWith("in response") ? response : request;
                if (m == null) return false;
                return rx(String.join("\n", m.headerLines()));
            }
            case "body": {
                HttpMessage m = relationship.startsWith("of response") ? response : request;
                return m != null && rx(m.bodyText());
            }
            case "param name": {
                if (request == null) return false;
                for (var p : request.parameters()) if (rx(p.name())) return true;
                return false;
            }
            case "param value": {
                if (request == null) return false;
                for (var p : request.parameters()) if (rx(p.value())) return true;
                return false;
            }
            case "response code": return response != null && rx(String.valueOf(response.statusCode()));
            case "content type":  return response != null && rx(response.contentType());
            case "page title":    return response != null && rx(response.htmlTitle());
            default: return false;
        }
    }

    private static String joinHeaders(HttpMessage m, String lower) {
        StringBuilder sb = new StringBuilder();
        for (String l : m.headerLines()) {
            if (l.toLowerCase(Locale.ROOT).startsWith(lower)) sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private boolean rx(String value) {
        if (condition == null || condition.isBlank()) return true;
        if (value == null) value = "";
        try {
            return Pattern.compile(condition, Pattern.CASE_INSENSITIVE).matcher(value).find();
        } catch (Exception e) {
            return value.toLowerCase(Locale.ROOT).contains(condition.toLowerCase(Locale.ROOT));
        }
    }

    private static boolean ipInRange(String ip, String range) {
        if (ip == null || ip.isBlank() || range == null || range.isBlank()) return false;
        if (range.contains("-")) {
            String[] parts = range.split("-", 2);
            return cmpIp(ip, parts[0].trim()) >= 0 && cmpIp(ip, parts[1].trim()) <= 0;
        }
        return ip.startsWith(range.trim());
    }

    private static int cmpIp(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            try {
                int c = Integer.compare(Integer.parseInt(x[i]), Integer.parseInt(y[i]));
                if (c != 0) return c;
            } catch (Exception e) { return 0; }
        }
        return 0;
    }

    /** Apply a whole chain, honouring each row's and/or operator. */
    public static boolean evaluateChain(List<ProxyRule> rules, HttpService svc,
                                        HttpMessage request, HttpMessage response,
                                        String remoteIp, boolean wasIntercepted,
                                        boolean wasModified, Scope scope) {
        Boolean acc = null;
        for (ProxyRule r : rules) {
            if (!r.enabled) continue;
            boolean v = r.test(svc, request, response, remoteIp, wasIntercepted, wasModified, scope);
            if (acc == null) acc = v;
            else if ("and".equalsIgnoreCase(r.booleanOp)) acc = acc && v;
            else acc = acc || v;
        }
        return acc != null && acc;
    }
}
