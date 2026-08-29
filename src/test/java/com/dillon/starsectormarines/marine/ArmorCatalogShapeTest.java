package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Invariants about the armour catalog's <b>shape</b> — properties of the whole
 * table rather than of any entry in it ({@code role-and-access.md}).
 *
 * <p>This file exists because every check the catalog already had reads one
 * pattern at a time: this pattern's role is well formed, its stats are in band,
 * its art resolves. All of them passed for the entire life of a catalog in which
 * a fully equipped company could only buy one role, because no single entry is
 * wrong — the defect is the arrangement. A rule about a catalog's shape needs a
 * test that reads the catalog.
 *
 * <p>Nothing here pins a count, a tier, or an authored number. The assertions
 * are about reachability and coverage, so filling in the matrix widens them
 * rather than breaking them.
 */
class ArmorCatalogShapeTest {

    /** Roles a player is expected to be able to field. UNPOWERED is the absence of a suit. */
    private static Set<ArmorRole> jobs() {
        Set<ArmorRole> jobs = new LinkedHashSet<>();
        for (ArmorRole role : ArmorRole.values()) {
            if (role != ArmorRole.UNPOWERED) jobs.add(role);
        }
        return jobs;
    }

    private static MarineArmorCatalogRegistry catalog() {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        assumeTrue(catalog != null, "the armour catalog is not installed in this context");
        return catalog;
    }

    /**
     * Every job in the vocabulary is something the catalog can actually dress a
     * marine as. A role nobody builds is a role that exists only in an enum, and
     * a squad cannot be composed out of those.
     */
    @Test
    void everyRoleInTheVocabularyHasAPatternThatFillsIt() {
        Map<ArmorRole, Integer> counts = countByRole(catalog());
        for (ArmorRole role : jobs()) {
            assertTrue(counts.getOrDefault(role, 0) > 0,
                    "no catalogued pattern is a " + role.key + " suit, so no squad can"
                            + " field that role at any price: " + renderCounts(counts));
        }
    }

