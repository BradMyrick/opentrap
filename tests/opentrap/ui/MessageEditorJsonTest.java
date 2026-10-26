package opentrap.ui;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests the JSON string-span walk that replaced the regex in colourJson. The
 * regex "(\\.|[^"\\])*" recursed one Pattern frame per character inside a
 * string, so a 100 KB+ value overflowed the AWT event queue's stack; these
 * cases pin the walk's behaviour and prove it survives that input.
 *
 * <p>The full colouring methods are also driven on a thread with a
 * deliberately small stack: the old bug blew a ~1 MB stack with a 100 KB
 * value, so a 256 KB stack makes any regression that recurses per character
 * fail here in a second instead of crashing a user's app.
 */
public final class MessageEditorJsonTest {

    private static String spansOf(String body) {
        StringBuilder sb = new StringBuilder();
        for (int[] s : MessageEditor.jsonStringSpans(body)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(s[0]).append('-').append(s[1]);
        }
        return sb.toString();
    }

    public void run() {
        Harness.suite("message editor json spans");

        Harness.test("pairs off keys and values", () -> {
            Expect.equal(spansOf("{\"a\":\"b\"}"), "1-4 5-8", "spans");
        });

        Harness.test("honours escaped quotes and backslashes", () -> {
            // "a\"b" then "c" - the escaped quote must not close the string
            Expect.equal(spansOf("\"a\\\"b\" \"c\""), "0-6 7-10", "spans");
        });

        Harness.test("unterminated final string yields nothing after it", () -> {
            // "ok" and "x" close; the value string at the end never does
            Expect.equal(spansOf("{\"ok\":1,\"x\":\"never"), "1-5 8-11", "spans");
        });

        Harness.test("a 1 MB string value walks without overflowing", () -> {
            StringBuilder body = new StringBuilder("{\"k\":\"");
            for (int i = 0; i < 1_000_000; i++) body.append('x');
            body.append("\"}");
            int[][] spans = MessageEditor.jsonStringSpans(body.toString());
            Expect.isTrue(spans.length == 2 && spans[1][0] == 5
                && spans[1][1] == body.length() - 1, "key span then one span covering the value");
        });

        Harness.test("alternating escapes walk without overflowing", () -> {
            StringBuilder body = new StringBuilder("\"");
            for (int i = 0; i < 500_000; i++) body.append("\\\\");
            body.append('"');
            int[][] spans = MessageEditor.jsonStringSpans(body.toString());
            Expect.isTrue(spans.length == 1 && spans[0][1] == body.length(), "one closed span");
        });

        Harness.test("full json colouring holds on a small stack", () -> {
            StringBuilder body = new StringBuilder("{\"k\":\"");
            for (int i = 0; i < 200_000; i++) body.append('x');
            body.append("\"}");
            smallStackColour(body.toString(), (doc, b) -> MessageEditor.colourJson(doc, 0, b));
        });

        Harness.test("a huge digit run colours without overflowing", () -> {
            StringBuilder body = new StringBuilder("{\"n\":");
            for (int i = 0; i < 200_000; i++) body.append('7');
            body.append('}');
            smallStackColour(body.toString(), (doc, b) -> MessageEditor.colourJson(doc, 0, b));
        });

        Harness.test("a huge quoted attribute colours without overflowing", () -> {
            StringBuilder body = new StringBuilder("<a href=\"");
            for (int i = 0; i < 200_000; i++) body.append('u');
            body.append("\">x</a>");
            smallStackColour(body.toString(), (doc, b) -> MessageEditor.colourMarkup(doc, 0, b));
        });

        // -------- A/B against the retired implementation --------
        // Side A: the old regex under the same rig and the same payload must
        // FAIL. If it ever stops failing, this rig can no longer detect the
        // bug class and every B-side proof above is void.

        Harness.test("A/B control: the retired regex still overflows a small stack", () -> {
            StringBuilder body = new StringBuilder("{\"k\":\"");
            for (int i = 0; i < 200_000; i++) body.append('x');
            body.append("\"}");
            AtomicReference<Throwable> escaped = new AtomicReference<>();
            runOnSmallStack(() -> oldRegexColourer(null, 0, body.toString()), escaped);
            Expect.isTrue(escaped.get() instanceof StackOverflowError,
                    "the old regex must overflow here, got: " + escaped.get());
        });

        Harness.test("A/B containment: colourBody swallows the same overflow", () -> {
            // The identical failing colourer, driven through the guard the
            // render path uses, must not let the error escape to the caller
            // (the event thread, in the app).
            StringBuilder body = new StringBuilder("{\"k\":\"");
            for (int i = 0; i < 200_000; i++) body.append('x');
            body.append("\"}");
            StyledDocument doc = new DefaultStyledDocument();
            try { doc.insertString(0, body.toString(), null); } catch (Exception e) { throw new RuntimeException(e); }
            AtomicReference<Throwable> escaped = new AtomicReference<>();
            runOnSmallStack(() -> MessageEditor.colourBody(doc, 0, body.toString(),
                    "application/json", MessageEditorJsonTest::oldRegexColourer), escaped);
            Expect.isTrue(escaped.get() == null,
                    "colourBody must contain the overflow, got: " + escaped.get());
        });
    }

    /**
     * The retired colourer, kept as the A-side negative control: the exact
     * string regex that overflowed the AWT event queue's stack, matching the
     * pre-fix implementation. Styling is omitted - the overflow happens in
     * the matcher itself.
     */
    private static void oldRegexColourer(StyledDocument doc, int base, String body) {
        Matcher str = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"").matcher(body);
        while (str.find()) { /* styling is irrelevant to the overflow */ }
    }

    /**
     * Colour {@code body} through a real colourer on a small stack and fail
     * if anything escapes it.
     */
    private static void smallStackColour(String body,
            java.util.function.BiConsumer<StyledDocument, String> colour) {
        StyledDocument doc = new DefaultStyledDocument();
        try { doc.insertString(0, body, null); } catch (Exception e) { throw new RuntimeException(e); }
        AtomicReference<Throwable> escaped = new AtomicReference<>();
        runOnSmallStack(() -> colour.accept(doc, body), escaped);
        Expect.isTrue(escaped.get() == null, "nothing escaped colouring: " + escaped.get());
    }

    /**
     * Run {@code r} on a thread with a 256 KB stack and record anything that
     * escapes it. A per-character recursion blows such a stack within a few
     * thousand characters, so these bodies - an order of magnitude past what
     * killed the event queue before - prove colouring is flat.
     */
    private static void runOnSmallStack(Runnable r, AtomicReference<Throwable> escaped) {
        Thread probe = new Thread(null, r, "colour-stack-probe", 256L * 1024);
        probe.setUncaughtExceptionHandler((t, e) -> escaped.set(e));
        probe.start();
        try { probe.join(30_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Expect.isTrue(!probe.isAlive(), "probe finished");
    }
}
