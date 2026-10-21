package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * The washroom basin re-drawn at real size, where a cell is about 0.75 m (a crewman's shoulders are 0.6 cells). The
 * original counter filled its cell at 0.73 m; a wall basin is about 0.5 m wide and 0.45 m deep, so it keeps its 1x1
 * footprint and is drawn at true size inside it. The toilet and shower cubicles and the deck sample were already true
 * and keep their original generation.
 *
 * <p>Drawn large, 320 px per cell, on a two-cell canvas: small boxy pieces drawn small come back far too big. The
 * basin's wall, with its faucet and two controls, is its bottom edge, flush with the frame: the ship washroom lines
 * its basins along the bottom bulkhead and washes at them from the aisle above, so the basin's open front is its top.
 *
 * <pre>
 *   slot  frame (x,y,w,h px)   real size
 *   sink  160,160,320,320      0.5 x 0.45 m basin counter, faucet and controls on the bottom (wall) edge
 * </pre>
 */
public final class ShipWashroomTemplate {
    private ShipWashroomTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(2 * C, 2 * C, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Basin counter: 0.5 m wide, 0.45 m deep, its back against the bottom of the frame.
        g.setTransform(new AffineTransform());
        g.translate(C / 2, C / 2);
        g.scale(C / 64.0, C / 64.0);
        rect(g, 10, 24, 44, 38, 0x878980);
        rect(g, 12, 26, 40, 31, 0xB4B4A8);
        oval(g, 16, 28, 32, 24, 0x5E6865);
        oval(g, 19, 30, 26, 19, 0xA8AFA9);
        oval(g, 30, 38, 4, 4, 0x48524F);
        rect(g, 10, 57, 44, 5, 0x454D47);
        rect(g, 29, 47, 6, 11, 0xA4ABA5);
        oval(g, 18, 51, 5, 5, 0x737E77);
        oval(g, 41, 51, 5, 5, 0x737E77);

        g.dispose();
        return image;
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }
    private static void oval(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillOval(x, y, w, h);
    }
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(render(), "png", path.toFile());
    }
}
