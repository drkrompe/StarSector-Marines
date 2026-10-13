package com.dillon.starsectormarines.battle.infantry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SquadPodCadenceTest {
    @Test void firstShotIsImmediateAndSuccessfulShotEnforcesAuthoredSpacing() {
        SquadPodCadence cadence = new SquadPodCadence();
        assertTrue(cadence.ready(0));
        cadence.committed(10L, 7, 1.5f, 1f / 30f);
        assertFalse(cadence.ready(7));
        assertFalse(cadence.ready(51));
        assertTrue(cadence.ready(52));
    }

    @Test void handoffUsesStableIdentityRegardlessOfCandidateEnumeration() {
        SquadPodCadence cadence = new SquadPodCadence();
        assertEquals(10L, choose(cadence, 30, 10, 20));
        cadence.committed(10L, 0, 1f, 1f / 30f);
        assertEquals(20L, choose(cadence, 30, 10, 20));
        cadence.committed(20L, 30, 1f, 1f / 30f);
        assertEquals(30L, choose(cadence, 20, 30, 10));
        cadence.committed(30L, 60, 1f, 1f / 30f);
        assertEquals(10L, choose(cadence, 20, 30, 10));
    }

    @Test void unavailableSuccessorIsSkippedAndEmptyOpportunityDoesNotConsumeCadence() {
        SquadPodCadence cadence = new SquadPodCadence();
        cadence.committed(10L, 0, 1f, 1f / 30f);
        assertEquals(30L, choose(cadence, 10, 30));
        assertEquals(0L, choose(cadence));
        assertTrue(cadence.ready(30));
        assertEquals(20L, choose(cadence, 20, 30));
    }

    @Test void squadsHaveIndependentClocksAndEvenSubTickSpacingExcludesSameTick() {
        SquadPodCadence first = new SquadPodCadence();
        SquadPodCadence second = new SquadPodCadence();
        first.committed(10L, 5, 0.001f, 1f / 30f);
        assertFalse(first.ready(5));
        assertTrue(first.ready(6));
        assertTrue(second.ready(5));
    }

    private static long choose(SquadPodCadence cadence, long... eligible) {
        long selected = 0L;
        for (long candidate : eligible) {
            if (cadence.prefers(candidate, selected)) selected = candidate;
        }
        return selected;
    }
}
