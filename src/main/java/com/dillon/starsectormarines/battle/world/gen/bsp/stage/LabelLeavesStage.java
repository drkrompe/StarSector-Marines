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
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
 * landing apron.
 */
public final class LabelLeavesStage implements GenStage {

    /** Minimum dimension a LANDING_ZONE leaf must have on both axes to keep that kind — smaller leaves get demoted to PLAZA, since tiny LZ pads tucked between big buildings read as "courtyard interior" rather than open touchdown apron. */
    private static final int LANDING_ZONE_MIN_SIDE = 5;

    @Override
    public void run(GenContext ctx) {
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);
        DistrictMap districtMap = ctx.get(BspKeys.DISTRICT_MAP);
        List<BlockLeaf> civilianPortLeaves = new ArrayList<>();
        for (BlockLeaf leaf : partition.leaves) {
            MapDistrictTheme theme = (biomeMap != null)
                    ? biomeMap.themeAt(leaf.centerX(), leaf.centerY())
                    : districtMap.themeAt(leaf.centerX(), leaf.centerY());
            leaf.kind = theme.pickBlockKind(ctx.rng);
            if (biomeMap == null && theme == MapDistrictTheme.HARBOR_PORT
                    && leaf.width() >= LANDING_ZONE_MIN_SIDE
                    && leaf.height() >= LANDING_ZONE_MIN_SIDE) {
                civilianPortLeaves.add(leaf);
            }
            if ((leaf.kind == BlockKind.LANDING_ZONE || leaf.kind == BlockKind.SPACEPORT_PAD)
                    && (leaf.width() < LANDING_ZONE_MIN_SIDE
                        || leaf.height() < LANDING_ZONE_MIN_SIDE)) {
                leaf.kind = BlockKind.PLAZA;
            }
        }

        // Tier 1 reserves up to three berths (the default deployment), while a
        // megaport can reserve five. Largest parcels win; tie order remains
        // spatially deterministic. Surrounding HARBOR_PORT leaves retain their
        // warehouse/yard/commercial rolls and complete the district.
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        if (biomeMap == null && profile != null && profile.spaceportTier() > 0) {
            int targetPads = profile.spaceportTier() >= 2 ? 5 : 3;
            civilianPortLeaves.sort(Comparator
                    .comparingInt(BlockLeaf::area).reversed()
                    .thenComparingInt(l -> l.top)
                    .thenComparingInt(l -> l.left));
            for (int i = 0; i < Math.min(targetPads, civilianPortLeaves.size()); i++) {
                civilianPortLeaves.get(i).kind = BlockKind.SPACEPORT_PAD;
            }
        }
    }
}
