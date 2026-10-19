package opentrap.comparer;

import opentrap.core.Bytes;
import opentrap.core.SuiteContext;
import opentrap.core.ToolModule;
import opentrap.ui.Ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds a set of loaded items and compares any two of them. Both lists are
 * views of the same items: the user picks one row in each, then compares.
 */
public class ComparerTool implements ToolModule {

    private static final String PLACEHOLDER =
            "load, paste, or send data here from other tools to perform a compare";
    private static final int PREVIEW_CHARS = 120;

    /** One loaded blob, numbered in the order it arrived. */
    record Item(int id, byte[] data) {}

    private final List<Item> items = new ArrayList<>();
    private final ItemModel model = new ItemModel();
    private final JTable firstList = new JTable(model);
    private final JTable secondList = new JTable(model);
    private final JPanel firstCards = new JPanel(new CardLayout());
    private final JPanel secondCards = new JPanel(new CardLayout());
    private final JButton words = compareButton("words");
    private final JButton bytes = compareButton("bytes");
    private final JPanel root = new JPanel(new BorderLayout(8, 0));

    private SuiteContext ctx;
    private int nextId = 1;

    public ComparerTool() {
        cards(firstCards, firstList);
        cards(secondCards, secondList);

        JPanel lists = new JPanel(new GridLayout(2, 1, 0, 10));
        lists.setOpaque(false);
        lists.add(titled(" item 1", firstCards));
        lists.add(titled(" item 2", secondCards));

        root.setBorder(Ui.pad(8, 8, 8, 8));
        root.add(lists, BorderLayout.CENTER);
        root.add(buttons(), BorderLayout.EAST);

        firstList.getSelectionModel().addListSelectionListener(e -> refreshButtons());
        secondList.getSelectionModel().addListSelectionListener(e -> refreshButtons());
        refreshButtons();
        showCards();
    }

    @Override public String name() { return "comparer"; }

    @Override public Tab[] tabs() { return new Tab[]{ new Tab("comparer", root) }; }

    @Override public void init(SuiteContext ctx) {
        this.ctx = ctx;
        ctx.registerSink("comparer", (svc, req, resp, caption) -> SwingUtilities.invokeLater(() -> {
            if (req != null) add(req.raw());
            if (resp != null) add(resp.raw());
        }));
    }

    // ---- layout ----

    private void cards(JPanel cards, JTable table) {
        Ui.styleTable(table);
        Ui.setWidths(table, 30, 70, 1000);
        // the two narrow columns are pinned so the preview column takes the rest
        pin(table.getColumnModel().getColumn(0), 30);
        pin(table.getColumnModel().getColumn(1), 70);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cards.add(placeholder(), "empty");
        cards.add(Ui.scroll(table), "items");
    }

    private static void pin(TableColumn column, int width) {
        column.setMinWidth(width);
        column.setMaxWidth(width);
    }

