package com.dillon.starsectormarines.battle.weapon;

/**
 * The one rule a machine's loader paces its ammunition by (mechs-nouns.md, the loader): a mech's loader part and a
 * vehicle's accelerated-loader kit both multiply the refill rate of every ammunition-bearing weapon the machine
 * carries, a missile rack's trigger packs, a gun's magazine and a rotary gun's hopper alike.
 *
 * <p>A weapon's own authored rate is the standard loader's ({@link #STANDARD}), so a stock fit fires as it is
 * authored; the accelerated loader puts a pack back in {@link #ACCELERATED_TIME_SHARE} of that time. A machine with no
 * loader leaves a belt or magazine at the weapon's own rate (a hopper is the gun's), and only missile packs, which the
 * loader fabricates, stop coming back.</p>
 */
public final class LoaderPace {

    /** The standard loader: the weapon's own authored rate. */
    public static final float STANDARD = 1f;
    /** The accelerated loader's time per pack as a share of the standard loader's. */
    public static final float ACCELERATED_TIME_SHARE = .75f;
    /** The accelerated loader's refill rate as a multiple of the standard loader's. */
    public static final float ACCELERATED = 1f / ACCELERATED_TIME_SHARE;

    private LoaderPace() { }

    /**
     * The refill multiplier of a loader that takes {@code timeShare} of the standard loader's time per pack; the
     * standard rate for a share that is not a positive finite number.
     */
    public static float ofTimeShare(float timeShare) {
        return timeShare > 0f && Float.isFinite(timeShare) ? 1f / timeShare : STANDARD;
    }

    /** {@code standardRate} (rounds a second at the standard loader) under a loader of {@code factor}. */
    public static float refillPerSecond(float standardRate, float factor) {
        return standardRate * factor;
    }

    /** {@code standardSeconds} per pack under a loader of {@code factor}. */
    public static float secondsPerPack(float standardSeconds, float factor) {
        return standardSeconds / factor;
    }
}
