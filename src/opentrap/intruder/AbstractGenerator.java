package opentrap.intruder;

import java.util.List;

/** Shared change-notification plumbing for the payload generators. */
abstract class AbstractGenerator implements PayloadGenerator {

    private Runnable onChange;
    protected java.util.List<String> base = java.util.List.of();

    @Override public void onChange(Runnable r) { this.onChange = r; }

    @Override public void setBase(List<String> b) {
        this.base = b == null ? java.util.List.of() : b;
    }

    /** Notify the owning set that the count may have changed. */
    protected void fire() { if (onChange != null) onChange.run(); }
}
