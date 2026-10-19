package opentrap.intruder;

import javax.swing.JComponent;
import java.util.List;

/**
 * A source of payload strings for one payload set.
 *
 * Each generator owns a small configuration panel and can both report its
 * payload count (cheaply, for the live labels on the payloads tab) and
 * materialise the payloads when an attack runs.
 */
public interface PayloadGenerator {

    /** Hard limit on how many payloads a single set will materialise. */
    int CAP = 100_000;

    /** User-visible type name, exactly as it appears in the combo. */
    String label();

    /** The configuration panel shown when this type is selected. */
    JComponent config();

    /** Materialised payloads, capped at {@link #CAP}. */
    List<String> generate();

    /** Payload count, or -1 when the count is unknown or unbounded. */
    int count();

    /** Register a callback fired whenever the configuration changes. */
    default void onChange(Runnable r) {}

    /**
     * Supply the base items that transforming generators (character and case
     * substitution) work from. Ignored by generators that produce their own
     * payloads.
     */
    default void setBase(List<String> base) {}
}
