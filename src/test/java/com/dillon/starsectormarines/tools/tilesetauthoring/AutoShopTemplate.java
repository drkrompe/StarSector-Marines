package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Whole-bay geometry avoids joining separately hallucinated borders and pipe ports. */
public final class AutoShopTemplate {
    private AutoShopTemplate() { }

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // One 5x7 bay, mouth south. Broad pads are flush floor-mounted lift tracks.
        rect(g,32,32,400,560,0x999B94);
        rect(g,48,48,368,9,0xD7B954);
        rect(g,48,48,9,518,0xD7B954); rect(g,407,48,9,518,0xD7B954);
        rect(g,48,557,68,9,0xD7B954); rect(g,348,557,68,9,0xD7B954);
        for (int x : new int[]{128,280}) {
            rect(g,x,144,56,320,0x444B4C);
            rect(g,x+5,150,46,308,0x616A6A);
            for(int y=172;y<460;y+=64) rect(g,x+5,y,46,3,0x444B4C);
        }
        for(int x:new int[]{66,370}) rect(g,x,280,28,64,0x963F37);
        // Quiet concrete variants: no per-cell border or checker pattern.
        for(int x:new int[]{480,656,832}) rect(g,x,32,160,160,0x999B94);
        rect(g,480,224,160,160,0x7B8C83); // painted pedestrian passage
        // The next two cells are isolated fixtures, not floor tiles.
        rect(g,674,250,124,104,0x343A3C);
        rect(g,678,254,116,94,0xA4483D);
        rect(g,678,254,116,22,0xB8BCB6);
        for(int y=284;y<342;y+=18) rect(g,690,y,92,4,0x66362F);
        rect(g,850,246,124,112,0x30434F);
        rect(g,856,252,112,100,0x48788C);
        rect(g,868,264,62,40,0x26373C);
        rect(g,872,268,54,32,0x839D8A);
        rect(g,866,320,86,16,0xABB1A9);
        // Complete closed service run, no connection touching any sprite boundary.
        rect(g,480,448,160,480,0x999B94);
        Path2D pipe = new Path2D.Double();
        pipe.moveTo(518,888); pipe.lineTo(554,888); pipe.quadTo(576,888,576,866);
        pipe.lineTo(576,498); pipe.moveTo(576,698); pipe.lineTo(610,698);
        g.setStroke(new BasicStroke(25,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x404B4D));g.draw(pipe);
        g.setStroke(new BasicStroke(15,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xB3B5A6));g.draw(pipe);
        for(int y:new int[]{540,620,760,836}) rect(g,564,y,24,8,0x636A67);
        for(int[] p:new int[][]{{506,876},{564,478},{598,686}}) {
            rect(g,p[0],p[1],24,24,0x425E6A);
            rect(g,p[0]+7,p[1]+7,10,10,0xB77846);
        }
        // A substantial red lift column, top-down and confined to one cell.
        rect(g,752,508,72,106,0x30383A);
        rect(g,762,512,52,96,0xA6483D);
        rect(g,770,520,36,78,0xC26752);
        rect(g,730,544,36,26,0x7E8984);
        // Coarse slotted drain: six strong openings, no fine wire lattice.
        rect(g,720,740,128,72,0x454E4E);
        rect(g,724,744,120,64,0x858C85);
        for(int x=734;x<842;x+=18) rect(g,x,753,9,46,0x323B3C);
        g.dispose(); return image;
    }

    private static void rect(Graphics2D g,int x,int y,int w,int h,int rgb) {
        g.setColor(new Color(rgb));g.fillRect(x,y,w,h);
    }
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);Files.createDirectories(path.getParent());
        ImageIO.write(render(),"png",path.toFile());
    }
}
