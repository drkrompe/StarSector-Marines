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

/** Two curated choices over the ordinary squad equipment transaction. */
final class FirstRefitModel {
    private final MarineRoster roster;
    private final EquipmentIssueResources resources;
    private final Runnable done;
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

    FirstRefitModel(Reactor reactor, MarineRoster roster, EquipmentIssueResources resources, Runnable done) {
        this.roster = roster; this.resources = resources; this.done = done;
        row = reactor.signal(Map.of());
        path = roster.firstRefit().chosenPath();
        refresh();
    }
    PreparationGuide.Lesson guide() {
        if (!FirstRefit.available(roster)) return null;
        String copy = path == null ? "lessonRefitSquad" : "lessonRefitClaim";
        return CampaignLessons.lesson(path == null ? 0 : 1,
                "lessonRefitTitle", copy,
                List.of(path == null ? "refit-paths" : "refit-actions", "refit-paths", "refit-comparison"),
                null, null, GUIDE_HEIGHT);
    }

    /** Castle's callout: portrait, title, progress and three lines, under the two choices. */
    private static final int GUIDE_HEIGHT = 210;

    Signal<Map<String, Object>> row() { return row; }

    /** The shared squad view's projection over this lesson's preview. */
    @SuppressWarnings("unchecked")
    Map<String, Object> inspection() { return (Map<String, Object>) row.get().get("inspection"); }
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

    /**
     * The player's one decision: taking a package learns it, fits every line squad and ends the offer (owner). With
     * none at home it refuses first, so the free transfer is not spent on nothing. A save from when these were two
     * steps may already hold the claim, and only needs the fitting.
     */
    void choose() {
        if (path == null || reviewed == null || roster.firstRefit().complete()) return;
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || squad.stationed()) {
            feedback = blocker(SquadEquipmentResult.STATIONED);
            refresh();
            return;
        }
        roster.firstRefit().claim(roster, path);
        feedback = "";
        refresh();
        issue();
        if (roster.firstRefit().complete()) done.run();
    }

    private void issue() {
        if (reviewed == null || path == null || !roster.firstRefit().claimed() || roster.firstRefit().complete()) return;
        SquadEquipmentPreview live = roster.previewSquadEquipment(squadId, path.weapons, path.armor, resources);
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || !reviewedMembers.equals(roster.manningMemberIds(squad))
                || !live.billets().equals(reviewed.billets())) {
            feedback = LessonCopy.text("refitOrderChanged");
        } else {
            SquadEquipmentResult result = roster.firstRefit().issueToAll(roster, path, resources);
            feedback = result == SquadEquipmentResult.APPLIED ? "" : blocker(result);
        }
        refresh();
    }

    List<FleetArmoryViewModel.DoctrineTile> pathCards() { return pathCards; }

    void refresh() {
        boolean claimed = roster.firstRefit().claimed(), complete = roster.firstRefit().complete();
        MarineSquad squad = previewSquad();
        squadId = squad == null ? null : squad.id();
        reviewed = path != null && squad != null
                ? roster.firstRefit().preview(roster, squadId, path) : null;
        reviewedMembers = squad == null ? List.of() : List.copyOf(roster.manningMemberIds(squad));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("chooseDisabled", complete || path == null || reviewed == null || squad.stationed());
        props.put("chooseLabel", path == null ? Strings.get("openingFirstRefitChooseAPath") : Strings.format("openingFirstRefitLearn", optionLetter(path)));
        props.put("choose", (Runnable) this::choose);
        // The fitting goes to every line squad, so there is nothing to pick; with none, the hint says why.
        props.put("hintClasses", squad == null ? "refit-hint tone-muted" : "refit-hidden");
        props.put("squadHint", Strings.get("openingFirstRefitNoLineSquadReturnToEquipmentTo"));
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
                : squad != null && squad.stationed() ? blocker(SquadEquipmentResult.STATIONED)
                : LessonCopy.text("refitChooseTransfer"));
        row.set(props);
    }

    /** The squad the review shows: the first line squad at home, or the first away when none is. */
    private MarineSquad previewSquad() {
        MarineSquad away = null;
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve()) continue;
            if (!squad.stationed()) return squad;
            if (away == null) away = squad;
        }
        return away;
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
