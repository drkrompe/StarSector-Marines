package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Authored inputs to the Ruins Expedition battle ({@code ruins-expedition.md}, The Ruins mission family): Extraction's inputs plus the
 * ruins grade, which sets the delve's depth and the vault's breach time, and the site's occupant, which chooses the
 * defenders ({@code ruins-expedition.md}, Occupants).
 */
public record RuinsBattleFixture(
        long seed,
        List<ShuttleAssignment> manifest,
        boolean enemyHasHeavyArmor,
        OperationTier tier,
        RiskLevel risk,
        TargetProfile targetProfile,
        List<FighterWingCommitment> marineFighterSupport,
        List<FighterWingCommitment> enemyFighterSupport,
        RuinsGrade grade,
        RuinsOccupant occupant,
        List<String> takenFinds) implements BattleFixture {

    public static final String KIND = "RUINS";

    public RuinsBattleFixture {
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
        tier = Objects.requireNonNull(tier, "tier");
        risk = Objects.requireNonNull(risk, "risk");
        targetProfile = Objects.requireNonNull(targetProfile, "targetProfile");
        marineFighterSupport = List.copyOf(Objects.requireNonNull(
                marineFighterSupport, "marineFighterSupport"));
        enemyFighterSupport = List.copyOf(Objects.requireNonNull(
                enemyFighterSupport, "enemyFighterSupport"));
        grade = Objects.requireNonNull(grade, "grade");
        occupant = Objects.requireNonNull(occupant, "occupant");
        takenFinds = takenFinds == null ? List.of() : List.copyOf(new TreeSet<>(takenFinds));
    }

    /** A site with nothing yet brought home. */
    public RuinsBattleFixture(long seed, List<ShuttleAssignment> manifest, boolean enemyHasHeavyArmor,
                              OperationTier tier, RiskLevel risk, TargetProfile targetProfile,
                              List<FighterWingCommitment> marineFighterSupport,
                              List<FighterWingCommitment> enemyFighterSupport, RuinsGrade grade,
                              RuinsOccupant occupant) {
        this(seed, manifest, enemyHasHeavyArmor, tier, risk, targetProfile, marineFighterSupport,
                enemyFighterSupport, grade, occupant, List.of());
    }

    public static RuinsBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport, RuinsGrade grade, RuinsOccupant occupant) {
        return fromFactoryInputs(seed, manifest, enemyHasHeavyArmor, tier, risk, targetProfile,
                marineFighterSupport, enemyFighterSupport, grade, occupant, Set.of());
    }

    /** @param takenFinds the ids of the site's finds already brought home, left out of the battle */
    public static RuinsBattleFixture fromFactoryInputs(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile targetProfile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport, RuinsGrade grade, RuinsOccupant occupant,
            Collection<String> takenFinds) {
        return new RuinsBattleFixture(seed,
                manifest == null ? List.of() : List.copyOf(manifest),
                enemyHasHeavyArmor, tier, risk, targetProfile,
                FighterWingCommitment.captureRoster(marineFighterSupport),
                FighterWingCommitment.captureRoster(enemyFighterSupport),
                grade, occupant, takenFinds == null ? List.of() : List.copyOf(takenFinds));
    }

    @Override public String kind() { return KIND; }

    @Override
    public BattleSimulation build() {
        BattleSimulation sim = BattleSetup.createRuins(seed, manifest, enemyHasHeavyArmor,
                tier, risk, targetProfile,
                FighterWingCommitment.toRoster(marineFighterSupport),
                FighterWingCommitment.toRoster(enemyFighterSupport), grade, occupant);
        // What earlier expeditions brought home stays taken (ruins-expedition.md, Law 6).
        sim.setTakenFinds(Set.copyOf(takenFinds));
        return sim;
    }
}
