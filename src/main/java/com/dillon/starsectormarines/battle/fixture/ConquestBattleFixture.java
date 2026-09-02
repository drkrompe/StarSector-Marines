package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.ShuttleArrivalPlan;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.LanePath;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Authored inputs to the production Conquest scenario factory.
 *
 * <p>{@code sprawl} is the battle's own statement of how settled its map is,
 * or {@code null} to derive it from the target market. Absent from a fixture
 * document means null, so a fixture written before the field existed still
 * derives its own answer rather than being pinned to one.
 *
 * <p>{@code standoff} is the same shape for how far from the objective the
 * force lands, or {@code null} for Conquest's own default. It is on this
 * fixture and not the others because Conquest is the only mission that consults
 * one — everything else takes the map-edge beachhead it was measured on.
 *
 * <p>{@code lanes} is the third of them: how many lanes of resistance lie
 * between the beachhead and the objective, or {@code null} for one per command
 * track. What stands on each lane steps down from the objective's own
 * fortification.
 *
 * <p>{@code lanePaths} is where those lanes go — one route per lane in lane
 * order, or {@code null} for lanes that derive their own. A fixture pins a
 * zig-zag here when the evidence it exists for is about a bend; ordinary
 * fixtures state none and take the derived meander.
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
        PrecinctPlan.Sprawl sprawl,
        Standoff standoff,
        Integer lanes,
        List<LanePath> lanePaths) implements BattleFixture {

    public static final String KIND = "CONQUEST";

    /** A fixture stating a lane count but leaving every route to derive. */
    public ConquestBattleFixture(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile,
            List<FighterWingCommitment> marineFighterSupport,
            List<FighterWingCommitment> enemyFighterSupport,
            ShuttleArrivalPlan arrivalPlan, PrecinctPlan.Sprawl sprawl,
            Standoff standoff, Integer lanes) {
        this(seed, manifest, enemyHasHeavyArmor, tier, risk, targetProfile,
                marineFighterSupport, enemyFighterSupport, arrivalPlan, sprawl,
                standoff, lanes, null);
    }

    public ConquestBattleFixture {
        // Not List.copyOf: a null entry is a lane that derives its own route.
        lanePaths = lanePaths == null ? null
                : Collections.unmodifiableList(new ArrayList<>(lanePaths));
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

    /** A fixture that states a sprawl but nothing about its approach. */
    public ConquestBattleFixture(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile,
            List<FighterWingCommitment> marineFighterSupport,
            List<FighterWingCommitment> enemyFighterSupport,
            ShuttleArrivalPlan arrivalPlan, PrecinctPlan.Sprawl sprawl) {
        this(seed, manifest, enemyHasHeavyArmor, tier, risk, targetProfile,
                marineFighterSupport, enemyFighterSupport, arrivalPlan, sprawl,
                null, null);
    }

    /** A fixture that states a sprawl and an approach but nothing about lanes. */
    public ConquestBattleFixture(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile,
            List<FighterWingCommitment> marineFighterSupport,
            List<FighterWingCommitment> enemyFighterSupport,
            ShuttleArrivalPlan arrivalPlan, PrecinctPlan.Sprawl sprawl,
            Standoff standoff) {
        this(seed, manifest, enemyHasHeavyArmor, tier, risk, targetProfile,
                marineFighterSupport, enemyFighterSupport, arrivalPlan, sprawl,
                standoff, null);
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
                marineFighterSupport, enemyFighterSupport, arrivalPlan, null,
                null, null);
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
        return fromFactoryInputs(seed, manifest, enemyHasHeavyArmor, tier, risk,
                targetProfile, marineFighterSupport, enemyFighterSupport,
                arrivalPlan, sprawl, null);
    }

    public static ConquestBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport,
            ShuttleArrivalPlan arrivalPlan, PrecinctPlan.Sprawl sprawl,
            Standoff standoff) {
        return fromFactoryInputs(seed, manifest, enemyHasHeavyArmor, tier, risk,
                targetProfile, marineFighterSupport, enemyFighterSupport,
                arrivalPlan, sprawl, standoff, null);
    }

    public static ConquestBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport,
            ShuttleArrivalPlan arrivalPlan, PrecinctPlan.Sprawl sprawl,
            Standoff standoff, Integer lanes) {
        return fromFactoryInputs(seed, manifest, enemyHasHeavyArmor, tier, risk,
                targetProfile, marineFighterSupport, enemyFighterSupport,
                arrivalPlan, sprawl, standoff, lanes, null);
    }

    public static ConquestBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport,
            ShuttleArrivalPlan arrivalPlan, PrecinctPlan.Sprawl sprawl,
            Standoff standoff, Integer lanes, List<LanePath> lanePaths) {
        return new ConquestBattleFixture(seed,
                manifest == null ? List.of() : List.copyOf(manifest),
                enemyHasHeavyArmor, tier, risk, targetProfile,
                FighterWingCommitment.captureRoster(marineFighterSupport),
                FighterWingCommitment.captureRoster(enemyFighterSupport),
                arrivalPlan, sprawl, standoff, lanes, lanePaths);
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
                sprawl, standoff, lanes, lanePaths);
    }
}
