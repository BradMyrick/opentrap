package opentrap.ui;

import opentrap.core.Scope;
import opentrap.core.SuiteContext;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.List;

/** Include and exclude rule tables over protocol, host, port and file. */
public class ScopeEditor extends JPanel {

    private final SuiteContext ctx;
    private final Scope scope;

    public ScopeEditor(SuiteContext ctx, Scope scope) {
        super();
        this.ctx = ctx;
        this.scope = scope;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        JLabel inc = Ui.bold("include in scope");
        inc.setAlignmentX(LEFT_ALIGNMENT);
        add(inc);
        add(Box.createVerticalStrut(6));
        add(new RulePanel(scope.include()));
        add(Box.createVerticalStrut(16));
        JLabel exc = Ui.bold("exclude from scope");
        exc.setAlignmentX(LEFT_ALIGNMENT);
        add(exc);
        add(Box.createVerticalStrut(6));
        add(new RulePanel(scope.exclude()));
    }

    private class RulePanel extends JPanel {
        private final List<Scope.Rule> rules;
        private final Model model;
        private final JTable table;
        private final JComboBox<String> protocol = Ui.combo(new String[]{"any", "http", "https"});
        private final JTextField host = Ui.field("", 18);
        private final JTextField port = Ui.field("", 5);
        private final JTextField file = Ui.field("", 24);
        private final JButton addBtn = Ui.button("add", "add the item");
        private int editingRow = -1;

        RulePanel(List<Scope.Rule> rules) {
            super(new BorderLayout(8, 6));
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            this.rules = rules;
            this.model = new Model();
            this.table = new JTable(model);
            Ui.styleTable(table);
            Ui.setWidths(table, 36, 70, 180, 50, 200);
            table.getColumnModel().getColumn(0).setMaxWidth(36);

            JScrollPane sp = Ui.scroll(table);
            sp.setPreferredSize(new Dimension(560, 110));
            add(sp, BorderLayout.CENTER);

            JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 4));
            buttons.setOpaque(false);
            JButton edit = Ui.button("edit", "edit the selected item");
            JButton remove = Ui.button("remove", "remove the selected item");
            buttons.add(addBtn);
            buttons.add(edit);
            buttons.add(remove);
            JPanel bwrap = new JPanel(new BorderLayout());
            bwrap.setOpaque(false);
            bwrap.add(buttons, BorderLayout.NORTH);
            add(bwrap, BorderLayout.EAST);

            protocol.setToolTipText("choose the protocol to match");
            host.setToolTipText("hostname regex or IP range to match (leave blank to match any)");
            port.setToolTipText("port regex to match (leave blank to match any)");
            file.setToolTipText("file regex to match (leave blank to match any)");

            JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            entry.setOpaque(false);
            entry.add(protocol);
            entry.add(host);
            entry.add(port);
            entry.add(file);
            add(entry, BorderLayout.SOUTH);

            addBtn.addActionListener(e -> commit());
            edit.addActionListener(e -> beginEdit());
            remove.addActionListener(e -> removeSelected());
            setMaximumSize(new Dimension(760, 190));
        }

        private void commit() {
            Scope.Rule r = editingRow >= 0 ? rules.get(editingRow) : new Scope.Rule();
            r.protocol = String.valueOf(protocol.getSelectedItem());
            r.host = host.getText().trim();
            r.port = port.getText().trim();
            r.file = file.getText().trim();
            if (editingRow < 0) { r.enabled = true; rules.add(r); }
            editingRow = -1;
            addBtn.setText("add");
            host.setText(""); port.setText(""); file.setText("");
            model.fireTableDataChanged();
            ctx.scopeChanged();
        }

        private void beginEdit() {
            int i = table.getSelectedRow();
            if (i < 0) return;
            Scope.Rule r = rules.get(i);
            protocol.setSelectedItem(r.protocol);
            host.setText(r.host);
            port.setText(r.port);
            file.setText(r.file);
            editingRow = i;
            addBtn.setText("update");
        }

        private void removeSelected() {
            int[] sel = table.getSelectedRows();
            for (int i = sel.length - 1; i >= 0; i--) rules.remove(sel[i]);
            model.fireTableDataChanged();
            ctx.scopeChanged();
        }

        private class Model extends AbstractTableModel {
            private final String[] cols = {"", "protocol", "host / IP range", "port", "file"};
            @Override public int getRowCount() { return rules.size(); }
            @Override public int getColumnCount() { return cols.length; }
            @Override public String getColumnName(int c) { return cols[c]; }
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }

            @Override public Object getValueAt(int r, int c) {
                Scope.Rule x = rules.get(r);
                return switch (c) {
                    case 0 -> x.enabled;
                    case 1 -> x.protocol;
                    case 2 -> x.host;
                    case 3 -> x.port;
                    default -> x.file;
                };
            }

            @Override public void setValueAt(Object v, int r, int c) {
                if (c == 0) { rules.get(r).enabled = Boolean.TRUE.equals(v); ctx.scopeChanged(); }
            }
        }
    }

    public void refresh() { repaint(); }
}
