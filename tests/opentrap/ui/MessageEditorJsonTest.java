package opentrap.ui;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;
import opentrap.testkit.SmallStack;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests the JSON string-span walk that replaced the regex in colourJson. The
 * regex "(\\.|[^"\\])*" recursed one Pattern frame per character inside a
 * string, so a 100 KB+ value overflowed the AWT event queue's stack; these
 * cases pin the walk's behaviour and prove it survives that input.
 *
 * <p>The full colouring methods are also driven on {@link SmallStack}'s small
 * stack, so a regression that recurses per character fails here in a second
 * instead of crashing a user's app.
 */
public final class MessageEditorJsonTest {

    /** A body an order of magnitude past what killed the event queue before. */
    private static final int LARGE_VALUE_CHARS = 200_000;
    private static final int ONE_MB_CHARS = 1_000_000;
    private static final int ESCAPE_PAIRS = 500_000;

    private static String spansOf(String body) {
        StringBuilder sb = new StringBuilder();
        for (MessageEditor.Span s : MessageEditor.jsonStringSpans(body)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(s.start()).append('-').append(s.end());
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
            String body = "{\"k\":\"" + "x".repeat(ONE_MB_CHARS) + "\"}";
            var spans = MessageEditor.jsonStringSpans(body);
            Expect.isTrue(spans.size() == 2 && spans.get(1).start() == 5
                && spans.get(1).end() == body.length() - 1, "key span then one span covering the value");
        });

        Harness.test("alternating escapes walk without overflowing", () -> {
            String body = "\"" + "\\\\".repeat(ESCAPE_PAIRS) + "\"";
            var spans = MessageEditor.jsonStringSpans(body);
            Expect.isTrue(spans.size() == 1 && spans.get(0).end() == body.length(), "one closed span");
        });

        Harness.test("full json colouring holds on a small stack", () ->
            smallStackColour("{\"k\":\"" + "x".repeat(LARGE_VALUE_CHARS) + "\"}",
                (doc, b) -> MessageEditor.colourJson(doc, 0, b)));

        Harness.test("a huge digit run colours without overflowing", () ->
            smallStackColour("{\"n\":" + "7".repeat(LARGE_VALUE_CHARS) + "}",
                (doc, b) -> MessageEditor.colourJson(doc, 0, b)));

        Harness.test("a huge quoted attribute colours without overflowing", () ->
            smallStackColour("<a href=\"" + "u".repeat(LARGE_VALUE_CHARS) + "\">x</a>",
                (doc, b) -> MessageEditor.colourMarkup(doc, 0, b)));

        // -------- A/B against the retired implementation --------
        // Side A: the old regex under the same rig and the same payload must
        // FAIL. If it ever stops failing, this rig can no longer detect the
        // bug class and every B-side proof above is void.

        Harness.test("A/B control: the retired regex still overflows a small stack", () -> {
            String body = "{\"k\":\"" + "x".repeat(LARGE_VALUE_CHARS) + "\"}";
            Throwable escaped = SmallStack.probe(() -> oldRegexColourer(null, 0, body));
            Expect.isTrue(escaped instanceof StackOverflowError,
                    "the old regex must overflow here, got: " + escaped);
        });

        Harness.test("A/B containment: colourBody swallows the same overflow", () -> {
            // The identical failing colourer, driven through the guard the
            // render path uses, must not let the error escape to the caller
            // (the event thread, in the app).
            String body = "{\"k\":\"" + "x".repeat(LARGE_VALUE_CHARS) + "\"}";
            StyledDocument doc = new DefaultStyledDocument();
            try { doc.insertString(0, body, null); } catch (Exception e) { throw new RuntimeException(e); }
            Throwable escaped = SmallStack.probe(() -> MessageEditor.colourBody(doc, 0, body,
                    "application/json", MessageEditorJsonTest::oldRegexColourer));
            Expect.isTrue(escaped == null, "colourBody must contain the overflow, got: " + escaped);
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
        Throwable escaped = SmallStack.probe(() -> colour.accept(doc, body));
        Expect.isTrue(escaped == null, "nothing escaped colouring: " + escaped);
    }
}
