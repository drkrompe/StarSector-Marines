package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.PlanetSkin;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hazardous world's town is sealed inside one wall with a few locks ({@link SealedTown}). */
class SealedTownTest {

    private static TargetProfile world(Climate climate) {
        return new TargetProfile(5, 5, 2, 1, "independent", EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.ROCK, SettlementLink.ROAD).withClimate(climate);
    }

    private static Precinct settlement(PrecinctPlan plan) {
        return plan.precincts().stream().filter(p -> p.name().equals("settlement")).findFirst().orElseThrow();
    }

    @Test
    void aHazardousWorldsTownIsWalledWithAFewLocksAndNothingOutside() {
        for (Climate.Hazard hazard : List.of(Climate.Hazard.TOXIC, Climate.Hazard.AIRLESS, Climate.Hazard.RADIATION)) {
            PrecinctPlan plan = PrecinctPlan.derive(world(new Climate(EnumSet.of(hazard))), 240, 144, new Random(7));
            Precinct town = settlement(plan);
            assertFalse(town.isProgrammed(), hazard + ": still a town of streets and blocks");
            assertEquals(Precinct.Boundary.WALLED, town.boundary(), hazard.toString());
            assertEquals(SealedTown.LOCKS, town.fortification(), hazard.toString());
            assertEquals("garrison", plan.objective().name(), hazard + ": the garrison stays the objective");
            for (Precinct p : plan.precincts()) assertFalse(p.name().startsWith("outlying"), hazard + ": " + p.name());
        }
    }

    @Test
    void anOpenWorldsTownIsOpen() {
        Precinct town = settlement(PrecinctPlan.derive(world(Climate.NONE), 240, 144, new Random(7)));
        assertEquals(Precinct.Boundary.OPEN, town.boundary());
    }

    @Test
    void aSealedLayoutWallsEveryZonedPlaceWhereItStandsAndLeavesTheMissionsOwn() {
        GrownTrunkPlan.Profile growth = GrownTrunkPlan.Profile.hamlet();
        Precinct town = Precinct.settlement("colony", 40, 30, growth, PrecinctCharacter.TOWN);
        Precinct hamlet = Precinct.settlement("rear-west", 10, 5, growth, PrecinctCharacter.HAMLET);
        Precinct garrison = Precinct.garrison("redoubt-1", 60, 50, growth, FortressProgram.fieldRedoubt(),
                Fortification.GARRISON);
        PrecinctPlan sealed = SealedTown.seal(new PrecinctPlan(List.of(town, hamlet, garrison)));
        for (int i = 0; i < 2; i++) {
            Precinct before = List.of(town, hamlet).get(i), after = sealed.precincts().get(i);
            assertEquals(before.name(), after.name());
            assertEquals(before.seedX(), after.seedX());
            assertEquals(before.character(), after.character());
            assertTrue(SealedTown.is(after), after.name() + " is a sealed town");
            assertEquals(SealedTown.LOCKS, after.fortification());
        }
        assertSame(garrison, sealed.precincts().get(2), "a mission's garrison keeps its own wall");
        assertFalse(SealedTown.is(garrison));
        assertFalse(SealedTown.is(town), "an open town is not sealed");
    }

    /** A town on the east of a 24 by 12 map, open country to its west, its wall on column 12. */
    private static int[][] eastTown() {
        int[][] claim = new int[24][12];
        for (int x = 0; x < 24; x++) for (int y = 0; y < 12; y++) claim[x][y] = x >= 12 ? 1 : GrownTrunkPlan.UNOWNED;
        return claim;
    }

    private static boolean[][] allOpen() {
        boolean[][] open = new boolean[24][12];
        for (boolean[] column : open) Arrays.fill(column, true);
        return open;
    }

    private static int[][] noRoad() {
        int[][] road = new int[24][12];
        for (int[] column : road) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        return road;
    }

