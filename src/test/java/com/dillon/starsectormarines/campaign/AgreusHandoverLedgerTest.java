package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.AgreusArcProgress.HandoverStage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The Latchkey custody decision: one explicit signature, ratified only by Chapter 4 victory. */
class AgreusHandoverLedgerTest {
    @Test void nothingCanBeSignedOrDeclinedBeforeTheInspectionSettlesAsAVictory() {
        var p = new AgreusArcProgress();
        assertEquals(HandoverStage.NOT_REACHED, p.handoverStage());
        assertFalse(p.signAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertFalse(p.declineFurtherWork());
        p = afterYard(false);
        assertEquals(HandoverStage.NOT_REACHED, p.handoverStage());
        assertFalse(p.signAgreement(AgreusYardAgreement.YARD_COMPACT));
        assertNull(p.signedAgreement());
    }

    @Test void oneSignatureCommitsOneRouteAndSurvivesReloadAndDefeat() throws Exception {
        var p = afterYard(true);
        assertEquals(HandoverStage.OPEN, p.handoverStage());
        assertNull(p.signedAgreement());
        assertFalse(p.termsOfProtectionInPreparation());
        assertFalse(p.signAgreement(null));
        assertTrue(p.signAgreement(AgreusYardAgreement.YARD_COMPACT));
        assertFalse(p.signAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertFalse(p.signAgreement(AgreusYardAgreement.YARD_COMPACT));
        assertFalse(p.declineFurtherWork());
        assertTrue(p.termsOfProtectionInPreparation());
        assertEquals(AgreusArcProgress.TERMS_PREFIX + 1, p.nextMissionId(), "a signature makes Terms of Protection the next mobilization");
        p = reload(p);
        assertEquals(HandoverStage.SIGNED, p.handoverStage());
        assertEquals(AgreusYardAgreement.YARD_COMPACT, p.signedAgreement());
        assertFalse(p.signAgreement(AgreusYardAgreement.HOUSE_CUSTODY), "reload never reopens the other route");
        assertTrue(p.yardCompleted());
    }

    @Test void onlyAMatchingChapterFourVictoryRatifiesAndOnlyOnce() {
        var p = afterYard(true);
        assertFalse(p.ratifyAgreement(AgreusYardAgreement.HOUSE_CUSTODY), "unsigned routes cannot be ratified");
        assertTrue(p.signAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertFalse(p.ratifyAgreement(AgreusYardAgreement.YARD_COMPACT), "a receipt for the other route is foreign");
        assertFalse(p.ratifyAgreement(null));
        assertFalse(p.agreementRatified());
        assertTrue(p.ratifyAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertEquals(HandoverStage.RATIFIED, p.handoverStage());
        assertFalse(p.ratifyAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertFalse(p.termsOfProtectionInPreparation());
    }

    @Test void custodyPremiumIsDisclosedAtSigningAndPaidOnceAfterRatification() {
        var p = afterYard(true);
        assertEquals(0, p.custodyPremiumDue());
        assertTrue(p.signAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertEquals(12_000, p.custodyPremiumDue());
        assertEquals(0, p.settleCustodyPremium(), "no premium before Chapter 4 ratifies the route");
        assertFalse(p.custodyPremiumSettled());
        assertTrue(p.ratifyAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertEquals(12_000, p.settleCustodyPremium());
        assertEquals(0, p.settleCustodyPremium());
        assertEquals(0, p.custodyPremiumDue());

        var compact = afterYard(true);
        assertTrue(compact.signAgreement(AgreusYardAgreement.YARD_COMPACT));
        assertEquals(0, compact.custodyPremiumDue());
        assertTrue(compact.ratifyAgreement(AgreusYardAgreement.YARD_COMPACT));
        assertEquals(0, compact.settleCustodyPremium());
    }

    @Test void routesCarryOnlyTheirDisclosedConsequences() {
        assertEquals(12_000, AgreusYardAgreement.HOUSE_CUSTODY.completionPremium);
        assertEquals(AgreusYardAgreement.RUSK_OFFICE, AgreusYardAgreement.HOUSE_CUSTODY.escortContactId);
        assertFalse(AgreusYardAgreement.HOUSE_CUSTODY.independentCounterparty);
        assertEquals(0, AgreusYardAgreement.YARD_COMPACT.completionPremium);
        assertEquals(AgreusYardAgreement.LENNA_ASSOCIATION, AgreusYardAgreement.YARD_COMPACT.escortContactId);
        assertTrue(AgreusYardAgreement.YARD_COMPACT.independentCounterparty);
    }

    @Test void decliningBeforeSigningKeepsCompletedWorkAndClosesTheDecision() {
        var p = afterYard(true);
        assertTrue(p.declineFurtherWork());
        assertEquals(HandoverStage.DECLINED, p.handoverStage());
        assertFalse(p.declineFurtherWork());
        assertFalse(p.signAgreement(AgreusYardAgreement.YARD_COMPACT));
        assertFalse(p.ratifyAgreement(AgreusYardAgreement.YARD_COMPACT));
        assertTrue(p.completed() && p.warrantCompleted() && p.yardCompleted());
        assertNull(p.nextMissionId());
    }

    private static AgreusArcProgress reload(AgreusArcProgress p) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (AgreusArcProgress) in.readObject(); }
    }

    private static AgreusArcProgress afterYard(boolean victory) {
        var p = new AgreusArcProgress(); String id = p.missionId();
        assertTrue(p.begin(id, "agreus"));
        assertEquals(26000, p.settle(id, "agreus", true, 26000, true, true, true, true, true, true, 0, 0, false));
        id = p.nextMissionId(); assertTrue(p.begin(id, "agreus"));
        assertEquals(30000, p.settleWarrant(id, "agreus", true, 30000, true, true, true, true, false, 0, 0));
        id = p.nextMissionId(); assertTrue(p.begin(id, "agreus", "Agreus"));
        if (victory) assertEquals(36000, p.settleYard(id, "agreus", true, 36000, true, true, true, true, true, false, 0, 0));
        else assertEquals(0, p.settleYard(id, "agreus", false, 0, true, false, false, false, true, false, 0, 0));
        return p;
    }
}
