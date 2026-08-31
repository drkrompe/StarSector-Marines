package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A post's ring bounds straight-line distance from the post; the marine has to
 * walk. The two are the same number on open ground and nothing like each other
 * with a building in between, and the pickers measure only the first.
 *
 * <p>The detour test is deliberately the caller's choice, so the case that
 * must <em>not</em> be refused is pinned here too: a member going home or to a
 * heard noise takes the long way when the long way is the only way.
 */
public class ApproachBoundTest {

    private static final int W = 48;
    private static final int H = 24;
    private static final int WALL_X = 24;

    private static BattleSimulation walledSim(int doorY) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < H; y++) {
            if (y != doorY) grid.setWalkable(WALL_X, y, false);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static int[] path(BattleSimulation sim, int fx, int fy, int tx, int ty) {
        return GridPathfinder.findPath(sim.getGrid(), fx, fy, tx, ty);
    }

    @Test
    public void anUnreachableDestinationIsRefusedEvenUnbounded() {
        BattleSimulation sim = walledSim(-1);
        int[] p = path(sim, 18, 4, 25, 4);

        assertTrue(Paths.isEmpty(p), "fixture must be unreachable");
        assertFalse(ApproachBound.worthWalkingTo(18, 4, 25, 4, p, false),
                "no path is refused whether or not the detour bound is asked "
                        + "for — there is nothing to walk");
    }

    @Test
    public void theLongWayRoundIsRefusedForAnImprovement() {
        BattleSimulation sim = walledSim(22);
        int[] p = path(sim, 18, 4, 25, 4);

        assertTrue(p.length > 0, "fixture must be reachable the long way");
        assertFalse(ApproachBound.worthWalkingTo(18, 4, 25, 4, p, true),
                "a firing position seven cells away that costs a march around "
                        + "the building is not an improvement worth leaving the post for");
    }

    @Test
    public void theLongWayRoundIsTakenWhenItIsTheOnlyWay() {
        BattleSimulation sim = walledSim(22);
        int[] p = path(sim, 18, 4, 25, 4);

        assertTrue(ApproachBound.worthWalkingTo(18, 4, 25, 4, p, false),
                "going home or to a heard noise must take the long way when "
                        + "the long way is the only way");
    }

    @Test
    public void aRefusedFiringPositionParksTheMemberInsteadOfSearchingAgain() {
        // The refusal must clear the path. setPath stamps the repath throttle
        // only on a non-empty assignment, so leaving an empty path behind
        // re-runs a full-component search on the very next tick.
        BattleSimulation sim = walledSim(-1);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long member = sim.spawn(new EntitySpec("m0", Faction.MARINE,
                UnitType.MARINE, 18, 4).squad(squadId));

        boolean moved = PatrolMotion.moveToward(member, sim, 25, 4, true);

        assertFalse(moved, "an unreachable firing position must report refusal");
        assertTrue(Paths.isEmpty(sim.world().path(member)),
                "the refusal must leave the member parked with no path, not "
                        + "holding an empty one that dodges the repath throttle");
    }
}
