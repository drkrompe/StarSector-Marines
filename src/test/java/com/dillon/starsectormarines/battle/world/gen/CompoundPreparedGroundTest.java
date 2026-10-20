package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sandbag apron at a compound's door: where it goes, what it leaves open, and that it is always a full run. On a
 * 30x30 field with one building (interior 10..19 x 12..19, wall ring round it) and a door in its south wall at x=14.
 */
class CompoundPreparedGroundTest {

    private static final int W = 30, H = 30;
    private static final CompoundPreparedGround.KeepClear NOTHING = (x, y) -> false;

    private record Field(NavigationGrid grid, CellTopology topology, TacticalNode node) { }

    private static Field field(int doorWidth) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        for (int x = 9; x <= 20; x++) {
            grid.setWalkable(x, 11, false);
            grid.setWalkable(x, 20, false);
        }
        for (int y = 11; y <= 20; y++) {
            grid.setWalkable(9, y, false);
            grid.setWalkable(20, y, false);
        }
        for (int w = 0; w < doorWidth; w++) {
            grid.setWalkableFloor(14 + w, 11);
            grid.setDoorway(14 + w, 11, true);
        }
        TacticalNode node = new TacticalNode(TacticalNode.Kind.BARRACKS, 14, 15, 10, 12, 19, 19, Faction.DEFENDER, 60, 6);
        return new Field(grid, topology, node);
    }

    private static CompoundPreparedGround.Apron southApron(Field f) {
        for (CompoundPreparedGround.Entrance e : CompoundPreparedGround.candidates(f.grid, f.topology, f.node, 0, -1)) {
            CompoundPreparedGround.Apron apron = CompoundPreparedGround.plan(f.grid, f.topology, e, NOTHING);
            if (apron != null) return apron;
        }
        return null;
    }

    @Test
    void aDoorFacingTheAttackGetsACourseStandingOffItWithItsLaneOpen() {
        Field f = field(1);
        CompoundPreparedGround.Apron apron = southApron(f);
        assertNotNull(apron);
        assertEquals(14, apron.entrance().x());
        assertEquals(11, apron.entrance().y());
        Set<Long> bags = new HashSet<>();
        for (CompoundPreparedGround.Bag bag : apron.bags()) bags.add(key(bag.x(), bag.y()));
        for (int d = 1; d <= 4; d++) assertFalse(bags.contains(key(14, 11 - d)), "the door's lane stays open at " + d);
        for (int x : new int[]{11, 12, 13, 15, 16, 17}) assertTrue(bags.contains(key(x, 8)), "course three out at x=" + x);
        assertTrue(bags.contains(key(11, 9)) && bags.contains(key(17, 9)), "each wing turns back toward the wall");
        assertEquals("corner-sw", CompoundPreparedGround.artName(bagAt(apron, 11, 8)));
        assertEquals("s", CompoundPreparedGround.artName(bagAt(apron, 13, 8)));
        assertEquals("e", CompoundPreparedGround.artName(bagAt(apron, 17, 9)));
        int[] first = apron.behind().get(0);
        assertEquals(1, Math.abs(first[0] - 14), "the lookout's first cell is beside the gap");
        assertEquals(9, first[1]);
    }

    @Test
    void whatItLaysIsAlwaysAFullRun() {
        Field f = field(1);
        CompoundPreparedGround.Apron apron = southApron(f);
        Set<Long> course = new HashSet<>();
        for (CompoundPreparedGround.Bag bag : apron.bags()) course.add(key(bag.x(), bag.y()));
        assertTrue(CompoundPreparedGround.members(f.grid, course) >= FightingPositionIndex.MIN_RUN_CELLS);
    }

    @Test
    void groundTheCallerKeepsClearRefusesTheWholeCourse() {
        Field f = field(1);
        CompoundPreparedGround.Entrance door = CompoundPreparedGround.entrances(f.grid, 10, 12, 19, 19).get(0);
        // A road along the course row at every standoff: no partial apron, nothing at all.
        CompoundPreparedGround.KeepClear road = (x, y) -> x == 12 && y <= 9;
        assertNull(CompoundPreparedGround.plan(f.grid, f.topology, door, road));
    }

    @Test
    void aVehicleWideOpeningGetsNone() {
        Field f = field(3);
        List<CompoundPreparedGround.Entrance> doors = CompoundPreparedGround.entrances(f.grid, 10, 12, 19, 19);
        assertEquals(1, doors.size());
        assertEquals(3, doors.get(0).width());
        assertNull(CompoundPreparedGround.plan(f.grid, f.topology, doors.get(0), NOTHING));
    }

    @Test
    void aDoorFacingAwayFromTheAttackIsNeverACandidate() {
        Field f = field(1);
        for (CompoundPreparedGround.Entrance e : CompoundPreparedGround.candidates(f.grid, f.topology, f.node, 0, 1)) {
            assertTrue(e.fy() >= 0, "an apron facing the attack from the north never sits at the south door");
        }
    }

    @Test
    void aFaceWithNoDoorStillOffersItsApproach() {
        Field f = field(1);
        List<CompoundPreparedGround.Entrance> west = CompoundPreparedGround.candidates(f.grid, f.topology, f.node, -1, 0);
        assertFalse(west.isEmpty());
        CompoundPreparedGround.Entrance first = west.get(0);
        assertEquals(-1, first.fx());
        assertEquals(8, first.x(), "the first open cell past the west wall");
        assertNotNull(CompoundPreparedGround.plan(f.grid, f.topology, first, NOTHING));
    }

    private static CompoundPreparedGround.Bag bagAt(CompoundPreparedGround.Apron apron, int x, int y) {
        for (CompoundPreparedGround.Bag bag : apron.bags()) if (bag.x() == x && bag.y() == y) return bag;
        throw new AssertionError("no bag at " + x + "," + y);
    }

    private static long key(int x, int y) {
        return PreparedGround.key(x, y);
    }
}
