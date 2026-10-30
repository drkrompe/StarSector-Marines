package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArmorPlanKnowledgeTest {
    @Test void freshCompanyAndJanusDoNotTeachForeignSheets() {
        MarineArmory armory = new MarineArmory();
        assertEquals(List.of(SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR),
                armory.armorDoctrines().stream().map(SquadArmorDoctrine::id).toList());
        armory.unlockArmor(MarineArmorPattern.BLUE_SCOUT);
        assertFalse(armory.knowsArmorPlan(SquadEquipmentDoctrines.RECON_ARMOR),
                "one suit of a sheet bundle is not the sheet");
        EquipmentTestFixtures.learnSheet(armory, SquadEquipmentDoctrines.RECON_ARMOR);
        assertTrue(armory.knowsArmorPlan(SquadEquipmentDoctrines.RECON_ARMOR));
        for (String foreign : List.of(SquadEquipmentDoctrines.LEAGUE_MOBILE_ARMOR,
                SquadEquipmentDoctrines.SINDRIAN_SECURITY_ARMOR, SquadEquipmentDoctrines.FLEET_COMBAT_ARMOR)) {
            assertFalse(armory.knowsArmorPlan(foreign));
            assertNull(armory.armorDoctrineById(foreign));
        }
    }

    @Test void unknownSheetCannotBeFittedEvenWhenItsKitCouldResolve() {
        MarineRoster roster = new MarineRoster();
        MarineSquad squad = roster.createSquad();
        roster.recruitToSquad(squad.id(), 12);
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(squad.id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));
        String weaponPlan = squad.weaponDoctrineId(), armorPlan = squad.armorDoctrineId();
        var before = roster.soldiers().stream().map(MarineSoldier::armorId).toList();
        assertEquals(SquadEquipmentResult.UNKNOWN_ARMOR_DOCTRINE, roster.applySquadEquipment(squad.id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, SquadEquipmentDoctrines.FLEET_COMBAT_ARMOR));
        assertEquals(before, roster.soldiers().stream().map(MarineSoldier::armorId).toList());
        assertEquals(weaponPlan, squad.weaponDoctrineId());
        assertEquals(armorPlan, squad.armorDoctrineId());
    }

    @Test void freshRoundTripRetainsOnlyTheBaselineSheet() throws Exception {
        MarineRoster loaded = roundTrip(new MarineRoster());
        assertEquals(List.of(SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR),
                knownSheets(loaded.armory()));
        assertEquals(EquipmentTestFixtures.baselineTemplateIds(), loaded.armory().ownedEquipmentTemplateIds());
    }

    @Test void owningEverySuitOfASheetIsNotTheSheetOnlyItsCardIs() {
        MarineArmory armory = new MarineArmory();
        for (String suit : ArmorSheetBundles.patternsFor(SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR)) {
            armory.unlockArmor(suit);
        }
        assertFalse(armory.knowsArmorPlan(SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR));
        EquipmentTestFixtures.learnSheet(armory, SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR);
        assertEquals(Set.of(SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR,
                SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR), Set.copyOf(knownSheets(armory)));
        MarineArmory heavy = new MarineArmory();
        EquipmentTestFixtures.learnSheet(heavy, SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR);
        assertTrue(heavy.canIssue(SquadEquipmentDoctrines.armorPlanById(SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR)));
        assertEquals(Set.of(SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR,
                SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR), Set.copyOf(knownSheets(heavy)));
        assertFalse(heavy.canIssue(SquadEquipmentDoctrines.armorPlanById(SquadEquipmentDoctrines.CORPORATE_HEAVY_ARMOR)));
    }

    private static List<String> knownSheets(MarineArmory armory) {
        return SquadEquipmentDoctrines.armorPlans().stream().map(SquadArmorPlan::id)
                .filter(armory::knowsArmorPlan).toList();
    }

    @Test void everyAuthoredSheetIsACardThatCanBeLearned() {
        MarineArmory armory = new MarineArmory();
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            EquipmentTestFixtures.learnSheet(armory, plan.id());
            assertTrue(armory.knowsArmorPlan(plan.id()), plan.id());
        }
    }

    @Test void aSaveFromBeforeTheCardLedgerKnowsExactlyWhatItCouldField() throws Exception {
        MarineArmory armory = new MarineArmory();
        LoadoutCard assault = new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.ASSAULT_WEAPONS);
        for (String template : assault.templateIds()) armory.acquireEquipmentTemplate(template);
        for (String suit : ArmorSheetBundles.patternsFor(SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR)) {
            armory.unlockArmor(suit);
        }
        var ledger = MarineArmory.class.getDeclaredField("knownLoadoutCardIds");
        ledger.setAccessible(true);
        ledger.set(armory, null);
        MarineArmory loaded = roundTrip(armory);
        assertTrue(loaded.knowsWeaponCard(SquadEquipmentDoctrines.ASSAULT_WEAPONS));
        assertTrue(loaded.knowsArmorPlan(SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR));
        assertTrue(loaded.knowsWeaponCard(SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS));
        assertFalse(loaded.knowsWeaponCard(SquadEquipmentDoctrines.FIRE_SUPPORT_WEAPONS));
    }

    @Test void alternateRefitSheetWaitsForItsTechnologyAndSurvivesSave() throws Exception {
        MarineRoster roster = new MarineRoster();
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertTrue(roster.firstRefit().claim(roster, FirstRefit.Path.STEADY_LINE));
        assertTrue(roster.armory().knowsArmorPlan(FirstRefit.Path.STEADY_LINE.armor));
        assertFalse(roster.armory().knowsArmorPlan(FirstRefit.Path.FORWARD_SCREEN.armor));
        roster.armory().unlockArmor(MarineArmorPattern.BLUE_SCOUT);
        assertFalse(roster.armory().knowsArmorPlan(FirstRefit.Path.FORWARD_SCREEN.armor));
        EquipmentTestFixtures.learnSheet(roster.armory(), FirstRefit.Path.FORWARD_SCREEN.armor);
        assertTrue(roundTrip(roster).armory().knowsArmorPlan(FirstRefit.Path.FORWARD_SCREEN.armor));
    }

    @Test void missingLegacyKnowledgeRetainsAssignedSheetAndClaimWithoutChangingGear() throws Exception {
        MarineRoster roster = new MarineRoster();
        MarineSquad squad = roster.createSquad();
        roster.recruitToSquad(squad.id(), 12);
        EquipmentTestFixtures.learnSheet(roster.armory(), SquadEquipmentDoctrines.LEAGUE_MOBILE_ARMOR);
        assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(squad.id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, SquadEquipmentDoctrines.LEAGUE_MOBILE_ARMOR));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.firstRefit().claim(roster, FirstRefit.Path.STEADY_LINE);
        var templates = Set.copyOf(roster.armory().ownedEquipmentTemplateIds());
        var gear = roster.soldiers().stream().map(MarineSoldier::armorId).toList();
        var field = MarineArmory.class.getDeclaredField("knownArmorPlanIds");
        field.setAccessible(true);
        field.set(roster.armory(), null);
        var ledger = MarineArmory.class.getDeclaredField("knownLoadoutCardIds");
        ledger.setAccessible(true);
        ledger.set(roster.armory(), null);
        MarineRoster loaded = roundTrip(roster);
        assertTrue(loaded.armory().knowsArmorPlan(SquadEquipmentDoctrines.LEAGUE_MOBILE_ARMOR));
        assertTrue(loaded.armory().knowsArmorPlan(FirstRefit.Path.STEADY_LINE.armor));
        assertEquals(templates, loaded.armory().ownedEquipmentTemplateIds());
        assertEquals(gear, loaded.soldiers().stream().map(MarineSoldier::armorId).toList());
    }

    private static MarineRoster roundTrip(MarineRoster roster) throws Exception {
        return (MarineRoster) roundTripObject(roster);
    }

    private static MarineArmory roundTrip(MarineArmory armory) throws Exception {
        return (MarineArmory) roundTripObject(armory);
    }

    private static Object roundTripObject(Object value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(value); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }
}
