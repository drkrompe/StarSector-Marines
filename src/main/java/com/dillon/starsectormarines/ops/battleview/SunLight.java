package com.dillon.starsectormarines.ops.battleview;

/**
 * Where the sun is, for everything in the scene that casts.
 *
 * <p>It started as three fields on {@link GroundParallaxPipeline} because the
 * ground composite was the only thing that cast anything. It is not a property
 * of that composite: a bearing and an elevation belong to the scene, and the
 * moment a second caster exists the two have to agree about them or the ground
 * and the things standing on it are lit by different suns.
 *
 * <p>Angles rather than a vector, because that is what a person setting one
 * reaches for — a bearing and a time of day — and because the elevation is the
 * only number that decides reach. A {@code h}-metre object lays down
 * {@code h / tan(elevation)} metres of shadow, and one cell is one metre
 * ({@link com.dillon.starsectormarines.battle.air.AirScale#METERS_PER_CELL}), so
 * {@link #reachCells} is the whole of the geometry.
 *
 * <p>Presentation only. Nothing here reaches simulation: a shadow is not sight,
 * does not darken a unit, and does not gate what anybody can see.
 */
public final class SunLight {

    /**
     * How hard cast shadows read. Zero is not merely an untinted shadow — the
     * ground composite also collapses its off-view margin at zero, so the whole
     * feature costs nothing when it is dialled off.
     */
    public static final float MIN_SHADOW_STRENGTH = 0f;
    public static final float MAX_SHADOW_STRENGTH = 1f;
    public static final float DEFAULT_SHADOW_STRENGTH = 0.75f;

    /**
     * Bearing the sun sits at, in world-cell space, degrees anticlockwise from
     * +X. The default puts it over the player's left shoulder so shadows fall
     * down and to the right — the direction a reader of a top-down map expects
     * depth to lie in.
     */
    public static final float MIN_AZIMUTH_DEGREES = 0f;
    public static final float MAX_AZIMUTH_DEGREES = 360f;
    public static final float DEFAULT_AZIMUTH_DEGREES = 135f;

    /**
     * Height above the horizon, in degrees.
     *
     * <p>Floored well above zero because reach is a tangent: the last few
     * degrees run away to hundreds of cells, which is a shadow that covers the
     * map and, for the ground composite, a texture margin nobody can afford.
     */
    public static final float MIN_ELEVATION_DEGREES = 12f;
    public static final float MAX_ELEVATION_DEGREES = 85f;
    public static final float DEFAULT_ELEVATION_DEGREES = 38f;

    /** Full-shadow colour multiplier: darker, and cooler, as ground lit only by sky rather than sun. */
    public static final float TINT_R = 0.46f;
    public static final float TINT_G = 0.50f;
    public static final float TINT_B = 0.62f;

    private float azimuthDegrees = DEFAULT_AZIMUTH_DEGREES;
    private float elevationDegrees = DEFAULT_ELEVATION_DEGREES;
    private float shadowStrength = DEFAULT_SHADOW_STRENGTH;

    public float azimuthDegrees() { return azimuthDegrees; }

    public float elevationDegrees() { return elevationDegrees; }

    public float shadowStrength() { return shadowStrength; }

    /** Applies to the next rendered frame. */
    public void setAzimuthDegrees(float degrees) {
        if (Float.isNaN(degrees)) return;
        azimuthDegrees = clamp(degrees, MIN_AZIMUTH_DEGREES, MAX_AZIMUTH_DEGREES);
    }

    /** Applies to the next rendered frame; a lower sun reaches further. */
    public void setElevationDegrees(float degrees) {
        if (Float.isNaN(degrees)) return;
        elevationDegrees = clamp(degrees, MIN_ELEVATION_DEGREES, MAX_ELEVATION_DEGREES);
    }

    /** Applies to the next rendered frame; crossing zero also resizes the ground composite's height target. */
    public void setShadowStrength(float strength) {
        if (Float.isNaN(strength)) return;
        shadowStrength = clamp(strength, MIN_SHADOW_STRENGTH, MAX_SHADOW_STRENGTH);
    }

    /** True when anything should cast at all. */
    public boolean casts() {
        return shadowStrength > 0f;
    }

    /** X of the unit vector pointing <em>toward</em> the sun, in world cells. */
    public float dirX() {
        return (float) Math.cos(Math.toRadians(azimuthDegrees));
    }

    /** Y of the unit vector pointing <em>toward</em> the sun, in world cells. */
    public float dirY() {
        return (float) Math.sin(Math.toRadians(azimuthDegrees));
    }

    /** Metres a sun ray climbs per cell travelled toward it: {@code tan(elevation)}. */
    public float risePerCell() {
        return (float) Math.tan(Math.toRadians(elevationDegrees));
    }

    /** How far something {@code meters} tall lays its shadow, in cells. One cell is one metre. */
    public float reachCells(float meters) {
        if (meters <= 0f) return 0f;
        return meters / risePerCell();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return "SunLight[" + azimuthDegrees + " deg az, " + elevationDegrees
                + " deg el, strength " + shadowStrength + "]";
    }
}
