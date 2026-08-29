package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/**
 * Puts one small airbase in the city, on the largest block still going spare.
 *
 * <p><b>After the compounds have claimed, not with the labelling.</b> It was a
 * promotion inside {@code LabelLeavesStage} at first, which runs before compound
 * seeding — and compound seeding takes the largest leaf it can find, which is
 * the same leaf. The airbase was assigned and then silently overwritten on
 * every seed, so nothing appeared and nothing complained. Picking from what is
 * left is both correct and the same principle the fortress airfield follows:
 * a landmark takes ground the map has not already promised to something bigger.
 *
 * <p>Promoted rather than rolled per leaf. An airbase is a landmark — one per
 * city reads as a place the city has, several read as a city made of airfields
 * — and only a handful of blocks can hold one, so a per-leaf roll would put it
 * on a minority of seeds and never where it fits best.
 */
public final class AirbasePadSeedStage implements GenStage {

    /**
     * Whether the lot fits this block at any facing.
     *
     * <p>Asked of the lot rather than stated as a pair of numbers here. Two
     * places that both encode what an airbase needs will disagree, and the way
     * they disagree is that the seed promotes a block the filler then declines
     * — which produces a city with no airbase and no error.
     */
    private static boolean holdsALot(BlockLeaf leaf) {
        for (AirbaseLot.Facing facing : AirbaseLot.Facing.values()) {
            if (leaf.width() >= AirbaseLot.spanX(AirbaseLot.Size.STRIP, facing)
                    && leaf.height() >= AirbaseLot.spanY(AirbaseLot.Size.STRIP, facing)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this kind is ordinary city a landmark may replace.
     *
     * <p>Excludes the multi-leaf compound seeds, which own their neighbours,
     * and the natural kinds, whose ground is the point. Everything else is a
     * block of city, and a block of city can be an airfield instead.
     */
    private static boolean ordinaryCityBlock(BlockKind kind) {
        return switch (kind) {
            case MILITARY_BASE, GATED_HOUSING, DENSE_QUARTER, INDUSTRIAL_COMPOUND,
                 MEDICAL_CAMPUS, COMPOUND_MEMBER, WATERFRONT, PARK,
                 NATURE_GRASSLAND, NATURE_WETLAND, NATURE_BEACH,
                 AIRBASE_PAD -> false;
            default -> true;
        };
    }

    /**
     * Put one small airbase in the city, on the largest block that can hold it.
     *
     * <p>Promoted rather than rolled. An airbase is a landmark — one per city
     * reads as a place the city has, and several read as a city made of
     * airfields — and the blocks that can hold one are the handful of largest,
     * so leaving it to a per-leaf roll would have it appear on a minority of
     * seeds and never where it fits best.
     *
     * <p>Industrial ground by preference. A landing pad wedged between houses
     * is a strange thing; one at the back of a yard or a depot is what an
     * operator with a light aircraft actually has.
     */
    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        if (partition == null) return;
        seed(partition);
    }

    private static void seed(Bsp.Partition partition) {
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.AIRBASE_PAD) return;
        }
        BlockLeaf best = null;
        for (BlockLeaf leaf : partition.leaves) {
            if (!ordinaryCityBlock(leaf.kind)) continue;
            if (!holdsALot(leaf)) continue;
            if (best == null || leaf.area() > best.area()) best = leaf;
        }
        if (best != null) best.kind = BlockKind.AIRBASE_PAD;
    }

}
