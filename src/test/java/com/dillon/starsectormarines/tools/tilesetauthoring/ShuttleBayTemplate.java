package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Geometry contract: 64px/cell, seven isolated slots in a 3x3 sheet. */
public final class ShuttleBayTemplate {
    private ShuttleBayTemplate() {}
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);
        for (int slot=0;slot<7;slot++) {
            Graphics2D g=image.createGraphics();
            g.translate(32+slot%3*336,32+slot/3*320);
            switch(slot) {
                case 0 -> { // 2x3 flight operations desk, controls on its right edge.
                    box(g,4,4,120,184,0x434B46); box(g,12,12,104,168,0x92968B);
                    box(g,26,20,46,55,0x384940); box(g,31,25,36,45,0x647C6C);
                    box(g,83,20,23,55,0xB0B2A3); box(g,25,103,80,52,0xB4B5A8);
                    box(g,94,163,18,9,0x5D665C);
                }
                case 1 -> { // 2x3 mobile servicing station, coiled leads entirely on cart.
                    box(g,5,8,118,175,0x343F38);box(g,15,15,98,157,0x818C7C);
                    box(g,25,25,78,43,0xA2A899);box(g,29,36,67,8,0x455349);
                    g.setStroke(new BasicStroke(9));g.setColor(new Color(0x34483B));
                    g.drawOval(29,88,69,65);g.drawOval(42,101,43,39);
                    box(g,17,1,15,12,0x29332D);box(g,93,1,15,12,0x29332D);
                    box(g,17,179,15,12,0x29332D);box(g,93,179,15,12,0x29332D);
                }
                case 2 -> { // 4x2 ground-power unit.
                    box(g,4,4,248,120,0x354239);box(g,14,13,165,102,0x7F8D79);
                    box(g,25,25,140,10,0x586650);box(g,25,48,140,10,0x586650);
                    box(g,25,71,140,10,0x586650);box(g,194,17,42,41,0xA5AB9A);
                    g.setColor(new Color(0x35483A));g.fillOval(186,69,54,40);
                }
                case 3 -> { // 4x2 contained fluid servicing skid; short joined manifold.
                    box(g,4,4,248,120,0x354239);
                    g.setColor(new Color(0xA5AD9D));g.fillRoundRect(17,17,85,83,24,24);
                    g.fillRoundRect(124,17,85,83,24,24);
                    box(g,49,98,134,10,0x697762);box(g,49,83,10,20,0x697762);
                    box(g,173,83,10,20,0x697762);box(g,216,45,25,50,0x809479);
                    box(g,183,98,47,10,0x697762);box(g,222,82,8,26,0x697762);
                }
                case 4 -> { // 2x2 wheels in cradles, transparent shelf overlay.
                    for(int x:new int[]{26,72}) {
                        box(g,x-5,23,37,82,0x6A7664);
                        g.setColor(new Color(0x334138));g.fillOval(x,29,27,65);
                        g.setColor(new Color(0xA1AA99));g.fillOval(x+8,44,11,33);
                    }
                }
                case 5 -> { // 1x1 seamless matte landing-deck material, enlarged sample.
                    box(g,0,0,192,192,0x777C73);
                }
                case 6 -> { // 1x1 flush tie-down marking, enlarged sample, transparent.
                    g.setColor(new Color(0xA6AB99));g.setStroke(new BasicStroke(7));
                    g.drawOval(47,47,98,98);
                    box(g,83,87,26,18,0x4B574B);box(g,90,77,12,38,0xA6AB99);
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
        Path path=Path.of(args[0]);Files.createDirectories(path.getParent());ImageIO.write(render(),"png",path.toFile());
    }
}
