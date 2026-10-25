package opentrap.engine;

import opentrap.core.Issue;
import opentrap.spider.Cdp.JsonValue;
import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

/**
 * Tests the translation of a ZAP alert into an OpenTRAP {@link Issue}, which is
 * the one piece of detection logic OpenTRAP owns now that ZAP is the engine: the
 * mapping of ZAP's result fields onto our model.
 */
public final class EngineMappingTest {

    public void run() {
        Harness.suite("ZAP alert mapping");

        String sqli = "{\"name\":\"SQL Injection\",\"alert\":\"SQL Injection\","
                + "\"risk\":\"High\",\"confidence\":\"Medium\","
                + "\"url\":\"http://t.example/login.aspx?id=1\",\"param\":\"tbUsername\","
                + "\"attack\":\"' OR 1=1 --\",\"evidence\":\"SQL syntax error\","
                + "\"cweid\":\"89\",\"wascid\":\"19\",\"pluginId\":\"40018\","
                + "\"description\":\"d\",\"solution\":\"s\",\"reference\":\"r\","
                + "\"other\":\"\",\"tags\":{\"A03\":\"u\"}}";
        JsonValue a = JsonValue.parse(sqli);

        Harness.test("engine history records are tagged by what made them", () -> {
            Expect.equal(ZapScanner.toolFor("2", "crawl"), "crawl", "spider record");
            Expect.equal(ZapScanner.toolFor("10", "audit"), "crawl", "AJAX spider record during the audit");
            Expect.equal(ZapScanner.toolFor("3", "crawl"), "audit", "scanner record");
            Expect.equal(ZapScanner.toolFor("14", "audit"), "audit", "temporary scanner record");
            Expect.isNull(ZapScanner.toolFor("1", "crawl"), "proxied traffic is the proxy's");
            Expect.isNull(ZapScanner.toolFor("16", "audit"), "a CONNECT relay");
            Expect.equal(ZapScanner.toolFor(null, "audit"), "audit", "an untyped record takes the phase");
            Expect.equal(ZapScanner.toolFor("0", "crawl"), "crawl", "a temporary record takes the phase");
        });

        Harness.test("a high-risk alert maps onto a HIGH issue with all fields", () -> {
            Issue i = ZapScanner.mapAlert(null, a);
            Expect.notNull(i, "mapped issue");
            Expect.equal(i.title(), "SQL Injection", "title");
            Expect.severity(i, Issue.Severity.HIGH);
            Expect.confidence(i, Issue.Confidence.FIRM);
            Expect.equal(i.host(), "t.example", "host");
            Expect.equal(i.path(), "/login.aspx?id=1", "path");
            Expect.param(i, "tbUsername");
            Expect.equal(i.cwe(), 89, "cwe");
            Expect.equal(i.wasc(), 19, "wasc");
            Expect.equal(i.moduleId(), 40018, "pluginId");
            Expect.evidence(i, "SQL syntax");
        });

        Harness.test("risk and confidence words map to the right levels", () -> {
            JsonValue b = JsonValue.parse(
                    "{\"name\":\"X\",\"risk\":\"Low\",\"confidence\":\"High\",\"url\":\"http://h/p\"}");
            Issue i = ZapScanner.mapAlert(null, b);
            Expect.severity(i, Issue.Severity.LOW);
            Expect.confidence(i, Issue.Confidence.CERTAIN);
        });

        Harness.test("informational risk maps to INFORMATION", () -> {
            JsonValue b = JsonValue.parse(
                    "{\"name\":\"X\",\"risk\":\"Informational\",\"confidence\":\"Low\",\"url\":\"http://h/p\"}");
            Issue i = ZapScanner.mapAlert(null, b);
            Expect.severity(i, Issue.Severity.INFORMATION);
            Expect.confidence(i, Issue.Confidence.TENTATIVE);
        });

        Harness.test("an alert with no url is skipped rather than mis-mapped", () -> {
            JsonValue b = JsonValue.parse("{\"name\":\"X\",\"risk\":\"High\"}");
            Expect.isNull(ZapScanner.mapAlert(null, b), "issue for a url-less alert");
        });
    }
}
