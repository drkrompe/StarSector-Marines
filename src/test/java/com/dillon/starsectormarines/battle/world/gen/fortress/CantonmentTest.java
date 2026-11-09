package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The garrison laid in rows ({@code raid-mission.md}, Garrison district): what it owes by market and security, that
 * its rows seat on their own square from any gate side, never on a road, that every door opens on open ground, and
 * that the gate is where the town's road comes in.
 */
class CantonmentTest {

    private static final FortressProgram GUARDED_FIVE = FortressProgram.cantonment(5, 1, 1);

    @Test
    void theMarketSizesTheRowsAndSecurityTheirGuard() {
        FortressProgram small = FortressProgram.cantonment(4, 0, 1);
        FortressProgram large = FortressProgram.cantonment(7, 2, 1);
        assertTrue(large.countOf(RoomPurpose.BARRACKS) > small.countOf(RoomPurpose.BARRACKS));
        assertTrue(large.countOf(RoomPurpose.CONTROL_ROOM) > small.countOf(RoomPurpose.CONTROL_ROOM));
        assertTrue(large.countOf(RoomPurpose.ARMORY) > small.countOf(RoomPurpose.ARMORY));
        assertEquals(0, small.countOf(RoomPurpose.KEEP_ENTRY), "an open garrison keeps no guardroom");
        assertEquals(1, large.countOf(RoomPurpose.KEEP_ENTRY));
        assertEquals(1, large.countOf(RoomPurpose.KEEP_THRONE), "one headquarters");
        assertTrue(large.cantonment() && large.with(RoomPurpose.BARRACKS, 2).cantonment(),
                "a trimmed cantonment is still laid in rows");
    }

    @Test
    void everyPlanSeatsItsWholeProgramOnItsOwnSquareFromEveryGate() {
        for (Cantonment.Plan plan : Cantonment.Plan.values()) {
            // The square a claim is given: the rows' side and the slack for roads and the flight line.
            int side = Cantonment.side(GUARDED_FIVE, plan) + Cantonment.ROAD_SLACK;
            for (Side gate : Side.values()) {
                boolean[][] open = open(side + 10, side + 10);
                Cantonment.Laid laid = Cantonment.lay(open, 5, 5, 4 + side, 4 + side, gate,
                        5 + side / 2, GUARDED_FIVE, plan);
                assertTrue(laid.unplaced().isEmpty(), plan + " from " + gate + " left " + laid.unplaced());
                assertNotNull(laid.airfield(), plan + " from " + gate + " has its flight line");
                assertApart(laid, open);
            }
        }
    }

    @Test
    void noBuildingStandsOnARoadThroughThePlace() {
        int side = Cantonment.side(GUARDED_FIVE, Cantonment.Plan.PARADE) + 20;
        boolean[][] ground = open(side, side);
        for (int x = 0; x < side; x++) {
            for (int y = side / 3; y < side / 3 + 7; y++) ground[x][y] = false;   // a road across the place
        }
        Cantonment.Laid laid = Cantonment.lay(ground, 0, 0, side - 1, side - 1, Side.WEST, side / 2,
                GUARDED_FIVE, Cantonment.Plan.PARADE);
        assertFalse(laid.rooms().isEmpty());
        assertApart(laid, ground);
    }

    @Test
    void theHeadquartersFacesTheGateAcrossTheParadeGround() {
        int side = Cantonment.side(GUARDED_FIVE, Cantonment.Plan.PARADE);
        Cantonment.Laid laid = Cantonment.lay(open(side, side), 0, 0, side - 1, side - 1, Side.SOUTH, side / 2,
                GUARDED_FIVE, Cantonment.Plan.PARADE);
        RoomPacker.Placed hq = laid.rooms().stream()
                .filter(r -> r.purpose() == RoomPurpose.KEEP_THRONE).findFirst().orElseThrow();
        int hqX = hq.originX() + hq.shape().width() / 2;
        assertTrue(Math.abs(hqX - side / 2) <= 1, "the headquarters stands on the avenue");
        double paradeY = laid.parade().stream().mapToInt(c -> c[1]).average().orElseThrow();
        double barracksY = laid.rooms().stream().filter(r -> r.purpose() == RoomPurpose.BARRACKS)
                .mapToInt(RoomPacker.Placed::originY).average().orElseThrow();
        assertTrue(barracksY < paradeY && paradeY < hq.originY(),
                "gate, barracks, parade ground, headquarters, in that order from the gate");
        for (Doorway door : hq.doors()) assertTrue(door.y() < hq.originY(), "its door faces the parade ground");
    }

