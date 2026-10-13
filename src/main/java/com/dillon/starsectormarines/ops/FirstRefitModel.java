package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.FirstRefit.Path;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineRoster;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    Signal<Map<String, Object>> row() { return row; }
    void selectSquad(String id) { squadId = id; feedback = ""; refresh(); }
    void selectPath(Path value) {
        path = value; selectedBillet = value == Path.STEADY_LINE ? 3 : 0;
        feedback = ""; refresh();
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
    void claim() { roster.firstRefit().claim(roster, path); feedback = ""; refresh(); }
    void issue() {
        if (reviewed == null || path == null || !roster.firstRefit().claimed() || roster.firstRefit().complete()) return;
        SquadEquipmentPreview live = roster.previewSquadEquipment(squadId, path.weapons, path.armor, resources);
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || !reviewedMembers.equals(roster.manningMemberIds(squad))
                || !live.billets().equals(reviewed.billets())) {
            feedback = "The squad or its equipment changed. Review the updated order before issuing.";
        } else {
            SquadEquipmentResult result = roster.firstRefit().issue(roster, squadId, path, resources);
            feedback = result == SquadEquipmentResult.APPLIED ? "Equipment plan saved. The squad's current marines, including wounded personnel, now carry the reviewed kit." : blocker(result);
        }
        refresh();
    }

    void refresh() {
        boolean claimed = roster.firstRefit().claimed(), complete = roster.firstRefit().complete();
        MarineSquad squad = roster.squadById(squadId);
        reviewed = path != null && squad != null
                ? roster.firstRefit().preview(roster, squadId, path) : null;
        reviewedMembers = squad == null ? List.of() : List.copyOf(roster.manningMemberIds(squad));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", "CASTLE / A PROPER ISSUE");
        props.put("progress", complete ? "4 / 4  KIT ISSUED" : squad == null ? "1 / 4  CHOOSE A SQUAD"
                : path == null ? "2 / 4  COMPARE TWO PATHS" : !claimed ? "3 / 4  CHOOSE YOUR FREE TECHNOLOGY" : "4 / 4  FIT YOUR SQUAD");
        props.put("copy", complete
                ? "Technology received, kit fitted. The Machine Shop can fit and swap any learned equipment for free. Try different plans in Equipment; find more specs through markets and mission rewards."
                : !claimed ? "Wayline can transfer one equipment package as thanks for holding their checkpoint. Compare both paths, then choose one free technology transfer. Its specs are yours permanently; the Machine Shop fits them for free. The other path can be learned later through markets or mission rewards."
                : "The transfer is yours permanently. Select a square to compare current and proposed equipment; hover an item for full specs. Fit learned equipment for free, including on wounded marines. Other technology remains available through markets and mission rewards.");
        props.put("claimClasses", !claimed && !complete ? "refit-action console-action" : "refit-hidden");
        props.put("claimDisabled", path == null || reviewed == null);
        props.put("claimLabel", path == null ? "CHOOSE A PATH" : "LEARN " + path.label.toUpperCase(Locale.ROOT));
        props.put("issueClasses", claimed && !complete ? "refit-action console-action" : "refit-hidden");
        props.put("workClasses", !complete ? "refit-work" : "refit-hidden");
        props.put("doneClasses", complete ? "refit-action console-action" : "refit-hidden");
        props.put("claim", (Runnable) this::claim);
        props.put("inspect", (Runnable) () -> inspect.accept(roster.firstRefit().issuedSquadId()));
        props.put("issue", (Runnable) this::issue);
        props.put("squads", roster.squads().stream().filter(s -> !s.reserve()).map(s -> Map.<String, Object>of(
                "id", "refit-squad:" + s.id(), "label", (s.id().equals(squadId) ? "SELECTED / " : "") + s.name() + (s.stationed() ? " / STATIONED" : ""),
                "classes", "refit-squad" + (s.id().equals(squadId) ? " refit-selected selected" : ""),
                "select", (Runnable) () -> selectSquad(s.id()))).toList());
        props.put("squadHint", roster.squads().stream().noneMatch(s -> !s.reserve())
                ? "No line squad. Return to Equipment to found one." : "SELECT ONE FORMATION / Wounded and understrength squads can receive equipment.");
        props.put("paths", List.of(Path.values()).stream().map(p -> Map.<String, Object>of(
                "id", "refit-path:" + p.name(), "headingId", "refit-path-heading:" + p.name(),
                "copyId", "refit-path-copy:" + p.name(), "label", (p == path ? "SELECTED / " : "") + p.label
                        + (claimed ? FirstRefit.technologyKnown(roster, p) ? " / LEARNED" : " / FIND SPECS" : ""), "copy", p.description,
                "classes", "refit-path" + (p == path ? " refit-selected selected" : ""),
                "select", (Runnable) () -> selectPath(p))).toList());
        List<Map<String, Object>> billets = new ArrayList<>();
        currentKit = null; proposedKit = null;
        String selectedName = "Choose a squad and a path to compare equipment.";
        if (reviewed != null) for (int i = 0; i < reviewed.billets().size(); i++) {
            int index = i;
            MarineSoldier soldier = i < reviewedMembers.size() ? roster.soldierById(reviewedMembers.get(i)) : null;
            SquadEquipmentBillet kit = reviewed.billet(i);
            String name = new String[]{"Alpha", "Bravo", "Charlie"}[i / 4] + " " + (i % 4 + 1)
                    + " / " + (soldier == null ? "Vacant" : soldier.name());
            FireTeamBillet current = soldier == null ? null : new FireTeamBillet(soldier.name(),
                    soldier.primaryDef().id, soldier.primaryGrade(),
                    soldier.specialEquipmentDef() == null ? null : soldier.specialEquipmentDef().id(), soldier.armorDef().id());
            FireTeamBillet next = new FireTeamBillet(kit.role(), kit.primaryId(), kit.grade(), kit.specialEquipmentId(), kit.armorId());
            String change = current == null ? "PLAN ONLY / No charge" : changes(current, next);
            Map<String, Object> billet = new LinkedHashMap<>();
            billet.put("id", "refit-billet:" + i); billet.put("nameId", "refit-name:" + i);
            billet.put("detailId", "refit-detail:" + i); billet.put("name", name);
            billet.put("gridName", (i % MarineSquad.TEAM_SIZE + 1) + " / " + (soldier == null ? "Vacant" : soldier.name()));
            billet.put("kitId", "refit-kit:" + i);
            billet.put("armorIconId", "refit-armor-icon:" + i);
            billet.put("primaryIconId", "refit-primary-icon:" + i);
            billet.put("armorIcon", soldier == null ? "graphics/ui/formation/marine-cutout-vacant.svg" : soldier.armorDef().iconPath());
            billet.put("primaryIcon", soldier == null ? "" : EquipmentIcons.primary(soldier.primaryDef(), soldier.primaryGrade()));
            billet.put("detail", (soldier == null ? "" : soldier.status() + " / ") + change);
            billet.put("classes", "refit-billet" + (i == selectedBillet ? " refit-selected selected" : ""));
            billet.put("select", (Runnable) () -> selectBillet(index));
            billets.add(billet);
            if (i == selectedBillet) {
                currentKit = current; proposedKit = next;
                selectedName = name + " / " + kit.role() + (soldier == null ? " / Vacant: plan only, no charge" : " / " + soldier.status());
            }
        }
        props.put("billets", billets);
        List<Map<String, Object>> fireTeams = new ArrayList<>();
        if (!billets.isEmpty()) for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            String id = "refit-team:" + team;
            fireTeams.add(Map.of("id", id, "headingId", id + ":heading", "membersId", id + ":members",
                    "label", new String[]{"ALPHA", "BRAVO", "CHARLIE"}[team],
                    "billets", List.copyOf(billets.subList(team * MarineSquad.TEAM_SIZE, (team + 1) * MarineSquad.TEAM_SIZE))));
        }
        props.put("fireTeams", fireTeams);
        props.put("selectedName", selectedName);
        props.put("currentItems", items(false));
        props.put("proposedItems", items(true));
        props.put("comparisonClasses", reviewed == null ? "refit-hidden" : "refit-comparison");
        props.put("emptyClasses", reviewed == null ? "refit-empty panel" : "refit-hidden");
        props.put("status", !feedback.isEmpty() ? feedback
                : !claimed ? "Choose one free transfer. Comparing paths does not learn technology or change equipment."
                : reviewed == null ? "Choose a squad and a learned equipment path. Machine Shop fitting is free."
                : reviewed.canApply() ? "Ready to fit " + reviewedMembers.size() + " current marines in " + squad.name() + ". Free fitting and swaps; wounded personnel included."
                : blocker(reviewed.result()));
        props.put("disabled", !claimed || complete || reviewed == null || !reviewed.canApply());
        row.set(props);
    }

    private static String changes(FireTeamBillet current, FireTeamBillet next) {
        List<String> changes = new ArrayList<>();
        if (!current.primaryId().equals(next.primaryId()) || current.grade() != next.grade()) changes.add("Weapon");
        if (!current.armorId().equals(next.armorId())) changes.add("Armor");
        if (!Objects.equals(current.specialEquipmentId(), next.specialEquipmentId())) changes.add("Special");
        return changes.isEmpty() ? "Keep current kit" : String.join(" + ", changes);
    }

    private List<Map<String, Object>> items(boolean proposed) {
        List<Map<String, Object>> items = new ArrayList<>();
        FireTeamBillet kit = proposed ? proposedKit : currentKit;
        for (int i = 0; i < 3; i++) {
            String kind = new String[]{"WEAPON", "ARMOR", "SPECIAL"}[i];
            String id = "refit-" + (proposed ? "proposed" : "current") + "-item:" + i;
            SpecSheet sheet = sheet(proposed, i);
            String icon = kit == null ? "" : switch (i) {
                case 0 -> EquipmentIcons.primary(kit.primaryDef(), kit.grade());
                case 1 -> kit.armorDef().iconPath();
                default -> kit.specialDef() == null ? "" : kit.specialDef().armoryIconPath();
            };
            SpecSheet before = sheet(false, i);
            boolean same = Objects.equals(before, sheet);
            String change = !proposed ? "" : currentKit == null ? " / PLAN ONLY"
                    : same ? " / UNCHANGED" : sheet == null ? " / REMOVE" : " / REPLACE";
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id); item.put("iconId", id + ":icon"); item.put("textId", id + ":text");
            item.put("kindId", id + ":kind"); item.put("titleId", id + ":title");
            item.put("subtitleId", id + ":subtitle"); item.put("statsId", id + ":stats");
            item.put("systemId", id + ":system"); item.put("icon", icon);
            item.put("kind", kind + change);
            item.put("title", sheet != null ? sheet.title() : kit == null ? "No current equipment" : "No special equipment");
            item.put("subtitle", sheet != null ? sheet.subtitle() : proposed && before != null
                    ? "This plan removes the current special. You can fit learned equipment again for free." : kit == null ? "Vacant billet / no charge" : "This slot carries no special item.");
            item.put("stats", sheet == null ? "" : sheet.stats().stream()
                    .map(stat -> stat.label() + "  " + stat.value()).collect(Collectors.joining("     ")));
            item.put("system", i == 1 && kit != null ? IntegralSystemCopy.tile(kit.armorDef()) : "");
            item.put("classes", "refit-item panel" + (i == 1 ? " refit-armor-item" : i == 2 ? " refit-special-item" : ""));
            items.add(item);
        }
        return items;
    }

    private static String blocker(SquadEquipmentResult result) {
        return switch (result) {
            case SQUAD_NOT_READY -> "The squad changed. Select it again to review the current roll.";
            case STATIONED -> "This squad is stationed away from the ship. Recall it before issuing equipment.";
            case MISSING_TEMPLATE -> "Technology not learned. Find these specs through markets or mission rewards, then fit them here for free.";
            default -> "This order is unavailable. Select a current squad and an available equipment path.";
        };
    }
}
