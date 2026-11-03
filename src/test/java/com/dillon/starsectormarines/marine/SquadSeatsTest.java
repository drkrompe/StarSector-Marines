package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A marine keeps a seat while holding it: a loss leaves it empty, and a replacement takes the earliest. */
public class SquadSeatsTest {

    private static List<String> places(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add("m" + i);
        return ids;
    }

    private static MarineSquad fullSquad(MarineRoster roster) {
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        return roster.squads().stream().filter(squad -> !squad.reserve()).findFirst().orElseThrow();
    }

    @Test
    public void aLostMarineLeavesItsSeatEmptyAndNobodyMoves() {
        Set<String> lost = Set.of("m7", "m9");
        List<String> seats = MarineRoster.seats(places(12), id -> !lost.contains(id));

        assertNull(seats.get(7));
        assertNull(seats.get(9));
        assertEquals("m8", seats.get(8));
        assertEquals("m11", seats.get(11));
    }

    @Test
    public void aReplacementTakesTheEarliestSeatLeftEmpty() {
        Set<String> lost = Set.of("m7", "m9");
        List<String> seats = MarineRoster.seats(places(13), id -> !lost.contains(id));

        assertEquals("m12", seats.get(7));
        assertNull(seats.get(9));
    }

    @Test
    public void seatsNeverOverflowTheSquad() {
        List<String> seats = MarineRoster.seats(places(14), id -> true);

        assertEquals(12, seats.size());
        assertEquals("m11", seats.get(11));
    }

    @Test
    public void aMarineFightsInTheTeamOfTheSeatItHolds() {
        MarineRoster roster = new MarineRoster();
        MarineSquad squad = fullSquad(roster);
        String behind = roster.seatedMemberIds(squad).get(8);

        roster.applySoldierOutcome(Map.of(roster.seatedMemberIds(squad).get(7), MarineSoldierStatus.KIA), 0f, 1f);

        assertEquals(2, roster.teamIndexOf(squad, behind));
        assertEquals(behind, roster.teamMemberIds(squad, 2).get(0));
        assertNull(roster.teamMemberIds(squad, 1).get(3));
    }

    @Test
    public void aReplacementKeepsItsSeatWhenAnEarlierReplacementIsLost() {
        MarineRoster roster = new MarineRoster();
        MarineSquad squad = fullSquad(roster);
        List<String> before = roster.seatedMemberIds(squad);
        roster.applySoldierOutcome(Map.of(before.get(2), MarineSoldierStatus.KIA,
                before.get(5), MarineSoldierStatus.KIA), 0f, 1f);
        roster.recruitToSquad(roster.reserveSquad().id(), 2);
        roster.fillVacanciesFromReserve(squad.id());
        String inSeatTwo = roster.seatedMemberIds(squad).get(2);
        String inSeatFive = roster.seatedMemberIds(squad).get(5);

        roster.applySoldierOutcome(Map.of(inSeatTwo, MarineSoldierStatus.KIA), 0f, 1f);

        assertNull(roster.seatedMemberIds(squad).get(2));
        assertEquals(inSeatFive, roster.seatedMemberIds(squad).get(5));
    }

    @Test
    public void aTransferLeavesItsSeatEmptyAndNobodyMoves() {
        MarineRoster roster = new MarineRoster();
        MarineSquad squad = fullSquad(roster);
        List<String> before = roster.seatedMemberIds(squad);

        roster.transferSoldier(before.get(3), roster.reserveSquad().id());

        assertNull(roster.seatedMemberIds(squad).get(3));
        assertEquals(before.get(4), roster.seatedMemberIds(squad).get(4));
    }
}
