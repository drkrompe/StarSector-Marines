package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundLightServiceTest {

    @Test
    void repeatedFireAtOnePointMergesAndExpires() {
        GroundLightService lights = new GroundLightService();
        lights.spawnFire(5f, 5f, 1f);
        lights.spawnFire(5.1f, 5.1f, 1.2f);

        assertEquals(1, lights.liveCount());
        lights.advance(0.69f);
        assertEquals(1, lights.liveCount());
        assertTrue(lights.selectNearest(camera()) > 0);
        assertTrue(lights.selected(0).effectiveIntensity() > 0f);
        lights.advance(0.02f);
        assertEquals(0, lights.liveCount());
    }

    @Test
    void selectionKeepsEightNearestVisibleLightsInDistanceOrder() {
        GroundLightService lights = new GroundLightService();
        for (int x = 1; x <= 10; x++) lights.spawnImpact(ImpactProfile.RIFLE, x, 10f);

        assertEquals(GroundLightService.MAX_SHADER_LIGHTS, lights.selectNearest(camera()));
        for (int i = 0; i < GroundLightService.MAX_SHADER_LIGHTS; i++) {
            assertEquals(10f - i, lights.selected(i).x, 1e-6f);
        }
    }

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(20, 20);
        camera.setViewport(0f, 0f, 200f, 200f, 10f);
        return camera;
    }
}
