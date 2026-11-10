package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.NaturalVoidZoning;
import com.dillon.starsectormarines.battle.world.gen.GenKey;

/** The ruins recipe's facts on the generation blackboard. */
public final class RuinsKeys {

    private RuinsKeys() {}

    /** What the mission asked of the ground; bound before the recipe runs. */
    public static final GenKey<RuinsGroundPlan> PLAN = GenKey.of("ruinsPlan");

    /** The delve {@link RuinsDelveStage} laid, read by the stages after it and by result assembly. */
    public static final GenKey<RuinsDelve> DELVE = GenKey.of("ruinsDelve");

    /** A forge works' foundry melt, row-major, that {@link RuinsMeltStage} cuts after finalize; absent without one. */
    public static final GenKey<boolean[]> MELT = GenKey.of("ruinsMelt");

    /**
     * The drop {@link #MELT} is cut as: a works hall's shaft or sump ({@link WorksHallPlan.Drop}); absent for a
     * foundry's melt.
     */
    public static final GenKey<NaturalVoidZoning.Drop> MELT_DROP = GenKey.of("ruinsMeltDrop");
}
