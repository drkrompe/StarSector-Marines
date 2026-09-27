package com.dillon.starsectormarines.battle.component;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
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
 * {@code ecs-nouns.md}. Registered capabilities include the
 * corpse archetype plus the mandatory live capabilities ({@link #POSITION},
 * {@link #HEALTH}, {@link #COMBAT}), the optional live ones ({@link #ARMOR},
 * {@link #MOVEMENT}, {@link #AI_STATE}, {@link #SECONDARY_WEAPON}), the universal {@link #VISION}
 * sight stats and {@link #ROLE} dispatch tag, and the optional post-death
 * {@link #CRASHING}. Every unit spawns
 * into the world as {@code {IDENTITY, POSITION, HEALTH, COMBAT, VISION, ROLE}}, plus
 * {@link #MOVEMENT} + {@link #AI_STATE} iff it is mobile (a static turret/hub
 * carries neither) and {@link #SECONDARY_WEAPON} iff it carries one — so presence
 * <em>is</em> the capability, no nullable field. Death is the transmute to the
 * corpse archetype (identity, cell, and any {@link #TELEMETRY} record ride the
 * row-move; health, combat, vision, and
 * any movement, ai-state, or secondary are removed); a crashing air unit then
 * carries {@link #CRASHING} over the corpse while it falls. The ecs-migration is complete: the standalone
 * {@code MechLoadout} and {@code Crashing} stores folded into archetype
 * membership ({@link #MECH_LOADOUT} / {@link #CRASHING}), and the registry
 * dissolution is done — the dense roster lives on {@code UnitRosterService},
 * by-id access on {@code World}.
 *
 * <p><b>Air craft</b> (see {@code air-nouns.md}) are world entities too, with a
 * near-disjoint archetype: {@code {AIR_IDENTITY, KINEMATICS, SHUTTLE_MISSION,
 * APPEARANCE}} (+ optional {@link #THRUSTER_FX} / {@link #AIR_TURRETS}) plus the
 * convoy chassis's {@code {IDENTITY, HEALTH, ARMOR}} — and, exactly as on a
 * chassis, <em>no</em> {@link #POSITION}, {@link #COMBAT}, {@link #MOVEMENT} or
 * {@link #ROLE}, so occupancy, separation, the fire system, the mover and the
 * planner all skip them for free (membership-narrowing). They are world-resident
 * but never in the dense ground roster; walk them via {@link #airCraft}.
 *
 * <p>Column access is positional ({@code table.floats(POSITION, POSITION_X)});
 * the {@code int} constants below are the named field indices per component.
 */
public final class BattleComponents {

    // ---- field indices ----

    /** {@link #IDENTITY} field 0: the {@link com.dillon.starsectormarines.battle.unit.UnitType} (OBJECT). */
    public static final int IDENTITY_TYPE = 0;
    /** {@link #IDENTITY} field 1: the {@link com.dillon.starsectormarines.battle.unit.Faction} (OBJECT). */
    public static final int IDENTITY_FACTION = 1;
    /** {@link #IDENTITY} field 2: the human-readable name (String OBJECT) — greppable id like {@code "m0"} / {@code "drone-dh1-5"}, seeded from the ctor String id, read by id via {@code IdentityService.name}. Debug/log/test-facing; the stable machine identity is the {@code long} entityId. */
    public static final int IDENTITY_NAME = 2;
    /** Persisted campaign soldier id for player marines; null for generated units. */
    public static final int IDENTITY_CAMPAIGN_SOLDIER_ID = 3;
    /** Optional persistent {@link com.dillon.starsectormarines.battle.mech.MechVariant}; null for non-mechs. */
    public static final int IDENTITY_MECH_VARIANT = 4;
    /**
     * {@link #IDENTITY} field 5: the campaign squad this marine deployed with, frozen at
     * spawn from its {@link com.dillon.starsectormarines.battle.squad.CampaignSquadTag}
     * (String OBJECT); null for generated units. Deliberately frozen rather than looked
     * up at mission end, so a roster edit after the battle cannot rewrite who a marine
     * fought alongside ({@code progression-nouns.md}).
     */
    public static final int IDENTITY_CAMPAIGN_SQUAD_ID = 5;
    /**
     * {@link #IDENTITY} field 6: the
     * {@link com.dillon.starsectormarines.battle.air.Airframe} a parked
     * aircraft is a hull of (OBJECT); null for everything else.
     *
     * <p>Here for the same reason {@link #IDENTITY_MECH_VARIANT} is: body
     * geometry on these types comes from the per-instance thing rather than
     * from the {@link com.dillon.starsectormarines.battle.unit.UnitType}, and
     * the shared radius accessor has to reach it without a scan. The berth
     * knows which airframe stands on it, but finding a berth from a unit id is
     * a walk over every hardstand on the field, and that read is per candidate
     * per shot.
     */
    public static final int IDENTITY_AIRFRAME = 6;

    /** {@link #POSITION} field 0: continuous position x (FLOAT) — cell (cx,cy) spans [cx,cx+1), center at cx+0.5; floor for the grid cell. */
    public static final int POSITION_X = 0;
    /** {@link #POSITION} field 1: continuous position y (FLOAT) — cell (cx,cy) spans [cy,cy+1), center at cy+0.5; floor for the grid cell. */
    public static final int POSITION_Y = 1;

    /**
     * {@link #SPRITE} field 0: sheet selector (INT) — {@code 0} = the type's
     * base sheet, {@code 1} = the secondary-aim sheet (the renderer maps the
     * selector to a {@code SpriteAPI}; the component itself stays tier-neutral,
     * never a sprite handle). A corpse is always {@code 0} (the base sheet).
     */
    public static final int SPRITE_SHEET = 0;
    /**
     * {@link #SPRITE} field 1: frame index within the sheet (INT); {@code < 0}
     * = nothing to draw. Live: the per-tick facing/pose frame authored by
     * {@code battle.appearance.FacingSystem}. Corpse: the frozen death-pose
     * frame, written once at the death transmute.
     */
    public static final int SPRITE_INDEX = 1;
    /**
     * {@link #SPRITE} field 2: vertical flip as 0/1 (INT). Live: the
     * weapon-up SOUTH vertical mirror, authored by {@code
     * battle.appearance.FacingSystem}. Corpse: always {@code 0}.
     */
    public static final int SPRITE_FLIP_V = 2;

    /** {@link #LAYERED_ANIMATION} field 0: torso facing in continuous sprite degrees (FLOAT). */
    public static final int LAYERED_FACING_DEGREES = 0;
    /** Field 1: repeating locomotion phase [0,1) (FLOAT). */
    public static final int LAYERED_LOCOMOTION_PHASE = 1;
    /** Field 2: normalized phase within the current weapon pose [0,1] (FLOAT). */
    public static final int LAYERED_WEAPON_PHASE = 2;
    /** Field 3: helmet rotation relative to the torso, degrees (FLOAT). */
    public static final int LAYERED_HEAD_LOOK_DEGREES = 3;
    /** Field 4: one of {@code LayeredAppearance.POSE_*} (INT). */
    public static final int LAYERED_WEAPON_POSE = 4;
    /** Field 5: bitset of {@code LayeredAppearance.FLAG_*} (INT). */
    public static final int LAYERED_FLAGS = 5;
    /** Field 6: {@code LayeredArmorFamily} ordinal for the shoulder/body layer (INT). */
    public static final int LAYERED_BODY_FAMILY = 6;
    /** Field 7: independently swappable helmet-family ordinal (INT). */
    public static final int LAYERED_HEAD_FAMILY = 7;

    /** {@link #MECH_LAYERED_ANIMATION} field 0: upper chassis facing (FLOAT). */
    public static final int MECH_LAYERED_FACING_DEGREES = 0;
    /** Field 1: repeating locomotion phase [0,1) (FLOAT). */
    public static final int MECH_LAYERED_LOCOMOTION_PHASE = 1;
    /** Fields 2..4: independent weapon-cycle phases (FLOAT). */
    public static final int MECH_LAYERED_CHAINGUN_PHASE = 2;
    public static final int MECH_LAYERED_SRM_PHASE = 3;
    public static final int MECH_LAYERED_LRM_PHASE = 4;
    /** Field 5: {@code LayeredMechAppearance.FLAG_*} bitset (INT). */
    public static final int MECH_LAYERED_FLAGS = 5;
    /** Fields 6..9: independently swappable chassis hardpoint selectors (INT). */
    public static final int MECH_LAYERED_CHASSIS = 6;
    public static final int MECH_LAYERED_ARMS = 7;
    public static final int MECH_LAYERED_LEFT_SHOULDER = 8;
    public static final int MECH_LAYERED_RIGHT_SHOULDER = 9;
    /** Field 10: locomotion/hip facing used by the independently planted feet (FLOAT). */
    public static final int MECH_LAYERED_HIP_FACING_DEGREES = 10;

    /** {@link #MECH_LOCOMOTION} field 0: persistent chassis heading in sprite degrees (FLOAT). */
    public static final int MECH_LOCOMOTION_FACING_DEGREES = 0;
    /** Field 1: signed angular velocity in degrees/sec, authored by steering (FLOAT). */
    public static final int MECH_LOCOMOTION_ANGULAR_VELOCITY = 1;
    /** Field 2: maximum chassis turn rate in degrees/sec (FLOAT). */
    public static final int MECH_LOCOMOTION_TURN_RATE = 2;
    /** {@link #MECH_GAIT_STATE} field 0: persistent planted-foot state (OBJECT). */
    public static final int MECH_GAIT_STATE_STATE = 0;

    /** {@link #HEALTH} field 0: current hp (FLOAT). */
    public static final int HEALTH_HP = 0;
    /** {@link #HEALTH} field 1: max hp (FLOAT). */
    public static final int HEALTH_MAX_HP = 1;
    /** Field 2: multiplier applied to post-cover incoming damage; 1 = unmodified. */
    public static final int HEALTH_DAMAGE_TAKEN_MULT = 2;
    /** Field 3: multiplier applied to incoming ballistic hit rolls; 1 = unmodified. */
    public static final int HEALTH_INCOMING_ACCURACY_MULT = 3;

    /** {@link #ARMOR} field 0: current armor protection remaining (FLOAT). */
    public static final int ARMOR_CURRENT = 0;
    /** {@link #ARMOR} field 1: maximum armor protection (FLOAT). */
    public static final int ARMOR_MAX = 1;
    /** {@link #ARMOR} field 2: resistance compared with an attack's penetration (FLOAT). */
    public static final int ARMOR_RATING = 2;

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
    /** {@link #COMBAT} field 8: per-unit primary-weapon cooldown reset value, sim-seconds — the value {@link #COMBAT_COOLDOWN_TIMER} is reset to on a fire (FLOAT). Seed-only stat like attack damage/range/accuracy. */
    public static final int COMBAT_ATTACK_COOLDOWN = 8;
    /** {@link #COMBAT} field 9: the {@link WeaponDef} primary-weapon definition (OBJECT); {@code null} = no per-weapon profile (militia/aliens/turrets fall back to the baked attack stats). Seed-only stat like the attack stats. */
    public static final int COMBAT_PRIMARY_WEAPON = 9;
    /** {@link #COMBAT} field 10: consume-once fire-intent target entity id (LONG), {@code 0L} = no intent = hold fire. Written by a behavior that decided to shoot (e.g. {@code EngagePosture}) instead of firing inline; cleared every tick by {@code battle.combat.FiringSystem} whether or not the shot actually fired, so a stale intent can never re-fire. Distinct from {@link #COMBAT_TARGET_ID} ("who I'm engaging," which can stay live while fire is held). */
    public static final int COMBAT_FIRE_TARGET_ID = 10;
    /** {@link #COMBAT} field 11: the {@link com.dillon.starsectormarines.battle.combat.FireStance} ordinal for the queued shot (INT). Meaningless without a live {@link #COMBAT_FIRE_TARGET_ID}; overwritten by the next {@code setFireIntent}. */
    public static final int COMBAT_FIRE_STANCE = 11;
    /** {@link #COMBAT} field 12: run {@code battle.infantry.RepositionToCover} after this fire, as 0/1 (INT) — {@code EngagePosture}'s post-fire hook, gated so it only fires same-tick as a successful shot. */
    public static final int COMBAT_FIRE_REPOSITION = 12;
    /** {@link #COMBAT} field 13: the primary weapon's {@link com.dillon.starsectormarines.battle.infantry.EquipmentGrade} (OBJECT). */
    public static final int COMBAT_EQUIPMENT_GRADE = 13;
    /** {@link #COMBAT} field 14: the combatant's immutable {@link com.dillon.starsectormarines.battle.infantry.SoldierProfile} (OBJECT). */
    public static final int COMBAT_SOLDIER_PROFILE = 14;
    /** {@link #COMBAT} field 15: threat entity id currently being registered by the reflex passive (LONG), {@code 0L} = no threat. */
    public static final int COMBAT_REFLEX_TARGET_ID = 15;
    /** {@link #COMBAT} field 16: sim-seconds remaining before {@link #COMBAT_REFLEX_TARGET_ID} is registered and may receive primary fire (FLOAT). */
    public static final int COMBAT_REFLEX_TIMER = 16;
    /** {@link #COMBAT} field 17: {@link com.dillon.starsectormarines.battle.combat.FireGate} ordinal for the most recently consumed primary-fire intent (INT). */
    public static final int COMBAT_LAST_FIRE_GATE = 17;
    /** {@link #COMBAT} field 18: sim tick on which {@link #COMBAT_LAST_FIRE_GATE} was authored, {@code 0} before any intent was consumed (INT). */
    public static final int COMBAT_LAST_FIRE_GATE_TICK = 18;
    /**
     * {@link #COMBAT} field 19: decaying count of rounds that have recently landed
     * near this combatant from a shooter it has line of sight to (FLOAT).
     *
     * <p>Stored undecayed as of {@link #COMBAT_INCOMING_PRESSURE_TICK} and decayed
     * on read, so the signal costs one write per incoming round rather than a sweep
     * over every combatant every tick. Read it through
     * {@code CombatService.incomingPressure(id, simTick)}; the raw field is
     * meaningless without its tick.
     */
    public static final int COMBAT_INCOMING_PRESSURE = 19;
    /** {@link #COMBAT} field 20: sim tick {@link #COMBAT_INCOMING_PRESSURE} was last written at (INT). */
    public static final int COMBAT_INCOMING_PRESSURE_TICK = 20;
    /** {@link #COMBAT} field 21: cell x the most recent incoming round was fired from (INT). Meaningless at zero pressure. */
    public static final int COMBAT_INCOMING_FROM_X = 21;
    /** {@link #COMBAT} field 22: cell y the most recent incoming round was fired from (INT). Meaningless at zero pressure. */
    public static final int COMBAT_INCOMING_FROM_Y = 22;
    /**
     * {@link #COMBAT} field 23: sim tick a hit last landed on this combatant,
     * stored plus one so the zero every fresh column starts at means "never"
     * rather than "on tick zero" (INT). Read it through
     * {@code CombatService.ticksSinceDamaged}, never raw.
     *
     * <p>Distinct from {@link #COMBAT_INCOMING_PRESSURE}, which counts rounds landing
     * <em>near</em> it from a shooter it can see. This is damage that actually
     * arrived, from anywhere, seen or not.
     */
    public static final int COMBAT_LAST_DAMAGED_TICK = 23;
    /** Nullable point-fire intent; mutually exclusive with FIRE_TARGET_ID. */
    public static final int COMBAT_POINT_FIRE_AIM = 24;
    /** Nullable frozen point aim for primary burst continuation. */
    public static final int COMBAT_BURST_POINT_AIM = 25;

    /** {@link #MOVEMENT} field 0: repeating [0,1) walk-stride phase, advanced by distance traveled — one full cycle per cell (FLOAT). Presentation-only: read by {@code battle.appearance.FacingSystem} for the locomotion pose; the sim never gates on it. */
    public static final int MOVEMENT_GAIT_PHASE = 0;
    /** {@link #MOVEMENT} field 1: the flat {@code int[]} path reference (OBJECT); {@link com.dillon.starsectormarines.battle.nav.GridPathfinder#EMPTY_PATH} = nothing scheduled. */
    public static final int MOVEMENT_PATH = 1;
    /** {@link #MOVEMENT} field 2: index of the next un-crossed path waypoint — the carrot picker's resume cursor (INT); {@code >= cellCount(path)} = path exhausted = not moving. */
    public static final int MOVEMENT_PATH_IDX = 2;
    /** {@link #MOVEMENT} field 3: per-unit movement speed in cells/sec — the mover stat {@code advanceAlongPath} steps by (FLOAT). Seed-only like the COMBAT stats. */
    public static final int MOVEMENT_MOVE_SPEED = 3;
    /** {@link #MOVEMENT} field 4: sim-clock stamp of the last {@code NavigationService.setPath} assignment (FLOAT) — drives {@code MovementService.mayRepath}'s mid-motion throttle. */
    public static final int MOVEMENT_LAST_REPATH_TIME = 4;
    /** {@link #MOVEMENT} field 5: velocity x actually applied this tick, cells/sec (FLOAT). Zeroed for every mover at tick start, written by {@code advanceAlongPath} when it translates — so nonzero means "moved this tick". Presentation reads it for the moving flag + travel bearing. */
    public static final int MOVEMENT_VEL_X = 5;
    /** {@link #MOVEMENT} field 6: velocity y actually applied this tick, cells/sec (FLOAT). See {@link #MOVEMENT_VEL_X}. */
    public static final int MOVEMENT_VEL_Y = 6;
    /** {@link #MOVEMENT} field 7: brief sim-seconds of formation-heading memory after actual translation, used only to preserve shared-destination infantry footprints after arrival (FLOAT). */
    public static final int MOVEMENT_FORMATION_MEMORY_TIMER = 7;
    /**
     * {@link #MOVEMENT} field 8: the unit's unmodified movement speed in
     * cells/sec (FLOAT), seeded at spawn from the same {@code EntitySpec.moveSpeed}
     * as {@link #MOVEMENT_MOVE_SPEED} and never written again.
     *
     * <p>Exists so a temporary speed effect has something to return to.
     * {@link #MOVEMENT_MOVE_SPEED} is the live value the mover steps by; an
     * effect recomputes it as {@code base * multiplier} rather than scaling the
     * live value in place, because repeated scale-and-restore drifts and a
     * second overlapping effect would compound the first one's leftovers.
     */
    public static final int MOVEMENT_BASE_MOVE_SPEED = 8;
    /** Proven continuous Mech waypoints, or its completed arrival witness (OBJECT, nullable). */
    public static final int MOVEMENT_CONTINUOUS_ROUTE = 9;
    /** Former traffic displacement still requires terrain-checked cell-path return (INT, 0/1). */
    public static final int MOVEMENT_TRAFFIC_RETURN_GUARD = 10;
    /** Paused objective travel must install a fresh route before resuming (INT, 0/1). */
    public static final int MOVEMENT_OBJECTIVE_ROUTE_REFRESH = 11;
    /** Member-owned known-grenade escape witness; path replacement revokes it (OBJECT, nullable). */
    public static final int MOVEMENT_GRENADE_ESCAPE = 12;

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
    /** {@link #AI_STATE} field 5: name of the {@link com.dillon.starsectormarines.battle.decision.Reflex} that pre-empted this unit's plan step on the last tick it was dispatched, {@code null} = none (OBJECT). Diagnostic only — written by {@code ReflexChain.run} and read by the per-member dumps so a unit standing off its step says <em>why</em>; nothing in the simulation reads it back. */
    public static final int AI_STATE_LAST_REFLEX = 5;
    /** {@link #AI_STATE} field 6: sim-seconds left in a {@code LaneSidestep} step-aside, {@code 0} = not stepping aside (FLOAT). The reflex's own ownership marker: {@link #AI_STATE_REPOSITION_COOLDOWN} alone cannot say <em>whose</em> short move is in flight, and a reflex that adopted the post-fire cover reposition as well would stop the marine authoring fire intent for the whole of it. */
    public static final int AI_STATE_SIDESTEP_TIMER = 6;

    /** {@link #VISION} field 0: how far this unit can see in cells — drives its fog-of-war shadowcast radius (FLOAT). */
    public static final int VISION_RANGE = 0;
    /** {@link #VISION} field 1: close-wall "air" line-of-sight radius in cells, {@code 0} = standard grid LoS (FLOAT). */
    public static final int VISION_AIR_LOS_RADIUS = 1;

    /** {@link #SQUAD} field 0: the squad id this unit belongs to (INT) — a key into the roster's squad registry. Presence IS membership: a non-member carries no SQUAD, so {@code NO_SQUAD} is never a stored value. */
    public static final int SQUAD_ID = 0;
    /** {@link #SQUAD} field 1: stable zero-based fire-team index inside the squad (INT). */
    public static final int SQUAD_FIRE_TEAM_INDEX = 1;

    /** {@link #ROLE} field 0: the {@link com.dillon.starsectormarines.battle.unit.UnitRole} ordinal (INT) driving per-tick behavior dispatch. Stored as the ordinal rather than an OBJECT ref so it's a plain primitive column ({@code RoleService} reconstructs the enum). */
    public static final int ROLE_ORDINAL = 0;

    /** {@link #HOME} field 0: the garrison idle-post cell x (INT). */
    /** {@code RIDING} field 0 — entity id of the vehicle carrying this unit. */
    public static final int RIDING_CARRIER_ID = 0;
    public static final int HOME_CELL_X = 0;
    /** {@link #HOME} field 1: the garrison idle-post cell y (INT). */
    public static final int HOME_CELL_Y = 1;

    /** {@link #TASK} field 0: the {@link com.dillon.starsectormarines.battle.command.objective.Objective} this unit is acting on, or {@code null} (OBJECT). */
    public static final int TASK_ASSIGNED_OBJECTIVE = 0;
    /** {@link #TASK} field 1: the {@link com.dillon.starsectormarines.battle.infantry.EquipmentDrop} kit a KIT_RETRIEVER is heading to, or {@code null} (OBJECT). */
    public static final int TASK_EQUIPMENT_DROP = 1;

    /** {@link #SECONDARY_WEAPON} field 0: the authoritative {@link com.dillon.starsectormarines.marine.SpecialEquipmentDef} (OBJECT). */
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

    /** {@link #INTEGRAL_SYSTEM} field 0: the authoritative {@link com.dillon.starsectormarines.marine.IntegralSystemDef} the worn armour pattern declares (OBJECT). */
    public static final int INTEGRAL_SYSTEM_SPEC = 0;
    /** {@link #INTEGRAL_SYSTEM} field 1: sim-seconds until the system may be activated again (FLOAT); {@code <= 0} = ready. */
    public static final int INTEGRAL_SYSTEM_COOLDOWN_TIMER = 1;
    /** {@link #INTEGRAL_SYSTEM} field 2: sim-seconds remaining in the current activation (FLOAT); {@code > 0} = running. */
    public static final int INTEGRAL_SYSTEM_ACTIVE_TIMER = 2;
    /** {@link #INTEGRAL_SYSTEM} field 3: uses remaining for an ammunition-gated system (INT); unused by cooldown-gated ones. */
    public static final int INTEGRAL_SYSTEM_AMMO = 3;

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

    /** {@link #APPEARANCE} field 0: cruise-altitude scalar {@code altitudeT} in [0,1] — 0 = on the LZ, 1 = at cruising height (FLOAT). */
    public static final int APPEARANCE_ALTITUDE_T = 0;
    /** {@link #APPEARANCE} field 1: accumulated phase (radians) for the in-flight scale wobble (FLOAT). */
    public static final int APPEARANCE_FLIGHT_PHASE = 1;

    /** {@link #GROUND_IDENTITY} field 0: the {@link com.dillon.starsectormarines.battle.vehicle.VehicleType} (OBJECT). */
    public static final int GROUND_IDENTITY_TYPE = 0;
    /** {@link #GROUND_IDENTITY} field 1: the {@link com.dillon.starsectormarines.battle.unit.Faction} (OBJECT). */
    public static final int GROUND_IDENTITY_FACTION = 1;

    /** {@link #GROUND_KINEMATICS} field 0: the {@link com.dillon.starsectormarines.battle.vehicle.GroundBody} payload (OBJECT) — continuous float pose (x/y/facing/speed) for a road-driving vehicle. */
    public static final int GROUND_KINEMATICS_BODY = 0;

    /** {@link #GROUND_TURRET} field 0: the {@link com.dillon.starsectormarines.battle.vehicle.GroundTurret} payload (OBJECT) — live aim/fire state (facing/cooldown/ammo/burst). Presence == "armed". */
    public static final int GROUND_TURRET_STATE = 0;

    /** {@link #VEHICLE_MISSION} field 0: the convoy mission-state payload (OBJECT) — the {@link com.dillon.starsectormarines.battle.vehicle.VehicleMission} bag. Liveness is {@code state == GONE}. */
    public static final int VEHICLE_MISSION_STATE = 0;

    /** {@link #VEHICLE_CONTROL} field 0: the {@link com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent} payload (OBJECT) — the vehicle's id-keyed ground-motion control state (corridor / rolling plan / docking / reverse recovery). */
    public static final int VEHICLE_CONTROL_STATE = 0;

    /** {@link #HUB_STATE} field 0: sim-seconds until the hub's next drone-launch attempt (FLOAT). */
    public static final int HUB_STATE_SPAWN_COOLDOWN = 0;
    /** {@link #HUB_STATE} field 1: lifetime count of drones the hub has launched (INT). */
    public static final int HUB_STATE_DRONES_LAUNCHED = 1;
    /** {@link #HUB_STATE} field 2: the squad id the hub's drones join, {@code Squad.NO_SQUAD} (-1) = none minted yet (INT). */
    public static final int HUB_STATE_DRONE_SQUAD_ID = 2;

    /** {@link #TURRET_STATE} field 0: current barrel facing, degrees (FLOAT). 0° = +Y (north). */
    public static final int TURRET_STATE_FACING_DEGREES = 0;
    /** {@link #TURRET_STATE} field 1: sim-seconds since the last fired round (FLOAT) — drives the renderer's per-round barrel recoil slide. */
    public static final int TURRET_STATE_RECOIL_TIMER = 1;
    /** {@link #TURRET_STATE} field 2: stable structure definition id baked at construction (OBJECT String). */
    public static final int TURRET_STATE_STRUCTURE_ID = 2;
    /** {@link #TURRET_STATE} field 3: rounds left in the current burst, excluding the trigger-pull round; {@code 0} = idle/single-shot kind (INT). */
    public static final int TURRET_STATE_BURST_REMAINING = 3;
    /** {@link #TURRET_STATE} field 4: sim-seconds until the next burst round fires; counts down while field 3 &gt; 0 (FLOAT). */
    public static final int TURRET_STATE_BURST_TIMER = 4;
    /** {@link #TURRET_STATE} field 5: entity id of the target locked when the burst started, {@code 0L} when idle (LONG). */
    public static final int TURRET_STATE_BURST_TARGET_ID = 5;

    /** {@link #DRONE_STATE} field 0: current patrol waypoint cell x, {@link Float#NaN} = no waypoint yet (FLOAT). */
    public static final int DRONE_STATE_PATROL_GOAL_X = 0;
    /** {@link #DRONE_STATE} field 1: current patrol waypoint cell y, paired with {@link #DRONE_STATE_PATROL_GOAL_X} (FLOAT). */
    public static final int DRONE_STATE_PATROL_GOAL_Y = 1;
    /** {@link #DRONE_STATE} field 2: last-known engagement/agro-scan cell x the drone is pursuing, {@link Float#NaN} = no pursuit target on record (FLOAT). */
    public static final int DRONE_STATE_PURSUIT_GOAL_X = 2;
    /** {@link #DRONE_STATE} field 3: last-known pursuit cell y, paired with {@link #DRONE_STATE_PURSUIT_GOAL_X} (FLOAT). */
    public static final int DRONE_STATE_PURSUIT_GOAL_Y = 3;
    /** {@link #DRONE_STATE} field 4: sim-seconds remaining on the pursuit latch; {@code 0} = latch expired, back to patrol (FLOAT). */
    public static final int DRONE_STATE_PURSUIT_TIMER = 4;
    /** {@link #DRONE_STATE} field 5: entity id of the hub that launched this drone, {@code 0L} = none (LONG). */
    public static final int DRONE_STATE_HOME_HUB_ID = 5;

    /** {@link #TELEMETRY} field 0: rounds this entity has pulled the trigger on — one per primary round, burst-aware (INT). */
    public static final int TELEMETRY_ROUNDS_FIRED = 0;
    /** {@link #TELEMETRY} field 1: rounds this entity fired that reached a body — one per arriving {@code PendingImpact}, not per damage event, so one AoE shell striking six units is still one round (INT). */
    public static final int TELEMETRY_ROUNDS_HIT = 1;
    /** {@link #TELEMETRY} field 2: post-mitigation HP this entity has taken off hostiles — cover, armor and the hardened multiplier are already applied, and overkill past zero is not counted (FLOAT). */
    public static final int TELEMETRY_DAMAGE_DEALT = 2;
    /** {@link #TELEMETRY} field 3: post-mitigation HP this entity has taken off its OWN side. Kept out of {@link #TELEMETRY_DAMAGE_DEALT} deliberately — netting it in would hide it (FLOAT). */
    public static final int TELEMETRY_FRIENDLY_FIRE_DAMAGE = 3;
    /** {@link #TELEMETRY} field 4: post-mitigation HP this entity has absorbed, from any source including friendly fire (FLOAT). */
    public static final int TELEMETRY_DAMAGE_TAKEN = 4;
    /** {@link #TELEMETRY} field 5: hostiles this entity landed the killing blow on. One AoE detonation that kills three counts three (INT). */
    public static final int TELEMETRY_KILLS = 5;
    /** {@link #TELEMETRY} field 6: secondary-weapon rounds expended — rockets and grenades, for economy tuning (INT). */
    public static final int TELEMETRY_SECONDARY_USED = 6;
    /**
     * {@link #TELEMETRY} field 7: post-cover damage this entity's live
     * mitigation refused, never reaching either armor or structure (FLOAT).
     *
     * <p>Its own quantity on purpose. Folding a screen's work into
     * {@link #TELEMETRY_DAMAGE_TAKEN} would make a working screen
     * indistinguishable from a run of lucky misses, which is exactly the
     * invisible damage multiplier {@code combat-durability-nouns.md} refuses.
     */
    public static final int TELEMETRY_DAMAGE_MITIGATED = 7;
    /**
     * {@link #TELEMETRY} field 8: hostile warheads this entity's placed
     * point-defence emplacements engaged in flight. Its own quantity, never
     * folded into damage: an intercepted round is removed before it detonates,
     * so there is no damage figure to prevent and no attribution to reverse (INT).
     */
    public static final int TELEMETRY_ORDNANCE_INTERCEPTED = 8;
    /**
     * {@link #TELEMETRY} field 9: hostile warheads this entity's placed
     * emplacements <em>fired at</em>, whether or not the burst connected.
     * Kept separate from {@link #TELEMETRY_ORDNANCE_INTERCEPTED} so an
     * after-action can say "engaged five, stopped three" — the saturation and
     * accuracy bounds are only reviewable if the attempts are counted (INT).
     */
    public static final int TELEMETRY_ORDNANCE_ENGAGED = 9;

    /**
     * {@link #MITIGATION} field 0: post-cover damage the live screen can still
     * absorb before it breaks (FLOAT). {@code 0} = nothing raised, or a pool
     * spent to nothing — which are the same state, because a spent screen is
     * gone.
     */
    public static final int MITIGATION_SOAK_REMAINING = 0;
    /**
     * {@link #MITIGATION} field 1: the pool this screen was raised with (FLOAT).
     * Kept beside the remainder purely so "how much is left" is answerable as a
     * fraction; the damage path only ever reads
     * {@link #MITIGATION_SOAK_REMAINING}.
     */
    public static final int MITIGATION_SOAK_CAPACITY = 1;
    /** {@link #MITIGATION} field 2: total arc width the screen covers, centred on {@link #MITIGATION_FACING_DEGREES}, in {@code [0, 360)} (FLOAT). */
    public static final int MITIGATION_ARC_DEGREES = 2;
    /** {@link #MITIGATION} field 3: sim-owned direction the screen points, in the same degrees as {@code AirBody.facingToward} (FLOAT). */
    public static final int MITIGATION_FACING_DEGREES = 3;
    /** {@link #MITIGATION} field 4: sim-seconds left before the screen drops (FLOAT); {@code <= 0} = nothing raised. */
    public static final int MITIGATION_REMAINING = 4;
    /**
     * {@link #MITIGATION} field 5: sim-seconds left of the "this screen just
     * broke" mark (FLOAT). Set by the absorb that emptied the pool and drained
     * by {@code MitigationSystem}; it exists because breaking is an edge only
     * the damage path can see, and the tail-of-tick presentation sweep has to
     * be able to see it too. No simulation decision reads it.
     */
    public static final int MITIGATION_BREAK_FLASH = 5;

    /**
     * {@link #SYSTEM_FX} field 0: how much of the running activation's window is
     * left, as a fraction in {@code [0, 1]} (FLOAT). {@code 1} on the tick the
     * system is spent, falling to {@code 0} as the window closes. {@code 0} =
     * nothing is running, which is the whole "is there a treatment" question.
     */
    public static final int SYSTEM_FX_INTENSITY = 0;
    /** {@link #SYSTEM_FX} field 1: the direction the drawn screen faces (FLOAT), copied from the arc the damage path is resolving against. */
    public static final int SYSTEM_FX_ARC_FACING_DEGREES = 1;
    /** {@link #SYSTEM_FX} field 2: total width of the drawn screen's arc (FLOAT); {@code 0} = the running system raises no screen. */
    public static final int SYSTEM_FX_ARC_DEGREES = 2;
    /**
     * {@link #SYSTEM_FX} field 3: how much of the screen's soak pool is left, as
     * a fraction in {@code [0, 1]} (FLOAT). This is what the drawn screen's
     * strength reads off — a screen with a sliver of pool left looks like one.
     */
    public static final int SYSTEM_FX_SOAK_FRACTION = 3;
    /**
     * {@link #SYSTEM_FX} field 4: the "it just shattered" mark, in {@code [0, 1]}
     * (FLOAT), {@code 1} at the instant the pool emptied and falling to
     * {@code 0}. Distinct from a window simply closing, because a screen beaten
     * down by massed fire is the outcome worth seeing.
     */
    public static final int SYSTEM_FX_BREAK_FLASH = 4;
    /**
     * {@link #SYSTEM_FX} field 5: a wrapping {@code [0, 1)} phase for the
     * treatment's shimmer (FLOAT). Authored from elapsed simulation time rather
     * than sampled from a wall clock, so the same battle state draws the same
     * frame and deterministic visual evidence stays reproducible.
     */
    public static final int SYSTEM_FX_SHIMMER_PHASE = 5;

    // ---- component types ----

    /** Who/what this entity is — {@code UnitType type, Faction faction, String name}. Persists alive→dead. */
    public final ComponentType IDENTITY;
    /**
     * Continuous position — {@code float x, y} in cell space (cell {@code (cx,cy)}
     * spans {@code [cx,cx+1) x [cy,cy+1)}, center at {@code (cx+0.5, cy+0.5)}; see
     * {@code continuous-positions-nouns.md}).
     * {@link com.dillon.starsectormarines.battle.sim.World#cellX}/
     * {@link com.dillon.starsectormarines.battle.sim.World#cellY} derive the grid
     * cell via {@code floor}. Every spatially-present entity, corpse included.
     */
    public final ComponentType POSITION;
    /**
     * Authored appearance — {@code int sheet, index, flipV}. The authoritative
     * "draw this"; one {@code Sprite} = one drawn quad, written by presentation
     * systems, read by the render collector. No longer corpse-only:
     * every live {@link com.dillon.starsectormarines.battle.unit.UnitType#drawnAsSheet()}
     * unit carries one too, authored per-tick by {@code
     * battle.appearance.FacingSystem} (facing/pose frame + the weapon-up
     * vertical mirror + the base/secondary-aim sheet selector); a corpse's
     * frozen death pose lives in {@code index} (written once at the death
     * transmute), with {@code sheet}/{@code flipV} re-asserted to {@code 0}.
     * <b>Interim:</b> {@code sheet} is a small selector ({@code 0}/{@code 1}),
     * not a minted handle, until the unified sprite registry mints sheet
     * handles ({@code unified-sprite-registry.md}).
     * The render resolves selector {@code 0} against {@link #IDENTITY_TYPE}
     * (the type's base sheet), but selector {@code 1} against the
     * {@link #SECONDARY_WEAPON_SPEC} column — aim sheets are keyed by the
     * <em>weapon kind</em>, not the unit type.
     */
    public final ComponentType SPRITE;
    /**
     * Optional authored transform animation for modular top-down infantry:
     * {@code float facing, locomotionPhase, weaponPhase, headLook; int pose,
     * flags, bodyFamily, headFamily}. Presence means the live unit can use layered rendering. It remains
     * presentation-only and is removed on death; {@link #SPRITE} stays alongside
     * it as the legacy/failure fallback and as the later corpse carrier.
     */
    public final ComponentType LAYERED_ANIMATION;
    /** Live-only authored transform state for independently animated mech hardpoints. */
    public final ComponentType MECH_LAYERED_ANIMATION;
    /**
     * Live-only mech steering state: persistent facing, current angular velocity,
     * and maximum turn rate. Unlike {@link #MECH_LAYERED_ANIMATION}, this is
     * simulation-authoritative: path movement reads it to pivot before stepping.
     */
    public final ComponentType MECH_LOCOMOTION;
    /** Live-only persistent planted-foot and waist presentation state. */
    public final ComponentType MECH_GAIT_STATE;
    /** Dead-archetype marker — pure presence tag, no columns. */
    public final ComponentType CORPSE;
    /**
     * Live damageable state — {@code float hp, maxHp}. Live-only by design: a
     * corpse does NOT carry it (death removes it in the corpse transmute), so
     * "has {@code HEALTH} with {@code hp > 0}" <em>is</em> the liveness
     * definition ({@code UnitRosterService.isAliveById}). Seeded at spawn by
     * {@code UnitRosterService.adopt}; damage writes go through {@code World}'s
     * by-id accessors.
     */
    public final ComponentType HEALTH;
    /**
     * Optional live protection state — {@code float current, max, rating}.
     * Presence means the actor has an authored armor capability, including while
     * its current capacity is depleted; armorless actors omit the component. Removed
     * in the corpse transmute alongside {@link #HEALTH}.
     */
    public final ComponentType ARMOR;
    /**
     * Live-combat state — {@code float attackDamage, attackRange, accuracy,
     * cooldownTimer; long targetId; int burstRemaining; float burstTimer; long
     * burstTargetId; float attackCooldown; WeaponDef primaryWeaponDef}. The
     * primary-weapon capability. The {@code primaryWeapon} flyweight is
     * <em>nullable</em> — militia / aliens / turrets carry no per-weapon profile and
     * fall back to the baked attack stats; a marine's deboard loadout seeds it.
     * <em>Optional</em>: added at
     * spawn only for combatants ({@code UnitType.combatant}), so "has COMBAT"
     * defines a combatant — a non-combatant (civilian / engineer / scientist) never
     * fires or is targeted and carries none. Seeded at spawn like {@link #HEALTH}
     * when present; the attack stats (incl. the {@code primaryWeapon} object) are
     * seed-only, the rest are mid-combat scalars that start at zero. Removed in the corpse transmute (a corpse does not fight),
     * so a live combatant is {@code {IDENTITY, POSITION, HEALTH, COMBAT}}. The
     * optional <em>secondary</em> weapon is a separate presence component, not a
     * field here — see {@code ecs-nouns.md}.
     *
     * <p><b>Fire-intent (consume-once):</b> {@code long fireTargetId; int
     * fireStance; int fireReposition}. A behavior that decides to shoot writes
     * these instead of firing inline — {@code fireTargetId} is the entity to
     * shoot <em>this tick</em> ({@code 0L} = no intent = hold fire),
     * {@code fireStance} is the {@link com.dillon.starsectormarines.battle.combat.FireStance}
     * ordinal for the shot, and {@code fireReposition} flags the post-fire
     * {@code RepositionToCover} call. Target <em>selection</em> and every
     * posture-specific pre-gate stay with the behavior; {@code
     * battle.combat.FiringSystem} consumes the intent every tick (clearing
     * {@code fireTargetId} whether or not it fired) and applies the uniform
     * cooldown/range/LoS execution gate. Nullable {@code pointFireAim} is the
     * mutually exclusive target-free alternative; nullable {@code burstPointAim}
     * preserves that world point across follow-up rounds. Both intent forms
     * share stance and cadence, but point fire lets physical obstacles stop the
     * emitted round rather than rejecting the trigger for line of sight. See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType COMBAT;
    /**
     * Career counters for one combatant — {@code int roundsFired, roundsHit;
     * float damageDealt, friendlyFireDamage, damageTaken; int kills,
     * secondaryUsed}. <em>Optional</em>, added at spawn on the same gate as
     * {@link #COMBAT}: only a combatant can fire or be credited with a kill,
     * so "has TELEMETRY" means "this entity's fighting is being recorded".
     *
     * <p><b>Lifecycle-stable, unlike {@link #COMBAT}.</b> It is deliberately
     * absent from the corpse-remove mask, so a marine's record survives the
     * death transmute and rides the corpse — which is the whole point: a
     * soldier's statistics matter most when they were killed, and the
     * end-of-battle gather runs long after the roster released them. See
     * {@code progression-nouns.md}.
     */
    public final ComponentType TELEMETRY;
    /**
     * Movement state — {@code float gaitPhase} (the repeating walk cycle),
     * {@code int[] path} (the flat path reference), {@code int pathIdx} (the
     * carrot cursor along it), {@code float moveSpeed} (the per-unit cells/sec
     * rate — a seed-only stat), the repath-throttle stamp, and the per-tick
     * applied velocity. <em>Optional</em>: added at spawn
     * only for mobile units, so "has MOVEMENT" defines a mover. A static
     * emplacement (a turret or drone hub;
     * {@link com.dillon.starsectormarines.battle.unit.UnitType#isStatic}) never
     * paths and carries no MOVEMENT — the few all-unit readers (the occupancy-map
     * and destination-index rebuilds) gate on {@code World.hasMovement};
     * per-unit movement code only ever runs for movers. Removed in the corpse
     * transmute (a corpse does not move). See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType MOVEMENT;
    /**
     * AI decision-cadence state — {@code float repositionCooldown; float
     * fallbackTimer; int fallbackCellX, fallbackCellY; float wanderDwellTimer}.
     * The per-unit countdowns + cached fall-back cell the decision tier drives:
     * reposition gating, break-contact fall-back (timer + its destination cell,
     * {@code -1/-1} = none — the one field pair whose default is <em>not</em>
     * zero, so {@code adopt} explicitly seeds the sentinel since a fresh world
     * row appends as zero), and the FLEE wander dwell. <em>Optional</em>: added at
     * spawn only for thinking units, so "has AI_STATE" defines a thinker. A static
     * emplacement (a turret or drone hub;
     * {@link com.dillon.starsectormarines.battle.unit.UnitType#isStatic}) has no
     * decision cadence and carries no AI_STATE — the per-tick dispatch
     * ({@code UnitUpdateSystem}) and the per-hit fall-back roll
     * ({@code HitResponseSystem}) gate on {@code World.hasAiState};
     * per-unit decision code only ever runs for thinkers. Removed in the corpse
     * transmute (a corpse does not think). See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType AI_STATE;
    /**
     * Sight stats — {@code float visionRange} (how far the unit sees, in cells —
     * its fog-of-war shadowcast radius) and {@code float airLosRadius} (the
     * close-wall "air" line-of-sight radius; {@code 0} = standard grid LoS, &gt;0
     * for fliers that see/shoot over the walls they hover above). <em>Universal</em>
     * (every live unit has one — both are seeded from {@code UnitType}, a ground
     * unit's {@code airLosRadius} just defaulting to 0), but <b>live-only</b>: a
     * corpse does not see, so the death transmute removes VISION (the COMBAT
     * precedent — accessors are fail-loud on a corpse). The data owner is
     * {@code battle.sim.VisionService} (the per-component Service mirroring
     * {@code CombatService}/{@code MovementService}); {@code FogOfWarService}'s
     * shadowcast + the decision/combat LoS checks read these by id off it. See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType VISION;
    /**
     * Optional squad membership — the {@code squadId} key into the roster's squad
     * registry plus the member's stable fire-team index. The first
     * <em>universal-domain</em> capability modeled as archetype presence rather than
     * a sentinel: a unit carries SQUAD <em>iff</em> it belongs to a squad, so "has
     * SQUAD" <em>is</em> membership and the old {@code Squad.NO_SQUAD} sentinel is
     * never a stored value — a solo defender / civilian / unsquadded turret simply
     * has no SQUAD (the SECONDARY_WEAPON presence precedent, applied to a field that
     * used to default to a sentinel on every unit). Set once at spawn (seeded at
     * {@code adopt} from {@code EntitySpec.squadId}; the post-spawn join seam is
     * {@code SquadService.assignSquad}); never reassigned on a live unit. The data
     * owner is {@code battle.sim.SquadService} ({@code hasSquad} presence + the
     * fail-loud {@code squadId} read), distinct from the squad <em>objects</em> the
     * roster owns. Removed in the corpse transmute (a corpse is not a squad member;
     * the death cascade reads membership pre-transmute). See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType SQUAD;
    /**
     * Behavior-dispatch role — one INT field, the {@link
     * com.dillon.starsectormarines.battle.unit.UnitRole} ordinal ({@code RoleService}
     * hides the ordinal↔enum round-trip). <em>Universal</em> (every live unit has a
     * role — the default is {@code COMBATANT}), but <b>live-only</b>: a corpse does
     * not act, so the death transmute removes ROLE (the COMBAT/SQUAD precedent — the
     * accessor is fail-loud on a corpse; the death cascade reads the role
     * pre-transmute). Stored as an ordinal INT rather than an OBJECT enum ref because
     * a role is fundamentally a small enumerated value — a plain primitive column that
     * later lets dispatch branch on / partition by the ordinal without materializing
     * the {@code Entity}. Unlike SQUAD it is <b>mutable on a live unit</b> (a marine is
     * promoted to {@code KIT_RETRIEVER}/{@code PLANTER} on a kit pickup and reverts to
     * {@code COMBATANT}), so {@code RoleService} carries a live {@code setRole} seam
     * beside the {@code adopt}-time seed from {@code EntitySpec.role}. The data owner
     * is {@code battle.sim.RoleService}; the per-tick dispatch ({@code UnitUpdateSystem})
     * reads it by id. See {@code ecs-nouns.md}.
     */
    public final ComponentType ROLE;
    /**
     * Optional garrison idle-post — two INT fields, the "home" cell a
     * {@link com.dillon.starsectormarines.battle.unit.UnitRole#GARRISON} unit returns
     * to and holds while its squad is UNAWARE. <em>Optional</em>: added at spawn only
     * for garrison defenders (seeded from {@code EntitySpec.homeCellX}/{@code homeCellY}
     * when {@code >= 0}), so "has HOME" <em>is</em> "has a post" — the old {@code -1} sentinel is never a
     * stored value (a roaming marine / patrol simply carries no HOME). The runtime
     * reassignment ({@code SquadFallbackSystem} redistributes posts when a garrison
     * squad retreats to a new node) is a serial-phase write on units that already carry
     * HOME. Live-only (a corpse holds no post) — removed on the corpse transmute. Data
     * owner {@code battle.sim.HomeService}. See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType HOME;
    /**
     * Optional passenger state — one LONG field, the entity id of the vehicle
     * this unit is riding in. <em>Optional and rare</em>: added when a unit
     * mounts and removed when it dismounts, so "has RIDING" <em>is</em> "is
     * aboard something".
     *
     * <p>A mounted unit keeps everything about who it is — {@code IDENTITY},
     * {@code HEALTH}, {@code SQUAD}, its loadout — and loses everything about
     * being somewhere: {@code POSITION}, {@code MOVEMENT}, {@code VISION},
     * {@code COMBAT}. That is the same membership-narrowing a convoy chassis
     * already relies on, one level down: the occupancy map, the fire system,
     * the mover and the sight passes stop matching it without any of them
     * learning what a passenger is. This component exists so the unit update
     * can say so explicitly rather than inferring it from a missing
     * {@code POSITION}, which would silently swallow a genuine bug.
     *
     * <p>Live-only, and the single source of truth for who is aboard what:
     * a carrier's manifest is the set of units whose {@code RIDING} names it,
     * so there is no second list to fall out of step. Data owner
     * {@code battle.vehicle.VehicleTransportService}.
     */
    public final ComponentType RIDING;
    /**
     * Optional objective/kit task — two <em>nullable</em> OBJECT fields: the
     * {@link com.dillon.starsectormarines.battle.command.objective.Objective}
     * {@code assignedObjective} a unit is acting on (a planter's charge site, a VIP's
     * exfil, an objective-camper's position) and the
     * {@link com.dillon.starsectormarines.battle.infantry.EquipmentDrop}
     * {@code equipmentDropTarget} a KIT_RETRIEVER is heading to. <em>Optional</em>:
     * added when a unit first acquires a task (seeded at spawn for loadout
     * planters/VIPs; added at kit-assignment for recruited retrievers) — so a plain
     * combatant that was never tasked carries no TASK, and {@code TaskService}'s reads
     * are <b>tolerant</b> (null when the component is absent, preserving the old
     * "null = no task" semantics). Modeled as nullable fields rather than presence tags
     * because {@code equipmentDropTarget} is <em>cleared during the parallel dispatch</em>
     * ({@code KitRetrieverBehavior}), which must be a plain field-write, not a structural
     * remove. Live-only (the death cascade reads the task pre-transmute, in resolve()).
     * Data owner {@code battle.sim.TaskService}. See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType TASK;
    /**
     * Optional special equipment — {@code SpecialEquipmentDef spec; int ammo; float
     * cooldownTimer, actionTimer; long aimTargetId; int fired}. The first
     * <em>optional</em> live capability modeled as archetype presence: added at
     * spawn only for units that carry a secondary (a rocket launcher today;
     * other secondary types later), absent on everyone else — so "has a
     * secondary" is the archetype membership, not a nullable field. The
     * {@code spec} flyweight is weapon-type-agnostic; richer AI may later query
     * it to decide what the unit can do. Removed in the corpse transmute (no-op
     * for units that never had it). See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType SECONDARY_WEAPON;
    /**
     * Optional integral-system state — present iff the unit's worn armour
     * pattern declares one, so presence IS "this suit can do something"
     * ({@code integral-armor-systems.md}). Holds the authored definition plus
     * the live cooldown/active timers, in the {@link #SECONDARY_WEAPON}
     * precedent: a flyweight spec column beside the per-unit clocks.
     *
     * <p>Deliberately separate from {@link #SECONDARY_WEAPON}: a suit's system
     * is not the billet's carried item and must never spend it. A marine can
     * carry both, and most carry neither. Removed in the corpse transmute.
     */
    public final ComponentType INTEGRAL_SYSTEM;
    /**
     * Optional mitigation state — the live screen an actor can hold up:
     * {@code float fraction, arcDegrees, facingDegrees, remaining}
     * ({@code combat-durability-nouns.md}).
     *
     * <p>Presence means "this actor carries something that can raise a screen",
     * not "a screen is up right now" — the {@link #ARMOR} shape rather than the
     * {@link #INTEGRAL_SYSTEM} one, so a grant and an expiry are a handful of
     * float writes instead of an archetype move on the damage path. A raised
     * screen is {@link #MITIGATION_REMAINING} {@code > 0} <em>and</em>
     * {@link #MITIGATION_SOAK_REMAINING} {@code > 0}; ask
     * {@code MitigationService.isActive} rather than reading the column.
     *
     * <p>The facing is deliberately simulation state, unlike the presentation
     * facings on {@link #LAYERED_ANIMATION}. A screen that turns with the wearer
     * is the rule that keeps an entry a squad problem, so it cannot be authored
     * by a render-tier system. Removed in the corpse transmute.
     */
    public final ComponentType MITIGATION;
    /**
     * Optional <em>presentation</em> state for a running integral system:
     * {@code float intensity, arcFacingDegrees, arcDegrees, soakFraction,
     * breakFlash, shimmerPhase} ({@code progression-nouns.md}).
     *
     * <p>Write-only appearance data in the {@link #SPRITE} / {@link #THRUSTER_FX}
     * sense — {@code battle.appearance.SystemFxSystem} authors it each tick from
     * the live {@link #INTEGRAL_SYSTEM} and {@link #MITIGATION} state, and the
     * render tier is a pure reader of it. <b>Nothing in the simulation reads
     * these columns</b>, and no activation, damage, or movement decision may
     * ever come to depend on one.
     *
     * <p>Presence means "this actor can show a system running", following its
     * {@link #INTEGRAL_SYSTEM} exactly — the {@link #MITIGATION} shape rather
     * than an archetype move per activation, so starting and ending a treatment
     * is a handful of float writes. A treatment that has ended is
     * {@link #SYSTEM_FX_INTENSITY} {@code 0}; ask
     * {@code SystemFxService.isRunning} rather than reading the column. Removed
     * in the corpse transmute, so a treatment can never outlive its wearer.
     *
     * <p>Deliberately keyed on the capability rather than on the effect: a
     * running system with no screen simply reports {@link #SYSTEM_FX_ARC_DEGREES}
     * {@code 0}, and the renderer branches on the data it was handed instead of
     * on which suit produced it.
     */
    public final ComponentType SYSTEM_FX;
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
     * {@code ecs-nouns.md}.
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
     * {@code ecs-nouns.md}.
     */
    public final ComponentType MECH_LOADOUT;
    /**
     * Continuous kinematics for a flier — one OBJECT field holding the
     * {@link com.dillon.starsectormarines.battle.air.AirBody} (float
     * position/velocity/facing). The air analogue of {@link #POSITION} (which is
     * an int grid cell): a shuttle has {@code KINEMATICS} and no grid
     * {@code POSITION}; a drone has <em>both</em> (its cell synced from the body
     * each tick). An OBJECT column (not decomposed floats) because {@code AirBody}
     * is a small, shared POJO (drones and {@code CrashingComponent} hold one
     * too) and air is a tiny population — the
     * CRASHING/MECH_LOADOUT precedent; see {@code air-nouns.md}.
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
     * state machine, LZ/entry/exit, marines remaining, squad, cycle schedule).
     * Structure is deliberately <em>not</em> here: an aircraft's hull lives in
     * an ordinary {@link #HEALTH} component beside an ordinary {@link #ARMOR}
     * one, so the damage pipeline resolves a hit on an airframe with the same
     * law it resolves every other hit. The mission bag carries what the sortie
     * is doing, not how much of the machine is left. The CRASHING/MECH_LOADOUT
     * OBJECT-payload precedent.
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
    /**
     * Authored air render-state — {@code float altitudeT, flightPhase}. The
     * altitude-driven visual feel of a flier: {@code altitudeT} (0 on the LZ, 1 at
     * cruise) and {@code flightPhase} (the accumulated wobble phase). The derived
     * {@code scaleMult} + altitude Y-offset + engine intensity are pure functions
     * of these, computed on read by {@code AirAppearance} — not stored. A FLOAT
     * pair (not an OBJECT payload) because, unlike {@link #KINEMATICS} /
     * {@link #SHUTTLE_MISSION}, there is no pre-existing shared instance to alias;
     * the render-state is genuinely <em>moved</em> here off the {@code Shuttle}
     * handle. Part of the air spawn archetype (every craft has one); written by
     * {@code AirSystem}'s state-machine tick, read by the render + audio passes by
     * id. The [[feedback_appearance_authored_component]] pattern. See
     * {@code air-nouns.md}.
     */
    public final ComponentType APPEARANCE;
    /**
     * Ground-vehicle identity — {@code VehicleType type, Faction faction}. The
     * ground twin of {@link #AIR_IDENTITY}: convoy trucks / APCs are not units, so
     * they get their own identity pair rather than widening grid {@link #IDENTITY}
     * (whose {@code type} is a concrete
     * {@link com.dillon.starsectormarines.battle.unit.UnitType}). Part of the
     * convoy-{@code Vehicle}-into-world epic
     * ({@code ecs-nouns.md}).
     */
    public final ComponentType GROUND_IDENTITY;
    /**
     * Ground-vehicle kinematics — one OBJECT field holding the
     * {@link com.dillon.starsectormarines.battle.vehicle.GroundBody} (a
     * {@code BicycleBody} today) with continuous float pose. The ground sibling of
     * {@link #KINEMATICS} (which holds an {@code AirBody}); a <em>separate</em>
     * component rather than a generalization because {@code GroundBody} /
     * {@code AirBody} share no base type and the {@code AIR_IDENTITY}-vs-{@code IDENTITY}
     * precedent favors disjoint components over widening. OBJECT (not decomposed
     * floats) for the same tiny-population + shared-mutable-POJO reason as
     * {@link #KINEMATICS}.
     */
    public final ComponentType GROUND_KINEMATICS;
    /**
     * Optional ground-vehicle turret — one OBJECT field holding a
     * {@link com.dillon.starsectormarines.battle.vehicle.GroundTurret} (the live
     * aim/fire state). Presence == "armed"; an unarmed transport (truck) lacks it. The
     * ground twin of {@link #AIR_TURRETS}. Seeded at spawn for a vehicle whose
     * {@code VehicleType.hasTurretWeapon()}; {@code has}-gate every read.
     */
    public final ComponentType GROUND_TURRET;
    /**
     * Convoy mission state — one OBJECT field. The lifecycle/deboard/route/path state a
     * convoy vehicle carries: the payload is the
     * {@link com.dillon.starsectormarines.battle.vehicle.VehicleMission} bag (so the backbone
     * can be a {@code List<Long>} of ids and the mission lives in the world, not a side
     * list), reached via {@code ConvoyService.vehicle(id)}. Liveness is {@code state ==
     * GONE}, not {@link #HEALTH}.
     */
    public final ComponentType VEHICLE_MISSION;
    /**
     * Optional drone-hub live state — {@code float spawnCooldown; int
     * dronesLaunched; int droneSquadId}. Presence <em>IS</em> "is a live drone
     * hub" — added at spawn only for {@code UnitType.DRONE_HUB_STRUCTURE}
     * ({@link com.dillon.starsectormarines.battle.unit.UnitType#isDroneHub()}),
     * absent on every other unit. {@code spawnCooldown} is the sim-seconds
     * countdown to the hub's next launch attempt, ticked by
     * {@code battle.drone.DroneHubBehavior}; {@code dronesLaunched} is the
     * lifetime launch count {@code battle.drone.DroneSpawner} folds into each
     * drone's greppable id; {@code droneSquadId} is the squad the hub's drones
     * join, minted lazily on the first successful launch — {@code
     * Squad.NO_SQUAD} ({@code -1}) is the "no squad yet" sentinel <em>because
     * {@code 0} is a valid squad id</em>, so a fresh-row zero can't be
     * mistaken for "already minted". Live-only (a demolished hub is a corpse;
     * see {@code battle.drone.HubDemolitionSystem}'s side-table for the
     * separate {@code demolished} flag, which is not world state). The data
     * owner is {@code battle.sim.HubStateService}. See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType HUB_STATE;
    /**
     * Optional turret live state — {@code float facingDegrees; float
     * recoilTimer; String structureId; int burstRemaining; float burstTimer; long
     * burstTargetId}. Presence <em>IS</em> "is a live turret" — added at spawn
     * only for {@code UnitType.TURRET}
     * ({@link com.dillon.starsectormarines.battle.unit.UnitType#isTurret()}),
     * absent on every other unit. {@code facingDegrees}/{@code recoilTimer} are
     * read every tick by {@code battle.turret.TurretBehavior} (the aim/fire
     * loop) and the renderer (barrel rotation + recoil slide); {@code kind} is
     * the stable structure id whose installed definition owns stats, sprite, and firing profile
     * construction by the {@code MapTurret} factory.
     *
     * <p>{@code burstRemaining}/{@code burstTimer}/{@code burstTargetId} are a
     * <b>deliberately self-contained turret-only burst</b> — <em>not</em> the
     * COMBAT burst columns ({@code world.burstRemaining(id)} etc.) that
     * infantry/mech/drone {@code beginBurst} writes.
     * {@code battle.infantry.InfantryWeapons.tick} gathers every combatant with
     * {@code COMBAT.burstRemaining(id) > 0} and continues its burst via the
     * infantry {@code fireShot} path; turrets fire their burst rounds through
     * {@code TurretBehavior} using the turret {@code fireShotFrom} pipeline
     * (modeled direct round or legacy aerial/indirect procedure per kind).
     * If turrets wrote the COMBAT
     * burst columns, {@code InfantryWeapons.tick} would ALSO process them —
     * double-fire, and through the wrong pipeline — so the turret burst rides
     * here instead and {@code InfantryWeapons} never reads/writes it.
     *
     * <p>Live-only (a demolished turret is a corpse; see
     * {@code battle.turret.TurretDemolitionSystem}'s side-table for the
     * separate {@code demolished} flag, which is not world state). The data
     * owner is {@code battle.sim.TurretStateService}. See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType TURRET_STATE;
    /**
     * Optional drone live state — {@code float patrolGoalX, patrolGoalY,
     * pursuitGoalX, pursuitGoalY, pursuitTimer; long homeHubId}. Presence
     * <em>IS</em> "is a live drone" — added at spawn only for
     * {@code UnitType.DRONE}
     * ({@link com.dillon.starsectormarines.battle.unit.UnitType#isDrone()}),
     * absent on every other unit. {@code patrolGoalX/Y} is the current patrol
     * waypoint (cell coords) the drone cruises toward while idle;
     * {@code pursuitGoalX/Y} is the last-known engagement/agro-scan position
     * the drone commits to closing on while {@code pursuitTimer} counts down.
     * Both goal pairs use {@link Float#NaN} as the "no waypoint yet" sentinel
     * — a fresh world row appends {@code 0.0f}, not {@code NaN}, and
     * {@code battle.drone.DroneSwarmAction}'s {@code ensureSectorWaypoint}
     * gates on {@code Float.isNaN}, so {@code UnitRosterService.adopt}
     * seeds the sentinel explicitly (the {@code AI_STATE} {@code -1}/{@code -1}
     * fall-back-cell precedent). {@code homeHubId} is the entity id of the hub
     * that launched this drone ({@code 0L} = none — test fixtures that never
     * register a hub), seeded once at spawn from {@code EntitySpec.homeHubId}
     * and never reassigned.
     *
     * <p>Read/written every tick by {@code battle.drone.DroneSwarmAction} (the
     * three-mode engage/pursue/patrol dispatch); {@code battle.drone.DroneHubBehavior}
     * and {@code battle.drone.HubDemolitionSystem} read {@code homeHubId} to
     * find a hub's active/doomed drones. Live-only (a crashing drone doesn't
     * patrol — removed in the corpse transmute, unlike {@code KINEMATICS},
     * which rides the transmute so the crash system can read the falling
     * body). The data owner is {@code battle.sim.DroneStateService}. See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType DRONE_STATE;

    /**
     * Per-vehicle ground-motion control state — the id-keyed bag that used to live as loose
     * fields on {@code VehicleController}. Single OBJECT payload
     * ({@link com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent}),
     * seeded at spawn and dropped wholesale at despawn (vehicles never corpse-transmute). The
     * data owner is {@code battle.sim.ConvoyService} ({@code control(id)}). See
     * {@code ecs-nouns.md}.
     */
    public final ComponentType VEHICLE_CONTROL;

    // ---- shared queries (per-world lifecycle, cached matched-table lists) ----

    /**
     * The corpse archetype {@code {IDENTITY, POSITION, SPRITE,
     * CORPSE}} — every body on the field, exactly as {@code DeadBodySystem}
     * spawns them. Walked by the dead-sprite render and the mission resolver's
     * casualty tally. Split into narrower queries only when a corpse variant
     * that lacks one of these components actually exists.
     */
    public final Query corpses;

    /**
     * Every live sheet-drawn unit ({@code {IDENTITY, POSITION,
     * SPRITE, HEALTH}}) — {@code SPRITE} now lives on the live archetype for every
     * {@link com.dillon.starsectormarines.battle.unit.UnitType#drawnAsSheet()}
     * type, authored per-tick by {@code battle.appearance.FacingSystem}.
     * Requiring {@code HEALTH} excludes corpses (the death transmute removes
     * it) without an explicit {@code CORPSE} exclusion. Walked by
     * {@code FacingSystem.tick()} and the live-sprite render sweep.
     */
    public final Query liveSprites;
    /** Live modular actors carrying authored layered-animation state. */
    public final Query layeredSprites;

    /**
     * Every live actor with authored armor state ({@code {HEALTH, ARMOR}}).
     * A depleted but living actor remains in this query because component
     * presence expresses the armor capability; the corpse transmute removes
     * both live-only components.
     */
    public final Query armoredActors;

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

    /**
     * Every <em>live</em> grid-resident entity ({@code {POSITION}} minus
     * {@code {CORPSE}}) — the dense roster's live set: movers, static emplacements
     * (turrets / hubs), and drones, exactly the units
     * {@code NavigationService.rebuildOccupancyMap} bins into the occupancy grid.
     * Corpses carry POSITION but are excluded (a body claims no cell); air craft
     * carry no POSITION and never match. A matched table carries {@code MOVEMENT}
     * iff its rows are movers, so a per-table {@code has(MOVEMENT)} check (not a
     * per-row probe) partitions path-reserving movers from cell-claiming statics —
     * the first per-tick combatant-population {@code Query} consumer (the systems
     * half, {@code ecs-nouns.md}).
     */
    public final Query gridOccupants;

    /**
     * Every live combatant ({@code {COMBAT}}) — the fire-intent column walk
     * {@code battle.combat.FiringSystem} drives, consuming each row's
     * {@code fireTargetId}/{@code fireStance}/{@code fireReposition} and
     * applying the uniform cooldown/range/LoS gate. No exclusion mask needed:
     * {@code COMBAT} itself is removed in the corpse transmute, so a corpse
     * never matches.
     */
    public final Query combatants;

    /**
     * Every entity carrying a {@link #TELEMETRY} record, live or dead. No
     * exclusion mask, deliberately: {@code TELEMETRY} survives the corpse
     * transmute, and a battle report that dropped the fallen would be the
     * exact report nobody wants. {@link #IDENTITY} rides along because every
     * row needs a name, a faction, and (for a marine) the campaign soldier id
     * the gather keys on.
     */
    public final Query telemetryRecords;

    public BattleComponents(EntityWorld world) {
        IDENTITY        = world.register(0, "Identity", FieldKind.OBJECT, FieldKind.OBJECT,
                FieldKind.OBJECT, FieldKind.OBJECT, FieldKind.OBJECT, FieldKind.OBJECT,
                FieldKind.OBJECT);
        POSITION        = world.register(1, "Position", FieldKind.FLOAT, FieldKind.FLOAT);
        SPRITE          = world.register(3, "Sprite", FieldKind.INT, FieldKind.INT, FieldKind.INT);
        CORPSE          = world.register(4, "Corpse");
        HEALTH          = world.register(5, "Health", FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.FLOAT, FieldKind.FLOAT);
        COMBAT          = world.register(6, "Combat",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.LONG, FieldKind.INT, FieldKind.FLOAT, FieldKind.LONG,
                FieldKind.FLOAT, FieldKind.OBJECT,
                FieldKind.LONG, FieldKind.INT, FieldKind.INT,
                FieldKind.OBJECT, FieldKind.OBJECT,
                FieldKind.LONG, FieldKind.FLOAT,
                FieldKind.INT, FieldKind.INT,
                FieldKind.FLOAT, FieldKind.INT, FieldKind.INT, FieldKind.INT,
                FieldKind.INT, FieldKind.OBJECT, FieldKind.OBJECT);
        SECONDARY_WEAPON = world.register(7, "SecondaryWeapon",
                FieldKind.OBJECT, FieldKind.INT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.LONG, FieldKind.INT);
        MOVEMENT        = world.register(8, "Movement",
                FieldKind.FLOAT, FieldKind.OBJECT, FieldKind.INT, FieldKind.FLOAT,
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.FLOAT, FieldKind.OBJECT, FieldKind.INT, FieldKind.INT, FieldKind.OBJECT);
        AI_STATE        = world.register(9, "AiState",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.INT, FieldKind.INT, FieldKind.FLOAT,
                FieldKind.OBJECT, FieldKind.FLOAT);
        CRASHING        = world.register(10, "Crashing", FieldKind.OBJECT);
        MECH_LOADOUT    = world.register(11, "MechLoadout", FieldKind.OBJECT);
        KINEMATICS      = world.register(12, "Kinematics", FieldKind.OBJECT);
        AIR_IDENTITY    = world.register(13, "AirIdentity", FieldKind.OBJECT, FieldKind.OBJECT);
        SHUTTLE_MISSION = world.register(14, "ShuttleMission", FieldKind.OBJECT);
        THRUSTER_FX     = world.register(15, "ThrusterFx", FieldKind.OBJECT);
        AIR_TURRETS     = world.register(16, "AirTurrets", FieldKind.OBJECT);
        APPEARANCE      = world.register(17, "Appearance", FieldKind.FLOAT, FieldKind.FLOAT);
        VISION          = world.register(18, "Vision", FieldKind.FLOAT, FieldKind.FLOAT);
        SQUAD           = world.register(19, "Squad", FieldKind.INT, FieldKind.INT);
        ROLE            = world.register(20, "Role", FieldKind.INT);
        HOME            = world.register(21, "Home", FieldKind.INT, FieldKind.INT);
        RIDING          = world.register(40, "Riding", FieldKind.LONG);
        TASK            = world.register(22, "Task", FieldKind.OBJECT, FieldKind.OBJECT);
        GROUND_IDENTITY   = world.register(23, "GroundIdentity", FieldKind.OBJECT, FieldKind.OBJECT);
        GROUND_KINEMATICS = world.register(24, "GroundKinematics", FieldKind.OBJECT);
        GROUND_TURRET     = world.register(25, "GroundTurret", FieldKind.OBJECT);
        VEHICLE_MISSION   = world.register(26, "VehicleMission", FieldKind.OBJECT);
        HUB_STATE       = world.register(27, "HubState", FieldKind.FLOAT, FieldKind.INT, FieldKind.INT);
        TURRET_STATE    = world.register(28, "TurretState", FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.OBJECT, FieldKind.INT, FieldKind.FLOAT, FieldKind.LONG);
        DRONE_STATE     = world.register(29, "DroneState", FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.LONG);
        VEHICLE_CONTROL = world.register(30, "VehicleControl", FieldKind.OBJECT);
        LAYERED_ANIMATION = world.register(31, "LayeredAnimation",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.INT, FieldKind.INT, FieldKind.INT, FieldKind.INT);
        MECH_LAYERED_ANIMATION = world.register(32, "MechLayeredAnimation",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.FLOAT, FieldKind.INT, FieldKind.INT, FieldKind.INT,
                FieldKind.INT, FieldKind.INT, FieldKind.FLOAT);
        MECH_LOCOMOTION = world.register(33, "MechLocomotion",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT);
        TELEMETRY       = world.register(34, "Telemetry",
                FieldKind.INT, FieldKind.INT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.FLOAT, FieldKind.INT, FieldKind.INT, FieldKind.FLOAT,
                FieldKind.INT, FieldKind.INT);
        ARMOR          = world.register(35, "Armor",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT);
        INTEGRAL_SYSTEM = world.register(36, "IntegralSystem",
                FieldKind.OBJECT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.INT);
        MECH_GAIT_STATE = world.register(37, "MechGaitState", FieldKind.OBJECT);
        MITIGATION      = world.register(38, "Mitigation",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.FLOAT, FieldKind.FLOAT);
        SYSTEM_FX       = world.register(39, "SystemFx",
                FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT, FieldKind.FLOAT,
                FieldKind.FLOAT, FieldKind.FLOAT);
        corpses = world.query(
                new ComponentType[]{IDENTITY, POSITION, SPRITE, CORPSE}, null);
        liveSprites = world.query(
                new ComponentType[]{IDENTITY, POSITION, SPRITE, HEALTH}, null);
        layeredSprites = world.query(
                new ComponentType[]{IDENTITY, POSITION, SPRITE,
                        LAYERED_ANIMATION, HEALTH}, null);
        armoredActors = world.query(new ComponentType[]{HEALTH, ARMOR}, null);
        crashing = world.query(new ComponentType[]{CRASHING}, null);
        mechLoadouts = world.query(new ComponentType[]{MECH_LOADOUT}, new ComponentType[]{CORPSE});
        airCraft = world.query(new ComponentType[]{AIR_IDENTITY, KINEMATICS, SHUTTLE_MISSION}, null);
        gridOccupants = world.query(new ComponentType[]{POSITION}, new ComponentType[]{CORPSE});
        combatants = world.query(new ComponentType[]{COMBAT}, null);
        telemetryRecords = world.query(new ComponentType[]{TELEMETRY, IDENTITY}, null);
    }
}
