package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A military warrant names Restricted cards; it no longer gates acquiring them, the Kit Ladder does. */
class MilitaryWarrantsTest {

    private static final String XIV_SHEET = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR).id();
    private static final String AUXILIARY_SECTION = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_SECTION_ARMOR).id();
    private static final String SPECTER_SHEET = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.CORPORATE_HEAVY_ARMOR).id();
    private static final String LEAGUE_LINE = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.LEAGUE_LINE_ARMOR).id();

    @Test
    void aCardIsIssuedByTheTraditionThatBuildsItsRestrictedKit() {
        assertEquals(ArmorTradition.HEGEMONY, MilitaryWarrants.issuerOf(LoadoutCard.parse(XIV_SHEET)));
        assertEquals(ArmorTradition.HEGEMONY, MilitaryWarrants.issuerOf(LoadoutCard.parse(AUXILIARY_SECTION)));
        assertNull(MilitaryWarrants.issuerOf(LoadoutCard.parse(LEAGUE_LINE)), "an open card has no issuer");
    }

    @Test
    void aRestrictedCardIsSoldAtItsStationWithoutAWarrant() {
        LoadoutCard xiv = LoadoutCard.parse(XIV_SHEET);
        UnlockStation station = KitLadder.station(XIV_SHEET);
        Set<UnlockStation> before = new HashSet<>();
        for (UnlockStation reached : UnlockStation.all()) if (reached.compareTo(station) < 0) before.add(reached);
        EquipmentAcquisitionEligibility.Progress early = new EquipmentAcquisitionEligibility.Progress(0, before);
        EquipmentAcquisitionEligibility.Progress late = new EquipmentAcquisitionEligibility.Progress(0);
        assertFalse(StoreStock.cards(Set.of(), early).allCardIds().contains(XIV_SHEET), "not before its station");
        assertTrue(StoreStock.cards(Set.of(), late).marketCardIds().contains(XIV_SHEET),
                "no warrant, standing or faction asked: the station is the gate");
        assertTrue(EquipmentAcquisitionEligibility.allows(xiv, FactionEquipmentSource.PATRON, late));
        assertFalse(EquipmentAcquisitionEligibility.allows(EquipmentAccessTier.RESTRICTED,
                FactionEquipmentSource.PATRON, late), "a single Restricted template still comes only inside a card");
    }

    @Test
    void theStandingWarrantNeedsOnlyCloseStanding() {
        assertTrue(MilitaryWarrants.standingWarrantDue(RepLevel.COOPERATIVE));
        assertFalse(MilitaryWarrants.standingWarrantDue(RepLevel.FRIENDLY));
        assertFalse(MilitaryWarrants.standingWarrantDue(null));
    }

    @Test
    void aStandingWarrantOpensEveryRestrictedCardItsTraditionIssues() {
        CampaignState state = new CampaignState();

        List<String> opened = MilitaryWarrants.grantStanding(state, ArmorTradition.HEGEMONY, 40);

        assertTrue(opened.containsAll(List.of(XIV_SHEET, AUXILIARY_SECTION)));
        assertEquals(Set.copyOf(opened), MilitaryWarrants.warrantedCardIds(state));
        assertFalse(opened.contains(SPECTER_SHEET));
        assertTrue(MilitaryWarrants.holds(state, ArmorTradition.HEGEMONY));
        assertFalse(MilitaryWarrants.holds(state, ArmorTradition.TRITACHYON));
        MilitaryWarrants.Held held = MilitaryWarrants.held(state).get(0);
        assertEquals(MilitaryWarrants.Reason.STANDING, held.reason());
        assertEquals(40, held.day());
        assertTrue(MilitaryWarrants.grantStanding(state, ArmorTradition.HEGEMONY, 41).isEmpty(),
                "a second grant opens nothing new");
    }

    @Test
    void aStandingWarrantCompletesAPartialAuthoredOneWithoutRewritingIt() {
        CampaignState state = new CampaignState();
        MilitaryWarrants.grant(state, ArmorTradition.HEGEMONY, List.of(AUXILIARY_SECTION),
                MilitaryWarrants.Reason.STORY, 10);

        List<String> opened = MilitaryWarrants.grantStanding(state, ArmorTradition.HEGEMONY, 90);
        assertTrue(opened.contains(XIV_SHEET));
        assertFalse(opened.contains(AUXILIARY_SECTION));
        MilitaryWarrants.Held held = MilitaryWarrants.held(state).get(0);
        assertEquals(MilitaryWarrants.Reason.STORY, held.reason());
        assertEquals(10, held.day());
    }

    @Test
    void aWarrantSpeaksOnlyForRestrictedCardsItsIssuerIssues() {
        CampaignState state = new CampaignState();
        assertThrows(IllegalArgumentException.class, () -> MilitaryWarrants.grant(state,
                ArmorTradition.HEGEMONY, List.of(SPECTER_SHEET), MilitaryWarrants.Reason.STORY, 0));
        assertThrows(IllegalArgumentException.class, () -> MilitaryWarrants.grant(state,
                ArmorTradition.HEGEMONY, List.of(LEAGUE_LINE), MilitaryWarrants.Reason.STORY, 0));
        assertThrows(IllegalArgumentException.class, () -> MilitaryWarrants.grant(state,
                ArmorTradition.HEGEMONY, List.of(EquipmentTemplateCatalog.armorId("armor.heavy")),
                MilitaryWarrants.Reason.STORY, 0), "a warrant names cards, never a suit");
        assertEquals(0, state.militaryWarrantCount);
    }

    @Test
    void aSaveFromBeforeCardWarrantsOpensExactlyTheCardsItsSuitsCovered() {
        CampaignState state = new CampaignState();
        int hegemony = state.factionRegistry.intern(ArmorTradition.HEGEMONY.key);
        int arbalest = state.equipmentTemplateRegistry.intern(EquipmentTemplateCatalog.armorId("armor.arbalest"));
        state.militaryWarrantIssuerId[0] = hegemony;
        state.militaryWarrantTemplateId[0] = arbalest;
        state.militaryWarrantReason[0] = MilitaryWarrants.Reason.STORY.code;
        state.militaryWarrantDay[0] = 12;
        state.militaryWarrantCount = 1;

        Set<String> warranted = MilitaryWarrants.warrantedCardIds(state);

        assertTrue(warranted.contains(AUXILIARY_SECTION), "every Restricted suit it carries was warranted");
        assertFalse(warranted.contains(XIV_SHEET), "the XIV was not");
        MilitaryWarrants.Held held = MilitaryWarrants.held(state).get(0);
        assertEquals(MilitaryWarrants.Reason.STORY, held.reason());
        assertEquals(12, held.day());
    }

    @Test
    void theSafetyNetNeverGrantsARestrictedCard() {
        MarineArmory armory = new MarineArmory();
        for (int win = 0; win < 60; win++) armory.recordVictory(true);

        for (String id : armory.knownLoadoutCardIds()) {
            LoadoutCard card = LoadoutCard.parse(id);
            if (card == null || !card.defined()) continue;
            assertNotEquals(EquipmentAccessTier.RESTRICTED, card.accessTier(), id);
        }
    }
}
