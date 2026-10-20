package opentrap.ui;

import opentrap.core.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The shared request/response viewer and editor.
 *
 * Views are gated by a bitmask: 1 for requests, 2 for responses. The "send to"
 * routes are supplied by the suite context, so a tool added later appears in
 * the menu automatically.
 */
public class MessageEditor extends JPanel {

    public static final int REQUEST  = 1;
    public static final int RESPONSE = 2;

    public interface ActionProvider { void contribute(JPopupMenu menu); }

    private final int flags;
    private final boolean editable;
    private final SuiteContext ctx;

    private HttpMessage message = new HttpMessage(new byte[0]);
    private HttpService service;
    private HttpMessage relatedRequest;

    private final JTabbedPane views = new JTabbedPane();
    private final JTextArea rawArea = new JTextArea();
    private final JTable paramTable = new JTable();
    private final JTextArea headersArea = new JTextArea();
    private final HexView hexView;
    private final JEditorPane renderPane = new JEditorPane();
    private final JTextField searchField = Ui.field("", 18);
    private final JLabel matchLabel = Ui.label("0 matches");
    private final JLabel selectionLabel = Ui.label("");

    private final List<ActionProvider> providers = new ArrayList<>();
    private boolean updating;
    private Runnable changeListener;

    public MessageEditor(SuiteContext ctx, int flags, boolean editable) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.flags = flags;
        this.editable = editable;
        this.hexView = new HexView(editable);

        rawArea.setFont(Ui.MONO);
        rawArea.setEditable(editable);
        rawArea.setLineWrap(true);
        rawArea.setWrapStyleWord(false);
        rawArea.setTabSize(4);

        headersArea.setFont(Ui.MONO);
        headersArea.setEditable(false);

        Ui.styleTable(paramTable);
        renderPane.setEditable(false);
        renderPane.setContentType("text/html");

        views.setFont(Ui.PLAIN);
        views.addTab("raw", Ui.scroll(rawArea));
        if ((flags & REQUEST) != 0) views.addTab("params", Ui.scroll(paramTable));
        views.addTab("headers", Ui.scroll(headersArea));
        views.addTab("hex", hexView);
        if ((flags & RESPONSE) != 0) views.addTab("render", Ui.scroll(renderPane));

        views.addChangeListener(e -> syncFromRaw());

