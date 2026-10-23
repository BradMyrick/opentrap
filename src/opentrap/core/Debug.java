package opentrap.core;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Verbose diagnostic logging, enabled with the {@code --debug} command-line
 * flag. When on, every log line is written to the console and appended to a
 * dated file under {@code ~/.opentrap/logs/}, and all uncaught exceptions
 * (including on the Swing event thread) are captured there too, so a crash or a
 * failed launch leaves a full trace to work from instead of vanishing.
 *
 * When off, this is nearly free: {@link #log} returns immediately.
 */
public final class Debug {

    private static volatile boolean enabled;
    private static PrintStream file;
    private static Path logPath;

    private static final DateTimeFormatter LINE_TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Debug() {}

    public static boolean enabled() { return enabled; }

    /** The file the current session is logging to, or null if not enabled. */
    public static Path logFile() { return logPath; }

    /** Turn on debug logging: open the dated log file and capture uncaught exceptions. */
    public static synchronized void enable() {
        if (enabled) return;
        enabled = true;
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".opentrap", "logs");
            Files.createDirectories(dir);
            logPath = dir.resolve("opentrap-" + LocalDateTime.now().format(FILE_TS) + ".log");
            file = new PrintStream(new FileOutputStream(logPath.toFile(), true), true, "UTF-8");
        } catch (Exception e) {
            System.err.println("[debug] could not open log file: " + e);
        }
        // Capture everything that would otherwise be lost, including Swing EDT
        // exceptions, which route to the thread's default uncaught handler.
        Thread.setDefaultUncaughtExceptionHandler(
                (t, ex) -> error("uncaught exception in thread '" + t.getName() + "'", ex));

        log("debug logging enabled");
        if (logPath != null) log("log file: " + logPath);
        log("OpenTRAP " + opentrap.ui.Main.VERSION
                + " | java " + System.getProperty("java.version")
                + " | " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " | scale uiScale=" + System.getProperty("sun.java2d.uiScale", "?")
                + " scaled=" + System.getProperty("opentrap.scaled", "?"));
    }

    /** Write a timestamped line to the console and the log file (no-op unless enabled). */
    public static void log(String msg) {
        if (!enabled) return;
        String line = "[" + LocalDateTime.now().format(LINE_TS) + "] " + msg;
        System.err.println(line);
        if (file != null) file.println(line);
    }

    /** Log a message and a full stack trace. Always prints, so crashes are never silent. */
    public static void error(String msg, Throwable ex) {
        String line = "[" + LocalDateTime.now().format(LINE_TS) + "] ERROR: " + msg;
        System.err.println(line);
        if (ex != null) ex.printStackTrace(System.err);
        if (file != null) {
            file.println(line);
            if (ex != null) ex.printStackTrace(file);
        }
    }
}
