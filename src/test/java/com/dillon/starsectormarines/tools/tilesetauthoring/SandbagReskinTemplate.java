package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Two canonical cell silhouettes; their rotations supply every connected ring direction. */
public final class SandbagReskinTemplate {
    public static final int PITCH = 768, MARGIN = 64, CELL = 640;
    private SandbagReskinTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(PITCH * 2, PITCH, BufferedImage.TYPE_INT_ARGB);
        for (int shape = 0; shape < 2; shape++) {
            Graphics2D g = image.createGraphics();
            g.translate(shape * PITCH + MARGIN, MARGIN);
            g.clipRect(0, 0, CELL, CELL);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int course = 0; course < 2; course++) {
                int inset = course == 0 ? 48 : 128;
                if (shape == 0) {
                    for (int x = course == 0 ? 0 : -80; x <= CELL + 80; x += 160) bag(g, x, inset, 0);
                } else {
                    for (int along = course == 0 ? 320 : 240; along <= CELL + 80; along += 160) {
                        bag(g, along, inset, 0);
                        bag(g, inset, along, 90);
                    }
                    bag(g, 192, inset, 0);
                    bag(g, inset, 192, 90);
                    int bend = course == 0 ? 90 : 147;
                    bag(g, bend, bend, -45);
                }
            }
            g.dispose();
        }
        return image;
    }

    private static void bag(Graphics2D target, int x, int y, int angle) {
        Graphics2D g = (Graphics2D) target.create();
        g.translate(x, y);
        g.rotate(Math.toRadians(angle));
        g.setColor(new Color(0x4D4933));
        g.fillRoundRect(-90, -36, 180, 72, 32, 32);
        g.setColor(new Color(0xA3986B));
        g.fillRoundRect(-84, -30, 168, 60, 26, 26);
        g.dispose();
    }
}