        add(views, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        rawArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { onEdit(); }
            public void removeUpdate(DocumentEvent e)  { onEdit(); }
            public void changedUpdate(DocumentEvent e) { onEdit(); }
        });
        rawArea.addCaretListener(e -> updateSelectionLabel());

        installPopup(rawArea);
        installPopup(paramTable);
        installPopup(headersArea);
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(Ui.pad(3, 4, 3, 4));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        JButton prev = Ui.button("<", "move to the previous match");
        JButton next = Ui.button(">", "move to the next match");
        prev.setMargin(new Insets(0, 6, 0, 6));
        next.setMargin(new Insets(0, 6, 0, 6));
        searchField.setToolTipText("type a search term");
        left.add(Ui.label("search:"));
        left.add(searchField);
        left.add(prev);
        left.add(next);
        left.add(matchLabel);
        p.add(left, BorderLayout.WEST);
        p.add(selectionLabel, BorderLayout.EAST);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { highlightMatches(0); }
            public void removeUpdate(DocumentEvent e)  { highlightMatches(0); }
            public void changedUpdate(DocumentEvent e) { highlightMatches(0); }
        });
        next.addActionListener(e -> highlightMatches(1));
        prev.addActionListener(e -> highlightMatches(-1));
        return p;
    }

    // ---------------- content ----------------

    public void setMessage(HttpService svc, HttpMessage m) { setMessage(svc, m, null); }

    public void setMessage(HttpService svc, HttpMessage m, HttpMessage related) {
        this.service = svc;
        this.message = m == null ? new HttpMessage(new byte[0]) : m;
        this.relatedRequest = related;
        updating = true;
        rawArea.setText(Bytes.str(message.raw()));
        rawArea.setCaretPosition(0);
        updating = false;
        refreshViews();
    }

    public HttpMessage message()  { return message; }
    public HttpService service()  { return service; }
    public byte[] bytes()         { return Bytes.bytes(rawArea.getText()); }

    /**
     * Highlight every occurrence of the given text, as if it had been typed into
     * the search box, and scroll the first into view. Used to auto-highlight an
     * issue's evidence in its request/response, the way Burp does. A null or empty
     * term clears any highlight.
     */
    public void highlight(String term) {
        searchField.setText(term == null ? "" : term);   // fires highlightMatches(0)
        if (term == null || term.isEmpty()) return;
        int pos = rawArea.getText().toLowerCase().indexOf(term.toLowerCase());
        if (pos >= 0) {
            try { rawArea.setCaretPosition(pos); } catch (RuntimeException ignored) {}
        }
    }

    public void setChangeListener(Runnable r) { this.changeListener = r; }

    public void addActionProvider(ActionProvider p) { providers.add(p); }

    private void onEdit() {
        if (updating) return;
        message.setRaw(bytes());
        if (changeListener != null) changeListener.run();
    }

    private void syncFromRaw() {
        if (updating) return;
        message.setRaw(bytes());
        refreshViews();
    }

    private void refreshViews() {
        headersArea.setText(message.headerText());
        headersArea.setCaretPosition(0);
        hexView.setData(message.raw());

        if ((flags & REQUEST) != 0) {
            DefaultTableModel m = new DefaultTableModel(
                    new Object[]{"type", "name", "value"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
            for (Param p : message.parameters()) {
                m.addRow(new Object[]{p.typeLabel(), p.name(), p.value()});
            }
            paramTable.setModel(m);
            Ui.setWidths(paramTable, 90, 160, 320);
        }

        if ((flags & RESPONSE) != 0 && views.getSelectedIndex() == views.indexOfTab("render")) {
            renderHtml();
        }
        updateSelectionLabel();
    }

    private void renderHtml() {
        try {
            String body = message.bodyText();
            // Strip remote references so rendering never makes a network call.
            body = body.replaceAll("(?is)<script.*?</script>", "")
                       .replaceAll("(?i)<link[^>]*>", "")
                       .replaceAll("(?i)src\\s*=\\s*\"https?://[^\"]*\"", "");
            renderPane.setText(body);
            renderPane.setCaretPosition(0);
        } catch (Exception e) {
            renderPane.setText("<html><body><i>could not render this response</i></body></html>");
        }
    }

    private void updateSelectionLabel() {
        int a = rawArea.getSelectionStart(), b = rawArea.getSelectionEnd();
        int n = Math.abs(b - a);
        if (n > 0) selectionLabel.setText("selection: " + n + " (0x" + Integer.toHexString(n) + ")");
        else selectionLabel.setText("length: " + message.length());
    }

    // ---------------- search ----------------

    private int lastMatchPos = -1;

    private void highlightMatches(int direction) {
        Highlighter h = rawArea.getHighlighter();
        h.removeAllHighlights();
        String term = searchField.getText();
        if (term.isEmpty()) { matchLabel.setText("0 matches"); return; }

        String text = rawArea.getText().toLowerCase();
        String want = term.toLowerCase();
        int count = 0;
        List<Integer> positions = new ArrayList<>();
        int i = text.indexOf(want);
        while (i >= 0) {
            positions.add(i);
            try {
                h.addHighlight(i, i + want.length(),
                        new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFF, 0xF1, 0x76)));
            } catch (BadLocationException ignored) {}
            count++;
            i = text.indexOf(want, i + 1);
        }
        matchLabel.setText(count + (count == 1 ? " match" : " matches"));

        if (!positions.isEmpty() && direction != 0) {
            int idx = positions.indexOf(lastMatchPos);
            idx = (idx + direction + positions.size()) % positions.size();
            lastMatchPos = positions.get(idx);
            rawArea.setCaretPosition(lastMatchPos);
            rawArea.select(lastMatchPos, lastMatchPos + want.length());
            rawArea.requestFocusInWindow();
        } else if (!positions.isEmpty()) {
            lastMatchPos = positions.get(0);
        }
    }

    // ---------------- context menu ----------------

    private void installPopup(JComponent c) {
        c.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybe(e); }
            @Override public void mouseReleased(MouseEvent e) { maybe(e); }
            private void maybe(MouseEvent e) {
                if (e.isPopupTrigger()) buildMenu().show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    /**
     * Whether this editor is showing a request. The flags settle it for the
     * single-purpose views; the intercept editor is built with both, so there
     * the loaded message decides. Without this an intercepted message was sent
     * as request *and* response, which handed the comparer the same bytes twice.
     */
    private boolean showsRequest(HttpMessage shown) {
        if ((flags & REQUEST) == 0) return false;
        if ((flags & RESPONSE) == 0) return true;
        return shown.isRequest();
    }

    /** Build the action menu. Also used by toolbar "action" buttons. */
    public JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();
        boolean haveRequest = (flags & REQUEST) != 0 || relatedRequest != null;
        boolean haveMessage = message != null && message.raw().length > 0;
        boolean anySend = false;

        for (String tool : ctx.sinkNames()) {
            final String t = tool;
            if (ctx.sinkTakesSingleItem(t)) {
                // One item per send: the comparer gets exactly the view that was
                // right-clicked, never its counterpart. Sending the pair would
                // load two rows and leave the user comparing a request against
                // its own response.
                if (!haveMessage) continue;
                JMenuItem mi = new JMenuItem("send to " + t);
                mi.setFont(Ui.PLAIN);
                mi.addActionListener(e -> {
                    HttpMessage own = message.copy();
                    boolean asRequest = showsRequest(own);
                    ctx.sendTo(t, service, asRequest ? own : null, asRequest ? null : own, null);
                });
                menu.add(mi);
                anySend = true;
            } else if (service != null && haveRequest) {
                JMenuItem mi = new JMenuItem("send to " + t);
                mi.setFont(Ui.PLAIN);
                mi.addActionListener(e -> {
                    HttpMessage own = message.copy();
                    boolean asRequest = showsRequest(own);
                    HttpMessage req = asRequest ? own
                            : (relatedRequest == null ? null : relatedRequest.copy());
                    HttpMessage resp = asRequest ? null : own;
                    ctx.sendTo(t, service, req, resp, null);
                });
                menu.add(mi);
                anySend = true;
            }
        }
        if (anySend) menu.addSeparator();

        if ((flags & REQUEST) != 0 && editable) {
            JMenuItem m1 = new JMenuItem("change request method");
            m1.setFont(Ui.PLAIN);
            m1.addActionListener(e -> toggleMethod());
            menu.add(m1);
        }

        JMenuItem copy = new JMenuItem("copy");
        copy.setFont(Ui.PLAIN);
        copy.addActionListener(e -> {
            String sel = rawArea.getSelectedText();
            String text = (sel == null || sel.isEmpty()) ? rawArea.getText() : sel;
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new java.awt.datatransfer.StringSelection(text), null);
        });
        menu.add(copy);

        if (service != null) {
            JMenuItem cu = new JMenuItem("copy URL");
            cu.setFont(Ui.PLAIN);
            cu.addActionListener(e -> {
                String url = service.url() + (message.isRequest() ? message.requestTarget() : "");
                Toolkit.getDefaultToolkit().getSystemClipboard()
                       .setContents(new java.awt.datatransfer.StringSelection(url), null);
            });
            menu.add(cu);
        }

        if (!providers.isEmpty()) {
            menu.addSeparator();
            for (ActionProvider p : providers) p.contribute(menu);
        }
        return menu;
    }

    /** Switch between GET and POST, moving parameters between URL and body. */
    private void toggleMethod() {
        String method = message.method();
        if ("GET".equalsIgnoreCase(method)) {
            String q = message.query();
            message.setMethod("POST");
            message.setRequestTarget(message.path());
            message.setBody(Bytes.bytes(q));
            message.setHeader("Content-Type", "application/x-www-form-urlencoded");
            message.updateContentLength();
        } else {
            String body = message.bodyText();
            message.setMethod("GET");
            String target = message.path();
            if (!body.isBlank()) target += "?" + body;
            message.setRequestTarget(target);
            message.setBody(new byte[0]);
            message.removeHeader("Content-Type");
            message.removeHeader("Content-Length");
        }
        updating = true;
        rawArea.setText(Bytes.str(message.raw()));
        updating = false;
        refreshViews();
        if (changeListener != null) changeListener.run();
    }

    public void selectView(String name) {
        int i = views.indexOfTab(name);
        if (i >= 0) views.setSelectedIndex(i);
    }
}
