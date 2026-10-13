package com.dillon.starsectormarines.battle.battery;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Who holds a fortress battery's fire control, and whether it is being fought over. A pure state machine: it is told,
 * once per tick, which side stands at the control point, and answers with who controls the guns.
 *
 * <p>The rules are the compound capture rules reduced to one point and written for sides, never for marines or
 * defenders: the holder is whoever holds it, a hostile presence with no holder alongside contests it, an uncontested
 * hostile presence that stays long enough takes it, and a holder left alone pushes the contest back. A contested
 * battery is silent, because nobody has the control; a taken one fires for its new holder. Allied presence counts
 * as the holder's side through {@link Faction#friendlyTo}, and the holder is recorded as one of the two fighting sides
 * (an ally who takes a point takes it for the marines).
 */
public final class FireControl {

    /** Seconds of uncontested hostile presence that take the point. */
    public static final float CAPTURE_SECONDS = 12f;
    /** Seconds a holder left alone (or an empty point) needs to push a contest back to nothing. */
    public static final float RESTORE_SECONDS = 8f;

    private Faction holder;
    private boolean contested;
    private Faction challenger;
    private float progress;

    public FireControl(Faction holder) {
        this.holder = fightingSide(holder);
    }

    /** The side whose guns these are now. */
    public Faction holder() {
        return holder;
    }

    /** Whether the point is being fought over, which silences the guns. */
    public boolean contested() {
        return contested;
    }

    /** 0 to 1: how far a challenger has got towards taking the point; 0 when nobody is. */
    public float captureProgress() {
        return contested ? progress : 0f;
    }

    /** The side working to take the point, or {@code null} when uncontested. */
    public Faction challenger() {
        return contested ? challenger : null;
    }

    /** Whether the guns may fire: held and not under contest. */
    public boolean operational() {
        return !contested;
    }

    /**
     * Advances one tick.
     *
     * @param holderPresent whether any of the holder's side stands at the point
     * @param hostile       the side of a hostile presence at the point, or {@code null} when there is none
     */
    public void tick(float dt, boolean holderPresent, Faction hostile) {
        Faction hostileSide = hostile == null ? null : fightingSide(hostile);
        if (!contested) {
            if (hostileSide != null) {
                contested = true;
                challenger = hostileSide;
                progress = 0f;
            }
            return;
        }
        if (hostileSide != null && !holderPresent) {
            challenger = hostileSide;
            progress += dt / CAPTURE_SECONDS;
            if (progress >= 1f) {
                holder = challenger;
                contested = false;
                challenger = null;
                progress = 0f;
            }
        } else if (hostileSide == null) {
            // The holder alone, or nobody at all: the point drifts back to its holder.
            progress -= dt / RESTORE_SECONDS;
            if (progress <= 0f) {
                contested = false;
                challenger = null;
                progress = 0f;
            }
        }
        // Both sides present: the contest holds where it is.
    }

    /** Which of the two fighting sides a faction stands with; an ally stands with the marines. */
    static Faction fightingSide(Faction faction) {
        return Faction.MARINE.friendlyTo(faction) ? Faction.MARINE : faction;
    }
}
