package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.precinct.ApproachRegion;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
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
}
