package opentrap.extensions;

import opentrap.core.ToolModule;
import opentrap.ui.Ui;
import opentrap.ui.Unbuilt;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Extensions tool: load and manage add-ons that customise the suite. This is a
 * laid-out placeholder - the structure and controls are in place, but loading
 * and the catalogue are not built yet. Controls stay enabled and open the shared
 * "not built yet" dialog ({@link Unbuilt}) so a user can contribute the feature
 * rather than hit a dead end. The real work is tracked in the dev plan.
 */
public class ExtensionsTool implements ToolModule {

    @Override public String name() { return "extensions"; }

    @Override public Tab[] tabs() {
        return new Tab[]{
                new Tab("Installed", installed()),
                new Tab("Store", Unbuilt.EXT_STORE.panel()),
                new Tab("API", Unbuilt.EXT_API.panel()),
                new Tab("Custom checks", Unbuilt.EXT_CUSTOM_CHECKS.panel()),
                new Tab("Filters", Unbuilt.EXT_FILTERS.panel()),
                new Tab("Settings", Unbuilt.EXT_SETTINGS.panel()),
        };
    }

    /** The Installed sub-tab: the management table and controls, laid out but inert. */
    private JComponent installed() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(Ui.pad(10, 12, 10, 12));
        root.setOpaque(false);

        // Header: heading + description + a search box on the right.
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        JPanel titleCol = new JPanel();
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        titleCol.setOpaque(false);
        JLabel heading = Ui.heading("Extensions");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel impact = Ui.label("Total estimated system impact: none");
        impact.setForeground(opentrap.ui.Theme.MUTED);
        impact.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleCol.add(heading);
        titleCol.add(Box.createVerticalStrut(2));
        titleCol.add(impact);
        head.add(titleCol, BorderLayout.WEST);
        JTextField search = Ui.field("", 18);
        JPanel searchWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        searchWrap.setOpaque(false);
        searchWrap.add(Ui.label("Search "));
        searchWrap.add(search);
        head.add(searchWrap, BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        // Centre: left button column + extensions table.
        JPanel centre = new JPanel(new BorderLayout(8, 0));
        centre.setOpaque(false);

        JPanel buttons = new JPanel(new GridLayout(4, 1, 0, 4));
        buttons.setOpaque(false);
        for (String b : new String[]{"Add", "Remove", "Up", "Down"}) {
            JButton btn = Ui.button(b);
            Unbuilt.EXT_INSTALLED.attach(btn);
            buttons.add(btn);
        }
        JPanel btnWrap = new JPanel(new BorderLayout());
        btnWrap.setOpaque(false);
        btnWrap.add(buttons, BorderLayout.NORTH);
        centre.add(btnWrap, BorderLayout.WEST);

        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Loaded", "Type", "Name", "System impact", "Auto-reload"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        Ui.styleTable(table);
        JScrollPane tableScroll = Ui.scroll(table);
        tableScroll.setPreferredSize(new Dimension(600, 150));
        centre.add(tableScroll, BorderLayout.CENTER);

        // Bottom: Details / Output / Errors for the selected extension.
        JTabbedPane detail = new JTabbedPane();
        detail.setFont(Ui.PLAIN);
        detail.addTab("Details", detailPane());
        detail.addTab("Output", consolePane());
        detail.addTab("Errors", consolePane());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centre, detail);
        split.setResizeWeight(0.45);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);

        return root;
    }

    private JComponent detailPane() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.setBorder(Ui.pad(8, 4, 4, 4));
        JCheckBox loaded = Ui.check("Extension loaded", false);
        loaded.setEnabled(false);
        p.add(loaded, BorderLayout.NORTH);
        DefaultTableModel m = new DefaultTableModel(new Object[]{"Item", "Detail"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(m);
        Ui.styleTable(t);
        p.add(Ui.scroll(t), BorderLayout.CENTER);
        return p;
    }

    private JComponent consolePane() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setFont(Ui.MONO);
        return Ui.scroll(a);
    }
}
