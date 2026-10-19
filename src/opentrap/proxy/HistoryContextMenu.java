package opentrap.proxy;

import opentrap.core.*;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Right-click actions for a history table: scope, send-to, copy, delete. */
public class HistoryContextMenu {

    private final SuiteContext ctx;
    private final JTable table;
    private final HistoryModel model;

    public HistoryContextMenu(SuiteContext ctx, JTable table, HistoryModel model) {
        this.ctx = ctx;
        this.table = table;
        this.model = model;
    }

    public void install() {
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybe(e); }
            @Override public void mouseReleased(MouseEvent e) { maybe(e); }
            private void maybe(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && !table.isRowSelected(row)) table.setRowSelectionInterval(row, row);
                build().show(table, e.getX(), e.getY());
            }
        });
    }

    private List<LogEntry> selected() {
        List<LogEntry> out = new ArrayList<>();
        for (int r : table.getSelectedRows()) {
            LogEntry e = model.at(table.convertRowIndexToModel(r));
            if (e != null) out.add(e);
        }
        return out;
    }

    private JPopupMenu build() {
        JPopupMenu menu = new JPopupMenu();
        List<LogEntry> sel = selected();
        if (sel.isEmpty()) return menu;
        LogEntry first = sel.get(0);
        boolean single = sel.size() == 1;

        JMenuItem title = new JMenuItem(opentrap.ui.Ui.truncate(first.url(), 60));
        title.setEnabled(false);
        menu.add(title);
        menu.addSeparator();

        add(menu, single ? "add item to scope" : "add selection to scope", () -> {
            for (LogEntry e : sel) ctx.scope().includeUrl(e.service(), e.request() == null ? "/" : e.request().path(), false);
            ctx.scopeChanged();
        });
        add(menu, single ? "remove item from scope" : "remove selection from scope", () -> {
            for (LogEntry e : sel) ctx.scope().excludeUrl(e.service(), e.request() == null ? "/" : e.request().path(), false);
            ctx.scopeChanged();
        });
        menu.addSeparator();

        if (single) {
            for (String tool : ctx.sinkNames()) {
                add(menu, "send to " + tool, () -> ctx.sendTo(tool, first.service(),
                        first.request() == null ? null : first.request().copy(),
                        first.response() == null ? null : first.response().copy(), null));
            }
            menu.addSeparator();
        }

        add(menu, single ? "delete item" : "delete selected items", () -> {
            for (LogEntry e : sel) model.remove(e);
        });
        add(menu, single ? "copy URL" : "copy selected URLs", () -> {
            StringBuilder sb = new StringBuilder();
            for (LogEntry e : sel) sb.append(e.url()).append('\n');
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(sb.toString().trim()), null);
        });
        return menu;
    }

    private void add(JPopupMenu menu, String label, Runnable action) {
        JMenuItem mi = new JMenuItem(label);
        mi.setFont(opentrap.ui.Ui.PLAIN);
        mi.addActionListener(e -> action.run());
        menu.add(mi);
    }
}
