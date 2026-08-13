package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedStationingBindingTest {

    @Test
    void bindingIsAtomicRankBoundedAndExcludesOrdinaryReadiness() {
        MarineRoster roster = roster(12);
        MarineCaptain captain = captain(Rank.PRIVATE);
        roster.add(captain);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);

        assertFalse(roster.bindStationing(41L, captain.id(),
                List.of(first.id(), second.id())));
        assertFalse(first.stationed());
        assertFalse(second.stationed());

        assertTrue(roster.bindStationing(41L, captain.id(), List.of(first.id())));
        assertEquals(41L, first.stationingContractId());
        assertFalse(roster.isSquadAvailable(first.id()));
        assertEquals(6, roster.lineReadySoldiers().size());
        assertEquals(List.of(first), roster.squadsStationedOn(41L));
    }

    @Test
    void bindingRejectsUnavailableTeamsAndReleaseIsReplaySafe() {
        MarineRoster roster = roster(12);
        MarineCaptain captain = captain(Rank.CORPORAL);
        roster.add(captain);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);
        assertTrue(roster.bindStationing(51L, captain.id(), List.of(first.id())));

        assertFalse(roster.bindStationing(52L, captain.id(),
                List.of(first.id(), second.id())));
        assertFalse(second.stationed());
        assertEquals(1, roster.releaseStationing(51L));
        assertEquals(0, roster.releaseStationing(51L));
        assertTrue(roster.isSquadAvailable(first.id()));
    }

    private static MarineRoster roster(int personnel) {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(personnel);
        return roster;
    }

    private static MarineCaptain captain(Rank rank) {
        return new MarineCaptain("Station Commander", null, rank, 0f);
    }
}
