package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
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
        List<WingCommitment> marineFighterSupport,
        List<WingCommitment> enemyFighterSupport) implements BattleFixture {

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
                captureWings(marineFighterSupport),
                captureWings(enemyFighterSupport));
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public BattleSimulation build() {
        return BattleSetup.createConquest(seed, manifest, enemyHasHeavyArmor,
                tier, risk, targetProfile, toRoster(marineFighterSupport),
                toRoster(enemyFighterSupport));
    }

    private static List<WingCommitment> captureWings(FlybyRoster roster) {
        if (roster == null || roster.isEmpty()) return List.of();
        List<WingCommitment> captured = new ArrayList<>(roster.wings.size());
        for (FighterWing wing : roster.wings) {
            captured.add(WingCommitment.from(wing));
        }
        return List.copyOf(captured);
    }

    private static FlybyRoster toRoster(List<WingCommitment> commitments) {
        if (commitments.isEmpty()) return FlybyRoster.EMPTY;
        List<FighterWing> wings = new ArrayList<>(commitments.size());
        for (WingCommitment commitment : commitments) {
            wings.add(commitment.toWing());
        }
        return new FlybyRoster(wings);
    }

    /** Immutable, equality-safe representation of one fighter factory input. */
    public record WingCommitment(
            FighterProfile profile,
            Faction side,
            int sortieCount,
            float firstArrivalSec,
            float spawnIntervalSec) {

        public WingCommitment {
            profile = Objects.requireNonNull(profile, "profile");
            side = Objects.requireNonNull(side, "side");
            if (sortieCount < 1) {
                throw new IllegalArgumentException("sortieCount must be positive");
            }
            if (firstArrivalSec < 0f || !Float.isFinite(firstArrivalSec)) {
                throw new IllegalArgumentException(
                        "firstArrivalSec must be finite and non-negative");
            }
            if (spawnIntervalSec < 0.1f || !Float.isFinite(spawnIntervalSec)) {
                throw new IllegalArgumentException(
                        "spawnIntervalSec must be finite and at least 0.1");
            }
        }

        public static WingCommitment from(FighterWing wing) {
            return new WingCommitment(wing.profile, wing.side,
                    wing.sortieCount, wing.firstArrivalSec,
                    wing.spawnIntervalSec);
        }

        FighterWing toWing() {
            return new FighterWing(profile, side, sortieCount,
                    firstArrivalSec, spawnIntervalSec);
        }
    }
}
