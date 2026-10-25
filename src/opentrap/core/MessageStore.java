package opentrap.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central history of every exchange any tool performs.
 *
 * Kept in memory deliberately: v1.2 did the same, and it keeps the project
 * file a single serialisable object graph. Listeners are notified on the EDT
 * by the UI layer, not here.
 *
 * <p>The one kind of traffic that is capped is the audit's own probe traffic
 * (tool {@link ProjectOptions#AUDIT_TOOL}): an active audit sends thousands of
 * requests per endpoint, so past {@link #setAuditCapBytes(long) the cap} the
 * oldest probes are dropped as new ones arrive. Everything else is kept.
 */
public class MessageStore {
    public interface Listener {
        void entryAdded(LogEntry e);
        void entryUpdated(LogEntry e);
        /** An entry was dropped to stay within the audit traffic cap. */
        default void entryRemoved(LogEntry e) {}
        /**
         * The whole history was replaced at once (a project was restored).
         * Listeners rebuild from {@code all} in one go rather than one entry at
         * a time, which is what keeps a large restore fast.
         */
        default void reloaded(List<LogEntry> all) {}
    }

    private final List<LogEntry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private long auditCapBytes = ProjectOptions.DEFAULT_AUDIT_MEMORY_MB * 1024L * 1024L;
    private long auditBytes;

    public synchronized void add(LogEntry e) {
        entries.add(e);
        List<LogEntry> evicted = null;
        if (ProjectOptions.AUDIT_TOOL.equals(e.tool())) {
            auditBytes += e.bytes();
            evicted = evictAudit();
        }
        for (Listener l : listeners) l.entryAdded(e);
        if (evicted != null) for (LogEntry x : evicted) for (Listener l : listeners) l.entryRemoved(x);
    }

    /** Drop the oldest audit probes until the audit traffic fits the cap again. */
    private List<LogEntry> evictAudit() {
        if (auditCapBytes <= 0 || auditBytes <= auditCapBytes) return null;
        List<LogEntry> out = new ArrayList<>();
        java.util.Iterator<LogEntry> it = entries.iterator();
        while (it.hasNext() && auditBytes > auditCapBytes) {
            LogEntry x = it.next();
            if (!ProjectOptions.AUDIT_TOOL.equals(x.tool())) continue;
            it.remove();
            auditBytes -= x.bytes();
            out.add(x);
        }
        return out.isEmpty() ? null : out;
    }

    /** Audit traffic kept in memory, in bytes; 0 keeps everything. */
    public synchronized void setAuditCapBytes(long bytes) {
        auditCapBytes = bytes;
        List<LogEntry> evicted = evictAudit();
        if (evicted != null) for (LogEntry x : evicted) for (Listener l : listeners) l.entryRemoved(x);
    }

    public synchronized long auditCapBytes() { return auditCapBytes; }

    /** Bytes of audit traffic currently held. */
    public synchronized long auditBytes() { return auditBytes; }

    public void updated(LogEntry e) {
        for (Listener l : listeners) l.entryUpdated(e);
    }

    public synchronized List<LogEntry> all() { return new ArrayList<>(entries); }

    public synchronized int size() { return entries.size(); }

    public synchronized LogEntry get(int i) {
        return (i >= 0 && i < entries.size()) ? entries.get(i) : null;
    }

    public synchronized void clear() { entries.clear(); auditBytes = 0; }

    public synchronized void remove(LogEntry e) {
        if (entries.remove(e) && ProjectOptions.AUDIT_TOOL.equals(e.tool())) auditBytes -= e.bytes();
    }

    public void replaceAll(List<LogEntry> list) {
        List<LogEntry> copy;
        synchronized (this) {
            entries.clear();
            entries.addAll(list);
            auditBytes = 0;
            for (LogEntry e : entries) if (ProjectOptions.AUDIT_TOOL.equals(e.tool())) auditBytes += e.bytes();
            copy = new ArrayList<>(entries);
        }
        for (Listener l : listeners) l.reloaded(copy);
    }

    public void addListener(Listener l)    { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
}
