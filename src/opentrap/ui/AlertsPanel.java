package opentrap.ui;

import opentrap.core.Alert;
import opentrap.core.SuiteContext;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.DateFormat;
import java.util.List;

/** The suite event log. */
public class AlertsPanel extends JPanel {

    private final SuiteContext ctx;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final DateFormat fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM);

    public AlertsPanel(SuiteContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        Ui.styleTable(table);
        Ui.distributeWidths(table, 90, 90, 900);
        add(Ui.scroll(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton clear = Ui.button("clear");
        clear.addActionListener(e -> { ctx.clearAlerts(); model.fireTableDataChanged(); });
        south.add(clear);
        add(south, BorderLayout.SOUTH);

        ctx.addAlertListener(a -> SwingUtilities.invokeLater(() -> {
            model.fireTableDataChanged();
            int n = model.getRowCount();
            if (n > 0) table.scrollRectToVisible(table.getCellRect(n - 1, 0, true));
        }));
    }

    private class Model extends AbstractTableModel {
        private final String[] cols = {"Time", "Tool", "Message"};
        @Override public int getRowCount() { return ctx.alerts().size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            List<Alert> list = ctx.alerts();
            if (r >= list.size()) return "";
            Alert a = list.get(r);
            return switch (c) {
                case 0 -> fmt.format(a.time);
                case 1 -> a.tool;
                default -> (a.repeats > 1 ? "[" + a.repeats + "]  " : "") + a.message;
            };
        }
    }
}
