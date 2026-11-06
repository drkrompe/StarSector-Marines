package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;

import java.util.Locale;

/**
 * Which weapon colour a campaign faction fits its gun tanks' omni cards with (hegemony-armor-line.md): the Diktat and
 * the Lion's Guard rebuild the old drawings around energy weapons, the Luddic factions around missile racks, and the
 * Hegemony keeps the ballistic guns it drew them for. Everyone else, the independents and the pirates among them,
 * fields the yellow cannons that are most of what is for sale or to steal. A
 * hardware doctrine like {@link com.dillon.starsectormarines.battle.mech.FactionMechLoadouts}, separate from paint.
 */
public final class FactionVehiclePoints {

    private FactionVehiclePoints() {
    }

    /** The colour {@code factionId}'s stock gun tanks fit; ballistic for an unknown or absent faction. */
    public static HardpointType of(String factionId) {
        if (factionId == null) return HardpointType.BALLISTIC;
        return switch (factionId.trim().toLowerCase(Locale.ROOT)) {
            case "sindrian_diktat", "lions_guard" -> HardpointType.ENERGY;
            case "luddic_church", "knights_of_ludd", "luddic_path" -> HardpointType.MISSILE;
            default -> HardpointType.BALLISTIC;
        };
    }
}
