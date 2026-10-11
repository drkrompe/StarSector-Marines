package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Ten overhead components, 80px/cell, at (64,20) with 512x200 slot pitch. */
public final class SickbayClinicTemplate {
    private SickbayClinicTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int slot = 0; slot < 10; slot++) {
            Graphics2D g = image.createGraphics();
            g.translate(64 + slot % 2 * 512, 20 + slot / 2 * 200);
            switch (slot) {
                case 0 -> { // 1x2 bed frame, open tray to receive linen
                    rect(g, 7, 3, 66, 154, 0x525D56);
                    rect(g, 13, 9, 54, 142, 0x8F988C);
                    for (int x : new int[]{3,70}) rect(g, x, 29, 7, 102, 0xC0C4B8);
                    rect(g, 8, 5, 64, 8, 0xB9BDB0);
                    rect(g, 8, 147, 64, 8, 0xB9BDB0);
                }
                case 1, 2 -> { // 1x2 linen, pillow high/low in image coordinates
                    rect(g, 15, 15, 50, 130, 0xCBCBBF);
                    boolean top = slot == 1;
                    rect(g, 18, top ? 20 : 117, 44, 24, 0xE0DED0);
                    rect(g, 15, top ? 49 : 15, 50, 96, 0xA6B2A0);
                    rect(g, 15, top ? 49 : 103, 50, 10, 0xC3CBBB);
                }
                case 3 -> { // 1x1 monitor cabinet
                    casework(g, 80, 80);
                    rect(g, 13, 12, 54, 38, 0x303F3B);
                    rect(g, 20, 27, 39, 3, 0x7D9C85);
                    rect(g, 21, 60, 38, 5, 0x606D62);
                }
                case 4 -> { // 2x1 nurse station
                    casework(g, 160, 80);
                    rect(g, 14, 13, 60, 35, 0x33453E);
                    rect(g, 22, 54, 48, 12, 0x69786A);
                    rect(g, 108, 22, 32, 40, 0xE0DCCB);
                }
                case 5 -> { // 2x1 secured medical cabinet
                    casework(g, 160, 80);
                    for (int x : new int[]{11,82}) {
                        rect(g, x, 10, 67, 60, 0x9EAC99);
                        rect(g, x+49, 29, 5, 24, 0xD0D0C2);
                    }
                }
                case 6 -> { // 1x1 scrub sink
                    casework(g, 80, 80);
                    rect(g, 13, 15, 48, 48, 0x667B71);
                    rect(g, 20, 22, 34, 34, 0x394E47);
                    rect(g, 53, 36, 18, 6, 0xD5D8CE);
                }
                case 7 -> { // 1x1 supply trolley
                    casework(g, 80, 80);
                    rect(g, 15, 14, 50, 50, 0x647868);
                    rect(g, 20, 20, 20, 28, 0xE0DDCC);
                    rect(g, 47, 20, 10, 16, 0xAEBDA7);
                    rect(g, 21, 54, 37, 4, 0xC2CCBF);
                }
                case 8 -> { // 2x1 examination couch, pillow left
                    casework(g, 160, 80);
                    rect(g, 12, 16, 136, 48, 0x93A993);
                    rect(g, 17, 20, 33, 40, 0xD6D8C9);
                    rect(g, 55, 16, 4, 48, 0x5B7261);
                    rect(g, 119, 16, 4, 48, 0x5B7261);
                }
                case 9 -> { // 1x2 privacy screen, narrow vertical silhouette
                    rect(g, 32, 6, 16, 148, 0x728477);
                    rect(g, 36, 12, 8, 136, 0xB6C3AE);
                    rect(g, 13, 7, 54, 7, 0x9AA89A);
                    rect(g, 13, 146, 54, 7, 0x9AA89A);
                }
            }
            g.dispose();
        }
        return image;
    }
    private static void casework(Graphics2D g, int w, int h) {
        rect(g, 4, 4, w-8, h-8, 0x46564B);
        rect(g, 8, 8, w-16, h-16, 0xB7BFAF);
    }
    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(render(), "png", path.toFile());
    }
}
