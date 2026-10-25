package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.combat.BodyPlan;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;

import com.dillon.starsectormarines.battle.turret.MapTurret;

/**
 * Tier / archetype of a {@code Entity}. Bundles whether its body carries a
 * facing frame and death pose, its corpse sheet where it has one, whether it's a
 * combatant, and the base stat block applied at construction. {@link Faction}
 * stays orthogonal — a MARINE_RED unit can spawn as MARINE or DEFENDER faction
 * depending on which side hired the pirates this mission.
 *
 * <p>Live bodies are drawn from modular art (layered infantry, layered mechs,
 * Rav rigs); the old single-sheet unit art is gone.
 */
public enum UnitType {
    /** Player faction's elite. Higher accuracy and HP than militia, expensive in lore. */
    MARINE     (true, null,      true,  25f, 18.0f, 2.0f, 0.35f, 1.0f, 24.0f, 36.0f, 1.0f, 1.0f, 0.3f, 0.45f),
    /** Generic regular soldier of an aligned faction (e.g., friendly sector navy on a joint op). Stat-equal to MARINE; sprite differs. */
    MARINE_BLUE(true, null, true,  25f, 18.0f, 2.0f, 0.35f, 1.0f, 24.0f, 36.0f, 1.0f, 1.0f, 0.3f, 0.45f),
    /** Pirate / opposed-faction regular. Stat-equal to MARINE; sprite differs. Often on the DEFENDER side, occasionally allied. */
    MARINE_RED (true, null,  true,  25f, 18.0f, 2.0f, 0.35f, 1.0f, 24.0f, 36.0f, 1.0f, 1.0f, 0.3f, 0.45f),
    /** Lightly armed local conscript. Less accurate, less HP, slower cooldown, shorter sight lanes than a regular. The bread-and-butter defender. Low morale impact — militia are not that intimidating to marines, so a squad can take a lot of militia fire before breaking. */
    MILITIA    (true, null,     true,  15f, 13.5f, 2.0f, 0.22f, 1.2f, 18.0f, 28.0f, 1.0f, 0.4f, 0.3f, 0.45f),
    /** Hostile fauna / xeno boarder. Aggressive glass-cannon brawler — low HP, high damage, slightly faster, mid accuracy. Above-baseline morale impact (animal panic). */
    ALIEN      (true, null,       true, 15.0f, 3.0f, 2.2f, 0.32f, 1.1f, 22.0f, 34.0f, 1.0f, 1.2f, 0.3f, 0.45f),
    /** Compatibility archetype for modular strider mechs. The persistent {@link com.dillon.starsectormarines.battle.mech.MechVariant} supplies each body's actual stats, geometry, and installed {@link MechLoadoutComponent}; an unspecified profile defaults to the stock heavy Bulwark. Defender mechs show up when the target planet produces or demands heavy armaments. */
    HEAVY_MECH (true, "graphics/battle/heavy-mech-dead.png",  true,  550f, 36.0f, 1.15f, 0.40f, 0.6f, 40.0f, 55.0f, 1.6f, 1.5f, 0.6f, 0.80f),
    /**
     * A machine on the stocks in a vehicle bay — the chassis a garrison's
     * technicians are building, standing in its berth while they build it.
     *
     * <p>Combatant so it is seen, targeted and damaged exactly where it stands,
     * which is the whole reason it has a body at all: a production line nobody
     * can shoot is a number going up in a building. {@link #isStatic}, so it
     * neither paths nor thinks — a frame in a gantry is not deciding anything —
     * and its role is {@link UnitRole#STRUCTURE}, so it has no aim loop and
     * never fires. It is not finished; it has no weapons to fire with.
     *
     * <p><b>Its structure is how built it is.</b> HP is not a proxy for
     * progress here, it is the progress: welding raises it, a marine lowers it,
     * and the machine is finished when it is whole. So it spawns near zero and
     * is trivially easy to destroy early, which is correct — a keel is not a
     * mech.
     *
     * <p><b>Drawn as the chassis it is going to be.</b> A machine on the stocks
     * is a named variant taking shape, so it is composed from that variant's own
     * modular art exactly as the finished machine will be — an unfinished Hound
     * looks like a Hound. What it has not got is a loadout, which is what keeps
     * it out of the turret pass and off every firing path without anything
     * having to say so. Its dimensions, structure and silhouette all come from
     * the chassis on the instance; the values here are zero placeholders, the
     * same convention as {@link #TURRET}.
     *
     * <p>It keeps the mech's corpse sheet, so one shot in the gantry leaves a
     * hulk there rather than a machine that vanishes.
     */
    MACHINE_FRAME(true, "graphics/battle/heavy-mech-dead.png",
                                                   true,   0f, 0f,   0f,   0f,    1f,   0f,    0f,    1.6f, 0f, 0.6f, 0.80f),
    /** Random urban resident. Wanders the map and flees gunfire. Non-combatant; combat stats are unused but kept zero-safe. No corpse — civilian death just removes them from the map. */
    CIVILIAN   (true, null,                                   false,  8f, 0f,   2.4f, 0f,    1f,   0f,    12.0f, 1.0f, 1.0f, 0.3f, 0.45f),
    /** Maintenance / industrial worker. Same role as civilian — wanders, flees. */
    ENGINEER   (true, null,                                   false, 10f, 0f,   2.2f, 0f,    1f,   0f,    12.0f, 1.0f, 1.0f, 0.3f, 0.45f),
    /**
     * A garrison's working trade with a sidearm on their hip — the technicians
     * a motor pool or an airfield is staffed with.
     *
     * <p>The same person as an {@link #ENGINEER} and drawn on the same sheet;
     * what differs is that somebody issued them a pistol, which is a decision of
     * the force that posted them rather than a fact about the trade. A ship's
     * engineer is not armed and a garrison's is.
     *
     * <p>Armed, not soldiers. The numbers are a sidearm's: short reach, poor
     * accuracy, a slow cycle and little of the morale weight a rifle section
     * carries. A technician who is shot at can shoot back, cannot hold ground,
     * and is worth an attacker's attention only because they are standing on
     * something that matters.
     *
     * <p>No corpse sheet, like the engineer they are. What the art has is
     * civilians who vanish, and inventing a body for one trade would leave the
     * hangar's fallen drawn and the barracks' undrawn.
     */
    TECHNICIAN (true, null,                                   true,  12f, 7.0f, 2.2f, 0.15f, 1.4f, 9.0f, 20.0f, 1.0f, 0.2f, 0.3f, 0.45f),
    /** Lab tech. Same role as civilian — wanders, flees. Lower HP than engineer; same speed. */
    SCIENTIST  (true, null,                                   false,  8f, 0f,   2.2f, 0f,    1f,   0f,    12.0f, 1.0f, 1.0f, 0.3f, 0.45f),
    /** Invisible, simulation-only backstop used by authored live-fire tasks. It is targetable and static but never participates in a campaign roster or render pass. */
    RANGE_TARGET(false, null,                                   true, 1_000_000f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0.25f, 0.45f),
    /** Static ground turret. Combatant so it targets and gets targeted, but its sprite and stats come from the structure definition baked in at construction (see {@code MapTurret#create}); these values are zero placeholders overwritten by the factory. */
    TURRET     (false, null,                                   true,   0f, 0f,   0f,   0f,    1f,   0f,    0f,    1.0f, 1.0f, 0.45f, 0.60f),
    /** Drone launch hub — a static structure that periodically deploys aerial drones (see {@link com.dillon.starsectormarines.battle.drone.DroneHub}). Combatant so marines target and damage it, but its role is {@link UnitRole#STRUCTURE} (no aim loop, no firing). Sprite path is empty because the hub uses a per-instance vanilla weapon sprite picked at construction, same convention as {@link #TURRET}. HP set on the instance, not here. */
    DRONE_HUB_STRUCTURE (false, null,                                   true,   0f, 0f,   0f,   0f,    1f,   0f,    8.0f,  1.0f, 1.0f, 0.5f, 0.70f),
    /** Autonomous defensive drone launched from a {@link com.dillon.starsectormarines.battle.drone.DroneHub}. Combatant so marines target it, but the sprite path is empty because the drone uses a per-instance vanilla drone sprite (see {@link com.dillon.starsectormarines.battle.drone.Drone#SPRITE_PATH}), same convention as {@link #TURRET} / {@link #DRONE_HUB_STRUCTURE}. HP / speed are set on the instance, not here. */
    DRONE      (false, null,                                   true,   0f, 0f,   0f,   0f,    1f,   0f,    0f,    1.0f, 1.0f, 0.35f, 0.35f),
    /** An aircraft standing on a garrison hardstand — the grid-unit half of a based aircraft (see {@link com.dillon.starsectormarines.battle.air.BasedAircraft}). Combatant so it is targeted and damaged where it stands, but its role is {@link UnitRole#STRUCTURE}: no aim loop, no firing. Sprite path is empty because it draws its own hull, the same convention as {@link #TURRET} / {@link #DRONE_HUB_STRUCTURE}; HP and armor are set on the instance from the hull it represents. */
    BASED_AIRCRAFT (false, null,                                   true,   0f, 0f,   0f,   0f,    1f,   0f,    0f,    1.0f, 1.0f, 0.5f, 0.70f),
    /** A convoy ground vehicle — the chassis itself, not its payload (see {@link com.dillon.starsectormarines.battle.sim.ConvoyService}). Combatant so it is seen, targeted, and damaged through the paths every other body uses; its role, structure, armor, and body geometry all come from the per-instance {@link com.dillon.starsectormarines.battle.vehicle.VehicleType}, the same convention as {@link #TURRET} / {@link #BASED_AIRCRAFT}. Sprite path is empty because it draws its own chassis. Not {@link #isStatic}: it moves, it simply does its moving through a {@code GroundBody} rather than the grid mover. */
    GROUND_VEHICLE(false, null,                                   true,   0f, 0f,   0f,   0f,    1f,   0f,    0f,    1.0f, 1.3f, 1.5f, 0.90f),
    /** Fast biological close-contact attacker for the civilian-rescue swarm payload. Append-only; legacy ALIEN remains the generic ranged-stat archetype. */
    SWARM_RUNNER(true, null,       true,  20.0f, 5.0f, 3.2f, 1.0f, 0.7f, 1.5f, 20.0f, 1.0f, 1.3f, 0.3f, 0.30f),
    /**
     * Slow, plated swarm strain that breaks into held buildings and goes for emplacements. Its plate is
     * issued at spawn ({@code SwarmStrains}), not here: 20 points at rating 10, which a DMR or rocket
     * goes straight through and an SMG barely scratches. Append-only.
     */
    SWARM_BRUTE(true, null, true,  40.0f, 14.0f, 1.8f, 1.0f, 1.2f, 1.7f, 16.0f, 1.5f, 2.0f, 0.45f, 0.45f),
    /**
     * Fragile swarm strain that keeps its distance and lobs acid with a splash, at infantry or a low
     * flier. {@link #attackDamage} is the acid's direct hit; the splash and range live with its
     * behaviour. Append-only.
     */
    SWARM_SPITTER(true, "graphics/battle/swarm-spitter-dead.png", true, 12.0f, 7.0f, 2.6f, 0.55f, 2.6f, 14.0f, 20.0f, 1.1f, 1.4f, 0.3f, 0.30f),
    /**
     * The mother bore's heavy strain: one per mother at maw tier, a plated quadruped about four and a
     * half cells nose to tail. Slow and very durable; its scythes reap the infantry in front of it and its
     * crown wrecks emplacements, hulls and walls. {@link #attackDamage} is one reap's cut on each body it
     * catches; the crown blow and the reap's arc live with its behaviour. {@link #renderScale} is its
     * length in cells, which sizes its corpse. Append-only.
     */
    SWARM_MATRIARCH(true, "graphics/battle/rav-matriarch-dead.png", true, 1200.0f, 30.0f, 0.7f, 1.0f, 1.8f, 2.2f, 18.0f, 4.5f, 4.0f, 1.0f, 0.90f),
    /**
     * The Rav Bloom's flier: a winged strain that hunts in packs over the swarm ({@code SwarmFliers}). A roster
     * body like a drone, flown by its pack rather than by a per-unit behaviour, shot only by weapons that can
     * track it ({@link #isFlier}). Its numbers are issued at release ({@code HarrierRules}); these are
     * placeholders, the {@link #DRONE} convention. Append-only.
     */
    SWARM_HARRIER(false, null, true, 28f, 9f, 0f, 1.0f, 1.0f, 0.9f, 44f, 1.0f, 1.4f, 0.35f, 0.35f),
    /**
     * The skitterer, the nest's worker ({@code SwarmWorkers}): a small Runner-family body that hauls the dead,
     * mends the nest's growths, tends its creep and feeds its spire, and never fights. Drawn on the Runner's
     * skeleton at {@link #renderScale} of its size with its own bones. Its kit is issued at spawn
     * ({@code SwarmStrains}). Append-only.
     */
    SWARM_WORKER(true, null, true, 10f, 0f, 2.8f, 0f, 1.0f, 0f, 12f, 0.68f, 0.3f, 0.22f, 0.22f);

