package com.dillon.starsectormarines.campaign.ruins;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsSurveyTest {

    private static final RuinsSurvey.Pool POOL = new RuinsSurvey.Pool("assessment", "band",
            List.of("a", "b", "c", "d", "e"));

    @Test
    void aRevisitReadsTheSame() {
        assertEquals(RuinsSurvey.read(POOL, "ossuary_market", RuinsGrade.VAST),
                RuinsSurvey.read(POOL, "ossuary_market", RuinsGrade.VAST));
    }

    @Test
    void aSiteReadsTwoOrThreeDistinctReadingsFromItsPool() {
        Set<List<String>> seen = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            List<String> readings = RuinsSurvey.read(POOL, "market_" + i, RuinsGrade.SCATTERED).readingKeys();
            assertTrue(readings.size() >= 2 && readings.size() <= 3, readings.toString());
            assertEquals(readings.size(), new HashSet<>(readings).size());
            assertTrue(POOL.readingKeys().containsAll(readings));
            seen.add(readings);
        }
        assertTrue(seen.size() > 1, "sites read alike");
    }

    @Test
    void largerRuinsReadStronger() {
        RuinsSurvey.Band previous = null;
        for (RuinsGrade grade : RuinsGrade.values()) {
            RuinsSurvey.Band band = RuinsSurvey.read(POOL, "m", grade).band();
            if (previous != null) assertTrue(band.compareTo(previous) > 0, grade + " reads no stronger");
            previous = band;
        }
    }

    @Test
    void eachOccupantReadsThroughItsOwnInstruments() {
        Set<String> assessments = new HashSet<>();
        for (RuinsOccupant occupant : RuinsOccupant.values()) {
            RuinsSurvey.Pool pool = RuinsSurvey.poolFor(occupant);
            assertTrue(pool != null && assessments.add(pool.assessmentKey()), occupant + " has no assessment of its own");
        }
    }

    @Test
    void aPoolNeedsEnoughDistinctReadings() {
        assertThrows(IllegalArgumentException.class, () -> new RuinsSurvey.Pool("a", "b", List.of("x", "y")));
        assertThrows(IllegalArgumentException.class, () -> new RuinsSurvey.Pool("a", "b", List.of("x", "x", "y")));
    }
}
