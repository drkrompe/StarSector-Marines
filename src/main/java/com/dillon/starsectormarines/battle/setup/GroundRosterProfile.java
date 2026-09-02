package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Immutable, data-authored ground doctrine frozen for one defending campaign
 * faction. It owns personnel and equipment identity only; mission setup still
 * owns force size, risk, tier, reinforcement tickets, delivery means, AI, and
 * objectives.
 *
 * <p>The {@link UnitType} values are temporary body/stat compatibility shells.
 * Faction identity lives in stable weapon, armor, special-equipment, and mech
 * ids, so a later art/color pass does not invalidate doctrine data.
 */
public final class GroundRosterProfile {

    public enum ForceTier { BULK, ELITE }

    /** One weighted doctrine table. Package-private construction keeps parsing centralized. */
    static final class WeightedTable<T> {
        private final List<Entry<T>> entries;
        private final int totalWeight;

        WeightedTable(List<Entry<T>> entries) {
            if (entries == null || entries.isEmpty()) {
                throw new IllegalArgumentException("weighted roster table must not be empty");
            }
            this.entries = List.copyOf(entries);
            int total = 0;
            for (Entry<T> entry : entries) {
                if (entry.weight <= 0) {
                    throw new IllegalArgumentException("roster weights must be positive");
                }
                total = Math.addExact(total, entry.weight);
            }
            totalWeight = total;
        }

        /** Every value the table can roll, in the order it was authored or derived. */
        List<T> values() {
            List<T> out = new ArrayList<>(entries.size());
            for (Entry<T> entry : entries) out.add(entry.value);
            return List.copyOf(out);
        }

        T pick(Random rng) {
            int roll = rng.nextInt(totalWeight);
            for (Entry<T> entry : entries) {
                roll -= entry.weight;
                if (roll < 0) return entry.value;
            }
            throw new IllegalStateException("weighted roster roll escaped its table");
        }
    }

    static final class Entry<T> {
        final T value;
        final int weight;

        Entry(T value, int weight) {
            this.value = value;
            this.weight = weight;
        }
    }

    /** Bulk or elite issue package, with risk selecting eligibility/quality curves. */
    public static final class Issue {
        private final UnitType unitType;
        private final WeightedTable<WeaponDef> primaries;
        private final Map<RiskLevel, WeightedTable<EquipmentGrade>> grades;
        private final Map<RiskLevel, WeightedTable<MarineArmorCatalogDef>> armor;
        private final Map<RiskLevel, WeightedTable<SpecialEquipmentDef>> specials;

        Issue(UnitType unitType, WeightedTable<WeaponDef> primaries,
              Map<RiskLevel, WeightedTable<EquipmentGrade>> grades,
              Map<RiskLevel, WeightedTable<MarineArmorCatalogDef>> armor,
              Map<RiskLevel, WeightedTable<SpecialEquipmentDef>> specials) {
            this.unitType = unitType;
            this.primaries = primaries;
            this.grades = completeRiskMap(grades, "equipment grades");
            this.armor = completeRiskMap(armor, "armor");
            this.specials = completeRiskMap(specials, "special issue");
        }

        public UnitType unitType() { return unitType; }
        public WeaponDef pickPrimaryDef(Random rng) { return primaries.pick(rng); }
        public EquipmentGrade pickGrade(RiskLevel risk, Random rng) {
            return grades.get(resolvedRisk(risk)).pick(rng);
        }
        public MarineArmorCatalogDef pickArmorDef(RiskLevel risk, Random rng) {
            return armor.get(resolvedRisk(risk)).pick(rng);
        }
        public MarineArmorPattern pickArmor(RiskLevel risk, Random rng) {
            return MarineArmorPattern.fromId(pickArmorDef(risk, rng).id());
        }
        public SpecialEquipmentDef pickSpecialDef(RiskLevel risk, Random rng) {
            return specials.get(resolvedRisk(risk)).pick(rng);
        }

        /**
         * Every grade this tier can be issued at {@code risk}. For a panel that
         * has to say what a doctrine currently yields: sampling a table cannot
         * answer "which grades are admitted at all" without rolling it to death,
         * and re-deriving the answer beside the derivation is how the two drift.
         */
        public List<EquipmentGrade> grades(RiskLevel risk) {
            return grades.get(resolvedRisk(risk)).values();
        }

