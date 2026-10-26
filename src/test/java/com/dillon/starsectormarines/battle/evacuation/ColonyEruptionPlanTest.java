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
    void theWaveOpensAtOnceOnTheOutskirtsEitherSideOfTheFieldAndTheMotherLater() {
        // The field is the lift's, in the east; inward is west, toward the shelter.
        ColonyEruptionPlan plan = ColonyEruptionPlan.select(colony(List.of()),
                ColonyEruptionPlan.FIRST_WAVE_SITES, 11L);
        assertTrue(plan.isComplete());
        int north = 0, south = 0;
        for (ColonyEruptionPlan.Site site : plan.sites()) {
            if (site.mother()) continue;
            assertEquals(0, site.delayTicks(), "the first wave is open when the company lands");
            assertTrue(site.x() > W / 2, "a first-wave bore deep in town at x=" + site.x());
            if (site.y() < 120) north++;
            else if (site.y() > 120) south++;
        }
        assertEquals(2, north, "two on one flank of the field");
        assertEquals(2, south, "two on the other");
        ColonyEruptionPlan.Site mother = plan.mother();
        assertNotNull(mother);
        assertTrue(mother.delayTicks() > 0, "the mother comes later");
        assertTrue(Math.abs(mother.y() - 120) > 80, "the mother out on a flank's far reach, at y=" + mother.y());
    }

    @Test
    void theWalkInFromTheFieldIsLeftOpen() {
        // The field in the south; inward is north-west, to the shelter.
        float fx = 200, fy = 225, ux = 80 - fx, uy = 120 - fy, ul = (float) Math.hypot(ux, uy);
        ColonyEruptionPlan plan = ColonyEruptionPlan.select(colony(List.of(new int[]{200, 225})), 4, 3L);
        int corridor = ColonyEruptionPlan.corridor(W, H);
        for (ColonyEruptionPlan.Site site : plan.sites()) {
            if (site.mother()) continue;
            float across = Math.abs(ux * (site.y() - fy) - uy * (site.x() - fx)) / ul;
            assertTrue(across >= corridor, "a bore " + across + " cells off the walk in at " + site.x() + ","
                    + site.y());
        }
    }

    @Test
    void theMotherComesUpOnTheFlankAwayFromTheLift() {
        // The field in the south, the lift off to its east end; inward is north-west, to the shelter.
        float fx = 200, fy = 225, ux = 80 - fx, uy = 120 - fy;
        ColonyEruptionPlan plan = ColonyEruptionPlan.select(colony(List.of(new int[]{200, 225})), 4, 7L);
        ColonyEruptionPlan.Site mother = plan.mother();
        assertNotNull(mother);
        float liftAcross = ux * (120 - fy) - uy * (380 - fx);
        float motherAcross = ux * (mother.y() - fy) - uy * (mother.x() - fx);
        assertTrue(liftAcross * motherAcross < 0, "the mother came up on the lift's flank at " + mother.x() + ","
                + mother.y());
    }

    @Test
    void thePlacesNoSiteTookAreKeptForBudding() {
        ColonyEruptionPlan plan = ColonyEruptionPlan.select(colony(List.of()), 4, 5L);
        assertFalse(plan.places().isEmpty());
        for (int[] place : plan.places()) {
            for (ColonyEruptionPlan.Site site : plan.sites()) {
                assertFalse(place[0] == site.x() && place[1] == site.y(), "a site's place offered again");
            }
        }
    }

    @Test
    void sitesNumberInEruptionOrderAndKeepTheirSpacingAndClearances() {
        List<int[]> landings = List.of(new int[]{200, 30});
        ColonyEruptionPlan plan = ColonyEruptionPlan.select(colony(landings),
                ColonyEruptionPlan.FIRST_WAVE_SITES, 3L);
        int spacing = ColonyEruptionPlan.siteSpacing(W, H);
        int number = 1;
        for (ColonyEruptionPlan.Site site : plan.sites()) {
            if (!site.mother()) assertEquals(number++, site.number());
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

    @Test
    void anObjectivesSetPieceIsKeptClearOfEveryBoreAndBud() {
        ColonyEruptionPlan plan = ColonyEruptionPlan.select(colony(List.of()),
                ColonyEruptionPlan.FIRST_WAVE_SITES, 11L);
        ColonyEruptionPlan.Site first = plan.sites().get(0);
        int radius = 45;
        ColonyEruptionPlan cleared = plan.clearOf(first.x(), first.y(), radius);
        assertEquals(plan.sites().size(), cleared.sites().size(), "every site still opens");
        for (int i = 0; i < cleared.sites().size(); i++) {
            ColonyEruptionPlan.Site site = cleared.sites().get(i), was = plan.sites().get(i);
            assertTrue(Math.hypot(site.x() - first.x(), site.y() - first.y()) >= radius,
                    "bore " + site.number() + " inside the ring at " + site.x() + "," + site.y());
            assertEquals(was.number(), site.number());
            assertEquals(was.delayTicks(), site.delayTicks(), "a moved bore keeps its time");
            for (int j = 0; j < i; j++) {
                ColonyEruptionPlan.Site other = cleared.sites().get(j);
                assertTrue(Math.hypot(site.x() - other.x(), site.y() - other.y()) >= plan.spacing(),
                        "bores " + site.number() + " and " + other.number() + " crowd each other");
            }
        }
        for (int[] place : cleared.places())
            assertTrue(Math.hypot(place[0] - first.x(), place[1] - first.y()) >= radius, "a bud place in the ring");
    }
}
