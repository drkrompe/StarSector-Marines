package com.dillon.starsectormarines.campaign.ruins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsDelveTest {

    @Test
    void largerRuinsAreNeverAShorterDelve() {
        RuinsDelve previous = null;
        for (RuinsGrade grade : RuinsGrade.values()) {
            RuinsDelve delve = RuinsDelve.of(grade);
            if (previous != null) {
                assertTrue(delve.layers() >= previous.layers(), grade + " layers");
                assertTrue(delve.breachSeconds() >= previous.breachSeconds(), grade + " breach");
                assertTrue(delve.fieldCaches() >= previous.fieldCaches(), grade + " caches");
            }
            previous = delve;
        }
    }

    @Test
    void powerNeverSlowsABreach() {
        assertThrows(IllegalArgumentException.class, () -> new RuinsDelve(1, 10f, 20f, 2));
        assertThrows(IllegalArgumentException.class, () -> new RuinsDelve(0, 30f, 10f, 2));
    }
}
