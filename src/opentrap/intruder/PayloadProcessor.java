package opentrap.intruder;

import opentrap.ui.Ui;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The payload-processing panel shown beneath a payload set: case changes, a
 * regular-expression match/replace, and an encoding choice. {@link #process}
 * runs each payload through the configured chain before it is used.
 */
public class PayloadProcessor extends JPanel {

    /** The case options shared with the case-substitution generator. */
    static final String[] CASE_OPTIONS = {
            "do not modify", "to lower case", "to upper case", "to Propername", "to ProperName"
    };

    private final JComboBox<String> caseCombo = Ui.combo(CASE_OPTIONS);

    private final JTextField matchField   = Ui.field("", 14);
    private final JTextField replaceField = Ui.field("", 14);
    private final JCheckBox replaceAll     = Ui.check("replace all", true);

    private final JTextField prefixField = Ui.field("", 14);
    private final JTextField suffixField = Ui.field("", 14);

    private final JRadioButton encNone = Ui.radio("do not encode", false);
    private final JRadioButton encUrl  = Ui.radio("URL-encode these characters:", true);
    private final JRadioButton encB64  = Ui.radio("base-64 encode", false);
    private final JRadioButton encHash = Ui.radio("hash", false);
    private final JTextField urlChars  = Ui.field(" ./\\=<>?+&*;:#%@\"'", 14);
    private final JComboBox<String> hashAlgo =
            Ui.combo(new String[]{"MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512"});

    public PayloadProcessor() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        ButtonGroup enc = new ButtonGroup();
        enc.add(encNone); enc.add(encUrl); enc.add(encB64); enc.add(encHash);

        JLabel heading = Ui.heading("payload processing");
        heading.setAlignmentX(LEFT_ALIGNMENT);
        add(heading);
        add(Box.createVerticalStrut(6));

        add(left(Ui.row(Ui.label("case:"), caseCombo)));
        add(left(Ui.row(Ui.label("match regex:"), matchField,
                Ui.label("replace with:"), replaceField, replaceAll)));
        add(left(Ui.row(Ui.label("add prefix:"), prefixField,
                Ui.label("add suffix:"), suffixField)));
        add(left(Ui.bold("encoding")));
        add(left(encNone));
        add(left(Ui.row(encUrl, urlChars)));
        add(left(encB64));
        add(left(Ui.row(encHash, hashAlgo)));
    }

    private JComponent left(JComponent c) { c.setAlignmentX(LEFT_ALIGNMENT); return c; }

    // ---------------- processing ----------------

    public String process(String payload) {
        String s = applyCase(payload, caseCombo.getSelectedIndex());
        s = applyRegex(s);
        s = prefixField.getText() + s + suffixField.getText();
        s = applyEncoding(s);
        return s;
    }

    private String applyRegex(String s) {
        String rx = matchField.getText();
        if (rx.isEmpty()) return s;
        try {
            Matcher m = Pattern.compile(rx).matcher(s);
            String with = Matcher.quoteReplacement(replaceField.getText());
            return replaceAll.isSelected() ? m.replaceAll(with) : m.replaceFirst(with);
        } catch (Exception e) {
            return s;
        }
    }

    private String applyEncoding(String s) {
        if (encUrl.isSelected()) {
            String chars = urlChars.getText();
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (chars.indexOf(c) >= 0) {
                    out.append('%').append(String.format("%02X", (int) c & 0xFF));
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }
        if (encB64.isSelected()) {
            return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.ISO_8859_1));
        }
        if (encHash.isSelected()) {
            try {
                MessageDigest md = MessageDigest.getInstance((String) hashAlgo.getSelectedItem());
                byte[] d = md.digest(s.getBytes(StandardCharsets.ISO_8859_1));
                StringBuilder sb = new StringBuilder(d.length * 2);
                for (byte b : d) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                return s;
            }
        }
        return s;
    }

    /** Apply one of {@link #CASE_OPTIONS} by index. */
    static String applyCase(String s, int mode) {
        return switch (mode) {
            case 1 -> s.toLowerCase();
            case 2 -> s.toUpperCase();
            case 3 -> properName(s);       // Propername: first letter only
            case 4 -> properCase(s);       // ProperName: each word
            default -> s;
        };
    }

    private static String properName(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static String properCase(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean startOfWord = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
                startOfWord = false;
            } else {
                out.append(c);
                startOfWord = true;
            }
        }
        return out.toString();
    }
}
