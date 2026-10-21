package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.Map;

/**
 * The SwarmImpale loops, from the same run that produced their verdicts. The {@code taken} loop
 * also records a close view on the Runners' skeletons: on the line where they strike the squad
 * until one impales a Marine, then on the first carrier as it drags its load to the bore.
 */
public final class SwarmImpaleSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    /** Where the pack meets the squad, between it and the bore. */
    private static final float[] CONTACT = {36f, 16f, 14f};

    public SwarmImpaleSceneSnapshotSuite() {
        super(new SwarmImpaleScene(), 1920, 1080);
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected float[] closeView(String loopId) {
        return loopId.equals("taken") ? CONTACT : null;
    }

    @Override protected float[] closeView(String loopId, BattleSimulation sim) {
        if (!loopId.equals("taken")) return null;
        for (Map.Entry<Long, Long> load : sim.swarmHarvest().loads().entrySet()) {
            long carrier = load.getKey();
            if (sim.resolveUnit(carrier) != 0L) {
                return new float[]{sim.world().x(carrier), sim.world().y(carrier), CONTACT[2]};
            }
        }
        return CONTACT;
    }

    @Override protected int[] stillFrames(String loopId) {
        return loopId.equals("taken-close") ? new int[]{30, 45, 60, 75, 90, 110} : new int[0];
    }
}
