package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two halves of letting an order that names a room close its own
 * firing gap: that it happens at all, and that it cannot happen outside the
 * room.
 *
 * <p>{@link AbstractZoneAction#advanceIntoZone}'s objective-anchored firing
 * position was restricted to an order with no target zone — in practice the
 * attack move — because the search scores walkability, leash distance, range
 * and line of fire and knows nothing about rooms. Instrumented over a Conquest
 * matrix, that restriction refused 271,732 of the 466,940 member-ticks that
 * reached the gate, and 39,342 of the refusals had a legal firing cell inside
 * their own leash. Containment in the search is what makes lifting the
 * restriction safe.
 *
 * <h2>The map</h2>
 * <p>Two rooms on either side of a wall with one door, both on row {@link #ROW}
 * so the only line between them runs through that door. The target sits deep in
 * the west room; the member stands far east in the east room, 26 cells from it
 * with a clear line down the row and a 22-cell rifle. Its order names the west
 * room, so the anchor is inside the west room and the leash reaches back
 * through the door into the east one.
 *
 * <p>That geometry is what makes the constraint bite rather than merely hold: a
 * cell just inside the east room is both legal and nearer to the member than
 * anything in the west room, so the unconstrained search prefers it. The member
 * would improve its angle by declining to enter the room it was sent to.
 */
public class ZoneContainedFiringPositionTest {

    private static final int W = 32;
    private static final int H = 25;
    private static final int WALL_X = 12;
    private static final int ROW = 12;

    /** Deep in the west room, and 26 cells from the member — four beyond its rifle. */
    private static final int TARGET_X = 2;
    /** Inside the west room: the cell the order actually names. */
    private static final int DEST_X = 8;
    /**
     * The behavioural loop sends the member to the room's south-west corner
     * rather than to the cell the target stands on. On the sight row the
     * contact is astride the route, {@code assessAdvanceThreat} commits the
     * squad, and the committed firing line — a different branch with a
     * different anchor — is what moves it. This clause is the uncommitted one.
     */
    private static final int DEST_Y = 20;
    /** Far side of the east room. */
    private static final int MEMBER_X = 28;
    private static final float FIELD_RIFLE_RANGE = 22f;

    private static BattleSimulation twoRooms() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < H; y++) grid.setWalkable(WALL_X, y, false);
        grid.setWalkableFloor(WALL_X, ROW);
        grid.setDoorway(WALL_X, ROW, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad marine(BattleSimulation sim) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long member = sim.spawn(new EntitySpec("m0", Faction.MARINE,
                UnitType.MARINE, MEMBER_X, ROW).squad(squadId));
        sim.world().setAttackRange(member, FIELD_RIFLE_RANGE);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = MEMBER_X + 0.5f;
        squad.centroidY = ROW + 0.5f;
        return squad;
    }

    private static int westZone(BattleSimulation sim) {
        return sim.getZoneGraph().zoneIdAt(DEST_X, ROW);
    }

    private static int eastZone(BattleSimulation sim) {
        return sim.getZoneGraph().zoneIdAt(MEMBER_X, ROW);
    }

    /**
     * The geometry itself, asserted before anything depends on it. A test whose
     * map quietly became one zone would pass every containment claim below
     * while proving none of them.
     */
    @Test
    public void theTwoRoomsAreTwoZonesAndTheThresholdIsNeither() {
        BattleSimulation sim = twoRooms();
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(westZone(sim) >= 0, "the west room is a zone");
        assertTrue(eastZone(sim) >= 0, "the east room is a zone");
        assertNotEquals(westZone(sim), eastZone(sim),
                "the wall separates them; if it did not, containment could not be tested here");
        int threshold = sim.getZoneGraph().zoneIdAt(WALL_X, ROW);
        assertNotEquals(westZone(sim), threshold,
                "the threshold is not the west room");
        assertNotEquals(eastZone(sim), threshold,
                "nor the east room — a doorway belongs to neither, so a constraint naming "
                        + "either one refuses it, and nobody takes up a firing position in a door");
    }

    /**
     * The guard, asked of the search directly. The unconstrained answer is a
     * cell in the room the member is standing in, because it is nearer; the
     * constrained answer is inside the room the order names.
     */
    @Test
    public void theSearchRefusesAFiringCellOutsideTheRoomTheOrderNames() {
        BattleSimulation sim = twoRooms();
        Squad squad = marine(sim);
        sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, TARGET_X, ROW));
        sim.advance(BattleSimulation.TICK_DT);
        long member = squad.leaderId;
        long target = sim.getTacticalScoring().findBestTarget(member);
        assertNotEquals(0L, target);

        TacticalScoring scoring = sim.getTacticalScoring();
        int[] loose = scoring.findFiringPositionWithin(member, target, DEST_X, ROW,
                AbstractZoneAction.OBJECTIVE_FIRING_LEASH);
        assertNotNull(loose, "the unconstrained search finds something, or this map proves nothing");
        assertEquals(eastZone(sim), sim.getZoneGraph().zoneIdAt(loose[0], loose[1]),
                "unconstrained, the nearest legal cell is in the room the member is already in — "
                        + "so the constraint below is doing work rather than agreeing");

        int[] contained = scoring.findFiringPositionWithin(member, target, DEST_X, ROW,
                AbstractZoneAction.OBJECTIVE_FIRING_LEASH, westZone(sim));
        assertNotNull(contained, "a legal cell exists inside the named room too");
        assertEquals(westZone(sim), sim.getZoneGraph().zoneIdAt(contained[0], contained[1]),
                "contained: the chosen cell is inside the room the order names");
        assertTrue(TacticalScoring.cellDistance(contained[0], contained[1], DEST_X, ROW)
                        <= AbstractZoneAction.OBJECTIVE_FIRING_LEASH,
                "and still inside the order's own leash");
    }

    /** A zone that holds no legal cell yields nothing rather than leaking one. */
    @Test
    public void aRoomWithNoLegalCellYieldsNothingRatherThanTheNextRoomsBest() {
        BattleSimulation sim = twoRooms();
        Squad squad = marine(sim);
        sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, TARGET_X, ROW));
        sim.advance(BattleSimulation.TICK_DT);
        long member = squad.leaderId;
        long target = sim.getTacticalScoring().findBestTarget(member);

        // A zone id nothing on this map carries. Every candidate fails it, and
        // the search must answer "nowhere" rather than fall back to the best
        // cell it had before the constraint was applied.
        assertNull(sim.getTacticalScoring().findFiringPositionWithin(
                member, target, DEST_X, ROW,
                AbstractZoneAction.OBJECTIVE_FIRING_LEASH, 9999));
    }

    /**
     * The widening, through the real caller. Before this change an order naming
     * a room was excluded from the clause outright, so this member marched its
     * route with a clear shot on something it could not reach.
     */
    @Test
    public void anApproachToANamedRoomClosesTheGapInsideThatRoom() {
        BattleSimulation sim = twoRooms();
        Squad squad = marine(sim);
        sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, TARGET_X, ROW));
        sim.advance(BattleSimulation.TICK_DT);
        long member = squad.leaderId;
        int west = westZone(sim);

        new ProbeZoneAction(west).advance(member, squad, sim, DEST_X, DEST_Y);

        assertFalse(squad.advanceEngageCommitted,
                "the route threat must not be what is moving this member");
        int[] path = sim.world().path(member);
        assertFalse(Paths.isEmpty(path), "the member moves rather than marching on obliviously");
        int spotX = Paths.destX(path);
        int spotY = Paths.destY(path);
        assertTrue(spotX != DEST_X || spotY != DEST_Y,
                "it walks to a firing cell, not to the plain objective cell");
        assertEquals(west, sim.getZoneGraph().zoneIdAt(spotX, spotY),
                "and that cell is inside the room it was sent to");
    }

    /**
     * The same map with an order that names no room keeps the old answer — the
     * nearest legal cell, wherever it is. Containment applies to an order that
     * has a room to be contained by, and an attack move does not.
     */
    @Test
    public void anOrderThatNamesNoRoomIsStillUnconstrained() {
        BattleSimulation sim = twoRooms();
        Squad squad = marine(sim);
        sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, TARGET_X, ROW));
        sim.advance(BattleSimulation.TICK_DT);
        long member = squad.leaderId;

        new ProbeZoneAction(-1).advance(member, squad, sim, DEST_X, ROW);

        int[] path = sim.world().path(member);
        assertFalse(Paths.isEmpty(path));
        assertEquals(eastZone(sim),
                sim.getZoneGraph().zoneIdAt(Paths.destX(path), Paths.destY(path)),
                "unconstrained, the nearest legal cell wins and it is in the member's own room");
    }

    private static final class ProbeZoneAction extends AbstractZoneAction {
        private ProbeZoneAction(int targetZoneId) { super(targetZoneId); }
        @Override public String name() { return "ProbeZoneAction"; }
        @Override public ActionStatus execute(long member, Squad squad, BattleControl sim) {
            return ActionStatus.RUNNING;
        }

        private void advance(long member, Squad squad, BattleControl sim, int destX, int destY) {
            advanceIntoZone(member, squad, sim, destX, destY, true);
        }
    }
}
