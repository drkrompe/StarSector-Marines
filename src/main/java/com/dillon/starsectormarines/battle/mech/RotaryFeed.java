package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.weapon.RotaryFeedDef;

/**
 * The live spin and belt of one rotary mount. One rule serves the pilot and the AI: a held trigger spins
 * the barrels up and releases rounds at the rate the spin has reached, a released one lets them coast and
 * then spin down, and the hopper refills the belt every tick for the life of the mech, firing or not.
 *
 * <p>The AI does not hold a button: it takes a short {@linkplain #demand lease} on the trigger each time
 * its doctrine wants the gun firing, and {@linkplain #hold holds the spin} while it is engaged without a
 * shot. It keeps a reserve on the belt: below {@link #AI_STOP_FRACTION} of the belt it stops asking for
 * rounds until the hopper has put {@link #AI_RESUME_FRACTION} back, so an autonomous gun eases off rather
 * than running dry, and has rounds in hand when the next target shows. A pilot may empty the belt.
 *
 * <p>Rounds are released whole. A round the mount asked for and could not fire (no aim, a wall at the
 * muzzle, no flux) still uses its slot, so a blocked gun never builds a backlog it would dump on release.
 */
public final class RotaryFeed {

    /** An autonomous gunner stops asking for rounds below this fraction of the belt. */
    public static final float AI_STOP_FRACTION = 0.2f;
    /** ...and asks again once the hopper has refilled it to this fraction. */
    public static final float AI_RESUME_FRACTION = 0.3f;
    /** How long one autonomous demand keeps the trigger down: a few ticks, renewed every decision. */
    public static final float AI_LEASE_SECONDS = 0.25f;

    private final RotaryFeedDef def;
    private float spin;
    private float coast;
    private float belt;
    private float pending;
    private boolean demandedLastTick;
    private float lease;
    private long leaseTarget;
    private boolean reserveHeld;
    private float sinceRound = Float.POSITIVE_INFINITY;

    public RotaryFeed(RotaryFeedDef def) {
        if (def == null) throw new IllegalArgumentException("Rotary feed needs its definition");
        this.def = def;
        this.belt = def.belt();
    }

    public RotaryFeedDef def() { return def; }

    /** 0 at rest, 1 at full speed. */
    public float spin() { return spin; }

    /** Whole rounds on the belt. */
    public int beltRounds() { return (int) belt; }

    public float beltFraction() { return belt / def.belt(); }

    /** Rounds a second the barrels would release now with the trigger held. */
    public float rate() { return def.rate(spin); }

    /** Seconds since the last round left, for the arm's recoil and flash. */
    public float sinceRound() { return sinceRound; }

    /** The AI's target while its lease runs, else 0. */
    public long leaseTarget() { return lease > 0f ? leaseTarget : 0L; }

    /** Whether the AI's lease on the trigger is running (it may still be holding its reserve). */
    public boolean leased() { return lease > 0f; }

    /** Whether the AI is holding fire to let the belt recover. */
    public boolean reserveHeld() { return reserveHeld; }

    /** An autonomous gunner asks for fire on {@code target} for the next {@link #AI_LEASE_SECONDS}. */
    public void demand(long target) {
        lease = AI_LEASE_SECONDS;
        leaseTarget = target;
        coast = def.coastSeconds();
    }

    /** Engaged without a shot this decision: keep the barrels turning at the speed they have. */
    public void hold() {
        coast = Math.max(coast, def.coastSeconds());
    }

    /** Drops the AI's lease, as when a pilot takes the controls or the target is gone. */
    public void releaseLease() {
        lease = 0f;
        leaseTarget = 0L;
    }

    /**
     * Advances one tick. {@code pilotHeld} is a pilot's trigger on this mount; without it the AI's lease
     * decides, less its reserve. Returns the rounds the mount asks to release this tick; report each one
     * that actually fired with {@link #spend}.
     */
    public int advance(float dt, boolean pilotHeld) {
        float step = Math.max(0f, dt);
        belt = Math.min(def.belt(), belt + def.refillPerSecond() * step);
        sinceRound += step;
        boolean aiAsking = lease > 0f;
        if (aiAsking) lease = Math.max(0f, lease - step);
        updateReserve();
        boolean demanded = pilotHeld || (aiAsking && !reserveHeld);
        if (!demanded) {
            pending = 0f;
            demandedLastTick = false;
            if (aiAsking) coast = Math.max(coast, def.coastSeconds());
            float coasting = Math.min(coast, step);
            coast -= coasting;
            float spinning = step - coasting;
            if (spinning > 0f) spin = def.spinDownSeconds() > 0f ? Math.max(0f, spin - spinning / def.spinDownSeconds()) : 0f;
            return 0;
        }
        // The first round leaves on the press; the barrels are turning, if only just.
        spin = def.instant() ? 1f : Math.min(1f, spin + step / def.spinUpSeconds());
        coast = def.coastSeconds();
        if (demandedLastTick) pending += def.rate(spin) * step;
        else pending = Math.max(pending, 1f);
        demandedLastTick = true;
        int ready = Math.min((int) pending, (int) belt);
        pending -= ready;
        // A dry belt banks no backlog: the next round waits on the hopper, not on the clock.
        if (belt - ready < 1f) pending = Math.min(pending, 1f);
        return ready;
    }

    /** One requested round actually left the barrel. */
    public void spend() {
        belt = Math.max(0f, belt - 1f);
        sinceRound = 0f;
        updateReserve();
    }

    private void updateReserve() {
        float fraction = beltFraction();
        if (fraction < AI_STOP_FRACTION) reserveHeld = true;
        else if (fraction >= AI_RESUME_FRACTION) reserveHeld = false;
    }
}
