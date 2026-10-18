package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.mech.MechDuelBand;
import com.dillon.starsectormarines.battle.mech.MechDuelBand.Band;
import com.dillon.starsectormarines.battle.mech.MechDuelBand.Weapon;
import com.dillon.starsectormarines.battle.mech.MechDuelRange;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A deployed hull fighting armor on its own: the vehicle side of the duel-range rule
 * ({@code mechs-nouns.md}, Family and doctrine; {@code convoy-nouns.md}).
 *
 * <p>A deployed, armed hull with no player order and no story route looks for the nearest hostile mech or ground
 * vehicle it can see within {@link #AWARE_REACH_FACTOR} of its gun's reach, chooses a band from its turret against
 * that machine's weapons ({@link MechDuelBand}), and drives to it: outside an out-ranged target's reach, otherwise
 * the outer part of its own. Inside the band it stops and lets the turret work, which acquires on its own as it
 * always has. A single-gun hull has no shorter band to close to, so it never closes into contact.
 *
 * <p>What it does not touch: a player order (an active one owns the chassis and an arrived one hands back here, the
 * way a mech's doctrine resumes after a move), a hull under direct control, delivery legs, allied story columns
 * (their scripts own when they move), unarmed hulls and empty magazines. Infantry is not a duel target: against
 * infantry a hull still holds where it stands and fires.
 *
 * <p>{@code -Dbattle.vehicle.duelRange=false} is the control: a deployed hull holds where it was set down.
 */
public final class VehicleEngagement {

    public static final String PROPERTY = "battle.vehicle.duelRange";

    private static volatile boolean enabled = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** A hull sees armor this far, as a multiple of its gun's reach. */
    static final float AWARE_REACH_FACTOR = 1.5f;
    /** A hull with one gun closes nowhere; its bias only matters for a future multi-mount hull. */
    static final float CLOSE_BIAS = 1.15f;
    /** Cells either side of the band a stopped hull tolerates before it moves again. */
    static final float HOLD_SLACK = 1.5f;
    /**
     * Slack inside a stand-off band's near edge. That edge is already the threat's reach plus a margin, and the
     * ordinary slack would spend the whole margin: a Hound parked at its reach would read as in band.
     */
    static final float STANDOFF_INNER_SLACK = 0.5f;
    /** Seconds between decisions, and the least time between two route proofs. */
    static final float DECIDE_SECONDS = 1f;
    static final float REPLAN_SECONDS = 2f;
    /** A planned position is rethought once the target has moved this far from where it was planned against. */
    static final float TARGET_DRIFT_CELLS = 5f;
    /** Bearings off the line to the target tried for a firing position, degrees. */
    private static final float[] BEARINGS = {0f, 25f, -25f, 50f, -50f, 80f, -80f};
    private static final int SNAP_RADIUS = 4;
    /** Seconds a hull rests after a route it could not finish before it tries again. */
    static final float GIVE_UP_REST_SECONDS = 5f;
    /** Seconds a reversing hull may stand against an obstruction before it gives the retreat up. */
    static final float RETREAT_STUCK_SECONDS = 1.5f;

    private final ConvoyService convoy;
    private final UnitRosterService roster;
    private final NavigationService navigation;
    private final VehicleControlSystem controlSystem;
    private final Map<Long, Plan> plans = new HashMap<>();

    private static final class Plan {
        long target;
        float decideIn;
        float replanIn;
        float restIn;
        float[] routeX;
        float[] routeY;
        float plannedTargetX;
        float plannedTargetY;
        /** Backing off a target that came inside the band, nose kept on it. */
        boolean retreating;
    }

    VehicleEngagement(ConvoyService convoy, UnitRosterService roster, NavigationService navigation,
                      VehicleControlSystem controlSystem) {
        this.convoy = convoy;
        this.roster = roster;
        this.navigation = navigation;
        this.controlSystem = controlSystem;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabledForEvidence(boolean value) {
        enabled = value;
    }

    /** Drops a hull's engagement and the route it was driving. */
    void forget(long id) {
        if (plans.remove(id) != null) controlSystem.clearRoute(id);
    }

    /** Drops a hull's engagement without touching its route, when a player order or direct control owns it. */
    void yieldTo(long id) {
        plans.remove(id);
    }

    /**
     * Drives deployed hull {@code id} for one tick if it has an armored target to take a band against. Called for
     * a deployed hull with no player order and no story route.
     */
    void tick(long id, float dt) {
        if (!enabled || !eligible(id)) {
            forget(id);
            return;
        }
        Plan plan = plans.computeIfAbsent(id, k -> new Plan());
        plan.decideIn -= dt;
        plan.replanIn -= dt;
        plan.restIn -= dt;
        GroundBody body = convoy.body(id);
        if (plan.decideIn <= 0f) {
            plan.decideIn = DECIDE_SECONDS;
            decide(id, plan, body);
        }
        if (plan.retreating) {
            retreat(id, plan, body, dt);
            return;
        }
        if (plan.routeX == null) {
            brake(id, body, dt);
            return;
        }
        controlSystem.tick(id, dt, plan.routeX, plan.routeY, VehicleLeg.MOVE_ORDER);
        if (controlSystem.consumeArrived(id)) {
            body.speed = 0f;
            plan.routeX = null;
            plan.routeY = null;
        } else if (convoy.control(id).localPlanFailureTime >= VehicleMoveOrderSystem.GIVE_UP_SECONDS) {
            controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            plan.restIn = GIVE_UP_REST_SECONDS;
        }
    }

    /**
     * Backs away from the target with the nose kept on it, through the same manual-drive physics direct control
     * uses: a bicycle hull cannot reverse along a route, and a planned retreat starts with a forward turnaround loop
     * toward the enemy. A target behind the hull is driven away from instead. Blocked for long, it stops and holds.
     */
    private void retreat(long id, Plan plan, GroundBody body, float dt) {
        if (plan.target == 0L || !isLive(plan.target) || convoy.control(id).wallStuckTime > RETREAT_STUCK_SECONDS) {
            plan.retreating = false;
            plan.restIn = GIVE_UP_REST_SECONDS;
            brake(id, body, dt);
            return;
        }
        float[] at = position(plan.target);
        float bearing = AirBody.facingToward(at[0] - body.x, at[1] - body.y);
        float alpha = ((bearing - body.facingDegrees + 540f) % 360f) - 180f;
        if (Math.abs(alpha) <= 90f) {
            // Reversing inverts the yaw response, so steering opposite the bearing swings the nose onto it.
            controlSystem.tickManual(id, dt, -1f, -steer(alpha));
        } else {
            float away = alpha > 0f ? alpha - 180f : alpha + 180f;
            controlSystem.tickManual(id, dt, 1f, steer(away));
        }
    }

    static float innerSlack(Band band) {
        return band.stance() == MechDuelBand.Stance.STANDOFF ? STANDOFF_INNER_SLACK : HOLD_SLACK;
    }

    /** In band with this hull's slack: {@link #innerSlack} on the near side, {@link #HOLD_SLACK} on the far. */
    static boolean inBand(Band band, float distance) {
        return distance >= band.min() - innerSlack(band) && distance <= band.max() + HOLD_SLACK;
    }

    /** Full lock beyond thirty degrees off, proportional inside it. */
    private static float steer(float degrees) {
        return Math.max(-1f, Math.min(1f, degrees / 30f));
    }

    private boolean isLive(long id) {
        return convoy.isVehicle(id) ? convoy.isTargetable(id) && convoy.structure(id) > 0f : roster.isAliveById(id);
    }

    private void brake(long id, GroundBody body, float dt) {
        if (Math.abs(body.speed) > 0.05f) controlSystem.tickManual(id, dt, 0f, 0f);
        else body.speed = 0f;
    }

    private boolean eligible(long id) {
        if (!convoy.isVehicle(id) || !convoy.isTargetable(id) || convoy.structure(id) <= 0f) return false;
        VehicleMission mission = convoy.mission(id);
        if (mission == null || mission.state != VehicleState.DEPLOYED || mission.authoredRouteX != null) return false;
        if (convoy.faction(id) == Faction.ALLY) return false;
        VehicleType type = convoy.vehicleType(id);
        GroundTurret turret = convoy.turret(id);
        return type != null && type.hasTurretWeapon() && turret != null && turret.ammo > 0;
    }

    private void decide(long id, Plan plan, GroundBody body) {
        VehicleType type = convoy.vehicleType(id);
        Weapon gun = Weapon.of(type.turretStructure().mount.weapon);
        long target = pickTarget(id, plan.target, body, gun.range() * AWARE_REACH_FACTOR);
        if (target != plan.target) {
            plan.routeX = null;
            plan.routeY = null;
        }
        plan.target = target;
        if (target == 0L) {
            plan.retreating = false;
            if (plan.routeX != null) controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            return;
        }
        float[] at = position(target);
        Band band = MechDuelBand.choose(List.of(gun), threatOf(target), CLOSE_BIAS);
        if (band == null) return;
        float distance = (float) Math.hypot(at[0] - body.x, at[1] - body.y);
        if (plan.retreating ? distance < band.min() : distance < band.min() - innerSlack(band)) {
            if (!plan.retreating && plan.routeX != null) controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            plan.retreating = plan.restIn <= 0f;
            return;
        }
        plan.retreating = false;
        if (inBand(band, distance)) {
            if (plan.routeX != null) controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            return;
        }
        // A route is rethought when the target has moved off the point it was planned against, or when the
        // end of the route is no longer in band (a closing target has made it too near).
        boolean stale = plan.routeX == null
                || Math.hypot(at[0] - plan.plannedTargetX, at[1] - plan.plannedTargetY) > TARGET_DRIFT_CELLS
                || !inBand(band, (float) Math.hypot(plan.routeX[plan.routeX.length - 1] - at[0],
                        plan.routeY[plan.routeY.length - 1] - at[1]));
        if (!stale || plan.replanIn > 0f || plan.restIn > 0f) return;
        plan.replanIn = REPLAN_SECONDS;
        float[][] route = planToBand(id, type, body, at, band);
        if (plan.routeX != null) controlSystem.clearRoute(id);
        if (route == null || route[0].length == 0) {
            plan.routeX = null;
            plan.routeY = null;
            return;
        }
        plan.routeX = route[0];
        plan.routeY = route[1];
        plan.plannedTargetX = at[0];
        plan.plannedTargetY = at[1];
    }

    /**
     * A drivable route to a cell at the middle of {@code band} off the target, with a line of sight to it, tried on
     * the line through the hull first and then at widening bearings either side; null when none is drivable.
     */
    private float[][] planToBand(long id, VehicleType type, GroundBody body, float[] target, Band band) {
        NavigationGrid grid = controlSystem.planningGrid(type);
        VehicleClearance clearance = VehicleClearance.erode(grid, VehicleClearance.radiusForWidth(type.visualWidthCells));
        TerrainCostField cost = TerrainCostField.from(navigation.getTopology());
        int[] from = VehicleRoutePlanner.snapToMask(clearance, (int) Math.floor(body.x), (int) Math.floor(body.y),
                SNAP_RADIUS);
        if (from == null) return null;
        double base = Math.atan2(body.y - target[1], body.x - target[0]);
        float radius = band.middle();
        int tx = (int) Math.floor(target[0]);
        int ty = (int) Math.floor(target[1]);
        for (float bearing : BEARINGS) {
            double angle = base + Math.toRadians(bearing);
            int gx = (int) Math.floor(target[0] + Math.cos(angle) * radius);
            int gy = (int) Math.floor(target[1] + Math.sin(angle) * radius);
            int[] to = VehicleRoutePlanner.snapToMask(clearance, gx, gy, SNAP_RADIUS);
            if (to == null) continue;
            float d = (float) Math.hypot(to[0] + 0.5f - target[0], to[1] + 0.5f - target[1]);
            // Sight is read off the live grid: the planning grid is a navigation-only copy.
            if (!inBand(band, d) || !navigation.getGrid().hasLineOfSight(to[0], to[1], tx, ty)) continue;
            if (from[0] == to[0] && from[1] == to[1]) return new float[][] {new float[0], new float[0]};
            float[][] route = VehicleRoutePlanner.routeDrivable(from[0], from[1], to[0], to[1],
                    grid, cost, clearance, type);
            if (route != null) return route;
        }
        return null;
    }

    /**
     * The hostile machine this hull fights: its current one while still visible and in awareness, else the
     * nearest visible hostile mech or ground vehicle within {@code aware} cells.
     */
    private long pickTarget(long id, long current, GroundBody body, float aware) {
        Faction own = convoy.faction(id);
        if (current != 0L && valid(current, own, body, aware)) return current;
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        World world = roster.world();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long candidate = roster.get(i);
            if (!world.hasMechLoadout(candidate)) continue;
            float d = distance(candidate, body);
            if (d < bestDistance && valid(candidate, own, body, aware)) {
                best = candidate;
                bestDistance = d;
            }
        }
        for (long candidate : convoy.entityIds()) {
            if (candidate == id) continue;
            float d = distance(candidate, body);
            if (d < bestDistance && valid(candidate, own, body, aware)) {
                best = candidate;
                bestDistance = d;
            }
        }
        return best;
    }

    private boolean valid(long candidate, Faction own, GroundBody body, float aware) {
        boolean vehicle = convoy.isVehicle(candidate);
        if (vehicle) {
            if (!convoy.isTargetable(candidate) || convoy.structure(candidate) <= 0f) return false;
            if (!own.hostileTo(convoy.faction(candidate))) return false;
        } else {
            if (!roster.isAliveById(candidate) || !roster.world().hasMechLoadout(candidate)) return false;
            if (!own.hostileTo(roster.identity().faction(candidate))) return false;
        }
        float[] at = position(candidate);
        if (Math.hypot(at[0] - body.x, at[1] - body.y) > aware) return false;
        return navigation.getGrid().hasLineOfSight((int) Math.floor(body.x), (int) Math.floor(body.y),
                (int) Math.floor(at[0]), (int) Math.floor(at[1]));
    }

    private float distance(long candidate, GroundBody body) {
        float[] at = position(candidate);
        return (float) Math.hypot(at[0] - body.x, at[1] - body.y);
    }

    private float[] position(long id) {
        if (convoy.isVehicle(id)) {
            GroundBody b = convoy.body(id);
            return new float[] {b.x, b.y};
        }
        return new float[] {roster.world().x(id), roster.world().y(id)};
    }

    /** The target's weapons: a mech's loaded mounts, or a hull's turret (none for an unarmed one). */
    private List<Weapon> threatOf(long target) {
        if (!convoy.isVehicle(target)) return MechDuelRange.ownWeapons(roster.world().mechLoadout(target));
        List<Weapon> out = new ArrayList<>(1);
        VehicleType type = convoy.vehicleType(target);
        if (type != null && type.hasTurretWeapon()) out.add(Weapon.of(type.turretStructure().mount.weapon));
        return out;
    }
}
