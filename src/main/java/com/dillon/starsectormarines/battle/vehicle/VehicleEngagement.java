package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.mech.MechDuelBand;
import com.dillon.starsectormarines.battle.mech.MechDuelBand.Band;
import com.dillon.starsectormarines.battle.mech.MechDuelBand.Weapon;
import com.dillon.starsectormarines.battle.mech.MechDuelRange;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

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
 * <p>A hull's {@link VehicleType.Stance} adjusts this. A stand-off hull (the LRM carrier) holds beyond the target's
 * reach where its gun out-reaches it and at the outer edge of its own reach otherwise, never closer; with an
 * indirect-fire gun it also takes a target a spotter on its side is looking at ({@link SideSpotting}), and does not
 * drive in to see it for itself. An ambusher (the SRM technical) never drives at armor: it holds where it was set
 * down and lets its turret take what comes into reach.
 *
 * <p>A hull whose gun has been shot away ({@link HullTurretSection}) has nothing left to fight with: a hull carries
 * one gun, so there is no secondary armament to keep using from cover. It <b>withdraws</b> instead, the way a disarmed
 * mech breaks contact: away from every hostile that can still shoot it (mech, armed hull or infantry), until it stands
 * {@link #WITHDRAW_MARGIN} cells outside each one's reach, and there it holds. It drives a route where one exists,
 * rear on, and backs off the nearest threat, nose on it, where none does. Backing out of reach nose on was measured
 * and rejected: at a tank's reverse speed it stays in reach three times as long, which the front plate does not buy
 * back. An empty magazine is not this: the gun is still there.
 * {@code -Dbattle.vehicle.disarmedWithdraw=false} is the control, in which it holds where it stands.
 *
 * <p>A hull keeps its front on the machine it duels ({@code convoy-nouns.md}, what presents the front). A bicycle hull
 * cannot pivot in place, so holding in band with the target more than {@link #REFACE_START_DEGREES} off the nose it
 * turns back onto it with a manoeuvre: backing with opposite lock out to {@link #BACKING_TURN_DEGREES} off the nose,
 * a little past the beam, and a forward turn toward it beyond that. A forward turn drives the hull out from behind
 * its parapet before the nose comes round, so a hull a parapet screens from the target ({@link #SCREEN_CELLS}) turns
 * only by backing. Any hull makes a turn only if, rehearsed on a copy of its body, it ends square on over drivable
 * ground and still in sight of the target, and a turn begun backing stops once it has given ground to within
 * {@link #HOLD_SLACK} of the band's far edge: a turn that backs a hull round a street corner, or out to where its gun
 * no longer reaches, takes it out of the fight it was turning to. {@code -Dbattle.vehicle.faceInBand=false} is the
 * control. The front goes to the worst hostile machine the hull knows of, which need not be the target it holds its
 * band against ({@link ThreatFront}); a gun on a turning hull shakes ({@link HullPivot}). The retreat from a too-close target backs off it nose-on across the same arc; only a target further behind
 * is driven away from, rear on. A forward turn toward that one was tried and parked hulls out of sight in the street.
 *
 * <p>A hold in band is a hold for as long as it wears the target down. A hull that has held through
 * {@link #stallSeconds} without its target losing any integrity has a <b>stalled</b> hold: its rounds go into the corner
 * or barricade between, or it stands at the far slack where its gun no longer hits. It moves to another firing position
 * in band, at least {@link #REPOSITION_MIN_SHIFT} cells away, whose lane to the target is clear of anything that stops a
 * round ({@link #laneClear}), the band's middle tried first. It drives there whatever the band says on the way, then
 * holds and watches again; with no such position it holds for another spell. An indirect-fire hull is exempt: its lane
 * is an arc. {@code -Dbattle.vehicle.repositionStalled=false} is the control.
 *
 * <p>What it does not touch: a player order (an active one owns the chassis and an arrived one hands back here, the
 * way a mech's doctrine resumes after a move), a hull under direct control, delivery legs, allied story columns
 * (their scripts own when they move; an allied hull a mission has enlisted under allied
 * command is not one of those, and fights as the player's do), hulls built unarmed and empty magazines. Infantry is not a duel target:
 * against infantry a hull still holds where it stands and fires.
 *
 * <p>{@code -Dbattle.vehicle.duelRange=false} is the control: a deployed hull holds where it was set down.
 */
public final class VehicleEngagement {

    public static final String PROPERTY = "battle.vehicle.duelRange";

    private static volatile boolean enabled = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** {@code false} is the control: a hull whose gun was shot away holds where it stands, as it did before. */
    public static final String WITHDRAW_PROPERTY = "battle.vehicle.disarmedWithdraw";

    private static final boolean withdraws = Boolean.parseBoolean(System.getProperty(WITHDRAW_PROPERTY, "true"));

    /** {@code false} is the control: a hull holding in its band never turns toward a target off its front arc. */
    public static final String FACE_IN_BAND_PROPERTY = "battle.vehicle.faceInBand";

    private static final boolean facesInBand = Boolean.parseBoolean(System.getProperty(FACE_IN_BAND_PROPERTY, "true"));

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
    /** Cells outside every threat's reach a disarmed hull withdraws to before it holds. */
    static final float WITHDRAW_MARGIN = 6f;
    /** Cells past the margin a withdrawal route aims, so a hull that arrives is not back in reach at once. */
    static final float WITHDRAW_OVERSHOOT = 4f;
    /** Bearings off the straight line away a withdrawal tries, degrees. */
    private static final float[] WITHDRAW_BEARINGS = {0f, 30f, -30f, 60f, -60f, 90f, -90f};
    /**
     * Degrees off the nose a held target may sit before an in-band hull turns to it. Inside the front arc's 45, so
     * the turn starts before a hit lands on the side plate.
     */
    static final float REFACE_START_DEGREES = 35f;
    /** Degrees off the nose at which a re-facing hull is square on enough to stop. */
    static final float REFACE_STOP_DEGREES = 10f;
    /** Seconds of a turn an in-band hull rehearses before it makes it, and the rehearsal's step. */
    static final float REHEARSAL_SECONDS = 12f;
    static final float REHEARSAL_STEP_SECONDS = 0.1f;
    /** Cells toward the target within which a parapet screens a hull, so it holds rather than drive off it. */
    static final int SCREEN_CELLS = 5;
    /**
     * Degrees off the nose out to which a hull turning onto a target backs with opposite lock rather than driving a
     * forward turn. Past the beam a little, backing gives up a cell or so toward the target while the nose comes
     * round; the alternative is a forward turn, or a retreat that drives away with the rear toward the gun.
     * {@code -Dbattle.vehicle.backingTurnDegrees=90} is the control, the beam the retreat used before.
     */
    public static final String BACKING_TURN_PROPERTY = "battle.vehicle.backingTurnDegrees";

    static final float BACKING_TURN_DEGREES =
            Math.max(0f, Math.min(180f, Float.parseFloat(System.getProperty(BACKING_TURN_PROPERTY, "120"))));

    /**
     * {@code false} is the control: a hull holding in its band stays there however long its rounds go into the
     * street furniture rather than the target.
     */
    public static final String REPOSITION_STALLED_PROPERTY = "battle.vehicle.repositionStalled";

    private static final boolean repositionsStalled =
            Boolean.parseBoolean(System.getProperty(REPOSITION_STALLED_PROPERTY, "true"));

    /** Least seconds a hull holds in band without wearing its target down before it counts the hold as stalled. */
    static final float STALL_MIN_SECONDS = 10f;
    /** Volleys of its own gun a hull holds in band without wearing its target down before the hold is stalled. */
    static final float STALL_VOLLEYS = 3f;
    /** Cells a stalled hull's new firing position must lie from where it stands, or it is the same position. */
    static final float REPOSITION_MIN_SHIFT = 3f;
    /**
     * Cells either side of the line from a firing position to the target that must also be clear of anything that
     * stops a round: the turret's scatter, and the corner of a target standing in the lee of a building front.
     */
    static final float LANE_HALF_WIDTH = 0.6f;
    /** Cells of room beyond its footprint a stalled hull wants around a new firing position. */
    static final int REPOSITION_ROOM_CELLS = 1;

    private final ConvoyService convoy;
    private final UnitRosterService roster;
    private final NavigationService navigation;
    private final VehicleControlSystem controlSystem;
    private final Map<Long, Plan> plans = new HashMap<>();
    private SideSpotting spotting = SideSpotting.NONE;
    private VehicleMoveOrderService moveOrders;

    /** A stand-off hull that cannot out-reach its target holds no nearer than this share of its own reach. */
    static final float EDGE_BAND_SHARE = 0.85f;

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
        /** Gun shot away: breaking contact rather than fighting. */
        boolean withdrawing;
        /** Holding in band and turning the nose back onto a target that sits off the front arc. */
        boolean refacing;
        /**
         * The machine the front is kept on ({@link ThreatFront}): the worst known threat, which need not be the
         * target the band is held against.
         */
        long faceTarget;
        /** The turn began with the target well behind: it is driven forward all the way round. */
        boolean refaceForward;
        /**
         * How far off its target a backing turn may give ground: the band's far edge less its slack, since a hull
         * holding at the very edge stands about where its gun stops reaching.
         */
        float refaceMaxDistance;
        /** Seconds held in band since the target last lost integrity. */
        float stallSeconds;
        /** The target's integrity when last read, or negative when not being watched. */
        float watchedIntegrity = -1f;
        /** Driving from a stalled hold to another firing position in band; the band does not stop it. */
        boolean repositioning;
    }

    VehicleEngagement(ConvoyService convoy, UnitRosterService roster, NavigationService navigation,
                      VehicleControlSystem controlSystem) {
        this.convoy = convoy;
        this.roster = roster;
        this.navigation = navigation;
        this.controlSystem = controlSystem;
    }

    /** An allied hull its story script owns: one no allied command has enlisted. */
    private boolean scripted(long id) {
        return convoy.faction(id) == Faction.ALLY && (moveOrders == null || !moveOrders.isEnlisted(id));
    }

    /** Whose enlistment frees an allied hull to fight. */
    void setMoveOrders(VehicleMoveOrderService moveOrders) {
        this.moveOrders = moveOrders;
    }

    /** Who calls targets for an indirect-fire hull that cannot see them itself. */
    void setSpotting(SideSpotting spotting) {
        this.spotting = spotting != null ? spotting : SideSpotting.NONE;
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
        boolean disarmed = enabled && withdraws && disarmed(id);
        if (!enabled || !(disarmed || eligible(id))) {
            forget(id);
            return;
        }
        Plan plan = plans.computeIfAbsent(id, k -> new Plan());
        if (disarmed != plan.withdrawing) {
            // The gun went: whatever band it was driving to no longer matters.
            if (plan.routeX != null) controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            plan.retreating = false;
            plan.refacing = false;
            plan.repositioning = false;
            plan.target = 0L;
            plan.decideIn = 0f;
            plan.replanIn = 0f;
            plan.restIn = 0f;
            plan.withdrawing = disarmed;
        }
        plan.decideIn -= dt;
        plan.replanIn -= dt;
        plan.restIn -= dt;
        GroundBody body = convoy.body(id);
        if (plan.decideIn <= 0f) {
            plan.decideIn = DECIDE_SECONDS;
            if (disarmed) decideWithdrawal(id, plan, body);
            else decide(id, plan, body);
        }
        if (plan.retreating) {
            retreat(id, plan, body, dt);
            return;
        }
        if (plan.refacing) {
            reface(id, plan, body, dt);
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
            plan.repositioning = false;
        } else if (convoy.control(id).localPlanFailureTime >= VehicleMoveOrderSystem.GIVE_UP_SECONDS) {
            controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            plan.repositioning = false;
            plan.restIn = GIVE_UP_REST_SECONDS;
        }
    }

    /**
     * Backs away from the target with the nose kept on it, through the same manual-drive physics direct control
     * uses: a bicycle hull cannot reverse along a route, and a planned retreat starts with a forward turnaround loop
     * toward the enemy. A target more than {@link #BACKING_TURN_DEGREES} off the nose is driven away from instead.
     * Blocked for long, it stops and holds.
     */
    private void retreat(long id, Plan plan, GroundBody body, float dt) {
        if (plan.target == 0L || !isLive(plan.target) || convoy.control(id).wallStuckTime > RETREAT_STUCK_SECONDS) {
            plan.retreating = false;
            plan.restIn = GIVE_UP_REST_SECONDS;
            brake(id, body, dt);
            return;
        }
        float alpha = offNose(plan.target, body);
        if (Math.abs(alpha) <= BACKING_TURN_DEGREES) {
            // Reversing inverts the yaw response, so steering opposite the bearing swings the nose onto it.
            controlSystem.tickManual(id, dt, -1f, -steer(alpha));
        } else {
            float away = alpha > 0f ? alpha - 180f : alpha + 180f;
            controlSystem.tickManual(id, dt, 1f, steer(away));
        }
    }

    /**
     * Turns a hull holding in its band until its nose is back on the target: the same manual drive as the retreat,
     * so it backs while the target is within {@link #BACKING_TURN_DEGREES} of its nose, and a turn begun with the
     * target further behind drives forward all the way round. Square on, or blocked for long, it stops; so does a
     * backing turn once it has given ground to within {@link #HOLD_SLACK} of its band's far edge (a target backing off
     * as it backs).
     */
    private void reface(long id, Plan plan, GroundBody body, float dt) {
        if (plan.target == 0L || !isLive(plan.target) || plan.faceTarget == 0L || !isLive(plan.faceTarget)
                || convoy.control(id).wallStuckTime > RETREAT_STUCK_SECONDS) {
            plan.refacing = false;
            plan.restIn = GIVE_UP_REST_SECONDS;
            brake(id, body, dt);
            return;
        }
        float alpha = offNose(plan.faceTarget, body);
        float[] drive = faceDrive(alpha, plan.refaceForward);
        // A backing turn gives ground for good; a forward one swings out and comes back round.
        if (Math.abs(alpha) <= REFACE_STOP_DEGREES
                || !plan.refaceForward && distance(plan.target, body) > plan.refaceMaxDistance) {
            plan.refacing = false;
            brake(id, body, dt);
            return;
        }
        controlSystem.tickManual(id, dt, drive[0], drive[1]);
    }

    /**
     * Manual throttle and steering, {@code {throttle, steering}}, that swing a bicycle hull's nose onto a bearing
     * {@code alpha} degrees off it (positive to the left) without driving at it: reversing with opposite lock out to
     * {@link #BACKING_TURN_DEGREES}, so the hull backs away (or, just past the beam, nearly sideways) as it turns, and
     * a full-lock forward turn toward it beyond that, since reversing then would close on it.
     */
    static float[] faceDrive(float alpha) {
        return faceDrive(alpha, Math.abs(alpha) > BACKING_TURN_DEGREES);
    }

    /**
     * As {@link #faceDrive(float)}, but a turn already {@code forward} stays a forward turn all the way round: a
     * full-lock circle does not close on the target, and handing over to backing for the last stretch would finish it
     * at a tank's reverse speed.
     */
    static float[] faceDrive(float alpha, boolean forward) {
        if (!forward) return new float[] {-1f, -steer(alpha)};
        return new float[] {1f, alpha > 0f ? 1f : -1f};
    }

    /**
     * Whether an in-band hull turns to its target {@code alpha} degrees off its nose: once the target is
     * {@link #REFACE_START_DEGREES} off, and, already turning, until it is within {@link #REFACE_STOP_DEGREES}.
     */
    static boolean shouldReface(float alpha, boolean refacing) {
        return Math.abs(alpha) > (refacing ? REFACE_STOP_DEGREES : REFACE_START_DEGREES);
    }

    /**
     * Whether something the hull cannot drive through stands within {@link #SCREEN_CELLS} of it on the line to
     * {@code target}: a berm or wall corner that rounds meet before the hull does.
     */
    private boolean screened(GroundBody body, float[] target) {
        NavigationGrid grid = navigation.getGrid();
        float dx = target[0] - body.x;
        float dy = target[1] - body.y;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1e-3f) return false;
        int cx = (int) Math.floor(body.x);
        int cy = (int) Math.floor(body.y);
        for (int step = 1; step <= SCREEN_CELLS; step++) {
            int x = (int) Math.floor(body.x + dx / length * step);
            int y = (int) Math.floor(body.y + dy / length * step);
            if ((x != cx || y != cy) && grid.inBounds(x, y) && !grid.isWalkable(x, y)) return true;
        }
        return false;
    }

    /**
     * Rehearses the turn onto {@code target} on a scratch copy of the hull's body: whether it comes square on within
     * {@link #REHEARSAL_SECONDS} over drivable ground and ends with a line of sight to the target. How much ground a
     * backing turn gives is watched as it is driven ({@link Plan#refaceMaxDistance}): the target may be backing too.
     */
    private boolean turnKeepsSight(VehicleType type, GroundBody body, float[] target) {
        GroundBody scratch = type.createBody();
        if (!(scratch instanceof BicycleBody bicycle)) return true;
        bicycle.teleport(body.x, body.y, body.facingDegrees);
        bicycle.speed = body.speed;
        NavigationGrid grid = navigation.getGrid();
        boolean forward = Math.abs(((AirBody.facingToward(target[0] - body.x, target[1] - body.y)
                - body.facingDegrees + 540f) % 360f) - 180f) > BACKING_TURN_DEGREES;
        for (float t = 0f; t < REHEARSAL_SECONDS; t += REHEARSAL_STEP_SECONDS) {
            float alpha = ((AirBody.facingToward(target[0] - bicycle.x, target[1] - bicycle.y)
                    - bicycle.facingDegrees + 540f) % 360f) - 180f;
            if (Math.abs(alpha) <= REFACE_STOP_DEGREES) {
                return grid.hasLineOfSight((int) Math.floor(bicycle.x), (int) Math.floor(bicycle.y),
                        (int) Math.floor(target[0]), (int) Math.floor(target[1]));
            }
            float[] drive = faceDrive(alpha, forward);
            bicycle.tickManual(drive[0], drive[1], REHEARSAL_STEP_SECONDS);
            int x = (int) Math.floor(bicycle.x);
            int y = (int) Math.floor(bicycle.y);
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return false;
        }
        return false;
    }

    /** Degrees the target sits off the hull's nose, in (-180, 180], positive to the left. */
    private float offNose(long target, GroundBody body) {
        float[] at = position(target);
        float bearing = AirBody.facingToward(at[0] - body.x, at[1] - body.y);
        return ((bearing - body.facingDegrees + 540f) % 360f) - 180f;
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
        if (scripted(id)) return false;
        VehicleType type = convoy.vehicleType(id);
        GroundTurret turret = convoy.turret(id);
        return type != null && convoy.isArmed(id) && turret != null && turret.ammo > 0;
    }

    /** A deployed hull, ours to drive, whose gun has been shot away: it has a turret weapon and it no longer fires. */
    private boolean disarmed(long id) {
        if (!convoy.isVehicle(id) || !convoy.isTargetable(id) || convoy.structure(id) <= 0f) return false;
        VehicleMission mission = convoy.mission(id);
        if (mission == null || mission.state != VehicleState.DEPLOYED || mission.authoredRouteX != null) return false;
        if (scripted(id)) return false;
        VehicleType type = convoy.vehicleType(id);
        return type != null && type.hasTurretWeapon() && !convoy.isArmed(id);
    }

    /**
     * Where a disarmed hull goes: a drivable point out of every live hostile's reach, tried straight away from the
     * threats first and then at widening bearings; it holds once it is clear, and backs off the nearest threat when
     * no point is drivable.
     */
    private void decideWithdrawal(long id, Plan plan, GroundBody body) {
        Faction own = convoy.faction(id);
        List<float[]> threats = new ArrayList<>();
        long nearest = 0L;
        float nearestDistance = Float.MAX_VALUE;
        World world = roster.world();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long candidate = roster.get(i);
            if (roster.isRiding(candidate)) continue;
            if (convoy.isVehicle(candidate) || !roster.isAliveById(candidate)) continue;
            if (!own.hostileTo(roster.identity().faction(candidate))) continue;
            float reach = roster.threatRange(candidate);
            if (reach <= 0f) continue;
            float x = world.x(candidate);
            float y = world.y(candidate);
            threats.add(new float[] {x, y, reach});
            float d = (float) Math.hypot(x - body.x, y - body.y);
            if (d < reach + WITHDRAW_MARGIN && d < nearestDistance) {
                nearest = candidate;
                nearestDistance = d;
            }
        }
        for (long candidate : convoy.entityIds()) {
            if (candidate == id || !convoy.isTargetable(candidate) || convoy.structure(candidate) <= 0f) continue;
            if (!own.hostileTo(convoy.faction(candidate))) continue;
            float reach = convoy.weaponRange(candidate);
            if (reach <= 0f) continue;
            GroundBody b = convoy.body(candidate);
            threats.add(new float[] {b.x, b.y, reach});
            float d = (float) Math.hypot(b.x - body.x, b.y - body.y);
            if (d < reach + WITHDRAW_MARGIN && d < nearestDistance) {
                nearest = candidate;
                nearestDistance = d;
            }
        }
        float[] away = withdrawal(body.x, body.y, threats);
        plan.target = nearest;
        plan.refacing = false;
        if (away == null) {
            // Out of everybody's reach: hold here.
            plan.retreating = false;
            if (plan.routeX != null) controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            return;
        }
        if (plan.replanIn > 0f) return;
        if (!plan.retreating && plan.routeX != null && withdrawal(plan.routeX[plan.routeX.length - 1],
                plan.routeY[plan.routeY.length - 1], threats) == null) {
            return; // the route it is driving still ends clear of everybody
        }
        plan.replanIn = REPLAN_SECONDS;
        float[][] route = planWithdrawal(convoy.vehicleType(id), body, away);
        if (plan.routeX != null) controlSystem.clearRoute(id);
        if (route == null || route[0].length == 0) {
            plan.routeX = null;
            plan.routeY = null;
            plan.retreating = nearest != 0L && plan.restIn <= 0f;
            return;
        }
        plan.retreating = false;
        plan.routeX = route[0];
        plan.routeY = route[1];
    }

    /**
     * Which way and how far a disarmed hull at ({@code x}, {@code y}) withdraws from {@code threats} (each
     * {@code {x, y, reach}}): a unit heading and a distance, as {@code {dx, dy, distance}}, or null when it already
     * stands {@link #WITHDRAW_MARGIN} outside every threat's reach. The heading is away from every threat it is
     * inside, each weighted by how far inside it is, so the nearest and longest-reaching guns push hardest; the
     * distance is the deepest of those intrusions plus {@link #WITHDRAW_OVERSHOOT}.
     */
    static float[] withdrawal(float x, float y, List<float[]> threats) {
        float dx = 0f;
        float dy = 0f;
        float deepest = 0f;
        float[] closest = null;
        float closestDistance = Float.MAX_VALUE;
        for (float[] t : threats) {
            float ox = x - t[0];
            float oy = y - t[1];
            float d = (float) Math.hypot(ox, oy);
            float inside = t[2] + WITHDRAW_MARGIN - d;
            if (inside <= 0f) continue;
            deepest = Math.max(deepest, inside);
            if (d < closestDistance) {
                closestDistance = d;
                closest = t;
            }
            if (d > 1e-3f) {
                dx += ox / d * inside;
                dy += oy / d * inside;
            }
        }
        if (closest == null) return null;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1e-3f) {
            // Threats balanced either side, or standing on the hull: any way off the nearest one will do.
            dx = x - closest[0];
            dy = y - closest[1];
            length = (float) Math.hypot(dx, dy);
            if (length < 1e-3f) {
                dx = 1f;
                dy = 0f;
                length = 1f;
            }
        }
        return new float[] {dx / length, dy / length, deepest + WITHDRAW_OVERSHOOT};
    }

    /** A drivable route along {@code away}, then at widening bearings off it; null when none is drivable. */
    private float[][] planWithdrawal(VehicleType type, GroundBody body, float[] away) {
        NavigationGrid grid = controlSystem.planningGrid(type);
        VehicleClearance clearance = convoy.clearances().of(grid, type);
        TerrainCostField cost = TerrainCostField.from(navigation.getTopology());
        int[] from = VehicleRoutePlanner.snapToMask(clearance, (int) Math.floor(body.x), (int) Math.floor(body.y),
                SNAP_RADIUS);
        if (from == null) return null;
        double base = Math.atan2(away[1], away[0]);
        for (float bearing : WITHDRAW_BEARINGS) {
            double angle = base + Math.toRadians(bearing);
            int gx = (int) Math.floor(body.x + Math.cos(angle) * away[2]);
            int gy = (int) Math.floor(body.y + Math.sin(angle) * away[2]);
            int[] to = VehicleRoutePlanner.snapToMask(clearance, gx, gy, SNAP_RADIUS);
            if (to == null || (from[0] == to[0] && from[1] == to[1])) continue;
            float[][] route = VehicleRoutePlanner.routeDrivable(from[0], from[1], to[0], to[1],
                    grid, cost, clearance, type);
            if (route != null) return route;
        }
        return null;
    }

    private void decide(long id, Plan plan, GroundBody body) {
        VehicleType type = convoy.vehicleType(id);
        Weapon gun = Weapon.of(type.turretStructure().mount.weapon);
        boolean indirect = type.turretStructure().mount.weapon.indirectFire;
        long target = pickTarget(id, plan.target, body, gun.range() * AWARE_REACH_FACTOR, indirect);
        if (target != plan.target) {
            plan.routeX = null;
            plan.routeY = null;
            plan.repositioning = false;
            unwatch(plan);
        }
        plan.target = target;
        if (target == 0L) {
            plan.retreating = false;
            plan.refacing = false;
            if (plan.routeX != null) controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            return;
        }
        if (type.engagementStance() == VehicleType.Stance.AMBUSH) {
            // An ambusher waits in its cover: no band to drive to and nothing to back away from.
            plan.retreating = false;
            plan.refacing = false;
            if (plan.routeX != null) controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            return;
        }
        float[] at = position(target);
        Band band = MechDuelBand.choose(List.of(gun), threatOf(target), CLOSE_BIAS);
        if (band == null) {
            plan.refacing = false;
            return;
        }
        if (type.engagementStance() == VehicleType.Stance.STANDOFF) band = standoff(band, gun.range());
        float distance = (float) Math.hypot(at[0] - body.x, at[1] - body.y);
        if (plan.retreating ? distance < band.min() : distance < band.min() - innerSlack(band)) {
            if (!plan.retreating && plan.routeX != null) controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            plan.retreating = plan.restIn <= 0f;
            plan.refacing = false;
            plan.repositioning = false;
            unwatch(plan);
            return;
        }
        plan.retreating = false;
        if (inBand(band, distance)) {
            // A hull leaving a stalled hold drives on through the band to the position it chose.
            if (plan.repositioning && plan.routeX != null) return;
            plan.repositioning = false;
            if (plan.routeX != null) controlSystem.clearRoute(id);
            plan.routeX = null;
            plan.routeY = null;
            if (!plan.refacing && !indirect && repositionsStalled
                    && stalled(plan, type.turretStructure().mount.weapon.cooldown, integrity(target)) && plan.restIn <= 0f) {
                // Holding in band and not wearing the target down: the rounds are going into whatever stands
                // between, or the far edge of the band is past where the gun still hits. Another firing position
                // with a clear lane, at the band's middle, is worth the drive; none, and it holds for another spell.
                unwatch(plan);
                float[][] route = planReposition(type, body, at, band, distance);
                if (route != null && route[0].length > 0) {
                    plan.routeX = route[0];
                    plan.routeY = route[1];
                    plan.plannedTargetX = at[0];
                    plan.plannedTargetY = at[1];
                    plan.repositioning = true;
                    plan.refacing = false;
                    return;
                }
            }
            // A bicycle hull cannot pivot where it stands, so turning to a target off its front arc is a manoeuvre.
            // The forward turn a target behind it needs drives it out from behind a parapet before its nose comes
            // round, so a screened hull turns only by backing.
            // A turn is made only if, rehearsed, it ends square on and still in sight: one that backs the hull behind
            // the corner it was holding takes it out of the fight.
            // The front goes to the worst threat the hull knows of, which need not be the target it holds against.
            long face = faceTarget(id, plan.faceTarget, body, target);
            if (face != plan.faceTarget) plan.refacing = false;
            plan.faceTarget = face;
            float[] faceAt = position(face);
            float alpha = offNose(face, body);
            boolean wasRefacing = plan.refacing;
            plan.refacing = facesInBand && plan.restIn <= 0f && shouldReface(alpha, plan.refacing)
                    && (plan.refacing || (Math.abs(alpha) <= BACKING_TURN_DEGREES || !screened(body, faceAt))
                    && turnKeepsSight(type, body, faceAt));
            if (!wasRefacing && plan.refacing) {
                plan.refaceForward = Math.abs(alpha) > BACKING_TURN_DEGREES;
                plan.refaceMaxDistance = band.max() - HOLD_SLACK;
            }
            return;
        }
        plan.refacing = false;
        unwatch(plan);
        // A route is rethought when the target has moved off the point it was planned against, or when the
        // end of the route is no longer in band (a closing target has made it too near).
        boolean stale = plan.routeX == null
                || Math.hypot(at[0] - plan.plannedTargetX, at[1] - plan.plannedTargetY) > TARGET_DRIFT_CELLS
                || !inBand(band, (float) Math.hypot(plan.routeX[plan.routeX.length - 1] - at[0],
                        plan.routeY[plan.routeY.length - 1] - at[1]));
        if (!stale || plan.replanIn > 0f || plan.restIn > 0f) return;
        plan.replanIn = REPLAN_SECONDS;
        float[][] route = planToBand(id, type, body, at, band, !indirect);
        if (plan.routeX != null) controlSystem.clearRoute(id);
        plan.repositioning = false;
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
     * Whether a hull holding in band has held long enough without its target losing integrity
     * ({@link #stallSeconds}): one decision's worth of holding is added unless the target has lost some since the
     * last reading, which starts the count again. The first reading only starts watching.
     */
    private static boolean stalled(Plan plan, float cooldown, float integrity) {
        if (plan.watchedIntegrity < 0f || integrity < plan.watchedIntegrity - 1e-3f) {
            plan.stallSeconds = 0f;
        } else {
            plan.stallSeconds += DECIDE_SECONDS;
        }
        plan.watchedIntegrity = integrity;
        return plan.stallSeconds >= stallSeconds(cooldown);
    }

    /**
     * How long a hull holds in band without wearing its target down before the hold counts as stalled: three volleys
     * of its gun, and never less than {@link #STALL_MIN_SECONDS}, so a slow gun's misses are not taken for a lane
     * the rounds cannot get down.
     */
    static float stallSeconds(float cooldown) {
        return Math.max(STALL_MIN_SECONDS, STALL_VOLLEYS * cooldown);
    }

    private static void unwatch(Plan plan) {
        plan.stallSeconds = 0f;
        plan.watchedIntegrity = -1f;
    }

    /** What the target has left to lose: hit points and armor, and a mech's section structure and plate. */
    private float integrity(long target) {
        World world = roster.world();
        float sum = world.hp(target) + (world.hasArmor(target) ? world.armor(target) : 0f);
        if (!convoy.isVehicle(target) && world.hasMechLoadout(target)) {
            MechSections sections = world.mechLoadout(target).sections();
            if (sections != null) sum += sections.totalStructure() + sections.totalArmor();
        }
        return sum;
    }

    /**
     * Where a stalled hull moves: a drivable cell in band, at least {@link #REPOSITION_MIN_SHIFT} from where it
     * stands, whose lane to the target is clear ({@link #laneClear}). The band's middle is tried before the
     * hull's present distance, on the line through the hull first and then at widening bearings either side, so a
     * hull holding at the far slack closes to where its gun hits and a hull whose lane is fouled steps across the
     * street. Null when nothing qualifies.
     */
    private float[][] planReposition(VehicleType type, GroundBody body, float[] target, Band band, float distance) {
        NavigationGrid grid = controlSystem.planningGrid(type);
        NavigationGrid live = navigation.getGrid();
        int fit = VehicleClearance.radiusForWidth(type.visualWidthCells);
        VehicleClearance clearance = convoy.clearances().of(grid, fit);
        // A firing position is one a hull can drive onto from any side, not one its footprint only just fits.
        VehicleClearance roomy = convoy.clearances().of(grid, fit + REPOSITION_ROOM_CELLS);
        TerrainCostField cost = TerrainCostField.from(navigation.getTopology());
        int[] from = VehicleRoutePlanner.snapToMask(clearance, (int) Math.floor(body.x), (int) Math.floor(body.y),
                SNAP_RADIUS);
        if (from == null) return null;
        double base = Math.atan2(body.y - target[1], body.x - target[0]);
        float[] radii = {band.middle(), Math.min(distance, band.max())};
        for (float bearing : BEARINGS) {
            double angle = base + Math.toRadians(bearing);
            for (float radius : radii) {
                int gx = (int) Math.floor(target[0] + Math.cos(angle) * radius);
                int gy = (int) Math.floor(target[1] + Math.sin(angle) * radius);
                int[] to = VehicleRoutePlanner.snapToMask(roomy, gx, gy, SNAP_RADIUS);
                if (to == null) continue;
                float cx = to[0] + 0.5f;
                float cy = to[1] + 0.5f;
                if (Math.hypot(cx - body.x, cy - body.y) < REPOSITION_MIN_SHIFT) continue;
                if (!inBand(band, (float) Math.hypot(cx - target[0], cy - target[1]))) continue;
                // Lanes are read off the live grid: the planning grid is a navigation-only copy.
                if (!laneClear(live, cx, cy, target[0], target[1])) continue;
                float[][] route = VehicleRoutePlanner.routeDrivable(from[0], from[1], to[0], to[1],
                        grid, cost, clearance, type);
                if (route != null && route[0].length > 0) return route;
            }
        }
        return null;
    }

    /**
     * Whether a round fired from ({@code x}, {@code y}) at ({@code tx}, {@code ty}) meets nothing that stops it: the
     * line itself and the two lines {@link #LANE_HALF_WIDTH} either side of it, each judged as the turret judges its
     * own shot ({@link NavigationGrid#hasLineOfFire}). A cell's line of sight runs centre to centre and can graze a
     * corner the scatter of real rounds does not.
     */
    static boolean laneClear(NavigationGrid grid, float x, float y, float tx, float ty) {
        float dx = tx - x;
        float dy = ty - y;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1e-3f) return true;
        float nx = -dy / length * LANE_HALF_WIDTH;
        float ny = dx / length * LANE_HALF_WIDTH;
        return grid.hasLineOfFire(x, y, tx, ty)
                && grid.hasLineOfFire(x + nx, y + ny, tx + nx, ty + ny)
                && grid.hasLineOfFire(x - nx, y - ny, tx - nx, ty - ny);
    }

    /**
     * A stand-off hull's band: the duel band's stand-off where its gun out-reaches the target, else the outer edge of
     * its own reach rather than the duel band's middle, so it never closes to trade at the target's preferred range.
     */
    static Band standoff(Band chosen, float reach) {
        if (chosen.stance() == MechDuelBand.Stance.STANDOFF) return chosen;
        return new Band(MechDuelBand.Stance.STANDOFF, reach * EDGE_BAND_SHARE, reach - 1f);
    }

    /**
     * A drivable route to a cell at the middle of {@code band} off the target, with a line of sight to it (when
     * {@code needSight}; an indirect-fire hull fires on a spotter's call instead), tried on
     * the line through the hull first and then at widening bearings either side; null when none is drivable.
     */
    private float[][] planToBand(long id, VehicleType type, GroundBody body, float[] target, Band band,
                                 boolean needSight) {
        NavigationGrid grid = controlSystem.planningGrid(type);
        VehicleClearance clearance = convoy.clearances().of(grid, type);
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
            if (!inBand(band, d) || (needSight && !navigation.getGrid().hasLineOfSight(to[0], to[1], tx, ty))) {
                continue;
            }
            if (from[0] == to[0] && from[1] == to[1]) return new float[][] {new float[0], new float[0]};
            float[][] route = VehicleRoutePlanner.routeDrivable(from[0], from[1], to[0], to[1],
                    grid, cost, clearance, type);
            if (route != null) return route;
        }
        return null;
    }

    /**
     * The hostile machine this hull fights: its current one while still visible and in awareness, else the
     * nearest visible hostile mech or ground vehicle within {@code aware} cells. An {@code indirect} hull also counts
     * a machine its side's spotters see as visible.
     */
    private long pickTarget(long id, long current, GroundBody body, float aware, boolean indirect) {
        Faction own = convoy.faction(id);
        if (current != 0L && valid(current, own, body, aware, indirect)) return current;
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        World world = roster.world();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long candidate = roster.get(i);
            if (!world.hasMechLoadout(candidate)) continue;
            float d = distance(candidate, body);
            if (d < bestDistance && valid(candidate, own, body, aware, indirect)) {
                best = candidate;
                bestDistance = d;
            }
        }
        for (long candidate : convoy.entityIds()) {
            if (candidate == id) continue;
            float d = distance(candidate, body);
            if (d < bestDistance && valid(candidate, own, body, aware, indirect)) {
                best = candidate;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * The machine hull {@code id} keeps its front on: of the hostile machines it knows of ({@link #knows}), the one
     * that can do it the most harm from where it stands ({@link ThreatFront}), the one it is facing kept unless another
     * is clearly worse. The duel target when nothing known can reach it, or with {@link ThreatFront#PROPERTY} off.
     */
    private long faceTarget(long id, long current, GroundBody body, long duelTarget) {
        if (!ThreatFront.ENABLED) return duelTarget;
        Faction own = convoy.faction(id);
        World world = roster.world();
        float rating = world.hasArmor(id) ? world.armorRating(id) : 0f;
        List<ThreatFront.Threat> threats = new ArrayList<>();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long candidate = roster.get(i);
            if (!world.hasMechLoadout(candidate) || !hostileMachine(candidate, own) || !knows(own, body, candidate)) {
                continue;
            }
            threats.add(new ThreatFront.Threat(candidate,
                    ThreatFront.harm(weaponsOf(candidate), distance(candidate, body), rating)));
        }
        for (long candidate : convoy.entityIds()) {
            if (candidate == id || !hostileMachine(candidate, own) || !knows(own, body, candidate)) continue;
            threats.add(new ThreatFront.Threat(candidate,
                    ThreatFront.harm(weaponsOf(candidate), distance(candidate, body), rating)));
        }
        long face = ThreatFront.choose(threats, current);
        return face != 0L ? face : duelTarget;
    }

    /**
     * Whether a hull knows where {@code candidate} is: it sees it, or a spotter on its side does
     * ({@link SideSpotting}). Everything, with {@link ThreatFront#KNOWN_ONLY_PROPERTY} off.
     */
    private boolean knows(Faction own, GroundBody body, long candidate) {
        if (!ThreatFront.KNOWN_ONLY) return true;
        float[] at = position(candidate);
        return navigation.getGrid().hasLineOfSight((int) Math.floor(body.x), (int) Math.floor(body.y),
                (int) Math.floor(at[0]), (int) Math.floor(at[1]))
                || spotting.spotted(own, candidate);
    }

    /** A live hostile mech or ground vehicle. */
    private boolean hostileMachine(long candidate, Faction own) {
        if (convoy.isVehicle(candidate)) {
            return convoy.isTargetable(candidate) && convoy.structure(candidate) > 0f
                    && own.hostileTo(convoy.faction(candidate));
        }
        return roster.isAliveById(candidate) && roster.world().hasMechLoadout(candidate)
                && own.hostileTo(roster.identity().faction(candidate));
    }

    /** What a machine can still shoot with: a mech's loaded mounts, or a hull's turret gun while it has one. */
    private List<WeaponDef> weaponsOf(long candidate) {
        List<WeaponDef> out = new ArrayList<>(3);
        if (convoy.isVehicle(candidate)) {
            VehicleType type = convoy.vehicleType(candidate);
            if (type != null && convoy.isArmed(candidate)) out.add(type.turretStructure().mount.weapon);
            return out;
        }
        MechLoadoutComponent loadout = roster.world().mechLoadout(candidate);
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && (mount.hasAmmo() || mount.burstRemaining > 0)) out.add(mount.weaponDef());
        }
        return out;
    }

    private boolean valid(long candidate, Faction own, GroundBody body, float aware, boolean indirect) {
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
                (int) Math.floor(at[0]), (int) Math.floor(at[1]))
                || (indirect && spotting.spotted(own, candidate));
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
        if (type != null && convoy.isArmed(target)) out.add(Weapon.of(type.turretStructure().mount.weapon));
        return out;
    }
}
