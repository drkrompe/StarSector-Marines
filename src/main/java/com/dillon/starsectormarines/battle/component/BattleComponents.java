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
 * corpse archetype plus the mandatory live capabilities ({@link #POSITION},
 * {@link #HEALTH}, {@link #COMBAT}), the optional live ones ({@link #MOVEMENT},
 * {@link #AI_STATE}, {@link #SECONDARY_WEAPON}), and the optional post-death
 * {@link #CRASHING}. Every unit spawns into the world as
 * {@code {IDENTITY, POSITION, HEALTH, COMBAT}}, plus {@link #MOVEMENT} +
 * {@link #AI_STATE} iff it is mobile (a static turret/hub carries neither) and
 * {@link #SECONDARY_WEAPON} iff it carries one — so presence <em>is</em> the
 * capability, no nullable field. Death is the transmute to the corpse archetype
 * (identity + cell ride the row-move; health, combat, and any movement, ai-state,
 * or secondary are removed); a crashing air unit then carries {@link #CRASHING}
 * over the corpse while it falls. The ecs-migration is complete: the standalone
 * {@code MechLoadout} and {@code Crashing} stores folded into archetype
 * membership ({@link #MECH_LOADOUT} / {@link #CRASHING}), and the registry
 * dissolution is done — the dense roster lives on {@code UnitRosterService},
 * by-id access on {@code World}.
 *
 * <p><b>Air craft</b> (the air-into-world epic,
 * {@code roadmap/air/air-entities-into-world.md}) are world entities too, with a
 * disjoint archetype: {@code {AIR_IDENTITY, KINEMATICS, SHUTTLE_MISSION}} (+
 * optional {@link #THRUSTER_FX} / {@link #AIR_TURRETS}) and <em>no</em>
 * grid/combat components — so every grid system skips them for free
 * (membership-narrowing). They are world-resident but never in the dense ground
 * roster; walk them via {@link #airCraft}.
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
    /** {@link #MOVEMENT} field 1: the flat {@code int[]} path reference (OBJECT); {@link com.dillon.starsectormarines.battle.nav.GridPathfinder#EMPTY_PATH} = nothing scheduled. */
    public static final int MOVEMENT_PATH = 1;
    /** {@link #MOVEMENT} field 2: index of the next cell along the path to step into (INT). */
    public static final int MOVEMENT_PATH_IDX = 2;

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

    /** {@link #CRASHING} field 0: the {@link com.dillon.starsectormarines.battle.air.components.CrashingComponent} payload (OBJECT) — the falling body, fall timer, and spin. */
    public static final int CRASHING_STATE = 0;

    /** {@link #MECH_LOADOUT} field 0: the {@link com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent} payload (OBJECT) — the chassis weapon/morale state bag. */
    public static final int MECH_LOADOUT_STATE = 0;

    /** {@link #KINEMATICS} field 0: the {@link com.dillon.starsectormarines.battle.air.AirBody} payload (OBJECT) — continuous float position/velocity/facing for a flier (shuttle/drone). */
    public static final int KINEMATICS_BODY = 0;

    /** {@link #AIR_IDENTITY} field 0: the {@link com.dillon.starsectormarines.battle.air.ShuttleType} (OBJECT). */
    public static final int AIR_IDENTITY_TYPE = 0;
    /** {@link #AIR_IDENTITY} field 1: the {@link com.dillon.starsectormarines.battle.unit.Faction} (OBJECT). */
    public static final int AIR_IDENTITY_FACTION = 1;

    /** {@link #SHUTTLE_MISSION} field 0: the {@link com.dillon.starsectormarines.battle.air.ShuttleMission} payload (OBJECT) — the per-sortie delivery state bag (incl. {@code hp}; air liveness is {@code mission.state}, not a HEALTH component). */
    public static final int SHUTTLE_MISSION_STATE = 0;

    /** {@link #THRUSTER_FX} field 0: the {@link com.dillon.starsectormarines.battle.air.engine.ThrusterFx} payload (OBJECT) — per-slot smoothed engine-plume demand. */
    public static final int THRUSTER_FX_STATE = 0;

    /** {@link #AIR_TURRETS} field 0: the {@link com.dillon.starsectormarines.battle.air.AirTurrets} payload (OBJECT) — the mounted-turret array (presence == "armed"). */
    public static final int AIR_TURRETS_STATE = 0;

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
     * definition ({@code UnitRosterService.isAliveById}). Seeded at spawn by
     * {@code UnitRosterService.allocate}; damage writes go through {@code World}'s
     * by-id accessors.
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
     * the next path cell), {@code int[] path} (the flat path reference), and
     * {@code int pathIdx} (the cursor along it). <em>Optional</em>: added at spawn
     * only for mobile units, so "has MOVEMENT" defines a mover. A static
     * emplacement (a turret or drone hub;
     * {@link com.dillon.starsectormarines.battle.unit.UnitType#isStatic}) never
     * paths and carries no MOVEMENT — the few all-unit readers (the occupancy-map
     * and destination-index rebuilds) gate on {@code World.hasMovement};
     * per-unit movement code only ever runs for movers. Removed in the corpse
     * transmute (a corpse does not move). See
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
     * row appends as zero), and the FLEE wander dwell. <em>Optional</em>: added at
     * spawn only for thinking units, so "has AI_STATE" defines a thinker. A static
     * emplacement (a turret or drone hub;
     * {@link com.dillon.starsectormarines.battle.unit.UnitType#isStatic}) has no
     * decision cadence and carries no AI_STATE — the per-tick dispatch
     * ({@code UnitUpdateSystem}) and the per-hit fall-back roll
     * ({@code HitResponseService}) gate on {@code World.hasAiState};
     * per-unit decision code only ever runs for thinkers. Removed in the corpse
     * transmute (a corpse does not think). See
     * {@code roadmap/ecs-migration/archetype-storage.md}.
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
    /**
     * Optional crash state — one OBJECT field holding the
     * {@link com.dillon.starsectormarines.battle.air.components.CrashingComponent}
     * (the falling body + fall timer + tumble spin). Attached on an air unit's
     * death (a shot-down or cascade-killed drone), so "is crashing" IS the
     * archetype membership; the crash system spins + counts each one down and
     * detaches it on impact. <b>Survives the corpse transmute</b> (kept off the
     * {@code corpseRemove} mask) — the dead drone is a corpse that also carries
     * {@code CRASHING} while it falls, mirroring how the old {@code ComponentStore}
     * entry outlived the unit's registry release. See
     * {@code roadmap/ecs-migration/archetype-storage.md}.
     */
    public final ComponentType CRASHING;
    /**
     * Optional mech-chassis loadout — one OBJECT field holding the
     * {@link com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent}
     * (the three weapon tracks + per-mech morale state). Added at spawn only for
     * mech-class units, so "has {@code MECH_LOADOUT}" IS "is a mech" — no nullable
     * field. The mech-fire pass walks the live-mech query; the per-mech morale +
     * the wreck handler read it by id. <b>Survives the corpse transmute</b> (kept
     * off the {@code corpseRemove} mask) so {@code MechWreckSystem} can read the
     * dead mech's loadout to drop a wreck, then detaches it — mirroring how the old
     * {@code ComponentStore} entry outlived the unit's registry release. See
     * {@code roadmap/ecs-migration/archetype-storage.md}.
     */
    public final ComponentType MECH_LOADOUT;
    /**
     * Continuous kinematics for a flier — one OBJECT field holding the
     * {@link com.dillon.starsectormarines.battle.air.AirBody} (float
     * position/velocity/facing). The air analogue of {@link #POSITION} (which is
     * an int grid cell): a shuttle has {@code KINEMATICS} and no grid
     * {@code POSITION}; a drone has <em>both</em> (its cell synced from the body
     * each tick). An OBJECT column (not decomposed floats) because {@code AirBody}
     * is a small, shared POJO (drones, the non-entity {@code FlybyOverlay}, and
     * {@code CrashingComponent} all hold one) and air is a tiny population — the
     * CRASHING/MECH_LOADOUT precedent. The air-into-world epic
     * ({@code roadmap/air/air-entities-into-world.md}).
     */
    public final ComponentType KINEMATICS;
    /**
     * Air-craft identity — {@code ShuttleType type, Faction faction}. Distinct
     * from grid {@link #IDENTITY} (whose {@code type} is a concrete
     * {@link com.dillon.starsectormarines.battle.unit.UnitType} consumed by render
     * + many systems); air craft are not units, so they get their own identity
     * pair rather than widening {@code IDENTITY_TYPE} to {@code Object}.
     */
    public final ComponentType AIR_IDENTITY;
    /**
     * The per-sortie shuttle mission state — one OBJECT field holding the
     * {@link com.dillon.starsectormarines.battle.air.ShuttleMission} bag (delivery
     * state machine, LZ/entry/exit, marines remaining, squad, hp, cycle schedule).
     * {@code mission.hp} lives HERE, not in a {@link #HEALTH} component — a
     * transport carries no grid/combat components, and air liveness is
     * {@code mission.state}. The CRASHING/MECH_LOADOUT OBJECT-payload precedent.
     */
    public final ComponentType SHUTTLE_MISSION;
    /**
     * Optional engine-plume FX — one OBJECT field holding the
     * {@link com.dillon.starsectormarines.battle.air.engine.ThrusterFx} (per-slot
     * smoothed thruster demand). Re-keyed off the standalone {@code ComponentStore}
     * in the air-into-world epic; presence is attached lazily by
     * {@code ThrusterFxSystem}. {@code has}-gate every read.
     */
    public final ComponentType THRUSTER_FX;
    /**
     * Optional mounted turrets — one OBJECT field holding the
     * {@link com.dillon.starsectormarines.battle.air.AirTurrets} (the
     * {@code MountedTurret[]}). Presence == "armed"; an unarmed transport lacks it.
     * Re-keyed off the standalone {@code ComponentStore} in the air-into-world
     * epic. {@code has}-gate every read.
     */
    public final ComponentType AIR_TURRETS;

    // ---- shared queries (per-world lifecycle, cached matched-table lists) ----

    /**
     * The corpse archetype {@code {IDENTITY, POSITION, RENDER_POSITION, SPRITE,
     * CORPSE}} — every body on the field, exactly as {@code DeadBodySystem}
     * spawns them. Walked by the dead-sprite render and the mission resolver's
     * casualty tally. Split into narrower queries only when a corpse variant
     * that lacks one of these components actually exists.
     */
    public final Query corpses;

    /**
     * Every entity currently crashing ({@code {CRASHING}}) — the falling drones the
     * crash system advances each tick and the drone renderer draws as fading hulls.
     * Required-only on {@code CRASHING}; the carrier is a corpse-archetype entity
     * (it died) that additionally carries the crash component while it falls.
     */
    public final Query crashing;

    /**
     * Every <em>live</em> mech ({@code {MECH_LOADOUT}} minus {@code {CORPSE}}) — the
     * mech-fire continuation pass walks these. {@code CORPSE} is excluded so a
     * dead mech that still carries its loadout (until the wreck handler detaches
     * it) doesn't fire; the wreck handler reads that dead loadout by id instead.
     */
    public final Query mechLoadouts;

    /**
     * Every air craft ({@code {AIR_IDENTITY, KINEMATICS, SHUTTLE_MISSION}}) — the
     * adopted shuttles (and planned fighters), world-resident but never in the
     * dense ground roster. The render + objective consumers walk this in place of
     * the retired {@code List<Shuttle>}. Matches no grid query (air carries no
     * POSITION/CORPSE/CRASHING/MECH_LOADOUT), so grid systems skip it for free.
     */
    public final Query airCraft;

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
        MOVEMENT        = world.register(8, "Movement",
                FieldKind.FLOAT, FieldKind.OBJECT, FieldKind.INT);
        AI_STATE        = world.register(9, "AiState",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.INT, FieldKind.INT, FieldKind.FLOAT);
        CRASHING        = world.register(10, "Crashing", FieldKind.OBJECT);
        MECH_LOADOUT    = world.register(11, "MechLoadout", FieldKind.OBJECT);
        KINEMATICS      = world.register(12, "Kinematics", FieldKind.OBJECT);
        AIR_IDENTITY    = world.register(13, "AirIdentity", FieldKind.OBJECT, FieldKind.OBJECT);
        SHUTTLE_MISSION = world.register(14, "ShuttleMission", FieldKind.OBJECT);
        THRUSTER_FX     = world.register(15, "ThrusterFx", FieldKind.OBJECT);
        AIR_TURRETS     = world.register(16, "AirTurrets", FieldKind.OBJECT);
        corpses = world.query(
                new ComponentType[]{IDENTITY, POSITION, RENDER_POSITION, SPRITE, CORPSE}, null);
        crashing = world.query(new ComponentType[]{CRASHING}, null);
        mechLoadouts = world.query(new ComponentType[]{MECH_LOADOUT}, new ComponentType[]{CORPSE});
        airCraft = world.query(new ComponentType[]{AIR_IDENTITY, KINEMATICS, SHUTTLE_MISSION}, null);
    }
}
