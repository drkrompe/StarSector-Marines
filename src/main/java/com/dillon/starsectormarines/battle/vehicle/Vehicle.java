package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * One ground transport — the air {@code Shuttle}'s ground analog
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
 * a {@link com.dillon.starsectormarines.battle.world.gen.road.RoadGraph} edge
 * sequence; they live in fractional world space the same way shuttle
 * lzX/lzY does. The terminal waypoint is the LZ — body teleports to it on
 * arrival, then transitions to LANDED.
 */
public class Vehicle {

    public enum State { PENDING, INCOMING, LANDED, OVERWATCH, DEPARTING, GONE }

    public final VehicleType type;
    public final Faction faction;

    /**
     * World entity id, assigned when this vehicle is adopted into the battle
     * {@link com.dillon.starsectormarines.battle.sim.ConvoyService} at
     * {@link GroundSystem#add}; {@code 0L} before adoption. The vehicle's identity
     * ({@link #type}/{@link #faction}) and kinematics ({@link #body}) are aliased into
     * the world's {@code GROUND_IDENTITY} / {@code GROUND_KINEMATICS} columns under
     * this id, while this handle stays authoritative for lifecycle / turret / deboard
     * state through the aliasing phases of the convoy-{@code Vehicle}-into-world epic
     * ({@code roadmap/ecs-migration/stories/vehicle-into-world.md}). The world entity is
     * destroyed at terminal {@link State#GONE}, after which this id is dead (nothing
     * reads a GONE vehicle by id).
     */
    public long entityId = 0L;

    /** Inbound path's cell-center coords. {@link #lzX}/{@link #lzY} repeat the last entry as a convenience. Mutable — may be replaced by a re-plan. */
    public float[] inboundX;
    public float[] inboundY;
    /** Outbound path's cell-center coords. Same shape as inbound; usually inbound reversed for V1. Mutable — may be replaced by a re-plan. */
    public float[] outboundX;
    public float[] outboundY;

    /** LZ position — terminal waypoint of {@link #inboundX}. */
    public final float lzX;
    public final float lzY;

    public State state = State.PENDING;
    public float pendingDelay;
    public float deboardCountdown;
    public int marinesRemaining;
    /** Sim-seconds remaining in OVERWATCH before transitioning to DEPARTING. Initialized from {@link VehicleType#overwatchDurationSec} on entering OVERWATCH. */
    public float overwatchCountdown;

    /** Kinematic state — position, facing, model-specific. Driven each tick by {@link VehicleController} via pure-pursuit + per-variant {@link GroundBody} model. The renderer and turret loop read the pose off this body. */
    public final GroundBody body;

    /**
     * Owns this vehicle's motion (corridor cursor, playback/docking state,
     * recovery). Assigned by {@link GroundSystem#add} when the vehicle joins
     * the system; {@code null} only before then. See
     * {@code navigation-rework/overview.md}.
     */
    public VehicleController controller;

    /**
     * Per-battle routing inputs, stashed by the spawn layer ({@code ConvoyMeans})
     * so the recovery ladder can re-route mid-drive ("lap around" a stuck spot)
     * via {@link VehicleRoutePlanner}. Both {@code null} for vehicles that aren't
     * cost-field-routed (e.g. legacy/debug spawns) — re-route is then skipped.
     */
    public TerrainCostField routeCostField;
    public VehicleClearance routeClearance;

    /** Turret barrel facing in world frame (0° = +Y, positive CCW). Driven by {@link com.dillon.starsectormarines.battle.turret.TurretAim} when the vehicle has a {@link VehicleType#turretKind}. */
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
     * Lazily set to a fresh id on the first successful deboard; {@link Entity#NO_SQUAD}
     * means "no squad has been created for this vehicle yet."
     */
    public int squadId = Entity.NO_SQUAD;

    public static final int HISTORY_SIZE = 120;
    public final float[] histX = new float[HISTORY_SIZE];
    public final float[] histY = new float[HISTORY_SIZE];
    public final float[] histFacing = new float[HISTORY_SIZE];
    public final float[] histSpeed = new float[HISTORY_SIZE];
    public final float[] histStuck = new float[HISTORY_SIZE];
    public final byte[] histState = new byte[HISTORY_SIZE];
    public int histHead = 0;
    public int histCount = 0;

    public void recordTick() {
        histX[histHead] = body.x;
        histY[histHead] = body.y;
        histFacing[histHead] = body.facingDegrees;
        histSpeed[histHead] = body.speed;
        histStuck[histHead] = (controller != null) ? controller.wallStuckTime() : 0f;
        histState[histHead] = (byte) state.ordinal();
        histHead = (histHead + 1) % HISTORY_SIZE;
        if (histCount < HISTORY_SIZE) histCount++;
    }

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
        // The controller starts its corridor cursor at index 1 (already at
        // index 0, steering toward index 1); see VehicleController.tick.
    }

    /** True when the vehicle is on-map and rendered. */
    public boolean isVisible() {
        return state == State.INCOMING || state == State.LANDED
                || state == State.OVERWATCH || state == State.DEPARTING;
    }
}
