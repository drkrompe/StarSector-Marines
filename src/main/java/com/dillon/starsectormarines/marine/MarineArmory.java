package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    /** Reusable fire-team designs; the legacy field name is retained for save compatibility. */
    private List<FireTeamTemplateCard> templateCards = new ArrayList<>();
    /** Saved three-template compositions; applying one is still an inventory transaction. */
    private List<SquadArrangement> squadArrangements = new ArrayList<>();
    /** Player-authored squad weapon definitions. Authoring never consumes stock. */
    private List<SquadWeaponDoctrine> customWeaponDoctrines = new ArrayList<>();
    /** Player-authored squad armour definitions. Authoring never consumes stock. */
    private List<SquadArmorDoctrine> customArmorDoctrines = new ArrayList<>();

    public MarineArmory() {
        seedStarterIssue();
        seedStarterCards();
    }

    public int fabricationMaterials() { return fabricationMaterials; }
    public int victories() { return victories; }
    public int highRiskVictories() { return highRiskVictories; }
    public Set<String> unlockedRecipes() {
        return Collections.unmodifiableSet(unlockedRecipes);
    }
    public List<FireTeamTemplateCard> templateCards() {
        return Collections.unmodifiableList(templateCards);
    }
    public List<SquadArrangement> squadArrangements() {
        return Collections.unmodifiableList(squadArrangements);
    }
    public List<SquadWeaponDoctrine> weaponDoctrines() {
        List<SquadWeaponDoctrine> result = new ArrayList<>(
                SquadEquipmentDoctrines.weaponDoctrines());
        result.addAll(customWeaponDoctrines);
        return Collections.unmodifiableList(result);
    }
    public List<SquadArmorDoctrine> armorDoctrines() {
        List<SquadArmorDoctrine> result = new ArrayList<>(
                SquadEquipmentDoctrines.armorDoctrines());
        result.addAll(customArmorDoctrines);
        return Collections.unmodifiableList(result);
    }
    public SquadWeaponDoctrine weaponDoctrineById(String id) {
        SquadWeaponDoctrine builtIn = SquadEquipmentDoctrines.weaponById(id);
        if (builtIn != null) return builtIn;
        if (id == null) return null;
        for (SquadWeaponDoctrine doctrine : customWeaponDoctrines) {
            if (doctrine != null && id.equals(doctrine.id())) return doctrine;
        }
        return null;
    }
    public SquadArmorDoctrine armorDoctrineById(String id) {
        SquadArmorDoctrine builtIn = SquadEquipmentDoctrines.armorById(id);
        if (builtIn != null) return builtIn;
        if (id == null) return null;
        for (SquadArmorDoctrine doctrine : customArmorDoctrines) {
            if (doctrine != null && id.equals(doctrine.id())) return doctrine;
        }
        return null;
    }

    public SquadWeaponDoctrine createWeaponDoctrine(
            String displayName, List<SquadWeaponIssue> issues) {
        return createWeaponDoctrine("custom:weapons:" + UUID.randomUUID(),
                displayName, issues);
    }

    public SquadArmorDoctrine createArmorDoctrine(
            String displayName, List<MarineArmorPattern> issues) {
        return createArmorDoctrine("custom:armor:" + UUID.randomUUID(),
                displayName, issues);
    }

    public SquadWeaponDoctrine cloneWeaponDoctrine(String sourceId) {
        SquadWeaponDoctrine source = weaponDoctrineById(sourceId);
        return source != null
                ? createWeaponDoctrine(source.displayName() + " Copy", source.issues()) : null;
    }

    public SquadArmorDoctrine cloneArmorDoctrine(String sourceId) {
        SquadArmorDoctrine source = armorDoctrineById(sourceId);
        return source != null
                ? createArmorDoctrine(source.displayName() + " Copy", source.issues()) : null;
    }

    public boolean renameWeaponDoctrine(String id, String displayName) {
        int index = customWeaponDoctrineIndex(id);
        if (index < 0 || displayName == null || displayName.isBlank()) return false;
        SquadWeaponDoctrine existing = customWeaponDoctrines.get(index);
        customWeaponDoctrines.set(index, new SquadWeaponDoctrine(
                existing.id(), displayName, existing.description(), existing.issues()));
        return true;
    }

    public boolean renameArmorDoctrine(String id, String displayName) {
        int index = customArmorDoctrineIndex(id);
        if (index < 0 || displayName == null || displayName.isBlank()) return false;
        SquadArmorDoctrine existing = customArmorDoctrines.get(index);
        customArmorDoctrines.set(index, new SquadArmorDoctrine(
                existing.id(), displayName, existing.description(), existing.issues()));
        return true;
    }

    boolean deleteWeaponDoctrine(String id) {
        int index = customWeaponDoctrineIndex(id);
        if (index < 0) return false;
        customWeaponDoctrines.remove(index);
        return true;
    }

    boolean deleteArmorDoctrine(String id) {
        int index = customArmorDoctrineIndex(id);
        if (index < 0) return false;
        customArmorDoctrines.remove(index);
        return true;
    }

    SquadWeaponDoctrine ensureWeaponDoctrine(
            String id, String displayName, List<SquadWeaponIssue> issues) {
        SquadWeaponDoctrine existing = weaponDoctrineById(id);
        return existing != null ? existing : createWeaponDoctrine(id, displayName, issues);
    }

    SquadArmorDoctrine ensureArmorDoctrine(
            String id, String displayName, List<MarineArmorPattern> issues) {
        SquadArmorDoctrine existing = armorDoctrineById(id);
        return existing != null ? existing : createArmorDoctrine(id, displayName, issues);
    }
    public FireTeamTemplateCard templateCardById(String id) {
        if (id == null) return null;
        for (FireTeamTemplateCard card : templateCards) {
            if (card != null && id.equals(card.id())) return card;
        }
        return null;
    }

    public SquadArrangement squadArrangementById(String id) {
        if (id == null) return null;
        for (SquadArrangement arrangement : squadArrangements) {
            if (arrangement != null && id.equals(arrangement.id())) return arrangement;
        }
        return null;
    }

    /**
     * Saves a player-authored design. Stock and recipe state are deliberately
     * ignored: those constrain assignment, not what the player may design.
     */
    public FireTeamTemplateCard createTemplateCard(String displayName,
                                                   List<FireTeamBillet> billets) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Template name is required");
        }
        FireTeamTemplateCard card = new FireTeamTemplateCard(
                "custom:" + UUID.randomUUID(), displayName, billets);
        templateCards.add(card);
        return card;
    }

    /** Clones either a built-in or custom template into a new player-owned design. */
    public FireTeamTemplateCard cloneTemplateCard(String sourceId) {
        FireTeamTemplateCard source = templateCardById(sourceId);
        if (source == null) return null;
        return createTemplateCard(source.displayName() + " Copy", source.billets());
    }

    /** Renaming changes library metadata only and is therefore safe for assigned templates. */
    public boolean renameTemplateCard(String id, String displayName) {
        FireTeamTemplateCard card = templateCardById(id);
        if (card == null || FireTeamTemplateCards.isStarterId(id)
                || displayName == null || displayName.isBlank()) return false;
        card.rename(displayName);
        return true;
    }

    /** Assignment-aware callers must reject templates with any live reference. */
    boolean deleteTemplateCard(String id) {
        if (id == null || FireTeamTemplateCards.isStarterId(id)) return false;
        return templateCards.removeIf(card -> id.equals(card.id()));
    }

    /** Saves a reusable three-template plan without consulting recipes or stock. */
    public SquadArrangement createSquadArrangement(String displayName,
                                                   List<String> templateIds) {
        SquadArrangement arrangement = new SquadArrangement(
                "arrangement:" + UUID.randomUUID(), displayName, templateIds);
        for (String templateId : arrangement.templateIds()) {
            if (templateCardById(templateId) == null) {
                throw new IllegalArgumentException(
                        "Arrangement references an unknown fire-team template");
            }
        }
        squadArrangements.add(arrangement);
        return arrangement;
    }

    /** Renaming changes plan metadata only and never refits a squad. */
    public boolean renameSquadArrangement(String id, String displayName) {
        SquadArrangement arrangement = squadArrangementById(id);
        if (arrangement == null || displayName == null || displayName.isBlank()) return false;
        arrangement.rename(displayName);
        return true;
    }

    /** Deleting a convenience plan never changes existing team assignments. */
    public boolean deleteSquadArrangement(String id) {
        return id != null && squadArrangements.removeIf(
                arrangement -> id.equals(arrangement.id()));
    }

    public boolean isTemplateReferencedByArrangement(String templateId) {
        if (templateId == null) return false;
        for (SquadArrangement arrangement : squadArrangements) {
            if (arrangement != null
                    && arrangement.referencesTemplate(templateId)) return true;
        }
        return false;
    }

    public void addFabricationMaterials(int amount) {
        fabricationMaterials = Math.max(0, fabricationMaterials + amount);
    }

    public boolean isPrimaryUnlocked(MarineWeapon weapon, EquipmentGrade grade) {
        if (weapon == MarineWeapon.FIELD_RIFLE) return grade == EquipmentGrade.SERVICE;
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
        if (weapon == MarineWeapon.FIELD_RIFLE) return false;
        String key = primaryKey(weapon, grade);
        return print(key, primaryFabricationCost(grade));
    }

    public boolean printSecondary(MarineSecondary secondary) {
        return print(secondaryKey(secondary), secondaryFabricationCost(secondary));
    }

    public boolean printArmor(MarineArmorPattern armor) {
        return print(armorKey(armor), armorFabricationCost(armor));
    }

    public boolean canPrintPrimary(MarineWeapon weapon, EquipmentGrade grade) {
        return weapon != MarineWeapon.FIELD_RIFLE && isPrimaryUnlocked(weapon, grade)
                && fabricationMaterials >= primaryFabricationCost(grade);
    }

    public boolean canPrintSecondary(MarineSecondary secondary) {
        return isSecondaryUnlocked(secondary)
                && fabricationMaterials >= secondaryFabricationCost(secondary);
    }

    public boolean canPrintArmor(MarineArmorPattern armor) {
        return isArmorUnlocked(armor)
                && fabricationMaterials >= armorFabricationCost(armor);
    }

    /** Award fabrication feedstock and open recipes at stable operation milestones. */
    public void recordVictory(int materialReward, boolean highRisk) {
        victories++;
        if (highRisk) highRiskVictories++;
        addFabricationMaterials(materialReward);
        if (victories >= 2) unlockPrimary(MarineWeapon.PULSE_RIFLE, EquipmentGrade.MILSPEC);
        if (victories >= 2) unlockSecondary(MarineSecondary.FRAG_GRENADE);
        if (victories >= 3) unlockPrimary(MarineWeapon.SMG, EquipmentGrade.MILSPEC);
        if (victories >= 4) unlockPrimary(MarineWeapon.DMR, EquipmentGrade.MILSPEC);
        if (victories >= 4) unlockPrimary(MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.MILSPEC);
        // The first aspirational chase item: proven operations plus one dangerous field test.
        if (victories >= 5 && highRiskVictories >= 1) {
            unlockPrimary(MarineWeapon.DMR, EquipmentGrade.MASTERWORK);
        }
    }

    /** Fatigues remain counted stock; the FR-1 service rifle itself is unlimited fleet issue. */
    public void ensureBasicIssue(int soldierCount) {
        putAtLeast(armorKey(MarineArmorPattern.ARMORLESS), soldierCount);
    }

    private boolean print(String key, int cost) {
        if (!unlockedRecipes.contains(key) || fabricationMaterials < cost) return false;
        fabricationMaterials -= cost;
        printedGear.merge(key, 1, Integer::sum);
        return true;
    }

    private void seedStarterIssue() {
        unlockPrimary(MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE);
        unlockPrimary(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE);
        unlockPrimary(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SURPLUS);
        unlockPrimary(MarineWeapon.SMG, EquipmentGrade.SERVICE);
        unlockPrimary(MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.SERVICE);
        unlockPrimary(MarineWeapon.DMR, EquipmentGrade.SERVICE);
        unlockSecondary(MarineSecondary.ROCKET_LAUNCHER);
        unlockSecondary(MarineSecondary.ANTI_MATERIEL_RIFLE);
        unlockSecondary(MarineSecondary.SMOKE_GRENADE);
        unlockSecondary(MarineSecondary.SATCHEL_CHARGE);
        unlockArmor(MarineArmorPattern.ARMORLESS);
        unlockArmor(MarineArmorPattern.MILITIA);
        unlockArmor(MarineArmorPattern.CHARCOAL);
        unlockArmor(MarineArmorPattern.ARMY_GREEN);
        printedGear.put(primaryKey(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE), 12);
        printedGear.put(primaryKey(MarineWeapon.PULSE_RIFLE, EquipmentGrade.SURPLUS), 1);
        printedGear.put(primaryKey(MarineWeapon.SMG, EquipmentGrade.SERVICE), 3);
        printedGear.put(primaryKey(MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.SERVICE), 3);
        printedGear.put(primaryKey(MarineWeapon.DMR, EquipmentGrade.SERVICE), 3);
        printedGear.put(secondaryKey(MarineSecondary.ROCKET_LAUNCHER), 2);
        printedGear.put(secondaryKey(MarineSecondary.ANTI_MATERIEL_RIFLE), 1);
        printedGear.put(secondaryKey(MarineSecondary.SMOKE_GRENADE), 2);
        printedGear.put(secondaryKey(MarineSecondary.SATCHEL_CHARGE), 2);
        printedGear.put(armorKey(MarineArmorPattern.ARMORLESS), 12);
        printedGear.put(armorKey(MarineArmorPattern.MILITIA), 3);
        printedGear.put(armorKey(MarineArmorPattern.CHARCOAL), 6);
        printedGear.put(armorKey(MarineArmorPattern.ARMY_GREEN), 4);
    }

    private void seedStarterCards() {
        List<FireTeamTemplateCard> ordered = new ArrayList<>();
        for (FireTeamTemplateCard starter : FireTeamTemplateCards.starterCards()) {
            // Built-ins are versioned library fixtures rather than player
            // documents. Reinstall the current definition on load so an
            // existing save receives catalog migrations such as the Fire
            // Support team's squad automatic; custom clones remain untouched.
            ordered.add(starter);
        }
        for (FireTeamTemplateCard card : templateCards) {
            if (card != null && !FireTeamTemplateCards.isStarterId(card.id())
                    && ordered.stream().noneMatch(existing -> existing.id().equals(card.id()))) {
                ordered.add(card);
            }
        }
        templateCards = ordered;
    }

    private SquadWeaponDoctrine createWeaponDoctrine(
            String id, String displayName, List<SquadWeaponIssue> issues) {
        if (weaponDoctrineById(id) != null) {
            throw new IllegalArgumentException("Weapon doctrine id already exists: " + id);
        }
        SquadWeaponDoctrine doctrine = new SquadWeaponDoctrine(
                id, displayName, "Player-authored squad weapon definition.", issues);
        customWeaponDoctrines.add(doctrine);
        return doctrine;
    }

    private SquadArmorDoctrine createArmorDoctrine(
            String id, String displayName, List<MarineArmorPattern> issues) {
        if (armorDoctrineById(id) != null) {
            throw new IllegalArgumentException("Armor doctrine id already exists: " + id);
        }
        SquadArmorDoctrine doctrine = new SquadArmorDoctrine(
                id, displayName, "Player-authored squad armour definition.", issues);
        customArmorDoctrines.add(doctrine);
        return doctrine;
    }

    private int customWeaponDoctrineIndex(String id) {
        if (id == null) return -1;
        for (int index = 0; index < customWeaponDoctrines.size(); index++) {
            SquadWeaponDoctrine doctrine = customWeaponDoctrines.get(index);
            if (doctrine != null && id.equals(doctrine.id())) return index;
        }
        return -1;
    }

    private int customArmorDoctrineIndex(String id) {
        if (id == null) return -1;
        for (int index = 0; index < customArmorDoctrines.size(); index++) {
            SquadArmorDoctrine doctrine = customArmorDoctrines.get(index);
            if (doctrine != null && id.equals(doctrine.id())) return index;
        }
        return -1;
    }

    private void putAtLeast(String key, int count) {
        if (printedGear.getOrDefault(key, 0) < count) printedGear.put(key, count);
    }

    public static int primaryFabricationCost(EquipmentGrade grade) {
        if (grade == null) return 2;
        return switch (grade) {
            case SURPLUS -> 1;
            case SERVICE -> 2;
            case MILSPEC -> 4;
            case MASTERWORK -> 8;
        };
    }

    public static int secondaryFabricationCost(MarineSecondary secondary) {
        return 5;
    }

    public static int armorFabricationCost(MarineArmorPattern armor) {
        return armor == MarineArmorPattern.ARMORLESS ? 1 : 3;
    }

    public static String primaryKey(MarineWeapon weapon, EquipmentGrade grade) {
        return "primary:" + weapon.name() + ":" + grade.name();
    }
    public static String secondaryKey(MarineSecondary secondary) {
        return "special:" + secondary.specialEquipmentId;
    }
    public static String armorKey(MarineArmorPattern armor) {
        return "armor:" + armor.name();
    }

    private Object readResolve() {
        if (printedGear == null) printedGear = new HashMap<>();
        if (unlockedRecipes == null) unlockedRecipes = new HashSet<>();
        if (templateCards == null) templateCards = new ArrayList<>();
        if (squadArrangements == null) squadArrangements = new ArrayList<>();
        if (customWeaponDoctrines == null) customWeaponDoctrines = new ArrayList<>();
        if (customArmorDoctrines == null) customArmorDoctrines = new ArrayList<>();
        customWeaponDoctrines.removeIf(doctrine -> doctrine == null
                || SquadEquipmentDoctrines.weaponById(doctrine.id()) != null);
        customArmorDoctrines.removeIf(doctrine -> doctrine == null
                || SquadEquipmentDoctrines.armorById(doctrine.id()) != null);
        migrateLegacySecondaryKeys();
        if (unlockedRecipes.isEmpty()) seedStarterIssue();
        seedStarterCards();
        // Existing saves predate the recruit-grade field rifle recipe.
        unlockPrimary(MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE);
        unlockPrimary(MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.SERVICE);
        putAtLeast(primaryKey(MarineWeapon.SQUAD_AUTOMATIC, EquipmentGrade.SERVICE), 3);
        unlockArmor(MarineArmorPattern.MILITIA);
        putAtLeast(armorKey(MarineArmorPattern.MILITIA), MarineSquad.TEAMS_PER_SQUAD);
        unlockSecondary(MarineSecondary.ANTI_MATERIEL_RIFLE);
        putAtLeast(secondaryKey(MarineSecondary.ANTI_MATERIEL_RIFLE), 1);
        unlockSecondary(MarineSecondary.SMOKE_GRENADE);
        putAtLeast(secondaryKey(MarineSecondary.SMOKE_GRENADE), 2);
        unlockSecondary(MarineSecondary.SATCHEL_CHARGE);
        putAtLeast(secondaryKey(MarineSecondary.SATCHEL_CHARGE), 2);
        if (victories >= 2) unlockSecondary(MarineSecondary.FRAG_GRENADE);
        fabricationMaterials = Math.max(0, fabricationMaterials);
        victories = Math.max(0, victories);
        highRiskVictories = Math.max(0, highRiskVictories);
        return this;
    }

    private void migrateLegacySecondaryKeys() {
        for (MarineSecondary secondary : MarineSecondary.values()) {
            String legacy = "secondary:" + secondary.name();
            String stable = secondaryKey(secondary);
            Integer owned = printedGear.remove(legacy);
            if (owned != null) printedGear.merge(stable, owned, Math::max);
            if (unlockedRecipes.remove(legacy)) unlockedRecipes.add(stable);
        }
    }
}
