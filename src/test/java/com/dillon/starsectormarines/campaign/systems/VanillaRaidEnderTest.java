package com.dillon.starsectormarines.campaign.systems;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaRaidEnderTest {

    private static final Set<String> NAMED = Set.of("jangala", "asharu");

    @Test
    void endsARaidThatNamesTheDefendedMarket() {
        assertTrue(VanillaRaidEnder.shouldEnd(
                "jangala", "pirates", "pirates", false, NAMED, false, false));
    }

    @Test
    void leavesAnotherFactionsRaidAlone() {
        assertFalse(VanillaRaidEnder.shouldEnd(
                "jangala", "pirates", "hegemony", false, NAMED, false, false));
    }

    @Test
    void leavesARaidThatNamesAnotherMarketAlone() {
        assertFalse(VanillaRaidEnder.shouldEnd(
                "kazeron", "pirates", "pirates", false, NAMED, false, false));
    }

    /** Ending is idempotent: a group already ending or a raid already failed is left alone. */
    @Test
    void leavesARaidThatIsAlreadyOverAlone() {
        assertFalse(VanillaRaidEnder.shouldEnd(
                "jangala", "pirates", "pirates", true, NAMED, false, false));
    }

    /**
     * The fleet-group shape may take any hostile market rather than only its named
     * targets, so an unnamed market still matches when its owner is hostile to the raider.
     */
    @Test
    void anyHostileMarketMatchesOnlyWhenTheMarketIsHostile() {
        List<String> noNames = Collections.emptyList();
        assertTrue(VanillaRaidEnder.shouldEnd(
                "jangala", "pirates", "pirates", false, noNames, true, true));
        assertFalse(VanillaRaidEnder.shouldEnd(
                "jangala", "pirates", "pirates", false, noNames, true, false));
        assertFalse(VanillaRaidEnder.shouldEnd(
                "jangala", "pirates", "pirates", false, noNames, false, true));
    }

    @Test
    void missingIdentityNeverEndsAnything() {
        assertFalse(VanillaRaidEnder.shouldEnd(
                null, "pirates", "pirates", false, NAMED, false, false));
        assertFalse(VanillaRaidEnder.shouldEnd(
                "jangala", null, null, false, NAMED, false, false));
        assertFalse(VanillaRaidEnder.shouldEnd(
                "jangala", "pirates", null, false, NAMED, false, false));
    }
}
