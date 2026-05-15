package com.dillon.starsectormarines.battle;

/**
 * One troop-drop shuttle. Separate from {@link Unit} because shuttles are
 * cinematic / spawn-source entities — they fly in fractional world space, rotate
 * freely, and don't pathfind through the grid or participate in combat. The
 * sim ticks their state machine and deboards marines while {@link com.dillon.starsectormarines.ops.BattleScreen}
 * reads {@code worldX}/{@code worldY}/{@code facingDegrees} for the sprite.
 *
 * <p>Lifecycle: PENDING (waiting on stagger) → INCOMING (flying from off-map
 * entry to LZ) → LANDED (deboarding marines on {@code deboardInterval} cadence)
 * → DEPARTING (flying back off-map) → GONE.
 *
 * <p>Coord convention matches {@link Unit#cellX}/{@link Unit#cellY}: cell-units
 * with Y up. Entry/exit points sit outside the grid (negative or > gridH) so
 * the shuttle reads as off-screen before INCOMING and after DEPARTING.
 */
public class Shuttle {

    public enum State { PENDING, INCOMING, LANDED, DEPARTING, GONE }

    public final ShuttleType type;
    public final Faction faction;

    public final float lzX;
    public final float lzY;
    public final float entryX;
    public final float entryY;
    public final float exitX;
    public final float exitY;

    public State state = State.PENDING;
    public float worldX;
    public float worldY;
    public float pendingDelay;
    public float deboardCountdown;
    public int marinesRemaining;

    /**
     * Sprite rotation in Starsector convention — 0° = sprite's drawn orientation
     * (nose points to screen-north / +Y), positive rotates counterclockwise
     * (90° = nose points west). Computed from velocity during INCOMING/DEPARTING,
     * held constant while LANDED.
     */
    public float facingDegrees;

    /**
     * Render scale multiplier. 1.0 on the ground; rises to {@code CRUISE_SCALE}
     * while at altitude so the shuttle reads as "bigger because higher" during
     * cruise. Lerped along the leg so it shrinks during landing and grows
     * during takeoff.
     */
    public float scaleMult = 1f;

    /**
     * Normalized progress (0..1) along the current INCOMING or DEPARTING leg.
     * Set to 0 at state entry and advanced each tick by
     * {@code flightSpeed * TICK_DT / legChordLength}. Used by the sim to drive
     * both position (via the curved-path formula) and {@link #scaleMult}.
     */
    public float legProgress = 0f;

    /** Cached straight-line distance between the current leg's endpoints. */
    public float legChordLength = 1f;

    /**
     * Perpendicular bow amplitude (cells) for the current leg's flight path.
     * 0 means a straight line; larger values arc out further from the straight
     * line. Combined with {@link #curveSide} ({@code ±1}) to give a smooth sin²
     * deflection that's zero at both endpoints.
     */
    public float curveStrength = 0f;
    public int curveSide = 1;

    /**
     * Accumulated phase (radians) for the in-flight scale wobble. Advances each
     * tick during INCOMING/DEPARTING at {@code 2π·SHUTTLE_OSCILLATION_HZ·dt}.
     * Per-shuttle random offset at leg setup so the three drops don't pulse
     * in sync.
     */
    public float flightPhase = 0f;

    /**
     * Facing captured at the INCOMING → LANDED transition. Used as the start
     * point of the DEPARTING facing ease — the shuttle smoothly rotates from
     * this angle into the new leg's tangent rather than snapping. Without
     * this, takeoff reads as an instant 180° flip.
     */
    public float landedFacing = 0f;

    public Shuttle(ShuttleType type, Faction faction,
                   float lzX, float lzY,
                   float entryX, float entryY,
                   float exitX, float exitY,
                   float pendingDelay) {
        this.type = type;
        this.faction = faction;
        this.lzX = lzX;
        this.lzY = lzY;
        this.entryX = entryX;
        this.entryY = entryY;
        this.exitX = exitX;
        this.exitY = exitY;
        this.pendingDelay = pendingDelay;
        this.worldX = entryX;
        this.worldY = entryY;
        this.marinesRemaining = type.capacity;
        this.facingDegrees = facingTowards(entryX, entryY, lzX, lzY);
    }

    public boolean isVisible() {
        return state == State.INCOMING || state == State.LANDED || state == State.DEPARTING;
    }

    /**
     * Returns the Starsector setAngle value (degrees, CCW from north) for a
     * sprite traveling from ({@code fromX,fromY}) to ({@code toX,toY}) in our
     * Y-up cell-coord space. Math: angle of velocity vector minus 90° because
     * Starsector's sprite default is north-facing while math's 0° is east.
     */
    public static float facingTowards(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        float mathDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        return mathDeg - 90f;
    }
}
