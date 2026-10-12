package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Full placement frames; fractured furniture and loose yard stock retain clear ground gaps. */
public final class FurnishingDamageTemplate {
    public static final String[] IDS = {
            "doodad.industrial-cable-reel", "doodad.industrial-pallet-stack", "doodad.industrial-pipe-bundle",
            "doodad.military-bunk", "doodad.desk-2", "doodad.desk-1",
            "doodad.shelf-empty", "doodad.shelf-1", "doodad.shelf-2", "doodad.shelf-3"};
    private FurnishingDamageTemplate() {}
    public static BufferedImage render(Path root, int group, boolean reference) throws Exception {
        BufferedImage result = new BufferedImage(2720, reference ? 544 : 1088, BufferedImage.TYPE_INT_ARGB);
        Graphics2D out = result.createGraphics();
        for (int slot = 0; slot < 5; slot++) {
            int index = group * 5 + slot;
            var def = TileRegistry.installed().doodad(IDS[index]);
            var atlas = ImageIO.read(root.resolve("mod").resolve(def.sheetPath).toFile());
            int px = def.sourceCellPx;
            var source = atlas.getSubimage(def.col * px, def.row * px, px * def.footprintCellsX, px * def.footprintCellsY);
            BufferedImage cell = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cell.createGraphics();
            g.drawImage(source, 0, 0, 512, 512, null);
            if (!reference) {
                for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
                    cell.setRGB(x, y, (cell.getRGB(x, y) >>> 24) > 32 ? 0xFF7D8481 : 0);
                g.setComposite(AlphaComposite.SrcAtop);
                g.setColor(new Color(0x303B3B)); g.setStroke(new BasicStroke(13));
                g.drawPolyline(new int[]{180,220,208,270,286}, new int[]{80,156,215,270,365}, 5);
            }
            g.dispose(); out.drawImage(cell, slot * 544 + 16, 16, null);
            if (!reference) {
                Graphics2D debris = (Graphics2D) out.create();
                debris.translate(slot * 544 + 16, 560);
                debris.setColor(new Color(0x7D8481));
                if (index == 0) {
                    debris.setStroke(new BasicStroke(22));
                    debris.drawArc(80,100,140,140,20,230);
                    debris.drawArc(280,270,130,130,180,240);
                    slab(debris,320,125,105,32,-18); slab(debris,140,345,110,24,26);
                } else if (index == 1 || index == 2) {
                    slab(debris,165,155,190,27,15); slab(debris,355,195,125,25,-22);
                    slab(debris,160,337,150,25,-13); slab(debris,330,375,180,28,12);
                } else if (index == 3) {
                    debris.setStroke(new BasicStroke(16));
                    debris.drawPolyline(new int[]{120,108,210},new int[]{245,100,112},3);
                    debris.drawPolyline(new int[]{290,399,387},new int[]{392,406,280},3);
                    slab(debris,320,165,142,95,12); slab(debris,187,332,120,90,-12);
                } else {
                    slab(debris,165,156,168,67,-12); slab(debris,355,178,100,59,18);
                    slab(debris,170,348,128,60,15); slab(debris,335,354,148,53,-20);
                    if (index >= 7) { slab(debris,87,268,42,32,11); slab(debris,411,279,32,38,-14); }
                }
                debris.dispose();
            }
        }
        out.dispose(); return result;
    }
    private static void slab(Graphics2D g, int x, int y, int width, int height, int angle) {
        Graphics2D piece = (Graphics2D) g.create();
        piece.translate(x,y); piece.rotate(Math.toRadians(angle));
        piece.fillRect(-width/2,-height/2,width,height); piece.dispose();
    }
}
