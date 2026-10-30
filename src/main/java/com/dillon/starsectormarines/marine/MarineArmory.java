package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The company's collection of loadout cards, the equipment beneath them, and the
 * squad definitions they name.
 *
 * <p><b>A card is the unit of access.</b> {@link #knownLoadoutCardIds} is the
 * authority for which weapon cards and tactic sheets a squad may be given; the
 * equipment templates a card names are recorded beside it as the permanent
 * capability beneath, never as a separate way in. Owning every suit a sheet names
 * does not teach the sheet.
 */
public final class MarineArmory implements Serializable {

    /** Saved squad profiles from before squads held their cards directly; emptied on load. */
    private List<SquadProfile> squadProfiles = new ArrayList<>();

    private int victories;
    private int highRiskVictories;
    private Set<String> unlockedRecipes = new HashSet<>();
    /** Permanent collectible equipment templates used by all live issue paths. */
    private Set<String> ownedEquipmentTemplateIds = new HashSet<>();
    /** Sheet knowledge from before the card ledger; read once by load migration. */
    private Set<String> knownArmorPlanIds = new HashSet<>();
    /** Every loadout card this company holds, by {@link LoadoutCard#id()}; null in a save from before it. */
    private Set<String> knownLoadoutCardIds = new LinkedHashSet<>();
    /**
     * Save input only: the retired victory milestones a save had paid. Cards come from the Kit Ladder's store and
     * Relief's free pick now ({@link KitLadder}); what a milestone taught stays learned.
     */
    private Set<String> paidVictoryMilestoneIds = new LinkedHashSet<>();
    /** Reusable fire-team designs; the legacy field name is retained for save compatibility. */
    private List<FireTeamTemplateCard> templateCards = new ArrayList<>();
    /** Saved three-template compositions; applying one is still an inventory transaction. */
    private List<SquadArrangement> squadArrangements = new ArrayList<>();
    /** Player-authored squad weapon definitions. Authoring never consumes stock. */
    private List<SquadWeaponDoctrine> customWeaponDoctrines = new ArrayList<>();

    public MarineArmory() {
        seedStarterIssue();
        seedStarterCards();
    }

    /** A profile from a save written before squads held their cards directly; load migration only. */
    SquadProfile legacySquadProfile(String id) {
        if (id == null || squadProfiles == null) return null;
        for (SquadProfile profile : squadProfiles) {
            if (profile != null && id.equals(profile.id())) return profile;
        }
        return null;
    }

    void dropLegacySquadProfiles() {
        if (squadProfiles != null) squadProfiles.clear();
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

    public Set<String> knownLoadoutCardIds() {
        return Collections.unmodifiableSet(knownLoadoutCardIds);
    }

    public boolean knowsLoadoutCard(String cardId) {
        return cardId != null && knownLoadoutCardIds.contains(cardId);
    }

    /**
     * Adds one whole card to the collection, together with the equipment it names.
     * Returns whether the card was new.
     */
    public boolean learnLoadoutCard(LoadoutCard card) {
        if (card == null || !card.defined()) {
            throw new IllegalArgumentException("Unknown loadout card " + card);
        }
        for (String templateId : card.templateIds()) ownedEquipmentTemplateIds.add(templateId);
        return knownLoadoutCardIds.add(card.id());
    }

    /**
     * Whether a squad may be given this weapon card. A built-in or Wayline card is
     * known only once collected; a card registered outside them under a fixed id (a
     * debug operation's, or one an old save's designer made) is known by being here.
     */
    public boolean knowsWeaponCard(String doctrineId) {
        if (doctrineId == null) return false;
        LoadoutCard card = new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrineId);
        if (card.defined()) return knowsLoadoutCard(card.id());
        return weaponDoctrineById(doctrineId) != null;
    }

    /** Whether this tactic sheet's card is in the collection. */
    public boolean knowsArmorPlan(String id) {
        return id != null && knowsLoadoutCard(new LoadoutCard(LoadoutCard.Kind.SHEET, id).id());
    }

    /** Learns one tactic sheet's card. */
    public boolean acquireArmorPlan(String id) {
        if (SquadEquipmentDoctrines.armorPlanById(id) == null) {
            throw new IllegalArgumentException("Unknown armor plan id '" + id + "'");
        }
        return learnLoadoutCard(new LoadoutCard(LoadoutCard.Kind.SHEET, id));
    }

    /**
     * A save from before the card ledger knew a card by owning everything it names
     * (and a sheet by its own ledger); carry exactly that forward, once.
     */
    private void migrateCardKnowledge() {
        List<LoadoutCard> candidates = new ArrayList<>();
        for (SquadWeaponDoctrine doctrine : SquadEquipmentDoctrines.weaponDoctrines()) {
            candidates.add(new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrine.id()));
        }
        for (FirstRefit.Path path : FirstRefit.Path.values()) {
            if (weaponDoctrineById(path.weapons) != null) {
                candidates.add(new LoadoutCard(LoadoutCard.Kind.WEAPON, path.weapons));
            }
        }
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            LoadoutCard sheet = new LoadoutCard(LoadoutCard.Kind.SHEET, plan.id());
            if (knownArmorPlanIds.contains(plan.id())) knownLoadoutCardIds.add(sheet.id());
            else candidates.add(sheet);
        }
        for (LoadoutCard card : candidates) {
            List<String> templates = card.templateIds();
            if (!templates.isEmpty() && ownedEquipmentTemplateIds.containsAll(templates)) {
                knownLoadoutCardIds.add(card.id());
            }
        }
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
     * Every learned tactic sheet that this armory can physically issue.
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
            if (canIssue(plan)) result.add(issue(plan));
        }
        return Collections.unmodifiableList(result);
    }

    /** Whether the sheet is learned and stock can fill it without crossing protection classes. */
    public boolean canIssue(SquadArmorPlan plan) {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        if (plan == null || catalog == null || !knowsArmorPlan(plan.id())) return false;
        try {
            issue(plan);
            return true;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    /** Resolves the sheet's own twelve; the squad fitting checks the company owns every pattern. */
    public SquadArmorDoctrine issue(SquadArmorPlan plan) {
        return ArmorIssueResolver.issueSheet(plan);
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
        return canIssue(plan) ? issue(plan) : null;
    }

    /**
     * Registers a developer-authored weapon card outside the built-in list — a story
     * path's or a debug operation's — under its fixed id, or returns the one already
     * registered. The player never authors a card: this is not a designer.
     */
    public SquadWeaponDoctrine ensureWeaponDoctrine(
            String id, String displayName, List<SquadWeaponIssue> issues) {
        SquadWeaponDoctrine existing = weaponDoctrineById(id);
        SquadWeaponDoctrine result = existing != null ? existing
                : createWeaponDoctrine(id, displayName, issues);
        return result;
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

    /** Counts a won operation and pays any victory milestone it reaches. */
    public void recordVictory(boolean highRisk) {
        recordVictory(highRisk, Set.of());
    }

    /** Cards held in cargo count toward the safety net's floor without being learned. */
    public void recordVictory(boolean highRisk, Set<String> carriedCardIds) {
        victories++;
        if (highRisk) highRiskVictories++;
        repairCollectionProgression(carriedCardIds);
    }

    /** Idempotent save-load and mission-resolution bridge for the collection safety net. */
    public void repairCollectionProgression(Set<String> carriedCardIds) {
        EquipmentCollectionCurve.repair(this, victories, carriedCardIds);
    }

    /**
     * The baseline a new company holds, and a save is repaired to: the two cards every squad is founded on, every
     * other card the Kit Ladder places on {@link UnlockStation#START}, and the equipment those cards name. The
     * baseline is whatever the start cards carry, so editing them on the Kit Ladder page changes it.
     */
    private void seedStarterIssue() {
        unlockArmor(MarineArmorPattern.ARMORLESS);
        learnLoadoutCard(new LoadoutCard(LoadoutCard.Kind.WEAPON, SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS));
        learnLoadoutCard(new LoadoutCard(LoadoutCard.Kind.SHEET, SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR));
        for (LoadoutCard card : startCards()) learnLoadoutCard(card);
    }

    /** Every defined loadout card the Kit Ladder places on {@link UnlockStation#START}, in authored order. */
    public static List<LoadoutCard> startCards() {
        List<LoadoutCard> cards = new ArrayList<>();
        for (Map.Entry<String, UnlockStation> placement : KitLadder.stations().entrySet()) {
            if (!UnlockStation.START.equals(placement.getValue())) continue;
            LoadoutCard card = LoadoutCard.parse(placement.getKey());
            if (card != null) cards.add(card);
        }
        return cards;
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

    /**
     * A save holds its own copy of each Wayline path's weapon card, written when the path was offered. The card's
     * seats are authored data, so a saved copy is re-dressed from the current card on load, keeping its saved name
     * and description; squads holding it are re-dressed from it by the roster's own refresh.
     */
    private void refreshWaylineCards() {
        for (int i = 0; i < customWeaponDoctrines.size(); i++) {
            SquadWeaponDoctrine saved = customWeaponDoctrines.get(i);
            for (FirstRefit.Path path : FirstRefit.Path.values()) {
                if (path.weapons.equals(saved.id())) {
                    customWeaponDoctrines.set(i, new SquadWeaponDoctrine(saved.id(), saved.savedDisplayName(),
                            saved.savedDescription(), FirstRefit.weapons(path)));
                }
            }
        }
    }

    private SquadWeaponDoctrine createWeaponDoctrine(
            String id, String displayName, List<SquadWeaponIssue> issues) {
        if (weaponDoctrineById(id) != null) {
            throw new IllegalArgumentException("Weapon doctrine id already exists: " + id);
        }
        SquadWeaponDoctrine doctrine = new SquadWeaponDoctrine(
                id, displayName, SquadWeaponDoctrine.CUSTOM_DESCRIPTION, issues);
        customWeaponDoctrines.add(doctrine);
        return doctrine;
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
        if (knownArmorPlanIds == null) knownArmorPlanIds = new HashSet<>();
        boolean cardLedgerMissing = knownLoadoutCardIds == null;
        if (cardLedgerMissing) knownLoadoutCardIds = new LinkedHashSet<>();
        if (paidVictoryMilestoneIds == null) paidVictoryMilestoneIds = new LinkedHashSet<>();
        if (squadProfiles == null) squadProfiles = new ArrayList<>();
        if (unlockedRecipes == null) unlockedRecipes = new HashSet<>();
        if (ownedEquipmentTemplateIds == null) ownedEquipmentTemplateIds = new HashSet<>();
        if (templateCards == null) templateCards = new ArrayList<>();
        if (squadArrangements == null) squadArrangements = new ArrayList<>();
        if (customWeaponDoctrines == null) customWeaponDoctrines = new ArrayList<>();
        customWeaponDoctrines.removeIf(doctrine -> doctrine == null
                || SquadEquipmentDoctrines.weaponById(doctrine.id()) != null);
        refreshWaylineCards();
        migrateLegacyPrimaryKeys();
        migrateLegacySecondaryKeys();
        migrateLegacyTemplateOwnership();
        if (ownedEquipmentTemplateIds.isEmpty()) seedStarterIssue();
        seedStarterCards();
        // Keep the baseline available without granting specialist technology on reload.
        // Previously learned templates stay in the saved ownership set.
        seedStarterIssue();
        victories = Math.max(0, victories);
        highRiskVictories = Math.max(0, highRiskVictories);
        if (cardLedgerMissing) migrateCardKnowledge();
        // Profiles are saved intent owned by this armory, including legacy
        // profiles written before sheet knowledge had its own ledger.
        for (SquadProfile profile : squadProfiles) {
            if (profile != null && SquadEquipmentDoctrines.armorPlanById(profile.armorDoctrineId()) != null) {
                acquireArmorPlan(profile.armorDoctrineId());
            }
        }
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
