package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-4 retrofit coverage for {@link WalkInMeans}: its perimeter spawn-cell
 * and squad-member selection now route through {@link LandingZoneScorer}, so
 * walk-in infantry never spawn on a walkable building-edge cell.
 */
public class WalkInMeansTest {

    private static final int W = 12;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static TacticalNode barracks(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.BARRACKS, x, y,
                x - 1, y - 1, x + 1, y + 1, Faction.DEFENDER, 50, 4);
    }

    @Test
    public void walkInSpawnsOutsideEdgeBuildings() {
        BattleSimulation sim = openSim();
        sim.getCompoundService().register(barracks(2, 2)); // alive BARRACKS → gate passes
        CellTopology topo = sim.getTopology();
        // Building straddling the defender (north) edge around the rally column.
        for (int y = 9; y <= 11; y++) {
            for (int x = 4; x <= 6; x++) topo.setBuildingId(x, y, 1);
        }

        WalkInMeans means = new WalkInMeans(TraversalAxis.SOUTH_TO_NORTH);
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 5, 8); // rally column under the building

        assertTrue(means.canFulfill(sim, req));
        means.dispatch(sim, req);

        long defenders = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.liveUnitAt(i).faction == Faction.DEFENDER) defenders++;
        }
        assertTrue(defenders > 0, "walk-in spawned at least one defender");
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Unit u = sim.liveUnitAt(i);
            if (u.faction != Faction.DEFENDER) continue;
            assertEquals(0, topo.getBuildingId(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId)),
                    "no walk-in unit may spawn inside a building footprint");
            assertTrue(sim.getGrid().isWalkable(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId)), "spawn cell is walkable");
        }
    }
}
