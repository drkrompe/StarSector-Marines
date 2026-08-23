package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.squad.Squad;
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
 * <p>Waypoints are cell-center coordinates ({@code cellX + 0.5},
 * {@code cellY + 0.5}) forming the cost-routed advisory corridor, with optional
 * off-map entry/exit tails. The terminal inbound waypoint names the LZ
 * ({@link #lzX}/{@link #lzY}); travel is body-driven through ordinary tracking
 * and the validated terminal docking phase until the final arrival tolerance,
 * where the state transition applies a small terminal snap as a fallback.
 */
public final class VehicleMission {

    public VehicleState state = VehicleState.PENDING;

    /** Inbound path's cell-center coords. {@link #lzX}/{@link #lzY} repeat the last entry as a convenience. Mutable — may be replaced by a re-plan. */
    public float[] inboundX;
    public float[] inboundY;
    /** Outbound path's cell-center coords. Same shape as inbound. Mutable — may be replaced by a re-plan. */
    public float[] outboundX;
    public float[] outboundY;

    /** LZ position — terminal waypoint of {@link #inboundX}. */
    public final float lzX;
    public final float lzY;
    /** Heading used by terminal docking so the parked APC is already aligned with its outbound corridor. */
    public final float lzDepartureFacingDeg;

    public float pendingDelay;
    public float deboardCountdown;
    public int marinesRemaining;
    /** Sim-seconds remaining in OVERWATCH before transitioning to DEPARTING. Initialized from {@link VehicleType#overwatchDurationSec} on entering OVERWATCH. */
    public float overwatchCountdown;

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
     * Tactical node stamped as the deboarded squad's {@link Squad#assignedNode}
     * at squad mint — the recapture-target objective a progressive-reinforcement
     * delivery should advance on and re-man (see
     * {@code roadmap/conquest/stories/progressive-reinforcement.md}, the "assign
     * at deboard, not on arrival" contract). Distinct from a marine
     * {@code HOLD_NODE} <em>objective</em> assignment — this only sets the
     * squad's spawn-time anchor. {@code null} for deliveries with no objective.
     * Mirrors {@link com.dillon.starsectormarines.battle.air.ShuttleMission#assignNode}.
     */
    public TacticalNode assignNode;

    /**
     * Squad identity assigned to all marines deboarded from this vehicle. Lazily
     * set to a fresh id on the first successful deboard; {@link Squad#NO_SQUAD}
     * means "no squad has been created for this vehicle yet."
     */
    public int squadId = Squad.NO_SQUAD;

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
     * Appends one debug-history frame from the current pose. The body and wall-stuck
     * seconds are passed in (not held) because both live id-keyed — kinematics in
     * {@code GROUND_KINEMATICS}, control state in {@code VEHICLE_CONTROL} — so
     * {@link GroundSystem} supplies them from {@code convoy.body(id)} /
     * {@code convoy.control(id).wallStuckTime()}.
     */
    public void recordTick(GroundBody body, float wallStuckTime) {
        histX[histHead] = body.x;
        histY[histHead] = body.y;
        histFacing[histHead] = body.facingDegrees;
        histSpeed[histHead] = body.speed;
        histStuck[histHead] = wallStuckTime;
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
        this.lzDepartureFacingDeg = initialHeading(outboundX, outboundY);
        this.pendingDelay = pendingDelay;
        this.marinesRemaining = marinesRemaining;
    }

    private static float initialHeading(float[] xs, float[] ys) {
        for (int i = 1; i < xs.length; i++) {
            float dx = xs[i] - xs[0], dy = ys[i] - ys[0];
            if (dx * dx + dy * dy > 1e-6f) return AirBody.facingToward(dx, dy);
        }
        return 0f;
    }

    /** True when the vehicle is on-map and rendered. */
    public boolean isVisible() {
        return state == VehicleState.INCOMING || state == VehicleState.LANDED
                || state == VehicleState.OVERWATCH || state == VehicleState.DEPARTING;
    }
}
