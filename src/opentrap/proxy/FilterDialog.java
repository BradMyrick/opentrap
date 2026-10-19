package opentrap.proxy;

import opentrap.ui.Ui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * The filter popup for a history or site-map table.
 *
 * <p>This is the thorough half of the search. It gathers every structural filter
 * and the full-text search term into one dialog, the way the commercial tool's
 * filter settings do, so the bar beside the table stays a one-line summary and
 * the quick box beside it stays a fast URL search.
 *
 * <p>The dialog edits the live {@link HistoryFilter} and calls back on apply, so
 * the table re-filters immediately.
 */
public final class FilterDialog extends JDialog {

    private final HistoryFilter f;
    private final Runnable onApply;

    private final JCheckBox inScope, hasResp, params;
    private final JCheckBox html, script, xml, json, css, images, otherText, otherBinary;
    private final JCheckBox s2, s3, s4, s5;
    private final JTextField extOnly, extHide;
    private final JTextField term;
    private final JCheckBox regex, caseSensitive, negative;

    public FilterDialog(Window owner, String title, HistoryFilter filter, Runnable onApply) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.f = filter;
        this.onApply = onApply;

        inScope = Ui.check("show only in-scope items", f.inScopeOnly);
        hasResp = Ui.check("show only items with responses", f.hideWithoutResponses);
        params  = Ui.check("show only parameterised requests", f.parameterisedOnly);

        html = Ui.check("HTML", f.showHtml);
        script = Ui.check("script", f.showScript);
        xml = Ui.check("XML", f.showXml);
        json = Ui.check("JSON", f.showJson);
        css = Ui.check("CSS", f.showCss);
        images = Ui.check("images", f.showImages);
        otherText = Ui.check("other text", f.showOtherText);
        otherBinary = Ui.check("other binary", f.showOtherBinary);

        s2 = Ui.check("2xx  success", f.show2xx);
        s3 = Ui.check("3xx  redirection", f.show3xx);
        s4 = Ui.check("4xx  request error", f.show4xx);
        s5 = Ui.check("5xx  server error", f.show5xx);

        extOnly = Ui.field(f.extShowOnly, 14);
        extHide = Ui.field(f.extHide, 14);

        term = Ui.field(f.searchTerm, 22);
        regex = Ui.check("regex", f.searchRegex);
        caseSensitive = Ui.check("case sensitive", f.searchCaseSensitive);
        negative = Ui.check("negative search", f.searchNegative);

        setContentPane(build());
        pack();
        setLocationRelativeTo(owner);
    }

    private JComponent build() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBorder(Ui.pad(12, 12, 6, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 14);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.BOTH;
        c.gridy = 0;

        c.gridx = 0; grid.add(section("request type", stack(inScope, hasResp, params)), c);
        c.gridx = 1; grid.add(section("content type",
                two(html, otherText, script, images, xml, css, json, otherBinary)), c);
        c.gridx = 2; grid.add(section("status code", stack(s2, s3, s4, s5)), c);

        c.gridy = 1; c.insets = new Insets(10, 0, 0, 14);
        c.gridx = 0; grid.add(section("file extension",
                Ui.form("show only:", extOnly, "hide:", extHide)), c);
        c.gridwidth = 2; c.gridx = 1;
        grid.add(section("search term (requests and responses)",
                stack(term, row(regex, caseSensitive, negative))), c);
        c.gridwidth = 1;

        JButton showAll = Ui.button("show all");
        JButton hideAll = Ui.button("hide all");
        JButton apply = Ui.button("apply");
        JButton applyClose = Ui.button("apply and close");
        JButton cancel = Ui.button("cancel");
        showAll.addActionListener(e -> setAllMime(true));
        hideAll.addActionListener(e -> setAllMime(false));
        apply.addActionListener(e -> commit());
        applyClose.addActionListener(e -> { commit(); dispose(); });
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        buttons.add(showAll); buttons.add(hideAll);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(cancel); buttons.add(apply); buttons.add(applyClose);

        JPanel root = new JPanel(new BorderLayout());
        root.add(grid, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void setAllMime(boolean on) {
        for (JCheckBox b : new JCheckBox[]{html, script, xml, json, css, images, otherText, otherBinary})
            b.setSelected(on);
    }

    private void commit() {
        f.inScopeOnly = inScope.isSelected();
        f.hideWithoutResponses = hasResp.isSelected();
        f.parameterisedOnly = params.isSelected();
        f.showHtml = html.isSelected(); f.showScript = script.isSelected();
        f.showXml = xml.isSelected(); f.showJson = json.isSelected();
        f.showCss = css.isSelected(); f.showImages = images.isSelected();
        f.showOtherText = otherText.isSelected(); f.showOtherBinary = otherBinary.isSelected();
        f.show2xx = s2.isSelected(); f.show3xx = s3.isSelected();
        f.show4xx = s4.isSelected(); f.show5xx = s5.isSelected();
        f.extShowOnly = extOnly.getText().trim();
        f.extHide = extHide.getText().trim();
        f.searchTerm = term.getText();
        f.searchRegex = regex.isSelected();
        f.searchCaseSensitive = caseSensitive.isSelected();
        f.searchNegative = negative.isSelected();
        if (onApply != null) onApply.run();
    }

    // ---- small layout helpers ----------------------------------------------

    private static JComponent section(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new TitledBorder(title));
        body.setBorder(Ui.pad(4, 6, 6, 6));
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private static JComponent stack(JComponent... parts) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        for (JComponent c : parts) { c.setAlignmentX(Component.LEFT_ALIGNMENT); p.add(c); }
        return p;
    }

    private static JComponent two(JComponent... parts) {
        JPanel p = new JPanel(new GridLayout(0, 2, 10, 0));
        for (JComponent c : parts) p.add(c);
        return p;
    }

    private static JComponent row(JComponent... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (JComponent c : parts) p.add(c);
        return p;
    }
}
