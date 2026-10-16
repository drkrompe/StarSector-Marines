package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.engine.EngineHullLighting;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EngineHullLightRasterTest {
    @Test void overlappingLightsAddAfterHullEdgeCoverageRatherThanClampingSummedRgbFirst() {
        var hull=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        hull.setRGB(0,0,new Color(200,200,200,128).getRGB());
        var light=new EngineHullLighting.Light(0,0,0,1,1,1,Color.WHITE,1);
        UiElement canvas=new UiElement("canvas").tag(UiTag.CANVAS).canvasSize(1,1);
        UiDocument document=new UiDocument(canvas);
        document.canvases().set(canvas,context->{
            context.fillRect(0,0,1,1,Color.BLACK);
            HeadlessBattleSceneRenderer.drawEngineHullLights(context,hull,.5f,.5f,1,1,0,1,1,0,0,1,
                    new EngineHullLighting.Light[]{light,light});
        });
        var image=new HeadlessUiRenderer(List.of(Path.of("mod"))).render(document,1,1);
        assertEquals(200,new Color(image.getRGB(0,0)).getRed(),1,
                "each 200 RGB light contributes ~100 after half-alpha coverage; summing first would give only128");
    }
    @Test void bottomUpHullUvAndMaterialColorArePreservedWithAlphaAppliedOnce() {
        BufferedImage hull=new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<3;y++)for(int x=0;x<3;x++)hull.setRGB(x,y,new Color(200,100,40,128).getRGB());
        var light=new EngineHullLighting.Light(0,1,0,1,.4f,.4f,Color.WHITE,.5f);
        BufferedImage overlay=HeadlessBattleSceneRenderer.engineHullOverlay(hull,3,3,0,0,
                new EngineHullLighting.Light[]{light});
        Color lit=new Color(overlay.getRGB(1,0),true);
        assertEquals(100,lit.getRed());assertEquals(50,lit.getGreen());assertEquals(20,lit.getBlue());
        assertEquals(128,lit.getAlpha());
        assertEquals(0,overlay.getRGB(1,2)&0xffffff,"bottom row must not receive top-row local light");
        UiElement canvas=new UiElement("canvas").tag(UiTag.CANVAS).canvasSize(3,3);
        UiDocument document=new UiDocument(canvas);
        document.canvases().set(canvas,context->{
            context.fillRect(0,0,3,3,new Color(10,10,10));
            HeadlessUiRenderer.additiveImage(context,overlay,1.5f,1.5f,3,3,0,.5f);
        });
        var image=new HeadlessUiRenderer(List.of(Path.of("mod"))).render(document,3,3);
        assertEquals(35,new Color(image.getRGB(1,0)).getRed(),1);
        assertEquals(10,new Color(image.getRGB(1,2)).getRed());
    }
    @Test void zeroHullCoverageCannotReceiveReflectedLight() {
        var transparent=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        transparent.setRGB(0,0,0x00ffffff);
        var light=new EngineHullLighting.Light(0,0,0,1,1,1,Color.WHITE,1);
        var overlay=HeadlessBattleSceneRenderer.engineHullOverlay(transparent,1,1,0,0,
                new EngineHullLighting.Light[]{light});
        assertEquals(0,overlay.getRGB(0,0));
    }
}
