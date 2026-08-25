package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.marine.SquadWeaponIssue;
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
    private static final List<MarineWeapon> WEAPONS = List.of(
            MarineWeapon.FIELD_RIFLE, MarineWeapon.PULSE_RIFLE, MarineWeapon.SMG,
            MarineWeapon.SQUAD_AUTOMATIC, MarineWeapon.DMR);
    private static final List<String> TEAM_NAMES = List.of("Alpha", "Bravo", "Charlie");

    private final MarineRoster roster;
    private final String squadId;
    private final MutableSignal<Kind> kind;
    private final MutableSignal<String> sourceId;
    private final MutableSignal<String> draftName;
    private final MutableSignal<Integer> selectedTeam;
    private final MutableSignal<Integer> revision;
    private final MutableSignal<String> feedback;
    private String weaponSourceId;
    private String armorSourceId;
    private String weaponDraftName;
    private String armorDraftName;
    private final List<SquadWeaponIssue> weaponIssues = new ArrayList<>();
    private final List<MarineArmorPattern> armorIssues = new ArrayList<>();
    private final ComputedSignal<List<DefinitionTile>> definitions;
    private final ComputedSignal<List<TeamTab>> teamTabs;
    private final ComputedSignal<List<BilletCard>> billets;
    private final ComputedSignal<String> heading;
    private final ComputedSignal<String> subheading;
    private final ComputedSignal<Boolean> renameDisabled;
    private final ComputedSignal<Boolean> deleteDisabled;

    public EquipmentDoctrineDesignerViewModel(
            Reactor reactor, MarineRoster roster, String squadId,
            String weaponDoctrineId, String armorDoctrineId) {
        if (reactor == null || roster == null) {
            throw new IllegalArgumentException("Designer requires reactor and roster");
        }
        this.roster = roster;
        this.squadId = squadId;
        kind = reactor.signal(Kind.WEAPON);
        sourceId = reactor.signal(null);
        draftName = reactor.signal("");
        selectedTeam = reactor.signal(0);
        revision = reactor.signal(0);
        feedback = reactor.signal("Author freely. Inventory is checked only when a definition is issued to a squad.");
        definitions = reactor.computed(this::buildDefinitions);
        teamTabs = reactor.computed(this::buildTeamTabs);
        billets = reactor.computed(this::buildBillets);
        heading = reactor.computed(() -> kind.get() == Kind.WEAPON
                ? "WEAPON EQUIPMENT DEFINITION" : "ARMOR EQUIPMENT DEFINITION");
        subheading = reactor.computed(() -> kind.get() == Kind.WEAPON
                ? "Primary weapons, grade, roles, and special equipment for all twelve billets."
                : "Individual armor issue for all twelve billets; a squad need not wear one uniform pattern.");
        renameDisabled = reactor.computed(() -> !customSource());
        deleteDisabled = reactor.computed(() -> !customSource() || assignedSource());
        loadWeapon(validWeaponId(weaponDoctrineId));
        loadArmor(validArmorId(armorDoctrineId), false);
        kind.set(Kind.WEAPON);
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
    public Runnable showWeapons() { return () -> switchKind(Kind.WEAPON); }
    public Runnable showArmor() { return () -> switchKind(Kind.ARMOR); }
    public Runnable newDraft() { return this::startNewDraft; }
    public Runnable cloneSelected() { return this::cloneDraft; }
    public Runnable saveAsNew() { return this::saveDraft; }
    public Runnable rename() { return this::renameSource; }
    public Runnable delete() { return this::deleteSource; }
    public String squadName() {
        MarineSquad squad = roster.squadById(squadId);
        return squad != null ? squad.name() : "Squad";
    }

    public FireTeamBillet viewerBilletAt(int localIndex) {
        int index = selectedTeam.peek() * MarineSquad.TEAM_SIZE + localIndex;
        if (index < 0 || index >= MarineSquad.CAPACITY
                || weaponIssues.size() != MarineSquad.CAPACITY
                || armorIssues.size() != MarineSquad.CAPACITY) return null;
        SquadWeaponIssue weapon = weaponIssues.get(index);
        return new FireTeamBillet(weapon.role(), weapon.primary(), weapon.grade(),
                weapon.special(), armorIssues.get(index));
    }

    private void switchKind(Kind next) {
        if (kind.peek() == next) return;
        rememberCurrent();
        kind.set(next);
        sourceId.set(next == Kind.WEAPON ? weaponSourceId : armorSourceId);
        draftName.set(next == Kind.WEAPON ? weaponDraftName : armorDraftName);
        feedback.set((next == Kind.WEAPON ? "Weapon" : "Armor")
                + " definition resumed; the other draft remains intact.");
        touch();
    }

    private void loadWeapon(String id) {
        SquadWeaponDoctrine doctrine = roster.armory().weaponDoctrineById(id);
        if (doctrine == null) return;
        kind.set(Kind.WEAPON);
        sourceId.set(doctrine.id());
        draftName.set(doctrine.displayName());
        weaponSourceId = doctrine.id();
        weaponDraftName = doctrine.displayName();
        weaponIssues.clear();
        weaponIssues.addAll(doctrine.issues());
        touch();
    }

    private void loadArmor(String id, boolean changeKind) {
        SquadArmorDoctrine doctrine = roster.armory().armorDoctrineById(id);
        if (doctrine == null) return;
        if (changeKind) kind.set(Kind.ARMOR);
        sourceId.set(doctrine.id());
        draftName.set(doctrine.displayName());
        armorSourceId = doctrine.id();
        armorDraftName = doctrine.displayName();
        armorIssues.clear();
        armorIssues.addAll(doctrine.issues());
        touch();
    }

    private String validWeaponId(String preferred) {
        if (roster.armory().weaponDoctrineById(preferred) != null) return preferred;
        return SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS;
    }

    private String validArmorId(String preferred) {
        if (roster.armory().armorDoctrineById(preferred) != null) return preferred;
        return SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR;
    }

    private List<DefinitionTile> buildDefinitions() {
        revision.get();
        List<DefinitionTile> result = new ArrayList<>();
        if (kind.get() == Kind.WEAPON) {
            for (SquadWeaponDoctrine doctrine : roster.armory().weaponDoctrines()) {
                result.add(tile(doctrine.id(), doctrine.displayName()));
            }
        } else {
            for (SquadArmorDoctrine doctrine : roster.armory().armorDoctrines()) {
                result.add(tile(doctrine.id(), doctrine.displayName()));
            }
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
        if (kind.peek() == Kind.WEAPON) loadWeapon(id);
        else loadArmor(id, true);
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
            if (kind.get() == Kind.WEAPON) {
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
                        "Primary  ·  " + issue.primary().catalogName(issue.grade()),
                        "Grade  ·  " + title(issue.grade().name()),
                        "Special  ·  " + specialName(issue.special()),
                        () -> cycleRole(billet), () -> cycleWeapon(billet),
                        () -> cycleGrade(billet), () -> cycleSpecial(billet)));
            } else {
                MarineArmorPattern armor = armorIssues.get(billet);
                result.add(new BilletCard(prefix, prefix + ":top",
                        "designer-marine-preview:" + local,
                        prefix + ":title", prefix + ":flavor", prefix + ":stats",
                        prefix + ":role",
                        prefix + ":primary", prefix + ":grade", prefix + ":special",
                        TEAM_NAMES.get(team) + "  /  " + (local + 1),
                        armorFlavor(armor), armorStats(prefix, armor),
                        "designer-stat-fill armor-stat-fill",
                        "Armor  ·  " + armor.displayName,
                        "Protection  ·  " + Math.round(armor.armorPool) + " / "
                                + Math.round(armor.armorRating),
                        "Evasion  ·  " + signedPercent(1f - armor.incomingAccuracyMult),
                        "Move  ·  " + signedPercent(armor.moveSpeedMult - 1f),
                        () -> cycleArmor(billet), () -> cycleArmor(billet),
                        () -> cycleArmor(billet), () -> cycleArmor(billet)));
            }
        }
        return List.copyOf(result);
    }

    private void cycleRole(int index) {
        SquadWeaponIssue issue = weaponIssues.get(index);
        String role = next(ROLES, issue.role());
        weaponIssues.set(index, new SquadWeaponIssue(
                role, issue.primary(), issue.grade(), issue.specialEquipmentId()));
        changed("Role changed to " + role + ".");
    }

    private void cycleWeapon(int index) {
        SquadWeaponIssue issue = weaponIssues.get(index);
        MarineWeapon weapon = next(WEAPONS, issue.primary());
        weaponIssues.set(index, new SquadWeaponIssue(
                issue.role(), weapon, issue.grade(), issue.specialEquipmentId()));
        changed("Primary changed to " + weapon.displayName() + ".");
    }

    private void cycleGrade(int index) {
        SquadWeaponIssue issue = weaponIssues.get(index);
        EquipmentGrade[] values = EquipmentGrade.values();
        EquipmentGrade grade = values[(issue.grade().ordinal() + 1) % values.length];
        weaponIssues.set(index, new SquadWeaponIssue(
                issue.role(), issue.primary(), grade, issue.specialEquipmentId()));
        changed("Equipment grade changed to " + title(grade.name()) + ".");
    }

    private void cycleSpecial(int index) {
        SquadWeaponIssue issue = weaponIssues.get(index);
        MarineSecondary current = issue.special();
        MarineSecondary[] values = MarineSecondary.values();
        MarineSecondary next = current == null ? values[0]
                : current.ordinal() == values.length - 1 ? null : values[current.ordinal() + 1];
        weaponIssues.set(index, new SquadWeaponIssue(
                issue.role(), issue.primary(), issue.grade(), next));
        changed("Special equipment changed to " + specialName(next) + ".");
    }

    private void cycleArmor(int index) {
        MarineArmorPattern current = armorIssues.get(index);
        MarineArmorPattern[] values = MarineArmorPattern.values();
        MarineArmorPattern next = values[(current.ordinal() + 1) % values.length];
        armorIssues.set(index, next);
        changed("Armor changed to " + next.displayName + ".");
    }

    private void startNewDraft() {
        sourceId.set(null);
        draftName.set(kind.peek() == Kind.WEAPON ? "New Weapon Equipment" : "New Armor Equipment");
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
        if (kind.peek() == Kind.WEAPON) {
            SquadWeaponDoctrine saved = roster.armory().createWeaponDoctrine(
                    draftName.peek(), List.copyOf(weaponIssues));
            sourceId.set(saved.id());
        } else {
            SquadArmorDoctrine saved = roster.armory().createArmorDoctrine(
                    draftName.peek(), List.copyOf(armorIssues));
            sourceId.set(saved.id());
        }
        rememberCurrent();
        feedback.set("Saved " + draftName.peek() + ". It is now available for squad issue.");
        touch();
    }

    private void renameSource() {
        boolean renamed = kind.peek() == Kind.WEAPON
                ? roster.armory().renameWeaponDoctrine(sourceId.peek(), draftName.peek())
                : roster.armory().renameArmorDoctrine(sourceId.peek(), draftName.peek());
        feedback.set(renamed ? "Definition renamed." : "Only player-authored definitions can be renamed.");
        touch();
    }

    private void deleteSource() {
        boolean deleted = kind.peek() == Kind.WEAPON
                ? roster.deleteWeaponDoctrine(sourceId.peek())
                : roster.deleteArmorDoctrine(sourceId.peek());
        if (!deleted) {
            feedback.set("Assigned and built-in definitions cannot be deleted.");
            return;
        }
        feedback.set("Definition deleted. Existing issued equipment was not changed.");
        if (kind.peek() == Kind.WEAPON) loadWeapon(validWeaponId(null));
        else loadArmor(validArmorId(null), true);
    }

    private boolean customSource() {
        String id = sourceId.get();
        if (id == null) return false;
        return kind.get() == Kind.WEAPON
                ? SquadEquipmentDoctrines.weaponById(id) == null
                : SquadEquipmentDoctrines.armorById(id) == null;
    }

    private boolean customId(String id) {
        return kind.peek() == Kind.WEAPON
                ? SquadEquipmentDoctrines.weaponById(id) == null
                : SquadEquipmentDoctrines.armorById(id) == null;
    }

    private boolean assignedSource() {
        String id = sourceId.get();
        return kind.get() == Kind.WEAPON
                ? roster.isWeaponDoctrineAssigned(id) : roster.isArmorDoctrineAssigned(id);
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
        if (kind.peek() == Kind.WEAPON) {
            weaponSourceId = sourceId.peek();
            weaponDraftName = draftName.peek();
        } else {
            armorSourceId = sourceId.peek();
            armorDraftName = draftName.peek();
        }
    }

    private static String specialName(MarineSecondary special) {
        return special != null ? special.displayName() : "None";
    }

    private static String signedPercent(float value) {
        int percent = Math.round(value * 100f);
        return (percent > 0 ? "+" : "") + percent + "%";
    }

    private static String weaponFlavor(SquadWeaponIssue issue) {
        if (issue.special() != null) {
            return "Specialist billet · " + issue.special().displayName() + ".";
        }
        if (issue.role().contains("Leader")) {
            return "Fire-team lead · priority command issue.";
        }
        return switch (issue.primary()) {
            case SMG -> "Assault billet · fast handling in close terrain.";
            case SQUAD_AUTOMATIC -> "Support billet · sustained covering fire.";
            case DMR -> "Precision billet · reach over volume.";
            default -> "Line billet · reliable general-purpose issue.";
        };
    }

    private static String armorFlavor(MarineArmorPattern armor) {
        if (armor == MarineArmorPattern.ARMORLESS) {
            return "Unplated fatigues · mobility over protection.";
        }
        if (armor == MarineArmorPattern.MILITIA || armor == MarineArmorPattern.OUTLAW) {
            return "Patchwork protection · light plate, low burden.";
        }
        if (armor.moveSpeedMult >= 1f) {
            return "Mobile protection · speed under fire.";
        }
        return "Combat protection · plate traded for mobility.";
    }

    private static List<StatMeter> weaponStats(String cardId, SquadWeaponIssue issue) {
        SoldierProfile profile = SoldierProfile.REGULAR;
        float damage = InfantryCombatStats.damage(issue.primary(), issue.grade());
        float range = InfantryCombatStats.range(issue.primary(), issue.grade());
        float accuracy = InfantryCombatStats.accuracy(issue.primary(), issue.grade(), profile);
        float dps = InfantryCombatStats.estimatedDps(issue.primary(), issue.grade(), profile);
        return List.of(
                statMeter(cardId + ":damage", "DMG", formatOneDecimal(damage),
                        damage, maximumWeaponDamage()),
                statMeter(cardId + ":range", "RNG", formatOneDecimal(range),
                        range, maximumWeaponRange()),
                statMeter(cardId + ":accuracy", "ACC",
                        String.format(Locale.ROOT, "%.0f%%", accuracy * 100f), accuracy, 1f),
                statMeter(cardId + ":dps", "DPS", formatOneDecimal(dps),
                        dps, maximumWeaponDps(profile)));
    }

    private static List<StatMeter> armorStats(String cardId, MarineArmorPattern armor) {
        float evasion = 1f - armor.incomingAccuracyMult;
        return List.of(
                statMeter(cardId + ":pool", "POOL",
                        String.format(Locale.ROOT, "%.0f", armor.armorPool),
                        armor.armorPool, maximumArmorPool()),
                statMeter(cardId + ":rating", "RATING",
                        String.format(Locale.ROOT, "%.0f", armor.armorRating),
                        armor.armorRating, maximumArmorRating()),
                statMeter(cardId + ":move", "MOVE",
                        String.format(Locale.ROOT, "%.0f%%", armor.moveSpeedMult * 100f),
                        armor.moveSpeedMult, maximumMoveSpeed()),
                statMeter(cardId + ":evasion", "EVA",
                        signedPercent(1f - armor.incomingAccuracyMult),
                        evasion, maximumEvasion()));
    }

    private static StatMeter statMeter(
            String id, String label, String value, float amount, float maximum) {
        int percentage = maximum > 0f
                ? Math.round(Math.max(0f, Math.min(1f, amount / maximum)) * 100f) : 0;
        return new StatMeter(id, id + ":label", id + ":track", id + ":fill",
                id + ":value", label, value, "width: " + percentage + "%;");
    }

    private static String formatOneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static float maximumWeaponDamage() {
        float maximum = 1f;
        for (MarineWeapon weapon : MarineWeapon.values()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                maximum = Math.max(maximum, InfantryCombatStats.damage(weapon, grade));
            }
        }
        return maximum;
    }

    private static float maximumWeaponRange() {
        float maximum = 1f;
        for (MarineWeapon weapon : MarineWeapon.values()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                maximum = Math.max(maximum, InfantryCombatStats.range(weapon, grade));
            }
        }
        return maximum;
    }

    private static float maximumWeaponDps(SoldierProfile profile) {
        float maximum = 1f;
        for (MarineWeapon weapon : MarineWeapon.values()) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                maximum = Math.max(maximum,
                        InfantryCombatStats.estimatedDps(weapon, grade, profile));
            }
        }
        return maximum;
    }

    private static float maximumArmorPool() {
        float maximum = 1f;
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            maximum = Math.max(maximum, armor.armorPool);
        }
        return maximum;
    }

    private static float maximumArmorRating() {
        float maximum = 1f;
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            maximum = Math.max(maximum, armor.armorRating);
        }
        return maximum;
    }

    private static float maximumMoveSpeed() {
        float maximum = 1f;
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            maximum = Math.max(maximum, armor.moveSpeedMult);
        }
        return maximum;
    }

    private static float maximumEvasion() {
        float maximum = 0.01f;
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            maximum = Math.max(maximum, 1f - armor.incomingAccuracyMult);
        }
        return maximum;
    }

    private static String title(String value) {
        String lower = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
    private static <T> T next(List<T> values, T current) {
        int index = values.indexOf(current);
        return values.get((index + 1 + values.size()) % values.size());
    }

    private enum Kind { WEAPON, ARMOR }

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

    public record StatMeter(
            String id, String labelId, String trackId, String fillId, String valueId,
            String label, String value, String fillStyle) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> labelId; case "trackId" -> trackId;
                case "fillId" -> fillId; case "valueId" -> valueId; case "label" -> label;
                case "value" -> value; case "fillStyle" -> fillStyle;
                default -> throw new IllegalArgumentException("Unknown stat meter property");
            };
        }
    }
}
