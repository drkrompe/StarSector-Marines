package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import java.util.List;

/**
 * The company A Cold Account is balanced against: the chapter's five starter squads and four machines, the
 * recovered Hound beside a Warden, an LRM carrier and a breakthrough tank. The debug briefing lands it and the
 * Corvus evidence musters it, so a played debug start and an evidence run meet the same force.
 */
public final class ColdAccountCompany {
    private ColdAccountCompany() {}

    /** Mechs in the bay beside the recovered Hound. */
    public static final List<MechVariant> MECHS = List.of(MechVariant.WARDEN);
    /** Vehicles in the bay. */
    public static final List<VehicleType> VEHICLES = List.of(VehicleType.LRM_CARRIER, VehicleType.BREAKTHROUGH_TANK);
    /** Every machine the company drops: the Hound, its mechs and its vehicles. */
    public static final int MACHINES = 1 + MECHS.size() + VEHICLES.size();
    /** The bay as the debug briefing names it. */
    static final String BAY = "the Hound (chaingun), a Warden, an LRM carrier and a breakthrough tank";

    /** The bay as a debug drop: the recovered Hound first, then the rest. */
    static DebugMechRoster bay() {
        return DebugMechRoster.recoveredHoundWith(MECHS, VEHICLES);
    }
}
