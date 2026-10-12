package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Full-frame silhouettes, broken roots and separated panels constrain the reskin. */
public final class CompoundPropDamageTemplate {
    public static final String[] IDS = {
            "doodad.industrial-generator", "doodad.military-radar-dish",
            "doodad.industrial-machine-tool", "doodad.industrial-fluid-tank",
            "doodad.industrial-control-console", "doodad.military-command-console",
            "doodad.military-tactical-table", "doodad.office-workstation-bank"};
    private CompoundPropDamageTemplate() {}

    public static BufferedImage render(Path root, int group, boolean reference) throws Exception {
        BufferedImage result = new BufferedImage(2048, reference ? 512 : 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D out = result.createGraphics();
        for (int slot = 0; slot < 4; slot++) {
            var def = TileRegistry.installed().doodad(IDS[group * 4 + slot]);
            var atlas = ImageIO.read(root.resolve("mod").resolve(def.sheetPath).toFile());
            int px = def.sourceCellPx;
            var source = atlas.getSubimage(def.col * px, def.row * px,
                    px * def.footprintCellsX, px * def.footprintCellsY);
            var cell = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cell.createGraphics();
            // Full source rectangle, including its original transparent border and pivot.
            g.drawImage(source, 0, 0, 512, 512, null);
            if (!reference) {
                for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
                    cell.setRGB(x, y, (cell.getRGB(x, y) >>> 24) > 32 ? 0xFF7D8481 : 0);
                g.setComposite(AlphaComposite.SrcAtop);
                g.setColor(new Color(0x303B3B)); g.setStroke(new BasicStroke(13));
                g.drawPolyline(new int[]{180,220,208,270,286}, new int[]{80,156,215,270,365}, 5);
                g.drawPolyline(new int[]{350,300,336}, new int[]{135,200,290}, 3);
            }
            g.dispose(); out.drawImage(cell, slot * 512, 0, null);
            if (!reference) {
                Graphics2D debris = (Graphics2D) out.create();
                debris.translate(slot * 512, 512);
                // A low central base surrounded by split, tilted panels; no floor backing.
                debris.setColor(new Color(0x5B6262));
                debris.fillPolygon(new int[]{150,310,335,230,155}, new int[]{275,253,340,379,341}, 5);
                debris.setColor(new Color(0x7D8481));
                debris.fillPolygon(new int[]{78,177,167,101}, new int[]{111,130,228,205}, 4);
                debris.fillPolygon(new int[]{250,350,389,283}, new int[]{99,80,172,200}, 4);
                debris.fillPolygon(new int[]{360,423,413,364}, new int[]{269,286,369,341}, 4);
                debris.fillPolygon(new int[]{75,125,153,100}, new int[]{355,368,420,409}, 4);
                debris.fillRect(203,211,26,29);
                debris.dispose();
            }
        }
        out.dispose(); return result;
    }
}
