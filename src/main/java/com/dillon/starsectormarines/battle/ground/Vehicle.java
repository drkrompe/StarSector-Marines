package com.dillon.starsectormarines.battle.ground;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapons.MarineLoadout;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * One ground transport — analog of {@link com.dillon.starsectormarines.battle.air.Shuttle}
 * for trucks/APCs that path through the road network instead of flying. The
 * sim ticks its {@link GroundBody} (kinematic model selected per-variant by
 * {@link VehicleType#createBody()}); the renderer reads {@code body.x},
 * {@code body.y}, {@code body.facingDegrees} for the sprite — same field
 * names as the air-side {@code AirBody} so renderer code stays interchangeable.
 *
 * <p>Lifecycle: PENDING (off-map, waiting on stagger) → INCOMING (consuming
 * the inbound waypoint queue, head waypoint = current goal) → LANDED
 * (deboarding militia on {@code type.deboardInterval} cadence at the LZ) →
 * DEPARTING (consuming a reversed-or-explicit outbound queue) → GONE. No
 * loiter / hover analog — ground vehicles drop off and leave.
 *
 * <p>Waypoints are cell-center coordinates (cellX + 0.5, cellY + 0.5) along
 * a {@link com.dillon.starsectormarines.battle.mapgen.road.RoadGraph} edge
 * sequence; they live in fractional world space the same way shuttle
 * lzX/lzY does. The terminal waypoint is the LZ — body teleports to it on
 * arrival, then transitions to LANDED.
 */
public class Vehicle {

    public enum State { PENDING, INCOMING, LANDED, OVERWATCH, DEPARTING, GONE }

    public final VehicleType type;
    public final Faction faction;

    /** Inbound path's cell-center coords. {@link #lzX}/{@link #lzY} repeat the last entry as a convenience. */
    public final float[] inboundX;
    public final float[] inboundY;
    /** Outbound path's cell-center coords. Same shape as inbound; usually inbound reversed for V1. */
    public final float[] outboundX;
    public final float[] outboundY;

    /** LZ position — terminal waypoint of {@link #inboundX}. */
    public final float lzX;
    public final float lzY;

    public State state = State.PENDING;
    public float pendingDelay;
    public float deboardCountdown;
    public int marinesRemaining;

    /** Kinematic state — position, facing, model-specific. Driven each tick by {@link GroundSystem} via pure-pursuit + per-variant {@link GroundBody} model. */
    public final GroundBody body;

    /** Current waypoint index inside the active queue (inbound during INCOMING, outbound during DEPARTING). */
    public int waypointIndex;

    /** Turret barrel facing in world frame (0° = +Y, positive CCW). Driven by {@link com.dillon.starsectormarines.battle.ai.TurretAim} when the vehicle has a {@link VehicleType#turretKind}. */
    public float turretFacingDeg;
    /** Sim-seconds until the turret can fire again. */
    public float turretCooldownTimer;
    /** Entity id of the currently locked target, or {@code 0L} when idle. */
    public long turretTargetId;
    /** Rounds remaining in the turret magazine. Initialized from {@link com.dillon.starsectormarines.battle.turret.TurretKind#startingAmmo}. */
    public int turretAmmo;
    /** Rounds left in the current burst (excluding the trigger-pull round). */
    public int turretBurstRemaining;
    /** Sim-seconds until the next burst round fires. */
    public float turretBurstTimer;
    /** Entity id of the target locked when the current burst started. */
    public long turretBurstTargetId;

    /**
     * Per-deboard loadouts for this delivery. {@code marineLoadout[i]} is
     * the spec for the (i+1)-th marine to disembark; null entries (and a
     * null array) fall back to a plain {@link MarineLoadout#COMBATANT}.
     * Defender-side militia squads typically use one loadout for the whole
     * truck — same array slot repeated — but per-slot variation is supported
     * for future "officer in the lead seat" tweaks.
     */
    public MarineLoadout[] marineLoadout;

    /**
     * Optional override for the {@link com.dillon.starsectormarines.battle.unit.UnitType}
     * stamped on each deboarded passenger. {@code null} (default) means
     * {@code GroundSystem.tryDeboardMarine} picks
     * {@code FactionUnitRoster.forFaction(faction).infantry()}. Symmetric to
     * {@code Shuttle.deboardUnitType}; lets a convoy disgorge the right tier
     * for the requesting side rather than baking marine stats into every
     * truck.
     */
    public com.dillon.starsectormarines.battle.unit.UnitType deboardUnitType;

    /**
     * Squad identity assigned to all marines deboarded from this vehicle.
     * Lazily set to a fresh id on the first successful deboard; {@link Unit#NO_SQUAD}
     * means "no squad has been created for this vehicle yet."
     */
    public int squadId = Unit.NO_SQUAD;

    /**
     * Active Reeds-Shepp docking path, or {@code null} if not in docking mode.
     * When non-null, {@link GroundSystem} bypasses pure-pursuit and plays the
     * body's pose along the sampled path at constant docking speed. Set once
     * by {@link GroundSystem} when the inbound truck enters the LZ approach
     * window; cleared on arrival.
     */
    public ReedsShepp.Path dockingPath;
    public Pose dockingStartPose;
    public float dockingTurnRadius;
    /** Cells traveled along {@link #dockingPath} so far. */
    public float dockingProgressCells;
    /** Goal facing applied to the body on terminal snap-to-LZ. */
    public float dockingGoalFacingDeg;

    public Vehicle(VehicleType type, Faction faction,
                   float[] inboundX, float[] inboundY,
                   float[] outboundX, float[] outboundY,
                   float pendingDelay) {
        if (inboundX.length != inboundY.length || inboundX.length < 2) {
            throw new IllegalArgumentException("inbound path must have at least 2 matched waypoints");
        }
        if (outboundX.length != outboundY.length || outboundX.length < 2) {
            throw new IllegalArgumentException("outbound path must have at least 2 matched waypoints");
        }
        this.type = type;
        this.faction = faction;
        this.inboundX = inboundX;
        this.inboundY = inboundY;
        this.outboundX = outboundX;
        this.outboundY = outboundY;
        this.lzX = inboundX[inboundX.length - 1];
        this.lzY = inboundY[inboundY.length - 1];
        this.pendingDelay = pendingDelay;
        this.marinesRemaining = type.capacity;
        this.turretAmmo = type.turretKind != null ? type.turretKind.startingAmmo : 0;
        this.body = type.createBody();
        // Spawn at the inbound queue's first waypoint, facing the second so
        // the truck reads as already rolling when it appears on-screen.
        float spawnX = inboundX[0];
        float spawnY = inboundY[0];
        float nextX = inboundX[1];
        float nextY = inboundY[1];
        body.teleport(spawnX, spawnY, AirBody.facingToward(nextX - spawnX, nextY - spawnY));
        this.waypointIndex = 1;  // already at index 0, steering toward index 1
    }

    /** True when the vehicle is on-map and rendered. */
    public boolean isVisible() {
        return state == State.INCOMING || state == State.LANDED
                || state == State.OVERWATCH || state == State.DEPARTING;
    }
}
