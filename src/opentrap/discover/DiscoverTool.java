package opentrap.discover;

import opentrap.core.ToolModule;
import opentrap.ui.Ui;
import opentrap.ui.Unbuilt;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * The Discover tool: a getting-started landing and the project roadmap.
 *
 * <p>Getting started is a short guide to the testing workflow. The roadmap is
 * generated from {@link Unbuilt#all()} - every planned-but-unbuilt feature with
 * its location and reference code - so the list stays in step with the actual
 * placeholders in the interface, and invites users to contribute or to open an
 * issue to discuss and prioritise.
 */
public class DiscoverTool implements ToolModule {

    @Override public String name() { return "discover"; }

    @Override public boolean alwaysTabbed() { return true; }

    @Override public Tab[] tabs() {
        return new Tab[]{
                new Tab("Getting started", gettingStarted()),
                new Tab("Roadmap", roadmap()),
        };
    }

    private JComponent gettingStarted() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(Ui.pad(16, 16, 16, 16));
        root.setOpaque(false);

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        JLabel h = Ui.heading("Getting started");
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent intro = Ui.note("OpenTRAP is a free, open-source web security "
                + "testing suite. The usual workflow runs through these four steps.");
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        intro.setMaximumSize(new Dimension(900, intro.getPreferredSize().height));
        head.add(h);
        head.add(Box.createVerticalStrut(4));
        head.add(intro);
        root.add(head, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(2, 2, 12, 12));
        cards.setOpaque(false);
        cards.add(card("1. Set the target scope",
                "Choose which hosts and URLs are in scope on the Target > Scope tab, "
              + "so the tools only touch what you intend to test."));
        cards.add(card("2. Map the website",
                "Browse the target through the proxy, or run a crawl, to build the "
              + "site map on the Target tab and discover content and features."));
        cards.add(card("3. Analyse the attack surface",
                "Review the site map and proxy history to understand parameters, "
              + "endpoints and technologies worth testing."));
        cards.add(card("4. Test for vulnerabilities",
                "Run a scan from the Dashboard, and use Intruder, Repeater and the "
              + "other tools to probe findings by hand."));
        JPanel cardsWrap = new JPanel(new BorderLayout());
        cardsWrap.setOpaque(false);
        cardsWrap.add(cards, BorderLayout.NORTH);
        root.add(cardsWrap, BorderLayout.CENTER);
        return root;
    }

    private JComponent card(String title, String desc) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.GRID), Ui.pad(12, 14, 12, 14)));
        // A wrapping text area reports a one-line preferred height, so give the
        // card a fixed height tall enough for the wrapped description.
        p.setPreferredSize(new Dimension(420, 104));
        JLabel t = Ui.bold(title);
        JComponent d = Ui.note(desc);
        p.add(t, BorderLayout.NORTH);
        p.add(d, BorderLayout.CENTER);
        return p;
    }

    private JComponent roadmap() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(Ui.pad(16, 16, 16, 16));
        root.setOpaque(false);

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        JLabel h = Ui.heading("Roadmap");
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent intro = Ui.note("OpenTRAP is built in the open. The features below "
                + "are planned but not yet implemented, each with a reference code. "
                + "Pick one and open a pull request on the main project to build it - "
                + "accepted work is merged with full credit. To discuss a feature or "
                + "ask for it to be prioritised, open an issue on the project.");
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        intro.setMaximumSize(new Dimension(900, intro.getPreferredSize().height));
        JButton gh = Ui.primaryButton("Open project on GitHub", "open the OpenTRAP project page");
        gh.addActionListener(e -> Unbuilt.openProject(root));
        JPanel ghRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ghRow.setOpaque(false);
        ghRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        ghRow.add(gh);
        head.add(h);
        head.add(Box.createVerticalStrut(4));
        head.add(intro);
        head.add(Box.createVerticalStrut(8));
        head.add(ghRow);
        root.add(head, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Area", "Feature", "Reference"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Unbuilt u : Unbuilt.all()) {
            String area = u.location().contains(" > ") ? u.group() : "Suite";
            model.addRow(new Object[]{area, u.feature(), u.code()});
        }
        JTable table = new JTable(model);
        Ui.styleTable(table);
        Ui.distributeWidths(table, 140, 300, 220);
        root.add(Ui.scroll(table), BorderLayout.CENTER);
        return root;
    }
}
