package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;

/**
 * The militarybase Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's, but for its close-up: the
 * base is the motor pool and the garrison's parade square together, so the frame stands between the two.
 */
public final class RaidMilitarySiteLookSuite extends RaidSiteLookSuite {

    public RaidMilitarySiteLookSuite() {
        super("raid-military-site-look", "militarybase", EconomicFunction.MILITARY);
    }

    @Override
    protected float[] closeUp(BattleSimulation sim, float machinesX, float machinesY) {
        float best = Float.MAX_VALUE, px = 0, py = 0;
        for (Doodad d : sim.getDoodads()) {
            if (!"doodad.parade-centre".equals(d.definitionId())) continue;
            float dx = d.cellX + 1 - machinesX, dy = d.cellY + 1 - machinesY;
            if (dx * dx + dy * dy < best) {
                best = dx * dx + dy * dy;
                px = d.cellX + 1;
                py = d.cellY + 1;
            }
        }
        if (best == Float.MAX_VALUE) return super.closeUp(sim, machinesX, machinesY);
        float span = Math.max(Math.abs(px - machinesX), Math.abs(py - machinesY) * 16f / 9f);
        float width = Math.max(BattleReviewFrameRenderer.PLAY_ZOOM_CELLS * 1.6f, span * 1.3f + 24f);
        return new float[]{(px + machinesX) / 2f, (py + machinesY) / 2f, width};
    }
}
