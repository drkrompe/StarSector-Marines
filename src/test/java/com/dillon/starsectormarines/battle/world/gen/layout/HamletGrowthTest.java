package com.dillon.starsectormarines.battle.world.gen.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hamlet's lane, joined to a way, and the yards fronting it. */
class HamletGrowthTest {

    /** A way runs down x = 0; everything east of it is open. */
    private static final LineCourse.CellTest OPEN = (x, y) -> x > 0;
    private static final LineCourse.CellTest WAY = (x, y) -> x == 0;

    private static boolean inside(Structure s, int x, int y) {
        return x >= s.x() && x < s.x() + s.width() && y >= s.y() && y < s.y() + s.height();
    }

    /** With room, a full hamlet: six buildings, none on another or on the lane, the lane opening onto the way. */
    @Test
    void withRoomTheHamletIsFullAndItsLaneJoinsTheWay() {
        HamletGrowth.Hamlet h = HamletGrowth.grow("base", new int[]{0, 0, 80, 60}, 30, 30, OPEN, OPEN, WAY);
        List<Structure> b = h.buildings();
        assertEquals(HamletGrowth.MAX, b.size());
        boolean joined = false;
        for (int[] c : h.lane()) {
            joined |= c[0] == 1;
            for (Structure s : b) assertFalse(inside(s, c[0], c[1]), s.name() + " stands on the lane");
        }
        assertTrue(joined, "the lane opens onto the way");
        for (int i = 0; i < b.size(); i++) {
            for (int j = i + 1; j < b.size(); j++) {
                Structure s = b.get(j), o = b.get(i);
                assertFalse(s.x() < o.x() + o.width() && o.x() < s.x() + s.width()
                        && s.y() < o.y() + o.height() && o.y() < s.y() + s.height(), s.name() + " overlaps " + o.name());
            }
        }
    }

    /** Each yard's door side faces the lane: a cell just past it, across the verge, is lane. */
    @Test
    void everyDoorFacesTheLane() {
        HamletGrowth.Hamlet h = HamletGrowth.grow("base", new int[]{0, 0, 80, 60}, 30, 30, OPEN, OPEN, WAY);
        for (Structure s : h.buildings()) {
            int mx = s.x() + s.width() / 2, my = s.y() + s.height() / 2;
            int[] front = switch (s.turns()) {
                case 0 -> new int[]{mx, s.y() - 1 - HamletGrowth.VERGE};
                case 2 -> new int[]{mx, s.y() + s.height() + HamletGrowth.VERGE};
                case 1 -> new int[]{s.x() - 1 - HamletGrowth.VERGE, my};
                default -> new int[]{s.x() + s.width() + HamletGrowth.VERGE, my};
            };
            assertTrue(h.lane().stream().anyMatch(c -> c[0] == front[0] && c[1] == front[1]), s.name() + " turns its back");
        }
    }

    /** Where room is short the hamlet is smaller, and none where no yard fits. */
    @Test
    void shortRoomGrowsASmallerHamletOrNone() {
        LineCourse.CellTest narrow = (x, y) -> x > 0 && y < 16;
        int small = HamletGrowth.grow("base", new int[]{0, 0, 80, 60}, 30, 8, narrow, narrow, WAY).buildings().size();
        assertTrue(small >= 1 && small < HamletGrowth.MAX, "a strip one yard deep: " + small);
        assertTrue(HamletGrowth.grow("base", new int[]{0, 0, 80, 60}, 30, 8, (x, y) -> x > 0 && y < 5,
                (x, y) -> false, WAY).buildings().isEmpty());
    }
}
