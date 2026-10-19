package opentrap.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Interface scaling via Java's real HiDPI mechanism (sun.java2d.uiScale).
 *
 * That property scales the entire Swing rendering uniformly: fonts, column
 * widths, borders and all coordinates, and it stays crisp. It can only be set
 * at JVM startup, so changing scale relaunches the app. This is why scaling
 * "just works" here instead of leaving fixed pixel sizes behind.
 */
public final class UiScale {
    private UiScale() {}

    private static final String PREF_NODE = "opentrap.v1";
    private static final String PREF_KEY = "ui.scale";
    public static final double MIN = 0.75, MAX = 3.0;

    /**
     * The scale to use: the launch flag, else the saved preference, else a
     * default detected from the screen.
     */
    public static double desired() {
        String flag = System.getProperty("opentrap.scale");
        if (flag != null) {
            try { return clamp(Double.parseDouble(flag.trim())); } catch (Exception ignored) {}
        }
        try {
            String saved = Preferences.userRoot().node(PREF_NODE).get(PREF_KEY, null);
            if (saved != null) return clamp(Double.parseDouble(saved));
        } catch (Exception ignored) {}
        return detect();
    }

    /**
     * A sensible scale for this display, used on first run.
     *
     * <p>Defaulting to 1.0 made the interface unusably small on a high-density
     * screen, which is the common case now. Java cannot be relied on to tell us
     * the display is high-density: on a 4K panel under X11 it frequently reports
     * 96 DPI and a transform scale of 1.0, so there is nothing to key off except
     * the pixel dimensions themselves.
     *
     * <p>Only the three scales the view menu offers are returned, so the menu
     * always shows the current state accurately. A saved preference always
     * overrides this, so choosing a scale once makes the choice stick.
     */
    public static double detect() {
        try {
            java.awt.GraphicsConfiguration gc = java.awt.GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
            // When the platform already scales for us, do not scale twice.
            double already = gc.getDefaultTransform().getScaleX();
            if (already >= 1.5) return 1.0;

            java.awt.Rectangle b = gc.getBounds();
            int w = b.width, h = b.height;
            if (w >= 3200 || h >= 1800) return 2.0;    // 4K and above
            if (w >= 2400 || h >= 1350) return 1.5;    // 2.5K class
            return 1.0;
        } catch (Exception e) {
            return 1.0;   // headless or an unusual display: do not guess
        }
    }

    /** Whether the JVM is already running with the scale applied. */
    public static boolean applied() {
        return System.getProperty("opentrap.scaled") != null
            || System.getProperty("sun.java2d.uiScale") != null;
    }

    static double clamp(double v) { return Math.max(MIN, Math.min(MAX, v)); }

    private static void save(double v) {
        try { Preferences.userRoot().node(PREF_NODE).put(PREF_KEY, String.valueOf(v)); }
        catch (Exception ignored) {}
    }

    /**
     * Relaunch this application in a fresh JVM with the given scale applied,
     * then exit the current one. Called at startup (when a non-default scale is
     * saved) and whenever the user changes scale from the UI.
     *
     * @return true if a relaunch was started (caller should return/stop).
     */
    public static boolean relaunchWith(double scale, String[] args) {
        double s = clamp(scale);
        try {
            String javaBin = System.getProperty("java.home")
                    + java.io.File.separator + "bin" + java.io.File.separator + "java";
            String cp = System.getProperty("java.class.path");
            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin);
            // Carry forward the original JVM arguments (-Dopentrap.port, -Xmx, etc.)
            // so the relaunch for scaling does not drop them.
            for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.startsWith("-Dsun.java2d.uiScale") || arg.startsWith("-Dopentrap.scaled")
                        || arg.startsWith("-javaagent") || arg.startsWith("-agentlib")) continue;
                if (arg.startsWith("-D") || arg.startsWith("-X")) cmd.add(arg);
            }
            if (Math.abs(s - 1.0) > 0.001) cmd.add("-Dsun.java2d.uiScale=" + s);
            cmd.add("-Dopentrap.scaled=" + s);
            cmd.add("-cp");
            cmd.add(cp);
            cmd.add("opentrap.ui.Main");
            if (args != null) for (String a : args) cmd.add(a);
            new ProcessBuilder(cmd).inheritIO().start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Save a new scale and relaunch to apply it. */
    public static void changeTo(double scale, String[] args) {
        double s = clamp(scale);
        save(s);
        if (relaunchWith(s, args)) System.exit(0);
    }

    // Kept for call-site compatibility; the JVM now does all scaling, so these
    // are no-ops and fonts stay at their base sizes.
    public static void installDefaults() {}
    public static void rescaleFonts() {}
    public static double factor() { return 1.0; }
    public static int px(int base) { return base; }
    public static java.awt.Font font(String family, int style, int baseSize) {
        return new java.awt.Font(family, style, baseSize);
    }
}
