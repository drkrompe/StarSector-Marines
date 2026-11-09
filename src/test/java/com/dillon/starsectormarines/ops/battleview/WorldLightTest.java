package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The light a world's ground is seen in ({@link WorldLight}). */
class WorldLightTest {
    @Test void everyWorldButIrradiatedKeepsItsGroundAsPainted() {
        for (var palette : SurfacePalette.values()) {
            if (palette == SurfacePalette.IRRADIATED) continue;
            assertSame(WorldLight.NEUTRAL, WorldLight.forPalette(palette));
        }
        assertSame(WorldLight.NEUTRAL, WorldLight.forPalette(null));
        assertTrue(WorldLight.NEUTRAL.neutral());
        assertArrayEquals(new float[]{.2f, .5f, .7f}, WorldLight.NEUTRAL.grade(.2f, .5f, .7f), 1e-6f);
        assertEquals(SunLight.TINT_B, WorldLight.NEUTRAL.shadowB());
    }

    @Test void irradiatedLightIsPalerAndLessColourful() {
        var light = WorldLight.forPalette(SurfacePalette.IRRADIATED);
        assertFalse(light.neutral());
        float[] in = {.55f, .40f, .25f};
        float[] out = light.grade(in[0], in[1], in[2]);
        assertTrue(luma(out) > luma(in), "washed toward the glare");
        assertTrue(spread(out) < spread(in), "bleached of colour");
        // Shadows are neutral, not the cool sky-fill of a living world.
        assertTrue(light.shadowB() - light.shadowR() < SunLight.TINT_B - SunLight.TINT_R);
    }

    private static float luma(float[] c) { return .299f * c[0] + .587f * c[1] + .114f * c[2]; }
    private static float spread(float[] c) { return Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2])); }
}
