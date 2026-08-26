package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.List;
import java.util.Objects;

/** Authored inputs to the production Conquest scenario factory. */
public record ConquestBattleFixture(
        long seed,
        List<ShuttleAssignment> manifest,
        boolean enemyHasHeavyArmor,
        OperationTier tier,
        RiskLevel risk,
        TargetProfile targetProfile,
        List<FighterWingCommitment> marineFighterSupport,
        List<FighterWingCommitment> enemyFighterSupport) implements BattleFixture {

    public static final String KIND = "CONQUEST";

    public ConquestBattleFixture {
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
        tier = Objects.requireNonNull(tier, "tier");
        risk = Objects.requireNonNull(risk, "risk");
        targetProfile = Objects.requireNonNull(targetProfile, "targetProfile");
        marineFighterSupport = List.copyOf(Objects.requireNonNull(
                marineFighterSupport, "marineFighterSupport"));
        enemyFighterSupport = List.copyOf(Objects.requireNonNull(
                enemyFighterSupport, "enemyFighterSupport"));
    }

    public static ConquestBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport) {
        return new ConquestBattleFixture(seed,
                manifest == null ? List.of() : List.copyOf(manifest),
                enemyHasHeavyArmor, tier, risk, targetProfile,
                FighterWingCommitment.captureRoster(marineFighterSupport),
                FighterWingCommitment.captureRoster(enemyFighterSupport));
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public BattleSimulation build() {
        return BattleSetup.createConquest(seed, manifest, enemyHasHeavyArmor,
                tier, risk, targetProfile,
                FighterWingCommitment.toRoster(marineFighterSupport),
                FighterWingCommitment.toRoster(enemyFighterSupport));
    }
}
