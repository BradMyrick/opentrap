package opentrap.engine;

import opentrap.core.Debug;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The scan-rule choices that travel between sessions, saved as one properties
 * file under {@code ~/.opentrap/scan-rules.properties}.
 *
 * <p>The Scanner tabs write rule changes straight to the live engine, and the
 * engine process starts fresh with ZAP's defaults every run, so without this
 * store a policy tuned on Monday is gone on Tuesday. Every accepted edit is
 * recorded here too; {@link #applyAll} pushes the saved choices to the engine
 * whenever it starts, and again after a scan tier sets its blanket attack
 * strength and alert threshold, so an explicit per-rule choice always beats
 * the tier preset. Like {@link ScanConfigStore} this is a stateless file
 * store: every call reads or writes the one small file, so there is no cache
 * to fall out of step with what is on disk.
 */
public final class RuleOverrides {

    /** The two rule kinds; ZAP numbers them separately, so the file does too. */
    public static final String PASSIVE = "passive";
    public static final String ACTIVE  = "active";

    /**
     * One rule's saved settings. A null field means no preference: whatever
     * the engine (or the scan tier) last set stays in force for it.
     */
    public record Override(Boolean enabled, String strength, String threshold) {
        /** This override with one field replaced; the rest carry over. */
        public Override withEnabled(Boolean on)  { return new Override(on, strength, threshold); }
        public Override withStrength(String s)   { return new Override(enabled, s, threshold); }
        public Override withThreshold(String t)  { return new Override(enabled, strength, t); }
    }

    private RuleOverrides() {}

    private static Path file() {
        return Path.of(System.getProperty("user.home"), ".opentrap", "scan-rules.properties");
    }

    /** The saved override for one rule, or null when nothing was recorded. */
    public static Override get(String kind, String id) {
        synchronized (RuleOverrides.class) {
            return load().get(kind).get(id);
        }
    }

    /** Every rule id of one kind that has anything saved, sorted. */
    public static List<String> ids(String kind) {
        synchronized (RuleOverrides.class) {
            return new ArrayList<>(load().get(kind).keySet());
        }
    }

    /** Record (or replace) the saved override for one rule. */
    public static void set(String kind, String id, Override o) {
        synchronized (RuleOverrides.class) {
            Map<String, TreeMap<String, Override>> all = load();
            if (o == null) all.get(kind).remove(id);
            else all.get(kind).put(id, o);
            store(all);
        }
    }

    /** Forget one rule's saved override; the engine's own default governs again. */
    public static void clear(String kind, String id) {
        set(kind, id, null);
    }

    /** Record one rule's enabled state, keeping its other saved settings. */
    public static void setEnabled(String kind, String id, boolean on) {
        synchronized (RuleOverrides.class) {
            Map<String, TreeMap<String, Override>> all = load();
            all.get(kind).put(id, blank(all.get(kind).get(id)).withEnabled(on));
            store(all);
        }
    }

    /** Record one rule's attack strength (active rules only), keeping the rest. */
    public static void setStrength(String kind, String id, String strength) {
        synchronized (RuleOverrides.class) {
            Map<String, TreeMap<String, Override>> all = load();
            all.get(kind).put(id, blank(all.get(kind).get(id)).withStrength(strength));
            store(all);
        }
    }

    /** Record one rule's alert threshold, keeping its other saved settings. */
    public static void setThreshold(String kind, String id, String threshold) {
        synchronized (RuleOverrides.class) {
            Map<String, TreeMap<String, Override>> all = load();
            all.get(kind).put(id, blank(all.get(kind).get(id)).withThreshold(threshold));
            store(all);
        }
    }

    /** The rule's current saved override, or a blank one to build on. */
    private static Override blank(Override o) {
        return o == null ? new Override(null, null, null) : o;
    }

    /**
     * Push every saved choice to the engine. Called once the engine has just
     * started, so the saved policy governs the whole session (passive
     * scanning included), and again after a scan tier applies its blanket
     * strength and threshold, so a per-rule choice beats the tier. A rule the
     * engine no longer has is skipped quietly: a saved choice for a rule that
     * was removed costs nothing but one refused call.
     */
    public static void applyAll(ZapApi api) {
        Map<String, TreeMap<String, Override>> all;
        synchronized (RuleOverrides.class) {
            all = load();
        }
        for (Map.Entry<String, Override> e : all.get(ACTIVE).entrySet()) {
            String id = e.getKey();
            Override o = e.getValue();
            try {
                if (o.enabled() != null) api.setActiveEnabled(id, o.enabled());
                if (o.strength() != null) api.setActiveStrength(id, o.strength());
                if (o.threshold() != null) api.setActiveThreshold(id, o.threshold());
            } catch (Exception ex) {
                Debug.log("saved override for active rule " + id + ": " + ex);
            }
        }
        for (Map.Entry<String, Override> e : all.get(PASSIVE).entrySet()) {
            String id = e.getKey();
            Override o = e.getValue();
            try {
                if (o.enabled() != null) api.setPassiveEnabled(id, o.enabled());
                if (o.threshold() != null) api.setPassiveThreshold(id, o.threshold());
            } catch (Exception ex) {
                Debug.log("saved override for passive rule " + id + ": " + ex);
            }
        }
    }

    /** Parse the store; missing or unreadable means nothing saved yet. */
    private static Map<String, TreeMap<String, Override>> load() {
        TreeMap<String, Override> passive = new TreeMap<>();
        TreeMap<String, Override> active = new TreeMap<>();
        Path f = file();
        if (Files.isRegularFile(f)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            } catch (IOException e) {
                Debug.log("could not read the saved scan rules: " + e);
            }
            for (String key : p.stringPropertyNames()) {
                // kind.id.field - the kinds and ZAP's numeric ids carry no dots.
                String[] part = key.split("\\.");
                if (part.length != 3) continue;
                TreeMap<String, Override> into = PASSIVE.equals(part[0]) ? passive
                        : ACTIVE.equals(part[0]) ? active : null;
                if (into == null) continue;
                Override base = blank(into.get(part[1]));
                Override next = switch (part[2]) {
                    case "enabled"  -> base.withEnabled(Boolean.parseBoolean(p.getProperty(key)));
                    case "strength" -> base.withStrength(p.getProperty(key));
                    case "threshold"-> base.withThreshold(p.getProperty(key));
                    default -> null;   // a field a newer build knows; leave what is saved
                };
                if (next != null) into.put(part[1], next);
            }
        }
        Map<String, TreeMap<String, Override>> out = new TreeMap<>();
        out.put(PASSIVE, passive);
        out.put(ACTIVE, active);
        return out;
    }

    /** Write the store; a failure is logged, never thrown at a caller. */
    private static void store(Map<String, TreeMap<String, Override>> all) {
        Properties p = new Properties();
        for (String kind : new String[]{PASSIVE, ACTIVE}) {
            for (Map.Entry<String, Override> e : all.get(kind).entrySet()) {
                String id = e.getKey();
                Override o = e.getValue();
                if (o.enabled() != null)
                    p.setProperty(kind + "." + id + ".enabled", String.valueOf(o.enabled()));
                if (o.strength() != null)
                    p.setProperty(kind + "." + id + ".strength", o.strength());
                if (o.threshold() != null)
                    p.setProperty(kind + "." + id + ".threshold", o.threshold());
            }
        }
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "OpenTRAP scan-rule choices");
            }
        } catch (IOException e) {
            Debug.log("could not save the scan rules: " + e);
        }
    }
}
