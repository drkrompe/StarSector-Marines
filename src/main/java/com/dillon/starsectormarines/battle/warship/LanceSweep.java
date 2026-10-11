package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * One swing of a warship's lance: a beam laid on the ground from the hull's
 * position, its far end walked across an arc over a stated time.
 *
 * <p>The plan is immutable and says nothing about who is standing where. It is
 * committed whole — the arc is fixed when the sweep is laid, like a gun run's
 * line, and is not re-aimed while it burns — which is what makes it something
 * a body on the ground can run from. A telegraph precedes the burn for the
 * same reason: the line is drawn before it is lit.
 *
 * <p>See {@code air-nouns.md}, <b>Overhead ships</b>.
 */
public record LanceSweep(
        long shooterId,
        Faction faction,
        float originX,
        float originY,
        float startAngle,
        float endAngle,
        float reach,
        float halfWidth,
        float telegraphSeconds,
        float burnSeconds,
        float damagePerSecond,
        float penetration,
        int wallDamagePerTick,
        String weaponId) {

    public LanceSweep {
        if (reach <= 0f || halfWidth <= 0f) {
            throw new IllegalArgumentException("a lance needs reach and width");
        }
        if (burnSeconds <= 0f || telegraphSeconds < 0f) {
            throw new IllegalArgumentException("a lance needs a burn and a non-negative telegraph");
        }
    }

    /** A lance with no weapon behind it, drawn in the renderer's own colours. */
    public LanceSweep(long shooterId, Faction faction, float originX, float originY,
                      float startAngle, float endAngle, float reach, float halfWidth,
                      float telegraphSeconds, float burnSeconds, float damagePerSecond,
                      float penetration, int wallDamagePerTick) {
        this(shooterId, faction, originX, originY, startAngle, endAngle, reach, halfWidth,
                telegraphSeconds, burnSeconds, damagePerSecond, penetration, wallDamagePerTick, null);
    }

    /** Total seconds from commitment to the beam going out. */
    public float totalSeconds() {
        return telegraphSeconds + burnSeconds;
    }

    /**
     * Bearing of the beam {@code elapsed} seconds after commitment. Held at
     * the start through the telegraph, then walked at constant angular rate and
     * held at the end.
     */
    public float angleAt(float elapsed) {
        float t = (elapsed - telegraphSeconds) / burnSeconds;
        t = Math.max(0f, Math.min(1f, t));
        return startAngle + (endAngle - startAngle) * t;
    }

    /** True from the end of the telegraph until the burn ends. */
    public boolean isBurning(float elapsed) {
        return elapsed >= telegraphSeconds && elapsed < totalSeconds();
    }

    /** Distance between successive impact centres along the beam. */
    public float sampleSpacing() {
        return halfWidth * 1.7f;
    }

    /** Number of impact centres laid along the beam, from the hull outward. */
    public int sampleCount() {
        return Math.max(1, (int) Math.ceil(reach / sampleSpacing()));
    }

    /** X of the {@code i}th impact centre of a beam at {@code angle}. */
    public float sampleX(float angle, int i) {
        return originX + (float) Math.cos(angle) * Math.min(reach, (i + 1) * sampleSpacing());
    }

    /** Y of the {@code i}th impact centre of a beam at {@code angle}. */
    public float sampleY(float angle, int i) {
        return originY + (float) Math.sin(angle) * Math.min(reach, (i + 1) * sampleSpacing());
    }
}
