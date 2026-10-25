package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Turning on the spot ({@code convoy-nouns.md}, Neutral pivot). A tank runs its tracks against each other and swings
 * round where it stands; a hover skirt spins on its fans. A wheeled hull's pivot stands for the cramped shunt a
 * driver makes where there is no room for a three-point turn, played as a slow turn in place rather than as the
 * dozen shuffles it would take.
 *
 * <p>A pivot is what a hull does when it has to, never how it prefers to turn, and how readily it does so depends on
 * what it runs on. Tracks and skirts turn on the spot as a matter of course: route proof fillets every bend it can at
 * the chassis's turning circle and pivots one only where no fillet fits ({@link #onRoutes}), and the controller pivots
 * whenever its rolling planner finds no forward plan. A wheeled hull spinning in place reads as wrong, so it never
 * plans one: its routes are the turning circle's alone, and the controller lets it pivot only as the last rung, once
 * it has stood braked without a forward plan for {@link #LAST_RESORT_SECONDS} and its reroute has failed, the moment
 * before an order would be given up. Either way the hull's footprint is swept through the whole rotation, so a pivot
 * is never a way through a wall.
 *
 * <p>{@code -Dbattle.vehicle.neutralPivot=false} is the control: no hull pivots, and the turning circle is the
 * whole answer again. Remove it with the losing path once The Burn's vehicle-order evidence has been read against it.
 */
public final class NeutralPivot {

    public static final String PROPERTY = "battle.vehicle.neutralPivot";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** A tank counter-rotating its tracks: a half turn in about four and a half seconds. */
    static final float TRACKED_DEG_PER_SEC = 40f;
    /** A hover skirt yawing on its fans, quicker than tracks. */
    static final float HOVER_DEG_PER_SEC = 60f;
    /** A wheeled hull's last-resort shunt, slow, so it reads as working the hull round rather than spinning it. */
    static final float WHEELED_DEG_PER_SEC = 15f;

    /**
     * Seconds a wheeled hull stands braked with no forward plan, its reroute already tried, before it may pivot. Inside
     * a move order's give-up time, so the pivot comes before the order is abandoned rather than instead of a reroute.
     */
    static final float LAST_RESORT_SECONDS = 4f;

    /** Heading error (degrees) past which a hull following a corridor without a forward plan stops to pivot. */
    static final float ENGAGE_DEG = 20f;
    /** Heading error (degrees) a pivot turns down to before the hull drives again. */
    static final float ALIGNED_DEG = 3f;
    /** Carrot distance (cells) when following a corridor by pivot: short, so a sharp bend is turned near its vertex. */
    static final float LOOKAHEAD_CELLS = .75f;
    /** Speed (cells a second) below which a hull counts as stopped and may begin to pivot. */
    static final float STANDSTILL_SPEED = .05f;
    /** Forward speed, as a fraction of top speed, while following a corridor by pivot. */
    static final float TRACK_SPEED_FRACTION = .6f;
    /** Largest rotation (degrees) one sweep is asked to prove; a larger one is proved in pieces. */
    private static final float SWEEP_PIECE_DEG = 45f;

    private NeutralPivot() {}

    /** How fast {@code type} turns on the spot, degrees a second; zero when it cannot (or under the control). */
    public static float rateDegPerSec(VehicleType type) {
        if (!ENABLED || type == null) return 0f;
        if (type.isHover()) return HOVER_DEG_PER_SEC;
        if (type.isTank()) return TRACKED_DEG_PER_SEC;
        return WHEELED_DEG_PER_SEC;
    }

    /** Whether {@code type} may turn on the spot at all, if only as a last resort. */
    public static boolean canPivot(VehicleType type) {
        return rateDegPerSec(type) > 0f;
    }

    /**
     * Whether {@code type} pivots as an ordinary manoeuvre, planned into its routes and used whenever it has no forward
     * plan: tracks and hover skirts. A wheeled hull pivots only as the last resort.
     */
    public static boolean onRoutes(VehicleType type) {
        return canPivot(type) && (type.isTank() || type.isHover());
    }

    /**
     * Which way a hull of {@code length} by {@code width} cells standing at ({@code x}, {@code y}) can turn on the
     * spot from {@code fromDeg} to {@code toDeg} with its footprint clear the whole way: {@code +1} counterclockwise,
     * {@code -1} clockwise, {@code 0} not at all. Only the shorter way is asked: a hull turning about its centre looks
     * the same after half a turn, so the longer way sweeps everything the shorter one does and more.
     */
    static int clearDirection(float x, float y, float fromDeg, float toDeg,
                              float length, float width, NavigationGrid grid) {
        float turn = signedDegrees(toDeg - fromDeg);
        if (!rotationClear(x, y, fromDeg, turn, length, width, grid)) return 0;
        return turn >= 0f ? 1 : -1;
    }

    /** Whether the footprint stays clear turning {@code signedDeg} (positive counterclockwise) from {@code fromDeg}. */
    static boolean rotationClear(float x, float y, float fromDeg, float signedDeg,
                                 float length, float width, NavigationGrid grid) {
        int pieces = Math.max(1, (int) Math.ceil(Math.abs(signedDeg) / SWEEP_PIECE_DEG));
        float step = signedDeg / pieces;
        for (int i = 0; i < pieces; i++) {
            Pose a = new Pose(x, y, fromDeg + step * i);
            Pose b = new Pose(x, y, fromDeg + step * (i + 1));
            if (!VehicleTerrainMotion.isSweepFeasible(a, b, length, width, grid)) return false;
        }
        return true;
    }

    /** {@code degrees} wrapped into [0, 360). */
    static float positiveDegrees(float degrees) {
        float wrapped = degrees % 360f;
        return wrapped < 0f ? wrapped + 360f : wrapped;
    }

    /** {@code degrees} wrapped into (-180, 180]. */
    static float signedDegrees(float degrees) {
        float wrapped = positiveDegrees(degrees);
        return wrapped > 180f ? wrapped - 360f : wrapped;
    }
}