    @Test
    void theGateIsWhereTheRoadComesInOnTheSideFacingTheSite() {
        boolean[][] own = new boolean[100][100];
        boolean[][] road = new boolean[100][100];
        for (int x = 10; x <= 50; x++) for (int y = 10; y <= 50; y++) own[x][y] = true;
        for (int x = 0; x < 100; x++) for (int y = 30; y < 35; y++) road[x][y] = true;   // a road west to east
        for (int x = 20; x < 23; x++) for (int y = 0; y < 100; y++) road[x][y] = true;   // and one south to north
        Cantonment.Gate gate = Cantonment.gate(own, road, 10, 10, 50, 50);
        // The map's middle is north-east of the place; the road across comes in at the east side's middle.
        assertEquals(Side.EAST, gate.side());
        assertEquals(32, gate.axis());
    }

    @Test
    void aRoadOffTheMapIsTheLastGate() {
        boolean[][] own = new boolean[100][100];
        boolean[][] road = new boolean[100][100];
        for (int x = 0; x <= 40; x++) for (int y = 0; y <= 40; y++) own[x][y] = true;
        for (int x = 18; x < 23; x++) for (int y = 0; y <= 20; y++) road[x][y] = true;   // runs off the map's edge
        for (int x = 0; x <= 40; x++) for (int y = 5; y < 8; y++) road[x][y] = true;     // and on into the map east
        assertEquals(Side.EAST, Cantonment.gate(own, road, 0, 0, 40, 40).side());
    }

    /** No two footprints touch, none leaves the ground, and every door opens on ground no building stands on. */
    private static void assertApart(Cantonment.Laid laid, boolean[][] ground) {
        int w = ground.length, h = ground[0].length;
        int[][] owner = new int[w][h];
        for (int[] col : owner) Arrays.fill(col, -1);
        for (int i = 0; i < laid.rooms().size(); i++) {
            RoomPacker.Placed room = laid.rooms().get(i);
            for (int[] c : room.shape().filled()) mark(owner, ground, room, c, i);
            for (int[] c : room.shape().wall()) mark(owner, ground, room, c, i);
        }
        for (RoomPacker.Placed room : laid.rooms()) {
            assertFalse(room.doors().isEmpty(), room.purpose() + " has a way in");
            for (Doorway door : room.doors()) {
                int outside = 0;
                for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int x = door.x() + s[0], y = door.y() + s[1];
                    if (x >= 0 && y >= 0 && x < w && y < h && owner[x][y] < 0) outside++;
                }
                assertTrue(outside >= 1, room.purpose() + "'s door at " + door + " opens on open ground");
            }
        }
    }

    private static void mark(int[][] owner, boolean[][] ground, RoomPacker.Placed room, int[] c, int i) {
        int x = room.originX() + c[0], y = room.originY() + c[1];
        assertTrue(ground[x][y], room.purpose() + " stands on ground it may build on at " + x + "," + y);
        assertTrue(owner[x][y] < 0 || owner[x][y] == i, room.purpose() + " overlaps another building at " + x + "," + y);
        owner[x][y] = i;
    }

    private static boolean[][] open(int w, int h) {
        boolean[][] g = new boolean[w][h];
        for (boolean[] col : g) Arrays.fill(col, true);
        return g;
    }
}
