package opentrap.comparer;

import opentrap.core.Bytes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The token diff behind a compare window. Modest inputs get a real LCS over
 * words or bytes; past the cap the tokens become lines, and past that a
 * prefix/suffix split, so two large responses can never hang the window.
 */
final class Diff {
    private Diff() {}

    enum Kind { SAME, DELETED, ADDED, MODIFIED }

    /** One aligned span. An empty side means that pane shows nothing here. */
    record Block(Kind kind, String left, String right) {}

    /** Tokens per side above which words and bytes give way to lines. */
    private static final int MAX_TOKENS = 20_000;

    /** Cells the LCS table may use; beyond it the tokens are made coarser. */
    private static final long CELL_BUDGET = 4_000_000L;

    private static final int SAME_OP = 0;
    private static final int DELETE_OP = 1;
    private static final int INSERT_OP = 2;

    private static final byte DIAGONAL = 0;
    private static final byte UP = 1;
    private static final byte LEFT = 2;

    static List<Block> compare(byte[] a, byte[] b, boolean words) {
        String left = Bytes.str(a);
        String right = Bytes.str(b);
        String[] ta = words ? splitWords(left) : splitBytes(left);
        String[] tb = words ? splitWords(right) : splitBytes(right);

        List<Block> blocks = null;
        if (ta.length <= MAX_TOKENS && tb.length <= MAX_TOKENS) blocks = tokenDiff(ta, tb);
        if (blocks == null) blocks = tokenDiff(splitLines(left), splitLines(right));
        if (blocks == null) blocks = coarse(left, right);
        return blocks;
    }

    static int differences(List<Block> blocks) {
        int n = 0;
        for (Block b : blocks) if (b.kind() != Kind.SAME) n++;
        return n;
    }

    // ---- tokenising ----

