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
        for (long seed : new long[]{42L, 777L, 5L}) {
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

    /**
     * A gate is a road leaving, so every cell of one is road inside this
     * precinct — whoever grew that road.
     *
     * <p>Whose it is deliberately goes unasked. A precinct is circulated by
     * whatever road lies in its claim, and requiring its own is what sealed a
     * garrison whose claim had outgrown the reach of its arms.
     */
    @Test
    void everyGateIsARoadThatLeaves() {
        Grown grown = grow(42L);
        List<PrecinctBoundary.Gate> gates =
                PrecinctBoundary.gates(grown.claim(), grown.owner(), 0, W, H);
        assertTrue(!gates.isEmpty(), "no road leaves the garrison, so it has no way out "
                + "and no artery to anywhere");
        for (PrecinctBoundary.Gate gate : gates) {
            for (int[] cell : gate.cells()) {
                assertTrue(grown.owner()[cell[0]][cell[1]] != GrownTrunkPlan.UNOWNED,
                        "a gate cell at " + cell[0] + "," + cell[1] + " is not road at all");
                assertTrue(grown.claim()[cell[0]][cell[1]] == 0,
                        "a gate cell is outside the precinct it is a gate of");
            }
        }
    }

    /**
     * A neighbour's road crossing the outline is a way through it.
     *
     * <p>Hand-built rather than grown, because the case is exactly the one a
     * grown fixture is bad at showing: a precinct that owns a solid block of
     * ground and not one cell of road, sitting under somebody else's street
     * grid. Every crossing here belongs to precinct 0 and every gate belongs to
     * precinct 1. Asking whose road it was reported no gates at all — which on
     * a production map is a walled garrison nothing can reach.
     */
    @Test
    void aNeighboursRoadCrossingTheOutlineIsAGate() {
        Gridded g = grid(false);
        List<PrecinctBoundary.Gate> gates =
                PrecinctBoundary.gates(g.claim(), g.owner(), 1, GW, GH);
        assertTrue(!gates.isEmpty(), "a blob of ground under a street grid has no way in, "
                + "so nothing outside it can reach anything inside");
        assertTrue(gates.size() == GRID_CROSSINGS, "the grid crosses the blob's outline "
                + GRID_CROSSINGS + " times — three on each of its four sides — but "
                + gates.size() + " gates were found, so crossings are being split or merged");
        for (PrecinctBoundary.Gate gate : gates) {
            assertTrue(gate.width() == 1 && !gate.drivable(),
                    "a one-cell crossing came out " + gate.width() + " cells wide and "
                            + (gate.drivable() ? "drivable" : "not drivable")
                            + ", so a footpath is being counted as a road for armour");
            for (int[] cell : gate.cells()) {
                assertTrue(g.owner()[cell[0]][cell[1]] == 0,
                        "the fixture's road all belongs to precinct 0; this gate cell does not");
            }
        }
    }

    /** Widening one of those roads to three cells is what makes its gate drivable. */
    @Test
    void aGateIsDrivableOnlyWhenTheRoadThroughItIsWide() {
        Gridded g = grid(true);
        List<PrecinctBoundary.Gate> gates =
                PrecinctBoundary.gates(g.claim(), g.owner(), 1, GW, GH);
        assertTrue(gates.size() == GRID_CROSSINGS, "widening one road changed how many ways "
                + "through there are, from " + GRID_CROSSINGS + " to " + gates.size()
                + "; it should change only how wide two of them are");
        List<PrecinctBoundary.Gate> drivable =
                gates.stream().filter(PrecinctBoundary.Gate::drivable).toList();
        assertTrue(drivable.size() == 2, "the widened road crosses the outline twice, so "
                + "two gates should be drivable, not " + drivable.size());
        for (PrecinctBoundary.Gate gate : drivable) {
            assertTrue(gate.width() == PrecinctBoundary.DRIVABLE_GATE_WIDTH,
                    "a drivable gate came out " + gate.width() + " cells wide against the "
                            + PrecinctBoundary.DRIVABLE_GATE_WIDTH + " the road is");
        }
    }

    /**
     * A road that leaves the map is not a way through.
     *
     * <p>The hole it would leave opens onto ground nobody in the battle can
     * stand on, so it is wall. The claim here runs to the left edge and its one
     * road runs off that edge; the outline is crossed by nothing.
     */
    @Test
    void aRoadOffTheMapEdgeIsNotAGate() {
        int[][] claim = new int[GW][GH];
        int[][] owner = new int[GW][GH];
        for (int x = 0; x < GW; x++) {
            for (int y = 0; y < GH; y++) {
                claim[x][y] = GrownTrunkPlan.UNOWNED;
                owner[x][y] = GrownTrunkPlan.UNOWNED;
            }
        }
        for (int x = 0; x <= 10; x++) {
            for (int y = 5; y <= 25; y++) claim[x][y] = 1;
            owner[x][15] = 0;
        }
        assertTrue(PrecinctBoundary.gates(claim, owner, 1, GW, GH).isEmpty(),
                "a road running off the map edge was counted as a gate, so a wall has a "
                        + "hole in it that opens onto nothing");
    }

    private static final int GW = 40;
    private static final int GH = 30;

    /** Three grid lines cross each of the blob's four sides. */
    private static final int GRID_CROSSINGS = 12;

    private record Gridded(int[][] claim, int[][] owner) { }

    /**
     * A street grid owned by precinct 0 over a solid claim owned by precinct 1.
     *
     * <p>The grid runs every fifth column and row; the blob is inset one cell
     * off those lines so its outline is crossed rather than run along, which is
     * what makes the crossings countable.
     *
     * @param wideRoad whether the grid line through column 10 is widened to a
     *                 drivable three cells
     */
    private static Gridded grid(boolean wideRoad) {
        int[][] claim = new int[GW][GH];
        int[][] owner = new int[GW][GH];
        for (int x = 0; x < GW; x++) {
            for (int y = 0; y < GH; y++) {
                claim[x][y] = (x >= 6 && x <= 24 && y >= 6 && y <= 24)
                        ? 1 : GrownTrunkPlan.UNOWNED;
                boolean road = x % 5 == 0 || y % 5 == 0
                        || (wideRoad && (x == 9 || x == 11));
                owner[x][y] = road ? 0 : GrownTrunkPlan.UNOWNED;
            }
        }
        return new Gridded(claim, owner);
    }

    /**
     * Growth does not always leave a way out, and the failure is silent.
     *
     * <p>A claim large enough relative to how far its arms reach swallows every
     * road that touches it, so nothing crosses the boundary and the precinct is
     * walled with no opening at all. Recorded as a fact rather than asserted
     * away: of three seeds, two grow a drivable gate and one grows none.
     *
     * <p>Seed 3 used to be the one that grew none and no longer is — under the
     * any-owner gate rule the settlement's road crosses its outline — so seed 5
     * carries the case now. Measured over seeds 0..59, three of them still seal.
     */
    @Test
    void growthAloneDoesNotGuaranteeAWayOut() {
        int sealed = 0;
        for (long seed : new long[]{42L, 777L, 5L}) {
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
        for (long seed : new long[]{42L, 777L, 5L}) {
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
