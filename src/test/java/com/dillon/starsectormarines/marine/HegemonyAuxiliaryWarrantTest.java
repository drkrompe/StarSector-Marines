package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.AuxiliaryWarrantProgress;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Hegemony takes on a company it has watched fight: once, in two stages. */
class HegemonyAuxiliaryWarrantTest {

    private static final String AUXILIARY_SECTION = "loadout-card:sheet:armor:hegemony-auxiliary-section";
    private static final String XIV_SHEET = "loadout-card:sheet:armor:hegemony-shock";

    @Test
    void theOfferWaitsForASmallWarAndTheLicenceThresholdAndComesOnce() {
        AuxiliaryWarrantProgress fresh = new AuxiliaryWarrantProgress();
        assertFalse(HegemonyAuxiliaryWarrant.offerDue(false, RepLevel.COOPERATIVE, fresh));
        assertFalse(HegemonyAuxiliaryWarrant.offerDue(true, RepLevel.NEUTRAL, fresh));
        assertFalse(HegemonyAuxiliaryWarrant.offerDue(true, RepLevel.HOSTILE, fresh));
        assertFalse(HegemonyAuxiliaryWarrant.offerDue(true, null, fresh));
        assertTrue(HegemonyAuxiliaryWarrant.offerDue(true, RepLevel.FAVORABLE, fresh));

        CampaignState state = new CampaignState();
        assertTrue(HegemonyAuxiliaryWarrant.offer(state, 10));
        assertFalse(HegemonyAuxiliaryWarrant.offer(state, 11), "never twice");
        assertFalse(HegemonyAuxiliaryWarrant.offerDue(true, RepLevel.FRIENDLY, state.auxiliaryWarrant()));
    }

    @Test
    void acceptanceRecordsAHegemonyWarrantNamingOnlyTheAuxiliarySection() {
        CampaignState state = offered();
        assertTrue(HegemonyAuxiliaryWarrant.accept(state, 20));
        assertEquals(Set.of(HegemonyAuxiliaryWarrant.STAGE_ONE), MilitaryWarrants.warrantedCardIds(state));
        List<MilitaryWarrants.Held> held = MilitaryWarrants.held(state);
        assertEquals(1, held.size());
        assertEquals(ArmorTradition.HEGEMONY, held.get(0).issuer());
        assertEquals(MilitaryWarrants.Reason.STORY, held.get(0).reason());

        assertFalse(HegemonyAuxiliaryWarrant.accept(state, 21), "a replayed answer grants nothing");
        assertFalse(HegemonyAuxiliaryWarrant.decline(state, 21));
        assertEquals(1, state.militaryWarrantCount);
    }

    @Test
    void theXivFollowsOnlyTheFirstHonouredPeriodOfAnEnrolledCompany() {
        CampaignState state = offered();
        assertFalse(HegemonyAuxiliaryWarrant.honouredPeriod(state, 15), "not before enrolment");
        HegemonyAuxiliaryWarrant.accept(state, 20);
        assertFalse(MilitaryWarrants.warrantedCardIds(state).contains(HegemonyAuxiliaryWarrant.STAGE_TWO));

        assertTrue(HegemonyAuxiliaryWarrant.honouredPeriod(state, 110));
        assertEquals(Set.of(HegemonyAuxiliaryWarrant.STAGE_ONE, HegemonyAuxiliaryWarrant.STAGE_TWO),
                MilitaryWarrants.warrantedCardIds(state));
        assertFalse(HegemonyAuxiliaryWarrant.honouredPeriod(state, 200), "once");
        assertEquals(2, state.militaryWarrantCount);
    }

    @Test
    void declineIsARealAnswerAndIsNeverAskedAgain() {
        CampaignState state = offered();
        assertTrue(HegemonyAuxiliaryWarrant.decline(state, 20));
        assertFalse(HegemonyAuxiliaryWarrant.accept(state, 21));
        assertFalse(HegemonyAuxiliaryWarrant.honouredPeriod(state, 110));
        assertFalse(HegemonyAuxiliaryWarrant.offer(state, 30));
        assertTrue(MilitaryWarrants.warrantedCardIds(state).isEmpty());
        assertTrue(MilitaryWarrants.standingWarrantDue(EquipmentAcquisitionEligibility.requiredMrb(
                EquipmentAccessTier.PRESTIGE), RepLevel.COOPERATIVE), "the standing route stays open");
    }

    @Test
    void underStageOneOnlyTheHegemonyLicenceAndPatronOfferTheAuxiliarySection() {
        EquipmentAcquisitionEligibility.Progress warranted = new EquipmentAcquisitionEligibility.Progress(
                99, 100, Set.of(HegemonyAuxiliaryWarrant.STAGE_ONE));
        List<String> licence = StoreStock.cards("hegemony", true, Set.of(), warranted).licensedCardIds();
        assertTrue(licence.contains(AUXILIARY_SECTION));
        assertFalse(licence.contains(XIV_SHEET), "the XIV waits for stage two");
        assertTrue(pick("hegemony", FactionEquipmentSource.PATRON, warranted).contains(AUXILIARY_SECTION));

        assertFalse(StoreStock.cards("hegemony", false, Set.of(), warranted).allCardIds().contains(AUXILIARY_SECTION));
        assertFalse(pick("hegemony", FactionEquipmentSource.RECOVERY, warranted).contains(AUXILIARY_SECTION));
        for (String other : List.of("independent", "tritachyon", "persean", "pirates")) {
            assertFalse(StoreStock.cards(other, true, Set.of(), warranted).allCardIds().contains(AUXILIARY_SECTION),
                    other + " store");
            for (FactionEquipmentSource source : List.of(FactionEquipmentSource.PATRON, FactionEquipmentSource.RECOVERY)) {
                assertFalse(pick(other, source, warranted).contains(AUXILIARY_SECTION), other + " " + source);
            }
        }
        assertFalse(StoreStock.cards("hegemony", true, Set.of(), new EquipmentAcquisitionEligibility.Progress(99, 100))
                .allCardIds().contains(AUXILIARY_SECTION), "no warrant, no approved suit");
    }

    @Test
    void anOldSaveStartsWithTheOfferUnsent() {
        CampaignState legacy = new CampaignState();
        legacy.hegemonyAuxiliary = null;
        assertEquals(AuxiliaryWarrantProgress.Stage.NONE, legacy.auxiliaryWarrant().stage());
    }

    private static CampaignState offered() {
        CampaignState state = new CampaignState();
        HegemonyAuxiliaryWarrant.offer(state, 10);
        return state;
    }

    private static List<String> pick(String faction, FactionEquipmentSource source,
                                     EquipmentAcquisitionEligibility.Progress progress) {
        return LoadoutCardPicker.pick(faction, source, 500, 7L, Set.of(), progress);
    }
}
