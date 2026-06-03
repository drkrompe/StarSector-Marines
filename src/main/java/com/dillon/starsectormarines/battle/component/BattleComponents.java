package com.dillon.starsectormarines.battle.component;

import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.engine.ecs.FieldKind;
import com.dillon.starsectormarines.engine.ecs.Query;

/**
 * The game's component-type registrations for the battle {@link EntityWorld} —
 * the one place battle component ids, field layouts, and the shared queries over
 * them are defined. Constructed once per battle alongside the world it registers
 * into (both transient — battles never save/load mid-fight).
 *
 * <p>Components are grouped by lifecycle-stable capability (Position persists
 * alive→dead; Health will be live-only), per the committed decomposition in
 * {@code roadmap/ecs-migration/archetype-storage.md}. Only the corpse archetype's
 * components are registered so far; live-combat components join as migration
 * step 3 lands, continuing the id space.
 *
 * <p>Column access is positional ({@code table.ints(POSITION, POSITION_CELL_X)});
 * the {@code int} constants below are the named field indices per component.
 */
public final class BattleComponents {

    // ---- field indices ----

    /** {@link #IDENTITY} field 0: the {@link com.dillon.starsectormarines.battle.unit.UnitType} (OBJECT). */
    public static final int IDENTITY_TYPE = 0;
    /** {@link #IDENTITY} field 1: the {@link com.dillon.starsectormarines.battle.unit.Faction} (OBJECT). */
    public static final int IDENTITY_FACTION = 1;

    /** {@link #POSITION} field 0: logical cell x (INT). */
    public static final int POSITION_CELL_X = 0;
    /** {@link #POSITION} field 1: logical cell y (INT). */
    public static final int POSITION_CELL_Y = 1;

    /** {@link #RENDER_POSITION} field 0: smooth sub-cell draw x (FLOAT). */
    public static final int RENDER_POSITION_X = 0;
    /** {@link #RENDER_POSITION} field 1: smooth sub-cell draw y (FLOAT). */
    public static final int RENDER_POSITION_Y = 1;

    /** {@link #SPRITE} field 0: minted sheet handle (INT) — see the interim note on {@link #SPRITE}. */
    public static final int SPRITE_SHEET = 0;
    /** {@link #SPRITE} field 1: frame index within the sheet (INT); {@code < 0} = nothing to draw. */
    public static final int SPRITE_INDEX = 1;
    /** {@link #SPRITE} field 2: vertical flip as 0/1 (INT). */
    public static final int SPRITE_FLIP_V = 2;

    // ---- component types ----

    /** Who/what this entity is — {@code UnitType type, Faction faction}. Persists alive→dead. */
    public final ComponentType IDENTITY;
    /** Logical cell — {@code int cellX, cellY}. Every spatially-present entity, corpse included. */
    public final ComponentType POSITION;
    /** Smooth draw position — {@code float x, y}. Sub-cell, distinct cadence from the int cell. */
    public final ComponentType RENDER_POSITION;
    /**
     * Authored appearance — {@code int sheet, index, flipV}. The authoritative
     * "draw this"; one {@code Sprite} = one drawn quad, written by presentation
     * systems (a corpse's frozen death pose lives in {@code index}), read by the
     * render collector. <b>Interim:</b> {@code sheet} stays {@code 0} until the
     * unified sprite registry mints sheet handles
     * ({@code roadmap/battle-render/stories/unified-sprite-registry.md}) — the
     * render resolves the sheet from {@link #IDENTITY_TYPE} until then.
     */
    public final ComponentType SPRITE;
    /** Dead-archetype marker — pure presence tag, no columns. */
    public final ComponentType CORPSE;

    // ---- shared queries (per-world lifecycle, cached matched-table lists) ----

    /**
     * The corpse archetype {@code {IDENTITY, POSITION, RENDER_POSITION, SPRITE,
     * CORPSE}} — every body on the field, exactly as {@code DeadBodySystem}
     * spawns them. Walked by the dead-sprite render and the mission resolver's
     * casualty tally. Split into narrower queries only when a corpse variant
     * that lacks one of these components actually exists.
     */
    public final Query corpses;

    public BattleComponents(EntityWorld world) {
        IDENTITY        = world.register(0, "Identity", FieldKind.OBJECT, FieldKind.OBJECT);
        POSITION        = world.register(1, "Position", FieldKind.INT, FieldKind.INT);
        RENDER_POSITION = world.register(2, "RenderPosition", FieldKind.FLOAT, FieldKind.FLOAT);
        SPRITE          = world.register(3, "Sprite", FieldKind.INT, FieldKind.INT, FieldKind.INT);
        CORPSE          = world.register(4, "Corpse");
        corpses = world.query(
                new ComponentType[]{IDENTITY, POSITION, RENDER_POSITION, SPRITE, CORPSE}, null);
    }
}
