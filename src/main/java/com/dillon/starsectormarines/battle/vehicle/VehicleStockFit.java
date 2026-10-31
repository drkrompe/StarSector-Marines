package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.VehicleRack;

import java.util.ArrayList;
import java.util.List;

/**
 * A named instance of a hull ({@code convoy-nouns.md}, a vehicle instance is a hull and a loadout): a hull as a mission
 * fields it fitted out, rather than as it rolls off the line (mechs-nouns.md, a vehicle is a mech
 * for parts): the plating, engine, kits and compartment internals a company would put on it, frozen into the same
 * {@link VehicleDeploymentSpec} a company vehicle drops as. A tougher or faster version of a hull is a fit, never a
 * hull of its own; only a turret that changes the gun card is a variant ({@link VehicleType}).
 */
public enum VehicleStockFit {
    /**
     * The convoy escort: the APC in heavy plating on a military engine, with a spall liner, so it outruns the column
     * it guards and stands more fire than the troop carrier.
     */
    CONVOY_ESCORT(VehicleType.HEAVY_APC, VehiclePlating.HEAVY, VehicleEngine.MILITARY,
            List.of(), List.of(MechInternal.SPALL_LINER)),
    /**
     * The Parapet: the Onager with active protection on its rack, the tank line's answer to a missile mech. It was a
     * hull of its own until the system became a kit; missions and evidence that field it field this fit.
     */
    PARAPET(VehicleType.BREAKTHROUGH_TANK, VehiclePlating.FACTORY, VehicleEngine.STOCK,
            List.of(), List.of(MechInternal.ACTIVE_PROTECTION));

    public final VehicleType type;
    public final VehiclePlating plating;
    public final VehicleEngine engine;
    public final List<VehicleKit> kits;
    /** The hull's auxiliary compartment: its stock bank, vent arrays and loader, then the fit's own internals. */
    public final List<MechInternal> compartment;

    VehicleStockFit(VehicleType type, VehiclePlating plating, VehicleEngine engine, List<VehicleKit> kits,
                    List<MechInternal> internals) {
        List<MechInternal> compartment = new ArrayList<>(VehicleRack.stockFit(type));
        compartment.addAll(internals);
        if (!VehicleRack.fits(type, kits, compartment)) {
            throw new IllegalArgumentException(name() + "'s parts do not fit its hull");
        }
        this.type = type;
        this.plating = plating;
        this.engine = engine;
        this.kits = List.copyOf(kits);
        this.compartment = List.copyOf(compartment);
    }

    /**
     * This fit as an unowned vehicle: its loadout, with the part a spawn that names none fits in the gun card
     * ({@link VehicleType#standardPart}), and the hull's stock flux frame.
     */
    public VehicleDeploymentSpec spec() {
        return new VehicleDeploymentSpec(null, null, type, kits, plating, engine, type.standardPart(), compartment);
    }
}
