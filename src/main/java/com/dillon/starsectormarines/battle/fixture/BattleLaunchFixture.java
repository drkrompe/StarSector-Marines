package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.Objects;

/** A versioned scenario construction fixture plus the frozen production launch overlay. */
public record BattleLaunchFixture(
        BattleFixture construction,
        BattleLaunchOverlay launch) implements BattleFixture {

    public BattleLaunchFixture {
        construction = Objects.requireNonNull(construction, "construction");
        launch = Objects.requireNonNull(launch, "launch");
        if (construction instanceof BattleLaunchFixture) {
            throw new IllegalArgumentException("Launch fixtures may not be nested");
        }
    }

    @Override
    public String kind() {
        return construction.kind();
    }

    @Override
    public BattleSimulation build() {
        return build(new FiniteCommandPowerResources(launch.startingSupplies()));
    }

    /** Production variant whose power costs continue to debit live campaign cargo. */
    public BattleSimulation build(CommandPowerResources resources) {
        BattleSimulation simulation = construction.build();
        try {
            applyTo(simulation, resources);
            return simulation;
        } catch (RuntimeException | Error failure) {
            simulation.close();
            throw failure;
        }
    }

    /** Applies the frozen overlay to an already-built production simulation. */
    public void applyTo(BattleSimulation simulation, CommandPowerResources resources) {
        launch.applyTo(simulation, resources);
    }
}
