package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        MarineRoster roster = roster(7);
        MarineCaptain captain = captain(Rank.CORPORAL);
        MarineCaptain replacement = captain(Rank.CORPORAL);
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
        MarineSoldier homeRecruit = roster.enlistLineRecruit();
        assertEquals(available, roster.squadForSoldier(homeRecruit.id()));
        assertNull(roster.nextTransferTarget(roster.squadMembers(stationed).get(0).id()));
        assertFalse(roster.assignCaptainToSquad(replacement.id(), stationed.id()));
        assertFalse(roster.clearSquadCaptain(stationed.id()));
        assertNull(roster.nextAssignableCaptain(stationed.id()));
        assertFalse(roster.removeById(captain.id()));

        MarineSoldier away = roster.squadMembers(stationed).get(0);
        assertFalse(roster.allocatePrimary(away.id(), away.primary(), away.primaryGrade()));
        assertFalse(roster.allocateSecondary(away.id(), null));
        assertFalse(roster.allocateArmor(away.id(), away.armor()));
        assertEquals(SquadPresetResult.STATIONED,
                roster.applySquadPreset(stationed.id(), SquadEquipmentPreset.LINE));

        assertEquals(1, roster.releaseStationing(61L));
        assertTrue(roster.allocateSecondary(away.id(), null));
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

    private static MarineRoster roster(int personnel) {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(personnel);
        return roster;
    }

    private static MarineCaptain captain(Rank rank) {
        return new MarineCaptain("Station Commander", null, rank, 0f);
    }
}
