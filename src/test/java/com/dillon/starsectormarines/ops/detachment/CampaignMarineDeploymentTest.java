package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CampaignMarineDeploymentTest {

    @Test
    void freezesPersistentIdentityProgressionAndAllocatedVisuals() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);
        MarineSoldier soldier = roster.activeSoldiers().get(0);
        assertTrue(roster.allocateArmor(soldier.id(), MarineArmorPattern.CHARCOAL));
        assertTrue(roster.allocatePrimary(soldier.id(), MarineWeapon.DMR,
                EquipmentGrade.SERVICE));
        soldier.addExperience(123);

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(roster, 1);
        MarineLoadout seat = deployment.seat(0);

        assertNotNull(seat);
        assertEquals(soldier.id(), seat.campaignSoldierId);
        assertEquals(MarineWeapon.DMR, seat.primary);
        assertEquals(123, seat.soldierProfile.experienceXp());
        assertEquals(LayeredArmorFamily.CHARCOAL, seat.armorFamily);
    }

    @Test
    void selectedFireteamIsLoadedBeforeOtherRosterPersonnel() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        MarineSquad second = roster.squads().get(1);

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(
                roster, Collections.singleton(second.id()), 2);

        assertEquals(second.memberIds().get(0), deployment.seat(0).campaignSoldierId);
        assertEquals(second.memberIds().get(1), deployment.seat(1).campaignSoldierId);
    }

    @Test
    void freezingDeploymentDoesNotGenerateFreeReplacements() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(roster, 4);

        assertEquals(1, deployment.size());
        assertEquals(1, roster.soldiers().size());
    }

    @Test
    void reservePersonnelAreNotAutoLoadedWithoutFireteamAssignment() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2);
        MarineSoldier reserveMarine = roster.soldiers().get(0);
        assertTrue(roster.transferSoldier(
                reserveMarine.id(), roster.reserveSquad().id()));

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(roster, 2);

        assertEquals(1, deployment.size());
        assertNotEquals(reserveMarine.id(), deployment.seat(0).campaignSoldierId);
    }

    @Test
    void staleExplicitSelectionDoesNotFallBackToUnselectedFireteams() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(6);

        CampaignMarineDeployment deployment = CampaignMarineDeployment.freeze(
                roster, Collections.singleton("missing-squad"), 2);

        assertEquals(0, deployment.size());
    }
}
