package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Six isolated slots; equipment uses 48 guide pixels per gameplay cell. */
public final class LoadingBayTemplate {
    private LoadingBayTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        for (int slot=0;slot<6;slot++) {
            Graphics2D g=image.createGraphics();
            g.translate(24+slot%3*336,48+slot/3*352);
            switch(slot) {
                case 0 -> { // 4x2 receiving desk, broad uncluttered worktop.
                    box(g,4,4,184,88,0x40483E); box(g,11,10,170,75,0x9B9F90);
                    box(g,18,17,50,35,0x465649); box(g,23,21,40,26,0x748879);
                    box(g,20,62,47,12,0x616D5E);box(g,103,21,58,49,0xB7B7A7);
                    box(g,114,29,35,5,0x93998B);
                }
                case 1 -> { // 2x3 electric pallet tug, open paired forks at bottom.
                    box(g,5,12,86,62,0x374437); box(g,13,16,70,50,0x7D8974);
                    box(g,19,3,13,16,0x29372C);box(g,65,3,13,16,0x29372C);
                    box(g,17,68,18,72,0x8C9782);box(g,62,68,18,72,0x8C9782);
                    box(g,37,33,23,20,0x465640);
                    g.setColor(new Color(0xADB09E));g.setStroke(new BasicStroke(6));
                    g.drawLine(48,38,48,12);g.drawLine(34,10,62,10);
                }
                case 2 -> { // 6x2 short broad-roller transfer conveyor, continuous frame.
                    box(g,4,10,280,76,0x46513F);
                    for(int x=14;x<278;x+=24) box(g,x,18,17,60,0xA1A795);
                    box(g,4,6,280,8,0x66735E);box(g,4,82,280,8,0x66735E);
                    box(g,15,0,23,6,0x343F32);box(g,249,0,23,6,0x343F32);
                    box(g,15,90,23,6,0x343F32);box(g,249,90,23,6,0x343F32);
                }
                case 3 -> { // 2x2 shelf overlay: sealed insulated provisions tote.
                    box(g,14,16,68,64,0x68715F);box(g,20,21,56,54,0xA4AA96);
                    box(g,27,27,42,37,0x929C87);box(g,11,39,7,16,0x404F3E);
                    box(g,78,39,7,16,0x404F3E);box(g,39,69,18,6,0xCAD0B9);
                }
                case 4 -> { // 2x2 shelf overlay: orderly strapped cartons, no pallet baked in.
                    box(g,14,16,36,63,0xA3A08A);box(g,53,16,29,63,0x96977F);
                    box(g,23,16,6,63,0x6D7660);box(g,66,16,6,63,0x6D7660);
                    box(g,14,44,68,6,0x69725D);
                }
                case 5 -> { // 1x1 enlarged seamless rubberized staging-deck sample.
                    box(g,0,0,192,192,0x81877A);
                }
            }
            g.dispose();
        }
        return image;
    }
    private static void box(Graphics2D g,int x,int y,int w,int h,int rgb) {
        g.setColor(new Color(rgb));g.fillRect(x,y,w,h);
    }
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);Files.createDirectories(path.getParent());
        ImageIO.write(render(),"png",path.toFile());
    }
}
