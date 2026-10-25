package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.MarketSquare;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans the ground for one market square ({@code set-piece-blocks.md}) after compound claims and before the
 * road graph: two complete commercial or plaza parcels and the minor street between them become one
 * {@link BlockKind#MARKET_SQUARE} lot the square fits. Ordinary leaves stop at eighteen cells and every street
 * between them carries the road reservation, so no single parcel or unplanned pair holds a framed square.
 *
 * <p>The rules are the industrial planning pass's ({@link IndustrialTemplateParcelStage}): only complete
 * parcels of the eligible kinds may be joined, the street ring round the lot must stay complete so every
 * neighbour keeps its frontage and every mouth gives onto road, and trunk roads are inviolate. The smallest
 * fitting lot wins, at most one per map, with no random draw. The road graph is derived afterwards from the
 * remaining streets, so it never routes through the lot.
 */
public final class MarketParcelStage implements GenStage {

    @Override public void run(GenContext c) {
        Bsp.Partition partition = c.get(BspKeys.PARTITION);
        TrunkPlan.Plan plan = c.get(BspKeys.TRUNK_PLAN);
        if (partition == null || plan == null) return;
        List<BlockLeaf> eligible = partition.leaves.stream().filter(MarketParcelStage::eligible).toList();
        BlockLeaf best = null;
        List<BlockLeaf> owned = List.of();
        for (int i = 0; i < eligible.size(); i++) {
            for (int j = i + 1; j < eligible.size(); j++) {
                BlockLeaf a = eligible.get(i), b = eligible.get(j);
                BlockLeaf box = new BlockLeaf(Math.min(a.left, b.left), Math.min(a.top, b.top),
                        Math.max(a.right, b.right), Math.max(a.bottom, b.bottom), false);
                if (best != null && box.area() >= best.area()) continue;
                if (!MarketSquare.fitsParcel(box.width(), box.height())) continue;
                if (!roadRing(box, partition) || crossesTrunk(box, plan)) continue;
                List<BlockLeaf> members = new ArrayList<>();
                boolean valid = true;
                for (BlockLeaf leaf : partition.leaves) {
                    if (!overlaps(box, leaf)) continue;
                    if (!eligible.contains(leaf) || !box.contains(leaf.left, leaf.top)
                            || !box.contains(leaf.right, leaf.bottom)) {
                        valid = false;
                        break;
                    }
                    members.add(leaf);
                }
                // Ground inside the rectangle that is neither street nor one of the parcels is somebody else's.
                for (int y = box.top; y <= box.bottom && valid; y++) {
                    for (int x = box.left; x <= box.right; x++) {
                        if (partition.roadCells[x][y]) continue;
                        boolean member = false;
                        for (BlockLeaf leaf : members) if (leaf.contains(x, y)) { member = true; break; }
                        if (!member) { valid = false; break; }
                    }
                }
                if (valid) {
                    best = box;
                    owned = members;
                }
            }
        }
        if (best == null) return;
        best.kind = BlockKind.MARKET_SQUARE;
        int index = partition.leaves.indexOf(owned.get(0));
        partition.leaves.removeAll(owned);
        partition.leaves.add(index, best);
        for (int y = best.top; y <= best.bottom; y++) {
            for (int x = best.left; x <= best.right; x++) {
                partition.roadCells[x][y] = false;
                plan.roadCells[x][y] = false;
            }
        }
    }

    private static boolean eligible(BlockLeaf leaf) {
        return leaf.kind == BlockKind.BUILDING_COMMERCIAL || leaf.kind == BlockKind.PLAZA
                || leaf.kind == BlockKind.DENSE_BLOCK || leaf.kind == BlockKind.BUILDING_RESIDENTIAL;
    }

    private static boolean overlaps(BlockLeaf a, BlockLeaf b) {
        return a.left <= b.right && a.right >= b.left && a.top <= b.bottom && a.bottom >= b.top;
    }

    private static boolean roadRing(BlockLeaf b, Bsp.Partition p) {
        if (b.left < 1 || b.top < 1 || b.right >= p.width - 1 || b.bottom >= p.height - 1) return false;
        for (int x = b.left - 1; x <= b.right + 1; x++) {
            if (!p.roadCells[x][b.top - 1] || !p.roadCells[x][b.bottom + 1]) return false;
        }
        for (int y = b.top - 1; y <= b.bottom + 1; y++) {
            if (!p.roadCells[b.left - 1][y] || !p.roadCells[b.right + 1][y]) return false;
        }
        return true;
    }

    private static boolean crossesTrunk(BlockLeaf b, TrunkPlan.Plan p) {
        for (TrunkPlan.TrunkSegment t : p.trunks) {
            if (b.left <= t.right && b.right >= t.left && b.top <= t.bottom && b.bottom >= t.top) return true;
        }
        return false;
    }
}
