package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.json.JSONObject;
import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Full-cell silhouettes taken from the existing seven rocks; the lower half specifies loose debris. */
public final class RockDamageReskinTemplate {
    private RockDamageReskinTemplate() {}
    public static BufferedImage render(Path root, boolean reference) throws Exception {
        var doc = new JSONObject(Files.readString(root.resolve("art-source/tilesets/nature-tiles.tileset-authoring.json")));
        BufferedImage raw = ImageIO.read(root.resolve(doc.getString("sheet")).toFile());
        BufferedImage result = new BufferedImage(2048, reference ? 1024 : 2048, BufferedImage.TYPE_INT_ARGB);
        int slot = 0;
        var entries = doc.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            var entry = entries.getJSONObject(i);
            if (!entry.getString("id").startsWith("nature.rock-")) continue;
            var r = entry.getJSONArray("rect");
            BufferedImage source = raw.getSubimage(r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3));
            BufferedImage cell = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cell.createGraphics();
            g.drawImage(source, 40, 40, 432, 432, null);
            if (!reference) {
                for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
                    cell.setRGB(x, y, (cell.getRGB(x, y) >>> 24) > 32 ? 0xFF77766F : 0);
                g.setComposite(AlphaComposite.SrcAtop);
                g.setColor(new Color(0x302F2D)); g.setStroke(new BasicStroke(12));
                g.drawPolyline(new int[]{125, 218, 242, 315, 369}, new int[]{70, 184, 268, 334, 443}, 5);
                g.drawPolyline(new int[]{242, 292, 404}, new int[]{268, 191, 139}, 3);
            }
            g.dispose();
            Graphics2D out = result.createGraphics();
            out.drawImage(cell, slot % 4 * 512, slot / 4 * 512, null);
            if (!reference) {
                out.translate(slot % 4 * 512, 1024 + slot / 4 * 512);
                for (int shard = 0; shard < 9; shard++) {
                    double angle = shard * Math.PI * 2 / 9 + slot * .17;
                    int radius = shard % 3 == 0 ? 65 : 145;
                    int cx = 256 + (int)(Math.cos(angle) * radius), cy = 256 + (int)(Math.sin(angle) * radius);
                    int size = 20 + (shard + slot) % 3 * 8;
                    out.setColor(new Color(shard % 2 == 0 ? 0x848078 : 0x676760));
                    out.fill(new Polygon(new int[]{cx-size, cx-size/2, cx+size, cx+size/2, cx-size/2},
                            new int[]{cy, cy-size, cy-size/2, cy+size, cy+size/2}, 5));
                }
            }
            out.dispose(); slot++;
        }
        return result;
    }
}
