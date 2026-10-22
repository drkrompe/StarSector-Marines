package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * The ground residential bed re-drawn at real length, where a cell is about 0.75 m (a crewman's shoulders are 0.6
 * cells). A 2x1 bed was 1.5 m long and nobody fitted in it; this one is a 0.72 x 2.1 m single in a 3x1 frame, the
 * same length as the ship's bunk, head at the west (left) end. The other three facings are quarter turns of it
 * ({@code art-source/doodads/derive_residential_frames.py}), and the doodad atlas keeps the whole frame, so the bed
 * stays its true size inside its footprint.
 *
 * <p>Drawn large, 320 px per cell, on a canvas the size ImageGen returns, so the cut needs no rescaling.
 *
 * <pre>
 *   slot   frame (x,y,w,h px)    real size
 *   bed    288,352,960,320       0.72 x 2.1 m single bed, headboard and pillow at the left
 * </pre>
 */
public final class ResidentialBedTemplate {
    private ResidentialBedTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1536, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(288, 352);
        g.scale(C / 64.0, C / 64.0);

        // Frame 180 x 61 (2.1 x 0.72 m) with a headboard at the left end.
        rect(g, 6, 2, 180, 61, 0x7E8786);
        rect(g, 6, 2, 7, 61, 0x5E6766);
        // Mattress inside the frame, a pillow at the head, a blanket turned down over the rest.
        rect(g, 14, 5, 168, 55, 0xA6AA9F);
        rect(g, 18, 10, 26, 45, 0xB4B8AD);
        rect(g, 54, 5, 128, 55, 0x536C70);
        rect(g, 54, 5, 9, 55, 0x6B8387);
        g.dispose();
        return image;
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(render(), "png", path.toFile());
    }
}
