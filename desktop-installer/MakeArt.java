import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the installers' artwork in Daydreamin's colours: the Windows setup wizard's side and corner
 * pictures and the Mac disk image's background, each at 1x and 2x. Run from android/:
 *   java desktop-installer/MakeArt.java
 * The pictures it writes are kept in the repository; run it again only to change them.
 */
public class MakeArt {
    static final Color VIOLET = new Color(0x6D5EE8), PINK = new Color(0xE87FC2), INK = new Color(0x07070B);
    static BufferedImage logo;

    public static void main(String[] args) throws Exception {
        logo = ImageIO.read(new File("app/src/commonMain/composeResources/drawable/splash_logo.png"));
        File out = new File("desktop-installer");
        for (int s = 1; s <= 2; s++) {
            String suffix = s == 1 ? "" : "@2x";
            ImageIO.write(rgb(wizardSide(s)), "bmp", new File(out, "wizard-large" + suffix + ".bmp"));
            ImageIO.write(rgb(wizardCorner(s)), "bmp", new File(out, "wizard-small" + suffix + ".bmp"));
            ImageIO.write(dmg(s), "png", new File(out, "dmg-background" + suffix + ".png"));
        }
    }

    static Graphics2D g(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    /** The app's dark room, lit violet from the top left and pink from the bottom right. */
    static void room(Graphics2D g, int w, int h) {
        g.setColor(INK);
        g.fillRect(0, 0, w, h);
        glow(g, 0, 0, Math.max(w, h) * 0.95f, VIOLET, 0.75f, w, h);
        glow(g, w, h, Math.max(w, h) * 0.8f, PINK, 0.45f, w, h);
    }

    static void glow(Graphics2D g, float x, float y, float r, Color c, float a, int w, int h) {
        Color from = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(a * 255));
        Color to = new Color(c.getRed(), c.getGreen(), c.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(x, y), r, new float[]{0f, 1f}, new Color[]{from, to}));
        g.fillRect(0, 0, w, h);
    }

    static void centered(Graphics2D g, String text, Font font, Color color, int cx, int y) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics m = g.getFontMetrics();
        g.drawString(text, cx - m.stringWidth(text) / 2, y);
    }

    static Font font(int style, float size) { return new Font("Helvetica Neue", style, 1).deriveFont(size); }

    /** The setup wizard's tall picture (its first and last pages): 164 x 314. */
    static BufferedImage wizardSide(int s) {
        int w = 164 * s, h = 314 * s;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(im);
        room(g, w, h);
        int size = 92 * s;
        g.drawImage(logo, (w - size) / 2, 74 * s, size, size, null);
        centered(g, "Daydreamin", font(Font.BOLD, 22f * s), Color.WHITE, w / 2, 200 * s);
        centered(g, "Music from everywhere,", font(Font.PLAIN, 11f * s), new Color(255, 255, 255, 190), w / 2, 222 * s);
        centered(g, "played beautifully.", font(Font.PLAIN, 11f * s), new Color(255, 255, 255, 190), w / 2, 237 * s);
        g.dispose();
        return im;
    }

    /** The small picture in the corner of the other pages: 55 x 55, on the wizard's white header. */
    static BufferedImage wizardCorner(int s) {
        int w = 55 * s;
        BufferedImage im = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(im);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, w);
        g.setClip(new RoundRectangle2D.Float(2 * s, 2 * s, w - 4 * s, w - 4 * s, 14 * s, 14 * s));
        room(g, w, w);
        int pad = 7 * s;
        g.drawImage(logo, pad, pad, w - 2 * pad, w - 2 * pad, null);
        g.dispose();
        return im;
    }

    /**
     * The disk image's window: 660 x 400. The app sits at (180, 220) and Applications at (480, 220)
     * on a mid-tone band, so Finder's labels read in light and dark mode alike.
     */
    static BufferedImage dmg(int s) {
        int w = 660 * s, h = 400 * s;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(im);
        room(g, w, h);
        int logoSize = 40 * s;
        g.drawImage(logo, w / 2 - logoSize / 2, 26 * s, logoSize, logoSize, null);
        centered(g, "Daydreamin", font(Font.BOLD, 26f * s), Color.WHITE, w / 2, 96 * s);
        centered(g, "Drag Daydreamin into Applications to install it", font(Font.PLAIN, 13f * s), new Color(255, 255, 255, 200), w / 2, 120 * s);
        // The band the two icons sit on.
        RoundRectangle2D band = new RoundRectangle2D.Float(60 * s, 146 * s, w - 120 * s, 200 * s, 28 * s, 28 * s);
        g.setPaint(new GradientPaint(0, 0, new Color(0x8A7EF2), w, 0, new Color(0xD98BC8)));
        g.fill(band);
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(1.5f * s));
        g.draw(band);
        // The arrow from the app to Applications.
        g.setColor(new Color(255, 255, 255, 235));
        g.setStroke(new BasicStroke(5f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int y = 214 * s;
        g.draw(new Line2D.Float(268 * s, y, 384 * s, y));
        Path2D head = new Path2D.Float();
        head.moveTo(372 * s, y - 13 * s);
        head.lineTo(390 * s, y);
        head.lineTo(372 * s, y + 13 * s);
        g.draw(head);
        centered(g, "No account · No ads · Yours to keep", font(Font.PLAIN, 11f * s), new Color(255, 255, 255, 150), w / 2, 378 * s);
        g.dispose();
        return im;
    }

    /** BMP has no transparency: flattened to plain RGB. */
    static BufferedImage rgb(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
