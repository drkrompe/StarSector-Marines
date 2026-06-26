package com.dillon.starsectormarines.battle.combat;

import java.util.Random;

/**
 * Single source of truth for where a fired round ends up visually, around a
 * target. Shared across {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons#fireShot},
 * {@link com.dillon.starsectormarines.battle.infantry.InfantryWeapons#fireSecondary}, and
 * {@link HeavyWeapons#fireMechWeapon} so all three live by the same rules.
 *
 * <ul>
 *   <li><b>Targeting</b> uses the target's smooth render position (the world
 *       {@code RENDER_POSITION} component, passed in by the caller), not the
 *       logical {@code cellX}/{@code cellY}. Tracers terminate on the
 *       sprite, not on the cell the sprite is lerping toward.</li>
 *   <li><b>Hits</b> land near the target with a universal
 *       {@link #HIT_JITTER_BASELINE} plus the weapon's distance-scaled
 *       {@code effectiveSpread}. Damage was already applied against the
 *       target's {@code Entity} reference — this is pure visual, so even
 *       tight-spread weapons (DMR, militia single-shot) get organic
 *       near-center variance instead of robotic dead-center hits.</li>
 *   <li><b>Misses</b> scatter in the {@link #MISS_OFFSET_MIN}..
 *       {@link #MISS_OFFSET_MAX} ring around the target, additively widened
 *       by {@code effectiveSpread} so a stray long-range round wanders
 *       further than a stray close-range one.</li>
 * </ul>
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
    public static final float HIT_JITTER_BASELINE = 0.20f;

    /** Resolved endpoint pair. Returned as a record so callers stay readable; JIT scalar-replaces these on the hot path. */
    public record Endpoint(float x, float y) {}

    private ShotEndpoint() {}

    /**
     * Resolve the visual endpoint for a shot around a target whose sprite center
     * is ({@code targetRenderX} + 0.5, {@code targetRenderY} + 0.5) — the caller
     * passes the target's smooth render position (its world {@code RENDER_POSITION}
     * read by id), so tracers terminate on the sprite, not the logical cell.
     * {@code effectiveSpread} is the weapon's distance-scaled scatter radius
     * (see {@link RangeFalloff#spread}); 0 for weapons without a hitSpread profile.
     */
    public static Endpoint resolve(float targetRenderX, float targetRenderY, boolean hit, float effectiveSpread, Random rng) {
        float cx = targetRenderX + 0.5f;
        float cy = targetRenderY + 0.5f;
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
                cx + (float) Math.cos(angle) * radius,
                cy + (float) Math.sin(angle) * radius);
    }
}
