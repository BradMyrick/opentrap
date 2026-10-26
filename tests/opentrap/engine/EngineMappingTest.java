package opentrap.engine;

import opentrap.core.Issue;
import opentrap.spider.Cdp.JsonValue;
import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // -------- evidence sharing: one message commonly triggers several alerts --------

        Harness.test("alerts raised on one message share one evidence pair", () -> {
            int[] fetches = {0};
            ZapScanner.MessageSource src = id -> { fetches[0]++; return MESSAGE; };
            Map<String, ZapScanner.Evidence> evidence = new HashMap<>();
            Issue one = ZapScanner.mapAlert(alertNamed("Missing header"), evidence, src);
            Issue two = ZapScanner.mapAlert(alertNamed("Cookie without Secure"), evidence, src);
            Expect.equal(fetches[0], 1, "the message is fetched once, not once per alert");
            Expect.isTrue(one.response() == two.response(), "both findings hold the same response");
        });

        Harness.test("a message that cannot be fetched is not cached", () -> {
            int[] fetches = {0};
            ZapScanner.MessageSource src = id -> { fetches[0]++; return null; };
            Map<String, ZapScanner.Evidence> evidence = new HashMap<>();
            Issue one = ZapScanner.mapAlert(alertNamed("A"), evidence, src);
            Issue two = ZapScanner.mapAlert(alertNamed("B"), evidence, src);
            Expect.equal(fetches[0], 2, "each alert retries the fetch, as before");
            Expect.isNull(one.response(), "no evidence attached to the first");
            Expect.isNull(two.response(), "no evidence attached to the second");
        });

        Harness.test("a record with only a response attaches only the response", () -> {
            JsonValue responseOnly =
                    JsonValue.parse("{\"responseHeader\":\"HTTP/1.1 204 No Content\\r\\n\\r\\n\"}");
            ZapScanner.MessageSource src = id -> responseOnly;
            Issue i = ZapScanner.mapAlert(alertNamed("A"), new HashMap<>(), src);
            Expect.isNull(i.request(), "no request in the record");
            Expect.notNull(i.response(), "the response is attached");
        });

        Harness.test("shared evidence is written once and restores shared", () -> {
            ZapScanner.MessageSource src = id -> MESSAGE;
            // Side A: two findings, each with its own copy of equal evidence,
            // which is what per-alert construction produced before.
            Issue a1 = ZapScanner.mapAlert(alertNamed("A"), new HashMap<>(), src);
            Issue a2 = ZapScanner.mapAlert(alertNamed("B"), new HashMap<>(), src);
            // Side B: two findings sharing one pair, as one listing's cache builds.
            Map<String, ZapScanner.Evidence> evidence = new HashMap<>();
            Issue b1 = ZapScanner.mapAlert(alertNamed("A"), evidence, src);
            Issue b2 = ZapScanner.mapAlert(alertNamed("B"), evidence, src);

            byte[] distinct = serialize(a1, a2);
            byte[] shared = serialize(b1, b2);
            Expect.isTrue(shared.length < distinct.length - EVIDENCE_BODY_BYTES / 2,
                    "the project file holds one copy, not one per finding");
            List<Issue> back = deserialize(shared);
            Expect.isTrue(back.get(0).response() == back.get(1).response(),
                    "a restored project holds the pair once too");
        });
    }

    /** Body size for evidence in the sharing tests, large enough that one copy is unmistakable in the file. */
    private static final int EVIDENCE_BODY_BYTES = 64 * 1024;

    /** One engine message record: a request and a response with a sizeable body. */
    private static final JsonValue MESSAGE = JsonValue.parse(
            "{\"requestHeader\":\"GET /a HTTP/1.1\\r\\nHost: t.example\\r\\n\\r\\n\","
            + "\"responseHeader\":\"HTTP/1.1 200 OK\\r\\nContent-Type: application/json\\r\\n\\r\\n\","
            + "\"responseBody\":\"" + "x".repeat(EVIDENCE_BODY_BYTES) + "\"}");

    private static JsonValue alertNamed(String name) {
        return JsonValue.parse("{\"name\":\"" + name + "\",\"risk\":\"Low\",\"confidence\":\"Medium\","
                + "\"url\":\"http://t.example/a\",\"messageId\":\"7\"}");
    }

    private static byte[] serialize(Issue a, Issue b) {
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(List.of(a, b));
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @SuppressWarnings("unchecked")
    private static List<Issue> deserialize(byte[] bytes) {
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bytes))) {
            return (List<Issue>) ois.readObject();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
