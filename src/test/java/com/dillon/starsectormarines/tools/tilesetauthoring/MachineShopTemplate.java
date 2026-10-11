package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Four 4x2 workstations and three inset 2x2 shelf loads, at 64px/cell. */
public final class MachineShopTemplate {
    private MachineShopTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);
        for (int slot=0;slot<7;slot++) {
            Graphics2D g=image.createGraphics();
            g.translate(32+slot%3*336,32+slot/3*320);
            switch (slot) {
                case 0 -> {
                    base(g);rect(g,13,36,59,74,0x7B8976);
                    rect(g,75,46,159,17,0xB1B5A2);rect(g,75,82,159,14,0xB1B5A2);
                    rect(g,88,66,136,9,0x626D60);rect(g,133,57,46,30,0x8D9885);
                    rect(g,207,36,28,72,0x7B8976);rect(g,25,13,43,13,0xA9B39C);
                    g.setColor(new Color(0x3B4C40));g.fillOval(140,12,26,26);
                }
                case 1 -> {
                    base(g);rect(g,97,68,69,47,0x6B7868);rect(g,112,22,38,66,0xA0AA96);
                    rect(g,48,33,163,34,0x8D9985);rect(g,60,44,140,8,0x414F42);
                    rect(g,15,15,32,33,0x677861);rect(g,19,19,24,16,0x354B3D);
                }
                case 2 -> {
                    base(g);rect(g,12,12,232,103,0xA5A692);rect(g,21,22,125,73,0x5F7260);
                    rect(g,174,21,45,61,0x6D7867);rect(g,161,39,74,19,0x404D40);
                    rect(g,30,103,105,6,0xBBBCA5);
                }
                case 3 -> {
                    base(g);rect(g,12,12,232,103,0xA5A692);rect(g,21,20,128,78,0xC0BEA6);
                    rect(g,35,50,90,6,0x596458);rect(g,41,36,6,31,0x596458);
                    rect(g,183,25,40,38,0x4A6250);rect(g,175,74,56,15,0xC7C6AD);
                }
                case 4 -> {
                    for(int x:new int[]{20,70}) {
                        rect(g,x,22,38,82,0x6B7B66);
                        for(int y:new int[]{36,70}) {
                            g.setColor(new Color(0xB2B8A2));g.fillOval(x+7,y,24,24);
                            g.setColor(new Color(0x344237));g.fillOval(x+14,y+7,10,10);
                        }
                    }
                }
                case 5 -> {
                    for(int y:new int[]{23,70}) {
                        rect(g,20,y,88,35,0x758570);
                        rect(g,27,y+7,36,21,0x384D3F);rect(g,73,y+7,27,21,0xA6AE97);
                    }
                }
                case 6 -> {
                    for(int x:new int[]{26,49,72,95})rect(g,x,21,12,85,0xA6B09D);
                    rect(g,20,37,88,8,0x4E5F4F);rect(g,20,82,88,8,0x4E5F4F);
                }
            }
            g.dispose();
        }
        return image;
    }
    private static void base(Graphics2D g) {
        rect(g,4,4,248,120,0x334137);rect(g,9,9,238,110,0x63735F);
    }
    private static void rect(Graphics2D g,int x,int y,int w,int h,int rgb) {
        g.setColor(new Color(rgb));g.fillRect(x,y,w,h);
    }
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);Files.createDirectories(path.getParent());ImageIO.write(render(),"png",path.toFile());
    }
}
