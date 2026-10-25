package opentrap.ui;

import opentrap.core.ProjectOptions;
import opentrap.core.SuiteContext;

import javax.swing.*;
import java.awt.*;

/**
 * The strip above the site map and the proxy history that says the proxy is no
 * longer recording out-of-scope traffic, with a button to turn it back on. It
 * shows itself whenever the setting goes off and can be closed until the next
 * time; closing it does not change the setting.
 */
public final class OutOfScopeBanner extends JPanel {

    private boolean dismissed;

    public OutOfScopeBanner(SuiteContext ctx) {
        super(new BorderLayout());
        setBackground(Theme.ACCENT_FAINT);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.GRID), Ui.pad(4, 10, 4, 6)));

        JLabel text = Ui.label("Logging of out-of-scope Proxy traffic is disabled");
        text.setIcon(ToolIcons.info());
        text.setIconTextGap(8);
        JButton reenable = Ui.button("Re-enable", "record out-of-scope traffic again");
        reenable.addActionListener(e -> ctx.settings().setBool(ProjectOptions.LOG_OUT_OF_SCOPE, true));
        JPanel middle = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        middle.setOpaque(false);
        middle.add(text);
        middle.add(reenable);
        add(middle, BorderLayout.CENTER);

        JButton close = new JButton("×");
        close.setFont(Ui.PLAIN.deriveFont(Font.BOLD, 15f));
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFocusable(false);
        close.setToolTipText("hide this message");
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> { dismissed = true; refresh(ctx); });
        add(close, BorderLayout.EAST);

        ctx.settings().addListener(key -> {
            if (ProjectOptions.LOG_OUT_OF_SCOPE.equals(key)) {
                if (ProjectOptions.logOutOfScope(ctx.settings())) dismissed = false;
                SwingUtilities.invokeLater(() -> refresh(ctx));
            }
        });
        ctx.addScopeListener(() -> SwingUtilities.invokeLater(() -> refresh(ctx)));
        refresh(ctx);
    }

    /** Shown only while there is a scope for the setting to apply to. */
    private void refresh(SuiteContext ctx) {
        setVisible(!dismissed && !ctx.scope().isEmpty() && !ProjectOptions.logOutOfScope(ctx.settings()));
        revalidate();
        repaint();
    }
}
