package opentrap.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Lifecycle for the bundled OWASP ZAP scan engine.
 *
 * <p>ZAP is run with no user interface, as a headless daemon child process on the
 * loopback. OpenTRAP never shows anything of ZAP; it finds the engine jar, starts
 * the daemon on a private port with a random API key, waits for it to answer, and
 * then drives it through {@link ZapApi}. The engine is kept warm for the session
 * and shut down on exit.
 *
 * <p>In a release build the trimmed engine is embedded in the application jar as
 * {@code /opentrap/engine/engine.zip}. On first use it is extracted once to
 * {@code ~/.opentrap/engine} and run from there, so a single jar is self-contained.
 * A development build with an {@code engine/} directory (or a system ZAP) is used
 * directly without extraction.
 */
public final class ZapEngine {

    private static final String BUNDLE = "/opentrap/engine/engine.zip";
    private static final String BUNDLE_VERSION = "/opentrap/engine/VERSION";

    private String jar;             // path to the engine jar; null until a bundled engine is extracted
    private final boolean bundled;  // engine.zip is embedded in this application jar
    private String base;            // http://127.0.0.1:<port>
    private String apiKey;
    private Process proc;
    private ZapApi api;

    private ZapEngine(String jar, boolean bundled) { this.jar = jar; this.bundled = bundled; }

    private static ZapEngine shared;
    private static boolean sharedResolved;

    /**
     * The one engine instance for the whole app. The dashboard (for scans) and
     * the passive-rules tab (for configuration) share it, so a rule change made
     * in the tab governs the scans, and one daemon serves both. Null if ZAP is
     * not installed.
     */
    public static synchronized ZapEngine shared() {
        if (!sharedResolved) { sharedResolved = true; shared = locate(); }
        return shared;
    }

    /** Find an engine on disk, or note the one embedded in this jar, or null. */
    public static ZapEngine locate() {
        for (String dir : candidateDirs()) {
            String j = findJar(dir);
            if (j != null) return new ZapEngine(j, false);
        }
        // Embedded in a release jar but not yet extracted: extract lazily on start.
        if (ZapEngine.class.getResource(BUNDLE) != null) return new ZapEngine(null, true);
        return null;
    }

    private static List<String> candidateDirs() {
        List<String> l = new ArrayList<>();
        l.add(extractDir().toString());       // a previously-extracted bundled engine
        String cwd = System.getProperty("user.dir", ".");
        l.add(cwd + "/engine");               // a dev build's fetched engine (fetch-zap.sh)
        l.add("/usr/share/zaproxy");          // a system install, as a fallback
        return l;
    }

    private static Path extractDir() {
        return Path.of(System.getProperty("user.home", "."), ".opentrap", "engine");
    }

    private static String findJar(String dir) {
        try {
            Path d = Path.of(dir);
            if (!Files.isDirectory(d)) return null;
            try (var s = Files.list(d)) {
                return s.map(p -> p.getFileName().toString())
                        .filter(n -> n.startsWith("zap-") && n.endsWith(".jar"))
                        .findFirst()
                        .map(n -> dir + "/" + n)
                        .orElse(null);
            }
        } catch (IOException e) {
            return null;
        }
    }

    public boolean available() { return jar != null || bundled; }
    public ZapApi api() { return api; }
    public String base() { return base; }

    /** Start the daemon if not already running, and block until its API answers. */
    public synchronized void start() throws Exception {
        if (api != null && responds()) return;

        // First run of a release jar: unpack the embedded engine to ~/.opentrap/engine.
        if (jar == null && bundled) jar = ensureExtracted();
        if (jar == null) throw new IOException("No scan engine available");

        int port = freePort();
        apiKey = Long.toHexString(new java.security.SecureRandom().nextLong());
        Path home = Files.createTempDirectory("opentrap-zap-");

        List<String> cmd = new ArrayList<>(List.of(
                javaBin(), "-jar", jar, "-daemon",
                "-host", "127.0.0.1", "-port", String.valueOf(port),
                "-config", "api.key=" + apiKey,
                "-config", "api.addrs.addr.name=127.0.0.1",
                "-config", "api.addrs.addr.regex=false",
                "-silent",
                "-dir", home.toString()));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        proc = pb.start();
        base = "http://127.0.0.1:" + port;
        api = new ZapApi(base, apiKey);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive()) throw new IOException("Scan engine exited during start-up");
            if (responds()) return;
            Thread.sleep(1500);
        }
        throw new IOException("Scan engine did not become ready in time");
    }

    private boolean responds() {
        try { return api.version() != null; } catch (Exception e) { return false; }
    }

    /** Attach to an already-running engine (used by diagnostics/tests). */
    public static ZapEngine attach(String base, String apiKey) {
        ZapEngine e = new ZapEngine("attached", false);
        e.base = base;
        e.apiKey = apiKey;
        e.api = new ZapApi(base, apiKey);
        return e;
    }

    /**
     * Unpack the embedded engine to {@code ~/.opentrap/engine} once, reusing an
     * existing extraction when its version marker matches the bundled version.
     * Returns the path to the engine jar.
     */
    private static synchronized String ensureExtracted() throws IOException {
        Path dir = extractDir();
        String want = readResource(BUNDLE_VERSION);
        String existing = findJar(dir.toString());
        if (existing != null && want != null && want.equals(readMarker(dir))) {
            return existing;   // already extracted and up to date
        }
        extractZip(dir);
        if (want != null) Files.writeString(dir.resolve(".version"), want);
        String j = findJar(dir.toString());
        if (j == null) throw new IOException("Engine extraction produced no jar");
        return j;
    }

    private static void extractZip(Path dest) throws IOException {
        Files.createDirectories(dest);
        try (InputStream in = ZapEngine.class.getResourceAsStream(BUNDLE);
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest)) continue;   // guard against zip-slip
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) { zis.transferTo(os); }
                }
            }
        }
    }

    private static String readMarker(Path dir) {
        try { return Files.readString(dir.resolve(".version")).trim(); }
        catch (IOException e) { return null; }
    }

    private static String readResource(String name) {
        try (InputStream in = ZapEngine.class.getResourceAsStream(name)) {
            return (in == null) ? null : new String(in.readAllBytes()).trim();
        } catch (IOException e) { return null; }
    }

    public synchronized void shutdown() {
        try { if (api != null) api.shutdown(); } catch (Exception ignore) { }
        if (proc != null) {
            proc.destroy();
            try { proc.waitFor(); } catch (InterruptedException ignore) { }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    }

    private static String javaBin() {
        String home = System.getProperty("java.home");
        return (home != null) ? home + "/bin/java" : "java";
    }
}
