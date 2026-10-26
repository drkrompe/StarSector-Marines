package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionMarkerRenderSystemTest {
    @Test
    void aCardReachingUnderTheRailMovesLeftOfItAndOneBelowOrBesideItStays() {
        var camera = new BattleCamera(100, 100);
        camera.setViewport(0, 0, 1920, 1080, 20);
        camera.coverTopRight(1550, 300);
        assertEquals(1550 - 60 - 8, MissionMarkerRenderSystem.uncovered(camera, 1520, 600, 60));
        assertEquals(1520, MissionMarkerRenderSystem.uncovered(camera, 1520, 250, 60), "below the rail");
        assertEquals(800, MissionMarkerRenderSystem.uncovered(camera, 800, 600, 60), "left of the rail");
        camera.coverTopRight(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        assertEquals(1520, MissionMarkerRenderSystem.uncovered(camera, 1520, 600, 60), "no rail");
    }
}
