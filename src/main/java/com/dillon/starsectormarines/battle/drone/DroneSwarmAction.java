package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirSteeringSystem;
import com.dillon.starsectormarines.battle.air.SteeringMode;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.combat.FireStance;

import java.util.List;
import java.util.Random;

/**
 * <b>Squad posture: defend hub with a coordinated drone swarm.</b> The single
 * custom-plan action emitted by {@link com.dillon.starsectormarines.battle.drone.DefendHubGoal}.
 * Runs per-member three-mode dispatch (engage / pursue / patrol) with
 * slot-aware positioning so the swarm spreads instead of stacking:
 *
 * <ul>
 *   <li><b>Engage</b> — TurretAim drives acquisition + facing slew + cooldown
 *       gating. The body cruises to a hover point at
 *       {@code attackRange × ENGAGE_HOVER_FRACTION} on a slot-derived bearing
 *       around the target (0°, 120°, 240° for a 3-drone swarm — they encircle
 *       rather than stacking). Inside the hover band the body station-keeps so
 *       the firing solution doesn't drift.</li>
 *   <li><b>Pursue</b> — no active TurretAim target, but the pursuit latch is
 *       still live (recent engagement or a hit from the wider agro scan).
 *       Cruise to the same slot-derived offset around the latched goal, leash-
 *       clamped to the hub.</li>
 *   <li><b>Patrol</b> — random waypoints within
 *       {@link Drone#PATROL_RADIUS_CELLS} of the hub, but constrained to the
 *       drone's angular sector around the hub (each slot owns 360°/N of the
 *       orbit ring) so the swarm covers approach lanes rather than orbiting
 *       in a single bunch.</li>
 * </ul>
 *
 * <p>Slot resolution: each member's index in this step's assignment slot list
 * defines its slot index {@code idx ∈ [0, N)}. Slot count {@code N} is the
 * number of members assigned to this step's slot. Stable within a plan and
 * across most replans (RoleAssigner runs the same scorer set, member list
 * order rarely shuffles); occasional cross-replan re-numbering is harmless
 * since the swarm still covers N distinct sectors / bearings.
 *
 * <p>The previous per-unit {@code DroneBehavior} class is replaced wholesale
 * by this action — drones now route through {@code CombatantBehavior}'s drone
 * branch like any other GOAP-managed combatant.
 */
public final class DroneSwarmAction implements Action {

    public static final DroneSwarmAction INSTANCE = new DroneSwarmAction();

    /**
     * Fraction of {@code attackRange} the drone hovers at when in firing
     * range. Pulls the drone comfortably inside its weapon envelope so a
     * target stepping back one cell doesn't immediately drop the lock.
     */
    public static final float ENGAGE_HOVER_FRACTION = 0.7f;

    private DroneSwarmAction() {}

