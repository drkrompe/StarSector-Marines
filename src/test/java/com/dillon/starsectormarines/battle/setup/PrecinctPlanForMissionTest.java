package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which battles are made of places, and how hard the place they are about is.
 *
 * <p>Two laws. A precinct plan reaches Assault and Raid and nothing else, and
 * only when a real market stands behind the battle — every other mission and
 * every marketless one takes the map it already had. And where a plan is
 * derived, the garrison's fortification is the world's rating nudged by the
 * risk and then capped by the tier, in that order, so risk never lifts a place
 * past what its operation was raised for.
 */
class PrecinctPlanForMissionTest {

    /** A defended industrial world: a stronghold's rating, a town's size. */
    private static final TargetProfile MARKET = new TargetProfile(
            6, 5, 5, 2, "independent",
            EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.HEAVY_INDUSTRY),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    private static final long SEED = 607_898L;

    @Test
    void aBattleWithNoMarketBehindItTakesTheMapItAlreadyHad() {
        assertNull(BattleSetup.precinctPlanFor(MissionType.ASSAULT,
                        OperationTier.ESTABLISHED, RiskLevel.HIGH,
                        TargetProfile.NEUTRAL, MapScale.MEDIUM, SEED),
                "a neutral profile has no size, no rating and no economy to derive places from");
        assertNull(BattleSetup.precinctPlanFor(MissionType.ASSAULT,
                        OperationTier.ESTABLISHED, RiskLevel.HIGH,
                        null, MapScale.MEDIUM, SEED),
                "no profile at all is even less to derive from than a neutral one");
    }

    @Test
    void onlyAssaultAndRaidAreMadeOfPlaces() {
        for (MissionType type : MissionType.values()) {
            PrecinctPlan plan = BattleSetup.precinctPlanFor(type,
                    OperationTier.ESTABLISHED, RiskLevel.HIGH,
                    MARKET, MapScale.MEDIUM, SEED);
            if (type == MissionType.ASSAULT || type == MissionType.RAID) {
                assertNotNull(plan, type + " is one of the two missions wired to the precinct recipe");
            } else {
                assertNull(plan, type + " keeps the recipe it has; only Assault and Raid are wired");
            }
        }
    }

    @Test
    void theTierCapsWhatTheRiskAskedFor() {
        Precinct objective = objectiveOf(OperationTier.ESTABLISHED, RiskLevel.HIGH);
        assertEquals(Fortification.GARRISON, objective.fortification(),
                "rating 5 is a stronghold, high risk nudges it to citadel, "
                        + "and an Established operation caps it back to garrison");
    }

    @Test
    void aFullStrengthOperationIsNotCappedBelowTheWorldsOwnAnswer() {
        Precinct objective = objectiveOf(OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM);
        assertEquals(Fortification.STRONGHOLD, objective.fortification(),
                "rating 5 is a stronghold, medium risk moves nothing, "
                        + "and a full-strength operation's ceiling is the citadel above it");
    }

    @Test
    void theSameBattleLaysOutTheSameMapTwice() {
        PrecinctPlan first = planFor(OperationTier.ESTABLISHED, RiskLevel.HIGH, SEED);
        PrecinctPlan repeat = planFor(OperationTier.ESTABLISHED, RiskLevel.HIGH, SEED);
        assertEquals(seedsOf(first), seedsOf(repeat),
                "one seed derives one plan, or the same battle is a different map on replay");

        PrecinctPlan other = planFor(OperationTier.ESTABLISHED, RiskLevel.HIGH, SEED + 1);
        assertNotEquals(seedsOf(first), seedsOf(other),
                "two seeds that lay out the same places are a plan that does not vary");
    }

    @Test
    void anAssaultAgainstARealMarketStandsUpAtEveryTierItIsOfferedAt() {
        for (OperationTier tier : List.of(OperationTier.ESTABLISHED, OperationTier.FIRST_CONTRACT)) {
            try (BattleSimulation sim = BattleSetup.createPlaceholder(
                    SEED, manifest(), false, tier, RiskLevel.MEDIUM,
                    MissionType.ASSAULT, MARKET, FlybyRoster.EMPTY, FlybyRoster.EMPTY)) {
                assertTrue(sim.getGrid().getWidth() > 0,
                        "an assault at " + tier + " built no map at all");
            }
        }
    }

    @Test
    void aRaidAgainstARealMarketStillFindsSomethingToStrike() {
        for (OperationTier tier : List.of(OperationTier.ESTABLISHED, OperationTier.FIRST_CONTRACT)) {
            try (BattleSimulation sim = BattleSetup.createPlaceholder(
                    SEED, manifest(), false, tier, RiskLevel.MEDIUM,
                    MissionType.RAID, MARKET, FlybyRoster.EMPTY, FlybyRoster.EMPTY)) {
                assertTrue(sim.getObjectives().stream().anyMatch(RaidObjective.class::isInstance),
                        "a raid at " + tier + " on a precinct map found no target to strike");
            }
        }
    }

    private static Precinct objectiveOf(OperationTier tier, RiskLevel risk) {
        Precinct objective = planFor(tier, risk, SEED).objective();
        assertNotNull(objective, "a defended world always has the place the mission is about");
        return objective;
    }

    private static PrecinctPlan planFor(OperationTier tier, RiskLevel risk, long seed) {
        return BattleSetup.precinctPlanFor(MissionType.ASSAULT, tier, risk,
                MARKET, MapScale.MEDIUM, seed);
    }

    /** Where every place starts, which is the part a seed decides. */
    private static List<String> seedsOf(PrecinctPlan plan) {
        return plan.precincts().stream()
                .map(precinct -> precinct.name() + "@" + precinct.seedX() + "," + precinct.seedY())
                .toList();
    }

    private static List<ShuttleAssignment> manifest() {
        return List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1));
    }
}
