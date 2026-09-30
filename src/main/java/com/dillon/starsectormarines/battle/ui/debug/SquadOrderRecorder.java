package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.decision.goap.SquadRouteGoalProvider;
import com.dillon.starsectormarines.battle.nav.NavigationService.SquadRouteDiagnostic;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.SharedGoalPolicy;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records what one squad was ordered to do across a window of frames and
 * tallies it, so "this squad is swapping orders back and forth several times
 * a second" becomes a number instead of an impression.
 *
 * <p>A single-frame dump cannot show churn: it reports one goal and one
 * action, and a squad flapping between two of them looks exactly like a squad
 * settled on one. This samples the same readouts the
 * {@link com.dillon.starsectormarines.battle.ui.panel.SquadPlanDebugPanel}
 * shows, once per frame for a fixed window, and reports per distinct value
 * how many frames held it, how many separate <em>runs</em> it was entered on,
 * and the longest unbroken run. Two values at 90 frames each with 45 runs
 * apiece is flapping; two values at 90 frames each with one run apiece is a
 * squad that changed its mind once.
 *
 * <p>Orders are recorded per {@link Layer} because the layers can disagree,
 * and which one is churning says where to look: a stable
 * {@link Layer#ASSIGNMENT} under a thrashing {@link Layer#GOAL} is the squad's
 * own planner re-deciding, while a thrashing assignment is the commander
 * re-tasking it faster than it can execute anything.
 *
 * <p>{@link Layer#MISSION} and {@link Layer#ASSIGNMENT} are separate rungs for
 * the same reason. A player order is a lease over the commander's directive
 * rather than a replacement of it, so the two disagree for as long as the order
 * stands — the mission is read off the shelf the lease put it on — and a
 * capture that reported only one side confidently named an order the squad was
 * not carrying out.
 *
 * <p>Sampling is per UI frame and deduplicated by {@code simTickIndex}, so a
 * paused battle contributes no samples and a frame that advanced several
 * ticks still contributes one. Frame counts are therefore a lower bound on
 * tick counts; the recorded tick span is what the rate figures divide by.
 *
 * <p>Not thread-safe and not intended to be: it is driven from the UI frame
 * loop only, reading the same volatile squad fields the panel already reads.
 */
@DebugOnly
public final class SquadOrderRecorder {

    /** Frames captured by a DUMP click — six seconds of 30Hz sim, enough for a flap to repeat many times. */
    public static final int DEFAULT_FRAMES = 180;

    /**
     * Transition-log ceiling. A pathological flap at one change per frame
     * would otherwise write a line per frame per layer; the tallies still
     * count every change, only the individual entries stop being listed.
     */
    private static final int MAX_TRANSITIONS = 400;

    /** Shown for a layer that has nothing to report this frame (no directive, no plan, no goal). */
    private static final String NONE = "—";

    /**
     * Marks an order the player issued directly. Without it an
     * {@code ATTACK_MOVE} the player clicked and an {@code ATTACK_MOVE} a
     * commander assigned read identically, which is the one distinction
     * somebody reading a dump to work out who pointed the squad needs.
     */
    public static final String PLAYER_ORDER_PREFIX = "player ";

    /**
     * One rung of the order stack. Ordered outermost (what command wants)
     * to innermost (what the squad is doing about it), so a dump reads
     * top-down from cause to effect.
     */
    public enum Layer {
        /** Ledger-owned command directive: who owns the squad and on what standing. */
        DIRECTIVE("directive"),
        /** The strategic task the mission commander assigned, whatever stands on top of it. */
        MISSION("mission"),
        /**
         * The tactical task the squad is actually carrying out — a live player
         * order when one stands, otherwise the mission assignment itself.
         * Marked {@code player } when the player issued it.
         */
        ASSIGNMENT("assignment"),
        /** Whether that task is actually executable right now. */
        EXECUTION("execution"),
        /** The GOAP goal the squad picked at its last replan. */
        GOAL("goal"),
        /** The plan step the squad is executing this frame. */
        ACTION("action"),
        /** Published contact doctrine and posture. */
        DOCTRINE("doctrine"),
        /** Composite of assignment, goal and action — the order as a whole. */
        ORDER("order");

        /** JSON key this layer is written under. */
        public final String key;

        Layer(String key) {
            this.key = key;
        }
    }

    /** How one distinct value of one layer fared across the window. */
    private static final class Tally {
        int frames;
        int runs;
        int longestRun;
    }

    /** Per-layer accumulation: the value tallies plus the run bookkeeping. */
    private static final class LayerState {
        final Map<String, Tally> tallies = new LinkedHashMap<>();
        String current;
        int currentRun;
        int changes;
    }

    /** One recorded change of one layer, in capture order. */
    private record Transition(int frame, int tick, Layer layer, String from, String to) {}

    private final int squadId;
    private final int requestedFrames;
    private final Map<Layer, LayerState> layers = new LinkedHashMap<>();
    private final List<Transition> transitions = new ArrayList<>();
    private record RouteSample(int tick, int goalX, int goalY, boolean enabled,
            float centroidX, float centroidY, int activePaths, int movingMembers,
            SquadRouteDiagnostic route) {}
    private final List<RouteSample> routeSamples = new ArrayList<>();

    private int frames;
    private int transitionsDropped;
    private int firstTick = -1;
    private int lastTick = -1;
    private int lastSampledTick = Integer.MIN_VALUE;
    private boolean squadLost;

    public SquadOrderRecorder(int squadId, int requestedFrames) {
        this.squadId = squadId;
        this.requestedFrames = Math.max(1, requestedFrames);
        for (Layer layer : Layer.values()) layers.put(layer, new LayerState());
    }

    /** Squad this recorder was started on. Selection may move on; the capture does not. */
    public int squadId() {
        return squadId;
    }

    /** Frames captured so far. */
    public int frames() {
        return frames;
    }

    /** Frames the capture was asked for. */
    public int requestedFrames() {
        return requestedFrames;
    }

    /** True once the window is full or the squad it was recording is gone. */
    public boolean isComplete() {
        return squadLost || frames >= requestedFrames;
    }

    /**
     * Ends the capture early because the squad no longer exists. Whatever was
     * recorded still writes — a squad wiped out mid-capture is often exactly
     * the case worth reading.
     */
    public void noteSquadLost() {
        squadLost = true;
    }

    /**
     * Captures one frame of the squad's orders. A frame on which the sim did
     * not advance is ignored, so a paused battle neither fills the window nor
     * inflates a value's run length.
     */
    public void sample(Squad squad, BattleSimulation sim) {
        if (squad == null || sim == null || isComplete() || sim.simTickIndex == lastSampledTick) return;
        captureRoute(squad, sim);
        Map<Layer, String> labels = new EnumMap<>(Layer.class);
        for (Layer layer : Layer.values()) labels.put(layer, label(layer, squad, sim));
        sample(sim.simTickIndex, labels);
    }

    private void captureRoute(Squad squad, BattleSimulation sim) {
        SquadPlan.Step step = squad.currentPlan == null ? null : squad.currentPlan.currentStep();
        if (step == null || !(step.action instanceof SquadRouteGoalProvider provider)) return;
        SquadRouteGoalProvider.Goal goal = provider.squadRouteGoal(squad, sim);
        if (goal == null) return;
        List<Long> members = step.allAssignedMembers().stream().distinct()
                .filter(member -> sim.resolveUnit(member) != 0L && !sim.isRiding(member)).toList();
        int[] starts = new int[members.size()];
        int activePaths = 0, moving = 0;
        for (int i = 0; i < members.size(); i++) {
            long member = members.get(i);
            starts[i] = sim.getGrid().index(sim.world().cellX(member), sim.world().cellY(member));
            if (sim.world().pathIdx(member) < Paths.cellCount(sim.world().path(member))) activePaths++;
            if (sim.movement().has(member) && !sim.movement().settled(member)) moving++;
        }
        routeSamples.add(new RouteSample(sim.simTickIndex, goal.x(), goal.y(),
                SharedGoalPolicy.usesSquadRouteCorridors(sim.liveUnitCount()),
                squad.centroidX, squad.centroidY, activePaths, moving,
                sim.inspectSquadRoute(squad.id, squad.routingEpoch, step, goal.x(), goal.y(), starts)));
    }

    /**
     * Tallying core, separated from the world reads so the run/change
     * bookkeeping can be exercised on synthetic label streams.
     */
    void sample(int tick, Map<Layer, String> labels) {
        if (isComplete() || tick == lastSampledTick) return;
        lastSampledTick = tick;
        if (firstTick < 0) firstTick = tick;
        lastTick = tick;
        frames++;
        for (Layer layer : Layer.values()) {
            String value = labels.get(layer);
            record(layer, value == null ? NONE : value, tick);
        }
    }

    private void record(Layer layer, String value, int tick) {
        LayerState state = layers.get(layer);
        Tally tally = state.tallies.computeIfAbsent(value, k -> new Tally());
        tally.frames++;
        if (value.equals(state.current)) {
            state.currentRun++;
        } else {
            if (state.current != null) {
                state.changes++;
                if (transitions.size() < MAX_TRANSITIONS) {
                    transitions.add(new Transition(frames, tick, layer, state.current, value));
                } else {
                    transitionsDropped++;
                }
            }
            tally.runs++;
            state.current = value;
            state.currentRun = 1;
        }
        if (state.currentRun > tally.longestRun) tally.longestRun = state.currentRun;
    }

    // ------------------------------------------------------------------
    // Labels. These mirror what the selected-squad panel puts on screen so
    // a tally line and a HUD line describe the same thing.
    // ------------------------------------------------------------------

    private static String label(Layer layer, Squad squad, BattleSimulation sim) {
        return switch (layer) {
            case DIRECTIVE -> directiveLabel(sim.getSquadCommandDirective(squad.id));
            case MISSION -> assignmentLabel(missionAssignment(squad, sim));
            case ASSIGNMENT -> executingAssignmentLabel(squad);
            case EXECUTION -> executionLabel(squad);
            case GOAL -> goalLabel(squad);
            case ACTION -> actionLabel(squad);
            case DOCTRINE -> doctrineLabel(squad.contactPicture);
            case ORDER -> executingAssignmentLabel(squad)
                    + " » " + goalLabel(squad)
                    + " » " + actionLabel(squad);
        };
    }

    private static String directiveLabel(CommandDirective directive) {
        if (directive == null) return NONE;
        return directive.status().name() + " " + directive.issuer()
                + "/" + directive.authority().name();
    }

    /**
     * The order the squad is actually carrying out, marked when the player's
     * lease is what wrote it. The commander's directive underneath stays
     * readable in {@link Layer#MISSION}.
     *
     * <p>Deliberately the order the squad <em>holds</em> rather than
     * {@code assignmentForExecution()}, which form-up masks to null: a squad
     * still assembling has an order, and whether it can act on it yet is what
     * {@link Layer#EXECUTION} is for.
     */
    static String executingAssignmentLabel(Squad squad) {
        return squad.underPlayerOrder()
                ? PLAYER_ORDER_PREFIX + assignmentLabel(squad.assignedObjective)
                : assignmentLabel(squad.assignedObjective);
    }

    /**
     * The commander's standing assignment, whatever the squad is carrying out.
     * While a lease stands the squad's own field holds the player's order, so
     * the mission comes off the shelf the lease put it on.
     */
    static ObjectiveAssignment missionAssignment(Squad squad, BattleSimulation sim) {
        CommandDirective shelved = sim.getShelvedSquadDirective(squad.id);
        return shelved != null ? shelved.assignment() : squad.assignedObjective;
    }

    /** Same shape the panel's Assignment row draws: kind plus whichever target slots the kind populates. */
    public static String assignmentLabel(ObjectiveAssignment assignment) {
        if (assignment == null) return NONE;
        StringBuilder sb = new StringBuilder(assignment.kind().name());
        if (assignment.targetZoneId() >= 0) sb.append(" zone:").append(assignment.targetZoneId());
        if (assignment.targetNode() != null) sb.append(" node");
        if (assignment.objectiveId() >= 0) sb.append(" obj:").append(assignment.objectiveId());
        if (assignment.targetCellX() >= 0 && assignment.targetCellY() >= 0) {
            sb.append(" cell:").append(assignment.targetCellX())
                    .append(',').append(assignment.targetCellY());
        }
        return sb.toString();
    }

    private static String executionLabel(Squad squad) {
        String suspension = squad.assignmentExecutionSuspension();
        if (suspension != null) return "SUSPENDED:" + suspension;
        return squad.assignmentForExecution() != null ? "READY" : "UNASSIGNED";
    }

    private static String goalLabel(Squad squad) {
        if (squad.currentGoal == null) return NONE;
        return squad.currentGoal.name() + " [" + squad.currentGoal.priority().name() + "]";
    }

    private static String actionLabel(Squad squad) {
        SquadPlan plan = squad.currentPlan;
        if (plan == null) return NONE;
        SquadPlan.Step step = plan.currentStep();
        if (step == null) return "(plan complete)";
        return step.action.name();
    }

    private static String doctrineLabel(SquadContactPicture picture) {
        if (picture == null) return NONE;
        return picture.doctrine().name() + "/" + picture.posture().name();
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    /**
     * The capture as JSON, for embedding in the squad dump. Values within a
     * layer are written most-held first so the dominant order reads first and
     * the flap partner reads second.
     */
    public JSONObject toJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("squadId", squadId);
        root.put("requestedFrames", requestedFrames);
        root.put("frames", frames);
        root.put("firstTick", firstTick);
        root.put("lastTick", lastTick);
        int tickSpan = (firstTick < 0) ? 0 : (lastTick - firstTick);
        root.put("tickSpan", tickSpan);
        float seconds = tickSpan * BattleSimulation.TICK_DT;
        root.put("simSeconds", seconds);
        root.put("endedEarly", squadLost);
        root.put("note", "One sample per UI frame, deduplicated by sim tick; "
                + "a value's runs count is how many separate times it was entered.");
        JSONObject layerJson = new JSONObject();
        for (Layer layer : Layer.values()) {
            layerJson.put(layer.key, buildLayerJson(layers.get(layer), seconds));
        }
        root.put("layers", layerJson);
        root.put("transitions", buildTransitionsJson());
        root.put("transitionsDropped", transitionsDropped);
        JSONArray routes = new JSONArray();
        for (RouteSample sample : routeSamples) {
            JSONObject route = SquadStateDumper.buildRouteDiagnosticJson(sample.route);
            route.put("tick", sample.tick);
            route.put("goalX", sample.goalX);
            route.put("goalY", sample.goalY);
            route.put("enabled", sample.enabled);
            route.put("centroidX", sample.centroidX);
            route.put("centroidY", sample.centroidY);
            route.put("activePathMembers", sample.activePaths);
            route.put("movingMembers", sample.movingMembers);
            routes.put(route);
        }
        root.put("routeSamples", routes);
        return root;
    }

    private static JSONObject buildLayerJson(LayerState state, float seconds) throws Exception {
        JSONObject o = new JSONObject();
        o.put("distinctValues", state.tallies.size());
        o.put("changes", state.changes);
        o.put("changesPerSecond", seconds > 0f ? state.changes / seconds : 0f);
        List<Map.Entry<String, Tally>> ordered = new ArrayList<>(state.tallies.entrySet());
        ordered.sort((a, b) -> Integer.compare(b.getValue().frames, a.getValue().frames));
        JSONArray values = new JSONArray();
        for (Map.Entry<String, Tally> e : ordered) {
            JSONObject v = new JSONObject();
            v.put("value", e.getKey());
            v.put("frames", e.getValue().frames);
            v.put("runs", e.getValue().runs);
            v.put("longestRun", e.getValue().longestRun);
            values.put(v);
        }
        o.put("values", values);
        return o;
    }

    private JSONArray buildTransitionsJson() throws Exception {
        JSONArray out = new JSONArray();
        for (Transition t : transitions) {
            JSONObject o = new JSONObject();
            o.put("frame", t.frame());
            o.put("tick", t.tick());
            o.put("layer", t.layer().key);
            o.put("from", t.from());
            o.put("to", t.to());
            out.put(o);
        }
        return out;
    }

    /** One-line progress string for the panel banner while the window fills. */
    public String progressLabel() {
        return "(recording orders " + frames + "/" + requestedFrames + ")";
    }
}
