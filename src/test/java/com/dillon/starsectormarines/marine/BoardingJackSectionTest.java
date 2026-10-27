package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.setup.GreyhookRigIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pirate Boarding Jack is a tier-III assault suit a rung below military issue, worn in a
 * light section card that only its story may hand out.
 */
class BoardingJackSectionTest {

    private static final String SUIT = "armor.boarding-jack";
    private static final LoadoutCard CARD = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.BOARDING_JACK_SECTION_ARMOR);

    @Test
    void theSuitSitsInTheRecoveredRungWithItsOwnTrade() {
        MarineArmorCatalogDef jack = MarineArmorCatalogRegistry.require(SUIT);
        MarineArmorCatalogDef specter = MarineArmorCatalogRegistry.require("armor.specter-recovered");
        MarineArmorCatalogDef foundry = MarineArmorCatalogRegistry.require("armor.foundry-breaker");
        assertEquals(ArmorClass.POWER_SUIT, jack.armorClass());
        assertEquals(ArmorRole.ASSAULT, jack.role());
        assertEquals(ArmorTradition.PIRATES, jack.tradition());
        assertEquals(3, jack.tier());
        // A larger raw reserve than the recovered Specter, bought with resistance and handling.
        assertTrue(jack.armorCapacity() > specter.armorCapacity());
        assertTrue(jack.armorRating() < specter.armorRating());
        assertTrue(jack.moveSpeedMult() < specter.moveSpeedMult());
        assertTrue(jack.incomingAccuracyMult() > specter.incomingAccuracyMult());
        // Still far below the tier-IV crude rig it must not be mistaken for.
        assertTrue(jack.armorCapacity() < foundry.armorCapacity() / 2f);
        assertNotEquals(foundry.appearanceFamily(), jack.appearanceFamily());
        assertEquals(LayeredArmorFamily.BOARDING_JACK, jack.appearanceFamily());
        // Greyhook's raiders wear their own paint on the same pattern; the company's issue does not.
        assertEquals(LayeredArmorFamily.BOARDING_JACK_PIRATE, GreyhookRigIssue.drawnFamily(jack));
    }

    @Test
    void theSectionIsLightAndCarriesEveryPatternItWears() {
        MarineArmory armory = new MarineArmory();
        LoadoutCardLearning.learn(armory, CARD.id());
        SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(CARD.sheetId());
        List<String> issued = armory.issue(plan).issueIds();
        assertEquals(MarineSquad.CAPACITY, issued.size());
        assertEquals(2, issued.stream().filter(SUIT::equals).count());
        assertTrue(issued.stream().filter(id -> MarineArmorCatalogRegistry.require(id).armorClass()
                == ArmorClass.POWER_SUIT).count() <= 4, "the arc's kit ceiling is four powered billets");
    }

    @Test
    void onlyItsStoryCanReachIt() {
        String template = EquipmentTemplateCatalog.armorId(SUIT);
        assertEquals("a-small-war", EquipmentTemplateCatalog.installed().storySource(template));
        assertFalse(FactionEquipmentCatalog.installed().entries().stream()
                .flatMap(pool -> pool.offers().stream())
                .anyMatch(offer -> offer.templateId().equals(template)));
        assertTrue(CARD.storyReserved());
        // Its layered art has shipped, so the story may print it; no faction channel sells it.
        assertTrue(CARD.presentationDeclared());
        assertFalse(LoadoutCards.all().contains(CARD));
        assertEquals(CARD.id(), LoadoutCardItemPlugin.itemData(CARD.id()).getData());
    }
}
