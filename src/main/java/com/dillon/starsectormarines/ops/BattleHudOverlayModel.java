package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.setup.ColdAccountIntervention.Phase;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.LiftQueueReadout;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Live, render-free projection for the MLX-authored battle command overlay: the time control
 * and objectives on the right, and the lift queue on the left ({@code ui-nouns.md}).
 */
final class BattleHudOverlayModel {

    static final float PAUSED = 0f;
    static final float NORMAL = 1f;
    static final float DOUBLE = 2f;
    static final float QUAD = 4f;

    private static final String TIME_CLASSES = "time-button";
    private static final String TIME_SELECTED_CLASSES = "time-button time-selected";
    private static final String TIME_STYLE = "";
    private static final String TIME_SELECTED_STYLE =
            "background-color: #d6c27a24; border-color: #d6c27a99; color: #ffd464;";
    private static final String OBJECTIVE_PANEL = "objective-panel";
    private static final String OBJECTIVE_PANEL_HIDDEN =
            "objective-panel objective-panel-hidden";
    private static final String OBJECTIVE_FOCUS = "objective-focus";
    private static final String OBJECTIVE_FOCUS_HIDDEN =
            "objective-focus objective-focus-hidden";
    private static final String OPTIONAL_HEADING = "label heading optional-heading";
    private static final String OPTIONAL_HEADING_HIDDEN = "label heading optional-heading optional-hidden";
    private static final String OPTIONAL_LIST_HIDDEN = "display: none;";
    /** The Optional section's heading and each of its rows, card and gap, in document pixels. */
    static final float OPTIONAL_HEADING_HEIGHT = 29f, OPTIONAL_ROW_HEIGHT = 66f;
    private static final String LIFT_PANEL = "lift-queue-panel";
    private static final String LIFT_PANEL_HIDDEN = "lift-queue-panel lift-queue-hidden";
    /** Rows the lift queue shows before folding the rest into a count; keeps it in its bounds. */
    static final int MAX_LIFT_ROWS = 5;

    private final MutableSignal<String> missionClasses;
    private final MutableSignal<List<MissionCard>> missionCards;
    private final MutableSignal<String> missionPanelStyle;
    private final MutableSignal<String> missionListStyle;
    private final MutableSignal<String> missionScore;
    private final MutableSignal<List<MissionCard>> optionalCards;
    private final MutableSignal<String> optionalHeadingClasses;
    private final MutableSignal<String> optionalListStyle;
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
    private final MutableSignal<String> liftClasses;
    private final MutableSignal<String> liftStyle;
    private final MutableSignal<String> liftSummary;
    private final MutableSignal<List<LiftRow>> liftRows;
    private final Map<String, Runnable> liftActions = new LinkedHashMap<>();

    private final Consumer<Float> speedSetter;
    private final String[] speedLabels;

    BattleHudOverlayModel(Reactor reactor, Consumer<Float> speedSetter,
                          String pauseLabel, String normalLabel,
                          String doubleLabel, String quadLabel) {
        this.speedSetter = speedSetter;
        this.speedLabels = new String[]{pauseLabel, normalLabel, doubleLabel, quadLabel};
        missionClasses = reactor.signal("mission-panel mission-hidden");
        missionCards = reactor.signal(List.of());
        missionPanelStyle = reactor.signal("");
        missionListStyle = reactor.signal("");
        missionScore = reactor.signal("");
        optionalCards = reactor.signal(List.of());
        optionalHeadingClasses = reactor.signal(OPTIONAL_HEADING_HIDDEN);
        optionalListStyle = reactor.signal(OPTIONAL_LIST_HIDDEN);
        pauseClasses = reactor.signal(TIME_CLASSES);
        normalClasses = reactor.signal(TIME_SELECTED_CLASSES);
        doubleClasses = reactor.signal(TIME_CLASSES);
        quadClasses = reactor.signal(TIME_CLASSES);
        pauseStyle = reactor.signal(TIME_STYLE);
        normalStyle = reactor.signal(TIME_SELECTED_STYLE);
        doubleStyle = reactor.signal(TIME_STYLE);
        quadStyle = reactor.signal(TIME_STYLE);
        objectivePanelClasses = reactor.signal(OBJECTIVE_PANEL_HIDDEN);
        objectiveTitle = reactor.signal(Strings.get("battleCapture"));
        objectiveScore = reactor.signal("");
        objectiveTally = reactor.signal("");
        objectiveChips = reactor.signal(List.of());
        objectiveFocusClasses = reactor.signal(OBJECTIVE_FOCUS_HIDDEN);
        objectiveFocus = reactor.signal("");
        objectiveProgressStyle = reactor.signal("width: 0%;");
        objectiveLocateClasses = reactor.signal("objective-locate objective-locate-hidden");
        objectiveLocateDisabled = reactor.signal(true);
        liftClasses = reactor.signal(LIFT_PANEL_HIDDEN);
        liftStyle = reactor.signal("");
        liftSummary = reactor.signal("");
        liftRows = reactor.signal(List.of());
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("missionClasses", missionClasses);
        props.put("missionCards", missionCards);
        props.put("missionPanelStyle", missionPanelStyle);
        props.put("missionListStyle", missionListStyle);
        props.put("missionScore", missionScore);
        props.put("optionalCards", optionalCards);
        props.put("optionalHeadingClasses", optionalHeadingClasses);
        props.put("optionalListStyle", optionalListStyle);
        props.put("timeLabel", Strings.get("battleTime"));
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
        props.put("liftClasses", liftClasses);
        props.put("liftStyle", liftStyle);
        props.put("liftSummary", liftSummary);
        props.put("liftRows", liftRows);
        return props;
    }

