package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Full-cell crate silhouettes and separated broken-panel guides for ImageGen. */
public final class CrateDamageReskinTemplate {
    public static final String[] IDS = {"doodad.box", "doodad.crate", "doodad.industrial-crate-stack"};
    private CrateDamageReskinTemplate() {}
    public static BufferedImage render(Path root, boolean reference) throws Exception {
        BufferedImage result = new BufferedImage(1536, reference ? 512 : 1024, BufferedImage.TYPE_INT_ARGB);
        for (int slot = 0; slot < IDS.length; slot++) {
            var def = TileRegistry.installed().doodad(IDS[slot]);
            var atlas = ImageIO.read(root.resolve("mod").resolve(def.sheetPath).toFile());
            int px = def.sourceCellPx;
            var source = atlas.getSubimage(def.col * px, def.row * px, px, px);
            var cell = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cell.createGraphics();
            g.drawImage(source, 32, 32, 448, 448, null);
            if (!reference) {
                for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
                    cell.setRGB(x, y, (cell.getRGB(x, y) >>> 24) > 32 ? 0xFF878477 : 0);
                g.setComposite(AlphaComposite.SrcAtop);
                g.setColor(new Color(0x343630)); g.setStroke(new BasicStroke(16));
                g.drawPolyline(new int[]{120, 198, 173, 310, 335}, new int[]{75, 184, 260, 342, 450}, 5);
                g.setComposite(AlphaComposite.Clear);
                g.fill(new Polygon(new int[]{440, 350, 388, 465}, new int[]{115, 181, 232, 251}, 4));
            }
            g.dispose();
            Graphics2D out = result.createGraphics(); out.drawImage(cell, slot * 512, 0, null);
            if (!reference) {
                out.translate(slot * 512, 512);
                for (int[] panel : new int[][]{{70,90,-20}, {240,90,23}, {335,230,-20}, {75,350,20}, {285,385,-18}}) {
                    int x = panel[0], y = panel[1];
                    Graphics2D fragment = (Graphics2D) out.create();
                    fragment.rotate(Math.toRadians(panel[2]), x + 55, y + 36);
                    fragment.setColor(new Color(0x878477));
                    fragment.fill(new Polygon(new int[]{x, x+110, x+98, x+60, x+12},
                            new int[]{y+12, y, y+62, y+47, y+72}, 5));
                    fragment.dispose();
                }
                out.setColor(new Color(0x626F74));
                out.fillRect(200, 235, 48, 45); out.fillRect(270, 310, 35, 52);
                out.fillRect(90, 250, 55, 36); out.fillRect(410, 120, 30, 31);
            }
            out.dispose();
        }
        return result;
    }
}
