package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.goap.action.FightingPositionClaims.Probe;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Open-ground places on a drawn line take cover against the line's front. */
class LinePlacementTest {

    private static final int W = 40;

    /** An open field whose cells give the cover listed for them, keyed by cell and compass side. */
    private static final class Field implements Probe {
        final Map<String, Integer> cover = new HashMap<>();
        final Set<Integer> blind = new HashSet<>();

        /** Cover at (x, y) against fire from the side ({@code sx}, {@code sy}), each -1, 0 or 1. */
        Field cover(int x, int y, int sx, int sy, int level) {
            cover.put(x + "," + y + "," + sx + "," + sy, level);
            return this;
        }

        @Override public boolean walkable(int x, int y) {
            return x >= 0 && y >= 0 && x < W && y < W;
        }

        @Override public int cover(int x, int y, int fromDx, int fromDy) {
            int sx = Integer.signum(Math.abs(fromDx) >= Math.abs(fromDy) ? fromDx : 0);
            int sy = sx != 0 ? 0 : Integer.signum(fromDy);
            return cover.getOrDefault(x + "," + y + "," + sx + "," + sy, 0);
        }

        @Override public boolean lineOfFire(int x, int y, float toX, float toY) {
            return !blind.contains(y * W + x);
        }
    }

    /** A line along y = 20 from x = 10 to 30 whose front is north (lower y): the squad came from the south. */
    private static FrontageLine northFacing() {
        FrontageLine line = FrontageLine.facing(10, 20, 30, 20, 20, 30, -1, -1);
        assertTrue(line.frontY() < 0f, "fixture: the front is north");
        return line;
    }

    private static int[] place(FrontageLine line, int count, Probe probe, Set<Integer> taken) {
        int[] cells = new int[count];
        Arrays.fill(cells, -1);
        return LinePlacement.openCells(line, count, cells, null, null, W, probe, taken, -1, -1);
    }

    @Test
    void coverIsTakenAgainstTheFrontNotTheRear() {
        FrontageLine line = northFacing();
        // One marine; his share is the line's middle, a cell behind it: (20, 21).
        Field field = new Field()
                .cover(19, 21, 0, 1, 3)    // a wall to his south: cover from the rear only
                .cover(21, 21, 0, -1, 2);  // sandbags to his north: cover from the front
        int[] open = place(line, 1, field, new HashSet<>());
        assertEquals(21 * W + 21, open[0], "the front's cover, though the rear's is heavier");
    }

    @Test
    void squadmatesNeverShareACoveredCell() {
        FrontageLine line = FrontageLine.facing(18, 20, 22, 20, 20, 30, -1, -1);
        Field field = new Field().cover(20, 21, 0, -1, 3);
        int[] open = place(line, 2, field, new HashSet<>());
        assertTrue(open[0] == 21 * W + 20 || open[1] == 21 * W + 20, "one of them takes the sandbag");
        assertNotEquals(open[0], open[1], "and the other stands somewhere else");
    }

    @Test
    void aDiagonalFrontWeighsBothItsSides() {
        // A line from (10, 30) to (30, 10), the squad to its south-east: the front faces north-west.
        FrontageLine line = FrontageLine.facing(10, 30, 30, 10, 30, 30, -1, -1);
        assertTrue(line.frontX() < 0f && line.frontY() < 0f, "fixture: the front is north-west");
        Field field = new Field()
                .cover(20, 21, -1, 0, 3)   // heavy on the west face alone
                .cover(21, 20, -1, 0, 2)   // a corner: west and north
                .cover(21, 20, 0, -1, 2);
        assertTrue(LinePlacement.frontCover(field, 21, 20, line) > LinePlacement.frontCover(field, 20, 21, line),
                "a corner covering both sides of the front beats one face of it");
    }

