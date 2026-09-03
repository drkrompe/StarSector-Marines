package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BattleRendererFogAlphaTest {

    /** The fog scale is one alpha byte wide, so a level is the finest step it has. */
    private static final float EPS = 1f / 255f;

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

    /**
     * Every level is exactly a value the alpha channel can hold.
     *
     * <p>Not fussiness about a thousandth: the resident fog field stores the
     * same scale as texels, and the two only paint the same pixels while a
     * level's float is the byte it will be converted to. A shadow authored as a
     * value that lands between two bytes rounds one way in a vertex colour and
     * the other in a texel, and the seam shows up wherever both are on screen.
     */
    @Test
    void everyShadowIsExactlyALevelOfTheChannelItIsDrawnInto() {
        float[] authored = {
                BattleRenderer.fogAlphaForCell(false, 0, false),
                BattleRenderer.fogAlphaForCell(false, 0, true),
                BattleRenderer.fogAlphaForCell(true, 1, false),
                BattleRenderer.fogAlphaForCell(true, 2, false),
                BattleRenderer.fogAlphaForCell(true, 3, false),
                BattleRenderer.fogAlphaForCell(true, 4, false),
        };
        for (float alpha : authored) {
            assertEquals(Math.round(alpha * 255f) / 255f, alpha, 0f,
                    "a fog shadow must be a whole level of the alpha channel");
        }
    }
}
