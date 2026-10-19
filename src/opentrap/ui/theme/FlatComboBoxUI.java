package opentrap.ui.theme;

import opentrap.ui.Theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.geom.Path2D;

/** A flat combo box: rounded outline, a chevron instead of a bevelled arrow button, plain popup. */
public class FlatComboBoxUI extends BasicComboBoxUI {

    public static ComponentUI createUI(JComponent c) { return new FlatComboBoxUI(); }

    @Override protected void installDefaults() {
        super.installDefaults();
        comboBox.setOpaque(false);
    }

    @Override protected JButton createArrowButton() {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(comboBox.isEnabled() ? Theme.MUTED : Theme.BORDER);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                Path2D p = new Path2D.Float();
                p.moveTo(cx - 4, cy - 2);
                p.lineTo(cx, cy + 2);
                p.lineTo(cx + 4, cy - 2);
                g2.draw(p);
                g2.dispose();
            }
        };
        b.setContentAreaFilled(false);
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(20, 20));
        return b;
    }

    @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
        g.setColor(comboBox.isEnabled() ? Theme.BG : Theme.SURFACE);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override public void paint(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(comboBox.isEnabled() ? Theme.BG : Theme.SURFACE);
        g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), Theme.RADIUS * 2, Theme.RADIUS * 2);
        g2.dispose();
        super.paint(g, c);
    }

    @Override protected ComboPopup createPopup() {
        BasicComboPopup p = new BasicComboPopup(comboBox) {
            @Override protected void configurePopup() {
                super.configurePopup();
                setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            }
            @Override protected void configureList() {
                super.configureList();
                list.setSelectionBackground(Theme.ACCENT_LIGHT);
                list.setSelectionForeground(Theme.TEXT);
                list.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
            }
        };
        return p;
    }
}
