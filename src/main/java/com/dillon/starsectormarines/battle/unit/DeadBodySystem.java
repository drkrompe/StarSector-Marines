package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Turns the dead unit's world entity into a corpse — the death-event handler
 * that builds the corpse home. Subscribed to the battle's death dispatcher; on
 * each {@link DeathEvent} it {@code transmute}s the entity (one row-move) from
 * the live {@code {IDENTITY, POSITION, HEALTH, COMBAT, MOVEMENT}} archetype
 * (plus an optional {@code SECONDARY_WEAPON}) to the corpse archetype
 * {@code {IDENTITY, POSITION, RENDER_POSITION, SPRITE, CORPSE}}: {@code HEALTH},
 * {@code COMBAT}, {@code MOVEMENT}, and any {@code SECONDARY_WEAPON} are removed
 * (a corpse neither lives, fights, nor moves — and "lacks HEALTH" is half the
 * liveness definition), {@code IDENTITY} <b>and the cell</b> are
 * carried by the row-move ("the corpse keeps its cell" is literal: nothing
 * moves a unit after the kill zeroes its hp, so the live POSITION already is
 * the death cell the event snapshotted), the draw position is frozen at the
 * spot it fell, and the death pose is authored into {@code SPRITE.index} (the
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
                components.RENDER_POSITION, components.SPRITE, components.CORPSE};
        // MOVEMENT is removed too (a corpse does not move). SECONDARY_WEAPON is
        // removed when present; transmute treats a remove of a component the
        // entity lacks as a no-op, so listing it unconditionally is safe for
        // units that never carried a secondary.
        this.corpseRemove = new ComponentType[]{
                components.HEALTH, components.COMBAT, components.MOVEMENT,
                components.SECONDARY_WEAPON};
    }

    /** Death-event handler: transmute the dead unit's entity to the corpse archetype. */
    public void onDeath(DeathEvent event) {
        Entity u = event.unit();
        long id = u.entityId;
        BattleComponents c = components;
        // IDENTITY and POSITION ride the row-move — the live cell IS the death
        // cell (the event's snapshot is the same value; demolition handlers
        // still read it off the event, this transmute just doesn't re-write it).
        world.transmute(id, corpseAdd, corpseRemove);
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
