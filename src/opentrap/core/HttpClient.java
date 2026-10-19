package opentrap.core;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Sends a request exactly as given and reads the response.
 *
 * Deliberately byte-exact: no header reordering, no silent Content-Length
 * correction, no normalisation. Callers that want those must ask.
 */
public class HttpClient {

    public static class Options {
        public int connectTimeoutMs = 15000;
        public int readTimeoutMs = 30000;
        public boolean updateContentLength = false;
        public boolean unpackGzip = true;
        public boolean followRedirects = false;
        public int maxRedirects = 5;
        public boolean keepAlive = false;
    }

    public static class Result {
        public HttpMessage response;
        public String remoteIp = "";
        public long millis;
        public String error = "";
        public int redirectsFollowed;
        public HttpService finalService;
        public HttpMessage finalRequest;
        public boolean ok() { return error.isEmpty() && response != null; }
    }

    private static SSLSocketFactory permissiveFactory;

    /** Testing tools must reach hosts with broken certificates, so trust all. */
    private static synchronized SSLSocketFactory sslFactory() throws Exception {
        if (permissiveFactory == null) {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{ new X509ExtendedTrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public void checkClientTrusted(X509Certificate[] c, String a, Socket s) {}
                public void checkServerTrusted(X509Certificate[] c, String a, Socket s) {}
                public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) {}
                public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            permissiveFactory = ctx.getSocketFactory();
        }
        return permissiveFactory;
    }

    public Result send(HttpService svc, HttpMessage request, Options opt) {
        Result r = new Result();
        HttpService cur = svc;
        HttpMessage req = request.copy();
        long start = System.currentTimeMillis();

        for (int hop = 0; ; hop++) {
            if (opt.updateContentLength && req.isRequest() && req.body().length > 0) {
                req.updateContentLength();
            }
            Result one = sendOnce(cur, req, opt);
            r.remoteIp = one.remoteIp;
            r.error = one.error;
            r.response = one.response;
            r.finalService = cur;
            r.finalRequest = req;
            if (!one.ok()) break;

            if (!opt.followRedirects || hop >= opt.maxRedirects) break;
            int sc = one.response.statusCode();
            String loc = one.response.header("Location");
            if (sc < 300 || sc > 399 || loc == null || loc.isBlank()) break;

            try {
                URI base = URI.create(cur.url() + req.path());
                URI next = base.resolve(loc.trim());
                boolean sec = "https".equalsIgnoreCase(next.getScheme());
                int port = next.getPort() == -1 ? (sec ? 443 : 80) : next.getPort();
                if (next.getHost() == null) break;
                cur = new HttpService(next.getHost(), port, sec);
                String target = (next.getRawPath() == null || next.getRawPath().isEmpty()) ? "/" : next.getRawPath();
                if (next.getRawQuery() != null) target += "?" + next.getRawQuery();
                HttpMessage nr = req.copy();
                if (sc == 303 || ((sc == 301 || sc == 302) && !nr.method().equalsIgnoreCase("GET"))) {
                    nr.setMethod("GET");
                    nr.setBody(new byte[0]);
                    nr.removeHeader("Content-Length");
                    nr.removeHeader("Content-Type");
                }
                nr.setRequestTarget(target);
                nr.setHeader("Host", cur.defaultPort() ? cur.host() : cur.host() + ":" + cur.port());
                req = nr;
                r.redirectsFollowed++;
            } catch (Exception e) {
                break;
            }
        }
        r.millis = System.currentTimeMillis() - start;
        return r;
    }

    private Result sendOnce(HttpService svc, HttpMessage request, Options opt) {
        Result r = new Result();
        Socket sock = null;
        try {
            if (svc.secure()) {
                SSLSocket s = (SSLSocket) sslFactory().createSocket();
                s.connect(new InetSocketAddress(svc.host(), svc.port()), opt.connectTimeoutMs);
                try {
                    SSLParameters p = s.getSSLParameters();
                    p.setServerNames(java.util.List.of(new SNIHostName(svc.host())));
                    s.setSSLParameters(p);
                } catch (Exception ignored) {}
                s.startHandshake();
                sock = s;
            } else {
                sock = new Socket();
                sock.connect(new InetSocketAddress(svc.host(), svc.port()), opt.connectTimeoutMs);
            }
            sock.setSoTimeout(opt.readTimeoutMs);
            try { r.remoteIp = ((InetSocketAddress) sock.getRemoteSocketAddress()).getAddress().getHostAddress(); }
            catch (Exception ignored) {}

            OutputStream out = sock.getOutputStream();
            out.write(request.raw());
            out.flush();

            byte[] raw = readResponse(sock.getInputStream(), request.method());
            if (raw.length == 0) { r.error = "empty response"; return r; }
            HttpMessage resp = new HttpMessage(raw);
            if (opt.unpackGzip) unpack(resp);
            r.response = resp;
            return r;
        } catch (UnknownHostException e) {
            r.error = "unknown host: " + svc.host();
        } catch (SocketTimeoutException e) {
            r.error = "timeout";
        } catch (ConnectException e) {
            r.error = "failed to connect to " + svc.host() + ":" + svc.port();
        } catch (SSLException e) {
            r.error = "TLS error: " + e.getMessage();
        } catch (Exception e) {
            r.error = String.valueOf(e.getMessage());
        } finally {
            if (sock != null) try { sock.close(); } catch (IOException ignored) {}
        }
        return r;
    }

