package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * The hit-effect loops twice over, from one run: through the duel camera, at the distance the owner watches a hull
 * fight from, and as a close-up that follows the struck hull at about three of its lengths across, where a ripple on
 * the shield and a chip off the plate can be read. Both at 1920x1080.
 */
public final class WarshipHitFxSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    /** The close-up's width, in lengths of the hull it follows. */
    private static final float CLOSE_HULLS = 3.2f;

    public WarshipHitFxSceneSnapshotSuite() {
        super(new WarshipHitFxScene(), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() {
        return true;
    }

    @Override protected boolean showUnitShadows() {
        return true;
    }

    @Override protected boolean followWarships() {
        return true;
    }

    @Override protected float closeViewScale() {
        return 1f;
    }

    @Override protected float[] closeView(String loopId) {
        float[] at = WarshipHitFxScene.targetStart();
        return new float[]{at[0], at[1], 90f};
    }

    /** Follows the struck hull: the one defender over the map. */
    @Override protected float[] closeView(String loopId, BattleSimulation sim) {
        for (Warship ship : sim.warships().ships()) {
            if (ship.faction != Faction.DEFENDER) continue;
            float across = Math.max(ship.lengthCells, ship.widthCells) * CLOSE_HULLS;
            return new float[]{ship.centerX(), ship.centerY(), across};
        }
        return closeView(loopId);
    }
}
