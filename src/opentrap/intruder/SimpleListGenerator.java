package opentrap.intruder;

import opentrap.ui.Ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/** A hand-built list of payload strings. */
class SimpleListGenerator extends AbstractGenerator {

    private final DefaultTableModel model =
            new DefaultTableModel(new Object[]{"item"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTable table = new JTable(model);
    private final JTextField entry = Ui.field("", 18);
    private final JLabel countLabel = Ui.label("0 items");
    private final JPanel panel;

    SimpleListGenerator() {
        Ui.styleTable(table);
        Ui.setWidths(table, 300);
        JScrollPane sp = Ui.scroll(table);
        sp.setPreferredSize(new Dimension(360, 150));

        JButton add    = Ui.button("Add");
        JButton load   = Ui.button("Load ...", "load payloads from a file");
        JButton paste  = Ui.button("Paste");
        JButton delete = Ui.button("Remove");
        JButton clear  = Ui.button("Clear");

        add.addActionListener(e -> addEntry());
        entry.addActionListener(e -> addEntry());
        load.addActionListener(e -> load());
        paste.addActionListener(e -> paste());
        delete.addActionListener(e -> deleteSelected());
        clear.addActionListener(e -> { model.setRowCount(0); changed(); });

        // As in the reference: the button column to the left of the list, and
        // the entry field with its Add button beneath.
        JPanel buttons = Ui.buttonColumn(paste, load, delete, clear);
        JPanel addRow = Ui.row(entry, add);

        panel = new JPanel(new BorderLayout(8, 4));
        panel.setOpaque(false);
        JPanel list = new JPanel(new BorderLayout());
        list.setOpaque(false);
        list.add(sp, BorderLayout.CENTER);
        list.add(addRow, BorderLayout.SOUTH);
        panel.add(list, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.WEST);
        panel.add(countLabel, BorderLayout.NORTH);
    }

    private void addEntry() {
        String t = entry.getText();
        if (t.isEmpty()) return;
        model.addRow(new Object[]{t});
        entry.setText("");
        changed();
    }

    private void deleteSelected() {
        int[] rows = table.getSelectedRows();
        for (int i = rows.length - 1; i >= 0; i--) model.removeRow(rows[i]);
        changed();
    }

    private void load() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(panel) != JFileChooser.APPROVE_OPTION) return;
        try (BufferedReader r = new BufferedReader(new FileReader(fc.getSelectedFile()))) {
            String line;
            while ((line = r.readLine()) != null) model.addRow(new Object[]{line});
            changed();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, "could not read file: " + ex.getMessage());
        }
    }

    private void paste() {
        try {
            String data = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            if (data == null) return;
            for (String line : data.split("\r\n|\r|\n")) model.addRow(new Object[]{line});
            changed();
        } catch (Exception ignored) {}
    }

    private void changed() {
        countLabel.setText(model.getRowCount() + " items");
        fire();
    }

    @Override public String label() { return " simple list"; }
    @Override public JComponent config() { return panel; }

    @Override public List<String> generate() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < model.getRowCount() && out.size() < CAP; i++) {
            out.add(String.valueOf(model.getValueAt(i, 0)));
        }
        return out;
    }

    @Override public int count() { return model.getRowCount(); }
}
