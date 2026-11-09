package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.GenKey;

/** The ruins recipe's facts on the generation blackboard. */
public final class RuinsKeys {

    private RuinsKeys() {}

    /** What the mission asked of the ground; bound before the recipe runs. */
    public static final GenKey<RuinsGroundPlan> PLAN = GenKey.of("ruinsPlan");

    /** The delve {@link RuinsDelveStage} laid, read by the stages after it and by result assembly. */
    public static final GenKey<RuinsDelve> DELVE = GenKey.of("ruinsDelve");
}
