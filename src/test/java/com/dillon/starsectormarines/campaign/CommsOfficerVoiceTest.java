package com.dillon.starsectormarines.campaign;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema-validation coverage for {@link CommsOfficerVoice#parse}. The
 * real load path needs SettingsAPI; these tests target the pure parse
 * step which has no I/O.
 */
public class CommsOfficerVoiceTest {

    @Test
    public void validJsonRoundTripsEveryMood() throws Exception {
        JSONObject root = buildValidRoot();
        Map<OfficerMood, CommsOfficerVoice.Frame> parsed = CommsOfficerVoice.parse(root);
        assertEquals(OfficerMood.values().length, parsed.size());
        for (OfficerMood m : OfficerMood.values()) {
            CommsOfficerVoice.Frame f = parsed.get(m);
            assertNotNull(f, "mood " + m + " missing from parse");
            assertEquals(2, f.prefix.length);
            assertEquals(2, f.suffix.length);
            assertEquals(m.name() + " p0", f.prefix[0]);
            assertEquals(m.name() + " s0", f.suffix[0]);
            assertNotNull(f.summary, "mood " + m + " summary missing");
            assertEquals(2, f.summary.overview.length);
            assertEquals(2, f.summary.client.length);
            assertEquals(m.name() + " o0", f.summary.overview[0]);
            assertEquals(m.name() + " c0", f.summary.client[0]);
        }
    }

    @Test
    public void missingSummaryObjectIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        root.getJSONObject(CommsOfficerVoice.MOODS_KEY)
                .getJSONObject(OfficerMood.STEADY.name())
                .remove(CommsOfficerVoice.SUMMARY_KEY);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CommsOfficerVoice.parse(root));
        assertTrue(ex.getMessage().contains("STEADY"));
        assertTrue(ex.getMessage().contains(CommsOfficerVoice.SUMMARY_KEY));
    }

    @Test
    public void missingOverviewPoolIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        root.getJSONObject(CommsOfficerVoice.MOODS_KEY)
                .getJSONObject(OfficerMood.DESPERATE.name())
                .getJSONObject(CommsOfficerVoice.SUMMARY_KEY)
                .remove(CommsOfficerVoice.OVERVIEW_KEY);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CommsOfficerVoice.parse(root));
        assertTrue(ex.getMessage().contains("DESPERATE"));
        assertTrue(ex.getMessage().contains(CommsOfficerVoice.OVERVIEW_KEY));
    }

    @Test
    public void emptyClientSummaryIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        root.getJSONObject(CommsOfficerVoice.MOODS_KEY)
                .getJSONObject(OfficerMood.SEASONED.name())
                .getJSONObject(CommsOfficerVoice.SUMMARY_KEY)
                .put(CommsOfficerVoice.CLIENT_KEY, new JSONArray());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CommsOfficerVoice.parse(root));
        assertTrue(ex.getMessage().contains("SEASONED"));
        assertTrue(ex.getMessage().contains("zero variants"));
    }

    @Test
    public void nullRootIsRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CommsOfficerVoice.parse(null));
        assertTrue(ex.getMessage().contains("null root"));
    }

    @Test
    public void missingMoodsObjectIsRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CommsOfficerVoice.parse(new JSONObject()));
        assertTrue(ex.getMessage().contains("moods"));
    }

    @Test
    public void missingMoodIsRejectedLoudly() throws Exception {
        JSONObject root = buildValidRoot();
        root.getJSONObject(CommsOfficerVoice.MOODS_KEY).remove(OfficerMood.GREEN.name());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CommsOfficerVoice.parse(root));
        assertTrue(ex.getMessage().contains("GREEN"),
                "Error should name the missing mood, got: " + ex.getMessage());
    }

    @Test
    public void missingPrefixArrayIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        JSONObject entry = root.getJSONObject(CommsOfficerVoice.MOODS_KEY)
                .getJSONObject(OfficerMood.STEADY.name());
        entry.remove(CommsOfficerVoice.PREFIX_KEY);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CommsOfficerVoice.parse(root));
        assertTrue(ex.getMessage().contains("STEADY"));
        assertTrue(ex.getMessage().contains(CommsOfficerVoice.PREFIX_KEY));
    }

    @Test
    public void emptyPoolIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        root.getJSONObject(CommsOfficerVoice.MOODS_KEY)
                .getJSONObject(OfficerMood.DESPERATE.name())
                .put(CommsOfficerVoice.SUFFIX_KEY, new JSONArray());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CommsOfficerVoice.parse(root));
        assertTrue(ex.getMessage().contains("DESPERATE"));
        assertTrue(ex.getMessage().contains("zero variants"));
    }

    @Test
    public void emptyStringVariantIsRejected() throws Exception {
        JSONObject root = buildValidRoot();
        JSONArray bad = new JSONArray().put("ok").put("");
        root.getJSONObject(CommsOfficerVoice.MOODS_KEY)
                .getJSONObject(OfficerMood.SEASONED.name())
                .put(CommsOfficerVoice.PREFIX_KEY, bad);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CommsOfficerVoice.parse(root));
        assertTrue(ex.getMessage().contains("SEASONED"));
    }

    /** Build a minimal valid JSON root — every mood gets two distinct prefix/suffix/overview/client lines. */
    private static JSONObject buildValidRoot() throws Exception {
        JSONObject root = new JSONObject();
        JSONObject moods = new JSONObject();
        for (OfficerMood m : OfficerMood.values()) {
            JSONObject entry = new JSONObject();
            entry.put(CommsOfficerVoice.PREFIX_KEY,
                    new JSONArray().put(m.name() + " p0").put(m.name() + " p1"));
            entry.put(CommsOfficerVoice.SUFFIX_KEY,
                    new JSONArray().put(m.name() + " s0").put(m.name() + " s1"));
            JSONObject summary = new JSONObject();
            summary.put(CommsOfficerVoice.OVERVIEW_KEY,
                    new JSONArray().put(m.name() + " o0").put(m.name() + " o1"));
            summary.put(CommsOfficerVoice.CLIENT_KEY,
                    new JSONArray().put(m.name() + " c0").put(m.name() + " c1"));
            entry.put(CommsOfficerVoice.SUMMARY_KEY, summary);
            moods.put(m.name(), entry);
        }
        root.put(CommsOfficerVoice.MOODS_KEY, moods);
        return root;
    }
}
