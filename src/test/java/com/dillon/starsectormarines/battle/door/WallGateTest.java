package com.dillon.starsectormarines.battle.door;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A wall gate's leaves span the wall across the way out, and a breach of either jamb forces it. */
class WallGateTest {

    @Test
    void aGateInASouthWallSpansAlongXOnTheWallLine() {
        WallGate gate = WallGate.of(List.of(new int[]{12, 5}, new int[]{10, 5}, new int[]{11, 5}), 0, -1);
        assertTrue(gate.spanX());
        assertEquals(10, gate.min());
        assertEquals(12, gate.max());
        assertEquals(5, gate.across());
        assertEquals(3, gate.width());
        assertArrayEquals(new int[]{9, 5}, gate.jambs()[0]);
        assertArrayEquals(new int[]{13, 5}, gate.jambs()[1]);
    }

    @Test
    void aGateInAnEastWallSpansAlongY() {
        WallGate gate = WallGate.of(List.of(new int[]{40, 20}, new int[]{40, 21}), 1, 0);
        assertFalse(gate.spanX());
        assertArrayEquals(new int[]{40, 19}, gate.jambs()[0]);
        assertArrayEquals(new int[]{40, 22}, gate.jambs()[1]);
    }

    @Test
    void aBreachedJambForcesTheGateAndAnOpenWayDoesNot() {
        WallGate gate = WallGate.of(List.of(new int[]{10, 5}, new int[]{11, 5}, new int[]{12, 5}), 0, -1);
        assertFalse(gate.forced((x, y) -> y == 5 && x >= 10 && x <= 12), "the way through is open, not forced");
        assertTrue(gate.forced((x, y) -> x == 13 && y == 5), "the wall beside it blown");
    }

    @Test
    void leavesSpanTheWallNotTheWayThrough() {
        assertThrows(IllegalArgumentException.class, () -> new WallGate(false, 1, 3, 5, 0, 1));
    }
}
