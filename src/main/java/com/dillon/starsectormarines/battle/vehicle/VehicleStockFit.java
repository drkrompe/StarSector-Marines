package com.dillon.starsectormarines.battle.vehicle;

import java.util.List;

/**
 * A hull as a mission fields it fitted out, rather than as it rolls off the line (mechs-nouns.md, a vehicle is a mech
 * for parts): the plating, engine and kits a company would put on it, frozen into the same
 * {@link VehicleDeploymentSpec} a company vehicle drops as. A tougher or faster version of a hull is a fit, never a
 * hull of its own; only a turret that changes the gun card is a variant ({@link VehicleType}).
 */
public enum VehicleStockFit {
    /**
     * The convoy escort: the APC in heavy plating on a military engine, with a spall liner, so it outruns the column
     * it guards and stands more fire than the troop carrier.
     */
    CONVOY_ESCORT(VehicleType.HEAVY_APC, VehiclePlating.HEAVY, VehicleEngine.MILITARY,
            List.of(VehicleKit.SPALL_LINER));

    public final VehicleType type;
    public final VehiclePlating plating;
    public final VehicleEngine engine;
    public final List<VehicleKit> kits;

    VehicleStockFit(VehicleType type, VehiclePlating plating, VehicleEngine engine, List<VehicleKit> kits) {
        if (!VehicleKit.fits(type, kits)) throw new IllegalArgumentException(name() + "'s kits do not fit its hull");
        this.type = type;
        this.plating = plating;
        this.engine = engine;
        this.kits = List.copyOf(kits);
    }

    /** This fit as an unowned vehicle, with its hull's own gun. */
    public VehicleDeploymentSpec spec() {
        return new VehicleDeploymentSpec(null, null, type, kits, plating, engine);
    }
}