    /** Whether the body carries a {@code SPRITE} component: see {@link #carriesSprite()}. */
    private final boolean carriesSprite;
    /**
     * Optional corpse sheet — 4 prone poses, auto-sliced. Drawn only for a body no
     * other art lays down: one with no layered downed pose and no Rav rig of its own
     * (a mech hulk, a Spitter, a Matriarch). Null = no corpse sheet.
     */
    public final String deadSpritePath;
    public final boolean combatant;
    public final float maxHp;
    public final float attackDamage;
    public final float moveSpeed;
    public final float accuracy;
    public final float attackCooldown;
    public final float attackRange;
    /** How far this unit can see, in cells. Strictly &ge; {@link #attackRange} so a unit always spots enemies before they enter weapon range. Drives the fog-of-war shadowcast radius; 0 means the unit inherits {@code attackRange} as its vision range (used by turrets whose range is set per-instance). */
    public final float visionRange;
    /** Frame indexing convention of the facing frame {@code FacingSystem} authors. */
    /** Base multiplier on the renderer's per-cell sprite size; mech profiles override it per entity. */
    public final float renderScale;
    /** Base morale drain weight of outgoing fire; mech profiles override it per entity. */
    public final float moraleImpact;
    /**
     * Base collision/AoE footprint radius, in cells. Mech profiles override it
     * per entity through {@code UnitRosterService}. Consumed by {@code Detonations}
     * (widens a unit's AoE hit test beyond the raw blast radius to cover its
     * physical extent), {@code WorldPicker} (widens a unit's click pick
     * radius so bigger units are easier to select), {@code SeparationSystem}
     * (soft-collision body + inverse-mass {@code radius²}), and
     * {@code BallisticResolver} (unit-contact circle for ballistic rays) —
     * one physical body circle per entity, shared by every consumer.
     */
    public final float radius;
    /**
     * Half-height of the target-plane combat silhouette around Z=0, in
     * cells. Mech profiles override it per entity. This is deliberately independent of {@link #renderScale}: the
     * resolver combines it with {@link #radius} to decide whether a visible
     * high/low round intersects the body.
     */
    public final float hitHalfHeight;

