package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import org.apache.log4j.Logger;

/**
 * Step 1c — lay down the zoning overlay. In conquest mode
 * ({@link BspKeys#AXIS} bound) a {@link BiomeMap} takes precedence — biome
 * bands run along the traversal axis and fully drive theme picks. In legacy
 * mode a {@link DistrictMap} scatters themes uniformly with a CIVIC nudge at
 * the trunk crossing.
 *
 * <p>Reads {@link BspKeys#AXIS} (presence selects mode), {@link BspKeys#TRUNK_PLAN}
 * (intersection center for the CIVIC nudge) and {@link BspKeys#PARTITION} (for
 * the log line). Binds exactly one of {@link BspKeys#BIOME_MAP} /
 * {@link BspKeys#DISTRICT_MAP}.
 */
public final class ZoningOverlayStage implements GenStage {

    private static final Logger LOG = Logger.getLogger(ZoningOverlayStage.class);

    @Override
    public void run(GenContext ctx) {
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        TrunkPlan.Plan plan = ctx.get(BspKeys.TRUNK_PLAN);
        Bsp.Partition partition = ctx.get(BspKeys.PARTITION);
        if (axis != null) {
            BiomeMap biomeMap = new BiomeMap(ctx.width, ctx.height, axis, ctx.rng);
            ctx.put(BspKeys.BIOME_MAP, biomeMap);
            LOG.info("BspCityGenerator: " + partition.leaves.size() + " leaves on "
                    + ctx.width + "x" + ctx.height + " grid, "
                    + plan.trunks.size() + " trunk(s), biome axis=" + axis);
        } else {
            DistrictMap districtMap = new DistrictMap(ctx.width, ctx.height, ctx.rng);
            int ixCenterX = (plan.intersection.x0 + plan.intersection.x1) / 2;
            int ixCenterY = (plan.intersection.y0 + plan.intersection.y1) / 2;
            districtMap.forceThemeAt(ixCenterX, ixCenterY, MapDistrictTheme.CIVIC);
            ctx.put(BspKeys.DISTRICT_MAP, districtMap);
            LOG.info("BspCityGenerator: " + partition.leaves.size() + " leaves on "
                    + ctx.width + "x" + ctx.height + " grid, "
                    + plan.trunks.size() + " trunk(s), "
                    + districtMap.districtsX() + "x" + districtMap.districtsY() + " districts");
        }
    }
}
