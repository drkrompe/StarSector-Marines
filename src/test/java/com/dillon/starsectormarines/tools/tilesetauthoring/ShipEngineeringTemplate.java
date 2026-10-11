package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Complete machinery skids: pipe circuits terminate inside the same authored assembly. */
public final class ShipEngineeringTemplate {
    private ShipEngineeringTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.translate(40,40);
        base(g,864,288);
        pipe(g,new int[]{115,115,744,744},new int[]{128,40,40,128});
        pipe(g,new int[]{115,115,744,744},new int[]{150,248,248,150});
        pipe(g,new int[]{440,440},new int[]{40,248});
        rect(g,28,75,184,138,0x737E70);
        oval(g,48,76,144,136,0xA0A494);oval(g,77,102,86,84,0x566355);
        rect(g,304,73,280,142,0x657264);
        g.setColor(new Color(0x939D8C));g.fillRoundRect(317,85,254,118,48,48);
        for(int x=355;x<550;x+=48)rect(g,x,87,12,114,0x576356);
        rect(g,670,77,156,134,0x7C897A);
        for(int y=94;y<202;y+=28)rect(g,682,y,132,12,0x505F53);
        g.translate(-40,-40);
        for(int slot=0;slot<3;slot++) {
            g.translate(40+slot*320,384);
            base(g,288,192);
            pipe(g,new int[]{67,67,226,226,67},new int[]{85,25,25,165,165});
            pipe(g,new int[]{67,67},new int[]{165,85});
            if(slot==0) {
                rect(g,28,51,80,91,0x879281);oval(g,39,64,58,58,0xB1B3A1);
                rect(g,163,47,91,101,0x6C7D6C);
                for(int y=59;y<140;y+=22)rect(g,174,y,69,9,0xA8AE9D);
            } else if(slot==1) {
                rect(g,27,48,89,98,0x7C8877);oval(g,39,59,65,65,0x465648);
                rect(g,163,46,92,103,0x8C9685);
                rect(g,174,60,70,20,0x455647);rect(g,174,112,70,20,0x455647);
            } else {
                rect(g,28,49,226,94,0x7D8877);
                for(int x=45;x<232;x+=46)rect(g,x,58,25,76,0xADB09E);
            }
            g.translate(-40-slot*320,-384);
        }
        g.translate(40,688);base(g,144,48);
        for(int x=10;x<130;x+=44) {rect(g,x,8,36,22,0x354A40);rect(g,x+6,34,24,5,0xACB19B);}
        g.translate(-40,-688);
        g.translate(360,688);base(g,192,96);rect(g,10,10,172,76,0x9D9F8C);
        rect(g,22,24,68,48,0x566654);rect(g,117,20,48,53,0x6F7A6B);rect(g,108,37,66,15,0x414E42);
        g.translate(-360,-688);
        g.translate(680,688);base(g,144,96);
        for(int x=9;x<140;x+=44) {rect(g,x,9,39,78,0x77816E);rect(g,x+7,19,25,21,0x3B4E42);rect(g,x+9,55,21,6,0xBBB9A1);}
        g.translate(-680,-688);
        g.translate(40,864);base(g,144,96);
        for(int y=10;y<84;y+=38)for(int x=10;x<136;x+=42) {rect(g,x,y,38,31,0x7B8674);rect(g,x+9,y+12,20,5,0xB2B5A0);}
        g.dispose();return image;
    }
    private static void base(Graphics2D g,int w,int h) {
        rect(g,4,4,w-8,h-8,0x313D36);rect(g,9,9,w-18,h-18,0x48544A);
    }
    private static void pipe(Graphics2D g,int[] x,int[] y) {
        g.setColor(new Color(0xABB09D));g.setStroke(new BasicStroke(14,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND));
        g.drawPolyline(x,y,x.length);
    }
    private static void rect(Graphics2D g,int x,int y,int w,int h,int rgb) {
        g.setColor(new Color(rgb));g.fillRect(x,y,w,h);
    }
    private static void oval(Graphics2D g,int x,int y,int w,int h,int rgb) {
        g.setColor(new Color(rgb));g.fillOval(x,y,w,h);
    }
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);Files.createDirectories(path.getParent());ImageIO.write(render(),"png",path.toFile());
    }
}
