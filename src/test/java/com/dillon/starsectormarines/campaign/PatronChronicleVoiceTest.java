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

class PatronChronicleVoiceTest {

    @Test
    void parsesEveryReferencePool() throws Exception {
        Map<PatronChronicleReferenceType, String[]> parsed =
                PatronChronicleVoice.parse(validRoot());

        assertEquals(PatronChronicleReferenceType.values().length,
                parsed.size());
        assertEquals("CHAIN_ACTOR line", parsed.get(
                PatronChronicleReferenceType.CHAIN_ACTOR)[0]);
    }

    @Test
    void rejectsMissingEmptyAndNonStringPools() throws Exception {
        JSONObject missing = validRoot();
        missing.getJSONObject(PatronChronicleVoice.CONTENT_KEY)
                .remove(PatronChronicleReferenceType.CHAIN_TARGET.name());
        assertThrows(IllegalStateException.class,
                () -> PatronChronicleVoice.parse(missing));

        JSONObject empty = validRoot();
        empty.getJSONObject(PatronChronicleVoice.CONTENT_KEY).put(
                PatronChronicleReferenceType.THRONE_CLAIMANT.name(),
                new JSONArray());
        assertThrows(IllegalStateException.class,
                () -> PatronChronicleVoice.parse(empty));

        JSONObject invalid = validRoot();
        invalid.getJSONObject(PatronChronicleVoice.CONTENT_KEY).put(
                PatronChronicleReferenceType.TESTAMENT_DEPOSED.name(),
                new JSONArray().put(4));
        assertThrows(IllegalStateException.class,
                () -> PatronChronicleVoice.parse(invalid));
    }

    @Test
    void authoredVoiceFileContainsEveryReferencePool() throws Exception {
        Path path = Path.of("mod", "data", "marines",
                "comms_officer_voice.json");
        String authored = Files.readString(path, StandardCharsets.UTF_8)
                .replaceAll("(?m)^\\s*#.*$", "");
        JSONObject root = new JSONObject(authored);

        assertEquals(PatronChronicleReferenceType.values().length,
                PatronChronicleVoice.parse(root).size());
    }

    private static JSONObject validRoot() throws Exception {
        JSONObject root = new JSONObject();
        JSONObject chronicle = new JSONObject();
        for (PatronChronicleReferenceType type
                : PatronChronicleReferenceType.values()) {
            chronicle.put(type.name(),
                    new JSONArray().put(type.name() + " line"));
        }
        root.put(PatronChronicleVoice.CONTENT_KEY, chronicle);
        return root;
    }
}
