package com.dillon.starsectormarines.battle.objective.optional;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;

/**
 * How one rolled optional objective came out, as the campaign receives it.
 * {@code reward} and {@code cost} are what the objective declared; read
 * {@link #earned()} and {@link #incurred()} for what this result actually pays and carries.
 */
public record ObjectiveResult(String key, String title, Status status, Reward reward, Cost cost) {

    public ObjectiveResult {
        reward = reward == null ? Reward.NONE : reward;
        cost = cost == null ? Cost.NONE : cost;
    }

    /** The reward when this succeeded, otherwise nothing. */
    public Reward earned() { return status == Status.SUCCEEDED ? reward : Reward.NONE; }

    /** The cost when this failed, otherwise nothing. */
    public Cost incurred() { return status == Status.FAILED ? cost : Cost.NONE; }
}