    /**
     * A role is reachable through the player's own supply, not only through a
     * defender's issue. The equipment templates are what the Armory can unlock,
     * so a role catalogued but never templated is one the player watches other
     * people wear.
     */
    @Test
    void everyRoleIsReachableThroughPlayerSupply() {
        assumeTrue(EquipmentTemplateCatalog.installed() != null,
                "equipment templates are not installed in this context");
        MarineArmorCatalogRegistry catalog = catalog();

        Map<ArmorRole, Set<String>> obtainable = new EnumMap<>(ArmorRole.class);
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            if (EquipmentTemplateCatalog.armor(pattern.id()) == null) continue;
            obtainable.computeIfAbsent(pattern.role(), key -> new TreeSet<>())
                    .add(pattern.id());
        }
        for (ArmorRole role : jobs()) {
            assertFalse(obtainable.getOrDefault(role, Set.of()).isEmpty(),
                    "the player can never buy a " + role.key + " pattern, so that role"
                            + " is decoration: " + obtainable);
        }
    }

    /**
     * A capability is carried by patterns of one role, never spread across
     * several. This is {@code integral-system-slate.md}'s standing rule —
     * breaching is what an assault suit is <em>for</em> — expressed against the
     * whole table so that adding a pattern cannot quietly widen an effect's
     * ownership.
     */
    @Test
    void noCapabilityIsCarriedByTwoDifferentRoles() {
        Map<String, Set<ArmorRole>> carriers = new TreeMap<>();
        for (MarineArmorCatalogDef pattern : catalog().all()) {
            IntegralSystemDef system = pattern.integralSystem();
            if (system == null) continue;
            carriers.computeIfAbsent(system.effect().key, key -> new TreeSet<>())
                    .add(pattern.role());
        }
        assumeTrue(!carriers.isEmpty(), "no catalogued pattern carries a system here");
        carriers.forEach((effect, roles) -> assertTrue(roles.size() == 1,
                "'" + effect + "' is carried by more than one role " + roles
                        + ", so what a player learns is 'expensive suits get a trick'"
                        + " rather than what the role is for"));
    }

    /**
     * <b>The invariant this whole file exists for.</b> Every job is available at
     * more than one level of equipment, so buying up the ladder upgrades a
     * marine rather than changing what they are.
     *
     * <p>The catalog failed this for its entire life before the matrix was
     * filled: recon existed only at tier II, support only at III, assault only
     * at IV. That is what made a fully equipped company field nothing but
     * assault suits and therefore gave every marine in it the same capability —
     * not a rule about tiers granting abilities, but a table with no other cell
     * to buy.
     */
    @Test
    void everyRoleIsAvailableAtMoreThanOneTier() {
        Map<ArmorRole, Map<Integer, Set<String>>> matrix = matrix();
        for (ArmorRole role : jobs()) {
            assertTrue(matrix.getOrDefault(role, Map.of()).size() > 1,
                    role.key + " exists at a single tier, so a company that can afford"
                            + " better stops being able to field it:\n" + render(matrix));
        }
    }

    /**
     * A capability gets stronger as the suits carrying it do. Compared between
     * consecutive <em>populated</em> tiers and on each effect's own headline
     * axis, because a capability may skip a tier and because suits within one
     * tier are deliberately side-grades of each other — the Foundry-breaker and
     * the Reliquary are both tier IV and differ sevenfold.
     *
     * <p>This is what {@code role-and-access.md} means by a capability having
     * its own scaling ladder, and it could not be written at all until an effect
     * existed at two tiers.
     */
    @Test
    void aCapabilityGetsStrongerWithTheTierOfTheSuitCarryingIt() {
        Map<IntegralSystemEffect, Map<Integer, Float>> best = new EnumMap<>(IntegralSystemEffect.class);
        for (MarineArmorCatalogDef pattern : catalog().all()) {
            IntegralSystemDef system = pattern.integralSystem();
            if (system == null) continue;
            Float strength = headlineStrength(system);
            if (strength == null) continue;
            best.computeIfAbsent(system.effect(), key -> new TreeMap<>())
                    .merge(pattern.tier(), strength, Math::max);
        }
        assumeTrue(!best.isEmpty(), "no catalogued pattern carries a system here");
        best.forEach((effect, byTier) -> {
            Integer previousTier = null;
            float previousBest = Float.NEGATIVE_INFINITY;
            for (Map.Entry<Integer, Float> rung : byTier.entrySet()) {
                assertTrue(rung.getValue() >= previousBest,
                        "the best '" + effect.key + "' at tier " + rung.getKey() + " ("
                                + rung.getValue() + ") is weaker than the best at tier "
                                + previousTier + " (" + previousBest + "), so upgrading"
                                + " the suit downgrades the capability");
                previousTier = rung.getKey();
                previousBest = rung.getValue();
            }
        });
    }

    /**
     * The axis an effect is measured along when asking whether it got stronger.
     * Each is the number that entry's own catalog copy is really selling — the
     * pool a screen refuses, how far a sweep reads, how many rounds a rack
     * holds. Returns null for an effect with no such axis yet, which is skipped
     * rather than guessed at.
     */
    private static Float headlineStrength(IntegralSystemDef system) {
        return switch (system.effect()) {
            case BREACHER_ASSIST -> system.breacherAssist() != null
                    ? system.breacherAssist().screenSoak() : null;
            case PERCEPTION_SWEEP -> system.perceptionSweep() != null
                    ? system.perceptionSweep().revealRangeCells() : null;
            case MISSILE_POD -> system.usesAmmunition() ? (float) system.startingAmmo() : null;
        };
    }

    private static Map<ArmorRole, Map<Integer, Set<String>>> matrix() {
        Map<ArmorRole, Map<Integer, Set<String>>> matrix = new EnumMap<>(ArmorRole.class);
        for (MarineArmorCatalogDef pattern : catalog().all()) {
            matrix.computeIfAbsent(pattern.role(), key -> new TreeMap<>())
                    .computeIfAbsent(pattern.tier(), key -> new TreeSet<>())
                    .add(pattern.id());
        }
        return matrix;
    }

    /** The whole table, for a failure that shows the shape rather than one cell. */
    private static String render(Map<ArmorRole, Map<Integer, Set<String>>> matrix) {
        int topTier = matrix.values().stream()
                .flatMap(byTier -> byTier.keySet().stream())
                .mapToInt(Integer::intValue).max().orElse(0);
        StringBuilder out = new StringBuilder();
        for (ArmorRole role : ArmorRole.values()) {
            Map<Integer, Set<String>> byTier = matrix.getOrDefault(role, Map.of());
            out.append(String.format("  %-10s", role.key));
            for (int tier = 1; tier <= topTier; tier++) {
                Set<String> cell = byTier.getOrDefault(tier, Set.of());
                out.append(String.format(" | %d:%s", tier,
                        cell.isEmpty() ? "-" : String.valueOf(cell.size())));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static Map<ArmorRole, Integer> countByRole(MarineArmorCatalogRegistry catalog) {
        Map<ArmorRole, Integer> counts = new EnumMap<>(ArmorRole.class);
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            counts.merge(pattern.role(), 1, Integer::sum);
        }
        return counts;
    }

    private static String renderCounts(Map<ArmorRole, Integer> counts) {
        StringBuilder out = new StringBuilder();
        for (ArmorRole role : ArmorRole.values()) {
            if (out.length() > 0) out.append(", ");
            out.append(role.key).append('=').append(counts.getOrDefault(role, 0));
        }
        return out.toString();
    }
}
