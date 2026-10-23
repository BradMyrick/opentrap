package opentrap.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** A scan task shown on the dashboard: a crawl/audit, live passive audit, or active scan. */
public class ScanTask {
    public enum Type { LIVE_PASSIVE, LIVE_ACTIVE, AUDIT }
    public enum State { RUNNING, PAUSED, FINISHED }

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    public final int number;
    private final Type type;
    private volatile String title;
    private volatile String configuration;
    private volatile String scope = "";
    private volatile State state = State.RUNNING;

    private final AtomicInteger high = new AtomicInteger();
    private final AtomicInteger medium = new AtomicInteger();
    private final AtomicInteger low = new AtomicInteger();
    private final AtomicInteger info = new AtomicInteger();

    private volatile int requests;
    private volatile int itemsTotal;
    private volatile int itemsDone;
    private volatile int discoveredLocations;  // URLs the crawl found (real, from ZAP spider)
    private volatile int auditItemsTotal;      // active-scan rules enabled (real, from ZAP)
    private final long startedAt = System.currentTimeMillis();
    private volatile byte[] lastShot;   // latest rendered-page screenshot (crawl tasks)
    private final List<String> log = new CopyOnWriteArrayList<>();

    public ScanTask(Type type, String title, String configuration) {
        this.number = SEQ.incrementAndGet();
        this.type = type;
        this.title = title;
        this.configuration = configuration;
    }

    public static void resetCounter() { SEQ.set(0); }

    public Type type()            { return type; }
    public String title()         { return title; }
    public void setTitle(String t){ this.title = t; }
    public String configuration() { return configuration; }
    public String scope()         { return scope; }
    public void setScope(String s){ this.scope = s == null ? "" : s; }
    public State state()          { return state; }
    public void setState(State s) { this.state = s; }

    public int high()   { return high.get(); }
    public int medium() { return medium.get(); }
    public int low()    { return low.get(); }
    public int info()   { return info.get(); }

    /** Set the issue counts directly, for a task that reflects a shared set. */
    public void setCounts(int h, int m, int l, int i) {
        high.set(h); medium.set(m); low.set(l); info.set(i);
    }

    public void countIssue(Issue.Severity sev) {
        switch (sev) {
            case HIGH -> high.incrementAndGet();
            case MEDIUM -> medium.incrementAndGet();
            case LOW -> low.incrementAndGet();
            case INFORMATION -> info.incrementAndGet();
        }
    }

    public int requests()          { return requests; }
    public void addRequests(int n) { requests += n; }
    public int itemsTotal()        { return itemsTotal; }
    public void setItemsTotal(int n){ itemsTotal = n; }
    public int itemsDone()         { return itemsDone; }
    public void setItemsDone(int n){ itemsDone = n; }
    public int discoveredLocations()        { return discoveredLocations; }
    public void setDiscoveredLocations(int n){ discoveredLocations = n; }
    public int auditItemsTotal()            { return auditItemsTotal; }
    public void setAuditItemsTotal(int n)   { auditItemsTotal = n; }
    public long startedAt()        { return startedAt; }
    public byte[] lastShot()           { return lastShot; }
    public void setLastShot(byte[] b)  { lastShot = b; }

    public double progress() {
        if (type == Type.LIVE_PASSIVE || type == Type.LIVE_ACTIVE) return -1;  // continuous
        return itemsTotal <= 0 ? 0 : Math.min(1.0, (double) itemsDone / itemsTotal);
    }

    public void log(String line) {
        log.add(line);
        if (log.size() > 500) log.remove(0);
    }
    public List<String> logLines() { return new ArrayList<>(log); }

    public String stateLabel() {
        return switch (state) { case RUNNING -> "Running"; case PAUSED -> "Paused"; case FINISHED -> "Finished"; };
    }
}
