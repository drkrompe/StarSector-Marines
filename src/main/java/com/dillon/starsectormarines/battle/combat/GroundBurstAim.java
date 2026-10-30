package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.weapon.BeamWander;

import java.util.Random;

/**
 * Where a manually thrown rocket bursts when it meets nothing on the way: the aim point, scattered.
 * The error disk carries {@link PointAim}'s radius at weapon range and scales with the distance actually
 * thrown, so inside range it reads as the same dispersion the bearing-only round has. Past weapon range it
 * grows faster than linearly, which is the price of letting a player lob a missile beyond what it is rated
 * for rather than refusing the shot.
 */
final class GroundBurstAim {

    /** Extra error-radius growth per weapon range thrown beyond nominal range. */
    static final float OVER_RANGE_GROWTH = 1.5f;

    record Offset(float dx, float dy) { }

    private GroundBurstAim() { }

    /** Error-disk radius in cells for a burst {@code distance} cells from the muzzle. */
    static float errorRadius(float accuracy, float effectiveSpread, float weaponRange, float distance) {
        if (!Float.isFinite(accuracy) || !Float.isFinite(effectiveSpread) || !Float.isFinite(distance)
                || !(weaponRange > 0f) || !Float.isFinite(weaponRange)) {
            throw new IllegalArgumentException("Ground-burst aim parameters must be finite with positive range");
        }
        float clampedAccuracy = Math.max(0f, Math.min(1f, accuracy));
        float atRange = PointAim.BASE_ERROR_CELLS + Math.max(0f, effectiveSpread)
                + PointAimDispersion.WEAPON.accuracyErrorCells * (1f - clampedAccuracy);
        float fraction = Math.max(0f, distance) / weaponRange;
        return atRange * fraction * (1f + OVER_RANGE_GROWTH * Math.max(0f, fraction - 1f));
    }

    /** A uniform sample of the error disk around the aim point. */
    static Offset sample(float accuracy, float effectiveSpread, float weaponRange, float distance, Random rng) {
        return sample(accuracy, effectiveSpread, weaponRange, distance, null, rng);
    }

    /**
     * As above; a crawling beam ({@code wander} non-null) takes its point of the disk from its path, uniform over a
     * hold, so its pulses crawl around the aim point rather than each landing anywhere in the disk.
     */
    static Offset sample(float accuracy, float effectiveSpread, float weaponRange, float distance,
                         BeamWander wander, Random rng) {
        float error = errorRadius(accuracy, effectiveSpread, weaponRange, distance);
        if (wander != null) {
            BeamWander.Sample path = wander.sample(rng);
            return new Offset(path.diskX() * error, path.diskY() * error);
        }
        float radius = (float) Math.sqrt(rng.nextFloat()) * error;
        float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
        return new Offset(radius * (float) Math.cos(angle), radius * (float) Math.sin(angle));
    }
}
