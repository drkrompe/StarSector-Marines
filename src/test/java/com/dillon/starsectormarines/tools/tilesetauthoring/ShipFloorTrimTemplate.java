package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Eight quiet material fields, then four registered full-cell architectural overlays. */
public final class ShipFloorTrimTemplate {
    private ShipFloorTrimTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        int[] colors = {0x656963, 0x929087, 0xB3B5AB, 0x81847C,
                0x686F61, 0x939D94, 0x606861, 0x9C9F92};
        for (int i = 0; i < colors.length; i++)
            rect(g, 16 + i % 4 * 256, 16 + i / 4 * 256, 224, 224, colors[i]);
        // Edge of a floor against a bulkhead: spans the entire top of its cell.
        rect(g, 16, 528, 224, 16, 0x454E48);
        rect(g, 16, 539, 224, 4, 0x92998A);
        // Rib lies entirely on existing wall, aligned vertically on its full cell.
        rect(g, 364, 528, 40, 224, 0x3C453F);
        rect(g, 372, 528, 24, 224, 0x626B5F);
        // Flush access cover: not standing equipment or a new obstacle.
        rect(g, 568, 568, 144, 144, 0x444F48);
        rect(g, 574, 574, 132, 132, 0x798274);
        rect(g, 624, 582, 32, 7, 0x48544A);
        // Hairline inset binding, again full width and anchored to the top edge.
        rect(g, 784, 528, 224, 4, 0x9B9F8F);
        g.dispose();
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
