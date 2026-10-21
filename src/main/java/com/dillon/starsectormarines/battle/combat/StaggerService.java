package com.dillon.starsectormarines.battle.combat;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.function.IntSupplier;

/**
 * Data owner for the short "can't act" state ({@code brute-charge.md}, rule 4): a body thrown and
 * knocked down, a vehicle stalled, a mech staggered, a Brute stunned. One state for all of them,
 * because they are the same thing to the battle — for a fixed time the body neither moves nor fires.
 * It is not a casualty state: the body stays upright in the sim and takes hits as usual.
 *
 * <p>What the state does is not stored here. {@link StaggerModifier} holds move speed at zero, the
 * firing system refuses the shot, the vehicle driver stalls the hull, and a mech's hips hold still;
 * each reads {@link #isStaggered}.
 *
 * <p>Owned by the roster; sparse, keyed by entity id (ids are never reused). Ticks come from the
 * battle's own tick index, so the state is deterministic across replays.
 *
 * <p><b>Threading.</b> Written only from serial passes (the swarm strike pass that lands a charge's
 * hits); the parallel unit dispatch only reads. Concurrent reads of an unchanging map are safe.
 */
public final class StaggerService {

    private final Long2IntOpenHashMap untilTick = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap sinceTick = new Long2IntOpenHashMap();
    private IntSupplier clock = () -> 0;

    /** The battle's tick index; set once the simulation that owns the roster exists. */
    public void setClock(IntSupplier clock) { this.clock = clock; }

    /** Holds {@code unit} for {@code ticks} from now. A longer hold already running is kept. */
    public void stagger(long unit, int ticks) {
        if (ticks <= 0) return;
        int now = clock.getAsInt();
        int until = now + ticks;
        if (!isStaggered(unit)) {
            sinceTick.put(unit, now);
            untilTick.put(unit, until);
        } else if (until > untilTick.get(unit)) {
            untilTick.put(unit, until);
        }
    }

    /** Whether {@code unit} can't act this tick. */
    public boolean isStaggered(long unit) {
        return untilTick.get(unit) > clock.getAsInt();
    }

    /** Ticks left on the hold, or zero. */
    public int ticksLeft(long unit) {
        return Math.max(0, untilTick.get(unit) - clock.getAsInt());
    }

    /** The tick the current hold began, for presentation; meaningless when not staggered. */
    public int sinceTick(long unit) { return sinceTick.get(unit); }
}
