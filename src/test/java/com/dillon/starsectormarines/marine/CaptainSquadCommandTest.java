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

class CaptainSquadCommandTest {

    /** One line squad per twelve personnel; the roster grows no reserve until asked. */
    private static final int SQUADS_4 = 4 * MarineSquad.CAPACITY;
    private static final int SQUADS_2 = 2 * MarineSquad.CAPACITY;

    @Test
    void rankCapsCommandInWholeSquads() {
        int[] expected = {3, 6, 10, 16, 24};

        for (Rank rank : Rank.values()) {
            assertEquals(expected[rank.ordinal()], rank.squadCommandCap());
            assertEquals(expected[rank.ordinal()] * MarineSquad.CAPACITY,
                    rank.marineCommandCap());
        }
    }

    @Test
    void onlyTheTopOfTheLadderIsTerminal() {
        for (Rank rank : Rank.values()) {
            assertEquals(rank == Rank.COLONEL, rank.isTerminal());
            assertEquals(rank.isTerminal(), rank.promote() == rank);
        }
    }

    @Test
    void assignmentRejectsReserveAndReassignsAtomically() {
        MarineRoster roster = rosterWithSquads(MarineSquad.CAPACITY);
        MarineCaptain first = captain("First", Rank.LIEUTENANT);
        MarineCaptain second = captain("Second", Rank.LIEUTENANT);
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
        MarineRoster roster = rosterWithSquads(SQUADS_4);
        MarineCaptain captain = captain("Lieutenant", Rank.LIEUTENANT);
        roster.add(captain);
        for (int i = 0; i < Rank.LIEUTENANT.squadCommandCap(); i++) {
            assertTrue(roster.assignCaptainToSquad(captain.id(), roster.squads().get(i).id()));
        }

        Map<String, MarineSoldierStatus> casualties = new HashMap<>();
        for (MarineSoldier soldier : roster.squadMembers(roster.squads().get(0))) {
            casualties.put(soldier.id(), MarineSoldierStatus.KIA);
        }
        roster.applySoldierOutcome(casualties, 0, 0f, 1f);

        MarineSquad beyondCap = roster.squads().get(Rank.LIEUTENANT.squadCommandCap());
        assertFalse(roster.assignCaptainToSquad(captain.id(), beyondCap.id()));
        assertEquals(Rank.LIEUTENANT.squadCommandCap(),
                roster.squadsCommandedBy(captain.id()).size());
    }

    @Test
    void unavailableCaptainRetainsHistoryButCannotReceiveAnotherSquad() {
        MarineRoster roster = rosterWithSquads(SQUADS_2);
        MarineCaptain captain = captain("Wounded", Rank.CAPTAIN);
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
        MarineRoster roster = rosterWithSquads(SQUADS_4);
        MarineCaptain full = captain("Full", Rank.LIEUTENANT);
        MarineCaptain injured = captain("Injured", Rank.COLONEL);
        MarineCaptain available = captain("Available", Rank.CAPTAIN);
        roster.add(full);
        roster.add(injured);
        roster.add(available);
        injured.setStatus(Status.INJURED);
        for (int i = 0; i < Rank.LIEUTENANT.squadCommandCap(); i++) {
            assertTrue(roster.assignCaptainToSquad(full.id(), roster.squads().get(i).id()));
        }

        MarineSquad target = roster.squads().get(Rank.LIEUTENANT.squadCommandCap());

        assertSame(available, roster.nextAssignableCaptain(target.id()));
        assertTrue(roster.assignCaptainToSquad(available.id(), target.id()));
        assertNull(roster.nextAssignableCaptain(target.id()));
    }

    @Test
    void removingCaptainClearsTheirHomeFormation() {
        MarineRoster roster = rosterWithSquads(1);
        MarineCaptain captain = captain("Departing", Rank.LIEUTENANT);
        roster.add(captain);
        MarineSquad squad = roster.squads().get(0);
        assertTrue(roster.assignCaptainToSquad(captain.id(), squad.id()));

        assertTrue(roster.removeById(captain.id()));

        assertNull(squad.homeCaptainId());
        assertNull(roster.captainForSquad(squad.id()));
    }

    @Test
    void saveRepairClearsDanglingReserveAndOverCapacityBindings() throws Exception {
        MarineRoster roster = rosterWithSquads(SQUADS_4);
        MarineCaptain captain = captain("Lieutenant", Rank.LIEUTENANT);
        roster.add(captain);
        int cap = Rank.LIEUTENANT.squadCommandCap();
        for (int i = 0; i <= cap; i++) {
            roster.squads().get(i).setHomeCaptainId(captain.id());
        }
        MarineSquad reserve = roster.reserveSquad();
        reserve.setHomeCaptainId("missing-captain");

        Method readResolve = MarineRoster.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(roster);

        for (int i = 0; i < cap; i++) {
            assertEquals(captain.id(), roster.squads().get(i).homeCaptainId());
        }
        assertNull(roster.squads().get(cap).homeCaptainId());
        assertNull(reserve.homeCaptainId());
    }

    private static MarineRoster rosterWithSquads(int personnel) {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(personnel);
        return roster;
    }

    private static MarineCaptain captain(String name, Rank rank) {
        return new MarineCaptain(name, null, rank, 0f);
    }
}
