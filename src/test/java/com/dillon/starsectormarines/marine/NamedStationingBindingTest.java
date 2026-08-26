package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedStationingBindingTest {

    @Test
    void bindingIsAtomicRankBoundedAndExcludesOrdinaryReadiness() {
        int cap = Rank.LIEUTENANT.squadCommandCap();
        MarineRoster roster = roster((cap + 1) * MarineSquad.CAPACITY);
        MarineCaptain captain = captain(Rank.LIEUTENANT);
        roster.add(captain);
        List<MarineSquad> line = roster.squads().subList(0, cap + 1);
        List<String> withinCap = new ArrayList<>();
        for (int i = 0; i < cap; i++) withinCap.add(line.get(i).id());
        List<String> overCap = new ArrayList<>(withinCap);
        overCap.add(line.get(cap).id());

        assertFalse(roster.bindStationing(41L, captain.id(), overCap));
        for (MarineSquad squad : line) assertFalse(squad.stationed());

        assertTrue(roster.bindStationing(41L, captain.id(), withinCap));
        assertEquals(41L, line.get(0).stationingContractId());
        assertFalse(roster.isSquadAvailable(line.get(0).id()));
        assertEquals(MarineSquad.CAPACITY, roster.lineReadySoldiers().size());
        assertEquals(line.subList(0, cap), roster.squadsStationedOn(41L));
    }

    @Test
    void bindingRejectsUnavailableTeamsAndReleaseIsReplaySafe() {
        MarineRoster roster = roster(2 * MarineSquad.CAPACITY);
        MarineCaptain captain = captain(Rank.CAPTAIN);
        roster.add(captain);
        MarineSquad first = roster.squads().get(0);
        MarineSquad second = roster.squads().get(1);
        assertTrue(roster.bindStationing(51L, captain.id(), List.of(first.id())));

        assertFalse(roster.bindStationing(51L, captain.id(), List.of(second.id())));
        assertFalse(roster.bindStationing(52L, captain.id(),
                List.of(first.id(), second.id())));
        assertFalse(second.stationed());
        assertEquals(1, roster.releaseStationing(51L));
        assertEquals(0, roster.releaseStationing(51L));
        assertTrue(roster.isSquadAvailable(first.id()));
    }

    @Test
    void stationedTeamRejectsPersonnelCommandAndEquipmentMutation() {
        MarineRoster roster = roster(MarineSquad.CAPACITY + 1);
        MarineCaptain captain = captain(Rank.CAPTAIN);
        MarineCaptain replacement = captain(Rank.CAPTAIN);
        roster.add(captain);
        roster.add(replacement);
        MarineSquad stationed = roster.squads().get(0);
        MarineSquad available = roster.squads().get(1);
        MarineSoldier resident = roster.squadMembers(stationed).get(0);
        MarineSoldier outsider = roster.squadMembers(available).get(0);
        assertTrue(roster.transferSoldier(resident.id(), roster.reserveSquad().id()));
        assertTrue(roster.assignCaptainToSquad(captain.id(), stationed.id()));
        assertTrue(roster.bindStationing(61L, captain.id(), List.of(stationed.id())));

        assertFalse(roster.transferSoldier(roster.squadMembers(stationed).get(0).id(),
                available.id()));
        assertFalse(roster.transferSoldier(outsider.id(), stationed.id()));
        assertNull(roster.recruitToSquad(stationed.id()));
        MarineSoldier homeRecruit = roster.createLineReplacement();
        assertEquals(available, roster.squadForSoldier(homeRecruit.id()));
        assertNull(roster.nextTransferTarget(roster.squadMembers(stationed).get(0).id()));
        assertFalse(roster.assignCaptainToSquad(replacement.id(), stationed.id()));
        assertFalse(roster.clearSquadCaptain(stationed.id()));
        assertNull(roster.nextAssignableCaptain(stationed.id()));
        assertFalse(roster.removeById(captain.id()));

        MarineSoldier away = roster.squadMembers(stationed).get(0);
        assertFalse(roster.allocatePrimary(away.id(), away.primaryDef(), away.primaryGrade()));
        assertFalse(roster.allocateSecondary(away.id(), (String) null));
        assertFalse(roster.allocateArmor(away.id(), away.armor()));
        assertEquals(FireTeamTemplateResult.STATIONED,
                roster.applyFireTeamTemplate(stationed.id(), 0,
                        FireTeamTemplateCards.LINE_ID));

        assertEquals(1, roster.releaseStationing(61L));
        assertTrue(roster.allocateSecondary(away.id(), (String) null));
        assertTrue(roster.clearSquadCaptain(stationed.id()));
    }

    @Test
    void saveRepairNormalizesLegacyAndIllegalReserveBindings() throws Exception {
        MarineRoster roster = roster(1);
        MarineSquad line = roster.squads().get(0);
        MarineSquad reserve = roster.reserveSquad();
        Field stationingId = MarineSquad.class.getDeclaredField("stationingContractId");
        stationingId.setAccessible(true);
        stationingId.setLong(line, 0L);
        reserve.setStationingContractId(71L);

        Method squadReadResolve = MarineSquad.class.getDeclaredMethod("readResolve");
        squadReadResolve.setAccessible(true);
        squadReadResolve.invoke(line);
        Method rosterReadResolve = MarineRoster.class.getDeclaredMethod("readResolve");
        rosterReadResolve.setAccessible(true);
        rosterReadResolve.invoke(roster);

        assertEquals(-1L, line.stationingContractId());
        assertEquals(-1L, reserve.stationingContractId());
    }

    @Test
    void failedExtractionMarksRecoverablePersonnelMiaBeforeRelease() {
        MarineRoster roster = roster(MarineSquad.CAPACITY);
        MarineCaptain captain = captain(Rank.LIEUTENANT);
        roster.add(captain);
        MarineSquad squad = roster.squads().get(0);
        MarineSoldier wounded = roster.squadMembers(squad).get(0);
        MarineSoldier fallen = roster.squadMembers(squad).get(1);
        roster.applySoldierOutcome(Map.of(
                wounded.id(), MarineSoldierStatus.WIA,
                fallen.id(), MarineSoldierStatus.KIA), 0, 10f, 7f);
        assertTrue(roster.bindStationing(81L, captain.id(), List.of(squad.id())));

        assertEquals(1, roster.failStationingExtraction(81L));

        assertEquals(MarineSoldierStatus.MIA, wounded.status());
        assertEquals(MarineSoldierStatus.KIA, fallen.status());
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            assertTrue(soldier.status() == MarineSoldierStatus.MIA
                    || soldier.status() == MarineSoldierStatus.KIA);
        }
        assertFalse(squad.stationed());
        assertEquals(0, roster.failStationingExtraction(81L));
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
