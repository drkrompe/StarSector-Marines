package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.setup.ColdAccountIntervention.Phase;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Live, render-free projection for the MLX-authored battle command overlay. */
final class BattleHudOverlayModel {

    static final float PAUSED = 0f;
    static final float NORMAL = 1f;
    static final float DOUBLE = 2f;
    static final float QUAD = 4f;

    private static final String TIME_CLASSES = "time-button";
    private static final String TIME_SELECTED_CLASSES = "time-button time-selected";
    private static final String TIME_STYLE = "";
    private static final String TIME_SELECTED_STYLE =
            "background-color: #3a3018; border-color: #ffd464; color: #ffd464;";
    private static final String OBJECTIVE_PANEL = "objective-panel";
    private static final String OBJECTIVE_PANEL_HIDDEN =
            "objective-panel objective-panel-hidden";
    private static final String OBJECTIVE_FOCUS = "objective-focus";
    private static final String OBJECTIVE_FOCUS_HIDDEN =
            "objective-focus objective-focus-hidden";
    private static final String COMMAND_PANEL = "conquest-command-panel";
    private static final String COMMAND_PANEL_HIDDEN =
            "conquest-command-panel conquest-command-panel-hidden";

    private final MutableSignal<String> missionClasses;
    private final MutableSignal<String> missionSquadClasses;
    private final MutableSignal<String> missionSquadStyle;
    private final MutableSignal<List<MissionCard>> missionCards;
    private final MutableSignal<List<MissionSquad>> missionSquads;
    private final MutableSignal<String> missionSquadSummary;
    private final MutableSignal<String> missionPanelStyle;
    private final MutableSignal<String> missionListStyle;
    private final MutableSignal<String> missionScore;
    private final MutableSignal<String> pauseClasses;
    private final MutableSignal<String> normalClasses;
    private final MutableSignal<String> doubleClasses;
    private final MutableSignal<String> quadClasses;
    private final MutableSignal<String> pauseStyle;
    private final MutableSignal<String> normalStyle;
    private final MutableSignal<String> doubleStyle;
    private final MutableSignal<String> quadStyle;
    private final MutableSignal<String> objectivePanelClasses;
    private final MutableSignal<String> objectiveTitle;
    private final MutableSignal<String> objectiveScore;
    private final MutableSignal<String> objectiveTally;
    private final MutableSignal<List<ObjectiveChip>> objectiveChips;
    private final MutableSignal<String> objectiveFocusClasses;
    private final MutableSignal<String> objectiveFocus;
    private final MutableSignal<String> objectiveProgressStyle;
    private final MutableSignal<String> objectiveLocateClasses;
    private final MutableSignal<Boolean> objectiveLocateDisabled;
    private Runnable locateObjective = () -> { };
    private final MutableSignal<String> commandPanelClasses;
    private final MutableSignal<String> commandPhase;
    private final MutableSignal<String> commandForce;
    private final MutableSignal<String> commandTitle;
    private final MutableSignal<List<ConquestLane>> conquestLanes;
    private final MutableSignal<String> conquestLaneClasses;
    private final MutableSignal<String> conquestGroupClasses;
    private final MutableSignal<List<ConquestGroup>> conquestGroups;

    private final Consumer<Float> speedSetter;
    private final String[] speedLabels;

