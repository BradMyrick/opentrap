package opentrap.intruder;

/** The four Intruder attack strategies and how they consume payload sets. */
public enum AttackType {

    SNIPER("sniper"),
    BATTERING_RAM("battering ram"),
    PITCHFORK("pitchfork"),
    CLUSTER_BOMB("cluster bomb");

    private final String label;

    AttackType(String label) { this.label = label; }

    @Override public String toString() { return label; }

    /** Number of payload sets this attack uses for a given position count. */
    public int setCount(int positions) {
        return (this == PITCHFORK || this == CLUSTER_BOMB) ? Math.max(1, positions) : 1;
    }

    /** Total number of requests for the given position count and set sizes. */
    public long requestCount(int positions, int[] sizes) {
        if (sizes.length == 0) return 0;
        return switch (this) {
            case SNIPER -> (long) positions * nonNeg(sizes[0]);
            case BATTERING_RAM -> nonNeg(sizes[0]);
            case PITCHFORK -> {
                long min = Long.MAX_VALUE;
                for (int i = 0; i < positions && i < sizes.length; i++) min = Math.min(min, nonNeg(sizes[i]));
                yield min == Long.MAX_VALUE ? 0 : min;
            }
            case CLUSTER_BOMB -> {
                long product = 1;
                for (int i = 0; i < positions && i < sizes.length; i++) product *= nonNeg(sizes[i]);
                yield product;
            }
        };
    }

    private static long nonNeg(int n) { return Math.max(n, 0); }

    public static AttackType[] ordered() {
        return new AttackType[]{SNIPER, BATTERING_RAM, PITCHFORK, CLUSTER_BOMB};
    }
}
