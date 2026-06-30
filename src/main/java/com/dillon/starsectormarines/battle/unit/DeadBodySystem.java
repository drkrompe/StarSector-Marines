package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Turns the dead unit's world entity into a corpse — the death-event handler
 * that builds the corpse home. Subscribed to the battle's death dispatcher; on
 * each {@link DeathEvent} it {@code transmute}s the entity (one row-move) from
 * the live {@code {IDENTITY, POSITION, RENDER_POSITION, HEALTH, VISION}} archetype
 * (plus the optional {@code COMBAT} a combatant carries, and the {@code MOVEMENT},
 * {@code AI_STATE}, {@code SECONDARY_WEAPON} a mobile/armed unit carries) to the
 * corpse archetype {@code {IDENTITY, POSITION, RENDER_POSITION, SPRITE, CORPSE}}:
 * {@code HEALTH}, the universal {@code VISION}, and any {@code COMBAT},
 * {@code MOVEMENT}, {@code AI_STATE}, or {@code SECONDARY_WEAPON} are removed (a
 * corpse neither lives, sees, fights, moves, nor thinks — and "lacks HEALTH" is
 * half the liveness definition);
 * {@code IDENTITY}, the cell, <b>and the render position</b> are carried by the
 * row-move ("the corpse keeps its cell" is literal: nothing moves a unit after
 * the kill zeroes its hp, so the live POSITION + RENDER_POSITION already are the
 * death cell + frozen draw spot), and the death pose is authored into
 * {@code SPRITE.index} (the
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
    /** Cached transmute masks — no per-death array garbage. */
    private final ComponentType[] corpseAdd;
    private final ComponentType[] corpseRemove;

    public DeadBodySystem(EntityWorld world, BattleComponents components) {
        this.world = world;
        this.components = components;
        // RENDER_POSITION is universal on the live archetype now, so it rides the
        // row-move (no add) — the corpse only gains SPRITE + the CORPSE tag.
        this.corpseAdd = new ComponentType[]{components.SPRITE, components.CORPSE};
        // COMBAT, MOVEMENT, AI_STATE, and SECONDARY_WEAPON are all optional (a
        // non-combatant civilian has no COMBAT; a static turret/hub has no
        // MOVEMENT/AI_STATE; only armed units carry a secondary) and removed when
        // present — transmute treats a remove of a component the entity lacks as a
        // no-op, so listing them unconditionally is safe. VISION is universal but
        // live-only (a corpse does not see), so it's removed too.
        this.corpseRemove = new ComponentType[]{
                components.HEALTH, components.COMBAT, components.MOVEMENT,
                components.AI_STATE, components.SECONDARY_WEAPON, components.VISION};
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
        // RENDER_POSITION rides the row-move (it's on the live archetype too, kept
        // off the corpse-remove mask), so the corpse already draws where it fell —
        // no post-release snapshot needed.
        // SPRITE.sheet / flipV stay 0 — sheet resolves from IDENTITY.type until
        // the unified sprite registry mints handles (see BattleComponents.SPRITE).
        world.setInt(id, c.SPRITE, BattleComponents.SPRITE_INDEX, u.deathPoseIdx);
    }
}
