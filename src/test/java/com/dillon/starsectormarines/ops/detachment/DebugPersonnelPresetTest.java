package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugPersonnelPresetTest {

    @Test
    void everyPresetFillsLargeDebugManifestsWithoutCampaignIdentities() {
        for (DebugPersonnelPreset preset : DebugPersonnelPreset.values()) {
            CampaignMarineDeployment deployment =
                    CampaignMarineDeployment.debugFixture(preset, 280);

            assertEquals(280, deployment.size());
            for (int seat = 0; seat < deployment.size(); seat++) {
                assertNull(deployment.seat(seat).campaignSoldierId);
            }
        }
    }

    @Test
    void fixturesExposeDistinctPersonnelAndEquipmentSetups() {
        MarineLoadout recruit = CampaignMarineDeployment.debugFixture(
                DebugPersonnelPreset.RECRUITS, 1).seat(0);
        assertEquals(EquipmentGrade.SURPLUS, recruit.equipmentGrade);
        assertEquals(SoldierAptitude.LIMITED, recruit.soldierProfile.aptitude());
        assertEquals(ExperienceTier.GREEN, recruit.soldierProfile.experienceTier());

        CampaignMarineDeployment mixed = CampaignMarineDeployment.debugFixture(
                DebugPersonnelPreset.MIXED, 6);
        Set<MarineWeapon> weapons = new HashSet<>();
        Set<SoldierAptitude> aptitudes = new HashSet<>();
        for (int seat = 0; seat < mixed.size(); seat++) {
            weapons.add(mixed.seat(seat).primary);
            aptitudes.add(mixed.seat(seat).soldierProfile.aptitude());
        }
        assertTrue(weapons.size() >= 3);
        assertTrue(aptitudes.size() >= 2);

        MarineLoadout veteran = CampaignMarineDeployment.debugFixture(
                DebugPersonnelPreset.VETERANS, 1).seat(0);
        assertEquals(ExperienceTier.ELITE, veteran.soldierProfile.experienceTier());
        assertEquals(MarineSecondary.ROCKET_LAUNCHER, veteran.secondary);
    }
}
