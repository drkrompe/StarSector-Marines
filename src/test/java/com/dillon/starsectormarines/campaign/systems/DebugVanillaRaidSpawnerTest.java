package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.systems.DebugVanillaRaidSpawnerTargets.Candidate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one part of the debug raid spawners a test can reach. Everything else in them is
 * live sector objects — intel managers, routes, and markets — which is why the ranking
 * was pulled out as a list of (id, same-system, distance) in the first place.
 */
class DebugVanillaRaidSpawnerTest {

    @Test
    void picksNothingFromNoCandidates() {
        assertEquals(-1, DebugVanillaRaidSpawnerTargets.pickNearest(new ArrayList<Candidate>()));
        assertEquals(-1, DebugVanillaRaidSpawnerTargets.pickNearest(null));
    }

    @Test
    void picksTheNearest() {
        List<Candidate> candidates = Arrays.asList(
                new Candidate("far", false, 900f),
                new Candidate("near", false, 100f),
                new Candidate("middling", false, 400f));

        assertEquals(1, DebugVanillaRaidSpawnerTargets.pickNearest(candidates));
    }

    @Test
    void sameSystemBeatsNearer() {
        List<Candidate> candidates = Arrays.asList(
                new Candidate("nearer", false, 10f),
                new Candidate("here", true, 5000f));

        assertEquals(1, DebugVanillaRaidSpawnerTargets.pickNearest(candidates));
    }

    @Test
    void breaksTiesByIdSoIterationOrderDoesNotDecide() {
        List<Candidate> forward = Arrays.asList(
                new Candidate("beta", true, 100f),
                new Candidate("alpha", true, 100f));
        List<Candidate> reversed = Arrays.asList(
                new Candidate("alpha", true, 100f),
                new Candidate("beta", true, 100f));

        assertEquals("alpha", forward.get(
                DebugVanillaRaidSpawnerTargets.pickNearest(forward)).marketId);
        assertEquals("alpha", reversed.get(
                DebugVanillaRaidSpawnerTargets.pickNearest(reversed)).marketId);
    }

    @Test
    void skipsCandidatesWithNoMarket() {
        List<Candidate> candidates = Arrays.asList(
                null,
                new Candidate(null, true, 1f),
                new Candidate("real", false, 900f));

        assertEquals(2, DebugVanillaRaidSpawnerTargets.pickNearest(candidates));
    }
}
