package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
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
        assertEquals(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), recruit.primaryDef());
        assertEquals(EquipmentGrade.SERVICE, recruit.primaryGrade());
        assertTrue(roster.canAllocatePrimary(recruit.id(), WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID),
                EquipmentGrade.SERVICE), "fallback issue is unlimited");
    }

    @Test
    void fallbackRifleIsUnlimitedAndCannotConsumeFabricationMaterials() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(20);
        for (MarineSoldier soldier : roster.activeSoldiers()) {
            assertTrue(roster.allocatePrimary(soldier.id(), WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID),
                    EquipmentGrade.SERVICE));
        }

        MarineArmory armory = roster.armory();
        armory.addFabricationMaterials(100);
        assertFalse(armory.canPrintPrimary(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SERVICE));
        assertFalse(armory.printPrimary(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SERVICE));
        assertFalse(armory.isPrimaryUnlocked(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SURPLUS));
        assertEquals(100, armory.fabricationMaterials());
    }

    @Test
    void weaponCatalogNamesCombineDesignationTierAndModel() {
        assertEquals("FR-1 Rook", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID).catalogName(EquipmentGrade.MASTERWORK));
        assertEquals("PLS-3 Lancer", WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).catalogName(EquipmentGrade.MILSPEC));
        assertEquals("SHD-2 Rattler", WeaponRegistry.require(WeaponRegistry.SMG_ID).catalogName(EquipmentGrade.SERVICE));
        assertEquals("SA-2 Stalwart",
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID).catalogName(EquipmentGrade.SERVICE));
        assertEquals("RG-4 Longbow", WeaponRegistry.require(WeaponRegistry.DMR_ID).catalogName(EquipmentGrade.MASTERWORK));
    }

    @Test
    void squadAutomaticIsStarterReachableAndJoinsTheMilspecLadder() {
        MarineArmory armory = new MarineArmory();
        assertTrue(armory.isPrimaryUnlocked(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.SERVICE));
        assertEquals(3, armory.ownedPrimary(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.SERVICE));
        assertFalse(armory.isPrimaryUnlocked(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.MILSPEC));

        for (int i = 0; i < 4; i++) armory.recordVictory(false);
        assertTrue(armory.isPrimaryUnlocked(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.MILSPEC));
    }

    @Test
    void fragTemplateCardUnlocksAfterTwoVictoriesWithoutCreatingPrintCurrency() {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.isSecondaryUnlocked(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));
        assertEquals(0, armory.ownedSecondary(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));

        armory.recordVictory(false);
        assertFalse(armory.isSecondaryUnlocked(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));
        armory.recordVictory(false);
        assertTrue(armory.isSecondaryUnlocked(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));
        assertTrue(armory.ownsEquipmentTemplate(
                EquipmentTemplateCatalog.specialId(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID))));
        assertEquals(0, armory.fabricationMaterials());
    }

    @Test
    void saveLoadReinstallsCurrentBuiltInFireSupportDefinition() throws Exception {
        MarineArmory loaded = roundTrip(new MarineArmory());
        FireTeamTemplateCard fireSupport = loaded.templateCardById(
                FireTeamTemplateCards.FIRE_SUPPORT_ID);

        assertNotNull(fireSupport);
        assertEquals(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), fireSupport.billet(1).primaryDef());
        assertEquals(3, loaded.ownedPrimary(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.SERVICE));
    }

    @Test
    void legacyRecipeOwnershipMigratesToEquipmentTemplateCards() throws Exception {
        MarineArmory armory = new MarineArmory();
        armory.unlockPrimary(WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK);
        Field ownedTemplates = MarineArmory.class.getDeclaredField(
                "ownedEquipmentTemplateIds");
        ownedTemplates.setAccessible(true);
        ownedTemplates.set(armory, null);

        MarineArmory loaded = roundTrip(armory);

        assertTrue(loaded.ownsPrimaryTemplate(
                WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK));
        assertTrue(loaded.ownsSpecialTemplate(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID)));
    }

    @Test
    void highRiskProgressionUnlocksMasterworkDmrTemplateCard() {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.isPrimaryUnlocked(WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK));

        for (int i = 0; i < 4; i++) armory.recordVictory(false);
        assertFalse(armory.isPrimaryUnlocked(WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK));

        armory.recordVictory(true);
        assertTrue(armory.isPrimaryUnlocked(WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK));
        assertTrue(armory.ownsEquipmentTemplate(EquipmentTemplateCatalog.primaryId(
                WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK)));
        assertEquals(0, armory.fabricationMaterials());
    }

    @Test
    void collectionSafetyNetReachesTheFiveFifteenThirtyAndFortyVictoryTargets() {
        MarineArmory armory = new MarineArmory();
        assertEquals(14, armory.equipmentTemplateCards().size());

        for (int victory = 1; victory <= 40; victory++) {
            armory.recordVictory(false);
            if (victory == 5) {
                assertEquals(EquipmentCollectionCurve.FIVE_VICTORY_TARGET,
                        armory.equipmentTemplateCards().size());
            } else if (victory == 15) {
                assertEquals(EquipmentCollectionCurve.FIFTEEN_VICTORY_TARGET,
                        armory.equipmentTemplateCards().size());
            } else if (victory == 30) {
                assertEquals(EquipmentCollectionCurve.THIRTY_VICTORY_TARGET,
                        armory.equipmentTemplateCards().size());
            } else if (victory == 40) {
                assertEquals(EquipmentCollectionCurve.FORTY_VICTORY_TARGET,
                        armory.equipmentTemplateCards().size());
            }
        }

        assertTrue(armory.ownsArmorTemplate(MarineArmorPattern.BLUE_SCOUT));
        assertTrue(armory.ownsArmorTemplate(MarineArmorPattern.RED_ELITE));
        assertTrue(armory.ownsPrimaryTemplate(
                WeaponRegistry.DMR_ID, EquipmentGrade.MASTERWORK));
        assertTrue(armory.equipmentTemplateCards().size() < EquipmentTemplateCatalog.all().size(),
                "the safety net must leave faction-source chase cards after its post-30 rung");
    }

    @Test
    void collectionTargetIsMonotonicAndStopsBelowCatalogCompletion() {
        int previous = 0;
        for (int victories = 0; victories <= 100; victories++) {
            int target = EquipmentCollectionCurve.minimumCollectedAtVictories(victories);
            assertTrue(target >= previous);
            previous = target;
        }
        assertEquals(EquipmentCollectionCurve.FORTY_VICTORY_TARGET, previous);
        assertTrue(previous < EquipmentTemplateCatalog.all().size());
    }

    @Test
    void cardsCollectedThroughOtherSourcesCountTowardTheSafetyNet() {
        MarineArmory armory = new MarineArmory();
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            armory.acquireEquipmentTemplate(card.id());
        }

        for (int victory = 0; victory < 30; victory++) armory.recordVictory(false);

        assertEquals(EquipmentTemplateCatalog.all().size(),
                armory.equipmentTemplateCards().size());
    }

    @Test
    void cargoHeldCardsCountWithoutBeingLearnedOrDuplicated() {
        MarineArmory armory = new MarineArmory();
        Set<String> held = Set.of(
                EquipmentTemplateCatalog.primaryId(
                        WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.MILSPEC),
                EquipmentTemplateCatalog.armorId(MarineArmorPattern.BLUE_SCOUT),
                EquipmentTemplateCatalog.armorId(MarineArmorPattern.OUTLAW),
                EquipmentTemplateCatalog.primaryId(
                        WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.MASTERWORK));

        for (int victory = 0; victory < 15; victory++) {
            armory.recordVictory(false, held);
        }

        assertEquals(EquipmentCollectionCurve.FIFTEEN_VICTORY_TARGET,
                armory.equipmentTemplateCards().size() + held.size());
        assertTrue(held.stream().noneMatch(armory::ownsEquipmentTemplate));
    }

    @Test
    void existingLongRunningSaveReceivesTheCurrentCollectionFloor() throws Exception {
        MarineArmory armory = new MarineArmory();
        Field victories = MarineArmory.class.getDeclaredField("victories");
        victories.setAccessible(true);
        victories.setInt(armory, 30);

        MarineArmory loaded = roundTrip(armory);
        loaded.repairCollectionProgression(Set.of());

        assertEquals(EquipmentCollectionCurve.THIRTY_VICTORY_TARGET,
                loaded.equipmentTemplateCards().size());
    }

    @Test
    void fabricationAvailabilityIncludesRecipeAndMaterialCost() {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.canPrintPrimary(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.SERVICE));
        assertFalse(armory.canPrintSecondary(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)));
        assertFalse(armory.canPrintArmor(MarineArmorPattern.CHARCOAL));

        armory.addFabricationMaterials(3);
        assertTrue(armory.canPrintPrimary(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.SERVICE));
        assertTrue(armory.canPrintArmor(MarineArmorPattern.CHARCOAL));
        assertFalse(armory.canPrintSecondary(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)));
        assertFalse(armory.canPrintPrimary(WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK),
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
        assertFalse(roster.allocatePrimary(first.id(), WeaponRegistry.require(WeaponRegistry.DMR_ID),
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
        List<WeaponDef> untouchedWeapons = untouched.stream()
                .map(MarineSoldier::primaryDef).toList();

        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0,
                        FireTeamTemplateCards.FIRE_SUPPORT_ID));

        List<String> teamIds = squad.teamMembers(0);
        assertEquals(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), roster.soldierById(teamIds.get(0)).primaryDef());
        assertEquals(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID),
                roster.soldierById(teamIds.get(1)).primaryDef());
        assertEquals(WeaponRegistry.require(WeaponRegistry.DMR_ID), roster.soldierById(teamIds.get(2)).primaryDef());
        MarineSoldier antiArmor = roster.soldierById(teamIds.get(3));
        assertEquals(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), antiArmor.primaryDef());
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID), antiArmor.specialEquipmentDef());
        for (String teamId : teamIds) {
            assertEquals(MarineArmorPattern.ARMY_GREEN, roster.soldierById(teamId).armor());
        }
        assertEquals(FireTeamTemplateCards.FIRE_SUPPORT_ID, squad.teamTemplateCardId(0));
        assertNull(squad.teamTemplateCardId(1));
        assertEquals(untouchedWeapons, untouched.stream().map(MarineSoldier::primaryDef).toList());
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
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), persisted.specialEquipmentDef());
    }

    @Test
    void breachTemplateIssuesAReusableKitWithAStableIdentity() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);
        assertEquals(2, roster.armory().ownedSecondary(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID)));

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
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), persisted.specialEquipmentDef());
    }

    @Test
    void reusableCardCannotBeOverAssignedPastFiniteStock() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2 * MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);
        assertEquals(FireTeamTemplateResult.APPLIED,
                roster.applyFireTeamTemplate(squad.id(), 0, FireTeamTemplateCards.RECON_ID));

        List<String> secondTeam = squad.teamMembers(1);
        List<WeaponDef> priorWeapons = secondTeam.stream()
                .map(roster::soldierById).map(MarineSoldier::primaryDef).toList();
        assertEquals(FireTeamTemplateResult.INSUFFICIENT_PRIMARIES,
                roster.applyFireTeamTemplate(squad.id(), 1, FireTeamTemplateCards.RECON_ID));

        assertEquals(priorWeapons, secondTeam.stream()
                .map(roster::soldierById).map(MarineSoldier::primaryDef).toList());
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
        FireTeamGearDelta charcoal = delta(preview, "Bastion line armor");
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
        assertEquals(WeaponRegistry.require(WeaponRegistry.SMG_ID),
                roster.soldierById(squad.teamMembers(0).get(0)).primaryDef());
        assertEquals(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID),
                roster.soldierById(squad.teamMembers(1).get(0)).primaryDef());
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
        assertEquals(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID),
                roster.soldierById(squad.teamMembers(0).get(0)).primaryDef());
        assertEquals(WeaponRegistry.require(WeaponRegistry.SMG_ID), wounded.primaryDef());
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
                assertEquals(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID),
                        roster.soldierById(memberId).primaryDef());
            }
        }
    }

    @Test
    void insufficientSecondaryLeavesEveryBilletAndAssignmentUntouched() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().get(0);
        assertTrue(roster.allocateSecondary(squad.teamMembers(1).get(0),
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)));
        List<MarineSoldier> team = squad.teamMembers(0).stream()
                .map(roster::soldierById).toList();
        List<WeaponDef> priorWeapons = team.stream().map(MarineSoldier::primaryDef).toList();
        List<MarineArmorPattern> priorArmor = team.stream().map(MarineSoldier::armor).toList();

        assertEquals(FireTeamTemplateResult.INSUFFICIENT_SECONDARIES,
                roster.applyFireTeamTemplate(squad.id(), 0,
                        FireTeamTemplateCards.FIRE_SUPPORT_ID));

        assertEquals(priorWeapons, team.stream().map(MarineSoldier::primaryDef).toList());
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
        assertFalse(armory.isPrimaryUnlocked(WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK));
        assertFalse(armory.isArmorUnlocked(MarineArmorPattern.RED_ELITE));

        FireTeamTemplateCard card = armory.createTemplateCard("Aspirational Hunters",
                List.of(
                        billet("Leader", WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK,
                                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID), MarineArmorPattern.RED_ELITE),
                        billet("Hunter", WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK,
                                null, MarineArmorPattern.RED_ELITE),
                        billet("Hunter", WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK,
                                null, MarineArmorPattern.RED_ELITE),
                        billet("Hunter", WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK,
                                null, MarineArmorPattern.RED_ELITE)));

        MarineArmory loaded = roundTrip(armory);
        FireTeamTemplateCard persisted = loaded.templateCardById(card.id());
        assertNotNull(persisted);
        assertEquals("Aspirational Hunters", persisted.displayName());
        assertEquals(WeaponRegistry.require(WeaponRegistry.DMR_ID), persisted.billet(0).primaryDef());
        assertEquals(EquipmentGrade.MASTERWORK, persisted.billet(0).grade());
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID), persisted.billet(0).specialDef());
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
                        billet("Leader", WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK,
                                null, MarineArmorPattern.RED_ELITE),
                        billet("Rifleman", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SERVICE,
                                null, MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SERVICE,
                                null, MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SERVICE,
                                null, MarineArmorPattern.ARMORLESS)));

        assertEquals(original.id(), squad.teamTemplateCardId(0));
        assertEquals(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID),
                roster.soldierById(squad.teamMembers(0).get(0)).primaryDef());
        assertFalse(roster.deleteFireTeamTemplate(original.id()));
        assertTrue(roster.deleteFireTeamTemplate(revision.id()));
    }

    @Test
    void customCardRequiresExactlyFourCompleteBillets() {
        MarineArmory armory = new MarineArmory();
        List<FireTeamBillet> three = List.of(
                billet("One", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SERVICE,
                        null, MarineArmorPattern.ARMORLESS),
                billet("Two", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SERVICE,
                        null, MarineArmorPattern.ARMORLESS),
                billet("Three", WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SERVICE,
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
                WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.SERVICE));
        for (int i = 0; i < 3; i++) {
            assertTrue(roster.allocatePrimary(roster.soldiers().get(i).id(),
                    WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.SERVICE));
        }
        MarineSoldier wounded = roster.soldiers().get(0);
        roster.applySoldierOutcome(Collections.singletonMap(
                wounded.id(), MarineSoldierStatus.WIA), 0, 1f, 7f);

        assertFalse(roster.allocatePrimary(roster.soldiers().get(3).id(),
                WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.SERVICE));
    }

    private static FireTeamBillet billet(String name, WeaponDef primary,
                                          EquipmentGrade grade, SpecialEquipmentDef secondary,
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
