package opentrap.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Small monochrome glyphs for the top-right tool strip, drawn so they need no files. */
final class ToolIcons {
    private ToolIcons() {}

    private static final int SZ = 16;

    private interface Draw { void paint(Graphics2D g); }

    private static Icon make(Draw d) {
        BufferedImage img = new BufferedImage(SZ, SZ, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.MUTED);
        g.setStroke(new BasicStroke(1.4f));
        d.paint(g);
        g.dispose();
        return new ImageIcon(img);
    }

    /** A globe: a circle with one meridian and the equator. */
    static Icon browser() {
        return make(g -> {
            g.drawOval(1, 1, 13, 13);
            g.drawOval(5, 1, 5, 13);  // meridian ellipse
            g.drawLine(1, 8, 14, 8);  // equator
            g.drawLine(8, 1, 8, 14);  // axis
        });
    }

    /** A console: a rounded panel with a prompt chevron and a caret line. */
    static Icon console() {
        return make(g -> {
            g.drawRoundRect(1, 2, 13, 11, 3, 3);
            g.drawLine(4, 6, 7, 8);
            g.drawLine(7, 8, 4, 10);
            g.drawLine(9, 10, 12, 10);
        });
    }

    /** A magnifier. */
    static Icon search() {
        return make(g -> {
            g.drawOval(2, 2, 8, 8);
            g.drawLine(10, 10, 14, 14);
        });
    }

    /** A gear: a ring with eight teeth and a hub. */
    static Icon gear() {
        return make(g -> {
            double cx = 8, cy = 8;
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4.0;
                int x1 = (int) Math.round(cx + Math.cos(a) * 5);
                int y1 = (int) Math.round(cy + Math.sin(a) * 5);
                int x2 = (int) Math.round(cx + Math.cos(a) * 7);
                int y2 = (int) Math.round(cy + Math.sin(a) * 7);
                g.drawLine(x1, y1, x2, y2);
            }
            g.drawOval(4, 4, 8, 8);
            g.drawOval(6, 6, 4, 4);
        });
    }
}
