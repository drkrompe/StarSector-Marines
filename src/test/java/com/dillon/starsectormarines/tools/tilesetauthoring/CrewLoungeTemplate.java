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
 * The crew-lounge pieces re-drawn at real size, where a cell is about 0.75 m (a crewman's shoulders are 0.6 cells):
 * the card table and its game overlay, the round stool and the bookshelf. The sofas, coffee table, pool table, media
 * console, refreshment counter and planter were already true and keep their original generation.
 *
 * <p>Drawn large, 320 px per cell: small boxy pieces drawn small come back far too big. Each piece keeps its
 * footprint and is drawn at true size inside it. The card table stays 2x2 because it seats two a side; the table is
 * 1.05 m square, centred, so the stools in the cells beside it sit at its edge. The bookshelf is a tall 1x3 piece
 * whose wall is its right edge (a wall-backed tall piece takes one more quarter turn than a wide one), so the shallow
 * case stands against the right of the frame with its open front to the left.
 *
 * <pre>
 *   slot        frame (x,y,w,h px)    real size
 *   card-table   80,  80,640,640      1.05 m square table, sage felt inset
 *   game         80, 880,640,640      overlay inside that felt: small board, two cards, chips
 *   books      1040,  80,320,960      2.1 x 0.35 m case against the right edge, open front left
 *   stool      1040,1200,320,320      0.4 m round padded seat
 * </pre>
 */
public final class CrewLoungeTemplate {
    private CrewLoungeTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(5 * C, 5 * C, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Card table: 1.05 m square in its 1.5 m frame, steel rim around a sage felt inset.
        at(g, 0.25, 0.25);
        rect(g, 19, 19, 90, 90, 0x444F49);
        rect(g, 22, 22, 84, 84, 0xA09F8E);
        rect(g, 28, 28, 72, 72, 0x60796A);

        // Game overlay: everything inside the felt, transparent between pieces.
        at(g, 0.25, 2.75);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++)
            rect(g, 46 + x * 9, 46 + y * 9, 9, 9, (x + y) % 2 == 0 ? 0xAEB2A0 : 0x505E52);
        rect(g, 33, 33, 10, 14, 0xD1CBB6);
        rect(g, 85, 81, 10, 14, 0xD1CBB6);
        oval(g, 86, 35, 7, 0x3F4A42);
        oval(g, 35, 86, 7, 0x3F4A42);

        // Bookshelf: 2.1 m long, 0.35 m deep against the right edge; open front (the books' spines) on the left.
        at(g, 3.25, 0.25);
        rect(g, 32, 6, 30, 180, 0x444F49);
        rect(g, 34, 10, 24, 172, 0x2F3833);
        for (int y : new int[]{51, 96, 141}) rect(g, 32, y, 30, 4, 0x7A8070);
        int[] colours = {0x738172, 0xA39C85, 0x5E5A4E, 0x8A8D7A};
        for (int bay = 0; bay < 4; bay++) {
            int top = 12 + bay * 45;
            for (int k = 0; k < 4; k++) rect(g, 35, top + k * 9, 20, 8, colours[(bay + k) % colours.length]);
        }

        // Stool: 0.4 m round padded seat, centred.
        at(g, 3.25, 3.75);
        oval(g, 15, 15, 34, 0x444F47);
        oval(g, 19, 19, 26, 0x879383);

        g.dispose();
        return image;
    }

    /** A frame whose top-left corner is {@code col, row} cells into the canvas. */
    private static void at(Graphics2D g, double col, double row) {
        g.setTransform(new AffineTransform());
        g.translate(col * C, row * C);
        g.scale(C / 64.0, C / 64.0);
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }

    private static void oval(Graphics2D g, int x, int y, int d, int rgb) {
        g.setColor(new Color(rgb)); g.fillOval(x, y, d, d);
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent()); ImageIO.write(render(), "png", path.toFile());
    }
}
