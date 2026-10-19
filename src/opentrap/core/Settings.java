package opentrap.core;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/** String-keyed settings with typed accessors, persisted to java.util.prefs. */
public class Settings implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String NODE = "opentrap.v1";

    private final Map<String, String> values = new LinkedHashMap<>();
    private transient Preferences prefs;

    public Settings() {
        try { prefs = Preferences.userRoot().node(NODE); } catch (Exception ignored) {}
    }

    public synchronized String get(String key, String def) {
        String v = values.get(key);
        if (v != null) return v;
        if (prefs != null) {
            try {
                String p = prefs.get(key, null);
                if (p != null) { values.put(key, p); return p; }
            } catch (Exception ignored) {}
        }
        return def;
    }

    public synchronized void set(String key, String value) {
        values.put(key, value);
        if (prefs != null) {
            try { prefs.put(key, value); } catch (Exception ignored) {}
        }
    }

    public boolean getBool(String k, boolean def) { return Boolean.parseBoolean(get(k, String.valueOf(def))); }
    public void setBool(String k, boolean v)      { set(k, String.valueOf(v)); }

    public int getInt(String k, int def) {
        try { return Integer.parseInt(get(k, String.valueOf(def))); } catch (Exception e) { return def; }
    }
    public void setInt(String k, int v) { set(k, String.valueOf(v)); }

    public synchronized Map<String, String> snapshot() { return new LinkedHashMap<>(values); }

    public synchronized void restore(Map<String, String> m) {
        if (m == null) return;
        for (Map.Entry<String, String> e : m.entrySet()) set(e.getKey(), e.getValue());
    }
}
