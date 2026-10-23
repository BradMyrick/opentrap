package opentrap.dashboard;

import opentrap.core.SuiteContext;
import opentrap.ui.Theme;
import opentrap.ui.Ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * The New scan wizard: a left step rail (Scan type, Scan details, Scan
 * configuration, Application login, Resource pool) and a content area, with
 * Cancel / Previous / Next / Scan along the bottom.
 *
 * <p>Behaviour mirrors the reference wizard: only "Scan type" is shown until a
 * scan type is chosen, at which point the remaining steps appear and the Scan
 * button becomes enabled (orange). The dialog only gathers configuration; the
 * caller reads the getters and starts the scan.
 */
public class NewScanWizard extends JDialog {

    private static final String[] STEPS = {
            "Scan type", "Scan details", "Scan configuration", "Application login", "Resource pool"
    };
    private static final String[] STEP_ICONS = { "type", "details", "config", "login", "pool" };

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JToggleButton[] rail = new JToggleButton[STEPS.length];
    private int step;
    private boolean typeChosen;

    // Collected configuration.
    private final JRadioButton typeCrawlAudit = new JRadioButton();
    private final JRadioButton typeCrawl = new JRadioButton();
    private final JRadioButton typeApiOnly = new JRadioButton();
    private final JTextArea urls = new JTextArea();
    private String configPreset = "Lightweight";
    private final java.util.Map<String, JPanel> configCards = new java.util.LinkedHashMap<>();
    private final JSpinner threads = new JSpinner(new SpinnerNumberModel(10, 1, 50, 1));

