package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
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
