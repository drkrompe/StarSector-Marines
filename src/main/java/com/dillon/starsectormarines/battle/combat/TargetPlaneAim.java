package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.BeamWander;

import java.util.Random;

/**
 * Samples one committed aim result in the plane perpendicular to a shot's
 * ground direction. Lateral error becomes real XY divergence; elevation error
 * becomes the lightweight Z axis used by {@link BallisticResolver}.
 */
final class TargetPlaneAim {

    /** Extra clearance beyond the target silhouette for an authored miss. */
    static final float MISS_CLEARANCE_MIN = 0.20f;
    static final float MISS_CLEARANCE_MAX = 0.80f;
    /** Organic near-center variance for an intended hit, before weapon spread. */
    private static final float HIT_JITTER_BASELINE = 0.20f;
    private static final float AXIS_EPSILON = 1e-6f;

    /**
     * {@code heightFraction} is where an on-target sample landed up the sampled pattern, 0 at its
     * foot and 1 at its top, uniform over hits; it picks the body part. A miss carries 0.5.
     */
    record Sample(boolean onTarget, float lateral, float elevation, float heightFraction) {}

    private TargetPlaneAim() {}

    static Sample sample(float finalAccuracy, float incomingAccuracyMult,
                         float effectiveSpread, float horizontalRadius,
                         float verticalHalfHeight, Random rng) {
        float chance = clamp01(finalAccuracy * incomingAccuracyMult);
        boolean onTarget = rng.nextFloat() < chance;
        float horizontal = Math.max(AXIS_EPSILON, horizontalRadius);
        float vertical = Math.max(AXIS_EPSILON, verticalHalfHeight);

        if (onTarget) {
            float jitter = HIT_JITTER_BASELINE + Math.max(0f, effectiveSpread);
            float lateralMax = Math.min(horizontal * 0.90f, jitter);
            float elevationMax = Math.min(vertical * 0.90f, jitter);
            float lateral = signed(rng.nextFloat()) * lateralMax;
            float heightFraction = rng.nextFloat();
            float elevation = signed(heightFraction) * elevationMax;
            return new Sample(true, lateral, elevation, heightFraction);
        }

        float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
        float lateralDir = (float) Math.cos(angle);
        float elevationDir = (float) Math.sin(angle);
        float toHorizontalEdge = Math.abs(lateralDir) > AXIS_EPSILON
                ? horizontal / Math.abs(lateralDir)
                : Float.POSITIVE_INFINITY;
        float toVerticalEdge = Math.abs(elevationDir) > AXIS_EPSILON
                ? vertical / Math.abs(elevationDir)
                : Float.POSITIVE_INFINITY;
        float boundaryDistance = Math.min(toHorizontalEdge, toVerticalEdge);
        float clearance = MISS_CLEARANCE_MIN
                + rng.nextFloat() * (MISS_CLEARANCE_MAX - MISS_CLEARANCE_MIN)
                + Math.max(0f, effectiveSpread);
        float distance = boundaryDistance + clearance;
        return new Sample(false, lateralDir * distance, elevationDir * distance, 0.5f);
    }

    /**
     * Share of a crawling beam's miss path ({@link #crawl}) spent sliding from the rim of the hit box off the
     * silhouette's edge: a few pulses of a lance leaving its target, rather than a jump past it.
     */
    static final float CRAWL_SLIDE_SHARE = 0.15f;

