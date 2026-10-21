package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColonyEruptionPlanTest {

    private static final int W = 400;
    private static final int H = 240;

    /** A shelter in the west, a lift in the east, a place every twelve cells. */
    private static ColonyEruptionPlan.Geometry colony(List<int[]> landings) {
        List<int[]> candidates = new ArrayList<>();
        for (int y = 6; y < H - 6; y += 12) {
            for (int x = 6; x < W - 6; x += 12) {
                candidates.add(new int[]{x, y, PointOfInterest.Kind.RESIDENTIAL.ordinal()});
            }
        }
        return new ColonyEruptionPlan.Geometry(W, H, 80, 120, 380, 120, 15,
                candidates, landings);
    }

    @Test
    void aFullColonySeatsTheWaveAndAMotherFarFromTheLift() {
        ColonyEruptionPlan plan = ColonyEruptionPlan.select(colony(List.of()),
                ColonyEruptionPlan.FIRST_WAVE_SITES, 11L);
        assertTrue(plan.isComplete());
        assertEquals(ColonyEruptionPlan.FIRST_WAVE_SITES, plan.firstWaveCount());
        ColonyEruptionPlan.Site mother = plan.mother();
        assertNotNull(mother);
        assertTrue(mother.x() < W / 4, "the mother bore came up near the lift at x=" + mother.x());
        assertEquals(ColonyEruptionPlan.Role.CORRIDOR, plan.sites().get(0).role(),
                "the cohort's walk breaks open first");
        long flanks = plan.sites().stream().filter(s -> s.role() == ColonyEruptionPlan.Role.FLANK).count();
        assertTrue(flanks >= 2, "both flanks should open, got " + flanks);
    }

    @Test
    void sitesNumberInEruptionOrderAndKeepTheirSpacingAndClearances() {
        List<int[]> landings = List.of(new int[]{200, 30});
        ColonyEruptionPlan plan = ColonyEruptionPlan.select(colony(landings),
                ColonyEruptionPlan.FIRST_WAVE_SITES, 3L);
        int spacing = ColonyEruptionPlan.siteSpacing(W, H);
        int lastDelay = -1;
        int number = 1;
        for (ColonyEruptionPlan.Site site : plan.sites()) {
            if (!site.mother()) assertEquals(number++, site.number());
            assertTrue(site.delayTicks() > lastDelay, "eruptions are staggered in order");
            lastDelay = site.delayTicks();
            assertTrue(Math.abs(site.x() - 80) + Math.abs(site.y() - 120)
                    >= ColonyEruptionPlan.shelterClearance(W, H), "a site inside the shelter clearance");
            assertTrue(Math.max(Math.abs(site.x() - 380), Math.abs(site.y() - 120))
                    > 15 + ColonyEruptionPlan.liftClearance(W, H), "a site inside the lift clearance");
            assertTrue(Math.max(Math.abs(site.x() - 200), Math.abs(site.y() - 30))
                    > ColonyEruptionPlan.landingClearance(W, H), "a site on a landing pad");
            for (ColonyEruptionPlan.Site other : plan.sites()) {
                if (other == site) continue;
                int dx = other.x() - site.x(), dy = other.y() - site.y();
                assertTrue(dx * dx + dy * dy >= spacing * spacing, "two sites crowd each other");
            }
        }
    }

    @Test
    void theSameSeedBreaksTheColonyOpenInTheSamePlaces() {
        ColonyEruptionPlan a = ColonyEruptionPlan.select(colony(List.of()), 4, 99L);
        ColonyEruptionPlan b = ColonyEruptionPlan.select(colony(List.of()), 4, 99L);
        ColonyEruptionPlan c = ColonyEruptionPlan.select(colony(List.of()), 4, 100L);
        assertEquals(a.sites(), b.sites());
        assertFalse(a.sites().equals(c.sites()), "the seed should move the first wave");
    }

    @Test
    void aColonyWithTooFewPlacesReturnsAShortPlan() {
        ColonyEruptionPlan.Geometry crowded = new ColonyEruptionPlan.Geometry(W, H,
                80, 120, 380, 120, 15,
                List.of(new int[]{230, 120, 0}, new int[]{232, 122, 0}), List.of());
        ColonyEruptionPlan plan = ColonyEruptionPlan.select(crowded, 4, 1L);
        assertFalse(plan.isComplete());
        assertEquals(1, plan.firstWaveCount(), "two neighbouring places seat one site");
    }
}
