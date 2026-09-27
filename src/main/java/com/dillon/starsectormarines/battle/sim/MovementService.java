package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechLocomotion;
import com.dillon.starsectormarines.battle.nav.ContinuousRoute;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.vehicle.PurePursuit;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.Query;

import java.util.Arrays;

/**
 * Data owner for the {@code MOVEMENT} component — typed by-id access (read +
 * mutate) to a mover's path-following state in the archetype {@link EntityWorld}:
 * gait phase, the flat {@code int[]} path + cursor, the move-speed stat, and the
 * repath-throttle stamp.
 *
 * <p>A <b>Service</b> (data owner) in the sense described on {@link CombatService}:
 * consumers are constructor-injected with it (or reach {@code sim.movement()} /
 * {@code roster.movement()}) and call {@code movement.moveSpeed(id)} directly — no
 * {@link World} hop. Per-tick bulk systems column-walk the MOVEMENT table instead.
 *
 * <p>{@code MOVEMENT} is OPTIONAL (mover-narrowed): {@link #has} is the presence
 * check; the field accessors are <b>fail-loud</b> on a static emplacement (turret,
 * hub) that lacks it (and on a corpse). Gate on {@link #has} first.
 *
 * <p>The occupancy-bookkeeping path change goes through
 * {@code BattleControl.setPath} (NavigationService); {@link #setPathRef} is the raw
 * column write it calls under the hood. Serial-only.
 */
public final class MovementService {

    /**
     * Arrival tolerance around a destination cell center, in cells — the
     * {@link #atCell} radius. Kept under 0.5 so "at this cell" still implies
     * the floored grid cell matches.
     */
    public static final float ARRIVE_RADIUS = 0.35f;
    /**
     * Carrot lookahead along the path polyline, in cells. Kept below one cell
     * so the pursuit line stays within the current/next path cells and can't
     * cut through a wall corner the path routed around.
     */
    public static final float LOOKAHEAD = 0.45f;
    /**
     * Minimum sim-seconds between {@code setPath} assignments while in motion
     * — {@link #mayRepath}'s throttle. Replaces the retired cell-boundary
     * repath gate, whose cadence at typical move speeds this matches; without
     * it every repath-gated behavior would re-run findPath every tick.
     */
    public static final float REPATH_INTERVAL = 0.35f;
    /** Arrival window in which a shared-destination infantry formation may finish settling. */
    public static final float FORMATION_MEMORY_SECONDS = 2f;

    private final EntityWorld entityWorld;
    private final BattleComponents components;
    private final Query movers;
    private NavigationGrid terrain;

    /** Sim clock for the repath throttle — advanced once per tick by {@link #beginTick}. */
    private float now;

