package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
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
     * <b>Every</b> suit at a tier carries a better version of its capability
     * than <b>every</b> suit at the tier below. Not best-against-best: the floor
     * rises, which is what makes a tier mean something when a role spans several
     * ({@code role-and-access.md}).
     *
     * <p>Compared on {@link #systemValue}, not on any single authored number,
     * because the suits within one tier are deliberate side-grades and their
     * headline numbers disagree wildly — the Foundry-breaker refuses four points
     * of damage where the Reliquary refuses twenty-seven, and both are tier IV.
     * What they have in common is roughly how much they are worth per activation
     * once the shove is counted; where they differ is how often they can spend
     * it.
     */
    @Test
    void everySuitAtATierCarriesABetterSystemThanEverySuitBelowIt() {
        Map<IntegralSystemEffect, Map<Integer, List<Rung>>> ladders =
                new EnumMap<>(IntegralSystemEffect.class);
        for (MarineArmorCatalogDef pattern : catalog().all()) {
            IntegralSystemDef system = pattern.integralSystem();
            if (system == null) continue;
            Double value = systemValue(system);
            if (value == null) continue;
            ladders.computeIfAbsent(system.effect(), key -> new TreeMap<>())
                    .computeIfAbsent(pattern.tier(), key -> new ArrayList<>())
                    .add(new Rung(pattern.id(), value));
        }
        assumeTrue(!ladders.isEmpty(), "no catalogued pattern carries a system here");

        ladders.forEach((effect, byTier) -> {
            Rung previousCeiling = null;
            Integer previousTier = null;
            for (Map.Entry<Integer, List<Rung>> rung : byTier.entrySet()) {
                Rung floor = rung.getValue().stream()
                        .min(Comparator.comparingDouble(Rung::value)).orElseThrow();
                if (previousCeiling != null) {
                    assertTrue(floor.value() > previousCeiling.value(),
                            effect.key + ": " + floor.id() + " at tier " + rung.getKey()
                                    + " is worth " + round(floor.value()) + ", which does not"
                                    + " beat " + previousCeiling.id() + " at tier "
                                    + previousTier + " on " + round(previousCeiling.value())
                                    + " — a tier has to raise the floor, not just the ceiling");
                }
                previousCeiling = rung.getValue().stream()
                        .max(Comparator.comparingDouble(Rung::value)).orElseThrow();
                previousTier = rung.getKey();
            }
        });
    }

    private record Rung(String id, double value) {}

    /**
     * A rough index of what one system is worth, for ladder comparison only.
     *
     * <p>Deliberately not a balance model and never read by the game. It exists
     * because "stronger" has to mean something comparable before a ladder can be
     * checked, and no single authored field is that thing: a screen suit and a
     * shove suit spend the same tier budget on different axes.
     *
     * <p>A screen counts for the arc it actually covers, a shove is priced in
     * soak-equivalents, and the total is scaled by how much of the time the
     * system is available. Comparison is only ever between tiers of the SAME
     * effect, so the units need not mean anything across effects.
     */
    private static Double systemValue(IntegralSystemDef system) {
        return switch (system.effect()) {
            case BREACHER_ASSIST -> {
                BreacherAssistSpec screen = system.breacherAssist();
                if (screen == null) yield null;
                double perActivation = screen.screenSoak() * (screen.shieldedArcDegrees() / 360.0)
                        + SHOVE_IN_SOAK_POINTS * (screen.moveSpeedMult() - 1.0);
                yield perActivation * duty(system);
            }
            case PERCEPTION_SWEEP -> system.perceptionSweep() == null ? null
                    : system.perceptionSweep().revealRangeCells() * duty(system);
            case MISSILE_POD -> system.usesAmmunition() ? (double) system.startingAmmo() : null;
            // Everything one satchel puts back over a whole battle. Reach and
            // the treat-below threshold improve with tier too, but they decide
            // WHO gets a dressing rather than what one is worth.
            case FIELD_AID -> system.fieldAid() == null || !system.usesAmmunition() ? null
                    : (double) system.fieldAid().restoredHealth() * system.startingAmmo();
        };
    }

    /**
     * What a full point of movement multiplier is worth against a point of soak.
     * A guess, and the only one in this file — chosen so that the Foundry-breaker,
     * whose authored copy says its whole value is the shove and its screen is
     * scrap, comes out comparable per activation to the suits that spent the same
     * budget the other way.
     */
    private static final double SHOVE_IN_SOAK_POINTS = 30.0;

    /** The share of the time a cooldown-gated system is actually up. */
    private static double duty(IntegralSystemDef system) {
        double window = system.durationSeconds();
        double cycle = window + system.cooldownSeconds();
        return cycle <= 0 ? 1.0 : window / cycle;
    }

    private static String round(double value) {
        return String.valueOf(Math.round(value * 100) / 100.0);
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