    BattleHudOverlayModel(Reactor reactor, Consumer<Float> speedSetter,
                          String pauseLabel, String normalLabel,
                          String doubleLabel, String quadLabel) {
        this.speedSetter = speedSetter;
        this.speedLabels = new String[]{pauseLabel, normalLabel, doubleLabel, quadLabel};
        missionClasses = reactor.signal("mission-panel mission-hidden");
        missionSquadClasses = reactor.signal("mission-squads-panel mission-hidden");
        missionSquadStyle = reactor.signal("");
        missionCards = reactor.signal(List.of());
        missionSquads = reactor.signal(List.of());
        missionSquadSummary = reactor.signal("");
        missionPanelStyle = reactor.signal("");
        missionListStyle = reactor.signal("");
        missionScore = reactor.signal("");
        pauseClasses = reactor.signal(TIME_CLASSES);
        normalClasses = reactor.signal(TIME_SELECTED_CLASSES);
        doubleClasses = reactor.signal(TIME_CLASSES);
        quadClasses = reactor.signal(TIME_CLASSES);
        pauseStyle = reactor.signal(TIME_STYLE);
        normalStyle = reactor.signal(TIME_SELECTED_STYLE);
        doubleStyle = reactor.signal(TIME_STYLE);
        quadStyle = reactor.signal(TIME_STYLE);
        objectivePanelClasses = reactor.signal(OBJECTIVE_PANEL_HIDDEN);
        objectiveTitle = reactor.signal("CAPTURE");
        objectiveScore = reactor.signal("");
        objectiveTally = reactor.signal("");
        objectiveChips = reactor.signal(List.of());
        objectiveFocusClasses = reactor.signal(OBJECTIVE_FOCUS_HIDDEN);
        objectiveFocus = reactor.signal("");
        objectiveProgressStyle = reactor.signal("width: 0%;");
        objectiveLocateClasses = reactor.signal("objective-locate objective-locate-hidden");
        objectiveLocateDisabled = reactor.signal(true);
        commandPanelClasses = reactor.signal(COMMAND_PANEL_HIDDEN);
        commandPhase = reactor.signal("");
        commandForce = reactor.signal("");
        commandTitle = reactor.signal("COMMANDER");
        conquestLanes = reactor.signal(List.of());
        conquestLaneClasses = reactor.signal("conquest-lanes");
        conquestGroupClasses = reactor.signal("conquest-groups conquest-groups-hidden");
        conquestGroups = reactor.signal(List.of());
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("missionClasses", missionClasses);
        props.put("missionSquadClasses", missionSquadClasses);
        props.put("missionSquadStyle", missionSquadStyle);
        props.put("missionCards", missionCards);
        props.put("missionSquads", missionSquads);
        props.put("missionSquadSummary", missionSquadSummary);
        props.put("missionPanelStyle", missionPanelStyle);
        props.put("missionListStyle", missionListStyle);
        props.put("missionScore", missionScore);
        props.put("timeLabel", "TIME");
        props.put("pauseLabel", speedLabels[0]);
        props.put("normalLabel", speedLabels[1]);
        props.put("doubleLabel", speedLabels[2]);
        props.put("quadLabel", speedLabels[3]);
        props.put("pauseClasses", pauseClasses);
        props.put("normalClasses", normalClasses);
        props.put("doubleClasses", doubleClasses);
        props.put("quadClasses", quadClasses);
        props.put("pauseStyle", pauseStyle);
        props.put("normalStyle", normalStyle);
        props.put("doubleStyle", doubleStyle);
        props.put("quadStyle", quadStyle);
        props.put("pauseAction", (Runnable) () -> speedSetter.accept(PAUSED));
        props.put("normalAction", (Runnable) () -> speedSetter.accept(NORMAL));
        props.put("doubleAction", (Runnable) () -> speedSetter.accept(DOUBLE));
        props.put("quadAction", (Runnable) () -> speedSetter.accept(QUAD));
        props.put("objectivePanelClasses", objectivePanelClasses);
        props.put("objectiveTitle", objectiveTitle);
        props.put("objectiveScore", objectiveScore);
        props.put("objectiveTally", objectiveTally);
        props.put("objectiveChips", objectiveChips);
        props.put("objectiveFocusClasses", objectiveFocusClasses);
        props.put("objectiveFocus", objectiveFocus);
        props.put("objectiveProgressStyle", objectiveProgressStyle);
        props.put("objectiveLocateClasses", objectiveLocateClasses);
        props.put("objectiveLocateDisabled", objectiveLocateDisabled);
        props.put("objectiveLocateAction", (Runnable) () -> locateObjective.run());
        props.put("commandPanelClasses", commandPanelClasses);
        props.put("commandPhase", commandPhase);
        props.put("commandForce", commandForce);
        props.put("commandTitle", commandTitle);
        props.put("conquestLanes", conquestLanes);
        props.put("conquestLaneClasses", conquestLaneClasses);
        props.put("conquestGroupClasses", conquestGroupClasses);
        props.put("conquestGroups", conquestGroups);
        return props;
    }

