package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.marine.SquadWeaponIssue;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.spec.CatalogCeilings;
import com.dillon.starsectormarines.ops.spec.StatMeter;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Player-facing authoring state for reusable twelve-billet equipment definitions. */
public final class EquipmentDoctrineDesignerViewModel {

    private static final List<String> ROLES = List.of(
            "Team Leader", "Rifleman", "Assault Leader", "Breacher", "Grenadier",
            "Automatic Rifleman", "Marksman", "Anti-Armor", "Security Rifleman", "Scout");
    private static final List<String> TEAM_NAMES = List.of("Alpha", "Bravo", "Charlie");

    private final MarineRoster roster;
    private final String squadId;
    private final MutableSignal<String> sourceId;
    private final MutableSignal<String> draftName;
    private final MutableSignal<Integer> selectedTeam;
    private final MutableSignal<Integer> revision;
    private final MutableSignal<String> feedback;
    private String weaponSourceId;
    private String weaponDraftName;
    private final List<SquadWeaponIssue> weaponIssues = new ArrayList<>();
    private final ComputedSignal<List<DefinitionTile>> definitions;
    private final ComputedSignal<List<TeamTab>> teamTabs;
    private final ComputedSignal<List<BilletCard>> billets;
    private final ComputedSignal<String> heading;
    private final ComputedSignal<String> subheading;
    private final ComputedSignal<Boolean> renameDisabled;
    private final ComputedSignal<Boolean> deleteDisabled;

    public EquipmentDoctrineDesignerViewModel(
            Reactor reactor, MarineRoster roster, String squadId,
            String weaponDoctrineId) {
        if (reactor == null || roster == null) {
            throw new IllegalArgumentException("Designer requires reactor and roster");
        }
        this.roster = roster;
        this.squadId = squadId;
        sourceId = reactor.signal(null);
        draftName = reactor.signal("");
        selectedTeam = reactor.signal(0);
        revision = reactor.signal(0);
        feedback = reactor.signal(
                "Definitions use collected template cards. Cargo is charged only when issued.");
        definitions = reactor.computed(this::buildDefinitions);
        teamTabs = reactor.computed(this::buildTeamTabs);
        billets = reactor.computed(this::buildBillets);
        heading = reactor.computed(() -> "WEAPON EQUIPMENT DEFINITION");
        subheading = reactor.computed(() ->
                "Collected primary, grade, and special template cards for all twelve billets."
                        + "  Armour is issued as a tactic sheet, not authored a billet at a time.");
        renameDisabled = reactor.computed(() -> !customSource());
        deleteDisabled = reactor.computed(() -> !customSource() || assignedSource());
        loadWeapon(validWeaponId(weaponDoctrineId));
        sourceId.set(weaponSourceId);
        draftName.set(weaponDraftName);
        touch();
    }

    public Signal<List<DefinitionTile>> definitions() { return definitions; }
    public Signal<List<TeamTab>> teamTabs() { return teamTabs; }
    public Signal<List<BilletCard>> billets() { return billets; }
    public Signal<String> heading() { return heading; }
    public Signal<String> subheading() { return subheading; }
    public Signal<String> draftName() { return draftName; }
    public Consumer<String> editName() { return this::editDraftName; }
    public Signal<String> feedback() { return feedback; }
    public Signal<Boolean> renameDisabled() { return renameDisabled; }
    public Signal<Boolean> deleteDisabled() { return deleteDisabled; }
    public Runnable newDraft() { return this::startNewDraft; }
    public Runnable cloneSelected() { return this::cloneDraft; }
    public Runnable saveAsNew() { return this::saveDraft; }
    public Runnable rename() { return this::renameSource; }
    public Runnable delete() { return this::deleteSource; }
    public String squadName() {
        MarineSquad squad = roster.squadById(squadId);
        return squad != null ? squad.name() : "Squad";
    }

    /**
     * The billet as the preview should draw it: the weapon being authored here,
     * worn over whatever the squad's assigned tactic sheet actually issues that
     * billet.
     *
     * <p>The armour is read rather than edited, which is the whole shape of this
     * screen now — a marine still has to be drawn wearing something, and the
     * honest something is the suit they would deploy in.
     */
    public FireTeamBillet viewerBilletAt(int localIndex) {
        int index = selectedTeam.peek() * MarineSquad.TEAM_SIZE + localIndex;
        if (index < 0 || index >= MarineSquad.CAPACITY
                || weaponIssues.size() != MarineSquad.CAPACITY) return null;
        SquadWeaponIssue weapon = weaponIssues.get(index);
        return new FireTeamBillet(weapon.role(), weapon.primaryId(), weapon.grade(),
                weapon.specialEquipmentId(), issuedArmorId(index));
    }

