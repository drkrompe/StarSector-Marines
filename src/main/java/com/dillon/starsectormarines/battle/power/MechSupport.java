package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calls the company's armor down from its ship's bay, one machine per order, on the
 * player's ferry ({@link ArmorDropPower}). The manifest holds the bay's mechs and then its
 * vehicles.
 *
 * <p>The AI commander's drop ({@link MechDropService}) is not this power: it flies whole
 * lances in its own Valkyrie, is paid from its own allowance, and shares only
 * {@link #configureLance}.
 */
public final class MechSupport extends ArmorDropPower {

    public static final String ID = "mech_support";
    /** Machines one commander-called lance carries ({@code MechDropAllowance.MAX_LANCE_SIZE}). */
    public static final int LANCE_SIZE = 4;

    private final List<MechDeploymentSpec> deployments;
    private final List<VehicleDeploymentSpec> vehicles;

    public MechSupport() {
        this(List.of(MechVariant.BULWARK));
    }

    /** Debug/playtest constructor: the given chassis, with heavy lift. */
    public MechSupport(List<MechVariant> variants) {
        this(resolvedMechs(standardDeployments(variants), List.of()), List.of(), null, true);
    }

    /** Campaign constructor: preserves installed components across the seam. */
    public static MechSupport configured(List<MechDeploymentSpec> deployments) {
        return configured(deployments, List.of(), null, true);
    }

    /**
     * Campaign constructor with a committed cradled company boat whose lift counts.
     *
     * @param syntheticLift whether a mission, employer, hull or ship also supplies heavy lift
     */
    public static MechSupport configured(List<MechDeploymentSpec> deployments,
                                         FittedBoat companyCarrier, boolean syntheticLift) {
        return configured(deployments, List.of(), companyCarrier, syntheticLift);
    }

    /** As above, with the bay's vehicles in the manifest beside its mechs. */
    public static MechSupport configured(List<MechDeploymentSpec> deployments,
                                         List<VehicleDeploymentSpec> vehicles,
                                         FittedBoat companyCarrier, boolean syntheticLift) {
        return new MechSupport(resolvedMechs(deployments, vehicles), nonNull(vehicles),
                companyCarrier, syntheticLift);
    }

    private MechSupport(List<MechDeploymentSpec> mechs, List<VehicleDeploymentSpec> vehicles,
                        FittedBoat companyCarrier, boolean syntheticLift) {
        super(ID, "@battlePowerMechSupport", 3, mechs, vehicles, companyCarrier, syntheticLift);
        this.deployments = Collections.unmodifiableList(mechs);
        this.vehicles = Collections.unmodifiableList(vehicles);
    }

    /** Frozen, ordered mech payload in the manifest. */
    public List<MechDeploymentSpec> deployments() {
        return deployments;
    }

    /** Frozen bay vehicles in the manifest, after the mechs. */
    public List<VehicleDeploymentSpec> vehicles() {
        return vehicles;
    }

    /**
     * Loads {@code lance} into a carrier that is already in the air: what a commander's call
     * unloads, one squad of mechs.
     */
    static void configureLance(ShuttleMission mission, List<MechDeploymentSpec> lance) {
        mission.mechDeployments = lance.toArray(new MechDeploymentSpec[0]);
        mission.mechVariants = lance.stream()
                .map(MechDeploymentSpec::variant).toArray(MechVariant[]::new);
        mission.mechVariant = lance.get(0).variant();
        mission.marinesRemaining = lance.size();
    }

    /** The bay's mechs; a bay with neither mechs nor vehicles falls back to the debug Bulwark. */
    private static List<MechDeploymentSpec> resolvedMechs(List<MechDeploymentSpec> deployments,
                                                          List<VehicleDeploymentSpec> vehicles) {
        List<MechDeploymentSpec> mechs = nonNull(deployments);
        if (mechs.isEmpty() && nonNull(vehicles).isEmpty()) {
            mechs.add(MechDeploymentSpec.standard(MechVariant.BULWARK));
        }
        return mechs;
    }

    private static List<MechDeploymentSpec> standardDeployments(List<MechVariant> variants) {
        List<MechDeploymentSpec> deployments = new ArrayList<>();
        for (MechVariant variant : nonNull(variants)) {
            deployments.add(MechDeploymentSpec.standard(variant));
        }
        return deployments;
    }
}
