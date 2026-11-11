package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.air.PortFreighter;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;

import java.util.List;

/**
 * The spaceport Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's, its close-up taking in the
 * cargo terminal and the lighter apron beside it, from the bonded sheds to the lighter on its pad; and a close-up of
 * the hub's town side ({@code <case>-town-side.png}): the terminal, the truck gate's road and the streets meeting the
 * lines, where the town's roads join the port's.
 */
public final class RaidPortSiteLookSuite extends RaidSiteLookSuite {


    public RaidPortSiteLookSuite() {
        super("raid-port-site-look", "spaceport", EconomicFunction.COMMERCE);
    }

    @Override
    protected float[] closeUp(BattleSimulation sim, float machinesX, float machinesY) {
        float lx = 0, ly = 0;
        int n = 0;
        for (ParkedAircraft parked : sim.getParkedAircraft()) {
            // The port's traffic is bulk freighters; a town's berths hold its small craft.
            if (!(parked.type instanceof PortFreighter)) continue;
            lx += parked.centerX;
            ly += parked.centerY;
            n++;
        }
        if (n == 0) return super.closeUp(sim, machinesX, machinesY);
        lx /= n;
        ly /= n;
        // The pad line leads the frame, centred on its lighters; the terminal stands at the frame's edge.
        float cx = machinesX * .3f + lx * .7f;
        return new float[]{cx, ly, Math.abs(lx - machinesX) * .8f + 64f};
    }

    /** Width of the town side's close-up, cells. */
    static final float TOWN_SIDE_WIDTH = 120f;

    @Override
    protected List<Shot> extraShots(BattleSimulation sim, float machinesX, float machinesY) {
        float[] pads = pads(sim);
        if (pads == null) return List.of();
        // From the terminal a little toward the hub, and from it toward the lines, away from the pads.
        float x = machinesX + Math.signum(pads[0] - machinesX) * TOWN_SIDE_WIDTH * .3f;
        float y = machinesY + (machinesY - pads[1]) * .5f;
        return List.of(new Shot("town-side", x, y, TOWN_SIDE_WIDTH));
    }

    /** The port's ships' mean cell, or null on a map with none. */
    private static float[] pads(BattleSimulation sim) {
        float lx = 0, ly = 0;
        int n = 0;
        for (ParkedAircraft parked : sim.getParkedAircraft()) {
            if (!(parked.type instanceof PortFreighter)) continue;
            lx += parked.centerX;
            ly += parked.centerY;
            n++;
        }
        return n == 0 ? null : new float[]{lx / n, ly / n};
    }
}
