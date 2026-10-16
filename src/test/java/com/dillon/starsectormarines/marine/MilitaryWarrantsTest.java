package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Restricted equipment answers to a military warrant, never to reputation or victories. */
class MilitaryWarrantsTest {

    private static final String XIV = EquipmentTemplateCatalog.armorId("armor.heavy");
    private static final String MILITARY_ARBALEST = EquipmentTemplateCatalog.armorId("armor.arbalest");
    private static final String SPECTER = EquipmentTemplateCatalog.armorId("armor.specter-heavy");
    private static final String XIV_SHEET = "loadout-card:sheet:armor:hegemony-shock";

    private static final EquipmentAcquisitionEligibility.Progress EVERYTHING =
            new EquipmentAcquisitionEligibility.Progress(1000, 1000);

    @Test
    void theApprovedSuitsAreRestrictedAndTheCrudeOneIsNot() {
        for (String id : List.of("armor.heavy", "armor.arbalest", "armor.specter-heavy",
                "armor.bulwark-heavy", "armor.reliquary-heavy", "armor.lions-mantle")) {
            assertEquals(EquipmentAccessTier.RESTRICTED, EquipmentTemplateCatalog.armor(id).accessTier(), id);
        }
        assertEquals(EquipmentAccessTier.PRESTIGE, EquipmentTemplateCatalog.armor("armor.foundry-breaker").accessTier(),
                "the pirate road to powered armour stays open and unwarranted");
        assertEquals(EquipmentAccessTier.ADVANCED, EquipmentTemplateCatalog.armor("armor.arbalest-recovered").accessTier());
    }

    @Test
    void maximumProgressRefusesRestrictedKitOnEverySourceWithoutAWarrant() {
        EquipmentTemplateCard xiv = EquipmentTemplateCatalog.require(XIV);
        for (FactionEquipmentSource source : FactionEquipmentSource.values()) {
            assertFalse(EquipmentAcquisitionEligibility.allows(xiv, source, EVERYTHING), source.name());
        }
        assertFalse(EquipmentAcquisitionEligibility.allows(EquipmentAccessTier.RESTRICTED,
                FactionEquipmentSource.PATRON, EVERYTHING), "a tier alone can never answer for a warrant");
    }

    @Test
    void aWarrantOpensOnlyLicenceAndPatronAndOnlyForWhatItNames() {
        EquipmentAcquisitionEligibility.Progress warranted =
                new EquipmentAcquisitionEligibility.Progress(0, 0, Set.of(XIV));
        EquipmentTemplateCard xiv = EquipmentTemplateCatalog.require(XIV);

        assertTrue(EquipmentAcquisitionEligibility.allows(xiv, FactionEquipmentSource.LICENSE, warranted));
        assertTrue(EquipmentAcquisitionEligibility.allows(xiv, FactionEquipmentSource.PATRON, warranted));
        assertFalse(EquipmentAcquisitionEligibility.allows(xiv, FactionEquipmentSource.MARKET, warranted));
        assertFalse(EquipmentAcquisitionEligibility.allows(xiv, FactionEquipmentSource.RECOVERY, warranted));
        assertFalse(EquipmentAcquisitionEligibility.allows(EquipmentTemplateCatalog.require(SPECTER),
                FactionEquipmentSource.LICENSE, warranted));
    }

    @Test
    void theStandingWarrantNeedsBothThePrestigeBandAndCloseStanding() {
        assertTrue(MilitaryWarrants.standingWarrantDue(20, RepLevel.COOPERATIVE));
        assertFalse(MilitaryWarrants.standingWarrantDue(19, RepLevel.COOPERATIVE));
        assertFalse(MilitaryWarrants.standingWarrantDue(20, RepLevel.FRIENDLY));
        assertFalse(MilitaryWarrants.standingWarrantDue(1000, null));
    }

    @Test
    void aStandingWarrantOpensEveryRestrictedSuitItsTraditionBuilds() {
        CampaignState state = new CampaignState();

        List<String> opened = MilitaryWarrants.grantStanding(state, ArmorTradition.HEGEMONY, 40);

        assertEquals(Set.of(XIV, MILITARY_ARBALEST), Set.copyOf(opened));
        assertEquals(Set.of(XIV, MILITARY_ARBALEST), MilitaryWarrants.warrantedTemplateIds(state));
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
        MilitaryWarrants.grant(state, ArmorTradition.HEGEMONY, List.of(MILITARY_ARBALEST),
                MilitaryWarrants.Reason.STORY, 10);

        assertEquals(List.of(XIV), MilitaryWarrants.grantStanding(state, ArmorTradition.HEGEMONY, 90));
        MilitaryWarrants.Held held = MilitaryWarrants.held(state).get(0);
        assertEquals(MilitaryWarrants.Reason.STORY, held.reason());
        assertEquals(10, held.day());
    }

    @Test
    void aWarrantSpeaksOnlyForRestrictedKitItsIssuerBuilds() {
        CampaignState state = new CampaignState();
        assertThrows(IllegalArgumentException.class, () -> MilitaryWarrants.grant(state,
                ArmorTradition.HEGEMONY, List.of(SPECTER), MilitaryWarrants.Reason.STORY, 0));
        assertThrows(IllegalArgumentException.class, () -> MilitaryWarrants.grant(state,
                ArmorTradition.HEGEMONY, List.of(EquipmentTemplateCatalog.armorId("armor.line")),
                MilitaryWarrants.Reason.STORY, 0));
        assertEquals(0, state.militaryWarrantCount);
    }

    @Test
    void onlyTheIssuerOffersWarrantedKitAndOnlyWithTheWarrant() {
        EquipmentAcquisitionEligibility.Progress warranted =
                new EquipmentAcquisitionEligibility.Progress(1000, 1000, Set.of(XIV, MILITARY_ARBALEST));

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
    void theSafetyNetAndVictoryMilestonesNeverGrantRestrictedKit() {
        MarineArmory armory = new MarineArmory();
        for (int win = 0; win < 60; win++) armory.recordVictory(true);

        for (String id : armory.ownedEquipmentTemplateIds()) {
            if (!EquipmentTemplateCatalog.contains(id)) continue;
            assertNotEquals(EquipmentAccessTier.RESTRICTED, EquipmentTemplateCatalog.require(id).accessTier(), id);
        }
    }

    @Test
    void aSuitLearnedThroughTheOldOpenRoutesIsKept() {
        MarineArmory armory = new MarineArmory();
        assertTrue(armory.acquireEquipmentTemplate(XIV));
        for (int win = 0; win < 20; win++) armory.recordVictory(true);

        assertTrue(armory.ownsEquipmentTemplate(XIV), "the gate refuses new acquisition and revokes nothing");
    }

    private static boolean offers(String factionId, FactionEquipmentSource source,
                                  EquipmentAcquisitionEligibility.Progress progress) {
        return LoadoutCardPicker.pick(factionId, source, 100, 7L, Set.of(), progress).contains(XIV_SHEET);
    }
}
