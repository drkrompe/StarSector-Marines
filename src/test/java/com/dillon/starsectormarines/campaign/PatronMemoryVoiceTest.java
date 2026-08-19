package com.dillon.starsectormarines.campaign;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatronMemoryVoiceTest {

    @Test
    void parsesEveryOutcomePool() throws Exception {
        Map<PatronEngagementOutcome, String[]> parsed =
                PatronMemoryVoice.parse(validRoot());

        assertEquals(PatronEngagementOutcome.values().length, parsed.size());
        assertEquals("COMPLETED line", parsed.get(
                PatronEngagementOutcome.COMPLETED)[0]);
    }

    @Test
    void rejectsMissingEmptyAndNonStringPools() throws Exception {
        JSONObject missing = validRoot();
        missing.getJSONObject(PatronMemoryVoice.MEMORY_KEY)
                .remove(PatronEngagementOutcome.FAILED.name());
        assertThrows(IllegalStateException.class,
                () -> PatronMemoryVoice.parse(missing));

        JSONObject empty = validRoot();
        empty.getJSONObject(PatronMemoryVoice.MEMORY_KEY).put(
                PatronEngagementOutcome.WITHDREW.name(), new JSONArray());
        assertThrows(IllegalStateException.class,
                () -> PatronMemoryVoice.parse(empty));

        JSONObject invalid = validRoot();
        invalid.getJSONObject(PatronMemoryVoice.MEMORY_KEY).put(
                PatronEngagementOutcome.EMPLOYER_BREACHED.name(),
                new JSONArray().put(3));
        assertThrows(IllegalStateException.class,
                () -> PatronMemoryVoice.parse(invalid));
    }

    private static JSONObject validRoot() throws Exception {
        JSONObject root = new JSONObject();
        JSONObject memory = new JSONObject();
        for (PatronEngagementOutcome outcome
                : PatronEngagementOutcome.values()) {
            memory.put(outcome.name(),
                    new JSONArray().put(outcome.name() + " line"));
        }
        root.put(PatronMemoryVoice.MEMORY_KEY, memory);
        return root;
    }
}
