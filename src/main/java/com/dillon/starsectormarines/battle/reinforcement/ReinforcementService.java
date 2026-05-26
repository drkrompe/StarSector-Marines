package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Orchestration layer for "adding more to the fight." Owns the trigger
 * registry, the priority-ordered means provider list, and the pending
 * request queue. Full design in
 * {@code roadmap/reinforcement/architecture.md}.
 *
 * <p>Naming: this is a {@code *Service} per the project's decomposition
 * convention — it owns state (queues + registries) and gets constructor-
 * injected into {@link BattleSimulation}. Stateless tick consumers like
 * {@code GroundSystem} / {@code AirSystem} keep the {@code *System}
 * suffix.
 *
 * <p>Ticket budget (per-side cooldown / wave shaping) is not modeled in
 * v1 — every request gets dispatch attempts. The second slice plumbs
 * {@code ReinforcementBudget} from the mission config; the v1 service
 * shape doesn't need to change to accommodate it.
 */
public final class ReinforcementService {

    private static final Logger LOG = Global.getLogger(ReinforcementService.class);

    /**
     * Sim-seconds between trigger polls. Reinforcement decisions are slow
     * by nature (a garrison doesn't "deplete" inside one tick); the cadence
     * keeps the per-trigger walk off the per-frame hot path. Same shape
     * as {@code CommanderService.COMMANDER_TICK_PERIOD}; tune in playtest.
     */
    public static final float REINFORCEMENT_TICK_PERIOD = 1.0f;

    private final List<ReinforcementTrigger> triggers = new ArrayList<>();
    private final List<ReinforcementMeans> means = new ArrayList<>();
    private final Deque<ReinforcementRequest> pending = new ArrayDeque<>();

    private float accumulator = 0f;

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
     * next {@link #tick} pass alongside trigger-posted requests, so the
     * means priority list applies the same way for ad-hoc and trigger-
     * driven requests.
     */
    public void post(ReinforcementRequest req) {
        if (req != null) pending.add(req);
    }

    public boolean isEmpty() { return triggers.isEmpty() && means.isEmpty(); }

    /**
     * Slow-tick: accumulate {@code dt}, and when the cadence period elapses
     * poll every trigger, then drain the queue and dispatch each request to
     * the first means that can fulfill it. Requests that no means can
     * fulfill are logged as bugged-map diagnostics and dropped.
     */
    public void tick(float dt, BattleSimulation sim) {
        if (triggers.isEmpty() && pending.isEmpty()) return;
        accumulator += dt;
        if (accumulator < REINFORCEMENT_TICK_PERIOD) return;
        accumulator -= REINFORCEMENT_TICK_PERIOD;

        for (ReinforcementTrigger trigger : triggers) {
            trigger.check(sim, pending::add);
        }
        while (!pending.isEmpty()) {
            ReinforcementRequest req = pending.poll();
            dispatch(sim, req);
        }
    }

    private void dispatch(BattleSimulation sim, ReinforcementRequest req) {
        for (ReinforcementMeans m : means) {
            if (m.canFulfill(sim, req)) {
                m.dispatch(sim, req);
                LOG.info("reinforcement: dispatched " + req + " via " + m.getClass().getSimpleName());
                return;
            }
        }
        LOG.warn("reinforcement: no means could fulfill " + req + " — bugged map?");
    }
}
