package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;

import java.util.List;

/**
 * One company vehicle frozen for a battle: the campaign identity a loss writes back to,
 * the callsign, the hull, the engine it runs, the weapon in its gun card, the plating it wears, the kits on its
 * rack and the flux frame its rack's capacitor bank and vent arrays make ({@code flux}; null for a hull with no gun).
 * No campaign object crosses into the
 * simulation.
 *
 * <p>{@code campaignId} is null for a debug or mission-supplied vehicle the company does
 * not own; such a vehicle has nothing to write back.
 */
public record VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type, List<VehicleKit> kits,
                                    VehiclePlating plating, VehicleEngine engine, MechWeaponComponent gun,
                                    HullReservoir.Frame flux) {

    public VehicleDeploymentSpec {
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        kits = kits == null ? List.of() : List.copyOf(kits);
        plating = plating == null ? VehiclePlating.FACTORY : plating;
        engine = engine == null ? VehicleEngine.STOCK : engine;
    }

    /** A vehicle in its hull's stock flux frame ({@link HullReservoir#stockFrame}). */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type, List<VehicleKit> kits,
                                 VehiclePlating plating, VehicleEngine engine, MechWeaponComponent gun) {
        this(campaignId, callsign, type, kits, plating, engine, gun, HullReservoir.stockFrame(type));
    }

    /** A vehicle with its hull's own gun; {@code gun} is otherwise null. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type, List<VehicleKit> kits,
                                 VehiclePlating plating, VehicleEngine engine) {
        this(campaignId, callsign, type, kits, plating, engine, null);
    }

    /** A vehicle with its hull's own engine. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type, List<VehicleKit> kits,
                                 VehiclePlating plating) {
        this(campaignId, callsign, type, kits, plating, VehicleEngine.STOCK);
    }

    /** A vehicle in its factory plating. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type, List<VehicleKit> kits) {
        this(campaignId, callsign, type, kits, VehiclePlating.FACTORY);
    }

    /** A vehicle with an empty kit rack. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type) {
        this(campaignId, callsign, type, List.of());
    }


    /**
     * An unowned vehicle of {@code type}: debug grants and missions without a bay, with the part a spawn that names none
     * fits ({@link VehicleType#standardPart}), so a modular hull is not set down as a bare ring.
     */
    public static VehicleDeploymentSpec standard(VehicleType type) {
        return new VehicleDeploymentSpec(null, null, type, List.of(), VehiclePlating.FACTORY, VehicleEngine.STOCK,
                type.standardPart());
    }
}
