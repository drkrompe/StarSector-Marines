package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.command.objective.Objective;

import java.util.Random;

/**
 * One combatant in the battle simulation. Plain data — all behavior lives on
 * {@link BattleSimulation}. Fields are public for hot-path access from the
 * tick loop; the package keeps the surface narrow.
 *
 * <p><b>No registry back-pointer.</b> A {@code Entity} carries its {@link #entityId}
 * (its identity) plus immutable archetype + a handful of POJO fields, but it no
 * longer self-routes into the simulation's mutable state. Every mutable per-unit
 * column is reached <em>by id</em> through the {@code World} facade
 * ({@code world.hp(id)}, {@code world.cellX(id)}, …) — never through this
 * object. Every per-unit datum lives in the battle {@code EntityWorld}'s
 * components (HEALTH/POSITION, the combat timers, the target/burst/fallback
 * ids, movement — archetype tables persisting per their capability lifecycle),
 * reached by id.
 *
 * <p>Position is split: the logical cell (what pathfinding sees) is the world
 * POSITION component reached by id ({@code world.cellX(id)} /
 * {@code world.cellY(id)}; it persists alive→dead, so the corpse keeps its
 * cell), while the smooth-interpolated render position (cell units, fractional)
 * is the world's universal {@code RENDER_POSITION} component, read by id
 * ({@code world.renderX(id)} / {@code world.renderY(id)}); it survives release
 * (rides the death transmute) so a corpse still draws where it fell. The two
 * coincide when the unit is at rest or has just landed in a new cell.
 *
 * <p>The world's MOVEMENT component (reached by id) holds the whole movement
 * step — the flat {@code int[]} path, the {@code pathIdx} cursor, and
 * move-progress. {@link #advanceAlongPath(World, float)} rebuilds nothing
 * but lerps render position toward the next path cell as move-progress climbs
 * from 0 to 1; on arrival the logical cell advances, progress resets, and the
 * cursor steps forward.
 */
public class Entity {

    /** Sentinel for {@link #seedSquadId} when the unit isn't part of a squad — defenders, solo combatants, anyone not deboarded from a marine shuttle. Never a stored value once the unit is live: a non-member simply carries no {@code SQUAD} component (presence IS membership). */
    public static final int NO_SQUAD = -1;

    /**
     * Monotonic entity id assigned by {@link UnitRosterService}
     * on registration. {@code 0} means "not yet allocated" (matches the
     * registry's reserved sentinel); a non-zero value is stable for the
     * life of the unit and never recycled. This is the unit's identity — all
     * mutable state is keyed off it in the registry / component stores, reached
     * by id rather than through this object.
     */
    public long entityId = 0L;

    /**
     * Null-safe entity id of a {@link Entity} ref: {@code u.entityId}, or
     * {@code 0L} (the "no entity" sentinel) when {@code u == null}. The single
     * chokepoint for the "ref → id, null → 0L" convention every cross-reference
     * setter used to hide inside its own convenience overload — now that those
     * overloads are gone, callers write {@code world.setTargetId(self.entityId,
     * Entity.idOf(target))}. Survives the eventual {@code Entity} → {@code Entity}
     * rename unchanged.
     */
    public static long idOf(Entity u) {
        return (u == null) ? 0L : u.entityId;
    }