    Presentation update(float speedMultiplier,
                        Collection<CompoundService.Record> records,
                        CommanderSnapshot<?> commander) {
        List<CaptureObjective> objectives = new ArrayList<>();
        if (records != null) {
            for (CompoundService.Record record : records) {
                objectives.add(new CaptureObjective(record.node.kind,
                        record.state, record.captureProgress));
            }
        }
        return updateProjected(speedMultiplier, objectives, commander);
    }

    boolean updateProjected(float speedMultiplier,
                            List<CaptureObjective> objectives) {
        return updateProjected(speedMultiplier, objectives, null).objectivesVisible();
    }

    Presentation updateProjected(float speedMultiplier,
                                 List<CaptureObjective> objectives,
                                 CommanderSnapshot<?> commander) {
        missionClasses.set("mission-panel mission-hidden");
        missionSquadClasses.set("mission-squads-panel mission-hidden");
        missionCards.set(List.of());
        missionSquads.set(List.of());
        missionSquadStyle.set("");
        missionPanelStyle.set("");
        missionListStyle.set("");
        objectiveTitle.set("CAPTURE");
        objectiveLocateClasses.set("objective-locate objective-locate-hidden");
        objectiveLocateDisabled.set(true);
        locateObjective = () -> { };
        setSelectedSpeed(speedMultiplier);
        List<CaptureObjective> stable = objectives == null ? List.of() : List.copyOf(objectives);
        boolean visible = !stable.isEmpty();
        objectivePanelClasses.set(visible ? OBJECTIVE_PANEL : OBJECTIVE_PANEL_HIDDEN);
        if (!visible) {
            objectiveScore.set("");
            objectiveTally.set("");
            objectiveChips.set(List.of());
            objectiveFocusClasses.set(OBJECTIVE_FOCUS_HIDDEN);
            objectiveFocus.set("");
            objectiveProgressStyle.set("width: 0%;");
        } else {
            int secured = 0;
            int contested = 0;
            int hostile = 0;
            CaptureObjective focus = null;
            int focusIndex = -1;
            float focusProgress = -1f;
            EnumMap<TacticalNode.Kind, Integer> ordinalByKind =
                    new EnumMap<>(TacticalNode.Kind.class);
            List<ObjectiveChip> chips = new ArrayList<>(stable.size());
            for (int index = 0; index < stable.size(); index++) {
                CaptureObjective objective = stable.get(index);
                int ordinal = ordinalByKind.merge(objective.kind(), 1, Integer::sum);
                String label = abbreviation(objective.kind()) + ordinal;
                String stateClass;
                switch (objective.state()) {
                    case MARINE_HELD -> {
                        secured++;
                        stateClass = "objective-secured";
                    }
                    case CONTESTED -> {
                        contested++;
                        stateClass = "objective-contested";
                        float progress = clamp01(objective.progress());
                        if (focus == null || progress > focusProgress) {
                            focus = objective;
                            focusIndex = ordinal;
                            focusProgress = progress;
                        }
                    }
                    case DEFENDER_HELD -> {
                        hostile++;
                        stateClass = "objective-hostile";
                    }
                    default -> throw new IllegalStateException("Unhandled compound state "
                            + objective.state());
                }
                chips.add(new ObjectiveChip("battle-objective-chip-" + index,
                        label, "objective-chip " + stateClass));
            }
            objectiveScore.set(secured + " / " + stable.size());
            objectiveTally.set(secured + " SECURE  ·  " + contested
                    + " CONTESTED  ·  " + hostile + " HOSTILE");
            objectiveChips.set(List.copyOf(chips));
            if (focus == null) {
                objectiveFocusClasses.set(OBJECTIVE_FOCUS_HIDDEN);
                objectiveFocus.set("");
                objectiveProgressStyle.set("width: 0%;");
            } else {
                int percent = Math.round(focusProgress * 100f);
                objectiveFocusClasses.set(OBJECTIVE_FOCUS);
                objectiveFocus.set(kindName(focus.kind()) + " " + focusIndex
                        + "  ·  CONTESTED " + percent + "%");
                objectiveProgressStyle.set("width: " + percent + "%;");
            }
        }
        return new Presentation(visible, updateConquestCommand(commander));
    }

