package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.VehicleRack;

import java.util.List;

/**
 * One company vehicle frozen for a battle: the campaign identity a loss writes back to,
 * the callsign, the hull, the engine it runs, the weapon in its gun card, the plating it wears and the internals in
 * its auxiliary compartment ({@code compartment}: its capacitor bank and vent arrays, which are
 * its flux frame, and its loader). No campaign object crosses into the simulation.
 *
 * <p>{@code campaignId} is null for a debug or mission-supplied vehicle the company does
 * not own; such a vehicle has nothing to write back.
 */
public record VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type,
                                    VehiclePlating plating, VehicleEngine engine, MechWeaponComponent gun,
                                    List<MechInternal> compartment) {

    public VehicleDeploymentSpec {
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        plating = plating == null ? VehiclePlating.FACTORY : plating;
        engine = engine == null ? VehicleEngine.STOCK : engine;
        compartment = compartment == null ? VehicleRack.stockFit(type) : List.copyOf(compartment);
    }

    /** A vehicle with its hull's stock compartment ({@link VehicleRack#stockFit}): its bank, vent arrays and loader. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type,
                                 VehiclePlating plating, VehicleEngine engine, MechWeaponComponent gun) {
        this(campaignId, callsign, type, plating, engine, gun, null);
    }

    /** A vehicle with its hull's own gun; {@code gun} is otherwise null. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type,
                                 VehiclePlating plating, VehicleEngine engine) {
        this(campaignId, callsign, type, plating, engine, null);
    }

    /** A vehicle with its hull's own engine. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type,
                                 VehiclePlating plating) {
        this(campaignId, callsign, type, plating, VehicleEngine.STOCK);
    }

    /** A vehicle in its factory plating. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type) {
        this(campaignId, callsign, type, VehiclePlating.FACTORY);
    }

    /** The flux frame the compartment's bank and vent arrays make; null without a bank (an unarmed hull). */
    public HullReservoir.Frame flux() {
        return VehicleRack.frame(compartment);
    }

    /** The loader the compartment carries; {@link MissileReplenisherComponent#NONE} without one. */
    public MissileReplenisherComponent loader() {
        return VehicleRack.loader(compartment);
    }

    /**
     * An unowned vehicle of {@code type}: debug grants and missions without a bay, with the part a spawn that names none
     * fits ({@link VehicleType#standardPart}), so a modular hull is not set down as a bare ring.
     */
    public static VehicleDeploymentSpec standard(VehicleType type) {
        return new VehicleDeploymentSpec(null, null, type, VehiclePlating.FACTORY, VehicleEngine.STOCK, type.standardPart());
    }
}
