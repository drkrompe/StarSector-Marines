package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.OrdnanceDelivery;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rate limiter for delivered-ordnance audio cues, one pair of timers per
 * {@link OrdnanceDelivery}.
 *
 * <p>A rotary cannon releases fourteen rounds a second and a beam
 * twenty-four. Playing a clip per round is not a louder gun, it is a wall of
 * overlapping voices in which no individual shot is audible and the rest of
 * the battle stops being audible too. The gate lets a cue through on its own
 * cadence and drops the rest, so a gun run reads as a burst rather than as
 * static.
 *
 * <p>Pure, so the cadence can be tested without a sound player. Gaps come from
 * {@link OrdnanceFx}; the gate holds only the clocks.
 */
public final class OrdnanceCueGate {

    private final Map<OrdnanceDelivery, float[]> timers = new EnumMap<>(OrdnanceDelivery.class);

    /** Drains every timer by {@code dt} seconds. Call once per frame. */
    public void advance(float dt) {
        if (dt <= 0f) return;
        for (float[] pair : timers.values()) {
            pair[0] -= dt;
            pair[1] -= dt;
        }
    }

    /** Whether a fire cue may play now; consumes the allowance when it may. */
    public boolean allowFire(OrdnanceDelivery delivery) {
        return allow(delivery, 0, OrdnanceFx.of(delivery).fireCueMinGap());
    }

    /** Whether an impact cue may play now; consumes the allowance when it may. */
    public boolean allowImpact(OrdnanceDelivery delivery) {
        return allow(delivery, 1, OrdnanceFx.of(delivery).impactCueMinGap());
    }

    /** Forgets every clock — for a host tearing a battle down. */
    public void clear() {
        timers.clear();
    }

    private boolean allow(OrdnanceDelivery delivery, int slot, float gap) {
        float[] pair = timers.computeIfAbsent(delivery, d -> new float[2]);
        if (pair[slot] > 0f) return false;
        pair[slot] = Math.max(0f, gap);
        return true;
    }
}
