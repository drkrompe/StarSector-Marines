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
 * The loading-bay pieces re-drawn at real size, where a cell is about 0.75 m (a crewman's shoulders are 0.6 cells):
 * the manifest desk, two cells by one, and the pallet tug, one cell by three. The rest of the kit (conveyor, tote,
 * cartons, staging deck) was already true and keeps its original generation.
 *
 * <p>Drawn large, 320 px per cell: at 128 px per cell ImageGen draws boxy pieces far too deep. The worked face of
 * every piece is its bottom edge: a sprite's top edge lies at its footprint's highest row, and each piece is worked
 * from the row below it. The desk's operator side is its near (bottom) fascia; the tug's tiller is at the bottom
 * and its forks point up, away from the operator.
 *
 * <pre>
 *   slot           frame (x,y,w,h px)    real size
 *   manifest-desk  160, 80,640,320       1.45 x 0.66 m desk, screen at the back, operator fascia at the bottom
 *   pallet-tug     880, 80,320,960       0.68 x 1.9 m electric pallet jack, forks up, tiller at the bottom
 * </pre>
 */
public final class LoadingBayTemplate {
    private LoadingBayTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(4 * C, 4 * C, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Manifest desk 2x1: worktop with a recessed screen at the back, a clipboard, a keypad strip, and the
        // operator's fascia with two drawer pulls along the near (bottom) edge.
        at(g, C / 2, C / 4);
        rect(g, 2, 4, 124, 56, 0x40483E);
        rect(g, 4, 6, 120, 44, 0x9B9F90);
        rect(g, 12, 9, 36, 22, 0x465649);
        rect(g, 15, 11, 30, 17, 0x748879);
        rect(g, 80, 11, 30, 28, 0xB7B7A7);
        rect(g, 88, 13, 14, 3, 0x93998B);
        rect(g, 14, 38, 40, 8, 0x616D5E);
        rect(g, 2, 50, 124, 10, 0x353D35);
        rect(g, 20, 54, 22, 3, 0x92988B);
        rect(g, 86, 54, 22, 3, 0x92988B);

        // Pallet tug 1x3: two forks pointing up, a load backrest, the motor body and the tiller handle at the
        // bottom, where the operator walks it.
        at(g, 11 * C / 4, C / 4);
        rect(g, 8, 22, 14, 98, 0x8C9782);
        rect(g, 42, 22, 14, 98, 0x8C9782);
        rect(g, 11, 26, 8, 10, 0x29372C);
        rect(g, 45, 26, 8, 10, 0x29372C);
        rect(g, 5, 114, 54, 7, 0x5D6656);
        rect(g, 3, 120, 58, 36, 0x374437);
        rect(g, 7, 124, 50, 28, 0x7D8974);
        rect(g, 24, 130, 16, 14, 0x465640);
        rect(g, 29, 156, 6, 24, 0xADB09E);
        rect(g, 18, 178, 28, 8, 0xADB09E);

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
