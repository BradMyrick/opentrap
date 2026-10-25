package opentrap.ui.theme;

import opentrap.ui.Theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** A thin scrollbar: no arrow buttons, a rounded thumb that darkens under the pointer. */
public class FlatScrollBarUI extends BasicScrollBarUI {

    public static ComponentUI createUI(JComponent c) { return new FlatScrollBarUI(); }

    @Override protected void configureScrollBarColors() {
        thumbColor = Theme.SURFACE_3;
        thumbHighlightColor = Theme.SURFACE_3;
        thumbDarkShadowColor = Theme.SURFACE_3;
        thumbLightShadowColor = Theme.SURFACE_3;
        trackColor = Theme.BG;
    }

    @Override protected JButton createDecreaseButton(int o) { return zero(); }
    @Override protected JButton createIncreaseButton(int o) { return zero(); }

    private static JButton zero() {
        JButton b = new JButton();
        Dimension d = new Dimension(0, 0);
        b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
        b.setFocusable(false);
        return b;
    }

    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        g.setColor(Theme.BG);
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isThumbRollover() || isDragging ? Theme.SCROLL_THUMB_HOVER : Theme.SCROLL_THUMB);
        int inset = 2;
        g2.fill(new RoundRectangle2D.Float(r.x + inset, r.y + inset, r.width - inset * 2, r.height - inset * 2, 6, 6));
        g2.dispose();
    }

    @Override protected Dimension getMinimumThumbSize() { return new Dimension(10, 28); }
}
