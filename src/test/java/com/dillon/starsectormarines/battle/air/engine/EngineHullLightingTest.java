package com.dillon.starsectormarines.battle.air.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EngineHullLightingTest {
    private static EngineSlotData slot(float x, float y, float angle) {
        return new EngineSlotData(x, y, angle, 4, 1, 0, "HIGH_TECH");
    }

    @Test void reflectionExtendsInwardForAftAndSideNozzlesAndFallsOffSmoothly() {
        for (float angle : new float[]{0, 90, 180, -90}) {
            var light = EngineHullLighting.lights(new EngineSlotData[]{slot(2, -3, angle)}, 20, 1, null)[0];
            double radians = Math.toRadians(angle);
            assertEquals(Math.sin(radians), light.inwardX(), 1e-5);
            assertEquals(-Math.cos(radians), light.inwardY(), 1e-5);
            assertEquals(2 + .4f * light.inwardX(), light.x(), 1e-5);
            assertEquals(-3 + .4f * light.inwardY(), light.y(), 1e-5);
            assertEquals(1, EngineHullLighting.falloff(light, light.x(), light.y()), 1e-5);
            float half = EngineHullLighting.falloff(light, light.x() + light.inwardX() * light.depthRadius() / 2,
                    light.y() + light.inwardY() * light.depthRadius() / 2);
            assertTrue(half > 0 && half < 1);
            assertEquals(0, EngineHullLighting.falloff(light, light.x() + light.inwardX() * light.depthRadius() * 1.01f,
                    light.y() + light.inwardY() * light.depthRadius() * 1.01f), 1e-5);
        }
    }

    @Test void absentAndInactiveBurnsHaveNoLightsAndHullLengthLimitsReach() {
        assertEquals(0, EngineHullLighting.lights(null, 10, 1, null).length);
        assertEquals(0, EngineHullLighting.lights(new EngineSlotData[0], 10, 1, null).length);
        assertEquals(0, EngineHullLighting.lights(new EngineSlotData[]{slot(0, 0, 180)}, 10, 0, null).length);
        var capped = EngineHullLighting.lights(new EngineSlotData[]{slot(0, 0, 180)}, 2, 1, null)[0];
        assertTrue(capped.depthRadius() < 2 * .3f);
    }

    @Test void sharedBurnResponsePreservesCruiseContrastAndAsymmetricNozzleDemand() {
        var slots = new EngineSlotData[]{slot(-3, -2, 180), slot(3, -2, 180)};
        var lights = EngineHullLighting.lights(slots, 20, 1, new float[]{.25f, 1});
        assertTrue(lights[0].strength() < lights[1].strength() * .55f);
        var idle = EngineHullLighting.lights(slots, 20, 1, new float[]{0, 0});
        assertTrue(idle[0].strength() > 0 && idle[0].strength() < lights[0].strength());
        assertEquals(slots[0].localX, lights[0].x(), 1e-5);
        float[] rgb = new float[3];
        EngineHullLighting.sample(lights, lights[1].x(), lights[1].y(), rgb);
        assertTrue(rgb[2] > rgb[0], "blue engine hue survives mild desaturation");
        float red = rgb[0];
        EngineHullLighting.sample(lights, lights[1].x(), lights[1].y(), rgb);
        assertEquals(red, rgb[0], 1e-5, "reused output is cleared before sampling");
        EngineHullLighting.sample(new EngineHullLighting.Light[]{lights[1], lights[1]},
                lights[1].x(), lights[1].y(), rgb);
        assertEquals(red * 2, rgb[0], 1e-5, "all lights contribute without an arbitrary cap");
        EngineHullLighting.sample(new EngineHullLighting.Light[0], 0, 0, rgb);
        assertArrayEquals(new float[3], rgb);
        assertEquals(-3, slots[0].localX, "visual model never mutates native slots");
    }

    @Test void burnFallbackAndClampingMatchExistingExhaustResponse() {
        assertEquals(.7f, EngineBurn.slotIntensity(.7f, null, 0));
        assertEquals(.7f, EngineBurn.slotIntensity(.7f, new float[0], 0));
        assertEquals(EngineBurn.slotIntensity(1, new float[]{0}, 0),
                EngineBurn.slotIntensity(1, new float[]{-10}, 0));
        assertEquals(1, EngineBurn.slotIntensity(1, new float[]{10}, 0));
        assertEquals(.5f, EngineBurn.flameAlpha(.5f, 1));
    }
}
