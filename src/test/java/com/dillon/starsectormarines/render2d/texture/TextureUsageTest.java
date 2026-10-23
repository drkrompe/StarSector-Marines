package com.dillon.starsectormarines.render2d.texture;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextureUsageTest {

    private static SpriteAPI token() {
        return (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private final boolean wasEnabled = TextureUsage.setEnabled(true);

    @AfterEach
    void restore() {
        TextureUsage.setEnabled(wasEnabled);
    }

    @Test
    void aWorldDrawIsScaledToTheClosestZoomOfItsMap() {
        SpriteAPI sheet = token();
        TextureUsage.name(sheet, "unit-test/sheet-a.png", 512, 128);
        // A 560x336 map at 1920x1080: the closest zoom draws a cell 16 * 1920/560 px wide.
        TextureUsage.beginWorld(10f, 560, 336);
        // A quarter of the sheet drawn 20 px wide: the whole sheet is 80 px at 10 px a cell.
        TextureUsage.sheet(sheet, 128, 128, 20f, 20f);
        float referenceCell = 16f * 1920f / 560f;
        assertEquals(80f * referenceCell / 10f, TextureUsage.displayWidth("unit-test/sheet-a.png"), 0.1f);
    }

    @Test
    void aToyGridIsMeasuredAsTheSmallestPlayedMap() {
        assertEquals(TextureUsage.referenceCellPx(TextureUsage.MIN_PLAY_MAP_WIDTH, TextureUsage.MIN_PLAY_MAP_HEIGHT),
                TextureUsage.referenceCellPx(20, 12), 1e-3f);
    }

    @Test
    void aUiDrawIsMeasuredAtTheLargestUiScale() {
        // The left half of an image drawn 100 document units wide: the whole image is 200.
        TextureUsage.ui("unit-test/ui-b.png", null, 100f, 50f, 0.5f, 1f);
        assertEquals(200f * TextureUsage.MAX_UI_SCALE, TextureUsage.displayWidth("unit-test/ui-b.png"), 0.1f);
        try (TextureUsage.Scope ignored = TextureUsage.suppressUi()) {
            TextureUsage.ui("unit-test/ui-c.png", null, 100f, 50f, 1f, 1f);
        }
        assertEquals(0f, TextureUsage.displayWidth("unit-test/ui-c.png"));
    }
}
