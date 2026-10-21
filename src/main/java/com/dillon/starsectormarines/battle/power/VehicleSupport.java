package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.VehicleSupportPayload;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calls a shootable heavy transport that sets an armoured chassis down on the
 * field for the player to command.
 *
 * <p>The delivery is the ordinary one — the carrier flies in, is exposed on the
 * way, and can be shot down with the vehicle still aboard. What is different is
 * what it leaves behind: not a squad that takes objectives on its own, but a
 * vehicle holding position until it is given somewhere to go.
 *
 * <p>Configured from the mech bay, each charge brings down the next frozen company
 * vehicle in gantry order. Unconfigured (debug, or no bay) it keeps two charges of
 * the payload's fallback hull.
 *
 * <p>Like Mech Support, a committed cradled company boat that lifts the call's
 * vehicle flies it instead of the synthetic Valkyrie.
 *
 * <p>Clearance five, like the mech it shares a carrier with, because an APC's
 * footprint needs somewhere to stand rather than somewhere to stand up.
 */
public final class VehicleSupport extends AirDeliveryPower {

    public static final String ID = "vehicle_support";
    private static final int UNCONFIGURED_CHARGES = 2;

    private final List<VehicleDeploymentSpec> deployments;

    public VehicleSupport() {
        this(List.of(), null, true);
    }

    private VehicleSupport(List<VehicleDeploymentSpec> deployments, FittedBoat companyCarrier,
                           boolean syntheticLift) {
        super(ID, "@battlePowerArmourSupport", 3f,
                deployments.isEmpty() ? UNCONFIGURED_CHARGES : deployments.size(), 0, 2,
                ShuttleType.VALKYRIE, VehicleSupportPayload.INSTANCE, 5,
                companyCarrier, syntheticLift);
        this.deployments = deployments;
    }

    /** Campaign constructor: one charge per frozen company vehicle, in gantry order. */
    public static VehicleSupport configured(List<VehicleDeploymentSpec> deployments) {
        return configured(deployments, null, true);
    }

    /**
     * As above, with a committed cradled company boat that may fly the calls.
     *
     * @param syntheticLift whether a mission, employer or hull also supplies a
     *     heavy carrier for calls the boat cannot lift
     */
    public static VehicleSupport configured(List<VehicleDeploymentSpec> deployments,
                                            FittedBoat companyCarrier, boolean syntheticLift) {
        List<VehicleDeploymentSpec> valid = new ArrayList<>();
        if (deployments != null) {
            for (VehicleDeploymentSpec deployment : deployments) {
                if (deployment != null) valid.add(deployment);
            }
        }
        return new VehicleSupport(Collections.unmodifiableList(valid), companyCarrier,
                syntheticLift);
    }

    @Override
    protected List<LiftClass> nextLoad(CommandPowerService service) {
        int remaining = service != null ? service.getChargesRemaining(ID) : maxCharges - 1;
        VehicleDeploymentSpec next = deploymentForRemainingCharges(remaining);
        return List.of((next != null ? next.type() : VehicleSupportPayload.FALLBACK).liftClass());
    }

    @Override
    protected List<LiftClass> liftDemand() {
        if (deployments.isEmpty()) return List.of(VehicleSupportPayload.FALLBACK.liftClass());
        return deployments.stream().map(deployment -> deployment.type().liftClass()).toList();
    }

    /** Frozen, ordered company vehicles; empty when unconfigured. */
    public List<VehicleDeploymentSpec> deployments() {
        return deployments;
    }

    @Override
    protected void configureMission(ShuttleMission mission, ShuttleType carrier,
                                    CommandPowerService service) {
        int remaining = service != null ? service.getChargesRemaining(ID) : maxCharges - 1;
        mission.vehicleDeployment = deploymentForRemainingCharges(remaining);
    }

    /** The vehicle this activation carries; null when unconfigured. */
    VehicleDeploymentSpec deploymentForRemainingCharges(int remaining) {
        if (deployments.isEmpty()) return null;
        int activation = Math.max(0, maxCharges - Math.max(0, remaining) - 1);
        return deployments.get(Math.min(activation, deployments.size() - 1));
    }
}
