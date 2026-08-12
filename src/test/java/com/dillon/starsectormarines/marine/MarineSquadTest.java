package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineSquadTest {

    @Test
    void recruitsIntoStableSixMarineFireteams() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(13);

        assertEquals(3, roster.squads().size());
        assertEquals(6, roster.squads().get(0).memberIds().size());
        assertEquals(6, roster.squads().get(1).memberIds().size());
        assertEquals(1, roster.squads().get(2).memberIds().size());
    }

    @Test
    void woundedRecoverWhileMissingAndKilledRemainUnavailable() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(3);
        Map<String, MarineSoldierStatus> outcome = new HashMap<>();
        outcome.put(roster.soldiers().get(0).id(), MarineSoldierStatus.WIA);
        outcome.put(roster.soldiers().get(1).id(), MarineSoldierStatus.MIA);
        outcome.put(roster.soldiers().get(2).id(), MarineSoldierStatus.KIA);

        roster.applySoldierOutcome(outcome, 20, 100f, 7f);
        roster.recoverWounded(106f);
        assertEquals(MarineSoldierStatus.WIA, roster.soldiers().get(0).status());
        roster.recoverWounded(107f);

        assertEquals(MarineSoldierStatus.ACTIVE, roster.soldiers().get(0).status());
        assertEquals(MarineSoldierStatus.MIA, roster.soldiers().get(1).status());
        assertEquals(MarineSoldierStatus.KIA, roster.soldiers().get(2).status());
    }

    @Test
    void casualtiesOpenReplacementBilletsButWoundedDoNot() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> outcome = new HashMap<>();
        outcome.put(roster.soldiers().get(0).id(), MarineSoldierStatus.KIA);
        outcome.put(roster.soldiers().get(1).id(), MarineSoldierStatus.WIA);

        roster.applySoldierOutcome(outcome, 0, 10f, 7f);

        assertEquals(1, roster.vacancies(squad));
        assertNotNull(roster.recruitToSquad(squad.id()));
        assertEquals(0, roster.vacancies(squad));
        assertEquals(7, squad.memberIds().size(), "KIA remains on the historical roll");
    }

    @Test
    void readyMarinesCanMoveThroughReserveButCasualtiesCannot() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(7);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);
        MarineSquad reserve = roster.reserveSquad();
        MarineSoldier ready = roster.squadMembers(first).get(0);

        assertTrue(roster.transferSoldier(ready.id(), reserve.id()));
        assertEquals(reserve, roster.squadForSoldier(ready.id()));
        assertTrue(roster.transferSoldier(ready.id(), second.id()));
        assertEquals(second, roster.squadForSoldier(ready.id()));

        Map<String, MarineSoldierStatus> outcome = new HashMap<>();
        outcome.put(ready.id(), MarineSoldierStatus.MIA);
        roster.applySoldierOutcome(outcome, 0, 0f, 1f);
        assertFalse(roster.transferSoldier(ready.id(), reserve.id()));
    }

    @Test
    void lineFireteamsCanBeRenamedButReserveIdentityStaysStable() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);
        MarineSquad line = roster.squads().get(0);
        MarineSquad reserve = roster.reserveSquad();

        assertTrue(roster.renameSquad(line.id(), "Vandal One"));
        assertEquals("Vandal One", line.name());
        assertFalse(roster.renameSquad(reserve.id(), "Not Reserves"));
        assertEquals("Reserve Pool", reserve.name());
    }

    @Test
    void temporaryWoundedShortfallIsBackfilledFromReserveWithoutOvermanningLineSquad() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(6);
        MarineSquad line = roster.squads().get(0);
        MarineSquad reserve = roster.reserveSquad();
        Map<String, MarineSoldierStatus> outcome = new HashMap<>();
        outcome.put(roster.soldiers().get(0).id(), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(outcome, 0, 10f, 7f);

        roster.ensureActiveSoldiers(6);

        assertEquals(6, roster.manningCount(line));
        assertEquals(1, roster.readyCount(reserve));
        assertNotNull(roster.recruitToSquad(reserve.id()));
        assertEquals(2, roster.readyCount(reserve));
    }

    @Test
    void legacyRosterWithoutSquadFieldsBackfillsExistingPersonnel() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2);
        java.lang.reflect.Field squads = MarineRoster.class.getDeclaredField("squads");
        squads.setAccessible(true);
        squads.set(roster, null);
        java.lang.reflect.Field next = MarineRoster.class.getDeclaredField("nextSquadNumber");
        next.setAccessible(true);
        next.setInt(roster, 0);
        java.lang.reflect.Method readResolve = MarineRoster.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);

        readResolve.invoke(roster);

        assertEquals(1, roster.squads().size());
        assertEquals(2, roster.squads().get(0).memberIds().size());
    }
}
