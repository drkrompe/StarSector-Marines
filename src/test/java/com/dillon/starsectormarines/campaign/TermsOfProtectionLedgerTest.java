package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.AgreusArcProgress.HandoverStage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Chapter 4 settles once, under the agreement it launched with, and ratifies only on victory. */
class TermsOfProtectionLedgerTest {
    @Test void onlyASignedRouteOpensChapterFourAndLaunchFreezesIt() {
        var p = afterYard();
        assertNull(p.nextMissionId(), "unsigned custody offers no handover mission");
        assertFalse(p.begin(AgreusArcProgress.TERMS_PREFIX + 1, "agreus", "Agreus"));
        assertTrue(p.signAgreement(AgreusYardAgreement.YARD_COMPACT));
        String id = p.nextMissionId();
        assertEquals(AgreusArcProgress.TERMS_PREFIX + 1, id);
        assertEquals(AgreusArcProgress.TERMS_PAYMENT, p.nextPayment());
        assertTrue(AgreusArcProgress.owns(id));
        assertFalse(p.begin(id, "jangala", "Jangala"));
        assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(AgreusYardAgreement.YARD_COMPACT, p.activeAgreement());
        assertFalse(p.declineFurtherWork());
    }

    @Test void foreignContradictoryOrMismatchedReceiptsWriteNothing() {
        var p = signed(AgreusYardAgreement.HOUSE_CUSTODY); String id = p.nextMissionId();
        assertEquals(-1, win(p, id, AgreusYardAgreement.HOUSE_CUSTODY), "nothing settles before launch");
        assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(-1, win(p, id, AgreusYardAgreement.YARD_COMPACT), "a receipt for the other route is foreign");
        assertEquals(-1, p.settleTerms(id, "asharu", true, 32000, AgreusYardAgreement.HOUSE_CUSTODY, true, true, true, true, true, true, false, 0, 0, 0));
        assertEquals(-1, p.settleTerms(id, "agreus", true, 64000, AgreusYardAgreement.HOUSE_CUSTODY, true, true, true, true, true, true, false, 0, 0, 0));
        assertEquals(-1, p.settleTerms(id, "agreus", true, 32000, AgreusYardAgreement.HOUSE_CUSTODY, true, true, true, false, true, true, false, 0, 0, 0),
                "unresolved attacks cannot be a victory");
        assertEquals(-1, p.settleTerms(id, "agreus", true, 32000, AgreusYardAgreement.HOUSE_CUSTODY, true, true, true, true, true, true, true, 0, 0, 0),
                "a lost records carrier cannot be a victory");
        assertEquals(-1, p.settleTerms(id, "agreus", true, 32000, AgreusYardAgreement.HOUSE_CUSTODY, true, true, true, true, true, true, false, 2, 0, 0),
                "every representative lost cannot be a victory");
        assertEquals(-1, p.settleTerms(id, "agreus", false, 0, AgreusYardAgreement.HOUSE_CUSTODY, true, false, true, false, false, true, false, 0, 0, 0),
                "commissioning without a signature is contradictory");
        assertFalse(p.agreementRatified());
        assertEquals(32000, win(p, id, AgreusYardAgreement.HOUSE_CUSTODY), "technicians lost after the work is done do not void it");
        assertEquals(-1, win(p, id, AgreusYardAgreement.HOUSE_CUSTODY), "a duplicate receipt pays nothing");
    }

    @Test void victoryRatifiesTheLaunchedRouteOnEitherRoute() {
        for (var route : AgreusYardAgreement.values()) {
            var p = signed(route); String id = p.nextMissionId();
            assertTrue(p.begin(id, "agreus", "Agreus"));
            assertEquals(32000, win(p, id, route));
            assertEquals(HandoverStage.RATIFIED, p.handoverStage());
            assertTrue(p.agreementRatified());
            assertTrue(p.termsCompleted());
            assertNull(p.activeAgreement());
            assertFalse(p.ratifyAgreement(route), "ratification happens once");
            assertEquals(route == AgreusYardAgreement.HOUSE_CUSTODY ? 12000 : 0, p.custodyPremiumDue());
        }
    }

    @Test void defeatKeepsTheSignatureRecordsLossesAndOpensAReplacementMobilization() throws Exception {
        var p = signed(AgreusYardAgreement.YARD_COMPACT); String first = p.nextMissionId();
        assertTrue(p.begin(first, "agreus", "Agreus"));
        assertEquals(0, p.settleTerms(first, "agreus", false, 0, AgreusYardAgreement.YARD_COMPACT,
                true, true, false, false, false, true, true, 1, 2, 3));
        assertEquals(HandoverStage.SIGNED, p.handoverStage(), "defeat leaves the signed route standing");
        String retry = p.nextMissionId(); assertNotEquals(first, retry);
        assertFalse(p.signAgreement(AgreusYardAgreement.HOUSE_CUSTODY), "defeat never lets the other route be signed");
        assertTrue(p.begin(retry, "agreus", "Agreus"));
        p = reload(p);
        assertEquals(AgreusYardAgreement.YARD_COMPACT, p.activeAgreement());
        assertEquals(-1, win(p, first, AgreusYardAgreement.YARD_COMPACT), "the spent attempt stays spent");
        assertEquals(32000, win(p, retry, AgreusYardAgreement.YARD_COMPACT));
        assertEquals(2, p.termsRepresentativesLost()); assertEquals(4, p.termsTechniciansLost());
        assertEquals(5, p.termsEmployerLost()); assertEquals(1, p.recordsCarriersLost());
        assertTrue(p.agreementRatified());
    }

    private static int win(AgreusArcProgress p, String id, AgreusYardAgreement route) {
        return p.settleTerms(id, "agreus", true, 32000, route, true, true, true, true, true, true, false, 1, 2, 2);
    }

    private static AgreusArcProgress reload(AgreusArcProgress p) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (AgreusArcProgress) in.readObject(); }
    }

    static AgreusArcProgress signed(AgreusYardAgreement route) {
        var p = afterYard();
        assertTrue(p.signAgreement(route));
        return p;
    }

    static AgreusArcProgress afterYard() {
        var p = new AgreusArcProgress(); String id = p.missionId();
        assertTrue(p.begin(id, "agreus"));
        assertEquals(26000, p.settle(id, "agreus", true, 26000, true, true, true, true, true, true, 0, 0, false));
        id = p.nextMissionId(); assertTrue(p.begin(id, "agreus"));
        assertEquals(30000, p.settleWarrant(id, "agreus", true, 30000, true, true, true, true, false, 0, 0));
        id = p.nextMissionId(); assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(36000, p.settleYard(id, "agreus", true, 36000, true, true, true, true, true, false, 0, 0));
        return p;
    }
}
