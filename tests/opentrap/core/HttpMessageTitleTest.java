package opentrap.core;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests title extraction from HTML bodies. The old implementation ran a lazy
 * regex over the whole body, which recursed one Pattern frame per position
 * while hunting the closing tag - the same stack overflow the JSON colouring
 * had - and decoded the entire body to a string to do it.
 */
public final class HttpMessageTitleTest {

    private static HttpMessage html(String body) {
        String head = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n";
        return new HttpMessage(Bytes.bytes(head + body));
    }

    public void run() {
        Harness.suite("html title extraction");

        Harness.test("extracts and collapses the title", () -> {
            HttpMessage m = html("<html><head><TITLE id=\"t\">\n  Hello\n  there "
                    + "</TITLE></head><body>x</body></html>");
            Expect.equal(m.htmlTitle(), "Hello there", "title");
        });

        Harness.test("a body with no closing tag yields no title", () -> {
            StringBuilder b = new StringBuilder("<html><head><title>never closed");
            for (int i = 0; i < 200_000; i++) b.append('x');
            b.append("</head></html>");
            Expect.equal(html(b.toString()).htmlTitle(), "", "no title without a closing tag");
        });

        Harness.test("title hunting on a huge body holds on a small stack", () -> {
            // The old regex overflowed hunting the closing tag across a large
            // body; the scan cannot. Same rig as the colouring tests: any
            // per-position recursion blows a 256 KB stack within a few
            // thousand characters.
            StringBuilder b = new StringBuilder("<html><head><title>opened");
            for (int i = 0; i < 200_000; i++) b.append('x');
            HttpMessage m = html(b.toString());
            AtomicReference<Throwable> escaped = new AtomicReference<>();
            Thread probe = new Thread(null, m::htmlTitle, "title-stack-probe", 256L * 1024);
            probe.setUncaughtExceptionHandler((t, e) -> escaped.set(e));
            probe.start();
            try { probe.join(30_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            Expect.isTrue(!probe.isAlive(), "scan finished");
            Expect.isTrue(escaped.get() == null, "nothing escaped the scan: " + escaped.get());
        });

        Harness.test("a body with no title tag and a non-HTML body yield nothing", () -> {
            Expect.equal(html("<html><body>plain</body></html>").htmlTitle(), "", "no title tag");
            Expect.equal(new HttpMessage(Bytes.bytes(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{}")).htmlTitle(),
                    "", "non-HTML content type");
        });
    }
}
