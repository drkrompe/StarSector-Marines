package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calls a shootable heavy transport that physically unloads a marine mech lance.
 *
 * <p>The synthetic Valkyrie flies the lance where a mission, an employer or a
 * fleet hull supplies the lift. Given a committed cradled company boat that
 * lifts the whole call, the boat flies it instead, one machine per sortie: a
 * cradle carries one machine and nobody else.
 */
public final class MechSupport extends AirDeliveryPower {

    public static final String ID = "mech_support";
    public static final int LANCE_SIZE = 4;

    private final List<MechDeploymentSpec> deployments;

    public MechSupport() {
        this(List.of(MechVariant.BULWARK));
    }

    /** Debug/playtest constructor: one physical support sortie per lance. */
    public MechSupport(List<MechVariant> variants) {
        this(standardDeployments(variants), null, true);
    }

    /** Campaign constructor: preserves installed components across the seam. */
    public static MechSupport configured(List<MechDeploymentSpec> deployments) {
        return new MechSupport(deployments, null, true);
    }

    /**
     * Campaign constructor with a committed company boat that may fly the calls.
     *
     * @param syntheticLift whether a mission, employer, hull or ship also
     *     supplies a heavy carrier for calls the boat cannot lift
     */
    public static MechSupport configured(List<MechDeploymentSpec> deployments,
                                         FittedBoat companyCarrier, boolean syntheticLift) {
        return new MechSupport(deployments, companyCarrier, syntheticLift);
    }

    /** Frozen, ordered mech payload installed by this power. */
    public List<MechDeploymentSpec> deployments() {
        return deployments;
    }

    private MechSupport(List<MechDeploymentSpec> deployments, FittedBoat companyCarrier,
                        boolean syntheticLift) {
        super(ID, displayName(validDeployments(deployments).size()), 4f,
                lanceCount(validDeployments(deployments).size()), 0, 3,
                ShuttleType.VALKYRIE,
                MechSupportPayload.INSTANCE, 5, companyCarrier, syntheticLift);
        List<MechDeploymentSpec> valid = validDeployments(deployments);
        this.deployments = Collections.unmodifiableList(new ArrayList<>(valid));
    }

    @Override
    protected List<LiftClass> nextLoad(CommandPowerService service) {
        return deploymentLanceForRemainingCharges(remainingAfterCommit(service)).stream()
                .map(deployment -> deployment.variant().liftClass()).toList();
    }

    @Override
    protected List<LiftClass> liftDemand() {
        return deployments.stream().map(deployment -> deployment.variant().liftClass()).toList();
    }

    @Override
    protected void configureMission(ShuttleMission mission, Airframe frame,
                                    ShuttleType carrier, CommandPowerService service) {
        List<MechDeploymentSpec> lance =
                deploymentLanceForRemainingCharges(remainingAfterCommit(service));
        configureLance(mission, lance);
        if (frame instanceof FittedBoat) {
            // A cradle carries one machine: the boat flies a trip per mech.
            mission.totalCycles = lance.size();
            mission.marinesRemaining = 1;
        }
    }

    private int remainingAfterCommit(CommandPowerService service) {
        return service != null ? service.getChargesRemaining(ID) : maxCharges - 1;
    }

    /**
     * Loads {@code lance} into a carrier that is already in the air. Shared by the player's
     * power and a commander's call: what is unloaded is one set of mechs either way.
     */
    static void configureLance(ShuttleMission mission, List<MechDeploymentSpec> lance) {
        mission.mechDeployments = lance.toArray(new MechDeploymentSpec[0]);
        mission.mechVariants = lance.stream()
                .map(MechDeploymentSpec::variant).toArray(MechVariant[]::new);
        mission.mechVariant = lance.get(0).variant();
        mission.marinesRemaining = lance.size();
    }

    List<MechVariant> lanceForRemainingCharges(int remaining) {
        return deploymentLanceForRemainingCharges(remaining).stream()
                .map(MechDeploymentSpec::variant).toList();
    }

    List<MechDeploymentSpec> deploymentLanceForRemainingCharges(int remaining) {
        int activation = Math.max(0, maxCharges - Math.max(0, remaining) - 1);
        int from = Math.min(activation * LANCE_SIZE, deployments.size() - 1);
        int to = Math.min(from + LANCE_SIZE, deployments.size());
        return deployments.subList(from, to);
    }

    private static List<MechDeploymentSpec> standardDeployments(List<MechVariant> variants) {
        List<MechDeploymentSpec> deployments = new ArrayList<>();
        if (variants != null) {
            for (MechVariant variant : variants) {
                if (variant != null) deployments.add(MechDeploymentSpec.standard(variant));
            }
        }
        return validDeployments(deployments);
    }

    private static List<MechDeploymentSpec> validDeployments(
            List<MechDeploymentSpec> deployments) {
        if (deployments == null || deployments.isEmpty()) {
            return List.of(MechDeploymentSpec.standard(MechVariant.BULWARK));
        }
        List<MechDeploymentSpec> valid = new ArrayList<>();
        for (MechDeploymentSpec deployment : deployments) {
            if (deployment != null) valid.add(deployment);
        }
        return valid.isEmpty()
                ? List.of(MechDeploymentSpec.standard(MechVariant.BULWARK)) : valid;
    }

    private static String displayName(int mechs) {
        int lances = lanceCount(mechs);
        return mechs > 1 ? "Mech Lance x" + lances : "Mech Support";
    }

    private static int lanceCount(int mechCount) {
        return (int) Math.max(1L,
                ((long) mechCount + LANCE_SIZE - 1L) / LANCE_SIZE);
    }
}
