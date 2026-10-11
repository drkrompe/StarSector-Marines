package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Eight isolated components on a two-column sheet, 64 guide pixels per cell. */
public final class BriefingRoomTemplate {
    private BriefingRoomTemplate() {}
    public static BufferedImage render() {
        BufferedImage image=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);
        for(int slot=0;slot<8;slot++) {
            Graphics2D g=image.createGraphics();g.translate(32+slot%2*512,32+slot/2*240);
            switch(slot) {
                case 0 -> { // 6x2 central table, broad quiet rim around inset map.
                    g.setColor(new Color(0x424B43));g.fillRoundRect(4,4,376,120,24,24);
                    g.setColor(new Color(0xA0A394));g.fillRoundRect(11,11,362,106,18,18);
                    rect(g,60,28,264,72,0x616F60);
                }
                case 1 -> { // 6x2 transparent tactical overlay, inset on the table.
                    rect(g,68,34,248,60,0x45594D);
                    g.setStroke(new BasicStroke(3));g.setColor(new Color(0x839482));
                    g.drawPolyline(new int[]{80,122,148,192,220,258,300},new int[]{70,49,61,46,76,69,83},7);
                    rect(g,111,68,13,10,0xBAC2A8);rect(g,248,44,17,11,0xBAC2A8);
                }
                case 2 -> { // 4x1 inclined tactical display bank, screen visible overhead.
                    rect(g,4,4,248,56,0x38463E);rect(g,12,9,232,40,0x6C7C69);
                    rect(g,19,14,218,29,0x344D40);
                    g.setColor(new Color(0x96AA8D));g.setStroke(new BasicStroke(2));
                    g.drawPolyline(new int[]{35,65,92,135,167,205,224},new int[]{34,24,30,20,34,23,29},7);
                    rect(g,50,52,156,4,0xA0A694);
                }
                case 3 -> { // 2x1 presenter's lectern.
                    rect(g,4,4,120,56,0x444F43);rect(g,12,9,104,45,0x9CA28D);
                    rect(g,20,15,48,29,0x3B5545);rect(g,81,15,24,29,0xBABCA7);
                }
                default -> { // One identical armchair, four canonical backrest directions.
                    int turn=switch(slot){case 4->0;case 5->2;case 6->3;default->1;};
                    g.rotate(turn*Math.PI/2,32,32);
                    rect(g,8,7,48,49,0x39453D);rect(g,15,17,34,34,0x7B8777);
                    rect(g,9,7,46,11,0x536250);rect(g,8,19,8,32,0x596952);
                    rect(g,48,19,8,32,0x596952);
                }
            }
            g.dispose();
        }
        return image;
    }
    private static void rect(Graphics2D g,int x,int y,int w,int h,int rgb) {
        g.setColor(new Color(rgb));g.fillRect(x,y,w,h);
    }
    public static void main(String[] args) throws Exception {
        Path p=Path.of(args[0]);Files.createDirectories(p.getParent());ImageIO.write(render(),"png",p.toFile());
    }
}
