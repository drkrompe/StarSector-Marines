package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;

/**
 * Terminal Conquest map pass that publishes paired shuttle arrival areas on
 * the beach frontage. It consumes no RNG: candidates are scanned in stable
 * lateral/depth order after every terrain and emplacement mutation has run.
 */
public final class ConquestLandingAreaStage implements GenStage {

    /** Centers of the two 5x5 berths sit this far from the area's midpoint. */
    static final int BERTH_OFFSET = 4;
    /** Keeps neighboring 13-cell-wide areas disjoint with a visible gap. */
    static final int LATERAL_STEP = 16;
    /** Leaves the map corners as off-map approach space rather than touchdown ground. */
    static final int LATERAL_MARGIN = 12;

    @Override
    public void run(GenContext ctx) {
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        BiomeMap biomes = ctx.get(BspKeys.BIOME_MAP);
        if (axis == null || biomes == null) {
            throw new IllegalStateException(
                    "ConquestLandingAreaStage requires AXIS and BIOME_MAP");
        }
        if (!ctx.landingAreas.isEmpty()) {
            throw new IllegalStateException("Conquest landing areas already authored");
        }

        boolean advanceAlongY = axis == TraversalAxis.SOUTH_TO_NORTH;
        int lateralExtent = advanceAlongY ? ctx.width : ctx.height;
        LandingPad.Approach approach = advanceAlongY
                ? LandingPad.Approach.SOUTH : LandingPad.Approach.WEST;

        for (int lateral = LATERAL_MARGIN;
             lateral <= lateralExtent - 1 - LATERAL_MARGIN;
             lateral += LATERAL_STEP) {
            LandingArea area = firstClearArea(ctx, biomes, advanceAlongY,
                    lateral, approach);
            if (area != null) {
                ctx.landingAreas.add(area);
                ctx.landingPads.addAll(area.berths());
            }
        }
    }

    private static LandingArea firstClearArea(GenContext ctx, BiomeMap biomes,
                                               boolean advanceAlongY,
                                               int lateral,
                                               LandingPad.Approach approach) {
        int forwardExtent = advanceAlongY ? ctx.height : ctx.width;
        // A 5x5 berth needs two cells of inset on every side. Scan from the
        // attacker edge toward the city so the first legal result remains a
        // beachhead rather than an arbitrary deep cell.
        for (int forward = 2; forward < forwardExtent - 2; forward++) {
            int firstX = advanceAlongY ? lateral - BERTH_OFFSET : forward;
            int firstY = advanceAlongY ? forward : lateral - BERTH_OFFSET;
            int secondX = advanceAlongY ? lateral + BERTH_OFFSET : forward;
            int secondY = advanceAlongY ? forward : lateral + BERTH_OFFSET;
            LandingPad first = LandingPad.conquest(firstX, firstY, approach);
            LandingPad second = LandingPad.conquest(secondX, secondY, approach);
            if (!isLegalBerth(first, ctx, biomes)
                    || !isLegalBerth(second, ctx, biomes)) continue;
            String id = "conquest-arrival-" + lateral + "-" + forward;
            LandingArea area = new LandingArea(id, first, second);
            if (isLegalArea(area, ctx, biomes)) return area;
        }
        return null;
    }

    private static boolean isLegalArea(LandingArea area, GenContext ctx,
                                       BiomeMap biomes) {
        if (!LandingGround.isOpen(ctx, area)) return false;
        for (int y = area.bottom; y <= area.top; y++) {
            for (int x = area.left; x <= area.right; x++) {
                if (biomes.biomeAt(x, y) != BiomeKind.BEACH) return false;
            }
        }
        return true;
    }

    private static boolean isLegalBerth(LandingPad berth, GenContext ctx,
                                         BiomeMap biomes) {
        if (!berth.isClear(ctx.grid, ctx.topology)) return false;
        for (int y = berth.bottom(); y <= berth.top(); y++) {
            for (int x = berth.left(); x <= berth.right(); x++) {
                if (biomes.biomeAt(x, y) != BiomeKind.BEACH) return false;
            }
        }
        return true;
    }
}
