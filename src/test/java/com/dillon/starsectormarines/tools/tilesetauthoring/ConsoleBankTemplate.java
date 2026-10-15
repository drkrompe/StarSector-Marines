package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Six canonical one-cell console bodies. Connections retain the entire 256px canvas. */
public final class ConsoleBankTemplate {
    public static final int CELL = 256, PITCH = 384, MARGIN = 64;
    public static final String[] PIECES = {
            "straight", "straight-alt", "inside-corner", "outside-corner", "end-left", "end-right"
    };
    private ConsoleBankTemplate() {}

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1152, 768, BufferedImage.TYPE_INT_ARGB);
        for (int slot = 0; slot < PIECES.length; slot++) {
            Graphics2D g = image.createGraphics();
            g.translate(MARGIN + slot % 3 * PITCH, MARGIN + slot / 3 * PITCH);
            g.clipRect(0, 0, CELL, CELL);
            if (slot == 2 || slot == 3) corner(g, slot == 3);
            else straight(g, slot);
            g.dispose();
        }
        return image;
    }

    private static void straight(Graphics2D g, int slot) {
        int left = slot == 4 ? 24 : 0, right = slot == 5 ? 232 : 256;
        rect(g, left, 32, right-left, 192, 0x26343D);
        rect(g, left, 40, right-left, 176, 0x677777);
        rect(g, left, 48, right-left, 18, 0x3B4D56);
        rect(g, left, 194, right-left, 14, 0x94A09B);
        if (slot == 1) {
            screen(g, 26, 76, 88, 69); screen(g, 134, 76, 96, 69);
        } else screen(g, Math.max(32, left+12), 76, Math.min(192, right-left-24), 69);
        rect(g, 66, 164, 124, 20, 0x343E43);
        for (int x = 74; x < 184; x += 18) rect(g, x, 169, 11, 4, 0x9AADA5);
        if (slot == 4) rect(g, 24, 40, 12, 176, 0x35454C);
        if (slot == 5) rect(g, 220, 40, 12, 176, 0x35454C);
    }

    private static void corner(Graphics2D g, boolean outward) {
        int[] x = {64,256,256,224,224,32,32};
        int[] y = {32,32,224,224,256,256,64};
        g.setColor(new Color(0x677777)); g.fill(new Polygon(x,y,x.length));
        // The elbow has east and south ports, both spanning 32..224.
        if (!outward) {
            rect(g, 64, 40, 192, 26, 0x3B4D56);
            rect(g, 40, 64, 26, 192, 0x3B4D56);
            screen(g, 94, 78, 146, 58);
            screen(g, 78, 148, 58, 92);
            rect(g, 154, 158, 82, 25, 0x343E43);
            rect(g, 158, 192, 25, 48, 0x343E43);
        } else {
            rect(g, 196, 168, 60, 32, 0x3B4D56);
            rect(g, 168, 196, 32, 60, 0x3B4D56);
            screen(g, 118, 112, 120, 56);
            screen(g, 112, 174, 56, 66);
            rect(g, 92, 66, 144, 24, 0x343E43);
            rect(g, 66, 104, 24, 132, 0x343E43);
        }
    }

    private static void screen(Graphics2D g, int x, int y, int w, int h) {
        rect(g,x-5,y-5,w+10,h+10,0x26343D);
        rect(g,x,y,w,h,0x488B91);
        rect(g,x+8,y+8,w-16,3,0xB1E2C8);
    }

    private static void rect(Graphics2D g, int x, int y, int w, int h, int rgb) {
        g.setColor(new Color(rgb)); g.fillRect(x,y,w,h);
    }

    /** Connected neighborhoods prevent the material pass from capping every tile separately. */
    public static BufferedImage connectedGuide() {
        BufferedImage result = new BufferedImage(1536, 1024, BufferedImage.TYPE_INT_ARGB);
        BufferedImage tiles = render();
        Graphics2D g = result.createGraphics();
        place(g, tiles, 96,96,0,0,2,0);
        place(g, tiles, 96,96,4,0,2,1);
        for (int x=1;x<4;x++) place(g,tiles,96,96,x,0,x%2,0);
        for (int y=1;y<3;y++) {
            place(g,tiles,96,96,0,y,y%2,3);
            place(g,tiles,96,96,4,y,y%2,1);
        }
        place(g,tiles,96,96,0,3,4,3);
        place(g,tiles,96,96,4,3,5,1);
        place(g,tiles,896,96,0,0,3,0);
        place(g,tiles,896,96,3,0,3,1);
        place(g,tiles,896,96,0,3,3,3);
        place(g,tiles,896,96,3,3,3,2);
        for (int i=1;i<3;i++) {
            place(g,tiles,896,96,i,0,i%2,2);
            place(g,tiles,896,96,i,3,i%2,0);
            place(g,tiles,896,96,0,i,i%2,1);
            place(g,tiles,896,96,3,i,i%2,3);
        }
        g.dispose();
        return result;
    }

    private static void place(Graphics2D target, BufferedImage tiles, int ox, int oy,
                              int x, int y, int slot, int turns) {
        Graphics2D g=(Graphics2D)target.create();
        g.translate(ox+x*128+64,oy+y*128+64);
        g.rotate(turns*Math.PI/2);
        int sx=MARGIN+slot%3*PITCH, sy=MARGIN+slot/3*PITCH;
        g.drawImage(tiles,-64,-64,64,64,sx,sy,sx+CELL,sy+CELL,null);
        g.dispose();
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]);
        Files.createDirectories(path.getParent());
        ImageIO.write(args.length > 1 ? connectedGuide() : render(), "png", path.toFile());
    }
}
