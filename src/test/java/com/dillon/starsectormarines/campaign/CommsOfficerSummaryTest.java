package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link CommsOfficerSummary} — the dossier-stack header line.
 * Pins down: token substitution for both overview and client flavors,
 * per-day determinism, that mood/client/day actually shift the picked
 * variant, and null-arg safety.
 *
 * <p>Synthetic templates injected via {@link CommsOfficerVoice#loadForTest}.
 */
public class CommsOfficerSummaryTest {

    @BeforeAll
    public static void injectTestTemplates() {
        Map<OfficerMood, CommsOfficerVoice.Frame> map = new EnumMap<>(OfficerMood.class);
        for (OfficerMood m : OfficerMood.values()) {
            map.put(m, new CommsOfficerVoice.Frame(
                    new String[] { "[PREFIX:" + m.name() + "]" },
                    new String[] { "[SUFFIX:" + m.name() + "]" },
                    new CommsOfficerVoice.Summary(
                            new String[] {
                                    "OVR " + m.name() + " v0 {offerCount}/{clientCount}/{lapsingCount}",
                                    "OVR " + m.name() + " v1 {offerCount}/{clientCount}/{lapsingCount}",
                                    "OVR " + m.name() + " v2 {offerCount}/{clientCount}/{lapsingCount}"
                            },
                            new String[] {
                                    "CLI " + m.name() + " v0 {patron}/{offerCount}/{lapsingCount}",
                                    "CLI " + m.name() + " v1 {patron}/{offerCount}/{lapsingCount}",
                                    "CLI " + m.name() + " v2 {patron}/{offerCount}/{lapsingCount}"
                            })));
        }
        CommsOfficerVoice.loadForTest(map);
    }

    @AfterAll
    public static void resetTemplates() {
        CommsOfficerVoice.loadForTest(null);
    }

    @Test
    public void overviewSubstitutesAllTokens() {
        String out = CommsOfficerSummary.renderOverview(OfficerMood.STEADY, 100, 8, 4, 2);
        assertTrue(out.startsWith("OVR STEADY"));
        assertTrue(out.contains("8/4/2"));
        assertFalse(out.contains("{"));
    }

    @Test
    public void clientSubstitutesAllTokens() {
        String out = CommsOfficerSummary.renderForClient(
                OfficerMood.SEASONED, 100, 42L, "House Cavor", 3, 1);
        assertTrue(out.startsWith("CLI SEASONED"));
        assertTrue(out.contains("House Cavor"));
        assertTrue(out.contains("3/1"));
        assertFalse(out.contains("{"));
    }

    @Test
    public void overviewIsStableWithinASingleDay() {
        String a = CommsOfficerSummary.renderOverview(OfficerMood.STEADY, 50, 8, 4, 0);
        String b = CommsOfficerSummary.renderOverview(OfficerMood.STEADY, 50, 8, 4, 0);
        assertEquals(a, b, "Same (mood, day) inputs must render identically");
    }

    @Test
    public void clientIsStableWithinASingleDayForSameClient() {
        String a = CommsOfficerSummary.renderForClient(
                OfficerMood.DESPERATE, 50, 7L, "House Tobin", 2, 0);
        String b = CommsOfficerSummary.renderForClient(
                OfficerMood.DESPERATE, 50, 7L, "House Tobin", 2, 0);
        assertEquals(a, b, "Same (mood, day, clientId) inputs must render identically");
    }

    @Test
    public void differentDaysCycleOverviewVariants() {
        Set<String> seen = new HashSet<>();
        for (int day = 0; day < 30; day++) {
            String out = CommsOfficerSummary.renderOverview(OfficerMood.STEADY, day, 1, 1, 0);
            // Strip variant-specific content (3 slots in the synthetic bank).
            int start = "OVR STEADY ".length();
            seen.add(out.substring(start, start + 2)); // "v0" / "v1" / "v2"
        }
        assertEquals(3, seen.size(),
                "expected all 3 overview slots to be hit across 30 days, got: " + seen);
    }

    @Test
    public void differentClientsCycleClientVariants() {
        Set<String> seen = new HashSet<>();
        for (long id = 1; id <= 30; id++) {
            String out = CommsOfficerSummary.renderForClient(
                    OfficerMood.STEADY, 0, id, "P", 1, 0);
            int start = "CLI STEADY ".length();
            seen.add(out.substring(start, start + 2));
        }
        assertEquals(3, seen.size(),
                "expected all 3 client slots to be hit across 30 client ids, got: " + seen);
    }

    @Test
    public void differentMoodsShiftPickedPool() {
        String desperate = CommsOfficerSummary.renderOverview(OfficerMood.DESPERATE, 100, 0, 0, 0);
        String steady    = CommsOfficerSummary.renderOverview(OfficerMood.STEADY,    100, 0, 0, 0);
        String seasoned  = CommsOfficerSummary.renderOverview(OfficerMood.SEASONED,  100, 0, 0, 0);
        assertNotEquals(desperate, steady);
        assertNotEquals(steady, seasoned);
        assertNotEquals(desperate, seasoned);
        assertTrue(desperate.contains("DESPERATE"));
        assertTrue(steady.contains("STEADY"));
        assertTrue(seasoned.contains("SEASONED"));
    }

    @Test
    public void nullMoodAndPatronFallBackInsteadOfNpe() {
        String overview = CommsOfficerSummary.renderOverview(null, 1, 0, 0, 0);
        String client   = CommsOfficerSummary.renderForClient(null, 1, 0L, null, 0, 0);
        assertNotNull(overview);
        assertNotNull(client);
        assertFalse(overview.isEmpty());
        assertFalse(client.isEmpty());
        assertFalse(client.contains("null"), "Null patron must not leak into rendered text");
    }
}
