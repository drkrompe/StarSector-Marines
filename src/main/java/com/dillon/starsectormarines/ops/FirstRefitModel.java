package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.BilletRoles;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.FirstRefit.Path;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.marine.SquadWeaponIssue;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentBillet;
import com.dillon.starsectormarines.marine.SquadEquipmentPreview;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.dillon.starsectormarines.ui.spec.SpecSheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Two curated choices over the ordinary squad equipment transaction. */
final class FirstRefitModel {
    private final MarineRoster roster;
    private final EquipmentIssueResources resources;
    private final Consumer<String> inspect;
    private final MutableSignal<Map<String, Object>> row;
    private String squadId;
    private Path path;
    private SquadEquipmentPreview reviewed;
    private List<String> reviewedMembers = List.of();
    private List<FleetArmoryViewModel.DoctrineTile> pathCards = List.of();
    private String feedback = "";
    private int selectedBillet;
    private FireTeamBillet currentKit;
    private FireTeamBillet proposedKit;

    FirstRefitModel(Reactor reactor, MarineRoster roster, EquipmentIssueResources resources, Consumer<String> inspect) {
        this.roster = roster; this.resources = resources; this.inspect = inspect;
        row = reactor.signal(Map.of());
        var squads = roster.squads().stream().filter(s -> !s.reserve()).toList();
        if (squads.size() == 1) squadId = squads.get(0).id();
        path = roster.firstRefit().chosenPath();
        refresh();
    }
    PreparationGuide.Lesson guide() {
        if (!FirstRefit.available(roster)) return null;
        String copy = squadId == null ? "lessonRefitSquad" : path == null ? "lessonRefitCompare"
                : !roster.firstRefit().claimed() ? "lessonRefitClaim" : "lessonRefitIssue";
        return CampaignLessons.lesson(roster.firstRefit().claimed() ? 3 : path == null ? 0 : 1,
                "lessonRefitTitle", copy,
                List.of(path == null ? "refit-paths" : "refit-actions", "refit-squads", "refit-paths", "refit-comparison"),
                null, null, GUIDE_HEIGHT);
    }

    /** Castle's callout: portrait, title, progress and three lines, under the two choices. */
    private static final int GUIDE_HEIGHT = 210;

    Signal<Map<String, Object>> row() { return row; }

    /** The shared squad view's projection over this lesson's preview. */
    @SuppressWarnings("unchecked")
    Map<String, Object> inspection() { return (Map<String, Object>) row.get().get("inspection"); }
    void selectSquad(String id) { squadId = id; feedback = ""; refresh(); }
    /** The letter a choice goes by on its tab and on the learn button: A, then B. */
    static String optionLetter(Path value) {
        return value == Path.STEADY_LINE ? Strings.get("openingFirstRefitOptionA") : Strings.get("openingFirstRefitOptionB");
    }

    void selectPath(Path value) {
        path = value; selectedBillet = value != null ? signatureSeat(value) : 0;
        feedback = ""; refresh();
    }

    /** The first seat the path arms with something other than the starter rifle: what the offer is about. */
    private static int signatureSeat(Path path) {
        List<SquadWeaponIssue> seats = FirstRefit.weapons(path);
        for (int i = 0; i < seats.size(); i++) {
            if (!WeaponRegistry.STARTER_PRIMARY_ID.equals(seats.get(i).primaryId())) return i;
        }
        return 0;
    }
    void selectBillet(int index) {
        if (index < 0 || index >= MarineSquad.CAPACITY) return;
        selectedBillet = index; refresh();
    }
    SpecSheet sheet(boolean proposed, int item) {
        FireTeamBillet kit = proposed ? proposedKit : currentKit;
        if (kit == null) return null;
        return switch (item) {
            case 0 -> SpecSheets.weapon(kit.primaryDef(), kit.grade());
            case 1 -> SpecSheets.armor(kit.armorDef());
            case 2 -> kit.specialDef() == null ? null : SpecSheets.special(kit.specialDef());
            case 3 -> IntegralSystemCopy.carried(kit.armorDef()) ? SpecSheets.integralSystem(kit.armorDef()) : null;
            default -> null;
        };
    }
    /** The reward must be chosen before the player can leave the screen. */
    boolean leavable() {
        return roster.firstRefit().claimed() || roster.firstRefit().complete();
    }

