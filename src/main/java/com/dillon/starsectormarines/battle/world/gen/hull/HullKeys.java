package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.GenKey;

/** A fallen hull's facts on the generation blackboard. */
public final class HullKeys {

    private HullKeys() { }

    /** Where the patch puts the hull; bound before the recipe runs. */
    public static final GenKey<HullSitePlan> PLAN = GenKey.of("hullPlan");

    /** What {@link CrashSiteStage} laid before finalize, read by {@link CrashSiteFinishStage}. */
    static final GenKey<CrashSiteStage.Laid> LAID = GenKey.of("hullLaid");

    /** The finished site, read by result assembly. */
    public static final GenKey<HullSite> SITE = GenKey.of("hullSite");
}
