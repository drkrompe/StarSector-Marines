package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctDefence;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fortress wall's towers stand out from the wall toward the attacker, on either axis, and no forward bunker stands across a tower's arc. */
class FortressWallStamperTest {

    private static final int W = 160;
    private static final int H = 160;
    /** The fortress biome's attacker-facing edge, on whichever axis is played. */
    private static final int EDGE = 60;

    private static GenContext field() {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        return new GenContext(grid, topology, new Random(11L), W, H, 11L);
    }

    @Test
    void everyTowerStandsOutsideItsWallWithNoBunkerAcrossItsArc() {
        for (TraversalAxis axis : TraversalAxis.values()) {
            for (long seed = 1; seed <= 4; seed++) {
                GenContext ctx = field();
                Random rng = new Random(seed);
                if (axis == TraversalAxis.SOUTH_TO_NORTH) {
                    FortressWallStamper.stampSouthToNorth(ctx.grid, ctx.topology, new int[]{0, EDGE, W - 1, H - 1},
                            null, new boolean[W][H], null, null, ctx.tactical, W, H, rng);
                } else {
                    FortressWallStamper.stampWestToEast(ctx.grid, ctx.topology, new int[]{EDGE, 0, W - 1, H - 1},
                            null, new boolean[W][H], null, null, ctx.tactical, W, H, rng);
                }
                int[] out = FortressWallStamper.towerFacing(axis);
                List<TacticalNode> towers = new ArrayList<>();
                for (TacticalNode node : ctx.tactical) if (node.kind == TacticalNode.Kind.HEAVY_TOWER) towers.add(node);
                assertTrue(towers.size() >= 3, axis + " stamped its corner and mid-line towers");
                int wallLine = EDGE + 12;
                for (TacticalNode tower : towers) {
                    int depth = axis == TraversalAxis.SOUTH_TO_NORTH ? tower.anchorY : tower.anchorX;
                    assertTrue(depth < wallLine, axis + " tower at " + tower.anchorX + "," + tower.anchorY
                            + " stands behind the wall line " + wallLine);
                    assertTrue(PrecinctDefence.openArc(ctx, tower.anchorX, tower.anchorY, out[0], out[1], 2),
                            axis + " seed " + seed + ": tower at " + tower.anchorX + "," + tower.anchorY
                                    + " has a wall or bunker in its field of fire");
                }
            }
        }
    }

    @Test
    void aWallOrHouseInAGunsArcMasksItAndLowCoverDoesNot() {
        GenContext ctx = field();
        // A wall across the arc of a gun two cells ahead masks it; the same wall beside the arc does not.
        for (int x = 40; x <= 44; x++) ctx.grid.setWalkable(x, 46, false);
        assertFalse(PrecinctDefence.openArc(ctx, 42, 50, 0, -1, 2));
        assertTrue(PrecinctDefence.openArc(ctx, 60, 50, 0, -1, 2));
        // Low cover a gun sees over is no mask.
        for (int x = 58; x <= 62; x++) ctx.grid.setSeeThrough(x, 46, true);
        for (int x = 58; x <= 62; x++) ctx.grid.setWalkable(x, 46, false);
        assertTrue(PrecinctDefence.openArc(ctx, 60, 50, 0, -1, 2));
        // A house's rooms, seen through its windows, are not open ground either.
        for (int x = 78; x <= 82; x++) ctx.topology.setGroundKind(x, 44, GroundKind.INDOOR);
        assertFalse(PrecinctDefence.openArc(ctx, 80, 50, 0, -1, 2));
    }
}
