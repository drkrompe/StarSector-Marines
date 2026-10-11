package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Eight overhead components, 96px/cell, in 512x256 slots with origin (64,32). */
public final class MessGalleyTemplate {
    private MessGalleyTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int slot = 0; slot < 8; slot++) {
            Graphics2D g = image.createGraphics();
            g.translate(64 + slot % 2 * 512, 32 + slot / 2 * 256);
            switch (slot) {
                case 0 -> { // 3x1 dining table
                    rect(g, 4, 9, 280, 78, 0x343D39);
                    rect(g, 8, 9, 272, 68, 0x949889);
                    rect(g, 12, 13, 264, 3, 0xB1B3A4);
                }
                case 1 -> { // 3x1 sparse meal overlay, most table remains bare
                    for (int x : new int[]{34, 202}) {
                        rect(g, x, 22, 46, 48, 0x686F62);
                        oval(g, x+7, 29, 29, 29, 0xCFCEC0);
                        oval(g, x+12, 34, 19, 19, 0x88836A);
                        oval(g, x+48, 29, 13, 13, 0xA8AAA0);
                    }
                }
                case 2 -> { // 1x1 round bolted stool, no directional back
                    oval(g, 17, 17, 62, 62, 0x515B53);
                    oval(g, 21, 21, 54, 54, 0x939B8E);
                    oval(g, 25, 25, 46, 46, 0x596652);
                }
                case 3 -> { // 1x2 cooking range; working edge is image right
                    appliance(g);
                    for (int y : new int[]{18, 105}) {
                        rect(g, 14, y, 57, 65, 0x353F3C);
                        oval(g, 23, y+15, 38, 38, 0x626C62);
                        oval(g, 31, y+23, 22, 22, 0x303A35);
                    }
                    rect(g, 80, 15, 6, 159, 0x535E54);
                }
                case 4 -> { // 1x2 prep bench, clear central work surface
                    appliance(g);
                    rect(g, 21, 36, 54, 88, 0x9C9C81);
                    rect(g, 22, 151, 50, 22, 0x4D5A4E);
                }
                case 5 -> { // 1x2 double sink
                    appliance(g);
                    for (int y : new int[]{18, 103}) {
                        rect(g, 15, y, 61, 68, 0x59675F);
                        rect(g, 22, y+8, 47, 50, 0x3E4C48);
                        rect(g, 72, y+28, 15, 7, 0xC3C6B8);
                    }
                }
                case 6 -> { // 1x2 cold store, two sealed compartments
                    appliance(g);
                    for (int y : new int[]{12, 99}) {
                        rect(g, 12, y, 70, 79, 0x697568);
                        rect(g, 68, y+29, 6, 27, 0xB5BAAC);
                    }
                }
                case 7 -> { // 1x2 servery, tray rail on dining/right side
                    appliance(g);
                    for (int y : new int[]{20, 102}) {
                        rect(g, 17, y, 49, 65, 0x536153);
                        rect(g, 23, y+6, 37, 53, 0x8B9181);
                    }
                    rect(g, 76, 10, 12, 170, 0xAFB4A6);
                }
            }
            g.dispose();
        }
        return image;
    }
    private static void appliance(Graphics2D g) {
        rect(g, 4, 4, 88, 184, 0x37423C);
        rect(g, 8, 8, 80, 176, 0x9AA195);
    }
    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }
    private static void oval(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillOval(x, y, w, h);
    }
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(render(), "png", path.toFile());
    }
}
