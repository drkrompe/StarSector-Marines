package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Equipment at 64px/cell; three seamless material samples in the final row. */
public final class ShipGymTemplate {
    private ShipGymTemplate() {}
    public static BufferedImage render() {
        BufferedImage image=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);
        for(int slot=0;slot<7;slot++) {
            Graphics2D g=image.createGraphics();g.translate(32+slot%3*336,32+slot/3*320);
            switch(slot) {
                case 0 -> { // 2x3 treadmill, broad belt and coherent rails.
                    rect(g,10,7,108,178,0x444F46);rect(g,22,43,84,131,0x303B34);
                    rect(g,15,11,98,30,0x8B9683);rect(g,43,17,42,17,0x3E5747);
                    rect(g,10,40,8,125,0xA4AC9A);rect(g,110,40,8,125,0xA4AC9A);
                }
                case 1 -> { // 2x3 rowing ergometer.
                    rect(g,55,55,18,127,0x7C8975);rect(g,29,149,70,12,0x626F5C);
                    g.setColor(new Color(0x56664F));g.fillOval(28,8,72,72);
                    g.setColor(new Color(0x303F34));g.fillOval(38,18,52,52);
                    rect(g,37,108,54,30,0x969E8A);rect(g,18,88,35,17,0x3C4C3D);
                    rect(g,75,88,35,17,0x3C4C3D);rect(g,32,76,64,7,0xA7AE9C);
                }
                case 2 -> { // 2x3 bench press, racked bar and pads.
                    rect(g,21,21,10,146,0x9CA68F);rect(g,97,21,10,146,0x9CA68F);
                    rect(g,39,42,50,126,0x3B4A3D);rect(g,46,48,36,72,0x74856D);
                    rect(g,46,127,36,35,0x74856D);rect(g,10,29,108,8,0xA5AF9A);
                    for(int x:new int[]{13,100}) rect(g,x,16,15,34,0x344435);
                }
                case 3 -> { // 4x1 orderly dumbbell rack.
                    rect(g,4,8,248,48,0x525F4C);
                    for(int x=20;x<240;x+=45) {
                        rect(g,x,28,28,8,0xADB4A3);rect(g,x-3,18,10,28,0x303D33);
                        rect(g,x+22,18,10,28,0x303D33);
                    }
                }
                case 4 -> rect(g,0,0,192,192,0x4F5D50); // seamless rubber mat
                case 5 -> rect(g,0,0,192,192,0xADB0A0); // seamless non-slip coping
                case 6 -> { // seamless muted pool-water material; runtime owns waves.
                    rect(g,0,0,192,192,0x456D68);
                    g.setColor(new Color(0x688C7F));g.setStroke(new BasicStroke(3));
                    for(int y=20;y<190;y+=42) g.drawArc(-15,y,210,40,0,180);
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