    @Override public String name() { return "DroneSwarm"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        if (!(member instanceof Drone)) return ActionStatus.FAILURE;
        Drone d = (Drone) member;
        if (!sim.world().isAlive(d.entityId)) return ActionStatus.RUNNING;

        // The drone's kinematic body is a world KINEMATICS component now; read it
        // once and thread it through the per-mode helpers + the cell/render sync.
        AirBody body = sim.world().kinematics(d.entityId);

        int slotIdx = resolveSlotIndex(squad, d);
        int slotCount = Math.max(1, slotMemberCount(squad));

        TurretAim.State s = new TurretAim.State();
        s.originCellX = sim.world().cellX(d.entityId);
        s.originCellY = sim.world().cellY(d.entityId);
        s.originX = body.x;
        s.originY = body.y;
        s.faction = d.faction;
        s.squadId = d.squadId;
        s.excludeFromCrowding = d;
        s.facingDegrees = body.facingDegrees;
        s.turnRateDegPerSec = Drone.TURN_RATE_DEG_PER_SEC;
        s.attackRange = sim.world().attackRange(d.entityId);
        s.minRange = 0f;
        s.cooldownTimer = sim.world().cooldownTimer(d.entityId);
        s.attackCooldown = sim.combat().attackCooldown(d.entityId);
        s.target = sim.targetOf(d);
        s.ignoreCloseWalls = true;
        s.closeWallRadius = sim.vision().airLosRadius(d.entityId);

        TurretAim.tick(s, sim.getTacticalScoring(), sim.getGrid(), sim.world(), sim.vision(), BattleSimulation.TICK_DT);

        sim.world().setCooldownTimer(d.entityId, s.cooldownTimer);
        sim.world().setTargetId(d.entityId, Entity.idOf(s.target));

        float dt = BattleSimulation.TICK_DT;

        // Determine which target (if any) the drone is committing to. Engagement
        // target wins; absent that, an agro-scan hit promotes to pursuit. Both
        // refresh the pursuit latch.
        Entity lockedOn = s.target;
        if (lockedOn == null) {
            lockedOn = tryAgroScan(d, sim);
        }
        if (lockedOn != null) {
            d.pursuitGoalX = sim.world().cellX(lockedOn.entityId) + 0.5f;
            d.pursuitGoalY = sim.world().cellY(lockedOn.entityId) + 0.5f;
            d.pursuitTimer = Drone.PURSUIT_LATCH_SECONDS;
        }

        if (s.target != null) {
            tickEngage(d, body, s, slotIdx, slotCount, sim, dt);
        } else if (d.pursuitTimer > 0f) {
            tickPursue(d, body, lockedOn != null, slotIdx, slotCount, sim, dt);
        } else {
            tickPatrol(d, body, sim, slotIdx, slotCount, dt);
        }

        sim.world().setCellPos(d.entityId, (int) Math.floor(body.x), (int) Math.floor(body.y));
        // Sync render position so the shot pipeline picks up the drone's actual
        // position. InfantryWeapons.fireShot computes the tracer origin as
        // (shooter.getRenderX() + 0.5, shooter.getRenderY() + 0.5); without this sync
        // the tracers fire from the drone's spawn cell while the sprite
        // visually orbits the target.
        sim.world().setRenderPos(d.entityId, body.x - 0.5f, body.y - 0.5f);

        if (s.fireThisTick && s.target != null) {
            sim.fireShot(d, s.target, FireStance.STANCED);
            d.beginBurst(sim.world(), s.target);
        }
        return ActionStatus.RUNNING;
    }

    /**
     * Engage posture. Drone laps the target on an orbit with varying radius —
     * vanilla Starsector Terminator-drone style. The orbit point is recomputed
     * every tick from sim time + slot index:
     *
     * <ul>
     *   <li>Tangential drift: angular velocity {@link Drone#ENGAGE_ORBIT_ANGULAR_DEG_PER_SEC}
     *       CCW, offset by 360°/N per slot so the swarm starts evenly fanned.</li>
     *   <li>Radial pulse: sinusoidal at {@link Drone#ENGAGE_ORBIT_PULSE_HZ}, amplitude
     *       {@link Drone#ENGAGE_ORBIT_PULSE_FRACTION} × attackRange, with a 2π/N
     *       phase offset per slot so drones dive in / swing out of sync.</li>
     * </ul>
     *
     * <p>Cruise mode (full thrust toward the moving goal), <b>not</b> station-
     * keep — the orbit point is always moving, so the drone is always pursuing.
     * Facing is owned by {@code AirBody.tickToward} (boat physics: thrust along
     * facing), which keeps the drone nose-pointed in its motion direction.
     * {@code TurretAim}'s {@code FIRE_ARC_DEG} check then naturally gates fire
     * to the windows when the orbit motion swings the nose through target
     * bearing — like a fighter strafing pass. Three drones at 120° phases mean
     * at least one is roughly nose-on at most moments, distributing squad
     * fire across the orbit cycle.
     */
    private static void tickEngage(Drone d, AirBody body, TurretAim.State s,
                                   int slotIdx, int slotCount,
                                   BattleView sim, float dt) {
        float tx = sim.world().cellX(s.target.entityId) + 0.5f;
        float ty = sim.world().cellY(s.target.entityId) + 0.5f;
        float simTime = sim.getSimTickIndex() * BattleSimulation.TICK_DT;

        float baseBearingDeg = (360f * slotIdx) / slotCount;
        float driftDeg = simTime * Drone.ENGAGE_ORBIT_ANGULAR_DEG_PER_SEC;
        float orbitBearingDeg = baseBearingDeg + driftDeg;

        float baseRadius = sim.world().attackRange(d.entityId) * Drone.ENGAGE_ORBIT_BASE_FRACTION;
        float pulseAmplitude = sim.world().attackRange(d.entityId) * Drone.ENGAGE_ORBIT_PULSE_FRACTION;
        float pulsePhase = 2f * (float) Math.PI
                * (Drone.ENGAGE_ORBIT_PULSE_HZ * simTime + (float) slotIdx / slotCount);
        float orbitRadius = baseRadius + pulseAmplitude * (float) Math.sin(pulsePhase);

        float rad = (float) Math.toRadians(orbitBearingDeg);
        float ox = -(float) Math.sin(rad);
        float oy =  (float) Math.cos(rad);
        float orbitX = tx + orbitRadius * ox;
        float orbitY = ty + orbitRadius * oy;

        float[] goal = clampGoalToLeash(d, sim, orbitX, orbitY);
        AirSteeringSystem.steer(body, goal[0], goal[1], SteeringMode.CRUISE, Drone.HANDLING, dt);
    }

