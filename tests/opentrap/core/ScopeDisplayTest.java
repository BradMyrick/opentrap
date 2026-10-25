package opentrap.core;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

/**
 * Scope rules created from a target store the host and path as an anchored,
 * literal-quoted regex ({@code ^\Qexample.com\E$}). Scope.display turns that
 * back into plain text so URL fields and spider defaults read "example.com",
 * not "\Qexample.com\E".
 */
public final class ScopeDisplayTest {

    public void run() {
        Harness.suite("scope display");

        Harness.test("an auto-created host rule displays as plain text", () -> {
            Scope scope = new Scope();
            scope.includeHost(new HttpService("testaspnet.vulnweb.com", 443, true));
            Scope.Rule r = scope.include().get(scope.include().size() - 1);
            Expect.equal(r.describe(), "testaspnet.vulnweb.com", "host reads cleanly");
            Expect.isTrue(r.isPrefix(), "a target host is a prefix rule");
        });

        Harness.test("a dotted host keeps its dots and loses the quote markers", () -> {
            Expect.equal(Scope.display("^\\Qa.b.example.com\\E$"), "a.b.example.com", "dotted host");
        });

        Harness.test("anchors alone are stripped from a hand-written regex", () -> {
            Expect.equal(Scope.display("^.*\\.example\\.com$"), ".*\\.example\\.com", "regex kept");
        });

        Harness.test("null and empty patterns are safe", () -> {
            Expect.equal(Scope.display(null), "", "null pattern");
            Expect.equal(Scope.display(""), "", "empty pattern");
        });
    }
}
