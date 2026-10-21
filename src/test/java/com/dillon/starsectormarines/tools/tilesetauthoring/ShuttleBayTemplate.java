package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * The shuttle-bay pieces re-drawn at real size, where a cell is about 0.75 m (a crewman's shoulders are 0.6 cells):
 * the flight-operations desk, two cells by one, the servicing cart, one cell by two, and the flush tie-down, a
 * quarter-metre deck plate with a lashing ring in the middle of its one cell. The rest of the kit (ground-power
 * unit, fluid skid, wheel cradles, landing deck) was already true and keeps its original generation.
 *
 * <p>The old tie-down, a ring with a bar across its centre, read to ImageGen's safety filter as a gun sight: every
 * request carrying it, or the old sheet as reference, was refused as violence. The plate is square for that reason.
 *
 * <p>Drawn large, 320 px per cell: at 128 px per cell ImageGen draws boxy pieces far too deep. The worked face of
 * every piece is its bottom edge: a sprite's top edge lies at its footprint's highest row, and each piece is worked
 * from the row below it. The desk's operator side is its near (bottom) fascia; the cart's push handle is at the
 * bottom.
 *
 * <pre>
 *   slot          frame (x,y,w,h px)    real size
 *   flight-desk   160, 80,640,320       1.45 x 0.68 m desk, display at the back, operator fascia at the bottom
 *   service-cart  880, 80,320,640       0.66 x 1.4 m cart, coiled lead on top, push handle at the bottom
 *   tie-down      160,560,320,320       0.26 m flush deck plate with a lashing ring, centred, transparent around it
 * </pre>
 */
public final class ShuttleBayTemplate {
    private ShuttleBayTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(4 * C, 4 * C, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Flight-operations desk 2x1: a dim display and a status panel at the back, a log board, a control strip,
        // and the operator's fascia with two drawer pulls along the near (bottom) edge.
        at(g, C / 2, C / 4);
        rect(g, 2, 4, 124, 56, 0x434B46);
        rect(g, 4, 6, 120, 44, 0x92968B);
        rect(g, 10, 9, 40, 24, 0x384940);
        rect(g, 13, 11, 34, 19, 0x647C6C);
        rect(g, 56, 9, 18, 24, 0xB0B2A3);
        rect(g, 82, 12, 34, 30, 0xB4B5A8);
        rect(g, 12, 38, 56, 7, 0x5D665C);
        rect(g, 2, 50, 124, 10, 0x353D38);
        rect(g, 20, 54, 22, 3, 0x92988B);
        rect(g, 86, 54, 22, 3, 0x92988B);

        // Servicing cart 1x2: casters at the corners, a drawer at the far end, a coiled lead on the deck of the
        // cart, and the push handle across the near (bottom) end.
        at(g, 11 * C / 4, C / 4);
        rect(g, 7, 10, 10, 8, 0x29332D);
        rect(g, 47, 10, 10, 8, 0x29332D);
        rect(g, 7, 104, 10, 8, 0x29332D);
        rect(g, 47, 104, 10, 8, 0x29332D);
        rect(g, 4, 14, 56, 94, 0x343F38);
        rect(g, 9, 18, 46, 86, 0x818C7C);
        rect(g, 13, 22, 38, 22, 0xA2A899);
        rect(g, 22, 30, 20, 4, 0x455349);
        g.setColor(new Color(0x34483B));
        g.setStroke(new BasicStroke(5));
        g.draw(new Ellipse2D.Double(14, 54, 36, 36));
        g.draw(new Ellipse2D.Double(22, 62, 20, 20));
        rect(g, 10, 112, 4, 10, 0xADB09E);
        rect(g, 50, 112, 4, 10, 0xADB09E);
        rect(g, 10, 120, 44, 5, 0xADB09E);

        // Flush tie-down 1x1: a small square deck plate painted off-white round its edge, recessed in the middle,
        // with a steel lashing ring lying flat in the recess.
        at(g, C / 2, 7 * C / 4);
        rect(g, 21, 21, 22, 22, 0xA6AB99);
        rect(g, 24, 24, 16, 16, 0x6B7468);
        rect(g, 26, 26, 12, 12, 0x3B463D);
        g.setColor(new Color(0xA6AB99));
        g.setStroke(new BasicStroke(2));
        g.draw(new Ellipse2D.Double(28, 30, 9, 6));

        g.dispose();
        return image;
    }

    /** Places a frame's top-left corner at canvas pixel ({@code x}, {@code y}), scaled to 64ths of a cell. */
    private static void at(Graphics2D g, int x, int y) {
        g.setTransform(new AffineTransform());
        g.translate(x, y);
        g.scale(C / 64.0, C / 64.0);
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(render(), "png", path.toFile());
    }
}
