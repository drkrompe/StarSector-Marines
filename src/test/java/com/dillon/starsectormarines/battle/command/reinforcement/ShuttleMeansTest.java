package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-4 retrofit coverage for {@link ShuttleMeans}: its LZ selection now
 * routes through {@link LandingZoneScorer}, so an air drop never sets down
 * inside a building even when the rally hint lands squarely on one.
 */
public class ShuttleMeansTest {

    private static final int W = 12;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static TacticalNode commandPost(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.COMMAND_POST, x, y,
                x - 1, y - 1, x + 1, y + 1, Faction.DEFENDER, 50, 4);
    }

    @Test
    public void shuttleLandsOutsideBuildingsWhenRallyIsInside() {
        BattleSimulation sim = openSim();
        // Alive COMMAND_POST so the shuttle supply gate passes.
        sim.getCompoundService().register(commandPost(2, 2));
        // Building footprint straddling the rally hint.
        CellTopology topo = sim.getTopology();
        for (int y = 4; y <= 6; y++) {
            for (int x = 4; x <= 6; x++) topo.setBuildingId(x, y, 1);
        }

        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH);
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 5, 5); // rally inside the building

        assertTrue(means.canFulfill(sim, req), "open ground exists outside the building near the rally");
        means.dispatch(sim, req);

        long[] airIds = sim.getAirEntityIds();
        assertEquals(1, airIds.length, "one shuttle dispatched");
        ShuttleMission mission = sim.world().mission(airIds[0]);
        int lzX = (int) mission.lzX;
        int lzY = (int) mission.lzY;
        assertEquals(0, topo.getBuildingId(lzX, lzY), "LZ must be outside any building footprint");
        assertTrue(sim.getGrid().isWalkable(lzX, lzY), "LZ must be walkable");
    }
}
