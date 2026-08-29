package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ward with room for an airfield gets one, on ground nothing else wanted.
 *
 * <p>The berths are the point rather than the paving. A hardstand is authored
 * the way a machine berth in a vehicle shed is — a clear footprint and the
 * direction its approach faces — so what stands on it stays the host's to
 * decide from a roster.
 */
class FortressAirfieldTest {

    private static final int DEPTH = 28;
    private static final int W;
    private static final int H = DEPTH + 4;

    static {
        W = FortressProgram.envelopeArea(FortressProgram.ward()) / DEPTH + 4;
    }

    @Test
    void theWardGetsAnAirfieldWithBerthsOnOpenGround() {
        int seedsWithAField = 0;
        for (long seed : new long[]{ 1L, 5L, 9L }) {
            NavigationGrid grid = new NavigationGrid(W, H);
            CellTopology topology = new CellTopology(W, H);
            GenContext ctx = new GenContext(grid, topology, new Random(seed), W, H, seed);

            boolean[][] ground = new boolean[W][H];
            for (int x = 2; x < W - 2; x++) {
                for (int y = 2; y < H - 2; y++) ground[x][y] = true;
            }
            boolean[][] muster = new boolean[W][H];
            for (int y = 2; y < 10; y++) {
                muster[W / 2][y] = true;
                muster[W / 2 + 1][y] = true;
            }

            FortressInterior.pack(ctx, ground, muster,
                    TraversalAxis.SOUTH_TO_NORTH, FortressProgram.ward());

            long bases = ctx.tactical.stream()
                    .filter(node -> node.kind == TacticalNode.Kind.AIRBASE)
                    .count();
            long pads = ctx.landingPads.stream()
                    .filter(pad -> pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD)
                    .count();
            if (bases == 0) {
                assertEquals(0, pads, "seed " + seed
                        + ": hardstands without an airbase to hold them");
                continue;
            }
            seedsWithAField++;
            assertEquals(1, bases, "seed " + seed
                    + ": the airfield is one position to take, not a node per hardstand");
            assertTrue(pads > 0, "seed " + seed + ": an airfield with no berths on it");

            for (LandingPad pad : ctx.landingPads) {
                if (pad.purpose != LandingPad.Purpose.GARRISON_AIRFIELD) continue;
                for (int x = pad.left(); x <= pad.right(); x++) {
                    for (int y = pad.bottom(); y <= pad.top(); y++) {
                        assertTrue(grid.isWalkable(x, y), "seed " + seed
                                + ": a berth is a clear footprint, and " + x + "," + y
                                + " is not standable");
                    }
                }
            }
        }
        // Not every ward gets one — the apron takes ground the packing left
        // over, and a seed that left none has no airfield. What would be a
        // defect is a ward that never gets one at all.
        assertTrue(seedsWithAField > 0,
                "no seed produced an airfield: the ward is never leaving one room");
    }

    /**
     * A field is a facility: a shed with a wall round it, stands in the open,
     * and a fence saying where it starts.
     *
     * <p>Both axes, because the shed and the fence are the two things in the
     * airfield with a front and a back, and a mirrored layout that puts a
     * hangar's mouth against its own fence is exactly the bug this catches.
     */
    @ParameterizedTest
    @EnumSource(value = TraversalAxis.class,
            names = { "SOUTH_TO_NORTH", "WEST_TO_EAST" })
    void everyBerthIsClearAndCanBeWalkedTo(TraversalAxis axis) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        GenContext ctx = new GenContext(grid, topology, new Random(1L), W, H, 1L);
        boolean[][] ground = new boolean[W][H];
        for (int x = 1; x < W - 1; x++) {
            for (int y = 1; y < H - 1; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
                topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
                ground[x][y] = true;
            }
        }

        FortressAirfield field = FortressAirfield.site(ctx, ground, axis,
                1, 1, W - 2, H - 2);
        assertNotNull(field, axis + ": open ground this size has room for a field");
        field.author(ctx, axis);

        List<LandingPad> berths = ctx.landingPads.stream()
                .filter(pad -> pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD)
                .toList();
        assertEquals(3, berths.size(), axis + ": two stands in the open and one under cover");

        // Reachable from a far corner — through the fence's gate, across the
        // apron, and in through the hangar's mouth.
        boolean[][] reached = flood(grid, 1, 1);
        for (LandingPad pad : berths) {
            for (int x = pad.left(); x <= pad.right(); x++) {
                for (int y = pad.bottom(); y <= pad.top(); y++) {
                    assertTrue(grid.isWalkable(x, y), axis + ": berth cell " + x + "," + y
                            + " is not clear — something has to be able to land on it");
                }
            }
            assertTrue(reached[pad.centerX][pad.centerY], axis + ": the berth at "
                    + pad.centerX + "," + pad.centerY + " cannot be walked to. A sheltered"
                    + " berth whose only approach is through its own fence is a berth"
                    + " no crew can board at.");
        }
    }

    /**
     * One berth is behind a wall, and that is the whole point of the shed.
     *
     * <p>Two aircraft stand where anything with a sight line can burn them; the
     * third cannot be touched until somebody is inside the building. Without
     * the wall the hangar is a differently-coloured stand.
     */
    @ParameterizedTest
    @EnumSource(value = TraversalAxis.class,
            names = { "SOUTH_TO_NORTH", "WEST_TO_EAST" })
    void oneBerthStandsInsideAWalledShed(TraversalAxis axis) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        GenContext ctx = new GenContext(grid, topology, new Random(1L), W, H, 1L);
        boolean[][] ground = new boolean[W][H];
        for (int x = 1; x < W - 1; x++) {
            for (int y = 1; y < H - 1; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
                topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
                ground[x][y] = true;
            }
        }
        FortressAirfield field = FortressAirfield.site(ctx, ground, axis, 1, 1, W - 2, H - 2);
        assertNotNull(field);
        field.author(ctx, axis);

        int sheltered = 0;
        for (LandingPad pad : ctx.landingPads) {
            if (pad.purpose != LandingPad.Purpose.GARRISON_AIRFIELD) continue;
            if (walledOnEverySide(topology, grid, pad)) sheltered++;
        }
        assertEquals(1, sheltered, axis
                + ": exactly one berth is under cover — the other two are the"
                + " aircraft an attacker can reach without going indoors");
    }

    /** Whether a ring of wall surrounds this berth on all four sides. */
    private static boolean walledOnEverySide(CellTopology topology, NavigationGrid grid,
                                             LandingPad pad) {
        return wallAlong(topology, grid, pad, 1, 0) && wallAlong(topology, grid, pad, -1, 0)
                && wallAlong(topology, grid, pad, 0, 1) && wallAlong(topology, grid, pad, 0, -1);
    }

    /** Whether walking out from the berth centre in one direction meets a wall. */
    private static boolean wallAlong(CellTopology topology, NavigationGrid grid,
                                     LandingPad pad, int dx, int dy) {
        for (int step = 1; step <= 6; step++) {
            int x = pad.centerX + dx * step;
            int y = pad.centerY + dy * step;
            if (x < 0 || y < 0 || x >= topology.getWidth() || y >= topology.getHeight()) return false;
            if (topology.isWall(x, y)) return true;
            if (!grid.isWalkable(x, y)) return true;
        }
        return false;
    }

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
