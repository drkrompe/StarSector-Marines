package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/** Full 384px cells at 512px pitch: damaged above destroyed, straight before elbow. */
public final class FenceDamageReskinTemplate {
    private FenceDamageReskinTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) {
            Graphics2D g = image.createGraphics();
            g.translate(64 + col * 512, 64 + row * 512);
            g.clipRect(0, 0, 384, 384);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (row == 0 && col == 0) {
                rail(g, new int[]{0, 90, 160, 230, 300, 384}, new int[]{192, 192, 230, 214, 192, 192});
                post(g, 192, 222);
            } else if (row == 0) {
                rail(g, new int[]{384, 290, 230, 192, 192}, new int[]{192, 192, 218, 290, 384});
                post(g, 219, 226);
            } else if (col == 0) {
                rail(g, new int[]{0, 64, 110}, new int[]{192, 192, 230});
                rail(g, new int[]{384, 320, 274}, new int[]{192, 192, 152});
                fragment(g, 75, 286, 136, 264);
                fragment(g, 248, 264, 295, 276);
                post(g, 113, 292);
            } else {
                rail(g, new int[]{384, 324, 278}, new int[]{192, 192, 164});
                rail(g, new int[]{192, 192, 155}, new int[]{384, 324, 282});
                fragment(g, 262, 272, 305, 297);
                post(g, 119, 288);
            }
            g.dispose();
        }
        return image;
    }

    private static void rail(Graphics2D g, int[] xs, int[] ys) {
        g.setStroke(new BasicStroke(62, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        g.setColor(new Color(0x9CA39A));
        g.drawPolyline(xs, ys, xs.length);
        g.setStroke(new BasicStroke(42, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        g.setColor(new Color(0x525D59));
        g.drawPolyline(xs, ys, xs.length);
    }

    private static void post(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x353E3C)); g.fillOval(x - 38, y - 38, 76, 76);
        g.setColor(new Color(0xA5ADA1)); g.fillOval(x - 28, y - 28, 56, 56);
    }

    private static void fragment(Graphics2D g, int x, int y, int xx, int yy) {
        g.setColor(new Color(0x88918A)); g.setStroke(new BasicStroke(10)); g.drawLine(x, y, xx, yy);
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(render(), "png", new File(args[0]));
    }
}
