package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmPressureBehaviorTest {

    @Test
    void activeRegisteredEvacueeOutranksCloserMarineAndAmbientCivilian() {
        BattleSimulation sim = simulation();
        long runner = runner(sim, 2, 2);
        long ambient = civilian(sim, "ambient", 3, 2);
        marine(sim, 4, 2);
        long evacuee = civilian(sim, "evacuee", 10, 2);
        sim.getCivilianEvacuationTracker().register(evacuee);

        long target = SwarmPressureBehavior.selectTarget(runner, sim);

        assertEquals(evacuee, target);
        assertFalse(target == ambient);
    }

    @Test
    void fallsBackToNearestMarineWhenNoActiveEvacueeRemains() {
        BattleSimulation sim = simulation();
        long runner = runner(sim, 2, 2);
        long farther = marine(sim, 9, 2);
        long nearer = marine(sim, 5, 2);
        long evacuee = civilian(sim, "evacuee", 3, 2);
        sim.getCivilianEvacuationTracker().register(evacuee);
        sim.getCivilianEvacuationTracker().markEvacuated(evacuee);

        assertEquals(nearer,
                SwarmPressureBehavior.selectTarget(runner, sim));
        assertFalse(farther == nearer);
    }

    @Test
    void pathsTowardDistantTargetAndAppliesOnlyContactDamage() {
        BattleSimulation sim = simulation();
        long runner = runner(sim, 2, 2);
        long evacuee = civilian(sim, "evacuee", 8, 2);
        sim.getCivilianEvacuationTracker().register(evacuee);
        float initialHp = sim.world().hp(evacuee);

        SwarmPressureBehavior.INSTANCE.update(runner, sim);

        int[] path = sim.movement().path(runner);
        assertFalse(Paths.isEmpty(path));
        assertEquals(8, Paths.destX(path));
        assertEquals(2, Paths.destY(path));
        assertEquals(initialHp, sim.world().hp(evacuee));

        sim.world().setCellPos(runner, 7, 2);
        sim.clearPath(runner);
        SwarmPressureBehavior.INSTANCE.update(runner, sim);

        assertTrue(sim.world().hp(evacuee) < initialHp);
        assertTrue(sim.combat().cooldownTimer(runner) > 0f);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(16, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(16, 12));
    }

    private static long runner(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, x, y).role(UnitRole.SWARM_PRESSURE));
    }

    private static long civilian(BattleSimulation sim, String name,
                                  int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.CIVILIAN,
                UnitType.CIVILIAN, x, y).role(UnitRole.VIP));
    }

    private static long marine(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, x, y));
    }
}