    /** What the squad's assigned sheet puts on this billet, for the preview only. */
    private String issuedArmorId(int index) {
        MarineSquad squad = roster.squadById(squadId);
        String sheetId = squad != null ? squad.armorDoctrineId() : null;
        SquadArmorDoctrine issued = roster.armory().armorDoctrineById(
                sheetId != null ? sheetId : SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
        if (issued == null) {
            issued = roster.armory().armorDoctrineById(
                    SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
        }
        return issued.issueIds().get(index);
    }

    private void loadWeapon(String id) {
        SquadWeaponDoctrine doctrine = roster.armory().weaponDoctrineById(id);
        if (doctrine == null) return;
        sourceId.set(doctrine.id());
        draftName.set(doctrine.displayName());
        weaponSourceId = doctrine.id();
        weaponDraftName = doctrine.displayName();
        weaponIssues.clear();
        weaponIssues.addAll(doctrine.issues());
        touch();
    }

    private String validWeaponId(String preferred) {
        if (roster.armory().weaponDoctrineById(preferred) != null) return preferred;
        return SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS;
    }

    private List<DefinitionTile> buildDefinitions() {
        revision.get();
        List<DefinitionTile> result = new ArrayList<>();
        for (SquadWeaponDoctrine doctrine : roster.armory().weaponDoctrines()) {
            result.add(tile(doctrine.id(), doctrine.displayName()));
        }
        return List.copyOf(result);
    }

    private DefinitionTile tile(String id, String name) {
        String prefix = "designer-definition:" + id;
        return new DefinitionTile(prefix, prefix + ":name", prefix + ":description",
                name, (customId(id) ? "Player-authored" : "Fleet standard") + "  ·  12 billets",
                id.equals(sourceId.get())
                ? "definition-tile selected" : "definition-tile", () -> select(id));
    }

    private void select(String id) {
        loadWeapon(id);
        feedback.set("Loaded " + draftName.peek() + ". Changes remain a draft until saved as new.");
    }

    private List<TeamTab> buildTeamTabs() {
        int active = selectedTeam.get();
        List<TeamTab> result = new ArrayList<>();
        for (int index = 0; index < TEAM_NAMES.size(); index++) {
            int team = index;
            result.add(new TeamTab("designer-team:" + index, TEAM_NAMES.get(index),
                    index == active ? "team-tab selected" : "team-tab",
                    () -> { selectedTeam.set(team); feedback.set(TEAM_NAMES.get(team) + " team billets shown."); }));
        }
        return List.copyOf(result);
    }

    private List<BilletCard> buildBillets() {
        revision.get();
        int team = selectedTeam.get();
        List<BilletCard> result = new ArrayList<>();
        for (int local = 0; local < MarineSquad.TEAM_SIZE; local++) {
            int billet = team * MarineSquad.TEAM_SIZE + local;
            String prefix = "designer-billet:" + local;
            SquadWeaponIssue issue = weaponIssues.get(billet);
            result.add(new BilletCard(prefix, prefix + ":top",
                    "designer-marine-preview:" + local,
                    prefix + ":title", prefix + ":flavor", prefix + ":stats",
                    prefix + ":role",
                    prefix + ":primary", prefix + ":grade", prefix + ":special",
                    TEAM_NAMES.get(team) + "  /  " + (local + 1),
                    weaponFlavor(issue), weaponStats(prefix, issue),
                    "designer-stat-fill weapon-stat-fill",
                    "Role  ·  " + issue.role(),
                    "Primary  ·  " + issue.primaryDef().catalogName(issue.grade().tier),
                    "Grade  ·  " + title(issue.grade().name()),
                    "Special  ·  " + specialName(issue.specialDef()),
                    () -> cycleRole(billet), () -> cycleWeapon(billet),
                    () -> cycleGrade(billet), () -> cycleSpecial(billet)));
        }
        return List.copyOf(result);
    }

    private void cycleRole(int index) {
        SquadWeaponIssue issue = weaponIssues.get(index);
        String role = next(ROLES, issue.role());
        weaponIssues.set(index, new SquadWeaponIssue(
                role, issue.primaryId(), issue.grade(), issue.specialEquipmentId()));
        changed("Role changed to " + role + ".");
    }

    private void cycleWeapon(int index) {
        SquadWeaponIssue issue = weaponIssues.get(index);
        List<EquipmentTemplateCard> cards = ownedCards(EquipmentTemplateCard.Kind.PRIMARY);
        String weaponId = issue.primaryId();
        EquipmentGrade grade = issue.grade();
        int start = indexOfEquipment(cards, weaponId, grade);
        for (int offset = 1; offset <= cards.size(); offset++) {
            EquipmentTemplateCard candidate = cards.get((Math.max(0, start) + offset) % cards.size());
            if (!candidate.equipmentId().equals(weaponId)) {
                weaponId = candidate.equipmentId();
                grade = candidate.grade();
                break;
            }
        }
        weaponIssues.set(index, new SquadWeaponIssue(
                issue.role(), weaponId, grade, issue.specialEquipmentId()));
        changed("Primary changed to "
                + WeaponRegistry.require(weaponId).catalogName(grade.tier) + ".");
    }

    private void cycleGrade(int index) {
        SquadWeaponIssue issue = weaponIssues.get(index);
        EquipmentGrade[] values = EquipmentGrade.values();
        EquipmentGrade grade = issue.grade();
        for (int offset = 1; offset <= values.length; offset++) {
            EquipmentGrade candidate = values[(issue.grade().ordinal() + offset) % values.length];
            if (roster.armory().ownsPrimaryTemplate(issue.primaryId(), candidate)) {
                grade = candidate;
                break;
            }
        }
        weaponIssues.set(index, new SquadWeaponIssue(
                issue.role(), issue.primaryId(), grade, issue.specialEquipmentId()));
        changed("Equipment grade changed to " + title(grade.name()) + ".");
    }

    private void cycleSpecial(int index) {
        SquadWeaponIssue issue = weaponIssues.get(index);
        String current = issue.specialEquipmentId();
        List<SpecialEquipmentDef> owned = new ArrayList<>();
        SpecialEquipmentRegistry registry = SpecialEquipmentRegistry.installed();
        if (registry != null) {
            for (SpecialEquipmentDef special : registry.all()) {
                if (roster.armory().ownsSpecialTemplate(special.id())) owned.add(special);
            }
        }
        int currentIndex = -1;
        for (int i = 0; i < owned.size(); i++) {
            if (owned.get(i).id().equals(current)) currentIndex = i;
        }
        SpecialEquipmentDef next = currentIndex < 0
                ? owned.isEmpty() ? null : owned.get(0)
                : currentIndex == owned.size() - 1 ? null : owned.get(currentIndex + 1);
        weaponIssues.set(index, new SquadWeaponIssue(
                issue.role(), issue.primaryId(), issue.grade(),
                next != null ? next.id() : null));
        changed("Special equipment changed to " + specialName(next) + ".");
    }

    private void startNewDraft() {
        sourceId.set(null);
        draftName.set("New Weapon Equipment");
        rememberCurrent();
        feedback.set("New unsaved definition. Edit any billet, then Save as New.");
        touch();
    }

    private void cloneDraft() {
        sourceId.set(null);
        draftName.set(draftName.peek() + " Copy");
        rememberCurrent();
        feedback.set("Cloned into an unsaved definition. The source remains unchanged.");
        touch();
    }

    private void saveDraft() {
        if (draftName.peek() == null || draftName.peek().isBlank()) {
            feedback.set("A definition name is required before saving.");
            return;
        }
        if (!roster.armory().canAuthorWeaponDoctrine(weaponIssues)) {
            feedback.set("Collect every referenced weapon and special template card before saving.");
            return;
        }
        SquadWeaponDoctrine saved = roster.armory().createWeaponDoctrine(
                draftName.peek(), List.copyOf(weaponIssues));
        sourceId.set(saved.id());
        rememberCurrent();
        feedback.set("Saved " + draftName.peek() + ". It is now available for squad issue.");
        touch();
    }

    private void renameSource() {
        boolean renamed = roster.armory().renameWeaponDoctrine(
                sourceId.peek(), draftName.peek());
        feedback.set(renamed ? "Definition renamed." : "Only player-authored definitions can be renamed.");
        touch();
    }

    private void deleteSource() {
        if (!roster.deleteWeaponDoctrine(sourceId.peek())) {
            feedback.set("Assigned and built-in definitions cannot be deleted.");
            return;
        }
        feedback.set("Definition deleted. Existing issued equipment was not changed.");
        loadWeapon(validWeaponId(null));
    }

    private boolean customSource() {
        String id = sourceId.get();
        return id != null && SquadEquipmentDoctrines.weaponById(id) == null;
    }

    private boolean customId(String id) {
        return SquadEquipmentDoctrines.weaponById(id) == null;
    }

    private boolean assignedSource() {
        String id = sourceId.get();
        return roster.isWeaponDoctrineAssigned(id);
    }

    private void changed(String message) {
        feedback.set(message + " Save as New to keep this draft.");
        touch();
    }

    private void touch() { revision.update(value -> value + 1); }

    private void editDraftName(String value) {
        draftName.set(value);
        rememberCurrent();
    }

    private void rememberCurrent() {
        weaponSourceId = sourceId.peek();
        weaponDraftName = draftName.peek();
    }

    private static String specialName(SpecialEquipmentDef special) {
        return special != null ? special.displayName() : "None";
    }

    private static String weaponFlavor(SquadWeaponIssue issue) {
        if (issue.specialDef() != null) {
            return "Specialist billet · " + issue.specialDef().displayName() + ".";
        }
        if (issue.role().contains("Leader")) {
            return "Fire-team lead · priority command issue.";
        }
        String role = issue.primaryDef().catalogRole;
        return role != null && !role.isBlank()
                ? title(role) + " billet · " + issue.primaryDef().displayName + "."
                : "Line billet · reliable general-purpose issue.";
    }

    private static List<StatMeter> weaponStats(String cardId, SquadWeaponIssue issue) {
        SoldierProfile profile = SoldierProfile.REGULAR;
        float damage = InfantryCombatStats.damage(issue.primaryDef(), issue.grade());
        float range = InfantryCombatStats.range(issue.primaryDef(), issue.grade());
        float accuracy = InfantryCombatStats.accuracy(issue.primaryDef(), issue.grade(), profile);
        float dps = InfantryCombatStats.estimatedDps(issue.primaryDef(), issue.grade(), profile);
        return List.of(
                StatMeter.of(cardId + ":damage", "DMG", formatOneDecimal(damage),
                        damage, CatalogCeilings.weaponDamage()),
                StatMeter.of(cardId + ":range", "RNG", formatOneDecimal(range),
                        range, CatalogCeilings.weaponRange()),
                StatMeter.of(cardId + ":accuracy", "ACC",
                        String.format(Locale.ROOT, "%.0f%%", accuracy * 100f), accuracy, 1f),
                StatMeter.of(cardId + ":dps", "DPS", formatOneDecimal(dps),
                        dps, CatalogCeilings.weaponDps(profile)));
    }

    private static String formatOneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String title(String value) {
        String lower = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private List<EquipmentTemplateCard> ownedCards(EquipmentTemplateCard.Kind wanted) {
        return roster.armory().equipmentTemplateCards().stream()
                .filter(card -> card.kind() == wanted)
                .toList();
    }

    private static int indexOfEquipment(
            List<EquipmentTemplateCard> cards, String equipmentId, EquipmentGrade grade) {
        for (int index = 0; index < cards.size(); index++) {
            EquipmentTemplateCard card = cards.get(index);
            if (card.equipmentId().equals(equipmentId)
                    && (grade == null || card.grade() == grade)) return index;
        }
        return -1;
    }
    private static <T> T next(List<T> values, T current) {
        int index = values.indexOf(current);
        return values.get((index + 1 + values.size()) % values.size());
    }


    public record DefinitionTile(String id, String nameId, String descriptionId,
                                 String name, String description, String classes,
                                 Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "nameId" -> nameId; case "descriptionId" -> descriptionId;
                case "name" -> name; case "description" -> description; case "classes" -> classes;
                case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown definition tile property");
            };
        }
    }

    public record TeamTab(String id, String name, String classes, Runnable select)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "name" -> name; case "classes" -> classes; case "select" -> select;
                default -> throw new IllegalArgumentException("Unknown team tab property");
            };
        }
    }

    public record BilletCard(String id, String topId, String canvasId, String titleId,
                             String flavorId, String statsId,
                             String roleId, String primaryId,
                             String gradeId, String specialId, String title,
                             String flavor, List<StatMeter> stats, String statFillClasses, String role,
                             String primary, String grade, String special, Runnable cycleRole,
                             Runnable cyclePrimary, Runnable cycleGrade, Runnable cycleSpecial)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "topId" -> topId; case "canvasId" -> canvasId;
                case "titleId" -> titleId; case "flavorId" -> flavorId;
                case "statsId" -> statsId; case "roleId" -> roleId;
                case "primaryId" -> primaryId; case "gradeId" -> gradeId; case "specialId" -> specialId;
                case "title" -> title; case "flavor" -> flavor; case "stats" -> stats;
                case "statFillClasses" -> statFillClasses;
                case "role" -> role; case "primary" -> primary;
                case "grade" -> grade; case "special" -> special; case "cycleRole" -> cycleRole;
                case "cyclePrimary" -> cyclePrimary; case "cycleGrade" -> cycleGrade;
                case "cycleSpecial" -> cycleSpecial;
                default -> throw new IllegalArgumentException("Unknown billet card property");
            };
        }
    }

}
