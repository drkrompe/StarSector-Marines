package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
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
}
