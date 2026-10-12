package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Geometry-only ImageGen input: cap rectangles and exposed-edge bevels. */
public final class WallReskinTemplate {
    public static final int PANEL = 1024, PITCH = 256, MARGIN = 16, CELL = 224, BEVEL = 32;
    private WallReskinTemplate() {}

    public static BufferedImage render() {
        return render(new int[]{0x755139, 0x777569, 0x454C31});
    }

    public static BufferedImage render(int[] colors) {
        BufferedImage image = new BufferedImage(PANEL * colors.length, PANEL, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x17191C));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int family = 0; family < colors.length; family++) {
            Color cap = new Color(colors[family]);
            for (int mask = 0; mask < 16; mask++) {
                boolean n = (mask & 1) != 0, e = (mask & 2) != 0;
                boolean s = (mask & 4) != 0, w = (mask & 8) != 0;
                int[] slot = GridLayout.WALL_FACES_4X4.resolve(0, 0, n, s, e, w);
                int x = family * PANEL + slot[0] * PITCH + MARGIN;
                int y = slot[1] * PITCH + MARGIN;
                g.setColor(cap);
                g.fillRect(x, y, CELL, CELL);
                g.setColor(cap.brighter());
                if (n) g.fillRect(x, y, CELL, BEVEL);
                if (e) g.fillRect(x + CELL - BEVEL, y, BEVEL, CELL);
                if (s) g.fillRect(x, y + CELL - BEVEL, CELL, BEVEL);
                if (w) g.fillRect(x, y, BEVEL, CELL);
                // Thin machined/chamfered transition on the INNER edge of each rim.
                g.setColor(cap.darker().darker());
                int l = w ? BEVEL : 0, r = e ? BEVEL : 0;
                int t = n ? BEVEL : 0, b = s ? BEVEL : 0;
                if (n) g.fillRect(x + l, y + t, CELL - l - r, 4);
                if (s) g.fillRect(x + l, y + CELL - b - 4, CELL - l - r, 4);
                if (w) g.fillRect(x + l, y + t, 4, CELL - t - b);
                if (e) g.fillRect(x + CELL - r - 4, y + t, 4, CELL - t - b);
            }
        }
        g.dispose();
        return image;
    }
}
