package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.FieldPresencePolicy;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Frozen post-factory inputs applied at the campaign-to-battle launch seam. */
public record BattleLaunchOverlay(
        int playerShuttleMissionsToSkip,
        List<MarineSeatCommitment> marineSeats,
        List<FighterWingCommitment> marineFighterSupport,
        List<FighterWingCommitment> debugFighterSupport,
        List<CommandPowerCommitment> commandPowers,
        int startingSupplies,
        FieldPresencePolicy fieldPresencePolicy) {

    public BattleLaunchOverlay {
        if (playerShuttleMissionsToSkip < 0) {
            throw new IllegalArgumentException(
                    "playerShuttleMissionsToSkip must be non-negative");
        }
        if (startingSupplies < 0) {
            throw new IllegalArgumentException("startingSupplies must be non-negative");
        }
        marineSeats = List.copyOf(Objects.requireNonNull(marineSeats, "marineSeats"));
        marineFighterSupport = List.copyOf(Objects.requireNonNull(
                marineFighterSupport, "marineFighterSupport"));
        debugFighterSupport = List.copyOf(Objects.requireNonNull(
                debugFighterSupport, "debugFighterSupport"));
        commandPowers = List.copyOf(Objects.requireNonNull(commandPowers, "commandPowers"));
        fieldPresencePolicy = fieldPresencePolicy != null
                ? fieldPresencePolicy : FieldPresencePolicy.UNRESTRICTED;
        Set<String> powerIds = new HashSet<>();
        for (CommandPowerCommitment power : commandPowers) {
            if (!powerIds.add(power.id())) {
                throw new IllegalArgumentException("Duplicate command power id: " + power.id());
            }
        }
    }

    /** Compatibility constructor for fixtures authored before field presence. */
    public BattleLaunchOverlay(
            int playerShuttleMissionsToSkip,
            List<MarineSeatCommitment> marineSeats,
            List<FighterWingCommitment> marineFighterSupport,
            List<FighterWingCommitment> debugFighterSupport,
            List<CommandPowerCommitment> commandPowers,
            int startingSupplies) {
        this(playerShuttleMissionsToSkip, marineSeats, marineFighterSupport,
                debugFighterSupport, commandPowers, startingSupplies,
                FieldPresencePolicy.UNRESTRICTED);
    }

    public static BattleLaunchOverlay capture(
            int playerShuttleMissionsToSkip,
            CampaignMarineDeployment deployment,
            FlybyRoster marineFighterSupport,
            FlybyRoster debugFighterSupport,
            List<CommandPower> commandPowers,
            int startingSupplies) {
        return capture(playerShuttleMissionsToSkip, deployment,
                marineFighterSupport, debugFighterSupport, commandPowers,
                startingSupplies, FieldPresencePolicy.UNRESTRICTED);
    }

    public static BattleLaunchOverlay capture(
            int playerShuttleMissionsToSkip,
            CampaignMarineDeployment deployment,
            FlybyRoster marineFighterSupport,
            FlybyRoster debugFighterSupport,
            List<CommandPower> commandPowers,
            int startingSupplies,
            FieldPresencePolicy fieldPresencePolicy) {
        return new BattleLaunchOverlay(playerShuttleMissionsToSkip,
                deployment != null ? deployment.commitments() : List.of(),
                FighterWingCommitment.captureRoster(marineFighterSupport),
                FighterWingCommitment.captureRoster(debugFighterSupport),
                commandPowers == null ? List.of() : commandPowers.stream()
                        .map(CommandPowerCommitment::capture).toList(),
                startingSupplies, fieldPresencePolicy);
    }

    /** Applies this snapshot through the same setters used by a live launch. */
    public void applyTo(BattleSimulation simulation, CommandPowerResources resources) {
        Objects.requireNonNull(simulation, "simulation");
        CampaignMarineDeployment.fromCommitments(marineSeats)
                .applyTo(simulation, playerShuttleMissionsToSkip,
                        fieldPresencePolicy);
        simulation.setFieldPresencePolicy(fieldPresencePolicy);
        simulation.setFlybyRoster(FlybyRoster.combine(
                FlybyRoster.combine(
                        FighterWingCommitment.toRoster(marineFighterSupport),
                        simulation.getFlybyRoster()),
                FighterWingCommitment.toRoster(debugFighterSupport)));
        simulation.setCommandPowers(commandPowers.stream()
                .map(CommandPowerCommitment::toPower).toList());
        simulation.setCommandPowerResources(resources);
    }
}
