package com.dillon.starsectormarines.battle.combat;

import java.util.Random;

/**
 * Legacy target-relative scatter for indirect LRM artillery. Ground direct
 * fire uses {@link TargetPlaneAim} through {@link BallisticResolver}; this
 * helper intentionally remains only for the indirect procedure.
 *
 * <p>Targeting uses the target's smooth center position supplied by the
 * caller. Hits land within a small organic baseline plus distance-scaled
 * spread; misses land in the {@link #MISS_OFFSET_MIN}..
 * {@link #MISS_OFFSET_MAX} ring, widened by that same spread. The returned
 * point is both the projectile endpoint and its eventual detonation center.
 */
public final class ShotEndpoint {

    /** Min near-miss ring radius (cells) for missed shots. */
    public static final float MISS_OFFSET_MIN = 0.5f;
    /** Max near-miss ring radius (cells) for missed shots. */
    public static final float MISS_OFFSET_MAX = 2.0f;
    /**
     * Universal hit-endpoint jitter (cells). Every hit lands somewhere
     * inside this radius around the target's sprite center, additively with
     * any weapon-specific spread — keeps DMR shots from being pixel-perfect
     * lock-ons every time.
     */
    private static final float HIT_JITTER_BASELINE = 0.20f;

    /** Resolved endpoint pair. Returned as a record so callers stay readable; JIT scalar-replaces these on the hot path. */
    public record Endpoint(float x, float y) {}

    private ShotEndpoint() {}

    /**
     * Resolve the visual endpoint for a shot around a target whose sprite center
     * is ({@code targetX}, {@code targetY}) — the caller passes the target's
     * smooth center position ({@code World.renderX}/{@code renderY}, the
     * tolerant center-based reads, sampled by id), so tracers terminate on the
     * sprite, not the logical cell. {@code effectiveSpread} is the weapon's
     * distance-scaled scatter radius (see {@link RangeFalloff#spread}); 0 for
     * weapons without a hitSpread profile.
     */
    public static Endpoint resolve(float targetX, float targetY, boolean hit, float effectiveSpread, Random rng) {
        float angle = rng.nextFloat() * (float) (Math.PI * 2);
        float radius;
        if (hit) {
            radius = rng.nextFloat() * (HIT_JITTER_BASELINE + effectiveSpread);
        } else {
            radius = MISS_OFFSET_MIN
                    + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN)
                    + effectiveSpread;
        }
        return new Endpoint(
                targetX + (float) Math.cos(angle) * radius,
                targetY + (float) Math.sin(angle) * radius);
    }
}
