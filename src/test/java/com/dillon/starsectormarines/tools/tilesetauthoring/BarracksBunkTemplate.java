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
 * The bunk re-drawn at real length, where a cell is about 0.75 m (a crewman's shoulders are 0.6 cells): the bunk
 * base, its two opposing bedding layers and the open retaining frame, each one cell by three. A 1x2 bunk was 1.5 m
 * long and nobody fitted in it. The locker banks were already true and keep their original generation.
 *
 * <p>Drawn large, 320 px per cell, one 1x3 frame per column: ImageGen holds slim pieces at this size. The base is
 * symmetric end to end; which end is the head is the bedding's choice. A sprite's top edge lies at its footprint's
 * highest row, so {@code bedding-high} (pillow at the image top) heads a bunk at its high row and
 * {@code bedding-low} at its low row. The sleeper lies along the bunk with the head on the pillow.
 *
 * <pre>
 *   slot          frame (x,y,w,h px)     real size
 *   bed           128,160,320,960        0.70 x 2.20 m base, recessed spring deck, no bedding
 *   bedding-high  576,160,320,960        0.59 x 2.0 m mattress, pillow at the image top
 *   bedding-low   1024,160,320,960       the same, pillow at the image bottom
 *   frame         1472,160,320,960       open retaining rails and corner posts over the base outline
 * </pre>
 */
public final class BarracksBunkTemplate {
    private BarracksBunkTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;
    /** Gutter between frames and round the canvas edge. */
    static final int GUTTER = 128;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(4 * C + 5 * GUTTER, 3 * C + 2 * (C / 2), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Bunk base: steel pan 0.70 x 2.20 m with a recessed spring deck, the same at both ends.
        at(g, 0);
        rect(g, 2, 2, 60, 188, 0x3D4541);
        rect(g, 6, 8, 52, 176, 0x5B635B);
        for (int y : new int[]{40, 96, 152}) rect(g, 6, y, 52, 2, 0x4C544D);

        // Bedding, pillow at the image top, then at the image bottom.
        bedding(g, 1, true);
        bedding(g, 2, false);

        // Retaining frame: side rails and end bars on the base outline, corner posts, an open centre.
        at(g, 3);
        rect(g, 2, 2, 4, 188, 0x69746A);
        rect(g, 58, 2, 4, 188, 0x69746A);
        rect(g, 2, 2, 60, 5, 0x444E46);
        rect(g, 2, 185, 60, 5, 0x444E46);
        for (int y : new int[]{1, 183}) {
            rect(g, 1, y, 8, 8, 0x8B9589);
            rect(g, 55, y, 8, 8, 0x8B9589);
        }

        g.dispose();
        return image;
    }

    /** A 0.59 x 2.0 m mattress inside the rails, a pillow at one end and a blanket turned down over the rest. */
    private static void bedding(Graphics2D g, int slot, boolean headTop) {
        at(g, slot);
        rect(g, 7, 10, 50, 172, 0xA0A393);
        rect(g, 11, headTop ? 14 : 152, 42, 26, 0xC9CABB);
        rect(g, 7, headTop ? 50 : 10, 50, 132, 0x737E6D);
        rect(g, 7, headTop ? 50 : 134, 50, 8, 0x8B9582);
    }

    /** Slot {@code slot}'s 1x3 frame: one column per piece, half a cell of gutter above. */
    private static void at(Graphics2D g, int slot) {
        g.setTransform(new AffineTransform());
        g.translate(GUTTER + slot * (C + GUTTER), C / 2);
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
