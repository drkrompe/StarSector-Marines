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
 * The briefing-room lectern re-drawn at real size, where a cell is about 0.75 m (a crewman's shoulders are 0.6
 * cells). The original 2x1 presenter's desk was 1.4 m wide; a lectern is about 0.6 x 0.5 m, so it is one cell, drawn
 * at true size inside the frame. The table, map overlay, display bank and chairs were already true and keep their
 * original generation.
 *
 * <p>Drawn large, 320 px per cell, on a two-cell canvas: small boxy pieces drawn small come back far too big. The
 * speaker's side, with the sloped reading desk, its screen and notes, is the bottom edge; the plain front panel faces
 * the top of the frame, where a wall-backed piece has its wall.
 *
 * <pre>
 *   slot     frame (x,y,w,h px)   real size
 *   lectern  160,160,320,320      0.6 x 0.5 m, reading desk on the bottom (speaker's) side
 * </pre>
 */
public final class BriefingRoomTemplate {
    private BriefingRoomTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(2 * C, 2 * C, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Lectern: 0.6 m wide, 0.5 m deep. Front panel at the top, sloped desk with screen and notes below it.
        g.setTransform(new AffineTransform());
        g.translate(C / 2, C / 2);
        g.scale(C / 64.0, C / 64.0);
        rect(g, 6, 12, 52, 43, 0x444F43);
        rect(g, 8, 14, 48, 8, 0x5A6555);
        rect(g, 9, 24, 46, 26, 0x9CA28D);
        rect(g, 13, 28, 20, 16, 0x3B5545);
        rect(g, 37, 28, 14, 18, 0xBABCA7);
        rect(g, 8, 51, 48, 3, 0x6E7866);

        g.dispose();
        return image;
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }

    public static void main(String[] args) throws Exception {
        Path p = Path.of(args[0]); Files.createDirectories(p.getParent()); ImageIO.write(render(), "png", p.toFile());
    }
}
