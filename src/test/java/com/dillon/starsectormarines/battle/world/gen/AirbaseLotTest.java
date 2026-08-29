package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The airbase lot: berths on the apron, sheds behind them, a fence with a way
 * in, and a runway along the front.
 *
 * <p>Both traversal axes on every test, because the lot has a front and a back
 * and every piece of it is placed relative to those. A mirrored layout that
 * puts a shed's mouth against its own fence, or a berth outside the gate, is
 * exactly the class of error that survives looking at one orientation.
 */
class AirbaseLotTest {

    private static final int MARGIN = 6;

    /** A blank map with the lot authored into the middle of it. */
    private record Lot(NavigationGrid grid, CellTopology topology, GenContext ctx,
                       int left, int bottom, int right, int top) {}

    /**
     * Every size on every approach.
     *
     * <p>The invariants below are the lot's, not the large one's. A compact
     * site that quietly dropped a gate, parked a truck on a berth, or walled a
     * berth into its shed would be just as broken and much easier to miss.
     */
    static Stream<Arguments> shapes() {
        List<Arguments> out = new ArrayList<>();
        for (AirbaseLot.Size size : AirbaseLot.Size.values()) {
            for (AirbaseLot.Facing facing : AirbaseLot.Facing.values()) {
                out.add(Arguments.of(size, facing));
            }
        }
        return out.stream();
    }

