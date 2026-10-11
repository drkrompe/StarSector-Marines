package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** True overhead canopy bodies. The empty full-frame margin is part of the placement contract. */
public final class TreeReskinTemplate {
    public static final int PITCH = 512, FRAME = 480;
    private TreeReskinTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(PITCH * 3, PITCH * 2, BufferedImage.TYPE_INT_ARGB);
        for (TreeCanopy tree : TreeCanopy.values()) {
            int slot = tree.ordinal();
            double scale = FRAME / (double)Math.max(tree.width, tree.height);
            Graphics2D g = image.createGraphics();
            g.translate(slot % 3 * PITCH + (PITCH - tree.width * scale) / 2,
                    slot / 3 * PITCH + (PITCH - tree.height * scale) / 2);
            g.scale(scale, scale);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int n = 0;
            for (var lobe : tree.lobes()) {
                Color base = new Color(tree.color);
                g.setColor(n++ == 0 ? base.darker() : (n % 3 == 0 ? base.brighter() : base));
                g.fill(new Ellipse2D.Double(lobe.x() - lobe.rx(), tree.height - lobe.y() - lobe.ry(),
                        lobe.rx() * 2, lobe.ry() * 2));
            }
            g.setColor(new Color(tree.color).brighter());
            g.fill(new Ellipse2D.Double(tree.width * .32, tree.height * .32, tree.width * .36, tree.height * .36));
            g.dispose();
        }
        return image;
    }
    public static void main(String[] args) throws Exception {
        ImageIO.write(render(), "png", Path.of(args[0]).toFile());
    }
}
