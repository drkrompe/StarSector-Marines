package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;

/** The mining Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's. */
public final class RaidMineSiteLookSuite extends RaidSiteLookSuite {

    public RaidMineSiteLookSuite() {
        super("raid-mine-site-look", "mining", EconomicFunction.MINING);
    }
}
