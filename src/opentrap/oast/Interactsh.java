package opentrap.oast;

import opentrap.spider.Cdp;   // reuse the tiny JSON parser

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * A client for an interactsh out-of-band collaborator server.
 *
 * <p>This is the same protocol Nuclei uses. We register an RSA public key and a
 * correlation id with the server, mint payload hostnames under that id, and
 * poll for any DNS, HTTP or SMTP callbacks a target made to them. The server
 * returns interactions encrypted to our public key, so even a public server
 * cannot read the contents of what a target sent back.
 *
 * <p>Server choice is the tester's: a public instance such as {@code oast.pro}
 * is the default and needs nothing set up, or a self-hosted instance can be
 * named when an engagement forbids any target data leaving through a third
 * party. The protocol is identical either way.
 *
 * <p>The crypto has to match the server exactly or nothing decrypts: RSA-OAEP
 * with SHA-256 for both the digest and the mask (Java defaults the mask to
 * SHA-1, so it is set explicitly), and AES-CFB for the data.
 */
public final class Interactsh {

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CORRELATION_LEN = 20;
    private static final int NONCE_LEN = 13;

    private final String serverUrl;        // e.g. https://oast.pro
    private final String serverHost;       // e.g. oast.pro
    private final String token;            // optional auth token, may be empty
    private final String correlationId;
    private final String secret;
    private final KeyPair keyPair;
    private final SecureRandom rng = new SecureRandom();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    private volatile boolean registered;

    public Interactsh(String serverUrl, String token) throws Exception {
        String s = (serverUrl == null || serverUrl.isBlank()) ? "https://oast.pro" : serverUrl.trim();
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        this.serverUrl = s;
        this.serverHost = URI.create(s).getHost();
        this.token = token == null ? "" : token.trim();
        this.correlationId = random(CORRELATION_LEN);
        this.secret = UUID.randomUUID().toString();
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        this.keyPair = g.generateKeyPair();
    }

    public String serverHost()    { return serverHost; }
    public String serverUrl()     { return serverUrl; }
    public String correlationId() { return correlationId; }
    public boolean isRegistered() { return registered; }

    // ---- registration ------------------------------------------------------

    /** Register our public key with the server. Must succeed before polling. */
    public void register() throws Exception {
        String pubPem = publicKeyPem();
        String body = "{"
                + "\"public-key\":\"" + Base64.getEncoder().encodeToString(pubPem.getBytes()) + "\","
                + "\"secret-key\":\"" + secret + "\","
                + "\"correlation-id\":\"" + correlationId + "\""
                + "}";
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(serverUrl + "/register"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!token.isEmpty()) b.header("Authorization", token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("registration failed: HTTP " + r.statusCode()
                    + " " + r.body());
        }
        registered = true;
    }

    private String publicKeyPem() {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    // ---- payloads -----------------------------------------------------------

    /**
     * A fresh payload hostname. The correlation id prefix lets the server route
     * the interaction to us; the random nonce suffix lets a check recognise its
     * own callback. Returns the bare host, so a check can build a URL, a JNDI
     * string or anything else from it.
     */
    public String newPayloadHost() {
        return correlationId + random(NONCE_LEN) + "." + serverHost;
    }

    /** A convenience http URL for the payload host. */
    public String newPayloadUrl() {
        return "http://" + newPayloadHost() + "/";
    }

    // ---- polling ------------------------------------------------------------

    /** Poll for new interactions, decrypt them, and return them. */
    public List<Interaction> poll() throws Exception {
        List<Interaction> out = new ArrayList<>();
        if (!registered) return out;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(
                serverUrl + "/poll?id=" + correlationId + "&secret=" + secret))
                .timeout(Duration.ofSeconds(15)).GET();
        if (!token.isEmpty()) b.header("Authorization", token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) return out;

        Cdp.JsonValue root = Cdp.JsonValue.parse(r.body());
        Cdp.JsonValue aesKeyV = root.get("aes_key");
        Cdp.JsonValue dataV = root.get("data");
        if (aesKeyV == null || !aesKeyV.isString() || dataV == null || !dataV.isArray()) return out;

        byte[] aesKey = rsaOaepDecrypt(Base64.getDecoder().decode(aesKeyV.asString()));
        for (Cdp.JsonValue item : dataV.asArray()) {
            if (!item.isString()) continue;
            try {
                byte[] blob = Base64.getDecoder().decode(item.asString());
                String json = aesDecrypt(aesKey, blob);
                Interaction in = parseInteraction(json);
                if (in != null) out.add(in);
            } catch (Exception ignore) {
                // one bad record must not drop the rest
            }
        }
        return out;
    }

    private static Interaction parseInteraction(String json) {
        Cdp.JsonValue v = Cdp.JsonValue.parse(json);
        if (v == null || !v.isObject()) return null;
        return new Interaction(
                str(v, "protocol"),
                firstNonEmpty(str(v, "full-id"), str(v, "unique-id")),
                str(v, "remote-address"),
                str(v, "timestamp"),
                str(v, "raw-request"),
                str(v, "raw-response"),
                str(v, "q-type"));
    }

    private static String str(Cdp.JsonValue o, String key) {
        Cdp.JsonValue v = o.get(key);
        return v != null && v.isString() ? v.asString() : "";
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    // ---- crypto -------------------------------------------------------------

    private byte[] rsaOaepDecrypt(byte[] ciphertext) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding");
        // The server uses SHA-256 for both the OAEP digest and the MGF1 mask.
        // Java's default mask is SHA-1, which would fail to decrypt, so set both.
        OAEPParameterSpec spec = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        c.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), spec);
        return c.doFinal(ciphertext);
    }

    private static String aesDecrypt(byte[] key, byte[] ivAndData) throws Exception {
        byte[] iv = new byte[16];
        System.arraycopy(ivAndData, 0, iv, 0, 16);
        byte[] ct = new byte[ivAndData.length - 16];
        System.arraycopy(ivAndData, 16, ct, 0, ct.length);
        Cipher c = Cipher.getInstance("AES/CTR/NoPadding");   // matches the server
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return new String(c.doFinal(ct));
    }

    private String random(int len) {
        StringBuilder sb = new StringBuilder(len);
        // First character a letter, so the label is always a valid hostname.
        sb.append(CHARS.charAt(rng.nextInt(26)));
        for (int i = 1; i < len; i++) sb.append(CHARS.charAt(rng.nextInt(CHARS.length())));
        return sb.toString();
    }
}
