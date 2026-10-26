package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.combat.StaggerService.Cause;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.HullImpact;
import com.dillon.starsectormarines.battle.vehicle.HullRam;
import com.dillon.starsectormarines.battle.vehicle.HullShove;
import com.dillon.starsectormarines.battle.vehicle.VehicleFootprint;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.HashSet;
import java.util.Set;

/**
 * Ground vehicles shove people out of their way.
 *
 * <p>A hull is an oriented rectangle that infantry used to walk through and
 * that rolled over infantry standing in the road, because nothing on either
 * side knew about the other: vehicles stamp no navigation footprint while they
 * move, and {@link SeparationSystem} only sees roster bodies, which a vehicle is
 * not. This pass gives each live, visible hull two fields:
 * <ul>
 *   <li><b>Contact.</b> A body overlapping the hull (inflated by its own radius)
 *       is pushed out along the shallower axis. A body in front of a moving hull
 *       is always pushed out sideways and is carried along with the bow while it
 *       goes, so a truck bulldozes a man aside instead of passing over him.</li>
 *   <li><b>Bow wave.</b> A body in the strip the hull will cover in the next
 *       {@link #BOW_SECONDS} is pushed sideways off the centerline, harder the
 *       faster the hull is going and the closer the body is. People step out of
 *       a column's path before it reaches them.</li>
 * </ul>
 * Emplacements, aircraft, drones and other vehicles do not take part (hulls meet
 * each other in {@link HullContactSystem}). A hover hull pushes nobody: its
 * skirt has no purchase on a body ({@link VehicleType#repelsBodies()}). Every displacement goes through
 * the same topology guard separation uses (full move, then either axis slide),
 * so a push can never put somebody inside a wall; a body pinned against one is
 * left where it is. Faction does not matter to the shove: a hull is physical.
 *
 * <p><b>Mass and ramming</b> ({@link HullRam}). A body takes the share of a contact
 * push its mass leaves it ({@link HullRam#shoveShare}) and the rest comes back onto
 * the hull, so a pickup is held up by a Brute or a mech instead of driving into it;
 * a parked hull's brakes hold it against anything lighter, which takes the whole push.
 * Against the other side the hull gives no bow wave, and the first tick a body is on
 * a moving bow the two exchange an impulse: the body is hurt, knocked down or
 * staggered by the velocity change it took, and the hull is slowed, hurt and
 * stalled by its own. A hostile mech meets the hull from any side, and its own walk
 * into the hull closes the gap as the hull's drive does: one mass model, so a mech
 * against a tank resolves by weight like any other pair ({@link MechRamSystem} is
 * a mech against bodies). Friendly, allied and civilian bodies are shoved and never hurt.
 *
 * <p>Runs in the separation slot, after soft-collision relaxation, so it reads
 * the hulls where last tick's ground pass left them. Applied displacement is
 * folded into the body's movement velocity so facing animates the stumble.
 */
public final class VehicleRepulsionSystem {