    @Test
    void coverThatCannotFireAlongTheFrontIsPassedOver() {
        FrontageLine line = northFacing();
        Field field = new Field()
                .cover(20, 21, 0, -1, 3)
                .cover(22, 21, 0, -1, 1);
        field.blind.add(21 * W + 20);
        int[] open = place(line, 1, field, new HashSet<>());
        assertEquals(21 * W + 22, open[0], "a covered cell that sees nothing ahead is no place to hold");
    }

    @Test
    void withNoCoverTheShareItselfIsHeld() {
        FrontageLine line = northFacing();
        int[] open = place(line, 1, new Field(), new HashSet<>());
        assertEquals(21 * W + 20, open[0]);
    }
    private static int[] placeUnderFire(FrontageLine line, Probe probe, int[] previous, int threatX, int threatY) {
        return LinePlacement.openCells(line, 1, new int[]{-1}, previous, null, W, probe, new HashSet<>(), threatX, threatY);
    }

    @Test
    void aFlankChargeOnBareCoverMovesHimBehindRealCoverAgainstIt() {
        FrontageLine line = northFacing();
        Field field = new Field()
                .cover(20, 21, 0, -1, 2)   // his place: sandbags against the front only
                .cover(22, 21, 1, 0, 3);   // a wall against the east
        int[] open = placeUnderFire(line, field, null, 36, 21);
        assertEquals(21 * W + 22, open[0], "nothing between him and the charge, so he takes the wall");

        int[] held = placeUnderFire(line, field, open, 36, 21);
        assertEquals(open[0], held[0], "and keeps it while the charge stands");
        assertEquals(21 * W + 20, placeUnderFire(line, field, open, -1, -1)[0],
                "and goes back to the front's cover once it is gone");
    }

    @Test
    void anyCoverAgainstTheFireKeepsThePlayersPlace() {
        FrontageLine line = northFacing();
        Field field = new Field()
                .cover(20, 21, 0, -1, 2)
                .cover(20, 21, 1, 0, 1)    // a little against the east already
                .cover(22, 21, 1, 0, 3);
        assertEquals(21 * W + 20, placeUnderFire(line, field, null, 36, 21)[0],
                "a better sidegrade is not reason enough to break the line");
    }

    @Test
    void lightCoverIsNotWorthLeavingTheLineFor() {
        FrontageLine line = northFacing();
        Field field = new Field()
                .cover(20, 21, 0, -1, 2)
                .cover(22, 21, 1, 0, 1);
        assertEquals(21 * W + 20, placeUnderFire(line, field, null, 36, 21)[0]);
    }

    @Test
    void coverHeCannotShootTheChargeFromIsNoAnswerToIt() {
        FrontageLine line = northFacing();
        Field field = new Field()
                .cover(20, 21, 0, -1, 2)
                .cover(22, 21, 1, 0, 3);
        field.blind.add(21 * W + 22);
        assertEquals(21 * W + 20, placeUnderFire(line, field, null, 36, 21)[0]);
    }

    @Test
    void sharesGoOutInTheOrderTheMarinesStandAlongTheLine() {
        FrontageLine line = northFacing();
        int[] cells = {-1, -1, -1};
        float[] along = {0.9f, 0.1f, 0.5f};
        int[] open = LinePlacement.openCells(line, 3, cells, null, along, W, new Field(), new HashSet<>(), -1, -1);

        assertTrue(open[1] % W < open[2] % W && open[2] % W < open[0] % W,
                "the marine nearest the line's start takes its first share, so no two paths cross");
    }

    @Test
    void aMarineOnTheWayRanksByTheCellHeHeldSoSharesAreNotTradedInPassing() {
        FrontageLine line = northFacing();
        int[] previous = {19 * W + 12, -1};
        float[] along = {0.9f, 0.5f};

        int[] ordinal = LinePlacement.ordinals(line, 2, previous, along, W);

        assertEquals(0, ordinal[0], "ranked by his cell near the start, not where he stands now");
        assertEquals(1, ordinal[1]);
    }
}
