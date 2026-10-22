package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * The Matriarch loops, from the same run that produced their verdicts, with a close view that follows the
 * Matriarch, so its scythes and its crown can be read.
 */
public final class MatriarchSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final float CLOSE_CELLS = 16f;

    public MatriarchSceneSnapshotSuite() {
        super(new MatriarchScene(), 1920, 1080);
    }

    @Override
    protected float[] closeView(String loopId) {
        if (loopId.equals("corpses")) return new float[]{17f, 12f, 30f};
        return new float[]{12f, 12f, CLOSE_CELLS};
    }

    @Override
    protected float[] closeView(String loopId, BattleSimulation sim) {
        if (loopId.equals("corpses")) return closeView(loopId);
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) == UnitType.SWARM_MATRIARCH) {
                return new float[]{sim.world().x(unit) + 3f, sim.world().y(unit), CLOSE_CELLS};
            }
        }
        return closeView(loopId);
    }

    @Override
    protected float closeViewScale() { return 1f; }
}
