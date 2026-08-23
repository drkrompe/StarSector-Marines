package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitUpdateProfilerAttributionTest {

    @Test
    void swarmPressureHasDedicatedBehaviorBucket() {
        assertEquals(TickInnerProfile.Bucket.BEHAVIOR_SWARM_PRESSURE,
                UnitUpdateSystem.innerBucketForRole(UnitRole.SWARM_PRESSURE));
        assertEquals(TickInnerProfile.Bucket.BEHAVIOR_COMBATANT,
                UnitUpdateSystem.innerBucketForRole(UnitRole.COMBATANT));
    }

    @Test
    void parallelDispatchMergesSwarmPathfindingAttribution() {
        int width = 24;
        int height = 20;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(width, height));
        sim.spawn(new EntitySpec("runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 12, 10)
                .role(UnitRole.SWARM_PRESSURE));
        for (int y = 9; y <= 11; y++) {
            for (int x = 11; x <= 13; x++) {
                if (x != 12 || y != 10) grid.setWalkable(x, y, false);
            }
        }

        sim.advance(BattleSimulation.TICK_DT);

        TickInnerProfile profile = sim.getTickInnerProfile();
        assertEquals(1, profile.countOf(
                TickInnerProfile.Bucket.BEHAVIOR_SWARM_PRESSURE));
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        assertEquals(profile.countOf(TickInnerProfile.Bucket.PATHFIND),
                profile.countOf(TickInnerProfile.Bucket.SWARM_PATHFIND));
        assertEquals(profile.nanosOf(TickInnerProfile.Bucket.PATHFIND),
                profile.nanosOf(TickInnerProfile.Bucket.SWARM_PATHFIND));
    }
}
