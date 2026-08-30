package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.Collection;
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

    private final MutableSignal<String> pauseClasses;
    private final MutableSignal<String> normalClasses;
    private final MutableSignal<String> doubleClasses;
    private final MutableSignal<String> quadClasses;
    private final MutableSignal<String> pauseStyle;
    private final MutableSignal<String> normalStyle;
    private final MutableSignal<String> doubleStyle;
    private final MutableSignal<String> quadStyle;
    private final MutableSignal<String> objectivePanelClasses;
    private final MutableSignal<String> objectiveScore;
    private final MutableSignal<String> objectiveTally;
    private final MutableSignal<List<ObjectiveChip>> objectiveChips;
    private final MutableSignal<String> objectiveFocusClasses;
    private final MutableSignal<String> objectiveFocus;
    private final MutableSignal<String> objectiveProgressStyle;
    private final MutableSignal<String> commandPanelClasses;
    private final MutableSignal<String> commandPhase;
    private final MutableSignal<String> commandForce;
    private final MutableSignal<List<ConquestLane>> conquestLanes;

    private final Consumer<Float> speedSetter;
    private final String[] speedLabels;

    BattleHudOverlayModel(Reactor reactor, Consumer<Float> speedSetter,
                          String pauseLabel, String normalLabel,
                          String doubleLabel, String quadLabel) {
        this.speedSetter = speedSetter;
        this.speedLabels = new String[]{pauseLabel, normalLabel, doubleLabel, quadLabel};
        pauseClasses = reactor.signal(TIME_CLASSES);
        normalClasses = reactor.signal(TIME_SELECTED_CLASSES);
        doubleClasses = reactor.signal(TIME_CLASSES);
        quadClasses = reactor.signal(TIME_CLASSES);
        pauseStyle = reactor.signal(TIME_STYLE);
        normalStyle = reactor.signal(TIME_SELECTED_STYLE);
        doubleStyle = reactor.signal(TIME_STYLE);
        quadStyle = reactor.signal(TIME_STYLE);
        objectivePanelClasses = reactor.signal(OBJECTIVE_PANEL_HIDDEN);
        objectiveScore = reactor.signal("");
        objectiveTally = reactor.signal("");
        objectiveChips = reactor.signal(List.of());
        objectiveFocusClasses = reactor.signal(OBJECTIVE_FOCUS_HIDDEN);
        objectiveFocus = reactor.signal("");
        objectiveProgressStyle = reactor.signal("width: 0%;");
        commandPanelClasses = reactor.signal(COMMAND_PANEL_HIDDEN);
        commandPhase = reactor.signal("");
        commandForce = reactor.signal("");
        conquestLanes = reactor.signal(List.of());
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
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
        props.put("objectiveScore", objectiveScore);
        props.put("objectiveTally", objectiveTally);
        props.put("objectiveChips", objectiveChips);
        props.put("objectiveFocusClasses", objectiveFocusClasses);
        props.put("objectiveFocus", objectiveFocus);
        props.put("objectiveProgressStyle", objectiveProgressStyle);
        props.put("commandPanelClasses", commandPanelClasses);
        props.put("commandPhase", commandPhase);
        props.put("commandForce", commandForce);
        props.put("conquestLanes", conquestLanes);
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

    private boolean updateConquestCommand(CommanderSnapshot<?> commander) {
        if (commander == null || commander.perspective() != Faction.MARINE
                || !(commander.detail() instanceof ConquestFrontSnapshot front)
                || front.perspective() != Faction.MARINE) {
            commandPanelClasses.set(COMMAND_PANEL_HIDDEN);
            commandPhase.set("");
            commandForce.set("");
            conquestLanes.set(List.of());
            return false;
        }

        commandPanelClasses.set(COMMAND_PANEL);
        commandPhase.set(phaseLabel(front.phase()));
        commandForce.set(commander.commandPoolSize() + " SQUADS  ·  "
                + commander.reserveCount() + " RESERVE");
        List<ConquestLane> lanes = new ArrayList<>(front.tracks().size());
        front.tracks().stream()
                .sorted((left, right) -> Integer.compare(left.index(), right.index()))
                .forEach(track -> lanes.add(projectLane(front, track)));
        conquestLanes.set(List.copyOf(lanes));
        return true;
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

    record Presentation(boolean objectivesVisible, boolean commandVisible) {
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
