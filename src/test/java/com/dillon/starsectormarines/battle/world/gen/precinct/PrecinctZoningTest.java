package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The district map, asked per precinct.
 *
 * <p>Pinned on the map itself rather than through the generator, because the
 * law is about a lookup: a cell a precinct claims answers from that precinct's
 * character, and a cell nobody claims answers exactly as it did before anything
 * was laid over it. Single-theme characters make both readable without
 * statistics.
 */
class PrecinctZoningTest {

    /** Six blocks by four, so a claim can cover several and leave several. */
    private static final int W = 120;
    private static final int H = 80;

    private static PrecinctCharacter only(MapDistrictTheme theme) {
        return new PrecinctCharacter("only " + theme, Map.of(theme, 1), null);
    }

    /** West half to precinct 0, the rest unclaimed. */
    private static int[][] westClaim() {
        int[][] claim = new int[W][H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                claim[x][y] = x < W / 2 ? 0 : GrownTrunkPlan.UNOWNED;
            }
        }
        return claim;
    }

    /** West half to precinct 0, east half to precinct 1. */
    private static int[][] splitClaim() {
        int[][] claim = new int[W][H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                claim[x][y] = x < W / 2 ? 0 : 1;
            }
        }
        return claim;
    }

    private static Precinct west(PrecinctCharacter character) {
        return Precinct.settlement("west", 30, 40, GrownTrunkPlan.Profile.hamlet(), character);
    }

    private static Precinct east(PrecinctCharacter character) {
        return Precinct.settlement("east", 90, 40, GrownTrunkPlan.Profile.hamlet(), character);
    }

    /** A place's ground reads its own character and nothing else. */
    @Test
    void aPlacesGroundReadsItsOwnCharacter() {
        DistrictMap map = new DistrictMap(W, H, new Random(1L));
        PrecinctZoning.apply(map, PrecinctPlan.authored(
                List.of(west(only(MapDistrictTheme.INDUSTRIAL)))), westClaim(), new Random(2L));
        for (int x = 0; x < W / 2; x++) {
            for (int y = 0; y < H; y += 7) {
                assertEquals(MapDistrictTheme.INDUSTRIAL, map.themeAt(x, y),
                        "a works precinct read " + map.themeAt(x, y) + " at " + x + "," + y);
            }
        }
    }

    /** Ground nobody claims is themed exactly as it was before anything was laid over it. */
    @Test
    void hinterlandKeepsTheMapWideAnswer() {
        DistrictMap plain = new DistrictMap(W, H, new Random(1L));
        DistrictMap layered = new DistrictMap(W, H, new Random(1L));
        PrecinctZoning.apply(layered, PrecinctPlan.authored(
                List.of(west(only(MapDistrictTheme.INDUSTRIAL)))), westClaim(), new Random(2L));
        for (int x = W / 2; x < W; x++) {
            for (int y = 0; y < H; y += 7) {
                assertEquals(plain.themeAt(x, y), layered.themeAt(x, y),
                        "hinterland at " + x + "," + y + " changed under a layer it is not in");
            }
        }
    }

    /** A centre lands on the seed's own block, and only there. */
    @Test
    void aCentreLandsOnTheSeed() {
        PrecinctCharacter homesWithACore = new PrecinctCharacter("core",
                Map.of(MapDistrictTheme.RESIDENTIAL, 1), MapDistrictTheme.CIVIC);
        DistrictMap map = new DistrictMap(W, H, new Random(1L));
        Precinct west = west(homesWithACore);
        PrecinctZoning.apply(map, PrecinctPlan.authored(List.of(west)), westClaim(),
                new Random(2L));
        assertEquals(MapDistrictTheme.CIVIC, map.themeAt(west.seedX(), west.seedY()));
        int seedDx = map.districtX(west.seedX());
        int seedDy = map.districtY(west.seedY());
        for (int x = 0; x < W / 2; x++) {
            for (int y = 0; y < H; y += 7) {
                if (map.districtX(x) == seedDx && map.districtY(y) == seedDy) continue;
                assertEquals(MapDistrictTheme.RESIDENTIAL, map.themeAt(x, y),
                        "a second centre at " + x + "," + y);
            }
        }
    }

    /** Smoothing clusters within a place; it never carries one place's theme into the next. */
    @Test
    void twoPlacesDoNotBleedIntoEachOther() {
        for (long seed = 1; seed <= 20; seed++) {
            DistrictMap map = new DistrictMap(W, H, new Random(seed));
            PrecinctZoning.apply(map, PrecinctPlan.authored(List.of(
                    west(only(MapDistrictTheme.INDUSTRIAL)),
                    east(only(MapDistrictTheme.RESIDENTIAL)))), splitClaim(), new Random(seed));
            for (int y = 0; y < H; y += 7) {
                for (int x = 0; x < W; x += 5) {
                    MapDistrictTheme expected = x < W / 2
                            ? MapDistrictTheme.INDUSTRIAL : MapDistrictTheme.RESIDENTIAL;
                    assertEquals(expected, map.themeAt(x, y),
                            "seed " + seed + ": " + x + "," + y + " read a neighbour's theme");
                }
            }
        }
    }

    /** A forced block is forced on the place that holds it, not on a roll nobody reads. */
    @Test
    void aForcedThemeReachesThePlace() {
        DistrictMap map = new DistrictMap(W, H, new Random(1L));
        PrecinctZoning.apply(map, PrecinctPlan.authored(
                List.of(west(only(MapDistrictTheme.INDUSTRIAL)))), westClaim(), new Random(2L));
        map.forceThemeAt(10, 10, MapDistrictTheme.HARBOR_PORT);
        assertEquals(MapDistrictTheme.HARBOR_PORT, map.themeAt(10, 10));
        assertEquals(MapDistrictTheme.INDUSTRIAL, map.themeAt(30, 40),
                "forcing one block forced the whole place");
    }

    /** A programmed place is packed, not themed; its ground is left to the map. */
    @Test
    void aProgrammedPlaceIsNotThemed() {
        DistrictMap plain = new DistrictMap(W, H, new Random(1L));
        DistrictMap layered = new DistrictMap(W, H, new Random(1L));
        Precinct garrison = Precinct.garrison("west", 30, 40, GrownTrunkPlan.Profile.hamlet(),
                FortressProgram.garrison());
        PrecinctZoning.apply(layered, PrecinctPlan.authored(List.of(garrison)), westClaim(),
                new Random(2L));
        for (int x = 0; x < W; x += 5) {
            for (int y = 0; y < H; y += 7) {
                assertEquals(plain.themeAt(x, y), layered.themeAt(x, y));
            }
        }
    }
}
