package com.dillon.starsectormarines.battle.drone;

/**
 * Where an escort drone loiters around the ship it screens.
 *
 * <p>A loose ring, not a rank: each drone owns an equal share of the circle,
 * drifts slowly round it, and breathes in and out on its own phase, so a wing
 * reads as a swarm tied to a moving ship rather than as a fixed pattern bolted
 * to it. The ring turns with the hull's heading, rides a little ahead of the
 * hull while at ease, and draws in close to it when the ship is threatened.
 * Pure arithmetic over its arguments: the caller supplies the clock and the
 * per-drone phase, so nothing here is random and nothing is stateful.
 *
 * <p>Angles use the sprite convention everywhere else in the air layer: 0 is
 * +Y, positive is counter-clockwise, and a bearing {@code a} points along
 * {@code (-sin a, cos a)}.
 */
public final class EscortFormation {

    /** Ring radius at ease, in cells; a Shepherd is about four long, so this clears the hull. */
    public static final float EASE_RADIUS = 6.5f;
    /** Ring radius while the ship is threatened. */
    public static final float THREAT_RADIUS = 3.8f;
    /** How far the radius breathes either side of the base, in cells. */
    public static final float BREATH = 1.2f;
    public static final float BREATH_HZ = 0.17f;
    /** How fast the whole ring turns while at ease, degrees per second. */
    public static final float DRIFT_DEG_PER_SEC = 9f;
    /** Half-width of the slow angular wander each drone adds on its own phase, degrees. */
    public static final float WANDER_DEG = 9f;
    /** How far ahead of the hull the ring's centre rides while at ease, in cells. */
    public static final float LEAD_CELLS = 1.5f;

    private EscortFormation() {}

    /**
     * The point slot {@code slot} of {@code count} should loiter at.
     *
     * @param ax anchor x
     * @param ay anchor y
     * @param headingDeg the ship's heading
     * @param seconds simulation clock
     * @param phase this drone's own phase in [0, 1), from {@link DroneHash}
     * @param threatened whether a hostile is inside the ship's reach
     * @return {@code {x, y}}
     */
    public static float[] station(float ax, float ay, float headingDeg, int slot, int count, float seconds,
                                  float phase, boolean threatened) {
        int n = Math.max(1, count);
        double tau = Math.PI * 2.0;
        float drift = threatened ? 0f : seconds * DRIFT_DEG_PER_SEC;
        float wander = WANDER_DEG * (float) Math.sin(tau * (0.11 * seconds + phase));
        float bearing = headingDeg + 360f * slot / n + drift + wander;
        float radius = (threatened ? THREAT_RADIUS : EASE_RADIUS)
                + BREATH * (float) Math.sin(tau * (BREATH_HZ * seconds + phase));
        float lead = threatened ? 0f : LEAD_CELLS;
        double h = Math.toRadians(headingDeg);
        double b = Math.toRadians(bearing);
        float x = ax + lead * (float) -Math.sin(h) + radius * (float) -Math.sin(b);
        float y = ay + lead * (float) Math.cos(h) + radius * (float) Math.cos(b);
        return new float[]{x, y};
    }
}
