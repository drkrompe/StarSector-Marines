package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.precinct.ApproachRegion;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.LandingKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A precinct map can be landed on.
 *
 * <p>The stock recipe's arrival areas are on a beach, and a precinct map has no
 * beach — no biome bands and no traversal axis, only a statement about where the
 * attack comes from. So the question here is whether the berths land in the
 * region the mission named, facing off the edge that region sits against, in
 * enough numbers that a mission can ask for its drop zones without throwing.
 *
 * <p>Asked of a whole generated map rather than of a hand-built fixture, because
 * what makes this hard is the ground: walls, buildings, water and emplacements
 * are all already on it by the time the stage runs, and a stage that finds a
 * clear pair on an empty grid proves nothing about a finished one.
 */
class PrecinctLandingAreaStageTest {

    private static final int W = 560;
    private static final int H = 336;
    private static final long SEED = 42L;

    /** A size-7, rating-6 world: a town, a garrison worth taking, one outlying place. */
    private static final TargetProfile WORLD = new TargetProfile(
            7, 50, 6, 3, "", EnumSet.noneOf(EconomicFunction.class),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    private record Generated(PrecinctPlan plan, MapResult map) {}

    private static Generated generate(MapPlacement objective, MapPlacement attackerFrom) {
        PrecinctPlan plan = PrecinctPlan.derive(WORLD, PrecinctPlan.Sprawl.BALANCED,
                Fortification.Demand.UNSTATED, objective, attackerFrom, W, H,
                new Random(SEED));
        return new Generated(plan,
                new BspCityGenerator().generate(W, H, SEED, null, WORLD, plan));
    }

    private static MapResult map(MapPlacement objective, MapPlacement attackerFrom) {
        return generate(objective, attackerFrom).map();
    }

    /**
     * The stated approach: berths in the attacker's own region, facing the edge
     * it arrives across, and enough of them for the drop zones a mission asks
     * for.
     *
     * <p>The count is measured against {@link ConquestArrivalConfig#DEFAULT},
     * which is what every checked-in conquest fixture carries and the most any
     * of them asks for — {@code conquestArrivalSlots} throws when the map
     * authored fewer areas than the mission's drop-zone count.
     */
    @Test
    void aStatedApproachSeatsItsBeachhead() {
        MapResult map = map(MapPlacement.NORTH, MapPlacement.SOUTH);
        int[] region = MapPlacement.SOUTH.bounds(W, H);

        assertTrue(map.landingAreas.size() >= ConquestArrivalConfig.DEFAULT.dropZoneCount(),
                "the attacker's region seated " + map.landingAreas.size() + " arrival areas, "
                        + "fewer than the " + ConquestArrivalConfig.DEFAULT.dropZoneCount()
                        + " drop zones a conquest mission asks for");
        for (LandingArea area : map.landingAreas) {
            assertTrue(area.left >= region[0] && area.right <= region[2]
                            && area.bottom >= region[1] && area.top <= region[3],
                    "arrival area " + area.id + " at " + area.left + "," + area.bottom
                            + ".." + area.right + "," + area.top
                            + " lies outside the region the mission put the attacker in");
            assertEquals(LandingPad.Approach.SOUTH, area.approach,
                    "arrival area " + area.id + " approaches from " + area.approach
                            + " on a map whose attacker arrives from the south");
            for (LandingPad berth : area.berths()) {
                assertEquals(LandingPad.Approach.SOUTH, berth.approach,
                        "a berth of " + area.id + " disagrees with its own area's approach");
                for (int y = berth.bottom(); y <= berth.top(); y++) {
                    for (int x = berth.left(); x <= berth.right(); x++) {
                        assertTrue(map.grid.isWalkable(x, y),
                                "berth cell " + x + "," + y + " of " + area.id
                                        + " is not walkable, so nothing can be set down on it");
                    }
                }
            }
        }
    }

    /**
     * The approach follows the region rather than a fixed side.
     *
     * <p>The conquest stage reads its approach off the traversal axis; there is
     * no axis here, so the only thing that can say which way the shuttles come
     * in is which edge the attacker's own region sits against.
     */
    @Test
    void theApproachIsTheEdgeTheRegionSitsAgainst() {
        MapResult map = map(MapPlacement.EAST, MapPlacement.WEST);
        int[] region = MapPlacement.WEST.bounds(W, H);
        assertTrue(!map.landingAreas.isEmpty(),
                "a western attacker region seated no arrival area at all");
        for (LandingArea area : map.landingAreas) {
            assertEquals(LandingPad.Approach.WEST, area.approach,
                    "arrival area " + area.id + " approaches from " + area.approach
                            + " on a map whose attacker arrives from the west");
            assertTrue(area.left >= region[0] && area.right <= region[2],
                    "arrival area " + area.id + " lies outside the western region");
        }
    }

    /**
     * Told nothing, the beachhead is where the marines are.
     *
     * <p>The stage and {@link SpawnAnchorStage} read the same rule for an
     * unstated attacker — the corner furthest from the objective. What is worth
     * asserting is that they land in the same place: the corner the spawn stage
     * chose holds both the marine spawn and every berth authored for it.
     */
    @Test
    void anUnstatedAttackerLandsWhereItSpawns() {
        Generated generated = generate(MapPlacement.NORTH_EAST, null);
        MapResult map = generated.map();
        int[] corner = ApproachRegion
                .awayFrom(generated.plan().objective(), W, H).bounds(W, H);

        assertTrue(!map.landingAreas.isEmpty(),
                "the corner the spawn stage chose seated no arrival area at all");
        assertTrue(inside(corner, map.marineSpawnX, map.marineSpawnY),
                "the marine spawn at " + map.marineSpawnX + "," + map.marineSpawnY
                        + " is not in the corner the same rule chose for the beachhead");
        for (LandingArea area : map.landingAreas) {
            assertTrue(inside(corner, area.left, area.bottom)
                            && inside(corner, area.right, area.top),
                    "arrival area " + area.id + " at " + area.left + "," + area.bottom
                            + ".." + area.right + "," + area.top + " is outside the corner "
                            + "the marines spawn in");
        }
    }

    private static boolean inside(int[] rect, int x, int y) {
        return x >= rect[0] && x <= rect[2] && y >= rect[1] && y <= rect[3];
    }

    /**
     * A plan with a landing place lands on it, and on nothing else.
     *
     * <p>Asked of a hand-built context rather than of a generated map, because
     * the question is about the claim and a generated one cannot hand a test
     * its per-cell claim array. Everything here is open, walkable ground — so a
     * stage that read only the approach region would seat its first area at the
     * region's own near edge, in the neighbour's claim. The whole map is the
     * attacker's region; the landing precinct owns one band inside it and the
     * settlement owns the rest.
     */
    @Test
    void theBerthsStayOnTheLandingPlacesOwnGround() {
        int w = 120;
        int h = 120;
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(1L), w, h, 1L);

        // Index 0 is the objective, 1 the settlement, 2 the landing place.
        // The landing band sits well inside the region so a stage ignoring the
        // claim would seat its first area short of it and fail this outright.
        int[][] claim = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                claim[x][y] = y >= LANDING_LOW && y <= LANDING_HIGH ? 2 : 1;
            }
        }
        PrecinctPlan plan = new PrecinctPlan(List.of(
                Precinct.garrison("garrison", 60, 110, GrownTrunkPlan.Profile.hamlet(),
                        FortressProgram.garrison()),
                Precinct.settlement("settlement", 60, 60, GrownTrunkPlan.Profile.hamlet()),
                Precinct.landing("landing", 60, 45, GrownTrunkPlan.Profile.hamlet(),
                        LandingKind.FIELD, LandingKind.FIELD.program())),
                MapPlacement.SOUTH, Standoff.FAR);
        ctx.put(BspKeys.PRECINCTS, plan);
        ctx.put(BspKeys.PRECINCT_CLAIM, claim);
        ctx.put(BspKeys.TACTICAL_MAP, new TacticalMap(List.of()));

        new PrecinctLandingAreaStage().run(ctx);

        assertTrue(!ctx.landingAreas.isEmpty(), "the landing place seated no arrival area");
        for (LandingArea area : ctx.landingAreas) {
            for (int y = area.bottom; y <= area.top; y++) {
                for (int x = area.left; x <= area.right; x++) {
                    assertEquals(2, claim[x][y],
                            "arrival area " + area.id + " covers " + x + "," + y
                                    + ", which belongs to precinct " + claim[x][y]
                                    + " and not to the landing place");
                }
            }
        }

        TacticalMap tactical = ctx.get(BspKeys.TACTICAL_MAP);
        List<TacticalNode> beachheads = tactical.ofKind(TacticalNode.Kind.BEACHHEAD);
        assertEquals(1, beachheads.size(), "the landing place is one compound");
        TacticalNode beachhead = beachheads.get(0);
        assertEquals(Faction.MARINE, beachhead.defaultGuard,
                "the ground the marines came ashore on is theirs");
        assertEquals(LANDING_LOW, beachhead.top,
                "the beachhead's footprint is the landing place's own claim");
        assertEquals(LANDING_HIGH, beachhead.bottom,
                "the beachhead's footprint is the landing place's own claim");
        for (LandingArea area : ctx.landingAreas) {
            assertTrue(inside(new int[]{beachhead.left, beachhead.top,
                            beachhead.right, beachhead.bottom},
                            area.left, area.bottom),
                    "arrival area " + area.id + " is outside the beachhead compound");
        }
    }

    /** Where the landing precinct's claim starts and stops in the fixture above. */
    private static final int LANDING_LOW = 30;
    private static final int LANDING_HIGH = 60;
}