    void claim() { roster.firstRefit().claim(roster, path); feedback = ""; refresh(); }
    void issue() {
        if (reviewed == null || path == null || !roster.firstRefit().claimed() || roster.firstRefit().complete()) return;
        SquadEquipmentPreview live = roster.previewSquadEquipment(squadId, path.weapons, path.armor, resources);
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || !reviewedMembers.equals(roster.manningMemberIds(squad))
                || !live.billets().equals(reviewed.billets())) {
            feedback = LessonCopy.text("refitOrderChanged");
        } else {
            SquadEquipmentResult result = roster.firstRefit().issue(roster, squadId, path, resources);
            feedback = result == SquadEquipmentResult.APPLIED ? LessonCopy.text("refitIssued") : blocker(result);
        }
        refresh();
    }

    List<FleetArmoryViewModel.DoctrineTile> pathCards() { return pathCards; }

    void refresh() {
        boolean claimed = roster.firstRefit().claimed(), complete = roster.firstRefit().complete();
        MarineSquad squad = roster.squadById(squadId);
        reviewed = path != null && squad != null
                ? roster.firstRefit().preview(roster, squadId, path) : null;
        reviewedMembers = squad == null ? List.of() : List.copyOf(roster.manningMemberIds(squad));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", LessonCopy.text("lessonRefitTitle"));
        props.put("conversationClasses", complete ? "refit-conversation panel" : "refit-hidden");
        props.put("progress", complete ? Strings.get("openingFirstRefitKitIssued") : squad == null ? Strings.get("openingFirstRefitChooseASquad")
                : path == null ? Strings.get("openingFirstRefitCompareTwoPaths") : !claimed ? Strings.get("openingFirstRefitChooseYourFreeTechnology") : Strings.get("openingFirstRefitFitYourSquad"));
        props.put("copy", complete
                ? LessonCopy.text("refitCompletedCopy")
                : !claimed ? LessonCopy.text("refitTransferCopy")
                : LessonCopy.text("refitFittingCopy"));
        props.put("claimClasses", !claimed && !complete ? "refit-action spec-action" : "refit-hidden");
        props.put("claimDisabled", path == null || reviewed == null);
        props.put("claimLabel", path == null ? Strings.get("openingFirstRefitChooseAPath") : Strings.format("openingFirstRefitLearn", optionLetter(path)));
        props.put("issueClasses", claimed && !complete ? "refit-action spec-action" : "refit-hidden");
        props.put("workClasses", !complete ? "refit-work" : "refit-hidden");
        props.put("doneClasses", complete ? "refit-action spec-action" : "refit-hidden");
        props.put("claim", (Runnable) this::claim);
        props.put("inspect", (Runnable) () -> inspect.accept(roster.firstRefit().issuedSquadId()));
        props.put("issue", (Runnable) this::issue);
        // The free fitting goes to one line squad. With only one there is nothing to pick,
        // so the picker and its hint step aside; with none, the hint says why.
        long lineSquads = roster.squads().stream().filter(s -> !s.reserve()).count();
        props.put("squadsClasses", lineSquads > 1 ? "refit-squads" : "refit-hidden");
        props.put("hintClasses", lineSquads != 1 ? "refit-hint tone-muted" : "refit-hidden");
        props.put("squads", roster.squads().stream().filter(s -> !s.reserve()).map(s -> Map.<String, Object>of(
                "id", "refit-squad:" + s.id(), "label", Strings.join("separatorPath", s.id().equals(squadId) ? Strings.get("squadRefitSelectedTag") : null,
                        s.name(), s.stationed() ? Strings.get("squadRefitStationedTag") : null),
                "classes", "refit-squad spec-tab" + (s.id().equals(squadId) ? " refit-selected selected" : ""),
                "select", (Runnable) () -> selectSquad(s.id()))).toList());
        props.put("squadHint", roster.squads().stream().noneMatch(s -> !s.reserve())
                ? Strings.get("openingFirstRefitNoLineSquadReturnToEquipmentTo") : LessonCopy.text("refitSquadHint"));
        pathCards = List.of(Path.values()).stream().map(p -> FleetArmoryViewModel.weaponTile(
                "refit-path:" + p.name(), new SquadWeaponDoctrine(p.weapons,
                Strings.format("openingFirstRefitWayline", p.label(), (claimed ? FirstRefit.technologyKnown(roster, p) ? Strings.get("openingFirstRefitLearned") : Strings.get("openingFirstRefitFindSpecs") : "")),
                p.description(), FirstRefit.weapons(p)), p == path, () -> selectPath(p)).asCompact()).toList();
        props.put("paths", pathCards);
        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 0; i < pathCards.size(); i++) {
            Path p = Path.values()[i];
            String id = "refit-option:" + p.name();
            // The card's style names its content height; its padding and border add 24px.
            int card = Integer.parseInt(String.valueOf(pathCards.get(i).markupProperty("cardStyle")).replaceAll("\\D", "")) + 24;
            options.add(Map.of("id", id, "tabId", id + ":tab", "captionId", id + ":caption", "letterId", id + ":letter",
                    "letter", optionLetter(p),
                    "tabClasses", "refit-option-tab spec-tab spec-choice" + (p == path ? " selected" : ""),
                    "rowStyle", "height: " + card + "px;",
                    "tabStyle", "height: " + (card - 2) + "px;",
                    "select", (Runnable) () -> selectPath(p),
                    "tile", pathCards.get(i)));
        }
        props.put("options", options);
        currentKit = null; proposedKit = null;
        String selectedName = Strings.get("openingFirstRefitChooseASquadAndAPathTo");
        if (reviewed != null && selectedBillet < reviewed.billets().size()) {
            int i = selectedBillet;
            MarineSoldier soldier = i < reviewedMembers.size() ? roster.soldierById(reviewedMembers.get(i)) : null;
            SquadEquipmentBillet kit = reviewed.billet(i);
            String name = new String[]{Strings.get("openingFirstRefitAlpha"), Strings.get("openingFirstRefitBravo"), Strings.get("openingFirstRefitCharlie")}[i / 4] + " " + (i % 4 + 1)
                    + " / " + (soldier == null ? Strings.get("openingFirstRefitVacant") : soldier.name());
            currentKit = soldier == null ? null : new FireTeamBillet(soldier.name(),
                    soldier.primaryDef().id, soldier.primaryGrade(),
                    soldier.specialEquipmentDef() == null ? null : soldier.specialEquipmentDef().id(), soldier.armorDef().id());
            proposedKit = new FireTeamBillet(kit.role(), kit.primaryId(), kit.grade(), kit.specialEquipmentId(), kit.armorId());
            selectedName = Strings.join("separatorPath", name, BilletRoles.label(kit.role()),
                    soldier == null ? Strings.get("squadRefitVacantPlanOnly") : String.valueOf(soldier.status()));
        }
        // The same squad view the Loadout screen shows, over this lesson's preview.
        props.put("inspection", LoadoutInspection.project(roster, squad, reviewed, selectedBillet, this::selectBillet));
        props.put("selectedName", selectedName);
        props.put("comparisonClasses", reviewed == null ? "refit-hidden" : "refit-comparison");
        props.put("emptyClasses", reviewed == null ? "refit-empty panel" : "refit-hidden");
        props.put("status", !feedback.isEmpty() ? feedback
                : !claimed ? LessonCopy.text("refitChooseTransfer")
                : reviewed == null ? LessonCopy.text("refitChooseLearned")
                : reviewed.canApply() ? Strings.format("openingLoadoutReadyToFit", reviewedMembers.size(), squad.name())
                : blocker(reviewed.result()));
        props.put("disabled", !claimed || complete || reviewed == null || !reviewed.canApply());
        row.set(props);
    }

    private static String blocker(SquadEquipmentResult result) {
        return switch (result) {
            case SQUAD_NOT_READY -> Strings.get("openingFirstRefitTheSquadChangedSelectItAgainTo");
            case STATIONED -> LessonCopy.text("refitStationed");
            case UNKNOWN_CARD -> LessonCopy.text("refitUnknownTechnology");
            default -> LessonCopy.text("refitUnavailable");
        };
    }
}
