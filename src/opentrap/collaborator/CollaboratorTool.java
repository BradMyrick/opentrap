package opentrap.collaborator;

import opentrap.core.SuiteContext;
import opentrap.core.ToolModule;
import opentrap.oast.Collaborator;
import opentrap.oast.Interaction;
import opentrap.ui.OptionsPage;
import opentrap.ui.Ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The collaborator tab: an out-of-band interaction view, in the spirit of the
 * commercial tool's Collaborator and ZAP's OAST.
 *
 * <p>It drives one {@link Collaborator} session. The top bar mints a payload to
 * copy into a manual test and shows the registration status; the table lists
 * every DNS, HTTP or SMTP callback a target made to a payload, and the lower
 * pane shows the raw request of the selected one. The active scanner shares the
 * same session, so a callback its blind checks triggered appears here too.
 *
 * <p>Plain table and buttons, matching the rest of the suite.
 */
public class CollaboratorTool implements ToolModule {

    private SuiteContext ctx;
    private Collaborator collab;
    private InteractionTableModel model;
    private JTable table;
    private JLabel statusLabel;
    private JTextField payloadField;
    private JTabbedPane detailTabs;

    @Override public String name() { return "collaborator"; }

    @Override public boolean alwaysTabbed() { return true; }

    @Override public void init(SuiteContext ctx) {
        this.ctx = ctx;
        this.collab = ctx.collaborator();
        collab.addListener(new Collaborator.Listener() {
            @Override public void interactionsAdded(List<Interaction> added) {
                SwingUtilities.invokeLater(() -> { model.add(added); refreshStatus(); });
            }
            @Override public void statusChanged(String status) {
                SwingUtilities.invokeLater(CollaboratorTool.this::refreshStatus);
            }
        });
    }

    @Override public Tab[] tabs() {
        return new Tab[]{
            new Tab("interactions", buildMain()),
            new Tab("options", buildOptions())
        };
    }

    // ---------------------------------------------------------------- main tab