    @Test
    void anOwnedMapSealsItsTownsByTheWorldItIsFoughtOnNeverItsHome() {
        Precinct town = Precinct.settlement("colony", 40, 30, GrownTrunkPlan.Profile.hamlet(), PrecinctCharacter.TOWN);
        PrecinctPlan plan = new PrecinctPlan(List.of(town));
        TargetProfile toxic = world(new Climate(EnumSet.of(Climate.Hazard.TOXIC)));
        TargetProfile verdant = new TargetProfile(5, 5, 2, 1, "independent", EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
        assertTrue(SealedTown.is(PlanetSkin.sealedFor(plan, toxic).precincts().get(0)));
        assertTrue(SealedTown.is(PlanetSkin.sealedFor(plan, world(Climate.NONE)).precincts().get(0)),
                "bare rock is airless");
        assertSame(plan, PlanetSkin.sealedFor(plan, verdant), "a world people live on in the open keeps it open");
        assertEquals(null, PlanetSkin.sealedFor(null, toxic));
    }

    @Test
    void groundNoRoadReachesGetsALockOfItsOwnWhereTheTownsGroundRunsDeepest() {
        int[][] claim = eastTown();
        boolean[][] open = allOpen();
        // A row of buildings behind the wall except at rows 7 and 8.
        for (int y = 0; y < 12; y++) if (y != 7 && y != 8) open[13][y] = false;
        List<PrecinctBoundary.Gate> locks = SealedTown.groundLocks(claim, 1, open, List.of());
        assertEquals(1, locks.size(), "one lock onto the open country");
        for (int[] c : locks.get(0).cells()) assertEquals(12, c[0], "on the wall");
        assertTrue(has(locks.get(0).cells(), 12, 7) && has(locks.get(0).cells(), 12, 8), "where the street runs in");
        assertTrue(SealedTown.groundLocks(claim, 1, open, locks).isEmpty(), "ground a lock opens onto is served");
    }

    @Test
    void theEnvelopeStandsThickOnTheTownsOwnGroundAndEachLockIsAChamberWithADoorAtEitherEnd() {
        int[][] claim = eastTown(), road = noRoad();
        for (int x = 12; x < 24; x++) road[x][10] = 1; // a street running in from the wall
        boolean[] sealed = {false, true};
        boolean[][] band = SealedTown.band(claim, sealed, sealed);
        int thick = SealedTown.WALL_THICKNESS;
        assertTrue(band[12][2] && band[12 + thick - 1][2], "the band runs " + thick + " in from the wall");
        assertFalse(band[12 + thick][2], "and no further");
        assertFalse(band[23][2] || band[18][0], "the map's edge is no outside");
        boolean[][] outline = SealedTown.facingOut(PrecinctBoundary.outline(claim, 1, 24, 12), claim, 1, new boolean[]{false, true});
        PrecinctBoundary.Gate gate = new PrecinctBoundary.Gate(
                List.of(new int[]{12, 5}, new int[]{12, 6}, new int[]{12, 7}), true);
        for (int[] c : gate.cells()) outline[c[0]][c[1]] = false;
        SealedTown.Envelope envelope = SealedTown.envelope(claim, 1, outline, band, road, List.of(gate));
        boolean[][] wall = envelope.wall();
        for (int x = 12; x < 12 + thick; x++) assertTrue(wall[x][2], "the envelope stands " + thick + " through");
        assertFalse(wall[13][10], "a street in the band is not walled over");
        SealedTown.Lock lock = envelope.locks().get(0);
        assertEquals(-1, lock.outX(), "the way out is west");
        assertEquals(3 * thick, lock.chamber().size(), "the chamber runs through the envelope");
        for (int[] c : lock.chamber()) assertFalse(wall[c[0]][c[1]], "a chamber is open");
        assertEquals(gate.cells(), lock.outer(), "the outer door is on the town's outline");
        assertEquals(3, lock.inner().size(), "the inner door, at the envelope's inner face");
        for (int[] c : lock.inner()) assertEquals(12 + thick - 1, c[0]);
    }

    @Test
    void eachPartOfATownGetsItsOwnLockOntoTheGroundTheyShare() {
        int[][] claim = eastTown();
        // A second part, a hamlet on the west edge, across the same open country.
        for (int x = 0; x < 3; x++) for (int y = 0; y < 12; y++) claim[x][y] = 1;
        List<PrecinctBoundary.Gate> locks = SealedTown.groundLocks(claim, 1, allOpen(), List.of());
        assertEquals(2, locks.size(), "one lock out of each part");
    }

    @Test
    void noEnvelopeFacesAWalledPlaceStandingInTheTown() {
        int[][] claim = eastTown();
        for (int x = 16; x < 20; x++) for (int y = 4; y < 8; y++) claim[x][y] = 2; // a walled compound in the town
        boolean[] sealed = {false, true, false}, side = {false, true, true};
        boolean[][] band = SealedTown.band(claim, sealed, side);
        assertFalse(band[15][5], "the town's ground beside the compound is built on");
        assertTrue(band[12][5], "the envelope still faces the country");
        boolean[][] out = SealedTown.facingOut(PrecinctBoundary.outline(claim, 1, 24, 12), claim, 1, side);
        assertFalse(out[15][5], "no envelope against the compound's own wall");
    }

    @Test
    void noWallStandsAlongTheMapsEdge() {
        int[][] claim = eastTown();
        boolean[][] kept = SealedTown.facingOut(PrecinctBoundary.outline(claim, 1, 24, 12), claim, 1, new boolean[]{false, true});
        assertTrue(kept[12][5], "the wall against the country stands");
        assertFalse(kept[23][5], "the town runs on past the east edge");
        assertFalse(kept[18][0], "and the south edge");
    }

    @Test
    void theOuterCourseIsTheEnvelopesFaceToTheWorldNotToTheTownOrTheMapsEdge() {
        int[][] claim = eastTown();
        boolean[][] wall = new boolean[24][12];
        for (int x = 12; x < 15; x++) for (int y = 0; y < 12; y++) wall[x][y] = true; // three cells through
        boolean[][] course = SealedTown.outerCourse(wall, claim, 1, new boolean[]{false, true});
        for (int y = 0; y < 12; y++) {
            assertTrue(course[12][y], "the cells against the country");
            assertFalse(course[13][y] || course[14][y], "not the body behind them, nor its face to the town");
        }
    }

    private static boolean has(List<int[]> cells, int x, int y) {
        return cells.stream().anyMatch(c -> c[0] == x && c[1] == y);
    }
}
