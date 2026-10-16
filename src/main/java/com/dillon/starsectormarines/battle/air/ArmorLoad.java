package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;

/**
 * One frozen machine aboard an armor drop: a mech or a vehicle, never both. A bay space
 * holds either ({@code mechs-nouns.md}, gantries), so a carrier unloading a picked call unloads
 * whichever each one is.
 */
public record ArmorLoad(MechDeploymentSpec mech, VehicleDeploymentSpec vehicle) {

    public ArmorLoad {
        if ((mech == null) == (vehicle == null)) {
            throw new IllegalArgumentException("an armor load is exactly one mech or one vehicle");
        }
    }

    public static ArmorLoad of(MechDeploymentSpec mech) {
        return new ArmorLoad(mech, null);
    }

    public static ArmorLoad of(VehicleDeploymentSpec vehicle) {
        return new ArmorLoad(null, vehicle);
    }

    public boolean isVehicle() {
        return vehicle != null;
    }

    /** What this machine asks of its carrier. */
    public LiftClass liftClass() {
        return isVehicle() ? vehicle.type().liftClass() : mech.variant().liftClass();
    }
}
