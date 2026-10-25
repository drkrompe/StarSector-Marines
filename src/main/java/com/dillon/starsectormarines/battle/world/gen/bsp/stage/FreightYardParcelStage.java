package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.FreightYard;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans the ground for one freight yard ({@code set-piece-blocks.md}) before compounds claim and
 * streets become a graph, and labels it {@link BlockKind#FREIGHT_YARD}.
 *
 * <p><b>A parcel, not a compound.</b> A compound claim keeps the streets between its members, and the
 * road reservation runs down them, so the largest clear rectangle a claim holds is one member and
 * the frame beside it: the larger yard fitted no compound on 24 maps. Joining two parcels across
 * their minor street before the road graph is derived removes that street, as the industrial
 * template pass does, so the joined parcel is clear ground. The outer street ring must be complete
 * and no trunk may cross it, so every neighbour keeps its frontage.
 *
 * <p><b>The ladder, largest first.</b> A pair of parcels joined into ground for the
 * {@link FreightYard.Size#LARGE two-bay yard}; else one parcel that holds it; else one that holds the
 * {@link FreightYard.Size#STANDARD standard yard}; else a pair joined for that. Working parcels are
 * preferred (industrial buildings and yards), then commercial and mixed ones a settlement can spare;
 * homes, civic blocks, compound seeds and natural ground are never taken. The fitting
 * ground nearest the map's edge wins, then the smallest, then the first found: stable, and no random draws.
 */
public final class FreightYardParcelStage implements GenStage {

    /** Cells of street outside a gate a parcel must have, the depth {@link FreightYard} asks of a gate's mouth. */
    static final int MOUTH = 2;

    @Override
    public void run(GenContext c) {
        Bsp.Partition partition = c.get(BspKeys.PARTITION);
        TrunkPlan.Plan plan = c.get(BspKeys.TRUNK_PLAN);
        if (partition == null || plan == null) return;
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.FREIGHT_YARD) return;
        }
        for (int tier = 0; tier < 2; tier++) {
            final int t = tier;
            List<BlockLeaf> eligible = partition.leaves.stream().filter(l -> tier(l.kind) >= 0 && tier(l.kind) <= t)
                    .toList();
            if (joinPair(partition, plan, eligible, FreightYard.Size.LARGE)) return;
            if (promoteSingle(partition, eligible, FreightYard.Size.LARGE)) return;
            if (promoteSingle(partition, eligible, FreightYard.Size.STANDARD)) return;
            if (joinPair(partition, plan, eligible, FreightYard.Size.STANDARD)) return;
        }
    }

    /** Which pass may take this kind: 0 working ground, 1 ground a settlement can spare, -1 never. */
    static int tier(BlockKind kind) {
        return switch (kind) {
            case BUILDING_INDUSTRIAL, INDUSTRIAL_YARD -> 0;
            case BUILDING_COMMERCIAL, DENSE_BLOCK, WASTELAND_RUBBLE, PLAZA -> 1;
            default -> -1;
        };
    }

    private static boolean promoteSingle(Bsp.Partition partition, List<BlockLeaf> eligible, FreightYard.Size size) {
        BlockLeaf best = null;
        for (BlockLeaf leaf : eligible) {
            if (!size.fits(leaf.width(), leaf.height()) || !opensOntoStreet(leaf, size, partition)) continue;
            if (best == null || better(leaf, best, partition)) best = leaf;
        }
        if (best == null) return false;
        best.kind = BlockKind.FREIGHT_YARD;
        return true;
    }

    /**
     * Whether {@code a} is better ground than {@code b}: nearer the map's edge, then smaller. A freight yard stands
     * where the road leaves town, and a hauler leaving one in the middle of a colony has the whole town to cross.
     */
    static boolean better(BlockLeaf a, BlockLeaf b, Bsp.Partition partition) {
        int ea = toEdge(a, partition), eb = toEdge(b, partition);
        return ea != eb ? ea < eb : a.area() < b.area();
    }

    /**
     * Whether some side of the parcel, long enough to carry the yard's gate wall, has street outside it deep enough
     * for the gates and the turn a hauler makes leaving them. Asked of the planned streets, so the parcel promoted is
     * one the filler can open onto a street rather than one it hands back as an ordinary yard.
     */
    static boolean opensOntoStreet(BlockLeaf parcel, FreightYard.Size size, Bsp.Partition partition) {
        // Each side: its first cell, the step along it, the step outward, and how deep the yard may stand from it.
        int[][] sides = {
                {parcel.left, parcel.top, 1, 0, 0, -1, parcel.height()},
                {parcel.left, parcel.bottom, 1, 0, 0, 1, parcel.height()},
                {parcel.left, parcel.top, 0, 1, -1, 0, parcel.width()},
                {parcel.right, parcel.top, 0, 1, 1, 0, parcel.width()}};
        for (int[] side : sides) {
            boolean alongX = side[2] == 1;
            int length = alongX ? parcel.width() : parcel.height();
            if (side[6] < size.depth || length < size.width) continue;
            int run = 0;
            for (int i = 0; i < length; i++) {
                int x = side[0] + side[2] * i, y = side[1] + side[3] * i;
                boolean street = true;
                for (int d = 1; d <= MOUTH && street; d++) {
                    int sx = x + side[4] * d, sy = y + side[5] * d;
                    street = sx >= 0 && sy >= 0 && sx < partition.width && sy < partition.height
                            && partition.roadCells[sx][sy];
                }
                run = street ? run + 1 : 0;
                if (run >= size.gateSpan()) return true;
            }
        }
        return false;
    }

    /** Cells from the parcel to the nearest map edge. */
    static int toEdge(BlockLeaf leaf, Bsp.Partition partition) {
        return Math.min(Math.min(leaf.left, leaf.top),
                Math.min(partition.width - 1 - leaf.right, partition.height - 1 - leaf.bottom));
    }

    /** Joins the pair of parcels whose box is the best ground that holds {@code size}, with the street between them. */
    private static boolean joinPair(Bsp.Partition partition, TrunkPlan.Plan plan, List<BlockLeaf> eligible,
                                    FreightYard.Size size) {
        BlockLeaf best = null;
        List<BlockLeaf> owned = List.of();
        for (int i = 0; i < eligible.size(); i++) {
            for (int j = i + 1; j < eligible.size(); j++) {
                BlockLeaf a = eligible.get(i), b = eligible.get(j);
                BlockLeaf box = new BlockLeaf(Math.min(a.left, b.left), Math.min(a.top, b.top),
                        Math.max(a.right, b.right), Math.max(a.bottom, b.bottom), false);
                if (best != null && !better(box, best, partition)) continue;
                if (!size.fits(box.width(), box.height())) continue;
                if (!IndustrialTemplateParcelStage.roadRing(box, partition)
                        || IndustrialTemplateParcelStage.crossesTrunk(box, plan)) continue;
                List<BlockLeaf> members = members(box, partition, eligible);
                if (members != null && opensOntoStreet(box, size, partition)) {
                    best = box;
                    owned = members;
                }
            }
        }
        if (best == null) return false;
        best.kind = BlockKind.FREIGHT_YARD;
        int index = partition.leaves.indexOf(owned.get(0));
        partition.leaves.removeAll(owned);
        partition.leaves.add(index, best);
        for (int y = best.top; y <= best.bottom; y++) {
            for (int x = best.left; x <= best.right; x++) {
                partition.roadCells[x][y] = false;
                plan.roadCells[x][y] = false;
            }
        }
        return true;
    }

    /**
     * The parcels the box joins, or null where it would take part of a parcel, a parcel that is not
     * eligible, or a hole that is neither street nor parcel: ground inside a rectangle is not ours
     * merely because it lies inside it.
     */
    private static List<BlockLeaf> members(BlockLeaf box, Bsp.Partition partition, List<BlockLeaf> eligible) {
        List<BlockLeaf> members = new ArrayList<>();
        for (BlockLeaf leaf : partition.leaves) {
            if (!IndustrialTemplateParcelStage.overlaps(box, leaf)) continue;
            if (!eligible.contains(leaf) || !box.contains(leaf.left, leaf.top)
                    || !box.contains(leaf.right, leaf.bottom)) return null;
            members.add(leaf);
        }
        for (int y = box.top; y <= box.bottom; y++) {
            for (int x = box.left; x <= box.right; x++) {
                if (partition.roadCells[x][y]) continue;
                boolean member = false;
                for (BlockLeaf leaf : members) {
                    if (leaf.contains(x, y)) {
                        member = true;
                        break;
                    }
                }
                if (!member) return null;
            }
        }
        return members;
    }
}
