package opentrap.proxy;

import opentrap.ui.Ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

/** Edit the match-and-replace rule list. */
public class MatchReplacePanel extends JPanel {

    private final ProxyServer server;
    private final Model model = new Model();
    private final JTable table = new JTable(model);

    public MatchReplacePanel(ProxyServer server) {
        super();
        this.server = server;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        // Laid out as in the reference: a heading and description, the button
        // column to the left of the rules table, and the fields for a new rule
        // beneath it. A second section covers WebSocket rules.
        JLabel h = Ui.heading("HTTP match and replace rules");
        h.setAlignmentX(LEFT_ALIGNMENT);
        add(h);
        add(Box.createVerticalStrut(4));
        JTextArea desc = Ui.note("Use these settings to automatically replace parts of HTTP "
                + "requests and responses passing through the Proxy.");
        desc.setAlignmentX(LEFT_ALIGNMENT);
        desc.setMaximumSize(new Dimension(760, 24));
        add(desc);
        add(Box.createVerticalStrut(8));

        Ui.styleTable(table);
        Ui.setWidths(table, 60, 120, 170, 170, 150);
        // The type column edits through a dropdown of the four scopes.
        JComboBox<String> typeEditor = new JComboBox<>(MatchReplaceRule.TYPES);
        typeEditor.setFont(Ui.PLAIN);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(typeEditor));
        JScrollPane sp = Ui.scroll(table);
        sp.setPreferredSize(new Dimension(640, 150));

        JButton add = Ui.button("Add", "add a rule from the fields below");
        JButton remove = Ui.button("Remove", "remove the selected rule");
        JButton up = Ui.button("Up", "move the selected rule earlier");
        JButton down = Ui.button("Down", "move the selected rule later");
        JPanel rulesRow = new JPanel(new BorderLayout(8, 0));
        rulesRow.setOpaque(false);
        rulesRow.setAlignmentX(LEFT_ALIGNMENT);
        rulesRow.add(Ui.buttonColumn(add, remove, up, down), BorderLayout.WEST);
        rulesRow.add(sp, BorderLayout.CENTER);
        rulesRow.setMaximumSize(new Dimension(760, 150));
        add(rulesRow);
        add(Box.createVerticalStrut(6));

        JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        entry.setOpaque(false);
        entry.setAlignmentX(LEFT_ALIGNMENT);
        JComboBox<String> type = Ui.combo(MatchReplaceRule.TYPES);
        JTextField match = Ui.field("", 14);
        JTextField replace = Ui.field("", 14);
        JTextField comment = Ui.field("", 10);
        type.setToolTipText("applies to request/response header/body");
        match.setToolTipText("regex to match (leave blank to insert new header)");
        replace.setToolTipText("replacement string (leave blank to remove matched header)");
        comment.setToolTipText("optional note");
        entry.add(Ui.label("New rule:"));
        entry.add(type);
        entry.add(match);
        entry.add(replace);
        entry.add(comment);
        add(entry);
        JLabel hint = Ui.label("Fill in the fields and click Add. Double-click any cell to edit a "
                + "rule in place; rules apply top to bottom.");
        hint.setForeground(opentrap.ui.Theme.MUTED);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        add(Box.createVerticalStrut(4));
        add(hint);

        add(Box.createVerticalStrut(22));
        JLabel wh = Ui.heading("WebSocket match and replace rules");
        wh.setAlignmentX(LEFT_ALIGNMENT);
        add(wh);
        add(Box.createVerticalStrut(4));
        JComponent ws = opentrap.ui.Unbuilt.PROXY_WS_MATCH.panel();
        ws.setAlignmentX(LEFT_ALIGNMENT);
        ws.setMaximumSize(new Dimension(760, ws.getPreferredSize().height));
        add(ws);

        add.addActionListener(e -> {
            server.matchReplace.add(new MatchReplaceRule(true,
                    String.valueOf(type.getSelectedItem()), match.getText(), replace.getText(), comment.getText()));
            model.fireTableDataChanged();
            match.setText(""); replace.setText(""); comment.setText("");
        });
        remove.addActionListener(e -> {
            int i = table.getSelectedRow();
            if (i >= 0 && i < server.matchReplace.size()) {
                server.matchReplace.remove(i);
                model.fireTableDataChanged();
            }
        });
        up.addActionListener(e -> move(-1));
        down.addActionListener(e -> move(1));
        setBorder(Ui.pad(10, 10, 10, 10));
    }

    /** Swap the selected rule with its neighbour, keeping the selection on it. */
    private void move(int delta) {
        int i = table.getSelectedRow();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= server.matchReplace.size()) return;
        MatchReplaceRule tmp = server.matchReplace.get(i);
        server.matchReplace.set(i, server.matchReplace.get(j));
        server.matchReplace.set(j, tmp);
        model.fireTableDataChanged();
        table.getSelectionModel().setSelectionInterval(j, j);
    }

    private class Model extends AbstractTableModel {
        private final String[] cols = {"Enabled", "Type", "Match", "Replace", "Comment"};
        @Override public int getRowCount() { return server.matchReplace.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int r, int c) { return true; }

        @Override public Object getValueAt(int r, int c) {
            MatchReplaceRule x = server.matchReplace.get(r);
            return switch (c) {
                case 0 -> x.enabled;
                case 1 -> x.type;
                case 2 -> x.match;
                case 3 -> x.replace;
                default -> x.comment;
            };
        }

        @Override public void setValueAt(Object v, int r, int c) {
            MatchReplaceRule x = server.matchReplace.get(r);
            String s = v == null ? "" : v.toString();
            switch (c) {
                case 0 -> x.enabled = Boolean.TRUE.equals(v);
                case 1 -> x.type = s;
                case 2 -> x.match = s;
                case 3 -> x.replace = s;
                default -> x.comment = s;
            }
            fireTableRowsUpdated(r, r);
        }
    }
}
