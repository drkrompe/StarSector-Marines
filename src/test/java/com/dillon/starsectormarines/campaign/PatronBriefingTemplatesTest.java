package com.dillon.starsectormarines.campaign;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema-validation coverage for the patron-briefing content loader.
 * The real {@link PatronBriefingTemplates#loadOrFallback() loadOrFallback}
 * path needs SettingsAPI; these tests target the pure
 * {@link PatronBriefingTemplates#parse(JSONObject) parse} step which has
 * no I/O.
 */
public class PatronBriefingTemplatesTest {

    @Test
    public void validJsonRoundTripsEveryArchetype() throws Exception {
        JSONObject root = buildValidRoot();
        Map<PatronArchetype, String[]> parsed = PatronBriefingTemplates.parse(root);
        assertEquals(PatronArchetype.values().length, parsed.size());
        for (PatronArchetype a : PatronArchetype.values()) {
            String[] variants = parsed.get(a);
            assertArrayEquals(new String[] { a.name() + " A", a.name() + " B" }, variants);
        }
    }

    @Test
    public void nullRootIsRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PatronBriefingTemplates.parse(null));
        assertTrue(ex.getMessage().contains("null root"));
    }

    @Test
    public void missingTemplatesObjectIsRejected() {
        JSONObject root = new JSONObject();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PatronBriefingTemplates.parse(root));
        assertTrue(ex.getMessage().contains("templates"));
    }

    @Test
    public void missingArchetypeIsRejectedLoudly() throws Exception {
        JSONObject root = buildValidRoot();
        // Drop one archetype's entry; parser should call it out by name.
        root.getJSONObject(PatronBriefingTemplates.TEMPLATES_KEY)
                .remove(PatronArchetype.FALLEN_NOBLE.name());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PatronBriefingTemplates.parse(root));
        assertTrue(ex.getMessage().contains("FALLEN_NOBLE"),
                "Error should name the missing archetype, got: " + ex.getMessage());
    }

    @Test
    public void emptyVariantArrayIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        root.getJSONObject(PatronBriefingTemplates.TEMPLATES_KEY)
                .put(PatronArchetype.SUSPICIOUS.name(), new JSONArray());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PatronBriefingTemplates.parse(root));
        assertTrue(ex.getMessage().contains("SUSPICIOUS"));
        assertTrue(ex.getMessage().contains("zero variants"));
    }

    @Test
    public void nonStringVariantIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        JSONArray bad = new JSONArray();
        bad.put("ok");
        bad.put(42); // not a string
        root.getJSONObject(PatronBriefingTemplates.TEMPLATES_KEY)
                .put(PatronArchetype.NEWCOMER.name(), bad);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PatronBriefingTemplates.parse(root));
        assertTrue(ex.getMessage().contains("NEWCOMER"));
    }

    @Test
    public void emptyStringVariantIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        JSONArray bad = new JSONArray();
        bad.put("ok");
        bad.put(""); // empty string is not a usable template
        root.getJSONObject(PatronBriefingTemplates.TEMPLATES_KEY)
                .put(PatronArchetype.TRUE_BELIEVER.name(), bad);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> PatronBriefingTemplates.parse(root));
        assertTrue(ex.getMessage().contains("TRUE_BELIEVER"));
    }

    @Test
    public void placeholderPredicateMatchesFallbackShape() {
        // Sanity check on the test seam: the placeholder string the loader
        // emits when it fails matches what isPlaceholder() recognizes.
        String[] fake = { "[TIME_RUSHED briefing template missing]" };
        assertTrue(PatronBriefingTemplates.isPlaceholder(fake));
        assertTrue(!PatronBriefingTemplates.isPlaceholder(new String[] { "real text" }));
        assertTrue(!PatronBriefingTemplates.isPlaceholder(new String[0]));
    }

    /** Build a minimal valid JSON root — every archetype gets two distinct variants. */
    private static JSONObject buildValidRoot() throws Exception {
        JSONObject root = new JSONObject();
        JSONObject templates = new JSONObject();
        for (PatronArchetype a : PatronArchetype.values()) {
            JSONArray arr = new JSONArray();
            arr.put(a.name() + " A");
            arr.put(a.name() + " B");
            templates.put(a.name(), arr);
        }
        root.put(PatronBriefingTemplates.TEMPLATES_KEY, templates);
        return root;
    }
}
