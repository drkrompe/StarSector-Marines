package com.dillon.starsectormarines.battle.world.gen.rural;

import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Farmstead;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Grade;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Plot;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Road;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout.Use;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuralLayoutTest {

    private static final int W = 200, H = 120;
    /** A field against the north edge (low y), as Colony Breach lays one. */
    private static final FieldOfFire FIELD = new FieldOfFire(100, 30, 40);

    /** A town south of the country with a wide street arriving at x 140..144 and a narrow one at x 40..42. */
    private static boolean[][] town(int depth) {
        boolean[][] road = new boolean[W][H];
        for (int y = depth; y < H; y++) {
            for (int x = 140; x <= 144; x++) road[x][y] = true;
            for (int x = 40; x <= 42; x++) road[x][y] = true;
        }
        // A street running along the town's edge leads nowhere out of town.
        for (int x = 0; x < W; x++) road[x][depth] = true;
        return road;
    }

    private static RuralLayout layout() {
        int depth = FIELD.countryDepth(W, H) + 1;
        return RuralLayout.plan(W, H, FIELD, town(depth), 7L);
    }

    @Test
    void theCountryRoadCarriesTheWidestArrivingStreetToTheMapEdge() {
        RuralLayout layout = layout();
        Road country = layout.roads().stream().filter(r -> r.grade() == Grade.COUNTRY).findFirst().orElseThrow();
        assertFalse(country.alongX());
        assertEquals(140, country.x0());
        assertEquals(144, country.x1());
        assertEquals(0, country.y0(), "it runs out to the map edge");
        assertEquals(layout.depth() - 1, country.y1(), "and in to the town");
    }

    @Test
    void everyRoadIsStraightAndItsGradesWidthAlongItsLength() {
        for (Road road : layout().roads()) {
            assertEquals(road.grade().width, road.breadth(), road.toString());
            assertTrue(road.length() > road.breadth(), road.toString());
        }
    }

    @Test
    void theFarmRoadRunsTheTownsWholeEdge() {
        RuralLayout layout = layout();
        Road farm = layout.roads().stream().filter(r -> r.grade() == Grade.FARM).findFirst().orElseThrow();
        assertTrue(farm.alongX());
        assertEquals(0, farm.x0());
        assertEquals(W - 1, farm.x1());
        assertEquals(layout.depth() - 1, farm.y1(), "it lies against the town");
    }

    @Test
    void plotsAreTheGroundTheRoadsLeave() {
        RuralLayout layout = layout();
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < layout.depth(); y++) {
                boolean road = layout.roadAt(x, y) != null;
                boolean plot = layout.plotAt(x, y) != null;
                assertTrue(road ^ plot, "cell " + x + "," + y + " road=" + road + " plot=" + plot);
            }
        }
        for (Plot plot : layout.plots()) {
            if (plot.use() == Use.WILD) continue;
            assertTrue(plot.x1() - plot.x0() + 1 >= RuralLayout.MIN_PLOT - 1, plot.toString());
        }
    }

    @Test
    void fieldsLieBesideTheRoadAndThinOutAlongTheEdge() {
        int width = 400;
        FieldOfFire field = new FieldOfFire(200, 30, 40);
        int depth = field.countryDepth(width, H) + 1;
        boolean[][] road = new boolean[width][H];
        for (int y = depth; y < H; y++) for (int x = 198; x <= 202; x++) road[x][y] = true;
        RuralLayout layout = RuralLayout.plan(width, H, field, road, 5L);
        Road country = layout.roads().stream().filter(r -> r.grade() == Grade.COUNTRY).findFirst().orElseThrow();
        int townRow = layout.depth() - Grade.FARM.width - 1;
        assertNotEquals(Use.WILD, layout.plotAt(country.x0() - 1, townRow).use(), "beside the road, against the town");
        assertNotEquals(Use.WILD, layout.plotAt(country.x1() + 1, townRow).use());
        boolean wild = false;
        for (Plot plot : layout.plots()) {
            wild |= plot.use() == Use.WILD;
            if (plot.use() == Use.WILD || plot.y1() >= townRow) continue;
            // Out by the map edge a field lies within its reach of the road.
            int gap = Math.max(plot.x0() - country.x1() - 1, country.x0() - 1 - plot.x1());
            assertTrue(gap < width * RuralLayout.FAR_SIDE_REACH, plot.toString());
        }
        assertTrue(wild, "some of the country is nobody's field");
    }

    @Test
    void everyFieldIsReachedByATrackOrRoad() {
        RuralLayout layout = layout();
        for (Plot plot : layout.plots()) {
            if (plot.use() == Use.WILD) continue;
            boolean touches = false;
            for (int x = plot.x0() - 1; x <= plot.x1() + 1 && !touches; x++) {
                for (int y = plot.y0() - 1; y <= plot.y1() + 1 && !touches; y++) {
                    touches = layout.inCountry(x, y) && layout.roadAt(x, y) != null;
                }
            }
            assertTrue(touches, plot.toString());
        }
    }

    @Test
    void theRoadsAreOneNetworkJoinedToTheTown() {
        RuralLayout layout = layout();
        boolean[][] seen = new boolean[W][H];
        Deque<int[]> open = new ArrayDeque<>();
        Road farm = layout.roads().stream().filter(r -> r.grade() == Grade.FARM).findFirst().orElseThrow();
        open.add(new int[]{farm.x0(), farm.y0()});
        seen[farm.x0()][farm.y0()] = true;
        while (!open.isEmpty()) {
            int[] c = open.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + d[0], y = c[1] + d[1];
                if (!layout.inCountry(x, y) || seen[x][y] || layout.roadAt(x, y) == null) continue;
                seen[x][y] = true;
                open.add(new int[]{x, y});
            }
        }
        for (Road road : layout.roads()) assertTrue(seen[road.x0()][road.y0()], road.toString());
    }

    @Test
    void onlyCountryAndFarmRoadsCarryTraffic() {
        RuralLayout layout = layout();
        Road track = layout.roads().stream().filter(r -> r.grade() == Grade.TRACK).findFirst().orElse(null);
        assertNotNull(track);
        assertFalse(layout.carriesTraffic(track.x0(), track.y0() + 1));
    }

    @Test
    void aFieldAgainstAnotherEdgeIsSurveyedTheSameWay() {
        FieldOfFire west = new FieldOfFire(30, 60, 40);
        int depth = west.countryDepth(W, H) + 1;
        boolean[][] road = new boolean[W][H];
        for (int x = depth; x < W; x++) for (int y = 58; y <= 62; y++) road[x][y] = true;
        RuralLayout layout = RuralLayout.plan(W, H, west, road, 3L);
        Road country = layout.roads().stream().filter(r -> r.grade() == Grade.COUNTRY).findFirst().orElseThrow();
        assertTrue(country.alongX());
        assertEquals(58, country.y0());
        assertEquals(0, country.x0());
        assertTrue(layout.inCountry(0, 0));
        assertFalse(layout.inCountry(depth, 0));
    }

    @Test
    void farmsteadsStandWellClearOfTheLandingAndFaceARoad() {
        int width = 400, height = 120;
        FieldOfFire field = new FieldOfFire(200, 30, 40);
        int depth = field.countryDepth(width, height) + 1;
        boolean[][] road = new boolean[width][height];
        for (int y = depth; y < height; y++) for (int x = 198; x <= 202; x++) road[x][y] = true;
        RuralLayout layout = RuralLayout.plan(width, height, field, road, 11L);
        assertFalse(layout.farmsteads().isEmpty());
        for (Farmstead farm : layout.farmsteads()) {
            for (int x = farm.yardX0(); x <= farm.yardX1(); x++) {
                for (int y = farm.yardY0(); y <= farm.yardY1(); y++) {
                    assertFalse(field.contains(x, y), farm + " in the field at " + x + "," + y);
                    assertTrue(layout.plotAt(x, y) != null, farm + " off its plot at " + x + "," + y);
                }
            }
            // North edge: doors face south (rising y, towards town), onto the road past the yard.
            assertEquals(2, farm.turns());
            assertNotNull(layout.roadAt(farm.yardX0(), farm.yardY1() + 1), farm.toString());
            assertTrue(farm.houseX() >= farm.yardX0() && farm.barnX() + RuralLayout.BARN_ALONG - 1 <= farm.yardX1());
            assertTrue(farm.houseY() >= farm.yardY0()
                    && farm.houseY() + RuralLayout.HOUSE_DEEP - 1 <= farm.yardY1() - 2, farm.toString());
        }
    }

    /** A country against the south edge with one plot x 0..{@code plotX1} and a farm in its west corner. */
    private static RuralLayout oneFarm(int plotX1) {
        Plot plot = new Plot(0, 0, plotX1, 59, Use.GRAIN, true, false);
        Farmstead farm = new Farmstead(10, 46, 38, 59, 12, 50, 24, 49, 2);
        return new RuralLayout(W, H, 2, 60, null, List.of(), List.of(plot), List.of(farm));
    }

    /** A farm a lane's track crosses steps aside along its plot, buildings and all, to the nearest clear place. */
    @Test
    void aFarmStepsAsideOffATrackCutAcrossIt() {
        RuralLayout moved = oneFarm(99).farmsKeepingOff((x, y) -> x >= 20 && x <= 22);
        assertEquals(1, moved.farmsteads().size());
        Farmstead farm = moved.farmsteads().get(0);
        assertEquals(23, farm.yardX0(), "the nearest place clear of the track, east of it");
        assertEquals(46, farm.yardY0(), "it keeps its depth, facing the same road");
        assertEquals(25, farm.houseX());
        assertEquals(37, farm.barnX());
    }

    /** A farm with nowhere clear in its plot is not built; one nothing touches stays where it is. */
    @Test
    void aFarmWithNoClearPlaceInItsPlotIsNotBuilt() {
        assertTrue(oneFarm(40).farmsKeepingOff((x, y) -> x >= 20 && x <= 22).farmsteads().isEmpty());
        RuralLayout untouched = oneFarm(99);
        assertEquals(untouched, untouched.farmsKeepingOff((x, y) -> x > 150));
    }
}
