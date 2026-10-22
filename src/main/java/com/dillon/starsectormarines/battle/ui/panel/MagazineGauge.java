package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * What a controlled Marine's magazine looks like, round by round: the strip under the weapon row and
 * the ring of pips beside the cursor both draw this, so the two can never disagree.
 *
 * <p>Rounds are counted from the first to leave the gun. A belt is drawn in blocks of one trigger pull's
 * burst, which is what turns fifty rounds back into something read at a glance: six blocks and a bit.
 * While reloading, the rounds pressed back in so far read as {@link Round#LOADING} and the rest as spent.
 *
 * @param loaded rounds left in the load
 * @param group rounds per block, one for a weapon that fires single rounds
 * @param reloadProgress how far the reload has come, from 0 to 1; negative when not reloading
 */
public record MagazineGauge(Kind kind, int capacity, int loaded, int group, float reloadProgress) {

    /** How a round is drawn: a rifle cartridge, a shotgun shell, or a belt link. */
    public enum Kind { RIFLE, SHELL, LINK }

    public enum Round { LIVE, SPENT, LOADING }

    /** The ring's span either side of the cursor's right-hand side, in degrees. */
    public static final float RING_HALF_SPAN_DEGREES = 100f;
    /** The gap between belt blocks on the ring, in degrees. */
    public static final float RING_GROUP_GAP_DEGREES = 5f;

    public MagazineGauge {
        capacity = Math.max(0, capacity);
        loaded = Math.max(0, Math.min(capacity, loaded));
        group = Math.max(1, group);
    }

    /**
     * The gauge for a magazine-fed primary, or {@code null} for one without a magazine. A weapon that
     * throws several projectiles a pull shows shells; one that bursts shows a belt in blocks of its burst.
     */
    public static MagazineGauge of(WeaponDef weapon, EquipmentGrade grade, int loaded,
                                   float reloadRemaining, float reloadDuration) {
        if (weapon == null || weapon.magazine() == null) return null;
        return of(weapon.projectilesPerShot, weapon.burstCount, weapon.magazine().size(grade), loaded,
                reloadRemaining, reloadDuration);
    }

    /** The same gauge from a weapon's packet and burst sizes, for callers that hold only its status. */
    public static MagazineGauge of(int projectilesPerShot, int burstCount, int capacity, int loaded,
                                   float reloadRemaining, float reloadDuration) {
        Kind kind = projectilesPerShot > 1 ? Kind.SHELL : burstCount > 1 ? Kind.LINK : Kind.RIFLE;
        float progress = reloadRemaining > 0f && reloadDuration > 0f
                ? Math.max(0f, Math.min(1f, 1f - reloadRemaining / reloadDuration)) : -1f;
        return new MagazineGauge(kind, capacity, loaded, kind == Kind.LINK ? burstCount : 1, progress);
    }

    public boolean reloading() { return reloadProgress >= 0f; }

    public Round round(int index) {
        if (reloading()) return index < Math.round(capacity * reloadProgress) ? Round.LOADING : Round.SPENT;
        return index < loaded ? Round.LIVE : Round.SPENT;
    }

    /** Whether a block ends after this round, so the next one is set apart. */
    public boolean groupEnd(int index) {
        return group > 1 && (index + 1) % group == 0 && index + 1 < capacity;
    }

    /** A quarter of the load or less, and at least the last round: time to think about reloading. */
    public boolean low() {
        return !reloading() && loaded <= Math.max(1, capacity / 4);
    }

    /** Degrees each pip takes on the ring, gaps between blocks left out. */
    public float pipStepDegrees() {
        int gaps = group > 1 ? (capacity - 1) / group : 0;
        return capacity == 0 ? 0f
                : (2f * RING_HALF_SPAN_DEGREES - gaps * RING_GROUP_GAP_DEGREES) / capacity;
    }

    /** Where a pip starts on the ring, measured from the top of its span. */
    public float pipStartDegrees(int index) {
        int gapsBefore = group > 1 ? index / group : 0;
        return -RING_HALF_SPAN_DEGREES + index * pipStepDegrees() + gapsBefore * RING_GROUP_GAP_DEGREES;
    }
}
