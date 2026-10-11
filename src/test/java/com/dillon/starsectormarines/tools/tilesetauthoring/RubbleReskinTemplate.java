package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Broken low fragments retain the wall's connected edges and leave open ground between them. */
public final class RubbleReskinTemplate {
    public static final int PANEL = 1024, PITCH = 256, MARGIN = 16, CELL = 224;
    private RubbleReskinTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(PANEL * 3, PANEL, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0x986347, 0xB9B3A0, 0x70754D};
        for (int family = 0; family < 3; family++) for (int mask = 0; mask < 16; mask++) {
            Graphics2D g = image.createGraphics();
            g.translate(family * PANEL + (mask % 4) * PITCH + MARGIN, (mask / 4) * PITCH + MARGIN);
            g.clipRect(0, 0, CELL, CELL);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = new Color(colors[family]);
            // The same deterministic scatter in each material makes geometry independent of skin.
            Random random = new Random(1709 + mask);
            for (int i = 0; i < 19; i++) {
                int x = 20 + random.nextInt(184), y = 20 + random.nextInt(184);
                if (Math.abs(x - 112) < 28 && Math.abs(y - 112) < 40) continue;
                chunk(g, x, y, 12 + random.nextInt(28), 10 + random.nextInt(19),
                        random.nextInt(90) - 45, base);
            }
            for (int side = 0; side < 4; side++) {
                Graphics2D edge = (Graphics2D) g.create();
                edge.rotate(side * Math.PI / 2, 112, 112);
                if ((mask & (1 << side)) == 0) {
                    // Remnant roots join the former wall at a connected edge.
                    chunk(edge, 66, 13, 67, 31, 0, base);
                    chunk(edge, 166, 12, 55, 29, 0, base);
                } else {
                    chunk(edge, 49, 33, 28, 20, -22, base);
                    chunk(edge, 181, 40, 25, 16, 25, base);
                }
                edge.dispose();
            }
            g.dispose();
        }
        return image;
    }

    private static void chunk(Graphics2D target, int x, int y, int w, int h, int angle, Color color) {
        Graphics2D g = (Graphics2D) target.create();
        g.translate(x, y); g.rotate(Math.toRadians(angle));
        int[] px = {-w / 2, w / 3, w / 2, w / 2 - 5, -w / 3, -w / 2};
        int[] py = {-h / 3, -h / 2, -h / 5, h / 2, h / 2 - 2, h / 5};
        g.setColor(color.darker()); g.fillPolygon(px, py, px.length);
        g.translate(0, -3); g.setColor(color); g.fillPolygon(px, py, px.length);
        g.dispose();
    }
}
