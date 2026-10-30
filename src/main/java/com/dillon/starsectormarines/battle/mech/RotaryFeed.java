package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.weapon.LoaderPace;
import com.dillon.starsectormarines.battle.weapon.RotaryFeedDef;

/**
 * The live spin and belt of one rotary mount. One rule serves the pilot and the AI: a held trigger spins
 * the barrels up and releases rounds at the rate the spin has reached, a released one lets them coast and
 * then spin down, and the hopper refills the belt every tick for the life of the mech, firing or not, at the
 * weapon's own rate times the machine's loader ({@link LoaderPace}).
 *
 * <p>The AI does not hold a button: it takes a short {@linkplain #demand lease} on the trigger each time
 * its doctrine wants the gun firing, and {@linkplain #hold holds the spin} while it is engaged without a
 * shot. It keeps a reserve on the belt: below {@link #AI_STOP_FRACTION} of the belt it stops asking for
 * rounds until the hopper has put {@link #AI_RESUME_FRACTION} back, so an autonomous gun eases off rather
 * than running dry, and has rounds in hand when the next target shows. A pilot may empty the belt.
 *
 * <p>Rounds are released whole. A round the mount asked for and could not fire (no aim, a wall at the
 * muzzle, no flux) still uses its slot, so a blocked gun never builds a backlog it would dump on release.
 *
 * <p>A beam ({@link RotaryFeedDef#beam()}) is the same trigger with no belt: its pulses never run out, and its
 * autonomous reserve is the reservoir's rather than a belt's. The owner reports the reservoir's share each tick
 * ({@link #observeFlux}); above {@link RotaryFeedDef#BEAM_AI_STOP_SHARE} the AI stops asking for pulses until it is
 * back to {@link RotaryFeedDef#BEAM_AI_RESUME_SHARE}. A pilot's beam stops only on release or on the reservoir's
 * own vent.
 */
public final class RotaryFeed {

    /** An autonomous gunner stops asking for rounds below this fraction of the belt. */
    public static final float AI_STOP_FRACTION = 0.2f;
    /** ...and asks again once the hopper has refilled it to this fraction. */
    public static final float AI_RESUME_FRACTION = 0.3f;
    /** How long one autonomous demand keeps the trigger down: a few ticks, renewed every decision. */
    public static final float AI_LEASE_SECONDS = 0.25f;

    /** The weapon's own feed, at the standard loader. */
    private final RotaryFeedDef authored;
    /** The feed as this machine carries it: its loader's hopper rate and its ready rack's belt. */
    private RotaryFeedDef def;
    private float spin;
    private float coast;
    private float belt;
    private float pending;
    private boolean demandedLastTick;
    private float lease;
    private long leaseTarget;
    private boolean reserveHeld;
    private float sinceRound = Float.POSITIVE_INFINITY;

    /** The weapon's feed at the standard loader, its belt full. */
    public RotaryFeed(RotaryFeedDef def) {
        this(def, LoaderPace.STANDARD, 1f);
    }

    /**
     * The weapon's feed on a machine whose loader refills at {@code loaderFactor} ({@link LoaderPace}) and whose ready
     * rack holds {@code beltFactor} times the belt, its belt full.
     */
    public RotaryFeed(RotaryFeedDef def, float loaderFactor, float beltFactor) {
        if (def == null) throw new IllegalArgumentException("Rotary feed needs its definition");
        this.authored = def;
        this.def = def.fitted(loaderFactor, beltFactor);
        this.belt = this.def.belt();
    }

    /**
     * Re-paces the hopper for the machine's loader ({@link LoaderPace}), as when a mech's loader part is installed;
     * the belt keeps its size and the rounds on it.
     */
    public void fitLoader(float loaderFactor) {
        if (def.beam()) return; // nothing to feed
        def = new RotaryFeedDef(def.spinUpSeconds(), def.coastSeconds(), def.spinDownSeconds(), def.firstTurnRate(),
                def.fullRate(), def.belt(), LoaderPace.refillPerSecond(authored.refillPerSecond(), loaderFactor),
                def.fluxPerRound());
    }

    /** The feed as this machine carries it: the hopper at its loader's rate, the belt its ready rack's size. */
    public RotaryFeedDef def() { return def; }

    /** 0 at rest, 1 at full speed. */
    public float spin() { return spin; }

    /** Whole rounds on the belt; a beam, which has none, reads as never empty. */
    public int beltRounds() { return def.beam() ? Integer.MAX_VALUE : (int) belt; }

    public float beltFraction() { return def.beam() ? 1f : belt / def.belt(); }

    /** Whether this is a beam's emitter rather than a rotary gun's barrels. */
    public boolean beam() { return def.beam(); }

    /**
     * A beam's autonomous reserve: the reservoir now holds {@code share} of its capacity. Above the stop share the AI
     * holds its pulses until the resume share; a rotary gun's reserve is its belt's and ignores this.
     */
    public void observeFlux(float share) {
        if (!def.beam()) return;
        if (share >= RotaryFeedDef.BEAM_AI_STOP_SHARE) reserveHeld = true;
        else if (share <= RotaryFeedDef.BEAM_AI_RESUME_SHARE) reserveHeld = false;
    }

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
        if (!def.beam()) belt = Math.min(def.belt(), belt + def.refillPerSecond() * step);
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
        int ready = def.beam() ? (int) pending : Math.min((int) pending, (int) belt);
        pending -= ready;
        // A dry belt banks no backlog: the next round waits on the hopper, not on the clock.
        if (!def.beam() && belt - ready < 1f) pending = Math.min(pending, 1f);
        return ready;
    }

    /** One requested round actually left the barrel. */
    public void spend() {
        if (!def.beam()) belt = Math.max(0f, belt - 1f);
        sinceRound = 0f;
        updateReserve();
    }

    private void updateReserve() {
        if (def.beam()) return;
        float fraction = beltFraction();
        if (fraction < AI_STOP_FRACTION) reserveHeld = true;
        else if (fraction >= AI_RESUME_FRACTION) reserveHeld = false;
    }
}
