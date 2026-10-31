package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Route;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Terms of Protection pays its fee and ratifies once on either route; the Boarding Jack section is sold in the store. */
class TermsOfProtectionRewardTest {
    private static HeadlessStrings copy;
    @BeforeAll static void shippedCopy() throws Exception { copy = new HeadlessStrings(Path.of("mod")); }
    @AfterAll static void closeCopy() { copy.close(); }
    @Test void eitherRoutePaysAndRatifiesOnce() {
        for (var route : AgreusYardAgreement.values()) {
            var p = signed(route); var m = AgreusArcCampaign.offer(p, "Agreus");
            assertEquals("Terms of Protection", m.name);
            assertEquals(AgreusArcProgress.TERMS_PAYMENT, m.payout);
            assertEquals(route == AgreusYardAgreement.HOUSE_CUSTODY, AgreusArcCampaign.custodyRoute(m));
            assertTrue(m.flavor.contains(Strings.get("agreusArcTermsAward")), "the award is disclosed before launch");
            assertTrue(p.begin(m.id, "agreus", "Agreus"));
            assertEquals(32000, AgreusArcCampaign.settleTerms(p, victory(m, AgreusArcCampaign.route(route))));
            assertTrue(p.agreementRatified());
            assertEquals(-1, AgreusArcCampaign.settleTerms(p, victory(m, AgreusArcCampaign.route(route))));
        }
    }

    @Test void foreignOrIncompleteReceiptsDoNotConsumeTheAttempt() {
        var p = signed(AgreusYardAgreement.YARD_COMPACT); var m = AgreusArcCampaign.offer(p, "Agreus"); p.begin(m.id, "agreus", "Agreus");
        assertEquals(-1, AgreusArcCampaign.settleTerms(p, victory(Mission.builder(m).source(MissionSource.RECURRING).build(), Route.YARD_COMPACT)));
        assertEquals(-1, AgreusArcCampaign.settleTerms(p, victory(m, Route.HOUSE_CUSTODY)), "the other route's receipt is foreign");
        var unratified = MissionOutcome.builder().mission(m).victory(true).payoutEarned(32000)
                .termsOfProtection(new TermsOfProtectionProgress.Report(Route.YARD_COMPACT, true, true, true, true, true, false, true, false, 0, 0, 0, 0, 0)).build();
        assertEquals(-1, AgreusArcCampaign.settleTerms(p, unratified));
        assertFalse(p.agreementRatified());
        assertEquals(32000, AgreusArcCampaign.settleTerms(p, victory(m, Route.YARD_COMPACT)));
    }

    @Test void defeatPaysNothingAndKeepsTheRouteForAReplacementMobilization() {
        var p = signed(AgreusYardAgreement.HOUSE_CUSTODY); var m = AgreusArcCampaign.offer(p, "Agreus"); p.begin(m.id, "agreus", "Agreus");
        var defeat = MissionOutcome.builder().mission(m).victory(false).payoutEarned(0)
                .termsOfProtection(new TermsOfProtectionProgress.Report(Route.HOUSE_CUSTODY, true, true, true, false, false, false, true, false, 2, 1, 0, 3, 1)).build();
        assertEquals(0, AgreusArcCampaign.settleTerms(p, defeat));
        assertFalse(p.agreementRatified());
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
