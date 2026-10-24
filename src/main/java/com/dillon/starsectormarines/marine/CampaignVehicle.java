package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.i18n.Strings;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent identity of one player-owned vehicle standing on a mech-bay gantry.
 *
 * <p>It shares the gantry with mechs and nothing else: no chassis, sockets, flux or
 * weapon components. Its hull, turret and kinematics are its {@link VehicleType}'s and are
 * sealed; the one thing fitted to it is the {@link VehicleKit}s on its rack.
 */
public final class CampaignVehicle implements Serializable {

    private final String id;
    private String displayName;
    private final VehicleType type;
    /** Kit ids on the rack, in fitted order; null in a save from before kits (an empty rack). */
    private List<String> kitIds = new ArrayList<>();

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

    /** The kits on the rack, in fitted order. */
    public List<VehicleKit> kits() {
        List<VehicleKit> kits = new ArrayList<>();
        if (kitIds != null) {
            for (String id : kitIds) {
                VehicleKit kit = VehicleKit.findById(id);
                if (kit != null) kits.add(kit);
            }
        }
        return List.copyOf(kits);
    }

    /** Whether {@code kit} would go on the rack: a free slot, not already fitted, and suiting the hull. */
    public boolean canFit(VehicleKit kit) {
        if (kit == null) return false;
        List<VehicleKit> proposed = new ArrayList<>(kits());
        proposed.add(kit);
        return VehicleKit.fits(type, proposed);
    }

    /** Puts {@code kit} on the rack; false when it would not go. */
    public boolean fit(VehicleKit kit) {
        if (!canFit(kit)) return false;
        if (kitIds == null) kitIds = new ArrayList<>();
        kitIds.add(kit.id);
        return true;
    }

    /** Takes {@code kit} off the rack; false when it was not there. */
    public boolean remove(VehicleKit kit) {
        return kit != null && kitIds != null && kitIds.remove(kit.id);
    }

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
        return new VehicleDeploymentSpec(id, displayName, type, kits());
    }

    /** Player-facing name of a vehicle hull. */
    public static String typeName(VehicleType type) {
        return type == null ? "" : Strings.get("vehicleTypeName" + type.name());
    }
}
