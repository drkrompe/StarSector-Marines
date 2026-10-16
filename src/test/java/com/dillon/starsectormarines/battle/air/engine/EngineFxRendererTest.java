package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineFxRendererTest {
    private record Draw(float x, float y, float width, float height, float angle, float alpha, Color color) {}
    private static final class Recorder {
        float width, height, angle, alpha;
        Color color;
        final List<Draw> draws = new ArrayList<>();
        final SpriteAPI sprite = (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setSize" -> { width = ((Number) args[0]).floatValue(); height = ((Number) args[1]).floatValue(); }
                        case "setAngle" -> angle = ((Number) args[0]).floatValue();
                        case "setAlphaMult" -> alpha = ((Number) args[0]).floatValue();
                        case "setColor" -> color = (Color) args[0];
                        case "renderAtCenter" -> draws.add(new Draw(((Number) args[0]).floatValue(),
                                ((Number) args[1]).floatValue(), width, height, angle, alpha, color));
                        case "hashCode" -> { return System.identityHashCode(proxy); }
                        case "equals" -> { return proxy == args[0]; }
                        default -> { }
                    }
                    return null;
                });
    }
    private static BattleCamera camera() {
        var camera = new BattleCamera(100, 100);
        camera.setViewport(0, 0, 800, 600, 10);
        return camera;
    }
    private static final EngineSlotData SLOT = new EngineSlotData(.5f, -.8f, 180, 4, 2, 0, "HIGH_TECH");

    @Test void softBloomAndTaperAreRestrainedAndTheFlameBaseRemainsAtTheNozzle() {
        var glow = new Recorder(); var flame = new Recorder();
        var camera = camera();
        for (float facing : new float[]{0, 90, -43}) {
            EngineFxRenderer.draw(new EngineSlotData[]{SLOT}, 50, 50, facing, 1.8f, 3,
                    1, .8f, camera, glow.sprite, flame.sprite, new float[]{1});
            Draw halo = glow.draws.get(glow.draws.size() - 1), exhaust = flame.draws.get(flame.draws.size() - 1);
            float nozzleWidth = SLOT.widthCells * 1.8f * camera.cellPxSize();
            float nativeLength = SLOT.lengthCells * 1.8f * camera.cellPxSize();
            assertEquals(nozzleWidth * 1.1f, halo.width(), .0001f);
            assertTrue(exhaust.width() >= nozzleWidth * .65f && exhaust.width() <= nozzleWidth * .75f);
            assertTrue(exhaust.height() >= nativeLength * .65f && exhaust.height() <= nativeLength * .8f);
            assertTrue(halo.alpha() <= exhaust.alpha() * .25f, "bloom stays much softer than the exhaust");
            double radians = Math.toRadians(exhaust.angle());
            float dx = (float) -Math.sin(radians), dy = (float) Math.cos(radians);
            assertEquals(halo.x(), exhaust.x() - dx * exhaust.height() * .5f, .0001f);
            assertEquals(halo.y(), exhaust.y() - dy * exhaust.height() * .5f, .0001f);
            assertEquals(EngineStylePalette.flameColor(SLOT.style), exhaust.color());
        }
        assertEquals(0f, flame.angle);
        assertEquals(Color.WHITE, flame.color);
        assertEquals(Color.WHITE, glow.color);
    }

    @Test void idleAndCruiseStayVisiblyShorterAndDimmerThanAccelerationWithUniformFallback() {
        var glow = new Recorder(); var flame = new Recorder(); var camera = camera();
        for (float demand : new float[]{0, .25f, 1})
            EngineFxRenderer.draw(new EngineSlotData[]{SLOT}, 50, 50, 0, 1, 0,
                    1, 1, camera, glow.sprite, flame.sprite, new float[]{demand});
        Draw idle = flame.draws.get(0), cruise = flame.draws.get(1), boost = flame.draws.get(2);
        assertTrue(idle.height() < cruise.height());
        assertTrue(cruise.height() < boost.height() * .65f);
        assertTrue(cruise.alpha() < boost.alpha() * .55f);
        assertTrue(glow.draws.get(0).alpha() > 0 && glow.draws.get(0).alpha() < .1f,
                "idle keeps a restrained live nozzle glow");
        EngineFxRenderer.draw(new EngineSlotData[]{SLOT}, 50, 50, 0, 1, 0,
                1, 1, camera, glow.sprite, flame.sprite);
        assertEquals(boost, flame.draws.get(3), "missing demand preserves uniform-throttle callers");
    }

    @Test void demandChangesLengthWidthAndBrightnessContinuouslyWithoutMovingTheNozzle() {
        var glow = new Recorder(); var flame = new Recorder(); var camera = camera();
        for (float demand : new float[]{0, .49f, .5f, .51f, 1})
            EngineFxRenderer.draw(new EngineSlotData[]{SLOT}, 50, 50, 27, 1, 0,
                    1, 1, camera, glow.sprite, flame.sprite, new float[]{demand});
        for (int i = 1; i < flame.draws.size(); i++) {
            Draw before = flame.draws.get(i - 1), after = flame.draws.get(i);
            assertTrue(after.height() > before.height());
            assertTrue(after.width() > before.width());
            assertTrue(after.alpha() > before.alpha());
            assertEquals(glow.draws.get(0).x(), glow.draws.get(i).x());
            assertEquals(glow.draws.get(0).y(), glow.draws.get(i).y());
        }
        assertTrue(flame.draws.get(3).alpha() - flame.draws.get(1).alpha() < .02f);
    }
}
