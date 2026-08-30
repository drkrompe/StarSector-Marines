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

    /**
     * The published strip is the strip that was painted.
     *
     * <p>Asked of the ground rather than of the formula: every cell the lot
     * painted as runway has to fall inside the runway it published, and the
     * published strip has to be no bigger than the paint. A centreline derived
     * a second way from the same numbers would agree with itself while both
     * drifted from the surface an aircraft actually rolls on.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void thePublishedStripIsTheOneThatWasPainted(AirbaseLot.Size size,
                                                 AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        List<Runway> published = lot.ctx().runways;

        int painted = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int x = 0; x < lot.grid().getWidth(); x++) {
            for (int y = 0; y < lot.grid().getHeight(); y++) {
                if (lot.topology().getGroundKind(x, y) != AirbaseLot.RUNWAY) continue;
                painted++;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
        }

        if (painted == 0) {
            assertTrue(published.isEmpty(),
                    size + " has no strip but published " + published.size());
            return;
        }
        assertEquals(1, published.size(), size + " laid one strip");
        Runway strip = published.get(0);

        boolean rollsAlongX = Math.abs(strip.endX - strip.startX)
                > Math.abs(strip.endY - strip.startY);
        // Thresholds land on the centres of the end cells, and the centreline
        // runs down the middle of the width. Exact, not merely inside the
        // paint: a centreline a cell off is still inside a four-row strip, and
        // that is precisely the error worth catching.
        float rollLo = rollsAlongX ? Math.min(strip.startX, strip.endX)
                : Math.min(strip.startY, strip.endY);
        float rollHi = rollsAlongX ? Math.max(strip.startX, strip.endX)
                : Math.max(strip.startY, strip.endY);
        int paintedLo = rollsAlongX ? minX : minY;
        int paintedHi = rollsAlongX ? maxX : maxY;
        assertEquals(paintedLo + 0.5f, rollLo, 1e-3f, "near threshold");
        assertEquals(paintedHi + 0.5f, rollHi, 1e-3f, "far threshold");

        float crossStart = rollsAlongX ? strip.startY : strip.startX;
        float crossEnd = rollsAlongX ? strip.endY : strip.endX;
        int crossLo = rollsAlongX ? minY : minX;
        int crossHi = rollsAlongX ? maxY : maxX;
        float crossCentre = (crossLo + crossHi) / 2f + 0.5f;
        assertEquals(crossCentre, crossStart, 1e-3f, "centreline is not down the middle");
        assertEquals(crossCentre, crossEnd, 1e-3f, "centreline is not straight");

        assertEquals(paintedHi - paintedLo, Math.round(strip.lengthCells()), "roll distance");
        assertEquals(crossHi - crossLo + 1, Math.round(strip.widthCells), "made width");
        assertEquals((paintedHi - paintedLo + 1) * (crossHi - crossLo + 1), painted,
                "the paint is a solid rectangle");
    }

    /**
     * Every shed publishes a shelter, and every shelter is a berth an aircraft
     * could actually be got out of.
     *
     * <p>Clear ground to stand on, facing the mouth, and an unobstructed line
     * from the berth to open apron. A bay an aircraft cannot leave is a
     * decorated dead end, and the failure would only surface as a sortie that
     * never launches.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void everyShedHoldsAnAircraftItCanGetOut(AirbaseLot.Size size,
                                             AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        List<Gantry> shelters = lot.ctx().shelters;
        assertEquals(size.hangars(), shelters.size(), size + " sheds, " + size + " shelters");

        for (Gantry shelter : shelters) {
            for (int x = shelter.centerX - shelter.halfWidth; x <= shelter.centerX + shelter.halfWidth; x++) {
                for (int y = shelter.centerY - shelter.halfHeight; y <= shelter.centerY + shelter.halfHeight; y++) {
                    assertTrue(lot.grid().isWalkable(x, y),
                            "shelter cell (" + x + "," + y + ") is not standable");
                }
            }
            // Walk out the way the berth faces until we are past the shed. The
            // mouth is the only opening, so anything in the way is a wall the
            // aircraft would have to go through.
            int steps = 0;
            int x = shelter.centerX;
            int y = shelter.centerY;
            while (steps < size.depth) {
                x += shelter.facing.dx;
                y += shelter.facing.dy;
                steps++;
                assertTrue(lot.grid().isWalkable(x, y),
                        size + "/" + facing + ": shelter at (" + shelter.centerX + ","
                                + shelter.centerY + ") is walled in " + steps + " out");
                if (lot.topology().getRoomPurpose(x, y) != RoomPurpose.HANGAR) break;
                if (lot.topology().getGroundKind(x, y) == GroundKind.STREET) break;
            }
        }
    }

    /** A roll runs toward wherever the sortie is going, so it leaves the strip pointing there. */
    @ParameterizedTest
    @MethodSource("shapes")
    void aRollStartsFromTheThresholdFurthestFromWhereItIsGoing(AirbaseLot.Size size,
                                                               AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        if (lot.ctx().runways.isEmpty()) return;
        Runway strip = lot.ctx().runways.get(0);

        float[] fromStartEnd = strip.departureThreshold(strip.endX, strip.endY);
        assertEquals(strip.startX, fromStartEnd[0], 1e-3f);
        assertEquals(strip.startY, fromStartEnd[1], 1e-3f);

        float[] other = strip.opposite(fromStartEnd);
        assertEquals(strip.endX, other[0], 1e-3f);
        assertEquals(strip.endY, other[1], 1e-3f);
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
     * Every side is either gated or built against, and at least two are gated.
     *
     * <p>Front and back are how the base is used; the ends are how everybody
     * else gets past it, so a lot gated on one axis only is a wall across the
     * map for anything moving along the other. But a gate has to open onto
     * something. On a compact lot the shed's back sits against the perimeter,
     * and a gap cut there is a doorway into masonry: it reads from outside as a
     * way in and is not one. A side closed by a building is closed honestly.
     *
     * <p>Two gates rather than four, then, because two is what makes the lot a
     * through-route rather than a cul-de-sac, and four is only available when
     * nothing is built against the fence.
     */
    @ParameterizedTest
    @MethodSource("shapes")
    void everySideIsGatedOrBuiltAgainst(AirbaseLot.Size size, AirbaseLot.Facing facing) {
        Lot lot = author(size, facing);
        int gated = 0;
        for (int side = 0; side < 4; side++) {
            boolean open = false;
            boolean builtAgainst = false;
            for (int x = lot.left(); x <= lot.right(); x++) {
                for (int y = lot.bottom(); y <= lot.top(); y++) {
                    boolean onSide = switch (side) {
                        case 0 -> x == lot.left();
                        case 1 -> x == lot.right();
                        case 2 -> y == lot.bottom();
                        default -> y == lot.top();
                    };
                    if (!onSide) continue;
                    if (lot.grid().isWalkable(x, y)) open = true;
                    int inX = x == lot.left() ? x + 1 : x == lot.right() ? x - 1 : x;
                    int inY = y == lot.bottom() ? y + 1 : y == lot.top() ? y - 1 : y;
                    if (lot.topology().isWall(inX, inY)) builtAgainst = true;
                }
            }
            assertTrue(open || builtAgainst, size + " " + facing + ": side " + side
                    + " is fenced shut with open ground behind it and no gate");
            if (open) gated++;
        }
        assertTrue(gated >= 2, size + " " + facing + ": only " + gated
                + " side(s) let anyone in, which makes the lot a cul-de-sac");
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
