package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;

/**
 * The leaves across a bay's opening, and how far apart they stand.
 *
 * <p><b>The door answers the boats; nothing orders it.</b> A boat leaving its
 * berth calls the door open before it rolls, so the leaves are clear by the
 * time it reaches the opening; a boat coming home calls it open on the
 * approach. Once no boat on a transit through it is near enough to need it,
 * the door closes again. It is presentation only: the opening is vacuum to
 * the simulation either way, and nothing waits on the leaves.
 *
 * <p>Openness travels at a fixed rate, so a door that is called back while
 * closing reverses from where it stands rather than jumping.
 */
public final class BayDoor {

    /** Seconds from shut to fully open, and back. */
    public static final float TRAVEL_SECONDS = 1.6f;
    /** How far outside the hull a departing boat still holds the door open. */
    static final float DEPARTURE_CLEAR_CELLS = 8f;
    /** How far out a returning boat calls the door open. */
    static final float RETURN_CALL_CELLS = 16f;
    /** How far inside a returning boat must be before the door may close behind it. */
    static final float RETURN_CLEAR_CELLS = 3f;

    private final BayAperture door;
    private float openness;

    public BayDoor(BayAperture door) {
        if (door == null) throw new IllegalArgumentException("a door needs its opening");
        this.door = door;
    }

    public BayAperture aperture() { return door; }

    /** 0 shut, 1 fully open, linear in time. */
    public float openness() { return openness; }

    /** How far each leaf has withdrawn into its pocket, eased so the leaves start and stop gently. */
    public float leafTravel() {
        float t = openness;
        return t * t * (3f - 2f * t);
    }

    /**
     * Whether a boat on a transit at {@code (x, y)} needs this door open.
     *
     * @param through the opening the boat's transit runs through
     * @param returning whether the boat is coming home rather than leaving
     */
    public boolean calledBy(BayAperture through, boolean returning, float x, float y) {
        if (through == null || through.compartmentId() != door.compartmentId()) return false;
        float out = (x - door.centerX()) * door.outDx() + (y - door.centerY()) * door.outDy();
        return returning
                ? out > -RETURN_CLEAR_CELLS && out < RETURN_CALL_CELLS
                : out < DEPARTURE_CLEAR_CELLS;
    }

    /** Moves the leaves toward open while called and toward shut otherwise. */
    public void advance(float seconds, boolean called) {
        if (!(seconds > 0f)) return;
        float step = seconds / TRAVEL_SECONDS;
        openness = called ? Math.min(1f, openness + step) : Math.max(0f, openness - step);
    }
}