    private static Lot author(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        int spanX = AirbaseLot.spanX(size, facing);
        int spanY = AirbaseLot.spanY(size, facing);
        int w = spanX + MARGIN * 2;
        int h = spanY + MARGIN * 2;
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
                topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(1L), w, h, 1L);
        int left = MARGIN;
        int bottom = MARGIN;
        new AirbaseLot(left, bottom, left + spanX - 1, bottom + spanY - 1, facing, size)
                .author(ctx, new Random(1L));
        return new Lot(grid, topology, ctx, left, bottom,
                left + spanX - 1, bottom + spanY - 1);
    }

    private static List<LandingPad> berths(Lot lot) {
        return lot.ctx().landingPads.stream()
                .filter(pad -> pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD)
                .toList();
    }

    /**
     * Every berth is clear, and every berth can be walked to from off the lot.
     *
     * <p>The two halves catch different things. Clear is the berth law —
     * something has to be able to land there. Reachable is the one that has
     * actually broken: a shed set flush against the edge of its own paving
     * opens onto the far side of the fence, and a crew then has to leave the
     * base and come back in to board an aircraft parked in the middle of it.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void everyBerthIsClearAndReachableFromOutside(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        List<LandingPad> berths = berths(lot);
        assertEquals(size.pads(), berths.size(),
                size + " " + facing + ": the berths this size carries");

        boolean[][] reached = flood(lot.grid(), 0, 0);
        for (LandingPad pad : berths) {
            for (int x = pad.left(); x <= pad.right(); x++) {
                for (int y = pad.bottom(); y <= pad.top(); y++) {
                    assertTrue(lot.grid().isWalkable(x, y), size + " " + facing + ": berth cell "
                            + x + "," + y + " is not clear");
                }
            }
            assertTrue(reached[pad.centerX][pad.centerY], size + " " + facing + ": the berth at "
                    + pad.centerX + "," + pad.centerY + " cannot be walked to from off the lot");
        }
    }

    /**
     * The berths are on the apron, not inside the sheds.
     *
     * <p>A hangar is where an aircraft is worked on; a berth is where one waits
     * to fly. Putting a berth indoors makes its crew walk through a building to
     * board, and hides the aircraft from the fight the airfield is supposed to
     * be part of.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void noBerthIsInsideAShed(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        for (LandingPad pad : berths(lot)) {
            assertFalse(underRoof(lot, pad.centerX, pad.centerY), size + " " + facing
                    + ": the berth at " + pad.centerX + "," + pad.centerY
                    + " is under a roof — berths belong on the apron");
        }
    }

    /**
     * Whether this cell is under a roof, asked of the floor rather than
     * inferred from the walls.
     *
     * <p>Two earlier versions of this guessed. "Can you walk out in a straight
     * line" says yes for anything in a shed, because a hangar has a mouth and
     * that is the direction the aircraft leaves by; "is it flanked by walls"
     * says yes for things the question was never about. The lot already floors
     * its buildings differently from its apron — that is the whole point of
     * the surface changing at a wall — so the floor is the answer.
     */
    private static boolean underRoof(Lot lot, int x, int y) {
        return lot.topology().getGroundKind(x, y) == GroundKind.INDOOR;
    }

    /**
     * The sheds are buildings: walled, and open only where they are meant to be.
     *
     * <p>Without walls a hangar is differently-coloured paint. The count is the
     * cheapest honest assertion that they exist as structures at all.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void theShedsAreWalledAndWorked(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        int walls = 0;
        for (int x = lot.left(); x <= lot.right(); x++) {
            for (int y = lot.bottom(); y <= lot.top(); y++) {
                if (lot.topology().isWall(x, y)) walls++;
            }
        }
        assertTrue(walls > 20, size + " " + facing
                + ": every shed should carry a wall ring, found " + walls + " wall cells");

        // And they have kit in them. A shed with nothing in it is a box.
        long inside = lot.ctx().doodads.stream()
                .filter(d -> underRoof(lot, d.cellX, d.cellY))
                .count();
        assertTrue(inside >= 2, size + " " + facing + ": the sheds should be worked, found "
                + inside + " pieces of kit inside them");
    }


    /**
     * The fence encloses the lot and still lets people in.
     *
     * <p>A ring with no gate is a pocket. The flood from outside reaching a
     * berth already proves a way in exists; this pins that the ring is
     * otherwise closed, so the gate is a gate rather than a gap in a line of
     * posts.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void theFenceEnclosesTheLotAndHasAWayIn(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        int posts = 0;
        int open = 0;
        for (int x = lot.left(); x <= lot.right(); x++) {
            for (int y = lot.bottom(); y <= lot.top(); y++) {
                boolean ring = x == lot.left() || x == lot.right()
                        || y == lot.bottom() || y == lot.top();
                if (!ring) continue;
                if (lot.grid().isWalkable(x, y)) open++;
                else posts++;
            }
        }
        // The only openings a fence is allowed are its gates: one per side, and
        // no wider than a gate. Stated as a bound rather than a ratio, because a
        // ratio that holds on a forty-cell frontage says nothing on a
        // fourteen-cell one — the same four gates are a much larger share of it.
        assertTrue(open <= AirbaseLot.gatedSides() * AirbaseLot.gateWidth(),
                size + " " + facing + ": " + open + " cells of the perimeter stand open,"
                        + " which is more than its gates");
        assertTrue(open >= AirbaseLot.gateWidth(),
                size + " " + facing + ": a fence with no gate makes the lot a pocket");
    }

    /**
     * A way in on every side.
     *
     * <p>Front and back are how the base is used. The two ends are how
     * everybody else gets past it: a lot gated on one axis only is a wall
     * across the map for anything trying to move along the other, and the
     * fortress ward it sits in is wider than it is deep.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void thereIsAGateOnEverySide(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        assertTrue(openOnSide(lot, lot.left(), lot.bottom(), lot.left(), lot.top()),
                size + " " + facing + ": no way through the west side");
        assertTrue(openOnSide(lot, lot.right(), lot.bottom(), lot.right(), lot.top()),
                size + " " + facing + ": no way through the east side");
        assertTrue(openOnSide(lot, lot.left(), lot.bottom(), lot.right(), lot.bottom()),
                size + " " + facing + ": no way through the south side");
        assertTrue(openOnSide(lot, lot.left(), lot.top(), lot.right(), lot.top()),
                size + " " + facing + ": no way through the north side");
    }

    /** Whether any cell along this edge run can be walked through. */
    private static boolean openOnSide(Lot lot, int x0, int y0, int x1, int y1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (lot.grid().isWalkable(x, y)) return true;
            }
        }
        return false;
    }

    /**
     * The fence leaves room to walk past it.
     *
     * <p>A lot whose fence sits on the boundary of its own reservation can be
     * packed flush against, and the way round the base is then whatever the
     * packing happened to leave — including nothing. The clearance is part of
     * what a host reserves, so this pins that the lot does not quietly spend it.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void theFenceKeepsItsClearanceUntouched(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        for (int ring = 1; ring <= size.clearance(); ring++) {
            for (int x = lot.left() - ring; x <= lot.right() + ring; x++) {
                for (int y = lot.bottom() - ring; y <= lot.top() + ring; y++) {
                    boolean onRing = x == lot.left() - ring || x == lot.right() + ring
                            || y == lot.bottom() - ring || y == lot.top() + ring;
                    if (!onRing) continue;
                    assertTrue(lot.grid().isWalkable(x, y), size + " " + facing + ": " + x + "," + y
                            + " is " + ring + " cells outside the fence and cannot be walked");
                }
            }
        }
    }

    /**
     * The way out of a berth is kept clear.
     *
     * <p>The base's ground vehicles used to stand one in front of each berth,
     * which put a heavy-cover truck straight across the taxiway — the strip
     * everybody on the base walks between the sheds and the aircraft, and the
     * strip the aircraft itself rolls out along. They park at the end of the
     * apron now, and this is the guard against them drifting back.
     *
     * <p>Scoped to the three rows immediately behind each berth, which is
     * unambiguously taxiway: far enough from the sheds not to be about what is
     * inside them, and close enough to the berth to be exactly the ground the
     * old placement occupied.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void theGroundBehindEachBerthIsClear(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        boolean alongY = facing.alongY();
        for (LandingPad pad : berths(lot)) {
            for (Doodad d : lot.ctx().doodads) {
                if (d.cover == Doodad.COVER_NONE) continue;
                int across = alongY ? d.cellY : d.cellX;
                int back = alongY
                        ? (facing.sign() > 0 ? pad.top() : pad.bottom())
                        : (facing.sign() > 0 ? pad.right() : pad.left());
                int depth = (across - back) * facing.sign();
                if (depth <= 0 || depth > TAXIWAY_ROWS) continue;
                int along = alongY ? d.cellX : d.cellY;
                int lo = alongY ? pad.left() : pad.bottom();
                int hi = alongY ? pad.right() : pad.top();
                if (along < lo || along > hi) continue;
                throw new AssertionError(size + " " + facing + ": something solid is parked at "
                        + d.cellX + "," + d.cellY + ", on the way out of the berth at "
                        + pad.centerX + "," + pad.centerY);
            }
        }
    }

    /** Rows behind a berth that belong to the taxiway rather than to a shed. */
    private static final int TAXIWAY_ROWS = 3;

    /** Cells reachable on foot from {@code (x, y)}. */
    private static boolean[][] flood(NavigationGrid grid, int x, int y) {
        boolean[][] seen = new boolean[grid.getWidth()][grid.getHeight()];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{x, y});
        seen[x][y] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int[] step : steps) {
                int nx = cell[0] + step[0];
                int ny = cell[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= grid.getWidth() || ny >= grid.getHeight()) continue;
                if (seen[nx][ny] || !grid.isWalkable(nx, ny)) continue;
                seen[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return seen;
    }
}
