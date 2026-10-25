package opentrap.proxy;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gives the browser OpenTRAP opens its own taskbar identity, so it is never
 * mistaken for the user's everyday browser.
 *
 * <p>The browser is launched with a window class of its own; the desktop maps
 * that class, through a desktop entry installed under the user's home, to an
 * icon of OpenTRAP's own drawing: an orange globe, nothing of any browser
 * vendor's branding. The entry is hidden from application menus, since the
 * browser is only ever started from inside OpenTRAP.
 */
final class BrowserDesktopEntry {
    private BrowserDesktopEntry() {}

    /** The window class the browser is launched with, and the entry's name. */
    static final String WM_CLASS = "opentrap-browser";

    private static final int[] SIZES = {16, 22, 24, 32, 48, 64, 128, 256};
    private static final Color ORANGE = new Color(0xDB, 0x61, 0x2F);

    private static final String ENTRY = """
            [Desktop Entry]
            Type=Application
            Version=1.0
            Name=OpenTRAP browser
            Comment=The browser OpenTRAP opens, routed through its proxy
            Exec=true
            Icon=opentrap-browser
            Terminal=false
            NoDisplay=true
            StartupWMClass=opentrap-browser
            Categories=Network;Security;
            """;

    /**
     * Write the desktop entry and the icon set if they are missing or stale.
     * Quiet on any failure: the browser still opens, only under a generic icon.
     */
    static void install() {
        try {
            Path home = Path.of(System.getProperty("user.home"));
            Path apps = home.resolve(".local/share/applications");
            Path entry = apps.resolve(WM_CLASS + ".desktop");
            boolean changed = false;
            if (!Files.exists(entry) || !ENTRY.equals(Files.readString(entry, StandardCharsets.UTF_8))) {
                Files.createDirectories(apps);
                Files.writeString(entry, ENTRY, StandardCharsets.UTF_8);
                changed = true;
            }
            for (int s : SIZES) {
                Path dir = home.resolve(".local/share/icons/hicolor/" + s + "x" + s + "/apps");
                Path png = dir.resolve(WM_CLASS + ".png");
                if (Files.exists(png)) continue;
                Files.createDirectories(dir);
                javax.imageio.ImageIO.write(icon(s), "png", png.toFile());
                changed = true;
            }
            if (changed) refreshCaches(home);
        } catch (Exception ignore) { /* a cosmetic step; never block the launch */ }
    }

    /** Ask the desktop to pick the new entry and icons up, where the tools exist. */
    private static void refreshCaches(Path home) {
        run("update-desktop-database", home.resolve(".local/share/applications").toString());
        run("gtk-update-icon-cache", "-q", "-t", "-f", home.resolve(".local/share/icons/hicolor").toString());
        run("xdg-icon-resource", "forceupdate");
        // KDE reads desktop entries from its own cache, which only a rebuild
        // picks a new file up into; without it the entry is invisible to Plasma.
        run("kbuildsycoca6", "--noincremental");
        run("kbuildsycoca5", "--noincremental");
    }

    private static void run(String... cmd) {
        try {
            new ProcessBuilder(cmd)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception ignore) { /* tool not installed */ }
    }

    /**
     * The icon: an orange disc carrying a white globe, with the arrow of the
     * OpenTRAP mark across its equator, at any size.
     */
    static BufferedImage icon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float u = size / 32f;                 // design unit: the icon is laid out on a 32 grid
        g.setColor(ORANGE);
        g.fill(new Ellipse2D.Float(1 * u, 1 * u, 30 * u, 30 * u));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1f, 2.2f * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(7 * u, 7 * u, 18 * u, 18 * u));        // globe
        g.draw(new Ellipse2D.Float(12.5f * u, 7 * u, 7 * u, 18 * u));     // meridian
        g.drawLine(Math.round(7 * u), Math.round(16 * u), Math.round(25 * u), Math.round(16 * u));   // equator
        g.drawLine(Math.round(8.5f * u), Math.round(11.5f * u), Math.round(23.5f * u), Math.round(11.5f * u));
        g.drawLine(Math.round(8.5f * u), Math.round(20.5f * u), Math.round(23.5f * u), Math.round(20.5f * u));
        g.dispose();
        return img;
    }
}
