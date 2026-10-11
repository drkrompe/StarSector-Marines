package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Six 1x2-cell components: bed, opposing linen layers, retaining rail and two locker banks. */
public final class BarracksBunkTemplate {
    private BarracksBunkTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int slot = 0; slot < 6; slot++) {
            Graphics2D g = image.createGraphics();
            g.translate(72 + slot % 3 * 320, 48 + slot / 3 * 512);
            switch (slot) {
                case 0 -> {
                    rect(g, 4, 4, 152, 312, 0x3D4541);
                    rect(g, 16, 16, 128, 288, 0x5B635B);
                    rect(g, 28, 293, 104, 10, 0x969D91);
                }
                case 1, 2 -> {
                    rect(g, 20, 18, 120, 284, 0xA0A393);
                    boolean headTop = slot == 1;
                    rect(g, 28, headTop ? 26 : 242, 104, 50, 0xC9CABB);
                    rect(g, 20, headTop ? 90 : 18, 120, 212, 0x737E6D);
                    rect(g, 20, headTop ? 90 : 208, 120, 22, 0x8B9582);
                }
                case 3 -> {
                    rect(g, 4, 4, 8, 312, 0x69746A);
                    rect(g, 148, 4, 8, 312, 0x69746A);
                    rect(g, 4, 4, 152, 10, 0x444E46);
                    rect(g, 4, 306, 152, 10, 0x444E46);
                    for (int y : new int[]{4, 294}) {
                        rect(g, 0, y, 18, 22, 0x8B9589);
                        rect(g, 142, y, 18, 22, 0x8B9589);
                    }
                }
                case 4, 5 -> {
                    rect(g, 4, 4, 152, 312, 0x3F4840);
                    for (int y : new int[]{12, 114, 216}) {
                        rect(g, 12, y, 136, 94, 0x78816F);
                        rect(g, 119, y+35, 8, 24, 0xB0B5A3);
                        rect(g, 27, y+12, 38, 8, 0x4B5447);
                    }
                    if (slot == 5) {
                        rect(g, 16, 118, 128, 86, 0x2D3831);
                        rect(g, 30, 146, 92, 42, 0x9A9D89);
                        rect(g, 34, 140, 84, 13, 0x707D69);
                    }
                }
            }
            g.dispose();
        }
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
