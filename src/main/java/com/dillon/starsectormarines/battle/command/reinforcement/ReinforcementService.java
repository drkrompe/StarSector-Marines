package com.dillon.starsectormarines.battle.command.reinforcement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Data owner for "adding more to the fight" — the trigger registry, the
 * priority-ordered means provider list, and the pending request queue. Full
 * design in {@code roadmap/reinforcement/architecture.md}.
 *
 * <p>A <b>Service</b> (data owner): it holds the registries + queue and exposes
 * the read/mutate methods for them; the per-tick poll-and-dispatch driver lives
 * on {@link ReinforcementSystem}. Named {@code *Service}, not {@code *System},
 * under the Service(data-owner)/System(processor) convention — see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}. The
 * System-facing accessors are package-private (only the sibling System reaches
 * them), mirroring {@code power.CommandPowerService}.
 */
public final class ReinforcementService {

    /**
     * Sim-seconds between trigger polls. Reinforcement decisions are slow
     * by nature (a garrison doesn't "deplete" inside one tick); the cadence
     * keeps the per-trigger walk off the per-frame hot path. Same shape
     * as {@code CommanderService.COMMANDER_TICK_PERIOD}; tune in playtest.
     * Owned here as the reinforcement-domain cadence constant; the
     * {@link ReinforcementSystem} drives it and {@code RecaptureTargetService}
     * shares it.
     */
    public static final float REINFORCEMENT_TICK_PERIOD = 1.0f;

    private final List<ReinforcementTrigger> triggers = new ArrayList<>();
    private final List<ReinforcementMeans> means = new ArrayList<>();
    private final Deque<ReinforcementRequest> pending = new ArrayDeque<>();

    public void addTrigger(ReinforcementTrigger trigger) { triggers.add(trigger); }

    /**
     * Register a means provider. Order matters — providers are tried in
     * insertion order on each request; first {@link ReinforcementMeans#canFulfill}
     * wins. Register cheap / readable means first (convoy > shuttle > walk-in
     * is the planned order; walk-in is the always-feasible floor).
     */
    public void addMeans(ReinforcementMeans m) { means.add(m); }

    /**
     * Inject a request from outside the trigger registry — debug UI,
     * scripted-mission flags, future commander overrides. Drains on the
     * next {@link ReinforcementSystem#tick} pass alongside trigger-posted
     * requests, so the means priority list applies the same way for ad-hoc
     * and trigger-driven requests.
     */
    public void post(ReinforcementRequest req) {
        if (req != null) pending.add(req);
    }

    public boolean isEmpty() { return triggers.isEmpty() && means.isEmpty(); }

    // ---- System-facing accessors (driven by ReinforcementSystem) ----

    List<ReinforcementTrigger> triggers() { return triggers; }

    List<ReinforcementMeans> means() { return means; }

    boolean isPendingEmpty() { return pending.isEmpty(); }

    /** Drain the pending queue in FIFO order, clearing it. Empty list when none pending. */
    List<ReinforcementRequest> drainPending() {
        if (pending.isEmpty()) return List.of();
        List<ReinforcementRequest> out = new ArrayList<>(pending);
        pending.clear();
        return out;
    }
}
