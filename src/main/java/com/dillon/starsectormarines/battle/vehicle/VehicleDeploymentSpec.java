package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;

import java.util.List;

/**
 * One company vehicle frozen for a battle: the campaign identity a loss writes back to,
 * the callsign, the hull, the engine it runs, the weapon in its gun card, the plating it wears and the kits on its
 * rack. No campaign object crosses into the
 * simulation.
 *
 * <p>{@code campaignId} is null for a debug or mission-supplied vehicle the company does
 * not own; such a vehicle has nothing to write back.
 */
public record VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type, List<VehicleKit> kits,
                                    VehiclePlating plating, VehicleEngine engine, MechWeaponComponent gun) {

    public VehicleDeploymentSpec {
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        kits = kits == null ? List.of() : List.copyOf(kits);
        plating = plating == null ? VehiclePlating.FACTORY : plating;
        engine = engine == null ? VehicleEngine.STOCK : engine;
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

    /** The flux frame the hull is set down with, from its hull and rack; null for a hull that builds no flux. */
    public HullReservoir.Frame flux() {
        return HullReservoir.frame(type, kits);
    }

    /** An unowned vehicle of {@code type}: debug grants and missions without a bay. */
    public static VehicleDeploymentSpec standard(VehicleType type) {
        return new VehicleDeploymentSpec(null, null, type);
    }
}
