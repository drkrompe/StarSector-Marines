package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

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
 * Mechs take part with a reduced share ({@link #MECH_SHARE}); emplacements,
 * aircraft, drones and other vehicles do not. A hover hull pushes nobody: its
 * skirt has no purchase on a body ({@link VehicleType#repelsBodies()}). Every displacement goes through
 * the same topology guard separation uses (full move, then either axis slide),
 * so a push can never put somebody inside a wall; a body pinned against one is
 * left where it is. Faction does not matter: a hull is physical.
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
    /** Push-speed ceiling for a parked hull, in cells per second. */
    static final float BASE_PUSH_SPEED = 2.5f;
    /** Additional push-speed ceiling per cell per second of hull speed. */
    static final float SPEED_PUSH_GAIN = 1.6f;
    /** Fraction of the push a mech accepts; it is heavy and mostly holds its ground. */
    static final float MECH_SHARE = 0.3f;

    private final UnitRosterService roster;
    private final ConvoyService convoy;
    private final World world;
    private final UnitSpatialIndex unitIndex;
    private final NavigationGrid grid;
    private final EntityWorld entityWorld;
    private final BattleComponents components;
    private final LongBucket scratch = new LongBucket();
    private final float[] push = new float[2];
    private long pushes;

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
    }

    private void repel(long vehicle, GroundBody body, VehicleType type, float dt) {
        float halfL = type.visualLengthCells * .5f, halfW = type.visualWidthCells * .5f;
        float speed = Math.abs(body.speed);
        float bow = speed >= MOVING_SPEED ? BOW_MIN_CELLS + speed * BOW_SECONDS : 0f;
        float reach = (float) Math.hypot(halfL + bow + 1f, halfW + BOW_LATERAL_MARGIN + 1f);
        unitIndex.gather(body.x, body.y, reach, scratch);
        float maxStep = (BASE_PUSH_SPEED + SPEED_PUSH_GAIN * speed) * dt;
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
            if (!displacement(body.x, body.y, body.facingDegrees, body.speed, halfL, halfW,
                    world.x(unit), world.y(unit), radius, dt, unit, push)) continue;
            float share = unitType.isMech() ? MECH_SHARE : 1f;
            float px = push[0] * share, py = push[1] * share;
            float mag = (float) Math.hypot(px, py);
            if (mag > maxStep) { px *= maxStep / mag; py *= maxStep / mag; }
            if (apply(unit, unitType, radius, px, py, dt)) pushes++;
        }
    }

    /**
     * The displacement one hull asks of one body this tick, before mass and the
     * per-tick ceiling. Pure geometry: {@code facingDegrees} uses the chassis
     * convention (forward is {@code (-sin, cos)}) and a negative {@code speed}
     * is reversing. Writes {@code out} and returns {@code true} when there is
     * anything to do.
     */
    static boolean displacement(float vx, float vy, float facingDegrees, float speed,
                                float halfL, float halfW, float ux, float uy, float radius,
                                float dt, long tiebreak, float[] out) {
        double rad = Math.toRadians(facingDegrees);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float sx = fy, sy = -fx;
        float dx = ux - vx, dy = uy - vy;
        float along = dx * fx + dy * fy, across = dx * sx + dy * sy;
        float travel = Math.abs(speed) >= MOVING_SPEED ? Math.signum(speed) : 0f;
        float ef = halfL + radius + CLEARANCE, ew = halfW + radius + CLEARANCE;
        float side = across > 1e-3f ? 1f : across < -1e-3f ? -1f : ((tiebreak & 1L) == 0L ? 1f : -1f);
        float absAlong = Math.abs(along), absAcross = Math.abs(across);
        if (absAlong < ef && absAcross < ew) {
            float outAcross = ew - absAcross, outAlong = ef - absAlong;
            boolean inFront = travel != 0f && along * travel > 0f;
            if (inFront || outAcross <= outAlong) {
                // Out the near side. In front of a moving hull, ride the bow
                // forward too so the hull cannot overtake the body mid-slide.
                float carry = inFront ? Math.abs(speed) * dt : 0f;
                out[0] = sx * side * outAcross + fx * travel * carry;
                out[1] = sy * side * outAcross + fy * travel * carry;
            } else {
                float end = along >= 0f ? 1f : -1f;
                out[0] = fx * end * outAlong;
                out[1] = fy * end * outAlong;
            }
            return true;
        }
        if (travel == 0f) return false;
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
