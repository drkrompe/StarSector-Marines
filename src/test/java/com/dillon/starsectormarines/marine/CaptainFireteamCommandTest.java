package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptainFireteamCommandTest {

    @Test
    void rankCapacityRoundsUpToWholeFireteams() {
        int[] expected = {1, 2, 7, 14, 27, 54, 107, 214};

        for (Rank rank : Rank.values()) {
            assertEquals(expected[rank.ordinal()], rank.fireteamCap());
        }
    }

    @Test
    void assignmentRejectsReserveAndReassignsAtomically() {
        MarineRoster roster = rosterWithFireteams(7);
        MarineCaptain first = captain("First", Rank.PRIVATE);
        MarineCaptain second = captain("Second", Rank.PRIVATE);
        roster.add(first);
        roster.add(second);
        MarineSquad line = roster.squads().get(0);

        assertTrue(roster.assignCaptainToSquad(first.id(), line.id()));
        assertFalse(roster.assignCaptainToSquad(first.id(), roster.reserveSquad().id()));
        assertTrue(roster.assignCaptainToSquad(second.id(), line.id()));

        assertSame(second, roster.captainForSquad(line.id()));
        assertTrue(roster.squadsCommandedBy(first.id()).isEmpty());
        assertEquals(1, roster.squadsCommandedBy(second.id()).size());
    }

    @Test
    void casualtiesDoNotCreateAdditionalCommandSlots() {
        MarineRoster roster = rosterWithFireteams(13);
        MarineCaptain captain = captain("Corporal", Rank.CORPORAL);
        roster.add(captain);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);
        MarineSquad third = roster.squads().get(2);
        assertTrue(roster.assignCaptainToSquad(captain.id(), first.id()));
        assertTrue(roster.assignCaptainToSquad(captain.id(), second.id()));

        Map<String, MarineSoldierStatus> casualties = new HashMap<>();
        for (MarineSoldier soldier : roster.squadMembers(first)) {
            casualties.put(soldier.id(), MarineSoldierStatus.KIA);
        }
        roster.applySoldierOutcome(casualties, 0, 0f, 1f);

        assertFalse(roster.assignCaptainToSquad(captain.id(), third.id()));
        assertEquals(2, roster.squadsCommandedBy(captain.id()).size());
    }

    @Test
    void unavailableCaptainRetainsHistoryButCannotReceiveAnotherTeam() {
        MarineRoster roster = rosterWithFireteams(7);
        MarineCaptain captain = captain("Wounded", Rank.CORPORAL);
        roster.add(captain);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);
        assertTrue(roster.assignCaptainToSquad(captain.id(), first.id()));

        captain.setStatus(Status.INJURED);

        assertSame(captain, roster.captainForSquad(first.id()));
        assertFalse(roster.assignCaptainToSquad(captain.id(), second.id()));
    }

    @Test
    void formationPickerSkipsUnavailableAndFullCaptains() {
        MarineRoster roster = rosterWithFireteams(13);
        MarineCaptain full = captain("Full", Rank.PRIVATE);
        MarineCaptain injured = captain("Injured", Rank.GENERAL);
        MarineCaptain available = captain("Available", Rank.CORPORAL);
        roster.add(full);
        roster.add(injured);
        roster.add(available);
        injured.setStatus(Status.INJURED);
        assertTrue(roster.assignCaptainToSquad(full.id(), roster.squads().get(0).id()));

        MarineSquad target = roster.squads().get(2);

        assertSame(available, roster.nextAssignableCaptain(target.id()));
        assertTrue(roster.assignCaptainToSquad(available.id(), target.id()));
        assertNull(roster.nextAssignableCaptain(target.id()));
    }

    @Test
    void removingCaptainClearsTheirHomeFormation() {
        MarineRoster roster = rosterWithFireteams(1);
        MarineCaptain captain = captain("Departing", Rank.PRIVATE);
        roster.add(captain);
        MarineSquad squad = roster.squads().get(0);
        assertTrue(roster.assignCaptainToSquad(captain.id(), squad.id()));

        assertTrue(roster.removeById(captain.id()));

        assertNull(squad.homeCaptainId());
        assertNull(roster.captainForSquad(squad.id()));
    }

    @Test
    void saveRepairClearsDanglingReserveAndOverCapacityBindings() throws Exception {
        MarineRoster roster = rosterWithFireteams(7);
        MarineCaptain captain = captain("Private", Rank.PRIVATE);
        roster.add(captain);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);
        MarineSquad reserve = roster.reserveSquad();
        first.setHomeCaptainId(captain.id());
        second.setHomeCaptainId(captain.id());
        reserve.setHomeCaptainId("missing-captain");

        Method readResolve = MarineRoster.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(roster);

        assertEquals(captain.id(), first.homeCaptainId());
        assertNull(second.homeCaptainId());
        assertNull(reserve.homeCaptainId());
    }

    private static MarineRoster rosterWithFireteams(int personnel) {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(personnel);
        return roster;
    }

    private static MarineCaptain captain(String name, Rank rank) {
        return new MarineCaptain(name, null, rank, 0f);
    }
}
