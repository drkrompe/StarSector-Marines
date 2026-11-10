package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.model.Doodad;

/**
 * The lightindustry Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's, its close-up widened to
 * take in the autoplant's assembly line beside the shops.
 */
public final class RaidLightIndustrySiteLookSuite extends RaidSiteLookSuite {

    public RaidLightIndustrySiteLookSuite() {
        super("raid-light-industry-site-look", "lightindustry", EconomicFunction.HEAVY_INDUSTRY);
    }

    /** The machines and the line's craft together, at no less than play zoom. */
    @Override protected float[] closeUp(BattleSimulation sim, float machinesX, float machinesY) {
        float[] base = super.closeUp(sim, machinesX, machinesY);
        float x0 = machinesX, x1 = machinesX, y0 = machinesY, y1 = machinesY;
        boolean line = false;
        for (Doodad d : sim.getDoodads()) {
            if (d.definitionId() == null || !d.definitionId().startsWith("autoplant.")) continue;
            line = true;
            x0 = Math.min(x0, d.cellX);
            y0 = Math.min(y0, d.cellY);
            x1 = Math.max(x1, d.cellX + d.footprintCellsX);
            y1 = Math.max(y1, d.cellY + d.footprintCellsY);
        }
        if (!line) return base;
        float width = Math.max(base[2], Math.max(x1 - x0, (y1 - y0) * 16f / 9f) + 12f);
        return new float[]{(x0 + x1) / 2f, (y0 + y1) / 2f, width};
    }
}
