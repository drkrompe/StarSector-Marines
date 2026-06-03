package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Spawns a corpse entity in the battle {@link EntityWorld} for every unit that
 * dies — the death-event handler that builds the corpse home. Subscribed to the
 * battle's death dispatcher; on each {@link DeathEvent} it creates an entity of
 * the corpse archetype {@code {IDENTITY, POSITION, RENDER_POSITION, SPRITE,
 * CORPSE}}: identity from the dead unit, the logical cell from the event's
 * death-cell snapshot, the draw position frozen at the spot it fell, and the
 * death pose authored into {@code SPRITE.index} (the appearance-as-authored-data
 * pattern — the render collector just draws what the sprite says).
 *
 * <p>The spawn is a plain {@link EntityWorld#createEntity} — creates are
 * walk-safe, so this needs no command-buffer deferral even though the death
 * drain runs between query walks anyway.
 *
 * <p>Spawns for <em>every</em> death, not just units with corpse art: a body is
 * a body regardless of render art (a future medic cares about all of them), and
 * keeping the filter out of this sim-tier handler avoids coupling it to the
 * render-tier {@code RenderAppearance}. A unit that died without a pose roll
 * (e.g. a cascade-killed drone) gets {@code SPRITE.index = -1}; the dead-sprite
 * render skips negative indices.
 *
 * <p>No per-tick lifecycle — a corpse is static once spawned, and the world is
 * per-battle, so corpse rows live until battle teardown. The corpse is its own
 * entity in the world's id space; the dead unit's registry id is not carried —
 * add an id field when a consumer (medic revive) actually needs the link.
 */
public final class DeadBodySystem {

    private final EntityWorld world;
    private final BattleComponents components;
    private final RenderPositionService renderPositions;

    public DeadBodySystem(EntityWorld world, BattleComponents components,
                          RenderPositionService renderPositions) {
        this.world = world;
        this.components = components;
        this.renderPositions = renderPositions;
    }

    /** Death-event handler: spawn the corpse entity for the unit that just died. */
    public void onDeath(DeathEvent event) {
        Entity u = event.unit();
        BattleComponents c = components;
        long corpse = world.createEntity(c.IDENTITY, c.POSITION, c.RENDER_POSITION, c.SPRITE, c.CORPSE);
        world.setObject(corpse, c.IDENTITY, BattleComponents.IDENTITY_TYPE, u.type);
        world.setObject(corpse, c.IDENTITY, BattleComponents.IDENTITY_FACTION, u.faction);
        world.setInt(corpse, c.POSITION, BattleComponents.POSITION_CELL_X, event.cellX());
        world.setInt(corpse, c.POSITION, BattleComponents.POSITION_CELL_Y, event.cellY());
        // The render-position entry survives registry release, so this still
        // resolves on the post-release death drain — frozen here, the corpse
        // never reads the shared entry again.
        world.setFloat(corpse, c.RENDER_POSITION, BattleComponents.RENDER_POSITION_X,
                renderPositions.getX(u.entityId));
        world.setFloat(corpse, c.RENDER_POSITION, BattleComponents.RENDER_POSITION_Y,
                renderPositions.getY(u.entityId));
        // SPRITE.sheet / flipV stay 0 — sheet resolves from IDENTITY.type until
        // the unified sprite registry mints handles (see BattleComponents.SPRITE).
        world.setInt(corpse, c.SPRITE, BattleComponents.SPRITE_INDEX, u.deathPoseIdx);
    }
}
