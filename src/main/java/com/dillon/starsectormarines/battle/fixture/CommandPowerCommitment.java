package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.power.VehicleSupport;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;

import java.util.List;
import java.util.Objects;

/**
 * Immutable launch value for one ordered command-power commitment.
 *
 * <p>Fixed power tuning remains in {@link PowerCatalog}; only Mech Support's
 * campaign-configured deployment payload, Ship on Station's frozen ships and a
 * drop power's company carrier cross the fixture boundary.
 *
 * @param companyCarrier the committed company boat a drop power flies with, or
 *     {@code null} for the synthetic carrier alone
 */
public record CommandPowerCommitment(
        String id,
        List<MechDeploymentSpec> mechDeployments,
        List<ShipSupportSource> ships,
        FittedBoat companyCarrier) {

    public CommandPowerCommitment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Command power id is required");
        }
        mechDeployments = mechDeployments == null
                ? List.of() : List.copyOf(mechDeployments);
        ships = ships == null ? List.of() : List.copyOf(ships);
    }

    public CommandPowerCommitment(String id, List<MechDeploymentSpec> mechDeployments) {
        this(id, mechDeployments, List.of());
    }

    public CommandPowerCommitment(String id, List<MechDeploymentSpec> mechDeployments,
                                  List<ShipSupportSource> ships) {
        this(id, mechDeployments, ships, null);
    }

    /** Captures the stable identity and any configured power-specific payload. */
    public static CommandPowerCommitment capture(CommandPower power) {
        Objects.requireNonNull(power, "power");
        List<MechDeploymentSpec> deployments = power instanceof MechSupport mechSupport
                ? mechSupport.deployments() : List.of();
        List<ShipSupportSource> ships = power instanceof ShipOnStation shipPower
                ? shipPower.sources() : List.of();
        FittedBoat carrier = power instanceof MechSupport mechSupport
                ? mechSupport.companyCarrier()
                : power instanceof VehicleSupport vehicleSupport
                ? vehicleSupport.companyCarrier() : null;
        return new CommandPowerCommitment(power.id, deployments, ships, carrier);
    }

    /** Rebuilds a fresh runtime power through the production catalog. */
    public CommandPower toPower() {
        return PowerCatalog.restore(id, mechDeployments, ships, companyCarrier);
    }
}