    private static JComponent placeholder() {
        JLabel l = Ui.label(PLACEHOLDER);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createLineBorder(Ui.GRID));
        p.add(l);
        return p;
    }

    private static JPanel titled(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.setOpaque(false);
        p.add(Ui.bold(title), BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JComponent buttons() {
        JButton paste = Ui.button("paste", "add the clipboard contents as an item");
        JButton load = Ui.button("load", "add the contents of a file as an item");
        JButton remove = Ui.button("remove", "remove the selected items");
        JButton clear = Ui.button("clear", "remove every item");
        paste.addActionListener(e -> paste());
        load.addActionListener(e -> load());
        remove.addActionListener(e -> remove());
        clear.addActionListener(e -> clear());
        words.addActionListener(e -> compare(true));
        bytes.addActionListener(e -> compare(false));

        JPanel actions = new JPanel(new GridLayout(4, 1, 0, 4));
        actions.setOpaque(false);
        actions.add(paste);
        actions.add(load);
        actions.add(remove);
        actions.add(clear);

        JPanel compares = new JPanel(new GridLayout(2, 1, 0, 4));
        compares.setOpaque(false);
        compares.add(words);
        compares.add(bytes);

        JPanel p = Ui.vbox(actions, Box.createVerticalStrut(16), Ui.bold("  compare ..."), compares);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setBorder(Ui.pad(18, 2, 0, 0));
        holder.add(p, BorderLayout.NORTH);
        return holder;
    }

    private static JButton compareButton(String text) {
        JButton b = Ui.button(text);
        b.setFont(Ui.BOLD);
        b.setEnabled(false);
        return b;
    }

    // ---- items ----

    private void add(byte[] data) {
        if (data == null) return;
        items.add(new Item(nextId++, data));
        model.fireTableRowsInserted(items.size() - 1, items.size() - 1);
        showCards();
        refreshButtons();
    }

    private void paste() {
        try {
            Object text = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (text instanceof String s && !s.isEmpty()) add(Bytes.bytes(s));
        } catch (Exception e) {
            problem("cannot read the clipboard: " + e);
        }
    }

    private void load() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("load file");
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        try {
            add(Files.readAllBytes(chooser.getSelectedFile().toPath()));
        } catch (IOException e) {
            problem("cannot read " + chooser.getSelectedFile().getName() + ": " + e.getMessage());
        }
    }

    private void remove() {
        int first = firstList.getSelectedRow();
        int second = secondList.getSelectedRow();
        int high = Math.max(first, second);
        int low = Math.min(first, second);
        if (high >= 0) items.remove(high);
        if (low >= 0 && low != high) items.remove(low);
        firstList.clearSelection();
        secondList.clearSelection();
        model.fireTableDataChanged();
        showCards();
        refreshButtons();
    }

    private void clear() {
        items.clear();
        firstList.clearSelection();
        secondList.clearSelection();
        model.fireTableDataChanged();
        showCards();
        refreshButtons();
    }

    private void showCards() {
        String card = items.isEmpty() ? "empty" : "items";
        ((CardLayout) firstCards.getLayout()).show(firstCards, card);
        ((CardLayout) secondCards.getLayout()).show(secondCards, card);
    }

    private void refreshButtons() {
        int first = firstList.getSelectedRow();
        int second = secondList.getSelectedRow();
        boolean ready = first >= 0 && second >= 0 && first != second;
        words.setEnabled(ready);
        bytes.setEnabled(ready);
    }

    private void compare(boolean asWords) {
        int first = firstList.getSelectedRow();
        int second = secondList.getSelectedRow();
        if (first < 0 || second < 0 || first == second) return;
        CompareWindow w = new CompareWindow(items.get(first), items.get(second), asWords,
                ctx == null ? null : ctx.frame());
        w.setVisible(true);
    }

    private void problem(String message) {
        if (ctx != null) ctx.alert(name(), message);
        else JOptionPane.showMessageDialog(root, message, "comparer", JOptionPane.WARNING_MESSAGE);
    }

    /** A printable sketch of an item, enough to tell two of them apart. */
    private static String preview(byte[] data) {
        StringBuilder sb = new StringBuilder(PREVIEW_CHARS + 4);
        int i = 0;
        while (i < data.length && i < PREVIEW_CHARS) {
            int v = data[i++] & 0xff;
            sb.append(v >= 32 && v <= 126 ? (char) v : '.');
        }
        if (i < data.length) sb.append(" ...");
        return sb.toString();
    }

    /** Shared by both lists, so an item added anywhere shows up twice. */
    private final class ItemModel extends AbstractTableModel {

        private final String[] columns = {"#", "length", "data"};

        @Override public int getRowCount() { return items.size(); }

        @Override public int getColumnCount() { return columns.length; }

        @Override public String getColumnName(int column) { return columns[column]; }

        @Override public Class<?> getColumnClass(int column) {
            return column == 2 ? String.class : Integer.class;
        }

        @Override public Object getValueAt(int row, int column) {
            Item item = items.get(row);
            return switch (column) {
                case 0 -> item.id();
                case 1 -> item.data().length;
                default -> preview(item.data());
            };
        }
    }
}
