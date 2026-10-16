package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.AgreusArcProgress.HandoverStage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgreusDistrictEscortLedgerTest {
    private static final int FEE = AgreusDistrictEscortProgress.PAYMENT;

    @Test void offeredOnlyAfterChapterFiveSettlesUnderARatifiedAgreement() {
        for (var agreement : AgreusYardAgreement.values()) {
            assertTrue(AgreusDistrictEscortProgress.offerable(true, HandoverStage.RATIFIED, agreement));
            assertFalse(AgreusDistrictEscortProgress.offerable(false, HandoverStage.RATIFIED, agreement));
            for (var stage : HandoverStage.values()) if (stage != HandoverStage.RATIFIED)
                assertFalse(AgreusDistrictEscortProgress.offerable(true, stage, agreement), stage.name());
        }
        assertFalse(AgreusDistrictEscortProgress.offerable(true, HandoverStage.RATIFIED, null));
        assertFalse(AgreusDistrictEscortProgress.offerable(new AgreusArcProgress()));
        assertFalse(AgreusDistrictEscortProgress.offerable(null));
    }

    @Test void theSignedAgreementNamesTheIssuingContact() {
        assertEquals(AgreusYardAgreement.RUSK_OFFICE, new AgreusDistrictEscortProgress(AgreusYardAgreement.HOUSE_CUSTODY).contactId());
        assertEquals(AgreusYardAgreement.LENNA_ASSOCIATION, new AgreusDistrictEscortProgress(AgreusYardAgreement.YARD_COMPACT).contactId());
        assertThrows(IllegalArgumentException.class, () -> new AgreusDistrictEscortProgress(null));
    }

    @Test void everyAttemptHasAFreshIdentityAndConsignment() {
        var p = new AgreusDistrictEscortProgress(AgreusYardAgreement.YARD_COMPACT);
        var ids = new HashSet<String>(); var loads = new HashSet<String>();
        boolean[] results = {false, true, false, true, true};
        for (boolean won : results) {
            assertTrue(ids.add(p.missionId())); assertTrue(loads.add(p.consignmentId()));
            assertEquals(AgreusYardAgreement.YARD_COMPACT, AgreusDistrictEscortProgress.agreementOf(p.missionId()));
            String id = p.missionId();
            assertTrue(p.begin(id, "agreus", "Agreus"));
            assertEquals(won ? FEE : 0, won ? settleWon(p, id) : settleLost(p, id));
        }
        assertEquals(3, p.jobsCompleted()); assertEquals(3 * FEE, p.paid());
        assertEquals(4, p.job()); assertEquals(6, p.attempt());
        assertEquals(2, p.consignmentsLost());
    }

    @Test void settlesOnceAndRefusesStaleDuplicateForeignOrInconsistentOutcomes() {
        var p = new AgreusDistrictEscortProgress(AgreusYardAgreement.HOUSE_CUSTODY);
        String id = p.missionId();
        assertEquals(-1, settleWon(p, id), "an attempt that never launched cannot settle");
        assertFalse(p.begin(id, "jangala", "Jangala"));
        assertTrue(p.begin(id, "agreus", "Agreus"));
        assertFalse(p.begin(id, "agreus", "Agreus"), "one launch per attempt");
        String compact = id.replace("_custody_", "_compact_");
        assertNull(AgreusDistrictEscortProgress.agreementOf("agreus_yard_owners_v1_1"));
        assertEquals(AgreusYardAgreement.YARD_COMPACT, AgreusDistrictEscortProgress.agreementOf(compact));
        assertEquals(-1, p.settle(compact, "agreus", true, FEE, true, true, true, true, false, 0, 0), "other agreement");
        assertEquals(-1, p.settle("agreus_limited_warrant_v1_1", "agreus", true, FEE, true, true, true, true, false, 0, 0));
        assertEquals(-1, p.settle(id, "jangala", true, FEE, true, true, true, true, false, 0, 0), "other site");
        assertEquals(-1, p.settle(id, "agreus", true, FEE + 12_000, true, true, true, true, false, 0, 0), "no premium rides along");
        assertEquals(-1, p.settle(id, "agreus", true, FEE, true, true, true, true, true, 0, 0), "victory with a lost load");
        assertEquals(-1, p.settle(id, "agreus", true, FEE, true, true, true, true, false, 2, 0), "victory with the party lost");
        assertEquals(-1, p.settle(id, "agreus", true, FEE, false, true, true, true, false, 0, 0), "delivery without link-up");
        assertEquals(-1, p.settle(id, "agreus", false, 0, true, true, true, true, false, 0, 0), "defeat contradicting a complete receipt");
        assertEquals(-1, p.settle(id, "agreus", false, FEE, false, false, false, false, true, 0, 0), "paid defeat");
        assertEquals(-1, p.settle(id, "agreus", false, 0, true, false, false, false, false, 3, 0), "impossible party loss");
        assertEquals(0, p.paid()); assertTrue(p.active());
        assertEquals(FEE, p.settle(id, "agreus", true, FEE, true, true, true, true, false, 1, 2));
        assertEquals(-1, p.settle(id, "agreus", true, FEE, true, true, true, true, false, 1, 2), "duplicate");
        assertFalse(p.begin(id, "agreus", "Agreus"), "a settled identity is never reissued");
        assertEquals(FEE, p.paid()); assertEquals(1, p.partyLost()); assertEquals(2, p.constablesLost());
        assertEquals(id, p.lastMissionId()); assertTrue(p.lastVictory());
    }

    @Test void escortSettlementLeavesTheArcsReceiptsUntouchedAndSurvivesReload() throws Exception {
        var arc = new AgreusArcProgress();
        var before = new Object[]{arc.handoverStage(), arc.signedAgreement(), arc.agreementRatified(), arc.custodyPremiumDue(), arc.custodyPremiumSettled(), arc.smallWarSettled()};
        var p = new AgreusDistrictEscortProgress(AgreusYardAgreement.HOUSE_CUSTODY);
        String id = p.missionId(); p.begin(id, "agreus", "Agreus");
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { p = (AgreusDistrictEscortProgress) in.readObject(); }
        assertTrue(p.acceptsTarget("Agreus"));
        assertEquals(FEE, settleWon(p, id));
        assertArrayEquals(before, new Object[]{arc.handoverStage(), arc.signedAgreement(), arc.agreementRatified(), arc.custodyPremiumDue(), arc.custodyPremiumSettled(), arc.smallWarSettled()});
    }

    private static int settleWon(AgreusDistrictEscortProgress p, String id) {
        return p.settle(id, "agreus", true, FEE, true, true, true, true, false, 0, 0);
    }
    private static int settleLost(AgreusDistrictEscortProgress p, String id) {
        return p.settle(id, "agreus", false, 0, true, false, false, false, true, 1, 1);
    }
}
