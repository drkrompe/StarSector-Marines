package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.ConquestTrackLayout;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            assertTheBeachheadIsAPlaceTheMarinesHold(map, sim);
            assertNothingElseStandsInTheBeachhead(map);
            assertThereIsResistanceInDepth(fixture, map);
            assertEveryCompoundCanBeWalkedTo(map);
            assertEveryLaneRouteIsWalkable(map);
        }
    }

    /**
     * The marines come ashore on a place of their own, and hold it.
     *
     * <p>Four facts, and each one is a way the beachhead used to fail. It is a
     * <b>compound</b>, so the capture rule and the defender's counterattack
     * reason about it rather than about a scatter of berths. It reads
     * {@code MARINE_HELD} at tick zero, because it is the attacker's ground and
     * a compound that started held by the defender would make a Conquest
     * unwinnable until the marines captured the pad they landed on. Every berth
     * and the marine spawn lie inside it, which is what confining the landing
     * stage to the landing precinct's claim buys — before it, the berths were
     * the first open ground a scan found in a third of the map, base district
     * included. And no other compound overlaps a berth, which is that same
     * confinement read from the other end.
     */
    private static void assertTheBeachheadIsAPlaceTheMarinesHold(MapResult map,
                                                                 BattleSimulation sim) {
        List<TacticalNode> beachheads = map.tacticalMap.all().stream()
                .filter(node -> node.kind == TacticalNode.Kind.BEACHHEAD)
                .toList();
        assertEquals(1, beachheads.size(),
                "a Conquest comes ashore on exactly one landing place");
        TacticalNode beachhead = beachheads.get(0);
        CompoundService.Record record = sim.getCompoundService().getRecord(beachhead);
        assertNotNull(record, "the landing place is not registered as a compound");
        assertEquals(CompoundService.CompoundState.MARINE_HELD, record.state,
                "the marines do not hold the ground they landed on at tick zero");

        assertTrue(covers(beachhead, map.marineSpawnX, map.marineSpawnY),
                "the marine spawn at " + map.marineSpawnX + "," + map.marineSpawnY
                        + " is outside the landing place " + extent(beachhead));
        for (LandingArea area : map.landingAreas) {
            assertTrue(covers(beachhead, area.left, area.bottom)
                            && covers(beachhead, area.right, area.top),
                    "arrival area " + area.id + " at " + area.left + "," + area.bottom
                            + ".." + area.right + "," + area.top
                            + " lies outside the landing place " + extent(beachhead));
            for (TacticalNode other : map.tacticalMap.all()) {
                if (other == beachhead || !CompoundService.isCompound(other.kind)) continue;
                assertTrue(other.right < area.left || other.left > area.right
                                || other.bottom < area.bottom || other.top > area.top,
                        "arrival area " + area.id + " overlaps the " + other.kind
                                + " at " + extent(other) + ", so the marines land on "
                                + "somebody else's ground");
            }
        }
    }

    /**
     * Nothing else stands inside the ground the marines come ashore on.
     *
     * <p>Wider than the per-berth check above, and it is the one that caught a
     * real defect: the lane ladder used to be seeded before the landing place,
     * so the outermost rung took ground the beachhead's claim then grew around,
     * and {@code reinforced-south} put an enemy barracks inside the marines'
     * own landing zone — clear of every berth, and still a garrison the force
     * lands beside. The order is objective, landing, lanes, settlement now, and
     * a waypoint that falls in the beachhead slides forward along its own path.
     *
     * <p>Asked of every compound rather than of lane places alone, because a
     * settlement building inside the landing zone would be the same fault
     * arriving from the other direction.
     */
    private static void assertNothingElseStandsInTheBeachhead(MapResult map) {
        TacticalNode beachhead = map.tacticalMap.all().stream()
                .filter(node -> node.kind == TacticalNode.Kind.BEACHHEAD)
                .findFirst().orElseThrow();
        for (TacticalNode other : map.tacticalMap.all()) {
            if (other == beachhead || !CompoundService.isCompound(other.kind)) continue;
            boolean apart = other.right < beachhead.left || other.left > beachhead.right
                    || other.bottom > beachhead.top || other.top < beachhead.bottom;
            assertTrue(apart, "a " + other.kind + " at " + extent(other)
                    + " stands inside the landing place " + extent(beachhead)
                    + ", so the marines come ashore beside it");
        }
    }

    private static boolean covers(TacticalNode node, int x, int y) {
        return x >= node.left && x <= node.right && y >= node.top && y <= node.bottom;
    }

    private static String extent(TacticalNode node) {
        return node.left + "," + node.top + ".." + node.right + "," + node.bottom;
    }

    /**
     * The tracks have places on them, and they are layered.
     *
     * <p>Two readings of the same set of compounds. <b>Depth</b> is which
     * {@link FrontDepth} band each one stands in, and <b>breadth</b> is which of
     * {@code ConquestTrackLayout}'s lateral thirds it stands in — the map's side
     * of the lanes the commanders advance up. Before this a Conquest map put
     * every compound in band 0 inside one wall, and all three tracks arrived at
     * it with nothing to take on the way.
     *
     * <p><b>Bands 1 and 2, not 1 to 3, and that is a measurement rather than a
     * concession.</b> A front band is a ring around the objective, cut into
     * three equal rings out to the map's furthest cell; a lane is a ribbon along
     * the axis. On both canonical fixtures band 3 is the ground <em>behind</em>
     * the beachhead: at {@code CLOSE} the force lands about two hundred cells
     * short of the claim, which is band 2, so a rung placed in band 3 would
     * stand at the marines' backs. On {@code full-strength-west} it is
     * impossible rather than merely undesirable — the lateral extent is 336
     * cells against a ring width of 139, so no cell in front of the beachhead is
     * far enough from the claim to be band 3 at all. The ladder still has three
     * rungs; the outer one shares band 2 with the middle one.
     */
    private static void assertThereIsResistanceInDepth(ConquestBattleFixture fixture,
                                                       MapResult map) {
        ConquestTrackLayout tracks = new ConquestTrackLayout(rolledAxis(fixture.seed()),
                MapScale.CONQUEST.width, MapScale.CONQUEST.height);
        int[][] byBandAndLane = new int[map.frontDepth.bands()][tracks.trackCount()];
        for (TacticalNode node : map.tacticalMap.all()) {
            if (!CompoundService.isCompound(node.kind)) continue;
            int lane = tracks.trackForCell(node.anchorX, node.anchorY);
            if (lane < 0) continue;
            byBandAndLane[map.frontDepth.bandAt(node.anchorX, node.anchorY)][lane]++;
        }

        String tally = tally(byBandAndLane);
        // The plan the first attempt derives. A re-rolled map is a different
        // plan, so this is what the shortfall was rather than what it is — which
        // is still the number worth printing when the assertion below fails.
        PrecinctPlan plan = BattleSetup.conquestPlanFor(fixture.tier(), fixture.risk(),
                fixture.targetProfile(), fixture.sprawl(),
                rolledAxis(fixture.seed()), fixture.seed());
        String unplaced = plan.unplacedLanePlaces().size() + " rungs unseated "
                + plan.unplacedLanePlaces();

        for (int band = 1; band <= 2; band++) {
            int lanes = 0;
            for (int lane = 0; lane < tracks.trackCount(); lane++) {
                if (byBandAndLane[band][lane] > 0) lanes++;
            }
            assertTrue(lanes >= 2, "front band " + band + " holds compounds in "
                    + lanes + " of " + tracks.trackCount() + " lanes; " + tally
                    + "; " + unplaced);
        }
        for (int lane = 0; lane < tracks.trackCount(); lane++) {
            int ahead = 0;
            for (int band = 1; band < map.frontDepth.bands(); band++) {
                ahead += byBandAndLane[band][lane];
            }
            assertTrue(ahead > 0, "lane " + lane + " has nothing on it outside the "
                    + "objective's own claim; " + tally + "; " + unplaced);
        }
    }

    /** The band-by-lane grid, for an assertion message that says what was found. */
    private static String tally(int[][] byBandAndLane) {
        StringBuilder out = new StringBuilder("compounds by band x lane");
        for (int band = 0; band < byBandAndLane.length; band++) {
            out.append(" [").append(band).append(':');
            for (int lane = 0; lane < byBandAndLane[band].length; lane++) {
                out.append(' ').append(byBandAndLane[band][lane]);
            }
            out.append(']');
        }
        return out.toString();
    }

    /**
     * Every compound is reachable from the marine spawn.
     *
     * <p>The standing law {@code conquest-nouns.md} states: victory requires
     * every compound to flip, so one nobody can walk into is an unwinnable
     * mission rather than a cosmetic defect. It is asserted here because lanes
     * put nine more walled places on the map, each with its own gates, and a
     * post that sealed itself would be exactly that failure.
     *
     * <p>Reached means <em>some</em> walkable cell of the compound's footprint,
     * not its anchor: a tactical-node anchor carries no promise of standing on
     * open floor, which is the trap {@code mapgen-nouns.md} already records.
     */
    private static void assertEveryCompoundCanBeWalkedTo(MapResult map) {
        NavigationGrid grid = map.grid;
        int width = grid.getWidth();
        int height = grid.getHeight();
        boolean[][] seen = new boolean[width][height];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{map.marineSpawnX, map.marineSpawnY});
        seen[map.marineSpawnX][map.marineSpawnY] = true;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (seen[nx][ny] || !grid.isWalkable(nx, ny)) continue;
                seen[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        for (TacticalNode node : map.tacticalMap.all()) {
            if (!CompoundService.isCompound(node.kind)) continue;
            boolean reached = false;
            for (int x = Math.min(node.left, node.right);
                 x <= Math.max(node.left, node.right) && !reached; x++) {
                for (int y = Math.min(node.top, node.bottom);
                     y <= Math.max(node.top, node.bottom); y++) {
                    if (x < 0 || y < 0 || x >= width || y >= height) continue;
                    if (seen[x][y]) {
                        reached = true;
                        break;
                    }
                }
            }
            assertTrue(reached, node.kind + " at " + node.anchorX + "," + node.anchorY
                    + " cannot be walked to from the marine spawn at "
                    + map.marineSpawnX + "," + map.marineSpawnY);
        }
    }

    /**
     * Every lane's recorded route can be walked, end to end, link by link.
     *
     * <p>The same law as {@link #assertEveryCompoundCanBeWalkedTo}, stated for
     * the road between the places rather than for the places. A lane is a route
     * through its links and the commander stages along it; a lane whose route
     * could not be walked is a generation defect, not a shorter route — and it
     * is invisible on a finished map, because the record simply comes back
     * short.
     *
     * <p>Walkability is asserted rather than assumed: the read-back searches
     * walkable ground only, so a route it produced is walkable by construction
     * and this would pass on a route that had never been checked. What it
     * actually catches is a route that stopped early — a link missing from the
     * chain, or a polyline that never reached the keep.
     */
    private static void assertEveryLaneRouteIsWalkable(MapResult map) {
        assertFalse(map.lanes.isEmpty(), "a Conquest map with lanes recorded none");
        for (LaneRoute lane : map.lanes) {
            assertTrue(lane.isWalked(), "lane " + (lane.lane() + 1)
                    + " records " + lane.links().size() + " links and "
                    + lane.route().size() + " cells of route, which is not a way through");
            assertEquals(LaneRoute.OBJECTIVE_BAND,
                    lane.links().get(lane.links().size() - 1).band(),
                    "lane " + (lane.lane() + 1) + " does not end at the objective");
            for (LaneRoute.Cell cell : lane.route()) {
                assertTrue(map.grid.isWalkable(cell.x(), cell.y()),
                        "lane " + (lane.lane() + 1) + " routes through "
                                + cell.x() + "," + cell.y() + ", which is not walkable");
            }
            // Cell by cell and in order: a polyline with a jump in it is two
            // routes, and a commander pacing along it would teleport.
            for (int i = 1; i < lane.route().size(); i++) {
                LaneRoute.Cell was = lane.route().get(i - 1);
                LaneRoute.Cell now = lane.route().get(i);
                assertEquals(1, Math.abs(was.x() - now.x()) + Math.abs(was.y() - now.y()),
                        "lane " + (lane.lane() + 1) + " jumps from " + was + " to " + now);
            }
            for (LaneRoute.Link link : lane.links()) {
                LaneRoute.Cell at = lane.route().get(link.routeIndex());
                assertEquals(link.x(), at.x(), link.place() + " is not on its own route");
                assertEquals(link.y(), at.y(), link.place() + " is not on its own route");
            }
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
        assertEquals(Standoff.CLOSE, BattleSetup.conquestPlanFor(
                        OperationTier.REINFORCED, RiskLevel.LOW, DEFENDED_TOWN,
                        null, TraversalAxis.SOUTH_TO_NORTH, 4096L).standoff(),
                "Conquest lands at the short approach the matrix chose");
        assertEquals(Standoff.STANDARD, BattleSetup.conquestPlanFor(
                        OperationTier.REINFORCED, RiskLevel.LOW, DEFENDED_TOWN,
                        null, Standoff.STANDARD, TraversalAxis.SOUTH_TO_NORTH, 4096L)
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
     * Every sprawl states exactly one place with a keep in it, and a remote map
     * is places and country and nothing else.
     *
     * <p>The one keep is the law {@code BspCityGenerator} now enforces on any
     * map with an objective, and it starts here: two keeps would be two command
     * posts however well the fillers behaved.
     *
     * <p><b>It is the keep that is unique, not the programmed precinct.</b> A
     * Conquest map now carries ten of those — the fortress and the nine rungs of
     * its lanes — and what keeps the law true is that neither lane program packs
     * a {@code KEEP_THRONE}. Counting programmed places instead would pass only
     * while lanes did not exist.
     */
    @Test
    void everySprawlStatesOnePlaceToTake() {
        for (PrecinctPlan.Sprawl sprawl : PrecinctPlan.Sprawl.values()) {
            PrecinctPlan plan = BattleSetup.conquestPlanFor(
                    OperationTier.REINFORCED, RiskLevel.LOW, DEFENDED_TOWN,
                    sprawl, TraversalAxis.SOUTH_TO_NORTH, 4096L);
            assertNotNull(plan, "a defended market derives a plan at " + sprawl);
            long keeps = plan.precincts().stream()
                    .filter(Precinct::isProgrammed)
                    .filter(precinct -> precinct.program()
                            .countOf(RoomPurpose.KEEP_THRONE) > 0)
                    .count();
            assertEquals(1, keeps, sprawl + " states one place to take");
            assertEquals(plan.objective(), plan.precincts().stream()
                            .filter(Precinct::isProgrammed).findFirst().orElseThrow(),
                    sprawl + " lets a lane place stand ahead of the fortress in the "
                            + "list, so everything reading objective() reads the wrong one");
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
