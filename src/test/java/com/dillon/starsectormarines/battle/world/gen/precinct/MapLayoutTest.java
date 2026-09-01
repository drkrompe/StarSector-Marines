package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mission says roughly where things are, and the map obeys.
 *
 * <p>Conquest culminates in taking an installation, so a scenario needs to be
 * able to put that installation somewhere and the attacking force somewhere
 * else. A cell is the wrong unit to say it in — what cell means "north-east"
 * depends on the map — so a placement is a fraction of the map and the same
 * brief lays out at any scale.
 */
class MapLayoutTest {

    private static final int W = 560;
    private static final int H = 336;

    private static TargetProfile world() {
        return new TargetProfile(9, 5, 5, 2, "independent",
                EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    private static List<PrecinctBrief> conquestBriefs(MapPlacement objective) {
        return List.of(
                PrecinctBrief.garrison("garrison", objective,
                        GrownTrunkPlan.Profile.hamlet(), FortressProgram.garrison()),
                PrecinctBrief.settlement("town", MapPlacement.CENTRE,
                        GrownTrunkPlan.Profile.town()));
    }

    private static PrecinctPlan plan(MapPlacement objective, MapPlacement from, long seed) {
        return PrecinctPlan.laidOut(conquestBriefs(objective), from, W, H, new Random(seed));
    }

    /** Asked for a corner, the place lands in that corner. */
    @Test
    void aPlaceLandsWhereTheMissionAsked() {
        for (long seed = 1; seed <= 12; seed++) {
            Precinct garrison = plan(MapPlacement.NORTH_EAST, MapPlacement.SOUTH_WEST, seed)
                    .objective();
            assertTrue(garrison.seedX() > W / 2, "seed " + seed + ": a north-east garrison "
                    + "seeded at x=" + garrison.seedX() + " on a " + W + "-wide map");
            assertTrue(garrison.seedY() > H / 2, "seed " + seed + ": a north-east garrison "
                    + "seeded at y=" + garrison.seedY() + ", and north is high y");
        }
    }

    /** The same brief lays out at any map size, which is why it is a fraction. */
    @Test
    void theSameBriefWorksAtAnyScale() {
        for (int[] size : new int[][]{{200, 140}, {560, 336}, {900, 600}}) {
            PrecinctPlan laid = PrecinctPlan.laidOut(
                    conquestBriefs(MapPlacement.NORTH_EAST), MapPlacement.SOUTH_WEST,
                    size[0], size[1], new Random(42L));
            Precinct garrison = laid.objective();
            assertTrue(garrison.seedX() > size[0] / 2 && garrison.seedY() > size[1] / 2,
                    size[0] + "x" + size[1] + ": the garrison left the north-east");
        }
    }

    /** Two places asked for different corners do not end up beside each other. */
    @Test
    void placesAskedForDifferentCornersStayApart() {
        PrecinctPlan laid = PrecinctPlan.laidOut(List.of(
                PrecinctBrief.garrison("north", MapPlacement.NORTH_EAST,
                        GrownTrunkPlan.Profile.hamlet(), FortressProgram.garrison()),
                PrecinctBrief.settlement("south", MapPlacement.SOUTH_WEST,
                        GrownTrunkPlan.Profile.town())),
                null, W, H, new Random(42L));
        Precinct a = laid.precincts().get(0);
        Precinct b = laid.precincts().get(1);
        int gap = (int) Math.hypot(a.seedX() - b.seedX(), a.seedY() - b.seedY());
        assertTrue(gap > PrecinctPlan.MIN_SEED_SEPARATION,
                "opposite corners came out " + gap + " cells apart");
    }

    /**
     * The attacker arrives where it was told, and the map is generated with it.
     *
     * <p>Asserted through the finished map rather than the plan, because the
     * spawn is chosen by a stage at the end of the pipeline and the plan only
     * states the intent.
     */
    @Test
    void theAttackerArrivesWhereTheMissionSaid() {
        MapResult map = new BspCityGenerator()
                .usePrecincts(plan(MapPlacement.NORTH_EAST, MapPlacement.SOUTH_WEST, 42L))
                .generate(W, H, 42L, null, world());
        assertTrue(map.marineSpawnX < W / 2 && map.marineSpawnY < H / 2,
                "marines told to arrive from the south-west spawned at "
                        + map.marineSpawnX + "," + map.marineSpawnY);
    }

    /** The defender holds the thing the mission is about. */
    @Test
    void theDefenderHoldsTheObjective() {
        PrecinctPlan laid = plan(MapPlacement.NORTH_EAST, MapPlacement.SOUTH_WEST, 42L);
        MapResult map = new BspCityGenerator().usePrecincts(laid)
                .generate(W, H, 42L, null, world());
        Precinct objective = laid.objective();
        int gap = (int) Math.hypot(map.defenderSpawnX - objective.seedX(),
                map.defenderSpawnY - objective.seedY());
        assertTrue(gap < 120, "the defender spawned " + gap + " cells from the installation "
                + "it is supposed to be holding");
    }

    /**
     * Told nothing, the attacker is still put somewhere worth attacking from.
     *
     * <p>A force landing beside the thing it is meant to take has no approach to
     * fight through, which is most of what a conquest map is for.
     */
    @Test
    void withNoInstructionTheAttackerStartsAwayFromTheObjective() {
        PrecinctPlan laid = plan(MapPlacement.NORTH_EAST, null, 42L);
        MapResult map = new BspCityGenerator().usePrecincts(laid)
                .generate(W, H, 42L, null, world());
        Precinct objective = laid.objective();
        int gap = (int) Math.hypot(map.marineSpawnX - objective.seedX(),
                map.marineSpawnY - objective.seedY());
        assertTrue(gap > W / 3, "with no stated approach the marines spawned " + gap
                + " cells from the objective, which is no approach at all");
    }
}
