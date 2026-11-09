package com.dillon.starsectormarines.battle.ruins;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultAccessTest {

    @Test
    void theSameSeedDrawsTheSameAccess() {
        for (long seed = 0; seed < 50; seed++) {
            assertEquals(VaultAccess.draw(seed, true, true), VaultAccess.draw(seed, true, true));
        }
    }

    @Test
    void sitesDrawEveryVariantTheirGroundAllows() {
        Set<VaultAccess> drawn = EnumSet.noneOf(VaultAccess.class);
        for (long seed = 0; seed < 200; seed++) drawn.add(VaultAccess.draw(seed, true, true));
        assertEquals(EnumSet.of(VaultAccess.TWO_KEYS, VaultAccess.RECORDS_CODE, VaultAccess.CROSSED_FEED), drawn);
    }

    @Test
    void groundWithoutRecordsOrACrossingNarrowsThePool() {
        for (long seed = 0; seed < 100; seed++) {
            assertFalse(VaultAccess.draw(seed, false, true).keypad(), "no records, no keypad");
            assertTrue(VaultAccess.draw(seed, true, false) != VaultAccess.CROSSED_FEED, "nothing to cross");
            assertEquals(VaultAccess.POWER_ONLY, VaultAccess.draw(seed, false, false));
        }
    }
}
