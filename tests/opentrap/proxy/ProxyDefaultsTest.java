package opentrap.proxy;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

/**
 * The proxy must come up on the conventional port the moment the suite starts,
 * so a browser pointed at 127.0.0.1:8080 works without visiting the options tab.
 */
public final class ProxyDefaultsTest {

    public void run() {
        Harness.suite("proxy defaults");

        Harness.test("a new listener is on port 8080 and set to run", () -> {
            ProxyListener l = new ProxyListener();
            Expect.equal(l.port, 8080, "default listener port");
            Expect.isTrue(l.running, "listener enabled by default");
        });

        Harness.test("the startup listener uses 8080 when no port is configured", () -> {
            System.clearProperty("opentrap.port");
            Expect.equal(ProxyServer.defaultPort(), 8080, "startup port");
        });

        Harness.test("the opentrap.port property overrides the default", () -> {
            System.setProperty("opentrap.port", "9090");
            try { Expect.equal(ProxyServer.defaultPort(), 9090, "startup port"); }
            finally { System.clearProperty("opentrap.port"); }
        });

        Harness.test("an invalid opentrap.port falls back to 8080", () -> {
            System.setProperty("opentrap.port", "not-a-port");
            try { Expect.equal(ProxyServer.defaultPort(), 8080, "startup port"); }
            finally { System.clearProperty("opentrap.port"); }
        });
    }
}
