package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** One connected overhead frame; cell centers are 256, 512 and 768 on each axis. */
public final class FenceReskinTemplate {
    private FenceReskinTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x737B78));
        g.setStroke(new BasicStroke(48, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.drawRect(256, 256, 512, 512);
        g.setColor(new Color(0xC0C4B9));
        g.setStroke(new BasicStroke(8));
        g.drawRect(236, 236, 552, 552);
        g.drawRect(276, 276, 472, 472);
        for (int y = 256; y <= 768; y += 256) for (int x = 256; x <= 768; x += 256) {
            if (x == 512 && y == 512) continue;
            g.setColor(new Color(0x363F40));
            g.fillOval(x - 32, y - 32, 64, 64);
            g.setColor(new Color(0xADB3AA));
            g.fillOval(x - 24, y - 24, 48, 48);
        }
        g.dispose();
        return image;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(render(), "png", new File(args[0]));
    }
}
