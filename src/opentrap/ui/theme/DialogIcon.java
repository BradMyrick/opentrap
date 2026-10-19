package opentrap.ui.theme;

import opentrap.ui.Theme;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;

/** Flat message-box marks: a filled circle with a glyph, in the colour of the message kind. */
public class DialogIcon implements Icon, UIResource {

    public enum Kind { INFO, QUESTION, WARNING, ERROR }

    private static final int SIZE = 36;
    private final Kind kind;

    public DialogIcon(Kind kind) { this.kind = kind; }

    @Override public int getIconWidth()  { return SIZE; }
    @Override public int getIconHeight() { return SIZE; }

    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color fill = switch (kind) {
            case INFO, QUESTION -> Theme.ACCENT;
            case WARNING -> new Color(0xE0, 0x8A, 0x1E);
            case ERROR -> new Color(0xD3, 0x3C, 0x3C);
        };
        g2.setColor(fill);
        g2.fillOval(x, y, SIZE, SIZE);
        String glyph = switch (kind) {
            case INFO -> "i";
            case QUESTION -> "?";
            case WARNING, ERROR -> "!";
        };
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Dialog", Font.BOLD, 20));
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (SIZE - fm.stringWidth(glyph)) / 2;
        int ty = y + (SIZE - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(glyph, tx, ty);
        g2.dispose();
    }
}
