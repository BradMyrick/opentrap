package opentrap.testkit;

import opentrap.core.Issue;

import java.util.List;

/**
 * Assertions phrased for scan-module tests.
 *
 * <p>Every failure message says what was expected, what happened, and when a
 * finding list is involved, which findings were actually present. A module test
 * that fails should not need the debugger to understand.
 */
public final class Expect {

    private Expect() {}

    public static void isTrue(boolean condition, String what) {
        if (!condition) throw new AssertionError("expected " + what);
    }

    public static void isFalse(boolean condition, String what) {
        if (condition) throw new AssertionError("expected not " + what);
    }

    public static void equal(Object actual, Object expected, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void isNull(Object actual, String what) {
        if (actual != null) throw new AssertionError(what + ": expected null but was <" + actual + ">");
    }

    public static void notNull(Object actual, String what) {
        if (actual == null) throw new AssertionError(what + ": expected a value but was null");
    }

    /** No finding at all. The most important assertion, because it catches false positives. */
    public static void none(List<Issue> issues) {
        if (issues != null && !issues.isEmpty()) {
            throw new AssertionError("expected no finding but got " + describe(issues));
        }
    }

    /** Exactly one finding, which is returned so its fields can be checked. */
    public static Issue one(List<Issue> issues) {
        notNull(issues, "finding list");
        if (issues.size() != 1) {
            throw new AssertionError("expected exactly one finding but got " + describe(issues));
        }
        return issues.get(0);
    }

    /** An exact number of findings. */
    public static void count(List<Issue> issues, int expected) {
        notNull(issues, "finding list");
        if (issues.size() != expected) {
            throw new AssertionError("expected " + expected + " findings but got " + describe(issues));
        }
    }

    /** At least one finding whose title contains the given text. */
    public static Issue raised(List<Issue> issues, String titleContains) {
        notNull(issues, "finding list");
        for (Issue i : issues) {
            if (i.title().toLowerCase().contains(titleContains.toLowerCase())) return i;
        }
        throw new AssertionError("expected a finding titled like \"" + titleContains
                + "\" but got " + describe(issues));
    }

    public static void severity(Issue i, Issue.Severity expected) {
        notNull(i, "finding");
        equal(i.severity(), expected, "severity of \"" + i.title() + "\"");
    }

    public static void confidence(Issue i, Issue.Confidence expected) {
        notNull(i, "finding");
        equal(i.confidence(), expected, "confidence of \"" + i.title() + "\"");
    }

    /** The finding quotes the given text as its evidence. */
    public static void evidence(Issue i, String contains) {
        notNull(i, "finding");
        if (i.evidence() == null || !i.evidence().contains(contains)) {
            throw new AssertionError("expected evidence of \"" + i.title() + "\" to contain <"
                    + contains + "> but was <" + (i.evidence() == null ? "" : i.evidence()) + ">");
        }
    }

    public static void param(Issue i, String expected) {
        notNull(i, "finding");
        equal(i.param(), expected, "parameter of \"" + i.title() + "\"");
    }

    /**
     * Every finding carries the metadata the advisory and any report depend on.
     * Worth asserting once per module so a new check cannot ship half-described.
     */
    public static void wellFormed(Issue i) {
        notNull(i, "finding");
        isTrue(i.title() != null && !i.title().isBlank(), "a title");
        isTrue(i.detail() != null && !i.detail().isBlank(), "detail prose on \"" + i.title() + "\"");
        isTrue(i.background() != null && !i.background().isBlank(),
                "background prose on \"" + i.title() + "\"");
        isTrue(i.remediation() != null && !i.remediation().isBlank(),
                "remediation prose on \"" + i.title() + "\"");
        isTrue(i.moduleId() > 0, "a module id on \"" + i.title() + "\"");
        isTrue(i.path() != null && !i.path().isBlank(), "a path on \"" + i.title() + "\"");
    }

    private static String describe(List<Issue> issues) {
        if (issues == null) return "null";
        if (issues.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder(issues.size() + ": [");
        for (int i = 0; i < issues.size(); i++) {
            if (i > 0) sb.append(", ");
            Issue is = issues.get(i);
            sb.append('"').append(is.title()).append('"');
            if (is.param() != null) sb.append(" param=").append(is.param());
        }
        return sb.append(']').toString();
    }
}
