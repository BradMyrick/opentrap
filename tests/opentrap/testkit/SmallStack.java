package opentrap.testkit;

/**
 * Runs code on a thread with a deliberately small stack, so a test can prove
 * in seconds that an implementation cannot recurse per input character: the
 * JSON colouring overflow blew a ~1 MB event-queue stack with a 100 KB value,
 * so a 256 KB stack trips any per-character recursion within a few thousand
 * characters.
 */
public final class SmallStack {

    /** The probe thread's stack size. */
    public static final long STACK_BYTES = 256L * 1024;

    /** How long to wait for the probe before failing. */
    public static final long TIMEOUT_MILLIS = 30_000;

    private SmallStack() {}

    /**
     * Runs {@code r} on a {@link #STACK_BYTES}-byte stack and returns whatever
     * escaped it, or null when it ran to completion.
     */
    public static Throwable probe(Runnable r) {
        java.util.concurrent.atomic.AtomicReference<Throwable> escaped =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread probe = new Thread(null, r, "small-stack-probe", STACK_BYTES);
        probe.setUncaughtExceptionHandler((t, e) -> escaped.set(e));
        probe.start();
        try {
            probe.join(TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (probe.isAlive()) throw new AssertionError("probe did not finish in " + TIMEOUT_MILLIS + " ms");
        return escaped.get();
    }
}
