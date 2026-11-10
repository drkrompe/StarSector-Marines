package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The garrison laid as an installation ({@code raid-mission.md}, Garrison district): what it owes by market and
 * security, that every plan seats it on its own square from any gate side, never on a road, that every door opens on
 * open ground, that every vehicle shed has a paved way out of the gate no building stands on, and that the gate is
 * where the town's road comes in.
 */
class CantonmentTest {

    private static final FortressProgram GUARDED_FIVE = FortressProgram.cantonment(5, 1, 1);
    private static final List<FortressProgram> PROGRAMS = List.of(FortressProgram.cantonment(4, 0, 1), GUARDED_FIVE,
            FortressProgram.cantonment(7, 2, 1));

    @Test
    void theMarketSizesTheGarrisonAndSecurityItsGuard() {
        FortressProgram small = FortressProgram.cantonment(4, 0, 1);
        FortressProgram large = FortressProgram.cantonment(7, 2, 1);
        assertTrue(large.countOf(RoomPurpose.BARRACKS) > small.countOf(RoomPurpose.BARRACKS));
        assertTrue(large.countOf(RoomPurpose.VEHICLE_BAY) > small.countOf(RoomPurpose.VEHICLE_BAY),
                "a larger garrison keeps a larger motor pool");
        assertTrue(Cantonment.fieldFor(large).ordinal() < Cantonment.fieldFor(small).ordinal(),
                "and flies from a larger flight line");
        assertTrue(large.countOf(RoomPurpose.CONTROL_ROOM) > small.countOf(RoomPurpose.CONTROL_ROOM));
        assertTrue(large.countOf(RoomPurpose.ARMORY) > small.countOf(RoomPurpose.ARMORY));
        assertEquals(0, small.countOf(RoomPurpose.KEEP_ENTRY), "an open garrison keeps no guardroom");
        assertEquals(1, large.countOf(RoomPurpose.KEEP_ENTRY));
        assertEquals(1, large.countOf(RoomPurpose.KEEP_THRONE), "one headquarters");
        assertTrue(large.cantonment() && large.with(RoomPurpose.BARRACKS, 2).cantonment(),
                "a trimmed cantonment is still laid as one");
    }

    @Test
    void everyPlanSeatsItsWholeProgramOnItsOwnSquareFromEveryGate() {
        for (FortressProgram program : PROGRAMS) {
            for (Cantonment.Plan plan : Cantonment.Plan.values()) {
                int side = Cantonment.side(program, plan) + Cantonment.ROAD_SLACK;
                for (Side gate : Side.values()) {
                    boolean[][] open = open(side + 10, side + 10);
                    Cantonment.Laid laid = Cantonment.lay(open, 5, 5, 4 + side, 4 + side, gate,
                            5 + side / 2, program, plan);
                    String what = plan + " from " + gate;
                    assertTrue(laid.unplaced().isEmpty(), what + " left " + laid.unplaced());
                    assertTrue(laid.strays().isEmpty(), what + " strayed " + laid.strays());
                    assertEquals(Cantonment.fieldFor(program), laid.airfieldSize(),
                            what + " has the flight line the garrison is owed");
                    assertApart(laid, open);
                }
            }
        }
    }

    @Test
    void everyVehicleShedDrivesOutOfTheGateOnPavingNoBuildingStandsOn() {
        for (FortressProgram program : PROGRAMS) {
            for (Cantonment.Plan plan : Cantonment.Plan.values()) {
                int side = Cantonment.side(program, plan) + Cantonment.ROAD_SLACK;
                for (Side gate : Side.values()) {
                    // The road comes in off the middle of the gate side, as a town's road often does.
                    Cantonment.Laid laid = Cantonment.lay(open(side, side), 0, 0, side - 1, side - 1, gate,
                            side / 3, program, plan);
                    assertVehiclesReachTheGate(laid, side, side, plan + " from " + gate);
                }
            }
        }
    }

    @Test
    void noBuildingStandsOnARoadThroughThePlace() {
        for (Cantonment.Plan plan : Cantonment.Plan.values()) {
            int side = Cantonment.side(GUARDED_FIVE, plan) + 20;
            boolean[][] ground = open(side, side);
            for (int x = 0; x < side; x++) {
                for (int y = side / 3; y < side / 3 + 7; y++) ground[x][y] = false;   // a road across the place
            }
            Cantonment.Laid laid = Cantonment.lay(ground, 0, 0, side - 1, side - 1, Side.WEST, side / 2,
                    GUARDED_FIVE, plan);
            assertFalse(laid.rooms().isEmpty());
            assertApart(laid, ground);
        }
    }

