package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;

/**
 * The colony a look suite frames beside its civilian rescue: the plan
 * {@code BattleSetup.createCivilianRescue(seed, manifest, heavyArmor, risk)} lays on its first attempt, on the
 * neutral world it is fought on, sealed where that world seals its towns. A suite that generates the colony to find a
 * street or a square asks here, so it reads the map its battle lays rather than one beside it.
 */
final class RescueColony {

    private RescueColony() {}

    static PrecinctPlan plan(MapScale scale, long seed) {
        return BattleSetup.colonyPlanOn(scale, seed, TargetProfile.NEUTRAL);
    }
}
