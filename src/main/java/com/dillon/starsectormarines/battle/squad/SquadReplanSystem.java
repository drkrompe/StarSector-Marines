package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.drone.GoapDroneBehavior;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.mech.GoapMechBehavior;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Squad-level replan pass — dispatches each {@link Squad} to the GOAP
 * behavior matching its kind (drone / mech / infantry) so plans reflect
 * THIS tick's fresh {@code aliveMembers} + centroid + alert level before
 * any unit executes. Runs after the squad triad (alert / morale /
 * fallback) and before {@link com.dillon.starsectormarines.battle.decision.UnitUpdateSystem}.
 *
 * <p>Serial today; the planner + WorldStateBuilder + actions are designed
 * for parallel execution across squads (see {@code ai-nouns.md}) and we'll
 * fork-join here once we feel the cost.
 * When that happens, the for-loop becomes the next entity for-loop seam —
 * sibling shape to {@link com.dillon.starsectormarines.battle.decision.UnitUpdateSystem},
 * just keyed on {@link Squad} instead of {@code Entity}.
 *
 * <p>Sibling System to {@link SquadAlertSystem} / {@link SquadMoraleSystem}
 * / {@link SquadFallbackSystem}.
 */
public final class SquadReplanSystem {

    private final UnitRosterService rosterService;
    private final DiagnosticsCollector diagnosticsCollector = new DiagnosticsCollector();
    private boolean diagnosticsEnabled;
    private TickDiagnostics lastTickDiagnostics = TickDiagnostics.EMPTY;

    /** Timing and trigger collection is off during ordinary simulation. */
    public void setDiagnosticsEnabled(boolean enabled) {
        diagnosticsEnabled = enabled;
        if (!enabled) {
            diagnosticsCollector.clear();
            lastTickDiagnostics = TickDiagnostics.EMPTY;
        }
    }

    /** Immutable result of the last completed pass, or empty when collection is off. */
    public TickDiagnostics lastTickDiagnostics() {
        return lastTickDiagnostics;
    }

    public SquadReplanSystem(UnitRosterService rosterService) {
        this.rosterService = rosterService;
    }

    /**
     * Run one replan pass across all squads. {@code sim} is threaded
     * through to the GOAP behaviors unchanged — same sim-as-context
     * coupling that the rest of the AI layer carries until the
     * {@code *SimContext} deprecation path completes.
     */
    public void tick(BattleSimulation sim) {
        boolean collectDiagnostics = diagnosticsEnabled;
        lastTickDiagnostics = TickDiagnostics.EMPTY;
        if (collectDiagnostics) diagnosticsCollector.clear();
        for (Squad squad : rosterService.getSquads()) {
            long previousRoutingEpoch = squad.routingEpoch;
            SquadPlan previousPlan = squad.currentPlan;
            SquadSample before = collectDiagnostics ? beforeCall(squad) : null;
            long startedNanos = collectDiagnostics ? System.nanoTime() : 0L;
            if (squad.isDroneSquad()) {
                GoapDroneBehavior.replanIfNeeded(squad, sim);
            } else if (squad.isMechSquad()) {
                GoapMechBehavior.replanIfNeeded(squad, sim);
            } else {
                GoapInfantryBehavior.replanIfNeeded(squad, sim);
            }
            if (collectDiagnostics) {
                diagnosticsCollector.record(before.withResult(
                        System.nanoTime() - startedNanos,
                        squad.routingEpoch != previousRoutingEpoch));
            }
            // An old DefendTrack action may never execute again after a
            // squad replan (or wipe), so its worker request cannot rely on
            // that action's early-exit cancellation. Retire it before the
            // unit-update phase consumes any results.
            if (sim.asyncDefendTrackRoutes() != null
                    && (squad.routingEpoch != previousRoutingEpoch
                    || (previousPlan != null && squad.currentPlan == null))) {
                for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                    sim.asyncDefendTrackRoutes().cancel(
                            sim.squadMemberAt(squad.id, i));
                }
            }
        }
        if (collectDiagnostics) lastTickDiagnostics = diagnosticsCollector.snapshot();
    }

    private static SquadSample beforeCall(Squad squad) {
        SquadKind kind = squad.isDroneSquad() ? SquadKind.DRONE
                : squad.isMechSquad() ? SquadKind.MECH : SquadKind.INFANTRY;
        boolean assignmentChanged = false;
        if (kind != SquadKind.DRONE && squad.aliveMembers > 0) {
            ObjectiveAssignment assignment = squad.assignmentForExecution();
            assignmentChanged = !Objects.equals(assignment,
                    squad.assignedObjectiveAtLastPlan);
        }
        boolean contactChanged = kind == SquadKind.INFANTRY
                && (squad._directContactStartedThisTick
                || squad._alertLevelChangedThisTick
                || squad._contactDoctrineChangedThisTick);
        return new SquadSample(squad.id, kind, 0L, false,
                squad.currentPlan == null,
                squad.currentPlan != null && squad.currentPlan.isComplete(),
                squad.timeSinceReplan >= Planner.REPLAN_PERIOD,
                squad.aliveMembers != squad.aliveMembersAtLastPlan,
                assignmentChanged, contactChanged,
                kind == SquadKind.INFANTRY
                        && squad._underFireAtLosThisTick
                        && !squad._underFireAtLosLastTick,
                kind == SquadKind.INFANTRY && squad._moraleBrokenChangedThisTick);
    }

    public enum SquadKind { INFANTRY, MECH, DRONE }

    /** Trigger flags are sampled before dispatch; several may be true together. */
    public record SquadSample(int squadId, SquadKind kind, long durationNanos,
                              boolean replanned, boolean planMissing,
                              boolean planComplete, boolean periodic,
                              boolean memberChanged, boolean assignmentChanged,
                              boolean contactChanged, boolean incomingFireStarted,
                              boolean moraleChanged) {
        private SquadSample withResult(long nanos, boolean didReplan) {
            return new SquadSample(squadId, kind, nanos, didReplan, planMissing,
                    planComplete, periodic, memberChanged, assignmentChanged,
                    contactChanged, incomingFireStarted, moraleChanged);
        }
    }

    public record TickDiagnostics(int squadCount, int replanCount,
                                  List<SquadSample> slowestSquads) {
        public static final TickDiagnostics EMPTY = new TickDiagnostics(0, 0, List.of());

        public TickDiagnostics {
            slowestSquads = List.copyOf(slowestSquads);
        }
    }

    /** Bounded top list so a large battle never retains one sample per squad. */
    static final class DiagnosticsCollector {
        private static final int TOP_LIMIT = 8;
        private final List<SquadSample> slowest = new ArrayList<>(TOP_LIMIT);
        private int squadCount;
        private int replanCount;

        void clear() {
            slowest.clear();
            squadCount = 0;
            replanCount = 0;
        }

        void record(SquadSample sample) {
            squadCount++;
            if (sample.replanned()) replanCount++;
            int at = 0;
            while (at < slowest.size()) {
                SquadSample other = slowest.get(at);
                if (sample.durationNanos() > other.durationNanos()
                        || (sample.durationNanos() == other.durationNanos()
                        && sample.squadId() < other.squadId())) break;
                at++;
            }
            if (at >= TOP_LIMIT) return;
            slowest.add(at, sample);
            if (slowest.size() > TOP_LIMIT) slowest.remove(TOP_LIMIT);
        }

        TickDiagnostics snapshot() {
            return new TickDiagnostics(squadCount, replanCount, slowest);
        }
    }
}
