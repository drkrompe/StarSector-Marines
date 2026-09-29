package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitAtlasNativeSizeTest {

    @Test
    void resizingBetweenBattlesPreservesNativeTextureDimensions() {
        SpriteAPI sprite = mutableSprite(150, 130);
        List<SpriteAtlas.Source> first = new ArrayList<>();
        UnitAtlas.offer(first, sprite, 150, 130);

        sprite.setSize(18, 12);
        assertEquals(18f, sprite.getWidth());
        assertEquals(12f, sprite.getHeight());
        List<SpriteAtlas.Source> reentered = new ArrayList<>();
        UnitAtlas.offer(reentered, sprite, 150, 130);

        assertEquals(first, reentered);
        assertEquals(150, reentered.get(0).contentW());
        assertEquals(130, reentered.get(0).contentH());
    }

    @Test
    void shrinkingAnOversizedHullDoesNotAdmitItToTheAtlas() {
        SpriteAPI sprite = mutableSprite(1200, 600);
        sprite.setSize(100, 50);
        List<SpriteAtlas.Source> sources = new ArrayList<>();
        UnitAtlas.offer(sources, sprite, 1200, 600);
        assertTrue(sources.isEmpty());
    }

    @Test
    void missingNativeDimensionsLeaveTheImageOnTheSpritePath() {
        SpriteAPI sprite = mutableSprite(18, 12);
        List<SpriteAtlas.Source> sources = new ArrayList<>();
        UnitAtlas.offer(sources, sprite, 0, 130);
        UnitAtlas.offer(sources, sprite, 150, 0);
        assertTrue(sources.isEmpty());
    }

    private static SpriteAPI mutableSprite(float width, float height) {
        float[] size = {width, height};
        return (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> size[0];
                    case "getHeight" -> size[1];
                    case "setSize" -> {
                        size[0] = (Float) args[0];
                        size[1] = (Float) args[1];
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "mutable sprite";
                    default -> null;
                });
    }
}
