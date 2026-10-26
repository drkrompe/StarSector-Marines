package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion.Kind;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion.Layer;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion.Part;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fixed overhead part fades while a figure the player sees stands under its prop, and only then; the fade eases
 * across its range in {@link PropMotionRenderSystem#FADE_SECONDS}. A prop whose own art is an overhead part draws
 * only there, never again as a ground doodad beneath the figures under it.
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

    @Test
    void aPropDrawnOverheadIsLeftOutOfTheGroundPass() {
        PropMotion awning = motion("doodad.awning", part("doodad.awning", Layer.OVERHEAD));
        PropMotion crane = motion("doodad.crane", part("doodad.crane-bridge", Layer.OVERHEAD));
        PropMotion fan = motion("doodad.fan", part("doodad.fan", Layer.DECK));
        Map<String, PropMotion> motions = Map.of("doodad.awning", awning, "doodad.crane", crane, "doodad.fan", fan);
        assertTrue(DoodadRenderSystem.drawnOverhead(placed("doodad.awning"), motions::get),
                "the awning is its own roof part");
        assertFalse(DoodadRenderSystem.drawnOverhead(placed("doodad.crane"), motions::get),
                "the crane's base stands on the ground under its moving bridge");
        assertFalse(DoodadRenderSystem.drawnOverhead(placed("doodad.fan"), motions::get), "a deck part stays below");
        assertFalse(DoodadRenderSystem.drawnOverhead(placed("doodad.crate"), motions::get), "no motion at all");
    }

    private static PropMotion motion(String id, Part part) {
        return new PropMotion(id, PropMotion.Runs.ALWAYS, 1f, 0f, List.of(part));
    }

    private static Part part(String id, Layer layer) {
        return new Part(id, Kind.FIXED, layer, .5f, .5f, null, 0f, 0f, null, false, 0f, 0f);
    }

    private static Doodad placed(String id) {
        return new Doodad(0, 0, new DoodadDef(id, "sheet", 0, 0, DoodadCover.NONE, 0f, 1, 1, null, 0, null, null));
    }
}
