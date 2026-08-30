package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BattleRendererFogAlphaTest {

    private static final float EPS = 0.0001f;

    @Test
    void smokeCausedLossHalvesTheShadowWithoutRevealingTheCell() {
        float naturallyUnseen = BattleRenderer.fogAlphaForCell(false, 0, false);
        float smokeCausedLoss = BattleRenderer.fogAlphaForCell(false, 0, true);

        assertEquals(0.85f, naturallyUnseen, EPS);
        assertEquals(naturallyUnseen * 0.5f, smokeCausedLoss, EPS);
    }

    @Test
    void smokeDoesNotChangeRevealedEdgeFeathering() {
        assertEquals(0.30f, BattleRenderer.fogAlphaForCell(true, 2, false), EPS);
        assertEquals(0.30f, BattleRenderer.fogAlphaForCell(true, 2, true), EPS);
        assertEquals(0f, BattleRenderer.fogAlphaForCell(true, 0, true), EPS);
    }
}
