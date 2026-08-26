package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SabotageSiteLayoutTest {

    private static final int MINIMUM_SEPARATION = 8;
    private static final long[] PRODUCTION_SEEDS = {1L, 7L, 42L, 4_096L};

    @Test
    void supportedProductionMapsProvideThreeDistinctReachableStableSites() {
        for (MapScale scale : MapScale.values()) {
            for (long seed : PRODUCTION_SEEDS) {
                MapResult firstMap = generated(scale, seed);
                MapResult secondMap = generated(scale, seed);
                List<LandingPad> firstPads = LandingPadSelector.select(
                        firstMap, 3, MINIMUM_SEPARATION);
                List<LandingPad> secondPads = LandingPadSelector.select(
                        secondMap, 3, MINIMUM_SEPARATION);
                SabotageSiteLayout first = SabotageSiteLayout.select(
                        firstMap, firstPads, MINIMUM_SEPARATION);
                SabotageSiteLayout second = SabotageSiteLayout.select(
                        secondMap, secondPads, MINIMUM_SEPARATION);

                assertEquals(first, second,
                        () -> "site selection drifted for " + scale + " seed " + seed);
                assertEquals(SabotageSiteLayout.REQUIRED_SITE_COUNT, first.sites().size());

                Set<String> ids = new HashSet<>();
                Set<Long> cells = new HashSet<>();
                for (SabotageSiteLayout.Site site : first.sites()) {
                    assertTrue(ids.add(site.id()), "site ids must be distinct");
                    assertTrue(cells.add(cellKey(site.cellX(), site.cellY())),
                            "site cells must be distinct");
                    assertTrue(firstMap.grid.isWalkable(site.cellX(), site.cellY()));
                    for (LandingPad pad : firstPads) {
                        assertTrue(GridPathfinder.findPath(firstMap.grid,
                                        pad.centerX, pad.centerY,
                                        site.cellX(), site.cellY()).length > 0,
                                () -> site.id() + " must be reachable from every LZ");
                    }
                    assertTrue(site.cellX() >= firstMap.grid.getWidth() / 2,
                            () -> site.id() + " must be in the defender half");
                }
                assertPairwiseSeparation(first.sites(), MINIMUM_SEPARATION);
            }
        }
    }

    @Test
    void malformedMapFailsClosedInsteadOfPublishingPartialLayout() {
        NavigationGrid grid = new NavigationGrid(24, 12);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        List<PointOfInterest> onlyTwo = List.of(
                poi(PointOfInterest.Kind.LABORATORY, 14, 3),
                poi(PointOfInterest.Kind.DEPOT, 20, 8));
        MapResult map = new MapResult(grid, new CellTopology(24, 12),
                1, 1, 22, 10, onlyTwo, List.<Doodad>of());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SabotageSiteLayout.select(map,
                        List.of(LandingPad.fallback(1, 1)), 2));
        assertTrue(failure.getMessage().contains("exactly 3 charge sites"));
    }

    private static MapResult generated(MapScale scale, long seed) {
        return new BspCityGenerator().generate(scale.width, scale.height, seed,
                null, TargetProfile.NEUTRAL);
    }

    private static PointOfInterest poi(PointOfInterest.Kind kind, int x, int y) {
        return new PointOfInterest(kind, x - 1, y - 1, x + 1, y + 1,
                x, y, x, y);
    }

    private static void assertPairwiseSeparation(List<SabotageSiteLayout.Site> sites,
                                                 int minimumSeparation) {
        int minimumSquared = minimumSeparation * minimumSeparation;
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                SabotageSiteLayout.Site first = sites.get(i);
                SabotageSiteLayout.Site second = sites.get(j);
                int dx = first.cellX() - second.cellX();
                int dy = first.cellY() - second.cellY();
                assertTrue(dx * dx + dy * dy >= minimumSquared,
                        "site cells must satisfy minimum separation");
            }
        }
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
