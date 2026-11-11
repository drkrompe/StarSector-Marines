package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.air.PortFreighter;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;

/**
 * The spaceport Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's, its close-up taking in the
 * cargo terminal and the lighter apron beside it, from the bonded sheds to the lighter on its pad.
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
}
