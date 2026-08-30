package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.ui.PositionAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleLayoutTest {

    private static final float EPSILON = 1e-4f;

    @Test
    void battleCameraCoversTheCompleteHostWithoutLetterboxing() {
        BattleLayout layout = new BattleLayout(
                position(20f, 30f, 1600f, 900f), 100, 60);

        assertEquals(20f, layout.gridX, EPSILON);
        assertEquals(30f, layout.gridY, EPSILON);
        assertEquals(1600f, layout.gridW, EPSILON);
        assertEquals(900f, layout.gridH, EPSILON);
        assertEquals(16f, layout.cellSize, EPSILON);
        assertTrue(layout.cellSize * 100 >= layout.gridW);
        assertTrue(layout.cellSize * 60 >= layout.gridH);
        assertEquals(32f, layout.backX, EPSILON);
        assertEquals(42f, layout.backY, EPSILON);
    }

    private static PositionAPI position(float x, float y, float width, float height) {
        return (PositionAPI) Proxy.newProxyInstance(
                PositionAPI.class.getClassLoader(),
                new Class<?>[]{PositionAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getWidth" -> width;
                    case "getHeight" -> height;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
