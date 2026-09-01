package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A walled precinct owes a drivable way out, and the artery is what pays it.
 *
 * <p>Hand-built rather than grown. The failure this pins is geometric — a claim
 * that already reaches the edge the artery is aimed at — and a grown fixture
 * reproduces it only by luck of the seed, which is how it shipped: the ray runs
 * off the map still inside the claim, the carve returns at the map boundary,
 * and no gate is made without a word.
 */
class PrecinctArteryTest {

    private static final int W = 40;
    private static final int H = 30;

    /**
     * A claim against the left edge, with the left edge as its nearest one.
     *
     * <p>Aimed at that edge the ray never leaves the claim. Aimed instead at
     * the first ground the precinct does not hold, it crosses the outline where
     * the claim actually ends, which is the point of the carve.
     */
    @Test
    void aClaimAgainstTheMapEdgeStillGetsAWayOut() {
        int[][] claim = new int[W][H];
        int[][] owner = new int[W][H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                claim[x][y] = x <= 20 ? 1 : GrownTrunkPlan.UNOWNED;
                owner[x][y] = GrownTrunkPlan.UNOWNED;
            }
        }

        assertTrue(PrecinctBoundary.gates(claim, owner, 1, W, H).isEmpty(),
                "the fixture is supposed to start with no road at all, so the artery is "
                        + "the only thing that can make a gate here");

        boolean carved = PrecinctArtery.ensure(claim, owner, 1, 10, 14, W, H);
        assertTrue(carved, "a precinct with no road whatever was left without an artery, so "
                + "nothing drives out of it");

        List<PrecinctBoundary.Gate> gates = PrecinctBoundary.gates(claim, owner, 1, W, H);
        assertTrue(gates.stream().anyMatch(PrecinctBoundary.Gate::drivable),
                "the carve left the widest way out at "
                        + gates.stream().mapToInt(PrecinctBoundary.Gate::width).max().orElse(0)
                        + " cells, under the " + PrecinctBoundary.DRIVABLE_GATE_WIDTH
                        + " a vehicle needs: the ray ran off the map inside the claim "
                        + "instead of crossing its outline");
    }
}
