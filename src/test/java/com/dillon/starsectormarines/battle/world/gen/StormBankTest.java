package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a storm world's town banks earth against the wind ({@link StormBank}). */
class StormBankTest {

    private static final int W = 160;
    private static final int H = 120;

    /** How far in from {@code wind}'s edge a cell lies. */
    private static int in(StormWind.Side wind, int x, int y) {
        return wind.alongX() ? (wind.low() ? y : H - 1 - y) : (wind.low() ? x : W - 1 - x);
    }

    /** Ground the landscape pass leaves a ridge: everything but its reserved map edge. */
    private static boolean offTheEdge(int i) {
        int x = i % W, y = i / W, e = StormBank.EDGE_RESERVED;
        return x >= e && y >= e && x < W - e && y < H - e;
    }

    private static ElevationLayout raised(StormWind.Side wind, IntPredicate free) {
        ElevationPlan plan = RidgePlanner.plan(W, H, 1L, List.of(StormBank.band(wind, W, H)),
                i -> offTheEdge(i) && free.test(i), layout -> true);
        assertNotNull(plan, wind + ": the bank is raised");
        return ElevationLayout.resolve(plan);
    }

    @Test
    void theBankStandsInsideTheWindwardEdgeReservationAndIsClimbedFromTheTown() {
        for (StormWind.Side wind : StormWind.Side.values()) {
            ElevationPlan plan = RidgePlanner.plan(W, H, 1L, List.of(StormBank.band(wind, W, H)),
                    StormBankTest::offTheEdge, layout -> true);
            assertNotNull(plan, wind.name());
            ElevationLayout layout = ElevationLayout.resolve(plan);
            int reach = StormBank.reach(wind, W, H), raisedCells = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                if (!layout.touched(x, y)) continue;
                raisedCells++;
                assertTrue(in(wind, x, y) < reach - StormBank.STANDOFF, wind + ": raised ground within the bank's reach");
            }
            assertTrue(raisedCells > 0, wind + " raises ground");
            for (ElevationPlan.Ramp ramp : plan.ramps()) {
                assertTrue(in(wind, (int) ramp.foot().x(), (int) ramp.foot().y())
                        > in(wind, (int) ramp.head().x(), (int) ramp.head().y()), wind + ": climbed from the lee");
            }
        }
    }

    @Test
    void everyRoadThroughTheBankIsAPassAndNothingIsSealedOff() {
        StormWind.Side wind = StormWind.Side.EAST;
        // Two roads run off the map through the bank's ground.
        IntPredicate road = i -> { int y = i / W; return y >= 20 && y <= 23 || y >= 50 && y <= 52; };
        ElevationLayout layout = raised(wind, i -> !road.test(i));
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (road.test(y * W + x)) assertFalse(layout.touched(x, y), "road at " + x + "," + y + " is a pass");
        }
        // Every cell the bank leaves at ground level is one ground: the strip behind it reaches the town.
        boolean[] seen = new boolean[W * H];
        ArrayDeque<Integer> open = new ArrayDeque<>(List.of((H / 2) * W + W / 2));
        seen[(H / 2) * W + W / 2] = true;
        while (!open.isEmpty()) {
            int i = open.pop(), x = i % W, y = i / W;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] s : steps) {
                int nx = x + s[0], ny = y + s[1];
                if (nx < 0 || ny < 0 || nx >= W || ny >= H || seen[ny * W + nx] || layout.touched(nx, ny)) continue;
                seen[ny * W + nx] = true;
                open.push(ny * W + nx);
            }
        }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (!layout.touched(x, y)) assertTrue(seen[y * W + x], "ground at " + x + "," + y + " is cut off");
        }
    }

    @Test
    void aPlaceSetLeewardStandsClearOfEverythingTheBankHolds() {
        MapPlacement whole = new MapPlacement(0f, 0f, 1f, 1f);
        for (StormWind.Side wind : StormWind.Side.values()) {
            boolean[][] held = RidgePlanner.heldGround(List.of(StormBank.band(wind, W, H)), W, H, StormBank.HELD_MARGIN);
            MapPlacement lee = StormBank.leeward(whole, wind, W, H);
            for (int y = Math.round(lee.y0() * H); y < Math.round(lee.y1() * H); y++) {
                for (int x = Math.round(lee.x0() * W); x < Math.round(lee.x1() * W); x++) {
                    assertFalse(held[x][y], wind + ": the town's region reaches the bank at " + x + "," + y);
                }
            }
            // Regions keep their order: the far side of the map is still the far side.
            MapPlacement west = StormBank.leeward(new MapPlacement(0f, 0f, .3f, 1f), wind, W, H);
            MapPlacement east = StormBank.leeward(new MapPlacement(.7f, 0f, 1f, 1f), wind, W, H);
            assertTrue(west.x1() < east.x0(), wind + " keeps the regions in order");
        }
    }

    @Test
    void theCompanyNeverLandsOnTheWindwardSide() {
        for (StormWind.Side wind : StormWind.Side.values()) {
            for (boolean drawn : new boolean[] {false, true}) {
                StormWind.Side landing = StormBank.mirrorX(drawn, wind) ? StormWind.Side.EAST : StormWind.Side.WEST;
                assertNotEquals(wind, landing, "drawn " + drawn);
            }
        }
        assertTrue(StormBank.mirrorX(true, StormWind.Side.NORTH), "a wind across the landing leaves the draw");
        assertFalse(StormBank.mirrorX(false, StormWind.Side.SOUTH));
    }

    @Test
    void anApproachStoppedAtTheBanksGroundLeavesEveryCellTheBankHoldsFree() {
        for (StormWind.Side wind : StormWind.Side.values()) {
            boolean[][] ground = StormBank.ground(wind, W, H);
            boolean[][] held = RidgePlanner.heldGround(List.of(StormBank.band(wind, W, H)), W, H, StormBank.HELD_MARGIN);
            int depth = StormBank.depth(wind, W, H);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                if (held[x][y]) assertTrue(ground[x][y], wind + ": held at " + x + "," + y + " is the bank's");
                assertEquals(in(wind, x, y) < depth, ground[x][y], wind + ": the town's ground at " + x + "," + y);
            }
        }
    }

    @Test
    void onlyADesertLikeStormWorldBanks() {
        Climate storms = new Climate(EnumSet.of(Climate.Hazard.STORMS));
        assertTrue(StormBank.raises(profile(SurfacePalette.ARID, storms)));
        assertTrue(StormBank.raises(profile(SurfacePalette.ROCK, storms)));
        assertFalse(StormBank.raises(profile(SurfacePalette.FROZEN, storms)), "blizzards want fences, not earth");
        assertFalse(StormBank.raises(profile(SurfacePalette.VERDANT, storms)));
        assertFalse(StormBank.raises(profile(SurfacePalette.ARID, Climate.NONE)), "no storms, no bank");
        assertEquals(false, StormBank.raises(null));
    }

    private static TargetProfile profile(SurfacePalette surface, Climate climate) {
        return new TargetProfile(4, 5, 3, 2, "independent", EnumSet.of(EconomicFunction.HABITATION), surface,
                SettlementLink.ROAD).withClimate(climate);
    }

    @Test
    void nothingGrowsWindwardOfTheBankButItsPassesRunToTheEdge() {
        for (StormWind.Side wind : StormWind.Side.values()) {
            int length = wind.alongX() ? W : H, pass = length / 2;
            List<RidgePlanner.Band> bands = StormBank.bands(wind, W, H, List.of(pass));
            assertEquals(2, bands.size(), wind + ": the pass splits the bank");
            boolean[][] held = RidgePlanner.heldGround(bands, W, H, StormBank.HELD_MARGIN);
            List<Landscape.Track> kept = StormBank.windward(wind, W, H, List.of(pass));
            for (int a = 0; a < length; a++) {
                boolean inPass = Math.abs(a - pass) <= StormBank.OPEN_HALF;
                // Walk in from the edge past the bank's lee apron, where a road's reservation would refuse the
                // stretch: every cell is kept or held, except in the pass.
                int lee = StormBank.depth(wind, W, H) + StormBank.LEE_KEPT;
                for (int d = 0; d < lee; d++) {
                    int x = wind.alongX() ? a : wind.low() ? d : W - 1 - d;
                    int y = wind.alongX() ? (wind.low() ? d : H - 1 - d) : a;
                    boolean covered = held[x][y];
                    for (Landscape.Track t : kept) covered |= t.contains(x, y);
                    if (inPass) assertFalse(covered, wind + ": the pass is open at " + x + "," + y);
                    else assertTrue(covered, wind + ": windward ground grows nothing at " + x + "," + y);
                }
            }
        }
    }
}
