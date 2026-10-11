package com.dillon.starsectormarines.tools.roadauthoring;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoadMaterialBakerTest {
    @Test void featherRetainsPavementColorAndRevealsBackgroundOnlyAtExposedSides() {
        var source=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)source.setRGB(x,y,0xff345678);
        var north=RoadMaterialBaker.feather(source,1,.42,.08);
        assertTrue((north.getRGB(32,0)>>>24)<10);
        assertTrue((north.getRGB(32,12)>>>24)>10 && (north.getRGB(32,12)>>>24)<245);
        assertEquals(0xff345678,north.getRGB(32,63));
        assertEquals(0x345678,north.getRGB(32,0)&0xffffff);
        var south=RoadMaterialBaker.feather(source,2,.42,.08);
        assertEquals(255,south.getRGB(32,0)>>>24);assertTrue((south.getRGB(32,63)>>>24)<10);
        var corner=RoadMaterialBaker.feather(source,9,.42,.08);
        assertEquals(0,corner.getRGB(0,0)>>>24);assertEquals(255,corner.getRGB(63,63)>>>24);
    }
}
