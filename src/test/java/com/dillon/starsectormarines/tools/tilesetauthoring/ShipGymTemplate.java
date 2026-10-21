package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Gym equipment at real size: 128 px per cell on a 10x10-cell canvas, where a cell is about 0.75 m (a crewman's
 * shoulders are 0.6 cells). Every slot is a whole-cell placement frame with at least one empty cell of gutter, so the
 * exporter cuts each frame mechanically and the rower's seat keeps its registration against the rail it slides on.
 *
 * <pre>
 *   slot          frame (col,row,w,h)  real size
 *   treadmill     1,1,1,3              0.75 x 2.1 m deck, 0.5 m belt, console at the top
 *   rower         3,1,1,3              0.6 x 2.2 m, flywheel at the top, seat off the rail
 *   rower-seat    5,1,1,1              0.35 x 0.3 m sliding seat, centred
 *   bench-press   5,3,3,2              2.2 m bar racked across the top row, 0.3 m bench down the middle column
 *   weights       1,5,4,1              3 m rack of five dumbbell pairs
 *   mat           1,7,2,2              seamless rubber mat
 *   coping        4,7,2,2              seamless non-slip pool coping
 *   water         7,7,2,2              seamless pool water; the runtime owns waves
 * </pre>
 */
public final class ShipGymTemplate {
    private ShipGymTemplate() {}

    /** Pixels per cell on the canvas; shapes below are drawn in 64ths of a cell. */
    static final int C = 128;

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(10 * C, 10 * C, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Treadmill: deck and side rails the full frame, belt between them, console and handlebar at the top.
        at(g, 1, 1);
        rect(g, 4, 6, 56, 180, 0x444F46);
        rect(g, 12, 38, 40, 140, 0x2B2F30);
        rect(g, 4, 34, 7, 148, 0xA4AC9A); rect(g, 53, 34, 7, 148, 0xA4AC9A);
        rect(g, 6, 8, 52, 22, 0x8B9683); rect(g, 18, 12, 28, 12, 0x3E5747);
        rect(g, 2, 30, 60, 5, 0xA7AE9C);
        rect(g, 6, 180, 52, 6, 0x626F5C);

        // Rower: flywheel housing at the top, handle, footrests, rail to the rear foot. The seat is its own slot.
        at(g, 3, 1);
        rect(g, 28, 40, 8, 144, 0x7C8975);
        oval(g, 11, 4, 42, 42, 0x56664F); oval(g, 18, 11, 28, 28, 0x303F34);
        rect(g, 14, 48, 36, 5, 0xA7AE9C);
        rect(g, 12, 60, 15, 18, 0x3C4C3D); rect(g, 37, 60, 15, 18, 0x3C4C3D);
        rect(g, 12, 178, 40, 8, 0x626F5C);
        at(g, 5, 1);
        rect(g, 17, 19, 30, 26, 0x969E8A); rect(g, 22, 24, 20, 16, 0x7A8370);

        // Bench press: one bar racked across the top row with plates at both ends, two uprights, a narrow bench.
        at(g, 5, 3);
        rect(g, 54, 20, 10, 26, 0x9CA68F); rect(g, 128, 20, 10, 26, 0x9CA68F);
        rect(g, 44, 42, 30, 8, 0x626F5C); rect(g, 118, 42, 30, 8, 0x626F5C);
        rect(g, 4, 29, 184, 6, 0xA5AF9A);
        for (int x : new int[]{12, 26, 156, 170}) rect(g, x, 10, 10, 44, 0x344435);
        rect(g, 83, 40, 26, 82, 0x3B4A3D); rect(g, 86, 44, 20, 74, 0x74856D);
        rect(g, 78, 116, 36, 8, 0x626F5C);

        // Dumbbell rack: one long low shelf, five pairs of weights.
        at(g, 1, 5);
        rect(g, 4, 10, 248, 44, 0x525F4C);
        for (int x = 20; x < 240; x += 45) {
            rect(g, x, 28, 28, 8, 0xADB4A3); rect(g, x - 3, 18, 10, 28, 0x303D33);
            rect(g, x + 22, 18, 10, 28, 0x303D33);
        }

        // Material samples: opaque squares the runtime lays as floor.
        at(g, 1, 7); rect(g, 0, 0, 128, 128, 0x4F5D50);
        at(g, 4, 7); rect(g, 0, 0, 128, 128, 0xADB0A0);
        at(g, 7, 7); rect(g, 0, 0, 128, 128, 0x456D68);
        g.setClip(0, 0, 128, 128);
        g.setColor(new Color(0x688C7F)); g.setStroke(new BasicStroke(3));
        for (int y = 14; y < 128; y += 28) g.drawArc(-10, y, 148, 26, 0, 180);
        g.dispose();
        return image;
    }

    private static void at(Graphics2D g, int col, int row) {
        g.setTransform(new AffineTransform());
        g.translate(col * C, row * C);
        g.scale(C / 64.0, C / 64.0);
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }

    private static void oval(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillOval(x, y, w, h);
    }

    public static void main(String[] args) throws Exception {
        Path p = Path.of(args[0]); Files.createDirectories(p.getParent()); ImageIO.write(render(), "png", p.toFile());
    }
}
