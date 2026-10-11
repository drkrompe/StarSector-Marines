package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Six overhead bridge components, 64px/cell, on a 480x304 slot pitch. */
public final class ShipBridgeTemplate {
    private ShipBridgeTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);
        for (int slot=0;slot<6;slot++) {
            Graphics2D g=image.createGraphics();
            g.translate(48+slot%2*480,40+slot/2*304);
            switch(slot) {
                case 0 -> {
                    box(g,192,192);rect(g,22,22,148,148,0x34443F);
                    g.setColor(new Color(0x81998A));g.drawOval(49,49,94,94);
                    g.drawLine(38,96,154,96);g.drawLine(96,38,96,154);
                    rect(g,88,87,16,18,0xBAC1A8);
                    for(int x=28;x<164;x+=28)rect(g,x,175,18,7,0x8B9585);
                }
                case 1,2 -> {
                    box(g,192,64);
                    int screenY=slot==1?24:10, controlsY=slot==1?10:48;
                    for(int x:new int[]{12,70,128}) {
                        rect(g,x,screenY,52,27,0x344D42);
                        rect(g,x+8,controlsY,36,6,0xADB09B);
                    }
                }
                case 3,4 -> {
                    box(g,64,192);
                    int screenX=slot==3?10:27, controlsX=slot==3?48:10;
                    for(int y:new int[]{14,104}) {
                        rect(g,screenX,y,27,72,0x344D42);
                        rect(g,controlsX,y+12,6,48,0xADB09B);
                    }
                }
                case 5 -> {
                    box(g,64,128);
                    for(int y:new int[]{13,67}) {rect(g,12,y,40,46,0x69746A);rect(g,20,y+12,24,5,0x343F38);}
                }
            }
            g.dispose();
        }
        return image;
    }
    private static void box(Graphics2D g,int w,int h) {
        rect(g,4,4,w-8,h-8,0x333E39);rect(g,8,8,w-16,h-16,0x7F887A);
    }
    private static void rect(Graphics2D g,int x,int y,int w,int h,int rgb) {
        g.setColor(new Color(rgb));g.fillRect(x,y,w,h);
    }
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);Files.createDirectories(path.getParent());ImageIO.write(render(),"png",path.toFile());
    }
}
