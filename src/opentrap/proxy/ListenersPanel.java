package opentrap.proxy;

import opentrap.core.SuiteContext;
import opentrap.ui.Ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

/** Add, edit, start and stop proxy listeners. */
public class ListenersPanel extends JPanel {

    private final ProxyServer server;
    private final Model model = new Model();
    private final JTable table = new JTable(model);

    public ListenersPanel(SuiteContext ctx, ProxyServer server) {
        super();
        this.server = server;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        JLabel h = Ui.heading("proxy listeners");
        h.setAlignmentX(LEFT_ALIGNMENT);
        add(h);
        add(Box.createVerticalStrut(4));
        JLabel status = Ui.bold(server.listeningSummary());
        status.setAlignmentX(LEFT_ALIGNMENT);
        add(status);
        add(Box.createVerticalStrut(8));
        server.onListenersChanged(() -> {
            status.setText(server.listeningSummary());
            model.fireTableDataChanged();
        });

        Ui.styleTable(table);
        Ui.setWidths(table, 70, 70, 110, 110, 90);
        JScrollPane sp = Ui.scroll(table);
        sp.setPreferredSize(new Dimension(640, 90));
        sp.setMaximumSize(new Dimension(760, 90));
        sp.setAlignmentX(LEFT_ALIGNMENT);
        add(sp);
        add(Box.createVerticalStrut(8));

        JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        entry.setOpaque(false);
        entry.setAlignmentX(LEFT_ALIGNMENT);
        JTextField port = Ui.field("", 6);
        JCheckBox loopback = Ui.check("loopback only", true);
        JButton add = Ui.button("add", "add the item");
        JButton remove = Ui.button("remove", "remove the selected item");
        entry.add(Ui.label("port:"));
        entry.add(port);
        entry.add(loopback);
        entry.add(add);
        entry.add(remove);
        add(entry);

        // Selecting a listener shows its port and address mode in the fields,
        // so the running port is always visible here. The first listener is
        // selected when the panel opens.
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int i = table.getSelectedRow();
            if (i >= 0 && i < server.listeners().size()) {
                ProxyListener l = server.listeners().get(i);
                port.setText(String.valueOf(l.port));
                loopback.setSelected(l.loopbackOnly);
            }
        });
        if (model.getRowCount() > 0) table.setRowSelectionInterval(0, 0);

        add.addActionListener(e -> {
            try {
                int p = Integer.parseInt(port.getText().trim());
                for (ProxyListener existing : server.listeners()) {
                    if (existing.port == p) {
                        JOptionPane.showMessageDialog(this, "there is already a listener on port " + p,
                                "proxy listeners", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                }
                ProxyListener l = new ProxyListener(p);
                l.loopbackOnly = loopback.isSelected();
                server.listeners().add(l);
                server.start(l);
                model.fireTableDataChanged();
                table.setRowSelectionInterval(model.getRowCount() - 1, model.getRowCount() - 1);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "invalid port", "error", JOptionPane.ERROR_MESSAGE);
            }
        });
        remove.addActionListener(e -> {
            int i = table.getSelectedRow();
            if (i >= 0 && i < server.listeners().size()) {
                ProxyListener l = server.listeners().get(i);
                server.stop(l);
                server.listeners().remove(i);
                model.fireTableDataChanged();
                int n = model.getRowCount();
                if (n > 0) table.setRowSelectionInterval(Math.min(i, n - 1), Math.min(i, n - 1));
                else { port.setText(""); }
            }
        });

        // Toggle running by clicking the first column.
        table.getModel().addTableModelListener(e -> {});
        setMaximumSize(new Dimension(760, 260));
    }

    private class Model extends AbstractTableModel {
        private final String[] cols = {"running", "port", "loopback only", "invisible", "redirect"};
        @Override public int getRowCount() { return server.listeners().size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) {
            return (c == 0 || c == 2 || c == 3) ? Boolean.class : String.class;
        }
        @Override public boolean isCellEditable(int r, int c) { return c == 0; }

        @Override public Object getValueAt(int r, int c) {
            ProxyListener l = server.listeners().get(r);
            return switch (c) {
                case 0 -> l.running;
                case 1 -> String.valueOf(l.port);
                case 2 -> l.loopbackOnly;
                case 3 -> l.invisible;
                default -> l.redirectLabel();
            };
        }

        @Override public void setValueAt(Object v, int r, int c) {
            if (c != 0) return;
            ProxyListener l = server.listeners().get(r);
            if (Boolean.TRUE.equals(v)) server.start(l); else server.stop(l);
            fireTableRowsUpdated(r, r);
        }
    }
}
