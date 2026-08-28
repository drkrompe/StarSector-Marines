package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
