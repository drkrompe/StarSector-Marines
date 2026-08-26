package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        for (int x = 1; x <= 10; x++) {
            lights.spawnImpact(MarineWeapon.FIELD_RIFLE.def().fx, x, 10f);
        }

        assertEquals(GroundLightService.MAX_SHADER_LIGHTS, lights.selectNearest(camera()));
        for (int i = 0; i < GroundLightService.MAX_SHADER_LIGHTS; i++) {
            assertEquals(10f - i, lights.selected(i).x, 1e-6f);
        }
    }

    @Test
    void cannonHeCarriesTheLargestImpactLight() {
        GroundLightService lights = new GroundLightService();
        lights.spawnImpact(MarineSecondary.ROCKET_LAUNCHER.def().fx, 10f, 10f);
        lights.selectNearest(camera());
        GroundLightService.Light ordinaryHe = lights.selected(0);

        lights.clear();
        lights.spawnImpact(MechWeapon.HEAVY_CANNON.def().fx, 10f, 10f);
        lights.selectNearest(camera());
        GroundLightService.Light cannonHe = lights.selected(0);

        assertTrue(cannonHe.radius > ordinaryHe.radius);
        assertTrue(cannonHe.intensity > ordinaryHe.intensity);
        assertTrue(cannonHe.lifetime > ordinaryHe.lifetime);
    }

    @Test
    void travelingBoltLightsFollowTheRenderedBodiesUntilArrival() {
        GroundLightService lights = new GroundLightService();
        ShotEvent pulse = boltShot(0f, 0f, 10f, 0f, MarineWeapon.PULSE_RIFLE);
        ShotEvent dmr = boltShot(0f, 10f, 10f, 10f, MarineWeapon.DMR);
        pulse.lifetime = 0.5f;
        dmr.lifetime = 0.5f;

        lights.syncBoltLights(List.of(pulse, dmr));

        assertEquals(2, lights.liveCount());
        GroundLightService.Light pulseLight = lights.boltLight(pulse);
        GroundLightService.Light dmrLight = lights.boltLight(dmr);
        assertEquals(4.5f, pulseLight.x, 1e-6f);
        assertEquals(4.1f, dmrLight.x, 1e-6f);
        assertEquals(MarineWeapon.PULSE_RIFLE.tracerColor(), pulseLight.color);
        assertEquals(MarineWeapon.DMR.tracerColor(), dmrLight.color);

        pulse.lifetime = 0.25f;
        lights.syncBoltLights(List.of(pulse));

        assertSame(pulseLight, lights.boltLight(pulse));
        assertEquals(7f, pulseLight.x, 1e-6f);
        assertEquals(1, lights.liveCount(), "the arrived DMR no longer owns a moving light");

        lights.syncBoltLights(List.of());
        assertEquals(0, lights.liveCount());
    }

    @Test
    void nonBoltRoundsDoNotAcquireTravelingLights() {
        GroundLightService lights = new GroundLightService();

        lights.syncBoltLights(List.of(
                boltShot(0f, 0f, 10f, 0f, MarineWeapon.FIELD_RIFLE)));

        assertEquals(0, lights.liveCount());
    }

    @Test
    void movingBoltDoesNotAbsorbItsSeparateMuzzleFlash() {
        GroundLightService lights = new GroundLightService();
        ShotEvent pulse = boltShot(0f, 0f, 10f, 0f, MarineWeapon.PULSE_RIFLE);
        pulse.lifetime = 0.95f;
        lights.syncBoltLights(List.of(pulse));

        lights.spawnMuzzle(pulse);

        assertEquals(2, lights.liveCount());
    }

    private static ShotEvent boltShot(float fromX, float fromY, float toX, float toY,
                                      MarineWeapon weapon) {
        return new ShotEvent(fromX, fromY, toX, toY, true, Faction.MARINE,
                1f, null, weapon, null, null);
    }

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(20, 20);
        camera.setViewport(0f, 0f, 200f, 200f, 10f);
        return camera;
    }
}
