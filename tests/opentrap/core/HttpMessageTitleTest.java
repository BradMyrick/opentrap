package opentrap.core;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;
import opentrap.testkit.SmallStack;

/**
 * Tests title extraction from HTML bodies. The old implementation ran a lazy
 * regex over the whole body, which recursed one Pattern frame per position
 * while hunting the closing tag - the same stack overflow the JSON colouring
 * had - and decoded the entire body to a string to do it.
 */
public final class HttpMessageTitleTest {

    /** A body an order of magnitude past what could overflow a ~1 MB stack. */
    private static final int LARGE_BODY_CHARS = 200_000;

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
            String body = "<html><head><title>never closed" + "x".repeat(LARGE_BODY_CHARS)
                    + "</head></html>";
            Expect.equal(html(body).htmlTitle(), "", "no title without a closing tag");
        });

        Harness.test("title hunting on a huge body holds on a small stack", () -> {
            // The old regex overflowed hunting the closing tag across a large
            // body; the scan cannot.
            HttpMessage m = html("<html><head><title>opened" + "x".repeat(LARGE_BODY_CHARS));
            Throwable escaped = SmallStack.probe(m::htmlTitle);
            Expect.isTrue(escaped == null, "nothing escaped the scan: " + escaped);
        });

        Harness.test("a body with no title tag and a non-HTML body yield nothing", () -> {
            Expect.equal(html("<html><body>plain</body></html>").htmlTitle(), "", "no title tag");
            Expect.equal(new HttpMessage(Bytes.bytes(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{}")).htmlTitle(),
                    "", "non-HTML content type");
        });
    }
}
