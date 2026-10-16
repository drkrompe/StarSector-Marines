package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Route;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.fs.starfarer.api.campaign.SpecialItemData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Boarding Jack award on both routes: recorded once on victory and placed in cargo once, an
 * entitlement recorded while the art was still pending delivered on the next load, and never
 * through the card gate's back door.
 */
class TermsOfProtectionRewardTest {
    @Test void eitherRouteRecordsTheEntitlementAndPlacesTheCardOnce() {
        assertTrue(LoadoutCard.parse(AgreusArcProgress.BOARDING_JACK_CARD).presentationDeclared(),
                "the Boarding Jack's layered art has shipped, so its card is awardable");
        for (var route : AgreusYardAgreement.values()) {
            var p = signed(route); var m = AgreusArcCampaign.offer(p, "Agreus");
            assertEquals("Terms of Protection", m.name);
            assertEquals(AgreusArcProgress.TERMS_PAYMENT, m.payout);
            assertEquals(route == AgreusYardAgreement.HOUSE_CUSTODY, AgreusArcCampaign.custodyRoute(m));
            assertTrue(m.flavor.contains(AgreusArcCampaign.TERMS_AWARD), "the award is disclosed before launch");
            assertTrue(p.begin(m.id, "agreus", "Agreus"));
            var cards = new ArrayList<SpecialItemData>(); var cargo = YardOwnersRewardTest.cargo(cards);
            assertEquals(32000, AgreusArcCampaign.settleTerms(p, victory(m, AgreusArcCampaign.route(route)), cargo, Set.of()));
            assertEquals(1, cards.size());
            assertEquals(AgreusArcProgress.BOARDING_JACK_CARD, cards.get(0).getData());
            assertTrue(p.boardingJackEntitled());
            assertFalse(p.boardingJackDeferred());
            assertTrue(p.agreementRatified());
            assertFalse(AgreusArcCampaign.deliverBoardingJack(p, cargo, Set.of()), "delivered once, never again");
            assertEquals(-1, AgreusArcCampaign.settleTerms(p, victory(m, AgreusArcCampaign.route(route)), cargo, Set.of()));
            assertEquals(1, cards.size());
        }
    }

    @Test void anEntitlementRecordedWhileTheArtWasPendingIsDeliveredOnce() {
        // A save from before the art shipped: the victory was recorded, the delivery was refused.
        var p = signed(AgreusYardAgreement.YARD_COMPACT); var m = AgreusArcCampaign.offer(p, "Agreus"); p.begin(m.id, "agreus", "Agreus");
        assertEquals(32000, p.settleTerms(m.id, "agreus", true, 32000, AgreusYardAgreement.YARD_COMPACT,
                true, true, true, true, true, true, false, 1, 0, 1));
        assertTrue(p.boardingJackDeferred());
        var cards = new ArrayList<SpecialItemData>(); var cargo = YardOwnersRewardTest.cargo(cards);
        assertTrue(AgreusArcCampaign.deliverBoardingJack(p, cargo, Set.of()));
        assertEquals(List.of(AgreusArcProgress.BOARDING_JACK_CARD), cards.stream().map(SpecialItemData::getData).toList());
        assertFalse(p.boardingJackDeferred());
        assertFalse(AgreusArcCampaign.deliverBoardingJack(p, cargo, Set.of()));
        assertEquals(1, cards.size());
    }

    @Test void aCardAlreadyLearnedOrCarriedSatisfiesTheDeferredEntitlementWithoutADuplicate() {
        var p = signed(AgreusYardAgreement.HOUSE_CUSTODY); var m = AgreusArcCampaign.offer(p, "Agreus"); p.begin(m.id, "agreus", "Agreus");
        p.settleTerms(m.id, "agreus", true, 32000, AgreusYardAgreement.HOUSE_CUSTODY,
                true, true, true, true, true, true, false, 1, 0, 1);
        var cards = new ArrayList<SpecialItemData>(); var cargo = YardOwnersRewardTest.cargo(cards);
        assertFalse(AgreusArcCampaign.deliverBoardingJack(p, cargo, Set.of(AgreusArcProgress.BOARDING_JACK_CARD)));
        assertTrue(cards.isEmpty());
        assertFalse(p.boardingJackDeferred());
    }

