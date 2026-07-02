package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code MOVEMENT} component — typed by-id access (read +
 * mutate) to a mover's path-step state in the archetype {@link EntityWorld}:
 * move-progress, the flat {@code int[]} path + cursor, and the move-speed stat.
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
 * <p>Part of the {@link World} decomposition: World delegates its MOVEMENT
 * accessors here. The occupancy-bookkeeping path change still goes through
 * {@code BattleControl.setPath} (NavigationService); {@link #setPathRef} is the raw
 * column write it calls under the hood. Serial-only.
 */
public final class MovementService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public MovementService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} carries MOVEMENT (is a mover). Gate field reads on this. */
    public boolean has(long id) { return entityWorld.has(id, components.MOVEMENT); }

    public float moveProgress(long id) { return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS); }
    public void setMoveProgress(long id, float v) { entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS, v); }

    /** Per-unit movement speed in cells/sec (seed-only mover stat). Fail-loud on a non-mover; gate on {@link #has}. */
    public float moveSpeed(long id) { return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_SPEED); }

    public int[] path(long id) { return (int[]) entityWorld.getObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH); }
    public void setPathRef(long id, int[] p) { entityWorld.setObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH, p); }

    public int pathIdx(long id) { return entityWorld.getInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX); }
    public void setPathIdx(long id, int v) { entityWorld.setInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX, v); }

    /**
     * Advances a mover one path step: lerps its render position toward the next
     * path cell as move-progress climbs from 0 to 1, and on arrival advances the
     * logical cell, resets progress, and steps the path cursor. The flat
     * {@code int[]} path (cell {@code i} at {@code (path[i*2], path[i*2+1])},
     * {@link GridPathfinder#EMPTY_PATH} when nothing is scheduled) is fetched once
     * and interrogated through {@link Paths}. Reads/writes the mover's MOVEMENT
     * fields directly and its POSITION / RENDER_POSITION columns via the
     * {@link World} facade — the step is inherently cross-component. Rehomed from
     * {@code Entity.advanceAlongPath} (identity-collapse Phase A); the sole caller
     * is {@code BattleSimulation.advanceMovement}.
     */
    public void advanceAlongPath(World world, long id, float dt) {
        int[] path = path(id);
        int pathIdx = pathIdx(id);
        if (pathIdx >= Paths.cellCount(path)) return;
        int nextX = Paths.cellX(path, pathIdx);
        int nextY = Paths.cellY(path, pathIdx);
        int curX = world.cellX(id);
        int curY = world.cellY(id);
        float dx = nextX - curX;
        float dy = nextY - curY;
        float cellDist = (float) Math.sqrt(dx * dx + dy * dy);
        if (cellDist < 0.0001f) { setPathIdx(id, pathIdx + 1); return; }
        float mp = moveProgress(id) + (moveSpeed(id) * dt) / cellDist;
        if (mp >= 1f) {
            world.setCellPos(id, nextX, nextY);
            world.setRenderPos(id, nextX, nextY);
            setMoveProgress(id, 0f);
            setPathIdx(id, pathIdx + 1);
        } else {
            setMoveProgress(id, mp);
            world.setRenderPos(id, curX + dx * mp, curY + dy * mp);
        }
    }
}