    // Custom tier controls, seeded from "Balanced" as a sensible starting point.
    // Shown only while Custom is the selected tier; read by config() to build a
    // ScanConfig straight from the engine knobs the user set.
    private JComponent customBox;
    private final JComboBox<String> cStrength =
            new JComboBox<>(new String[]{"LOW", "MEDIUM", "HIGH", "INSANE"});
    private final JComboBox<String> cThreshold =
            new JComboBox<>(new String[]{"OFF", "LOW", "MEDIUM", "HIGH"});
    private final JSpinner cCrawlDepth = new JSpinner(new SpinnerNumberModel(10, 0, 100, 1));
    private final JSpinner cCrawlDuration = new JSpinner(new SpinnerNumberModel(0, 0, 1440, 5));
    private final JCheckBox cSubmitForms = Ui.check("Submit forms while crawling", true);
    private final JCheckBox cAjax = Ui.check("AJAX spider (crawl JavaScript applications)", true);
    private final JSpinner cAjaxDuration = new JSpinner(new SpinnerNumberModel(30, 0, 600, 5));
    private final JSpinner cAjaxDepth = new JSpinner(new SpinnerNumberModel(10, 0, 100, 1));
    private final JSpinner cAjaxStates = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 10));
    private final JCheckBox vQuery   = Ui.check("Query string", true);
    private final JCheckBox vBody    = Ui.check("POST body", true);
    private final JCheckBox vPath    = Ui.check("URL path", true);
    private final JCheckBox vHeaders = Ui.check("Headers", true);
    private final JCheckBox vCookie  = Ui.check("Cookies", true);
    private final JCheckBox rMultipart = Ui.check("Multipart", true);
    private final JCheckBox rXml       = Ui.check("XML", true);
    private final JCheckBox rJson      = Ui.check("JSON", true);
    private final JCheckBox rGwt       = Ui.check("GWT", true);
    private final JCheckBox rOdata     = Ui.check("OData", true);
    private final JSpinner cDelay = new JSpinner(new SpinnerNumberModel(0, 0, 5000, 50));
    private final JSpinner cScanDuration = new JSpinner(new SpinnerNumberModel(0, 0, 1440, 5));

    private JButton prevButton, nextButton, scanButton;
    private boolean started;

    // Scan configuration tiers. Every tier tests the same things (all input
    // vectors, all rules, the same reporting threshold); they differ only in how
    // much the crawl discovers and how hard each rule attacks it, so the
    // descriptions talk about depth and strength, not fixed time promises.
    private static final String[][] CONFIGS = {
        {"Lightweight", "Full coverage at low attack strength over a shallow crawl. The quickest way to surface obvious issues."},
        {"Fast",        "Full coverage at medium strength over a wider crawl. A good first pass on most targets."},
        {"Balanced",    "Full coverage at high strength with JavaScript crawling. Thorough without running unbounded."},
        {"Deep",        "Full coverage at maximum strength over an unlimited crawl. The most comprehensive scan; time depends on the target's size."},
        {"Custom",      "Set every engine knob yourself: crawl depth, AJAX budget, attack strength, threshold and injection points."},
    };

    public NewScanWizard(SuiteContext ctx, Frame owner) {
        super(owner, "New scan", true);

        content.add(buildScanType(), STEPS[0]);
        content.add(buildScanDetails(ctx), STEPS[1]);
        content.add(buildScanConfiguration(), STEPS[2]);
        content.add(buildApplicationLogin(), STEPS[3]);
        content.add(buildResourcePool(), STEPS[4]);

        setLayout(new BorderLayout());
        add(buildRail(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        selectStep(0);
        revealSteps(false);   // only "Scan type" until a type is chosen

        setSize(940, 620);
        setLocationRelativeTo(owner);
    }

    // ---- getters the caller reads after the dialog closes ----

    public boolean started()       { return started; }
    public boolean crawlAndAudit() { return typeCrawlAudit.isSelected(); }
    public int threads()           { return (Integer) threads.getValue(); }

    public String targetUrl() {
        for (String line : urls.getText().split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) return t;
        }
        return "";
    }

    /** The real engine settings implied by the chosen configuration tier. */
    public opentrap.engine.ScanConfig config() {
        if (!"Custom".equals(configPreset)) return opentrap.engine.ScanConfig.preset(configPreset);
        int inject = (vQuery.isSelected()   ? opentrap.engine.ScanConfig.INJECT_QUERY   : 0)
                   | (vBody.isSelected()    ? opentrap.engine.ScanConfig.INJECT_BODY    : 0)
                   | (vPath.isSelected()    ? opentrap.engine.ScanConfig.INJECT_PATH    : 0)
                   | (vHeaders.isSelected() ? opentrap.engine.ScanConfig.INJECT_HEADERS : 0)
                   | (vCookie.isSelected()  ? opentrap.engine.ScanConfig.INJECT_COOKIE  : 0);
        int rpc = (rMultipart.isSelected() ? opentrap.engine.ScanConfig.RPC_MULTIPART : 0)
                | (rXml.isSelected()       ? opentrap.engine.ScanConfig.RPC_XML       : 0)
                | (rJson.isSelected()      ? opentrap.engine.ScanConfig.RPC_JSON      : 0)
                | (rGwt.isSelected()       ? opentrap.engine.ScanConfig.RPC_GWT       : 0)
                | (rOdata.isSelected()     ? opentrap.engine.ScanConfig.RPC_ODATA     : 0);
        return new opentrap.engine.ScanConfig(
                "Custom",
                (Integer) cCrawlDepth.getValue(),
                (Integer) cCrawlDuration.getValue(),
                cSubmitForms.isSelected(),
                cAjax.isSelected(),
                (Integer) cAjaxDuration.getValue(),
                (Integer) cAjaxDepth.getValue(),
                (Integer) cAjaxStates.getValue(),
                (String) cStrength.getSelectedItem(),
                (String) cThreshold.getSelectedItem(),
                inject, rpc,
                (Integer) cDelay.getValue(),
                (Integer) cScanDuration.getValue());
    }

    // ---- left rail ----

    private JComponent buildRail() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.SURFACE);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.GRID));
        ButtonGroup g = new ButtonGroup();
        for (int i = 0; i < STEPS.length; i++) {
            final int idx = i;
            JToggleButton b = new JToggleButton(
                    "<html><div style='text-align:center'>" + STEPS[i].replace(" ", "<br>") + "</div></html>");
            b.setFont(Ui.PLAIN);
            b.setFocusable(false);
            b.setContentAreaFilled(false);
            b.setOpaque(true);
            b.setBackground(Theme.SURFACE);
            b.setHorizontalTextPosition(SwingConstants.CENTER);
            b.setVerticalTextPosition(SwingConstants.BOTTOM);
            b.setBorder(Ui.pad(12, 14, 12, 14));
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(150, 80));
            b.addActionListener(e -> selectStep(idx));
            g.add(b);
            rail[i] = b;
            p.add(b);
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(p, BorderLayout.NORTH);
        wrap.setBackground(Theme.SURFACE);
        wrap.setPreferredSize(new Dimension(152, 10));
        return wrap;
    }

    /** Show or hide every step after "Scan type". */
    private void revealSteps(boolean show) {
        for (int k = 1; k < rail.length; k++) rail[k].setVisible(show);
        rail[0].getParent().revalidate();
        rail[0].getParent().repaint();
    }

    private void selectStep(int i) {
        // Steps beyond the first are only reachable once a scan type is chosen.
        if (!typeChosen) i = 0;
        step = Math.max(0, Math.min(STEPS.length - 1, i));
        for (int k = 0; k < rail.length; k++) {
            boolean on = k == step;
            rail[k].setSelected(on);
            rail[k].setBackground(on ? Ui.ACCENT : Theme.SURFACE);
            rail[k].setForeground(on ? Color.WHITE : Theme.TEXT);
            rail[k].setIcon(railIcon(STEP_ICONS[k], on ? Color.WHITE : Theme.MUTED));
        }
        cards.show(content, STEPS[step]);
        updateButtons();
    }

    // ---- step panels ----

    private JComponent buildScanType() {
        ButtonGroup g = new ButtonGroup();
        g.add(typeCrawlAudit);
        g.add(typeCrawl);
        g.add(typeApiOnly);
        JPanel cardsRow = new JPanel(new GridLayout(1, 3, 16, 0));
        cardsRow.setOpaque(false);
        cardsRow.add(typeCard(typeCrawlAudit, "Crawl and audit",
                "Maps the attack surface and identifies vulnerabilities. Scans the web application and any detected APIs.", true));
        cardsRow.add(typeCard(typeCrawl, "Crawl",
                "Maps the attack surface of the web application and any detected APIs.", true));
        // API-only scanning is not implemented yet, so the card is shown but not
        // selectable, rather than pretending to offer it.
        cardsRow.add(typeCard(typeApiOnly, "API-only scan",
                "Scans only detected APIs. Not yet available.", false));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(cardsRow, BorderLayout.NORTH);
        return stepPanel("Scan type", "Select the type of scan you'd like to run.", wrap);
    }

    private JComponent typeCard(JRadioButton radio, String title, String desc, boolean enabled) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(enabled ? Theme.BG : Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.GRID), Ui.pad(14, 14, 14, 14)));
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        head.setOpaque(false);
        radio.setOpaque(false);
        radio.setEnabled(enabled);
        head.add(radio);
        JLabel titleLabel = Ui.bold(title);
        if (!enabled) titleLabel.setForeground(Theme.MUTED);
        head.add(titleLabel);
        JTextArea d = Ui.note(desc);
        card.add(head, BorderLayout.NORTH);
        card.add(d, BorderLayout.CENTER);
        if (enabled) {
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            java.awt.event.MouseAdapter pick = new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    radio.setSelected(true);
                    onTypeChosen();
                }
            };
            for (Component c : new Component[]{card, head, titleLabel, d}) c.addMouseListener(pick);
            radio.addActionListener(e -> onTypeChosen());
        }
        return card;
    }

    /** First scan-type selection: reveal the rest of the steps and enable Scan. */
    private void onTypeChosen() {
        if (typeChosen) { updateButtons(); return; }
        typeChosen = true;
        revealSteps(true);
        updateButtons();
    }

    private JComponent buildScanDetails(SuiteContext ctx) {
        urls.setFont(Ui.MONO);
        StringBuilder sb = new StringBuilder();
        try {
            for (var r : ctx.scope().include()) {
                String host = r.host == null ? "" : r.host.replaceAll("[\\^$]", "");
                if (!host.isBlank()) sb.append("https://").append(host).append("/\n");
            }
        } catch (Exception ignored) {}
        urls.setText(sb.toString());

        JScrollPane sp = Ui.scroll(urls);
        sp.setPreferredSize(new Dimension(500, 220));

        JTextArea urlsNote = Ui.note("OpenTRAP will begin crawling from these URLs, and by default "
                + "includes everything beneath the specified URLs' folders. One URL per line.");

        // Protocol settings.
        JRadioButton pHttpHttps = Ui.radio("Scan using HTTP & HTTPS", true);
        JRadioButton pSpecified = Ui.radio("Scan using my specified protocols", false);
        ButtonGroup pg = new ButtonGroup();
        pg.add(pHttpHttps); pg.add(pSpecified);
        JPanel protoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        protoRow.setOpaque(false);
        protoRow.add(pHttpHttps); protoRow.add(Box.createHorizontalStrut(18)); protoRow.add(pSpecified);

        // Detailed scope configuration: an expander that reveals the in-scope hosts.
        JTextArea scopeInfo = Ui.note(sb.length() == 0
                ? "Scope is taken from the URLs above (and everything beneath their folders)."
                : "In scope:\n" + sb.toString().trim());
        scopeInfo.setVisible(false);
        JButton scopeToggle = new JButton("› Detailed scope configuration");
        scopeToggle.setFont(Ui.PLAIN);
        scopeToggle.setBorder(Ui.pad(2, 0, 2, 0));
        scopeToggle.setContentAreaFilled(false);
        scopeToggle.setFocusPainted(false);
        scopeToggle.setHorizontalAlignment(SwingConstants.LEFT);
        scopeToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        scopeToggle.addActionListener(e -> {
            boolean now = !scopeInfo.isVisible();
            scopeInfo.setVisible(now);
            scopeToggle.setText((now ? "⌄ " : "› ") + "Detailed scope configuration");
        });

        // Advanced scan parameters: the isolated-scan toggle (not yet honoured, so
        // shown disabled rather than as a control that does nothing).
        JCheckBox isolated = Ui.check("Run isolated scan", false);
        isolated.setEnabled(false);
        isolated.setToolTipText("Isolated scans are not yet available.");

        // Lay the body out in a single full-width column with GridBag, which keeps
        // every row flush-left and lets the URLs box fill the width.
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1; c.insets = new Insets(0, 0, 0, 0);
        int row = 0;
        c.gridy = row++; c.fill = GridBagConstraints.BOTH; c.weighty = 0; c.ipady = 200; body.add(sp, c);
        c.fill = GridBagConstraints.HORIZONTAL; c.ipady = 0;
        c.gridy = row++; c.insets = new Insets(6, 0, 16, 0); body.add(urlsNote, c);
        c.gridy = row++; c.insets = new Insets(0, 0, 6, 0); body.add(sectionLabel("Protocol settings"), c);
        c.gridy = row++; c.insets = new Insets(0, 0, 6, 0); body.add(protoRow, c);
        c.gridy = row++; c.insets = new Insets(0, 0, 0, 0); body.add(scopeToggle, c);
        c.gridy = row++; c.insets = new Insets(2, 0, 16, 0); body.add(scopeInfo, c);
        c.gridy = row++; c.insets = new Insets(0, 0, 6, 0); body.add(sectionLabel("Advanced scan parameters"), c);
        c.gridy = row++; c.insets = new Insets(0, 0, 4, 0);
        body.add(Ui.note("Isolate the scan so its results do not appear in the main site map or issue log."), c);
        c.gridy = row++; c.insets = new Insets(0, 0, 0, 0); body.add(isolated, c);
        // Filler row pushes everything to the top.
        c.gridy = row; c.weighty = 1; c.fill = GridBagConstraints.BOTH; body.add(Box.createGlue(), c);

        return stepPanel("URLs to scan",
                "Define the URLs to scan. OpenTRAP will begin crawling from these URLs.", body);
    }

    private JComponent buildScanConfiguration() {
        // The configurations are shown directly as a selectable list (no dropdown).
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.setBorder(BorderFactory.createLineBorder(Ui.GRID));
        for (String[] c : CONFIGS) {
            JPanel card = configCard(c[0], c[1], true);
            configCards.put(c[0], card);
            list.add(card);
        }

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.setOpaque(false);
        listWrap.add(list, BorderLayout.NORTH);
        body.add(listWrap, BorderLayout.NORTH);

        // The Custom knob panel sits below the tier list and appears only while
        // Custom is selected.
        customBox = Ui.scroll(buildCustomPanel());
        customBox.setVisible(false);
        body.add(customBox, BorderLayout.CENTER);

        // Save / Load named configurations is not implemented, so disabled.
        JButton save = Ui.button("Save");
        JButton load = Ui.button("Load");
        save.setEnabled(false); load.setEnabled(false);
        save.setToolTipText("Saving named configurations is coming soon.");
        load.setToolTipText("Loading named configurations is coming soon.");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(save); actions.add(load);

        JComponent panel = stepPanel("Scan configuration",
                "Select the type of scan configuration you want to run. You can adjust it as needed.", body);
        if (panel instanceof JPanel jp && jp.getComponent(0) instanceof JPanel head) {
            head.add(actions, BorderLayout.EAST);
        }
        selectConfig(configPreset);
        return panel;
    }

    private JPanel configCard(String name, String desc, boolean enabled) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(Theme.BG);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.GRID), Ui.pad(10, 12, 10, 12)));
        row.add(new JLabel(configIcon(name)), BorderLayout.WEST);
        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        JLabel title = Ui.bold(name);
        if (!enabled) title.setForeground(Theme.MUTED);
        text.add(title, BorderLayout.NORTH);
        text.add(Ui.note(desc), BorderLayout.CENTER);
        row.add(text, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        // A tier whose configuration UI is not built yet is shown but not
        // selectable, rather than offering a control that does nothing.
        if (!enabled) {
            row.setToolTipText(name + " configuration is coming soon.");
            return row;
        }
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        java.awt.event.MouseAdapter pick = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { selectConfig(name); }
        };
        for (Component c : row.getComponents()) c.addMouseListener(pick);
        row.addMouseListener(pick);
        return row;
    }

    private void selectConfig(String name) {
        if (name == null) return;
        configPreset = name;
        for (var e : configCards.entrySet()) {
            boolean on = e.getKey().equals(name);
            e.getValue().setBackground(on ? Ui.SELECTION : Theme.BG);
        }
        if (customBox != null) customBox.setVisible("Custom".equals(name));
    }

    private JComponent buildApplicationLogin() {
        JPanel box = new JPanel(new GridBagLayout());
        box.setOpaque(false);
        JLabel msg = new JLabel("<html><div style='text-align:center'><b>Application login</b><br><br>"
                + "Recorded login sequences and credential-based login are <b>coming soon</b>.<br>"
                + "This section is under construction.</div></html>");
        msg.setFont(Ui.PLAIN);
        msg.setForeground(Theme.MUTED);
        box.add(msg);
        return stepPanel("Application login", "Configure how the scan authenticates (not yet available).", box);
    }

    private JComponent buildResourcePool() {
        threads.setFont(Ui.PLAIN);
        threads.setPreferredSize(new Dimension(70, threads.getPreferredSize().height));
        threads.setMaximumSize(threads.getPreferredSize());
        JPanel spinnerWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        spinnerWrap.setOpaque(false);
        spinnerWrap.add(threads);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(Ui.form("Concurrent requests:", spinnerWrap), BorderLayout.NORTH);
        body.add(Ui.note("The number of requests the scan engine sends at once. Lower this to "
                + "be gentler on the target; raise it to scan faster."), BorderLayout.CENTER);
        return stepPanel("Resource pool", "Specify the resources the scan may use.", body);
    }

    /**
     * The Custom tier's editor: every engine knob a scan can set, grouped into
     * discovery, attack, and the injection points. Seeded from "Balanced" so it
     * starts from a sensible, thorough configuration the user then adjusts.
     */
    private JComponent buildCustomPanel() {
        cStrength.setSelectedItem("HIGH");
        cThreshold.setSelectedItem("MEDIUM");
        cStrength.setFont(Ui.PLAIN);
        cThreshold.setFont(Ui.PLAIN);

        // The AJAX budget inputs follow the AJAX checkbox.
        Runnable syncAjax = () -> {
            boolean on = cAjax.isSelected();
            cAjaxDuration.setEnabled(on);
            cAjaxDepth.setEnabled(on);
            cAjaxStates.setEnabled(on);
        };
        cAjax.addActionListener(e -> syncAjax.run());
        syncAjax.run();

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);

        col.add(left(sectionLabel("Discovery")));
        col.add(left(Ui.form(
                "Crawl depth:", compact(cCrawlDepth),
                "Crawl time budget (min):", compact(cCrawlDuration))));
        col.add(left(cSubmitForms));
        col.add(left(cAjax));
        col.add(left(Ui.form(
                "AJAX time budget (min):", compact(cAjaxDuration),
                "AJAX depth:", compact(cAjaxDepth),
                "AJAX states:", compact(cAjaxStates))));
        col.add(left(Ui.note("0 means unlimited depth and states, and no time limit.")));

        col.add(Box.createVerticalStrut(10));
        col.add(left(sectionLabel("Attack")));
        col.add(left(Ui.form(
                "Attack strength:", compact(cStrength),
                "Alert threshold:", compact(cThreshold),
                "Request delay (ms):", compact(cDelay),
                "Active-scan cap (min):", compact(cScanDuration))));
        col.add(left(Ui.note("Keep the active-scan cap at 0 so every test runs to completion.")));

        col.add(Box.createVerticalStrut(10));
        col.add(left(sectionLabel("Injection points")));
        col.add(left(row(vQuery, vBody, vPath, vHeaders, vCookie)));
        col.add(left(sectionLabel("Request body types")));
        col.add(left(row(rMultipart, rXml, rJson, rGwt, rOdata)));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(Ui.pad(6, 0, 0, 0));
        wrap.add(col, BorderLayout.NORTH);
        return wrap;
    }

    /** Wrap a component flush-left for a vertical BoxLayout column. */
    private static JComponent left(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(c);
        return p;
    }

    /** Keep a spinner or combo at its natural (small) width rather than stretched. */
    private static JComponent compact(JComponent c) {
        Dimension d = c.getPreferredSize();
        c.setPreferredSize(new Dimension(Math.max(d.width, 90), d.height));
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    /** A left-aligned row of check boxes. */
    private static JComponent row(JCheckBox... boxes) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        p.setOpaque(false);
        for (JCheckBox b : boxes) { b.setOpaque(false); p.add(b); }
        return p;
    }

    private JComponent stepPanel(String title, String subtitle, JComponent body) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Theme.BG);
        p.setBorder(Ui.pad(16, 20, 16, 20));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        titles.add(Ui.heading(title), BorderLayout.NORTH);
        titles.add(Ui.note(subtitle), BorderLayout.CENTER);
        head.add(titles, BorderLayout.CENTER);
        p.add(head, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = Ui.bold(text);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    // ---- bottom buttons ----

    private JComponent buildButtons() {
        JButton cancel = Ui.button("Cancel");
        prevButton = Ui.button("Previous");
        nextButton = Ui.button("Next");
        scanButton = Ui.button("Scan");
        scanButton.setEnabled(false);   // greyed until a scan type is chosen
        cancel.addActionListener(e -> { started = false; dispose(); });
        prevButton.addActionListener(e -> selectStep(step - 1));
        nextButton.addActionListener(e -> selectStep(step + 1));
        scanButton.addActionListener(e -> doScan());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.GRID));
        bar.add(cancel);
        bar.add(prevButton);
        bar.add(nextButton);
        bar.add(scanButton);
        return bar;
    }

    private void updateButtons() {
        prevButton.setEnabled(step > 0);
        nextButton.setEnabled(typeChosen && step < STEPS.length - 1);
        boolean scannable = typeChosen;
        scanButton.setEnabled(scannable);
        // Turn the Scan button orange once it is usable, grey before that.
        scanButton.putClientProperty(Theme.PRIMARY, scannable ? Boolean.TRUE : Boolean.FALSE);
        scanButton.repaint();
    }

    private void doScan() {
        if (targetUrl().isEmpty()) {
            selectStep(1);
            JOptionPane.showMessageDialog(this, "Enter at least one URL to scan.",
                    "New scan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (opentrap.core.HttpService.parse(targetUrl()) == null) {
            selectStep(1);
            JOptionPane.showMessageDialog(this, "Could not parse that URL. Use the form https://host/path.",
                    "New scan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        started = true;
        dispose();
    }

    // ---- icons ----

    private static Icon railIcon(String kind, Color col) {
        int s = 20;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(col);
        g.setStroke(new BasicStroke(1.5f));
        switch (kind) {
            case "type" -> {
                // Target / crosshair.
                g.drawOval(4, 4, 12, 12);
                g.drawOval(8, 8, 4, 4);
                g.drawLine(10, 1, 10, 4);
                g.drawLine(10, 16, 10, 19);
                g.drawLine(1, 10, 4, 10);
                g.drawLine(16, 10, 19, 10);
            }
            case "details" -> { g.drawLine(5, 3, 5, 17); g.drawPolygon(new int[]{5, 15, 5}, new int[]{4, 8, 12}, 3); }
            case "config" -> {
                g.drawOval(7, 7, 6, 6);
                for (int a = 0; a < 360; a += 45) {
                    double r = Math.toRadians(a);
                    g.drawLine((int) (10 + 4 * Math.cos(r)), (int) (10 + 4 * Math.sin(r)),
                               (int) (10 + 6.5 * Math.cos(r)), (int) (10 + 6.5 * Math.sin(r)));
                }
            }
            case "login" -> {
                g.drawRect(10, 4, 6, 12);
                g.drawLine(3, 10, 11, 10);
                g.drawLine(8, 7, 11, 10);
                g.drawLine(8, 13, 11, 10);
            }
            case "pool" -> { g.drawOval(4, 4, 12, 12); g.drawLine(10, 10, 10, 4); g.drawLine(10, 10, 16, 11); }
        }
        g.dispose();
        return new ImageIcon(img);
    }

    private static Icon configIcon(String name) {
        int s = 18;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.MUTED);
        g.setStroke(new BasicStroke(1.4f));
        switch (name) {
            case "Lightweight" -> { g.drawOval(2, 2, 13, 13); g.drawLine(8, 8, 8, 4); g.drawLine(8, 8, 12, 9); }
            case "Fast" -> {
                g.fillPolygon(new int[]{2, 2, 8}, new int[]{3, 15, 9}, 3);
                g.fillPolygon(new int[]{8, 8, 14}, new int[]{3, 15, 9}, 3);
            }
            case "Balanced" -> { g.drawLine(9, 2, 9, 15); g.drawLine(3, 5, 15, 5); g.drawOval(2, 5, 3, 3); g.drawOval(13, 5, 3, 3); }
            case "Deep" -> { g.drawArc(2, 1, 14, 8, 0, -180); g.drawArc(4, 5, 10, 7, 0, -180); g.drawArc(6, 9, 6, 6, 0, -180); }
            case "Custom" -> {
                for (int y : new int[]{4, 9, 14}) { g.drawLine(2, y, 16, y); }
                g.fillRect(5, 2, 3, 4); g.fillRect(11, 7, 3, 4); g.fillRect(7, 12, 3, 4);
            }
        }
        g.dispose();
        return new ImageIcon(img);
    }
}
