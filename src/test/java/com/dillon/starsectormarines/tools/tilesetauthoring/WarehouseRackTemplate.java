package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Separate rack bed, retaining frame and interchangeable 2x2 stock layers. */
public final class WarehouseRackTemplate {
    private WarehouseRackTemplate() { }

    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // A complete 2x6 rack. Each 2x2 opening receives an independent stock sprite.
        rect(g, 40, 40, 176, 560, 0x283D47);
        for (int y : new int[]{48, 240, 432}) {
            rect(g, 52, y, 152, 160, 0x606765);
            rect(g, 60, y+8, 136, 144, 0x72786E);
        }
        // Identical full-frame coordinates translated 256px right. Interior stays empty.
        rect(g, 296, 40, 12, 560, 0x385D6C);
        rect(g, 460, 40, 12, 560, 0x385D6C);
        for (int y : new int[]{40, 224, 416, 584}) {
            rect(g, 296, y, 176, 16, 0xBD713B);
            rect(g, 292, y-4, 20, 24, 0x3D6778);
            rect(g, 456, y-4, 20, 24, 0x3D6778);
        }
        // Six stock layers, each inset well inside a 192px square cell.
        for (int i=0; i<6; i++) {
            Graphics2D item=(Graphics2D)g.create();
            item.translate(544+(i%2)*224, 32+(i/2)*224);
            if(i==0) { // strapped pallet of cartons
                rect(item,28,28,136,136,0x746350);
                for(int x:new int[]{36,100}) for(int y:new int[]{36,100}) {
                    rect(item,x,y,56,56,0xA99775); rect(item,x+25,y,6,56,0xC4B78F);
                }
            } else if(i==1) { // four stackable parts bins
                for(int x:new int[]{30,100}) for(int y:new int[]{34,100}) {
                    rect(item,x,y,60,56,0x365A6E); rect(item,x+7,y+7,46,42,0x547E8B);
                    rect(item,x+21,y+21,20,16,0x283D48);
                }
            } else if(i==2) { // crated engine assembly
                rect(item,28,30,136,132,0x8D7B5A);
                rect(item,40,42,112,108,0x35413E);
                rect(item,62,55,66,82,0x89918A);
                for(int y=64;y<135;y+=18) rect(item,51,y,88,8,0x566562);
            } else if(i==3) { // strapped pipe stock
                rect(item,28,28,136,136,0x6B6754);
                for(int x=40;x<160;x+=24) rect(item,x,34,16,122,0x9CA398);
                rect(item,30,52,132,12,0x43555A); rect(item,30,126,132,12,0x43555A);
            } else if(i==4) { // one partly picked tray of broad components
                rect(item,30,34,132,124,0x3F4D4C);
                rect(item,38,42,116,108,0x6F756A);
                for(int x:new int[]{58,104}) {
                    item.setColor(new Color(0x303C3B));item.fillOval(x,52,32,32);
                    item.setColor(new Color(0x9BA497));item.fillOval(x+8,60,16,16);
                }
                rect(item,48,106,68,24,0x8A938C);
            } else { // protective olive transit cases
                rect(item,30,34,60,126,0x6E775D); rect(item,102,34,60,126,0x6E775D);
                for(int x:new int[]{38,110}) for(int y:new int[]{50,130}) rect(item,x,y,44,8,0xA6A992);
            }
            item.dispose();
        }
        g.dispose(); return image;
    }

    private static void rect(Graphics2D g,int x,int y,int w,int h,int color) {
        g.setColor(new Color(color));g.fillRect(x,y,w,h);
    }
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]);Files.createDirectories(path.getParent());
        ImageIO.write(render(),"png",path.toFile());
    }
}
