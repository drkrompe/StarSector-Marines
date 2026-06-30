package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.BattleResources;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.command.ResourceType;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Stateless-data per-tick driver for {@link ReinforcementService} — the
 * Services-own-state / Systems-process shape. On its slow-tick cadence it polls
 * every registered trigger, then drains the Service's request queue and
 * dispatches each request to the first means that can fulfill it.
 *
 * <p>A <b>System</b> (processor): it owns only the cadence {@link #accumulator}
 * (transient bookkeeping); the trigger/means registries and the pending queue
 * live on the Service. Named {@code *System}, not {@code *Service}, under the
 * Service(data-owner)/System(processor) convention — see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}.
 *
 * <p>Dispatch is resource-gated: each successful dispatch debits one
 * {@link ResourceType#REINFORCEMENT} ticket from the requesting side's
 * {@link BattleResources} pool. Insufficient balance re-queues the request for
 * the next tick.
 */
public final class ReinforcementSystem {

    private static final Logger LOG = Global.getLogger(ReinforcementSystem.class);

    private final ReinforcementService service;
    /** Per-faction resource pool each dispatch debits a {@link ResourceType#REINFORCEMENT} ticket from. */
    private final BattleResources resources;

    private float accumulator = 0f;

    public ReinforcementSystem(ReinforcementService service, BattleResources resources) {
        this.service = service;
        this.resources = resources;
    }

    /**
     * Slow-tick: accumulate {@code dt}, and when the cadence period elapses
     * poll every trigger, then drain the queue and dispatch each request to
     * the first means that can fulfill it. Requests that no means can
     * fulfill are logged as bugged-map diagnostics and dropped; requests no
     * pool can pay for are re-queued for the next tick.
     */
    public void tick(float dt, BattleControl sim) {
        if (service.triggers().isEmpty() && service.isPendingEmpty()) return;
        accumulator += dt;
        if (accumulator < ReinforcementService.REINFORCEMENT_TICK_PERIOD) return;
        accumulator -= ReinforcementService.REINFORCEMENT_TICK_PERIOD;

        for (ReinforcementTrigger trigger : service.triggers()) {
            trigger.check(sim, service::post);
        }
        // Snapshot-drain in FIFO order: a request no pool can pay for is
        // re-posted (to the now-empty queue) and so retried next tick, not this
        // one — matching the prior deferred-requeue-at-end semantics.
        for (ReinforcementRequest req : service.drainPending()) {
            if (!dispatch(sim, req)) service.post(req);
        }
    }

    private boolean dispatch(BattleControl sim, ReinforcementRequest req) {
        float cost = resources.reinforcementCost();
        if (!resources.tryConsume(req.side, ResourceType.REINFORCEMENT, cost)) {
            return false;
        }
        for (ReinforcementMeans m : service.means()) {
            if (m.canFulfill(sim, req)) {
                m.dispatch(sim, req);
                LOG.info("reinforcement: dispatched " + req + " via " + m.getClass().getSimpleName());
                return true;
            }
        }
        resources.produce(req.side, ResourceType.REINFORCEMENT, cost);
        LOG.warn("reinforcement: no means could fulfill " + req + " — bugged map?");
        return true;
    }
}
