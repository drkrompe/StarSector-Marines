package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarineArmoryTest {

    @Test
    void highRiskProgressionUnlocksAndPrintsMasterworkDmr() {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.isPrimaryUnlocked(MarineWeapon.DMR, EquipmentGrade.MASTERWORK));

        for (int i = 0; i < 4; i++) armory.recordVictory(2, false);
        assertFalse(armory.isPrimaryUnlocked(MarineWeapon.DMR, EquipmentGrade.MASTERWORK));

        armory.recordVictory(7, true);
        assertTrue(armory.isPrimaryUnlocked(MarineWeapon.DMR, EquipmentGrade.MASTERWORK));
        assertTrue(armory.printPrimary(MarineWeapon.DMR, EquipmentGrade.MASTERWORK));
        assertEquals(1, armory.ownedPrimary(MarineWeapon.DMR, EquipmentGrade.MASTERWORK));
        assertEquals(7, armory.fabricationMaterials());
    }

    @Test
    void allocationCannotExceedPrintedInventory() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(13);
        int assigned = 0;
        for (MarineSoldier soldier : roster.activeSoldiers()) {
            if (roster.allocateArmor(soldier.id(), MarineArmorPattern.ARMORLESS)) assigned++;
        }
        assertEquals(13, assigned, "basic issue expands with the persistent roster");

        MarineSoldier first = roster.activeSoldiers().get(0);
        assertFalse(roster.allocatePrimary(first.id(), MarineWeapon.DMR,
                EquipmentGrade.MASTERWORK), "locked recipe cannot be allocated");
    }

    @Test
    void survivorsDevelopAndFallenSoldiersStayPersistentlyKia() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2);
        MarineSoldier survivor = roster.activeSoldiers().get(0);
        MarineSoldier fallen = roster.activeSoldiers().get(1);

        roster.applySoldierOutcome(Set.of(survivor.id()), Set.of(fallen.id()), 80);

        assertEquals(80, survivor.experienceXp());
        assertEquals(MarineSoldierStatus.KIA, fallen.status());
        roster.applySoldierOutcome(Collections.emptySet(), Set.of(fallen.id()), 0);
        assertEquals(MarineSoldierStatus.KIA, fallen.status());
    }
}
