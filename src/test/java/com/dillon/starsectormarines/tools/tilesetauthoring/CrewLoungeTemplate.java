package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Twelve overhead lounge components at 64px/cell, on a 336x240 slot pitch. */
public final class CrewLoungeTemplate {
    private CrewLoungeTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);
        for (int slot = 0; slot < 12; slot++) {
            Graphics2D g = image.createGraphics();
            g.translate(48 + slot % 3 * 336, 32 + slot / 3 * 240);
            switch (slot) {
                case 0, 1 -> {
                    rect(g,4,4,184,56,0x414C47);
                    rect(g,10,slot==0 ? 47 : 6,172,11,0x647264);
                    for (int x : new int[]{16,70,124}) rect(g,x,slot==0 ? 10 : 21,50,31,0x879383);
                    rect(g,7,10,8,44,0x647264); rect(g,177,10,8,44,0x647264);
                }
                case 2 -> { box(g,128,64); rect(g,10,10,108,44,0x938875); }
                case 3 -> { // inset overlay on the 2x1 coffee table
                    rect(g,24,18,30,27,0xB8B49F); rect(g,61,23,26,21,0x707D6C);
                    g.setColor(new Color(0xC4C1AF)); g.fillOval(96,24,12,12);
                }
                case 4 -> { box(g,128,128); rect(g,13,13,102,102,0x60796A); }
                case 5 -> { // inset card/checker game overlay, no table beneath
                    for (int y=0;y<4;y++) for (int x=0;x<4;x++)
                        rect(g,40+x*12,40+y*12,12,12,(x+y)%2==0?0xAEB2A0:0x505E52);
                    rect(g,24,24,14,19,0xD1CBB6); rect(g,92,85,14,19,0xD1CBB6);
                }
                case 6 -> {
                    box(g,192,128); rect(g,16,16,160,96,0x576E61);
                    for(int x:new int[]{17,96,175}) for(int y:new int[]{17,111}) {
                        g.setColor(new Color(0x293A30)); g.fillOval(x-5,y-5,10,10);
                    }
                    rect(g,46,30,4,67,0xABA48A);
                    g.setColor(new Color(0xD5D2BD));g.fillOval(116,60,7,7);
                }
                case 7 -> { g.setColor(new Color(0x444F47));g.fillOval(8,8,48,48);
                    g.setColor(new Color(0x879383));g.fillOval(13,13,38,38); }
                case 8 -> {box(g,192,64);rect(g,16,10,160,37,0x2F403C);rect(g,30,50,132,5,0x737A6E);}
                case 9 -> {box(g,64,192);rect(g,12,13,40,65,0x535F54);
                    rect(g,17,20,30,28,0x263C33);rect(g,14,100,36,32,0xD0CAB5);}
                case 10 -> {box(g,64,192);for(int y=12;y<168;y+=32)
                    for(int x=12;x<50;x+=10)rect(g,x,y,8,25,((x+y)%3==0)?0xA39C85:0x738172);}
                case 11 -> {box(g,64,64);g.setColor(new Color(0x607459));g.fillOval(11,11,42,42);}
            }
            g.dispose();
        }
        return image;
    }
    private static void box(Graphics2D g,int w,int h){rect(g,4,4,w-8,h-8,0x444F49);rect(g,8,8,w-16,h-16,0xA09F8E);}
    private static void rect(Graphics2D g,int x,int y,int w,int h,int rgb){g.setColor(new Color(rgb));g.fillRect(x,y,w,h);}
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);Files.createDirectories(path.getParent());ImageIO.write(render(),"png",path.toFile());
    }
}
