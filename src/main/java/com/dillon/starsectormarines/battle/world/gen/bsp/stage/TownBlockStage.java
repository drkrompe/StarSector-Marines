package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.hull.HullKeys;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSitePlan;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Town blocks ({@code town-plan.md}): once compounds, districts and the market lot have claimed their ground and
 * before the road graph is built, two ordinary shop or home lots in the built-up town and the minor street
 * between them become one block, so a street is lined by a row of buildings rather than cut by a side street
 * every lot. The partition cuts lots about one building in size (five to seven cells deep, eight to twelve
 * long); a block is what a frontage row needs. Joining this late leaves the labels and every planned claim as
 * they were: only lots nothing else wanted are joined.
 *
 * <p>Two lots join only where they face each other across one minor street along the whole of their shared
 * side, so the street removed runs from one cross street to the next and no dead end is left; the street
 * ring round the block must be complete and no trunk may cross it, the market lot's rules
 * ({@link MarketParcelStage}). A block grows by further joins up to {@link #LONG_MAX} by {@link #SHORT_MAX}.
 * Only shop and home lots whose zoning is residential, civic or mixed town join: the outskirts, the coast and
 * the industrial belt keep their grain. A block is a shop block if either lot was shops and the shops' lot was
 * the larger, else homes. Draws no random number; the road graph is built afterwards from the streets left.
 */
public final class TownBlockStage implements GenStage {

    /** Longest side of a town block. */
    static final int LONG_MAX = 36;
    /** Shortest side's ceiling: two rows back to back with an alley, never a block too deep to front. */
    static final int SHORT_MAX = 20;
    /**
     * A grown block's longest and shortest side: inside a story map's block boxes ({@link HullSitePlan#blocks}) a
     * city's lots join into the big, dense blocks a fight brings down. UNTUNED.
     */
    static final int GROWN_LONG_MAX = 56, GROWN_SHORT_MAX = 32;
    /** Cells a grown block keeps off a starting stake's cell, so its lattice point keeps open ground. */
    static final int STAKE_KEEP = 10;
    /** Widest street two lots may be joined across: the partition's frames, never a trunk. */
    static final int STREET_MAX = Bsp.ROAD_WIDTH_MAX;

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        TrunkPlan.Plan plan = ctx.get(BspKeys.TRUNK_PLAN);
        DistrictMap districts = ctx.get(BspKeys.DISTRICT_MAP);
        if (partition == null || plan == null || districts == null || ctx.get(BspKeys.BIOME_MAP) != null) return;
        HullSitePlan crash = ctx.get(HullKeys.PLAN);
        assemble(partition, plan, leaf -> (leaf.kind == BlockKind.BUILDING_COMMERCIAL
                || leaf.kind == BlockKind.BUILDING_RESIDENTIAL)
                && town(districts.themeAt(leaf.centerX(), leaf.centerY())),
                crash == null ? List.of() : grown(crash));
        if (crash != null) {
            openStakeLots(partition, crash);
            openPortLots(partition, crash);
        }
    }

    /**
     * In each of a free port's quarters ({@link HullSitePlan#port}) a share of the building lots wholly inside its box
     * ({@link HullSitePlan.Port.Kind#openEvery}) is left open ground, of the lots at least
     * {@link HullSitePlan.Port.Kind#leastLot} on a side, by turns in the partition's order, so the quarter's yards, pads
     * and stalls have lots to stand on.
     */
    static void openPortLots(Bsp.Partition partition, HullSitePlan crash) {
        for (HullSitePlan.Port q : crash.port()) {
            HullSitePlan.Box b = q.box();
            int n = 0;
            for (BlockLeaf leaf : partition.leaves) {
                if (!leaf.kind.name().startsWith("BUILDING_") || !b.contains(leaf.left, leaf.top, leaf.right, leaf.bottom)
                        || Math.min(leaf.right - leaf.left, leaf.bottom - leaf.top) + 1 < q.kind().leastLot()) continue;
                if (n++ % q.kind().openEvery() == 0) leaf.kind = BlockKind.PLAZA;
            }
        }
    }

    /**
     * On a map whose city is grown into blocks, every starting stake's lot is left an open square: the building lot
     * nearest its cell, within {@link #STAKE_KEEP}, is a plaza, so the claim's lattice point there has open ground to
     * seat on whatever the city round it grew to.
     */
    static void openStakeLots(Bsp.Partition partition, HullSitePlan crash) {
        if (crash.blocks().isEmpty()) return;
        for (HullSitePlan.StakeAt stake : crash.stakes()) {
            BlockLeaf best = null;
            long bestD = (long) STAKE_KEEP * STAKE_KEEP + 1;
            for (BlockLeaf leaf : partition.leaves) {
                if (leaf.kind != BlockKind.BUILDING_COMMERCIAL && leaf.kind != BlockKind.BUILDING_RESIDENTIAL) continue;
                long dx = Math.max(0, Math.max(leaf.left - stake.x(), stake.x() - leaf.right));
                long dy = Math.max(0, Math.max(leaf.top - stake.y(), stake.y() - leaf.bottom));
                if (dx * dx + dy * dy < bestD) {
                    bestD = dx * dx + dy * dy;
                    best = leaf;
                }
            }
            if (best != null) best.kind = BlockKind.PLAZA;
        }
    }

    /**
     * The crash plan's block boxes less a stake's ground: a box with a starting stake in it is cut back to the side of
     * the stake that keeps the most of it, {@link #STAKE_KEEP} off it, so the stake's lattice point keeps its open ground.
     */
    static List<HullSitePlan.Box> grown(HullSitePlan crash) {
        List<HullSitePlan.Box> out = new ArrayList<>(crash.blocks());
        for (HullSitePlan.StakeAt stake : crash.stakes()) {
            List<HullSitePlan.Box> next = new ArrayList<>();
            for (HullSitePlan.Box b : out) {
                int sx = stake.x(), sy = stake.y(), k = STAKE_KEEP;
                if (sx + k < b.x0() || sx - k > b.x1() || sy + k < b.y0() || sy - k > b.y1()) {
                    next.add(b);
                    continue;
                }
                // The four pieces of the box round the stake's square.
                if (sx - k - 1 >= b.x0()) next.add(new HullSitePlan.Box(b.x0(), b.y0(), sx - k - 1, b.y1()));
                if (sx + k + 1 <= b.x1()) next.add(new HullSitePlan.Box(sx + k + 1, b.y0(), b.x1(), b.y1()));
                if (sy - k - 1 >= b.y0()) next.add(new HullSitePlan.Box(b.x0(), b.y0(), b.x1(), sy - k - 1));
                if (sy + k + 1 <= b.y1()) next.add(new HullSitePlan.Box(b.x0(), sy + k + 1, b.x1(), b.y1()));
            }
            out = next;
        }
        return out;
    }

    static boolean town(MapDistrictTheme theme) {
        return theme == MapDistrictTheme.RESIDENTIAL || theme == MapDistrictTheme.CIVIC
                || theme == MapDistrictTheme.MIXED;
    }

    /** Joins town lots into blocks until no pair joins. Returns how many joins were made. */
    static int assemble(Bsp.Partition partition, TrunkPlan.Plan plan, Predicate<BlockLeaf> town) {
        return assemble(partition, plan, town, List.of());
    }

    /** As above; a block lying wholly inside one of {@code grown} may grow to the grown limits. */
    static int assemble(Bsp.Partition partition, TrunkPlan.Plan plan, Predicate<BlockLeaf> town,
                        List<HullSitePlan.Box> grown) {
        int joins = 0;
        boolean joined = true;
        while (joined) {
            joined = false;
            for (int i = 0; i < partition.leaves.size() && !joined; i++) {
                BlockLeaf a = partition.leaves.get(i);
                if (!town.test(a)) continue;
                for (int j = i + 1; j < partition.leaves.size(); j++) {
                    BlockLeaf b = partition.leaves.get(j);
                    if (!town.test(b)) continue;
                    BlockLeaf block = join(a, b, partition, plan, grown);
                    if (block == null) continue;
                    partition.leaves.set(i, block);
                    partition.leaves.remove(j);
                    for (int y = block.top; y <= block.bottom; y++) {
                        for (int x = block.left; x <= block.right; x++) {
                            partition.roadCells[x][y] = false;
                            plan.roadCells[x][y] = false;
                        }
                    }
                    joins++;
                    joined = true;
                    break;
                }
            }
        }
        return joins;
    }

    /**
     * The block {@code a} and {@code b} make with the street between them, or null: they must face each other
     * across nothing but street along the whole of one side, the block must fit the size limits, keep a
     * complete street ring and cross no trunk.
     */
    static BlockLeaf join(BlockLeaf a, BlockLeaf b, Bsp.Partition partition, TrunkPlan.Plan plan) {
        return join(a, b, partition, plan, List.of());
    }

    static BlockLeaf join(BlockLeaf a, BlockLeaf b, Bsp.Partition partition, TrunkPlan.Plan plan,
                          List<HullSitePlan.Box> grown) {
        boolean sideBySide = a.top == b.top && a.bottom == b.bottom;
        boolean stacked = a.left == b.left && a.right == b.right;
        if (!sideBySide && !stacked) return null;
        BlockLeaf lo = sideBySide ? (a.left < b.left ? a : b) : (a.top < b.top ? a : b);
        BlockLeaf hi = lo == a ? b : a;
        int gap = sideBySide ? hi.left - lo.right - 1 : hi.top - lo.bottom - 1;
        if (gap < Bsp.ROAD_WIDTH_MIN || gap > STREET_MAX) return null;
        BlockLeaf block = new BlockLeaf(lo.left, lo.top, hi.right, hi.bottom, a.touchesMapEdge || b.touchesMapEdge);
        BlockLeaf larger = a.area() > b.area() || (a.area() == b.area() && a.kind == BlockKind.BUILDING_COMMERCIAL)
                ? a : b;
        block.kind = larger.kind;
        boolean big = false;
        for (HullSitePlan.Box box : grown) big |= box.contains(block.left, block.top, block.right, block.bottom);
        if (Math.max(block.width(), block.height()) > (big ? GROWN_LONG_MAX : LONG_MAX)
                || Math.min(block.width(), block.height()) > (big ? GROWN_SHORT_MAX : SHORT_MAX)) return null;
        // The street between them is street end to end: no third lot stands in it.
        for (int y = block.top; y <= block.bottom; y++) {
            for (int x = block.left; x <= block.right; x++) {
                if (lo.contains(x, y) || hi.contains(x, y)) continue;
                if (!partition.roadCells[x][y]) return null;
            }
        }
        if (!roadRing(block, partition) || crossesTrunk(block, plan)) return null;
        return block;
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
