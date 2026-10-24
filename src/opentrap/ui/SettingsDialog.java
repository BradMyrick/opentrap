package opentrap.ui;

import opentrap.core.SuiteContext;

import javax.swing.*;
import java.awt.*;

/**
 * The suite Settings dialog: a left-hand category list and a settings pane on the
 * right, opened from the gear in the top bar.
 *
 * <p>This class is the dialog shell only. The "General" pane hosts OpenTRAP's real
 * options; the remaining category panes are laid out but not implemented yet, so
 * each is an {@link Unbuilt} panel with its own reference code - one per category,
 * so a contributor can build a single settings pane at a time.
 */
public final class SettingsDialog {

    private SettingsDialog() {}

    public static void open(Window owner, SuiteContext ctx) {
        final JDialog d = new JDialog(owner, "Settings", Dialog.ModalityType.APPLICATION_MODAL);

        // name -> pane. "General" is real; the rest are unbuilt, one code each.
        Object[][] cats = {
            {"General",               new SuiteOptionsPanel(ctx)},
            {"Proxy",                 Unbuilt.SETTINGS_PROXY.panel()},
            {"Intruder",              Unbuilt.SETTINGS_INTRUDER.panel()},
            {"Repeater",              Unbuilt.SETTINGS_REPEATER.panel()},
            {"Sequencer",             Unbuilt.SETTINGS_SEQUENCER.panel()},
            {"Comparer",              Unbuilt.SETTINGS_COMPARER.panel()},
            {"Browser",               Unbuilt.SETTINGS_BROWSER.panel()},
            {"Project",               Unbuilt.SETTINGS_PROJECT.panel()},
            {"Sessions",              Unbuilt.SETTINGS_SESSIONS.panel()},
            {"Suite",                 Unbuilt.SETTINGS_SUITE.panel()},
            {"Extensions",            Unbuilt.SETTINGS_EXTENSIONS.panel()},
            {"Configuration library", Unbuilt.SETTINGS_CONFIG_LIBRARY.panel()},
        };

        CardLayout cards = new CardLayout();
        final JPanel right = new JPanel(cards);
        right.setOpaque(false);
        String[] names = new String[cats.length];
        for (int i = 0; i < cats.length; i++) {
            names[i] = (String) cats[i][0];
            right.add((Component) cats[i][1], names[i]);
        }

        JList<String> nav = new JList<>(names);
        nav.setFont(Ui.PLAIN);
        nav.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nav.setSelectedIndex(0);
        nav.setFixedCellHeight(26);
        nav.setBorder(Ui.pad(4, 4, 4, 4));
        nav.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && nav.getSelectedValue() != null) {
                cards.show(right, nav.getSelectedValue());
            }
        });
        JScrollPane navScroll = new JScrollPane(nav);
        navScroll.setPreferredSize(new Dimension(190, 10));
        navScroll.setBorder(BorderFactory.createLineBorder(Ui.GRID));

        JPanel body = new JPanel(new BorderLayout(10, 0));
        body.setBorder(Ui.pad(12, 12, 8, 12));
        body.add(navScroll, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);

        JButton close = Ui.button("Close");
        close.addActionListener(e -> d.dispose());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        bar.add(close);

        JPanel root = new JPanel(new BorderLayout());
        root.add(body, BorderLayout.CENTER);
        root.add(bar, BorderLayout.SOUTH);

        d.setContentPane(root);
        d.setSize(860, 580);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
