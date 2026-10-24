package opentrap.ui;

import opentrap.core.ScanTask;
import opentrap.core.SuiteContext;
import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

import java.io.File;
import java.nio.file.Files;

/**
 * The scans on the dashboard are part of a project: saving and opening it
 * brings the crawl and audit cards back with their figures, finished, while
 * the always-present live passive audit is neither saved nor duplicated.
 */
public final class ProjectTasksTest {

    public void run() {
        Harness.suite("project scan tasks");

        Harness.test("crawl and audit tasks round-trip through the project file", () -> {
            try {
                SuiteContext ctx = new SuiteContext();
                ScanTask live = new ScanTask(ScanTask.Type.LIVE_PASSIVE, "Live audit", "passive");
                ctx.addTask(live);
                ScanTask crawl = new ScanTask(ScanTask.Type.LIVE_ACTIVE, "Crawl of example.com", "Crawl then audit");
                crawl.setDiscoveredLocations(143);
                crawl.addRequests(612);
                crawl.log("Crawl finished: 612 request(s)");
                crawl.setState(ScanTask.State.FINISHED);
                ctx.addTask(crawl);
                ScanTask audit = new ScanTask(ScanTask.Type.AUDIT, "Audit of example.com", "ZAP active rules");
                audit.setCounts(2, 5, 9, 30);
                audit.setScope("http://example.com");
                audit.setItemsTotal(100); audit.setItemsDone(40);   // still running when saved
                ctx.addTask(audit);

                File f = Files.createTempFile("opentrap-tasks", ".otrap").toFile();
                try {
                    StateIO.save(ctx, f);

                    SuiteContext back = new SuiteContext();
                    ScanTask backLive = new ScanTask(ScanTask.Type.LIVE_PASSIVE, "Live audit", "passive");
                    back.addTask(backLive);
                    StateIO.restore(back, f);

                    var tasks = back.tasks();
                    Expect.equal(tasks.size(), 3, "live audit plus the two saved scans");
                    Expect.isTrue(tasks.get(0) == backLive, "the session's own live audit is kept");
                    ScanTask c = tasks.get(1), a = tasks.get(2);
                    Expect.equal(c.number, crawl.number, "crawl keeps its number");
                    Expect.equal(c.title(), "Crawl of example.com", "crawl title");
                    Expect.equal(c.discoveredLocations(), 143, "crawl locations");
                    Expect.equal(c.requests(), 612, "crawl requests");
                    Expect.equal(c.logLines().size(), 1, "crawl log");
                    Expect.equal(a.type(), ScanTask.Type.AUDIT, "audit type");
                    Expect.equal(a.high(), 2, "audit high");
                    Expect.equal(a.info(), 30, "audit info");
                    Expect.equal(a.scope(), "http://example.com", "audit scope");
                    Expect.equal(a.state(), ScanTask.State.FINISHED, "a restored scan is finished, nothing drives it");

                    ScanTask next = new ScanTask(ScanTask.Type.AUDIT, "next", "");
                    Expect.isTrue(next.number > audit.number, "new tasks number after the restored ones");
                } finally {
                    f.delete();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