    UnitType(boolean carriesSprite, String deadSpritePath, boolean combatant,
             float maxHp, float attackDamage, float moveSpeed,
             float accuracy, float attackCooldown, float attackRange,
             float visionRange,
             float renderScale, float moraleImpact,
             float radius, float hitHalfHeight) {
        this.carriesSprite = carriesSprite;
        this.deadSpritePath = deadSpritePath;
        this.combatant = combatant;
        this.maxHp = maxHp;
        this.attackDamage = attackDamage;
        this.moveSpeed = moveSpeed;
        this.accuracy = accuracy;
        this.attackCooldown = attackCooldown;
        this.attackRange = attackRange;
        this.visionRange = visionRange;
        this.renderScale = renderScale;
        this.moraleImpact = moraleImpact;
        this.radius = radius;
        this.hitHalfHeight = hitHalfHeight;
    }

    /**
     * Whether this archetype is a mech-class chassis — the units that carry a
     * {@link MechLoadoutComponent} component (attached at spawn). Today just
     * {@link #HEAVY_MECH}; a future tank/hovercraft chassis joins this predicate.
     * Used where mech-ness must be known <em>before</em> the loadout component is
     * attached (squad-type denormalization at mint time).
     */
    public boolean isMech() { return this == HEAVY_MECH; }

