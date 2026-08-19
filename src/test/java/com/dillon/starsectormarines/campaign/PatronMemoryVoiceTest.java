package com.dillon.starsectormarines.campaign;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void parsesEveryContinuityPool() throws Exception {
        Map<PatronRelationshipPattern, String[]> parsed =
                PatronMemoryVoice.parseContinuity(validRoot());

        assertEquals(PatronRelationshipPattern.values().length, parsed.size());
        assertEquals("SUCCESS_STREAK line", parsed.get(
                PatronRelationshipPattern.SUCCESS_STREAK)[0]);
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

    @Test
    void rejectsMalformedContinuityPools() throws Exception {
        JSONObject missing = validRoot();
        missing.getJSONObject(PatronMemoryVoice.CONTINUITY_KEY)
                .remove(PatronRelationshipPattern.RECOVERY.name());
        assertThrows(IllegalStateException.class,
                () -> PatronMemoryVoice.parseContinuity(missing));

        JSONObject empty = validRoot();
        empty.getJSONObject(PatronMemoryVoice.CONTINUITY_KEY).put(
                PatronRelationshipPattern.MUTUAL_TROUBLE.name(),
                new JSONArray());
        assertThrows(IllegalStateException.class,
                () -> PatronMemoryVoice.parseContinuity(empty));

        JSONObject invalid = validRoot();
        invalid.getJSONObject(PatronMemoryVoice.CONTINUITY_KEY).put(
                PatronRelationshipPattern.SUCCESS_STREAK.name(),
                new JSONArray().put(false));
        assertThrows(IllegalStateException.class,
                () -> PatronMemoryVoice.parseContinuity(invalid));
    }

    @Test
    void authoredVoiceFileContainsValidMemoryAndContinuityPools()
            throws Exception {
        Path path = Path.of("mod", "data", "marines",
                "comms_officer_voice.json");
        String authored = Files.readString(path, StandardCharsets.UTF_8)
                .replaceAll("(?m)^\\s*#.*$", "");
        JSONObject root = new JSONObject(authored);

        assertEquals(PatronEngagementOutcome.values().length,
                PatronMemoryVoice.parse(root).size());
        assertEquals(PatronRelationshipPattern.values().length,
                PatronMemoryVoice.parseContinuity(root).size());
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
        JSONObject continuity = new JSONObject();
        for (PatronRelationshipPattern pattern
                : PatronRelationshipPattern.values()) {
            continuity.put(pattern.name(),
                    new JSONArray().put(pattern.name() + " line"));
        }
        root.put(PatronMemoryVoice.CONTINUITY_KEY, continuity);
        return root;
    }
}