    /**
     * Pursuit posture. Cruise toward the slot-derived encircle position
     * around the latched goal; tick the latch down only when no fresh target
     * sourced the refresh this tick.
     */
    private static void tickPursue(Drone d, AirBody body, boolean latchRefreshedThisTick,
                                   int slotIdx, int slotCount, BattleView sim, float dt) {
        if (!latchRefreshedThisTick) {
            d.pursuitTimer -= dt;
        }
        float comfortableDist = sim.world().attackRange(d.entityId) * ENGAGE_HOVER_FRACTION;
        float[] hover = encircleOffset(d.pursuitGoalX, d.pursuitGoalY,
                comfortableDist, slotIdx, slotCount);
        float[] goal = clampGoalToLeash(d, sim, hover[0], hover[1]);
        AirSteeringSystem.steer(body, goal[0], goal[1], SteeringMode.BRAKE_TO_STATION, Drone.HANDLING, dt);
    }

    /**
     * Patrol posture. Picks waypoints in the drone's angular sector around
     * the hub anchor (each slot owns 360°/N of the orbit ring). Sector
     * constraint keeps the swarm fanned around the hub rather than orbiting
     * in a single bunch.
     */
    private static void tickPatrol(Drone d, AirBody body, BattleView sim,
                                   int slotIdx, int slotCount, float dt) {
        ensureSectorWaypoint(d, body, sim, slotIdx, slotCount);
        AirSteeringSystem.steer(body, d.patrolGoalX, d.patrolGoalY,
                SteeringMode.CRUISE, Drone.HANDLING, dt);
        if (body.distanceTo(d.patrolGoalX, d.patrolGoalY)
                <= Drone.PATROL_WAYPOINT_ARRIVE_DIST) {
            pickSectorWaypoint(d, body, sim, slotIdx, slotCount);
        }
    }

    /**
     * Position offset for an encircling swarm: {@code center} plus
     * {@code radius} along bearing {@code (360° × slotIdx) / slotCount}.
     * Bearing convention matches the Starsector sprite angle — 0° = +Y
     * (north), positive CCW — so slot 0 sits north of the center, slot 1
     * 120° CCW for a 3-drone swarm, etc.
     */
    private static float[] encircleOffset(float cx, float cy, float radius,
                                          int slotIdx, int slotCount) {
        float bearingDeg = (360f * slotIdx) / slotCount;
        float rad = (float) Math.toRadians(bearingDeg);
        float dx = -(float) Math.sin(rad);
        float dy =  (float) Math.cos(rad);
        return new float[]{cx + radius * dx, cy + radius * dy};
    }

    /**
     * Wider-than-weapon-range agro scan. Reuses {@link TacticalScoring#findBestTarget}
     * for its squad-aware crowding + threat-density scoring, then post-filters
     * by {@link Drone#AGGRO_RANGE_CELLS} and an air-LoS check.
     */
    private static Entity tryAgroScan(Drone d, BattleView sim) {
        float dAir = sim.vision().airLosRadius(d.entityId);
        Entity candidate = sim.getTacticalScoring().findBestTarget(
                sim.world().cellX(d.entityId), sim.world().cellY(d.entityId), d.faction, d.squadId, d, dAir);
        if (candidate == null) return null;
        float dist = TacticalScoring.cellDistance(
                sim.world().cellX(d.entityId), sim.world().cellY(d.entityId), sim.world().cellX(candidate.entityId), sim.world().cellY(candidate.entityId));
        if (dist > Drone.AGGRO_RANGE_CELLS) return null;
        boolean visible = TacticalScoring.canSeePair(sim.getGrid(),
                sim.world().cellX(d.entityId), sim.world().cellY(d.entityId), sim.world().cellX(candidate.entityId), sim.world().cellY(candidate.entityId),
                dAir, sim.vision().airLosRadius(candidate.entityId));
        return visible ? candidate : null;
    }

