package com.dillon.starsectormarines.campaign.start;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FoundingReserveTest {
    @Test void reserveBelongsToOriginalHullAndSurvivesReloadWithoutReplenishing() throws Exception {
        var saved = new HashMap<String, Object>(Map.of(CerberusCompanyStart.SHIP_KEY, "original"));
        assertFalse(FoundingReserve.protectedStart(saved));
        assertEquals(0, FoundingReserve.available(saved, "original"));
        FoundingReserve.issue(saved);
        assertEquals(FoundingReserve.CREDITS, FoundingReserve.available(saved, "original"));
        assertEquals(0, FoundingReserve.available(saved, "other"));
        assertThrows(IllegalArgumentException.class, () -> FoundingReserve.consume(saved, "other", FoundingReserve.CREDITS));
        assertEquals(FoundingReserve.CREDITS, FoundingReserve.available(saved, "original"));
        FoundingReserve.consume(saved, "original", FoundingReserve.CREDITS);
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(saved); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked") var reloaded = (Map<String, Object>) in.readObject();
            FoundingReserve.issue(reloaded);
            assertTrue(FoundingReserve.protectedStart(reloaded));
            assertEquals(0, FoundingReserve.available(reloaded, "original"));
        }
    }

    @Test void chitsFallShortByExactlyThePressureTestTheYardWaives() {
        assertEquals(MercenaryCompanyConversion.CREDIT_COST,
                FoundingReserve.CREDITS + CompanyYard.waived(FoundingReserve.CREDITS));
        assertEquals(0, CompanyYard.cashCost(FoundingReserve.CREDITS));
        assertEquals(0, CompanyYard.waived(0), "Paying from the ship's account waives nothing");
        assertEquals(MercenaryCompanyConversion.CREDIT_COST, CompanyYard.cashCost(0));
    }

    @Test void aSaveIssuedTheOldWholeEstimateDrawsOnlyTheChits() {
        var saved = new HashMap<String, Object>(Map.of(CerberusCompanyStart.SHIP_KEY, "original",
                "sm_founding_yard_reserve", 5000));
        assertEquals(FoundingReserve.CREDITS, FoundingReserve.available(saved, "original"));
    }
}
