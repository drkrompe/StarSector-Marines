package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentTemplateCost;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.FirstRefit.Path;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentBillet;
import com.dillon.starsectormarines.marine.SquadEquipmentPreview;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private String feedback = "";

    FirstRefitModel(Reactor reactor, MarineRoster roster, EquipmentIssueResources resources, Consumer<String> inspect) {
        this.roster = roster; this.resources = resources; this.inspect = inspect;
        row = reactor.signal(Map.of());
        refresh();
    }
    static FireTeamBillet example(boolean scout) {
        return new FireTeamBillet(scout ? "Scout" : "Line gunner",
                scout ? WeaponRegistry.PULSE_RIFLE_ID : WeaponRegistry.SQUAD_AUTOMATIC_ID,
                EquipmentGrade.MILSPEC, null, scout ? MarineArmorPattern.BLUE_SCOUT.id : MarineArmorPattern.ARMY_GREEN.id);
    }
    Signal<Map<String, Object>> row() { return row; }
    void selectSquad(String id) { squadId = id; feedback = ""; refresh(); }
    void selectPath(Path value) { path = value; feedback = ""; refresh(); }
    void claim() { roster.firstRefit().claim(roster); refresh(); }
    void issue() {
        if (reviewed == null || path == null || roster.firstRefit().complete()) return;
        SquadEquipmentPreview live = roster.previewSquadEquipment(squadId, path.weapons, path.armor, resources);
        MarineSquad squad = roster.squadById(squadId);
        if (squad == null || !reviewedMembers.equals(roster.manningMemberIds(squad))
                || !live.billets().equals(reviewed.billets()) || !live.issueCost().equals(reviewed.issueCost())) {
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
        reviewed = claimed && path != null && squad != null
                ? roster.previewSquadEquipment(squadId, path.weapons, path.armor, resources) : null;
        reviewedMembers = squad == null ? List.of() : List.copyOf(roster.manningMemberIds(squad));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", "CASTLE / A PROPER ISSUE");
        props.put("progress", complete ? "4 / 4  KIT ISSUED" : !claimed ? "1 / 4  WAYLINE'S CONSIGNMENT"
                : squad == null ? "2 / 4  CHOOSE A SQUAD" : path == null ? "3 / 4  CHOOSE A PATH" : "4 / 4  REVIEW AND ISSUE");
        props.put("copy", complete
                ? "That is what the last job bought us: a better-equipped squad. Their names and command stay the same; the weapons and suits on the roll are what they will take into the next fight. Inspect Alpha, Bravo and Charlie in the Armory. You can use either plan again; future changes pay their own material bill."
                : !claimed ? "Mara sent over the patterns her quartermaster could spare: Milspec Stalwart automatics, Milspec Lancer pulse rifles and Janus scout suits. We held their checkpoint. Now Wayline can help us put better kit on our people. Accept the patterns, then we will refit one squad together."
                : squad == null ? "Choose a squad to equip. Wounded marines can receive gear while recovering; a squad does not need to be full. We only charge for changed equipment issued to people on its current roll."
                : path == null ? "Two plans, Captain. Steady Line puts more weight behind holding ground. Forward Screen puts more scouts ahead of us. Each plans weapons and armor for twelve billets; select one to review the issue to your current marines."
                : "Read across the roll: current kit on the left, proposed kit on the right. Nothing changes until Issue to squad. Patterns are ours to keep; the supplies, armaments and machinery below pay for the changed equipment. Wounded marines receive kit without changing recovery. Vacant slots keep the plan and cost nothing; review and issue replacement equipment when recruits join.");
        props.put("showcaseClasses", !claimed ? "refit-showcase" : "refit-hidden");
        props.put("claimClasses", !claimed ? "refit-action console-action" : "refit-hidden");
        props.put("workClasses", claimed && !complete ? "refit-work" : "refit-hidden");
        props.put("laterLabel", complete ? "ARMORY GALLERY" : "ARMORY GALLERY / Continue later");
        props.put("doneClasses", complete ? "refit-action console-action" : "refit-hidden");
        props.put("claim", (Runnable) this::claim);
        props.put("inspect", (Runnable) () -> inspect.accept(roster.firstRefit().issuedSquadId()));
        props.put("issue", (Runnable) this::issue);
        props.put("squads", roster.squads().stream().filter(s -> !s.reserve()).map(s -> Map.<String, Object>of(
                "id", "refit-squad:" + s.id(), "label", (s.id().equals(squadId) ? "SELECTED / " : "") + s.name() + (s.stationed() ? " / STATIONED" : ""),
                "classes", "refit-squad" + (s.id().equals(squadId) ? " refit-selected" : ""),
                "select", (Runnable) () -> selectSquad(s.id()))).toList());
        props.put("squadHint", roster.squads().stream().noneMatch(s -> !s.reserve())
                ? "No line squad. Return to the Armory gallery to found one." : "SELECT ONE FORMATION / Wounded and understrength squads can receive equipment.");
        props.put("paths", List.of(Path.values()).stream().map(p -> Map.<String, Object>of(
                "id", "refit-path:" + p.name(), "headingId", "refit-path-heading:" + p.name(),
                "copyId", "refit-path-copy:" + p.name(), "label", (p == path ? "SELECTED / " : "") + p.label, "copy", p.description,
                "classes", "refit-path" + (p == path ? " refit-selected" : ""),
                "select", (Runnable) () -> selectPath(p))).toList());
        List<Map<String, Object>> billets = new ArrayList<>();
        if (reviewed != null) for (int i = 0; i < reviewed.billets().size(); i++) {
            MarineSoldier soldier = i < reviewedMembers.size() ? roster.soldierById(reviewedMembers.get(i)) : null;
            SquadEquipmentBillet kit = reviewed.billet(i);
            String current = soldier == null ? "Vacant / no charge" : soldier.primaryGrade().displayName + " "
                    + soldier.primaryDef().modelName + " / " + soldier.armorDef().displayName()
                    + (soldier.specialEquipmentDef() == null ? "" : " / " + soldier.specialEquipmentDef().displayName());
            billets.add(Map.of("id", "refit-billet:" + i, "nameId", "refit-name:" + i,
                    "currentId", "refit-current:" + i, "nextId", "refit-next:" + i, "name", new String[]{"Alpha", "Bravo", "Charlie"}[i / 4] + " " + (i % 4 + 1)
                            + " / " + (soldier == null ? "Vacant" : soldier.name() + " / " + soldier.status()),
                    "current", current, "next", kit.grade().displayName + " " + kit.primaryDef().modelName
                            + " / " + kit.armorDef().displayName() + " / No special"));
        }
        props.put("billets", billets);
        props.put("bill", reviewed == null ? "Choose a squad and a path to see the bill."
                : "COST  " + reviewed.issueCost().display() + "\nAVAILABLE  " + resources.available().display());
        List<Map<String, Object>> costs = new ArrayList<>();
        if (reviewed != null) {
            EquipmentTemplateCost cost = reviewed.issueCost(), have = resources.available();
            addCost(costs, Commodities.SUPPLIES, cost.supplies(), have.supplies());
            addCost(costs, Commodities.HAND_WEAPONS, cost.heavyArmaments(), have.heavyArmaments());
            addCost(costs, Commodities.HEAVY_MACHINERY, cost.heavyMachinery(), have.heavyMachinery());
            addCost(costs, Commodities.FOOD, cost.food(), have.food());
        }
        props.put("costs", costs);
        props.put("billHeading", reviewed == null ? "Choose a squad and a path to review costs."
                : reviewed.issueCost().isZero() && reviewed.canApply() ? "No material charge. Vacant slots receive no gear."
                : "MATERIAL BILL / Cost | Available | Short by");
        props.put("shortfall", reviewed == null ? "" : shortage(reviewed.issueCost(), resources.available()));
        props.put("status", !feedback.isEmpty() ? feedback : reviewed == null ? "Selection spends nothing. Both paths remain available."
                : reviewed.canApply() ? "Ready to issue to " + reviewedMembers.size() + " current marines in " + squad.name() + ". Wounded personnel included; vacant slots cost nothing."
                : blocker(reviewed.result()));
        props.put("disabled", complete || reviewed == null || !reviewed.canApply());
        row.set(props);
    }

    private void addCost(List<Map<String, Object>> rows, String commodity, int cost, int have) {
        if (cost == 0) return;
        String id = "refit-material:" + commodity;
        rows.add(Map.of("id", id, "iconId", id + ":icon", "labelId", id + ":label",
                "icon", resources.commodityIcon(commodity), "label", resources.commodityName(commodity)
                        + " / " + cost + " | " + have + " | " + Math.max(0, cost - have)));
    }

    private static String shortage(EquipmentTemplateCost cost, EquipmentTemplateCost available) {
        EquipmentTemplateCost missing = new EquipmentTemplateCost(Math.max(0, cost.supplies() - available.supplies()),
                Math.max(0, cost.heavyArmaments() - available.heavyArmaments()),
                Math.max(0, cost.heavyMachinery() - available.heavyMachinery()), Math.max(0, cost.food() - available.food()));
        return missing.isZero() ? "" : "SHORT BY  " + missing.display();
    }
    private static String blocker(SquadEquipmentResult result) {
        return switch (result) {
            case SQUAD_NOT_READY -> "The squad changed. Select it again to review the current roll.";
            case STATIONED -> "This squad is stationed away from the ship. Recall it before issuing equipment.";
            case INSUFFICIENT_CARGO -> "Not enough materials. Resupply, then return to this order. Nothing has been spent.";
            default -> "This order is unavailable. Select a current squad and an available equipment path.";
        };
    }
}