    @Test void foreignIncompleteOrUnavailableReceiptsDoNotConsumeTheAttempt() {
        var p = signed(AgreusYardAgreement.YARD_COMPACT); var m = AgreusArcCampaign.offer(p, "Agreus"); p.begin(m.id, "agreus", "Agreus");
        var cards = new ArrayList<SpecialItemData>(); var cargo = YardOwnersRewardTest.cargo(cards);
        assertEquals(-1, AgreusArcCampaign.settleTerms(p, victory(m, Route.YARD_COMPACT), cargo, null));
        assertEquals(-1, AgreusArcCampaign.settleTerms(p, victory(m, Route.YARD_COMPACT), null, Set.of()));
        assertEquals(-1, AgreusArcCampaign.settleTerms(p, victory(Mission.builder(m).source(MissionSource.RECURRING).build(), Route.YARD_COMPACT), cargo, Set.of()));
        assertEquals(-1, AgreusArcCampaign.settleTerms(p, victory(m, Route.HOUSE_CUSTODY), cargo, Set.of()), "the other route's receipt is foreign");
        var unratified = MissionOutcome.builder().mission(m).victory(true).payoutEarned(32000)
                .termsOfProtection(new TermsOfProtectionProgress.Report(Route.YARD_COMPACT, true, true, true, true, true, false, true, false, 0, 0, 0, 0, 0)).build();
        assertEquals(-1, AgreusArcCampaign.settleTerms(p, unratified, cargo, Set.of()));
        assertFalse(p.agreementRatified()); assertFalse(p.boardingJackEntitled());
        assertEquals(32000, AgreusArcCampaign.settleTerms(p, victory(m, Route.YARD_COMPACT), cargo, Set.of(AgreusArcProgress.BOARDING_JACK_CARD)));
        assertTrue(cards.isEmpty());
    }

    @Test void defeatPaysNothingAndKeepsTheRouteForAReplacementMobilization() {
        var p = signed(AgreusYardAgreement.HOUSE_CUSTODY); var m = AgreusArcCampaign.offer(p, "Agreus"); p.begin(m.id, "agreus", "Agreus");
        var cards = new ArrayList<SpecialItemData>(); var cargo = YardOwnersRewardTest.cargo(cards);
        var defeat = MissionOutcome.builder().mission(m).victory(false).payoutEarned(0)
                .termsOfProtection(new TermsOfProtectionProgress.Report(Route.HOUSE_CUSTODY, true, true, true, false, false, false, true, false, 2, 1, 0, 3, 1)).build();
        assertEquals(0, AgreusArcCampaign.settleTerms(p, defeat, cargo, Set.of()));
        assertFalse(p.boardingJackEntitled()); assertFalse(p.agreementRatified());
        var retry = AgreusArcCampaign.offer(p, "Agreus");
        assertNotEquals(m.id, retry.id);
        assertTrue(retry.flavor.contains("Confirmed prior losses: 2 representatives"), retry.flavor);
        assertTrue(AgreusArcCampaign.custodyRoute(retry));
    }

    static MissionOutcome victory(Mission m, Route route) {
        return MissionOutcome.builder().mission(m).victory(true).payoutEarned(32000)
                .termsOfProtection(new TermsOfProtectionProgress.Report(route, true, true, true, true, true, true, true, false, 1, 0, 1, 0, 0)).build();
    }

    static AgreusArcProgress signed(AgreusYardAgreement route) {
        var p = YardOwnersRewardTest.afterWarrant();
        String id = p.nextMissionId(); p.begin(id, "agreus", "Agreus");
        assertEquals(36000, p.settleYard(id, "agreus", true, 36000, true, true, true, true, true, false, 0, 0));
        assertTrue(p.signAgreement(route));
        return p;
    }
}