        /** Every armour pattern this tier can be issued at {@code risk}. */
        public List<MarineArmorCatalogDef> armorPatterns(RiskLevel risk) {
            return armor.get(resolvedRisk(risk)).values();
        }

        private static <T> Map<RiskLevel, WeightedTable<T>> completeRiskMap(
                Map<RiskLevel, WeightedTable<T>> source, String label) {
            EnumMap<RiskLevel, WeightedTable<T>> copy = new EnumMap<>(RiskLevel.class);
            copy.putAll(source);
            for (RiskLevel risk : RiskLevel.values()) {
                if (!copy.containsKey(risk)) {
                    throw new IllegalArgumentException("missing " + label + " table for " + risk);
                }
            }
            return Map.copyOf(copy);
        }

        /** Starts an issue package for one force tier. */
        public static Builder builder(UnitType unitType) {
            return new Builder(unitType);
        }

        /**
         * Assembles one {@link Issue} from weighted entries rather than from
         * JSON, so a <em>derived</em> doctrine — the polity's, which is rebuilt
         * from its own economy rather than authored — is built through the same
         * construction and the same validation as every catalogued one. The
         * weighted tables stay package-private; a caller states values and
         * weights and never holds a table.
         */
        public static final class Builder {
            private final UnitType unitType;
            private final List<Entry<WeaponDef>> primaries = new ArrayList<>();
            private final EnumMap<RiskLevel, List<Entry<EquipmentGrade>>> grades =
                    new EnumMap<>(RiskLevel.class);
            private final EnumMap<RiskLevel, List<Entry<MarineArmorCatalogDef>>> armor =
                    new EnumMap<>(RiskLevel.class);
            private final EnumMap<RiskLevel, List<Entry<SpecialEquipmentDef>>> specials =
                    new EnumMap<>(RiskLevel.class);

            private Builder(UnitType unitType) {
                if (unitType == null) {
                    throw new IllegalArgumentException("issue unitType is required");
                }
                if (!unitType.usesInfantryTraining()) {
                    throw new IllegalArgumentException(
                            "issue unitType must be trained infantry, got " + unitType);
                }
                this.unitType = unitType;
            }

            public Builder primary(WeaponDef weapon, int weight) {
                if (weapon == null) {
                    throw new IllegalArgumentException("issue primary weapon is required");
                }
                if (weapon.mount != MountClass.MARINE_PRIMARY) {
                    throw new IllegalArgumentException(
                            "issue references non-primary weapon '" + weapon.id + "'");
                }
                primaries.add(new Entry<>(weapon, weight));
                return this;
            }

            public Builder grade(RiskLevel risk, EquipmentGrade grade, int weight) {
                if (grade == null) {
                    throw new IllegalArgumentException("issue equipment grade is required");
                }
                bucket(grades, risk).add(new Entry<>(grade, weight));
                return this;
            }

            public Builder armor(RiskLevel risk, MarineArmorCatalogDef pattern, int weight) {
                if (pattern == null) {
                    throw new IllegalArgumentException("issue armor pattern is required");
                }
                bucket(armor, risk).add(new Entry<>(pattern, weight));
                return this;
            }

            /**
             * A null {@code special} is the authored "none" outcome — the roll
             * that issues no special item at all — exactly as the catalog spells
             * it, so a derived table and an authored one read the same.
             */
            public Builder special(RiskLevel risk, SpecialEquipmentDef special, int weight) {
                bucket(specials, risk).add(new Entry<>(special, weight));
                return this;
            }

            public Issue build() {
                return new Issue(unitType, new WeightedTable<>(primaries),
                        tables(grades), tables(armor), tables(specials));
            }

            private static <T> List<Entry<T>> bucket(
                    EnumMap<RiskLevel, List<Entry<T>>> source, RiskLevel risk) {
                if (risk == null) throw new IllegalArgumentException("issue risk level is required");
                return source.computeIfAbsent(risk, key -> new ArrayList<>());
            }

            /** A risk left out entirely is reported by {@link #completeRiskMap}. */
            private static <T> Map<RiskLevel, WeightedTable<T>> tables(
                    EnumMap<RiskLevel, List<Entry<T>>> source) {
                EnumMap<RiskLevel, WeightedTable<T>> result = new EnumMap<>(RiskLevel.class);
                for (Map.Entry<RiskLevel, List<Entry<T>>> entry : source.entrySet()) {
                    result.put(entry.getKey(), new WeightedTable<>(entry.getValue()));
                }
                return result;
            }
        }
    }