    Presentation updateMission(MissionObjectiveSnapshot snapshot, Consumer<MissionObjectiveSnapshot.Marker> locate) {
        if (snapshot.objectives().isEmpty()) return new Presentation(false, false);
        objectivePanelClasses.set(OBJECTIVE_PANEL_HIDDEN);
        boolean territorial = snapshot.objectives().stream().allMatch(i -> i.id().startsWith("territory-"));
        if (!territorial) commandPanelClasses.set(COMMAND_PANEL_HIDDEN);
        missionClasses.set("mission-panel");
        missionSquadClasses.set(territorial || snapshot.squads().isEmpty() ? "mission-squads-panel mission-hidden" : "mission-squads-panel");
        missionSquadStyle.set(territorial || snapshot.squads().isEmpty() ? "height: 0px; width: 0px; padding: 0px; border-width: 0px;" : "height: 256px;");
        int count = Math.min(2, snapshot.objectives().size());
        missionPanelStyle.set("height: " + (33 + count * 174) + "px;");
        missionListStyle.set("height: " + (count * 174) + "px;");
        missionScore.set(snapshot.objectives().stream().filter(i -> i.state() == MissionObjectiveSnapshot.State.COMPLETE).count()
                + " / " + snapshot.objectives().size());
        missionCards.set(snapshot.objectives().stream().map(i -> new MissionCard(i, () -> {
            if (i.focus() != null) locate.accept(i.focus());
        })).toList());
        missionSquads.set(snapshot.squads().stream().map(MissionSquad::new).toList());
        missionSquadSummary.set(snapshot.squads().isEmpty() ? "Awaiting deployed squad assignments"
                : snapshot.squads().size() + " squads / current assignments");
        return new Presentation(true, true, count);
    }

