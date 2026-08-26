package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.ops.MarineArrivalPolicy;

import java.util.Objects;

/** Immutable battle-construction facts resolved from a mission's arrival policy. */
public record ShuttleArrivalPlan(
        MarineArrivalPolicy policy,
        int firstPlayerShuttle) {

    public ShuttleArrivalPlan {
        policy = Objects.requireNonNull(policy, "policy");
        firstPlayerShuttle = Math.max(0, firstPlayerShuttle);
    }

    public static ShuttleArrivalPlan legacy() {
        return new ShuttleArrivalPlan(MarineArrivalPolicy.INDEPENDENT_FULL_LOAD, 0);
    }

    public boolean paired() {
        return policy == MarineArrivalPolicy.PAIRED_HALF_SQUAD;
    }
}