    /** {@code -Dbattle.vehicle.repulsion=false} is the control: hulls and bodies ignore each other again. */
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("battle.vehicle.repulsion", "true"));

    /** Look-ahead of the bow wave, in seconds of the hull's current speed. */
    static final float BOW_SECONDS = 1.1f;
    /** Bow-wave reach at a crawl, so a hull easing forward still clears its nose. */
    static final float BOW_MIN_CELLS = 0.6f;
    /** Extra lateral reach of the bow wave beyond the inflated hull side. */
    static final float BOW_LATERAL_MARGIN = 0.6f;
    /** Bow-wave sideways push at full proximity, as a multiple of hull speed. */
    static final float BOW_GAIN = 1.3f;
    /** Skin kept between a hull and a body it has pushed clear. */
    static final float CLEARANCE = 0.12f;
    /** Below this speed a hull is parked: contact still pushes, no bow wave. */
    static final float MOVING_SPEED = 0.15f;
    /** How far beyond a stopped hull routes keep clear, cells: about a body's radius. */
    static final float HULL_ROUTE_MARGIN = 0.3f;
    /** Occupancy a stopped hull's cell carries: sixteen bodies' worth, so a route takes a detour of several cells first. */
    static final int HULL_OCCUPANCY = 16;
    /** Push-speed ceiling for a parked hull, in cells per second. */
    static final float BASE_PUSH_SPEED = 2.5f;
    /** Additional push-speed ceiling per cell per second of hull speed. */
    static final float SPEED_PUSH_GAIN = 1.6f;

    private final UnitRosterService roster;
    private final ConvoyService convoy;
    private final World world;
    private final UnitSpatialIndex unitIndex;
    private final NavigationGrid grid;
    private final EntityWorld entityWorld;
    private final BattleComponents components;
    private final LongBucket scratch = new LongBucket();
    private final float[] push = new float[4];
    /** Hostile body-on-bow pairs this tick and last; a ram lands on the tick a pair first touches. */
    private Set<Long> touching = new HashSet<>(), touched = new HashSet<>();
    private long pushes, rams;
    private float ramDamageDealt, ramDamageTaken, fastestRam;

    public VehicleRepulsionSystem(UnitRosterService roster, UnitSpatialIndex unitIndex, NavigationGrid grid) {
        this.roster = roster;
        this.convoy = roster.convoy();
        this.world = roster.world();
        this.unitIndex = unitIndex;
        this.grid = grid;
        this.entityWorld = roster.entityWorld();
        this.components = roster.components();
    }

    /** Bodies displaced by a hull since the battle began; evidence, not state anything reads. */
    public long pushes() { return pushes; }

    /** Rams landed on hostile bodies since the battle began; evidence. */
    public long rams() { return rams; }

    /** Damage those rams dealt the bodies, and cost the hulls back; evidence. */
    public float ramDamageDealt() { return ramDamageDealt; }
    public float ramDamageTaken() { return ramDamageTaken; }

    /** The highest closing speed a ram was struck at, cells a second; evidence. */
    public float fastestRam() { return fastestRam; }

    public void tick(float dt) {
        if (!ENABLED || !(dt > 0f) || convoy.vehicleCount() == 0) return;
        for (long vehicle : convoy.entityIds()) {
            VehicleMission mission = convoy.mission(vehicle);
            GroundBody body = convoy.body(vehicle);
            VehicleType type = convoy.vehicleType(vehicle);
            if (mission == null || body == null || type == null || !mission.isVisible()
                    || mission.state == VehicleState.WRECKED || mission.state == VehicleState.GONE
                    || !type.repelsBodies()) continue;
            repel(vehicle, body, type, dt);
        }
        Set<Long> swap = touched;
        touched = touching;
        touching = swap;
        touching.clear();
    }

    /**
     * Marks every stopped hull's footprint, widened by a body's reach, as
     * heavily occupied, so routes go round a parked vehicle instead of through
     * it. A hull stamps no walkability, so without this a route crosses it and
     * {@link #tick} pushes the walker back out at the same rate he walks in.
     * Moving hulls are left to the bow wave: their cells change every tick.
     */
    public void occupyStandingHulls(NavigationService navigation) {
        forEachStandingHullCell((x, y) -> navigation.addOccupancy(x, y, HULL_OCCUPANCY));
    }

    /** Visits every cell a stopped hull keeps routes off (see {@link #occupyStandingHulls}); a cell may come twice. */
    public void forEachStandingHullCell(VehicleFootprint.CellConsumer cells) {
        if (!ENABLED || convoy.vehicleCount() == 0) return;
        for (long vehicle : convoy.entityIds()) {
            VehicleMission mission = convoy.mission(vehicle);
            GroundBody body = convoy.body(vehicle);
            VehicleType type = convoy.vehicleType(vehicle);
            if (mission == null || body == null || type == null || !mission.isVisible()
                    || mission.state == VehicleState.WRECKED || mission.state == VehicleState.GONE
                    || !type.repelsBodies()) continue;
            standingHullCells(body.x, body.y, body.facingDegrees, body.speed,
                    type.visualLengthCells, type.visualWidthCells, grid, cells);
        }
    }

    /**
     * The stopped hull hostile to {@code side} whose route cells (see {@link #forEachStandingHullCell}) take in cell
     * {@code (x, y)}, or 0: the hull a body whose way runs through that cell has in front of it.
     */
    public long standingHullAt(int x, int y, Faction side) {
        if (!ENABLED || convoy.vehicleCount() == 0) return 0L;
        for (long vehicle : convoy.entityIds()) {
            VehicleMission mission = convoy.mission(vehicle);
            GroundBody body = convoy.body(vehicle);
            VehicleType type = convoy.vehicleType(vehicle);
            if (mission == null || body == null || type == null || !mission.isVisible()
                    || mission.state == VehicleState.WRECKED || mission.state == VehicleState.GONE
                    || !type.repelsBodies() || Math.abs(body.speed) >= MOVING_SPEED) continue;
            Faction owner = convoy.faction(vehicle);
            if (side != null && (owner == null || !side.hostileTo(owner))) continue;
            float d = HullImpact.surfaceDistance(body.x, body.y, body.facingDegrees,
                    type.visualLengthCells / 2f + HULL_ROUTE_MARGIN, type.visualWidthCells / 2f + HULL_ROUTE_MARGIN,
                    x + .5f, y + .5f);
            // The cell's centre within half its diagonal of the widened footprint: the cell overlaps it.
            if (d <= CELL_HALF_DIAGONAL) return vehicle;
        }
        return 0L;
    }

    private static final float CELL_HALF_DIAGONAL = 0.71f;

    /** Whether a hull going at {@code speed} stands: routes keep off it and a striker takes it from the side. */
    public static boolean standing(float speed) {
        return Math.abs(speed) < MOVING_SPEED;
    }

    /** The cells a route keeps off for one hull: its footprint widened by {@link #HULL_ROUTE_MARGIN}, none while it moves. */
    static void standingHullCells(float x, float y, float facingDegrees, float speed, float lengthCells,
                                  float widthCells, NavigationGrid grid, VehicleFootprint.CellConsumer cells) {
        if (Math.abs(speed) >= MOVING_SPEED) return;
        VehicleFootprint.forEachOverlappedCell(x, y, facingDegrees, 0f, lengthCells + 2f * HULL_ROUTE_MARGIN,
                widthCells + 2f * HULL_ROUTE_MARGIN, grid, cells);
    }

    private void repel(long vehicle, GroundBody body, VehicleType type, float dt) {
        float halfL = type.visualLengthCells * .5f, halfW = type.visualWidthCells * .5f;
        float speed = Math.abs(body.speed);
        float bow = speed >= MOVING_SPEED ? BOW_MIN_CELLS + speed * BOW_SECONDS : 0f;
        float reach = (float) Math.hypot(halfL + bow + 1f, halfW + BOW_LATERAL_MARGIN + 1f);
        unitIndex.gather(body.x, body.y, reach, scratch);
        float maxStep = (BASE_PUSH_SPEED + SPEED_PUSH_GAIN * speed) * dt;
        Faction side = convoy.faction(vehicle);
        float hullMass = type.massTonnes();
        float backX = 0f, backY = 0f;
        for (int k = 0, n = scratch.size; k < n; k++) {
            long unit = scratch.ids[k];
            if (unit == vehicle || convoy.isVehicle(unit) || !roster.isAliveById(unit)
                    || !entityWorld.has(unit, components.POSITION) || !entityWorld.has(unit, components.MOVEMENT)
                    || world.hasKinematics(unit)) continue;
            UnitType unitType = roster.identity().type(unit);
            if (unitType == null || unitType.isStatic() || unitType == UnitType.GROUND_VEHICLE
                    || entityWorld.getInt(unit, components.ROLE, BattleComponents.ROLE_ORDINAL)
                    == UnitRole.STRUCTURE.ordinal()) continue;
            float radius = roster.radius(unit);
            boolean hostile = side != null && side.hostileTo(roster.identity().faction(unit));
            // A walking mech that meets a hull slides round its side rather than being stood off its end.
            boolean slide = unitType.isMech() && walking(unit);
            if (!displacement(body.x, body.y, body.facingDegrees, body.speed, halfL, halfW,
                    world.x(unit), world.y(unit), radius, dt, unit, !hostile, slide, push)) continue;
            boolean contact = push[2] != 0f;
            float bodyMass = bodyTonnes(unit, unitType);
            // A machine meets a hull from any side; anything else is struck only by the bow.
            if (hostile && (push[3] != 0f || contact && unitType.isMech())) {
                long pair = pairKey(vehicle, unit);
                touching.add(pair);
                if (!touched.contains(pair)) ram(vehicle, body, hullMass, unit, unitType, bodyMass, push[3] != 0f);
                if (!roster.isAliveById(unit)) continue;
            }
            // A parked hull's brakes hold it against anything lighter than itself: that body takes the whole push.
            boolean braked = Math.abs(body.speed) < MOVING_SPEED && bodyMass < hullMass;
            float share = braked ? 1f : HullRam.shoveShare(hullMass, bodyMass);
            float px = push[0], py = push[1];
            float mag = (float) Math.hypot(px, py);
            if (mag > maxStep) { px *= maxStep / mag; py *= maxStep / mag; }
            if (apply(unit, unitType, radius, px * share, py * share, dt)) pushes++;
            // What the body did not give comes back onto the hull: a heavy one holds it up.
            if (contact && !braked) {
                backX -= px * (1f - share);
                backY -= py * (1f - share);
            }
        }
        // A crowd of men leaning on a hull moves it by hairs; only sweep for a push worth a sweep.
        if (Math.abs(backX) + Math.abs(backY) > 1e-3f) {
            float mag = (float) Math.hypot(backX, backY);
            if (mag > maxStep) { backX *= maxStep / mag; backY *= maxStep / mag; }
            HullShove.move(body, type, grid, backX, backY, false, push);
        }
    }

    /**
     * The bow lands on a hostile body ({@link HullRam}): the impulse of the hull's travel against the body's
     * retreat, damage both ways through the shared armour law, the body knocked down or staggered and the hull
     * slowed and stalled by what each could not shed.
     */
    private void ram(long vehicle, GroundBody body, float hullMass, long unit, UnitType unitType, float bodyMass,
                     boolean onBow) {
        double rad = Math.toRadians(body.facingDegrees);
        float travel = Math.signum(body.speed);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        // The contact normal, hull toward body: the bow's travel, or for a mech beside the hull the way it is pushed out.
        float nx, ny;
        if (onBow) {
            nx = fx * travel;
            ny = fy * travel;
        } else {
            float len = (float) Math.hypot(push[0], push[1]);
            if (len < 1e-6f) return;
            nx = push[0] / len;
            ny = push[1] / len;
        }
        float toward = body.speed * (fx * nx + fy * ny);
        float away = entityWorld.getFloat(unit, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X) * nx
                + entityWorld.getFloat(unit, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y) * ny;
        // A mech's own walk into the hull closes the gap as the hull's drive does; a man's or a creature's does not.
        float closing = unitType.isMech() ? HullRam.machineClosing(toward, away) : HullRam.closing(toward, away);
        HullRam.Exchange hit = HullRam.exchange(closing, hullMass, bodyMass);
        if (hit.impulse() <= 0f) return;
        rams++;
        fastestRam = Math.max(fastestRam, closing);
        float along = Math.abs(fx * nx + fy * ny);
        body.speed -= Math.signum(body.speed) * Math.min(Math.abs(body.speed), hit.hullDv() * along);
        if (hit.bodyDamage() > 0f) {
            ramDamageDealt += hit.bodyDamage();
            roster.damageService().applyDamage(unit, vehicle, hit.bodyDamage(), HullRam.PENETRATION, 1f);
        }
        if (hit.hullDamage() > 0f) {
            ramDamageTaken += hit.hullDamage();
            roster.damageService().applyDamage(vehicle, unit, hit.hullDamage(), HullRam.PENETRATION, 0f);
        }
        if (hit.bodyDv() >= HullRam.KNOCKDOWN_DV && roster.isAliveById(unit)) {
            roster.stagger().stagger(unit, ticks(HullRam.KNOCKDOWN_SECONDS),
                    unitType.isMech() ? Cause.STAGGERED : Cause.KNOCKED_DOWN);
        }
        if (hit.hullDv() >= HullRam.STALL_DV) {
            roster.stagger().stagger(vehicle, ticks(HullRam.STALL_SECONDS), Cause.STALLED);
        }
    }

    private boolean walking(long unit) {
        float vx = entityWorld.getFloat(unit, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X);
        float vy = entityWorld.getFloat(unit, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y);
        return vx * vx + vy * vy > MOVING_SPEED * MOVING_SPEED;
    }

    private float bodyTonnes(long unit, UnitType type) {
        MechLoadoutComponent loadout = type.isMech() ? world.mechLoadout(unit) : null;
        return HullRam.bodyTonnes(type, roster.identity().swarmStage(unit),
                loadout == null ? 1f : loadout.variant.relativeMass);
    }

    private static long pairKey(long vehicle, long unit) {
        return vehicle * 0x9E3779B97F4A7C15L ^ unit;
    }

    private static int ticks(float seconds) {
        return Math.max(1, Math.round(seconds / BattleSimulation.TICK_DT));
    }

    /**
     * The displacement one hull asks of one body this tick, before mass and the
     * per-tick ceiling. Pure geometry: {@code facingDegrees} uses the chassis
     * convention (forward is {@code (-sin, cos)}) and a negative {@code speed}
     * is reversing. Writes {@code out} and returns {@code true} when there is
     * anything to do: {@code out[0..1]} the push, {@code out[2]} nonzero for a body
     * overlapping the hull (rather than in its bow wave), {@code out[3]} nonzero for
     * one on the bow of a moving hull. {@code bowWave} false leaves the road ahead
     * alone, for a body the hull means to hit.
     */
    static boolean displacement(float vx, float vy, float facingDegrees, float speed,
                                float halfL, float halfW, float ux, float uy, float radius,
                                float dt, long tiebreak, boolean bowWave, float[] out) {
        return displacement(vx, vy, facingDegrees, speed, halfL, halfW, ux, uy, radius, dt, tiebreak, bowWave,
                false, out);
    }

    /** {@link #displacement}, with {@code slide} pushing an overlapping body out sideways wherever it touches. */
    static boolean displacement(float vx, float vy, float facingDegrees, float speed,
                                float halfL, float halfW, float ux, float uy, float radius,
                                float dt, long tiebreak, boolean bowWave, boolean slide, float[] out) {
        double rad = Math.toRadians(facingDegrees);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float sx = fy, sy = -fx;
        float dx = ux - vx, dy = uy - vy;
        float along = dx * fx + dy * fy, across = dx * sx + dy * sy;
        float travel = Math.abs(speed) >= MOVING_SPEED ? Math.signum(speed) : 0f;
        float ef = halfL + radius + CLEARANCE, ew = halfW + radius + CLEARANCE;
        float side = across > 1e-3f ? 1f : across < -1e-3f ? -1f : ((tiebreak & 1L) == 0L ? 1f : -1f);
        float absAlong = Math.abs(along), absAcross = Math.abs(across);
        out[2] = out[3] = 0f;
        if (absAlong < ef && absAcross < ew) {
            float outAcross = ew - absAcross, outAlong = ef - absAlong;
            boolean inFront = travel != 0f && along * travel > 0f;
            out[2] = 1f;
            out[3] = inFront ? 1f : 0f;
            if (inFront || slide || outAcross <= outAlong) {
                // Out the near side. In front of a moving hull, ride the bow
                // forward too so the hull cannot overtake the body mid-slide.
                float carry = inFront ? Math.abs(speed) * dt : 0f;
                out[0] = sx * side * outAcross + fx * travel * carry;
                out[1] = sy * side * outAcross + fy * travel * carry;
                if (slide && !inFront && outAlong < outAcross) {
                    // Sliding round an end: held off it as it goes, so it rounds the corner instead of cutting it.
                    float end = along >= 0f ? 1f : -1f;
                    out[0] += fx * end * outAlong;
                    out[1] += fy * end * outAlong;
                }
            } else {
                float end = along >= 0f ? 1f : -1f;
                out[0] = fx * end * outAlong;
                out[1] = fy * end * outAlong;
            }
            return true;
        }
        if (travel == 0f || !bowWave) return false;
        float ahead = along * travel - ef;
        float bow = BOW_MIN_CELLS + Math.abs(speed) * BOW_SECONDS;
        if (ahead < 0f || ahead > bow || absAcross >= ew + BOW_LATERAL_MARGIN) return false;
        float proximity = 1f - ahead / bow;
        float centred = 1f - absAcross / (ew + BOW_LATERAL_MARGIN);
        float step = BOW_GAIN * Math.abs(speed) * proximity * (.4f + .6f * centred) * dt;
        out[0] = sx * side * step;
        out[1] = sy * side * step;
        return true;
    }

    private boolean apply(long unit, UnitType type, float radius, float px, float py, float dt) {
        if (px == 0f && py == 0f) return false;
        float ax = world.x(unit), ay = world.y(unit);
        float appliedX, appliedY;
        if (type.isMech()) {
            ManualTerrainMotion.Result result = ManualTerrainMotion.move(grid, ax, ay, px, py, radius);
            appliedX = result.dx();
            appliedY = result.dy();
        } else if (canStep(ax, ay, ax + px, ay + py)) {
            appliedX = px; appliedY = py;
        } else if (canStep(ax, ay, ax + px, ay)) {
            appliedX = px; appliedY = 0f;
        } else if (canStep(ax, ay, ax, ay + py)) {
            appliedX = 0f; appliedY = py;
        } else {
            return false;
        }
        if (appliedX == 0f && appliedY == 0f) return false;
        world.setPos(unit, ax + appliedX, ay + appliedY);
        entityWorld.setFloat(unit, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X,
                entityWorld.getFloat(unit, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X) + appliedX / dt);
        entityWorld.setFloat(unit, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y,
                entityWorld.getFloat(unit, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y) + appliedY / dt);
        return true;
    }

    private boolean canStep(float fromX, float fromY, float toX, float toY) {
        int tx = (int) Math.floor(toX), ty = (int) Math.floor(toY);
        return grid.inBounds(tx, ty) && grid.canTraverseCellStep(
                (int) Math.floor(fromX), (int) Math.floor(fromY), tx, ty);
    }
}
