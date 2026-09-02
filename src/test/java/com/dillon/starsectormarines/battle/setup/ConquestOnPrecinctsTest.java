package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.fixture.FighterWingCommitment;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.precinct.ApproachRegion;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conquest generates as places, at the size the model was measured at.
 *
 * <p>Four things the mission is entitled to, checked on the two fixtures the
 * commander evidence actually replays. The map is {@link MapScale#CONQUEST};
 * it publishes a front to reinforce along, and the reinforcement layer is
 * therefore installed rather than falling back to the compound-only trigger;
 * it carries the one keep, the airfield and the beachheads
 * {@link MissionMapRequirements} promises; and the attacker lands on the side
 * of the map the rolled axis says it does, which is the whole of what the axis
 * now buys.
 */
class ConquestOnPrecinctsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "conquest-reinforced-south-v3",
            "conquest-full-strength-west-v3"})
    void aConquestFixtureBuildsAsPlacesAtTheConquestSize(String fixtureName)
            throws Exception {
        ConquestBattleFixture fixture = loadConquest(fixtureName);
        BattleSetup.MapBuild build = BattleSetup.createConquestBuild(
                fixture.seed(), fixture.manifest(), fixture.enemyHasHeavyArmor(),
                fixture.tier(), fixture.risk(), fixture.targetProfile(),
                roster(fixture.marineFighterSupport()),
                roster(fixture.enemyFighterSupport()),
                fixture.arrivalPlan(), fixture.sprawl());
        MapResult map = build.map();
        try (BattleSimulation sim = build.sim()) {
            assertEquals(MapScale.CONQUEST.width, sim.getGrid().getWidth());
            assertEquals(MapScale.CONQUEST.height, sim.getGrid().getHeight());

            // A front to reinforce along, with something actually standing in
            // the band the battle is about: an empty band 0 is a front the
            // recapture walk starts past.
            FrontDepth depth = map.frontDepth;
            assertNotNull(depth, "a Conquest map states where its front is");
            assertTrue(bandCells(depth, 0) > 0, "band 0 is the objective own ground");

            // And the layer that reads it. Installed, rather than falling
            // through to the compound-only trigger, is the whole point of the
            // front being a depth rather than a biome.
            assertNotNull(sim.getCounterattackSystem(),
                    "the front-line reinforcement layer installs on a precinct Conquest map");

            assertEquals(1, map.tacticalMap.all().stream()
                    .filter(node -> node.kind == TacticalNode.Kind.COMMAND_POST)
                    .count(), "one keep, and a settlement base is not a second one");
            assertTrue(map.landingPads.stream().anyMatch(
                            pad -> pad.purpose == LandingPad.Purpose.GARRISON_AIRFIELD),
                    "a garrison air arm has somewhere to fly from");
            assertTrue(map.landingAreas.size()
                            >= ConquestArrivalConfig.DEFAULT.dropZoneCount(),
                    "authored " + map.landingAreas.size() + " arrival areas for "
                            + ConquestArrivalConfig.DEFAULT.dropZoneCount()
                            + " drop zones");

            assertTheForceLandsAtItsStandoff(fixture.seed(), map);
        }
    }

    /**
     * The marines land on the side the axis says, at the distance the mission
     * states.
     *
     * <p>Two facts in one assertion, because they are one arrival. The side is
     * the substitution the precinct story made: the axis used to paint a beach
     * and now places a precinct, and both commanders keep reading it either way.
     * The distance is what a stated {@link Standoff} buys — the map stays
     * 560x336 and the beachhead moves in until its objective-facing side is
     * {@code STANDARD} cells short of the claim.
     *
     * <p>The claim is read back off {@link MapResult#frontDepth}, whose band 0 is
     * the objective's own ground by construction. That is the finished map's own
     * statement of where the thing being taken is, rather than a second copy of
     * the generator's arithmetic.
     */
    private static void assertTheForceLandsAtItsStandoff(long seed, MapResult map) {
        TraversalAxis axis = rolledAxis(seed);
        MapPlacement from = axis == TraversalAxis.SOUTH_TO_NORTH
                ? MapPlacement.SOUTH : MapPlacement.WEST;
        ApproachRegion region = ApproachRegion.resolve(
                from, BattleSetup.CONQUEST_STANDOFF, objectiveClaim(map.frontDepth),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height);

        assertEquals(BattleSetup.CONQUEST_STANDOFF.cells(), region.standoffCells(),
                "a Conquest map affords its stated standoff");
        assertTrue(region.contains(map.marineSpawnX, map.marineSpawnY),
                "the marine spawn at " + map.marineSpawnX + "," + map.marineSpawnY
                        + " is outside the region the standoff resolved to "
                        + region.x0() + "," + region.y0() + ".."
                        + region.x1() + "," + region.y1());
        assertTrue(!map.landingAreas.isEmpty(), "the resolved region seated no beachhead");
        for (LandingArea area : map.landingAreas) {
            assertTrue(region.contains(area.left, area.bottom)
                            && region.contains(area.right, area.top),
                    "arrival area " + area.id + " at " + area.left + "," + area.bottom
                            + ".." + area.right + "," + area.top
                            + " lies outside the region the marines spawn in");
        }
    }

    /**
     * The objective claim's extent, taken off the finished map's front: band 0
     * is the objective precinct's own claimed ground.
     */
    private static int[] objectiveClaim(FrontDepth depth) {
        int x0 = depth.width();
        int y0 = depth.height();
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < depth.height(); y++) {
            for (int x = 0; x < depth.width(); x++) {
                if (depth.bandAt(x, y) != 0) continue;
                x0 = Math.min(x0, x);
                y0 = Math.min(y0, y);
                x1 = Math.max(x1, x);
                y1 = Math.max(y1, y);
            }
        }
        return new int[]{x0, y0, x1, y1};
    }

    /**
     * A mission that says nothing about its approach gets its own type's
     * default, and the two mission types disagree on purpose.
     *
     * <p>Conquest is the mission whose approach was measured and found too long
     * on the large map. Assault and Raid have not been measured against a
     * shorter one at all, so they keep {@link Standoff#FAR} — the beachhead on
     * the map edge, which is every approach those maps have ever had.
     */
    @Test
    void eachMissionTypeHasItsOwnDefaultApproach() {
        assertEquals(Standoff.STANDARD, BattleSetup.conquestPlanFor(
                        OperationTier.REINFORCED, RiskLevel.LOW, DEFENDED_TOWN,
                        null, TraversalAxis.SOUTH_TO_NORTH, 4096L).standoff(),
                "Conquest lands at the walk the balance was judged on");
        assertEquals(Standoff.CLOSE, BattleSetup.conquestPlanFor(
                        OperationTier.REINFORCED, RiskLevel.LOW, DEFENDED_TOWN,
                        null, Standoff.CLOSE, TraversalAxis.SOUTH_TO_NORTH, 4096L)
                        .standoff(),
                "a stated standoff wins over the mission type's default");
        assertEquals(Standoff.FAR, BattleSetup.precinctPlanFor(
                        MissionType.RAID, OperationTier.REINFORCED, RiskLevel.LOW,
                        DEFENDED_TOWN, MapScale.LARGE, 4096L).standoff(),
                "a raid keeps the map-edge beachhead it was measured on");
    }

    /**
     * The axis {@code createConquestBuild} rolls. Re-derived rather than
     * exposed, because the roll is one bit of the battle seed and the map is
     * the thing under test.
     */
    private static TraversalAxis rolledAxis(long seed) {
        return new Random(seed).nextBoolean()
                ? TraversalAxis.SOUTH_TO_NORTH : TraversalAxis.WEST_TO_EAST;
    }

    /** A defended town: enough market for places, enough rating for a garrison. */
    private static final TargetProfile DEFENDED_TOWN = new TargetProfile(
            5, 7, 2, 1, "independent",
            EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.SPACEPORT),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    /**
     * Every sprawl states exactly one place to take, and a remote map is that
     * place and nothing else.
     *
     * <p>The one keep is the law {@code BspCityGenerator} now enforces on any
     * map with an objective, and it starts here: two programmed precincts would
     * be two command posts however well the fillers behaved.
     */
    @Test
    void everySprawlStatesOnePlaceToTake() {
        for (PrecinctPlan.Sprawl sprawl : PrecinctPlan.Sprawl.values()) {
            PrecinctPlan plan = BattleSetup.conquestPlanFor(
                    OperationTier.REINFORCED, RiskLevel.LOW, DEFENDED_TOWN,
                    sprawl, TraversalAxis.SOUTH_TO_NORTH, 4096L);
            assertNotNull(plan, "a defended market derives a plan at " + sprawl);
            long programmed = plan.precincts().stream()
                    .filter(Precinct::isProgrammed).count();
            assertEquals(1, programmed, sprawl + " states one place to take");
            long zoned = plan.precincts().stream()
                    .filter(precinct -> !precinct.isProgrammed()).count();
            if (sprawl == PrecinctPlan.Sprawl.REMOTE) {
                assertEquals(0, zoned,
                        "a remote Conquest is the installation and the country round it");
            } else {
                assertTrue(zoned > 0,
                        sprawl + " puts a settlement round the installation");
            }
        }
    }

    /**
     * A Conquest with nothing behind it keeps the map it had.
     *
     * <p>Two gates, and they answer different questions. No market is nothing
     * to derive places from — the rule {@code precinctPlanFor} already applies.
     * No defences is nothing to derive a <em>garrison</em> from, and a Conquest
     * map with no garrison has no keep to take, so it would re-roll eight seeds
     * into an error rather than generating; the stock recipe stamps a fortress
     * ward whatever the profile says, and is the honest answer to both.
     */
    @Test
    void aConquestWithNothingBehindItKeepsTheStockCrossroad() {
        assertNull(BattleSetup.conquestPlanFor(OperationTier.REINFORCED,
                RiskLevel.LOW, TargetProfile.NEUTRAL, null,
                TraversalAxis.SOUTH_TO_NORTH, 91L));
        TargetProfile undefended = new TargetProfile(
                5, 7, 0, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.ROCK, SettlementLink.ROAD);
        assertNull(BattleSetup.conquestPlanFor(OperationTier.REINFORCED,
                RiskLevel.LOW, undefended, null,
                TraversalAxis.SOUTH_TO_NORTH, 91L));

        BattleSetup.MapBuild build = BattleSetup.createConquestBuild(
                91L, List.of(), false, OperationTier.FIRST_CONTRACT,
                RiskLevel.LOW, TargetProfile.NEUTRAL);
        try (BattleSimulation sim = build.sim()) {
            assertEquals(MapScale.CONQUEST.width, sim.getGrid().getWidth());
            assertNotNull(build.map().biomeMap,
                    "a marketless Conquest still gets the biome recipe");
        }
    }

    private static int bandCells(FrontDepth depth, int band) {
        int cells = 0;
        for (int y = 0; y < depth.height(); y++) {
            for (int x = 0; x < depth.width(); x++) {
                if (depth.bandAt(x, y) == band) cells++;
            }
        }
        return cells;
    }

    private static ConquestBattleFixture loadConquest(String name) throws Exception {
        try (InputStream stream = ConquestOnPrecinctsTest.class.getResourceAsStream(
                "/battle-fixtures/" + name + ".json")) {
            assertNotNull(stream, "missing fixture " + name);
            BattleFixture loaded = BattleFixtureJson.fromJson(new JSONObject(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            if (loaded instanceof BattleLaunchFixture launch) {
                loaded = launch.construction();
            }
            return (ConquestBattleFixture) loaded;
        }
    }

    private static FlybyRoster roster(List<FighterWingCommitment> commitments) {
        if (commitments.isEmpty()) return FlybyRoster.EMPTY;
        List<FighterWing> wings = new ArrayList<>();
        for (FighterWingCommitment commitment : commitments) {
            wings.add(new FighterWing(commitment.profile(), commitment.side(),
                    commitment.sortieCount(), commitment.firstArrivalSec(),
                    commitment.spawnIntervalSec()));
        }
        return new FlybyRoster(wings);
    }
}
