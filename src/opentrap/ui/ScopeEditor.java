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
        JLabel inc = Ui.bold("Include in scope");
        inc.setAlignmentX(LEFT_ALIGNMENT);
        add(inc);
        add(Box.createVerticalStrut(6));
        add(new RulePanel(scope.include()));
        add(Box.createVerticalStrut(16));
        JLabel exc = Ui.bold("Exclude from scope");
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
        private final JButton addBtn = Ui.button("Add", "add the item");
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

            // The button column sits to the left of the table, as in the reference.
            JPanel buttons = new JPanel(new GridLayout(5, 1, 0, 4));
            buttons.setOpaque(false);
            JButton edit = Ui.button("Edit", "edit the selected item");
            JButton remove = Ui.button("Remove", "remove the selected item");
            JButton pasteUrl = Ui.button("Paste URL", "add the URL on the clipboard as an item");
            JButton load = Ui.button("Load ...", "add every URL listed in a file, one per line");
            buttons.add(addBtn);
            buttons.add(edit);
            buttons.add(remove);
            buttons.add(pasteUrl);
            buttons.add(load);
            JPanel bwrap = new JPanel(new BorderLayout());
            bwrap.setOpaque(false);
            bwrap.add(buttons, BorderLayout.NORTH);
            add(bwrap, BorderLayout.WEST);
            pasteUrl.addActionListener(e -> {
                String text;
                try {
                    text = String.valueOf(java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .getData(java.awt.datatransfer.DataFlavor.stringFlavor));
                } catch (Exception ex) { text = ""; }
                if (!addUrl(text)) {
                    JOptionPane.showMessageDialog(this, "The clipboard does not hold a URL.",
                            "Paste URL", JOptionPane.INFORMATION_MESSAGE);
                }
            });
            load.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Load URLs");
                if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                int n = 0;
                try {
                    for (String line : java.nio.file.Files.readAllLines(fc.getSelectedFile().toPath())) {
                        if (addUrl(line)) n++;
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Could not read the file: " + ex.getMessage(),
                            "Load URLs", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (n == 0) JOptionPane.showMessageDialog(this, "No URLs were found in the file.",
                        "Load URLs", JOptionPane.INFORMATION_MESSAGE);
            });

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
            setMaximumSize(new Dimension(760, 210));
        }

        /** Turn a URL into a rule (its scheme, host, port and path prefix) and add it. */
        private boolean addUrl(String text) {
            if (text == null) return false;
            String s = text.trim();
            if (s.isEmpty()) return false;
            try {
                java.net.URI u = new java.net.URI(s.contains("://") ? s : "https://" + s);
                if (u.getHost() == null || u.getHost().isEmpty()) return false;
                String scheme = u.getScheme() == null ? "any" : u.getScheme().toLowerCase();
                protocol.setSelectedItem(scheme.equals("http") || scheme.equals("https") ? scheme : "any");
                host.setText(u.getHost());
                port.setText(u.getPort() > 0 ? String.valueOf(u.getPort()) : "");
                String path = u.getRawPath();
                file.setText(path == null || path.isEmpty() || path.equals("/")
                        ? "" : "^" + java.util.regex.Pattern.quote(path) + ".*");
                commit();
                return true;
            } catch (Exception ex) {
                return false;
            }
        }

        private void commit() {
            Scope.Rule r = editingRow >= 0 ? rules.get(editingRow) : new Scope.Rule();
            r.protocol = String.valueOf(protocol.getSelectedItem());
            // The fields show the friendly, unquoted form of each pattern. If the
            // user left a field untouched, keep its exact stored pattern so an
            // auto-created rule (^\Qhost\E$) round-trips without quietly losing
            // its anchors or literal quoting; only a field they actually retyped
            // is stored as typed.
            r.host = keptOrTyped(host.getText().trim(), r.host, editingRow >= 0);
            r.port = keptOrTyped(port.getText().trim(), r.port, editingRow >= 0);
            r.file = keptOrTyped(file.getText().trim(), r.file, editingRow >= 0);
            if (editingRow < 0) { r.enabled = true; rules.add(r); }
            editingRow = -1;
            addBtn.setText("Add");
            host.setText(""); port.setText(""); file.setText("");
            model.fireTableDataChanged();
            ctx.scopeChanged();
        }

        private void beginEdit() {
            int i = table.getSelectedRow();
            if (i < 0) return;
            Scope.Rule r = rules.get(i);
            protocol.setSelectedItem(r.protocol);
            host.setText(Scope.display(r.host));
            port.setText(Scope.display(r.port));
            file.setText(Scope.display(r.file));
            editingRow = i;
            addBtn.setText("update");
        }

        /** On edit, keep a field's stored pattern verbatim when the user left the
         *  friendly form unchanged; otherwise store what they typed. A brand-new
         *  rule always stores the typed text. */
        private static String keptOrTyped(String typed, String stored, boolean editing) {
            if (editing && typed.equals(Scope.display(stored))) return stored;
            return typed;
        }

        private void removeSelected() {
            int[] sel = table.getSelectedRows();
            for (int i = sel.length - 1; i >= 0; i--) rules.remove(sel[i]);
            model.fireTableDataChanged();
            ctx.scopeChanged();
        }

        private class Model extends AbstractTableModel {
            private final String[] cols = {"Enabled", "Protocol", "Host / IP range", "Port", "File"};
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
                    case 2 -> Scope.display(x.host);
                    case 3 -> Scope.display(x.port);
                    default -> Scope.display(x.file);
                };
            }

            @Override public void setValueAt(Object v, int r, int c) {
                if (c == 0) { rules.get(r).enabled = Boolean.TRUE.equals(v); ctx.scopeChanged(); }
            }
        }
    }

    public void refresh() { repaint(); }
}
