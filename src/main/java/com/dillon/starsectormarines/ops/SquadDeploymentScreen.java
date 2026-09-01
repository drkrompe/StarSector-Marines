package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ops.detachment.CaptainDeploymentPolicy;
import com.dillon.starsectormarines.ops.detachment.PersonnelReadiness;
import com.dillon.starsectormarines.ops.detachment.TaskForce;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MLX-authored pre-battle whole-squad assignment workspace. */
public final class SquadDeploymentScreen extends MissionFlowMlxScreen {

    static final String ROOT_COMPONENT = "squad-deployment";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/missions/squad-deployment.mlx");

    private MarineRoster roster;

    public SquadDeploymentScreen() {
        super(ROOT_COMPONENT, COMPONENT_PATHS);
    }

    @Override
    protected void onAttach() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        roster = script != null ? script.roster() : null;
    }

    @Override
    protected Map<String, Object> props() {
        Mission mission = context != null ? context.getSelectedMission() : null;
        int capacity = context != null ? context.getMarineDeploymentCapacity() : 0;
        if (mission != null && mission.type == MissionType.CONQUEST) {
            int selected = PersonnelReadiness.assessSelection(roster,
                    context.getSelectedMarineSquadIds(), 0).selectedReady();
            capacity = Math.max(capacity, selected);
        }
        PersonnelReadiness readiness = PersonnelReadiness.assessSelection(
                roster, context != null ? context.getSelectedMarineSquadIds() : null,
                capacity);
        TaskForce force = TaskForce.of(roster,
                context != null ? context.getSelectedCaptain() : null,
                context != null ? context.getSelectedMarineSquadIds() : null);

        Map<String, Object> props = baseProps();
        props.put("missionName", mission != null ? mission.name : "No mission selected");
        props.put("missionMeta", mission != null
                ? mission.tier.displayName + " · " + mission.type.name()
                : "Return to briefing and choose an operation.");
        props.put("readinessTone", "label title "
                + (readiness.ready() ? "tone-good" : "tone-danger"));
        props.put("readinessSummary", readiness.selectedReady() + " / " + capacity + " seats filled");
        props.put("commandSummary", force.summary());
        props.put("capacitySummary", capacity + " ready seats · "
                + readiness.companyReady() + " marines company-wide");
        props.put("reserveSummary", reserveSummary(readiness, capacity));
        props.put("squadRows", squadRows());
        props.put("emptyMessage", roster == null ? "Persistent roster unavailable." : "");
        props.put("backAction", (Runnable) this::onBack);
        props.put("armoryAction", (Runnable) this::onArmory);
        return props;
    }

    static Map<String, Object> previewProps() {
        Map<String, Object> props = baseProps();
        Runnable none = () -> { };
        props.put("missionName", "SABOTAGE — First Contract");
        props.put("missionMeta", "First Contract · SABOTAGE");
        props.put("readinessTone", "label title tone-good");
        props.put("readinessSummary", "12 / 12 seats filled");
        props.put("commandSummary", "1 officer · 1 squad · 12 marines");
        props.put("capacitySummary", "12 ready seats · 48 marines company-wide");
        props.put("reserveSummary", "No reserve commitment · 1 squad active in the field");
        props.put("squadRows", List.of(
                previewRow(0, "1st Squad", "12 / 12 RTD", "Selected", true, none),
                previewRow(1, "2nd Squad", "11 / 12 RTD", "Available", false, none),
                previewRow(2, "3rd Squad", "8 / 12 RTD", "3 WIA · 1 KIA", false, none),
                previewRow(3, "4th Squad", "12 / 12 RTD", "Command limit", false, none)));
        props.put("emptyMessage", "");
        props.put("backAction", none);
        props.put("armoryAction", none);
        return props;
    }

    private static Map<String, Object> baseProps() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", "Squad Assignment");
        return props;
    }

    private List<SquadRow> squadRows() {
        if (roster == null || context == null) return List.of();
        List<SquadRow> rows = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve()) continue;
            boolean selected = context.isMarineSquadSelected(squad.id());
            boolean canToggle = selected || CaptainDeploymentPolicy.canAdd(
                    roster, context.getSelectedCaptain(),
                    context.getSelectedMarineSquadIds(), squad.id());
            int ready = roster.readyCount(squad);
            int wia = countStatus(squad, MarineSoldierStatus.WIA);
            int mia = countStatus(squad, MarineSoldierStatus.MIA);
            int kia = countStatus(squad, MarineSoldierStatus.KIA);
            String casualties = casualtySummary(wia, mia, kia);
            String base = "deployment-squad-" + rows.size();
            rows.add(new SquadRow(base, "deployment-squad-card"
                    + (selected ? " selected" : ""), squad.name(),
                    ready + " / " + MarineSquad.CAPACITY + " RTD",
                    "label heading squad-card-readiness " + (selected ? "tone-accent"
                            : canToggle ? "tone-good" : "tone-danger"),
                    loadoutSummary(squad), commandSummary(squad, canToggle),
                    casualties, "label squad-card-casualty "
                            + (casualties.isEmpty() ? "tone-muted" : "tone-danger"),
                    !canToggle, () -> toggle(squad.id())));
        }
        return List.copyOf(rows);
    }

    private static SquadRow previewRow(int index, String name, String readiness,
                                       String status, boolean selected, Runnable action) {
        String base = "deployment-preview-" + index;
        return new SquadRow(base, "deployment-squad-card" + (selected ? " selected" : ""),
                name, readiness, "label heading squad-card-readiness "
                        + (selected ? "tone-accent" : "tone-good"),
                "Field Rifles / Standard Plate", "Lt. Mira Hale · " + status,
                index == 2 ? "3 WIA · 1 KIA" : "", "label squad-card-casualty "
                        + (index == 2 ? "tone-danger" : "tone-muted"),
                false, action);
    }

    private void toggle(String squadId) {
        context.toggleMarineSquad(squadId);
        rebuildDocument();
    }

    private int countStatus(MarineSquad squad, MarineSoldierStatus status) {
        int count = 0;
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            if (soldier.status() == status) count++;
        }
        return count;
    }

    private String loadoutSummary(MarineSquad squad) {
        SquadWeaponDoctrine weapons = roster.armory().weaponDoctrineById(squad.weaponDoctrineId());
        SquadArmorDoctrine armor = roster.armory().armorDoctrineById(squad.armorDoctrineId());
        String weaponName = weapons != null ? weapons.displayName() : "Field weapons";
        String armorName = armor != null ? armor.displayName() : "Field armor";
        return weaponName + " / " + armorName;
    }

    private String commandSummary(MarineSquad squad, boolean canToggle) {
        MarineCaptain home = roster.captainForSquad(squad.id());
        String command = home != null
                ? home.rank().displayName() + " " + home.name()
                : "Operation commander";
        return command + (!canToggle ? " · COMMAND LIMIT" : "");
    }

    private static String casualtySummary(int wia, int mia, int kia) {
        List<String> parts = new ArrayList<>();
        if (wia > 0) parts.add(wia + " WIA");
        if (mia > 0) parts.add(mia + " MIA");
        if (kia > 0) parts.add(kia + " KIA");
        return String.join(" · ", parts);
    }

    private static String reserveSummary(PersonnelReadiness readiness, int capacity) {
        int reserve = Math.max(0, readiness.selectedReady() - capacity);
        if (reserve > 0) return reserve + " marines committed in reserve";
        if (readiness.selectedShortfall() > 0) {
            return readiness.selectedShortfall() + " seats remain unfilled";
        }
        return "No reserve commitment";
    }

    private void onBack() {
        if (context != null) context.goTo(ScreenId.BRIEFING);
    }

    private void onArmory() {
        if (context != null) context.openCompanyArmoryFrom(ScreenId.SQUAD_DEPLOYMENT);
    }

    @Override protected void onCancel() { onBack(); }

    @Override
    protected List<String> requiredElementIds() {
        return List.of("squad-deployment-root", "squad-deployment-header",
                "squad-deployment-body", "squad-deployment-summary",
                "squad-deployment-roster", "squad-deployment-grid",
                "squad-deployment-actions", "squad-deployment-back",
                "squad-deployment-armory");
    }

    record SquadRow(String id, String classes, String name, String readiness,
                    String tone, String loadout, String command, String casualties,
                    String casualtyTone, boolean disabled, Runnable action)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "name" -> name;
                case "topId" -> id + "-top"; case "nameId" -> id + "-name";
                case "readinessId" -> id + "-readiness";
                case "loadoutId" -> id + "-loadout"; case "commandId" -> id + "-command";
                case "casualtyId" -> id + "-casualties"; case "readiness" -> readiness;
                case "tone" -> tone; case "loadout" -> loadout; case "command" -> command;
                case "casualties" -> casualties; case "casualtyTone" -> casualtyTone;
                case "disabled" -> disabled; case "action" -> action; default -> null;
            };
        }
    }
}
