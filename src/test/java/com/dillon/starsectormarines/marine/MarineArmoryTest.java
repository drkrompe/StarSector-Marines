package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarineArmoryTest {

    @Test
    void newRecruitStartsWithBasicFieldRifle() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(1);

        MarineSoldier recruit = roster.activeSoldiers().get(0);
        assertEquals(MarineWeapon.FIELD_RIFLE, recruit.primary());
        assertEquals(EquipmentGrade.SERVICE, recruit.primaryGrade());
        assertTrue(roster.canAllocatePrimary(recruit.id(), MarineWeapon.FIELD_RIFLE,
                EquipmentGrade.SERVICE), "fallback issue is unlimited");
    }

    @Test
    void fallbackRifleIsUnlimitedAndCannotConsumeFabricationMaterials() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(20);
        for (MarineSoldier soldier : roster.activeSoldiers()) {
            assertTrue(roster.allocatePrimary(soldier.id(), MarineWeapon.FIELD_RIFLE,
                    EquipmentGrade.SERVICE));
        }

        MarineArmory armory = roster.armory();
        armory.addFabricationMaterials(100);
        assertFalse(armory.canPrintPrimary(MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE));
        assertFalse(armory.printPrimary(MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE));
        assertFalse(armory.isPrimaryUnlocked(MarineWeapon.FIELD_RIFLE, EquipmentGrade.SURPLUS));
        assertEquals(100, armory.fabricationMaterials());
    }

    @Test
    void weaponCatalogNamesCombineDesignationTierAndModel() {
        assertEquals("FR-1 Rook", MarineWeapon.FIELD_RIFLE.catalogName(EquipmentGrade.MASTERWORK));
        assertEquals("PLS-3 Lancer", MarineWeapon.PULSE_RIFLE.catalogName(EquipmentGrade.MILSPEC));
        assertEquals("LMG-2 Rattler", MarineWeapon.SMG.catalogName(EquipmentGrade.SERVICE));
        assertEquals("RG-4 Longbow", MarineWeapon.DMR.catalogName(EquipmentGrade.MASTERWORK));
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
    void fabricationAvailabilityIncludesRecipeAndMaterialCost() {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.canPrintPrimary(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE));
        assertFalse(armory.canPrintSecondary(MarineSecondary.ROCKET_LAUNCHER));
        assertFalse(armory.canPrintArmor(MarineArmorPattern.CHARCOAL));

        armory.addFabricationMaterials(3);
        assertTrue(armory.canPrintPrimary(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE));
        assertTrue(armory.canPrintArmor(MarineArmorPattern.CHARCOAL));
        assertFalse(armory.canPrintSecondary(MarineSecondary.ROCKET_LAUNCHER));
        assertFalse(armory.canPrintPrimary(MarineWeapon.DMR, EquipmentGrade.MASTERWORK),
                "an unaffordable locked recipe stays disabled");
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
    void mixedTemplateCardAppliesToOneFireTeamAndPersistsItsAssignment() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        List<MarineSoldier> untouched = roster.squadMembers(squad).subList(
                MarineSquad.TEAM_SIZE, MarineSquad.CAPACITY);
        List<MarineWeapon> untouchedWeapons = untouched.stream()
                .map(MarineSoldier::primary).toList();

        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0,
                        FireTeamTemplateCards.FIRE_SUPPORT_ID));

        List<String> teamIds = squad.teamMembers(0);
        assertEquals(MarineWeapon.PULSE_RIFLE, roster.soldierById(teamIds.get(0)).primary());
        assertEquals(MarineWeapon.SMG, roster.soldierById(teamIds.get(1)).primary());
        assertEquals(MarineWeapon.DMR, roster.soldierById(teamIds.get(2)).primary());
        MarineSoldier antiArmor = roster.soldierById(teamIds.get(3));
        assertEquals(MarineWeapon.PULSE_RIFLE, antiArmor.primary());
        assertEquals(MarineSecondary.ROCKET_LAUNCHER, antiArmor.secondary());
        for (String teamId : teamIds) {
            assertEquals(MarineArmorPattern.ARMY_GREEN, roster.soldierById(teamId).armor());
        }
        assertEquals(FireTeamTemplateCards.FIRE_SUPPORT_ID, squad.teamTemplateCardId(0));
        assertNull(squad.teamTemplateCardId(1));
        assertEquals(untouchedWeapons, untouched.stream().map(MarineSoldier::primary).toList());
    }

    @Test
    void reusableCardCannotBeOverAssignedPastFiniteStock() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2 * MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0, FireTeamTemplateCards.RECON_ID));

        List<String> secondTeam = squad.teamMembers(1);
        List<MarineWeapon> priorWeapons = secondTeam.stream()
                .map(roster::soldierById).map(MarineSoldier::primary).toList();
        assertEquals(FireTeamTemplateResult.INSUFFICIENT_PRIMARIES,
                roster.applyFireTeamTemplate(squad.id(), 1, FireTeamTemplateCards.RECON_ID));

        assertEquals(priorWeapons, secondTeam.stream()
                .map(roster::soldierById).map(MarineSoldier::primary).toList());
        assertNull(squad.teamTemplateCardId(1));
    }

    @Test
    void targetTeamReturnsItsCurrentGearBeforeARefitIsEvaluated() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);

        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0, FireTeamTemplateCards.RECON_ID));
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0, FireTeamTemplateCards.RECON_ID));
    }

    @Test
    void insufficientSecondaryLeavesEveryBilletAndAssignmentUntouched() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        assertTrue(roster.allocateSecondary(squad.teamMembers(1).get(0),
                MarineSecondary.ROCKET_LAUNCHER));
        List<MarineSoldier> team = squad.teamMembers(0).stream()
                .map(roster::soldierById).toList();
        List<MarineWeapon> priorWeapons = team.stream().map(MarineSoldier::primary).toList();
        List<MarineArmorPattern> priorArmor = team.stream().map(MarineSoldier::armor).toList();

        assertEquals(FireTeamTemplateResult.INSUFFICIENT_SECONDARIES,
                roster.applyFireTeamTemplate(squad.id(), 0,
                        FireTeamTemplateCards.FIRE_SUPPORT_ID));

        assertEquals(priorWeapons, team.stream().map(MarineSoldier::primary).toList());
        assertEquals(priorArmor, team.stream().map(MarineSoldier::armor).toList());
        assertNull(squad.teamTemplateCardId(0));
    }

    @Test
    void incompleteTeamCannotReserveACompleteTemplateCard() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE - 1);
        MarineSquad squad = roster.squads().get(0);

        assertEquals(FireTeamTemplateResult.TEAM_NOT_READY,
                roster.applyFireTeamTemplate(squad.id(), 0, FireTeamTemplateCards.FIELD_ID));
        assertNull(squad.teamTemplateCardId(0));
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
