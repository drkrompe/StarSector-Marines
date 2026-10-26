package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterWing;

/**
 * One carrier bay's airframe across a battle: the rule that makes a bay's air
 * cover a renewable strength rather than a stock that runs out.
 *
 * <p>The bay keeps one airframe. It is on deck until its clock runs down, then
 * airborne until the sortie ends. How the sortie ends decides the next clock:
 * an airframe that comes home rearms for the wing's {@link FighterWing#refitSec},
 * and one that is shot down is replaced after the wing's
 * {@link FighterWing#replacementSec}. Either way it flies again — nothing in
 * the cycle ever retires the bay.
 *
 * <p>Pure state, no simulation: {@link AirCoverSystem} says when a sortie
 * launched, came home or was lost, and asks whether the bay is ready.
 */
public final class FighterBay {

    /** Where the bay's airframe is in its cycle. */
    public enum State {
        /** On deck awaiting its first launch. */
        ON_DECK,
        /** Over the battle. */
        AIRBORNE,
        /** Home and rearming. */
        REFITTING,
        /** Shot down; the bay is readying a replacement. */
        REPLACING
    }

    private final float refitSec;
    private final float replacementSec;
    private State state = State.ON_DECK;
    private float clock;

    public FighterBay(float firstLaunchSec, float refitSec, float replacementSec) {
        this.clock = Math.max(0f, firstLaunchSec);
        this.refitSec = refitSec;
        this.replacementSec = replacementSec;
    }

    public static FighterBay of(FighterWing wing) {
        return new FighterBay(wing.firstArrivalSec, wing.refitSec, wing.replacementSec);
    }

    /** Runs the deck clock down; an airborne airframe's clock does not run. */
    public void tick(float dt) {
        if (state != State.AIRBORNE && clock > 0f) clock = Math.max(0f, clock - dt);
    }

    /** Whether an airframe is on deck and may launch now. */
    public boolean ready() {
        return state != State.AIRBORNE && clock <= 0f;
    }

    public void launched() {
        state = State.AIRBORNE;
    }

    /** The airframe left the battle under its own power; it rearms. */
    public void cameHome() {
        if (state != State.AIRBORNE) return;
        state = State.REFITTING;
        clock = refitSec;
    }

    /** The airframe was shot down; the bay replaces it. */
    public void lost() {
        if (state != State.AIRBORNE) return;
        state = State.REPLACING;
        clock = replacementSec;
    }

    public State state() { return state; }

    /** Sim-seconds until the bay can launch; 0 when ready or airborne. */
    public float secondsToLaunch() {
        return state == State.AIRBORNE ? 0f : clock;
    }
}
