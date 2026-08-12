package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechLocomotion;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.vehicle.PurePursuit;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

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

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    /** Sim clock for the repath throttle — advanced once per tick by {@link #tickClock}. */
    private float now;

    public MovementService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Advance the repath clock; called exactly once per sim tick by {@code BattleSimulation}. */
    public void tickClock(float dt) { now += dt; }

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
     * a path to this cell always answers {@code true} on arrival.
     */
    public boolean atCell(long id, int cx, int cy) {
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

    /** Per-unit movement speed in cells/sec (seed-only mover stat). Fail-loud on a non-mover; gate on {@link #has}. */
    public float moveSpeed(long id) { return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_SPEED); }

    public int[] path(long id) { return (int[]) entityWorld.getObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH); }
    public void setPathRef(long id, int[] p) { entityWorld.setObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH, p); }

    public int pathIdx(long id) { return entityWorld.getInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX); }
    public void setPathIdx(long id, int v) { entityWorld.setInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX, v); }

    private float gaitPhase(long id) { return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_GAIT_PHASE); }
    private void setGaitPhase(long id, float v) { entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_GAIT_PHASE, v); }

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
     * drives toward the next path cell) before walking a new bearing.
     * {@code RENDER_POSITION} is mirrored from the moved point (old cell-index
     * convention, {@code -0.5}) until the render migration removes it. The
     * sole caller is {@code BattleSimulation.advanceMovement}.
     */
    public void advanceAlongPath(World world, long id, float dt) {
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
        } else if (dist > 1e-6f) {
            float nx = px + dx / dist * step;
            float ny = py + dy / dist * step;
            world.setPos(id, nx, ny);
            float gait = gaitPhase(id) + step;
            setGaitPhase(id, gait >= 1f ? gait % 1f : gait);
        }
        world.setRenderPos(id, world.x(id) - 0.5f, world.y(id) - 0.5f);
    }
}
