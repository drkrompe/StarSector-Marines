package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleasedKitTest {

    private static final String ADVANCED_PATTERN = "armor.combat";
    private static final String COMMON_PATTERN = "armor.militia";

    @Test
    void theCommonBandIsReleasedByConstructionAndOccupiesNoRow() {
        CampaignState state = new CampaignState();
        EquipmentTemplateCard common = EquipmentTemplateCatalog.armor(COMMON_PATTERN);

        assertEquals(EquipmentAccessTier.COMMON, common.accessTier());
        assertTrue(ReleasedKit.isReleased(state, common),
                "a card every market sells needs no release");
        assertFalse(ReleasedKit.release(state, common.id()),
                "releasing it changes nothing");
        assertEquals(0, state.releasedKitCount,
                "and it is never written to the save, where it could disagree with the catalog");
    }

    @Test
    void aHigherBandCardIsIssuedOnlyOnceItIsReleased() {
        CampaignState state = new CampaignState();
        EquipmentTemplateCard advanced = EquipmentTemplateCatalog.armor(ADVANCED_PATTERN);

        assertFalse(ReleasedKit.isReleased(state, advanced));
        assertTrue(ReleasedKit.release(state, advanced.id()));
        assertTrue(ReleasedKit.isReleased(state, advanced));
        assertEquals(1, state.releasedKitCount);
    }

    @Test
    void aSecondReleaseOfTheSameCardIsANoOp() {
        CampaignState state = new CampaignState();
        String cardId = EquipmentTemplateCatalog.armorId(ADVANCED_PATTERN);

        assertTrue(ReleasedKit.release(state, cardId));
        assertFalse(ReleasedKit.release(state, cardId));
        assertEquals(1, state.releasedKitCount);
    }

    /** A release names a definition, so an id nobody can resolve is a caller mistake. */
    @Test
    void anUnknownCardIsRefused() {
        CampaignState state = new CampaignState();

        assertThrows(IllegalArgumentException.class,
                () -> ReleasedKit.release(state, "equipment-template:no-such-thing"));
        assertEquals(0, state.releasedKitCount);
    }

    @Test
    void aPolityGivenNothingStillDrawsTheWholeCommonFloor() {
        CampaignState state = new CampaignState();

        List<EquipmentTemplateCard> cards = ReleasedKit.releasedCards(state);

        assertFalse(cards.isEmpty());
        for (EquipmentTemplateCard card : cards) {
            assertEquals(EquipmentAccessTier.COMMON, card.accessTier(),
                    card.id() + " is above the floor and nobody released it");
        }
        assertTrue(ids(cards).contains(EquipmentTemplateCatalog.armorId(COMMON_PATTERN)));
    }

    @Test
    void aReleasedCardJoinsTheFloorRatherThanReplacingIt() {
        CampaignState state = new CampaignState();
        int floorSize = ReleasedKit.releasedCards(state).size();
        ReleasedKit.release(state, EquipmentTemplateCatalog.armorId(ADVANCED_PATTERN));

        List<EquipmentTemplateCard> cards = ReleasedKit.releasedCards(state);

        assertEquals(floorSize + 1, cards.size());
        assertTrue(ids(cards).contains(EquipmentTemplateCatalog.armorId(ADVANCED_PATTERN)));
    }

    /** A save can outlive a card. That is one template fewer, not a game that will not load. */
    @Test
    void aCardTheCatalogNoLongerKnowsIsSkipped() {
        CampaignState state = new CampaignState();
        state.recordReleasedKit(state.equipmentTemplateRegistry.intern(
                "equipment-template:retired-in-a-later-version"));

        List<EquipmentTemplateCard> cards = ReleasedKit.releasedCards(state);

        for (EquipmentTemplateCard card : cards) {
            assertEquals(EquipmentAccessTier.COMMON, card.accessTier());
        }
    }

    /**
     * The Armory says what could be released and is never written by any of this
     * (law 2): a release grants a definition, and the company keeps its own kit.
     */
    @Test
    void whatIsReleasableComesFromTheArmoryAndTheArmoryIsUntouched() {
        CampaignState state = new CampaignState();
        MarineArmory armory = new MarineArmory();
        String advanced = EquipmentTemplateCatalog.armorId(ADVANCED_PATTERN);
        armory.acquireEquipmentTemplate(advanced);
        int owned = armory.ownedEquipmentTemplateIds().size();

        assertTrue(ids(ReleasedKit.releasable(state, armory)).contains(advanced));
        ReleasedKit.release(state, advanced);

        assertFalse(ids(ReleasedKit.releasable(state, armory)).contains(advanced),
                "a card already released is not offered again");
        assertEquals(owned, armory.ownedEquipmentTemplateIds().size(),
                "a release moves no stock and drops no card");
        assertTrue(armory.ownsEquipmentTemplate(advanced));
    }

    @Test
    void nothingCommonAndNothingUnownedIsOfferedForRelease() {
        CampaignState state = new CampaignState();
        MarineArmory armory = new MarineArmory();
        armory.acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId(COMMON_PATTERN));

        for (EquipmentTemplateCard card : ReleasedKit.releasable(state, armory)) {
            assertTrue(card.accessTier() != EquipmentAccessTier.COMMON,
                    "the Common band needs no release: " + card.id());
            assertTrue(armory.ownsEquipmentTemplate(card.id()),
                    "the company cannot release what it does not hold: " + card.id());
        }
        assertEquals(List.of(), ReleasedKit.releasable(state, null));
    }

    /** A primary card is released the same way, band by band, whatever its grade. */
    @Test
    void anAdvancedPrimaryIsReleasedLikeAnyOtherCard() {
        CampaignState state = new CampaignState();
        EquipmentTemplateCard milspec =
                EquipmentTemplateCatalog.primary("weapon.pulse-rifle", EquipmentGrade.MILSPEC);

        assertEquals(EquipmentAccessTier.ADVANCED, milspec.accessTier());
        assertTrue(ReleasedKit.release(state, milspec.id()));
        assertTrue(ids(ReleasedKit.releasedCards(state)).contains(milspec.id()));
    }

    private static TreeSet<String> ids(List<EquipmentTemplateCard> cards) {
        TreeSet<String> ids = new TreeSet<>();
        for (EquipmentTemplateCard card : cards) ids.add(card.id());
        return ids;
    }
}
