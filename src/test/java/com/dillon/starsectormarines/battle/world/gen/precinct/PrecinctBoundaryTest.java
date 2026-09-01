package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wall follows what grew, and its gates are the roads that already leave.
 *
 * <p>The invariant that matters is not that a wall exists but that it
 * <em>encloses</em>: everything inside the precinct must be unreachable from
 * outside except through a gate. A boundary derived from a claim can fail that
 * quietly — a claim with a one-cell isthmus, or an outline broken where two
 * claims touch diagonally — and the result is a fortress with a hole in it that
 * nothing in the render or the packing would show.
 */
class PrecinctBoundaryTest {

    private static final int W = 300;
    private static final int H = 200;

    private record Grown(int[][] claim, int[][] owner) { }

    private static Grown grow(long seed) {
        Precinct garrison = Precinct.garrison("garrison", 110, 100,
                GrownTrunkPlan.Profile.hamlet(), FortressProgram.garrison());
        Precinct town = Precinct.settlement("town", 230, 70,
                GrownTrunkPlan.Profile.town());
        List<Precinct> places = List.of(garrison, town);
        GrownTrunkPlan.Grown grown = GrownTrunkPlan.grow(W, H, new Random(seed),
                places.stream()
                        .map(p -> new GrownTrunkPlan.Seed(p.seedX(), p.seedY(), p.growth()))
                        .toList());
        int[] allowance = PrecinctAllowance.derive(places, grown.owner(), W, H);
        int[][] claim = PrecinctClaim.compact(
                new int[]{garrison.seedX(), town.seedX()},
                new int[]{garrison.seedY(), town.seedY()})
                .assign(grown.owner(), W, H, allowance);
        return new Grown(claim, grown.owner());
    }

    /**
     * Nothing gets in except through a gate.
     *
     * <p>Flood the outside of the map through everything that is not wall. Any
     * interior cell it reaches got there through a gate or through a hole; the
     * gates are then removed from the flood and anything still reachable is a
     * hole. That is the only formulation that separates the two — asserting the
     * outline is merely non-empty would pass on a wall with a gap in it.
     */
    @Test
    void aWalledPrecinctIsSealedExceptAtItsGates() {
        for (long seed : new long[]{42L, 777L, 3L}) {
            Grown grown = grow(seed);
            boolean[][] wall = PrecinctBoundary.wall(grown.claim(), grown.owner(), 0, W, H);
            for (PrecinctBoundary.Gate gate :
                    PrecinctBoundary.gates(grown.claim(), grown.owner(), 0, W, H)) {
                for (int[] cell : gate.cells()) wall[cell[0]][cell[1]] = true;
            }
            boolean[][] outside = floodFromEdge(wall);
            int leaks = 0;
            for (int x = 0; x < W; x++) {
                for (int y = 0; y < H; y++) {
                    if (grown.claim()[x][y] == 0 && !wall[x][y] && outside[x][y]) leaks++;
                }
            }
            assertTrue(leaks == 0, "seed " + seed + ": " + leaks + " cells inside the "
                    + "precinct are reachable from outside without crossing the wall, so "
                    + "the outline does not close");
        }
    }

