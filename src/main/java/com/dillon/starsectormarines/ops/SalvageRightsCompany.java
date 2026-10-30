package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.MarineRoster;
import java.util.List;

/**
 * The company Salvage Rights is balanced against: the one that comes to Asharu having won A Cold Account, five squads
 * and four machines with its first heavies in the bay (the Kit Ladder sells the Bulwark by Blind Watch and the
 * breakthrough tank by Thunder Run, {@code kit-ladder.json}). The recovered Hound leads a Bulwark, a breakthrough tank
 * and an LRM carrier. Its squads carry Cold Account's kit: the ladder sells no new weapon card between the two, and
 * Fire-Support comes only with this chapter's win. The debug briefing lands it and the probe builds it, so a played
 * debug start and a probe meet the same force.
 */
public final class SalvageRightsCompany {
    private SalvageRightsCompany() {}

    /** Line squads. */
    public static final int SQUADS = 5;
    /** Mechs in the bay beside the recovered Hound. */
    public static final List<MechVariant> MECHS = List.of(MechVariant.BULWARK);
    /** Vehicles in the bay. */
    public static final List<VehicleType> VEHICLES = List.of(VehicleType.BREAKTHROUGH_TANK, VehicleType.LRM_CARRIER);
    /** Every machine the company drops: the Hound, its mechs and its vehicles. */
    public static final int MACHINES = 1 + MECHS.size() + VEHICLES.size();
    /** The bay as the debug briefing names it. */
    static final String BAY = "the Hound (chaingun), a Bulwark, a breakthrough tank and an LRM carrier";

    /** Teaches {@code roster} the company's cards and issues them to its line squads: A Cold Account's kit. */
    public static void issue(MarineRoster roster) {
        ColdAccountCompany.issue(roster);
    }

    /** The bay as a debug drop: the recovered Hound first, then the rest. */
    static DebugMechRoster bay() {
        return DebugMechRoster.recoveredHoundWith(MECHS, VEHICLES);
    }
}
