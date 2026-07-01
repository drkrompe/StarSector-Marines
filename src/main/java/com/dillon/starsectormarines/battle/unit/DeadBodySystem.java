package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Turns the dead unit's world entity into a corpse — the death-event handler
 * that builds the corpse home. Subscribed to the battle's death dispatcher; on
 * each {@link DeathEvent} it {@code transmute}s the entity (one row-move) from
 * the live {@code {IDENTITY, POSITION, RENDER_POSITION, HEALTH, VISION, ROLE}}
 * archetype (plus the optional {@code COMBAT} a combatant carries, the
 * {@code MOVEMENT}, {@code AI_STATE}, {@code SECONDARY_WEAPON}, {@code SQUAD} a
 * mobile/armed/squadded unit carries, and the {@code SPRITE} every sheet-drawn
 * unit — {@link UnitType#drawnAsSheet()} — already carries live, authored
 * per-tick by {@code battle.appearance.FacingSystem}) to the
 * corpse archetype {@code {IDENTITY, POSITION, RENDER_POSITION, SPRITE, CORPSE}}:
 * {@code HEALTH}, the universal {@code VISION} + {@code ROLE}, and any {@code COMBAT},
 * {@code MOVEMENT}, {@code AI_STATE}, {@code SECONDARY_WEAPON}, or {@code SQUAD} are
 * removed (a corpse neither lives, sees, acts, fights, moves, thinks, nor belongs to
 * a squad — and "lacks HEALTH" is half the liveness definition);
 * {@code IDENTITY}, the cell, <b>and the render position</b> are carried by the
 * row-move ("the corpse keeps its cell" is literal: nothing moves a unit after
 * the kill zeroes its hp, so the live POSITION + RENDER_POSITION already are the
 * death cell + frozen draw spot). The transmute adds {@code SPRITE} only for a
 * non-sheet death (turret / drone-hub / drone, which never carried one live);
 * either way, the death write overwrites {@code SPRITE.index} with the death
 * pose and zeroes {@code SPRITE.sheet}/{@code SPRITE.flipV} (a sheet unit can
 * die mid-secondary-aim with those non-zero) — the
 * appearance-as-authored-data pattern — the render collector just draws what
 * the sprite says.
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
        // row-move (no add) — same for SPRITE, which every sheet-drawn unit
        // (UnitType.drawnAsSheet) already carries live (authored per-tick by
        // FacingSystem). Listing SPRITE in the add mask anyway is safe either
        // way: transmute ORs the add mask in, so it's a no-op for a sheet unit
        // that already has it, and still adds it fresh for a non-sheet death
        // (turret / drone-hub / drone) that never carried one live.
        this.corpseAdd = new ComponentType[]{components.SPRITE, components.CORPSE};
        // COMBAT, MOVEMENT, AI_STATE, SECONDARY_WEAPON, and SQUAD are all optional (a
        // non-combatant civilian has no COMBAT; a static turret/hub has no
        // MOVEMENT/AI_STATE; only armed units carry a secondary; only squad members
        // carry SQUAD) and removed when present — transmute treats a remove of a
        // component the entity lacks as a no-op, so listing them unconditionally is
        // safe. VISION and ROLE are universal but live-only (a corpse neither sees nor
        // acts), so they're removed too. SQUAD and ROLE are read pre-transmute by the
        // death cascade in resolve() (squad membership; the drop-carrier role check),
        // which runs before releaseFromRegistry and this buffered transmute.
        this.corpseRemove = new ComponentType[]{
                components.HEALTH, components.COMBAT, components.MOVEMENT,
                components.AI_STATE, components.SECONDARY_WEAPON, components.VISION,
                components.SQUAD, components.ROLE, components.HOME, components.TASK};
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
        world.setInt(id, c.SPRITE, BattleComponents.SPRITE_INDEX, event.deathPoseIdx());
        // A sheet unit's live-authored selector/flip ride the row-move (a unit
        // can die mid-secondary-aim with SHEET=1/FLIP_V=1) — corpses draw the
        // base sheet unflipped, so re-assert the corpse invariant explicitly
        // rather than relying on a fresh-row default that only holds for the
        // non-sheet (turret/hub/drone) deaths that gain SPRITE here for the
        // first time.
        world.setInt(id, c.SPRITE, BattleComponents.SPRITE_SHEET, 0);
        world.setInt(id, c.SPRITE, BattleComponents.SPRITE_FLIP_V, 0);
    }
}
