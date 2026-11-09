package com.dillon.starsectormarines.campaign.ruins;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsOccupantsTest {

    private static final long SEED = 0x5EC7012L;
    private static final int SITES = 4000;

    private static Map<RuinsOccupant, Integer> census(RuinsGrade grade, boolean reservoir, Set<String> tags) {
        Map<RuinsOccupant, Integer> counts = new EnumMap<>(RuinsOccupant.class);
        for (RuinsOccupant occupant : RuinsOccupant.values()) counts.put(occupant, 0);
        for (int i = 0; i < SITES; i++) {
            counts.merge(RuinsOccupants.draw(SEED, "site_" + i, grade, reservoir, tags), 1, Integer::sum);
        }
        return counts;
    }

    private static double derelictShare(Map<RuinsOccupant, Integer> counts) {
        return (counts.get(RuinsOccupant.DOMAIN_SECURITY) + counts.get(RuinsOccupant.CONTINGENCY_SECURITY))
                / (double) SITES;
    }

    @Test
    void aSiteDrawsTheSameOccupantEveryTime() {
        for (int i = 0; i < 200; i++) {
            RuinsOccupant first = RuinsOccupants.draw(SEED, "site_" + i, RuinsGrade.SCATTERED, true, Set.of());
            assertEquals(first, RuinsOccupants.draw(SEED, "site_" + i, RuinsGrade.SCATTERED, true, Set.of()));
        }
    }

    @Test
    void onlyAScatteredSiteHoldsNobody() {
        assertTrue(census(RuinsGrade.SCATTERED, false, Set.of()).get(RuinsOccupant.NOBODY) > 0);
        for (RuinsGrade grade : List.of(RuinsGrade.WIDESPREAD, RuinsGrade.EXTENSIVE, RuinsGrade.VAST)) {
            assertEquals(0, census(grade, false, Set.of()).get(RuinsOccupant.NOBODY), grade + " held nobody");
        }
    }

    @Test
    void theRavHoldAReservoirWorldOneTimeInTwoAndNoOtherWorld() {
        double rav = census(RuinsGrade.EXTENSIVE, true, Set.of()).get(RuinsOccupant.RAV) / (double) SITES;
        assertTrue(rav > 0.45 && rav < 0.55, "Rav share " + rav);
        assertEquals(0, census(RuinsGrade.EXTENSIVE, false, Set.of()).get(RuinsOccupant.RAV));
    }

    @Test
    void aDerelictSystemDoublesTheDerelictWeightAndKeepsDomainSecurity() {
        Map<RuinsOccupant, Integer> plain = census(RuinsGrade.SCATTERED, false, Set.of());
        Map<RuinsOccupant, Integer> themed =
                census(RuinsGrade.SCATTERED, false, Set.of(RuinsOccupants.THEME_DERELICT));
        // The scattered weight w of a total t reads w/t plain and 2w/(t+w) doubled.
        double w = RuinsOccupants.derelictWeight(RuinsGrade.SCATTERED);
        double t = w + RuinsOccupants.nobodyWeight(RuinsGrade.SCATTERED)
                + RuinsOccupants.scavengerWeight(RuinsGrade.SCATTERED);
        assertTrue(Math.abs(derelictShare(plain) - w / t) < 0.03, "plain " + derelictShare(plain));
        assertTrue(Math.abs(derelictShare(themed) - 2 * w / (t + w)) < 0.03, "themed " + derelictShare(themed));
        assertEquals(0, themed.get(RuinsOccupant.CONTINGENCY_SECURITY));
        assertEquals(0, plain.get(RuinsOccupant.CONTINGENCY_SECURITY));
    }

    @Test
    void aRemnantSystemDoublesTheWeightAndHoldsOnlyContingencySecurity() {
        Map<RuinsOccupant, Integer> themed =
                census(RuinsGrade.SCATTERED, false, Set.of(RuinsOccupants.THEME_DERELICT));
        Map<RuinsOccupant, Integer> remnant =
                census(RuinsGrade.SCATTERED, false, Set.of(RuinsOccupants.THEME_REMNANT));
        assertEquals(derelictShare(themed), derelictShare(remnant), 1e-9);
        assertEquals(0, remnant.get(RuinsOccupant.DOMAIN_SECURITY));
    }

    @Test
    void noSiteNoDraw() {
        assertNull(RuinsOccupants.draw(SEED, null, RuinsGrade.VAST, false, null));
        assertNull(RuinsOccupants.draw(SEED, "site", null, false, null));
    }
}