    record MissionCard(MissionObjectiveSnapshot.Item item, Runnable locate) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> "mission-" + item.id();
                case "name" -> item.name();
                case "phase" -> item.phase();
                case "steps" -> item.steps();
                case "instruction" -> item.instruction();
                case "progressText" -> item.progressText();
                case "blocker" -> item.blocker().isEmpty() ? "" : item.blocker();
                case "classes" -> "mission-card " + (item.state() == MissionObjectiveSnapshot.State.COMPLETE ? "mission-complete"
                        : item.state() == MissionObjectiveSnapshot.State.FAILED ? "mission-failed" : "mission-active");
                case "progressStyle" -> "width: " + Math.round(Math.max(0, Math.min(1, item.progress())) * 100) + "%;";
                case "trackStyle" -> item.progress() < 0 ? "height: 0px; opacity: 0; overflow: hidden;" : "";
                case "locate" -> locate;
                case "disabled" -> item.focus() == null;
                default -> {
                    if (key.endsWith("Id")) yield "mission-" + item.id() + "-" + key.substring(0, key.length() - 2);
                    throw new IllegalArgumentException(key);
                }
            };
        }
    }

    record MissionSquad(MissionObjectiveSnapshot.SquadActivity activity) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> "mission-squad-" + activity.squadId();
                case "name" -> activity.name();
                case "task" -> activity.task();
                case "status" -> activity.status();
                default -> {
                    if (key.endsWith("Id")) yield "mission-squad-" + activity.squadId() + "-" + key.substring(0, key.length() - 2);
                    throw new IllegalArgumentException(key);
                }
            };
        }
    }

    /** Public objective state, independent of commander cadence and debug visibility. */
    Presentation updateRaid(String targetName, RaidObjective.Phase phase,
                            float progress, float duration, Runnable locate) {
        boolean secured = phase == RaidObjective.Phase.EGRESS || phase == RaidObjective.Phase.COMPLETE;
        boolean complete = phase == RaidObjective.Phase.COMPLETE;
        objectiveTitle.set("MISSION OBJECTIVES");
        objectivePanelClasses.set(OBJECTIVE_PANEL);
        objectiveScore.set((complete ? 2 : secured ? 1 : 0) + " / 2");
        objectiveChips.set(List.of(
                new ObjectiveChip("battle-raid-seize", "1 / SEIZE" + (secured ? " / DONE" : " / ACTIVE"),
                        "objective-chip objective-raid " + (secured ? "objective-secured" : "objective-contested")),
                new ObjectiveChip("battle-raid-return", "2 / RETURN" + (complete ? " / DONE" : secured ? " / ACTIVE" : " / NEXT"),
                        "objective-chip objective-raid " + (complete ? "objective-secured" : secured ? "objective-contested" : "objective-pending"))));
        objectiveTally.set(targetName + (secured ? " / SECURED" : ""));
        objectiveFocusClasses.set(OBJECTIVE_FOCUS);
        objectiveFocus.set(switch (phase) {
            case APPROACH -> "Secure site; hold " + Math.round(duration) + "s without enemies";
            case SERVICE -> "Securing target / " + Math.round(clamp01(progress / duration) * 100) + "%";
            case EGRESS -> "Target secured. Return to landing zone.";
            case COMPLETE -> "Target recovered. Extraction complete.";
        });
        objectiveProgressStyle.set("width: " + Math.round(100 * (secured ? 1f : clamp01(progress / duration))) + "%;");
        objectiveLocateClasses.set("objective-locate");
        objectiveLocateDisabled.set(false);
        locateObjective = locate;
        return new Presentation(true, false);
    }

    Presentation updateIntervention(Phase phase, String instruction, int remainingWalls, int initialWalls,
                                    boolean companyRecovered) {
        objectiveTitle.set(phase == Phase.EVIDENCE ? "A COLD ACCOUNT" : "HEGEMONY / " + phase);
        objectivePanelClasses.set(OBJECTIVE_PANEL);
        objectiveScore.set("");
        objectiveTally.set(switch (phase) {
            case EVIDENCE -> "DISPATCH OFFICE / RECOVER RECORDS";
            case WITHDRAW, WARNING, BOMBARD -> "CLEAR FIRING AREA > SHUTTLE PICKUP";
            case PATROL, AFTERMATH -> companyRecovered ? "COMPANY RECOVERED / STRONGHOLD SILENCED"
                    : "STRONGHOLD SILENCED / SHUTTLE PICKUP";
            case COMPLETE -> "COMPANY RECOVERED / STRONGHOLD SILENCED";
            case FAILED -> "INTERVENTION FAILED";
        });
        objectiveChips.set(List.of());
        objectiveFocusClasses.set(OBJECTIVE_FOCUS);
        objectiveFocus.set(instruction);
        objectiveProgressStyle.set("width: " + Math.round(100f * (initialWalls - remainingWalls) / Math.max(1, initialWalls)) + "%;");
        return new Presentation(true, false);
    }

    /** Read-only convoy report; a captured site is a release condition, never a delivery. */
    void updateFreight(int phase, List<Integer> identities, List<String> cargo, int delivered, int lost) {
        objectiveTitle.set(phase == 3 ? "THUNDER RUN" : "FREIGHT COLUMN");
        objectivePanelClasses.set(OBJECTIVE_PANEL);
        objectiveScore.set(delivered + " / " + cargo.size());
        List<ObjectiveChip> chips = new ArrayList<>();
        for (int i = 0; i < cargo.size(); i++) chips.add(new ObjectiveChip("battle-cargo-" + i,
                (identities.get(i) + 1) + " / " + (cargo.get(i).equals("DELIVERED") ? "OUT" : cargo.get(i).equals("LOST") ? "LOST" : cargo.get(i).equals("WAITING") ? "WAIT" : "ROAD"), "objective-chip objective-cargo " + (cargo.get(i).equals("DELIVERED") ? "objective-secured"
                : cargo.get(i).equals("LOST") ? "objective-hostile" : "objective-contested")));
        objectiveChips.set(List.copyOf(chips));
        objectiveTally.set(delivered + " DELIVERED  /  " + lost + " LOST");
        objectiveFocusClasses.set(OBJECTIVE_FOCUS);
        objectiveFocus.set(switch (phase) {
            case 0 -> "SECURE DISPATCH POST";
            case 1 -> "SECURE EAST CROSSING";
            case 2 -> "SECURE OUTBOUND GATE";
            default -> "COVER THE COLUMN TO THE EXIT";
        });
        objectiveProgressStyle.set("width: " + Math.round(100f * (delivered + lost) / Math.max(1, cargo.size())) + "%;");
    }

    void updateFreightHold(int site, int ticks) {
        if (site <= 0) return;
        objectiveTitle.set("HOLD THE FREIGHT ROAD");
        objectiveFocus.set((site == 1 ? "DISPATCH" : "EAST CROSSING")
                + " / HOLD " + (ticks + 29) / 30 + "s");
    }

    private boolean updateConquestCommand(CommanderSnapshot<?> commander) {
        if (commander == null || commander.perspective() != Faction.MARINE
                || !(commander.detail() instanceof ConquestFrontSnapshot front)
                || front.perspective() != Faction.MARINE) {
            commandPanelClasses.set(COMMAND_PANEL_HIDDEN);
            commandPhase.set("");
            commandForce.set("");
            conquestLanes.set(List.of());
            conquestGroups.set(List.of());
            conquestGroupClasses.set("conquest-groups conquest-groups-hidden");
            return false;
        }

        commandPanelClasses.set(COMMAND_PANEL);
        commandPhase.set(phaseLabel(front.phase()));
        boolean grouped = !front.groups().isEmpty();
        commandTitle.set(grouped ? "COMMAND GROUPS" : "COMMANDER");
        commandForce.set(commander.commandPoolSize() + " SQUADS  ·  "
                + (grouped ? front.groups().size() + " GROUPS  ·  " : "")
                + commander.reserveCount() + " RESERVE");
        conquestLaneClasses.set(grouped ? "conquest-lanes conquest-lanes-hidden" : "conquest-lanes");
        conquestGroupClasses.set(grouped ? "conquest-groups"
                : "conquest-groups conquest-groups-hidden");
        conquestGroups.set(grouped ? projectGroups(front) : List.of());
        List<ConquestLane> lanes = new ArrayList<>(front.tracks().size());
        if (!grouped) front.tracks().stream()
                .sorted((left, right) -> Integer.compare(left.index(), right.index()))
                .forEach(track -> lanes.add(projectLane(front, track)));
        conquestLanes.set(List.copyOf(lanes));
        return true;
    }

    /** Three bounded rows: named leaders first, then an explicitly aggregated remainder. */
    private static List<ConquestGroup> projectGroups(ConquestFrontSnapshot front) {
        List<ConquestFrontSnapshot.GroupState> ordered = front.groups().stream()
                .sorted(Comparator.comparing((ConquestFrontSnapshot.GroupState group) ->
                                !group.identity().namedOfficer())
                        .thenComparing(group -> group.identity().groupId())).toList();
        int individualRows = ordered.size() <= 3 ? ordered.size() : 2;
        List<ConquestGroup> rows = new ArrayList<>(3);
        for (int index = 0; index < individualRows; index++) {
            ConquestFrontSnapshot.GroupState group = ordered.get(index);
            String id = "battle-conquest-group-" + index;
            String intent = group.task() + (group.rallying() ? " · RALLY" : "");
            String target = group.targetKind() == null ? "NO TARGET" : switch (group.targetKind()) {
                case SECURE_COMPOUND -> "COMPOUND";
                case CLEAR_ZONE -> "OBJECTIVE AREA";
                case ADVANCE_TRACK, ATTACK_MOVE -> "ROUTE";
                case HOLD_NODE, DEFEND_AREA, DEFEND_TRACK, DEFEND_SITE -> "POSITION";
                default -> group.targetKind().name().replace('_', ' ');
            };
            rows.add(new ConquestGroup(id, group.identity().label(),
                    groupForce(front, group.squadIds()), intent, target,
                    "conquest-group" + (group.rallying() ? " conquest-group-rally" : "")));
        }
        if (individualRows < ordered.size()) {
            List<ConquestFrontSnapshot.GroupState> rest = ordered.subList(individualRows, ordered.size());
            long officers = rest.stream().filter(group -> group.identity().namedOfficer()).count();
            long ncos = rest.size() - officers;
            String label = officers > 0 ? officers + " OFFICERS + " + ncos + " NCO GROUPS"
                    : ncos + " ACTING NCO GROUPS";
            Map<String, Integer> tasks = new LinkedHashMap<>();
            for (String task : List.of("HOLD", "ASSAULT", "MIXED", "UNASSIGNED")) {
                int count = (int) rest.stream().filter(group -> task.equals(group.task())).count();
                if (count > 0) tasks.put(task, count);
            }
            String intent = String.join(" · ", tasks.entrySet().stream()
                    .map(entry -> entry.getValue() + " " + entry.getKey()).toList());
            long rallying = rest.stream().filter(ConquestFrontSnapshot.GroupState::rallying).count();
            rows.add(new ConquestGroup("battle-conquest-group-summary", label,
                    groupForce(front, rest.stream().flatMap(group -> group.squadIds().stream()).toList()),
                    intent, rallying > 0 ? rallying + " RALLYING" : "VARIED TARGETS",
                    "conquest-group conquest-group-summary"));
        }
        return List.copyOf(rows);
    }

    private static String groupForce(ConquestFrontSnapshot front, List<Integer> squadIds) {
        int live = 0;
        boolean complete = true;
        for (int squadId : squadIds) {
            ConquestFrontSnapshot.SquadState state = front.squadFor(squadId);
            if (state == null) complete = false;
            else live += state.aliveMembers();
        }
        return squadIds.size() + " SQ" + (complete ? " · " + live + " LIVE" : "");
    }

    private static ConquestLane projectLane(
            ConquestFrontSnapshot front, ConquestFrontSnapshot.TrackState track) {
        EnumMap<AssignmentKind, Integer> assignments = new EnumMap<>(AssignmentKind.class);
        for (ConquestFrontSnapshot.SquadDirective directive : front.directives()) {
            if (directive.effectiveTrack() == track.index()
                    && directive.assignmentKind() != null) {
                assignments.merge(directive.assignmentKind(), 1, Integer::sum);
            }
        }

        String intent = switch (front.phase()) {
            case KEEP_CONVERGENCE -> "KEEP ASSAULT";
            case FINAL_COMPOUND_CONVERGENCE -> "FINAL ASSAULT";
            default -> dominantIntent(assignments, track.effectiveSquads());
        };
        String status;
        String classes = "conquest-lane";
        if (track.effectiveSquads() == 0) {
            status = "NO FORCE";
            classes += " conquest-lane-empty";
        } else if (track.knownHostileContacts() > 0) {
            status = track.knownHostileContacts() + (track.knownHostileContacts() == 1
                    ? " CONTACT" : " CONTACTS");
            classes += " conquest-lane-contact";
        } else if (track.targetZoneId() >= 0) {
            status = "OBJECTIVE";
            classes += " conquest-lane-objective";
        } else {
            status = "ON LINE";
        }
        String laneName = switch (track.index()) {
            case 0 -> "ALPHA";
            case 1 -> "BRAVO";
            case 2 -> "CHARLIE";
            default -> "LANE " + (track.index() + 1);
        };
        String id = "battle-conquest-lane-" + track.index();
        return new ConquestLane(id, id + "-name", id + "-force", id + "-intent",
                id + "-status", laneName,
                track.effectiveSquads() + " SQ · " + track.effectiveLiveMembers(),
                intent, status, classes);
    }

    private static String dominantIntent(EnumMap<AssignmentKind, Integer> assignments,
                                         int effectiveSquads) {
        AssignmentKind best = null;
        int bestCount = 0;
        for (AssignmentKind kind : List.of(
                AssignmentKind.SECURE_COMPOUND, AssignmentKind.ATTACK_MOVE,
                AssignmentKind.ADVANCE_TRACK, AssignmentKind.CLEAR_ZONE,
                AssignmentKind.SUPPORT, AssignmentKind.HOLD_NODE)) {
            int count = assignments.getOrDefault(kind, 0);
            if (count > bestCount) {
                best = kind;
                bestCount = count;
            }
        }
        if (best == null) return effectiveSquads > 0 ? "HOLDING" : "UNASSIGNED";
        String label = switch (best) {
            case SECURE_COMPOUND -> "SECURE";
            case ATTACK_MOVE -> "PUSH";
            case ADVANCE_TRACK -> "ADVANCE";
            case CLEAR_ZONE -> "CLEAR";
            case SUPPORT -> "SUPPORT";
            case HOLD_NODE -> "HOLD";
            default -> best.name().replace('_', ' ');
        };
        return bestCount > 1 ? label + " x" + bestCount : label;
    }

    private static String phaseLabel(ConquestFrontSnapshot.Phase phase) {
        return switch (phase) {
            case LANE_ADVANCE -> "ADVANCING THE FRONT";
            case FRONT_ADJUST -> "SHIFTING SUPPORT";
            case KEEP_CONVERGENCE -> "CONVERGING ON KEEP";
            case FINAL_COMPOUND_CONVERGENCE -> "SECURING LAST COMPOUND";
        };
    }

    private void setSelectedSpeed(float speedMultiplier) {
        pauseClasses.set(speedMultiplier == PAUSED ? TIME_SELECTED_CLASSES : TIME_CLASSES);
        normalClasses.set(speedMultiplier == NORMAL ? TIME_SELECTED_CLASSES : TIME_CLASSES);
        doubleClasses.set(speedMultiplier == DOUBLE ? TIME_SELECTED_CLASSES : TIME_CLASSES);
        quadClasses.set(speedMultiplier == QUAD ? TIME_SELECTED_CLASSES : TIME_CLASSES);
        pauseStyle.set(speedMultiplier == PAUSED ? TIME_SELECTED_STYLE : TIME_STYLE);
        normalStyle.set(speedMultiplier == NORMAL ? TIME_SELECTED_STYLE : TIME_STYLE);
        doubleStyle.set(speedMultiplier == DOUBLE ? TIME_SELECTED_STYLE : TIME_STYLE);
        quadStyle.set(speedMultiplier == QUAD ? TIME_SELECTED_STYLE : TIME_STYLE);
    }

    private static String abbreviation(TacticalNode.Kind kind) {
        return switch (kind) {
            case COMMAND_POST -> "C";
            case BARRACKS -> "B";
            case ARMORY -> "A";
            default -> "O";
        };
    }

    private static String kindName(TacticalNode.Kind kind) {
        return switch (kind) {
            case COMMAND_POST -> "COMMAND POST";
            case BARRACKS -> "BARRACKS";
            case ARMORY -> "ARMORY";
            default -> kind.name().replace('_', ' ').toUpperCase(Locale.ROOT);
        };
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    record CaptureObjective(TacticalNode.Kind kind,
                            CompoundService.CompoundState state,
                            float progress) {
    }

    record Presentation(boolean objectivesVisible, boolean commandVisible, int missionRows) {
        Presentation(boolean objectivesVisible, boolean commandVisible) { this(objectivesVisible, commandVisible, 0); }
    }

    record ConquestGroup(String id, String label, String force, String intent,
                         String target, String classes) implements MarkupPropertySource {
        @Override public Object markupProperty(String name) {
            return switch (name) {
                case "id" -> id;
                case "nameId" -> id + "-name";
                case "forceId" -> id + "-force";
                case "intentId" -> id + "-intent";
                case "targetId" -> id + "-target";
                case "identityLineId" -> id + "-identity-line";
                case "taskLineId" -> id + "-task-line";
                case "label" -> label;
                case "force" -> force;
                case "intent" -> intent;
                case "target" -> target;
                case "classes" -> classes;
                default -> throw new IllegalArgumentException("Unknown Conquest group property: " + name);
            };
        }
    }

    record ConquestLane(String id, String nameId, String forceId, String intentId,
                        String statusId, String label, String force,
                        String intent, String status, String classes)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String name) {
            return switch (name) {
                case "id" -> id;
                case "nameId" -> nameId;
                case "forceId" -> forceId;
                case "intentId" -> intentId;
                case "statusId" -> statusId;
                case "label" -> label;
                case "force" -> force;
                case "intent" -> intent;
                case "status" -> status;
                case "classes" -> classes;
                default -> throw new IllegalArgumentException(
                        "Unknown Conquest lane property: " + name);
            };
        }
    }

    record ObjectiveChip(String id, String label, String classes)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String name) {
            return switch (name) {
                case "id" -> id;
                case "label" -> label;
                case "classes" -> classes;
                default -> throw new IllegalArgumentException("Unknown objective chip property: " + name);
            };
        }
    }
}
