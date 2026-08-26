package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Calls a shootable heavy transport that physically unloads a marine mech lance. */
public final class MechSupport extends AirDeliveryPower {

    public static final String ID = "mech_support";
    public static final int LANCE_SIZE = 4;

    private final List<MechDeploymentSpec> deployments;

    public MechSupport() {
        this(List.of(MechVariant.BULWARK));
    }

    /** Debug/playtest constructor: one physical support sortie per lance. */
    public MechSupport(List<MechVariant> variants) {
        this(standardDeployments(variants), true);
    }

    /** Campaign constructor: preserves installed components across the seam. */
    public static MechSupport configured(List<MechDeploymentSpec> deployments) {
        return new MechSupport(deployments, true);
    }

    /** Frozen, ordered mech payload installed by this power. */
    public List<MechDeploymentSpec> deployments() {
        return deployments;
    }

    private MechSupport(List<MechDeploymentSpec> deployments, boolean frozenValues) {
        super(ID, displayName(validDeployments(deployments).size()), 4f,
                lanceCount(validDeployments(deployments).size()), 0, 3,
                ShuttleType.VALKYRIE,
                MechSupportPayload.INSTANCE, 5);
        List<MechDeploymentSpec> valid = validDeployments(deployments);
        this.deployments = Collections.unmodifiableList(new ArrayList<>(valid));
    }

    @Override
    protected void configureMission(ShuttleMission mission, ShuttleType carrier,
                                    CommandPowerService service) {
        int remaining = service != null ? service.getChargesRemaining(ID) : maxCharges - 1;
        List<MechDeploymentSpec> lance = deploymentLanceForRemainingCharges(remaining);
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
        return Math.max(1, (mechCount + LANCE_SIZE - 1) / LANCE_SIZE);
    }
}
