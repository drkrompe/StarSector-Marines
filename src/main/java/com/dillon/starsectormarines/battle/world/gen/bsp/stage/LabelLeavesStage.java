package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;

/**
 * Step 2 — label each leaf using whichever zoning overlay is active. In
 * conquest mode {@link BspKeys#BIOME_MAP} drives the theme pick (biome-band
 * placement along the traversal axis); in legacy mode {@link BspKeys#DISTRICT_MAP}
 * drives it (uniform district scatter). Exactly one of the two is bound.
 *
 * <p>Constraint guard for legacy mode: only WATERFRONT-theme districts can
 * produce WATERFRONT blocks; {@link DistrictMap} constrains that theme to
 * map-edge districts. Conquest mode lets WATERFRONT appear in BEACH theme as
 * well — accepting the occasional interior misfire because BEACH biome cells
 * get a SAND ground override that still sells the look.
 *
 * <p>Per-kind size constraint applied after the roll:
 * {@link BlockKind#LANDING_ZONE} requires both sides &gt;=
 * {@link #LANDING_ZONE_MIN_SIDE}. Smaller leaves get demoted to
 * {@link BlockKind#PLAZA} — a tiny striped pad wedged between building leaves
 * reads visually as "courtyard inside the buildings" rather than as an open
 * landing apron. Civic headquarters require a genuinely large lot so their
 * two-cell spine and four side rooms remain useful at infantry scale; smaller
 * civic rolls become ordinary commercial buildings. Industrial-compound
 * seeds likewise require the 15x12 tactical-factory footprint and demote to
 * ordinary industrial buildings when undersized.
 */
public final class LabelLeavesStage implements GenStage {

    /** Minimum dimension a LANDING_ZONE leaf must have on both axes to keep that kind — smaller leaves get demoted to PLAZA, since tiny LZ pads tucked between big buildings read as "courtyard interior" rather than open touchdown apron. */
    private static final int LANDING_ZONE_MIN_SIDE = 5;
    /** Minimum long footprint dimension for a multi-room civic headquarters. */
    public static final int CIVIC_MIN_LONG_DIM = 13;
    /** Minimum short footprint dimension for a multi-room civic headquarters. */
    public static final int CIVIC_MIN_SHORT_DIM = 11;
    /** Minimum long footprint for the factory seed in an industrial compound. */
    public static final int INDUSTRIAL_COMPOUND_MIN_LONG_DIM = 15;
    /** Minimum short footprint for the factory seed in an industrial compound. */
    public static final int INDUSTRIAL_COMPOUND_MIN_SHORT_DIM = 12;

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        DistrictMap districtMap = ctx.get(BspKeys.DISTRICT_MAP);
        for (BlockLeaf leaf : partition.leaves) {
            MapDistrictTheme theme = (biomeMap != null)
                    ? biomeMap.themeAt(leaf.centerX(), leaf.centerY())
                    : districtMap.themeAt(leaf.centerX(), leaf.centerY());
            leaf.kind = theme.pickBlockKind(ctx.rng);
            leaf.kind = constrainKindForSize(leaf.kind, leaf.width(), leaf.height());
        }
        ensureIndustrialCompoundSeed(partition);
    }

    /**
     * Keep the compound visible without converting arbitrary districts: when
     * no natural seed rolled, promote the largest already-industrial lot that
     * can hold the tactical factory. The claim pass still requires two valid
     * neighbors and demotes a failed seed back to an ordinary factory.
     */
    private static void ensureIndustrialCompoundSeed(Bsp.Partition partition) {
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.INDUSTRIAL_COMPOUND) return;
        }
        BlockLeaf best = null;
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind != BlockKind.BUILDING_INDUSTRIAL
                    && leaf.kind != BlockKind.INDUSTRIAL_YARD) continue;
            if (Math.max(leaf.width(), leaf.height()) < INDUSTRIAL_COMPOUND_MIN_LONG_DIM
                    || Math.min(leaf.width(), leaf.height()) < INDUSTRIAL_COMPOUND_MIN_SHORT_DIM) {
                continue;
            }
            if (best == null || leaf.area() > best.area()) best = leaf;
        }
        if (best != null) best.kind = BlockKind.INDUSTRIAL_COMPOUND;
    }

    static BlockKind constrainKindForSize(BlockKind kind, int width, int height) {
        if ((kind == BlockKind.LANDING_ZONE || kind == BlockKind.SPACEPORT_PAD)
                && (width < LANDING_ZONE_MIN_SIDE || height < LANDING_ZONE_MIN_SIDE)) {
            return BlockKind.PLAZA;
        }
        if (kind == BlockKind.BUILDING_CIVIC
                && (Math.max(width, height) < CIVIC_MIN_LONG_DIM
                    || Math.min(width, height) < CIVIC_MIN_SHORT_DIM)) {
            return BlockKind.BUILDING_COMMERCIAL;
        }
        if (kind == BlockKind.INDUSTRIAL_COMPOUND
                && (Math.max(width, height) < INDUSTRIAL_COMPOUND_MIN_LONG_DIM
                    || Math.min(width, height) < INDUSTRIAL_COMPOUND_MIN_SHORT_DIM)) {
            return BlockKind.BUILDING_INDUSTRIAL;
        }
        return kind;
    }
}
