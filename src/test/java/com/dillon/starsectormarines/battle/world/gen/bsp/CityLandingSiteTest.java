package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The city gets a small airbase, and only one.
 *
 * <p>Two properties, and the second is the one that bites. An airbase is a
 * landmark: one per city reads as a place the city has, and several read as a
 * city made of airfields. The seed is a promotion of the single largest
 * qualifying block for exactly that reason.
 *
 * <p>The first is that it appears at all. It did not, silently, for a while:
 * the promotion ran with the leaf labelling, which happens <em>before</em>
 * compound seeding, and compound seeding takes the largest leaf it can find —
 * the same one. The block was assigned and then overwritten on every seed, with
 * nothing to see and nothing to report.
 */
class CityLandingSiteTest {

    /** Sites on one generated Conquest city. */
    private static List<PointOfInterest> sitesOn(long seed) {
        MapResult map = new BspCityGenerator().generate(
                BattleSetup.CONQUEST_GRID_W, BattleSetup.CONQUEST_GRID_H,
                seed, TraversalAxis.SOUTH_TO_NORTH);
        return map.pointsOfInterest.stream()
                .filter(poi -> poi.kind == PointOfInterest.Kind.LANDING_SITE)
                .toList();
    }

    @Test
    void aCityUsuallyHasOneLandingSiteAndNeverTwo() {
        int withASite = 0;
        for (long seed : new long[]{ 1L, 7L, 13L, 42L, 100L }) {
            List<PointOfInterest> sites = sitesOn(seed);
            assertTrue(sites.size() <= 1, "seed " + seed + ": " + sites.size()
                    + " landing sites — a landmark appears once or not at all");
            if (!sites.isEmpty()) withASite++;
        }
        assertTrue(withASite >= 3, "only " + withASite
                + " of five seeds laid down a landing site; the promotion is not"
                + " finding blocks it should fit in");
    }
}
