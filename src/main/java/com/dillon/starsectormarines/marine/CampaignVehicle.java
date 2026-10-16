package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.i18n.Strings;

import java.io.Serializable;

/**
 * Persistent identity of one player-owned vehicle standing on a mech-bay gantry.
 *
 * <p>It shares the gantry with mechs and nothing else: no chassis, sockets, heat or
 * weapon components. Its hull, turret and kinematics are its {@link VehicleType}'s.
 */
public final class CampaignVehicle implements Serializable {

    private final String id;
    private String displayName;
    private final VehicleType type;

    public CampaignVehicle(String id, String displayName, VehicleType type) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Vehicle id is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Vehicle name is required");
        }
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        this.id = id;
        this.displayName = displayName;
        this.type = type;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public VehicleType type() { return type; }

    /** Changes only the player-facing callsign; identity and hull stay intact. */
    public boolean rename(String value) {
        String candidate = CampaignMech.normalizeCallSign(value);
        if (candidate == null || candidate.isEmpty()
                || candidate.length() > CampaignMech.CALLSIGN_MAX_LENGTH) return false;
        displayName = candidate;
        return true;
    }

    public MechPattern.WeightClass weightClass() {
        return MechPattern.weightClass(type);
    }

    /** The frozen value Armour Support carries; the campaign object stays in the bay. */
    public VehicleDeploymentSpec freezeForDeployment() {
        return new VehicleDeploymentSpec(id, displayName, type);
    }

    /** Player-facing name of a vehicle hull. */
    public static String typeName(VehicleType type) {
        return type == null ? "" : Strings.get("vehicleTypeName" + type.name());
    }
}
