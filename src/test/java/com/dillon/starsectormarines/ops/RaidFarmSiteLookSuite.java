package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;

/**
 * The farming Raid site ({@code GrainHub}), framed as {@link RaidSiteLookSuite} frames heavy industry's, on a
 * verdant world: the grain hub, its fields along the farm roads, the haulers at its intake. The works shot frames the
 * whole hub, so the process reads from intake to loadout.
 */
public final class RaidFarmSiteLookSuite extends RaidSiteLookSuite {

    /** Cells of hub kept round the silo battery in the works shot: the intake, the gallery and the loadout. */
    private static final float HUB_MARGIN = 38f;

    public RaidFarmSiteLookSuite() {
        super("raid-farm-site-look", "farming", EconomicFunction.AGRICULTURE, SurfacePalette.VERDANT);
    }

    @Override
    protected float[] closeUp(BattleSimulation sim, float machinesX, float machinesY) {
        float x0 = machinesX, y0 = machinesY, x1 = machinesX, y1 = machinesY;
        for (Objective objective : sim.getObjectives()) {
            if (!(objective instanceof RaidObjective raid) || !raid.works()) continue;
            for (RaidObjective.Site site : raid.sites()) {
                x0 = Math.min(x0, site.cellX());
                y0 = Math.min(y0, site.cellY());
                x1 = Math.max(x1, site.cellX());
                y1 = Math.max(y1, site.cellY());
            }
        }
        float across = Math.max(x1 - x0, (y1 - y0) * 16f / 9f) + 2 * HUB_MARGIN;
        return new float[]{(x0 + x1) / 2f + .5f, (y0 + y1) / 2f + .5f, across};
    }
}
