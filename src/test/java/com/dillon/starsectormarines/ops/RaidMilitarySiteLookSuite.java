package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;

/** The militarybase Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's. */
public final class RaidMilitarySiteLookSuite extends RaidSiteLookSuite {

    public RaidMilitarySiteLookSuite() {
        super("raid-military-site-look", "militarybase", EconomicFunction.MILITARY);
    }
}
