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

        Harness.test("the status line names the address and port that is listening", () -> {
            ProxyListener l = new ProxyListener();
            Expect.equal(ProxyServer.summary(java.util.List.of(l)),
                    "listening on 127.0.0.1:8080", "status line");
        });

        Harness.test("the status line lists every running listener and skips stopped ones", () -> {
            ProxyListener a = new ProxyListener(8080);
            ProxyListener b = new ProxyListener(8081);
            b.loopbackOnly = false;
            ProxyListener c = new ProxyListener(9000);
            c.running = false;
            Expect.equal(ProxyServer.summary(java.util.List.of(a, b, c)),
                    "listening on 127.0.0.1:8080, 0.0.0.0:8081", "status line");
        });

        Harness.test("the status line says plainly when nothing is listening", () -> {
            ProxyListener l = new ProxyListener();
            l.running = false;
            Expect.equal(ProxyServer.summary(java.util.List.of(l)),
                    "no proxy listeners are running - add one on the options tab", "status line");
            Expect.equal(ProxyServer.summary(java.util.List.of()),
                    "no proxy listeners are running - add one on the options tab", "status line");
        });
    }
}