    /**
     * Whether this archetype is a static emplacement — a {@link #TURRET} or a
     * {@link #DRONE_HUB_STRUCTURE}. These are combatants (targetable, damageable)
     * but immobile and mindless: they neither path nor run a decision cadence, so
     * {@code UnitRosterService.allocate} omits the {@code MOVEMENT} and {@code AI_STATE}
     * components for them (presence <em>is</em> the capability — "has MOVEMENT" =
     * mover, "has AI_STATE" = thinker). Every other type moves and decides
     * (infantry, mechs, wandering civilians, patrolling drones). If a future type
     * ever splits these — mobile-but-mindless, or static-but-thinking — this one
     * predicate becomes two.
     */
    public boolean isStatic() {
        return this == TURRET || this == DRONE_HUB_STRUCTURE || this == RANGE_TARGET
                || this == BASED_AIRCRAFT || this == MACHINE_FRAME;
    }

    /**
     * Whether this archetype is an aircraft standing on its hardstand — the
     * type tag the render pass and the airfield's own tick use, in the same
     * place {@link #isTurret} and {@link #isDrone} sit. An aircraft in the air
     * is not this: it is an air entity with no unit type at all.
     */
    public boolean isBasedAircraft() { return this == BASED_AIRCRAFT; }

    /**
     * Whether this archetype is a machine part-built in a vehicle bay — the
     * type tag that sits beside {@link #isTurret} and {@link #isBasedAircraft}
     * for the same reason: what a body is stays a fact about its archetype, so
     * the grid walks classify one without asking the works whether an id is a
     * frame.
     */
    public boolean isMachineFrame() { return this == MACHINE_FRAME; }

