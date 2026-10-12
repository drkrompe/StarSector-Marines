package com.dillon.starsectormarines.tools.tilesetauthoring;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Full-cell guides for four shallow blast scars in earth, paving and metal. */
public final class TerrainBlastTemplate {
    public static final int PITCH = 384;
    private TerrainBlastTemplate() {}

    public static BufferedImage render() {
        BufferedImage result = new BufferedImage(4 * PITCH, 3 * PITCH, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {0x77624B, 0x74777A, 0x626F79};
        for (int row = 0; row < 3; row++) for (int variant = 0; variant < 4; variant++) {
            Graphics2D g = result.createGraphics();
            g.translate(variant * PITCH, row * PITCH);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x343332));
            if (variant == 0) {
                g.fillPolygon(new int[]{65,99,157,192,255,313,279,228,181,128,82},
                        new int[]{135,100,121,91,115,176,214,197,243,211,192},11);
            } else if (variant == 1) {
                g.setStroke(new BasicStroke(22,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[]{79,114,160,184,235,278},new int[]{270,224,213,159,147,81},6);
                g.drawPolyline(new int[]{162,127,72},new int[]{209,164,146},3);
            } else if (variant == 2) {
                g.fillPolygon(new int[]{111,170,229,303,282,208,160,90},
                        new int[]{193,138,168,159,230,262,234,258},8);
                g.fillPolygon(new int[]{72,99,148,123},new int[]{75,64,119,146},4);
            } else {
                g.setStroke(new BasicStroke(12,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[]{70,120,152,192,217,286},new int[]{107,132,110,161,187,222},6);
                g.drawPolyline(new int[]{119,142,188,238,296},new int[]{244,217,239,282,267},5);
            }
            g.setColor(new Color(colors[row]));
            for (int i = 0; i < 11; i++) {
                int cx = 62 + Math.floorMod(i*73+variant*39,258);
                int cy = 62 + Math.floorMod(i*113+variant*59,258);
                g.fillPolygon(new int[]{cx-6,cx+3,cx+8,cx},new int[]{cy,cy-5,cy+2,cy+6},4);
            }
            g.dispose();
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        ImageIO.write(render(),"png",Path.of(args[0]).toFile());
    }
}
