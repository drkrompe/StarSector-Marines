package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechRefit;
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
 *
 * <p>{@code noOwnGun}: a null {@code gun} is an empty gun card, the hull's turret bare, rather than the hull's own gun.
 *
 * <p>{@code refit} is what the company's Mech Lab upgrades were worth at launch ({@link MechRefit}): aim, cooling,
 * plate and turret traverse, the same bonuses its mechs carry. Neutral for a vehicle the company does not own.
 */
public record VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type,
                                    VehiclePlating plating, VehicleEngine engine, MechWeaponComponent gun,
                                    List<MechInternal> compartment, boolean noOwnGun, MechRefit refit) {

    public VehicleDeploymentSpec {
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
        refit = refit == null ? MechRefit.NEUTRAL : refit;
        plating = plating == null ? VehiclePlating.FACTORY : plating;
        engine = engine == null ? VehicleEngine.STOCK : engine;
        compartment = compartment == null ? VehicleRack.stockFit(type) : List.copyOf(compartment);
    }

    /** A vehicle off the Mech Lab's upgrades: neutral. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type,
                                 VehiclePlating plating, VehicleEngine engine, MechWeaponComponent gun,
                                 List<MechInternal> compartment, boolean noOwnGun) {
        this(campaignId, callsign, type, plating, engine, gun, compartment, noOwnGun, MechRefit.NEUTRAL);
    }

    /** This deployment with the Mech Lab's upgrades frozen over it. */
    public VehicleDeploymentSpec withRefit(MechRefit refit) {
        return new VehicleDeploymentSpec(campaignId, callsign, type, plating, engine, gun, compartment, noOwnGun, refit);
    }

    /** A vehicle whose empty gun card, if it has one, is the hull's own gun. */
    public VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type,
                                 VehiclePlating plating, VehicleEngine engine, MechWeaponComponent gun,
                                 List<MechInternal> compartment) {
        this(campaignId, callsign, type, plating, engine, gun, compartment, false);
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

    /** The flux frame the compartment's bank and vent arrays make with the hull's plant; null without a bank. */
    public HullReservoir.Frame flux() {
        return VehicleRack.frame(type, compartment);
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
