package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.allied.AlliedDepot;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;

/**
 * The Broad Front's logistics base sending armour up (`heart-allied-forces.md`): the lanes' armour waves set out from
 * the laid vehicle park on the supply road rather than appearing at each lane's foot, and none sets out while the
 * swarm is on the park.
 */
public final class HeartVehiclePark implements AlliedDepot {

    /** Cells round the park the swarm must be clear of for it to send. */
    static final int OVERRUN_MARGIN = 3;

    private final int[] bounds;

    private HeartVehiclePark(int[] bounds) {
        this.bounds = bounds;
    }

    /** The park the layout laid, or {@code null} when it laid none. */
    public static HeartVehiclePark of(LaidLayout laid) {
        int[] b = HeartObjectives.bounds(laid, HeartLayout.VEHICLE_PARK, 0);
        return b == null ? null : new HeartVehiclePark(b);
    }

    @Override public int[] dispatchCell(BattleSimulation sim) {
        int[] around = {bounds[0] - OVERRUN_MARGIN, bounds[1] - OVERRUN_MARGIN,
                bounds[2] + OVERRUN_MARGIN, bounds[3] + OVERRUN_MARGIN};
        if (HeartObjectives.present(sim, Faction.DEFENDER, around)) return null;
        return new int[]{(bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2};
    }
}
