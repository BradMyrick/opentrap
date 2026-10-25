package opentrap.ui;

import opentrap.core.Bytes;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/** A 16-column hex editor with an offset gutter and an ASCII column. */
public class HexView extends JPanel {

    private byte[] data = new byte[0];
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private boolean editable;

    public HexView(boolean editable) {
        super(new BorderLayout());
        this.editable = editable;
        table.setFont(Ui.MONO);
        table.setRowHeight(18);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(Ui.MONO);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setCellSelectionEnabled(true);

        DefaultTableCellRenderer cells = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                setFont(Ui.MONO);
                if (c == 0) {
                    setForeground(sel ? Theme.TEXT : Theme.HEX_OFFSET);
                    setHorizontalAlignment(RIGHT);
                } else if (c == 17) {
                    setForeground(sel ? Theme.TEXT : Theme.HEX_ASCII);
                    setHorizontalAlignment(LEFT);
                } else {
                    setForeground(Theme.TEXT);
                    setHorizontalAlignment(CENTER);
                }
                return comp;
            }
        };
        for (int i = 0; i < 18; i++) table.getColumnModel().getColumn(i).setCellRenderer(cells);
        sizeColumns();
        add(Ui.scroll(table), BorderLayout.CENTER);
    }

    private void sizeColumns() {
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        for (int i = 1; i <= 16; i++) table.getColumnModel().getColumn(i).setPreferredWidth(26);
        table.getColumnModel().getColumn(17).setPreferredWidth(160);
    }

    public void setData(byte[] b) {
        this.data = b == null ? new byte[0] : b;
        model.fireTableDataChanged();
    }

    public byte[] getData() { return data; }

    public void setEditable(boolean b) { this.editable = b; }

    private class Model extends AbstractTableModel {
        @Override public int getRowCount() { return (data.length + 15) / 16; }
        @Override public int getColumnCount() { return 18; }

        @Override public String getColumnName(int c) {
            if (c == 0 || c == 17) return " ";
            return Integer.toHexString(c - 1);
        }

        @Override public Object getValueAt(int row, int col) {
            int base = row * 16;
            if (col == 0) return String.format("%08x", base);
            if (col == 17) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 16; i++) {
                    int idx = base + i;
                    if (idx >= data.length) break;
                    int v = data[idx] & 0xff;
                    sb.append(v >= 32 && v <= 126 ? (char) v : '.');
                }
                return sb.toString();
            }
            int idx = base + col - 1;
            if (idx >= data.length) return "";
            return String.format("%02x", data[idx] & 0xff);
        }

        @Override public boolean isCellEditable(int row, int col) {
            if (!editable || col == 0 || col == 17) return false;
            return row * 16 + col - 1 < data.length;
        }

        @Override public void setValueAt(Object v, int row, int col) {
            int idx = row * 16 + col - 1;
            if (idx < 0 || idx >= data.length) return;
            try {
                int b = Integer.parseInt(String.valueOf(v).trim(), 16);
                data[idx] = (byte) b;
                fireTableRowsUpdated(row, row);
            } catch (Exception ignored) {}
        }
    }
}
