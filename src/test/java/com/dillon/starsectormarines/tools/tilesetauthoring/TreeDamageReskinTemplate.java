package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

/** Damage stays inside the intact full-frame pivot and canopy envelope. */
public final class TreeDamageReskinTemplate {
    private TreeDamageReskinTemplate() {}
    public static BufferedImage render(boolean destroyed) {
        BufferedImage image = new BufferedImage(1536, 1024, BufferedImage.TYPE_INT_ARGB);
        for (TreeCanopy tree : TreeCanopy.values()) {
            int slot = tree.ordinal();
            double scale = 480d / Math.max(tree.width, tree.height);
            Graphics2D g = image.createGraphics();
            g.translate(slot % 3 * 512 + (512 - tree.width * scale) / 2,
                    slot / 3 * 512 + (512 - tree.height * scale) / 2);
            g.scale(scale, scale);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = tree.width * .5, cy = tree.height * .5;
            if (!destroyed) {
                int n = 0;
                for (var lobe : tree.lobes()) {
                    if (n++ == 0 || n % 4 == 0) continue;
                    g.setColor(new Color(tree.color).darker());
                    g.fill(new Ellipse2D.Double(lobe.x() - lobe.rx(), tree.height - lobe.y() - lobe.ry(),
                            lobe.rx() * 2, lobe.ry() * 2));
                }
            }
            for (int branch = 0; branch < 5; branch++) {
                double angle = branch * Math.PI * 2 / 5 + slot * .23;
                double ex = cx + Math.cos(angle) * tree.width * .29;
                double ey = cy + Math.sin(angle) * tree.height * .29;
                g.setStroke(new BasicStroke((float)(destroyed ? .14 : .10), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0x776049));
                double bx = destroyed ? cx + Math.cos(angle) * .40 : cx;
                double by = destroyed ? cy + Math.sin(angle) * .40 : cy;
                g.draw(new Line2D.Double(bx, by, ex, ey));
                g.setStroke(new BasicStroke(.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double((bx + ex) / 2, (by + ey) / 2,
                        ex + Math.sin(angle) * .3, ey - Math.cos(angle) * .3));
                if (destroyed) {
                    g.setColor(new Color(tree.color).darker());
                    g.fill(new Ellipse2D.Double(ex - .22, ey - .18, .44, .36));
                }
            }
            g.setColor(new Color(0xB79B70));
            g.fill(new Ellipse2D.Double(cx - .20, cy - .20, .40, .40));
            g.dispose();
        }
        return image;
    }
}
