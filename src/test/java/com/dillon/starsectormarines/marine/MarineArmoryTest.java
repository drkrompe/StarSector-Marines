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
        assertFalse(armory.isPrimaryUnlocked(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.MILSPEC));

        for (int i = 0; i < 4; i++) armory.recordVictory(false);
        assertTrue(armory.isPrimaryUnlocked(
                WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), EquipmentGrade.MILSPEC));
    }

    @Test
    void fragTemplateCardUnlocksAfterTwoVictories() {
        MarineArmory armory = new MarineArmory();
        assertFalse(armory.isSecondaryUnlocked(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));

        armory.recordVictory(false);
        assertFalse(armory.isSecondaryUnlocked(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));
        armory.recordVictory(false);
        assertTrue(armory.isSecondaryUnlocked(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));
        assertTrue(armory.ownsEquipmentTemplate(
                EquipmentTemplateCatalog.specialId(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID))));
    }

    @Test
    void saveLoadReinstallsCurrentBuiltInFireSupportDefinition() throws Exception {
        MarineArmory loaded = roundTrip(new MarineArmory());
        FireTeamTemplateCard fireSupport = loaded.templateCardById(
                FireTeamTemplateCards.FIRE_SUPPORT_ID);

        assertNotNull(fireSupport);
        assertEquals(WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID), fireSupport.billet(1).primaryDef());
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
    }

    @Test
    void collectionSafetyNetReachesTheFiveFifteenThirtyAndFortyVictoryTargets() {
        MarineArmory armory = new MarineArmory();
        // A floor rather than a census. Pinning the starter count meant every
        // added card broke a test about the collection curve, which is not what
        // this is measuring.
        assertTrue(armory.equipmentTemplateCards().size()
                        >= EquipmentCollectionCurve.minimumCollectedAtVictories(0),
                "starter issue should already satisfy the zero-victory floor");
        assertTrue(armory.ownsArmorTemplate("armor.cordon-shell"));
        assertTrue(armory.ownsArmorTemplate("armor.lashplate-harness"));

        for (int victory = 1; victory <= 40; victory++) {
            armory.recordVictory(false);
            if (victory == 5) {
                assertTrue(armory.equipmentTemplateCards().size()
                                >= EquipmentCollectionCurve.FIVE_VICTORY_TARGET,
                        "the safety net is a floor, not a ceiling");
            } else if (victory == 15) {
                assertTrue(armory.equipmentTemplateCards().size()
                                >= EquipmentCollectionCurve.FIFTEEN_VICTORY_TARGET,
                        "the safety net is a floor, not a ceiling");
            } else if (victory == 30) {
                assertTrue(armory.equipmentTemplateCards().size()
                                >= EquipmentCollectionCurve.THIRTY_VICTORY_TARGET,
                        "the safety net is a floor, not a ceiling");
            } else if (victory == 40) {
                assertTrue(armory.equipmentTemplateCards().size()
                                >= EquipmentCollectionCurve.FORTY_VICTORY_TARGET,
                        "the safety net is a floor, not a ceiling");
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

        assertTrue(armory.equipmentTemplateCards().size() + held.size()
                        >= EquipmentCollectionCurve.FIFTEEN_VICTORY_TARGET,
                "cards held in cargo count toward the floor without being learned");
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
    void fallenSoldiersStayPersistentlyKiaAndSurvivorsStayOnDuty() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(2);
        MarineSoldier survivor = roster.activeSoldiers().get(0);
        MarineSoldier fallen = roster.activeSoldiers().get(1);

        roster.applySoldierOutcome(Set.of(survivor.id()), Set.of(fallen.id()));

        assertEquals(MarineSoldierStatus.ACTIVE, survivor.status());
        assertEquals(MarineSoldierStatus.KIA, fallen.status());
        roster.applySoldierOutcome(Collections.emptySet(), Set.of(fallen.id()));
        assertEquals(MarineSoldierStatus.KIA, fallen.status());
    }

    @Test
    void antiMaterielTemplatePersistsTheStableSpecialIdentity() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(MarineSquad.TEAM_SIZE);
        MarineSquad squad = roster.squads().get(0);

        MarineSoldier carrier = roster.soldierById(squad.teamMembers(0).get(3));
        carrier.setSpecialEquipment(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID);
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

        MarineSoldier carrier = roster.soldierById(squad.teamMembers(0).get(1));
        carrier.setSpecialEquipment(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
        assertEquals(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID,
                carrier.specialEquipmentId());

        MarineRoster loaded = roundTrip(roster);
        MarineSoldier persisted = loaded.soldierById(carrier.id());
        assertEquals(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID,
                persisted.specialEquipmentId());
        assertEquals(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), persisted.specialEquipmentDef());
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

    private static FireTeamBillet billet(String name, WeaponDef primary,
                                          EquipmentGrade grade, SpecialEquipmentDef secondary,
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