    private JComponent buildMain() {
        model = new InteractionTableModel();
        table = new JTable(model);
        Ui.styleTable(table);
        Ui.distributeWidths(table, 50, 70, 140, 150, 400);
        table.getSelectionModel().addListSelectionListener(e -> showSelected());

        // Detail area as tabs, rebuilt per interaction so the set matches the
        // protocol, as the commercial tool does: a DNS lookup shows the query, an
        // HTTP request shows both the request and the response the server sent.
        detailTabs = new JTabbedPane();
        detailTabs.setFont(Ui.PLAIN);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                Ui.scroll(table), detailTabs);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        // Give the detail pane half the height once the tab is laid out, rather
        // than leaving it a thin strip under a mostly empty table.
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean set = false;
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (!set && split.getHeight() > 0) { set = true; split.setDividerLocation(0.5); }
            }
        });

        // Top bar: status and session controls.
        statusLabel = Ui.bold("not started");
        JButton start = Ui.button("start", "register with the collaborator server and begin polling");
        JButton stop  = Ui.button("stop", "stop polling (start again gives a fresh session)");
        JButton newPayload = Ui.button("new payload",
                "generate a fresh payload hostname, show it below, and copy it to the clipboard");
        JButton copy  = Ui.button("copy", "copy the payload shown below to the clipboard");
        JButton clear = Ui.button("clear", "remove the collected interactions");

        payloadField = new JTextField("", 52);
        payloadField.setEditable(false);
        payloadField.setFont(Ui.MONO);

        start.addActionListener(e -> {
            String err = collab.start();
            if (err != null) {
                JOptionPane.showMessageDialog(ctx.frame(),
                        "Could not register with " + collab.serverUrl() + ":\n" + err,
                        "collaborator", JOptionPane.WARNING_MESSAGE);
            } else {
                generatePayload();   // always show a current payload after starting
            }
            refreshStatus();
        });
        stop.addActionListener(e -> { collab.stop(); refreshStatus(); });
        newPayload.addActionListener(e -> {
            if (!generatePayload()) {
                JOptionPane.showMessageDialog(ctx.frame(),
                        "Start the collaborator first.", "collaborator",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        copy.addActionListener(e -> {
            if (!payloadField.getText().isBlank()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(payloadField.getText()), null);
                ctx.alert("collaborator", "copied collaborator payload " + payloadField.getText());
            }
        });
        clear.addActionListener(e -> {
            collab.clear(); model.clearAll(); detailTabs.removeAll();
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setOpaque(false);
        bar.add(start); bar.add(stop); bar.add(newPayload); bar.add(copy); bar.add(clear);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(Ui.label("status:")); bar.add(statusLabel);

        // The payload row stretches the field to the full width so the whole
        // hostname is always visible, never clipped.
        JPanel payloadRow = new JPanel(new BorderLayout(6, 0));
        payloadRow.setOpaque(false);
        payloadRow.setBorder(Ui.pad(0, 2, 2, 8));
        payloadRow.add(Ui.label("payload:"), BorderLayout.WEST);
        payloadRow.add(payloadField, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(Ui.pad(4, 6, 2, 6));
        top.add(bar, BorderLayout.NORTH);
        top.add(payloadRow, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.add(top, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        refreshStatus();
        return root;
    }

    private static JComponent textTab(String content, boolean wrap) {
        JTextArea a = new JTextArea(content);
        a.setEditable(false);
        a.setFont(Ui.MONO);
        a.setLineWrap(wrap);
        a.setWrapStyleWord(wrap);
        a.setCaretPosition(0);
        return Ui.scroll(a);
    }

    /** Mint a fresh payload, show it and copy it. Returns false if not running. */
    private boolean generatePayload() {
        String host = collab.payloadHost();
        if (host == null) return false;
        payloadField.setText(host);
        payloadField.setCaretPosition(0);
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(host), null);
        ctx.alert("collaborator", "generated collaborator payload " + host);
        return true;
    }

    private void showSelected() {
        int keep = detailTabs.getSelectedIndex();
        detailTabs.removeAll();
        int row = table.getSelectedRow();
        if (row < 0) return;
        Interaction i = model.at(table.convertRowIndexToModel(row));
        if (i == null) return;

        // Description, phrased like the commercial tool and naming the query type
        // for a DNS lookup.
        StringBuilder d = new StringBuilder();
        if (i.isDns()) {
            String qt = i.queryType().isBlank() ? "" : " of type " + i.queryType();
            d.append("The collaborator server received a DNS lookup").append(qt)
             .append(" for the domain name ").append(i.fullId()).append(".\n\n");
            d.append("The lookup was received from IP address ").append(i.remoteAddress());
        } else if (i.isHttp()) {
            d.append("The collaborator server received an HTTP request.\n\n");
            d.append("The request was received from IP address ").append(i.remoteAddress());
        } else if (i.isSmtp()) {
            d.append("The collaborator server received an SMTP connection.\n\n");
            d.append("The connection was received from IP address ").append(i.remoteAddress());
        } else {
            d.append("The collaborator server received a ").append(i.protocol().toUpperCase())
             .append(" interaction.\n\n");
            d.append("It was received from IP address ").append(i.remoteAddress());
        }
        d.append(" at ").append(i.timestamp()).append(".");
        detailTabs.addTab("description", textTab(d.toString(), true));

        // Per-protocol tabs, matching the commercial tool.
        if (i.isDns()) {
            detailTabs.addTab("DNS query", textTab(orNone(i.rawRequest(), "query"), false));
        } else if (i.isHttp()) {
            detailTabs.addTab("request to collaborator", textTab(orNone(i.rawRequest(), "request"), false));
            detailTabs.addTab("response from collaborator", textTab(orNone(i.rawResponse(), "response"), false));
        } else {
            detailTabs.addTab("interaction", textTab(orNone(i.rawRequest(), "data"), false));
            if (!i.rawResponse().isBlank())
                detailTabs.addTab("response", textTab(i.rawResponse(), false));
        }

        // Keep the same tab selected across rows where it still exists, so a
        // tester watching the request tab is not bounced back to description.
        if (keep >= 0 && keep < detailTabs.getTabCount()) detailTabs.setSelectedIndex(keep);
    }

    private static String orNone(String s, String what) {
        return s == null || s.isBlank() ? "(no raw " + what + " recorded for this interaction)" : s;
    }

    private void refreshStatus() {
        if (statusLabel == null) return;
        statusLabel.setText(collab.status());
        statusLabel.setForeground(collab.isRunning() ? new Color(0x1E, 0x7A, 0x33) : Ui.ERROR_RED);
    }

    // ------------------------------------------------------------- options tab

    private JComponent buildOptions() {
        OptionsPage page = new OptionsPage();

        JTextArea intro = Ui.note(
            "The collaborator catches out-of-band interactions: a DNS, HTTP or SMTP callback that "
            + "a target makes to a hostname the scanner planted. It is how blind vulnerabilities "
            + "such as server-side request forgery, blind cross-site scripting and Log4Shell are "
            + "confirmed, since the proof is the target reaching out to a host only the scanner "
            + "knew about.");
        page.section("about", intro);

        JTextField server = Ui.field(collab.serverUrl(), 34);
        JTextField tokenField = Ui.field(
                ctx.settings().get(Collaborator.TOKEN_KEY, ""), 34);
        JButton apply = Ui.button("apply and restart",
                "save the server and restart the collaborator session");
        apply.addActionListener(e -> {
            ctx.settings().set(Collaborator.SERVER_KEY, server.getText().trim());
            ctx.settings().set(Collaborator.TOKEN_KEY, tokenField.getText().trim());
            String err = collab.start();
            if (err != null) {
                JOptionPane.showMessageDialog(ctx.frame(), err, "collaborator",
                        JOptionPane.WARNING_MESSAGE);
            }
            refreshStatus();
        });
        JTextArea serverNote = Ui.note(
            "Use the public default for convenience, or a self-hosted interactsh server when an "
            + "engagement forbids any target interaction from transiting a third party. The "
            + "interaction contents are encrypted to this client either way, but a self-hosted "
            + "server keeps everything, including the callbacks themselves, under your control. An "
            + "authentication token is only needed if your server requires one.");
        page.section("collaborator server",
                OptionsPage.fieldRow("server URL", server, ""),
                OptionsPage.fieldRow("auth token", tokenField, "(optional)"),
                apply, serverNote);

        return page;
    }

    // ------------------------------------------------------------ table model

    private final class InteractionTableModel extends AbstractTableModel {
        private final String[] cols = {"#", "type", "source", "host"};
        private final List<Interaction> rows = new ArrayList<>();
        private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");

        void add(List<Interaction> added) {
            if (added.isEmpty()) return;
            int from = rows.size();
            rows.addAll(added);
            fireTableRowsInserted(from, rows.size() - 1);
        }

        void clearAll() { rows.clear(); fireTableDataChanged(); }

        Interaction at(int row) { return (row >= 0 && row < rows.size()) ? rows.get(row) : null; }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override public Object getValueAt(int r, int c) {
            Interaction i = rows.get(r);
            return switch (c) {
                case 0 -> r + 1;
                case 1 -> i.protocol().toUpperCase();
                case 2 -> i.remoteAddress();
                case 3 -> i.fullId();
                default -> "";
            };
        }
    }
}
