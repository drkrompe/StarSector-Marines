package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An early mixed section is the same twelve marines for every company that holds its card. */
class SelfContainedSheetTest {

    private static final List<String> SECTIONS = List.of(
            SquadEquipmentDoctrines.CORPORATE_SPECTER_SECTION_ARMOR,
            SquadEquipmentDoctrines.HEGEMONY_ARBALEST_SECTION_ARMOR,
            SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR,
            SquadEquipmentDoctrines.BOARDING_JACK_SECTION_ARMOR);

    private static MarineArmory armoryHolding(String sheetId) {
        MarineArmory armory = new MarineArmory();
        LoadoutCardLearning.learn(armory, new LoadoutCard(LoadoutCard.Kind.SHEET, sheetId).id());
        return armory;
    }

    private static long powerSuits(SquadArmorDoctrine issued) {
        return issued.issueIds().stream()
                .filter(id -> MarineArmorCatalogRegistry.require(id).armorClass() == ArmorClass.POWER_SUIT)
                .count();
    }

    @Test
    void anotherCompanysStockNeverChangesTheSquad() {
        for (String sheet : SECTIONS) {
            MarineArmory plain = armoryHolding(sheet);
            MarineArmory rich = armoryHolding(sheet);
            for (MarineArmorCatalogDef pattern : MarineArmorCatalogRegistry.installed().all()) {
                rich.acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId(pattern.id()));
            }
            SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(sheet);
            assertEquals(plain.issue(plan).issueIds(), rich.issue(plan).issueIds(),
                    sheet + " must not depend on what else the company owns");
        }
    }

    @Test
    void theCardCarriesEveryPatternTheSectionWears() {
        for (String sheet : SECTIONS) {
            MarineArmory armory = armoryHolding(sheet);
            SquadArmorDoctrine issued = armory.issue(SquadEquipmentDoctrines.armorPlanById(sheet));
            assertTrue(armory.canIssue(SquadEquipmentDoctrines.armorPlanById(sheet)));
            for (String id : issued.issueIds()) {
                assertTrue(armory.ownsArmorTemplate(id), sheet + " issues " + id + " but its card did not teach it");
            }
        }
    }

    @Test
    void earlySectionsFieldFewPowerSuitsInTheStatedPlaces() {
        assertEquals(2, powerSuits(armoryHolding(SECTIONS.get(0)).issue(
                SquadEquipmentDoctrines.armorPlanById(SECTIONS.get(0)))));
        assertEquals(2, powerSuits(armoryHolding(SECTIONS.get(1)).issue(
                SquadEquipmentDoctrines.armorPlanById(SECTIONS.get(1)))));
        assertEquals(4, powerSuits(armoryHolding(SECTIONS.get(2)).issue(
                SquadEquipmentDoctrines.armorPlanById(SECTIONS.get(2)))));
        assertEquals(2, powerSuits(armoryHolding(SECTIONS.get(3)).issue(
                SquadEquipmentDoctrines.armorPlanById(SECTIONS.get(3)))));
    }

    @Test
    void everySheetIsDefinedByItsBundleAndNoBundleTeachesAnotherSheet() {
        List<String> baseline = List.of("armor.field-fatigues", "armor.cordon-shell");
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            assertTrue(!ArmorSheetBundles.patternsFor(plan.id()).isEmpty(), plan.id() + " has no bundle");
        }
        for (SquadArmorPlan held : SquadEquipmentDoctrines.armorPlans()) {
            MarineArmory armory = new MarineArmory();
            for (String pattern : ArmorSheetBundles.patternsFor(held.id())) {
                armory.acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId(pattern));
            }
            for (SquadArmorPlan other : SquadEquipmentDoctrines.armorPlans()) {
                boolean taught = armory.knowsArmorPlan(other.id());
                boolean expected = other.id().equals(held.id())
                        || other.id().equals(SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
                assertEquals(expected, taught,
                        held.id() + " bundle must not teach " + other.id() + " (baseline " + baseline + ")");
            }
        }
    }
}
