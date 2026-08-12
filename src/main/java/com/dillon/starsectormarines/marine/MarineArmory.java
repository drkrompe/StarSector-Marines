package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persisted fleet fabrication inventory. Recipes are permanent unlocks; printed
 * items are finite and consume one shared resource: masterwork parts & materials.
 */
public final class MarineArmory implements Serializable {

    private int fabricationMaterials;
    private int victories;
    private int highRiskVictories;
    private Map<String, Integer> printedGear = new HashMap<>();
    private Set<String> unlockedRecipes = new HashSet<>();

    public MarineArmory() {
        seedStarterIssue();
    }

    public int fabricationMaterials() { return fabricationMaterials; }
    public int victories() { return victories; }
    public int highRiskVictories() { return highRiskVictories; }
    public Set<String> unlockedRecipes() {
        return Collections.unmodifiableSet(unlockedRecipes);
    }

    public void addFabricationMaterials(int amount) {
        fabricationMaterials = Math.max(0, fabricationMaterials + amount);
    }

    public boolean isPrimaryUnlocked(MarineWeapon weapon, EquipmentGrade grade) {
        return unlockedRecipes.contains(primaryKey(weapon, grade));
    }

    public boolean isSecondaryUnlocked(MarineSecondary secondary) {
        return unlockedRecipes.contains(secondaryKey(secondary));
    }

    public boolean isArmorUnlocked(MarineArmorPattern armor) {
        return unlockedRecipes.contains(armorKey(armor));
    }

    public void unlockPrimary(MarineWeapon weapon, EquipmentGrade grade) {
        if (weapon != null && grade != null) unlockedRecipes.add(primaryKey(weapon, grade));
    }

    public void unlockSecondary(MarineSecondary secondary) {
        if (secondary != null) unlockedRecipes.add(secondaryKey(secondary));
    }

    public void unlockArmor(MarineArmorPattern armor) {
        if (armor != null) unlockedRecipes.add(armorKey(armor));
    }

    public int ownedPrimary(MarineWeapon weapon, EquipmentGrade grade) {
        return printedGear.getOrDefault(primaryKey(weapon, grade), 0);
    }

    public int ownedSecondary(MarineSecondary secondary) {
        return printedGear.getOrDefault(secondaryKey(secondary), 0);
    }

    public int ownedArmor(MarineArmorPattern armor) {
        return printedGear.getOrDefault(armorKey(armor), 0);
    }

    public boolean printPrimary(MarineWeapon weapon, EquipmentGrade grade) {
        String key = primaryKey(weapon, grade);
        return print(key, primaryCost(grade));
    }

    public boolean printSecondary(MarineSecondary secondary) {
        return print(secondaryKey(secondary), 5);
    }

    public boolean printArmor(MarineArmorPattern armor) {
        return print(armorKey(armor), armor == MarineArmorPattern.ARMORLESS ? 1 : 3);
    }

    /** Award fabrication feedstock and open recipes at stable operation milestones. */
    public void recordVictory(int materialReward, boolean highRisk) {
        victories++;
        if (highRisk) highRiskVictories++;
        addFabricationMaterials(materialReward);
        if (victories >= 2) unlockPrimary(MarineWeapon.PULSE_RIFLE, EquipmentGrade.MILSPEC);
        if (victories >= 3) unlockPrimary(MarineWeapon.SMG, EquipmentGrade.MILSPEC);
        if (victories >= 4) unlockPrimary(MarineWeapon.DMR, EquipmentGrade.MILSPEC);
        // The first aspirational chase item: proven operations plus one dangerous field test.
        if (victories >= 5 && highRiskVictories >= 1) {
            unlockPrimary(MarineWeapon.DMR, EquipmentGrade.MASTERWORK);
        }
    }

    /** Standard issue is replenished freely; interesting upgrades consume fabrication stock. */
    public void ensureBasicIssue(int soldierCount) {
        putAtLeast(primaryKey(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE), soldierCount);
        putAtLeast(armorKey(MarineArmorPattern.ARMORLESS), soldierCount);
    }

    private boolean print(String key, int cost) {
        if (!unlockedRecipes.contains(key) || fabricationMaterials < cost) return false;
        fabricationMaterials -= cost;
        printedGear.merge(key, 1, Integer::sum);
        return true;
    }

    private void seedStarterIssue() {
        unlockPrimary(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE);
        unlockPrimary(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SURPLUS);
        unlockPrimary(MarineWeapon.SMG, EquipmentGrade.SERVICE);
        unlockPrimary(MarineWeapon.DMR, EquipmentGrade.SERVICE);
        unlockSecondary(MarineSecondary.ROCKET_LAUNCHER);
        unlockArmor(MarineArmorPattern.ARMORLESS);
        unlockArmor(MarineArmorPattern.CHARCOAL);
        unlockArmor(MarineArmorPattern.ARMY_GREEN);
        printedGear.put(primaryKey(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE), 12);
        printedGear.put(primaryKey(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SURPLUS), 1);
        printedGear.put(primaryKey(MarineWeapon.SMG, EquipmentGrade.SERVICE), 3);
        printedGear.put(primaryKey(MarineWeapon.DMR, EquipmentGrade.SERVICE), 3);
        printedGear.put(secondaryKey(MarineSecondary.ROCKET_LAUNCHER), 2);
        printedGear.put(armorKey(MarineArmorPattern.ARMORLESS), 12);
        printedGear.put(armorKey(MarineArmorPattern.CHARCOAL), 6);
        printedGear.put(armorKey(MarineArmorPattern.ARMY_GREEN), 4);
    }

    private void putAtLeast(String key, int count) {
        if (printedGear.getOrDefault(key, 0) < count) printedGear.put(key, count);
    }

    private static int primaryCost(EquipmentGrade grade) {
        if (grade == null) return 2;
        return switch (grade) {
            case SURPLUS -> 1;
            case SERVICE -> 2;
            case MILSPEC -> 4;
            case MASTERWORK -> 8;
        };
    }

    public static String primaryKey(MarineWeapon weapon, EquipmentGrade grade) {
        return "primary:" + weapon.name() + ":" + grade.name();
    }
    public static String secondaryKey(MarineSecondary secondary) {
        return "secondary:" + secondary.name();
    }
    public static String armorKey(MarineArmorPattern armor) {
        return "armor:" + armor.name();
    }

    private Object readResolve() {
        if (printedGear == null) printedGear = new HashMap<>();
        if (unlockedRecipes == null) unlockedRecipes = new HashSet<>();
        if (unlockedRecipes.isEmpty()) seedStarterIssue();
        fabricationMaterials = Math.max(0, fabricationMaterials);
        victories = Math.max(0, victories);
        highRiskVictories = Math.max(0, highRiskVictories);
        return this;
    }
}
