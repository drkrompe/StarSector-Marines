package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * The troop-delivery <b>mission</b> of a convoy ground vehicle — the lifecycle
 * state machine and all the per-run state, the {@code VEHICLE_MISSION} component
 * of a ground-craft entity. The ground twin of the air {@link com.dillon.starsectormarines.battle.air.ShuttleMission}:
 * the shared vehicle core ({@code entityId}, {@link VehicleType}/{@link com.dillon.starsectormarines.battle.unit.Faction}
 * identity in {@code GROUND_IDENTITY}, {@link GroundBody} kinematics in
 * {@code GROUND_KINEMATICS}, the {@link GroundTurret} in {@code GROUND_TURRET})
 * stays mission-agnostic and is reached <em>by id</em> through
 * {@link com.dillon.starsectormarines.battle.sim.ConvoyService} — this bag holds
 * <b>no</b> body / identity / turret / id reference. The behaviour that drives it
 * is {@link GroundSystem}'s state-machine tick.
 *
 * <p>Lifecycle: PENDING (off-map, waiting on stagger) → INCOMING (consuming the
 * inbound waypoint queue) → LANDED (deboarding militia on {@link VehicleType#deboardInterval}
 * cadence at the LZ) → optional OVERWATCH (armed loiter) → DEPARTING (consuming
 * the outbound queue) → GONE. No hover analog — ground vehicles drop off and leave.
 *
 * <p>Waypoints are cell-center coordinates (cellX + 0.5, cellY + 0.5) along a
 * {@link com.dillon.starsectormarines.battle.world.gen.road.RoadGraph} edge
 * sequence, in fractional world space. The terminal inbound waypoint is the LZ
 * ({@link #lzX}/{@link #lzY}) — the body teleports to it on arrival.
 */
public final class VehicleMission {

    public VehicleState state = VehicleState.PENDING;

    /** Inbound path's cell-center coords. {@link #lzX}/{@link #lzY} repeat the last entry as a convenience. Mutable — may be replaced by a re-plan. */
    public float[] inboundX;
    public float[] inboundY;
    /** Outbound path's cell-center coords. Same shape as inbound; usually inbound reversed for V1. Mutable — may be replaced by a re-plan. */
    public float[] outboundX;
    public float[] outboundY;

    /** LZ position — terminal waypoint of {@link #inboundX}. */
    public final float lzX;
    public final float lzY;

    public float pendingDelay;
    public float deboardCountdown;
    public int marinesRemaining;
    /** Sim-seconds remaining in OVERWATCH before transitioning to DEPARTING. Initialized from {@link VehicleType#overwatchDurationSec} on entering OVERWATCH. */
    public float overwatchCountdown;

    /**
     * Owns this vehicle's motion (corridor cursor, playback/docking state,
     * recovery). Assigned by {@link GroundSystem#add} when the vehicle joins the
     * system; {@code null} only before then. Holds refs to this mission plus the
     * kinematics / variant resolved by id at construction. See
     * {@code navigation-rework/overview.md}. (The air side has no controller —
     * a future epic statelessifies this like {@code AirSteeringSystem}.)
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

    /**
     * Per-deboard loadouts for this delivery. {@code marineLoadout[i]} is the spec
     * for the (i+1)-th marine to disembark; null entries (and a null array) fall
     * back to a plain {@link MarineLoadout#COMBATANT}. Defender-side militia squads
     * typically use one loadout for the whole truck — same array slot repeated.
     */
    public MarineLoadout[] marineLoadout;

    /**
     * Optional override for the {@link UnitType} stamped on each deboarded
     * passenger. {@code null} (default) means {@code GroundSystem.tryDeboardMarine}
     * picks {@code FactionUnitRoster.forFaction(faction).infantry()}. Symmetric to
     * {@link com.dillon.starsectormarines.battle.air.ShuttleMission#deboardUnitType}.
     */
    public UnitType deboardUnitType;

    /**
     * Squad identity assigned to all marines deboarded from this vehicle. Lazily
     * set to a fresh id on the first successful deboard; {@link Entity#NO_SQUAD}
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

    /**
     * Appends one debug-history frame from the current pose. The body is passed in
     * (not held) because kinematics live in {@code GROUND_KINEMATICS}, reached by
     * id — {@link GroundSystem} supplies it from {@code convoy.body(id)}.
     */
    public void recordTick(GroundBody body) {
        histX[histHead] = body.x;
        histY[histHead] = body.y;
        histFacing[histHead] = body.facingDegrees;
        histSpeed[histHead] = body.speed;
        histStuck[histHead] = (controller != null) ? controller.wallStuckTime() : 0f;
        histState[histHead] = (byte) state.ordinal();
        histHead = (histHead + 1) % HISTORY_SIZE;
        if (histCount < HISTORY_SIZE) histCount++;
    }

    public VehicleMission(float[] inboundX, float[] inboundY,
                          float[] outboundX, float[] outboundY,
                          float pendingDelay, int marinesRemaining) {
        if (inboundX.length != inboundY.length || inboundX.length < 2) {
            throw new IllegalArgumentException("inbound path must have at least 2 matched waypoints");
        }
        if (outboundX.length != outboundY.length || outboundX.length < 2) {
            throw new IllegalArgumentException("outbound path must have at least 2 matched waypoints");
        }
        this.inboundX = inboundX;
        this.inboundY = inboundY;
        this.outboundX = outboundX;
        this.outboundY = outboundY;
        this.lzX = inboundX[inboundX.length - 1];
        this.lzY = inboundY[inboundY.length - 1];
        this.pendingDelay = pendingDelay;
        this.marinesRemaining = marinesRemaining;
    }

    /** True when the vehicle is on-map and rendered. */
    public boolean isVisible() {
        return state == VehicleState.INCOMING || state == VehicleState.LANDED
                || state == VehicleState.OVERWATCH || state == VehicleState.DEPARTING;
    }
}
