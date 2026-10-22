package com.dillon.starsectormarines.battle.creep;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cave-in rule on a hand-made room: a ring of wall round a row of roofed
 * cells, with creep and crust set by hand.
 */
class CreepRoofCollapseTest {

    /** A 9x3 map: wall all round, seven roofed cells in the middle row. */
    private static final class Room implements CreepRoofCollapse.Cells {
        static final int W = 9, H = 3;
        final boolean[] gone = new boolean[W * H];
        final boolean[] creep = new boolean[W * H];
        final boolean[] crust = new boolean[W * H];

        @Override public int width() { return W; }

        @Override public int height() { return H; }

        @Override public boolean building(int x, int y) { return y == 1 && x > 0 && x < W - 1; }

        @Override public boolean roofGone(int x, int y) { return gone[y * W + x]; }

        @Override public boolean creeped(int x, int y) { return creep[y * W + x]; }

        @Override public boolean crustedWall(int x, int y) { return !building(x, y) && crust[y * W + x]; }

        void creepAll() { for (int x = 1; x < W - 1; x++) creep[W + x] = true; }
    }

    private record Fall(int x, int tick) { }

    private static List<Fall> run(CreepRoofCollapse rule, Room room, int fromTick, int toTick) {
        List<Fall> falls = new ArrayList<>();
        for (int tick = fromTick; tick <= toTick; tick += CreepRoofCollapse.CHECK_TICKS) {
            int now = tick;
            rule.check(now, room, (x, y) -> {
                room.gone[y * Room.W + x] = true;
                falls.add(new Fall(x, now));
            });
        }
        return falls;
    }

    private static int maxHold(CreepRoofCollapse rule) {
        int most = 0;
        for (int x = 1; x < Room.W - 1; x++) most = Math.max(most, rule.holdTicks(Room.W + x));
        return most;
    }

    @Test
    void nothingFallsWithoutCreep() {
        Room room = new Room();
        room.crust[Room.W] = true;
        assertTrue(run(new CreepRoofCollapse(7L), room, 0, 30 * 600).isEmpty());
    }

    @Test
    void nothingFallsBeforeTheHold() {
        Room room = new Room();
        room.creepAll();
        room.crust[Room.W] = true;
        CreepRoofCollapse rule = new CreepRoofCollapse(7L);
        int earliest = Math.round(CreepRoofCollapse.HOLD_SECONDS * CreepRoofCollapse.TICKS_PER_SECOND);
        assertTrue(run(rule, room, 0, earliest - 2 * CreepRoofCollapse.CHECK_TICKS).isEmpty());
        List<Fall> later = run(rule, room, earliest - CreepRoofCollapse.CHECK_TICKS, 30 * 600);
        assertTrue(!later.isEmpty());
        for (Fall fall : later) {
            assertTrue(rule.heldTicks(Room.W + fall.x) >= rule.holdTicks(Room.W + fall.x));
        }
    }

    @Test
    void creepWithoutCrustOrFallenNeighbourHoldsTheRoofUp() {
        Room room = new Room();
        room.creepAll();
        assertTrue(run(new CreepRoofCollapse(7L), room, 0, 30 * 600).isEmpty());
    }

    @Test
    void theCaveInRunsInwardFromTheCrustedWall() {
        Room room = new Room();
        room.creepAll();
        room.crust[Room.W] = true; // the west wall only
        CreepRoofCollapse rule = new CreepRoofCollapse(7L);
        List<Fall> falls = run(rule, room, 0, maxHold(rule) + 30 * 60);
        assertEquals(Room.W - 2, falls.size());
        for (int i = 0; i < falls.size(); i++) {
            assertEquals(i + 1, falls.get(i).x, "cell " + falls.get(i).x + " fell out of order");
            if (i == 0) continue;
            int gap = falls.get(i).tick - falls.get(i - 1).tick;
            assertTrue(gap >= rule.spreadTicks(Room.W + falls.get(i).x) - CreepRoofCollapse.CHECK_TICKS,
                    "cell " + falls.get(i).x + " followed its neighbour after " + gap + " ticks");
        }
    }

    @Test
    void aRoofFallenToWarSpreadsTheCaveIn() {
        Room room = new Room();
        room.creepAll();
        room.gone[Room.W + 4] = true;
        CreepRoofCollapse rule = new CreepRoofCollapse(7L);
        List<Fall> falls = run(rule, room, 0, maxHold(rule) + 30 * 60);
        assertEquals(Room.W - 3, falls.size());
        assertTrue(falls.get(0).x == 3 || falls.get(0).x == 5);
    }

    @Test
    void witheringResetsTheHold() {
        Room room = new Room();
        room.creepAll();
        room.crust[Room.W] = true;
        CreepRoofCollapse rule = new CreepRoofCollapse(7L);
        int half = Math.round(CreepRoofCollapse.HOLD_SECONDS * CreepRoofCollapse.TICKS_PER_SECOND / 2f);
        assertTrue(run(rule, room, 0, half).isEmpty());
        room.creep[Room.W + 1] = false;
        run(rule, room, half + CreepRoofCollapse.CHECK_TICKS, half + CreepRoofCollapse.CHECK_TICKS);
        assertEquals(0, rule.heldTicks(Room.W + 1));
    }
}
