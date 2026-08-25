package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
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
        int reinforcementSquadId = -1;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.DEFENDER) {
                defenders++;
                reinforcementSquadId = sim.squad().squadId(unit);
            }
        }
        assertTrue(defenders > 0, "walk-in spawned at least one defender");
        CommandDirective owner = sim.getSquadCommandDirective(reinforcementSquadId);
        assertEquals(CommandAuthority.REINFORCEMENT, owner.authority());
        assertEquals("reinforcement", owner.issuer());
        assertTrue(owner.ownsSquad());
        assertFalse(owner.ownsAssignment());
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (sim.identity().faction(u) != Faction.DEFENDER) continue;
            assertEquals(0, topo.getBuildingId(sim.world().cellX(u), sim.world().cellY(u)),
                    "no walk-in unit may spawn inside a building footprint");
            assertTrue(sim.getGrid().isWalkable(sim.world().cellX(u), sim.world().cellY(u)), "spawn cell is walkable");
        }
    }
}