    /** With the gates open again, the outside can get in — so the seal above was real. */
    @Test
    void theGatesAreActuallyWaysThrough() {
        Grown grown = grow(42L);
        boolean[][] wall = PrecinctBoundary.wall(grown.claim(), grown.owner(), 0, W, H);
        boolean[][] outside = floodFromEdge(wall);
        int reached = 0;
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (grown.claim()[x][y] == 0 && outside[x][y]) reached++;
            }
        }
        assertTrue(reached > 0, "with the gates left open nothing outside can reach the "
                + "inside, so the previous test is measuring a precinct with no way in "
                + "rather than a wall that seals");
    }

    /** A gate is a road leaving, so every cell of one carries this precinct's road. */
    @Test
    void everyGateIsARoadThatLeaves() {
        Grown grown = grow(42L);
        List<PrecinctBoundary.Gate> gates =
                PrecinctBoundary.gates(grown.claim(), grown.owner(), 0, W, H);
        assertTrue(!gates.isEmpty(), "the garrison grew arms but none of them leave it, so "
                + "it has no way out and no artery to anywhere");
        for (PrecinctBoundary.Gate gate : gates) {
            for (int[] cell : gate.cells()) {
                assertTrue(grown.owner()[cell[0]][cell[1]] == 0,
                        "a gate cell at " + cell[0] + "," + cell[1] + " is not this "
                                + "precinct's road");
                assertTrue(grown.claim()[cell[0]][cell[1]] == 0,
                        "a gate cell is outside the precinct it is a gate of");
            }
        }
    }

    /**
     * Growth does not always leave a way out, and the failure is silent.
     *
     * <p>A claim large enough relative to how far its arms reach swallows its
     * own road network, so nothing crosses the boundary and the precinct is
     * walled with no opening at all. Recorded as a fact rather than asserted
     * away: of three seeds, two grow a drivable gate and one grows none.
     */
    @Test
    void growthAloneDoesNotGuaranteeAWayOut() {
        int sealed = 0;
        for (long seed : new long[]{42L, 777L, 3L}) {
            Grown grown = grow(seed);
            List<PrecinctBoundary.Gate> gates =
                    PrecinctBoundary.gates(grown.claim(), grown.owner(), 0, W, H);
            if (gates.stream().noneMatch(PrecinctBoundary.Gate::drivable)) sealed++;
        }
        assertTrue(sealed > 0, "every seed grew its own drivable gate, so PrecinctArtery "
                + "is guarding a case that no longer happens and this test has stopped "
                + "describing the generator");
    }

    /**
     * Something can drive out, once the precinct is given the way out it owes.
     *
     * <p>A precinct whose every way out is two cells wide is walkable and not
     * drivable, which makes its armour scenery — and one with no way out at all
     * is a walled installation nothing leaves.
     */
    @Test
    void everyWalledPrecinctEndsUpWithADrivableWayOut() {
        for (long seed : new long[]{42L, 777L, 3L}) {
            Grown grown = grow(seed);
            PrecinctArtery.ensure(grown.claim(), grown.owner(), 0, 110, 100, W, H);
            List<PrecinctBoundary.Gate> gates =
                    PrecinctBoundary.gates(grown.claim(), grown.owner(), 0, W, H);
            boolean drivable = gates.stream().anyMatch(PrecinctBoundary.Gate::drivable);
            assertTrue(drivable, "seed " + seed + ": the widest way out of the garrison is "
                    + gates.stream().mapToInt(PrecinctBoundary.Gate::width).max().orElse(0)
                    + " cells, under the " + PrecinctBoundary.DRIVABLE_GATE_WIDTH
                    + " a vehicle needs, even after being given an artery");
        }
    }

    /** An artery is a last resort: it is not carved where growth already served. */
    @Test
    void anArteryIsNotCarvedWhereGrowthAlreadyProvided() {
        Grown grown = grow(42L);
        assertTrue(PrecinctBoundary.gates(grown.claim(), grown.owner(), 0, W, H)
                        .stream().anyMatch(PrecinctBoundary.Gate::drivable),
                "seed 42 is supposed to grow its own gate; pick another seed for this test");
        assertTrue(!PrecinctArtery.ensure(grown.claim(), grown.owner(), 0, 110, 100, W, H),
                "an artery was carved for a precinct that already had a drivable gate, so "
                        + "every map gets a straight rescue road through it");
    }

    /** Orthogonal flood from every map-edge cell that is not wall. */
    private static boolean[][] floodFromEdge(boolean[][] wall) {
        boolean[][] seen = new boolean[W][H];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < W; x++) {
            seed(wall, seen, queue, x, 0);
            seed(wall, seen, queue, x, H - 1);
        }
        for (int y = 0; y < H; y++) {
            seed(wall, seen, queue, 0, y);
            seed(wall, seen, queue, W - 1, y);
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
                if (seen[nx][ny] || wall[nx][ny]) continue;
                seen[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return seen;
    }

    private static void seed(boolean[][] wall, boolean[][] seen,
                             Deque<int[]> queue, int x, int y) {
        if (wall[x][y] || seen[x][y]) return;
        seen[x][y] = true;
        queue.add(new int[]{x, y});
    }
}
