package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * A killed Harrier falling out of the sky: carried on along the way it was flying while its speed bleeds off,
 * dropping faster as it goes, and tumbling. It falls for longer the higher it was hit. When it lands its body is
 * laid on the ground there ({@link SwarmFliers}), and from then it is a corpse like any other Rav's.
 *
 * <p>A pure function of what it was doing when it died and how long it has fallen, so the renderer, the shadow and
 * the landing all read the same body. The tumble's direction and rate are seeded off the id.
 */
public final class HarrierFall {

    /** Turn rate of the tumble, degrees a second, before its seeded share of {@link #SPIN_SPREAD}. */
    static final float SPIN_DEG_PER_SEC = 360f;
    static final float SPIN_SPREAD = 300f;
    /** The shortest fall, as a share of {@link HarrierRules#FALL_SECONDS}: a flier hit at the bottom of a dive. */
    static final float LOW_FALL_SHARE = 0.45f;

    public final long id;
    public final HarrierLook look;
    private final float x0, y0, vx, vy, facing0, altitude0, spin, duration;
    private float seconds;

    HarrierFall(long id, HarrierLook look, AirBody body, float altitude) {
        this.id = id;
        this.look = look;
        this.x0 = body.x;
        this.y0 = body.y;
        this.vx = body.vx;
        this.vy = body.vy;
        this.facing0 = body.facingDegrees;
        this.altitude0 = altitude;
        long h = id * 0x9E3779B97F4A7C15L;
        float share = (float) ((h >>> 40) % 1000) / 1000f;
        this.spin = ((h & 1L) == 0 ? 1f : -1f) * (SPIN_DEG_PER_SEC + SPIN_SPREAD * share);
        this.duration = HarrierRules.FALL_SECONDS * (LOW_FALL_SHARE + (1f - LOW_FALL_SHARE) * altitude);
    }

    void advance(float dt) { seconds += dt; }

    /** Whether it has reached the ground. */
    public boolean landed() { return seconds >= duration; }

    /** Seconds it has fallen. */
    public float seconds() { return seconds; }

    /** 0 as it is hit, 1 on the ground. */
    public float progress() { return Math.min(1f, seconds / duration); }

    /** Where it is: its momentum at the hit, bleeding off to nothing by the ground. */
    public float x() { return x0 + vx * drift(); }

    public float y() { return y0 + vy * drift(); }

    /** Height as a share of loiter height: falling faster and faster, to 0 on the ground. */
    public float altitude() {
        float t = progress();
        return altitude0 * (1f - t * t);
    }

    /** Its tumble. */
    public float facing() { return facing0 + spin * Math.min(seconds, duration); }

    /** Which of the corpse drawings it lies as, seeded off the id. */
    public int corpseVariant() {
        return (int) ((id * 0x9E3779B97F4A7C15L >>> 33) % HarrierLook.CORPSE_VARIANTS);
    }

    private float drift() {
        float t = progress();
        return duration * (t - 0.5f * t * t);
    }
}
