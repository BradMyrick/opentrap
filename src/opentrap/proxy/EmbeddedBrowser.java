package opentrap.proxy;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches a Chromium-based browser preconfigured for the proxy.
 *
 * The browser runs in its own isolated profile, routes all traffic through the
 * proxy, and trusts the OpenTRAP CA through the SPKI-list flag, so there is no
 * certificate to install and no manual proxy setup. Only certificates under the
 * OpenTRAP CA are trusted, so this is safe.
 */
public final class EmbeddedBrowser {
    private EmbeddedBrowser() {}

    /** Candidate browser binaries, in order of preference. */
    private static final String[] CANDIDATES = {
        "/usr/bin/chromium",
        "/usr/bin/chromium-browser",
        "/usr/bin/google-chrome",
        "/usr/bin/google-chrome-stable",
        "/usr/bin/brave-browser",
        "/snap/bin/chromium"
    };

    private static Process running;

    /** The Chromium bundled in the product's browser/ folder, or null. */
    private static File bundledBrowser() {
        for (File root : productDirs()) {
            File[] cands = {
                new File(root, "browser/chrome-linux/chrome"),      // plain Chromium snapshot
                new File(root, "browser/chrome-linux64/chrome"),    // Chrome for Testing layout
                new File(root, "browser/chrome"),
            };
            for (File f : cands) if (f.canExecute()) return f;
        }
        return null;
    }

    /** Likely locations of the product directory (where dist/ and browser/ live). */
    private static java.util.List<File> productDirs() {
        java.util.List<File> dirs = new ArrayList<>();
        // Next to the running jar: <product>/dist/opentrap.jar -> <product>.
        try {
            File jar = new File(EmbeddedBrowser.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (jar.isFile()) {
                File d = jar.getParentFile();                 // dist/ or build/
                if (d != null) dirs.add(d.getParentFile());   // product root
                dirs.add(d);
            } else if (jar.isDirectory()) {
                dirs.add(jar);                                 // running from build/
                if (jar.getParentFile() != null) dirs.add(jar.getParentFile());
            }
        } catch (Exception ignored) {}
        dirs.add(new File(System.getProperty("user.home"), "opentrap"));
        dirs.add(new File(System.getProperty("user.dir")));
        return dirs;
    }

    /** True if a Chromium-family browser can be found. */
    public static boolean available() { return findBinary() != null; }

    public static String findBinary() {
        // Prefer the Chromium bundled with the product, so it works even when the
        // machine has no browser installed.
        File bundled = bundledBrowser();
        if (bundled != null) return bundled.getAbsolutePath();
        for (String c : CANDIDATES) {
            if (new File(c).canExecute()) return c;
        }
        // Last resort: rely on PATH.
        for (String name : new String[]{"chromium", "chromium-browser", "google-chrome"}) {
            try {
                Process p = new ProcessBuilder("which", name).start();
                if (p.waitFor() == 0) {
                    String out = new String(p.getInputStream().readAllBytes()).trim();
                    if (!out.isEmpty() && new File(out).canExecute()) return out;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Launch (or focus) the browser pointed at the proxy.
     *
     * @return null on success, or a human-readable error.
     */
    public static synchronized String launch(ProxyServer server, String startUrl) {
        return launch(server, startUrl, true);
    }

    /**
     * Launch the browser, optionally with its own security enforcement relaxed
     * so that requests it would otherwise refuse to send still reach the proxy.
     * See {@link BrowserFlags} for exactly what that changes.
     */
    public static synchronized String launch(ProxyServer server, String startUrl,
                                             boolean relaxSecurity) {
        String binary = findBinary();
        if (binary == null) {
            return "No Chromium-based browser found. Install chromium (apt install chromium).";
        }

        int port = runningPort(server);
        if (port < 0) {
            return "No proxy listener is running. Enable one on the proxy options tab first.";
        }

        String spki;
        try {
            spki = server.ca().spkiFingerprint();
        } catch (Exception e) {
            return "Could not read the CA fingerprint: " + e.getMessage();
        }

        Path profile = Path.of(System.getProperty("user.home"), ".opentrap", "browser");
        profile.toFile().mkdirs();
        clearCache(profile);

        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("--user-data-dir=" + profile);
        cmd.add("--proxy-server=http://127.0.0.1:" + port);
        // Do not send local addresses direct; everything goes through the proxy
        // except the loopback literal the browser needs for internal pages.
        cmd.add("--proxy-bypass-list=<-loopback>");
        cmd.add("--ignore-certificate-errors-spki-list=" + spki);
        // Suppresses Chromium's yellow "unsupported command-line flag" banner
        // that the SPKI flag otherwise triggers.
        cmd.add("--test-type");
        cmd.add("--no-first-run");
        cmd.add("--no-default-browser-check");
        cmd.add("--disable-background-networking");
        cmd.add("--disable-sync");
        cmd.add("--disable-component-update");
        // Force all traffic over TCP through the proxy. Without this, Chromium
        // switches to QUIC/HTTP3 (UDP) for sites advertising Alt-Svc h3 (e.g.
        // google, example.com) and bypasses the proxy entirely, so nothing is
        // captured or intercepted.
        cmd.add("--disable-quic");
        // A pentest browser should show all traffic, so don't let the cache hide
        // requests. Minimise the disk cache and clear any stale entries below.
        cmd.add("--disk-cache-size=1");
        cmd.addAll(BrowserFlags.forTesting(relaxSecurity));
        cmd.add("--new-window");
        cmd.add(startUrl == null || startUrl.isBlank() ? "http://opentrap/" : startUrl);

        try {
            running = new ProcessBuilder(cmd)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return null;
        } catch (Exception e) {
            return "Failed to launch the browser: " + e.getMessage();
        }
    }

    /** Remove cached responses so every navigation reaches the proxy. */
    private static void clearCache(Path profile) {
        String[] dirs = {
            "Default/Cache", "Default/Code Cache", "Default/GPUCache",
            "Default/Service Worker/CacheStorage", "Cache", "GPUCache",
        };
        for (String d : dirs) deleteTree(profile.resolve(d).toFile());
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    /** The port of the first running listener, or -1. */
    private static int runningPort(ProxyServer server) {
        for (ProxyListener l : server.listeners()) {
            if (l.running) return l.port;
        }
        return -1;
    }

    public static boolean isRunning() {
        return running != null && running.isAlive();
    }

    public static void shutdown() {
        if (running != null && running.isAlive()) running.destroy();
    }
}
