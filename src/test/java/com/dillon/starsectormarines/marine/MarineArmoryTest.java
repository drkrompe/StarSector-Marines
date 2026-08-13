package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarineArmoryTest {

    @Test
    void newRecruitStartsWithBasicFieldRifle() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);

        MarineSoldier recruit = roster.activeSoldiers().get(0);
        assertEquals(MarineWeapon.FIELD_RIFLE, recruit.primary());
        assertEquals(EquipmentGrade.SURPLUS, recruit.primaryGrade());
    }

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

    @Test
    void squadPresetAppliesToEveryReadyMemberAtomically() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);

        assertEquals(SquadPresetResult.APPLIED,
                roster.applySquadPreset(squad.id(), SquadEquipmentPreset.LINE));

        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            assertEquals(MarineWeapon.PULSE_RIFLE, soldier.primary());
            assertEquals(EquipmentGrade.SERVICE, soldier.primaryGrade());
            assertEquals(MarineArmorPattern.CHARCOAL, soldier.armor());
        }
    }

    @Test
    void insufficientPresetInventoryLeavesEntireSquadUntouched() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(6);
        MarineSquad squad = roster.squads().get(0);
        MarineSoldier first = roster.squadMembers(squad).get(0);
        MarineWeapon priorWeapon = first.primary();
        EquipmentGrade priorGrade = first.primaryGrade();
        MarineArmorPattern priorArmor = first.armor();

        assertEquals(SquadPresetResult.INSUFFICIENT_WEAPONS,
                roster.applySquadPreset(squad.id(), SquadEquipmentPreset.RECON));

        assertEquals(priorWeapon, first.primary());
        assertEquals(priorGrade, first.primaryGrade());
        assertEquals(priorArmor, first.armor());
    }

    @Test
    void woundedPersonnelContinueHoldingTheirAllocatedGear() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(4);
        assertTrue(roster.allocatePrimary(roster.soldiers().get(3).id(),
                MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE));
        for (int i = 0; i < 3; i++) {
            assertTrue(roster.allocatePrimary(roster.soldiers().get(i).id(),
                    MarineWeapon.DMR, EquipmentGrade.SERVICE));
        }
        MarineSoldier wounded = roster.soldiers().get(0);
        roster.applySoldierOutcome(Collections.singletonMap(
                wounded.id(), MarineSoldierStatus.WIA), 0, 1f, 7f);

        assertFalse(roster.allocatePrimary(roster.soldiers().get(3).id(),
                MarineWeapon.DMR, EquipmentGrade.SERVICE));
    }
}
