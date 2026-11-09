package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;

/** The spaceport Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's. */
public final class RaidPortSiteLookSuite extends RaidSiteLookSuite {

    public RaidPortSiteLookSuite() {
        super("raid-port-site-look", "spaceport", EconomicFunction.COMMERCE);
    }
}
