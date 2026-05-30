package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.apache.log4j.Logger;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Step 3a' — pedestrian-frame classification. Per-leaf-pair RNG roll converts
 * narrow road frames between pairs of non-vehicular leaves (residential / plaza
 * / park) into GRASS + curb-side SIDEWALK, dropping the road in favor of a
 * park-like inter-building pocket. Adds visual variety to larger maps without
 * disrupting the trunk road network or any vehicular district's access.
 *
 * <p>Walks every {@link CellTopology.GroundKind#STREET} cell that isn't part of
 * a {@link TrunkPlan.TrunkSegment} band, finds the two leaves bounding the
 * frame perpendicular to the cell, and — if both leaves are non-vehicular kinds
 * and a per-pair RNG roll passes — converts the cell to a pedestrian zone:
 * {@link CellTopology.GroundKind#SIDEWALK} where the cell butts up against a
 * leaf (the curb-side strip), or {@link CellTopology.GroundKind#GRASS} for
 * cells in the frame interior.
 *
 * <p>Pair decisions are cached so all cells in the same frame agree on the
 * outcome — partial frames (some cells converted, some not) would read as
 * artifact. Cells at four-way intersections (both perpendicular axes resolve to
 * a leaf within scan range) are skipped — intersections stay vehicular
 * regardless of district mix.
 */
public final class PedestrianFrameStage implements GenStage {

    private static final Logger LOG = Logger.getLogger(PedestrianFrameStage.class);

    /** Cells of perpendicular-scan depth to find the leaf bounding a road frame. Catches every BSP frame (3-4 cells wide) but stops before the SECONDARY trunk (5 cells). */
    private static final int PEDESTRIAN_SCAN_DEPTH = 5;
    /** Frame widths up to this count qualify as "narrow" and may be converted to pedestrian zones. Wider frames stay vehicular. */
    private static final int PEDESTRIAN_MAX_FRAME_WIDTH = 4;
    /** Per-leaf-pair probability of converting their shared frame to a pedestrian zone. 0.8 gives noticeable variety on larger maps without taking over. */
    private static final float PEDESTRIAN_FRAME_CHANCE = 0.8f;

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        classifyPedestrianFrames(ctx.grid, ctx.topology, partition.leaves,
                ctx.get(BspKeys.TRUNK_PLAN), ctx.rng);
    }

    private void classifyPedestrianFrames(NavigationGrid grid, CellTopology topology,
                                          List<BlockLeaf> leaves, TrunkPlan.Plan plan,
                                          Random rng) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        BlockLeaf[][] cellLeaf = new BlockLeaf[w][h];
        for (BlockLeaf leaf : leaves) {
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                for (int x = leaf.left; x <= leaf.right; x++) {
                    if (x >= 0 && x < w && y >= 0 && y < h) cellLeaf[x][y] = leaf;
                }
            }
        }

        // Per-(unordered-pair-of-leaves) cached decision. IdentityHashMap so
        // BlockLeaf identity drives the key — there's no equals/hashCode on it.
        Map<BlockLeaf, Map<BlockLeaf, Boolean>> pairFlag = new IdentityHashMap<>();
        for (BlockLeaf leaf : leaves) pairFlag.put(leaf, new IdentityHashMap<>());

        int converted = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!topology.isStreet(x, y)) continue;
                if (isInTrunk(x, y, plan)) continue;
                BlockLeaf nLeaf = scanForLeaf(cellLeaf, x, y, 0,  1, w, h);
                BlockLeaf sLeaf = scanForLeaf(cellLeaf, x, y, 0, -1, w, h);
                BlockLeaf eLeaf = scanForLeaf(cellLeaf, x, y, 1,  0, w, h);
                BlockLeaf wLeaf = scanForLeaf(cellLeaf, x, y, -1, 0, w, h);
                boolean hasNS = nLeaf != null && sLeaf != null && nLeaf != sLeaf;
                boolean hasEW = eLeaf != null && wLeaf != null && eLeaf != wLeaf;
                if (hasNS == hasEW) continue;  // intersection (both) or unbounded (neither)
                BlockLeaf l1 = hasNS ? nLeaf : eLeaf;
                BlockLeaf l2 = hasNS ? sLeaf : wLeaf;
                if (!isPedestrianKind(l1.kind) || !isPedestrianKind(l2.kind)) continue;
                if (!leafPairConverts(pairFlag, l1, l2, rng)) continue;
                // Wall-adjacent cells (any cardinal neighbor is part of a
                // leaf) become SIDEWALK so the curb-side art kicks in; the
                // frame interior becomes GRASS. Width-3 frames produce a
                // SIDEWALK/GRASS/SIDEWALK strip; width-4 frames split into
                // SIDEWALK/GRASS/GRASS/SIDEWALK.
                if (isAdjacentToLeafCell(cellLeaf, x, y, w, h)) {
                    topology.setGroundKind(x, y, CellTopology.GroundKind.SIDEWALK);
                } else {
                    topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
                }
                converted++;
            }
        }
        if (converted > 0) {
            LOG.info("BspCityGenerator: pedestrian-frame pass converted "
                    + converted + " STREET cell(s) to GRASS/SIDEWALK");
        }
    }

    /** True for {@link BlockKind}s that read as pedestrian — residential, plaza, park. Commercial / industrial / fortified / LZ keep their road access. */
    private static boolean isPedestrianKind(BlockKind kind) {
        switch (kind) {
            case BUILDING_RESIDENTIAL:
            case PLAZA:
            case PARK:
                return true;
            default:
                return false;
        }
    }

    /** Cell-in-rect predicate against {@link TrunkPlan.TrunkSegment}s. Pedestrian conversion always skips trunk cells regardless of the kinds touching them. */
    private static boolean isInTrunk(int x, int y, TrunkPlan.Plan plan) {
        for (TrunkPlan.TrunkSegment t : plan.trunks) {
            if (x >= t.left && x <= t.right && y >= t.top && y <= t.bottom) return true;
        }
        return false;
    }

    /** First non-null leaf hit when stepping {@code (dx, dy)} up to {@link #PEDESTRIAN_SCAN_DEPTH} cells. Null if none. */
    private static BlockLeaf scanForLeaf(BlockLeaf[][] cellLeaf, int x, int y,
                                         int dx, int dy, int w, int h) {
        for (int step = 1; step <= PEDESTRIAN_SCAN_DEPTH; step++) {
            int nx = x + dx * step;
            int ny = y + dy * step;
            if (nx < 0 || nx >= w || ny < 0 || ny >= h) return null;
            BlockLeaf leaf = cellLeaf[nx][ny];
            if (leaf != null) {
                if (step > PEDESTRIAN_MAX_FRAME_WIDTH) return null;  // frame too wide
                return leaf;
            }
        }
        return null;
    }

    /** True if any cardinal neighbor of {@code (x, y)} belongs to a leaf (so this STREET cell is curb-side against a building). */
    private static boolean isAdjacentToLeafCell(BlockLeaf[][] cellLeaf, int x, int y, int w, int h) {
        if (x + 1 < w && cellLeaf[x + 1][y] != null) return true;
        if (x - 1 >= 0 && cellLeaf[x - 1][y] != null) return true;
        if (y + 1 < h && cellLeaf[x][y + 1] != null) return true;
        if (y - 1 >= 0 && cellLeaf[x][y - 1] != null) return true;
        return false;
    }

    /** Per-pair decision, memoized in both directions so all cells in the same frame see the same result. */
    private static boolean leafPairConverts(Map<BlockLeaf, Map<BlockLeaf, Boolean>> pairFlag,
                                            BlockLeaf l1, BlockLeaf l2, Random rng) {
        Boolean cached = pairFlag.get(l1).get(l2);
        if (cached != null) return cached;
        boolean ped = rng.nextFloat() < PEDESTRIAN_FRAME_CHANCE;
        pairFlag.get(l1).put(l2, ped);
        pairFlag.get(l2).put(l1, ped);
        return ped;
    }
}