    /**
     * The committed aim of one pulse of a crawling beam ({@link BeamWander}): {@link #sample}'s hit chance, hit box
     * and mean miss, read off the beam's continuous path instead of fresh draws, so that neighbouring pulses land next
     * to each other and a lance slides off its target rather than jumping.
     *
     * <p>The path's share decides the hit as the hit roll did, with the same chance: below it the pulse is on the
     * target, in {@link #sample}'s hit box (the weapon's spread about the centre, inside the silhouette) at a point
     * reached by an area-preserving map of the unit disk, so the box fills as evenly as before and the height it lands
     * at, and with it the body part, is uniform. At and above the chance the pulse misses on the same bearing: the
     * first {@link #CRAWL_SLIDE_SHARE} of the miss path slides from the box's rim to just past the silhouette's edge
     * ({@link #MISS_CLEARANCE_MIN}), the rest runs out to a far clearance set so the mean miss past the silhouette is
     * {@link #sample}'s ({@link #MISS_CLEARANCE_MIN} to {@link #MISS_CLEARANCE_MAX}, plus the spread).
     */
    static Sample crawl(float finalAccuracy, float incomingAccuracyMult, float effectiveSpread,
                        float horizontalRadius, float verticalHalfHeight, BeamWander.Sample path) {
        float chance = clamp01(finalAccuracy * incomingAccuracyMult);
        float horizontal = Math.max(AXIS_EPSILON, horizontalRadius);
        float vertical = Math.max(AXIS_EPSILON, verticalHalfHeight);
        float jitter = HIT_JITTER_BASELINE + Math.max(0f, effectiveSpread);
        float lateralMax = Math.min(horizontal * 0.90f, jitter);
        float elevationMax = Math.min(vertical * 0.90f, jitter);
        if (path.share() < chance) {
            float s = (float) Math.sqrt(path.share() / chance);
            float[] square = diskToSquare(s * path.cos(), s * path.sin());
            return new Sample(true, square[0] * lateralMax, square[1] * elevationMax, (square[1] + 1f) * 0.5f);
        }
        float t = chance < 1f ? (path.share() - chance) / (1f - chance) : 0f;
        float[] rim = diskToSquare(path.cos(), path.sin());
        float rimX = rim[0] * lateralMax;
        float rimY = rim[1] * elevationMax;
        float start = (float) Math.hypot(rimX, rimY);
        float dirX = start > AXIS_EPSILON ? rimX / start : 1f;
        float dirY = start > AXIS_EPSILON ? rimY / start : 0f;
        // The silhouette's edge on this bearing, as the per-pulse rule measures it.
        float toHorizontalEdge = Math.abs(dirX) > AXIS_EPSILON ? horizontal / Math.abs(dirX) : Float.POSITIVE_INFINITY;
        float toVerticalEdge = Math.abs(dirY) > AXIS_EPSILON ? vertical / Math.abs(dirY) : Float.POSITIVE_INFINITY;
        float boundary = Math.min(toHorizontalEdge, toVerticalEdge);
        float edge = boundary + MISS_CLEARANCE_MIN;
        float meanMiss = boundary + 0.5f * (MISS_CLEARANCE_MIN + MISS_CLEARANCE_MAX) + Math.max(0f, effectiveSpread);
        // The slide's mean is its midpoint; the run beyond is uniform out to the far edge that brings the whole path's
        // mean to the per-pulse rule's.
        float slide = CRAWL_SLIDE_SHARE;
        float far = (2f * meanMiss - slide * (start + edge)) / (1f - slide) - edge;
        float distance = t < slide
                ? start + t / slide * (edge - start)
                : edge + (t - slide) / (1f - slide) * (far - edge);
        return new Sample(false, dirX * distance, dirY * distance, 0.5f);
    }
    /**
     * Shirley and Chiu's concentric map run backwards: a point of the unit disk to the point of the square
     * [-1, 1]^2 it came from. It preserves area, so a uniform disk lands uniform on the square, and it is continuous,
     * so a path across the disk stays a path across the square.
     */
    static float[] diskToSquare(float x, float y) {
        double r = Math.hypot(x, y);
        if (r < 1e-12) return new float[] {0f, 0f};
        double phi = Math.atan2(y, x);
        if (phi < -Math.PI / 4) phi += 2 * Math.PI;
        double quarter = Math.PI / 4;
        double a;
        double b;
        if (phi < quarter) {
            a = r;
            b = phi / quarter * r;
        } else if (phi < 3 * quarter) {
            b = r;
            a = -(phi - Math.PI / 2) / quarter * r;
        } else if (phi < 5 * quarter) {
            a = -r;
            b = -(phi - Math.PI) / quarter * r;
        } else {
            b = -r;
            a = (phi - 3 * Math.PI / 2) / quarter * r;
        }
        return new float[] {(float) a, (float) b};
    }

    private static float signed(float unit) {
        return unit * 2f - 1f;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
