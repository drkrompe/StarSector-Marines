package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BattleRendererFogAlphaTest {

    private static final float EPS = 0.0001f;

    @Test
    void smokeHalvesTheShadowWithoutRevealingTheCell() {
        float ordinary = BattleRenderer.fogAlphaForCell(false, 0, false);
        float smoke = BattleRenderer.fogAlphaForCell(false, 0, true);

        assertEquals(0.85f, ordinary, EPS);
        assertEquals(ordinary * 0.5f, smoke, EPS);
    }

    @Test
    void smokeDoesNotChangeRevealedEdgeFeathering() {
        assertEquals(0.30f, BattleRenderer.fogAlphaForCell(true, 2, false), EPS);
        assertEquals(0.30f, BattleRenderer.fogAlphaForCell(true, 2, true), EPS);
        assertEquals(0f, BattleRenderer.fogAlphaForCell(true, 0, true), EPS);
    }
}
