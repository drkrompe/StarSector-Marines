package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers determinism (same contract id always picks the same template) and
 * variety (different contracts from the same archetype span the template pool).
 * Substitution is exercised against the token list documented on
 * {@link PatronBriefingFlavor}.
 */
public class PatronBriefingFlavorTest {

    @Test
    public void rendersAllTokens() {
        String out = PatronBriefingFlavor.render(
                PatronArchetype.ESTABLISHED, 1L,
                "House Cavor", "Eventide", "$50,000", 40);
        // Every template references at least the patron OR target name, the
        // payout, or the salvage %, but each archetype's three templates vary
        // in *which* tokens they use. Just assert the substitution machinery
        // works — the rendered string contains at least one of the four
        // surface forms.
        boolean substituted = out.contains("House Cavor")
                || out.contains("Eventide")
                || out.contains("$50,000")
                || out.contains("40");
        assertTrue(substituted, "Render did not substitute any token: " + out);
        // And no template token leaked through unsubstituted.
        assertFalse(out.contains("{"), "Unsubstituted token in: " + out);
    }

    @Test
    public void variantPickIsDeterministicPerContract() {
        String a = PatronBriefingFlavor.render(
                PatronArchetype.TIME_RUSHED, 42L, "P", "T", "$1", 0);
        String b = PatronBriefingFlavor.render(
                PatronArchetype.TIME_RUSHED, 42L, "P", "T", "$1", 0);
        assertEquals(a, b, "Same (archetype, contractId) must render identically");
    }

    @Test
    public void differentContractsHitDifferentVariants() {
        // 30 contract ids against a 3-template pool should reach all 3
        // variants with very high probability under a fair hash.
        Set<String> seen = new HashSet<>();
        for (long id = 1; id <= 30; id++) {
            seen.add(PatronBriefingFlavor.render(
                    PatronArchetype.FALLEN_NOBLE, id, "P", "T", "$0", 0));
        }
        assertEquals(3, seen.size(),
                "Variant cycling broken — expected 3 distinct templates, got " + seen.size());
    }

    @Test
    public void archetypeVoicesDiffer() {
        // Same contract id, different archetypes must produce different prose —
        // the writing surface IS the player's signal that archetypes exist.
        String rushed = PatronBriefingFlavor.render(
                PatronArchetype.TIME_RUSHED, 7L, "P", "T", "$1", 0);
        String noble  = PatronBriefingFlavor.render(
                PatronArchetype.FALLEN_NOBLE, 7L, "P", "T", "$1", 0);
        String estab  = PatronBriefingFlavor.render(
                PatronArchetype.ESTABLISHED, 7L, "P", "T", "$1", 0);
        assertNotEquals(rushed, noble);
        assertNotEquals(noble, estab);
        assertNotEquals(rushed, estab);
    }

    @Test
    public void nullArchetypeFallsBackInsteadOfNpe() {
        String out = PatronBriefingFlavor.render(
                null, 1L, "P", "T", "$1", 0);
        assertNotNull(out);
        assertFalse(out.isEmpty());
    }

    @Test
    public void nullSubstitutionsFallBackToPlaceholderText() {
        String out = PatronBriefingFlavor.render(
                PatronArchetype.SUSPICIOUS, 1L, null, null, null, 0);
        assertFalse(out.contains("null"), "Null tokens leaked into rendered text: " + out);
    }
}
