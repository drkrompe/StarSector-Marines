package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/** Damage inherits the intact full-cell silhouette; transparent breaches are authored before the reskin. */
public final class SandbagDamageReskinTemplate {
    private SandbagDamageReskinTemplate() {}

    public static BufferedImage render() {
        int pitch = SandbagReskinTemplate.PITCH;
        int margin = SandbagReskinTemplate.MARGIN;
        int cellSize = SandbagReskinTemplate.CELL;
        BufferedImage intact = SandbagReskinTemplate.render();
        BufferedImage result = new BufferedImage(pitch * 2, pitch * 2, BufferedImage.TYPE_INT_ARGB);
        for (int state = 0; state < 2; state++) for (int shape = 0; shape < 2; shape++) {
            BufferedImage cell = new BufferedImage(cellSize, cellSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cell.createGraphics();
            g.drawImage(intact, 0, 0, cellSize, cellSize, shape * pitch + margin, margin,
                    shape * pitch + margin + cellSize, margin + cellSize, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.Clear);
            if (state == 0) {
                g.fillOval(274, 82, 85, 96);
                if (shape == 1) g.fillOval(82, 278, 94, 76);
            } else if (shape == 0) {
                g.fillRect(208, 0, 224, cellSize);
            } else {
                // Elbow blown away, leaving roots toward east and south.
                g.fillRect(0, 0, 280, 280);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(0xC7B383));
            if (state == 0) {
                g.fillOval(271, 136, 110, 62);
                if (shape == 1) g.fillOval(127, 276, 64, 112);
            } else {
                g.fillOval(438, 138, 120, 61);
                if (shape == 0) g.fillOval(95, 133, 106, 68);
                else g.fillOval(130, 432, 68, 122);
            }
            g.dispose();
            Graphics2D out = result.createGraphics();
            out.drawImage(cell, shape * pitch + margin, state * pitch + margin, null);
            out.dispose();
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(render(), "png", new File(args[0]));
    }
}
