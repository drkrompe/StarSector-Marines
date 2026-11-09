package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;

/** The refining Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's. */
public final class RaidRefinerySiteLookSuite extends RaidSiteLookSuite {

    public RaidRefinerySiteLookSuite() {
        super("raid-refinery-site-look", "refining", EconomicFunction.REFINING);
    }
}
