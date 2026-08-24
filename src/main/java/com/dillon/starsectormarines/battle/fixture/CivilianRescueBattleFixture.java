package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.List;
import java.util.Objects;

/**
 * The authored inputs to the production civilian-rescue factory. Keeping the
 * requested seed (rather than a resolved retry seed) makes map retry behavior
 * replay exactly as it did at launch.
 */
public record CivilianRescueBattleFixture(
        long seed,
        List<ShuttleAssignment> manifest,
        boolean enemyHasHeavyArmor,
        RiskLevel risk,
        int swarmCount,
        TargetProfile targetProfile,
        boolean stressTest) implements BattleFixture {

    public static final String KIND = "CIVILIAN_RESCUE";

    public CivilianRescueBattleFixture {
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
        risk = Objects.requireNonNull(risk, "risk");
        targetProfile = Objects.requireNonNull(targetProfile, "targetProfile");
    }

    /** Captures the exact inputs already accepted by {@link BattleSetup}. */
    public static CivilianRescueBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, RiskLevel risk, int swarmCount,
            TargetProfile targetProfile, boolean stressTest) {
        List<ShuttleAssignment> capturedManifest = manifest == null
                ? List.of() : List.copyOf(manifest);
        return new CivilianRescueBattleFixture(seed, capturedManifest,
                enemyHasHeavyArmor, risk, swarmCount, targetProfile, stressTest);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public BattleSimulation build() {
        return BattleSetup.createCivilianRescue(seed, manifest,
                enemyHasHeavyArmor, risk, swarmCount, targetProfile, stressTest);
    }
}
