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
 * <p>Components are grouped by lifecycle-stable capability (Identity persists
 * alive→dead; Health is live-only), per the committed decomposition in
 * {@code roadmap/ecs-migration/archetype-storage.md}. Registered so far: the
 * corpse archetype plus the live capabilities ({@link #POSITION},
 * {@link #HEALTH}, {@link #COMBAT}, {@link #MOVEMENT}) and the first
 * <em>optional</em> live capability ({@link #SECONDARY_WEAPON}). Every unit
 * spawns into the world as {@code {IDENTITY, POSITION, HEALTH, COMBAT, MOVEMENT,
 * AI_STATE}}, plus {@link #SECONDARY_WEAPON} iff it carries one (so presence
 * <em>is</em> the capability — no nullable field); death is the transmute to
 * the corpse archetype (identity + cell ride the row-move; health, combat,
 * movement, ai-state, and any secondary are removed). With {@link #AI_STATE}
 * the registry holds no per-unit dense column at all — the remaining migration
 * work folds the standalone {@code Crashing}/{@code MechLoadout} component
 * stores into archetype membership, then dissolves {@code UnitRegistry}.
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

    /** {@link #HEALTH} field 0: current hp (FLOAT). */
    public static final int HEALTH_HP = 0;
    /** {@link #HEALTH} field 1: max hp (FLOAT). */
    public static final int HEALTH_MAX_HP = 1;

    /** {@link #COMBAT} field 0: base attack damage (FLOAT). */
    public static final int COMBAT_ATTACK_DAMAGE = 0;
    /** {@link #COMBAT} field 1: base attack range in cells (FLOAT). */
    public static final int COMBAT_ATTACK_RANGE = 1;
    /** {@link #COMBAT} field 2: base accuracy [0,1] (FLOAT). */
    public static final int COMBAT_ACCURACY = 2;
    /** {@link #COMBAT} field 3: primary-weapon cooldown, sim-seconds until next fire (FLOAT). */
    public static final int COMBAT_COOLDOWN_TIMER = 3;
    /** {@link #COMBAT} field 4: current-target entity id, 0L = none (LONG). */
    public static final int COMBAT_TARGET_ID = 4;
    /** {@link #COMBAT} field 5: burst rounds queued after the initial primary shot (INT). */
    public static final int COMBAT_BURST_REMAINING = 5;
    /** {@link #COMBAT} field 6: sim-seconds until the next queued burst round fires (FLOAT). */
    public static final int COMBAT_BURST_TIMER = 6;
    /** {@link #COMBAT} field 7: entity id captured when the burst was queued, 0L = idle (LONG). */
    public static final int COMBAT_BURST_TARGET_ID = 7;

    /** {@link #MOVEMENT} field 0: movement lerp factor [0,1] toward the next path cell (FLOAT). */
    public static final int MOVEMENT_MOVE_PROGRESS = 0;

    /** {@link #AI_STATE} field 0: sim-seconds until the unit may next micro-reposition between shots (FLOAT). */
    public static final int AI_STATE_REPOSITION_COOLDOWN = 0;
    /** {@link #AI_STATE} field 1: sim-seconds remaining in break-contact fall-back state, &gt;0 = falling back (FLOAT). */
    public static final int AI_STATE_FALLBACK_TIMER = 1;
    /** {@link #AI_STATE} field 2: cached fall-back destination cell x, {@code -1} = none (INT). */
    public static final int AI_STATE_FALLBACK_CELL_X = 2;
    /** {@link #AI_STATE} field 3: cached fall-back destination cell y, paired with {@link #AI_STATE_FALLBACK_CELL_X} (INT). */
    public static final int AI_STATE_FALLBACK_CELL_Y = 3;
    /** {@link #AI_STATE} field 4: FLEE-role idle pause between wander legs, sim-seconds (FLOAT). */
    public static final int AI_STATE_WANDER_DWELL_TIMER = 4;

    /** {@link #SECONDARY_WEAPON} field 0: the {@link com.dillon.starsectormarines.battle.infantry.MarineSecondary} flyweight (OBJECT). */
    public static final int SECONDARY_WEAPON_SPEC = 0;
    /** {@link #SECONDARY_WEAPON} field 1: rounds remaining on the secondary (INT). */
    public static final int SECONDARY_WEAPON_AMMO = 1;
    /** {@link #SECONDARY_WEAPON} field 2: secondary cooldown, sim-seconds (FLOAT). */
    public static final int SECONDARY_WEAPON_COOLDOWN_TIMER = 2;
    /** {@link #SECONDARY_WEAPON} field 3: sim-seconds remaining in the aim-then-fire window (FLOAT). */
    public static final int SECONDARY_WEAPON_ACTION_TIMER = 3;
    /** {@link #SECONDARY_WEAPON} field 4: entity id locked at aim start, 0L = none (LONG). */
    public static final int SECONDARY_WEAPON_AIM_TARGET_ID = 4;
    /** {@link #SECONDARY_WEAPON} field 5: one-shot-per-aim-cycle latch as 0/1 (INT). */
    public static final int SECONDARY_WEAPON_FIRED = 5;

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
    /**
     * Live damageable state — {@code float hp, maxHp}. Live-only by design: a
     * corpse does NOT carry it (death removes it in the corpse transmute), so
     * "has {@code HEALTH} with {@code hp > 0}" <em>is</em> the liveness
     * definition ({@code UnitRegistry.isAliveById}). Seeded at spawn by
     * {@code UnitRegistry.allocate}; damage writes go through the registry's
     * transitional by-id adapters until step 4 dissolves them.
     */
    public final ComponentType HEALTH;
    /**
     * Live-combat state — {@code float attackDamage, attackRange, accuracy,
     * cooldownTimer; long targetId; int burstRemaining; float burstTimer; long
     * burstTargetId}. The primary-weapon capability, universal to every live unit
     * (seeded at spawn like {@link #HEALTH}); the attack stats are seed-only, the
     * rest are mid-combat scalars that start at zero. Removed in the corpse
     * transmute (a corpse does not fight), so a live unit is
     * {@code {IDENTITY, POSITION, HEALTH, COMBAT}}. The optional <em>secondary</em>
     * weapon is a separate presence component (a later migration slice), not a
     * field here — see {@code roadmap/ecs-migration/archetype-storage.md}.
     */
    public final ComponentType COMBAT;
    /**
     * Movement state — {@code float moveProgress} (the [0,1] lerp factor toward
     * the next path cell). Universal on every live unit today (seeded zero at
     * spawn like the mid-combat {@link #COMBAT} scalars), so this slice is
     * behavior-preserving — even a turret carries a {@code moveProgress} of 0 it
     * never advances, exactly as the old universal registry column did. The
     * designed end-state narrows membership to path-executing (kinematic)
     * entities only and folds in the path reference ({@code int[] path; int
     * pathIdx}, still {@code Entity} fields); both are deferred to the slice that
     * brings the path in, where "has a path capability" is what truly defines a
     * mover. Removed in the corpse transmute (a corpse does not move). See
     * {@code roadmap/ecs-migration/archetype-storage.md}.
     */
    public final ComponentType MOVEMENT;
    /**
     * AI decision-cadence state — {@code float repositionCooldown; float
     * fallbackTimer; int fallbackCellX, fallbackCellY; float wanderDwellTimer}.
     * The per-unit countdowns + cached fall-back cell the decision tier drives:
     * reposition gating, break-contact fall-back (timer + its destination cell,
     * {@code -1/-1} = none — the one field pair whose default is <em>not</em>
     * zero, so {@code allocate} explicitly seeds the sentinel since a fresh world
     * row appends as zero), and the FLEE wander dwell. Universal on every live
     * unit today (behavior-preserving — these were universal registry columns),
     * so this slice empties the last of {@code UnitRegistry}'s dense columns; the
     * designed end-state narrows membership to thinking entities (a turret has no
     * decision cadence). Removed in the corpse transmute (a corpse does not
     * think). See {@code roadmap/ecs-migration/archetype-storage.md}.
     */
    public final ComponentType AI_STATE;
    /**
     * Optional secondary weapon — {@code MarineSecondary spec; int ammo; float
     * cooldownTimer, actionTimer; long aimTargetId; int fired}. The first
     * <em>optional</em> live capability modeled as archetype presence: added at
     * spawn only for units that carry a secondary (a rocket launcher today;
     * other secondary types later), absent on everyone else — so "has a
     * secondary" is the archetype membership, not a nullable field. The
     * {@code spec} flyweight is weapon-type-agnostic; richer AI may later query
     * it to decide what the unit can do. Removed in the corpse transmute (no-op
     * for units that never had it). See
     * {@code roadmap/ecs-migration/archetype-storage.md}.
     */
    public final ComponentType SECONDARY_WEAPON;

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
        HEALTH          = world.register(5, "Health", FieldKind.FLOAT, FieldKind.FLOAT);
        COMBAT          = world.register(6, "Combat",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.LONG, FieldKind.INT, FieldKind.FLOAT, FieldKind.LONG);
        SECONDARY_WEAPON = world.register(7, "SecondaryWeapon",
                FieldKind.OBJECT, FieldKind.INT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.LONG, FieldKind.INT);
        MOVEMENT        = world.register(8, "Movement", FieldKind.FLOAT);
        AI_STATE        = world.register(9, "AiState",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.INT, FieldKind.INT, FieldKind.FLOAT);
        corpses = world.query(
                new ComponentType[]{IDENTITY, POSITION, RENDER_POSITION, SPRITE, CORPSE}, null);
    }
}
