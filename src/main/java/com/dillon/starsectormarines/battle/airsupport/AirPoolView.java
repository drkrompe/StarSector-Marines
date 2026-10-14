package com.dillon.starsectormarines.battle.airsupport;

import java.util.List;

/**
 * An immutable snapshot of one side's air pool for a display: what it holds by kind, what is in the air and on what
 * errand, when the next airframe is replaced and what it has recently lost. Taken from
 * {@link AirPoolService#view}; reading it mutates nothing and it never changes after it is made.
 *
 * @param kinds         one row per declared kind, in {@link AirPoolKind} order; empty when the side has no pool
 * @param sorties       every airborne sortie, oldest first
 * @param callsGoingOut airframes reserved for an operator's call that has not yet launched
 * @param recentLosses  airframes shot down within {@link #RECENT_LOSS_SECONDS} of the view's tick
 */
public record AirPoolView(List<KindRow> kinds, List<SortieRow> sorties, int callsGoingOut, int recentLosses) {

    /** How far back "recent" reaches for {@link #recentLosses}, in sim seconds. */
    public static final float RECENT_LOSS_SECONDS = 60f;

    /** A side with no pool: nothing to show. */
    public static final AirPoolView NONE = new AirPoolView(List.of(), List.of(), 0, 0);

    /** What a sortie is for. A pool sortie that names no other errand is a strike. */
    public enum Mission { STRIKE, STRAFE, COVER }

    /**
     * One kind's stock.
     *
     * @param available       airframes that could be tasked now
     * @param outFlying       airframes on a sortie or reserved for a call in progress
     * @param cap             the establishment (available plus out never exceeds it)
     * @param lost            airframes shot down over the battle
     * @param nextAirframeSeconds sim seconds until the pool replaces one, or a negative number when it is not
     *                        replacing (at establishment, or a side that never replaces losses)
     */
    public record KindRow(AirPoolKind kind, int available, int outFlying, int cap, int lost,
                          float nextAirframeSeconds) {}

    /** One airborne airframe, and what it was sent to do. */
    public record SortieRow(AirPoolKind kind, Mission mission) {}

    public AirPoolView {
        kinds = List.copyOf(kinds);
        sorties = List.copyOf(sorties);
        callsGoingOut = Math.max(0, callsGoingOut);
        recentLosses = Math.max(0, recentLosses);
    }

    /** Whether the side holds a pool at all. */
    public boolean hasPool() {
        return !kinds.isEmpty();
    }
}
