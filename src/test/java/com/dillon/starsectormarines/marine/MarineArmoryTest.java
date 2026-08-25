package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
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
        assertEquals("SHD-2 Rattler", MarineWeapon.SMG.catalogName(EquipmentGrade.SERVICE));
        assertEquals("SA-2 Stalwart",
                MarineWeapon.SQUAD_AUTOMATIC.catalogName(EquipmentGrade.SERVICE));
        assertEquals("RG-4 Longbow", MarineWeapon.DMR.catalogName(EquipmentGrade.MASTERWORK));
    }

    @Test
    void squadAutomaticIsStarterReachableAndJoinsTheMilspecLadder() {
        MarineArmory armory = new MarineArmory();
        assertTrue(armory.isPrimaryUnlocked(
                MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.SERVICE));
        assertEquals(3, armory.ownedPrimary(
                MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.SERVICE));
        assertFalse(armory.isPrimaryUnlocked(
                MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.MILSPEC));

        for (int i = 0; i < 4; i++) armory.recordVictory(false);
        assertTrue(armory.isPrimaryUnlocked(
                MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.MILSPEC));
    }

    @Test
    void fragTemplateCardUnlocksAfterTwoVictoriesWithoutCreatingPrintCurrency() {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.isSecondaryUnlocked(MarineSecondary.FRAG_GRENADE));
        assertEquals(0, armory.ownedSecondary(MarineSecondary.FRAG_GRENADE));

        armory.recordVictory(false);
        assertFalse(armory.isSecondaryUnlocked(MarineSecondary.FRAG_GRENADE));
        armory.recordVictory(false);
        assertTrue(armory.isSecondaryUnlocked(MarineSecondary.FRAG_GRENADE));
        assertTrue(armory.ownsEquipmentTemplate(
                EquipmentTemplateCatalog.specialId(MarineSecondary.FRAG_GRENADE)));
        assertEquals(0, armory.fabricationMaterials());
    }

    @Test
    void saveLoadReinstallsCurrentBuiltInFireSupportDefinition() throws Exception {
        MarineArmory loaded = roundTrip(new MarineArmory());
        FireTeamTemplateCard fireSupport = loaded.templateCardById(
                FireTeamTemplateCards.FIRE_SUPPORT_ID);

        assertNotNull(fireSupport);
        assertEquals(MarineWeapon.SQUAD_AUTOMATIC, fireSupport.billet(1).primary());
        assertEquals(3, loaded.ownedPrimary(
                MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.SERVICE));
    }

    @Test
    void legacyRecipeOwnershipMigratesToEquipmentTemplateCards() throws Exception {
        MarineArmory armory = new MarineArmory();
        armory.unlockPrimary(MarineWeapon.DMR, EquipmentGrade.MASTERWORK);
        Field ownedTemplates = MarineArmory.class.getDeclaredField(
                "ownedEquipmentTemplateIds");
        ownedTemplates.setAccessible(true);
        ownedTemplates.set(armory, null);

        MarineArmory loaded = roundTrip(armory);

        assertTrue(loaded.ownsPrimaryTemplate(
                MarineWeapon.DMR, EquipmentGrade.MASTERWORK));
        assertTrue(loaded.ownsSpecialTemplate(MarineSecondary.SMOKE_GRENADE));
    }

    @Test
    void highRiskProgressionUnlocksMasterworkDmrTemplateCard() {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.isPrimaryUnlocked(MarineWeapon.DMR, EquipmentGrade.MASTERWORK));

        for (int i = 0; i < 4; i++) armory.recordVictory(false);
        assertFalse(armory.isPrimaryUnlocked(MarineWeapon.DMR, EquipmentGrade.MASTERWORK));

        armory.recordVictory(true);
        assertTrue(armory.isPrimaryUnlocked(MarineWeapon.DMR, EquipmentGrade.MASTERWORK));
        assertTrue(armory.ownsEquipmentTemplate(EquipmentTemplateCatalog.primaryId(
                MarineWeapon.DMR, EquipmentGrade.MASTERWORK)));
        assertEquals(0, armory.fabricationMaterials());
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
        assertEquals(MarineWeapon.SQUAD_AUTOMATIC,
                roster.soldierById(teamIds.get(1)).primary());
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
    void antiMaterielTemplatePersistsTheStableSpecialIdentity() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);

        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0,
                        FireTeamTemplateCards.ANTI_MATERIEL_ID));
        MarineSoldier carrier = roster.soldierById(squad.teamMembers(0).get(3));
        assertEquals(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID,
                carrier.specialEquipmentId());

        MarineRoster loaded = roundTrip(roster);
        MarineSoldier persisted = loaded.soldierById(carrier.id());
        assertEquals(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID,
                persisted.specialEquipmentId());
        assertEquals(MarineSecondary.ANTI_MATERIEL_RIFLE, persisted.secondary());
    }

    @Test
    void breachTemplateIssuesAReusableKitWithAStableIdentity() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);
        assertEquals(2, roster.armory().ownedSecondary(MarineSecondary.SATCHEL_CHARGE));

        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0,
                        FireTeamTemplateCards.BREACH_ID));
        MarineSoldier carrier = roster.soldierById(squad.teamMembers(0).get(1));
        assertEquals(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID,
                carrier.specialEquipmentId());

        MarineRoster loaded = roundTrip(roster);
        MarineSoldier persisted = loaded.soldierById(carrier.id());
        assertEquals(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID,
                persisted.specialEquipmentId());
        assertEquals(MarineSecondary.SATCHEL_CHARGE, persisted.secondary());
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
    void refitPreviewReportsFreeReturnedAndRequiredFromTheRealTransaction() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);

        FireTeamRefitPreview preview = roster.previewFireTeamTemplate(
                squad.id(), 0, FireTeamTemplateCards.LINE_ID);

        assertTrue(preview.canApply());
        FireTeamGearDelta fieldRifles = delta(preview, "FR-1 Rook");
        assertTrue(fieldRifles.unlimited());
        assertEquals(2, fieldRifles.returned());
        assertEquals(0, fieldRifles.required());
        FireTeamGearDelta pulseRifles = delta(preview, "PLS-2 Lancer");
        assertEquals(12, pulseRifles.free());
        assertEquals(0, pulseRifles.returned());
        assertEquals(4, pulseRifles.required());
        FireTeamGearDelta charcoal = delta(preview, "Charcoal combat armor");
        assertEquals(2, charcoal.free());
        assertEquals(4, charcoal.returned());
        assertEquals(4, charcoal.required());
    }

    @Test
    void cardAvailabilityCountsFieldedAssignmentsAndAdditionalCompleteKits() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);

        FireTeamTemplateAvailability before = roster.fireTeamTemplateAvailability(
                FireTeamTemplateCards.RECON_ID);
        assertEquals(0, before.fielded());
        assertEquals(1, before.readyToIssue());
        assertTrue(before.recipesUnlocked());

        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0, FireTeamTemplateCards.RECON_ID));
        FireTeamTemplateAvailability after = roster.fireTeamTemplateAvailability(
                FireTeamTemplateCards.RECON_ID);
        assertEquals(1, after.fielded());
        assertEquals(0, after.readyToIssue());
    }

    @Test
    void twoTeamSwapUsesBothReturnsInOneAtomicInventoryTransaction() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2 * MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0, FireTeamTemplateCards.LINE_ID));
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 1, FireTeamTemplateCards.RECON_ID));
        assertEquals(FireTeamTemplateResult.INSUFFICIENT_PRIMARIES,
                roster.applyFireTeamTemplate(squad.id(), 0, FireTeamTemplateCards.RECON_ID),
                "a sequential refit cannot borrow the other team's held recon kit");

        FireTeamRefitPreview preview = roster.previewFireTeamTemplateSwap(
                squad.id(), 0, squad.id(), 1);
        assertTrue(preview.canApply());
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.swapFireTeamTemplates(squad.id(), 0, squad.id(), 1));

        assertEquals(FireTeamTemplateCards.RECON_ID, squad.teamTemplateCardId(0));
        assertEquals(FireTeamTemplateCards.LINE_ID, squad.teamTemplateCardId(1));
        assertEquals(MarineWeapon.SMG,
                roster.soldierById(squad.teamMembers(0).get(0)).primary());
        assertEquals(MarineWeapon.PULSE_RIFLE,
                roster.soldierById(squad.teamMembers(1).get(0)).primary());
    }

    @Test
    void failedTwoTeamSwapLeavesBothTeamsAndAssignmentsUntouched() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2 * MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0, FireTeamTemplateCards.LINE_ID));
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 1, FireTeamTemplateCards.RECON_ID));
        MarineSoldier wounded = roster.soldierById(squad.teamMembers(1).get(0));
        roster.applySoldierOutcome(Collections.singletonMap(
                wounded.id(), MarineSoldierStatus.WIA), 0, 1f, 7f);

        assertEquals(FireTeamTemplateResult.TEAM_NOT_READY,
                roster.swapFireTeamTemplates(squad.id(), 0, squad.id(), 1));
        assertEquals(FireTeamTemplateCards.LINE_ID, squad.teamTemplateCardId(0));
        assertEquals(FireTeamTemplateCards.RECON_ID, squad.teamTemplateCardId(1));
        assertEquals(MarineWeapon.PULSE_RIFLE,
                roster.soldierById(squad.teamMembers(0).get(0)).primary());
        assertEquals(MarineWeapon.SMG, wounded.primary());
    }

    @Test
    void squadArrangementPersistsThreeTemplateReferences() throws Exception {
        MarineArmory armory = new MarineArmory();
        SquadArrangement arrangement = armory.createSquadArrangement(
                "Screen and Strike", List.of(
                        FireTeamTemplateCards.LINE_ID,
                        FireTeamTemplateCards.RECON_ID,
                        FireTeamTemplateCards.FIELD_ID));

        MarineArmory loaded = roundTrip(armory);
        SquadArrangement persisted = loaded.squadArrangementById(arrangement.id());
        assertNotNull(persisted);
        assertEquals("Screen and Strike", persisted.displayName());
        assertEquals(List.of(
                        FireTeamTemplateCards.LINE_ID,
                        FireTeamTemplateCards.RECON_ID,
                        FireTeamTemplateCards.FIELD_ID),
                persisted.templateIds());
    }

    @Test
    void squadArrangementRequiresThreeKnownTemplates() {
        MarineArmory armory = new MarineArmory();

        assertThrows(IllegalArgumentException.class,
                () -> armory.createSquadArrangement("Too Small",
                        List.of(FireTeamTemplateCards.FIELD_ID)));
        assertThrows(IllegalArgumentException.class,
                () -> armory.createSquadArrangement("Unknown",
                        List.of(FireTeamTemplateCards.FIELD_ID,
                                FireTeamTemplateCards.LINE_ID, "missing")));
    }

    @Test
    void arrangementReferenceProtectsCustomTemplateUntilPlanIsDeleted() {
        MarineRoster roster = new MarineRoster();
        FireTeamTemplateCard custom = roster.armory().cloneTemplateCard(
                FireTeamTemplateCards.FIELD_ID);
        SquadArrangement arrangement = roster.armory().createSquadArrangement(
                "Custom Line", List.of(custom.id(),
                        FireTeamTemplateCards.LINE_ID,
                        FireTeamTemplateCards.FIELD_ID));

        assertTrue(roster.isFireTeamTemplateReferenced(custom.id()));
        assertFalse(roster.deleteFireTeamTemplate(custom.id()));
        assertTrue(roster.armory().deleteSquadArrangement(arrangement.id()));
        assertTrue(roster.deleteFireTeamTemplate(custom.id()));
    }

    @Test
    void squadArrangementReordersScarceTemplatesAsOneAtomicTransaction() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        SquadArrangement initial = roster.armory().createSquadArrangement(
                "Line Forward", List.of(
                        FireTeamTemplateCards.LINE_ID,
                        FireTeamTemplateCards.RECON_ID,
                        FireTeamTemplateCards.FIELD_ID));
        SquadArrangement rotated = roster.armory().createSquadArrangement(
                "Recon Forward", List.of(
                        FireTeamTemplateCards.RECON_ID,
                        FireTeamTemplateCards.FIELD_ID,
                        FireTeamTemplateCards.LINE_ID));

        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applySquadArrangement(squad.id(), initial.id()));
        assertEquals(1, roster.squadArrangementFieldedCount(initial.id()));
        assertEquals(FireTeamTemplateResult.INSUFFICIENT_PRIMARIES,
                roster.applyFireTeamTemplate(squad.id(), 0,
                        FireTeamTemplateCards.RECON_ID),
                "a sequential refit cannot borrow Bravo's held recon weapons");

        SquadArrangementPreview preview = roster.previewSquadArrangement(
                squad.id(), rotated.id());
        assertTrue(preview.canApply());
        FireTeamGearDelta smgs = preview.gear().stream()
                .filter(item -> "SHD-2 Rattler".equals(item.label()))
                .findFirst().orElseThrow();
        assertEquals(1, smgs.free());
        assertEquals(2, smgs.returned());
        assertEquals(2, smgs.required());

        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applySquadArrangement(squad.id(), rotated.id()));
        assertEquals(FireTeamTemplateCards.RECON_ID, squad.teamTemplateCardId(0));
        assertEquals(FireTeamTemplateCards.FIELD_ID, squad.teamTemplateCardId(1));
        assertEquals(FireTeamTemplateCards.LINE_ID, squad.teamTemplateCardId(2));
        assertEquals(0, roster.squadArrangementFieldedCount(initial.id()));
        assertEquals(1, roster.squadArrangementFieldedCount(rotated.id()));
    }

    @Test
    void failedSquadArrangementLeavesEveryTeamUntouched() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        SquadArrangement field = roster.armory().createSquadArrangement(
                "Field Column", List.of(
                        FireTeamTemplateCards.FIELD_ID,
                        FireTeamTemplateCards.FIELD_ID,
                        FireTeamTemplateCards.FIELD_ID));
        SquadArrangement line = roster.armory().createSquadArrangement(
                "Line Column", List.of(
                        FireTeamTemplateCards.LINE_ID,
                        FireTeamTemplateCards.LINE_ID,
                        FireTeamTemplateCards.LINE_ID));
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applySquadArrangement(squad.id(), field.id()));
        MarineSoldier wounded = roster.soldierById(squad.teamMembers(1).get(0));
        roster.applySoldierOutcome(Collections.singletonMap(
                wounded.id(), MarineSoldierStatus.WIA), 0, 1f, 7f);

        assertEquals(FireTeamTemplateResult.TEAM_NOT_READY,
                roster.applySquadArrangement(squad.id(), line.id()));
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            assertEquals(FireTeamTemplateCards.FIELD_ID,
                    squad.teamTemplateCardId(team));
            for (String memberId : squad.teamMembers(team)) {
                assertEquals(MarineWeapon.FIELD_RIFLE,
                        roster.soldierById(memberId).primary());
            }
        }
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
        assertEquals(8, loaded.templateCards().size(),
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

    private static FireTeamGearDelta delta(FireTeamRefitPreview preview, String label) {
        return preview.gear().stream()
                .filter(item -> label.equals(item.label()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing gear delta: " + label));
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
