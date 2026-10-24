package opentrap.engine;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saved scan configurations round-trip through the library exactly, and names
 * are safe to use as file names. Runs against a throwaway home directory.
 */
public final class ScanConfigStoreTest {

    /** The store throws IOException; the harness takes a plain Runnable. */
    private static void save(String name, ScanConfig c) {
        try { ScanConfigStore.save(name, c); } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    private static ScanConfig load(String name) {
        try { return ScanConfigStore.load(name); } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    public void run() {
        Harness.suite("scan configuration library");

        String home = System.getProperty("user.home");
        Path tmp;
        try { tmp = Files.createTempDirectory("opentrap-configs"); }
        catch (Exception e) { throw new RuntimeException(e); }
        System.setProperty("user.home", tmp.toString());
        try {
            Harness.test("a saved configuration loads back with every value intact", () -> {
                ScanConfig c = new ScanConfig("mine", 7, 15, false, true, 20, 4, 300,
                        "HIGH", "LOW", ScanConfig.INJECT_QUERY | ScanConfig.INJECT_COOKIE,
                        ScanConfig.RPC_JSON, 250, 90);
                save("Night run", c);
                ScanConfig back = load("Night run");
                Expect.equal(back.name(), "Night run", "name is the library name");
                Expect.equal(back.crawlDepth(), 7, "crawl depth");
                Expect.equal(back.crawlDurationMin(), 15, "crawl duration");
                Expect.isTrue(!back.submitForms(), "submit forms off");
                Expect.isTrue(back.ajaxSpider(), "ajax on");
                Expect.equal(back.ajaxMaxDurationMin(), 20, "ajax duration");
                Expect.equal(back.ajaxMaxDepth(), 4, "ajax depth");
                Expect.equal(back.ajaxMaxStates(), 300, "ajax states");
                Expect.equal(back.attackStrength(), "HIGH", "strength");
                Expect.equal(back.alertThreshold(), "LOW", "threshold");
                Expect.equal(back.injectableMask(), ScanConfig.INJECT_QUERY | ScanConfig.INJECT_COOKIE, "injection");
                Expect.equal(back.rpcMask(), ScanConfig.RPC_JSON, "body types");
                Expect.equal(back.delayMs(), 250, "delay");
                Expect.equal(back.scanDurationMin(), 90, "scan duration");
            });

            Harness.test("names list sorted, exist, and delete", () -> {
                save("beta", ScanConfig.preset("Fast"));
                save("alpha", ScanConfig.preset("Deep"));
                Expect.isTrue(ScanConfigStore.names().indexOf("alpha") < ScanConfigStore.names().indexOf("beta"),
                        "sorted by name");
                Expect.isTrue(ScanConfigStore.exists("alpha"), "exists");
                Expect.isTrue(ScanConfigStore.delete("alpha"), "deleted");
                Expect.isTrue(!ScanConfigStore.exists("alpha"), "gone");
            });

            Harness.test("a name with path characters cannot escape the library directory", () -> {
                save("../escape/me", ScanConfig.preset("Fast"));
                Expect.isTrue(!Files.exists(tmp.resolve("escape")), "nothing written outside the library");
                Expect.isTrue(ScanConfigStore.exists("../escape/me"), "still retrievable under its own name");
            });
        } finally {
            System.setProperty("user.home", home);
        }
    }
}
