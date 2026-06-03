package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Turns the dead unit's world entity into a corpse — the death-event handler
 * that builds the corpse home. Subscribed to the battle's death dispatcher; on
 * each {@link DeathEvent} it {@code transmute}s the entity (one row-move) from
 * the live {@code {IDENTITY, HEALTH}} archetype to the corpse archetype
 * {@code {IDENTITY, POSITION, RENDER_POSITION, SPRITE, CORPSE}}: {@code HEALTH}
 * is removed (a corpse is not damageable — and "lacks HEALTH" is half the
 * liveness definition), {@code IDENTITY} is <b>carried by the row-move</b>
 * (written once at spawn, never re-written here), the logical cell comes from
 * the event's death-cell snapshot, the draw position is frozen at the spot it
 * fell, and the death pose is authored into {@code SPRITE.index} (the
 * appearance-as-authored-data pattern — the render collector just draws what
 * the sprite says).
 *
 * <p>The corpse <em>is</em> the same entity id the unit lived under — the seam
 * future mechanics (medic revive) hook into without an id correlation table.
 * Idempotent on a duplicate death event: a second transmute to the same
 * archetype is a mask no-op.
 *
 * <p>Handles <em>every</em> death, not just units with corpse art: a body is a
 * body regardless of render art, and keeping the filter out of this sim-tier
 * handler avoids coupling it to the render-tier {@code RenderAppearance}. A
 * unit that died without a pose roll (e.g. a cascade-killed drone) gets
 * {@code SPRITE.index = -1}; the dead-sprite render skips negative indices.
 *
 * <p>No per-tick lifecycle — a corpse is static once transmuted, and the world
 * is per-battle, so corpse rows live until battle teardown.
 */
public final class DeadBodySystem {

    private final EntityWorld world;
    private final BattleComponents components;
    private final RenderPositionService renderPositions;
    /** Cached transmute masks — no per-death array garbage. */
    private final ComponentType[] corpseAdd;
    private final ComponentType[] corpseRemove;

    public DeadBodySystem(EntityWorld world, BattleComponents components,
                          RenderPositionService renderPositions) {
        this.world = world;
        this.components = components;
        this.renderPositions = renderPositions;
        this.corpseAdd = new ComponentType[]{
                components.POSITION, components.RENDER_POSITION, components.SPRITE, components.CORPSE};
        this.corpseRemove = new ComponentType[]{components.HEALTH};
    }

    /** Death-event handler: transmute the dead unit's entity to the corpse archetype. */
    public void onDeath(DeathEvent event) {
        Entity u = event.unit();
        long id = u.entityId;
        BattleComponents c = components;
        world.transmute(id, corpseAdd, corpseRemove);   // IDENTITY rides the row-move
        world.setInt(id, c.POSITION, BattleComponents.POSITION_CELL_X, event.cellX());
        world.setInt(id, c.POSITION, BattleComponents.POSITION_CELL_Y, event.cellY());
        // The render-position entry survives registry release, so this still
        // resolves on the post-release death drain — frozen here, the corpse
        // never reads the shared entry again.
        world.setFloat(id, c.RENDER_POSITION, BattleComponents.RENDER_POSITION_X,
                renderPositions.getX(id));
        world.setFloat(id, c.RENDER_POSITION, BattleComponents.RENDER_POSITION_Y,
                renderPositions.getY(id));
        // SPRITE.sheet / flipV stay 0 — sheet resolves from IDENTITY.type until
        // the unified sprite registry mints handles (see BattleComponents.SPRITE).
        world.setInt(id, c.SPRITE, BattleComponents.SPRITE_INDEX, u.deathPoseIdx);
    }
}
