package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;

/** The farming Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's. */
public final class RaidFarmSiteLookSuite extends RaidSiteLookSuite {

    public RaidFarmSiteLookSuite() {
        super("raid-farm-site-look", "farming", EconomicFunction.AGRICULTURE);
    }
}
