package opentrap.ui;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

/**
 * The about box: the logo, the build, what it runs on, and where the project
 * lives. Links open in the desktop browser.
 */
public final class AboutDialog {

    private AboutDialog() {}

    private static final String REPO = "https://github.com/opentrap/opentrap";
    private static final String SITE = "https://opentrap.github.io/opentrap/";

    public static void show(Component parent) {
        JPanel panel = new JPanel(new BorderLayout(18, 0));
        panel.setOpaque(false);
        panel.setBorder(Ui.pad(6, 6, 6, 10));

        JLabel logo = new JLabel(AppIcon.icon(96));
        logo.setVerticalAlignment(SwingConstants.TOP);
        panel.add(logo, BorderLayout.WEST);

        JEditorPane text = new JEditorPane("text/html", html());
        text.setEditable(false);
        text.setOpaque(false);
        text.setFont(Ui.PLAIN);
        text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        text.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) open(e.getURL().toString());
        });
        panel.add(text, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(parent, panel, "about OpenTRAP", JOptionPane.PLAIN_MESSAGE);
    }

    private static String html() {
        String accent = String.format("#%06x", Theme.ACCENT.getRGB() & 0xFFFFFF);
        String muted = String.format("#%06x", Theme.MUTED.getRGB() & 0xFFFFFF);
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Dialog;font-size:12pt;width:380px'>");
        sb.append("<div style='font-size:16pt;font-weight:bold'>OpenTRAP</div>");
        sb.append("<div style='color:").append(muted).append("'>Open Traffic Replay and Analysis Proxy</div>");
        sb.append("<p>An open-source web security testing suite built around an intercepting proxy.</p>");
        sb.append("<table cellpadding='1' cellspacing='0'>");
        row(sb, "Build", Main.VERSION);
        row(sb, "Scan engine", engineVersion());
        row(sb, "Java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
        row(sb, "System", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        row(sb, "Licence", "GNU AGPL v3.0");
        sb.append("</table>");
        sb.append("<p>");
        link(sb, accent, REPO, "Source and issues on GitHub");
        link(sb, accent, REPO + "/releases/latest", "Latest release");
        link(sb, accent, SITE, "Project site");
        link(sb, accent, REPO + "/blob/main/CONTRIBUTING.md", "How to contribute");
        sb.append("</p>");
        sb.append("<div style='color:").append(muted).append("'>Built by its community. Every contribution is credited.</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String k, String v) {
        sb.append("<tr><td style='padding-right:14px'><b>").append(k).append("</b></td><td>")
          .append(v == null ? "-" : v).append("</td></tr>");
    }

    private static void link(StringBuilder sb, String colour, String url, String label) {
        sb.append("<a href='").append(url).append("' style='color:").append(colour)
          .append("'>").append(label).append("</a><br>");
    }

    private static String engineVersion() {
        try { return opentrap.engine.ZapEngine.describe(); }
        catch (Exception e) { return "OWASP ZAP"; }
    }

    private static void open(String url) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ignored) {}
    }
}