    public MovementService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
        this.movers = entityWorld.query(new ComponentType[]{components.MOVEMENT}, null);
    }

    /** Bind current terrain during roster setup for physical arrival checks. */
    public void setNavigationGrid(NavigationGrid grid) { terrain = grid; }

    /**
     * Per-tick prologue, called exactly once per sim tick by
     * {@code BattleSimulation}: advances the repath clock and zeroes every
     * mover's applied-velocity fields, so a unit whose behavior does not call
     * {@link #advanceAlongPath} this tick reads as not moving (velocity is
     * "movement actually applied this tick", never a stale carry-over).
     */
    public void beginTick(float dt) {
        now += dt;
        for (ArchetypeTable t : entityWorld.matched(movers)) {
            int n = t.rowCount();
            Arrays.fill(t.floats(components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X).array(), 0, n, 0f);
            Arrays.fill(t.floats(components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y).array(), 0, n, 0f);
            float[] formationMemory = t.floats(components.MOVEMENT,
                    BattleComponents.MOVEMENT_FORMATION_MEMORY_TIMER).array();
            for (int r = 0; r < n; r++) {
                formationMemory[r] = Math.max(0f, formationMemory[r] - dt);
            }
        }
    }

    /** Presence check — true iff {@code id} carries MOVEMENT (is a mover). Gate field reads on this. */
    public boolean has(long id) { return entityWorld.has(id, components.MOVEMENT); }

    // ---- movement-semantic gates ----
    //
    // The three intents the retired cell-hop mover overloaded onto
    // "moveProgress == 0" (standing on a cell center), now each with its own
    // continuous-motion meaning.

    /**
     * The unit has arrived at cell {@code (cx, cy)} for post/destination
     * tests: within {@link #ARRIVE_RADIUS} of the cell center. The mover pins
     * a finished path exactly on its final cell center, so a unit that walked
     * a path to this cell always answers {@code true} on arrival. Static positioned
     * bodies use the same cell-distance test; carriers without POSITION answer false.
     */
    public boolean atCell(long id, int cx, int cy) {
        if (!entityWorld.has(id, components.POSITION)) return false;
        ContinuousRoute route = has(id) ? continuousRoute(id) : null;
        if (route != null && route.requestedCellX() == cx && route.requestedCellY() == cy) {
            if (!route.completed()) return false;
            float x = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_X);
            float y = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_Y);
            if (terrain != null && !ManualTerrainMotion.canStand(terrain, x, y, route.radius())) return false;
            float rx = x - route.endX();
            float ry = y - route.endY();
            return rx * rx + ry * ry <= ARRIVE_RADIUS * ARRIVE_RADIUS;
        }
        float dx = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_X) - (cx + 0.5f);
        float dy = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_Y) - (cy + 0.5f);
        return dx * dx + dy * dy <= ARRIVE_RADIUS * ARRIVE_RADIUS;
    }

    /**
     * The unit is not in motion — the act/stance gate ("dwell, fire stanced,
     * play the idle pose"): no un-exhausted path.
     */
    public boolean settled(long id) {
        return pathIdx(id) >= Paths.cellCount(path(id));
    }

    /**
     * A new path may be assigned right now: always when idle; while in motion,
     * throttled to one assignment per {@link #REPATH_INTERVAL} (the carrot
     * follower accepts a mid-motion re-route — the throttle only bounds the
     * findPath cost, not correctness). Distinct from {@link #settled}:
     * "only repath when idle" would stall long paths.
     */
    public boolean mayRepath(long id) {
        return settled(id)
                || now - entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_LAST_REPATH_TIME) >= REPATH_INTERVAL;
    }

    /** Stamp the repath throttle — called by {@code NavigationService.setPath} on every non-empty assignment. */
    public void markRepath(long id) {
        entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_LAST_REPATH_TIME, now);
    }

    /** Member-owned admission handback; independent of the path and repath clock. */
    public void requireObjectiveRouteRefresh(long id) {
        entityWorld.setInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_OBJECTIVE_ROUTE_REFRESH, 1);
    }

    public boolean needsObjectiveRouteRefresh(long id) {
        return entityWorld.getInt(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_OBJECTIVE_ROUTE_REFRESH) != 0;
    }

    /** Clear only after this member installs its current objective route. */
    public void objectiveRouteRefreshed(long id) {
        entityWorld.setInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_OBJECTIVE_ROUTE_REFRESH, 0);
    }

    /** Per-unit movement speed in cells/sec (seed-only mover stat). Fail-loud on a non-mover; gate on {@link #has}. */
    public float moveSpeed(long id) { return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_SPEED); }

    /** Velocity x actually applied by this tick's movement pass, cells/sec — 0 when the mover hasn't moved this tick. Fail-loud on a non-mover; gate on {@link #has}. */
    public float velX(long id) { return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X); }

    /** Velocity y actually applied by this tick's movement pass, cells/sec. See {@link #velX}. */
    public float velY(long id) { return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y); }

    float formationMemoryTimer(long id) {
        return entityWorld.getFloat(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_FORMATION_MEMORY_TIMER);
    }

    void setFormationMemoryTimer(long id, float seconds) {
        entityWorld.setFloat(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_FORMATION_MEMORY_TIMER,
                Math.max(0f, seconds));
    }

    public int[] path(long id) { return (int[]) entityWorld.getObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH); }
    public void setPathRef(long id, int[] p) { entityWorld.setObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH, p); }

    /** Nullable route geometry; a completed route may remain as an arrival witness. */
    public ContinuousRoute continuousRoute(long id) {
        return (ContinuousRoute) entityWorld.getObject(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_CONTINUOUS_ROUTE);
    }

    /** Raw state write; NavigationService owns atomic route/projection/occupancy installation. */
    public void setContinuousRouteRef(long id, ContinuousRoute route) {
        entityWorld.setObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_CONTINUOUS_ROUTE, route);
    }

    /** Active projected path length; a cleared completed witness schedules no motion. */
    public int waypointCount(long id) { return Paths.cellCount(path(id)); }

    public float waypointX(long id, int index) {
        ContinuousRoute route = continuousRoute(id);
        return route == null ? Paths.cellX(path(id), index) + 0.5f : route.x(index);
    }

    public float waypointY(long id, int index) {
        ContinuousRoute route = continuousRoute(id);
        return route == null ? Paths.cellY(path(id), index) + 0.5f : route.y(index);
    }

    /** Read-only cursor lookahead over points already coincident with the body. */
    public int nextWaypointIndex(long id) {
        int index = pathIdx(id), count = waypointCount(id);
        if (continuousRoute(id) == null) return index;
        float x = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_X);
        float y = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_Y);
        while (index < count && Math.hypot(waypointX(id, index) - x, waypointY(id, index) - y) <= 1e-5f) index++;
        return index;
    }

    public float destinationX(long id) {
        ContinuousRoute route = continuousRoute(id);
        return route != null ? route.endX() : Paths.isEmpty(path(id)) ? Float.NaN : Paths.destX(path(id)) + 0.5f;
    }

    public float destinationY(long id) {
        ContinuousRoute route = continuousRoute(id);
        return route != null ? route.endY() : Paths.isEmpty(path(id)) ? Float.NaN : Paths.destY(path(id)) + 0.5f;
    }

    public boolean pathTargetsCell(long id, int cx, int cy) {
        if (Paths.isEmpty(path(id))) return false;
        ContinuousRoute route = continuousRoute(id);
        return route != null ? route.requestedCellX() == cx && route.requestedCellY() == cy
                : Paths.destX(path(id)) == cx && Paths.destY(path(id)) == cy;
    }

    public int pathIdx(long id) { return entityWorld.getInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX); }
    public void setPathIdx(long id, int v) { entityWorld.setInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX, v); }

    private float gaitPhase(long id) { return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_GAIT_PHASE); }
    private void setGaitPhase(long id, float v) { entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_GAIT_PHASE, v); }

    private void setVelocity(long id, float vx, float vy) {
        entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X, vx);
        entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y, vy);
    }

    /**
     * Applies one infantry drive step at the unit's current movement speed.
     * Axes shorter than one retain their magnitude; longer input (including a
     * WASD diagonal) is normalized. Terrain contact determines the position,
     * velocity and gait actually applied. The session clears any AI path at
     * entry; this method does not author a route or its occupancy bookkeeping.
     */
    public void moveDirect(long id, NavigationGrid grid, float axisX, float axisY,
                           float radius, float dt) {
        ManualTerrainMotion.Result result = previewDirect(id, grid, axisX, axisY, radius, dt);
        setVelocity(id, 0f, 0f);
        setFormationMemoryTimer(id, 0f);
        if (dt == 0f) return;
        entityWorld.setFloat(id, components.POSITION, BattleComponents.POSITION_X, result.x());
        entityWorld.setFloat(id, components.POSITION, BattleComponents.POSITION_Y, result.y());
        setVelocity(id, result.dx() / dt, result.dy() / dt);
        float appliedDistance = (float) Math.hypot(result.dx(), result.dy());
        setGaitPhase(id, (gaitPhase(id) + appliedDistance) % 1f);
    }

    /**
     * Manual mech drive owns both hip steering and translation for this tick.
     * The hips retain their damped turn and pivot gate; only aligned input is
     * passed to the shared full-body terrain sweep. Neutral input brakes hip
     * momentum without consulting the AI's target or remembered contact.
     * The normal locomotion pass must skip this actor after this call.
     */
    public void moveDirectMech(long id, NavigationGrid grid, float axisX, float axisY,
                               float radius, float dt) {
        if (!Float.isFinite(axisX) || !Float.isFinite(axisY)
                || !Float.isFinite(radius) || radius <= 0f
                || !Float.isFinite(dt) || dt < 0f) {
            throw new IllegalArgumentException("Mech drive requires finite axes, positive radius and nonnegative time");
        }
        if (dt == 0f) {
            moveDirect(id, grid, 0f, 0f, radius, 0f);
            return;
        }
        if (axisX == 0f && axisY == 0f) {
            MechLocomotion.stopTurning(entityWorld, components, id, dt);
        } else {
            float error = MechLocomotion.turnToward(entityWorld, components, id,
                    MechLocomotion.continuousFacing(axisX, axisY), dt);
            if (error > MechLocomotion.MOVE_ALIGNMENT_DEGREES) {
                axisX = 0f;
                axisY = 0f;
            }
        }
        moveDirect(id, grid, axisX, axisY, radius, dt);
    }

    /**
     * The legal drive step at the current speed, without changing position,
     * velocity, gait, or path state. Early suit policies use this before their
     * speed effects are applied; the movement pass resolves again afterward.
     */
    public ManualTerrainMotion.Result previewDirect(long id, NavigationGrid grid,
                                                     float axisX, float axisY,
                                                     float radius, float dt) {
        if (!Float.isFinite(axisX) || !Float.isFinite(axisY)
                || !Float.isFinite(dt) || dt < 0f) {
            throw new IllegalArgumentException("Direct motion requires finite axes and nonnegative time");
        }
        float x = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_X);
        float y = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_Y);
        if (dt == 0f || (axisX == 0f && axisY == 0f)) {
            return new ManualTerrainMotion.Result(x, y, 0f, 0f);
        }
        double divisor = Math.max(1d, Math.hypot(axisX, axisY));
        float distance = Math.max(0f, moveSpeed(id)) * dt;
        float dx = (float) (axisX / divisor * distance);
        float dy = (float) (axisY / divisor * distance);
        return ManualTerrainMotion.move(grid, x, y, dx, dy, radius);
    }

    public enum MotionResult { IDLE, MOVED, HELD_FOR_TURN, ARRIVED, BLOCKED }

    /**
     * Optional infantry traffic shaping over the existing cell path. The offset
     * translates the pursuit corridor, not the authored path or its destination;
     * the caller owns traffic eligibility and terrain anticipation. Both the
     * shifted join and any return to the original corridor must cross legal
     * current grid transitions. A refused join holds rather than searching or
     * clearing the route. This preserves the ordinary repath clock.
     *
     * <p>Speed is capped at the ordinary mover's budget. Near the destination
     * the offset fades to zero, and only the original final center can settle
     * the path. Mech/continuous routing and manual movement use other methods.
     * A successfully applied shifted step leaves a member-owned safety guard:
     * subsequent ordinary cell-path motion remains terrain-checked until arrival,
     * including when contact or changed intent bypasses traffic coordination.
     */
    public void advanceAlongPath(World world, long id, float dt,
                                 float offsetX, float offsetY, float speedScale) {
        if (terrain == null) throw new IllegalStateException("Traffic movement requires bound terrain");
        if (!Float.isFinite(dt) || dt < 0f || !Float.isFinite(offsetX)
                || !Float.isFinite(offsetY) || !Float.isFinite(speedScale)) {
            throw new IllegalArgumentException("Traffic movement requires finite inputs and nonnegative time");
        }
        if (continuousRoute(id) != null || entityWorld.has(id, components.MECH_LOCOMOTION)) {
            throw new IllegalArgumentException("Traffic shaping is cell-native infantry movement");
        }
        setVelocity(id, 0f, 0f);
        int[] path = path(id);
        int index = pathIdx(id), count = Paths.cellCount(path);
        if (index >= count || dt == 0f) return;
        float step = Math.max(0f, moveSpeed(id)) * dt * Math.max(0f, Math.min(1f, speedScale));
        if (step == 0f) return;
        float px = world.x(id), py = world.y(id);
        float goalX = Paths.destX(path) + 0.5f, goalY = Paths.destY(path) + 0.5f;
        float goalDistance = (float) Math.hypot(goalX - px, goalY - py);
        float taper = Math.max(0f, Math.min(1f, (goalDistance - 2f) / 4f));
        offsetX *= taper;
        offsetY *= taper;
        PurePursuit.Carrot carrot = PurePursuit.pick(
                px - offsetX, py - offsetY, path, index, LOOKAHEAD);
        float tx = carrot.atEnd ? goalX : carrot.x + offsetX;
        float ty = carrot.atEnd ? goalY : carrot.y + offsetY;
        boolean shifted = !carrot.atEnd && (offsetX != 0f || offsetY != 0f);
        if (!trafficSegmentClear(terrain, px, py, tx, ty)) {
            shifted = false;
            carrot = PurePursuit.pick(px, py, path, index, LOOKAHEAD);
            tx = carrot.x;
            ty = carrot.y;
            if (!trafficSegmentClear(terrain, px, py, tx, ty)) return;
        }
        float dx = tx - px, dy = ty - py;
        float distance = (float) Math.hypot(dx, dy);
        if (distance <= 1e-6f) {
            if (carrot.atEnd && goalDistance <= 1e-6f) {
                setPathIdx(id, count);
                setTrafficReturnGuard(id, false);
            }
            return;
        }
        float travel = Math.min(step, distance);
        float nx = px + dx / distance * travel;
        float ny = py + dy / distance * travel;
        // Validate the actual float-rounded displacement as well as its join.
        if (!trafficSegmentClear(terrain, px, py, nx, ny)) return;
        boolean arrived = carrot.atEnd && distance <= step;
        world.setPos(id, arrived ? goalX : nx, arrived ? goalY : ny);
        setPathIdx(id, arrived ? count : carrot.nextIdx);
        float appliedX = world.x(id) - px, appliedY = world.y(id) - py;
        float appliedDistance = (float) Math.hypot(appliedX, appliedY);
        if (arrived) setTrafficReturnGuard(id, false);
        else if (shifted && appliedDistance > 0f) setTrafficReturnGuard(id, true);
        setVelocity(id, appliedX / dt, appliedY / dt);
        setGaitPhase(id, arrived ? 0f : (gaitPhase(id) + appliedDistance) % 1f);
        setFormationMemoryTimer(id, FORMATION_MEMORY_SECONDS);
    }

    /** Member-owned terrain obligation, not a retained traffic steering or speed hint. */
    private void setTrafficReturnGuard(long id, boolean active) {
        entityWorld.setInt(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_TRAFFIC_RETURN_GUARD, active ? 1 : 0);
    }

    /**
     * Exact local point-segment traversal, capped at 32 cells of displacement.
     * Cell-native infantry has no added body-clearance rule. Package-visible
     * so preparation and execution use the same wall/edge legality test.
     */
    static boolean trafficSegmentClear(NavigationGrid terrain,
                                       float x0, float y0, float x1, float y1) {
        if (!Float.isFinite(x0) || !Float.isFinite(y0)
                || !Float.isFinite(x1) || !Float.isFinite(y1)) return false;
        double dx = (double) x1 - x0, dy = (double) y1 - y0;
        if (dx * dx + dy * dy > 32d * 32d) return false;
        int cx = (int) Math.floor(x0), cy = (int) Math.floor(y0);
        int ex = (int) Math.floor(x1), ey = (int) Math.floor(y1);
        if (!terrain.inBounds(cx, cy) || !terrain.inBounds(ex, ey)
                || !terrain.isWalkable(cx, cy) || !terrain.isWalkable(ex, ey)) return false;
        int sx = Double.compare(dx, 0d), sy = Double.compare(dy, 0d);
        double deltaX = sx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1d / dx);
        double deltaY = sy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1d / dy);
        double crossX = sx == 0 ? Double.POSITIVE_INFINITY
                : ((sx > 0 ? cx + 1d : cx) - x0) / dx;
        double crossY = sy == 0 ? Double.POSITIVE_INFINITY
                : ((sy > 0 ? cy + 1d : cy) - y0) / dy;
        while (cx != ex || cy != ey) {
            int nx = cx, ny = cy;
            if (cx == ex) {
                ny += sy;
                crossY += deltaY;
            } else if (cy == ey) {
                nx += sx;
                crossX += deltaX;
            } else if (Math.abs(crossX - crossY) <= 1e-10) {
                nx += sx;
                ny += sy;
                crossX += deltaX;
                crossY += deltaY;
            } else if (crossX < crossY) {
                nx += sx;
                crossX += deltaX;
            } else {
                ny += sy;
                crossY += deltaY;
            }
            if (!terrain.canTraverseCellStep(cx, cy, nx, ny)) return false;
            cx = nx;
            cy = ny;
        }
        return true;
    }

    /**
     * Ground-body route following against current terrain. Continuous points
     * are followed segment by segment, with the same next-point bearing used
     * by hip steering. A contact may apply a legal partial displacement but
     * retires the attempted route through BLOCKED, never through arrival.
     * Legacy mech paths are also swept while their callers migrate.
     */
    public MotionResult advanceAlongPath(World world, long id, float dt,
                                         NavigationGrid grid, float radius) {
        if (!Float.isFinite(dt) || dt < 0f || !Float.isFinite(radius) || radius <= 0f) {
            throw new IllegalArgumentException("Finite nonnegative time and positive radius required");
        }
        setVelocity(id, 0f, 0f);
        int count = waypointCount(id);
        int index = pathIdx(id);
        if (index >= count || dt == 0f) return MotionResult.IDLE;
        ContinuousRoute route = continuousRoute(id);
        if (route != null && (route.pointCount() != count || route.radius() != radius)) return MotionResult.BLOCKED;
        if (!ManualTerrainMotion.canStand(grid, world.x(id), world.y(id), radius)) return MotionResult.BLOCKED;
        float remaining = Math.max(0f, moveSpeed(id)) * dt;
        float initialX = world.x(id), initialY = world.y(id);
        float traveled = 0f;
        MotionResult outcome = MotionResult.IDLE;
        while (index < count) {
            float px = world.x(id), py = world.y(id);
            float dx = waypointX(id, index) - px, dy = waypointY(id, index) - py;
            float distance = (float) Math.hypot(dx, dy);
            if (distance <= 1e-5f) {
                setPathIdx(id, ++index);
                continue;
            }
            if (remaining <= 0f) break;
            if (entityWorld.has(id, components.MECH_LOCOMOTION)) {
                float bearingX = dx, bearingY = dy;
                if (route == null) {
                    bearingX = Paths.cellX(path(id), index) - world.cellX(id);
                    bearingY = Paths.cellY(path(id), index) - world.cellY(id);
                }
                if (bearingX != 0f || bearingY != 0f) {
                    float desired = route == null ? MechLocomotion.desiredFacing(bearingX, bearingY)
                            : MechLocomotion.continuousFacing(bearingX, bearingY);
                    float current = entityWorld.getFloat(id, components.MECH_LOCOMOTION,
                            BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
                    if (Math.abs(MechLocomotion.deltaDegrees(current, desired)) > MechLocomotion.MOVE_ALIGNMENT_DEGREES) {
                        outcome = traveled > 0f ? MotionResult.MOVED : MotionResult.HELD_FOR_TURN;
                        break;
                    }
                }
            }
            float step = Math.min(remaining, distance);
            float requestedX = dx / distance * step, requestedY = dy / distance * step;
            ManualTerrainMotion.Result applied = ManualTerrainMotion.move(grid, px, py,
                    requestedX, requestedY, radius);
            world.setPos(id, applied.x(), applied.y());
            float appliedDistance = (float) Math.hypot(applied.dx(), applied.dy());
            traveled += appliedDistance;
            remaining -= step;
            if (Math.hypot(applied.x() - (px + requestedX), applied.y() - (py + requestedY)) > 1e-5f) {
                outcome = MotionResult.BLOCKED;
                break;
            }
            outcome = MotionResult.MOVED;
            if (Math.hypot(waypointX(id, index) - applied.x(), waypointY(id, index) - applied.y()) <= 1e-5f) {
                setPathIdx(id, ++index);
            }
        }
        if (index >= count) {
            if (route != null) setContinuousRouteRef(id, route.withCompleted());
            outcome = MotionResult.ARRIVED;
        }
        setVelocity(id, (world.x(id) - initialX) / dt, (world.y(id) - initialY) / dt);
        if (traveled > 0f) {
            setGaitPhase(id, (gaitPhase(id) + traveled) % 1f);
            setFormationMemoryTimer(id, FORMATION_MEMORY_SECONDS);
        }
        return outcome;
    }

    /**
     * Advances a mover one tick of continuous carrot-following: picks a carrot
     * {@link #LOOKAHEAD} cells ahead on the cell polyline
     * ({@link PurePursuit#pick(float, float, int[], int, float)} over the flat
     * {@code int[]} path, waypoints at cell centers) and steps the POSITION
     * point toward it at {@code moveSpeed} cells/sec. Reaching the final
     * waypoint pins the position exactly on that cell center and exhausts the
     * path cursor, so {@link #settled}/{@link #atCell} flip atomically with
     * arrival. A mech pivots in place (translation gated on
     * {@code MECH_LOCOMOTION} alignment, which {@code MechLocomotionSystem}
     * drives toward the next path cell) before walking a new bearing. The
     * sole caller is {@code BattleSimulation.advanceMovement}.
     */
    public void advanceAlongPath(World world, long id, float dt) {
        if (continuousRoute(id) != null) {
            throw new IllegalStateException("Continuous routes require live terrain and body clearance");
        }
        if (entityWorld.getInt(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_TRAFFIC_RETURN_GUARD) != 0) {
            // Contact, a reflex, or a replacement route can take movement back
            // without passing through the squad traffic seam. Keep only the
            // terrain obligation; stale spacing and yielding have no authority.
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            long start = profile == null ? 0L : System.nanoTime();
            try {
                advanceAlongPath(world, id, dt, 0f, 0f, 1f);
            } finally {
                if (profile != null) profile.record(TickInnerProfile.Bucket.SQUAD_TRAFFIC_RETURN,
                        System.nanoTime() - start);
            }
            return;
        }
        int[] path = path(id);
        int pathIdx = pathIdx(id);
        int count = Paths.cellCount(path);
        if (pathIdx >= count) return;
        float px = world.x(id);
        float py = world.y(id);
        PurePursuit.Carrot carrot = PurePursuit.pick(px, py, path, pathIdx, LOOKAHEAD);
        if (carrot.nextIdx != pathIdx) setPathIdx(id, carrot.nextIdx);
        if (entityWorld.has(id, components.MECH_LOCOMOTION)) {
            // Pivot-then-walk: hold translation while the chassis is far off
            // the bearing of its next path cell (the same delta
            // MechLocomotionSystem turns toward, so gate and turner agree).
            int nextCellIdx = Math.min(carrot.nextIdx, count - 1);
            int ddx = Paths.cellX(path, nextCellIdx) - world.cellX(id);
            int ddy = Paths.cellY(path, nextCellIdx) - world.cellY(id);
            if (ddx != 0 || ddy != 0) {
                float currentFacing = entityWorld.getFloat(id, components.MECH_LOCOMOTION,
                        BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
                float remainingTurn = Math.abs(MechLocomotion.deltaDegrees(currentFacing,
                        MechLocomotion.desiredFacing(ddx, ddy)));
                if (remainingTurn > MechLocomotion.MOVE_ALIGNMENT_DEGREES) return;
            }
        }
        float dx = carrot.x - px;
        float dy = carrot.y - py;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float step = moveSpeed(id) * dt;
        if (carrot.atEnd && dist <= step + 1e-4f) {
            // Pin on the final cell center so downstream cell reads see
            // exactly the destination cell, and exhaust the cursor (settled).
            world.setPos(id, carrot.x, carrot.y);
            setPathIdx(id, count);
            setGaitPhase(id, 0f);
            setVelocity(id, dx / dt, dy / dt);
            setFormationMemoryTimer(id, FORMATION_MEMORY_SECONDS);
        } else if (dist > 1e-6f) {
            // Clamped so a mover faster than the carrot distance per tick
            // can't overshoot and oscillate around the pursuit line.
            float move = Math.min(step, dist);
            float nx = px + dx / dist * move;
            float ny = py + dy / dist * move;
            world.setPos(id, nx, ny);
            float gait = gaitPhase(id) + move;
            setGaitPhase(id, gait >= 1f ? gait % 1f : gait);
            setVelocity(id, dx / dist * move / dt, dy / dist * move / dt);
            setFormationMemoryTimer(id, FORMATION_MEMORY_SECONDS);
        }
    }
}