    /**
     * Whether this archetype is an armed working trade rather than a soldier.
     *
     * <p>Used where "combatant" is too blunt a word for what somebody is. A
     * technician with a pistol counts as armed everywhere the flag is read, and
     * a commander that weighed one the same as a rifleman would read a works
     * crew as a garrison.
     */
    public boolean isTechnician() { return this == TECHNICIAN; }

    /**
     * Whether this archetype is a drone launch hub — the classification gate
     * that replaced the old {@code instanceof} subclass checks once the hub's
     * live state moved off a dedicated {@code Entity} subclass and onto the
     * world {@code HUB_STATE} component. Used by {@code UnitRosterService.allocate}
     * to attach {@code HUB_STATE} (presence IS "is a live drone hub") and by the
     * render/scoring type-tag sites (footprint sizing, HP-bar sizing,
     * {@code TacticalScoring.isHardened}) that used to cast to the subclass.
     */
    public boolean isDroneHub() { return this == DRONE_HUB_STRUCTURE; }

    /**
     * Whether this archetype is a static ground turret — the classification
     * gate that replaced the old {@code instanceof} subclass checks once the
     * turret's live state moved off a dedicated {@code Entity}
     * subclass and onto the world {@code TURRET_STATE} component. Used by
     * {@code UnitRosterService.allocate} to attach {@code TURRET_STATE}
     * (presence IS "is a live turret") and by the render/scoring type-tag
     * sites (footprint sizing, HP-bar sizing, {@code TacticalScoring.isHardened},
     * the AA "defense posts only" filter) that used to cast to the subclass.
     */
    public boolean isTurret() { return this == TURRET; }

    /**
     * Whether this archetype is an autonomous defensive drone — the
     * classification gate that replaced the old {@code instanceof} subclass
     * checks once the drone's live state moved off a dedicated {@code Entity}
     * subclass and onto the world {@code DRONE_STATE} component. Used by
     * {@code UnitRosterService.allocate} to attach {@code DRONE_STATE}
     * (presence IS "is a live drone") and by the render/behavior type-tag
     * sites ({@code DroneRenderSystem}, {@code DroneCrashSystem},
     * {@code DroneHubBehavior.countActiveDrones}, {@code HubDemolitionSystem.cascadeKillDrones})
     * that used to cast to the subclass.
     */
    public boolean isDrone() { return this == DRONE; }

    /**
     * Whether this is a body in the low air: drawn above the roofs, out of every ground blast, and engaged only
     * by a weapon that can track it ({@code WeaponDef.tracksFliers}). Today the Harrier; a drone keeps the
     * older rule, under which anything may shoot it.
     */
    public boolean isFlier() { return this == SWARM_HARRIER; }

    /** Whether this archetype is a swarm strain body on the ground: runner, brute, spitter, matriarch or skitterer. */
    public boolean isSwarm() {
        return this == SWARM_RUNNER || this == SWARM_BRUTE || this == SWARM_SPITTER
                || this == SWARM_MATRIARCH || this == SWARM_WORKER;
    }

    /**
     * Whether this is the skitterer, the swarm's worker: a swarm body that never fights, kept out of the
     * director's population and every surge ({@code SwarmWorkers}).
     */
    public boolean isSwarmWorker() { return this == SWARM_WORKER; }

