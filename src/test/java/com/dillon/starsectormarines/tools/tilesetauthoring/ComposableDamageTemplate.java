package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Full placement frames and sparse debris guides for the composable furnishing damage reskin. */
public final class ComposableDamageTemplate {
    public static void main(String[] args) throws Exception {
        Path manifest = Path.of(args[0]);
        int columns = Integer.parseInt(args[1]), pitch = 384;
        List<String> lines = Files.readAllLines(manifest);
        int rows = (lines.size() + columns - 1) / columns;
        BufferedImage reference = new BufferedImage(columns * pitch, rows * 2 * pitch, BufferedImage.TYPE_INT_ARGB);
        BufferedImage guide = new BufferedImage(reference.getWidth(), reference.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D ref = reference.createGraphics(), out = guide.createGraphics();
        List<String> cuts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String[] v = lines.get(i).split("\t");
            int sx = Integer.parseInt(v[2]), sy = Integer.parseInt(v[3]);
            int sw = Integer.parseInt(v[4]), sh = Integer.parseInt(v[5]);
            int fw = Integer.parseInt(v[6]), fh = Integer.parseInt(v[7]);
            int scale = 320 / Math.max(fw, fh), width = fw * scale, height = fh * scale;
            int x = (i % columns) * pitch + (pitch - width) / 2;
            int y = (i / columns) * pitch * 2 + (pitch - height) / 2;
            BufferedImage atlas = ImageIO.read(Path.of(v[1]).toFile());
            BufferedImage cell = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D cg = cell.createGraphics();
            cg.drawImage(atlas, 0, 0, width, height, sx, sy, sx + sw, sy + sh, null);
            ref.drawImage(cell, x, y, null); ref.drawImage(cell, x, y + pitch, null);
            for (int py = 0; py < height; py++) for (int px = 0; px < width; px++)
                cell.setRGB(px, py, (cell.getRGB(px, py) >>> 24) > 32 ? 0xff808888 : 0);
            cg.setComposite(AlphaComposite.SrcAtop); cg.setColor(new Color(0x303b3b));
            cg.setStroke(new BasicStroke(Math.max(3, width / 30f)));
            cg.drawPolyline(new int[]{width/3,width/2,width*2/5,width*3/5},
                    new int[]{height/6,height/3,height*3/5,height*4/5},4);
            cg.dispose(); out.drawImage(cell, x, y, null);
            // Sparse low shards remain inside the same full footprint. No extra floor or pedestal.
            Graphics2D debris = (Graphics2D) out.create(); debris.translate(x,y+pitch);
            debris.scale(width / 100.0,height / 100.0);
            debris.setColor(new Color(0x687171));
            debris.fillPolygon(new int[]{12,38,34,9},new int[]{14,20,37,31},4);
            debris.fillPolygon(new int[]{60,87,88,67},new int[]{9,16,34,29},4);
            debris.fillPolygon(new int[]{35,64,70,44},new int[]{48,43,63,69},4);
            debris.fillPolygon(new int[]{13,29,34,16},new int[]{72,68,89,91},4);
            debris.fillPolygon(new int[]{75,91,88,72},new int[]{74,68,91,87},4);
            debris.dispose();
            cuts.add(v[0]+"\t"+x+"\t"+y+"\t"+width+"\t"+height+"\t"+pitch+"\t"+fw+"\t"+fh+"\t"+v[8]+"\t"+v[9]);
        }
        ref.dispose(); out.dispose();
        String base = manifest.toString().replace(".tsv", "");
        ImageIO.write(reference,"png",Path.of(base+"-reference.png").toFile());
        ImageIO.write(guide,"png",Path.of(base+"-guide.png").toFile());
        Files.write(Path.of(base+"-cuts.tsv"),cuts);
    }
}
