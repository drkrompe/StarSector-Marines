package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.EnumMap;
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
        public MarineWeapon pickPrimary(Random rng) {
            return MarineWeapon.fromId(pickPrimaryDef(rng).id);
        }
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
        public MarineSecondary pickSpecial(RiskLevel risk, Random rng) {
            SpecialEquipmentDef special = pickSpecialDef(risk, rng);
            return special != null ? SpecialEquipmentRegistry.compatibilityHandle(special.id()) : null;
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
    }

    private final String id;
    private final Set<String> factionIds;
    private final Issue bulk;
    private final Issue elite;
    private final List<MechVariant> heavySupport;

    GroundRosterProfile(String id, Set<String> factionIds, Issue bulk, Issue elite,
                        List<MechVariant> heavySupport) {
        this.id = id;
        this.factionIds = Set.copyOf(factionIds);
        this.bulk = bulk;
        this.elite = elite;
        this.heavySupport = List.copyOf(heavySupport);
    }

    public String id() { return id; }
    public Set<String> factionIds() { return factionIds; }
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
}
