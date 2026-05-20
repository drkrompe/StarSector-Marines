package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Drone;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.air.SteeringMode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.weapons.FireStance;

import java.util.Random;

/**
 * Per-tick driver for a {@link Drone}: reuses {@link TurretAim} to acquire +
 * track + fire at the nearest visible enemy combatant within the drone's
 * weapon range, and drives the kinematic body with patrol / pursuit / engage
 * motion depending on contact state.
 *
 * <p>Three-mode dispatch:
 * <ul>
 *   <li><b>Engaged</b> — drone has an active target inside its weapon range.
 *       {@link TurretAim} owns facing while it slews onto the target; the body
 *       cruises toward a comfortable firing distance (or station-keeps once
 *       inside it) so the firing solution doesn't drift while the drone
 *       hovers.</li>
 *   <li><b>Pursuing</b> — no firing-range target but a recent contact (either
 *       the engagement just dropped, or the wider agro scan picked up a marine
 *       outside weapon range). The drone cruises toward the last-known enemy
 *       cell, clamped to within {@link Drone#ENGAGE_LEASH_RADIUS_CELLS} of the
 *       hub anchor so it never strays past its leash.</li>
 *   <li><b>Patrol</b> — no contact. Drone cruises toward a random waypoint
 *       within {@link Drone#PATROL_RADIUS_CELLS} of the hub anchor; on arrival
 *       a new waypoint rolls. Facing follows motion via
 *       {@link com.dillon.starsectormarines.battle.air.AirBody#tickToward}.</li>
 * </ul>
 *
 * <p>Fires through {@link BattleSimulation#fireShot}, which routes to the
 * existing {@code InfantryWeapons} pipeline since the drone has a
 * {@code primaryWeapon} assigned.
 */
public final class DroneBehavior implements UnitBehavior {

    public static final DroneBehavior INSTANCE = new DroneBehavior();

    private DroneBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        if (!(u instanceof Drone)) return;
        Drone d = (Drone) u;
        if (!d.isAlive()) return;

        TurretAim.State s = new TurretAim.State();
        s.originCellX = d.cellX;
        s.originCellY = d.cellY;
        s.originX = d.body.x;
        s.originY = d.body.y;
        s.faction = d.faction;
        s.squadId = d.squadId;
        s.excludeFromCrowding = d;
        s.facingDegrees = d.body.facingDegrees;
        s.turnRateDegPerSec = Drone.TURN_RATE_DEG_PER_SEC;
        s.attackRange = d.attackRange;
        s.minRange = 0f;
        s.cooldownTimer = d.cooldownTimer;
        s.attackCooldown = d.attackCooldown;
        s.target = d.target;
        // Walls within the drone's air-LoS radius are transparent — the drone
        // is hovering above building roofs, not inside them. The same radius
        // applies reciprocally to marines firing UP at the drone via
        // Unit.airLosRadius; see Drone.DRONE_AIR_LOS_RADIUS.
        s.ignoreCloseWalls = true;
        s.closeWallRadius = d.airLosRadius;

        TurretAim.tick(s, sim, BattleSimulation.TICK_DT);

        d.cooldownTimer = s.cooldownTimer;
        d.target = s.target;

        float dt = BattleSimulation.TICK_DT;

        // Resolve which target (if any) the drone is committing to this tick.
        // Engagement target wins; absent that, an agro-scan hit within the
        // wider detection band promotes to a pursuit target. Both refresh the
        // pursuit latch so the drone keeps closing for a few seconds after
        // the active engagement drops.
        Unit lockedOn = s.target;
        if (lockedOn == null) {
            lockedOn = tryAgroScan(d, sim);
        }
        if (lockedOn != null) {
            d.pursuitGoalX = lockedOn.cellX + 0.5f;
            d.pursuitGoalY = lockedOn.cellY + 0.5f;
            d.pursuitTimer = Drone.PURSUIT_LATCH_SECONDS;
        }

        if (s.target != null) {
            tickEngage(d, s, dt);
        } else if (d.pursuitTimer > 0f) {
            tickPursue(d, lockedOn != null, dt);
        } else {
            tickPatrol(d, sim, dt);
        }

        // Sync the drone's logical cell to the interpolated body — the LoS /
        // target acquisition path reads cellX/Y, so a drone drifting between
        // cells needs the cell index to track or it'd fire from its spawn
        // cell forever.
        d.cellX = (int) Math.floor(d.body.x);
        d.cellY = (int) Math.floor(d.body.y);

