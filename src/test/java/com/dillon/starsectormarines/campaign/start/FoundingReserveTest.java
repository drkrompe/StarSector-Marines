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
        assertEquals(5000, FoundingReserve.available(saved, "original"));
        assertEquals(0, FoundingReserve.available(saved, "other"));
        assertThrows(IllegalArgumentException.class, () -> FoundingReserve.consume(saved, "other", 5000));
        assertEquals(5000, FoundingReserve.available(saved, "original"));
        FoundingReserve.consume(saved, "original", 5000);
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(saved); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked") var reloaded = (Map<String, Object>) in.readObject();
            FoundingReserve.issue(reloaded);
            assertTrue(FoundingReserve.protectedStart(reloaded));
            assertEquals(0, FoundingReserve.available(reloaded, "original"));
        }
    }
}
