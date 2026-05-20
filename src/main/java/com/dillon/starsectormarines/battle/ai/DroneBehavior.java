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
 * weapon range, and drives the kinematic body with patrol motion when idle.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Engaged</b> — drone has an active target. {@link TurretAim}
 *       owns facing (slewing onto the target); body station-keeps at the
 *       current position so the firing solution doesn't drift.</li>
 *   <li><b>Patrol</b> — no target. Drone cruises toward a random waypoint
 *       within {@link Drone#PATROL_RADIUS_CELLS} of its hub anchor; on
 *       arrival a new waypoint rolls. Facing follows motion via
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
        if (s.target != null) {
            // Engaged: facing already slewed by TurretAim; station-keep so
            // the firing solution doesn't drift while the body brakes off
            // any residual patrol velocity.
            d.body.facingDegrees = s.facingDegrees;
            d.body.tickToward(d.body.x, d.body.y, SteeringMode.STATION, Drone.HANDLING, dt);
        } else {
            // Patrol: ensure a waypoint exists, advance toward it, re-roll on
            // arrival. tickToward owns facing in this branch.
            ensurePatrolWaypoint(d, sim);
            d.body.tickToward(d.patrolGoalX, d.patrolGoalY,
                    SteeringMode.CRUISE, Drone.HANDLING, dt);
            if (d.body.distanceTo(d.patrolGoalX, d.patrolGoalY)
                    <= Drone.PATROL_WAYPOINT_ARRIVE_DIST) {
                pickPatrolWaypoint(d, sim);
            }
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
