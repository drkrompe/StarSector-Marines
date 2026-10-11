package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Four isolated slots: basin (1x1), toilet and shower cubicles (1x2), seamless deck. */
public final class ShipWashroomTemplate {
    private ShipWashroomTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Basin back faces image-bottom; the opposite bank's cubicles face image-bottom.
        g.translate(144, 136);
        rect(g, 0, 0, 192, 192, 0x878980);
        rect(g, 7, 7, 178, 170, 0xB4B4A8);
        oval(g, 32, 25, 128, 108, 0x5E6865);
        oval(g, 42, 34, 108, 86, 0xA8AFA9);
        oval(g, 89, 88, 14, 14, 0x48524F);
        rect(g, 0, 174, 192, 18, 0x454D47);
        rect(g, 85, 139, 22, 38, 0xA4ABA5);
        rect(g, 85, 132, 22, 19, 0x697771);
        oval(g, 43, 150, 19, 19, 0x737E77);
        oval(g, 130, 150, 19, 19, 0x737E77);
        g.translate(-144, -136);

        g.translate(656, 56);
        cubicle(g);
        rect(g, 38, 40, 116, 54, 0xB6B8AD); // cistern at the back
        oval(g, 39, 85, 114, 174, 0xBCC0B5);
        oval(g, 54, 102, 84, 122, 0x4A5954);
        oval(g, 65, 119, 62, 80, 0x919F98);
        rect(g, 159, 151, 16, 35, 0x9CA399); // paper holder, contained
        g.translate(-656, -56);

        g.translate(144, 556);
        cubicle(g);
        rect(g, 27, 56, 138, 278, 0x8C9992); // broad shower pan
        rect(g, 34, 63, 124, 264, 0x9DA7A0);
        rect(g, 87, 26, 18, 82, 0x6C7770);
        oval(g, 69, 78, 54, 39, 0xBEC4B8); // overhead shower head
        rect(g, 45, 29, 22, 17, 0x939C91); // control
        rect(g, 75, 274, 42, 28, 0x4B5852); // one broad drain
        for (int x = 81; x < 113; x += 9) rect(g, x, 279, 4, 18, 0xA4AEA3);
        g.translate(-144, -556);

        rect(g, 584, 590, 336, 336, 0x969D91); // quiet seamless non-slip resin sample
        g.dispose();
        return image;
    }

    private static void cubicle(Graphics2D g) {
        rect(g, 0, 0, 192, 384, 0xA7AAA0);
        rect(g, 0, 0, 192, 22, 0x353E39);
        rect(g, 0, 0, 17, 384, 0x353E39);
        rect(g, 175, 0, 17, 384, 0x353E39);
        rect(g, 17, 364, 24, 20, 0x535D54);
        rect(g, 151, 364, 24, 20, 0x535D54);
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
