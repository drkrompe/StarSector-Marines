package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;

/** The lightindustry Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's. */
public final class RaidLightIndustrySiteLookSuite extends RaidSiteLookSuite {

    public RaidLightIndustrySiteLookSuite() {
        super("raid-light-industry-site-look", "lightindustry", EconomicFunction.HEAVY_INDUSTRY);
    }
}
