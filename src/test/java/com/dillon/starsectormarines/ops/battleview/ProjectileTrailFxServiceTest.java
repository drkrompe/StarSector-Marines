package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.GuidedMissile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef.Guidance;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileTrailFxServiceTest {
    private static ShotEvent shot() {
        return new ShotEvent(0f, 0f, -20f, 0f, false, Faction.MARINE, 5f,
                TurretCatalogRegistry.requireStructure("structure.aa-swarmer"));
    }

    private static List<ProjectileTrailFxService.Emission> samples(int updates) {
        var service = new ProjectileTrailFxService();
        var shot = shot();
        var emissions = new ArrayList<ProjectileTrailFxService.Emission>();
        for (int i = 1; i <= updates; i++) {
            shot.lifetime = shot.lifetimeMax - (float) i / updates;
            service.sample(List.of(shot), emissions::add);
        }
        return emissions;
    }

    @Test void softLaunchCoastHasNoTrailOrIgnitionAndMotorLightsOnlyOnce() {
        var service = new ProjectileTrailFxService();
        var weapon = WeaponRegistry.require("weapon.pd-pack");
        var shot = new ShotEvent(0f, 0f, 20f, 0f, false, Faction.MARINE, 1f,
                TurretCatalogRegistry.requireStructure("structure.carried-point-defence"));
        var trails = new ArrayList<ProjectileTrailFxService.Emission>();
        var ignitions = new ArrayList<ProjectileTrailFxService.Emission>();
        assertNull(shot.guidedFlight);
        assertNotNull(ShotFx.of(shot).softLaunch(), "use the production turret-source FX route");
        float coast = weapon.softLaunch.seconds();
        shot.lifetime = 1f - coast * .8f;
        service.sample(List.of(shot), trails::add, ignitions::add);
        assertTrue(trails.isEmpty());
        assertTrue(ignitions.isEmpty());
        shot.lifetime = 1f - coast - .01f;
        service.sample(List.of(shot), trails::add, ignitions::add);
        assertEquals(1, ignitions.size());
        assertEquals(1, trails.size(), "no samples backfilled during unpowered coast");
        assertTrue(shot.ignitionShown);
        for (int i = 0; i < 10; i++) service.sample(List.of(shot), trails::add, ignitions::add);
        assertEquals(1, ignitions.size());
        assertEquals(1, trails.size());
        shot.lifetime -= .2f;
        service.sample(List.of(shot), trails::add, ignitions::add);
        assertEquals(1, ignitions.size());
        assertTrue(trails.size() > 1);
        int count = trails.size();
        shot.flightResolved = true;
        shot.lifetime -= .1f;
        service.sample(List.of(shot), trails::add, ignitions::add);
        assertEquals(count, trails.size());
        assertEquals(1, ignitions.size());
    }

    @Test void simulationOwnedGuidedFlightIgnoresPresentationCoastAndStopsAtFuelEnd() {
        var service = new ProjectileTrailFxService();
        var shot = new ShotEvent(0f, 0f, 20f, 0f, false, Faction.MARINE, 1f,
                TurretCatalogRegistry.requireStructure("structure.carried-point-defence"));
        shot.guidedFlight = new GuidedMissile(0f, 0f, 0f, 20f, new Guidance(240f, 20f, 1f));
        var trails = new ArrayList<ProjectileTrailFxService.Emission>();
        var ignitions = new ArrayList<ProjectileTrailFxService.Emission>();
        shot.lifetime = .95f;
        service.sample(List.of(shot), trails::add, ignitions::add);
        assertFalse(trails.isEmpty(), "guided motor owns its burn even inside an authored coast");
        assertTrue(ignitions.isEmpty());
        assertFalse(shot.ignitionShown);
        int count = trails.size();
        shot.guidedFlight.remaining = 0f;
        shot.lifetime = .8f;
        service.sample(List.of(shot), trails::add, ignitions::add);
        assertEquals(count, trails.size());
        assertTrue(ignitions.isEmpty());
    }

    @Test void frameCadenceDoesNotChangeEmissionCountOrStraightPathPositions() {
        var slow = samples(10);
        var fast = samples(144);
        assertEquals(21, slow.size());
        assertEquals(slow.size(), fast.size());
        for (int i = 0; i < slow.size(); i++) {
            assertEquals(slow.get(i).x(), fast.get(i).x(), 1e-4f);
            assertEquals(slow.get(i).y(), fast.get(i).y(), 1e-4f);
        }
    }

    @Test void noTrailSampleLandsBehindTheLaunchPoint() {
        // Westbound from x = 0: anything east of the muzzle would be drawn behind the shooter.
        for (var emission : samples(144)) {
            assertTrue(emission.x() <= 1e-4f, "a trail sample behind the shooter at x=" + emission.x());
        }
    }

    @Test void pausedAndResolvedShotsDoNotEmitAndTailRemainsBehindWestboundBody() {
        var service = new ProjectileTrailFxService();
        var shot = shot();
        var emissions = new ArrayList<ProjectileTrailFxService.Emission>();
        shot.lifetime = 4f;
        service.sample(List.of(shot), emissions::add);
        int count = emissions.size();
        var last = emissions.get(count - 1);
        assertTrue(last.x() > -4f, "westbound engine must be on the east/tail side");
        for (int i = 0; i < 100; i++) service.sample(List.of(shot), emissions::add);
        assertEquals(count, emissions.size());
        shot.flightResolved = true;
        shot.lifetime = 3f;
        service.sample(List.of(shot), emissions::add);
        assertEquals(count, emissions.size());
    }
}
