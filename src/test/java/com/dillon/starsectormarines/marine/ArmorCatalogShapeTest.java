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
                            + " field that role at any price: " + render(counts));
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
     * The shape this catalog is supposed to have, reported rather than asserted.
     *
     * <p>The invariant {@code role-and-access.md} actually wants — every job
     * spans more than one tier, so buying up the ladder upgrades a marine
     * instead of re-roling them — is <b>not</b> asserted here, because it does
     * not hold yet and a failing test is not a plan. Filling the matrix is that
     * doc's next unit of work, and turning the printed report below into an
     * assertion is its acceptance.
     */
    @Test
    void reportsTheRoleAndTierMatrix() {
        Map<ArmorRole, Map<Integer, Set<String>>> matrix = new EnumMap<>(ArmorRole.class);
        int topTier = 0;
        for (MarineArmorCatalogDef pattern : catalog().all()) {
            matrix.computeIfAbsent(pattern.role(), key -> new TreeMap<>())
                    .computeIfAbsent(pattern.tier(), key -> new TreeSet<>())
                    .add(pattern.id());
            topTier = Math.max(topTier, pattern.tier());
        }
        StringBuilder report = new StringBuilder("armour catalog role x tier:\n");
        for (ArmorRole role : ArmorRole.values()) {
            Map<Integer, Set<String>> byTier = matrix.getOrDefault(role, Map.of());
            report.append(String.format("  %-10s", role.key));
            for (int tier = 1; tier <= topTier; tier++) {
                Set<String> cell = byTier.getOrDefault(tier, Set.of());
                report.append(String.format(" | %d:%s", tier,
                        cell.isEmpty() ? "-" : String.valueOf(cell.size())));
            }
            int tiersSpanned = byTier.size();
            report.append(tiersSpanned <= 1 && role != ArmorRole.UNPOWERED
                    ? "   <- confined to one tier\n" : "\n");
        }
        System.out.println(report);
    }

    private static Map<ArmorRole, Integer> countByRole(MarineArmorCatalogRegistry catalog) {
        Map<ArmorRole, Integer> counts = new EnumMap<>(ArmorRole.class);
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            counts.merge(pattern.role(), 1, Integer::sum);
        }
        return counts;
    }

    private static String render(Map<ArmorRole, Integer> counts) {
        StringBuilder out = new StringBuilder();
        for (ArmorRole role : ArmorRole.values()) {
            if (out.length() > 0) out.append(", ");
            out.append(role.key).append('=').append(counts.getOrDefault(role, 0));
        }
        return out.toString();
    }
}