    /** Words and the whitespace between them, each run its own token. */
    private static String[] splitWords(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            boolean space = Character.isWhitespace(s.charAt(i));
            int j = i + 1;
            while (j < s.length() && Character.isWhitespace(s.charAt(j)) == space) j++;
            out.add(s.substring(i, j));
            i = j;
        }
        return out.toArray(new String[0]);
    }

    private static String[] splitBytes(String s) {
        String[] out = new String[s.length()];
        for (int i = 0; i < s.length(); i++) out[i] = String.valueOf(s.charAt(i));
        return out;
    }

    private static String[] splitLines(String s) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                out.add(s.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < s.length()) out.add(s.substring(start));
        return out.toArray(new String[0]);
    }

    // ---- diffing ----

    /** Null when the differing core is too big for an LCS over these tokens. */
    private static List<Block> tokenDiff(String[] a, String[] b) {
        int head = 0;
        while (head < a.length && head < b.length && a[head].equals(b[head])) head++;
        int tail = 0;
        while (tail < a.length - head && tail < b.length - head
                && a[a.length - 1 - tail].equals(b[b.length - 1 - tail])) tail++;

        int n = a.length - head - tail;
        int m = b.length - head - tail;
        if ((long) n * m > CELL_BUDGET) return null;

        List<Block> out = new ArrayList<>();
        if (head > 0) addSame(out, join(a, 0, head));
        out.addAll(core(a, head, n, b, head, m));
        if (tail > 0) addSame(out, join(a, a.length - tail, a.length));
        return out;
    }

    private static List<Block> core(String[] a, int aFrom, int n, String[] b, int bFrom, int m) {
        if (n == 0 && m == 0) return List.of();
        if (n == 0) return List.of(new Block(Kind.ADDED, "", join(b, bFrom, bFrom + m)));
        if (m == 0) return List.of(new Block(Kind.DELETED, join(a, aFrom, aFrom + n), ""));

        // intern the tokens so the inner loop only ever compares ints
        Map<String, Integer> ids = new HashMap<>();
        int[] x = new int[n];
        int[] y = new int[m];
        for (int i = 0; i < n; i++) x[i] = id(ids, a[aFrom + i]);
        for (int j = 0; j < m; j++) y[j] = id(ids, b[bFrom + j]);

        byte[][] from = new byte[n + 1][m + 1];
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (x[i - 1] == y[j - 1]) {
                    current[j] = previous[j - 1] + 1;
                    from[i][j] = DIAGONAL;
                } else if (previous[j] >= current[j - 1]) {
                    current[j] = previous[j];
                    from[i][j] = UP;
                } else {
                    current[j] = current[j - 1];
                    from[i][j] = LEFT;
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        List<int[]> ops = new ArrayList<>();
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && from[i][j] == DIAGONAL) {
                ops.add(new int[]{SAME_OP, aFrom + i - 1});
                i--;
                j--;
            } else if (j == 0 || (i > 0 && from[i][j] == UP)) {
                ops.add(new int[]{DELETE_OP, aFrom + i - 1});
                i--;
            } else {
                ops.add(new int[]{INSERT_OP, bFrom + j - 1});
                j--;
            }
        }
        Collections.reverse(ops);
        return blocks(ops, a, b);
    }

    /**
     * Group the edit script into coloured spans. A deletion that runs straight
     * into an insertion is a replacement, which both panes show as modified.
     */
    private static List<Block> blocks(List<int[]> ops, String[] a, String[] b) {
        List<Block> out = new ArrayList<>();
        int k = 0;
        while (k < ops.size()) {
            if (ops.get(k)[0] == SAME_OP) {
                StringBuilder same = new StringBuilder();
                while (k < ops.size() && ops.get(k)[0] == SAME_OP) same.append(a[ops.get(k++)[1]]);
                addSame(out, same.toString());
            } else {
                StringBuilder deleted = new StringBuilder();
                StringBuilder added = new StringBuilder();
                while (k < ops.size() && ops.get(k)[0] != SAME_OP) {
                    int[] op = ops.get(k++);
                    if (op[0] == DELETE_OP) deleted.append(a[op[1]]);
                    else added.append(b[op[1]]);
                }
                if (deleted.length() > 0 && added.length() > 0) {
                    out.add(new Block(Kind.MODIFIED, deleted.toString(), added.toString()));
                } else if (deleted.length() > 0) {
                    out.add(new Block(Kind.DELETED, deleted.toString(), ""));
                } else {
                    out.add(new Block(Kind.ADDED, "", added.toString()));
                }
            }
        }
        return out;
    }

    /** Last resort for inputs too big to tokenise: one modified middle. */
    private static List<Block> coarse(String a, String b) {
        int head = 0;
        while (head < a.length() && head < b.length() && a.charAt(head) == b.charAt(head)) head++;
        int tail = 0;
        while (tail < a.length() - head && tail < b.length() - head
                && a.charAt(a.length() - 1 - tail) == b.charAt(b.length() - 1 - tail)) tail++;

        List<Block> out = new ArrayList<>();
        if (head > 0) addSame(out, a.substring(0, head));
        String middleLeft = a.substring(head, a.length() - tail);
        String middleRight = b.substring(head, b.length() - tail);
        if (!middleLeft.isEmpty() && !middleRight.isEmpty()) {
            out.add(new Block(Kind.MODIFIED, middleLeft, middleRight));
        } else if (!middleLeft.isEmpty()) {
            out.add(new Block(Kind.DELETED, middleLeft, ""));
        } else if (!middleRight.isEmpty()) {
            out.add(new Block(Kind.ADDED, "", middleRight));
        }
        if (tail > 0) addSame(out, a.substring(a.length() - tail));
        return out;
    }

    private static void addSame(List<Block> out, String text) {
        if (!text.isEmpty()) out.add(new Block(Kind.SAME, text, text));
    }

    private static int id(Map<String, Integer> ids, String token) {
        Integer known = ids.get(token);
        if (known != null) return known;
        int next = ids.size();
        ids.put(token, next);
        return next;
    }

    private static String join(String[] tokens, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) sb.append(tokens[i]);
        return sb.toString();
    }
}
