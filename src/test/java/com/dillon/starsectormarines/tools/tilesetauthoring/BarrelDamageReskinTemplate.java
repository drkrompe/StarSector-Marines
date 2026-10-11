package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Code-owned round bodies and loose curved metal constrain the ImageGen reskin. */
public final class BarrelDamageReskinTemplate {
    public static final String[] IDS = {"doodad.industrial-drum-cluster", "main-stores.drums"};
    private BarrelDamageReskinTemplate() {}
    public static BufferedImage render(Path root, boolean reference) throws Exception {
        BufferedImage result = new BufferedImage(1024, reference ? 512 : 1024, BufferedImage.TYPE_INT_ARGB);
        for (int slot = 0; slot < IDS.length; slot++) {
            var def = TileRegistry.installed().doodad(IDS[slot]);
            var atlas = ImageIO.read(root.resolve("mod").resolve(def.sheetPath).toFile());
            int px = def.sourceCellPx;
            var source = atlas.getSubimage(def.col * px, def.row * px, px * def.footprintCellsX, px * def.footprintCellsY);
            var cell = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cell.createGraphics();
            g.drawImage(source, 24, 24, 464, 464, null);
            if (!reference) {
                for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
                    cell.setRGB(x, y, (cell.getRGB(x, y) >>> 24) > 32 ? 0xFF7D8481 : 0);
                g.setComposite(AlphaComposite.SrcAtop);
                g.setColor(new Color(0x303B3B)); g.setStroke(new BasicStroke(12));
                g.drawArc(95, 100, 110, 150, 40, 220);
                g.drawArc(290, 115, 100, 110, 100, 220);
                g.drawPolyline(new int[]{230, 250, 290, 260}, new int[]{300, 320, 350, 400}, 4);
            }
            g.dispose();
            Graphics2D out = result.createGraphics(); out.drawImage(cell, slot * 512, 0, null);
            if (!reference) {
                out.translate(slot * 512, 512);
                out.setColor(new Color(0x7D8481));
                for (int[] p : new int[][]{{70,85,-25},{275,80,30},{100,300,15},{300,315,-35}}) {
                    Graphics2D part = (Graphics2D) out.create();
                    part.rotate(Math.toRadians(p[2]), p[0]+50, p[1]+40);
                    part.fillArc(p[0], p[1], 110, 90, 15, 290);
                    part.setComposite(AlphaComposite.Clear); part.fillOval(p[0]+25,p[1]+20,60,48);
                    part.dispose();
                }
                out.fillOval(235,220,48,35); out.fillOval(80,225,35,25);
                out.fillPolygon(new int[]{360,410,398,370},new int[]{213,228,262,249},4);
                out.fillPolygon(new int[]{210,245,233},new int[]{395,410,445},3);
            }
            out.dispose();
        }
        return result;
    }
}
