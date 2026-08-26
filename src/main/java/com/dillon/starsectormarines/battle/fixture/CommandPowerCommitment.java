package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;

import java.util.List;
import java.util.Objects;

/**
 * Immutable launch value for one ordered command-power commitment.
 *
 * <p>Fixed power tuning remains in {@link PowerCatalog}; only Mech Support's
 * campaign-configured deployment payload crosses the fixture boundary.
 */
public record CommandPowerCommitment(
        String id,
        List<MechDeploymentSpec> mechDeployments) {

    public CommandPowerCommitment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Command power id is required");
        }
        mechDeployments = mechDeployments == null
                ? List.of() : List.copyOf(mechDeployments);
    }

    /** Captures the stable identity and any configured power-specific payload. */
    public static CommandPowerCommitment capture(CommandPower power) {
        Objects.requireNonNull(power, "power");
        List<MechDeploymentSpec> deployments = power instanceof MechSupport mechSupport
                ? mechSupport.deployments() : List.of();
        return new CommandPowerCommitment(power.id, deployments);
    }

    /** Rebuilds a fresh runtime power through the production catalog. */
    public CommandPower toPower() {
        return PowerCatalog.restore(id, mechDeployments);
    }
}
