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
 */
public class MessageStore {
    public interface Listener {
        void entryAdded(LogEntry e);
        void entryUpdated(LogEntry e);
        /**
         * The whole history was replaced at once (a project was restored).
         * Listeners rebuild from {@code all} in one go rather than one entry at
         * a time, which is what keeps a large restore fast.
         */
        default void reloaded(List<LogEntry> all) {}
    }

    private final List<LogEntry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public synchronized void add(LogEntry e) {
        entries.add(e);
        for (Listener l : listeners) l.entryAdded(e);
    }

    public void updated(LogEntry e) {
        for (Listener l : listeners) l.entryUpdated(e);
    }

    public synchronized List<LogEntry> all() { return new ArrayList<>(entries); }

    public synchronized int size() { return entries.size(); }

    public synchronized LogEntry get(int i) {
        return (i >= 0 && i < entries.size()) ? entries.get(i) : null;
    }

    public synchronized void clear() { entries.clear(); }

    public synchronized void remove(LogEntry e) { entries.remove(e); }

    public void replaceAll(List<LogEntry> list) {
        List<LogEntry> copy;
        synchronized (this) {
            entries.clear();
            entries.addAll(list);
            copy = new ArrayList<>(entries);
        }
        for (Listener l : listeners) l.reloaded(copy);
    }

    public void addListener(Listener l)    { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
}
