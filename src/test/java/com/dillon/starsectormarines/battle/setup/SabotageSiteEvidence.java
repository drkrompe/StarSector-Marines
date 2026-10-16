package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evidence, not a test: every supported production map scale, at several seeds,
 * offers the Sabotage selector three distinct, reachable, separated sites.
 *
 * <p>It generates two maps per scale and seed (the second to compare for
 * determinism), which cost ~17 s inside {@code :test}. The selection rules
 * themselves are pinned on hand-laid maps by {@code SabotageSiteLayoutTest}; this
 * asks the different question of whether the generator keeps supplying maps the
 * selector can succeed on. Run with {@code gradlew.bat generatedSpaceEvidence}.
 */
@Tag("generated-space-evidence")
class SabotageSiteEvidence {

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
                    assertTrue(cells.add(((long) site.cellX() << 32) | (site.cellY() & 0xffffffffL)),
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
                List<SabotageSiteLayout.Site> sites = first.sites();
                for (int i = 0; i < sites.size(); i++) {
                    for (int j = i + 1; j < sites.size(); j++) {
                        int dx = sites.get(i).cellX() - sites.get(j).cellX();
                        int dy = sites.get(i).cellY() - sites.get(j).cellY();
                        assertTrue(dx * dx + dy * dy >= MINIMUM_SEPARATION * MINIMUM_SEPARATION,
                                "site cells must satisfy minimum separation");
                    }
                }
            }
        }
    }

    private static MapResult generated(MapScale scale, long seed) {
        return new BspCityGenerator().generate(scale.width, scale.height, seed,
                null, TargetProfile.NEUTRAL);
    }
}
