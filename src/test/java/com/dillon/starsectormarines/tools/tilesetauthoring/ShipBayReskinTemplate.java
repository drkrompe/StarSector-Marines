package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Geometry contract for a shipboard deck study. Coordinates are image-space, north up. */
public final class ShipBayReskinTemplate {
    public static final int CELL = 224, PITCH = 256, MARGIN = 16;
    public static final String[] PARTS = {"deck", "passage", "hatch", "grate-run",
            "hazard-edge", "hazard-corner", "pipe-straight", "pipe-elbow",
            "pipe-tee", "pipe-terminal", "grate-field", "service-panel"};

    private ShipBayReskinTemplate() { }

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < PARTS.length; i++) {
            Graphics2D g = image.createGraphics();
            g.translate(i % 4 * PITCH + MARGIN, i / 4 * PITCH + MARGIN);
            g.setClip(0, 0, CELL, CELL);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x59646A)); g.fillRect(0, 0, CELL, CELL);
            if (i == 0 || i == 1) {
                g.setColor(new Color(0x414A4F)); g.fillRect(0, 0, CELL, 2);
                if (i == 0) g.fillRect(0, 0, 2, CELL);
                if (i == 1) {
                    g.setColor(new Color(0xA4ACAA)); g.fillRect(0, 24, CELL, 4);
                    g.fillRect(0, 196, CELL, 4);
                }
            }
            if (i == 2 || i == 11) {
                g.setColor(new Color(0x303A40)); g.fillRoundRect(30, 28, 164, 168, 12, 12);
                g.setColor(new Color(0x758186)); g.fillRoundRect(36, 34, 152, 156, 8, 8);
                g.setColor(new Color(0x303A40)); g.fillRect(95, 95, 34, 9);
                if (i == 11) { g.fillRect(55, 50, 110, 32); g.fillRect(55, 140, 110, 28); }
            }
            if (i == 3 || i == 10) {
                int top = i == 3 ? 30 : 0, bottom = i == 3 ? 194 : CELL;
                g.setColor(new Color(0x202B30)); g.fillRect(0, top, CELL, bottom - top);
                g.setColor(new Color(0x7B8587));
                for (int x = 8; x < CELL; x += 16) g.fillRect(x, top, 5, bottom - top);
                g.setColor(new Color(0x48575D));
                for (int y = top + 12; y < bottom; y += 24) g.fillRect(0, y, CELL, 4);
                if (i == 3) { g.fillRect(0, top, CELL, 8); g.fillRect(0, bottom - 8, CELL, 8); }
            }
            if (i == 4 || i == 5) {
                hazard(g);
                if (i == 5) { g.rotate(-Math.PI / 2, 112, 112); hazard(g); }
            }
            if (i >= 6 && i <= 9) {
                Path2D pipe = new Path2D.Double();
                pipe.moveTo(0, 112);
                if (i == 7) { pipe.lineTo(68, 112); pipe.quadTo(112, 112, 112, 156); pipe.lineTo(112, 224); }
                else pipe.lineTo(i == 9 ? 154 : CELL, 112);
                if (i == 8) { pipe.moveTo(112, 112); pipe.lineTo(112, CELL); }
                stroke(g, pipe, 86, 0x303C42);
                stroke(g, pipe, 68, 0x19262C);
                stroke(g, pipe, 40, 0x9C9D8B);
                stroke(g, pipe, 20, 0xC1BFA8);
                if (i == 6) {
                    g.setColor(new Color(0x485A63)); g.fillRect(47, 86, 12, 52); g.fillRect(165, 86, 12, 52);
                }
                if (i == 9) {
                    g.setColor(new Color(0x313C42)); g.fillRoundRect(144, 68, 60, 88, 8, 8);
                    g.setColor(new Color(0xA3ADA9)); g.fillOval(153, 87, 40, 50);
                    g.setColor(new Color(0xA17A43)); g.fillOval(162, 98, 22, 28);
                }
            }
            g.dispose();
        }
        return image;
    }

    private static void stroke(Graphics2D g, Path2D path, int width, int rgb) {
        g.setColor(new Color(rgb));
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(path);
    }

    private static void hazard(Graphics2D g) {
        g.setColor(new Color(0xB89E49)); g.fillRect(0, 8, CELL, 16);
        g.setColor(new Color(0x28343B));
        for (int x = -16; x < CELL; x += 32)
            g.fillPolygon(new int[]{x, x + 13, x + 29, x + 16}, new int[]{24, 24, 8, 8}, 4);
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(render(), "png", path.toFile());
    }
}
