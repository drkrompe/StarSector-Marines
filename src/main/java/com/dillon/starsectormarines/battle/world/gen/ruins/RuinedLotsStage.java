package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Every lot of a dead settlement is a ruin ({@code mapgen-nouns.md}, Ruins ground): each labelled leaf becomes a
 * {@link BlockKind#WASTELAND_RUBBLE} lot, which the ruined-lot filler draws as a burned-out footprint (standing
 * wall runs, rubble stubs, a collapsed corner, wrecked furniture), or a rubble heap where the lot is too small.
 * Open country stays country. Nothing is claimed as a compound: a compound is a working facility, and nothing on
 * this ground works except what the delve lays.
 *
 * <p>The settlement's lots are relabelled rather than drawn as a special case, so the ruins are a value the
 * ordinary fill dispatch reads, not an exception in it.
 *
 * <p>A ruin is joined to nothing ({@code SettlementLink.NONE}): its streets die out in rubble short of the map
 * edge, so no road leaves the map and the road graph offers no off-map entry for ground reinforcement.
 */
public final class RuinedLotsStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        if (partition == null) throw new IllegalStateException("RuinedLotsStage requires a partition");
        for (BlockLeaf leaf : partition.leaves) {
            if (!isCountry(leaf.kind)) leaf.kind = BlockKind.WASTELAND_RUBBLE;
        }
        ctx.put(BspKeys.COMPOUNDS, new ArrayList<>());
        TrunkPlan.Plan trunk = ctx.get(BspKeys.TRUNK_PLAN);
        if (trunk == null) return;
        endStreets(ctx, trunk.roadCells);
        // The road graph marks each trunk's centre line from the plan, so the trunks end where their cells do.
        List<TrunkPlan.TrunkSegment> clipped = new ArrayList<>();
        int w = trunk.roadCells.length, h = w == 0 ? 0 : trunk.roadCells[0].length;
        for (TrunkPlan.TrunkSegment t : trunk.trunks) {
            int left = Math.max(t.left, STREET_FALLOFF), right = Math.min(t.right, w - 1 - STREET_FALLOFF);
            int top = Math.max(t.top, STREET_FALLOFF), bottom = Math.min(t.bottom, h - 1 - STREET_FALLOFF);
            if (left <= right && top <= bottom) clipped.add(new TrunkPlan.TrunkSegment(left, top, right, bottom, t.kind, t.horizontal));
        }
        ctx.put(BspKeys.TRUNK_PLAN, new TrunkPlan.Plan(trunk.roadCells, trunk.subRects, clipped, trunk.intersection,
                trunk.width, trunk.height));
    }

    /** Cells from the map edge within which a dead settlement's streets are gone. */
    static final int STREET_FALLOFF = 6;

    static void endStreets(GenContext ctx, boolean[][] roads) {
        int rubble = ctx.topology.surfaceIndex(RuinsDelveStage.RUBBLE_BED);
        for (int x = 0; x < roads.length; x++) {
            for (int y = 0; y < roads[x].length; y++) {
                if (!roads[x][y]) continue;
                int edge = Math.min(Math.min(x, roads.length - 1 - x), Math.min(y, roads[x].length - 1 - y));
                if (edge >= STREET_FALLOFF) continue;
                roads[x][y] = false;
                ctx.topology.setGroundKind(x, y, GroundKind.RUBBLE);
                ctx.topology.setSurface(x, y, rubble);
            }
        }
    }

    static boolean isCountry(BlockKind kind) {
        return kind == BlockKind.NATURE_GRASSLAND || kind == BlockKind.NATURE_WETLAND || kind == BlockKind.NATURE_BEACH;
    }
}
