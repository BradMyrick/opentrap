package opentrap.ui;

import opentrap.engine.ScanConfig;
import opentrap.engine.ScanConfigStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Settings > Configuration library: the scan configurations saved from the New
 * scan wizard, with their key settings, and a way to remove one.
 */
public class ConfigLibraryPanel extends OptionsPage {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Name", "Crawl depth", "AJAX crawl", "Attack strength", "Alert threshold"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public ConfigLibraryPanel() {
        Ui.styleTable(table);
        Ui.distributeWidths(table, 200, 90, 90, 120, 120);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = Ui.scroll(table);
        sp.setPreferredSize(new Dimension(620, 200));

        JButton refresh = Ui.button("Refresh", "re-read the saved configurations");
        JButton remove = Ui.button("Remove", "delete the selected configuration");
        refresh.addActionListener(e -> reload());
        remove.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r < 0) return;
            String name = String.valueOf(model.getValueAt(r, 0));
            if (JOptionPane.showConfirmDialog(this, "Remove the saved configuration \"" + name + "\"?",
                    "Configuration library", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                ScanConfigStore.delete(name);
                reload();
            }
        });
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(Ui.buttonColumn(refresh, remove), BorderLayout.WEST);
        row.add(sp, BorderLayout.CENTER);

        section("Saved scan configurations",
                Ui.note("Configurations saved from the New scan wizard with Save. Load one from "
                        + "the wizard's Scan configuration step. They are kept under "
                        + "~/.opentrap/scan-configs, one file each."),
                row);
        reload();
    }

    /** Re-read the store into the table. */
    public void reload() {
        model.setRowCount(0);
        List<String> names = ScanConfigStore.names();
        for (String n : names) {
            try {
                ScanConfig c = ScanConfigStore.load(n);
                model.addRow(new Object[]{n,
                        c.crawlDepth() == 0 ? "unlimited" : String.valueOf(c.crawlDepth()),
                        c.ajaxSpider() ? "on" : "off",
                        c.attackStrength(), c.alertThreshold()});
            } catch (Exception e) {
                model.addRow(new Object[]{n, "?", "?", "?", "?"});
            }
        }
    }
}
