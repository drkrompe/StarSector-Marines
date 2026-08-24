package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitDestinationSpatialIndexTest {

    @Test
    void rebuildPreservesRosterDenseOrderAndFiltersNonDestinations() {
        BattleSimulation sim = openArena(48, 48);
        try {
            long released = spawn(sim, 1, 1);
            long middle = spawn(sim, 2, 2);
            long tail = spawn(sim, 3, 3);
            sim.setPath(released, new int[]{1, 1, 30, 30});
            sim.setPath(middle, new int[]{2, 2, 30, 30});
            sim.setPath(tail, new int[]{3, 3, 30, 30});

            // Swap-and-pop moves tail into released's dense slot. The rebuild's
            // result must follow that roster order, not archetype table row order.
            sim.getRoster().release(released);
            long sameCell = spawn(sim, 4, 4);
            spawn(sim, 5, 5); // no path
            sim.spawn(new EntitySpec("turret", Faction.DEFENDER,
                    UnitType.TURRET, 6, 6).health(50f));
            sim.setPath(sameCell, new int[]{4, 4});

            UnitDestinationSpatialIndex index = sim.getDestIndex();
            index.rebuild(sim.getRoster());

            LongBucket found = new LongBucket();
            index.gather(sim.getRoster(), 30.5f, 30.5f, 1f, found);

            assertEquals(2, found.size);
            assertEquals(tail, found.ids[0]);
            assertEquals(middle, found.ids[1]);
        } finally {
            sim.close();
        }
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static long spawn(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("unit-" + sim.liveUnitCount(),
                Faction.MARINE, UnitType.MARINE, x, y));
    }
}