    /**
     * Whether this unit is a trained humanoid whose individual soldier profile
     * controls handling, trigger discipline, and reflexes. Kept here so every
     * combat subsystem uses the same boundary: fauna, drones, turrets, and
     * mechs have their own behavior models rather than inheriting a synthetic
     * {@code SoldierProfile.REGULAR} skill roll.
     */
    public boolean usesInfantryTraining() {
        return this == MARINE || this == MARINE_BLUE || this == MARINE_RED
                || this == MILITIA;
    }

    /**
     * Whether this archetype's body carries a {@code SPRITE} component — the
     * classification that gates it at spawn ({@code UnitRosterService.allocate})
     * and that the render tier's {@code RenderAppearance.derive} defers to (this
     * is the single source of truth).
     *
     * <p>{@code SPRITE} is the body's facing frame, authored per tick by
     * {@code FacingSystem}, and after death the pose its corpse lies in. It says
     * nothing about which art draws the body: every such type is drawn from
     * modular art now, and the layered and rig composers read their own
     * components. What it separates is the bodies that face and fall from the
     * ones whose picture comes from their instance — {@link #TURRET},
     * {@link #DRONE_HUB_STRUCTURE}, {@link #BASED_AIRCRAFT} draw as whole rotated
     * sprites, {@link #DRONE} and {@link #GROUND_VEHICLE} in their own layers —
     * and from the invisible {@link #RANGE_TARGET}. Not {@code !isStatic()}: a
     * machine on the stocks stands still and still has a body to face and fall.
     */
    public boolean carriesSprite() {
        return carriesSprite;
    }

    /**
     * Whether this archetype is a convoy ground vehicle. The type tag sits
     * here beside {@link #isTurret} / {@link #isBasedAircraft} so the grid
     * walks classify a chassis the same way they classify every other body,
     * rather than each asking {@code ConvoyService} whether an id is one.
     */
    public boolean isGroundVehicle() { return this == GROUND_VEHICLE; }

    /**
     * The parts a round landing on this archetype is struck on, or null for one whole-body
     * contact. People have the {@linkplain BodyPlan#INFANTRY infantry plan} — head, arms, torso,
     * legs — and meet cover part by part; machines, structures and creatures have none and keep
     * the flat cover chance. See {@code body-part-cover.md}.
     */
    public BodyPlan bodyPlan() {
        return switch (this) {
            case MARINE, MARINE_BLUE, MARINE_RED, MILITIA,
                 CIVILIAN, ENGINEER, TECHNICIAN, SCIENTIST -> BodyPlan.INFANTRY;
            default -> null;
        };
    }

    /** Whether this archetype is struck on body parts at all: {@link #bodyPlan()} is not null. */
    public boolean hasBodyParts() {
        return bodyPlan() != null;
    }

    /**
     * Whether this infantry archetype has the modular body/head asset set (and,
     * for combatants, modular weapon layers).
     * These units keep a SPRITE component for facing and the corpse's pose.
     */
    public boolean drawnAsLayers() {
        return this == MARINE || this == MARINE_BLUE || this == MARINE_RED
                || this == MILITIA || this == CIVILIAN || this == ENGINEER
                || this == TECHNICIAN
                || this == SCIENTIST || this == ALIEN || isSwarm();
    }

    /** Whether the layered compositor should add carried/firing weapon art. */
    public boolean drawsLayeredWeapon() {
        return drawnAsLayers() && combatant && this != ALIEN && !isSwarm();
    }

    /**
     * Whether this archetype <em>is</em> a {@link com.dillon.starsectormarines.battle.mech.MechVariant}
     * — built on a named chassis, taking its dimensions, structure and
     * silhouette from one.
     *
     * <p>Deliberately not {@link #isMech}, which asks the narrower question of
     * whether the unit carries a loadout. A machine part-built in a gantry is
     * the chassis it is going to be and has to look like one; it simply has no
     * weapons on it yet. Folding the two together forces a choice between an
     * unfinished Hound that is drawn as a generic mech and one that arrives with
     * a working gun.
     */
    public boolean hasChassis() { return this == HEAVY_MECH || this == MACHINE_FRAME; }

    /** Whether this chassis has a modular true-overhead live composition. */
    public boolean drawnAsMechLayers() { return hasChassis(); }
}
