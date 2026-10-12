package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Nine separated slots; shelf bed, stock and open frame share a 2x1 canvas. */
public final class ShipUtilityTemplate {
    private ShipUtilityTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // Server cabinet: broad enclosed modules instead of a noisy perforated grille.
        panel(g, 48, 48);
        for (int y = 75; y < 250; y += 42) {
            rect(g, 70, y, 212, 32, 0x555F57);
            rect(g, 81, y + 8, 158, 14, 0x343F39);
            rect(g, 252, y + 9, 15, 12, 0x919A82);
        }
        // A compact service console.
        panel(g, 384, 48);
        rect(g, 411, 76, 201, 112, 0x303D36);
        rect(g, 423, 88, 177, 88, 0x68766A);
        rect(g, 411, 211, 144, 59, 0x6F786B);
        rect(g, 571, 211, 41, 59, 0x535E51);
        // Small enclosed switchboard.
        panel(g, 720, 48);
        rect(g, 742, 74, 212, 188, 0x788170);
        rect(g, 758, 93, 80, 54, 0x38483F);
        for (int y = 173; y < 248; y += 31) {
            rect(g, 759, y, 72, 19, 0x4B594B);
            rect(g, 855, y, 72, 19, 0x4B594B);
        }
        // Three 2x1 layers, identical registration, with generous transparent gutters.
        rect(g, 32, 444, 288, 144, 0x4A554C);
        rect(g, 46, 458, 260, 116, 0x777D6B);
        rect(g, 171, 458, 10, 116, 0x4E5B4D);
        for (int x : new int[]{388, 522}) {
            rect(g, x, 464, 114, 104, 0x8B8F7D);
            rect(g, x + 9, 473, 96, 85, 0x465449);
            for (int i = 0; i < 3; i++) rect(g, x + 17 + i*28, 485, 16, 59, 0x959E8D);
        }
        rect(g, 704, 444, 288, 13, 0x566256);
        rect(g, 704, 575, 288, 13, 0x566256);
        rect(g, 704, 444, 13, 144, 0x566256);
        rect(g, 979, 444, 13, 144, 0x566256);
        rect(g, 843, 444, 10, 144, 0x566256);
        // Contained pump skid: one connected loop, no pipe runs across cell boundaries.
        panel(g, 48, 720);
        rect(g, 74, 748, 204, 20, 0x9CA392);
        rect(g, 74, 748, 20, 196, 0x9CA392);
        rect(g, 74, 924, 204, 20, 0x9CA392);
        rect(g, 258, 748, 20, 196, 0x9CA392);
        rect(g, 154, 753, 24, 186, 0x9CA392);
        oval(g, 109, 799, 129, 99, 0x7C8A78);
        oval(g, 146, 816, 55, 63, 0x49594C);
        // Cleaning locker, large closed panels with a contained shallow supply compartment.
        panel(g, 384, 720);
        rect(g, 404, 740, 118, 216, 0x8D9181);
        rect(g, 532, 740, 87, 216, 0x63705E);
        rect(g, 497, 812, 12, 56, 0x4F5B4E);
        rect(g, 544, 756, 63, 57, 0x9A9F8B);
        rect(g, 544, 834, 63, 106, 0x445349);
        // Shallow issue/repair counter, one rubber mat and inset sorting tray.
        panel(g, 720, 720);
        rect(g, 739, 739, 218, 214, 0xA2A392);
        rect(g, 754, 754, 133, 182, 0x536455);
        rect(g, 902, 754, 40, 182, 0x6A7765);
        g.dispose();
        return image;
    }
    private static void panel(Graphics2D g, int x, int y) {
        rect(g, x, y, 256, 256, 0x354039);
        rect(g, x + 10, y + 10, 236, 236, 0x798172);
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
