package opentrap.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Byte-array helpers. Everything here works on raw bytes; HTTP is not text. */
public final class Bytes {
    private Bytes() {}

    public static final byte[] CRLF  = {13, 10};
    public static final byte[] CRLF2 = {13, 10, 13, 10};

    public static int indexOf(byte[] data, byte[] pattern, int from) {
        if (data == null || pattern == null || pattern.length == 0) return -1;
        outer:
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    public static int indexOf(byte[] data, byte[] pattern) { return indexOf(data, pattern, 0); }

    /** Case-insensitive ASCII search. */
    public static int indexOfIgnoreCase(byte[] data, byte[] pattern, int from) {
        if (data == null || pattern == null || pattern.length == 0) return -1;
        outer:
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (lower(data[i + j]) != lower(pattern[j])) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static byte lower(byte b) {
        return (b >= 'A' && b <= 'Z') ? (byte) (b + 32) : b;
    }

    public static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) if (p != null) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            if (p == null) continue;
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }

    public static byte[] slice(byte[] data, int from, int to) {
        if (data == null) return new byte[0];
        int a = Math.max(0, Math.min(from, data.length));
        int b = Math.max(a, Math.min(to, data.length));
        byte[] out = new byte[b - a];
        System.arraycopy(data, a, out, 0, b - a);
        return out;
    }

    /** ISO-8859-1 keeps a 1:1 byte/char mapping, so round-trips are lossless. */
    public static String str(byte[] b) {
        return b == null ? "" : new String(b, StandardCharsets.ISO_8859_1);
    }

    public static byte[] bytes(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Split on CRLF or bare LF, preserving empty lines. */
    public static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                int end = i;
                if (end > start && s.charAt(end - 1) == '\r') end--;
                out.add(s.substring(start, end));
                start = i + 1;
            }
        }
        if (start < s.length()) out.add(s.substring(start));
        return out;
    }

    public static boolean isPrintableText(byte[] data, int limit) {
        if (data == null) return true;
        int n = Math.min(data.length, limit);
        for (int i = 0; i < n; i++) {
            int v = data[i] & 0xff;
            if (v == 9 || v == 10 || v == 13) continue;
            if (v < 32 || v > 126) return false;
        }
        return true;
    }

    public static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