    Presentation update(float speedMultiplier,
                        Collection<CompoundService.Record> records) {
        List<CaptureObjective> objectives = new ArrayList<>();
        if (records != null) {
            for (CompoundService.Record record : records) {
                objectives.add(new CaptureObjective(record.node.kind,
                        record.state, record.captureProgress));
            }
        }
        return updateProjected(speedMultiplier, objectives);
    }

    Presentation updateProjected(float speedMultiplier,
                                 List<CaptureObjective> objectives) {
        missionClasses.set("mission-panel mission-hidden");
        missionCards.set(List.of());
        optionalCards.set(List.of());
        optionalHeadingClasses.set(OPTIONAL_HEADING_HIDDEN);
        optionalListStyle.set(OPTIONAL_LIST_HIDDEN);
        missionPanelStyle.set("");
        missionListStyle.set("");
        objectiveTitle.set(Strings.get("battleCapture"));
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
            objectiveTally.set(Strings.format("battleHudObjectiveTally", secured, contested, hostile));
            objectiveChips.set(List.copyOf(chips));
            if (focus == null) {
                objectiveFocusClasses.set(OBJECTIVE_FOCUS_HIDDEN);
                objectiveFocus.set("");
                objectiveProgressStyle.set("width: 0%;");
            } else {
                int percent = Math.round(focusProgress * 100f);
                objectiveFocusClasses.set(OBJECTIVE_FOCUS);
                objectiveFocus.set(Strings.format("battleObjectiveFocus", kindName(focus.kind()), focusIndex, percent));
                objectiveProgressStyle.set("width: " + percent + "%;");
            }
        }
        return new Presentation(visible);
    }

    Presentation updateMission(MissionObjectiveSnapshot snapshot, Consumer<MissionObjectiveSnapshot.Marker> locate) {
        if (snapshot.objectives().isEmpty()) return new Presentation(false);
        objectivePanelClasses.set(OBJECTIVE_PANEL_HIDDEN);
        missionClasses.set("mission-panel");
        var required = snapshot.required();
        var optional = snapshot.optional();
        int count = Math.min(2, required.size());
        float optionalHeight = optionalHeight(optional.size());
        missionPanelStyle.set("height: " + (33 + count * 174 + Math.round(optionalHeight)) + "px;");
        missionListStyle.set("height: " + (count * 174) + "px;");
        // The Optional section has its own list, so it stays in view however the mission's rows scroll.
        optionalListStyle.set(optional.isEmpty() ? OPTIONAL_LIST_HIDDEN : "height: " + Math.round(optionalHeight - 6) + "px;");
        // Side objectives never count toward the tally.
        missionScore.set(required.stream().filter(i -> i.state() == MissionObjectiveSnapshot.State.COMPLETE).count()
                + " / " + required.size());
        missionCards.set(required.stream().map(i -> new MissionCard(i, () -> {
            if (i.focus() != null) locate.accept(i.focus());
        })).toList());
        optionalHeadingClasses.set(optional.isEmpty() ? OPTIONAL_HEADING_HIDDEN : OPTIONAL_HEADING);
        optionalCards.set(optional.stream().map(i -> new MissionCard(i, () -> {
            if (i.focus() != null) locate.accept(i.focus());
        })).toList());
        return new Presentation(true, count, 0, optional.size());
    }

    /** Height, in document pixels, of the Optional section with {@code rows} rows; 0 without any. */
    static float optionalHeight(int rows) {
        return rows == 0 ? 0f : OPTIONAL_HEADING_HEIGHT + rows * OPTIONAL_ROW_HEIGHT;
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
                case "classes" -> (item.optional() ? "mission-card mission-optional " : "mission-card ")
                        + (item.state() == MissionObjectiveSnapshot.State.COMPLETE ? "mission-complete"
                        : item.state() == MissionObjectiveSnapshot.State.FAILED ? "mission-failed" : "mission-active");
                // A lapsed or failed side objective stays listed, struck, until the battle ends.
                case "strikeClasses" -> item.optional() && item.state() == MissionObjectiveSnapshot.State.FAILED
                        ? "optional-strike" : "optional-strike optional-hidden";
                case "objClasses" -> "obj " + switch (item.state()) {
                    case COMPLETE -> "obj-complete";
                    case FAILED -> "obj-failed";
                    default -> "obj-active";
                };
                case "progressRowClasses" -> item.progress() < 0 && item.progressText().isEmpty()
                        ? "obj-progress obj-hidden" : "obj-progress";
                case "blockerClasses" -> item.blocker().isEmpty() ? "obj-blocker obj-hidden" : "obj-blocker";
                case "progressStyle" -> "width: " + Math.round(Math.max(0, Math.min(1, item.progress())) * 100) + "%;";
                case "trackStyle" -> item.progress() < 0 ? "display: none;" : "";
                case "locate" -> locate;
                case "disabled" -> item.focus() == null;
                default -> {
                    if (key.endsWith("Id")) yield "mission-" + item.id() + "-" + key.substring(0, key.length() - 2);
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
        objectiveTitle.set(Strings.get("battleRaidTitle"));
        objectivePanelClasses.set(OBJECTIVE_PANEL);
        objectiveScore.set(Strings.format("battleRaidScore", complete ? 2 : secured ? 1 : 0));
        objectiveChips.set(List.of(
                new ObjectiveChip("battle-raid-seize", Strings.get(secured ? "battleRaidSeizeDone" : "battleRaidSeizeActive"),
                        "objective-chip objective-raid " + (secured ? "objective-secured" : "objective-contested")),
                new ObjectiveChip("battle-raid-return", Strings.get(complete ? "battleRaidReturnDone" : secured ? "battleRaidReturnActive" : "battleRaidReturnNext"),
                        "objective-chip objective-raid " + (complete ? "objective-secured" : secured ? "objective-contested" : "objective-pending"))));
        objectiveTally.set(secured ? Strings.format("battleRaidTallySecured", targetName) : targetName);
        objectiveFocusClasses.set(OBJECTIVE_FOCUS);
        objectiveFocus.set(switch (phase) {
            case APPROACH -> Strings.format("battleRaidApproach", Math.round(duration));
            case SERVICE -> Strings.format("battleRaidService", Math.round(clamp01(progress / duration) * 100));
            case EGRESS -> Strings.get("battleRaidEgress");
            case COMPLETE -> Strings.get("battleRaidComplete");
        });
        objectiveProgressStyle.set("width: " + Math.round(100 * (secured ? 1f : clamp01(progress / duration))) + "%;");
        objectiveLocateClasses.set("objective-locate");
        objectiveLocateDisabled.set(false);
        locateObjective = locate;
        return new Presentation(true);
    }

    Presentation updateIntervention(Phase phase, String instruction, int remainingWalls, int initialWalls,
                                    boolean companyRecovered) {
        objectiveTitle.set(phase == Phase.EVIDENCE ? Strings.get("battleInterventionTitle") : Strings.format("battleInterventionPhaseTitle", Strings.named("battleInterventionPhase", phase.name())));
        objectivePanelClasses.set(OBJECTIVE_PANEL);
        objectiveScore.set("");
        objectiveTally.set(switch (phase) {
            case EVIDENCE -> Strings.get("battleInterventionEvidence");
            case WITHDRAW, WARNING, BOMBARD -> Strings.get("battleInterventionClear");
            case PATROL, AFTERMATH -> Strings.get(companyRecovered ? "battleInterventionRecovered" : "battleInterventionSilenced");
            case COMPLETE -> Strings.get("battleInterventionRecovered");
            case FAILED -> Strings.get("battleInterventionFailed");
        });
        objectiveChips.set(List.of());
        objectiveFocusClasses.set(OBJECTIVE_FOCUS);
        objectiveFocus.set(instruction);
        objectiveProgressStyle.set("width: " + Math.round(100f * (initialWalls - remainingWalls) / Math.max(1, initialWalls)) + "%;");
        return new Presentation(true);
    }

    /** Read-only convoy report; a captured site is a release condition, never a delivery. */
    void updateFreight(int phase, List<Integer> identities, List<String> cargo, int delivered, int lost) {
        objectiveTitle.set(Strings.get(phase == 3 ? "battleFreightThunderRun" : "battleFreightColumn"));
        objectivePanelClasses.set(OBJECTIVE_PANEL);
        objectiveScore.set(Strings.format("battleFreightScore", delivered, cargo.size()));
        List<ObjectiveChip> chips = new ArrayList<>();
        for (int i = 0; i < cargo.size(); i++) chips.add(new ObjectiveChip("battle-cargo-" + i,
                Strings.format("battleFreightChip", identities.get(i) + 1, Strings.get(cargo.get(i).equals("DELIVERED") ? "battleFreightOut" : cargo.get(i).equals("LOST") ? "battleFreightLost" : cargo.get(i).equals("WAITING") ? "battleFreightWait" : "battleFreightRoad")), "objective-chip objective-cargo " + (cargo.get(i).equals("DELIVERED") ? "objective-secured"
                : cargo.get(i).equals("LOST") ? "objective-hostile" : "objective-contested")));
        objectiveChips.set(List.copyOf(chips));
        objectiveTally.set(Strings.format("battleFreightTally", delivered, lost));
        objectiveFocusClasses.set(OBJECTIVE_FOCUS);
        objectiveFocus.set(switch (phase) {
            case 0 -> Strings.get("battleFreightDispatch");
            case 1 -> Strings.get("battleFreightCrossing");
            case 2 -> Strings.get("battleFreightGate");
            default -> Strings.get("battleFreightExit");
        });
        objectiveProgressStyle.set("width: " + Math.round(100f * (delivered + lost) / Math.max(1, cargo.size())) + "%;");
    }

    void updateFreightHold(int site, int ticks) {
        if (site <= 0) return;
        objectiveTitle.set(Strings.get("battleFreightHoldTitle"));
        objectiveFocus.set(Strings.format(site == 1 ? "battleFreightHoldDispatch" : "battleFreightHoldCrossing", (ticks + 29) / 30));
    }

    /**
     * A placed armor drop the sim has not committed yet (the picker pauses the battle), with
     * the machine it names.
     */
    record Placed(String powerId, int manifestIndex, ArmorLoad load) {}

    /**
     * Projects the lift queue: ferry craft in the air and what each carries, then what is
     * waiting, each queued or placed drop with an X that cancels it. Returns the rows shown.
     *
     * @param cancelQueued   asks the sim to cancel a queued drop by its job id
     * @param withdrawPlaced takes back a placed drop not yet committed
     */
    int updateLiftQueue(LiftQueueReadout.Reading reading, List<Placed> placed,
                        LongConsumer cancelQueued, Consumer<Placed> withdrawPlaced) {
        List<LiftRow> rows = liftRows(reading, placed, cancelQueued, withdrawPlaced);
        int waiting = reading.waiting().size() + placed.size();
        boolean visible = !rows.isEmpty();
        liftClasses.set(visible ? LIFT_PANEL : LIFT_PANEL_HIDDEN);
        liftStyle.set(visible ? "height: " + Math.round(BattleHudOverlay.liftContentHeight(rows.size())) + "px;" : "");
        liftSummary.set(visible ? Strings.format("battleLiftQueueSummary", reading.flights().size(), waiting) : "");
        liftRows.set(List.copyOf(rows));
        return rows.size();
    }

    /** The pure projection behind {@link #updateLiftQueue}: at most {@link #MAX_LIFT_ROWS} rows. */
    List<LiftRow> liftRows(LiftQueueReadout.Reading reading, List<Placed> placed,
                           LongConsumer cancelQueued, Consumer<Placed> withdrawPlaced) {
        List<LiftRow> all = new ArrayList<>();
        for (LiftQueueReadout.Flight flight : reading.flights()) {
            String craft = craftName(flight);
            String name = flight.cargo() == LiftQueueReadout.Cargo.MACHINE
                    ? Strings.format("battleLiftMachineAboard", craft, machineName(flight.machine()))
                    : Strings.format("battleLiftSquadAboard", craft, flight.marines());
            all.add(new LiftRow("battle-lift-air-" + flight.craftId(), Strings.get("battleLiftKindAir"), name,
                    flight.onPad() ? Strings.get("battleLiftOnPad") : eta(flight.secondsToTouchdown()),
                    "lift-row lift-air", false, NO_ACTION));
        }
        for (LiftQueueReadout.Waiting waiting : reading.waiting()) {
            if (waiting.cargo() == LiftQueueReadout.Cargo.SQUAD) {
                all.add(new LiftRow("battle-lift-squads", Strings.get("battleLiftKindNext"),
                        Strings.format("battleLiftSquadLoads", waiting.loads()), eta(waiting.eta()),
                        "lift-row lift-squads", false, NO_ACTION));
                continue;
            }
            long job = waiting.drop().id();
            String id = "battle-lift-drop-" + job;
            all.add(new LiftRow(id, Strings.get("battleLiftKindDrop"), machineName(waiting.drop().load()),
                    eta(waiting.eta()), "lift-row lift-drop", true,
                    liftActions.computeIfAbsent(id, key -> () -> cancelQueued.accept(job))));
        }
        for (Placed drop : placed) {
            String id = "battle-lift-placed-" + drop.powerId() + "-" + drop.manifestIndex();
            all.add(new LiftRow(id, Strings.get("battleLiftKindDrop"), machineName(drop.load()),
                    Strings.get("battleLiftPlaced"), "lift-row lift-drop", true,
                    liftActions.computeIfAbsent(id, key -> () -> withdrawPlaced.accept(drop))));
        }
        if (all.size() <= MAX_LIFT_ROWS) return all;
        List<LiftRow> shown = new ArrayList<>(all.subList(0, MAX_LIFT_ROWS - 1));
        shown.add(new LiftRow("battle-lift-more", "", Strings.format("battleLiftQueueMore",
                all.size() - shown.size()), "", "lift-row lift-more", false, NO_ACTION));
        return shown;
    }

    private static final Runnable NO_ACTION = () -> { };

    private static String craftName(LiftQueueReadout.Flight flight) {
        ShuttleType pattern = ShuttleType.fromAirframe(flight.frame());
        return pattern == null ? "" : Strings.upper(pattern.displayName());
    }

    /** A machine as the picker names it: type, and callsign when it has one. */
    static String machineName(ArmorLoad load) {
        if (load == null) return "";
        BayManifest.Entry entry = new BayManifest.Entry(0, load);
        String callsign = entry.callsign();
        String type = Strings.upper(entry.typeName());
        return callsign == null || callsign.isBlank() ? type
                : Strings.format("battleLiftMachineNamed", type, Strings.upper(callsign));
    }

    /** Seconds to touchdown as the queue shows them; unknown when no live craft can fly it. */
    static String eta(float seconds) {
        if (Float.isNaN(seconds)) return Strings.get("battleLiftNoCraft");
        return Strings.format("battleLiftEta", Math.max(0, Math.round(seconds)));
    }

    /** One lift-queue row; {@code cancellable} rows carry an X. */
    record LiftRow(String id, String kind, String name, String eta, String classes,
                   boolean cancellable, Runnable cancel) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> id;
                case "kindId" -> id + "-kind";
                case "nameId" -> id + "-name";
                case "etaId" -> id + "-eta";
                case "cancelId" -> id + "-cancel";
                case "kind" -> kind;
                case "name" -> name;
                case "eta" -> eta;
                case "classes" -> classes;
                case "cancelClasses" -> cancellable ? "lift-cancel" : "lift-cancel lift-cancel-hidden";
                case "cancelDisabled" -> !cancellable;
                case "cancel" -> cancel;
                default -> throw new IllegalArgumentException("Unknown lift row property: " + key);
            };
        }
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
        return Strings.upper(Strings.named("tacticalKind", kind.name()));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    record CaptureObjective(TacticalNode.Kind kind,
                            CompoundService.CompoundState state,
                            float progress) {
    }

    /**
     * What the overlay shows, which sizes its document.
     *
     * @param missionRows mission objective cards on the right rail
     * @param liftRows    rows the lift queue shows on the left, 0 when it is hidden
     * @param optionalRows side objectives in the rail's Optional section
     */
    record Presentation(boolean objectivesVisible, int missionRows, int liftRows, int optionalRows) {
        Presentation(boolean objectivesVisible) { this(objectivesVisible, 0, 0, 0); }

        Presentation(boolean objectivesVisible, int missionRows) { this(objectivesVisible, missionRows, 0, 0); }

        Presentation(boolean objectivesVisible, int missionRows, int liftRows) { this(objectivesVisible, missionRows, liftRows, 0); }

        Presentation withLiftRows(int rows) { return new Presentation(objectivesVisible, missionRows, rows, optionalRows); }
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
