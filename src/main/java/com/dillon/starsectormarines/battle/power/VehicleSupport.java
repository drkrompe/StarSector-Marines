package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.VehicleSupportPayload;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;

import java.util.Collections;
import java.util.List;

/**
 * Armour Support: sets the company's vehicles down one per order, on the player's ferry, for
 * the player to command ({@link ArmorDropPower}). In the roster only where it is the
 * vehicle-only source; where Mech Support is present that carries the whole bay instead.
 *
 * <p>What it leaves behind is not a squad that takes objectives on its own but a vehicle
 * holding position until it is given somewhere to go. Configured from the bay, its manifest
 * is the frozen company vehicles in gantry order; unconfigured (debug, or no bay) it holds
 * two of the payload's unowned fallback hull.
 */
public final class VehicleSupport extends ArmorDropPower {

    public static final String ID = "vehicle_support";
    private static final int UNCONFIGURED_VEHICLES = 2;

    private final List<VehicleDeploymentSpec> deployments;

    public VehicleSupport() {
        this(List.of(), null, true);
    }

    private VehicleSupport(List<VehicleDeploymentSpec> deployments, FittedBoat companyCarrier,
                           boolean syntheticLift) {
        super(ID, "@battlePowerArmourSupport", 2, List.<MechDeploymentSpec>of(),
                deployments.isEmpty() ? fallback() : deployments, companyCarrier, syntheticLift);
        this.deployments = deployments;
    }

    /** Campaign constructor: the frozen company vehicles, in gantry order. */
    public static VehicleSupport configured(List<VehicleDeploymentSpec> deployments) {
        return configured(deployments, null, true);
    }

    /**
     * As above, with a committed cradled company boat whose lift counts.
     *
     * @param syntheticLift whether a mission, employer or hull also supplies heavy lift
     */
    public static VehicleSupport configured(List<VehicleDeploymentSpec> deployments,
                                            FittedBoat companyCarrier, boolean syntheticLift) {
        return new VehicleSupport(Collections.unmodifiableList(nonNull(deployments)),
                companyCarrier, syntheticLift);
    }

    /** Frozen, ordered company vehicles; empty when unconfigured. */
    public List<VehicleDeploymentSpec> deployments() {
        return deployments;
    }

    private static List<VehicleDeploymentSpec> fallback() {
        return Collections.nCopies(UNCONFIGURED_VEHICLES,
                VehicleDeploymentSpec.standard(VehicleSupportPayload.FALLBACK));
    }
}
