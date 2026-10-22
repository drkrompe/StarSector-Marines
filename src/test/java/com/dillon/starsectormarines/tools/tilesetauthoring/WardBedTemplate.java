package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * The ground sick bay's ward bed re-drawn at real length, where a cell is about 0.75 m (a crewman's shoulders are 0.6
 * cells). A 2x1 ward bed was 1.5 m long and drawn half a metre too wide; this one is a 0.76 x 2.15 m hospital bed in a
 * 3x1 frame, the length of the ship's ward bed, head at the west (left) end. The other three facings are quarter turns
 * of it ({@code art-source/doodads/derive_medical_frames.py}), and the doodad atlas keeps the whole frame, so the bed
 * stays its true size inside its footprint.
 *
 * <p>Drawn large, 320 px per cell, on a canvas the size ImageGen returns, so the cut needs no rescaling.
 *
 * <pre>
 *   slot   frame (x,y,w,h px)    real size
 *   bed    288,352,960,320       0.76 x 2.15 m hospital bed, head and pillow at the left, folded blanket at the foot
 * </pre>
 */
public final class WardBedTemplate {
    private WardBedTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1536, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(288, 352);
        g.scale(C / 64.0, C / 64.0);

        // Frame 184 x 62 (2.15 x 0.73 m): graphite tube frame, a head board at the left and a foot board at the right.
        rect(g, 4, 1, 184, 62, 0x3E4644);
        rect(g, 4, 1, 8, 62, 0x6E7A55);
        rect(g, 180, 1, 8, 62, 0x6E7A55);
        // Side rails along the middle of both long sides.
        rect(g, 44, 1, 100, 4, 0x8E9690);
        rect(g, 44, 59, 100, 4, 0x8E9690);
        // Mattress and sheet, a pillow at the head, a folded olive blanket at the foot.
        rect(g, 13, 6, 166, 52, 0xC9CCC6);
        rect(g, 17, 12, 26, 40, 0xE2E3DC);
        rect(g, 140, 6, 39, 52, 0x6B7449);
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
