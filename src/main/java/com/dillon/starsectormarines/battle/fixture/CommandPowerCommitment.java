package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;

import java.util.List;
import java.util.Objects;

/**
 * Immutable launch value for one ordered command-power commitment.
 *
 * <p>Fixed power tuning remains in {@link PowerCatalog}; only Mech Support's
 * campaign-configured deployment payload, Ship on Station's frozen ships and
 * the frozen company vehicles of Mech Support's bay manifest cross the fixture
 * boundary, with the company boat a drop power flies with.
 *
 * @param companyCarrier the committed company boat a drop power flies with, or
 *     {@code null} for the synthetic carrier alone
 * @param companyLiftOnly whether the drop has no synthetic carrier, behind that boat or at
 *     all; false (a frozen commitment from before it was kept) restores the synthetic carrier
 */
public record CommandPowerCommitment(
        String id,
        List<MechDeploymentSpec> mechDeployments,
        List<ShipSupportSource> ships,
        List<VehicleDeploymentSpec> vehicleDeployments,
        FittedBoat companyCarrier,
        boolean companyLiftOnly) {

    public CommandPowerCommitment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Command power id is required");
        }
        mechDeployments = mechDeployments == null
                ? List.of() : List.copyOf(mechDeployments);
        ships = ships == null ? List.of() : List.copyOf(ships);
        vehicleDeployments = vehicleDeployments == null
                ? List.of() : List.copyOf(vehicleDeployments);
    }

    public CommandPowerCommitment(String id, List<MechDeploymentSpec> mechDeployments,
                                  List<ShipSupportSource> ships,
                                  List<VehicleDeploymentSpec> vehicleDeployments,
                                  FittedBoat companyCarrier) {
        this(id, mechDeployments, ships, vehicleDeployments, companyCarrier, false);
    }

    public CommandPowerCommitment(String id, List<MechDeploymentSpec> mechDeployments,
                                  List<ShipSupportSource> ships,
                                  List<VehicleDeploymentSpec> vehicleDeployments) {
        this(id, mechDeployments, ships, vehicleDeployments, null);
    }

    public CommandPowerCommitment(String id, List<MechDeploymentSpec> mechDeployments,
                                  List<ShipSupportSource> ships) {
        this(id, mechDeployments, ships, List.of());
    }

    public CommandPowerCommitment(String id, List<MechDeploymentSpec> mechDeployments) {
        this(id, mechDeployments, List.of());
    }

    /** Captures the stable identity and any configured power-specific payload. */
    public static CommandPowerCommitment capture(CommandPower power) {
        Objects.requireNonNull(power, "power");
        List<MechDeploymentSpec> deployments = power instanceof MechSupport mechSupport
                ? mechSupport.deployments() : List.of();
        List<ShipSupportSource> ships = power instanceof ShipOnStation shipPower
                ? shipPower.sources() : List.of();
        List<VehicleDeploymentSpec> vehicles = power instanceof MechSupport mechSupport
                ? mechSupport.vehicles() : List.of();
        FittedBoat carrier = power instanceof MechSupport mechSupport
                ? mechSupport.companyCarrier() : null;
        boolean companyOnly = power instanceof MechSupport mechSupport && !mechSupport.syntheticLift();
        return new CommandPowerCommitment(power.id, deployments, ships, vehicles, carrier, companyOnly);
    }

    /**
     * Rebuilds a fresh runtime power through the production catalog; {@code null}
     * for a power since removed from the game.
     */
    public CommandPower toPower() {
        return PowerCatalog.restore(id, mechDeployments, ships, vehicleDeployments,
                companyCarrier, !companyLiftOnly);
    }
}
