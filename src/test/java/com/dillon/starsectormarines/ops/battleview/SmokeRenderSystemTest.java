package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmokeRenderSystemTest {

    private static final float EPS = 0.0001f;

    @Test
    void smokePaintsAfterFog() {
        assertTrue(RenderLayer.SMOKE.ordinal() > RenderLayer.FOG.ordinal());
    }

    @Test
    void foggedPuffsGainContrastWithoutBreakingTheFadeEnvelope() {
        assertEquals(0.48f, SmokeRenderSystem.alphaForVisibility(0.48f, true), EPS);
        assertEquals(0.84f, SmokeRenderSystem.alphaForVisibility(0.48f, false), EPS);
        assertEquals(1f, SmokeRenderSystem.alphaForVisibility(0.72f, false), EPS);
        assertEquals(0f, SmokeRenderSystem.alphaForVisibility(0f, false), EPS);
    }
}