    /**
     * Clamps the cruise goal so the resulting waypoint never sits beyond
     * {@link Drone#ENGAGE_LEASH_RADIUS_CELLS} of the hub anchor. Points
     * outside the leash are pulled radially inward to the leash boundary.
     */
    private static float[] clampGoalToLeash(Drone d, BattleView sim, float gx, float gy) {
        if (d.homeHub == null) return new float[]{gx, gy};
        float hubX = sim.world().cellX(d.homeHub.entityId) + 0.5f;
        float hubY = sim.world().cellY(d.homeHub.entityId) + 0.5f;
        float dx = gx - hubX;
        float dy = gy - hubY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= Drone.ENGAGE_LEASH_RADIUS_CELLS) return new float[]{gx, gy};
        float scale = Drone.ENGAGE_LEASH_RADIUS_CELLS / dist;
        return new float[]{hubX + dx * scale, hubY + dy * scale};
    }

    /** Picks an initial sector waypoint if the drone has never had one. */
    private static void ensureSectorWaypoint(Drone d, AirBody body, BattleView sim,
                                             int slotIdx, int slotCount) {
        if (Float.isNaN(d.patrolGoalX) || Float.isNaN(d.patrolGoalY)) {
            pickSectorWaypoint(d, body, sim, slotIdx, slotCount);
        }
    }

    /**
     * Rolls a fresh patrol waypoint inside the drone's angular sector around
     * the hub. Sector is {@code [slotIdx, slotIdx+1) × (360°/slotCount)};
     * radius is sqrt-uniform within {@link Drone#PATROL_RADIUS_CELLS} so
     * the distribution is uniform on the disk's annular slice. In-bounds
     * fallback: 6 attempts, then snap to the hub anchor.
     */
    private static void pickSectorWaypoint(Drone d, AirBody body, BattleView sim,
                                           int slotIdx, int slotCount) {
        if (d.homeHub == null) {
            d.patrolGoalX = body.x;
            d.patrolGoalY = body.y;
            return;
        }
        Random rng = d.rng;
        NavigationGrid grid = sim.getGrid();
        float anchorX = sim.world().cellX(d.homeHub.entityId) + 0.5f;
        float anchorY = sim.world().cellY(d.homeHub.entityId) + 0.5f;
        float sectorSize = 360f / slotCount;
        float sectorStart = sectorSize * slotIdx;
        for (int attempt = 0; attempt < 6; attempt++) {
            float bearingDeg = sectorStart + rng.nextFloat() * sectorSize;
            float r = (float) Math.sqrt(rng.nextFloat()) * Drone.PATROL_RADIUS_CELLS;
            float rad = (float) Math.toRadians(bearingDeg);
            float dx = -(float) Math.sin(rad);
            float dy =  (float) Math.cos(rad);
            float gx = anchorX + r * dx;
            float gy = anchorY + r * dy;
            int cx = (int) Math.floor(gx);
            int cy = (int) Math.floor(gy);
            if (grid.inBounds(cx, cy)) {
                d.patrolGoalX = gx;
                d.patrolGoalY = gy;
                return;
            }
        }
        d.patrolGoalX = anchorX;
        d.patrolGoalY = anchorY;
    }

    /**
     * Member's slot index in the current step's assignment map. Walks the
     * LinkedHashMap in insertion order so the index aligns with the slot
     * declaration order in {@link #roles}. Returns 0 as a safe default if
     * the member isn't in any slot (shouldn't happen — the dispatcher
     * filters by slotOf before calling execute, but defensive against
     * mid-tick squad churn).
     */
    private static int resolveSlotIndex(Squad squad, Entity member) {
        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) return 0;
        SquadPlan.Step step = plan.currentStep();
        if (step == null) return 0;
        int i = 0;
        for (List<Entity> bucket : step.assignments.values()) {
            int idx = bucket.indexOf(member);
            if (idx >= 0) return i + idx;
            i += bucket.size();
        }
        return 0;
    }

    /**
     * Total member count across all slots in the current step — used as the
     * swarm size for bearing math. Falls back to {@code aliveMembers} if no
     * plan is current (shouldn't happen since execute is only called from
     * within an active plan; defensive).
     */
    private static int slotMemberCount(Squad squad) {
        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) return Math.max(1, squad.aliveMembers);
        SquadPlan.Step step = plan.currentStep();
        if (step == null) return Math.max(1, squad.aliveMembers);
        int total = 0;
        for (List<Entity> bucket : step.assignments.values()) total += bucket.size();
        return Math.max(1, total);
    }
}
