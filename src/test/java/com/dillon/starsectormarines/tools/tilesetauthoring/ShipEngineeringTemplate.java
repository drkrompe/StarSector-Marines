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
 * The engineering pieces re-drawn at real size, where a cell is about 0.75 m (a crewman's shoulders are 0.6 cells):
 * the switchgear bank, the spares rack and the service bench, each one cell deep. The machinery skids and the control
 * board were already true and keep their original generation (the left half of the raw).
 *
 * <p>Drawn large, 320 px per cell, one frame per row: at 128 px per cell ImageGen drew boxy pieces half again too
 * wide and two to four times too deep, whatever the brief said, while at this size it holds them. The worked face of
 * every piece is its bottom edge: a sprite's top edge lies at its footprint's highest row, and each piece is worked
 * from the aisle below it.
 *
 * <pre>
 *   slot         frame (x,y,w,h px)    real size
 *   switchgear   160, 160, 960,320     2.1 x 0.6 m three-cabinet bank, breaker doors on the bottom face
 *   spares       160, 640, 960,320     2.1 x 0.6 m parts rack, drawer pulls on the bottom face
 *   bench        160,1120,1280,320     2.9 x 0.7 m service bench, drawers and vise on the near (bottom) edge
 * </pre>
 */
public final class ShipEngineeringTemplate {
    private ShipEngineeringTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(5 * C, 5 * C, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Switchgear: three cabinets 0.6 m deep against the back of the cell; breaker doors, handles and indicator
        // windows on the near face.
        at(g, 0);
        rect(g, 4, 12, 184, 50, 0x313D36);
        for (int x : new int[]{8, 69, 130}) {
            rect(g, x, 15, 54, 34, 0x77816E);
            rect(g, x + 8, 21, 38, 6, 0x56604F);
            rect(g, x + 8, 31, 38, 6, 0x56604F);
            rect(g, x + 2, 50, 50, 10, 0x5D6656);
            rect(g, x + 6, 52, 12, 5, 0x3B4E42);
            rect(g, x + 40, 53, 8, 4, 0xBBB9A1);
        }

        // Spares rack: 0.6 m deep; three bays of parts bins on the top shelf, six drawer pulls along the near face.
        at(g, 1);
        rect(g, 4, 12, 184, 50, 0x313D36);
        for (int x : new int[]{8, 69, 130}) {
            rect(g, x, 15, 54, 34, 0x48544A);
            for (int b = 0; b < 3; b++) rect(g, x + 3 + b * 17, 18, 14, 13, 0x7B8674);
            rect(g, x + 4, 35, 22, 11, 0x6A7563);
            rect(g, x + 29, 35, 21, 11, 0x6A7563);
            rect(g, x + 2, 50, 50, 10, 0x5D6656);
            rect(g, x + 8, 53, 10, 4, 0xB2B5A0);
            rect(g, x + 36, 53, 10, 4, 0xB2B5A0);
        }

        // Service bench: 2.9 x 0.7 m; a grey-green work pad, a steel vise on the near edge at the right end, four
        // drawer pulls along the near fascia.
        at(g, 2);
        rect(g, 4, 2, 248, 60, 0x313D36);
        rect(g, 6, 4, 244, 48, 0x9D9F8C);
        rect(g, 18, 10, 150, 34, 0x566654);
        rect(g, 196, 30, 30, 20, 0x6F7A6B);
        rect(g, 190, 38, 42, 7, 0x414E42);
        for (int x : new int[]{16, 74, 132, 190}) rect(g, x, 55, 44, 5, 0xACB19B);

        g.dispose();
        return image;
    }

    /** Slot {@code row}'s frame: half a cell in from the left, half a cell of gutter above each row. */
    private static void at(Graphics2D g, int row) {
        g.setTransform(new AffineTransform());
        g.translate(C / 2, C / 2 + row * (C + C / 2));
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
