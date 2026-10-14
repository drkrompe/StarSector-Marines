package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DugInMarkerRenderSystemTest {

    @Test
    void notDugInIsNeverMarked() {
        assertEquals(0f, DugInMarkerRenderSystem.markerAlpha(false, FogOfWarService.VIS_VISIBLE, true, 1f));
    }

    @Test
    void hiddenIsNeverRevealed() {
        assertEquals(0f, DugInMarkerRenderSystem.markerAlpha(true, FogOfWarService.VIS_HIDDEN, true, 1f));
    }

    @Test
    void selectedSquadDrawsBrighterThanUnselected() {
        float selected = DugInMarkerRenderSystem.markerAlpha(true, FogOfWarService.VIS_VISIBLE, true, 1f);
        float plain = DugInMarkerRenderSystem.markerAlpha(true, FogOfWarService.VIS_VISIBLE, false, 1f);
        assertTrue(plain > 0f && selected > plain);
    }

    @Test
    void fadingFollowsTheBodysFade() {
        assertEquals(0.5f * DugInMarkerRenderSystem.UNSELECTED_ALPHA,
                DugInMarkerRenderSystem.markerAlpha(true, FogOfWarService.VIS_FADING, false, 0.5f), 1e-6f);
    }
}
