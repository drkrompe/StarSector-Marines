package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persisted equipment-template ownership and reusable squad definitions. */
public final class MarineArmory implements Serializable {

    /** Legacy save data retained only to migrate the former print-stock economy. */
    private int fabricationMaterials;
    private int victories;
    private int highRiskVictories;
    private Map<String, Integer> printedGear = new HashMap<>();
    private Set<String> unlockedRecipes = new HashSet<>();
    /** Permanent collectible equipment templates used by all live issue paths. */
    private Set<String> ownedEquipmentTemplateIds = new HashSet<>();
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
    /** Legacy recipe ids retained for save compatibility and old fire-team APIs. */
    public Set<String> unlockedRecipes() {
        return Collections.unmodifiableSet(unlockedRecipes);
    }
    public Set<String> ownedEquipmentTemplateIds() {
        return Collections.unmodifiableSet(ownedEquipmentTemplateIds);
    }
    public List<EquipmentTemplateCard> equipmentTemplateCards() {
        List<EquipmentTemplateCard> cards = new ArrayList<>();
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            if (ownsEquipmentTemplate(card.id())) cards.add(card);
        }
        return Collections.unmodifiableList(cards);
    }
    public boolean ownsEquipmentTemplate(String id) {
        return id != null && ownedEquipmentTemplateIds.contains(id);
    }
    public boolean acquireEquipmentTemplate(String id) {
        if (!EquipmentTemplateCatalog.contains(id)) {
            throw new IllegalArgumentException("Unknown equipment template id '" + id + "'");
        }
        return ownedEquipmentTemplateIds.add(id);
    }
    public boolean ownsPrimaryTemplate(String weaponId, EquipmentGrade grade) {
        return weaponId != null && grade != null
                && ownsEquipmentTemplate(EquipmentTemplateCatalog.primaryId(weaponId, grade));
    }
    public boolean ownsPrimaryTemplate(WeaponDef weapon, EquipmentGrade grade) {
        return weapon != null && ownsPrimaryTemplate(weapon.id, grade);
    }
    public boolean ownsArmorTemplate(MarineArmorPattern armor) {
        return armor != null && ownsArmorTemplate(armor.id);
    }
    public boolean ownsArmorTemplate(String armorId) {
        return armorId != null
                && ownsEquipmentTemplate(EquipmentTemplateCatalog.armorId(armorId));
    }
    public boolean ownsSpecialTemplate(String specialId) {
        return specialId != null
                && ownsEquipmentTemplate(EquipmentTemplateCatalog.specialId(specialId));
    }
    public boolean ownsSpecialTemplate(SpecialEquipmentDef special) {
        return special != null && ownsSpecialTemplate(special.id());
    }
    public boolean canAuthorWeaponDoctrine(List<SquadWeaponIssue> issues) {
        if (issues == null || issues.size() != MarineSquad.CAPACITY) return false;
        for (SquadWeaponIssue issue : issues) {
            if (issue == null || !ownsPrimaryTemplate(issue.primaryId(), issue.grade())
                    || (issue.specialEquipmentId() != null
                    && (issue.specialDef() == null
                    || !ownsSpecialTemplate(issue.specialEquipmentId())))) {
                return false;
            }
        }
        return true;
    }
    public boolean canAuthorArmorDoctrine(List<MarineArmorPattern> issues) {
        if (issues == null || issues.size() != MarineSquad.CAPACITY) return false;
        for (MarineArmorPattern armor : issues) {
            if (!ownsArmorTemplate(armor)) return false;
        }
        return true;
    }
    public boolean canAuthorArmorDoctrineIds(List<String> issueIds) {
        if (issueIds == null || issueIds.size() != MarineSquad.CAPACITY) return false;
        for (String armorId : issueIds) {
            if (!ownsArmorTemplate(armorId)) return false;
        }
        return true;
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
                displayName, issues, true);
    }

    public SquadArmorDoctrine createArmorDoctrine(
            String displayName, List<MarineArmorPattern> issues) {
        return createArmorDoctrine("custom:armor:" + UUID.randomUUID(),
                displayName, issues, true);
    }

    public SquadArmorDoctrine createArmorDoctrineIds(
            String displayName, List<String> issueIds) {
        return createArmorDoctrineIds("custom:armor:" + UUID.randomUUID(),
                displayName, issueIds, true);
    }

    public SquadWeaponDoctrine cloneWeaponDoctrine(String sourceId) {
        SquadWeaponDoctrine source = weaponDoctrineById(sourceId);
        return source != null
                ? createWeaponDoctrine(source.displayName() + " Copy", source.issues()) : null;
    }

    public SquadArmorDoctrine cloneArmorDoctrine(String sourceId) {
        SquadArmorDoctrine source = armorDoctrineById(sourceId);
        return source != null
                ? createArmorDoctrineIds(source.displayName() + " Copy", source.issueIds()) : null;
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
        customArmorDoctrines.set(index, SquadArmorDoctrine.fromIds(
                existing.id(), displayName, existing.description(), existing.issueIds()));
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
        return existing != null ? existing
                : createWeaponDoctrine(id, displayName, issues, false);
    }

    SquadArmorDoctrine ensureArmorDoctrine(
            String id, String displayName, List<MarineArmorPattern> issues) {
        SquadArmorDoctrine existing = armorDoctrineById(id);
        return existing != null ? existing
                : createArmorDoctrine(id, displayName, issues, false);
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

    public boolean isPrimaryUnlocked(String weaponId, EquipmentGrade grade) {
        return ownsPrimaryTemplate(weaponId, grade);
    }
    public boolean isPrimaryUnlocked(WeaponDef weapon, EquipmentGrade grade) {
        return weapon != null && isPrimaryUnlocked(weapon.id, grade);
    }

    public boolean isSecondaryUnlocked(String specialEquipmentId) {
        return ownsSpecialTemplate(specialEquipmentId);
    }
    public boolean isSecondaryUnlocked(SpecialEquipmentDef special) {
        return special != null && isSecondaryUnlocked(special.id());
    }

    public boolean isArmorUnlocked(MarineArmorPattern armor) {
        return ownsArmorTemplate(armor);
    }

    public void unlockPrimary(String weaponId, EquipmentGrade grade) {
        if (weaponId != null && grade != null) {
            unlockedRecipes.add(primaryKey(weaponId, grade));
            String id = EquipmentTemplateCatalog.primaryId(weaponId, grade);
            if (EquipmentTemplateCatalog.contains(id)) acquireEquipmentTemplate(id);
        }
    }
    public void unlockPrimary(WeaponDef weapon, EquipmentGrade grade) {
        if (weapon != null) unlockPrimary(weapon.id, grade);
    }

    public void unlockSecondary(String specialEquipmentId) {
        if (specialEquipmentId != null) {
            unlockedRecipes.add(secondaryKey(specialEquipmentId));
            String id = EquipmentTemplateCatalog.specialId(specialEquipmentId);
            if (EquipmentTemplateCatalog.contains(id)) acquireEquipmentTemplate(id);
        }
    }
    public void unlockSecondary(SpecialEquipmentDef special) {
        if (special != null) unlockSecondary(special.id());
    }

    public void unlockArmor(MarineArmorPattern armor) {
        if (armor != null) unlockArmor(armor.id);
    }

    /** Compatibility unlock for enum-backed and additive data-authored armor patterns. */
    public void unlockArmor(String armorId) {
        if (armorId == null) return;
        try {
            unlockedRecipes.add(armorKey(MarineArmorPattern.fromId(armorId)));
        } catch (IllegalArgumentException ignored) {
            // Additive catalog ids have no legacy recipe-key representation.
        }
        acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId(armorId));
    }

    public int ownedPrimary(String weaponId, EquipmentGrade grade) {
        return printedGear.getOrDefault(primaryKey(weaponId, grade), 0);
    }
    public int ownedPrimary(WeaponDef weapon, EquipmentGrade grade) {
        return weapon != null ? ownedPrimary(weapon.id, grade) : 0;
    }

    public int ownedSecondary(String specialEquipmentId) {
        return printedGear.getOrDefault(secondaryKey(specialEquipmentId), 0);
    }
    public int ownedSecondary(SpecialEquipmentDef special) {
        return special != null ? ownedSecondary(special.id()) : 0;
    }

    public int ownedArmor(MarineArmorPattern armor) {
        return printedGear.getOrDefault(armorKey(armor), 0);
    }

    public boolean printPrimary(String weaponId, EquipmentGrade grade) {
        if (WeaponRegistry.STARTER_PRIMARY_ID.equals(weaponId)) return false;
        String key = primaryKey(weaponId, grade);
        return print(key, primaryFabricationCost(grade));
    }
    public boolean printPrimary(WeaponDef weapon, EquipmentGrade grade) {
        return weapon != null && printPrimary(weapon.id, grade);
    }

    public boolean printSecondary(String specialEquipmentId) {
        return print(secondaryKey(specialEquipmentId), secondaryFabricationCost(specialEquipmentId));
    }
    public boolean printSecondary(SpecialEquipmentDef special) {
        return special != null && printSecondary(special.id());
    }

    public boolean printArmor(MarineArmorPattern armor) {
        return print(armorKey(armor), armorFabricationCost(armor));
    }

    public boolean canPrintPrimary(String weaponId, EquipmentGrade grade) {
        return !WeaponRegistry.STARTER_PRIMARY_ID.equals(weaponId)
                && isPrimaryUnlocked(weaponId, grade)
                && fabricationMaterials >= primaryFabricationCost(grade);
    }
    public boolean canPrintPrimary(WeaponDef weapon, EquipmentGrade grade) {
        return weapon != null && canPrintPrimary(weapon.id, grade);
    }

    public boolean canPrintSecondary(String specialEquipmentId) {
        return isSecondaryUnlocked(specialEquipmentId)
                && fabricationMaterials >= secondaryFabricationCost(specialEquipmentId);
    }
    public boolean canPrintSecondary(SpecialEquipmentDef special) {
        return special != null && canPrintSecondary(special.id());
    }

    public boolean canPrintArmor(MarineArmorPattern armor) {
        return isArmorUnlocked(armor)
                && fabricationMaterials >= armorFabricationCost(armor);
    }

    /** Awards permanent template cards at stable operation milestones. */
    public void recordVictory(boolean highRisk) {
        recordVictory(highRisk, Set.of());
    }

    /** Cargo-held cards count toward the breadth floor without being learned implicitly. */
    public void recordVictory(boolean highRisk, Set<String> acquiredOrCarriedTemplateIds) {
        victories++;
        if (highRisk) highRiskVictories++;
        repairFixedVictoryMilestones();
        repairCollectionProgression(acquiredOrCarriedTemplateIds);
    }

    private void repairFixedVictoryMilestones() {
        if (victories >= 2) unlockPrimary(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.MILSPEC);
        if (victories >= 2) unlockSecondary(SpecialEquipmentRegistry.FRAG_GRENADE_ID);
        if (victories >= 3) unlockPrimary(WeaponRegistry.SMG_ID, EquipmentGrade.MILSPEC);
        if (victories >= 4) unlockPrimary(WeaponRegistry.DMR_ID, EquipmentGrade.MILSPEC);
        if (victories >= 4) unlockPrimary(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.MILSPEC);
        // The first aspirational chase item: proven operations plus one dangerous field test.
        if (victories >= 5 && highRiskVictories >= 1) {
            unlockPrimary(WeaponRegistry.DMR_ID, EquipmentGrade.MASTERWORK);
        }
    }

    /** Idempotent save-load and mission-resolution bridge for the collection safety net. */
    public void repairCollectionProgression(Set<String> acquiredOrCarriedTemplateIds) {
        EquipmentCollectionCurve.repair(this, victories, acquiredOrCarriedTemplateIds);
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
        unlockPrimary(WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SERVICE);
        unlockPrimary(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.SERVICE);
        unlockPrimary(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.SURPLUS);
        unlockPrimary(WeaponRegistry.SMG_ID, EquipmentGrade.SERVICE);
        unlockPrimary(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.SERVICE);
        unlockPrimary(WeaponRegistry.DMR_ID, EquipmentGrade.SERVICE);
        unlockSecondary(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID);
        unlockSecondary(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID);
        unlockSecondary(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        unlockSecondary(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
        unlockArmor(MarineArmorPattern.ARMORLESS);
        unlockArmor(MarineArmorPattern.MILITIA);
        unlockArmor(MarineArmorPattern.CHARCOAL);
        unlockArmor(MarineArmorPattern.ARMY_GREEN);
        printedGear.put(primaryKey(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.SERVICE), 12);
        printedGear.put(primaryKey(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.SURPLUS), 1);
        printedGear.put(primaryKey(WeaponRegistry.SMG_ID, EquipmentGrade.SERVICE), 3);
        printedGear.put(primaryKey(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.SERVICE), 3);
        printedGear.put(primaryKey(WeaponRegistry.DMR_ID, EquipmentGrade.SERVICE), 3);
        printedGear.put(secondaryKey(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID), 2);
        printedGear.put(secondaryKey(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), 1);
        printedGear.put(secondaryKey(SpecialEquipmentRegistry.SMOKE_GRENADE_ID), 2);
        printedGear.put(secondaryKey(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), 2);
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
            String id, String displayName, List<SquadWeaponIssue> issues,
            boolean requireOwnedTemplates) {
        if (weaponDoctrineById(id) != null) {
            throw new IllegalArgumentException("Weapon doctrine id already exists: " + id);
        }
        if (requireOwnedTemplates && !canAuthorWeaponDoctrine(issues)) {
            throw new IllegalArgumentException(
                    "Weapon definition requires an unowned equipment template");
        }
        SquadWeaponDoctrine doctrine = new SquadWeaponDoctrine(
                id, displayName, "Player-authored squad weapon definition.", issues);
        customWeaponDoctrines.add(doctrine);
        return doctrine;
    }

    private SquadArmorDoctrine createArmorDoctrine(
            String id, String displayName, List<MarineArmorPattern> issues,
            boolean requireOwnedTemplates) {
        if (armorDoctrineById(id) != null) {
            throw new IllegalArgumentException("Armor doctrine id already exists: " + id);
        }
        if (requireOwnedTemplates && !canAuthorArmorDoctrine(issues)) {
            throw new IllegalArgumentException(
                    "Armor definition requires an unowned equipment template");
        }
        SquadArmorDoctrine doctrine = new SquadArmorDoctrine(
                id, displayName, "Player-authored squad armour definition.", issues);
        customArmorDoctrines.add(doctrine);
        return doctrine;
    }

    private SquadArmorDoctrine createArmorDoctrineIds(
            String id, String displayName, List<String> issueIds,
            boolean requireOwnedTemplates) {
        if (armorDoctrineById(id) != null) {
            throw new IllegalArgumentException("Armor doctrine id already exists: " + id);
        }
        if (requireOwnedTemplates && !canAuthorArmorDoctrineIds(issueIds)) {
            throw new IllegalArgumentException(
                    "Armor definition requires an unowned equipment template");
        }
        SquadArmorDoctrine doctrine = SquadArmorDoctrine.fromIds(
                id, displayName, "Player-authored squad armour definition.", issueIds);
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

    public static int secondaryFabricationCost(String specialEquipmentId) {
        return 5;
    }
    public static int secondaryFabricationCost(SpecialEquipmentDef special) { return 5; }

    public static int armorFabricationCost(MarineArmorPattern armor) {
        return armor == MarineArmorPattern.ARMORLESS ? 1 : 3;
    }

    public static String primaryKey(String weaponId, EquipmentGrade grade) {
        return "primary:" + weaponId + ":" + grade.name();
    }
    public static String primaryKey(WeaponDef weapon, EquipmentGrade grade) {
        return primaryKey(weapon.id, grade);
    }
    public static String secondaryKey(String specialEquipmentId) {
        return "special:" + specialEquipmentId;
    }
    public static String secondaryKey(SpecialEquipmentDef special) {
        return secondaryKey(special.id());
    }
    public static String armorKey(MarineArmorPattern armor) {
        return "armor:" + armor.name();
    }

    private Object readResolve() {
        if (printedGear == null) printedGear = new HashMap<>();
        if (unlockedRecipes == null) unlockedRecipes = new HashSet<>();
        if (ownedEquipmentTemplateIds == null) ownedEquipmentTemplateIds = new HashSet<>();
        if (templateCards == null) templateCards = new ArrayList<>();
        if (squadArrangements == null) squadArrangements = new ArrayList<>();
        if (customWeaponDoctrines == null) customWeaponDoctrines = new ArrayList<>();
        if (customArmorDoctrines == null) customArmorDoctrines = new ArrayList<>();
        customWeaponDoctrines.removeIf(doctrine -> doctrine == null
                || SquadEquipmentDoctrines.weaponById(doctrine.id()) != null);
        customArmorDoctrines.removeIf(doctrine -> doctrine == null
                || SquadEquipmentDoctrines.armorById(doctrine.id()) != null);
        migrateLegacyPrimaryKeys();
        migrateLegacySecondaryKeys();
        migrateLegacyTemplateOwnership();
        if (ownedEquipmentTemplateIds.isEmpty()) seedStarterIssue();
        seedStarterCards();
        // Existing saves predate the recruit-grade field rifle recipe.
        unlockPrimary(WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SERVICE);
        unlockPrimary(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.SERVICE);
        putAtLeast(primaryKey(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.SERVICE), 3);
        unlockArmor(MarineArmorPattern.MILITIA);
        putAtLeast(armorKey(MarineArmorPattern.MILITIA), MarineSquad.TEAMS_PER_SQUAD);
        unlockSecondary(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID);
        putAtLeast(secondaryKey(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), 1);
        unlockSecondary(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        putAtLeast(secondaryKey(SpecialEquipmentRegistry.SMOKE_GRENADE_ID), 2);
        unlockSecondary(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
        putAtLeast(secondaryKey(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), 2);
        fabricationMaterials = Math.max(0, fabricationMaterials);
        victories = Math.max(0, victories);
        highRiskVictories = Math.max(0, highRiskVictories);
        repairFixedVictoryMilestones();
        return this;
    }

    private void migrateLegacySecondaryKeys() {
        for (String legacyName : List.of("ROCKET_LAUNCHER", "ANTI_MATERIEL_RIFLE",
                "SMOKE_GRENADE", "SATCHEL_CHARGE", "FRAG_GRENADE")) {
            String specialId = SpecialEquipmentRegistry.legacyId(legacyName);
            String legacy = "secondary:" + legacyName;
            String stable = secondaryKey(specialId);
            Integer owned = printedGear.remove(legacy);
            if (owned != null) printedGear.merge(stable, owned, Math::max);
            if (unlockedRecipes.remove(legacy)) unlockedRecipes.add(stable);
        }
    }

    private void migrateLegacyPrimaryKeys() {
        for (String legacyName : List.of("FIELD_RIFLE", "PULSE_RIFLE", "SMG",
                "SQUAD_AUTOMATIC", "DMR", "DRONE_PULSE")) {
            String weaponId = WeaponRegistry.legacyMarinePrimaryId(legacyName);
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                String legacy = "primary:" + legacyName + ":" + grade.name();
                String stable = primaryKey(weaponId, grade);
                Integer owned = printedGear.remove(legacy);
                if (owned != null) printedGear.merge(stable, owned, Math::max);
                if (unlockedRecipes.remove(legacy)) unlockedRecipes.add(stable);
            }
        }
    }

    private void migrateLegacyTemplateOwnership() {
        for (String id : new HashSet<>(unlockedRecipes)) {
            if (EquipmentTemplateCatalog.contains(id)) {
                ownedEquipmentTemplateIds.add(id);
                continue;
            }
            if (id.startsWith("primary:")) {
                String[] parts = id.split(":");
                if (parts.length == 3) {
                    try {
                        String weaponId = WeaponRegistry.legacyMarinePrimaryId(parts[1]);
                        if (weaponId != null) {
                            unlockPrimary(weaponId, EquipmentGrade.valueOf(parts[2]));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Unknown retired legacy entries stay harmless in the legacy set.
                    }
                }
            } else if (id.startsWith("armor:")) {
                try {
                    unlockArmor(MarineArmorPattern.valueOf(id.substring("armor:".length())));
                } catch (IllegalArgumentException ignored) {
                    // Unknown retired legacy entries stay harmless in the legacy set.
                }
            } else if (id.startsWith("special:")) {
                String specialId = id.substring("special:".length());
                if (SpecialEquipmentRegistry.get(specialId) != null) {
                    unlockSecondary(specialId);
                }
            }
        }
    }
}