        if (s.fireThisTick && s.target != null && s.target.isAlive()) {
            sim.fireShot(d, s.target, FireStance.STANCED);
            // Latch the burst — InfantryWeapons.tick pumps the remaining
            // rounds at the weapon's spacing. Drone is in STATION mode while
            // engaging so STANCED stays correct for the follow-ups.
            d.beginBurst(s.target);
        }
    }

    /**
     * Engaged path. Cruises toward a comfortable firing distance until inside
     * it, then station-keeps so {@link TurretAim}'s slew owns facing and the
     * firing solution doesn't drift. The "comfortable" distance sits well
     * inside weapon range (70%) — the drone closes past the edge so a target
     * stepping back a cell doesn't immediately drop the lock.
     */
    private static void tickEngage(Drone d, TurretAim.State s, float dt) {
        float tx = s.target.cellX + 0.5f;
        float ty = s.target.cellY + 0.5f;
        float distToTarget = d.body.distanceTo(tx, ty);
        float comfortableFiringDist = d.attackRange * 0.7f;

        if (distToTarget <= comfortableFiringDist) {
            d.body.facingDegrees = s.facingDegrees;
            d.body.tickToward(d.body.x, d.body.y, SteeringMode.STATION, Drone.HANDLING, dt);
        } else {
            float[] goal = clampGoalToLeash(d, tx, ty);
            d.body.tickToward(goal[0], goal[1], SteeringMode.BRAKE_TO_STATION, Drone.HANDLING, dt);
        }
    }

    /**
     * Pursuit path. Drone cruises toward the latched last-known enemy cell,
     * clamped to within {@link Drone#ENGAGE_LEASH_RADIUS_CELLS} of the hub.
     * Decrements the latch timer only when no fresh target sourced the latch
     * this tick — a continuous engagement refreshes the timer instead of
     * counting it down.
     */
    private static void tickPursue(Drone d, boolean latchRefreshedThisTick, float dt) {
        if (!latchRefreshedThisTick) {
            d.pursuitTimer -= dt;
        }
        float[] goal = clampGoalToLeash(d, d.pursuitGoalX, d.pursuitGoalY);
        d.body.tickToward(goal[0], goal[1], SteeringMode.BRAKE_TO_STATION, Drone.HANDLING, dt);
    }

    /**
     * Patrol path. Walks the random-waypoint orbit within
     * {@link Drone#PATROL_RADIUS_CELLS} of the hub. Re-rolls when the drone
     * gets within {@link Drone#PATROL_WAYPOINT_ARRIVE_DIST} of the current
     * waypoint.
     */
    private static void tickPatrol(Drone d, BattleSimulation sim, float dt) {
        ensurePatrolWaypoint(d, sim);
        d.body.tickToward(d.patrolGoalX, d.patrolGoalY,
                SteeringMode.CRUISE, Drone.HANDLING, dt);
        if (d.body.distanceTo(d.patrolGoalX, d.patrolGoalY)
                <= Drone.PATROL_WAYPOINT_ARRIVE_DIST) {
            pickPatrolWaypoint(d, sim);
        }
    }

    /**
     * Wider-than-weapon-range detection scan. Reuses {@link TacticalScoring#findBestTarget}
     * for its squad-aware crowding + threat-density scoring, then post-filters
     * by {@link Drone#AGGRO_RANGE_CELLS} and an LoS check using the drone's
     * air-LoS radius. Returns the candidate when both gates pass, null
     * otherwise.
     */
    private static Unit tryAgroScan(Drone d, BattleSimulation sim) {
        Unit candidate = TacticalScoring.findBestTarget(
                d.cellX, d.cellY, d.faction, d.squadId, d, d.airLosRadius, sim);
        if (candidate == null) return null;
        float dist = TacticalScoring.cellDistance(
                d.cellX, d.cellY, candidate.cellX, candidate.cellY);
        if (dist > Drone.AGGRO_RANGE_CELLS) return null;
        boolean visible = TacticalScoring.canSeePair(sim.getGrid(),
                d.cellX, d.cellY, candidate.cellX, candidate.cellY,
                d.airLosRadius, candidate.airLosRadius);
        return visible ? candidate : null;
    }

    /**
     * Clamps the cruise goal so the resulting waypoint never sits beyond
     * {@link Drone#ENGAGE_LEASH_RADIUS_CELLS} of the hub anchor. Goal points
     * inside the leash pass through unchanged; outside, the goal is pulled
     * radially inward to the leash boundary along the hub→goal line. With no
     * hub (defensive fallback), the goal passes through — drone behaves
     * as untethered.
     */
    private static float[] clampGoalToLeash(Drone d, float gx, float gy) {
        if (d.homeHub == null) return new float[]{gx, gy};
        float hubX = d.homeHub.cellX + 0.5f;
        float hubY = d.homeHub.cellY + 0.5f;
        float dx = gx - hubX;
        float dy = gy - hubY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= Drone.ENGAGE_LEASH_RADIUS_CELLS) return new float[]{gx, gy};
        float scale = Drone.ENGAGE_LEASH_RADIUS_CELLS / dist;
        return new float[]{hubX + dx * scale, hubY + dy * scale};
    }

    /** Picks an initial patrol waypoint if the drone has never had one. */
    private static void ensurePatrolWaypoint(Drone d, BattleSimulation sim) {
        if (Float.isNaN(d.patrolGoalX) || Float.isNaN(d.patrolGoalY)) {
            pickPatrolWaypoint(d, sim);
        }
    }

    /**
     * Rolls a fresh patrol waypoint within {@link Drone#PATROL_RADIUS_CELLS}
     * of the hub anchor. Prefers walkable cells but accepts any in-bounds
     * point — the drone is airborne, so building footprints don't block it
     * (the close-wall LoS pierce makes engagement work either way). Falls
     * back to the hub's own cell if every roll lands out-of-bounds.
     */
    private static void pickPatrolWaypoint(Drone d, BattleSimulation sim) {
        if (d.homeHub == null) {
            d.patrolGoalX = d.body.x;
            d.patrolGoalY = d.body.y;
            return;
        }
        Random rng = sim.getRng();
        NavigationGrid grid = sim.getGrid();
        float anchorX = d.homeHub.cellX + 0.5f;
        float anchorY = d.homeHub.cellY + 0.5f;
        // 6 attempts is plenty — uniform-disk picking that lands in bounds
        // succeeds on the first try in the common case (hub inland). Fail
        // path is the hub anchor, which is always in bounds by construction.
        for (int attempt = 0; attempt < 6; attempt++) {
            float theta = rng.nextFloat() * (float) (Math.PI * 2.0);
            float r = (float) Math.sqrt(rng.nextFloat()) * Drone.PATROL_RADIUS_CELLS;
            float gx = anchorX + r * (float) Math.cos(theta);
            float gy = anchorY + r * (float) Math.sin(theta);
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
}
