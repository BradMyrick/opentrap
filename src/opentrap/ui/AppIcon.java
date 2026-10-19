package opentrap.ui;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The application window icon.
 *
 * <p>The icon is the bidirectional traffic arrows from {@code icon/opentrap.svg},
 * rasterised to PNGs under {@code opentrap/ui/icons/} and loaded here, so the
 * window, the taskbar and the installed launcher all show the same thing and it
 * stays the same on any desktop theme.
 *
 * <p>If the raster resources are somehow absent, a plain drawn fallback is used
 * so the window always has an icon.
 */
public final class AppIcon {

    private AppIcon() {}

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    /** Icons at the sizes a window manager typically wants. */
    public static List<BufferedImage> images() {
        List<BufferedImage> out = new ArrayList<>();
        for (int size : SIZES) {
            BufferedImage img = load(size);
            if (img != null) out.add(img);
        }
        if (out.isEmpty()) out.add(fallback(64));   // never leave the window iconless
        return out;
    }

    private static BufferedImage load(int size) {
        String path = "/opentrap/ui/icons/opentrap_" + size + ".png";
        try (InputStream in = AppIcon.class.getResourceAsStream(path)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** A minimal drawn icon, only used if the bundled rasters are missing. */
    private static BufferedImage fallback(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.ACCENT);
        double s = size, t = s * 0.14, cy1 = s * 0.38, cy2 = s * 0.62;
        g.setStroke(new java.awt.BasicStroke((float) t, java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
        g.drawLine((int) (s * 0.2), (int) cy1, (int) (s * 0.8), (int) cy1);
        g.drawLine((int) (s * 0.8), (int) cy2, (int) (s * 0.2), (int) cy2);
        g.dispose();
        return img;
    }

    /**
     * Export the window icon as PNGs into a directory, named
     * {@code opentrap_<size>.png}, for the desktop installer. Run with
     * {@code java -cp dist/opentrap.jar opentrap.ui.AppIcon <dir>}.
     */
    public static void main(String[] args) throws Exception {
        java.io.File dir = new java.io.File(args.length > 0 ? args[0] : ".");
        dir.mkdirs();
        for (int size : SIZES) {
            BufferedImage img = load(size);
            if (img == null) img = fallback(size);
            ImageIO.write(img, "png", new java.io.File(dir, "opentrap_" + size + ".png"));
        }
        System.out.println("wrote icons to " + dir.getAbsolutePath());
    }
}
