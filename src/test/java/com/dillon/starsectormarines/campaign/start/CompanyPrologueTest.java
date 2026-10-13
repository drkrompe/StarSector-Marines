package com.dillon.starsectormarines.campaign.start;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CompanyPrologueTest {
    @Test void onlyAnExplicitNewStartGetsTheOpening() {
        var ordinary = new HashMap<String, Object>();
        assertFalse(CompanyPrologue.pending(ordinary));
        var legacy = new HashMap<String, Object>(Map.of(CerberusCompanyStart.SHIP_KEY, "second-wind"));
        assertFalse(CompanyPrologue.pending(legacy));
        CompanyPrologue.begin(legacy);
        assertTrue(CompanyPrologue.pending(legacy));
        assertEquals(0, CompanyPrologue.page(legacy));
    }

    @Test void unfinishedOpeningResumesButCompletionAndSkipNeverReplay() throws Exception {
        Map<String, Object> saved = new HashMap<>(Map.of(CerberusCompanyStart.SHIP_KEY, "second-wind"));
        CompanyPrologue.begin(saved);
        CompanyPrologue.moveTo(saved, 3);
        saved = roundTrip(saved);
        CompanyPrologue.begin(saved);
        assertTrue(CompanyPrologue.pending(saved));
        assertEquals(3, CompanyPrologue.page(saved));
        CompanyPrologue.finish(saved);
        saved = roundTrip(saved);
        CompanyPrologue.begin(saved);
        CompanyPrologue.moveTo(saved, 0);
        assertFalse(CompanyPrologue.pending(saved));
        assertEquals(3, CompanyPrologue.page(saved));
    }

    @Test void pageBoundsCannotStrandTheOpeningOrOptInAnExistingSave() {
        var saved = new HashMap<String, Object>(Map.of(CerberusCompanyStart.SHIP_KEY, "second-wind"));
        CompanyPrologue.moveTo(saved, 4);
        CompanyPrologue.finish(saved);
        assertEquals(1, saved.size());
        CompanyPrologue.begin(saved);
        CompanyPrologue.moveTo(saved, 999);
        assertEquals(CompanyPrologue.PAGE_COUNT - 1, CompanyPrologue.page(saved));
        CompanyPrologue.moveTo(saved, -1);
        assertEquals(0, CompanyPrologue.page(saved));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> roundTrip(Map<String, Object> saved) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) { output.writeObject(saved); }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Map<String, Object>) input.readObject();
        }
    }
}
