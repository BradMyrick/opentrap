package opentrap.core;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit traffic cap: past it the oldest audit probes are dropped, with
 * listeners told, and nothing from any other tool is ever dropped.
 */
public final class MessageStoreCapTest {

    private static final HttpService SVC = new HttpService("example.com", 80, false);

    private static LogEntry entry(String tool, int bodyLen) {
        LogEntry e = new LogEntry(tool, SVC, new HttpMessage(Bytes.bytes("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n")));
        e.setResponse(new HttpMessage(Bytes.bytes("HTTP/1.1 200 OK\r\n\r\n" + "y".repeat(bodyLen))));
        return e;
    }

    public void run() {
        Harness.suite("audit traffic cap");

        Harness.test("oldest audit probes go first, other tools never do", () -> {
            MessageStore store = new MessageStore();
            List<LogEntry> removed = new ArrayList<>();
            store.addListener(new MessageStore.Listener() {
                @Override public void entryAdded(LogEntry e) {}
                @Override public void entryUpdated(LogEntry e) {}
                @Override public void entryRemoved(LogEntry e) { removed.add(e); }
            });
            store.setAuditCapBytes(3500);   // three probes of about 1060 bytes fit, four do not
            LogEntry proxy = entry("proxy", 5000);     // bigger than the cap, not audit
            store.add(proxy);
            LogEntry a1 = entry("audit", 1000), a2 = entry("audit", 1000), a3 = entry("audit", 1000), a4 = entry("audit", 1000);
            store.add(a1); store.add(a2); store.add(a3);
            Expect.equal(removed.size(), 0, "within the cap nothing is dropped");
            store.add(a4);
            Expect.equal(removed.size(), 1, "one probe dropped");
            Expect.isTrue(removed.get(0) == a1, "the oldest probe");
            List<LogEntry> all = store.all();
            Expect.isTrue(all.contains(proxy), "the proxy entry stays");
            Expect.isFalse(all.contains(a1), "the dropped probe is gone");
            Expect.isTrue(all.contains(a4), "the newest probe stays");
            Expect.isTrue(store.auditBytes() <= 3500, "audit bytes within the cap");
        });

        Harness.test("lowering the cap trims at once; zero means no cap", () -> {
            MessageStore store = new MessageStore();
            store.setAuditCapBytes(0);
            for (int i = 0; i < 20; i++) store.add(entry("audit", 1000));
            Expect.equal(store.size(), 20, "no cap keeps everything");
            store.setAuditCapBytes(2500);
            Expect.equal(store.size(), 2, "trimmed to fit");
        });

        Harness.test("a restored history counts its audit bytes", () -> {
            MessageStore store = new MessageStore();
            List<LogEntry> list = new ArrayList<>();
            list.add(entry("audit", 100)); list.add(entry("proxy", 100));
            store.replaceAll(list);
            Expect.isTrue(store.auditBytes() > 100 && store.auditBytes() < 300, "only the audit entry counted");
        });
    }
}
