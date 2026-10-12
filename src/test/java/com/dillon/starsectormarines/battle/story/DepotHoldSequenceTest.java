package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.setup.DepotHoldSequence;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DepotHoldSequenceTest {
    @Test void survivingWalkerCanFinishButPendingRoadForcesPreventEarlyCompletion() {
        var grid = new NavigationGrid(100, 50);
        for (int y = 0; y < 50; y++) for (int x = 0; x < 100; x++) grid.setWalkableFloor(x, y);
        try (var sim = new BattleSimulation(grid, new CellTopology(100, 50))) {
            sim.spawn(new EntitySpec("Wayward", Faction.MARINE, UnitType.HEAVY_MECH, 25, 25).mechVariant(MechVariant.HOUND));
            var room = new PointOfInterest(PointOfInterest.Kind.DEPOT, 20, 20, 30, 30, 19, 25, 25, 25);
            var forces = new ArrayList<List<EntitySpec>>();
            for (int g = 0; g < 4; g++) {
                var group = new ArrayList<EntitySpec>();
                for (int i = 0; i < 4; i++) group.add(new EntitySpec("raider-" + g + "-" + i,
                        Faction.DEFENDER, UnitType.MILITIA, 76 + i, 2 + g));
                forces.add(group);
            }
            sim.installDepotHold(room, forces);
            DepotHoldSequence hold = sim.getDepotHold();
            assertEquals(16, sim.pendingGroundArrivals(Faction.DEFENDER));
            for (int t = 0; t < 790; t++) hold.advance(sim);
            assertEquals(1, hold.progress().waves());
            assertEquals(8, sim.pendingGroundArrivals(Faction.DEFENDER));
            assertFalse(hold.isComplete());
            for (int t = 0; t < 1230; t++) hold.advance(sim);
            assertEquals(2, hold.progress().waves());
            assertEquals(0, sim.pendingGroundArrivals(Faction.DEFENDER));
            assertFalse(hold.isComplete(), "living attackers still contest mission completion");
            List<Long> enemies = new ArrayList<>();
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long id = sim.liveUnitAt(i);
                if (sim.identity().faction(id) == Faction.DEFENDER) enemies.add(id);
            }
            assertEquals(16, enemies.size());
            enemies.forEach(sim::takeOffTheField);
            hold.advance(sim);
            assertTrue(hold.isComplete(), "the surviving walker must not be trapped in an unwinnable hold");
            assertEquals(1, sim.getStoryBeats().stream().filter(e -> e.id().equals(DepotHoldSequence.FIRST)).count());
            assertEquals(1, sim.getStoryBeats().stream().filter(e -> e.id().equals(DepotHoldSequence.SECOND)).count());
        }
    }
}
