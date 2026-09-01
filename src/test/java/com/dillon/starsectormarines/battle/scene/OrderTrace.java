package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.ui.debug.SquadOrderRecorder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What every tracked squad was ordered, was planning, and was doing, sampled
 * once per tick — the headless sibling of
 * {@link SquadOrderRecorder}, which asks the same question of a live UI frame
 * loop.
 *
 * <p>A scene's findings are about <em>when</em> something happened: "it handed
 * the order back within a tick of arriving", "it held that order through
 * contact", "it never lost its plan". None of those can be read off an end-state
 * assertion, and all of them are one predicate over a per-tick stream. So the
 * trace keeps the stream and the {@link Verdicts} read it, rather than each
 * scene growing its own counters.
 *
 * <p>The layers are kept apart for the reason the recorder keeps them apart: a
 * stable {@link Sample#mission} under a thrashing {@link Sample#goal} is the
 * squad's own planner re-deciding, while a thrashing mission is the commander
 * re-tasking it faster than it can execute anything. A scene that recorded only
 * one of them would confidently name an order the squad was not carrying out.
 *
 * <p>Not thread-safe: driven from a scene's own single-threaded run loop.
 */
public final class OrderTrace implements TickObserver {

    /** Shown for a layer with nothing to report this tick, matching the recorder's readouts. */
    private static final String NONE = "—";

    /**
     * One tick of one squad's order stack.
     *
     * <p>A squad the simulation no longer has still contributes a sample, with
     * {@code alive == 0} and every label empty — an absent squad is a finding
     * (it was wiped out, it was disbanded) and a gap in the stream would hide
     * the tick it stopped existing on.
     *
     * @param mission      the commander's standing assignment, whatever stands on top of it
     * @param executing    the order the squad actually holds, prefixed
     *                     {@link SquadOrderRecorder#PLAYER_ORDER_PREFIX} when the player issued it
     * @param suspension   why the order cannot be executed yet, or {@code ""} when it can
     * @param goal         the GOAP goal picked at the last replan
     * @param goalPriority that goal's {@link Goal.Priority} bucket, or {@code ""} when there is no goal
     * @param action       the plan step being executed
     * @param planless     the defect reading: the squad holds no plan at all
     */
    public record Sample(int tick, String mission, String executing, String suspension,
                         String goal, String goalPriority, String action,
                         boolean planless, int alive, float centroidX, float centroidY) {

        /** True when the order the squad holds was issued by the player rather than the commander. */
        public boolean playerOrdered() {
            return executing.startsWith(SquadOrderRecorder.PLAYER_ORDER_PREFIX);
        }

        /** The sample for a squad the simulation no longer has. */
        static Sample missing(int tick) {
            return new Sample(tick, "", "", "", "", "", "", true, 0, 0f, 0f);
        }
    }

    /** One change of one layer's value, at the tick the new value was first seen. */
    public record Transition(int tick, String from, String to) {}

    private final Map<Integer, List<Sample>> streams = new LinkedHashMap<>();
    private final Map<Integer, String> labels = new LinkedHashMap<>();

    /**
     * Adds a squad to the sampled set. The label names it in readouts; the id is
     * what every query takes.
     */
    public OrderTrace track(String label, int squadId) {
        labels.put(squadId, Objects.requireNonNull(label, "label"));
        streams.computeIfAbsent(squadId, k -> new ArrayList<>());
        return this;
    }

    /** The readout name given to a tracked squad. */
    public String label(int squadId) {
        String label = labels.get(squadId);
        if (label == null) throw new IllegalArgumentException("squad " + squadId + " is not tracked");
        return label;
    }

    @Override
    public void observe(BattleSimulation sim, int tick) {
        for (Map.Entry<Integer, List<Sample>> e : streams.entrySet()) {
            Squad squad = sim.getSquad(e.getKey());
            e.getValue().add(squad == null ? Sample.missing(tick) : read(sim, tick, squad));
        }
    }

    /**
     * Records one sample directly, separated from the world reads so the query
     * arithmetic can be exercised on synthetic streams — the recorder's own
     * label-stream seam exists for the same reason.
     */
    void sample(int tick, int squadId, Sample s) {
        streams.computeIfAbsent(squadId, k -> new ArrayList<>()).add(s);
        labels.putIfAbsent(squadId, "squad " + squadId);
    }

    /**
     * The centroid is taken from the live members rather than from
     * {@code Squad.centroidX/Y}, which the alert pass writes and which therefore
     * still reads (0, 0) on the tick before the first advance — a leg from the
     * origin that would be charged to {@link #cellsTravelled} as ground covered.
     */
    private static Sample read(BattleSimulation sim, int tick, Squad squad) {
        String suspension = squad.assignmentExecutionSuspension();
        Goal goal = squad.currentGoal;
        int alive = 0;
        float sumX = 0f;
        float sumY = 0f;
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (sim.resolveUnit(member) == 0L) continue;
            alive++;
            sumX += sim.world().x(member);
            sumY += sim.world().y(member);
        }
        return new Sample(tick,
                SquadOrderRecorder.assignmentLabel(squad.assignedObjective),
                executingLabel(squad),
                suspension == null ? "" : suspension,
                goal == null ? NONE : goal.name(),
                goal == null ? "" : goal.priority().name(),
                actionLabel(squad.currentPlan),
                squad.currentPlan == null,
                alive,
                alive == 0 ? 0f : sumX / alive,
                alive == 0 ? 0f : sumY / alive);
    }

    /**
     * The order the squad holds rather than {@code assignmentForExecution()},
     * which form-up masks to null: a squad still assembling has an order, and
     * whether it can act on it yet is what {@link Sample#suspension} carries.
     */
    private static String executingLabel(Squad squad) {
        ObjectiveAssignment player = squad.playerTacticalOrder();
        return player != null
                ? SquadOrderRecorder.PLAYER_ORDER_PREFIX + SquadOrderRecorder.assignmentLabel(player)
                : SquadOrderRecorder.assignmentLabel(squad.assignedObjective);
    }

    private static String actionLabel(SquadPlan plan) {
        if (plan == null) return NONE;
        SquadPlan.Step step = plan.currentStep();
        return step == null ? "(plan complete)" : step.action.name();
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Sampling rounds recorded, taken from the longest stream. */
    public int ticks() {
        int max = 0;
        for (List<Sample> stream : streams.values()) max = Math.max(max, stream.size());
        return max;
    }

    /** Every sample for one squad, in tick order. */
    public List<Sample> samples(int squadId) {
        return List.copyOf(stream(squadId));
    }

    /** The sample taken at {@code tick}, or null when the squad has none for that tick. */
    public Sample at(int squadId, int tick) {
        for (Sample s : stream(squadId)) {
            if (s.tick() == tick) return s;
        }
        return null;
    }

    /** Ticks the squad held no plan at all, across the whole recording. */
    public int planlessTicks(int squadId) {
        int count = 0;
        for (Sample s : stream(squadId)) {
            if (s.planless()) count++;
        }
        return count;
    }

    /** Ticks the squad held no plan, counting only samples in {@code [fromTick, toTickInclusive]}. */
    public int planlessTicks(int squadId, int fromTick, int toTickInclusive) {
        int count = 0;
        for (Sample s : stream(squadId)) {
            if (s.tick() >= fromTick && s.tick() <= toTickInclusive && s.planless()) count++;
        }
        return count;
    }

    /** The earliest tick whose sample satisfies {@code test}. */
    public OptionalInt firstTick(int squadId, Predicate<Sample> test) {
        for (Sample s : stream(squadId)) {
            if (test.test(s)) return OptionalInt.of(s.tick());
        }
        return OptionalInt.empty();
    }

    /** The latest tick whose sample satisfies {@code test}. */
    public OptionalInt lastTick(int squadId, Predicate<Sample> test) {
        List<Sample> stream = stream(squadId);
        for (int i = stream.size() - 1; i >= 0; i--) {
            if (test.test(stream.get(i))) return OptionalInt.of(stream.get(i).tick());
        }
        return OptionalInt.empty();
    }

    /**
     * True when every sample in {@code [from, to]} satisfies {@code test}.
     *
     * <p>A range holding no samples is false, not vacuously true. "It held the
     * order across the whole approach" asserted over a window the recording
     * never reached is a verdict that measured nothing, and one that passes by
     * measuring nothing is worse than none at all.
     */
    public boolean holds(int squadId, int from, int to, Predicate<Sample> test) {
        boolean sawAny = false;
        for (Sample s : stream(squadId)) {
            if (s.tick() < from || s.tick() > to) continue;
            sawAny = true;
            if (!test.test(s)) return false;
        }
        return sawAny;
    }

    /**
     * Every change of one layer's value, in tick order. The first sample opens
     * the run rather than transitioning into it, so a squad that never changed
     * its goal has no transitions.
     */
    public List<Transition> transitions(int squadId, Function<Sample, String> layer) {
        List<Transition> out = new ArrayList<>();
        String current = null;
        for (Sample s : stream(squadId)) {
            String value = layer.apply(s);
            if (current == null) {
                current = value;
            } else if (!current.equals(value)) {
                out.add(new Transition(s.tick(), current, value));
                current = value;
            }
        }
        return out;
    }

    /**
     * Distance the squad's centroid covered, summed between consecutive samples.
     * Ticks with no living member are skipped rather than treated as a jump to
     * the origin — a wiped squad's centroid is undefined, not at (0, 0).
     */
    public float cellsTravelled(int squadId) {
        float total = 0f;
        float lastX = 0f;
        float lastY = 0f;
        boolean anchored = false;
        for (Sample s : stream(squadId)) {
            if (s.alive() == 0) continue;
            if (anchored) total += (float) Math.hypot(s.centroidX() - lastX, s.centroidY() - lastY);
            lastX = s.centroidX();
            lastY = s.centroidY();
            anchored = true;
        }
        return total;
    }

    /**
     * One layer run-length encoded — {@code AttackMoveGoal×212 → ClearAssignedZoneGoal×88} —
     * short enough to quote on a FAIL line and specific enough to say where the
     * run went wrong.
     */
    public String runs(int squadId, Function<Sample, String> layer) {
        List<Sample> stream = stream(squadId);
        if (stream.isEmpty()) return "(no samples)";
        StringBuilder sb = new StringBuilder();
        String current = null;
        int run = 0;
        for (Sample s : stream) {
            String value = layer.apply(s);
            if (current != null && !current.equals(value)) {
                appendRun(sb, current, run);
                run = 0;
            }
            current = value;
            run++;
        }
        appendRun(sb, current, run);
        return sb.toString();
    }

    private static void appendRun(StringBuilder sb, String value, int length) {
        if (sb.length() > 0) sb.append(" → ");
        sb.append(value.isEmpty() ? "(gone)" : value).append('×').append(length);
    }

    private List<Sample> stream(int squadId) {
        List<Sample> stream = streams.get(squadId);
        if (stream == null) throw new IllegalArgumentException("squad " + squadId + " is not tracked");
        return stream;
    }
}
