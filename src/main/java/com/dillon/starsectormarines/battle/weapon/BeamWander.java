package com.dillon.starsectormarines.battle.weapon;

import java.util.Random;

/**
 * The slow drift of a held beam's aim (`mechs-nouns.md`, Beam weapons): one continuous path that consecutive pulses
 * sample, so the lance crawls across its target instead of jumping to a fresh random offset every pulse.
 *
 * <p>The path is a pair of independent standard-normal coordinates, each interpolated between knots
 * {@link #periodSeconds} apart with a smoothstep and renormalised so that every instant is exactly standard normal,
 * not only the knots. {@link #sample} turns the pair into a <b>share</b> {@code 1 - exp(-|g|^2 / 2)} and a bearing:
 * for a standard-normal pair the share is uniform on [0, 1) and independent of the bearing, which is uniform. The aim
 * rules that used to draw a fresh radius and bearing per round read these instead, so over a long hold a beam spends
 * exactly the weapon's authored accuracy and spread: the same hit chance, the same error disk, the same mean miss.
 * Only the order of the pulses changes: neighbours now lie next to each other on the path.
 *
 * <p>The path advances by beam time ({@link #advance}), one pulse spacing per pulse fired, so a released trigger
 * freezes it and the next press resumes the crawl where the last lance left off. Its seed is drawn once from the
 * battle's seeded stream on the first pulse ({@link #sample}); every knot after that is a hash of the seed and the
 * knot's index, so the path never consumes the battle stream again and plays the same serially or side by side.
 *
 * <p>{@code -Dbattle.mech.pdBeamWander=false} is the control: no beam builds a wander, and every pulse draws its own
 * offset as it did before. Temporary A/B lever: remove it with the per-pulse path once the owner has accepted the
 * crawl in play.
 */
public final class BeamWander {

    /** Whether held beams crawl ({@code battle.mech.pdBeamWander}); see the class note. */
    public static final boolean ON = Boolean.parseBoolean(System.getProperty("battle.mech.pdBeamWander", "true"));

    /**
     * Knots are standard-normal draws clipped to this many deviations, which bounds how fast the path can move. Only
     * 0.27% of draws are clipped, so the share stays uniform to within that.
     */
    public static final float KNOT_LIMIT = 3f;

    /**
     * The fastest the path's disk point ({@link Sample#diskX}, {@link Sample#diskY}) can move, in disk radii per
     * period at amplitude 1: two knots {@link #KNOT_LIMIT} apart on both axes blend at most 6 / sqrt(1/2) = 8.49 per
     * unit weight at mid-segment, where the smoothstep runs at 1.5 per unit time, and the map from the normal pair to
     * the disk is at most 1 / sqrt(2) steep: 8.49 * 1.5 * sqrt(2) / sqrt(2) = 12.73. A pulse therefore moves the
     * lance at most this times its spacing over the period; the typical step is a twentieth of it.
     */
    public static final double MAX_DISK_STEP_PER_PERIOD = 12.75;

    /** Smallest normal pair treated as having a bearing; below it the share is zero to float precision anyway. */
    private static final double MIN_RADIUS = 1e-9;

    private final float periodSeconds;
    private final float amplitude;
    private long seed;
    private boolean seeded;
    private double seconds;

    /**
     * A path whose knots are {@code periodSeconds} of beam time apart, scaled by {@code amplitude}: 1 spends the
     * weapon's error budget exactly, below 1 holds the lance nearer the aim point, above 1 nearer the rim.
     */
    public BeamWander(float periodSeconds, float amplitude) {
        if (!(periodSeconds > 0f) || !Float.isFinite(periodSeconds) || !(amplitude > 0f) || !Float.isFinite(amplitude)) {
            throw new IllegalArgumentException("Beam wander needs a positive period and amplitude");
        }
        this.periodSeconds = periodSeconds;
        this.amplitude = amplitude;
    }

    /** A path already seeded with {@code seed}, as {@link #sample} would seed it. */
    public static BeamWander seeded(float periodSeconds, float amplitude, long seed) {
        BeamWander wander = new BeamWander(periodSeconds, amplitude);
        wander.seed = seed;
        wander.seeded = true;
        return wander;
    }

    public float periodSeconds() { return periodSeconds; }

    public float amplitude() { return amplitude; }

    /** Moves the path on by {@code beamSeconds} of firing; a released trigger does not call it. */
    public void advance(float beamSeconds) {
        if (beamSeconds > 0f && Float.isFinite(beamSeconds)) seconds += beamSeconds;
    }

    /**
     * Where the path is now. The first call seeds it with one draw from {@code rng}, the battle's seeded stream; later
     * calls draw nothing.
     */
    public Sample sample(Random rng) {
        if (!seeded) {
            seed = rng.nextLong();
            seeded = true;
        }
        double t = seconds / periodSeconds;
        long knot = (long) Math.floor(t);
        double f = t - knot;
        double w = f * f * (3.0 - 2.0 * f);
        double gx = blend(knot(seed, knot, 0), knot(seed, knot + 1, 0), w);
        double gy = blend(knot(seed, knot, 1), knot(seed, knot + 1, 1), w);
        double radius = Math.hypot(gx, gy);
        double scaled = amplitude * radius;
        float share = (float) -Math.expm1(-0.5 * scaled * scaled);
        if (radius < MIN_RADIUS) return new Sample(share, 1f, 0f);
        return new Sample(Math.min(share, Math.nextDown(1f)), (float) (gx / radius), (float) (gy / radius));
    }

    /**
     * Two knots blended at {@code w} (a smoothstep weight) and renormalised: {@code a(1-w) + bw} of two independent
     * standard normals has variance {@code (1-w)^2 + w^2}, so dividing by its root keeps every instant standard normal.
     */
    static double blend(double a, double b, double w) {
        double u = 1.0 - w;
        return (a * u + b * w) / Math.sqrt(u * u + w * w);
    }

    /** Knot {@code index} on {@code axis}: a clipped standard normal, a pure function of the seed. */
    static double knot(long seed, long index, int axis) {
        long h = mix(seed ^ mix(index * 2L + axis + 0x632BE59BD9B4E019L));
        double u1 = ((h >>> 11) + 1L) * 0x1.0p-53; // (0, 1]
        double u2 = (mix(h) >>> 11) * 0x1.0p-53;    // [0, 1)
        double normal = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        return Math.max(-KNOT_LIMIT, Math.min(KNOT_LIMIT, normal));
    }

    /** SplitMix64's finaliser. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * One instant of the path: a {@code share} in [0, 1), uniform over a long hold, and a bearing ({@code cos},
     * {@code sin}), uniform and independent of it. An aim rule that draws {@code u} uniform and a uniform angle reads
     * {@code share} and the bearing instead.
     */
    public record Sample(float share, float cos, float sin) {

        /** A point of the unit disk, uniform over a long hold: the radius {@code sqrt(share)} on the bearing. */
        public float diskX() { return (float) Math.sqrt(share) * cos; }

        public float diskY() { return (float) Math.sqrt(share) * sin; }
    }
}
