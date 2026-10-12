package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.List;
import java.util.Objects;

/** Authored inputs to the production Sabotage scenario factory. */
public record SabotageBattleFixture(
        long seed,
        List<ShuttleAssignment> manifest,
        boolean enemyHasHeavyArmor,
        OperationTier tier,
        RiskLevel risk,
        TargetProfile targetProfile,
        List<FighterWingCommitment> marineFighterSupport,
        List<FighterWingCommitment> enemyFighterSupport,
        PrecinctPlan.Sprawl sprawl,
        boolean defenderReinforcements) implements BattleFixture {

    public static final String KIND = "SABOTAGE";

    public SabotageBattleFixture {
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
        tier = Objects.requireNonNull(tier, "tier");
        risk = Objects.requireNonNull(risk, "risk");
        targetProfile = Objects.requireNonNull(targetProfile, "targetProfile");
        marineFighterSupport = List.copyOf(Objects.requireNonNull(
                marineFighterSupport, "marineFighterSupport"));
        enemyFighterSupport = List.copyOf(Objects.requireNonNull(
                enemyFighterSupport, "enemyFighterSupport"));
    }

    /** Omitted sprawl preserves the legacy settlement recipe. */
    public SabotageBattleFixture(long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, List<FighterWingCommitment> marineFighterSupport,
            List<FighterWingCommitment> enemyFighterSupport) {
        this(seed, manifest, enemyHasHeavyArmor, tier, risk, targetProfile,
                marineFighterSupport, enemyFighterSupport, null, true);
    }

    /** Captures the exact inputs already accepted by {@link BattleSetup}. */
    public static SabotageBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport) {
        return fromFactoryInputs(seed, manifest, enemyHasHeavyArmor, tier, risk,
                targetProfile, marineFighterSupport, enemyFighterSupport, null, true);
    }

    public static SabotageBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport, PrecinctPlan.Sprawl sprawl) {
        return fromFactoryInputs(seed, manifest, enemyHasHeavyArmor, tier, risk,
                targetProfile, marineFighterSupport, enemyFighterSupport, sprawl, true);
    }

    /** A finite garrison is authored independently of terrain or settlement density. */
    public static SabotageBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport, PrecinctPlan.Sprawl sprawl,
            boolean defenderReinforcements) {
        return new SabotageBattleFixture(seed,
                manifest == null ? List.of() : List.copyOf(manifest),
                enemyHasHeavyArmor, tier, risk, targetProfile,
                FighterWingCommitment.captureRoster(marineFighterSupport),
                FighterWingCommitment.captureRoster(enemyFighterSupport), sprawl, defenderReinforcements);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public BattleSimulation build() {
        return BattleSetup.createSabotage(seed, manifest, enemyHasHeavyArmor,
                tier, risk, targetProfile,
                FighterWingCommitment.toRoster(marineFighterSupport),
                FighterWingCommitment.toRoster(enemyFighterSupport), sprawl, defenderReinforcements);
    }
}
