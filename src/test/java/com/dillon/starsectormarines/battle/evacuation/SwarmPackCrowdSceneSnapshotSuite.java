package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

/**
 * Records {@link SwarmPackCrowdScene}: the whole ground, and a 1920x1080 close
 * view on the fight, with every sub-pack drawn as an arrow from its lead to
 * its quarry.
 */
public final class SwarmPackCrowdSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    /** Cells across the close view. */
    private static final float CLOSE_CELLS = 26f;

    public SwarmPackCrowdSceneSnapshotSuite() {
        super(new SwarmPackCrowdScene(), 1920, 1080);
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected ReviewAnnotations annotations(BattleSimulation sim) {
        int[][] marines = SwarmPackCrowdScene.MARINES;
        ReviewAnnotations.Builder builder = ReviewAnnotations.builder()
                .label(marines[0][0] + 0.5f, marines[0][1] + 2.2f, "lone marine", ReviewStyle.NOTE)
                .label(marines[1][0] + 1.5f, marines[1][1] - 1.6f, "fireteam", ReviewStyle.NOTE);
        for (int id = 1; id <= 4; id++) {
            SwarmPackPlans.Plan plan = sim.swarmPackPlans().planFor(id);
            if (plan == null) continue;
            for (int s = 0; s < plan.subPackCount(); s++) {
                long lead = sim.resolveUnit(plan.subLead(s));
                long quarry = sim.resolveUnit(plan.subQuarry(s));
                if (lead == 0L || quarry == 0L) continue;
                builder.arrow(sim.world().x(lead), sim.world().y(lead), sim.world().x(quarry), sim.world().y(quarry),
                        "sub-pack of " + plan.subSize(s), ReviewStyle.APPROACH);
            }
        }
        return builder.build();
    }

    @Override
    protected float[] closeView(String loopId) {
        return new float[] {42f, 19f, CLOSE_CELLS};
    }
}
