package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.BattleResources;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.command.ResourceType;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stateless-data per-tick driver for {@link ReinforcementService} — the
 * Services-own-state / Systems-process shape. On its slow-tick cadence it polls
 * every registered trigger, then drains the Service's request queue and
 * dispatches each request to the means that would answer it soonest.
 *
 * <p>A <b>System</b> (processor): it owns only the cadence {@link #accumulator}
 * (transient bookkeeping); the trigger/means registries and the pending queue
 * live on the Service. Named {@code *System}, not {@code *Service}, under the
 * Service(data-owner)/System(processor) convention — see
 * {@code ecs-nouns.md}.
 *
 * <p>Dispatch is resource-gated: one
 * {@link ResourceType#REINFORCEMENT} ticket is reserved before attempts and
 * retained only by a committed dispatch. Insufficient balance or a retryable
 * attempt re-queues the request for the next tick.
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
     * the soonest-arriving means that can fulfill it. Requests that no means
     * can fulfill are logged as bugged-map diagnostics and dropped; requests
     * no pool can pay for are re-queued for the next tick.
     */
    public void tick(float dt, BattleControl sim) {
        // Every tick, ahead of the cadence gate: a means preparing a delivery
        // over several ticks is advanced on the sim's clock rather than on the
        // dispatcher's, which is a second wide. A means that prepares nothing
        // does nothing here.
        boolean readyEarly = advanceMeans(dt, sim);
        if (service.triggers().isEmpty() && service.isPendingEmpty()) return;
        accumulator += dt;
        boolean cadence = accumulator >= ReinforcementService.REINFORCEMENT_TICK_PERIOD;
        if (!cadence && !readyEarly) return;
        if (cadence) {
            accumulator -= ReinforcementService.REINFORCEMENT_TICK_PERIOD;
            for (ReinforcementTrigger trigger : service.triggers()) {
                trigger.check(sim, service::post);
            }
        }
        // Snapshot-drain in FIFO order: a request no pool can pay for is
        // re-posted (to the now-empty queue) and so retried next tick, not this
        // one — matching the prior deferred-requeue-at-end semantics.
        for (ReinforcementRequest req : service.drainPending()) {
            if (!dispatch(sim, req)) service.post(req);
        }
    }

    /**
     * Advances every means' in-progress preparation and reports whether any of
     * it finished on this tick.
     *
     * <p>A means that answers {@link ReinforcementDispatchResult#RETRYABLE}
     * while it prepares is otherwise retried only on the next cadence, so a
     * proof that finishes eight ticks after it began would still wait out the
     * rest of the second. Draining on the tick it finishes is what keeps the
     * cost of spreading the work across ticks measured in ticks.
     */
    private boolean advanceMeans(float dt, BattleControl sim) {
        boolean ready = false;
        List<ReinforcementMeans> all = service.means();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).advance(dt, sim)) ready = true;
        }
        return ready;
    }

    /**
     * The means that can serve this request, soonest-arriving first.
     *
     * <p>Every feasible means is asked when it would arrive and the whole set
     * is ordered by the answer, rather than the registration order handing the
     * request to the first one that merely said yes. A strict priority list
     * makes everything below the top of it unreachable for as long as the top
     * is feasible, which is not a fallback ladder but a single means with two
     * spares: a garrison airfield measured on a production Conquest map flew
     * nothing at all across a whole battle, because the convoy above it could
     * always deliver and so was always asked.
     *
     * <p>Registration order survives as the tie-break, so means that would
     * arrive together still resolve deterministically and in the order the
     * battle installed them.
     */
    private List<ReinforcementMeans> soonestFirst(BattleControl sim,
                                                  ReinforcementRequest req) {
        List<ReinforcementMeans> all = service.means();
        List<Candidate> feasible = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            ReinforcementMeans m = all.get(i);
            if (!m.canFulfill(sim, req)) continue;
            feasible.add(new Candidate(m, m.arrivalSeconds(sim, req), i));
        }
        feasible.sort(CANDIDATE_ORDER);
        List<ReinforcementMeans> ordered = new ArrayList<>(feasible.size());
        for (Candidate c : feasible) ordered.add(c.means);
        return ordered;
    }

    /** One feasible means, when it says it would arrive, and where it was registered. */
    private record Candidate(ReinforcementMeans means, float arrivalSeconds, int registered) { }

    /**
     * Soonest arrival first, registration order on a tie. A means that answers
     * with a NaN sorts last rather than corrupting the order, because a
     * comparator that is not a total order throws out of {@code List.sort}.
     */
    private static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.comparingDouble((Candidate c) ->
                            Float.isNaN(c.arrivalSeconds) ? Float.MAX_VALUE : c.arrivalSeconds)
                    .thenComparingInt(Candidate::registered);

    private boolean dispatch(BattleControl sim, ReinforcementRequest req) {
        float cost = resources.reinforcementCost();
        // Prepaid requests (the bulge counterattack's up-front earmark, see
        // ReinforcementRequest#prepaid) already debited their cost in one lump
        // at muster — skip the per-dispatch debit here, and skip the refund
        // below on the no-means path too. The earmark is a sunk bet: a wave
        // request no means can deliver still burns its ticket.
        if (!req.prepaid && !resources.tryConsume(req.side, ResourceType.REINFORCEMENT, cost)) {
            return false;
        }
        for (ReinforcementMeans m : soonestFirst(sim, req)) {
            ReinforcementDispatchResult result = m.dispatch(sim, req);
            if (result == ReinforcementDispatchResult.COMMITTED) {
                return true;
            }
            if (result == ReinforcementDispatchResult.RETRYABLE) {
                if (!req.prepaid) {
                    resources.produce(req.side, ResourceType.REINFORCEMENT, cost);
                }
                LOG.debug("reinforcement: retry deferred " + req + " after "
                        + m.getClass().getSimpleName());
                return false;
            }
        }
        if (!req.prepaid) {
            resources.produce(req.side, ResourceType.REINFORCEMENT, cost);
        }
        req.releaseDispatchReservation();
        LOG.warn("reinforcement: no means could fulfill " + req + " - bugged map?");
        return true;
    }
}
