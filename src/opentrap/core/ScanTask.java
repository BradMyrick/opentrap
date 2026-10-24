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
    private volatile long startedAt = System.currentTimeMillis();
    private volatile byte[] lastShot;   // latest rendered-page screenshot (crawl tasks)
    private final List<String> log = new CopyOnWriteArrayList<>();

    public ScanTask(Type type, String title, String configuration) {
        this.number = SEQ.incrementAndGet();
        this.type = type;
        this.title = title;
        this.configuration = configuration;
    }

    private ScanTask(int number, Type type, String title, String configuration) {
        this.number = number;
        this.type = type;
        this.title = title;
        this.configuration = configuration;
    }

    public static void resetCounter() { SEQ.set(0); }

    /** Continue numbering after {@code v}, so a restored project's tasks keep their numbers. */
    public static void setCounter(int v) { SEQ.accumulateAndGet(v, Math::max); }

    /**
     * Everything about a task worth keeping in a project file. A task is live
     * state driven by a scan loop, so the file holds this plain copy instead;
     * a task restored from it is always finished, since nothing is driving it.
     */
    public record Snapshot(int number, Type type, String title, String configuration, String scope,
                           int high, int medium, int low, int info,
                           int requests, int itemsTotal, int itemsDone,
                           int discoveredLocations, int auditItemsTotal,
                           long startedAt, byte[] lastShot, List<String> log)
            implements java.io.Serializable {}

    public Snapshot snapshot() {
        return new Snapshot(number, type, title, configuration, scope,
                high.get(), medium.get(), low.get(), info.get(),
                requests, itemsTotal, itemsDone, discoveredLocations, auditItemsTotal,
                startedAt, lastShot, logLines());
    }

    public static ScanTask fromSnapshot(Snapshot s) {
        ScanTask t = new ScanTask(s.number(), s.type(), s.title(), s.configuration());
        t.scope = s.scope() == null ? "" : s.scope();
        t.state = State.FINISHED;
        t.setCounts(s.high(), s.medium(), s.low(), s.info());
        t.requests = s.requests();
        t.itemsTotal = s.itemsTotal();
        t.itemsDone = s.itemsDone();
        t.discoveredLocations = s.discoveredLocations();
        t.auditItemsTotal = s.auditItemsTotal();
        t.startedAt = s.startedAt();
        t.lastShot = s.lastShot();
        if (s.log() != null) t.log.addAll(s.log());
        setCounter(s.number());
        return t;
    }

    public Type type()            { return type; }
    public String title()         { return title; }
    public void setTitle(String t){ this.title = t; }
    public String configuration() { return configuration; }
    public String scope()         { return scope; }
    public void setScope(String s){ this.scope = s == null ? "" : s; }
    public State state()          { return state; }
    public void setState(State s) { this.state = s; }

    // A cancelled task is one the user deleted or stopped: the scan loop driving
    // it checks this, stops the engine's scan, and exits so nothing keeps running
    // (and consuming memory) in the background after the task is gone.
    private volatile boolean cancelled;
    public boolean cancelled()    { return cancelled; }
    public void cancel()          { this.cancelled = true; }

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
