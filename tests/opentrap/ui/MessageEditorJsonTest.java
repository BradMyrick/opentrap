package opentrap.ui;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

/**
 * Tests the JSON string-span walk that replaced the regex in colourJson. The
 * regex "(\\.|[^"\\])*" recursed one Pattern frame per character inside a
 * string, so a 100 KB+ value overflowed the AWT event queue's stack; these
 * cases pin the walk's behaviour and prove it survives that input.
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
    }
}
