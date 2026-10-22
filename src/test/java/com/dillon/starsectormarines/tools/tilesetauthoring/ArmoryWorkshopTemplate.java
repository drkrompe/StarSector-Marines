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
 * The armory pieces re-drawn at real size, where a cell is about 0.75 m (a crewman's shoulders are 0.6 cells): the
 * repair bench, its tool overlay and the ready-use locker bank, each three cells by one. The rest of the kit (counter,
 * rifle bed, rifles, retaining frame, ammunition cabinet) was already true and keeps its original generation.
 *
 * <p>Drawn large, 320 px per cell, one 3x1 frame per row: at 128 px per cell ImageGen drew these boxy pieces half
 * again too wide and two to four times too deep, twice, whatever the brief said, while at this size it holds them.
 * The worked face of every piece is its bottom edge: a sprite's top edge lies at its footprint's highest row, and
 * each piece is worked from the aisle below it.
 *
 * <pre>
 *   slot          frame (x,y,w,h px)    real size
 *   bench         160, 80,960,320       2.1 x 0.7 m repair bench, drawers on the near (bottom) fascia
 *   bench-tools   160,480,960,320       overlay: stripped rifle, vise, tool tray
 *   ready-locker  160,880,960,320       2.1 x 0.5 m three-door locker bank, doors on the bottom face
 * </pre>
 *
 * <p>The ammunition cabinet followed later, on its own canvas ({@link #renderAmmunition()}): the original was a
 * 2x2 front elevation, a cabinet 1.5 m square seen face on. It is a 2x1 frame now, the lid of a sealed cabinet
 * 1.0 x 0.6 m seen from above, standing against the back of its frame with its two doors on the bottom face.
 *
 * <pre>
 *   slot          frame (x,y,w,h px)    real size
 *   ammo          448,352,640,320       1.0 x 0.6 m sealed ammunition cabinet on a 1536x1024 canvas
 * </pre>
 */
public final class ArmoryWorkshopTemplate {
    private ArmoryWorkshopTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 320;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(4 * C, 4 * C, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Repair bench: 0.7 m deep top with a rubber mat, three drawer pulls on the near fascia.
        at(g, 0, 0);
        rect(g, 4, 2, 184, 60, 0x3C4641);
        rect(g, 6, 4, 180, 48, 0x91978B);
        rect(g, 22, 10, 120, 36, 0x3D4941);
        for (int x : new int[]{14, 76, 138}) rect(g, x, 55, 40, 5, 0x92988B);

        // Bench tools: stripped rifle along the mat, removed barrel, vise at the right end, tool tray.
        at(g, 0, 1);
        rect(g, 30, 22, 88, 8, 0x778076);
        rect(g, 28, 19, 24, 15, 0x363D38);
        rect(g, 58, 30, 9, 10, 0x4E5850);
        rect(g, 70, 38, 46, 5, 0x7D897F);
        rect(g, 156, 10, 18, 26, 0x4E5B56);
        rect(g, 150, 18, 30, 8, 0xA4ACA0);
        rect(g, 150, 38, 30, 10, 0x434D44);

        // Ready-use locker bank: 0.5 m deep against the back of its cell, three doors with pulls on the near face.
        at(g, 0, 2);
        rect(g, 4, 18, 184, 44, 0x3D4741);
        for (int x : new int[]{8, 69, 130}) {
            rect(g, x, 22, 54, 32, 0x727B6A);
            rect(g, x + 4, 54, 46, 6, 0x5D6656);
            rect(g, x + 38, 55, 8, 4, 0xB0B5A5);
            rect(g, x + 10, 27, 30, 5, 0x424D42);
        }

        g.dispose();
        return image;
    }

    /** The 2x1 ammunition cabinet alone, on a canvas the size ImageGen returns, so its cut needs no rescaling. */
    public static BufferedImage renderAmmunition() {
        BufferedImage image = new BufferedImage(1536, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(448, 352);
        g.scale(C / 64.0, C / 64.0);

        // Armoured body 85 x 51 (1.0 x 0.6 m), back to the wall; a reinforced lid inside its rim.
        rect(g, 21, 4, 86, 52, 0x3C4641);
        rect(g, 24, 7, 80, 42, 0x6E7766);
        rect(g, 24, 26, 80, 3, 0x56604F);
        rect(g, 62, 7, 4, 42, 0x56604F);
        // Two door fronts on the near (bottom) face, a locking bar and a pull each.
        rect(g, 23, 49, 40, 6, 0x5D6656);
        rect(g, 65, 49, 40, 6, 0x5D6656);
        rect(g, 54, 50, 8, 4, 0xB0B5A5);
        rect(g, 66, 50, 8, 4, 0xB0B5A5);
        g.dispose();
        return image;
    }

    /** Slot {@code row}'s frame: half a cell in from the left, a quarter cell of gutter above each row. */
    private static void at(Graphics2D g, int col, int row) {
        g.setTransform(new AffineTransform());
        g.translate(col * C + C / 2, row * (C + C / 4) + C / 4);
        g.scale(C / 64.0, C / 64.0);
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        boolean ammunition = args.length > 1 && args[1].equals("ammunition");
        ImageIO.write(ammunition ? renderAmmunition() : render(), "png", path.toFile());
    }
}
