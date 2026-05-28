package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.BattleResources;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.command.ResourceType;
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
 * <p>Dispatch is resource-gated: each successful dispatch debits one
 * {@link ResourceType#REINFORCEMENT} ticket from the requesting side's
 * {@link BattleResources} pool. Insufficient balance re-queues the
 * request for the next tick.
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
        Deque<ReinforcementRequest> deferred = null;
        while (!pending.isEmpty()) {
            ReinforcementRequest req = pending.poll();
            if (!dispatch(sim, req)) {
                if (deferred == null) deferred = new ArrayDeque<>();
                deferred.add(req);
            }
        }
        if (deferred != null) pending.addAll(deferred);
    }

    private boolean dispatch(BattleSimulation sim, ReinforcementRequest req) {
        BattleResources res = sim.getBattleResources();
        float cost = res.reinforcementCost();
        if (!res.tryConsume(req.side, ResourceType.REINFORCEMENT, cost)) {
            return false;
        }
        for (ReinforcementMeans m : means) {
            if (m.canFulfill(sim, req)) {
                m.dispatch(sim, req);
                LOG.info("reinforcement: dispatched " + req + " via " + m.getClass().getSimpleName());
                return true;
            }
        }
        res.produce(req.side, ResourceType.REINFORCEMENT, cost);
        LOG.warn("reinforcement: no means could fulfill " + req + " — bugged map?");
        return true;
    }
}
