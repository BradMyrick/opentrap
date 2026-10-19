package opentrap.proxy;

import java.util.ArrayList;
import java.util.List;

/**
 * Command-line flags for the testing browser.
 *
 * <p>Shared by the browser the tester drives by hand and the headless browser
 * the crawler drives, so the two always see the application the same way. A
 * crawl that reached pages manual browsing could not, or the reverse, would make
 * the site map depend on which tool found a page.
 *
 * <p><b>What relaxing browser security does and does not do.</b> It does not
 * make the scanner find more weaknesses in a page it has already seen. Every
 * passive module reads the HTTP exchange at the proxy, so a missing
 * Content-Security-Policy, a cookie without Secure, or a permissive CORS header
 * is reported identically whether or not the browser would have enforced it.
 *
 * <p>What it does change is how much of the application is reached at all.
 * A browser enforcing its defaults silently refuses to issue requests, and a
 * request the browser never sends is traffic the proxy never sees and the
 * scanner never scans:
 *
 * <ul>
 *   <li>A single-page application whose API lives on another origin has its
 *       cross-origin reads blocked, so those endpoints never appear in the site
 *       map even though the crawler clicked everything that would call them.
 *   <li>An HTTPS page loading a plain HTTP sub-resource has that request blocked
 *       or silently upgraded, so the mixed content is never observed.
 *   <li>A request to a private network address from a public page is blocked
 *       outright.
 *   <li>Pop-ups the application opens are suppressed, so any route reachable
 *       only through one is never crawled.
 * </ul>
 *
 * <p>So this widens coverage rather than loosening detection, which is the
 * honest reason to turn it on.
 *
 * <p><b>Safety.</b> A browser started with these flags offers no protection to
 * the person using it, so it must only ever be the isolated testing profile
 * pointed at the target. It is never the tester's ordinary browser, and the
 * profile is kept separate precisely so the two cannot be confused.
 */
public final class BrowserFlags {

    private BrowserFlags() {}

    /** Settings key for the relaxed-security toggle. */
    public static final String RELAX_KEY = "browser.relaxSecurity";

    /**
     * Features disabled for every testing browser, relaxed or not, because they
     * change which scheme a navigation actually uses.
     *
     * <p>Recent Chromium silently upgrades an {@code http://} navigation to
     * {@code https://}. Against an HTTP-only target that upgrade fails and the
     * browser lands on its own {@code chrome-error://} page, so the site never
     * loads and the crawler sees nothing. A testing browser must reach exactly
     * the scheme it was pointed at, so the upgrade is always off here.
     */
    private static final String[] NAVIGATION_FEATURES = {
        "HttpsUpgrades",
        "HttpsFirstModeV2",
        "HttpsFirstBalancedMode",
        "HttpsFirstBalancedModeAutoEnable",
        // Just noise in a testing browser.
        "Translate",
    };

    /**
     * Chromium features that stop a request being issued at all. Disabling them
     * is what lets the proxy observe traffic the browser would otherwise refuse
     * to send.
     */
    private static final String[] BLOCKING_FEATURES = {
        // Blocks a public page from reaching a private or loopback address.
        "BlockInsecurePrivateNetworkRequests",
        "PrivateNetworkAccessSendPreflights",
        "PrivateNetworkAccessRespectPreflightResults",
        // Silently rewrites http sub-resources on an https page to https, which
        // hides the mixed content rather than letting it be observed.
        "AutoupgradeMixedContent",
        // Opaque responses never reach script, so cross-origin reads look empty.
        "CrossOriginOpenerPolicy",
        "CrossOriginEmbedderPolicy",
        // Opaque responses never reach script, so cross-origin reads look empty.
        "CrossOriginOpenerPolicy",
        "CrossOriginEmbedderPolicy",
    };

    /**
     * The flags to add for a testing browser.
     *
     * @param relaxSecurity whether to disable the browser's own enforcement
     */
    public static List<String> forTesting(boolean relaxSecurity) {
        List<String> out = new ArrayList<>();
        List<String> disableFeatures = new ArrayList<>(List.of(NAVIGATION_FEATURES));

        if (relaxSecurity) {
            // Permit cross-origin reads. Chromium only honours this when the
            // profile is a separate user-data-dir, which both launchers set.
            out.add("--disable-web-security");
            // Let an https page actually issue its http sub-resource requests,
            // so mixed content is observed instead of blocked.
            out.add("--allow-running-insecure-content");
            // Reach a target with a self-signed or otherwise broken certificate
            // that is not the proxy CA, which the SPKI pin alone would reject.
            out.add("--allow-insecure-localhost");
            // Routes that only exist behind window.open should be crawlable.
            out.add("--disable-popup-blocking");
            // Keeps cross-origin frames in one renderer so their requests are
            // seen and driven through the same debugging session.
            out.add("--disable-site-isolation-trials");
            disableFeatures.addAll(List.of(BLOCKING_FEATURES));
        }

        out.add("--disable-features=" + String.join(",", disableFeatures));
        return out;
    }

    /**
     * A short description of the state, for the status line and the alert log.
     */
    public static String describe(boolean relaxSecurity) {
        return relaxSecurity
                ? "browser security relaxed: cross-origin reads, mixed content and "
                  + "private network requests are allowed so the proxy can observe them"
                : "browser security left at the defaults";
    }
}
