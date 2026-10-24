package opentrap.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * The shared "not built yet" affordance.
 *
 * <p>OpenTRAP lays out the full interface for a feature before the behaviour
 * behind it exists. A control for an unbuilt feature stays enabled and, when
 * used, opens this panel instead of doing nothing: it tells the user the feature
 * is not built yet, invites them to contribute it to the project, and gives them
 * a one-click copy of a short anchor they can paste to an AI (or their own notes)
 * to start building it. Whole empty areas can embed {@link #panel()} directly.
 *
 * <p>Each entry carries two facts only, both certain: a stable reference code
 * ({@code OT-<TOOL>-<FEATURE>}) and where the feature lives in the interface. We
 * deliberately do not describe what the feature does: the person who picks it up
 * knows the feature, and we would rather not put a wrong description in their
 * hands. The copy-to-clipboard text holds only what helps build it.
 */
public final class Unbuilt {

    /** The canonical project that contributions (pull requests) go to. */
    public static final String PROJECT_URL = "https://github.com/opentrap/opentrap";

    private final String code;      // OT-<TOOL>-<FEATURE>
    private final String location;  // e.g. "Intruder > Resource pool"

    public Unbuilt(String code, String location) {
        this.code = code;
        this.location = location;
    }

    // ---- Registry of unbuilt features (grows as placeholders are added) -----
    // Dashboard
    public static final Unbuilt DASHBOARD_LIVE_TASK  = new Unbuilt("OT-DASHBOARD-LIVE-TASK",  "Dashboard > New live task");
    public static final Unbuilt DASHBOARD_LIVE_CRAWL = new Unbuilt("OT-DASHBOARD-LIVE-CRAWL", "Dashboard > Live crawl view");
    // Intruder
    public static final Unbuilt INTRUDER_RESPOOL     = new Unbuilt("OT-INTRUDER-RESPOOL",     "Intruder > Resource pool");
    // Extensions
    public static final Unbuilt EXT_INSTALLED        = new Unbuilt("OT-EXT-INSTALLED",        "Extensions > Installed");
    public static final Unbuilt EXT_STORE            = new Unbuilt("OT-EXT-STORE",            "Extensions > Store");
    public static final Unbuilt EXT_API              = new Unbuilt("OT-EXT-API",              "Extensions > API");
    public static final Unbuilt EXT_CUSTOM_CHECKS    = new Unbuilt("OT-EXT-CUSTOM-CHECKS",    "Extensions > Custom checks");
    public static final Unbuilt EXT_FILTERS          = new Unbuilt("OT-EXT-FILTERS",          "Extensions > Filters");
    // Discover
    public static final Unbuilt DISCOVER             = new Unbuilt("OT-DISCOVER",             "Discover");

    public String code()     { return code; }
    public String location() { return location; }

    /** The clipboard payload: only lines that help an AI build the feature. */
    private String prompt() {
        return "OpenTRAP (Java/Swing security testing suite, OWASP ZAP engine).\n"
             + "Implement: " + location + "  [" + code + "].\n"
             + "Currently a UI placeholder, not wired to the engine.";
    }

    /** Wire a control so using it opens the dialog. Keeps the control enabled. */
    public void attach(AbstractButton b) {
        b.setToolTipText("not built yet - click to contribute");
        b.addActionListener(e -> showDialog(b));
    }

    /** Open the modal "not built yet" dialog near the given component. */
    public void showDialog(Component near) {
        Window owner = near == null ? null : SwingUtilities.getWindowAncestor(near);
        final JDialog d = new JDialog(owner, "Not built yet", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = content(null);
        content.setBorder(Ui.pad(16, 18, 14, 18));

        JButton close = Ui.button("Close");
        close.addActionListener(e -> d.dispose());
        JPanel bar = buttonBar(near, close);
        content.add(Box.createVerticalStrut(14));
        content.add(bar);

        d.setContentPane(content);
        d.pack();
        d.setMinimumSize(new Dimension(520, d.getPreferredSize().height));
        d.setLocationRelativeTo(near);
        d.setVisible(true);
    }

    /** An inline placeholder panel for a whole empty area (e.g. an unbuilt tab). */
    public JComponent panel() {
        JPanel body = content(null);
        body.add(Box.createVerticalStrut(14));
        body.add(buttonBar(body, null));
        // Centre the block so an empty tab does not look stretched.
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.setBorder(Ui.pad(24, 24, 24, 24));
        body.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.GRID), Ui.pad(18, 20, 18, 20)));
        body.setMaximumSize(new Dimension(620, Integer.MAX_VALUE));
        wrap.add(body, new GridBagConstraints());
        return wrap;
    }

    /** The shared message + reference + location + copy-text block. */
    private JPanel content(Void unused) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);

        JLabel h = Ui.heading(location + "  -  not built yet");
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(h);
        p.add(Box.createVerticalStrut(8));

        JComponent note = Ui.note(
            "This part of OpenTRAP is not built yet. OpenTRAP is a community work "
          + "in progress. If you know how this should work, build it and open a "
          + "pull request on the main project - we review and merge it with full "
          + "credit as an OpenTRAP contributor.");
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setMaximumSize(new Dimension(560, 80));
        p.add(note);
        p.add(Box.createVerticalStrut(12));

        JPanel refs = Ui.form(
            "Reference", monoValue(code),
            "Location",  Ui.label(location));
        refs.setAlignmentX(Component.LEFT_ALIGNMENT);
        refs.setMaximumSize(new Dimension(560, refs.getPreferredSize().height));
        p.add(refs);
        p.add(Box.createVerticalStrut(10));

        JLabel copyHint = Ui.label("Copy this to an AI to help build it:");
        copyHint.setForeground(Theme.MUTED);
        copyHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(copyHint);
        p.add(Box.createVerticalStrut(4));

        JTextArea prompt = new JTextArea(prompt());
        prompt.setFont(Ui.MONO);
        prompt.setEditable(false);
        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        JScrollPane ps = new JScrollPane(prompt);
        ps.setBorder(BorderFactory.createLineBorder(Ui.GRID));
        ps.setAlignmentX(Component.LEFT_ALIGNMENT);
        ps.setPreferredSize(new Dimension(520, 64));
        ps.setMaximumSize(new Dimension(620, 64));
        p.add(ps);
        return p;
    }

    /** Copy / GitHub (and optional Close) row. */
    private JPanel buttonBar(Component near, JButton trailing) {
        JButton copy = Ui.primaryButton("Copy for AI", "copy the build anchor to the clipboard");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(prompt()), null);
            String was = copy.getText();
            copy.setText("Copied");
            Timer t = new Timer(1200, ev -> copy.setText(was));
            t.setRepeats(false);
            t.start();
        });
        JButton github = Ui.button("Open project on GitHub", "open the OpenTRAP project page");
        github.addActionListener(e -> openProject(near));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.add(copy);
        bar.add(github);
        if (trailing != null) bar.add(trailing);
        bar.setMaximumSize(new Dimension(620, bar.getPreferredSize().height));
        return bar;
    }

    private static JLabel monoValue(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Ui.MONO);
        return l;
    }

    private void openProject(Component near) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(java.net.URI.create(PROJECT_URL));
                return;
            }
        } catch (Exception ignore) {
            // fall through to the clipboard fallback
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(PROJECT_URL), null);
        JOptionPane.showMessageDialog(near,
                "Could not open a browser here.\nProject URL copied to the clipboard:\n" + PROJECT_URL,
                "Open project", JOptionPane.INFORMATION_MESSAGE);
    }
}
