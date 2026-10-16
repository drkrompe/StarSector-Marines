package com.dillon.starsectormarines.campaign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Chapter 5's once-only settlement: gated on a ratified route, the one writer of smallWarSettled. */
class SmallWarLedgerTest {
    @Test void onlyARatifiedAgreementOpensBlackSluice() {
        var signed = afterYard();
        assertNull(signed.nextMissionId());
        assertFalse(signed.smallWarAvailable());
        assertTrue(signed.signAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertFalse(signed.smallWarAvailable(), "a signed but unratified route waits on Chapter 4");
        assertEquals(AgreusArcProgress.TERMS_PREFIX + 1, signed.nextMissionId());
        assertFalse(signed.begin(AgreusArcProgress.SMALL_WAR_PREFIX + 1, "agreus", "Agreus"));
        assertTrue(signed.ratifyAgreement(AgreusYardAgreement.HOUSE_CUSTODY));
        assertEquals(AgreusArcProgress.SMALL_WAR_PREFIX + 1, signed.nextMissionId());
        assertEquals(AgreusArcProgress.SMALL_WAR_PAYMENT, signed.nextPayment());

        var declined = afterYard();
        assertTrue(declined.declineFurtherWork());
        assertFalse(declined.smallWarAvailable());
        assertNull(declined.nextMissionId());
    }

    @Test void victoryPaysFeeAndCustodyPremiumOnceAndSetsTheWarrantFlag() {
        var p = ratified(AgreusYardAgreement.HOUSE_CUSTODY); String id = p.nextMissionId();
        assertEquals(-1, win(p, id), "an unlaunched attempt cannot settle");
        assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(-1, p.settleSmallWar(id, "asharu", true, 44000, true, true, true, true, true, true, false, 0, 0, 0));
        assertEquals(-1, p.settleSmallWar(id, "agreus", true, 88000, true, true, true, true, true, true, false, 0, 0, 0));
        assertEquals(-1, p.settleSmallWar(id, "agreus", true, 44000, true, true, true, true, true, true, true, 0, 0, 0),
                "a victory claim with destroyed fuel infrastructure contradicts itself");
        assertEquals(-1, p.settleSmallWar(id, "agreus", true, 44000, true, true, true, true, true, true, false, 3, 0, 0));
        assertEquals(-1, p.settleSmallWar(id, "agreus", true, 44000, true, true, false, true, true, true, false, 0, 0, 0));
        assertEquals(-1, p.settleSmallWar(AgreusArcProgress.YARD_PREFIX + 2, "agreus", true, 44000, true, true, true, true, true, true, false, 0, 0, 0));
        assertFalse(p.smallWarSettled(), "refused receipts write nothing");
        assertEquals(56000, p.settleSmallWar(id, "agreus", true, 44000, true, true, true, true, true, true, false, 1, 2, 7));
        assertTrue(p.smallWarSettled());
        assertEquals(12000, p.smallWarPremiumPaid());
        assertEquals(7, p.greyhookWithdrawn());
        assertEquals(-1, win(p, id), "a duplicate receipt pays nothing");
        assertNull(p.nextMissionId());
        assertEquals(0, p.settleCustodyPremium());
    }

    @Test void compactVictoryForgoesThePremium() {
        var p = ratified(AgreusYardAgreement.YARD_COMPACT); String id = p.nextMissionId();
        assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(44000, win(p, id));
        assertEquals(0, p.smallWarPremiumPaid());
        assertTrue(p.smallWarSettled());
    }

    @Test void defeatRecordsLossesAndOpensADisclosedReplacementAcrossReload() throws Exception {
        var p = ratified(AgreusYardAgreement.HOUSE_CUSTODY); String first = p.nextMissionId();
        assertTrue(p.begin(first, "agreus", "Agreus"));
        assertEquals(-1, p.settleSmallWar(first, "agreus", false, 44000, true, true, false, true, false, true, true, 0, 0, 0));
        assertEquals(0, p.settleSmallWar(first, "agreus", false, 0, true, true, false, true, false, true, true, 1, 4, 3));
        assertFalse(p.smallWarSettled());
        assertEquals(0, p.smallWarPremiumPaid());
        assertEquals(12000, p.custodyPremiumDue(), "the premium waits for an actual victory");
        String retry = p.nextMissionId(); assertNotEquals(first, retry);
        assertTrue(p.begin(retry, "agreus", "Agreus"));
        p = reload(p);
        assertEquals(-1, win(p, first));
        assertEquals(56000, win(p, retry));
        assertEquals(1, p.smallWarCustodiansLost()); assertEquals(4, p.smallWarHoldersLost()); assertEquals(1, p.fuelInfrastructureLost());
        assertTrue(p.smallWarSettled());
    }

    static AgreusArcProgress ratified(AgreusYardAgreement route) {
        var p = afterYard();
        assertTrue(p.signAgreement(route));
        assertTrue(p.ratifyAgreement(route));
        return p;
    }

    private static AgreusArcProgress reload(AgreusArcProgress p) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (AgreusArcProgress) in.readObject(); }
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

    private static int win(AgreusArcProgress p, String id) {
        return p.settleSmallWar(id, "agreus", true, 44000, true, true, true, true, true, true, false, 0, 0, 0);
    }
}
