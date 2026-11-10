package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.StormBank;
import com.dillon.starsectormarines.battle.world.gen.StormBankDressing;
import com.dillon.starsectormarines.battle.world.gen.StormDrift;
import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

/**
 * A storm world banks its own ground against the windward face of every wall ({@link StormDrift}). The weather is the
 * last skin on the ground: it runs after {@link RoadSurfaceStage} has finished the streets, so a drift never denies a
 * carriageway its asphalt. A raised storm bank is dressed here too ({@link StormBankDressing}), once the streets
 * that run through its passes are known. Inert without a market behind the map or without {@link Climate.Hazard#STORMS}.
 */
public final class StormDriftStage implements GenStage {

    @Override public void run(GenContext c) {
        TargetProfile profile = c.get(BspKeys.MARKET_PROFILE);
        if (profile == null || !profile.climate().has(Climate.Hazard.STORMS)) return;
        StormDrift.paint(c.topology, SurfaceField.of(c), profile.climate().wind());
        PrecinctPlan plan = c.get(BspKeys.PRECINCTS);
        if (StormBank.raises(profile) && plan != null && !plan.landscape().kept().isEmpty()) {
            StormBankDressing.dress(c, profile.climate().wind());
        }
    }
}
