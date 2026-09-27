package com.dillon.starsectormarines.battle.vehicle.components;

import com.dillon.starsectormarines.battle.vehicle.Pose;
import com.dillon.starsectormarines.battle.vehicle.FailedVehicleRecovery;
import com.dillon.starsectormarines.battle.vehicle.ReedsShepp;
import com.dillon.starsectormarines.battle.vehicle.VehicleLeg;
import com.dillon.starsectormarines.battle.vehicle.ReferenceCorridor;
import com.dillon.starsectormarines.battle.vehicle.Trajectory;

/**
 * Per-vehicle ground-motion control state — the {@code VEHICLE_CONTROL} component payload,
 * keyed by entity id. These are the fields that used to live as loose instance state on the
 * {@code VehicleController} object hung off {@code mission.controller}; the identity-collapse
 * follow-on moves them into an id-keyed OBJECT column and turns the controller logic into the
 * stateless {@code VehicleControlSystem}.
 *
 * <p>A plain mutable POJO: the control system reads/writes these fields each tick (resolving the
 * bag via {@code ConvoyService.control(id)}); the renderer / debug dump read the getters below.
 * Seeded once at spawn via a bare {@code new} — the field initializers ARE the correct spawn
 * state, so callers must never hand-zero the fields (that would corrupt
 * {@link #recoveryBestRemaining}). Dropped wholesale by {@code world.destroy(id)} at despawn;
 * vehicles are world-resident and never corpse-transmute, so there is no live-only column split.
 */
public final class VehicleControlComponent {

    /** Recovery phase. {@code REVERSING} means a committed backup maneuver owns the pose until it completes. */
    public enum Recovery { NONE, REVERSING }

    /** Active corridor for the route being driven; rebuilt when the route or the leg changes. */
    public ReferenceCorridor corridor;
    /**
     * The route the corridor was built from, held by reference so a caller
     * handing over a different one rebuilds. A re-plan installs its replacement
     * here too, so the rebuild fires once for the re-plan rather than again on
     * the next tick. {@code null} until the first tick.
     */
    public float[] routeXs;
    public float[] routeYs;
    /** What the current route is being driven for; a change rebuilds the corridor. {@code null} until the first tick. */
    public VehicleLeg leg;

    /** Current rolling local plan the body is tracking, or {@code null} when on the coarse-corridor fallback. */
    public Trajectory trajectory;
    /** Arc-distance (cells) consumed along {@link #trajectory} since it was planned; resets on replan. */
    public float trajProgress;
    /** Sim-seconds since the last local plan; gates the replan cadence. */
    public float sinceReplan;
    /** True when last tick's carrot pinned to the trajectory end (consumed) — forces a replan. */
    public boolean trajCarrotAtEnd;
    /** Consecutive on-grid seconds for which the rolling planner has returned no executable forward trajectory. */
    public float localPlanFailureTime;
    /** True after the immediate re-route attempt for the current uninterrupted local-plan failure. */
    public boolean localPlanFailureRerouteAttempted;

    /** Active Reeds-Shepp docking path, or {@code null} when not docking. */
    public ReedsShepp.Path dockingPath;
    /** Start pose of the active docking path (the {@code ReedsShepp.sample} origin). */
    public Pose dockingStartPose;
    /** Turn radius (cells) of the active docking path. */
    public float dockingTurnRadius;
    /** Arc-length (cells) consumed along the docking path — the maneuver's progress cursor. */
    public float dockingProgressCells;
    /** Goal facing (deg) snapped to on docking completion. */
    public float dockingGoalFacingDeg;
    /**
     * True when the active {@link #dockingPath} is a departure turnaround
     * rather than an arrival dock. Both are the same Reeds-Shepp maneuver and
     * the same sampler; they differ only in what completion means — an arrival
     * lands on the LZ, a turnaround simply hands the pose back to ordinary
     * corridor tracking.
     */
    public boolean dockingIsDeparture;
    /**
     * Departure turnarounds already spent on this route. Bounded, so a pose no
     * maneuver can rescue costs a couple of attempts rather than looping.
     */
    public int turnaroundsUsed;

    /** Sim-seconds the vehicle has been continuously blocked by walls. Drives the reverse recovery. */
    public float wallStuckTime;
    /** Position where the vehicle first got stuck; {@link #wallStuckTime} only resets once it moves meaningfully away. */
    public float stuckOriginX, stuckOriginY;

    /** Recovery phase; {@code REVERSING} owns the pose until the committed backup completes. */
    public Recovery recovery = Recovery.NONE;
    /** Cells of backup still owed on the active reverse maneuver. */
    public float reverseRemaining;
    /** Recoveries since the last net progress toward the LZ; caps the retry ladder. */
    public int recoveryAttempts;
    /**
     * Best (smallest) corridor remaining-length reached so far; resetting {@link #recoveryAttempts}
     * requires beating it by a margin. Seed MUST be {@code Float.MAX_VALUE} (a fresh vehicle has
     * made no progress yet) — do not hand-zero.
     */
    public float recoveryBestRemaining = Float.MAX_VALUE;
    /** Seconds since the last net progress toward the goal; crossing the stall threshold triggers a re-route. */
    public float timeSinceProgress;
    /**
     * First-step directions already attempted by the current no-progress rescue
     * sequence, keyed by {@link com.dillon.starsectormarines.battle.nav.Direction#bit()}.
     * Kept across rescue re-routes so a repeat stall asks for the next-most-forward
     * escape instead of driving into the same failed corridor again.
     */
    public int rescueFirstStepTriedMask;
    /** Failed turn areas retained for this inbound/outbound leg so rescue routes cannot ping-pong through an earlier bad bend. */
    public final int[] rerouteAvoidX = new int[8];
    public final int[] rerouteAvoidY = new int[8];
    public int rerouteAvoidCount;
    /** Exact failed rescue request against a frozen routing world; cleared with control tracking. */
    public FailedVehicleRecovery failedRecovery;

    /** Set true the tick the vehicle reaches its terminal waypoint; cleared by the control system's {@code consumeArrived}. */
    public boolean arrived;

    /** Sim-seconds the vehicle has been continuously wall-blocked. Debug/history read. */
    public float wallStuckTime() { return wallStuckTime; }
    /** Arc-distance consumed along the current local trajectory, or 0 on the corridor fallback. Debug read. */
    public float trajectoryProgress() { return trajProgress; }
    /** True when tracking a feasible local plan (vs. the coarse-corridor fallback). Debug read. */
    public boolean hasTrajectory() { return trajectory != null; }
    /** Active waypoint cursor into the coarse corridor. Debug read. */
    public int waypointIndex() { return corridor != null ? corridor.cursor() : 1; }
    /** Active Reeds-Shepp docking path, or {@code null} when not docking. Debug overlay read. */
    public ReedsShepp.Path dockingPath() { return dockingPath; }
    /** Start pose of the active docking path. Debug overlay read. */
    public Pose dockingStartPose() { return dockingStartPose; }
    /** Turn radius (cells) of the active docking path. Debug overlay read. */
    public float dockingTurnRadius() { return dockingTurnRadius; }
}
