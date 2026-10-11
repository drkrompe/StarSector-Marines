package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Eight overhead components at 64px/cell, with room for padded overlay cuts. */
public final class MainStoresTemplate {
    private MainStoresTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        for (int x : new int[]{48, 272}) {
            if (x == 48) {
                rect(g, x + 4, 48, 120, 384, 0x414743);
                for (int slot = 0; slot < 3; slot++)
                    rect(g, x + 12, 60 + slot * 128, 104, 104, 0x71766A);
            }
            for (int side : new int[]{4,116}) rect(g, x + side, 48, 8, 384, 0x555F57);
            for (int beam = 0; beam <= 3; beam++)
                rect(g, x + 4, 48 + Math.min(beam * 128, 376), 120, 8, 0x98988A);
        }
        // Cartons, cases and drums are inset inside identical 2x2 canvases.
        for (int dx : new int[]{16,66}) for (int dy : new int[]{16,66}) {
            rect(g, 496 + dx, 48 + dy, 46, 46, 0xA99777);
            rect(g, 496 + dx + 20, 48 + dy, 6, 46, 0xC1B293);
        }
        for (int dx : new int[]{16,66}) {
            rect(g, 752 + dx, 48 + 16, 46, 96, 0x727B68);
            for (int dy : new int[]{32,88}) rect(g, 752 + dx, 48 + dy, 46, 5, 0xA4AAA0);
        }
        for (int dx : new int[]{39,89}) for (int dy : new int[]{39,89}) {
            g.setColor(new Color(0x696E65)); g.fillOval(496+dx-22, 304+dy-22, 44, 44);
            g.setColor(new Color(0x9D9D8C)); g.fillOval(496+dx-18, 304+dy-18, 36, 36);
        }
        // Pallet, full 2x2 footprint beneath any of the three stock overlays.
        rect(g, 756, 308, 120, 120, 0x4F5149);
        for (int dx = 8; dx < 120; dx += 23) rect(g, 752+dx, 308, 17, 120, 0x99927C);
        // Three-cell tally desk and packing/recovery bench, one cell deep.
        for (int y : new int[]{592, 784}) {
            rect(g, 48, y, 192, 64, 0x464F49);
            rect(g, 53, y+5, 182, 54, 0xA6A798);
        }
        rect(g, 62, 602, 60, 28, 0x344B43);
        rect(g, 66, 636, 52, 10, 0x777E71);
        rect(g, 172, 604, 42, 38, 0xC5C0AB);
        rect(g, 66, 796, 54, 35, 0x807B66);
        rect(g, 158, 798, 56, 6, 0x535E54);
        rect(g, 158, 814, 40, 6, 0x535E54);
        g.dispose();
        return image;
    }
    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x,y,w,h);
    }
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(render(), "png", path.toFile());
    }
}
