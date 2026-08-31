package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/**
 * Guarantees the lifeline of a settlement supplied by ship. Present in a recipe
 * only when the settlement is {@link SettlementLink#LANDING}, which has no road
 * off the map and must therefore hold somewhere a ship can put down.
 *
 * <p>Promoted rather than rolled, for the same reason
 * {@link AirbasePadSeedStage} promotes its airbase: the blocks that can hold a
 * pad are the handful of largest, so a per-leaf roll would put the settlement's
 * only lifeline on a minority of seeds. Unlike that stage this one is not a
 * flavour landmark — without it an off-grid settlement is unreachable, which is
 * the whole defect the {@link SettlementLink} vocabulary exists to prevent.
 *
 * <p>It runs late enough that the compound seeds have already taken their
 * parcels, and early enough that the claim stage still sees the promotion —
 * beside {@link AirbasePadSeedStage}, which learned that ordering the hard way.
 */
public final class SettlementLandingLinkStage implements GenStage {

    /**
     * Smallest leaf that actually publishes a landing pad.
     *
     * <p>A {@code LANDING_ZONE} below this is still striped and marked but
     * contributes nothing to {@code MapResult.landingPads}, so promoting one
     * would produce a settlement that looks linked and is not.
     */
    private static final int PAD_MIN_SIDE = 5;

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        if (partition == null) return;

        for (BlockLeaf leaf : partition.leaves) {
            if (publishesAPad(leaf)) return;
        }
        BlockLeaf best = null;
        for (BlockLeaf leaf : partition.leaves) {
            if (!ordinaryCityBlock(leaf.kind)) continue;
            if (leaf.width() < PAD_MIN_SIDE || leaf.height() < PAD_MIN_SIDE) continue;
            if (best == null || leaf.area() > best.area()) best = leaf;
        }
        if (best != null) best.kind = BlockKind.LANDING_ZONE;
    }

    /**
     * Whether this leaf already gives the settlement somewhere to land. The
     * size test is part of the question for the two pad kinds: below
     * {@link #PAD_MIN_SIDE} their fillers publish no pad, so a leaf that merely
     * carries the label does not count as a link.
     */
    private static boolean publishesAPad(BlockLeaf leaf) {
        return switch (leaf.kind) {
            case LANDING_ZONE, SPACEPORT_PAD ->
                    leaf.width() >= PAD_MIN_SIDE && leaf.height() >= PAD_MIN_SIDE;
            case AIRBASE_PAD, AIRBASE_COMPOUND -> true;
            default -> false;
        };
    }

    /**
     * Whether this kind is ordinary city the lifeline may replace. Mirrors
     * {@link AirbasePadSeedStage}'s exclusion: the multi-leaf compound seeds own
     * their neighbours, and the natural kinds are ground rather than city.
     */
    private static boolean ordinaryCityBlock(BlockKind kind) {
        return switch (kind) {
            case MILITARY_BASE, GATED_HOUSING, DENSE_QUARTER, INDUSTRIAL_COMPOUND,
                 MEDICAL_CAMPUS, COMPOUND_MEMBER, WATERFRONT, PARK,
                 NATURE_GRASSLAND, NATURE_WETLAND, NATURE_BEACH,
                 AIRBASE_PAD, AIRBASE_COMPOUND -> false;
            default -> true;
        };
    }
}
