package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Code-owned overhead silhouettes: trap, divider, preparation bench, control and two decks. */
public final class FiringRangeTemplate {
    private FiringRangeTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // A deep bullet trap faces image-left; repeats vertically without exposed junk.
        rect(g, 96, 88, 144, 288, 0x333B38);
        rect(g, 105, 98, 112, 268, 0x4D5651);
        rect(g, 111, 105, 75, 254, 0x242D2A);
        for (int y = 109; y < 350; y += 48) {
            rect(g, 145, y, 55, 12, 0x626B62);
            rect(g, 120, y + 12, 40, 12, 0x434D47);
        }
        rect(g, 220, 88, 20, 288, 0x727A70);
        // Broad ballistic divider and elbow-height shelf beside a shooter, pointing right.
        rect(g, 368, 160, 288, 144, 0x343D38);
        rect(g, 380, 175, 264, 90, 0x575E54);
        rect(g, 380, 270, 264, 22, 0x8D9082);
        rect(g, 380, 179, 264, 18, 0x747B6E);
        // Two cleaning mats and contained trays; no loose floor objects.
        rect(g, 704, 160, 288, 144, 0x4C554D);
        rect(g, 712, 168, 272, 128, 0x98988A);
        rect(g, 722, 183, 104, 93, 0x38463F);
        rect(g, 841, 183, 104, 93, 0x38463F);
        rect(g, 955, 181, 20, 98, 0x656D61);
        // Range controller, screen kept subdued.
        rect(g, 24, 666, 288, 144, 0x48534B);
        rect(g, 34, 676, 268, 124, 0x858B7D);
        rect(g, 54, 686, 145, 69, 0x293D37);
        rect(g, 63, 695, 127, 50, 0x596B5D);
        rect(g, 54, 767, 145, 22, 0x4A5348);
        rect(g, 218, 687, 66, 102, 0x606B5D);
        rect(g, 384, 604, 256, 256, 0x7B8077);
        rect(g, 720, 604, 256, 256, 0x4D5852);
        g.dispose();
        return image;
    }
    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x, y, w, h);
    }
    /** A rotationally symmetric visual plate; the live target remains simulation-owned. */
    public static BufferedImage target() {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        rect(g, 104, 104, 304, 304, 0x39433E);
        rect(g, 112, 112, 288, 288, 0x6C746A);
        for (int r = 132, i = 0; r >= 22; r -= 22, i++) {
            g.setColor(new Color(i % 2 == 0 ? 0xB9B7A5 : 0x404A43));
            g.fillOval(256 - r, 256 - r, r * 2, r * 2);
        }
        g.dispose();
        return image;
    }
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(args.length > 1 ? target() : render(), "png", path.toFile());
    }
}
