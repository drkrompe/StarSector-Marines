package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Same sixteen exposed-face masks as WallReskinTemplate, in a quiet ship palette. */
public final class ShipBulkheadTemplate {
    private ShipBulkheadTemplate() { }
    public static BufferedImage render() {
        BufferedImage image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x17191C)); g.fillRect(0, 0, 1024, 1024);
        for (int mask=0; mask<16; mask++) {
            int x=(mask%4)*256+16, y=(mask/4)*256+16;
            boolean n=(mask&1)!=0, e=(mask&2)!=0, s=(mask&4)!=0, w=(mask&8)!=0;
            g.setColor(new Color(0x454843)); g.fillRect(x, y, 224, 224);
            g.setColor(new Color(0xA7A89C));
            if(n) g.fillRect(x,y,224,32);
            if(e) g.fillRect(x+192,y,32,224);
            if(s) g.fillRect(x,y+192,224,32);
            if(w) g.fillRect(x,y,32,224);
            int l=w?32:0, r=e?32:0, t=n?32:0, b=s?32:0;
            g.setColor(new Color(0x282E2A));
            if(n) g.fillRect(x+l,y+t,224-l-r,4);
            if(s) g.fillRect(x+l,y+224-b-4,224-l-r,4);
            if(w) g.fillRect(x+l,y+t,4,224-t-b);
            if(e) g.fillRect(x+224-r-4,y+t,4,224-t-b);
        }
        g.dispose(); return image;
    }
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]); Files.createDirectories(path.getParent());
        ImageIO.write(render(),"png",path.toFile());
    }
}
