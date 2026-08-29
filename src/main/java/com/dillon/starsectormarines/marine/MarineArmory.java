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

    private int victories;
    private int highRiskVictories;
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

    public MarineArmory() {
        seedStarterIssue();
        seedStarterCards();
    }

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
    /**
     * Every authored plan, issued from what this armoury owns.
     *
     * <p>A plan names roles rather than patterns, so this is where "the standard
     * section" becomes twelve concrete suits — the best owned for each billet's
     * job. Two companies running the same plan therefore field the same section
     * in different kit, which is the whole point of separating the two
     * ({@code role-and-access.md}).
     *
     * <p><b>There is no hand-authored armour catalog, deliberately.</b> A player
     * once built armour definitions a billet at a time, twelve concrete patterns
     * frozen at the moment of saving; that predates the plan model and directly
     * contradicts it. Such a definition names the militia vest twelve times and
     * goes on naming it long after the company can afford siege plate, because
     * nothing in it says what the billets are <em>for</em>. Weapons are still
     * authored per billet — a fire-team lead's carbine is a real choice with no
     * role model above it — and armour is not.
     */
    public List<SquadArmorDoctrine> armorDoctrines() {
        List<SquadArmorDoctrine> result = new ArrayList<>();
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            result.add(issue(plan));
        }
        return Collections.unmodifiableList(result);
    }

    /** Issues one plan from this armoury's own stock. */
    public SquadArmorDoctrine issue(SquadArmorPlan plan) {
        return ArmorIssueResolver.resolve(plan, pattern -> ownsArmorTemplate(pattern.id()));
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
        SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(id);
        return plan != null ? issue(plan) : null;
    }

    public SquadWeaponDoctrine createWeaponDoctrine(
            String displayName, List<SquadWeaponIssue> issues) {
        return createWeaponDoctrine("custom:weapons:" + UUID.randomUUID(),
                displayName, issues, true);
    }

    public SquadWeaponDoctrine cloneWeaponDoctrine(String sourceId) {
        SquadWeaponDoctrine source = weaponDoctrineById(sourceId);
        return source != null
                ? createWeaponDoctrine(source.displayName() + " Copy", source.issues()) : null;
    }

    public boolean renameWeaponDoctrine(String id, String displayName) {
        int index = customWeaponDoctrineIndex(id);
        if (index < 0 || displayName == null || displayName.isBlank()) return false;
        SquadWeaponDoctrine existing = customWeaponDoctrines.get(index);
        customWeaponDoctrines.set(index, new SquadWeaponDoctrine(
                existing.id(), displayName, existing.description(), existing.issues()));
        return true;
    }

    boolean deleteWeaponDoctrine(String id) {
        int index = customWeaponDoctrineIndex(id);
        if (index < 0) return false;
        customWeaponDoctrines.remove(index);
        return true;
    }

    SquadWeaponDoctrine ensureWeaponDoctrine(
            String id, String displayName, List<SquadWeaponIssue> issues) {
        SquadWeaponDoctrine existing = weaponDoctrineById(id);
        return existing != null ? existing
                : createWeaponDoctrine(id, displayName, issues, false);
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
        unlockArmor("armor.cordon-shell");
        unlockArmor("armor.lashplate-harness");
        unlockArmor(MarineArmorPattern.MILITIA);
        unlockArmor(MarineArmorPattern.CHARCOAL);
        unlockArmor(MarineArmorPattern.ARMY_GREEN);
        // A section has somebody who goes through the door first and somebody
        // who carries the heavy thing. Both are basic rather than specialist, so
        // the cheapest surplus example of each is starter issue: without them
        // every tactic sheet reads as twelve line suits and the whole role model
        // is invisible at game start ({@code role-and-access.md}).
        //
        // Recon is deliberately NOT seeded. A dedicated scout suit is the first
        // specialist purchase worth making, and leaving that one gap is what
        // keeps the picker's "no kit" marker meaningful instead of universal.
        unlockArmor("armor.riot-shell");
        unlockArmor("armor.packframe");
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

    private int customWeaponDoctrineIndex(String id) {
        if (id == null) return -1;
        for (int index = 0; index < customWeaponDoctrines.size(); index++) {
            SquadWeaponDoctrine doctrine = customWeaponDoctrines.get(index);
            if (doctrine != null && id.equals(doctrine.id())) return index;
        }
        return -1;
    }

    public static int secondaryFabricationCost(SpecialEquipmentDef special) { return 5; }

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
        if (unlockedRecipes == null) unlockedRecipes = new HashSet<>();
        if (ownedEquipmentTemplateIds == null) ownedEquipmentTemplateIds = new HashSet<>();
        if (templateCards == null) templateCards = new ArrayList<>();
        if (squadArrangements == null) squadArrangements = new ArrayList<>();
        if (customWeaponDoctrines == null) customWeaponDoctrines = new ArrayList<>();
        customWeaponDoctrines.removeIf(doctrine -> doctrine == null
                || SquadEquipmentDoctrines.weaponById(doctrine.id()) != null);
        migrateLegacyPrimaryKeys();
        migrateLegacySecondaryKeys();
        migrateLegacyTemplateOwnership();
        if (ownedEquipmentTemplateIds.isEmpty()) seedStarterIssue();
        seedStarterCards();
        // Existing saves predate the recruit-grade field rifle recipe.
        unlockPrimary(WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SERVICE);
        unlockPrimary(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.SERVICE);
        unlockArmor(MarineArmorPattern.MILITIA);
        unlockSecondary(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID);
        unlockSecondary(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        unlockSecondary(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID);
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
