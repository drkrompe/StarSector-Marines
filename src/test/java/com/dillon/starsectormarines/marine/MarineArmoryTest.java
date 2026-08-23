package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    void customCardCanBeDesignedWithoutStockAndKeepsItsStableIdAcrossSaveLoad()
            throws Exception {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.isPrimaryUnlocked(MarineWeapon.DMR, EquipmentGrade.MASTERWORK));
        assertFalse(armory.isArmorUnlocked(MarineArmorPattern.RED_ELITE));

        FireTeamTemplateCard card = armory.createTemplateCard("Aspirational Hunters",
                List.of(
                        billet("Leader", MarineWeapon.DMR, EquipmentGrade.MASTERWORK,
                                MarineSecondary.ROCKET_LAUNCHER, MarineArmorPattern.RED_ELITE),
                        billet("Hunter", MarineWeapon.DMR, EquipmentGrade.MASTERWORK,
                                null, MarineArmorPattern.RED_ELITE),
                        billet("Hunter", MarineWeapon.DMR, EquipmentGrade.MASTERWORK,
                                null, MarineArmorPattern.RED_ELITE),
                        billet("Hunter", MarineWeapon.DMR, EquipmentGrade.MASTERWORK,
                                null, MarineArmorPattern.RED_ELITE)));

        MarineArmory loaded = roundTrip(armory);
        FireTeamTemplateCard persisted = loaded.templateCardById(card.id());
        assertNotNull(persisted);
        assertEquals("Aspirational Hunters", persisted.displayName());
        assertEquals(MarineWeapon.DMR, persisted.billet(0).primary());
        assertEquals(EquipmentGrade.MASTERWORK, persisted.billet(0).grade());
        assertEquals(MarineSecondary.ROCKET_LAUNCHER, persisted.billet(0).secondary());
        assertEquals(MarineArmorPattern.RED_ELITE, persisted.billet(0).armor());
        assertEquals(5, loaded.templateCards().size(),
                "readResolve restores missing starters without duplicating existing ones");
    }

    @Test
    void builtInsAreImmutableButCanBeClonedAndRenamed() {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.renameTemplateCard(FireTeamTemplateCards.RECON_ID, "Sneaky"));

        FireTeamTemplateCard clone = armory.cloneTemplateCard(FireTeamTemplateCards.RECON_ID);
        assertNotNull(clone);
        assertFalse(FireTeamTemplateCards.isStarterId(clone.id()));
        assertTrue(armory.renameTemplateCard(clone.id(), "Pathfinders"));
        assertEquals("Pathfinders", armory.templateCardById(clone.id()).displayName());
        assertEquals(FireTeamTemplateCards.RECON_ID,
                armory.templateCardById(FireTeamTemplateCards.RECON_ID).id());
    }

    @Test
    void newRevisionDoesNotRewriteAssignedCardAndAssignedCardCannotBeDeleted() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);
        MarineArmory armory = roster.armory();
        FireTeamTemplateCard original = armory.cloneTemplateCard(FireTeamTemplateCards.FIELD_ID);
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0, original.id()));

        FireTeamTemplateCard revision = armory.createTemplateCard("Field Mk II",
                List.of(
                        billet("Leader", MarineWeapon.DMR, EquipmentGrade.MASTERWORK,
                                null, MarineArmorPattern.RED_ELITE),
                        billet("Rifleman", MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE,
                                null, MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE,
                                null, MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE,
                                null, MarineArmorPattern.ARMORLESS)));

        assertEquals(original.id(), squad.teamTemplateCardId(0));
        assertEquals(MarineWeapon.FIELD_RIFLE,
                roster.soldierById(squad.teamMembers(0).get(0)).primary());
        assertFalse(roster.deleteFireTeamTemplate(original.id()));
        assertTrue(roster.deleteFireTeamTemplate(revision.id()));
    }

    @Test
    void customCardRequiresExactlyFourCompleteBillets() {
        MarineArmory armory = new MarineArmory();
        List<FireTeamBillet> three = List.of(
                billet("One", MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE,
                        null, MarineArmorPattern.ARMORLESS),
                billet("Two", MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE,
                        null, MarineArmorPattern.ARMORLESS),
                billet("Three", MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE,
                        null, MarineArmorPattern.ARMORLESS));

        assertThrows(IllegalArgumentException.class,
                () -> armory.createTemplateCard("Too Small", three));
        assertThrows(IllegalArgumentException.class,
                () -> armory.createTemplateCard("   ",
                        FireTeamTemplateCards.starterCards().get(0).billets()));
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

    private static FireTeamBillet billet(String name, MarineWeapon primary,
                                          EquipmentGrade grade, MarineSecondary secondary,
                                          MarineArmorPattern armor) {
        return new FireTeamBillet(name, primary, grade, secondary, armor);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }
}
