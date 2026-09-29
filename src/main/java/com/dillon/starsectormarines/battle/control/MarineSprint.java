package com.dillon.starsectormarines.battle.control;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongPredicate;

/** Battle-local stamina for exact Marine identities, independent of who currently controls them. */
public final class MarineSprint {
    public static final float SPEED_SCALE = 1.6f;
    public static final float CAPACITY_SECONDS = 6f;
    public static final float REST_DELAY_SECONDS = 1f;
    public static final float RECOVERY_SECONDS = 4f;
    private static final float RECOVERY_PER_SECOND = CAPACITY_SECONDS / RECOVERY_SECONDS;
    private static final float RESTART_SECONDS = CAPACITY_SECONDS * .25f;
    private static final float MOTION_EPSILON = 1e-6f;
    /** Sub-cell float rounding can leave an unspendable sliver after a real boosted step. */
    private static final float RESOURCE_EPSILON_SECONDS = .001f;

    public record Status(float staminaFraction, boolean sprinting, boolean exhausted) {}

    public static final Status EMPTY = new Status(1f, false, false);

    private final Map<Long, State> byMarine = new HashMap<>();

    /** A depleted hold stays latched until release and recovery to one quarter capacity. */
    public float speedScale(long id, boolean held, float dt) {
        if (!held || dt <= 0f) return 1f;
        State state = byMarine.get(id);
        if (state != null && (state.exhausted || state.remaining <= 0f)) return 1f;
        float remaining = state != null ? state.remaining : CAPACITY_SECONDS;
        return 1f + (SPEED_SCALE - 1f) * Math.min(remaining, dt) / dt;
    }

    /**
     * Charges only extra swept displacement beyond the same tick's normal-speed
     * legal step. A wall that stops both steps at the same point spends nothing.
     */
    public boolean finishStep(long id, boolean requested, float bonusDistance,
                              float authoredSpeed, float dt) {
        State state = byMarine.get(id);
        float available = state != null ? state.remaining : CAPACITY_SECONDS;
        float spent = 0f;
        if (requested && (state == null || !state.exhausted)
                && bonusDistance > MOTION_EPSILON && authoredSpeed > 0f && dt > 0f) {
            spent = Math.min(Math.min(dt, available),
                    bonusDistance / (authoredSpeed * (SPEED_SCALE - 1f)));
        }
        if (spent > 0f) {
            if (state == null) {
                state = new State();
                byMarine.put(id, state);
            }
            state.remaining = Math.max(0f, state.remaining - spent);
            state.restRemaining = REST_DELAY_SECONDS;
            state.sprinting = true;
            if (state.remaining <= RESOURCE_EPSILON_SECONDS) {
                state.remaining = 0f;
                state.exhausted = true;
                state.releasedSinceExhaustion = false;
            }
            return true;
        }
        if (state != null) {
            state.sprinting = false;
            state.recover(dt);
        }
        return false;
    }

    /** Recover every tracked body except the one whose motion is about to be resolved. */
    public void tickInactive(long activeMarineId, float dt, LongPredicate alive) {
        Iterator<Map.Entry<Long, State>> it = byMarine.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, State> entry = it.next();
            if (!alive.test(entry.getKey())) {
                it.remove();
            } else if (entry.getKey() != activeMarineId) {
                entry.getValue().sprinting = false;
                entry.getValue().recover(dt);
            }
        }
    }

    /** Input release is observable even if no simulation tick runs during pause. */
    public void releaseHold(long id) {
        State state = byMarine.get(id);
        if (state == null) return;
        state.sprinting = false;
        state.releasedSinceExhaustion = true;
        state.unlockIfRecovered();
    }

    /** Pause/chrome neutralization ends the current stride without faking a Shift key release. */
    public void suspend(long id) {
        State state = byMarine.get(id);
        if (state != null) state.sprinting = false;
    }

    public Status status(long id) {
        State state = byMarine.get(id);
        return state == null ? EMPTY
                : new Status(Math.min(1f, state.remaining / CAPACITY_SECONDS),
                        state.sprinting, state.exhausted);
    }

    private static final class State {
        float remaining = CAPACITY_SECONDS;
        float restRemaining;
        boolean sprinting;
        boolean exhausted;
        boolean releasedSinceExhaustion = true;

        void recover(float dt) {
            unlockIfRecovered();
            if (dt <= 0f || remaining >= CAPACITY_SECONDS) return;
            float recoveringTime = dt;
            if (restRemaining > 0f) {
                float waiting = Math.min(restRemaining, recoveringTime);
                restRemaining -= waiting;
                recoveringTime -= waiting;
            }
            remaining = Math.min(CAPACITY_SECONDS,
                    remaining + recoveringTime * RECOVERY_PER_SECOND);
            unlockIfRecovered();
        }

        void unlockIfRecovered() {
            if (exhausted && releasedSinceExhaustion && remaining >= RESTART_SECONDS) exhausted = false;
        }
    }
}