    /** Read headers, then body framed by Content-Length, chunked, or close. */
    public static byte[] readResponse(InputStream in, String requestMethod) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int b = in.read();
            if (b < 0) break;
            head.write(b);
            if ((state == 0 || state == 2) && b == 13) state++;
            else if ((state == 1 || state == 3) && b == 10) state++;
            else if (b == 10) state = (state == 2) ? 4 : 2;
            else state = 0;
            if (head.size() > 512 * 1024) break;
        }
        byte[] headBytes = head.toByteArray();
        if (headBytes.length == 0) return new byte[0];

        HttpMessage probe = new HttpMessage(headBytes);
        int status = probe.statusCode();
        boolean noBody = "HEAD".equalsIgnoreCase(requestMethod)
                || status == 204 || status == 304 || (status >= 100 && status < 200);
        if (noBody) return headBytes;

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String te = probe.header("Transfer-Encoding");
        String cl = probe.header("Content-Length");

        if (te != null && te.toLowerCase(Locale.ROOT).contains("chunked")) {
            while (true) {
                String line = readLine(in);
                if (line == null) break;
                String sz = line.trim();
                int semi = sz.indexOf(';');
                if (semi >= 0) sz = sz.substring(0, semi);
                int n;
                try { n = Integer.parseInt(sz.trim(), 16); } catch (Exception e) { break; }
                if (n == 0) { readLine(in); break; }
                byte[] buf = new byte[n];
                int got = 0;
                while (got < n) {
                    int k = in.read(buf, got, n - got);
                    if (k < 0) break;
                    got += k;
                }
                body.write(buf, 0, got);
                readLine(in);
                if (got < n) break;
            }
            byte[] merged = Bytes.concat(headBytes, body.toByteArray());
            HttpMessage m = new HttpMessage(merged);
            m.removeHeader("Transfer-Encoding");
            m.setHeader("Content-Length", String.valueOf(body.size()));
            return m.raw();
        }

        if (cl != null) {
            int n;
            try { n = Integer.parseInt(cl.trim()); } catch (Exception e) { n = -1; }
            if (n >= 0) {
                byte[] buf = new byte[8192];
                int remaining = n;
                while (remaining > 0) {
                    int k = in.read(buf, 0, Math.min(buf.length, remaining));
                    if (k < 0) break;
                    body.write(buf, 0, k);
                    remaining -= k;
                }
                return Bytes.concat(headBytes, body.toByteArray());
            }
        }

        byte[] buf = new byte[8192];
        try {
            int k;
            while ((k = in.read(buf)) > 0) body.write(buf, 0, k);
        } catch (SocketTimeoutException ignored) {
        }
        return Bytes.concat(headBytes, body.toByteArray());
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == 10) break;
            b.write(c);
        }
        if (c < 0 && b.size() == 0) return null;
        return Bytes.str(b.toByteArray());
    }

    /** Replace a compressed body with its plaintext, dropping the header. */
    public static void unpack(HttpMessage m) {
        String ce = m.header("Content-Encoding");
        if (ce == null) return;
        String enc = ce.trim().toLowerCase(Locale.ROOT);
        byte[] body = m.body();
        if (body.length == 0) return;
        try {
            InputStream src = new ByteArrayInputStream(body);
            InputStream dec;
            if (enc.contains("gzip")) dec = new GZIPInputStream(src);
            else if (enc.contains("deflate")) dec = new InflaterInputStream(src);
            else if (enc.contains("br")) dec = new org.brotli.dec.BrotliInputStream(src);
            else return;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int k;
            while ((k = dec.read(buf)) > 0) out.write(buf, 0, k);
            m.removeHeader("Content-Encoding");
            m.setBody(out.toByteArray());
            m.setHeader("Content-Length", String.valueOf(out.size()));
        } catch (Exception ignored) {
        }
    }
}
