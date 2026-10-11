package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Localized damage silhouettes; transparent space retains the original floor and its seams. */
public final class GroundDamageReskinTemplate {
    public static final int PITCH = 256, MARGIN = 16, CELL = 224;
    public static final String[] MATERIALS = {
            "limestone", "terracotta", "basalt", "sage", "terrazzo", "timber", "concrete", "ochre", "tread"};
    private static final int[] COLORS = {
            0xBBA481, 0xA86E47, 0x53534F, 0x85886C, 0xB7AF97, 0x6A5039, 0x8A8375, 0xAF8439, 0x65645A};
    private GroundDamageReskinTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(PITCH * 6, PITCH * 3, BufferedImage.TYPE_INT_ARGB);
        for (int material = 0; material < MATERIALS.length; material++) for (int variant = 0; variant < 2; variant++) {
            Graphics2D g = image.createGraphics();
            g.translate((material % 3 * 2 + variant) * PITCH + MARGIN, material / 3 * PITCH + MARGIN);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = new Color(COLORS[material]);
            // Both shapes stay inside the full cell, leaving the perimeter and most floor visible.
            if (variant == 0) {
                crack(g, new int[]{35, 65, 93, 117, 142, 184}, new int[]{53, 71, 105, 122, 147, 170}, base);
                crack(g, new int[]{93, 109, 130, 154}, new int[]{105, 84, 73, 40}, base);
                crack(g, new int[]{117, 101, 76, 68}, new int[]{122, 143, 153, 180}, base);
                chip(g, 96, 112, 15, base); chip(g, 146, 119, 10, base);
            } else {
                g.setColor(new Color(0x39362F));
                g.fillPolygon(new int[]{66, 91, 126, 153, 163, 133, 104, 71, 54},
                        new int[]{79, 61, 70, 92, 120, 150, 158, 138, 109}, 9);
                crack(g, new int[]{66, 46, 36}, new int[]{79, 65, 44}, base);
                crack(g, new int[]{133, 151, 177, 188}, new int[]{150, 171, 175, 188}, base);
                for (int i = 0; i < 7; i++) {
                    double a = i * Math.PI * 2 / 7;
                    chip(g, 110 + (int)(63 * Math.cos(a)), 110 + (int)(57 * Math.sin(a)), 12 + i % 3 * 3, base);
                }
            }
            g.dispose();
        }
        return image;
    }

    private static void crack(Graphics2D g, int[] x, int[] y, Color base) {
        g.setColor(base.brighter()); g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(x, y, x.length);
        g.setColor(new Color(0x37352F)); g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(x, y, x.length);
    }

    private static void chip(Graphics2D g, int x, int y, int size, Color base) {
        g.setColor(base.darker());
        g.fillPolygon(new int[]{x-size, x, x+size, x+size/2, x-size/2},
                new int[]{y, y-size/2, y-2, y+size/2, y+size/2}, 5);
        g.setColor(base);
        g.fillPolygon(new int[]{x-size, x, x+size, x+size/2, x-size/2},
                new int[]{y-2, y-size/2-2, y-4, y+size/2-2, y+size/2-2}, 5);
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(render(), "png", Path.of(args[0]).toFile());
    }
}
