package opentrap.ui;

import javax.swing.*;
import java.awt.*;

/**
 * A tab button with vertical text, for the slim strips on the right edge of a
 * panel (the message editor's Inspector / Notes, Intruder's Payloads / Resource
 * pool / Settings). Selected: accent text on the selection tint.
 */
public final class VerticalTab extends JToggleButton {
    public VerticalTab(String text) {
        super(text);
        setFont(Ui.PLAIN);
        setFocusable(false);
        setContentAreaFilled(false);
        setBorder(BorderFactory.createEmptyBorder());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.getHeight() + 10, fm.stringWidth(getText()) + 26);
    }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (isSelected()) { g2.setColor(Ui.SELECTION); g2.fillRect(0, 0, getWidth(), getHeight()); }
        g2.setColor(isSelected() ? Ui.ACCENT : Theme.MUTED);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(getText());
        g2.rotate(-Math.PI / 2, getWidth() / 2.0, getHeight() / 2.0);
        g2.drawString(getText(), (float) (getWidth() / 2.0 - tw / 2.0),
                (float) (getHeight() / 2.0 + (fm.getAscent() - fm.getDescent()) / 2.0));
        g2.dispose();
    }
}
