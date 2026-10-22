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
 * The ward bed re-drawn at real length, where a cell is about 0.75 m (a crewman's shoulders are 0.6 cells): the bed
 * frame and its two opposing linen layers, each one cell by three. A 1x2 bed was 1.5 m long and no patient fitted.
 * The rest of the kit (monitor, nurse station, cabinet, sink, trolley, examination couch, privacy screen) was already
 * true and keeps its original generation.
 *
 * <p>Drawn large, 320 px per cell, one 1x3 frame per column: ImageGen holds slim pieces at this size. The frame is
 * the same at both ends; which end is the head is the linen's choice. A sprite's top edge lies at its footprint's
 * highest row, so {@code linen-high} (pillow at the image top) heads a bed at its high row and {@code linen-low} at
 * its low row, beside the monitor.
 *
 * <pre>
 *   slot        frame (x,y,w,h px)     real size
 *   bed         128,160,320,960        0.66 x 2.20 m frame: open tray, head and foot boards, side rails
 *   linen-high  576,160,320,960        0.52 x 2.0 m mattress, pillow at the image top
 *   linen-low   1024,160,320,960       the same, pillow at the image bottom
 * </pre>
 */
public final class SickbayClinicTemplate {
    private SickbayClinicTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;
    /** Gutter between frames and round the canvas edge. */
    static final int GUTTER = 128;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(3 * C + 4 * GUTTER, 3 * C + 2 * (C / 2), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Ward bed frame: open tray to receive the linen, matching head and foot boards, side rails at mid-length.
        at(g, 0);
        rect(g, 4, 2, 56, 188, 0x525D56);
        rect(g, 9, 8, 46, 176, 0x8F988C);
        rect(g, 1, 44, 6, 104, 0xC0C4B8);
        rect(g, 57, 44, 6, 104, 0xC0C4B8);
        rect(g, 5, 2, 54, 8, 0xB9BDB0);
        rect(g, 5, 182, 54, 8, 0xB9BDB0);

        linen(g, 1, true);
        linen(g, 2, false);

        g.dispose();
        return image;
    }

    /** A 0.52 x 2.0 m mattress inside the frame, a pillow at one end and a blanket turned down over the rest. */
    private static void linen(Graphics2D g, int slot, boolean headTop) {
        at(g, slot);
        rect(g, 10, 11, 44, 170, 0xCBCBBF);
        rect(g, 13, headTop ? 15 : 155, 38, 22, 0xE0DED0);
        rect(g, 10, headTop ? 46 : 11, 44, 135, 0xA6B2A0);
        rect(g, 10, headTop ? 46 : 138, 44, 8, 0xC3CBBB);
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
