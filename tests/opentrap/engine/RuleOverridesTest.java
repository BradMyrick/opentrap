package opentrap.engine;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The saved scan-rule choices: overrides round-trip through the file exactly,
 * each field records without disturbing the others, the passive and active
 * rule kinds stay separate, and clearing one rule leaves the rest alone. Runs
 * against a throwaway home directory.
 */
public final class RuleOverridesTest {

    public void run() {
        Harness.suite("saved scan rules");

        String home = System.getProperty("user.home");
        Path tmp;
        try { tmp = Files.createTempDirectory("opentrap-rules"); }
        catch (Exception e) { throw new RuntimeException(e); }
        System.setProperty("user.home", tmp.toString());
        try {
            Harness.test("a saved override loads back exactly, per kind", () -> {
                RuleOverrides.set(RuleOverrides.ACTIVE, "40012",
                        new RuleOverrides.Override(false, "LOW", "OFF"));
                RuleOverrides.set(RuleOverrides.PASSIVE, "10012",
                        new RuleOverrides.Override(null, null, "HIGH"));
                RuleOverrides.Override a = RuleOverrides.get(RuleOverrides.ACTIVE, "40012");
                Expect.isFalse(a.enabled(), "active override enabled");
                Expect.equal(a.strength(), "LOW", "active override strength");
                Expect.equal(a.threshold(), "OFF", "active override threshold");
                // The same id under the other kind is a different rule.
                Expect.isNull(RuleOverrides.get(RuleOverrides.PASSIVE, "40012"), "no passive twin");
                RuleOverrides.Override p = RuleOverrides.get(RuleOverrides.PASSIVE, "10012");
                Expect.isNull(p.enabled(), "passive override has no enabled choice");
                Expect.isNull(p.strength(), "passive override has no strength choice");
                Expect.equal(p.threshold(), "HIGH", "passive override threshold");
            });

            Harness.test("each field records without disturbing the others", () -> {
                RuleOverrides.setThreshold(RuleOverrides.ACTIVE, "9", "LOW");
                RuleOverrides.setEnabled(RuleOverrides.ACTIVE, "9", true);
                RuleOverrides.setStrength(RuleOverrides.ACTIVE, "9", "INSANE");
                RuleOverrides.Override o = RuleOverrides.get(RuleOverrides.ACTIVE, "9");
                Expect.isTrue(o.enabled(), "enabled kept");
                Expect.equal(o.strength(), "INSANE", "strength kept");
                Expect.equal(o.threshold(), "LOW", "threshold kept");
                RuleOverrides.setStrength(RuleOverrides.ACTIVE, "9", "MEDIUM");
                o = RuleOverrides.get(RuleOverrides.ACTIVE, "9");
                Expect.equal(o.strength(), "MEDIUM", "strength replaced");
                Expect.isTrue(o.enabled(), "enabled survived the replacement");
                Expect.equal(o.threshold(), "LOW", "threshold survived the replacement");
            });

            Harness.test("a partial override stays partial across a reload", () -> {
                RuleOverrides.setEnabled(RuleOverrides.PASSIVE, "10021", false);
                RuleOverrides.Override o = RuleOverrides.get(RuleOverrides.PASSIVE, "10021");
                Expect.isFalse(o.enabled(), "the enabled choice");
                Expect.isNull(o.threshold(), "no threshold was recorded");
            });

            Harness.test("clear forgets one rule and leaves the rest", () -> {
                RuleOverrides.setThreshold(RuleOverrides.PASSIVE, "30001", "OFF");
                RuleOverrides.setThreshold(RuleOverrides.PASSIVE, "30002", "HIGH");
                RuleOverrides.clear(RuleOverrides.PASSIVE, "30001");
                Expect.isNull(RuleOverrides.get(RuleOverrides.PASSIVE, "30001"), "cleared");
                Expect.equal(RuleOverrides.get(RuleOverrides.PASSIVE, "30002").threshold(),
                        "HIGH", "the other rule untouched");
            });

            Harness.test("ids lists the rules with something saved, sorted", () -> {
                RuleOverrides.setEnabled(RuleOverrides.ACTIVE, "40040", true);
                RuleOverrides.setEnabled(RuleOverrides.ACTIVE, "40020", true);
                java.util.List<String> ids = RuleOverrides.ids(RuleOverrides.ACTIVE);
                Expect.isTrue(ids.contains("40020") && ids.contains("40040"), "both listed");
                Expect.isTrue(ids.contains("40012"), "the rule from the first case too");
                Expect.isTrue(ids.indexOf("40012") < ids.indexOf("40020")
                        && ids.indexOf("40020") < ids.indexOf("40040"), "sorted");
                Expect.isTrue(RuleOverrides.ids(RuleOverrides.PASSIVE).contains("30002")
                        && !RuleOverrides.ids(RuleOverrides.PASSIVE).contains("40020"),
                        "kinds stay separate");
            });
        } finally {
            System.setProperty("user.home", home);
        }
    }
}