    @Test
    void theHeadquartersFacesTheGateAcrossTheParadeGround() {
        for (Cantonment.Plan plan : Cantonment.Plan.values()) {
            int side = Cantonment.side(GUARDED_FIVE, plan);
            Cantonment.Laid laid = Cantonment.lay(open(side, side), 0, 0, side - 1, side - 1, Side.SOUTH, side / 2,
                    GUARDED_FIVE, plan);
            RoomPacker.Placed hq = laid.rooms().stream()
                    .filter(r -> r.purpose() == RoomPurpose.KEEP_THRONE).findFirst().orElseThrow();
            int hqX = hq.originX() + hq.shape().width() / 2;
            assertTrue(Math.abs(hqX - side / 2) <= 1, plan + ": the headquarters stands on the avenue's line");
            double paradeY = laid.parade().stream().mapToInt(c -> c[1]).average().orElseThrow();
            assertTrue(laid.gate()[1] < paradeY && paradeY < hq.originY(),
                    plan + ": gate, parade ground, headquarters, in that order");
            for (Doorway door : hq.doors()) {
                assertTrue(door.y() < hq.originY(), plan + ": its door faces the parade ground");
            }
        }
    }

    @Test
    void aCornerTakenByANeighbourLeavesTheLargestSquareWhole() {
        boolean[][] own = new boolean[60][60];
        for (int x = 0; x < 60; x++) for (int y = 0; y < 60; y++) own[x][y] = x >= 25 || y >= 35;   // an L
        int[] square = Cantonment.square(own);
        assertEquals(35, square[2]);
        for (int x = square[0]; x < square[0] + square[2]; x++) {
            for (int y = square[1]; y < square[1] + square[2]; y++) assertTrue(own[x][y], "the square is all its own");
        }
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

    /**
     * Every vehicle shed's door opens on paving (the streets and the hardstand) that reaches the gate
     * {@link Cantonment#VEHICLE_WAY} cells wide all the way, and no building's footprint stands on that paving: a
     * vehicle never has to path through the garrison's own buildings to leave it.
     */
    private static void assertVehiclesReachTheGate(Cantonment.Laid laid, int w, int h, String what) {
        boolean[][] paved = new boolean[w][h];
        for (int[] c : laid.streets()) paved[c[0]][c[1]] = true;
        for (int[] c : laid.pool()) paved[c[0]][c[1]] = true;
        boolean[][] building = new boolean[w][h];
        for (RoomPacker.Placed room : laid.rooms()) {
            for (int[] c : room.shape().filled()) building[room.originX() + c[0]][room.originY() + c[1]] = true;
            for (int[] c : room.shape().wall()) building[room.originX() + c[0]][room.originY() + c[1]] = true;
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                assertFalse(paved[x][y] && building[x][y], what + ": a building stands on the paving at " + x + "," + y);
            }
        }
        int r = Cantonment.VEHICLE_WAY / 2;
        boolean[][] drive = new boolean[w][h];
        for (int x = r; x < w - r; x++) {
            for (int y = r; y < h - r; y++) {
                boolean clear = true;
                for (int dx = -r; dx <= r && clear; dx++) {
                    for (int dy = -r; dy <= r && clear; dy++) clear = paved[x + dx][y + dy];
                }
                drive[x][y] = clear;
            }
        }
        int[] gate = laid.gate();
        assertTrue(drive[gate[0]][gate[1]], what + ": the gate is on the paving");
        boolean[][] reached = new boolean[w][h];
        Deque<int[]> open = new ArrayDeque<>();
        open.add(gate);
        reached[gate[0]][gate[1]] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int[] c = open.poll();
            for (int[] s : steps) {
                int x = c[0] + s[0], y = c[1] + s[1];
                if (x < 0 || y < 0 || x >= w || y >= h || reached[x][y] || !drive[x][y]) continue;
                reached[x][y] = true;
                open.add(new int[]{x, y});
            }
        }
        int sheds = 0;
        for (RoomPacker.Placed room : laid.rooms()) {
            if (room.purpose() != RoomPurpose.VEHICLE_BAY) continue;
            sheds++;
            boolean out = false;
            for (Doorway door : room.doors()) {
                for (int[] s : steps) {
                    int x1 = door.x() + s[0], y1 = door.y() + s[1];
                    int x2 = door.x() + 2 * s[0], y2 = door.y() + 2 * s[1];
                    if (x2 < 0 || y2 < 0 || x2 >= w || y2 >= h || building[x1][y1]) continue;
                    out |= paved[x1][y1] && reached[x2][y2];
                }
            }
            assertTrue(out, what + ": the shed at " + room.originX() + "," + room.originY()
                    + " drives out onto paving that reaches the gate");
        }
        assertTrue(sheds > 0, what + ": the motor pool stands");
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
