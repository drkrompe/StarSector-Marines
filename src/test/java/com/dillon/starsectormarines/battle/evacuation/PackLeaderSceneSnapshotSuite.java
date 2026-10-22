package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * The pack-leader loops, from the same run that produced their verdicts, with a close view that follows the
 * swarm, so the whelps, adults and elders of a pack can be told apart by size and colour.
 */
public final class PackLeaderSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final float CLOSE_CELLS = 14f;

    public PackLeaderSceneSnapshotSuite() {
        super(new PackLeaderScene(), 1920, 1080);
    }

    @Override
    protected boolean showUnitShadows() {
        return true;
    }

    @Override
    protected float[] closeView(String loopId) {
        return new float[]{20f, 15f, CLOSE_CELLS};
    }

    /** Centred on the Matriarch or the elder leading the pack while they live, then on the swarm's bodies. */
    @Override
    protected float[] closeView(String loopId, BattleSimulation sim) {
        float sx = 0f, sy = 0f;
        int n = 0;
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long unit = sim.liveUnitAt(i);
            UnitType type = sim.identity().type(unit);
            if (type == UnitType.SWARM_MATRIARCH) {
                return new float[]{sim.world().x(unit), sim.world().y(unit), CLOSE_CELLS};
            }
            if (!type.isSwarm()) continue;
            if (sim.identity().swarmStage(unit) == SwarmStage.ELDER) {
                return new float[]{sim.world().x(unit), sim.world().y(unit), CLOSE_CELLS};
            }
            sx += sim.world().x(unit);
            sy += sim.world().y(unit);
            n++;
        }
        return n == 0 ? closeView(loopId) : new float[]{sx / n, sy / n, CLOSE_CELLS};
    }

    @Override
    protected float closeViewScale() { return 1f; }

    @Override
    protected int[] stillFrames(String loopId) {
        if (loopId.equals("leader-killed-close")) return new int[]{20, 60, 80, 100, 130};
        if (loopId.equals("matriarch-leads-close")) return new int[]{10, 50, 80};
        return new int[0];
    }
}
