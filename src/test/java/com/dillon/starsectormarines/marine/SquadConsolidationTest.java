package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SquadConsolidationTest {
    @Test void combinesSurvivorsWithoutReplacingPeopleEquipmentOrFormationRecords() {
        var roster = new MarineRoster();
        var receiver = squad(roster, 6);
        var donor = squad(roster, 6);
        var untouched = squad(roster, 2);
        var people = List.copyOf(roster.squadMembers(donor));
        var kits = people.stream().map(MarineSoldier::armorId).toList();
        var career = donor.career();
        var plan = SquadConsolidation.preview(roster, Set.of(receiver.id()), Set.of(donor.id()));
        assertEquals(6, plan.moves().size());
        assertEquals(6, roster.readyCount(donor), "Review must not move anyone");
        assertTrue(SquadConsolidation.apply(roster, plan));
        assertEquals(12, roster.readyCount(receiver));
        assertEquals(0, roster.readyCount(donor));
        assertSame(donor, roster.squadById(donor.id()));
        assertSame(career, donor.career());
        assertEquals(2, roster.readyCount(untouched));
        for (int i = 0; i < people.size(); i++) {
            assertSame(people.get(i), roster.soldierById(people.get(i).id()));
            assertEquals(kits.get(i), people.get(i).armorId());
            assertEquals(receiver.id(), roster.squadForSoldier(people.get(i).id()).id());
        }
        assertNotNull(receiver.leaderSoldierId());
        assertNull(donor.leaderSoldierId());
        assertFalse(SquadConsolidation.apply(roster, plan), "A reviewed order cannot replay");
    }

    @Test void woundedKeepPlacesAndHistoricalCasualtiesStayWithTheirFormation() {
        var roster = new MarineRoster();
        var receiver = squad(roster, 12);
        var donor = squad(roster, 4);
        String wounded = receiver.memberIds().get(0);
        String killed = receiver.memberIds().get(1);
        String missing = donor.memberIds().get(0);
        String donorWounded = donor.memberIds().get(1);
        roster.applySoldierOutcome(Map.of(wounded, MarineSoldierStatus.WIA, killed, MarineSoldierStatus.KIA,
                missing, MarineSoldierStatus.MIA, donorWounded, MarineSoldierStatus.WIA), 1f, 20f);
        var plan = SquadConsolidation.preview(roster, Set.of(receiver.id()), Set.of(donor.id()));
        assertEquals(1, plan.moves().size());
        assertTrue(SquadConsolidation.apply(roster, plan));
        assertEquals(11, roster.readyCount(receiver));
        assertEquals(0, roster.vacancies(receiver));
        assertTrue(receiver.memberIds().containsAll(List.of(wounded, killed)));
        assertTrue(donor.memberIds().containsAll(List.of(missing, donorWounded)));
        assertEquals(MarineSoldierStatus.WIA, roster.soldierById(wounded).status());
    }

    @Test void rejectsChangedPlanAndPostingBeforeAnyTransfer() {
        var roster = new MarineRoster();
        var receiver = squad(roster, 6);
        var donor = squad(roster, 6);
        var plan = SquadConsolidation.preview(roster, Set.of(receiver.id()), Set.of(donor.id()));
        roster.applySoldierOutcome(Map.of(donor.memberIds().get(5), MarineSoldierStatus.WIA), 1f, 20f);
        assertFalse(SquadConsolidation.apply(roster, plan));
        assertEquals(6, roster.readyCount(receiver));
        var fresh = SquadConsolidation.preview(roster, Set.of(receiver.id()), Set.of(donor.id()));
        donor.setStationingContractId(42);
        assertFalse(SquadConsolidation.apply(roster, fresh));
        assertEquals(6, roster.readyCount(receiver));
        assertFalse(SquadConsolidation.preview(roster, Set.of(receiver.id()), Set.of(receiver.id())).canApply());
        assertFalse(SquadConsolidation.preview(roster, Set.of("missing"), Set.of(donor.id())).canApply());
    }

    private static MarineSquad squad(MarineRoster roster, int count) {
        var squad = roster.createSquad();
        for (int i = 0; i < count; i++) roster.recruitToSquad(squad.id());
        return squad;
    }
}
