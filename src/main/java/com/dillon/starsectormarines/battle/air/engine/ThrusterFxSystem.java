package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirHandling;
import com.dillon.starsectormarines.battle.component.ComponentStore;

/**
 * Advances the smoothed engine-FX demand ({@link ThrusterFx}) for one air entity
 * each sim tick. The instantaneous target from {@link ThrusterDemand} is spiky —
 * the steering model steps velocity bang-bang, so per-slot acceleration alignment
 * jumps between ~0 and ~1 tick-to-tick, which made the plumes pop. This lerps the
 * stored value toward the target with a fast attack / slower decay envelope, so
 * thrusters spool up quickly when called on and trail off gracefully — the
 * "ramp over time to a target thrust length" the visual needs.
 *
 * <p>System over a component set: the caller iterates its air entities and calls
 * {@link #advance} per entity, attaching / resizing the component lazily and
 * dropping it when the entity has no engine slots.
 */
public final class ThrusterFxSystem {

    /** Rising-edge rate (1/sec). Fast — a called-on thruster reaches ~95% of target in ~0.3s. */
    private static final float ATTACK_PER_SEC = 11f;
    /** Falling-edge rate (1/sec). Slower than attack so plumes trail off rather than cut. */
    private static final float DECAY_PER_SEC = 4f;

    private ThrusterFxSystem() {}

    /**
     * Advances (or initializes) the {@link ThrusterFx} for {@code entityId} from
     * the entity's current {@code body} + {@code handling} + engine {@code slots}.
     * No-ops to a removed component when the entity has no engine slots.
     *
     * @return the smoothed per-slot array (so a caller can use it immediately), or
     *         {@code null} if the entity carries no engine slots
     */
    public static float[] advance(long entityId, EngineSlotData[] slots,
                                  AirBody body, AirHandling handling,
                                  ComponentStore<ThrusterFx> store, float dt) {
        if (slots == null || slots.length == 0) {
            store.remove(entityId);
            return null;
        }

        ThrusterFx fx = store.get(entityId);
        if (fx == null || fx.smoothed.length != slots.length) {
            fx = new ThrusterFx(slots.length);
            store.add(entityId, fx);
        }

        float[] target = ThrusterDemand.compute(slots, body, handling);
        for (int i = 0; i < fx.smoothed.length; i++) {
            float tgt = target[i];
            float cur = fx.smoothed[i];
            float rate = (tgt > cur) ? ATTACK_PER_SEC : DECAY_PER_SEC;
            // Exponential approach — frame-rate independent, no overshoot.
            float a = 1f - (float) Math.exp(-rate * dt);
            fx.smoothed[i] = cur + (tgt - cur) * a;
        }
        return fx.smoothed;
    }
}
