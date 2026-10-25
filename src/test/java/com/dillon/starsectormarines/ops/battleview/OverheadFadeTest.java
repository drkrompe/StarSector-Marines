package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fixed overhead part fades while a figure the player sees stands under its prop, and only then; the fade eases
 * across its range in {@link PropMotionRenderSystem#FADE_SECONDS}.
 */
class OverheadFadeTest {

    private static final int W = 8, H = 4;

    @Test
    void fadesOnlyForASeenFigureOnTheFootprint() {
        Doodad awning = new Doodad(2, 1, new TileManifest.TileFrame(0, 0), "sheet.png", 0, 0f, 3, 1);
        boolean[] seen = new boolean[W * H];
        assertFalse(PropMotionRenderSystem.seenUnder(seen, W, awning), "nobody under it");
        seen[2 * W + 3] = true;
        assertFalse(PropMotionRenderSystem.seenUnder(seen, W, awning), "a figure beside it, not under");
        seen[W + 4] = true;
        assertTrue(PropMotionRenderSystem.seenUnder(seen, W, awning), "a figure under its far end");
    }

    @Test
    void easesAcrossTheRangeInTheFadeTime() {
        float low = PropMotionRenderSystem.SEEN_UNDER_ALPHA, t = PropMotionRenderSystem.FADE_SECONDS;
        assertEquals(low, PropMotionRenderSystem.ease(1f, low, t), 1e-5f);
        assertEquals(1f, PropMotionRenderSystem.ease(low, 1f, t), 1e-5f);
        float half = PropMotionRenderSystem.ease(1f, low, t / 2);
        assertEquals((1f + low) / 2, half, 1e-5f);
        assertEquals(low, PropMotionRenderSystem.ease(low, low, t), 1e-5f, "holds at its target");
    }
}