    private final String id;
    private final Set<String> factionIds;
    private final Issue bulk;
    private final Issue elite;
    private final List<MechVariant> heavySupport;

    GroundRosterProfile(String id, Set<String> factionIds, Issue bulk, Issue elite,
                        List<MechVariant> heavySupport) {
        this.id = id;
        // Authored order, not Set.copyOf: an immutable JDK set salts its iteration
        // order per JVM run, and the first authored faction id is this profile's
        // stable public name (see primaryFactionId).
        this.factionIds = Collections.unmodifiableSet(new LinkedHashSet<>(factionIds));
        this.bulk = bulk;
        this.elite = elite;
        this.heavySupport = List.copyOf(heavySupport);
    }

    public String id() { return id; }
    public Set<String> factionIds() { return factionIds; }

    /**
     * The first faction id the catalog assigned to this profile — the one a
     * caller names when it wants <em>this</em> doctrine rather than a particular
     * campaign faction. Round-trips: {@code resolve(primaryFactionId())} returns
     * this profile.
     */
    public String primaryFactionId() { return factionIds.iterator().next(); }
    public Issue issue(ForceTier tier) { return tier == ForceTier.ELITE ? elite : bulk; }
    public UnitType unitType(ForceTier tier) { return issue(tier).unitType(); }
    public List<MechVariant> heavySupport() { return heavySupport; }

    /** Copies a doctrine support cycle to the requested size, preserving authored order. */
    public List<MechVariant> heavySupportCycle(int count) {
        if (count <= 0 || heavySupport.isEmpty()) return List.of();
        List<MechVariant> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(heavySupport.get(i % heavySupport.size()));
        }
        return List.copyOf(result);
    }

    private static RiskLevel resolvedRisk(RiskLevel risk) {
        return risk != null ? risk : RiskLevel.LOW;
    }

    /** Starts a profile under the given catalog id. */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * The one construction path for a profile. The catalog parser feeds it, and
     * so does the polity's derivation, so a derived doctrine cannot quietly
     * satisfy weaker rules than an authored one.
     */
    public static final class Builder {
        private final String id;
        private final Set<String> factionIds = new LinkedHashSet<>();
        private final List<MechVariant> heavySupport = new ArrayList<>();
        private Issue bulk;
        private Issue elite;

        private Builder(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Ground-roster profile id is required");
            }
            this.id = id;
        }

        /** Authored order is preserved; the first id is the profile's public name. */
        public Builder factionId(String factionId) {
            if (factionId == null || factionId.isBlank()) {
                throw new IllegalArgumentException(
                        "Ground-roster profile '" + id + "' has a blank faction id");
            }
            factionIds.add(factionId);
            return this;
        }

        public Builder factionIds(Collection<String> ids) {
            if (ids == null) {
                throw new IllegalArgumentException("Ground-roster faction ids are required");
            }
            for (String factionId : ids) factionId(factionId);
            return this;
        }

        public Builder bulk(Issue issue) {
            bulk = requireIssue(issue, "bulk");
            return this;
        }

        public Builder elite(Issue issue) {
            elite = requireIssue(issue, "elite");
            return this;
        }

        public Builder addHeavySupport(MechVariant variant) {
            if (variant == null) {
                throw new IllegalArgumentException(
                        "Ground-roster profile '" + id + "' has a null heavy-support variant");
            }
            heavySupport.add(variant);
            return this;
        }

        /** Replaces the support cycle; an empty cycle is legal and means no mechs. */
        public Builder heavySupport(Collection<MechVariant> variants) {
            if (variants == null) {
                throw new IllegalArgumentException(
                        "Ground-roster profile '" + id + "' has a null heavy-support cycle");
            }
            heavySupport.clear();
            for (MechVariant variant : variants) addHeavySupport(variant);
            return this;
        }

        public GroundRosterProfile build() {
            if (factionIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Ground-roster profile '" + id + "' has no faction ids");
            }
            if (bulk == null || elite == null) {
                throw new IllegalArgumentException("Ground-roster profile '" + id
                        + "' needs both a bulk and an elite issue");
            }
            return new GroundRosterProfile(id, factionIds, bulk, elite, heavySupport);
        }

        private Issue requireIssue(Issue issue, String tier) {
            if (issue == null) {
                throw new IllegalArgumentException("Ground-roster profile '" + id
                        + "' has no " + tier + " issue");
            }
            return issue;
        }
    }
}
