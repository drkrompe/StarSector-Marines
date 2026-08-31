package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
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
 * civic rolls become ordinary commercial buildings. Gated-housing seeds need
 * enough room for a 12x10 inset apartment block and demote to ordinary homes
 * when undersized. Industrial-compound seeds likewise require the 15x12
 * tactical-factory footprint and demote to ordinary industrial buildings.
 * Medical-campus seeds require the same large-lot envelope as their clinic
 * plan and demote to ordinary commercial buildings when undersized (rather
 * than bypassing the civic headquarters' own large-lot guard).
 */
public final class LabelLeavesStage implements GenStage {

    /** Minimum dimension a LANDING_ZONE leaf must have on both axes to keep that kind — smaller leaves get demoted to PLAZA, since tiny LZ pads tucked between big buildings read as "courtyard interior" rather than open touchdown apron. */
    private static final int LANDING_ZONE_MIN_SIDE = 5;
    /** Minimum long footprint dimension for a multi-room civic headquarters. */
    public static final int CIVIC_MIN_LONG_DIM = 15;
    /** Minimum short footprint dimension for a multi-room civic headquarters. */
    public static final int CIVIC_MIN_SHORT_DIM = 13;
    /** Minimum long outer lot dimension for a courtyard-facing apartment seed. */
    public static final int GATED_HOUSING_MIN_LONG_DIM = 16;
    /** Minimum short outer lot dimension for a courtyard-facing apartment seed. */
    public static final int GATED_HOUSING_MIN_SHORT_DIM = 14;
    /** Minimum long footprint for the factory seed in an industrial compound. */
    public static final int INDUSTRIAL_COMPOUND_MIN_LONG_DIM = 15;
    /** Minimum short footprint for the factory seed in an industrial compound. */
    public static final int INDUSTRIAL_COMPOUND_MIN_SHORT_DIM = 12;
    /** Minimum long footprint for the clinic seed in a medical campus. */
    public static final int MEDICAL_CAMPUS_MIN_LONG_DIM = 15;
    /** Minimum short footprint for the clinic seed in a medical campus. */
    public static final int MEDICAL_CAMPUS_MIN_SHORT_DIM = 12;

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        DistrictMap districtMap = ctx.get(BspKeys.DISTRICT_MAP);
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        boolean dryWorld = profile != null && !profile.surface().bearsOpenWater();
        for (BlockLeaf leaf : partition.leaves) {
            MapDistrictTheme theme = (biomeMap != null)
                    ? biomeMap.themeAt(leaf.centerX(), leaf.centerY())
                    : districtMap.themeAt(leaf.centerX(), leaf.centerY());
            leaf.kind = theme.pickBlockKind(ctx.rng);
            leaf.kind = constrainKindForSize(leaf.kind, leaf.width(), leaf.height());
            if (dryWorld) leaf.kind = constrainKindForDryWorld(leaf.kind);
        }
        ensureGatedHousingSeed(partition);
        ensureIndustrialCompoundSeed(partition);
        ensureMedicalCampusSeed(partition);
        ensureCivicHeadquartersSeed(partition);
    }

    /**
     * Water-bearing lots on a world with no water become open ground.
     *
     * <p>The shoreline stage already declines to stamp a sea on a dry world,
     * but the theme tables roll waterfront and wetland lots independently of
     * it, and those fillers place their own ponds. A barren rock kept about
     * ninety cells of standing water that way after the shore was gone —
     * fewer than before and no less impossible.
     *
     * <p>They become open ground rather than nothing, because structurally a
     * waterfront is the edge of the built area: the surface palette then
     * decides what that ground is made of.
     */
    static BlockKind constrainKindForDryWorld(BlockKind kind) {
        return switch (kind) {
            case WATERFRONT, NATURE_WETLAND, NATURE_BEACH -> BlockKind.NATURE_GRASSLAND;
            default -> kind;
        };
    }

    /** Promote only an already-residential qualifying lot when no natural seed rolled. */
    private static void ensureGatedHousingSeed(Bsp.Partition partition) {
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.GATED_HOUSING) return;
        }
        BlockLeaf best = null;
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind != BlockKind.BUILDING_RESIDENTIAL) continue;
            if (Math.max(leaf.width(), leaf.height()) < GATED_HOUSING_MIN_LONG_DIM
                    || Math.min(leaf.width(), leaf.height()) < GATED_HOUSING_MIN_SHORT_DIM) {
                continue;
            }
            if (best == null || leaf.area() > best.area()) best = leaf;
        }
        if (best != null) best.kind = BlockKind.GATED_HOUSING;
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

    /** Promote the largest qualifying commercial lot when no clinic seed rolled. */
    private static void ensureMedicalCampusSeed(Bsp.Partition partition) {
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.MEDICAL_CAMPUS) return;
        }
        BlockLeaf best = null;
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind != BlockKind.BUILDING_COMMERCIAL) continue;
            if (Math.max(leaf.width(), leaf.height()) < MEDICAL_CAMPUS_MIN_LONG_DIM
                    || Math.min(leaf.width(), leaf.height()) < MEDICAL_CAMPUS_MIN_SHORT_DIM) {
                continue;
            }
            if (best == null || leaf.area() > best.area()) best = leaf;
        }
        if (best != null) best.kind = BlockKind.MEDICAL_CAMPUS;
    }

    /**
     * Preserve one properly sized headquarters after the compound seed passes
     * have claimed their qualifying parcels. A smaller parcel remains
     * commercial rather than inheriting an office program its rooms cannot hold.
     */
    private static void ensureCivicHeadquartersSeed(Bsp.Partition partition) {
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.BUILDING_CIVIC) return;
        }
        BlockLeaf best = null;
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind != BlockKind.BUILDING_COMMERCIAL) continue;
            if (Math.max(leaf.width(), leaf.height()) < CIVIC_MIN_LONG_DIM
                    || Math.min(leaf.width(), leaf.height()) < CIVIC_MIN_SHORT_DIM) {
                continue;
            }
            if (best == null || leaf.area() > best.area()) best = leaf;
        }
        if (best != null) best.kind = BlockKind.BUILDING_CIVIC;
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
        if (kind == BlockKind.GATED_HOUSING
                && (Math.max(width, height) < GATED_HOUSING_MIN_LONG_DIM
                    || Math.min(width, height) < GATED_HOUSING_MIN_SHORT_DIM)) {
            return BlockKind.BUILDING_RESIDENTIAL;
        }
        if (kind == BlockKind.INDUSTRIAL_COMPOUND
                && (Math.max(width, height) < INDUSTRIAL_COMPOUND_MIN_LONG_DIM
                    || Math.min(width, height) < INDUSTRIAL_COMPOUND_MIN_SHORT_DIM)) {
            return BlockKind.BUILDING_INDUSTRIAL;
        }
        if (kind == BlockKind.MEDICAL_CAMPUS
                && (Math.max(width, height) < MEDICAL_CAMPUS_MIN_LONG_DIM
                    || Math.min(width, height) < MEDICAL_CAMPUS_MIN_SHORT_DIM)) {
            return BlockKind.BUILDING_COMMERCIAL;
        }
        return kind;
    }
}
