package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctDefence;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Arms every walled precinct to the strength its fortification states.
 *
 * <p>Separate from {@link PrecinctWardStage} because it depends on that stage's
 * output rather than sharing its work: the emplacements are placed against a
 * finished wall, with the gates it left open and the buildings it packed already
 * standing in the way. Running it inside the ward would mean placing guns before
 * knowing where the ways in are.
 *
 * <p>Its counterpart on the conquest path is
 * {@link com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper},
 * which stamps the same emplacements against the map's biomes. Both call the
 * same placer; what differs is the region and where the seeds come from.
 */
public final class PrecinctDefenceStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        int[][] road = ctx.get(BspKeys.PRECINCT_ROAD);
        if (plan == null || claim == null || road == null) return;

        Map<String, Map<DefensePostKind, Integer>> unplaced = new LinkedHashMap<>();
        for (int i = 0; i < plan.precincts().size(); i++) {
            Precinct precinct = plan.precincts().get(i);
            if (precinct.boundary() != Precinct.Boundary.WALLED) continue;
            PrecinctDefence.Result result =
                    PrecinctDefence.stamp(ctx, precinct, claim, road, i);
            // Recorded rather than dropped, for the same reason the buildings
            // are. A garrison that was told to hold four heavy posts and found
            // room for two is a materially easier objective than the one the
            // mission asked for, and nothing else on the finished map says so:
            // an emplacement that was never stamped leaves no trace at all.
            if (result.unplacedCount() > 0) {
                unplaced.put(precinct.name(), result.unplaced());
            }
        }
        ctx.put(BspKeys.UNPLACED_DEFENCES, Map.copyOf(unplaced));
    }
}
