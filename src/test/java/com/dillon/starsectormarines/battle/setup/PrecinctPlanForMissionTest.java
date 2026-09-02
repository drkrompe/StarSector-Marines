package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SettlementZoning;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

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

    /** The raid evidence fixture's world: a spaceport and heavy industry on rock. */
    private static final TargetProfile RAID_MARKET = new TargetProfile(
            6, 5, 4, 2, "independent",
            EnumSet.of(EconomicFunction.SPACEPORT, EconomicFunction.HEAVY_INDUSTRY),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    private static final long RAID_SEED = 141_418L;

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

    /**
     * The dial the mission turns reaches the plan.
     *
     * <p>A remote assault is the installation and the country around it: the
     * settlement is dropped entirely, so the only place left is the one the
     * mission is about.
     */
    @Test
    void anAssaultMayAskForAnInstallationInOpenCountry() {
        PrecinctPlan remote = BattleSetup.precinctPlanFor(MissionType.ASSAULT,
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MARKET,
                MapScale.MEDIUM, SEED, PrecinctPlan.Sprawl.REMOTE);
        assertNotNull(remote, "a remote assault still has a map made of places");
        assertNotNull(remote.objective(),
                "a remote map is the installation, so it must still carry one");
        assertEquals(0, remote.precincts().stream()
                        .filter(precinct -> !precinct.isProgrammed()).count(),
                "a remote map came out with a town on it, which is the one thing "
                        + "that stops it being what it is called");
    }

    /**
     * A raid refuses the remote map even when the battle asks for one: its
     * target is a point of interest, and only settlement fills emit those.
     */
    @Test
    void aRaidClampsARemoteRequestBackToATown() {
        PrecinctPlan raid = BattleSetup.precinctPlanFor(MissionType.RAID,
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MARKET,
                MapScale.MEDIUM, SEED, PrecinctPlan.Sprawl.REMOTE);
        PrecinctPlan balanced = BattleSetup.precinctPlanFor(MissionType.RAID,
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MARKET,
                MapScale.MEDIUM, SEED, PrecinctPlan.Sprawl.BALANCED);
        assertNotNull(raid, "a raid always has a map made of places");
        assertEquals(seedsOf(balanced), seedsOf(raid),
                "a raid asked for a remote map and got something other than the "
                        + "balanced one the clamp promises");
    }

    /**
     * A conurbation is more places than a town, on the same world and seed.
     *
     * <p>Asked at the largest scale a mission is offered at, because a place
     * needs room to be a separate place: on a 144x80 map the seed separation
     * seats three and both presets saturate at it, which says something about
     * the map size rather than about the dial.
     */
    @Test
    void aDenseMapIsMorePlacesThanABalancedOne() {
        PrecinctPlan balanced = BattleSetup.precinctPlanFor(MissionType.ASSAULT,
                OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM, MARKET,
                MapScale.LARGE, SEED, PrecinctPlan.Sprawl.BALANCED);
        PrecinctPlan dense = BattleSetup.precinctPlanFor(MissionType.ASSAULT,
                OperationTier.FULL_STRENGTH, RiskLevel.MEDIUM, MARKET,
                MapScale.LARGE, SEED, PrecinctPlan.Sprawl.DENSE);
        assertTrue(dense.precincts().size() > balanced.precincts().size(),
                "dense has " + dense.precincts().size() + " places against balanced's "
                        + balanced.precincts().size());
    }

    /** A mission that states nothing takes the answer its market's size gives. */
    @Test
    void aBattleThatStatesNothingTakesTheMarketsOwnAnswer() {
        assertEquals(PrecinctPlan.Sprawl.BALANCED,
                SettlementZoning.sprawlFor(MARKET.marketSize()),
                "the fixture world is a town, so the derived default is the town map");
        PrecinctPlan derived = BattleSetup.precinctPlanFor(MissionType.ASSAULT,
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MARKET,
                MapScale.MEDIUM, SEED);
        PrecinctPlan stated = BattleSetup.precinctPlanFor(MissionType.ASSAULT,
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MARKET,
                MapScale.MEDIUM, SEED, PrecinctPlan.Sprawl.BALANCED);
        assertEquals(seedsOf(stated), seedsOf(derived),
                "a battle that said nothing about sprawl did not take the derived answer");
    }

    /**
     * A Conquest that says nothing lays the default ladder; one that says zero
     * lays none. Both readings cannot live on the same null, and for a while
     * they did: the plan replaced a deliberate {@code null} with the default,
     * which made the stated control unreachable and turned a control run into a
     * re-run of the thing it was controlling for.
     */
    @Test
    void aConquestThatStatesNoLanesGetsNone() {
        PrecinctPlan unstated = BattleSetup.conquestPlanFor(
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MARKET,
                PrecinctPlan.Sprawl.BALANCED, null,
                TraversalAxis.SOUTH_TO_NORTH, SEED);
        assertTrue(lanePlaces(unstated) > 0,
                "a Conquest that says nothing about lanes lays the default ladder");

        PrecinctPlan none = BattleSetup.conquestPlanFor(
                OperationTier.ESTABLISHED, RiskLevel.MEDIUM, MARKET,
                PrecinctPlan.Sprawl.BALANCED, null, null,
                TraversalAxis.SOUTH_TO_NORTH, SEED);
        assertEquals(0, lanePlaces(none),
                "a Conquest that states no lanes must lay none - it is the control "
                        + "the lane balance evidence is read against");
        assertNotNull(none.objective(),
                "laying no lanes is not laying no objective");
    }

    private static long lanePlaces(PrecinctPlan plan) {
        return plan.precincts().stream()
                .filter(precinct -> precinct.name().startsWith("lane-"))
                .count();
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

    /**
     * A raid needs a prize on the defender's side, and a precinct map is mostly
     * garrison — so the garrison's own stores and seat of command have to be on
     * the map as points of interest, or the only prizes are the settlement's and
     * the settlement is where the marines came from.
     *
     * <p>The objective does not carry the point-of-interest kind it was cut
     * from; its name is that kind spelled out, which is what the assertion below
     * reads.
     */
    @Test
    void aRaidAgainstARealMarketStillFindsSomethingToStrike() {
        for (OperationTier tier : List.of(OperationTier.ESTABLISHED, OperationTier.FIRST_CONTRACT)) {
            assertRaidFindsAPrize(tier, MARKET, SEED);
            assertRaidFindsAPrize(tier, RAID_MARKET, RAID_SEED);
        }
    }

    private static void assertRaidFindsAPrize(OperationTier tier, TargetProfile profile,
                                              long seed) {
        try (BattleSimulation sim = BattleSetup.createPlaceholder(
                seed, manifest(), false, tier, RiskLevel.MEDIUM,
                MissionType.RAID, profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY)) {
            RaidObjective raid = sim.getObjectives().stream()
                    .filter(RaidObjective.class::isInstance)
                    .map(RaidObjective.class::cast)
                    .findFirst()
                    .orElse(null);
            assertNotNull(raid, "a raid at " + tier + " on seed " + seed
                    + " found no target to strike");
            assertNotEquals(PointOfInterest.Kind.RESIDENTIAL.name()
                            .toLowerCase(Locale.ROOT), raid.targetName(),
                    "a raid at " + tier + " on seed " + seed
                            + " was sent to strike somebody's housing");
        }
    }

    /**
     * Nothing outside a precinct map is ever cut off from what is inside it.
     *
     * <p>A walled precinct whose boundary has no opening is a whole side of the
     * battle nobody can reach, and it fails late and obscurely: raid
     * construction throws for want of a reachable prize, and an assault merely
     * runs for as long as its marines keep asking the pathfinder for a route
     * that does not exist. Measured on the raid evidence fixture before the
     * gate rule was widened: all 23 points of interest on the defender's side
     * had route length 0 from the landing pad.
     *
     * <p>Asked of the map layer rather than of a built sim, because that is
     * where the defect lives and because a {@code BattleSimulation} does not
     * expose the spawns this reads.
     */
    @Test
    void aPrecinctMapIsNeverSealed() {
        for (MissionType type : List.of(MissionType.ASSAULT, MissionType.RAID)) {
            for (OperationTier tier : List.of(OperationTier.ESTABLISHED,
                    OperationTier.FIRST_CONTRACT)) {
                assertSidesCanReachEachOther(type, tier, MARKET, SEED);
                assertSidesCanReachEachOther(type, tier, RAID_MARKET, RAID_SEED);
            }
        }
    }

    private static void assertSidesCanReachEachOther(MissionType type, OperationTier tier,
                                                     TargetProfile profile, long seed) {
        MapScale scale = MapScale.forTier(tier);
        PrecinctPlan plan = BattleSetup.precinctPlanFor(
                type, tier, RiskLevel.MEDIUM, profile, scale, seed);
        assertNotNull(plan, type + " at " + tier + " built no plan to test");
        MapResult map = new BspCityGenerator()
                .generate(scale.width, scale.height, seed, null, profile, plan);
        int[] path = GridPathfinder.findPath(map.grid,
                map.marineSpawnX, map.marineSpawnY, map.defenderSpawnX, map.defenderSpawnY);
        assertTrue(path.length > 0, type + " at " + tier + " on seed " + seed
                + ": no route from the marine spawn at " + map.marineSpawnX + ","
                + map.marineSpawnY + " to the defender spawn at " + map.defenderSpawnX
                + "," + map.defenderSpawnY + ", so a walled precinct came out sealed");
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
