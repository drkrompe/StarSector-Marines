package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.GenKey;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;

import java.util.List;

/** A fallen hull's facts on the generation blackboard. */
public final class HullKeys {

    private HullKeys() { }

    /** Where the patch puts the hull; bound before the recipe runs. */
    public static final GenKey<HullSitePlan> PLAN = GenKey.of("hullPlan");

    /** What {@link CrashSiteStage} laid before finalize, read by {@link CrashSiteFinishStage}. */
    static final GenKey<CrashSiteStage.Laid> LAID = GenKey.of("hullLaid");

    /** Where the debris shower struck buildings, as damage strokes; read by result assembly. */
    public static final GenKey<List<DamagePlan.Stroke>> STRIKES = GenKey.of("hullStrikes");

    /** The initial-damage layer the shower's strikes are applied as. */
    public static final String STRIKE_LAYER = "hull-shower";

    /** The finished site, read by result assembly. */
    public static final GenKey<HullSite> SITE = GenKey.of("hullSite");
}
