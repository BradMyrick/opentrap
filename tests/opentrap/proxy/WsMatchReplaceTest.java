package opentrap.proxy;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

import java.util.List;

/**
 * WebSocket match and replace rewrites whole text messages by direction, so a
 * rule aimed at one side must never touch traffic going the other way.
 */
public final class WsMatchReplaceTest {

    public void run() {
        Harness.suite("websocket match and replace");

        Harness.test("a rule for both directions rewrites either way", () -> {
            List<MatchReplaceRule> rules = List.of(
                    new MatchReplaceRule(true, MatchReplaceRule.WS_BOTH, "role=user", "role=admin", ""));
            Expect.equal(MatchReplaceRule.applyAllWs(rules, "{role=user}", true), "{role=admin}", "to server");
            Expect.equal(MatchReplaceRule.applyAllWs(rules, "{role=user}", false), "{role=admin}", "to client");
        });

        Harness.test("a directional rule leaves the other direction alone", () -> {
            List<MatchReplaceRule> rules = List.of(
                    new MatchReplaceRule(true, MatchReplaceRule.WS_TO_SERVER, "ping", "pong", ""));
            Expect.equal(MatchReplaceRule.applyAllWs(rules, "ping", true), "pong", "to server rewritten");
            Expect.equal(MatchReplaceRule.applyAllWs(rules, "ping", false), "ping", "to client untouched");
        });

        Harness.test("disabled and blank rules are skipped", () -> {
            List<MatchReplaceRule> rules = List.of(
                    new MatchReplaceRule(false, MatchReplaceRule.WS_BOTH, "a", "b", ""),
                    new MatchReplaceRule(true, MatchReplaceRule.WS_BOTH, "", "b", ""));
            Expect.equal(MatchReplaceRule.applyAllWs(rules, "a", true), "a", "unchanged");
        });

        Harness.test("rules apply in order and a bad regex falls back to a literal match", () -> {
            List<MatchReplaceRule> rules = List.of(
                    new MatchReplaceRule(true, MatchReplaceRule.WS_BOTH, "one", "two", ""),
                    new MatchReplaceRule(true, MatchReplaceRule.WS_BOTH, "two", "three", ""),
                    new MatchReplaceRule(true, MatchReplaceRule.WS_BOTH, "(", "[", ""));
            Expect.equal(MatchReplaceRule.applyAllWs(rules, "one(", true), "three[", "chained");
        });
    }
}
