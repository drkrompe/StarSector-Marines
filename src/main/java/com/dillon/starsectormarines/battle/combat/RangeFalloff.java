package com.dillon.starsectormarines.battle.combat;

/**
 * Shared distance-to-range falloff math for ballistic weapons. Two effects
 * fall out of a fixed barrel angular error projected onto the ground:
 * <ul>
 *   <li><b>Spread radius</b> grows linearly with distance (angle × dist).
 *       Density on the target cell therefore drops as 1/d² — the inverse-
 *       square-law character users expect from burst weapons. We store the
 *       radius (linear), not the density.</li>
 *   <li><b>Hit probability</b> falls because the same target cell occupies
 *       a smaller fraction of the spread area at long range. Modelled here
 *       as a linear interpolation rather than literal 1/d² so each weapon
 *       has a clean two-knob tuning surface: base accuracy at point-blank
 *       and a {@code accuracyFalloff} fraction lost by max range.</li>
 * </ul>
 *
 * <p>Used by both {@link com.dillon.starsectormarines.battle.weapons.InfantryWeapons} (handheld primaries) and
 * {@link HeavyWeapons} (mech hardpoints) so a chaingun and an SMG share the
 * same physical model — the per-weapon tuning numbers differ, not the math.
 */
public final class RangeFalloff {
    private RangeFalloff() {}

    /**
     * Scatter radius at the given distance. Returns
     * {@code baseSpread * min(1, dist/range)} — linear on radius, clamped to
     * the weapon's tuned ceiling so a beyond-range shot doesn't exceed it.
     * Returns 0 for non-spread weapons ({@code baseSpread <= 0}).
     */
    public static float spread(float baseSpread, float dist, float weaponRange) {
        if (baseSpread <= 0f || weaponRange <= 0f) return 0f;
        float t = dist / weaponRange;
        if (t > 1f) t = 1f;
        return baseSpread * t;
    }

    /**
     * Per-shot hit probability at the given distance. Linearly interpolates
     * {@code baseAccuracy} (at d=0) down to {@code baseAccuracy * (1 -
     * accuracyFalloff)} at {@code dist >= weaponRange}. {@code accuracyFalloff
     * = 0} returns the base accuracy unchanged — opt-in per weapon so callers
     * (militia / aliens / turrets without a per-weapon profile) keep flat
     * accuracy under the same code path.
     */
    public static float accuracy(float baseAccuracy, float accuracyFalloff,
                                 float dist, float weaponRange) {
        if (accuracyFalloff <= 0f || weaponRange <= 0f) return baseAccuracy;
        float t = dist / weaponRange;
        if (t > 1f) t = 1f;
        return baseAccuracy * (1f - accuracyFalloff * t);
    }

    /** Cell distance between two unit cells, treating cells as their centers. */
    public static float dist(int ax, int ay, int bx, int by) {
        float dx = bx - ax;
        float dy = by - ay;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
