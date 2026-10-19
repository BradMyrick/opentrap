package opentrap.core;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/** One request/response exchange, as recorded by any tool. */
public class LogEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private final int number;
    private final String tool;
    private final HttpService service;
    private HttpMessage request;
    private HttpMessage response;
    private HttpMessage originalRequest;
    private HttpMessage originalResponse;
    private final Date time;
    private String remoteIp = "";
    private boolean edited;
    private String notes = "";
    private String highlight = "";
    private long durationMillis = -1;
    private String error = "";

    public LogEntry(String tool, HttpService service, HttpMessage request) {
        this.number = SEQ.incrementAndGet();
        this.tool = tool;
        this.service = service;
        this.request = request;
        this.time = new Date();
    }

    public static void resetCounter() { SEQ.set(0); }
    public static int peekCounter()   { return SEQ.get(); }
    public static void setCounter(int v) { SEQ.set(v); }

    public int number()              { return number; }
    public String tool()             { return tool; }
    public HttpService service()     { return service; }
    public HttpMessage request()     { return request; }
    public HttpMessage response()    { return response; }
    public HttpMessage originalRequest() { return originalRequest; }
    /**
     * The response as the server sent it, before any proxy response
     * modification. Null when nothing was modified. The scanner audits this in
     * preference to {@link #response()}, so that stripping a header to build a
     * proof of concept never turns into a false "header missing" finding.
     */
    public HttpMessage originalResponse() { return originalResponse; }
    /** The response to scan: the server's original when present, else the current. */
    public HttpMessage responseForScanning() {
        return originalResponse != null ? originalResponse : response;
    }
    public Date time()               { return time; }
    public String remoteIp()         { return remoteIp; }
    public boolean edited()          { return edited; }
    public String notes()            { return notes; }
    public String highlight()        { return highlight; }
    public long durationMillis()     { return durationMillis; }
    public String error()            { return error; }

    public void setRequest(HttpMessage m)  { this.request = m; }
    public void setResponse(HttpMessage m) { this.response = m; }
    public void setOriginalRequest(HttpMessage m) { this.originalRequest = m; }
    public void setOriginalResponse(HttpMessage m) { this.originalResponse = m; }
    public void setRemoteIp(String s)      { this.remoteIp = s == null ? "" : s; }
    public void setEdited(boolean b)       { this.edited = b; }
    public void setNotes(String s)         { this.notes = s == null ? "" : s; }
    public void setHighlight(String s)     { this.highlight = s == null ? "" : s; }
    public void setDurationMillis(long v)  { this.durationMillis = v; }
    public void setError(String s)         { this.error = s == null ? "" : s; }

    /** Absolute URL of the request. */
    public String url() {
        if (request == null) return service.url();
        String t = request.requestTarget();
        if (t.startsWith("http://") || t.startsWith("https://")) return t;
        return service.url() + (t.startsWith("/") ? t : "/" + t);
    }

    public String pathAndQuery() {
        return request == null ? "/" : request.requestTarget();
    }

    public int status()      { return response == null ? 0 : response.statusCode(); }
    public int responseLen() { return response == null ? 0 : response.length(); }
    public String mimeType() { return response == null ? "" : response.mimeType(); }
    public String title()    { return response == null ? "" : response.htmlTitle(); }

    public String cookies() {
        if (response == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String l : response.headerLines()) {
            if (l.toLowerCase().startsWith("set-cookie:")) {
                String v = l.substring("set-cookie:".length()).trim();
                int eq = v.indexOf('=');
                if (eq > 0) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(v, 0, eq);
                }
            }
        }
        return sb.toString();
    }
}
