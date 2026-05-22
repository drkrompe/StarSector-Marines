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
 * Exercises the comms-officer composer: prefix + body + optional suffix
 * layering, determinism per contract, mood-shifts-prefix.
 *
 * <p>Synthetic templates are injected into both the patron-archetype
 * and comms-officer banks via the {@code loadForTest} seams so neither
 * SettingsAPI nor the on-disk JSON files have to bootstrap.
 */
public class BriefingComposerTest {

    @BeforeAll
    public static void injectTestTemplates() {
        Map<PatronArchetype, String[]> patronTemplates = new EnumMap<>(PatronArchetype.class);
        for (PatronArchetype a : PatronArchetype.values()) {
            patronTemplates.put(a, new String[] {
                    "[BODY:" + a.name() + ":v0] {patron}/{target}/{payout}/{salvage}",
                    "[BODY:" + a.name() + ":v1] {patron}/{target}/{payout}/{salvage}",
                    "[BODY:" + a.name() + ":v2] {patron}/{target}/{payout}/{salvage}"
            });
        }
        PatronBriefingTemplates.loadForTest(patronTemplates);

        Map<OfficerMood, CommsOfficerVoice.Frame> moodTemplates = new EnumMap<>(OfficerMood.class);
        for (OfficerMood m : OfficerMood.values()) {
            moodTemplates.put(m, new CommsOfficerVoice.Frame(
                    new String[] {
                            "[PREFIX:" + m.name() + ":p0]",
                            "[PREFIX:" + m.name() + ":p1]",
                            "[PREFIX:" + m.name() + ":p2]"
                    },
                    new String[] {
                            "[SUFFIX:" + m.name() + ":s0]",
                            "[SUFFIX:" + m.name() + ":s1]",
                            "[SUFFIX:" + m.name() + ":s2]"
                    }));
        }
        CommsOfficerVoice.loadForTest(moodTemplates);
    }

    @AfterAll
    public static void resetTemplates() {
        PatronBriefingTemplates.loadForTest(null);
        CommsOfficerVoice.loadForTest(null);
    }

    @Test
    public void composeIncludesPrefixAndBody() {
        String out = BriefingComposer.compose(
                PatronArchetype.ESTABLISHED, OfficerMood.STEADY, 1L,
                "House Cavor", "Eventide", "$50,000", 40);
        assertTrue(out.startsWith("[PREFIX:STEADY:"), "prefix must lead: " + out);
        assertTrue(out.contains("[BODY:ESTABLISHED:"), "body must be present: " + out);
        assertTrue(out.contains("House Cavor"), "body substitution missing: " + out);
    }

    @Test
    public void compositionIsDeterministicPerContract() {
        String a = BriefingComposer.compose(
                PatronArchetype.TIME_RUSHED, OfficerMood.DESPERATE, 42L, "P", "T", "$1", 0);
        String b = BriefingComposer.compose(
                PatronArchetype.TIME_RUSHED, OfficerMood.DESPERATE, 42L, "P", "T", "$1", 0);
        assertEquals(a, b, "Same (archetype, mood, contractId) must compose identically");
    }

    @Test
    public void moodShiftsPrefixButNotBody() {
        String desperate = BriefingComposer.compose(
                PatronArchetype.ESTABLISHED, OfficerMood.DESPERATE, 11L, "P", "T", "$1", 0);
        String steady = BriefingComposer.compose(
                PatronArchetype.ESTABLISHED, OfficerMood.STEADY, 11L, "P", "T", "$1", 0);
        assertNotEquals(desperate, steady, "different moods must produce different composed text");
        assertTrue(desperate.contains("[PREFIX:DESPERATE:"));
        assertTrue(steady.contains("[PREFIX:STEADY:"));
        // Body is identical across moods at the same contractId — both should
        // carry the same archetype body marker.
        assertTrue(desperate.contains("[BODY:ESTABLISHED:"));
        assertTrue(steady.contains("[BODY:ESTABLISHED:"));
    }

    @Test
    public void differentContractsCyclePrefixes() {
        Set<String> seenPrefixIds = new HashSet<>();
        for (long id = 1; id <= 60; id++) {
            String out = BriefingComposer.compose(
                    PatronArchetype.FALLEN_NOBLE, OfficerMood.STEADY, id, "P", "T", "$0", 0);
            // Extract the prefix slot tag — three slots p0/p1/p2 in the synthetic bank
            int start = out.indexOf("[PREFIX:STEADY:") + "[PREFIX:STEADY:".length();
            int end   = out.indexOf("]", start);
            seenPrefixIds.add(out.substring(start, end));
        }
        assertEquals(3, seenPrefixIds.size(),
                "expected all 3 prefix slots to be hit across 60 contracts, got: " + seenPrefixIds);
    }

    @Test
    public void suffixSometimesIncludedSometimesNot() {
        int withSuffix = 0;
        int withoutSuffix = 0;
        for (long id = 1; id <= 200; id++) {
            if (BriefingComposer.shouldIncludeSuffix(id)) withSuffix++;
            else withoutSuffix++;
        }
        // 60% probability with 200 samples — both buckets should be hit
        // by a wide margin. Just assert both buckets are non-empty rather
        // than asserting a specific ratio.
        assertTrue(withSuffix > 50,  "expected suffix-included to hit reasonably often, got " + withSuffix);
        assertTrue(withoutSuffix > 20, "expected suffix-omitted to hit sometimes, got " + withoutSuffix);
    }

    @Test
    public void nullArchetypeAndMoodFallBackInsteadOfNpe() {
        String out = BriefingComposer.compose(
                null, null, 1L, "P", "T", "$1", 0);
        assertNotNull(out);
        assertFalse(out.isEmpty());
    }
}
