package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Exact top-down footprints: four window edges and four open-door facings per material. */
public final class OpeningReskinTemplate {
    public static final int PITCH = 384, MARGIN = 32, CELL = 320;
    private OpeningReskinTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(PITCH * 6, PITCH * 4, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0x986A45, 0xAAA694, 0x697044};
        for (int family = 0; family < colors.length; family++) {
            for (int kind = 0; kind < 2; kind++) for (int facing = 0; facing < 4; facing++) {
                Graphics2D g = image.createGraphics();
                g.translate((family * 2 + facing % 2) * PITCH + MARGIN,
                        (kind * 2 + facing / 2) * PITCH + MARGIN);
                g.rotate(facing * Math.PI / 2, CELL / 2.0, CELL / 2.0);
                Color trim = new Color(colors[family]);
                if (kind == 0) {
                    // Window stays entirely on the owning side of the boundary.
                    g.setColor(trim.darker().darker()); g.fillRect(0, 0, CELL, 96);
                    g.setColor(trim); g.fillRect(0, 0, CELL, 20); g.fillRect(0, 76, CELL, 20);
                    g.fillRect(0, 20, 32, 56); g.fillRect(288, 20, 32, 56);
                    g.setColor(new Color(0x567778)); g.fillRect(36, 28, 248, 40);
                    g.setColor(trim.darker()); g.fillRect(154, 20, 12, 56);
                } else {
                    // Low threshold across the passage; jambs are attached to the side walls.
                    g.setColor(trim.darker()); g.fillRect(44, 140, 232, 40);
                    g.setColor(trim.darker().darker());
                    g.fillRect(0, 0, 44, CELL); g.fillRect(276, 0, 44, CELL);
                    g.setColor(trim); g.fillRect(0, 0, 32, CELL); g.fillRect(288, 0, 32, CELL);
                    g.setColor(trim.brighter());
                    g.fillRect(0, 0, 32, 20); g.fillRect(288, 0, 32, 20);
                    g.fillRect(0, 300, 32, 20); g.fillRect(288, 300, 32, 20);
                }
                g.dispose();
            }
        }
        return image;
    }
}
