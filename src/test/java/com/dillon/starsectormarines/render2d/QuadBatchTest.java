package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class QuadBatchTest {
    @Test
    void rotatedMirroredSubrectangleRetainsItsPaddedTextureAddresses() throws Exception {
        SpriteAPI sheet = (SpriteAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getTextureWidth" -> .75f;
                    case "getTextureHeight" -> .5f;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        QuadBatch plain = new QuadBatch(sheet, 100, 80, 1);
        QuadBatch mirrored = new QuadBatch(sheet, 100, 80, 1);
        plain.appendRotated(20, 16, 40, 32, 10, 20, 8, 4, 90, 1, 1, 1, 1);
        mirrored.appendRotated(20, 16, 40, 32, 10, 20, 8, 4, 90, true, 1, 1, 1, 1);
        assertArrayEquals(new float[]{12, 16, 12, 24, 8, 24, 8, 16}, data(mirrored, "posData"), .00001f);
        assertArrayEquals(data(plain, "posData"), data(mirrored, "posData"), .00001f,
                "mirroring artwork must not change rotated geometry");
        assertArrayEquals(new float[]{.15f, .2f, .45f, .2f, .45f, .4f, .15f, .4f},
                data(plain, "uvData"), .00001f);
        assertArrayEquals(new float[]{.15f, .4f, .45f, .4f, .45f, .2f, .15f, .2f},
                data(mirrored, "uvData"), .00001f,
                "rotation must preserve the independent vertical artwork mirror");
    }

    private static float[] data(QuadBatch batch, String name) throws Exception {
        Field field = QuadBatch.class.getDeclaredField(name);
        field.setAccessible(true);
        return (float[]) field.get(batch);
    }
}