    public final String id;
    public final Faction faction;
    /** Archetype — drives sprite + base stat block. Set once at construction. */
    public final UnitType type;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> Squad membership key — a
     * positive squad id when this unit spawns as part of a fireteam, {@link #NO_SQUAD}
     * for a solo unit (defender / civilian / unsquadded turret).
     * {@link UnitRosterService#allocate} consumes it: a value {@code != NO_SQUAD} makes
     * the unit spawn with the {@code SQUAD} component (presence IS membership), seeded
     * with this key; a {@code NO_SQUAD} seed means no SQUAD component at all. The live
     * membership thereafter lives in the world component, reached by id via the
     * {@code SquadService} data owner ({@code sim.squad().hasSquad(id)} /
     * {@code squadId(id)}); the post-spawn join seam is {@code SquadService.assignSquad}.
     * Write-only construction input (the deboard / setup / reinforcement / drone-spawn
     * paths). Mirrors {@link #seedSecondaryWeapon}.
     */
    public int seedSquadId = NO_SQUAD;
    /**
     * Per-unit RNG owned by the thread processing this unit during UPDATE_UNITS.
     * Replaces sim-shared {@code BattleSimulation.rng} for parallel-decide-phase
     * call sites (weapon hit rolls, shot endpoint scatter, flee wander,
     * patrol jitter, drone swarm) so the fork-join dispatch has no Random
     * contention. Sim-global RNG keeps serving serial-phase callers
     * (death-pose pick in {@code DamageResolver.resolve}, map gen, setup). Seeded
     * with system time by default — we don't require bit-reproducible
     * battles.
     */
    public final Random rng = new Random();

    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b>
     * {@link UnitRosterService#allocate} copies these into the entity world's
     * {@code POSITION} component (migration step 3b) and the world is canonical
     * from then on, reached by id ({@code world.cellX(id)} /
     * {@code world.cellY(id)}). POSITION persists alive→dead — the corpse keeps
     * its cell via the death transmute's row-move (the demolition / wreck
     * handlers still read the death cell off the {@link DeathEvent} snapshot,
     * same value). Public for the same sibling-package seeding reason as
     * {@code seed*}.
     */
    public int seedCellX;
    public int seedCellY;

    /**
     * <b>Don't read directly.</b> Smooth render-position <em>pre-allocate seed
     * only</em>. {@link UnitRosterService#allocate} copies these into the entity
     * world's universal {@code RENDER_POSITION} component, which is canonical from
     * then on and survives release (it rides the death transmute) — there is no
     * post-release snapshot back to these fields. Read/write by id via
     * {@code world.renderX(id)} / {@code world.setRenderPos(id, x, y)}.
     */
    public float localRenderX;
    public float localRenderY;

    /**
     * Per-tick movement step. Every piece of per-unit movement state lives in the
     * entity world's components, read/written by id through the {@link World}
     * facade: the cell pair in POSITION, move-progress + the path reference + the
     * path cursor in MOVEMENT. The flat {@code int[]} path (cell {@code i} at
     * {@code (path[i*2], path[i*2+1])}, {@link GridPathfinder#EMPTY_PATH} when
     * nothing is scheduled) is fetched once and interrogated through
     * {@link Paths}; the cursor advances as the unit lands in each next cell.
     */
    public void advanceAlongPath(World world, float dt) {
        int[] path = world.path(entityId);
        int pathIdx = world.pathIdx(entityId);
        if (pathIdx >= Paths.cellCount(path)) return;
        int nextX = Paths.cellX(path, pathIdx);
        int nextY = Paths.cellY(path, pathIdx);
        int curX = world.cellX(entityId);
        int curY = world.cellY(entityId);
        float dx = nextX - curX;
        float dy = nextY - curY;
        float cellDist = (float) Math.sqrt(dx * dx + dy * dy);
        if (cellDist < 0.0001f) { world.setPathIdx(entityId, pathIdx + 1); return; }
        float mp = world.moveProgress(entityId) + (world.moveSpeed(entityId) * dt) / cellDist;
        if (mp >= 1f) {
            world.setCellPos(entityId, nextX, nextY);
            world.setRenderPos(entityId, nextX, nextY);
            world.setMoveProgress(entityId, 0f);
            world.setPathIdx(entityId, pathIdx + 1);
        } else {
            world.setMoveProgress(entityId, mp);
            world.setRenderPos(entityId, curX + dx * mp, curY + dy * mp);
        }
    }

    // Stats — initialized from UnitType, then mutable per-unit so captain traits
    // and mission modifiers can adjust an individual without changing the archetype.
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> Per-unit movement speed
     * (cells/sec). {@link UnitRosterService#allocate} copies it into the entity
     * world's {@code MOVEMENT} component (field
     * {@link BattleComponents#MOVEMENT_MOVE_SPEED}) for movers, canonical
     * thereafter; reached by id via {@code world.moveSpeed(id)}. A static
     * emplacement has no MOVEMENT, so its seed (0) is simply not consumed —
     * the seedAttack* / seedAttackCooldown shape. Write-only construction input.
     */
    public float seedMoveSpeed;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> Same shape as
     * {@link #seedMaxHp} and the cell pair: {@link UnitRosterService#allocate} copies
     * this into the entity world's {@code HEALTH} component (migration step 3)
     * and the world is canonical from then on. Reached by id
     * ({@code world.hp(id)} / {@code world.setHp(id, v)}); held-ref liveness
     * goes through {@code world.isAlive(id)} / {@code roster.isAliveById(id)}
     * — "has {@code HEALTH} with {@code hp > 0}", so a corpse (transmuted on the
     * death drain) reports dead.
     *
     * <p>Public so {@link UnitRosterService} can seed the slot at
     * allocate time. Write-only construction input: the ctor archetype seed and
     * the subclass overrides (see {@code seed*}).
     */
    public float seedHp;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The Group-S stat
     * columns (max HP + the three attack stats): {@link UnitRosterService#allocate}
     * copies these into the SoA arrays and the registry is canonical from then
     * on, and {@code release} does NOT snapshot them back. Reached post-allocate
     * by id through the registry/World ({@code world.maxHp(id)},
     * {@code world.attackDamage(id)}, …) since nothing reads them post-release —
     * the HUD that once read max HP post-release now snapshots the row by value at
     * {@code update()} (see {@code SquadDetailPanel}). These fields are write-only
     * <em>construction</em> input: the ctor archetype seed, the subclass overrides
     * ({@link com.dillon.starsectormarines.battle.drone.Drone} /
     * {@link com.dillon.starsectormarines.battle.drone.DroneHubUnit} /
     * {@link com.dillon.starsectormarines.battle.turret.MapTurret}), and the
     * shuttle/vehicle deboard loadout.
     */
    public float seedMaxHp;
    public float seedAttackDamage;
    public float seedAttackRange;
    public float seedAccuracy;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The optional secondary
     * weapon a unit deboards with (null = no secondary). {@link UnitRosterService#allocate}
     * consumes it: a non-null value makes the unit spawn with the
     * {@code SECONDARY_WEAPON} component (its capability IS that archetype
     * membership), seeded with this spec + {@link #seedSecondaryAmmo}. The live
     * secondary state thereafter lives in the world component, read via
     * {@code world.hasSecondaryWeapon}/{@code secondaryWeapon}/{@code secondaryAmmo}.
     */
    public MarineSecondary seedSecondaryWeapon;
    /** <b>Pre-allocate seed ONLY.</b> Starting rounds for {@link #seedSecondaryWeapon}; consumed by {@link UnitRosterService#allocate}. */
    public int seedSecondaryAmmo;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The optional kinematic
     * {@link AirBody} a flying unit spawns with (null = a ground unit, no body).
     * Only {@link com.dillon.starsectormarines.battle.drone.Drone} sets it today.
     * {@link UnitRosterService#allocate} consumes it: a non-null value makes the
     * unit spawn with the {@code KINEMATICS} component (its continuous-flight
     * capability IS that archetype membership), seeded with this body instance.
     * The live body thereafter lives in the world column, read by id via
     * {@code world.kinematics(id)} — the handle the sim steers each tick, then
     * syncs the grid cell + render position from. Mirrors {@link #seedSecondaryWeapon}.
     */
    public AirBody seedBody;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> How far this unit can see
     * (cells) — drives the fog-of-war shadowcast radius. {@link UnitRosterService#allocate}
     * copies it into the entity world's universal {@code VISION} component (field
     * {@link BattleComponents#VISION_RANGE}), canonical thereafter; reached by id via
     * the {@code VisionService} data owner ({@code sim.vision().visionRange(id)} /
     * {@code roster.vision()...}). Initialized from {@link UnitType#visionRange}; 0
     * falls back to the unit's attack-range stat. Write-only construction input (the
     * ctor archetype default + the {@code Drone} override). The planned night
     * multiplier mutates the live value via {@code VisionService.setVisionRange}.
     */
    public float seedVisionRange;
    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The per-unit primary
     * cooldown reset value (sim-seconds). {@link UnitRosterService#allocate} copies
     * it into the entity world's {@code COMBAT} component (field
     * {@link BattleComponents#COMBAT_ATTACK_COOLDOWN}) for combatants, canonical
     * thereafter; reached by id via {@code world.attackCooldown(id)}. Same shape as
     * the other COMBAT seed-stats ({@link #seedAttackDamage}/{@link #seedAttackRange}/
     * {@link #seedAccuracy}) — write-only construction input (ctor archetype default,
     * subclass overrides, deboard loadout).
     */
    public float seedAttackCooldown;

    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> Close-wall radius for
     * "air" line-of-sight, in cells. When &gt; 0, walls within this many cells of
     * this unit's position are treated as transparent for LoS checks involving this
     * unit — both as shooter and as target. Models flying mounts that hover above
     * building footprints: a drone can fire OUT of the building it's directly above,
     * and ground combatants can fire UP at the drone through the same close walls.
     * Both directions use the same radius so the rule is symmetric. 0 (default)
     * means standard grid LoS; only {@link Drone} sets this today, but
     * {@code Shuttle}-mounted turrets pass their own equivalent radius through
     * {@code TurretAim.State}.
     *
     * <p>{@link UnitRosterService#allocate} copies it into the entity world's
     * universal {@code VISION} component (field
     * {@link BattleComponents#VISION_AIR_LOS_RADIUS}), canonical thereafter; reached
     * by id via the {@code VisionService} data owner
     * ({@code sim.vision().airLosRadius(id)} / {@code roster.vision()...}). Write-only
     * construction input.
     */
    public float seedAirLosRadius = 0f;

    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The behavior-dispatch role a
     * unit spawns with — default {@link UnitRole#COMBATANT} (matches pre-role behavior),
     * overridden by the subclass ctors ({@code Drone}/{@code DroneHubUnit}/{@code MapTurret}),
     * the deboard loadout, and the setup/reinforcement spawn code before
     * {@link UnitRosterService#allocate} consumes it into the universal {@code ROLE}
     * component (the {@code UnitRole} ordinal). The live role thereafter lives in the
     * world component, reached by id via the {@code RoleService} data owner
     * ({@code sim.role().role(id)}); the runtime-reassignment seam (a kit pickup
     * promotes a marine to {@code KIT_RETRIEVER}/{@code PLANTER}, then reverts) is
     * {@code RoleService.setRole}. Write-only construction input; mirrors
     * {@link #seedSquadId}.
     */
    public UnitRole seedRole = UnitRole.COMBATANT;
    /** Objective this unit is acting on, when the role requires one (charge site for a planter, exfil zone for a VIP, position to camp for an objective camper). Null for plain combatants. */
    public Objective assignedObjective;
    /** {@link UnitRole#KIT_RETRIEVER} target — the dropped kit this unit is heading to recover. Cleared when picked up or when the drop is consumed by someone else. */
    public EquipmentDrop equipmentDropTarget;

    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The garrison idle-post cell
     * a {@link UnitRole#GARRISON} unit returns to and holds while its squad is UNAWARE.
     * {@link UnitRosterService#allocate} consumes it: a value {@code >= 0} makes the
     * unit spawn with the {@code HOME} component (presence IS "has a post"), seeded with
     * this cell; a {@code -1} seed (the default — roaming marines / patrols) means no
     * HOME component at all. The live post thereafter lives in the world component,
     * reached by id via the {@code HomeService} data owner ({@code sim.home().hasHome(id)}
     * / {@code homeCellX(id)}); the runtime reassignment seam ({@code SquadFallbackSystem}
     * redistributing posts on retreat) is {@code HomeService.setHome}. Write-only
     * construction input; mirrors {@link #seedSquadId}.
     */
    public int seedHomeCellX = -1;
    public int seedHomeCellY = -1;

    /**
     * <b>Don't read directly. Pre-allocate seed ONLY.</b> The primary handheld
     * weapon a unit spawns/deboards with (null = a legacy/non-marine unit with no
     * per-weapon profile — fire stats fall back to the {@link UnitType} attack-stat
     * defaults). {@link UnitRosterService#allocate} copies it into the entity
     * world's {@code COMBAT} component (field
     * {@link BattleComponents#COMBAT_PRIMARY_WEAPON}) for combatants, canonical
     * thereafter; reached by id via the {@code CombatService} data owner
     * ({@code sim.combat().primaryWeapon(id)} / {@code roster.combat()...}),
     * reassigned via {@code combat.setPrimaryWeapon}. Same shape as the other COMBAT seed-stats
     * ({@link #seedAttackDamage} etc.) — write-only construction input (the
     * {@code Drone} ctor default + the marine deboard loadout).
     */
    public MarineWeapon seedPrimaryWeapon;

    /**
     * Queue the burst follow-up rounds after the AI has already fired round 1.
     * No-op for single-shot weapons or combatants without a primary-weapon profile
     * (militia / aliens / turrets — those use their own burst paths or are
     * intrinsically single-shot). Centralizes the trigger pattern so every
     * fireShot callsite — stanced, moving, opportunity, garrison — gets bursts
     * consistently. Everything it touches is COMBAT — the primary-weapon profile
     * read and all three burst-column writes — so it consumes {@link CombatService}
     * directly (no {@code World} hop). Called at most once per shot per unit (not a
     * per-tick bulk path), so the by-id probes are fine.
     */
    public void beginBurst(CombatService combat, Entity target) {
        MarineWeapon weapon = combat.primaryWeapon(entityId);
        if (weapon == null || weapon.burstCount <= 1) return;
        combat.setBurstRemaining(entityId, weapon.burstCount - 1);
        combat.setBurstTimer(entityId, weapon.burstSpacing);
        combat.setBurstTargetId(entityId, Entity.idOf(target));
    }

    /** Random prone-pose index rolled on death. Drives which corpse frame the renderer picks from {@link UnitType#deadSpritePath} so a battlefield has pose variety rather than every body in the same slump. -1 sentinel = unit still alive. */
    public int deathPoseIdx = -1;

    public Entity(String id, Faction faction, UnitType type, int cellX, int cellY) {
        this.id = id;
        this.faction = faction;
        this.type = type;
        this.seedCellX = cellX;
        this.seedCellY = cellY;
        this.localRenderX = cellX;
        this.localRenderY = cellY;
        this.seedMoveSpeed = type.moveSpeed;
        // Pre-allocate seed; UnitRosterService.allocate will read these into the
        // SoA arrays. Use the field directly here because the registry-side
        // setters can't route yet (the unit isn't registered).
        this.seedHp = type.maxHp;
        this.seedMaxHp = type.maxHp;
        this.seedAttackDamage = type.attackDamage;
        this.seedAttackRange = type.attackRange;
        this.seedAccuracy = type.accuracy;
        this.seedVisionRange = type.visionRange > 0f ? type.visionRange : type.attackRange;
        this.seedAttackCooldown = type.attackCooldown;
    }

}
