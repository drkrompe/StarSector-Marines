package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import java.util.EnumSet;
import java.util.Set;

/**
 * The spaceport Raid site, framed as {@link RaidSiteLookSuite} frames heavy industry's, its close-up taking in the
 * cargo terminal and the lighter apron beside it, from the bonded sheds to the lighter on its pad.
 */
public final class RaidPortSiteLookSuite extends RaidSiteLookSuite {

    private static final Set<ShuttleType> LIGHTERS = EnumSet.of(ShuttleType.BUFFALO, ShuttleType.MULE,
            ShuttleType.TARSUS);

    public RaidPortSiteLookSuite() {
        super("raid-port-site-look", "spaceport", EconomicFunction.COMMERCE);
    }

    @Override
    protected float[] closeUp(BattleSimulation sim, float machinesX, float machinesY) {
        ParkedAircraft lighter = null;
        for (ParkedAircraft parked : sim.getParkedAircraft()) {
            // The port's lighters are freighters; a town's berths hold its small craft.
            if (!LIGHTERS.contains(parked.type)) continue;
            if (lighter == null || Math.abs(parked.centerX - machinesX) > Math.abs(lighter.centerX - machinesX)) {
                lighter = parked;
            }
        }
        if (lighter == null) return super.closeUp(sim, machinesX, machinesY);
        float cx = (machinesX + lighter.centerX) / 2f, cy = (machinesY + lighter.centerY) / 2f;
        float across = Math.max(Math.abs(lighter.centerX - machinesX), Math.abs(lighter.centerY - machinesY) * 16f / 9f);
        return new float[]{cx, cy, across + 56f};
    }
}
