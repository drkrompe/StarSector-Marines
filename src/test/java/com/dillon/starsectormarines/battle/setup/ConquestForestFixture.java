package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

/** Snapshot adapter to the production mission's plan selection without standing up a battle. */
public final class ConquestForestFixture {
    private ConquestForestFixture() {}
    public static MapResult generate(TargetProfile profile, TraversalAxis axis) {
        long seed = 4096;
        var plan = BattleSetup.conquestPlanFor(OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM,
                profile, PrecinctPlan.Sprawl.BALANCED, axis, seed);
        return new BspCityGenerator().generate(560, 336, seed, null, profile, plan);
    }
}
