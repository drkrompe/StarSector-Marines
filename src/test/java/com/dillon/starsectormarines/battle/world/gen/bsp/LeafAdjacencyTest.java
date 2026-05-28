package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shape tests for {@link LeafAdjacency}. Builds a real BSP partition from
 * {@link TrunkPlan} + {@link Bsp#partitionRect} and verifies the adjacency
 * map upholds the invariants the compound claim pass relies on.
 */
public class LeafAdjacencyTest {

    private static final int W = 80;
    private static final int H = 80;

    private static List<BlockLeaf> buildLeaves(TrunkPlan.Plan plan, long bspSeed) {
        List<BlockLeaf> leaves = new ArrayList<>();
        Random bspRng = new Random(bspSeed);
        for (TrunkPlan.SubRect r : plan.subRects) {
            Bsp.partitionRect(r.x0, r.y0, r.x1, r.y1, W, H,
                    Bsp.ROAD_WIDTH_MAX_WITH_TRUNKS, plan.roadCells, leaves, bspRng);
        }
        return leaves;
    }

    @Test
    public void adjacencyIsSymmetric() {
        for (long seed : new long[]{1L, 42L, 100L, 777L, 9999L}) {
            TrunkPlan.Plan plan = TrunkPlan.generate(W, H, new Random(seed));
            List<BlockLeaf> leaves = buildLeaves(plan, seed);
            Map<BlockLeaf, List<BlockLeaf>> adj = LeafAdjacency.compute(leaves, W, H);
            for (BlockLeaf a : leaves) {
                for (BlockLeaf b : adj.get(a)) {
                    assertTrue(adj.get(b).contains(a),
                            "seed=" + seed + " adjacency not symmetric: "
                                    + leafLabel(a) + " -> " + leafLabel(b)
                                    + " but reverse missing");
                }
            }
        }
    }

    @Test
    public void everyLeafHasANeighbor() {
        for (long seed : new long[]{1L, 42L, 100L, 777L, 9999L}) {
            TrunkPlan.Plan plan = TrunkPlan.generate(W, H, new Random(seed));
            List<BlockLeaf> leaves = buildLeaves(plan, seed);
            Map<BlockLeaf, List<BlockLeaf>> adj = LeafAdjacency.compute(leaves, W, H);
            for (BlockLeaf leaf : leaves) {
                assertNotNull(adj.get(leaf), "missing entry for " + leafLabel(leaf));
                assertFalse(adj.get(leaf).isEmpty(),
                        "seed=" + seed + " leaf has no neighbors: " + leafLabel(leaf));
            }
        }
    }

    /**
     * The defining property: leaves on opposite sides of either trunk should
     * not be adjacent. A compound that grows over the trunk would span the
     * city's main street — wrong.
     */
    @Test
    public void trunksActAsBarrier() {
        for (long seed : new long[]{1L, 42L, 100L, 777L, 9999L}) {
            TrunkPlan.Plan plan = TrunkPlan.generate(W, H, new Random(seed));
            List<BlockLeaf> leaves = buildLeaves(plan, seed);
            Map<BlockLeaf, List<BlockLeaf>> adj = LeafAdjacency.compute(leaves, W, H);

            TrunkPlan.TrunkSegment primary   = trunk(plan, TrunkPlan.TrunkKind.PRIMARY);
            TrunkPlan.TrunkSegment secondary = trunk(plan, TrunkPlan.TrunkKind.SECONDARY);
            int hTop = primary.top,   hBot = primary.bottom;
            int vL   = secondary.left, vR  = secondary.right;

            for (BlockLeaf a : leaves) {
                int aQuad = quadrant(a, hTop, hBot, vL, vR);
                if (aQuad < 0) continue;
                for (BlockLeaf b : adj.get(a)) {
                    int bQuad = quadrant(b, hTop, hBot, vL, vR);
                    if (bQuad < 0) continue;
                    assertTrue(aQuad == bQuad,
                            "seed=" + seed + " adjacency crosses trunk: "
                                    + leafLabel(a) + " (Q" + aQuad + ") <-> "
                                    + leafLabel(b) + " (Q" + bQuad + ")");
                }
            }
        }
    }

    private static TrunkPlan.TrunkSegment trunk(TrunkPlan.Plan plan, TrunkPlan.TrunkKind kind) {
        for (TrunkPlan.TrunkSegment t : plan.trunks) if (t.kind == kind) return t;
        throw new AssertionError("missing trunk: " + kind);
    }

    /** Returns 0=TL, 1=TR, 2=BL, 3=BR, or -1 if the leaf straddles a trunk band (shouldn't happen with the current pipeline). */
    private static int quadrant(BlockLeaf leaf, int hTop, int hBot, int vL, int vR) {
        boolean above = leaf.bottom < hTop;
        boolean below = leaf.top    > hBot;
        boolean left  = leaf.right  < vL;
        boolean right = leaf.left   > vR;
        if (above && left)  return 0;
        if (above && right) return 1;
        if (below && left)  return 2;
        if (below && right) return 3;
        return -1;
    }

    private static String leafLabel(BlockLeaf leaf) {
        return "(" + leaf.left + "," + leaf.top + ")-(" + leaf.right + "," + leaf.bottom + ")";
    }
}
