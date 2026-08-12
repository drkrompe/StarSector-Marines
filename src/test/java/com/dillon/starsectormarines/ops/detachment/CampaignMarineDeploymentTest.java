package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import org.junit.jupiter.api.Test;

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
}
