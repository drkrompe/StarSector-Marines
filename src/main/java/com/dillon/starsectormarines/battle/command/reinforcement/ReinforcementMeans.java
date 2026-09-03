package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;

/**
 * A way to deliver reinforcements to a battle. The {@link ReinforcementSystem}
 * asks every registered means whether it can serve a request, then offers the
 * first attempt to whichever of them says it would arrive soonest; a rejected
 * attempt falls through to the next-soonest.
 *
 * <p>The installed defender set is {@link ConvoyMeans}, {@link ShuttleMeans},
 * and {@link WalkInMeans}. Each is supply- and map-feasibility-gated; see
 * {@code reinforcement-nouns.md}.
 */
public interface ReinforcementMeans {

    /**
     * Can this means deliver the given request on the current map?
     * Convoy needs a road graph and a reachable rally; shuttle needs an
     * LZ; walk-in needs a usable perimeter cell. Cheap probe — called
     * once per request per means provider.
     */
    boolean canFulfill(BattleView sim, ReinforcementRequest req);

    /**
     * Sim-seconds between this dispatch and the delivered force standing on
     * the ground, as this means estimates it. Lower wins the request.
     *
     * <p>Asked only of a means that has already said it can fulfill, and
     * asked of every one of them: registration order decides nothing but a
     * tie. This is the whole selection rule, and it is deliberately a
     * property of the <em>delivery</em> rather than of the request — how far
     * this means has to come and how fast it travels — because a means chosen
     * on the reason for the request would be a hidden behaviour switch, which
     * {@code reinforcement-nouns.md} law 3 forbids.
     *
     * <p>An estimate, not a promise. It is compared against other estimates
     * and never against the clock, so a means only has to be honest about its
     * own journey relative to the alternatives: the entry it would come from,
     * the ground it would cross, the speed it crosses it at, and whatever
     * fixed delay it owes before setting off.
     */
    float arrivalSeconds(BattleView sim, ReinforcementRequest req);

    /**
     * Per-tick hook for a means that prepares a delivery over several ticks
     * rather than inside the one that asked. Called every sim tick, not on the
     * dispatcher's cadence, because a second is far too coarse to advance
     * anything on.
     *
     * <p>Return {@code true} when preparation that was in progress <em>finished
     * on this tick</em>, which asks the dispatcher to drain its queue now rather
     * than at the next cadence. That is the difference between a delivery
     * arriving a handful of ticks later than it used to and a whole second
     * later, and the whole point of the hook: a means that answers
     * {@link ReinforcementDispatchResult#RETRYABLE} while it works is otherwise
     * retried only once a second, whatever it finishes.
     *
     * <p>Most means prepare nothing and do nothing here. Convoy is the one that
     * does: proving a drivable journey across a city is a bounded enumeration of
     * whole-grid searches, and landing all of it in one frame is a visible hitch
     * for a delivery that then takes six seconds to appear.
     */
    default boolean advance(float dt, BattleControl sim) {
        return false;
    }

    /**
     * Attempt to spawn ordinary vehicle, air, or squad actors through their
     * native battle lifecycle. Called only after {@link #canFulfill} returns
     * {@code true}. {@link ReinforcementDispatchResult#COMMITTED} is legal only
     * after the actor or squad exists; an uncommitted return must leave no
     * partial delivery actors behind.
     */
    ReinforcementDispatchResult dispatch(BattleControl sim,
                                         ReinforcementRequest req);
}
