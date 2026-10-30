package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A military warrant names Restricted cards; nothing else opens them. */
class MilitaryWarrantsTest {

    private static final String XIV_SHEET = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR).id();
    private static final String AUXILIARY_SECTION = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_SECTION_ARMOR).id();
    private static final String SPECTER_SHEET = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.CORPORATE_HEAVY_ARMOR).id();
    private static final String LEAGUE_LINE = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.LEAGUE_LINE_ARMOR).id();

    private static final EquipmentAcquisitionEligibility.Progress EVERYTHING =
            new EquipmentAcquisitionEligibility.Progress(1000, 1000);

    @Test
    void aCardIsIssuedByTheTraditionThatBuildsItsRestrictedKit() {
        assertEquals(ArmorTradition.HEGEMONY, MilitaryWarrants.issuerOf(LoadoutCard.parse(XIV_SHEET)));
        assertEquals(ArmorTradition.HEGEMONY, MilitaryWarrants.issuerOf(LoadoutCard.parse(AUXILIARY_SECTION)));
        assertNull(MilitaryWarrants.issuerOf(LoadoutCard.parse(LEAGUE_LINE)), "an open card has no issuer");
    }

    @Test
    void maximumProgressRefusesARestrictedCardOnEverySourceWithoutAWarrant() {
        LoadoutCard xiv = LoadoutCard.parse(XIV_SHEET);
        for (FactionEquipmentSource source : FactionEquipmentSource.values()) {
            assertFalse(EquipmentAcquisitionEligibility.allows(xiv, source, EVERYTHING), source.name());
        }
        assertFalse(EquipmentAcquisitionEligibility.allows(EquipmentAccessTier.RESTRICTED,
                FactionEquipmentSource.PATRON, EVERYTHING), "a tier alone can never answer for a warrant");
    }

    @Test
    void aWarrantOpensOnlyLicenceAndPatronAndOnlyForTheCardsItNames() {
        EquipmentAcquisitionEligibility.Progress warranted =
                new EquipmentAcquisitionEligibility.Progress(0, 0, Set.of(XIV_SHEET));
        LoadoutCard xiv = LoadoutCard.parse(XIV_SHEET);

        assertTrue(EquipmentAcquisitionEligibility.allows(xiv, FactionEquipmentSource.LICENSE, warranted));
        assertTrue(EquipmentAcquisitionEligibility.allows(xiv, FactionEquipmentSource.PATRON, warranted));
        assertFalse(EquipmentAcquisitionEligibility.allows(xiv, FactionEquipmentSource.MARKET, warranted));
        assertFalse(EquipmentAcquisitionEligibility.allows(xiv, FactionEquipmentSource.RECOVERY, warranted));
        assertFalse(EquipmentAcquisitionEligibility.allows(LoadoutCard.parse(AUXILIARY_SECTION),
                FactionEquipmentSource.LICENSE, warranted), "a warrant opens the cards it names, not their kit");
    }

    @Test
    void theStandingWarrantNeedsBothThePrestigeBandAndCloseStanding() {
        assertTrue(MilitaryWarrants.standingWarrantDue(20, RepLevel.COOPERATIVE));
        assertFalse(MilitaryWarrants.standingWarrantDue(19, RepLevel.COOPERATIVE));
        assertFalse(MilitaryWarrants.standingWarrantDue(20, RepLevel.FRIENDLY));
        assertFalse(MilitaryWarrants.standingWarrantDue(1000, null));
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
    void onlyTheIssuerOffersAWarrantedCardAndOnlyWithTheWarrant() {
        EquipmentAcquisitionEligibility.Progress warranted =
                new EquipmentAcquisitionEligibility.Progress(1000, 1000, Set.of(XIV_SHEET));

        assertFalse(offers("hegemony", FactionEquipmentSource.LICENSE, EVERYTHING),
                "without the warrant the Hegemony itself does not sell it");
        assertTrue(offers("hegemony", FactionEquipmentSource.LICENSE, warranted));
        assertTrue(offers("hegemony", FactionEquipmentSource.PATRON, warranted));
        assertFalse(offers("hegemony", FactionEquipmentSource.RECOVERY, warranted));
        for (String faction : List.of("independent", "sindrian_diktat", "lions_guard", "knights_of_ludd", "pirates")) {
            for (FactionEquipmentSource source : FactionEquipmentSource.values()) {
                assertFalse(offers(faction, source, warranted), faction + " " + source);
            }
        }
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

    private static boolean offers(String factionId, FactionEquipmentSource source,
                                  EquipmentAcquisitionEligibility.Progress progress) {
        return LoadoutCardPicker.pick(factionId, source, 100, 7L, Set.of(), progress).contains(XIV_SHEET);
    }
}
