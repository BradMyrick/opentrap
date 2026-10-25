package opentrap.ui;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flat icons for the site map tree, drawn so they need no files: a padlock
 * for a host, closed for https and open for http, a folder for a path and a
 * page for a request. Cached per kind and colour.
 */
public final class SiteIcons {
    private SiteIcons() {}

    private static final int SZ = 16;
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

    private interface Draw { void paint(Graphics2D g); }

    private static Icon cached(String key, Draw d) {
        return CACHE.computeIfAbsent(key, k -> {
            BufferedImage img = new BufferedImage(SZ, SZ, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            d.paint(g);
            g.dispose();
            return new ImageIcon(img);
        });
    }

    /** The tone a muted (out-of-scope) icon takes. */
    private static Color tone(Color c, boolean muted) {
        if (!muted) return c;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 110);
    }

    /**
     * A host: a padlock, closed and dark for https, open and red for http so
     * the plain-text site stands out. Muted when the host is out of scope.
     */
    public static Icon host(boolean secure, boolean muted) {
        return cached("host:" + secure + ":" + muted, g -> {
            Color body = tone(secure ? Theme.TEXT : new Color(0xD5, 0x1F, 0x3D), muted);
            g.setColor(body);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Shackle: an arc over the body; the open one swings up to the left.
            if (secure) {
                g.draw(new Arc2D.Float(4.2f, 1.2f, 7.6f, 8f, 0, 180, Arc2D.OPEN));
                g.drawLine(4, 5, 4, 7);
                g.drawLine(12, 5, 12, 7);
            } else {
                g.draw(new Arc2D.Float(2.2f, 0.6f, 7.2f, 7.6f, 0, 180, Arc2D.OPEN));
                g.drawLine(2, 4, 2, 5);
                g.drawLine(9, 4, 9, 7);
            }
            // Body with a keyhole.
            g.fill(new RoundRectangle2D.Float(3f, 7f, 10f, 8f, 2.5f, 2.5f));
            g.setColor(new Color(255, 255, 255, muted ? 150 : 230));
            g.fillOval(7, 9, 2, 2);
            g.fillRect(7, 10, 2, 3);
        });
    }

    /** A path segment: a flat folder. */
    public static Icon folder(boolean muted) {
        return cached("folder:" + muted, g -> {
            Color c = tone(new Color(0xE8, 0xA2, 0x3B), muted);
            g.setColor(c);
            g.fill(new RoundRectangle2D.Float(1f, 3f, 6.5f, 4f, 2f, 2f));      // tab
            g.fill(new RoundRectangle2D.Float(1f, 5f, 14f, 9f, 2f, 2f));       // body
            g.setColor(tone(new Color(0xF4, 0xC2, 0x6B), muted));
            g.fill(new RoundRectangle2D.Float(1f, 7f, 14f, 7f, 2f, 2f));       // front
        });
    }

    /** A request: a page with a folded corner. */
    public static Icon page(boolean muted) {
        return cached("page:" + muted, g -> {
            Color edge = tone(Theme.MUTED, muted);
            g.setColor(tone(Color.WHITE, muted));
            g.fillRect(3, 1, 10, 14);
            g.setColor(edge);
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(3, 1, 9, 1); g.drawLine(9, 1, 13, 5); g.drawLine(13, 5, 13, 15);
            g.drawLine(13, 15, 3, 15); g.drawLine(3, 15, 3, 1);
            g.drawLine(9, 1, 9, 5); g.drawLine(9, 5, 13, 5);
            g.drawLine(5, 8, 11, 8); g.drawLine(5, 11, 11, 11);
        });
    }
}
