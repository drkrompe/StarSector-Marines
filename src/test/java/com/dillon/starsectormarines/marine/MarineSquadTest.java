package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineSquadTest {

    @Test
    void recruitsIntoStableTwelveMarineSquads() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2 * MarineSquad.CAPACITY + 1);

        assertEquals(3, roster.squads().size());
        assertEquals(MarineSquad.CAPACITY, roster.squads().get(0).memberIds().size());
        assertEquals(MarineSquad.CAPACITY, roster.squads().get(1).memberIds().size());
        assertEquals(1, roster.squads().get(2).memberIds().size());
    }

    @Test
    void bulkRecruitmentFillsOneSquadAsASingleFormationMutation() {
        MarineRoster roster = new MarineRoster();
        MarineSquad squad = roster.createSquad();

        List<MarineSoldier> recruits = roster.recruitToSquad(
                squad.id(), MarineSquad.CAPACITY * 2);

        assertEquals(MarineSquad.CAPACITY, recruits.size());
        assertEquals(MarineSquad.CAPACITY, roster.manningCount(squad));
        assertNotNull(roster.squadLeader(squad));
        assertEquals(recruits.get(0), roster.soldierById(recruits.get(0).id()));
        assertTrue(roster.recruitToSquad(squad.id(), 1).isEmpty());
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
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        Map<String, MarineSoldierStatus> outcome = new HashMap<>();
        outcome.put(roster.soldiers().get(0).id(), MarineSoldierStatus.KIA);
        outcome.put(roster.soldiers().get(1).id(), MarineSoldierStatus.WIA);

        roster.applySoldierOutcome(outcome, 0, 10f, 7f);

        assertEquals(1, roster.vacancies(squad));
        assertNotNull(roster.recruitToSquad(squad.id()));
        assertEquals(0, roster.vacancies(squad));
        assertEquals(MarineSquad.CAPACITY + 1, squad.memberIds().size(),
                "KIA remains on the historical roll");
    }

    @Test
    void readyMarinesCanMoveThroughReserveButCasualtiesCannot() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY + 1);
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
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad line = roster.squads().get(0);
        MarineSquad reserve = roster.reserveSquad();
        Map<String, MarineSoldierStatus> outcome = new HashMap<>();
        outcome.put(roster.soldiers().get(0).id(), MarineSoldierStatus.WIA);
        roster.applySoldierOutcome(outcome, 0, 10f, 7f);

        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);

        assertEquals(MarineSquad.CAPACITY, roster.manningCount(line));
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

    @Test
    void initialComplementCanOnlyBeIssuedOnce() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        roster.bootstrapInitialComplement(2 * MarineSquad.CAPACITY);

        assertEquals(MarineSquad.CAPACITY, roster.soldiers().size());
        assertEquals(MarineSquad.CAPACITY, roster.activeSoldiers().size());
    }

    @Test
    void onlyReadyReservePersonnelCanBeDemobilized() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);
        MarineSoldier line = roster.soldiers().get(0);
        MarineSquad reserve = roster.reserveSquad();

        assertFalse(roster.releaseReserveSoldier(line.id()));
        assertTrue(roster.transferSoldier(line.id(), reserve.id()));
        assertTrue(roster.releaseReserveSoldier(line.id()));
        assertEquals(0, roster.soldiers().size());
    }

    @Test
    void reservesDoNotCountAsLineDeploymentReadiness() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2);
        MarineSquad reserve = roster.reserveSquad();
        MarineSoldier moved = roster.soldiers().get(0);

        assertTrue(roster.transferSoldier(moved.id(), reserve.id()));

        assertEquals(2, roster.activeSoldiers().size());
        assertEquals(1, roster.lineReadySoldiers().size());
    }
}
