package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.goap.action.AbstractZoneAction.FiringApproach;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The firing-position pickers score walkability, leash distance, range and
 * line of fire, and never ask whether a path exists — so a cell with a clear
 * shot from behind a sealed wall is an ordinary answer from them. Taking one
 * froze a committed member in place: an empty path moves nobody, and
 * {@code setPath} stamps the repath throttle only on a non-empty assignment,
 * so the same full-component A* ran again every tick.
 *
 * <p>Both refusals about a <em>chosen</em> cell are asked of
 * {@code AbstractZoneAction.worthWalkingTo}, which is the boundary itself
 * rather than an action wrapped around it. The third answer — the picker
 * finding no cell at all — is decided one level up, so it is asked of
 * {@code advanceToReachableFiringPosition} directly.
 */
public class FiringPositionReachabilityTest {

    private static final int W = 48;
    private static final int H = 24;
    private static final int WALL_X = 24;

    /** Open floor, one full-height wall, optionally holed at {@code doorY}. */
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

    private static int[] pathTo(BattleSimulation sim, int fromX, int fromY, int[] spot) {
        return GridPathfinder.findPath(sim.getGrid(), fromX, fromY, spot[0], spot[1]);
    }

    @Test
    public void aFiringPositionBehindASealedWallIsRefused() {
        // No door: the east half is walkable and has a clear shot at a target
        // over there, and no member on the west half can ever stand on it.
        BattleSimulation sim = walledSim(-1);
        int[] spot = {25, 4};

        int[] path = pathTo(sim, 18, 4, spot);

        assertTrue(path.length == 0, "fixture must actually be unreachable");
        assertFalse(AbstractZoneAction.worthWalkingTo(18, 4, spot, path),
                "an unreachable firing position must be refused, not walked at "
                        + "with an empty path every tick");
    }

    @Test
    public void aFiringPositionAroundTheLongWayIsRefused() {
        // One door in the far south. The spot is seven cells away in a
        // straight line and a march around the whole wall on foot.
        BattleSimulation sim = walledSim(22);
        int[] spot = {25, 4};

        int[] path = pathTo(sim, 18, 4, spot);

        assertTrue(path.length > 0, "fixture must be reachable the long way");
        assertFalse(AbstractZoneAction.worthWalkingTo(18, 4, spot, path),
                "a firing position seven cells away that costs a march around "
                        + "the building is not the bounded improvement a leash is for");
    }

    @Test
    public void anOrdinaryFiringPositionIsStillTaken() {
        // Same side of the wall, nothing in the way: the ordinary case must
        // not be refused by the detour bound.
        BattleSimulation sim = walledSim(22);
        int[] spot = {14, 4};

        int[] path = pathTo(sim, 18, 4, spot);

        assertTrue(path.length > 0, "fixture must be reachable");
        assertTrue(AbstractZoneAction.worthWalkingTo(18, 4, spot, path),
                "a clear walk to a nearby firing position must still be taken");
    }

    @Test
    public void steppingAroundAnObstacleIsStillTaken() {
        // A one-cell jog around a crate is a detour in ratio terms only
        // because the straight line is short — that is what the slack is for.
        BattleSimulation sim = walledSim(22);
        sim.getGrid().setWalkable(19, 4, false);
        int[] spot = {20, 4};

        int[] path = pathTo(sim, 18, 4, spot);

        assertTrue(path.length > 0, "fixture must be reachable");
        assertTrue(AbstractZoneAction.worthWalkingTo(18, 4, spot, path),
                "a short jog around one blocked cell must not be refused");
    }

    /**
     * The three answers the approach can give, asked of the function that
     * decides them.
     *
     * <p>Two of them are about a cell that exists and one is about there being
     * no cell at all, and the last was folded into "no path" for a while. The
     * costs differ by more than a name: no path means hold and fight, while no
     * position means the member has committed to something it cannot reach
     * from inside its own leash — which under prosecution is the whole squad
     * standing still for as long as it can see the contact.
     */
    @Test
    public void aSearchThatFoundNothingIsNotTheSameAsACellWithNoPath() {
        BattleSimulation sealed = walledSim(-1);
        long member = sealed.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 18, 4));

        assertEquals(FiringApproach.NO_POSITION,
                AbstractZoneAction.advanceToReachableFiringPosition(member, sealed, null),
                "a null answer from the picker means no cell inside the leash has "
                        + "both the reach and the line of fire, which is not a cell "
                        + "the member merely cannot walk to");
        assertEquals(FiringApproach.UNREACHABLE,
                AbstractZoneAction.advanceToReachableFiringPosition(member, sealed,
                        new int[]{25, 4}),
                "a cell behind a sealed wall is a real position with no path to it");
    }

    @Test
    public void aPositionAroundTheLongWayReportsTheWalkRatherThanNoPosition() {
        BattleSimulation open = walledSim(22);
        long member = open.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 18, 4));

        assertEquals(FiringApproach.NOT_WORTH_THE_WALK,
                AbstractZoneAction.advanceToReachableFiringPosition(member, open,
                        new int[]{25, 4}),
                "a member that can still move perfectly well must say so, so the "
                        + "caller carries on toward the objective");
    }

    @Test
    public void theTwoRefusalsAreDistinguished() {
        // The committed branch answers them differently: no path means hold
        // and fight from here, a path not worth walking means carry on to the
        // objective. Collapsing them sent squads that had decided to fight
        // marching past the enemy, which the Conquest matrix charged for.
        BattleSimulation sealed = walledSim(-1);
        int[] spot = {25, 4};
        assertEquals(0, pathTo(sealed, 18, 4, spot).length,
                "sealed fixture must be unreachable");

        BattleSimulation open = walledSim(22);
        int[] longWay = pathTo(open, 18, 4, spot);
        assertTrue(longWay.length > 0, "door fixture must be reachable");
        assertFalse(AbstractZoneAction.worthWalkingTo(18, 4, spot, longWay),
                "the long way round must read as not worth the walk, which is "
                        + "a different refusal from having no path at all");
    }
}
