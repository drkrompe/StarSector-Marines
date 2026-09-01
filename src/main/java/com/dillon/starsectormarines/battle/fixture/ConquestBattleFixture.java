package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.ShuttleArrivalPlan;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.List;
import java.util.Objects;

/**
 * Authored inputs to the production Conquest scenario factory.
 *
 * <p>{@code sprawl} is the battle's own statement of how settled its map is,
 * or {@code null} to derive it from the target market. Absent from a fixture
 * document means null, so a fixture written before the field existed still
 * derives its own answer rather than being pinned to one.
 */
public record ConquestBattleFixture(
        long seed,
        List<ShuttleAssignment> manifest,
        boolean enemyHasHeavyArmor,
        OperationTier tier,
        RiskLevel risk,
        TargetProfile targetProfile,
        List<FighterWingCommitment> marineFighterSupport,
        List<FighterWingCommitment> enemyFighterSupport,
        ShuttleArrivalPlan arrivalPlan,
        PrecinctPlan.Sprawl sprawl) implements BattleFixture {

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
        arrivalPlan = Objects.requireNonNull(arrivalPlan, "arrivalPlan");
    }

    /** A fixture that says nothing about sprawl; the market derives it. */
    public ConquestBattleFixture(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile,
            List<FighterWingCommitment> marineFighterSupport,
            List<FighterWingCommitment> enemyFighterSupport,
            ShuttleArrivalPlan arrivalPlan) {
        this(seed, manifest, enemyHasHeavyArmor, tier, risk, targetProfile,
                marineFighterSupport, enemyFighterSupport, arrivalPlan, null);
    }

    /** V1 fixture compatibility: historical Conquest arrivals were independent. */
    public ConquestBattleFixture(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile,
            List<FighterWingCommitment> marineFighterSupport,
            List<FighterWingCommitment> enemyFighterSupport) {
        this(seed, manifest, enemyHasHeavyArmor, tier, risk, targetProfile,
                marineFighterSupport, enemyFighterSupport,
                ShuttleArrivalPlan.legacy());
    }

    public static ConquestBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport,
            ShuttleArrivalPlan arrivalPlan) {
        return fromFactoryInputs(seed, manifest, enemyHasHeavyArmor, tier, risk,
                targetProfile, marineFighterSupport, enemyFighterSupport,
                arrivalPlan, null);
    }

    public static ConquestBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport,
            ShuttleArrivalPlan arrivalPlan, PrecinctPlan.Sprawl sprawl) {
        return new ConquestBattleFixture(seed,
                manifest == null ? List.of() : List.copyOf(manifest),
                enemyHasHeavyArmor, tier, risk, targetProfile,
                FighterWingCommitment.captureRoster(marineFighterSupport),
                FighterWingCommitment.captureRoster(enemyFighterSupport),
                arrivalPlan, sprawl);
    }

    public static ConquestBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport) {
        return fromFactoryInputs(seed, manifest, enemyHasHeavyArmor, tier, risk,
                targetProfile, marineFighterSupport, enemyFighterSupport,
                ShuttleArrivalPlan.legacy());
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
                FighterWingCommitment.toRoster(enemyFighterSupport), arrivalPlan,
                sprawl);
    }
}
