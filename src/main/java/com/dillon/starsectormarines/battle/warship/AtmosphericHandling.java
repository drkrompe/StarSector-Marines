package com.dillon.starsectormarines.battle.warship;

/**
 * How a warship handles in atmosphere: the hull table's space figures, made heavy.
 *
 * <p>A hull's table row is a space ship's, and a frigate in space darts: a Wolf comes to full speed in about a second
 * and swings sixty degrees a second. Over a ground battle that read as an arcade fighter, not a ship. In atmosphere
 * every hull is a lumbering giant, so its figures are pulled toward a battleship's and nothing is authored per hull:
 *
 * <ul>
 *   <li><b>Top speed and turn rate</b> are compressed toward the reference battleship's ({@link #REFERENCE_SPEED},
 *   {@link #REFERENCE_TURN}): a hull no faster than the reference keeps its table figure, and a faster one keeps
 *   only the square root of how much faster it is. The ladder survives (a frigate still outruns and outturns a
 *   cruiser) but a frigate is two to three times a battleship's pace rather than six. Every hull then keeps only
 *   {@link #SPEED_SHARE} of that speed and {@link #TURN_SHARE} of that turn: a giant barely moving.</li>
 *   <li><b>Acceleration, braking and turn acceleration</b> are the table's, but never faster than coming to the
 *   new top speed (or turn rate) in {@link #SPOOL_SECONDS} ({@link #TURN_SPOOL_SECONDS}): every hull carries its
 *   way, takes seconds to gather it and seconds to shed it, and settles into a turn rather than pivoting.</li>
 * </ul>
 *
 * <p>All figures in and out are the table's units (game units per second and degrees per second); the caller
 * converts travel to cells. Speeds a mission or a manoeuvre derives from the hull's (a low pass's burn, a departure)
 * follow from these, so they are slowed with it.
 */
public final class AtmosphericHandling {

    /** The battleship's table top speed, game units per second, that faster hulls are compressed toward. */
    public static final float REFERENCE_SPEED = 25f;
    /** The battleship's table turn rate, degrees per second, that faster hulls are compressed toward. */
    public static final float REFERENCE_TURN = 4f;
    /**
     * The share of the compressed top speed a hull keeps in atmosphere. A body drawn at a giant's size has to move
     * like one: a frigate takes the better part of ten seconds to cover its own length and a capital most of a
     * minute, so a heavy ship's position changes over many seconds rather than across a frame.
     */
    public static final float SPEED_SHARE = 0.5f;
    /** The share of the compressed turn rate a hull keeps in atmosphere. */
    public static final float TURN_SHARE = 0.6f;
    /** No hull comes to its top speed, or stops from it, in less than this. */
    public static final float SPOOL_SECONDS = 6f;
    /** No hull comes to its turn rate, or out of it, in less than this. */
    public static final float TURN_SPOOL_SECONDS = 4f;

    /** One hull's handling in atmosphere, in table units. */
    public record Figures(float maxSpeed, float accel, float decel, float turnRate, float turnAccel) {}

    private AtmosphericHandling() {}

    /** {@code -Dbattle.warship.atmosphericHandling=false} is the control that flies every hull on its table figures. */
    static final boolean ENABLED = !"false".equals(System.getProperty("battle.warship.atmosphericHandling"));

    /** The atmospheric figures for a hull whose table row gives these. */
    public static Figures of(float maxSpeed, float accel, float decel, float turnRate, float turnAccel) {
        if (!ENABLED) return new Figures(maxSpeed, accel, decel, turnRate, turnAccel);
        float speed = compress(maxSpeed, REFERENCE_SPEED) * SPEED_SHARE;
        float turn = compress(turnRate, REFERENCE_TURN) * TURN_SHARE;
        return new Figures(speed,
                Math.min(accel, speed / SPOOL_SECONDS),
                Math.min(decel, speed / SPOOL_SECONDS),
                turn,
                Math.min(turnAccel, turn / TURN_SPOOL_SECONDS));
    }

    /** {@code value} unchanged at or below {@code reference}; above it, the reference times the root of the ratio. */
    static float compress(float value, float reference) {
        if (value <= reference) return value;
        return reference * (float) Math.sqrt(value / reference);
    }
}
