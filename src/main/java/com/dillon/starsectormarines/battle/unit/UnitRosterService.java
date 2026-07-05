package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.appearance.LiveAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.VisionService;
import com.dillon.starsectormarines.battle.sim.SquadService;
import com.dillon.starsectormarines.battle.sim.RoleService;
import com.dillon.starsectormarines.battle.sim.HomeService;
import com.dillon.starsectormarines.battle.sim.HubStateService;
import com.dillon.starsectormarines.battle.sim.TaskService;
import com.dillon.starsectormarines.battle.sim.TurretStateService;
import com.dillon.starsectormarines.battle.sim.DroneStateService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Owns the unit + squad rosters, the entity world that backs them, and the
 * spawn-queue plumbing that {@code BattleSimulation} previously held inline.
 * Combines the state slices that all share a lifetime ("until the battle ends"):
 *
 * <ul>
 *   <li><b>The live entity roster</b> — a dense, live-only {@code Entity[]} keyed
 *       by monotonic {@code long} entity ids ({@link #spawn} adopts, {@link #release}
 *       swap-and-pops), plus the {@link EntityWorld} + {@link BattleComponents}
 *       that hold every per-entity component, plus the {@link World} by-id access
 *       facade over them. {@link #spawn} builds + adopts the unit from an
 *       {@link EntitySpec}; the death cascade in
 *       {@code com.dillon.starsectormarines.battle.combat.DamageResolver#resolve}
 *       releases via {@link #releaseFromRegistry}. Post-death state is carried by
 *       the corpse archetype in the {@link EntityWorld} (a corpse entity spawned
 *       per death) plus the surviving id-keyed components (CrashingComponent /
 *       MechLoadout / render position) — there is no retained live+dead list.</li>
 *   <li>Squad registry (keyed by {@link Squad#id}). Lookup via
 *       {@link #getSquad(int)} (synchronized to fence against same-tick
 *       {@link #mintSquad} from drone-hub spawns), iteration via
 *       {@link #getSquads()} (live values view; safe in serial phases).</li>
 *   <li>Parallel-safe spawn queue — {@link #queueSpawn(EntitySpec)} spawns serial
 *       callers immediately via {@link #spawn} and queues parallel callers into
 *       {@link #pendingSpawns}, drained by
 *       {@link #flushPendingSpawns()} in the APPLY_SPAWNS phase. Sibling
 *       pattern to {@link DamageService}'s three queues; the in-flight
 *       parallel-flag itself lives on {@code DamageService} so this service
 *       reads {@link DamageService#isParallel()} to make the same decision.</li>
 *   <li>Monotonic ID counters — {@code nextId} (entities), {@code nextSquadId}
 *       (squads), {@code deboardedMarineCount} (deboard ids). No recycling, no
 *       generation bits — a released entity id stays released forever (see
 *       {@code feedback_skip_generation_bits} memory).</li>
 * </ul>
 *
 * <h2>ID strategy</h2>
 * <p>Monotonic {@code long} sequence, {@code nextId} starts at 1 so {@code 0} is
 * the "no entity" sentinel a setup-discarded {@link Entity} carries
 * ({@link Entity#entityId} is 0 before allocation). A stale id resolves to
 * {@link #INVALID_INDEX} via {@link #indexOf(long)} / null via
 * {@link #getOrNull(long)}; {@link #isAliveById(long)} returns false once hp
 * hits zero. The dense {@code Entity[]} swap-and-pop keeps iteration over
 * {@code [0, liveCount())} cache-coherent with no dead entries (battle is
 * ephemeral and high-churn, so hard-delete beats the campaign tier's tombstoned
 * {@code LongIntMap}, which append-only because xstream save/load needs stable
 * indices — battles never save/load mid-fight).
 *
 * <h2>Thread safety</h2>
 * <p>Single-writer / multi-reader within a tick. {@link #spawn} and
 * {@link #release(long)} run in serial sim phases (spawn flush and the
 * post-UPDATE_UNITS death drain); the parallel UPDATE_UNITS dispatch reads
 * {@link Entity#entityId} fields and may call {@link #isLive(long)} /
 * {@link #indexOf(long)} but never mutates.
 *
 * <p>Sibling slice to {@link DamageService},
 * {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService}, et al.
 * Constructor-injected dependencies: {@link UnitSpatialIndex} (mirrored
 * from {@link #spawn} so off-tick / mid-tick deboard callers see the new
 * unit on the next AI query), {@link DamageService} (for the parallel-flag
 * read inside {@link #queueSpawn(EntitySpec)}).
 */
public final class UnitRosterService {

    /** Sentinel returned by {@link #indexOf(long)} when the id is unknown. Matches the {@code -1} convention used by {@code LongIntMap.NOT_FOUND}. */
    public static final int INVALID_INDEX = -1;

    private static final int INITIAL_CAPACITY = 64;

    private final UnitSpatialIndex unitIndex;
    /** Set post-construction by {@link #setDamageService} — the sim's wiring loop is
     *  {@code rosterService → damageResolver → damageService} so this field gets
     *  bound on the last step. Only read inside {@link #queueSpawn(EntitySpec)} for the
     *  parallel-flag check (drone-hub same-tick spawn), so a null read here
     *  would only fire if a spawn happens before sim construction completes,
     *  which the harness prevents. */
    private DamageService damageService;

    // ---- the dense, live-only entity roster ----

    private Entity[] dense = new Entity[INITIAL_CAPACITY];
    private int liveCount = 0;
    private long nextId = 1L;
    private final Long2IntOpenHashMap indexById = new Long2IntOpenHashMap();

    /**
     * The battle's archetype-table entity world + its game component
     * registrations + the by-id access facade. Owned here because {@link #adopt}
     * is the single spawn seam — minting the id and adopting it into the world stay
     * in one place, and every per-entity component lives keyed by id in the world
     * (immune to the dense {@code Entity[]} reshuffle on release). The world,
     * components, and facade are all transient — battles never save/load mid-fight.
     */
    private final EntityWorld entityWorld = new EntityWorld();
    private final BattleComponents components = new BattleComponents(entityWorld);
    // Per-component data-owner Services (the World decomposition). World delegates
    // its COMBAT/MOVEMENT accessors to these; consumers inject them via combat()/movement().
    private final IdentityService identityService = new IdentityService(entityWorld, components);
    private final CombatService combatService = new CombatService(entityWorld, components);
    private final MovementService movementService = new MovementService(entityWorld, components);
    private final VisionService visionService = new VisionService(entityWorld, components);
    private final SquadService squadService = new SquadService(entityWorld, components);
    private final RoleService roleService = new RoleService(entityWorld, components);
    private final HomeService homeService = new HomeService(entityWorld, components);
    private final HubStateService hubStateService = new HubStateService(entityWorld, components);
    private final TurretStateService turretStateService = new TurretStateService(entityWorld, components);
    private final DroneStateService droneStateService = new DroneStateService(entityWorld, components);
    private final TaskService taskService = new TaskService(entityWorld, components);
    // Data owner for the convoy-vehicle world entities (ground archetype). Takes `this`
    // because adoption must mint through the shared allocateVehicle; the ref is stored,
    // not dereferenced during construction (used at spawn time).
    private final ConvoyService convoyService = new ConvoyService(this);
    private final World world = new World(entityWorld, components, combatService, movementService);

    /**
     * Dense, primitive-keyed squad lookup. fastutil's {@link Int2ObjectOpenHashMap}
     * avoids the per-call {@code Integer} autobox that {@link #getSquad}
     * would do on a {@code HashMap<Integer, Squad>} — getSquad is hit
     * per-unit per-tick from the behavior dispatch.
     *
     * <p>Pre-sized to 256 so the rare {@link #mintSquad} call (drone hubs
     * spawning during the parallel UPDATE_UNITS dispatch) can't trigger a
     * rehash while other workers read {@code squads.get}. Real-world battles
     * run well under 256 squads.
     */
    private final Int2ObjectMap<Squad> squads = new Int2ObjectOpenHashMap<>(256);

    /**
     * Deferred spawns queued during the parallel UPDATE_UNITS dispatch (drone-hub
     * launches), drained in APPLY_SPAWNS via {@link #flushPendingSpawns()}. Each entry
     * holds only the {@link EntitySpec} — the id isn't minted until the serial flush
     * adopts it (no id is available at queue time). Serial deboard / setup paths spawn
     * inline via {@link #spawn} instead.
     */
    private final ArrayList<PendingSpawn> pendingSpawns = new ArrayList<>();

    /** A queued deferred spawn: its construction spec, adopted (id minted) by {@link #flushPendingSpawns()}. */
    private record PendingSpawn(EntitySpec spec) {}

    /** Next squad id to assign on shuttle deboard. Monotonically increasing across the battle's lifetime. */
    private int nextSquadId = 0;
    /** Counter for IDs of marines deboarded from shuttles. Bumped via {@link #nextMarineId()} when {@code AirSystem} deboards. Format: "m0", "m1", ... matches the pre-shuttle setup convention. */
    private int deboardedMarineCount = 0;

    public UnitRosterService(UnitSpatialIndex unitIndex, DamageService damageService) {
        this.unitIndex = unitIndex;
        this.damageService = damageService;
        // Make missing-key lookups return INVALID_INDEX; the remove path relies on
        // this too (Long2IntOpenHashMap.remove returns the default when the key is
        // absent), so a duplicate release is a no-op without the caller checking.
        indexById.defaultReturnValue(INVALID_INDEX);
    }

    /** Bind the damage service after construction — used by the sim ctor to break
     *  the {@code rosterService ↔ damageService} circular dependency. Only legal
     *  once during sim setup. */
    public void setDamageService(DamageService damageService) {
        this.damageService = damageService;
    }

    /**
     * Exposes the raw squads map for {@code BattleSimulation}'s alias-field
     * init so the sim's 40+ internal {@code squads.get / squads.values}
     * reads keep their direct-access perf. External callers should go
     * through {@link #getSquad(int)} or {@link #getSquads()} — both keep
     * the locking + values-view contracts intact.
     */
    public Int2ObjectMap<Squad> getSquadsMap() { return squads; }

    /**
     * Spawns a ground-roster unit from an {@link EntitySpec}: {@link #adopt}s it (mints
     * its id + builds the handle + seeds every component column from the spec), mirrors
     * it into the spatial index so callers outside the tick loop (test fixtures,
     * AirSystem mid-tick deboard) see it on the next AI query, and returns the minted
     * id. The single immediate-spawn seam; {@code BattleSimulation.spawn} layers
     * fog-contributor registration on top. Serial phases only.
     */
    public long spawn(EntitySpec spec) {
        long id = adopt(spec);
        unitIndex.add(this, id);
        return id;
    }

    /**
     * Parallel-safe deferred spawn for callers inside the UPDATE_UNITS dispatch
     * (drone-hub launches). Serial callers spawn immediately via {@link #spawn} (a real
     * id); parallel callers queue only the {@link EntitySpec} into {@link #pendingSpawns}
     * and get {@code 0L} back — the id is minted when {@link #flushPendingSpawns()} adopts
     * the spec in APPLY_SPAWNS (there is no id to hand back at queue time). The parallel
     * flag lives on {@link DamageService} so the two queue patterns share one source of
     * truth.
     *
     * <p>Within-tick drift: a queued unit isn't in the live roster until the drain.
     * {@code DroneSpawner.isCellOccupied} iterates the live roster, so if two hubs
     * spawn the same tick the second won't see the first and could pick the same cell.
     * Hub intervals make same-tick double-spawn rare; the next tick's REBUILD_OCCUPANCY
     * restores the picture.
     */
    public long queueSpawn(EntitySpec spec) {
        if (!damageService.isParallel()) return spawn(spec);
        synchronized (pendingSpawns) {
            pendingSpawns.add(new PendingSpawn(spec));
        }
        return 0L;
    }

    /**
     * Drops the registry entry for {@code entityId} via swap-and-pop.
     * Called by the death cascade in
     * {@code com.dillon.starsectormarines.battle.combat.DamageResolver#resolve}.
     * Post-death readers source the corpse from the entity world (keyed by
     * entity id, surviving release), so the entity stays observable afterward.
     */
    public void releaseFromRegistry(long entityId) {
        release(entityId);
    }

    // ---- entity world + access facade ----

    /** The battle's archetype-table entity world — the storage every per-entity component lives in. */
    public EntityWorld entityWorld() { return entityWorld; }

    /** Game component-type registrations + shared queries for {@link #entityWorld()}. */
    public BattleComponents components() { return components; }

    /** The by-id entity-access facade over {@link #entityWorld()}. */
    public World world() { return world; }

    /** Data owner for the COMBAT component — inject into consumers that read/mutate combat state. */
    public CombatService combat() { return combatService; }

    /** Data owner for the MOVEMENT component — inject into consumers that read/mutate movement state. */
    public MovementService movement() { return movementService; }

    /** Data owner for the VISION component (sight stats) — inject into consumers that read/mutate visionRange/airLosRadius. */
    public VisionService vision() { return visionService; }

    /** Data owner for the SQUAD component (membership) — inject into consumers that gate on hasSquad / read squadId. Distinct from {@link #getSquad(int)} (the squad-object registry). */
    public SquadService squad() { return squadService; }

    /** Data owner for the ROLE component (behavior-dispatch role) — inject into consumers that read/reassign a unit's role. */
    public RoleService role() { return roleService; }

    /** Data owner for the HOME component (garrison idle-post) — inject into consumers that gate on hasHome / read the post cell. */
    public HomeService home() { return homeService; }

    /** Data owner for the HUB_STATE component (drone-hub spawn cadence) — inject into consumers that gate on isHub / read the spawn/launch state. */
    public HubStateService hubState() { return hubStateService; }

    /** Data owner for the TURRET_STATE component (turret facing/recoil/burst) — inject into consumers that gate on isTurret / read the aim/burst state. */
    public TurretStateService turretState() { return turretStateService; }

    /** Data owner for the DRONE_STATE component (drone patrol/pursuit vectors) — inject into consumers that gate on isDrone / read the patrol/pursuit state. */
    public DroneStateService droneState() { return droneStateService; }

    /** Data owner for the TASK component (objective/kit assignment) — inject into consumers that read/reassign a unit's task. */
    public TaskService task() { return taskService; }

    /** Data owner for the IDENTITY component (type/faction/name) — {@code identity().name(id)} is the greppable-name read for debug dumps / logs / tests. */
    public IdentityService identity() { return identityService; }

    /** Data owner for convoy-vehicle world entities (ground archetype) — {@code GroundSystem} adopts / reaps vehicles and reads their identity + pose by id through it. */
    public ConvoyService convoy() { return convoyService; }

    // ---- allocate / release (the spawn + death seam) ----

    /**
     * Builds the {@link Entity} handle for a spec and adopts it into the roster: mints
     * its {@link Entity#entityId}, drops it in the next dense slot, creates its world
     * entity with the archetype the spec's capabilities imply, and seeds every component
     * column from the spec. Returns the minted id; grows the backing array by doubling on
     * overflow. The single construct-and-column-seed seam — {@link #spawn} and
     * {@link #flushPendingSpawns} both route here.
     */
    private long adopt(EntitySpec spec) {
        if (liveCount == dense.length) {
            dense = Arrays.copyOf(dense, dense.length * 2);
        }
        long id = nextId++;
        Entity e = new Entity(spec.faction, spec.type);
        e.entityId = id;
        dense[liveCount] = e;
        // Create the minted id's world entity. Every live unit is at least
        // {IDENTITY, POSITION, RENDER_POSITION, HEALTH, VISION, ROLE} (VISION + ROLE
        // universal — sight stats + the behavior-dispatch role; both removed on
        // death); on top of that:
        //   - COMBAT iff the unit is a combatant. A non-combatant (civilian /
        //     engineer / scientist; UnitType.combatant == false) never fires and is
        //     never targeted, so "has COMBAT" defines a combatant — presence IS the
        //     capability (like MOVEMENT/AI_STATE; no inert attack/cooldown columns on
        //     a fleeing civilian). Readers that walk the whole roster must gate on
        //     spec.type.combatant before any COMBAT read (the accessors are fail-loud).
        //   - MOVEMENT + AI_STATE iff the unit is mobile. A static emplacement (a
        //     turret or drone hub; UnitType.isStatic) neither paths nor decides, so
        //     "has MOVEMENT" defines a mover and "has AI_STATE" a thinker — presence
        //     IS the capability (like SECONDARY_WEAPON; no inert path/cadence columns
        //     on a turret).
        //   - SECONDARY_WEAPON iff the unit carries one.
        //   - SPRITE iff the type is sheet-drawn (UnitType.drawnAsSheet) —
        //     presence IS "draws as a sheet frame", authored per-tick by
        //     FacingSystem (battle.appearance); seeded to the south-idle frame
        //     so a unit spawned between the facing pass and render still draws
        //     sanely. Kept on the row through the corpse transmute (the death
        //     write overwrites the index with the death pose).
        //   - HUB_STATE iff the unit is a drone hub (UnitType.isDroneHub()) —
        //     presence IS "is a live drone hub", the classification gate that
        //     replaced the old instanceof-subclass checks.
        //   - TURRET_STATE iff the unit is a turret (UnitType.isTurret()) —
        //     presence IS "is a live turret", the same classification-gate
        //     pattern replacing the old instanceof-subclass checks.
        //   - DRONE_STATE iff the unit is a drone (UnitType.isDrone()) —
        //     presence IS "is a live drone", the same classification-gate
        //     pattern replacing the old instanceof-subclass checks.
        // Identity is written once here and persists alive→dead (the corpse
        // transmute's row-move carries it — as does the cell, which IS the death cell
        // by the time the corpse forms); Position and Health seed from the
        // write-only seed* fields and are canonical thereafter — "has HEALTH with
        // hp > 0" is the liveness definition (isAliveById). Combat seeds the same way
        // when present. The corpse transmute removes HEALTH and any COMBAT /
        // MOVEMENT / AI_STATE / SECONDARY_WEAPON.
        boolean mobile = !spec.type.isStatic();
        boolean combatant = spec.type.combatant;
        boolean hasSecondary = spec.secondaryWeapon != null;
        // SPRITE iff sheet-drawn (UnitType.drawnAsSheet) — see the bullet above.
        boolean sheetDrawn = spec.type.drawnAsSheet();
        // KINEMATICS iff the unit carries a continuous-flight body (a drone today).
        // Optional like SECONDARY_WEAPON — presence IS the "is a flier" capability;
        // a ground unit has none. It is kept OFF the corpse-remove mask
        // (DeadBodySystem), so a dead drone's body rides the death transmute for the
        // crash handler to read before it detaches it.
        boolean hasBody = spec.body != null;
        // SQUAD iff the unit spawns in a squad. Presence IS membership — a solo unit
        // (NO_SQUAD seed) carries no SQUAD component, so the old sentinel never lands
        // in the world (the SECONDARY_WEAPON precedent). Live-only: removed on the
        // corpse transmute (the death cascade reads membership pre-transmute).
        boolean inSquad = spec.squadId != Entity.NO_SQUAD;
        // HOME iff the unit spawns with a garrison post (seedHomeCell >= 0). Presence IS
        // "has a post" — a roaming marine / patrol (the -1 seed) carries no HOME, so the
        // old sentinel never lands in the world (the SQUAD precedent). Live-only.
        boolean hasHome = spec.homeCellX >= 0;
        // TASK iff the unit deboards already acting on an objective (a planter/VIP/camper
        // loadout). The KIT_RETRIEVER kit target is purely runtime, so it never seeds
        // TASK at spawn — a plain combatant recruited as a retriever gains TASK then (a
        // serial addComponent). Optional; live-only.
        boolean hasTask = spec.assignedObjective != null;
        // HUB_STATE iff the unit is a drone hub (UnitType.isDroneHub()). Presence IS
        // "is a live drone hub" — the DroneHub factory config/entity has no other
        // marker, so the archetype membership itself is the classification gate that
        // replaced the old instanceof-subclass checks.
        boolean isHub = spec.type.isDroneHub();
        // TURRET_STATE iff the unit is a turret (UnitType.isTurret()). Presence IS
        // "is a live turret" — the MapTurret factory config/entity has no other
        // marker, so the archetype membership itself is the classification gate that
        // replaced the old instanceof-subclass checks.
        boolean isTurret = spec.type.isTurret();
        // DRONE_STATE iff the unit is a drone (UnitType.isDrone()). Presence IS
        // "is a live drone" — the Drone factory config/entity has no other
        // marker, so the archetype membership itself is the classification gate
        // that replaced the old instanceof-subclass checks.
        boolean isDrone = spec.type.isDrone();
        ComponentType[] archetype = new ComponentType[
                6 + (combatant ? 1 : 0) + (mobile ? 2 : 0) + (hasSecondary ? 1 : 0)
                  + (hasBody ? 1 : 0) + (inSquad ? 1 : 0) + (hasHome ? 1 : 0) + (hasTask ? 1 : 0)
                  + (sheetDrawn ? 1 : 0) + (isHub ? 1 : 0) + (isTurret ? 1 : 0) + (isDrone ? 1 : 0)];
        int c = 0;
        archetype[c++] = components.IDENTITY;
        archetype[c++] = components.POSITION;
        archetype[c++] = components.RENDER_POSITION;
        archetype[c++] = components.HEALTH;
        archetype[c++] = components.VISION;
        archetype[c++] = components.ROLE;
        if (combatant) archetype[c++] = components.COMBAT;
        if (mobile) {
            archetype[c++] = components.MOVEMENT;
            archetype[c++] = components.AI_STATE;
        }
        if (hasSecondary) archetype[c++] = components.SECONDARY_WEAPON;
        if (hasBody) archetype[c++] = components.KINEMATICS;
        if (inSquad) archetype[c++] = components.SQUAD;
        if (hasHome) archetype[c++] = components.HOME;
        if (hasTask) archetype[c++] = components.TASK;
        if (sheetDrawn) archetype[c++] = components.SPRITE;
        if (isHub) archetype[c++] = components.HUB_STATE;
        if (isTurret) archetype[c++] = components.TURRET_STATE;
        if (isDrone) archetype[c++] = components.DRONE_STATE;
        entityWorld.createEntity(id, archetype);
        // SHEET/FLIP_V stay 0 from the zero-init append; INDEX is seeded to the
        // south-idle frame so a unit spawned between this tick's FacingSystem
        // pass and render still draws sanely instead of frame 0.
        if (sheetDrawn) {
            entityWorld.setInt(id, components.SPRITE, BattleComponents.SPRITE_INDEX, LiveAppearance.SOUTH_IDLE_FRAME);
        }
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_TYPE, spec.type);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_FACTION, spec.faction);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_NAME, spec.name);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_X, spec.cellX);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_Y, spec.cellY);
        entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_HP, spec.hp);
        entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_MAX_HP, spec.maxHp);
        // VISION is universal — sight stats seeded from the unit's write-only seeds
        // (a ground unit's airLosRadius just seeds to 0). Removed on death.
        entityWorld.setFloat(id, components.VISION, BattleComponents.VISION_RANGE, spec.visionRange);
        entityWorld.setFloat(id, components.VISION, BattleComponents.VISION_AIR_LOS_RADIUS, spec.airLosRadius);
        // ROLE is universal — the behavior-dispatch role seeded from the unit's
        // write-only seedRole ordinal. Mutable on a live unit thereafter via
        // RoleService.setRole (kit-pickup promotion/revert); removed on death.
        entityWorld.setInt(id, components.ROLE, BattleComponents.ROLE_ORDINAL, spec.role.ordinal());
        // Seed the COMBAT stat columns from the unit's pre-allocation seed* fields
        // (only for combatants — a non-combatant has no COMBAT component); the
        // mid-combat COMBAT scalars start at zero (a fresh world row appends
        // zero-initialised — no slot-reuse reset needed).
        if (combatant) {
            entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_DAMAGE, spec.attackDamage);
            entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE, spec.attackRange);
            entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ACCURACY, spec.accuracy);
            entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_COOLDOWN, spec.attackCooldown);
            // primaryWeapon is the OBJECT stat — null for a combatant with no
            // per-weapon profile (militia/aliens/turrets); a fresh row appends null,
            // so this seed is what makes a marine's deboard loadout canonical.
            entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_PRIMARY_WEAPON, spec.primaryWeapon);
        }
        if (hasSecondary) {
            entityWorld.setObject(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC, spec.secondaryWeapon);
            entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO, spec.secondaryAmmo);
        }
        // Seed the flier's KINEMATICS body — the SAME AirBody instance the unit's
        // ctor created and positioned, now world-resident and aliased by the unit's
        // steering reads (zero-churn, the shuttle-KINEMATICS precedent).
        if (hasBody) {
            entityWorld.setObject(id, components.KINEMATICS, BattleComponents.KINEMATICS_BODY, spec.body);
        }
        // Seed the squad-membership key (the SQUAD component was attached above iff
        // the unit spawns in a squad).
        if (inSquad) {
            entityWorld.setInt(id, components.SQUAD, BattleComponents.SQUAD_ID, spec.squadId);
        }
        // Seed the garrison post (the HOME component was attached above iff the unit
        // spawns with one).
        if (hasHome) {
            entityWorld.setInt(id, components.HOME, BattleComponents.HOME_CELL_X, spec.homeCellX);
            entityWorld.setInt(id, components.HOME, BattleComponents.HOME_CELL_Y, spec.homeCellY);
        }
        // Seed the assigned objective (the TASK component was attached above iff the unit
        // deboards with one); the kit-target field appends null.
        if (hasTask) {
            entityWorld.setObject(id, components.TASK, BattleComponents.TASK_ASSIGNED_OBJECTIVE, spec.assignedObjective);
        }
        // Seed the hub's live state (the HUB_STATE component was attached above iff
        // the unit is a drone hub). spawnCooldown seeds to the hub's initial launch
        // delay from the write-only seed; droneSquadId seeds to NO_SQUAD (-1) because
        // 0 is a valid squad id, so a fresh-row zero can't mean "no squad yet".
        if (isHub) {
            entityWorld.setFloat(id, components.HUB_STATE, BattleComponents.HUB_STATE_SPAWN_COOLDOWN, spec.hubSpawnCooldown);
            entityWorld.setInt(id, components.HUB_STATE, BattleComponents.HUB_STATE_DRONE_SQUAD_ID, Entity.NO_SQUAD);
        }
        // Seed the turret's live state (the TURRET_STATE component was attached above
        // iff the unit is a turret). kind seeds from the write-only seed; recoilTimer
        // seeds to 1f — well past the renderer's recoil window — so an unfired turret
        // doesn't read as mid-recoil at sim start (a fresh row's zero-init would).
        // facingDegrees/burst* ride the zero-init.
        if (isTurret) {
            entityWorld.setObject(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_KIND, spec.turretKind);
            entityWorld.setFloat(id, components.TURRET_STATE, BattleComponents.TURRET_STATE_RECOIL_TIMER, 1f);
        }
        // Seed the drone's live state (the DRONE_STATE component was attached above
        // iff the unit is a drone). Patrol/pursuit goals default to NaN ("no
        // waypoint yet") — a fresh row appends 0.0f, not NaN, and
        // ensureSectorWaypoint gates on isNaN, so the sentinel must be seeded
        // explicitly (the AI_STATE -1/-1 cell precedent). homeHubId seeds from
        // the write-only seed; pursuitTimer rides the zero-init (0f = latch
        // expired).
        if (isDrone) {
            entityWorld.setFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PATROL_GOAL_X, Float.NaN);
            entityWorld.setFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PATROL_GOAL_Y, Float.NaN);
            entityWorld.setFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PURSUIT_GOAL_X, Float.NaN);
            entityWorld.setFloat(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_PURSUIT_GOAL_Y, Float.NaN);
            entityWorld.setLong(id, components.DRONE_STATE, BattleComponents.DRONE_STATE_HOME_HUB_ID, spec.homeHubId);
        }
        // Seed the non-zero defaults of the mobile-only components: AI_STATE's
        // fall-back cell is -1/-1 ("no cached cell"; readers treat a non-negative
        // cell as live), and MOVEMENT's path is an OBJECT column that appends null
        // while every path reader dereferences it. The remaining AI_STATE/MOVEMENT
        // scalars start at zero by the row append.
        if (mobile) {
            entityWorld.setInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_X, -1);
            entityWorld.setInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_Y, -1);
            entityWorld.setObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH, GridPathfinder.EMPTY_PATH);
            entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_SPEED, spec.moveSpeed);
        }
        // Seed the smooth render position from the unit's pre-allocation seed.
        // RENDER_POSITION is universal and kept OFF the corpse-remove mask, so it
        // rides the death transmute — a released corpse still resolves its
        // death-pose location with no post-release snapshot.
        entityWorld.setFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_X, spec.cellX);
        entityWorld.setFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_Y, spec.cellY);
        indexById.put(id, liveCount);
        liveCount++;
        return id;
    }

    /**
     * Mints a world entity id for an AIR craft (shuttle / planned fighter) and
     * adopts it into the entity world with the given {@code archetype}, <em>without</em>
     * inserting it into the dense ground roster. Air craft are world-resident only —
     * they never appear in {@link #denseArray()} / {@link #liveCount()} walks, the
     * spatial index, or occupancy, so every grid system skips them for free (their
     * archetype carries no POSITION/MOVEMENT/AI_STATE). The caller then seeds the
     * archetype's OBJECT columns (identity / kinematics / mission) via the {@link World}
     * setters.
     *
     * <p>Crucially this shares the single {@link #nextId} authority with
     * {@link #allocate}, so a shuttle id can never collide with a ground id — the
     * dual-mint trap the air-into-world migration closes (self-minting via
     * {@code EntityWorld.createEntity(comps)} would bump the world's counter but not
     * {@code nextId}, letting a later ground allocate reuse a shuttle's id). Serial-only
     * (the air spawn path runs in serial phases).
     */
    public long allocateAir(ComponentType[] archetype) {
        long id = nextId++;
        entityWorld.createEntity(id, archetype);
        return id;
    }

    /**
     * Mints a world entity id for a ground CONVOY vehicle (truck / APC) and adopts
     * it into the entity world with the given {@code archetype}, <em>without</em>
     * inserting it into the dense ground roster — the ground twin of
     * {@link #allocateAir}. Convoy vehicles are world-resident only: they never
     * appear in {@link #denseArray()} / {@link #liveCount()} walks, the spatial
     * index, occupancy, or fog, so every grid system skips them for free (their
     * archetype carries no POSITION/HEALTH/COMBAT/MOVEMENT/AI_STATE). The caller then
     * seeds the archetype's OBJECT columns (ground identity / kinematics / mission)
     * through the {@code ConvoyService} data owner. Vehicle liveness is
     * {@code mission.state == GONE}, not {@code HEALTH} — vehicles have no hp.
     *
     * <p>Shares the single {@link #nextId} authority with {@link #allocate} and
     * {@link #allocateAir}, so a vehicle id can never collide with a ground or air
     * id — the dual-mint trap (self-minting via {@code EntityWorld.createEntity(comps)}
     * would bump the world's counter but not {@code nextId}, letting a later ground
     * allocate reuse a vehicle's id). Serial-only (the convoy tick runs in the serial
     * GROUND_SYSTEM phase). Part of the convoy-{@code Vehicle}-into-world epic
     * ({@code roadmap/ecs-migration/stories/vehicle-into-world.md}).
     */
    public long allocateVehicle(ComponentType[] archetype) {
        long id = nextId++;
        entityWorld.createEntity(id, archetype);
        return id;
    }

    /**
     * Hard-removes the entity with id {@code id} via swap-and-pop. The tail
     * entity moves into the freed slot and its id→index mapping updates. No-op if
     * {@code id} is unknown (duplicate-release safety) or {@code 0L} (the
     * "never allocated" sentinel a setup-discarded {@link Entity} carries).
     *
     * <p>No per-unit state is moved by the swap — every column lives in the entity
     * world keyed by id, immune to the dense reshuffle: the cell + Group-S stats
     * persist (post-release readers read the death cell off the DeathEvent
     * snapshot; render position is the universal RENDER_POSITION component kept off
     * the corpse-remove mask), and hp / combat / movement / ai-state / secondary
     * stay under the entity's id until the death drain transmutes it to the corpse
     * archetype. So the swap only moves the dense {@code Entity[]} slot + fixes the
     * tail's id↔slot mapping.
     */
    public void release(long id) {
        if (id == 0L) return;
        int idx = indexById.remove(id);
        if (idx == INVALID_INDEX) return;
        int last = liveCount - 1;
        if (idx != last) {
            Entity tail = dense[last];
            dense[idx] = tail;
            indexById.put(tail.entityId, idx);
        }
        dense[last] = null;
        liveCount--;
    }

    /** Returns the current dense index for {@code id}, or {@link #INVALID_INDEX} if released or never allocated. */
    public int indexOf(long id) {
        return indexById.get(id);
    }

    /** True iff {@code id} is currently in the roster (allocated and not yet released). */
    public boolean isLive(long id) {
        return indexById.containsKey(id);
    }

    /**
     * Liveness for a held entity id — has a {@code HEALTH} component with
     * {@code hp > 0}. Backs {@code World.isAlive(id)} and every held-ref liveness
     * check. Purely world-side: a corpse fails it by <em>lacking</em> {@code HEALTH}
     * (the death transmute removed it), a just-killed-not-yet-transmuted id fails on
     * {@code hp <= 0} (every release path zeroes hp first), and a never-allocated /
     * {@code 0L} id misses the world entirely. One tolerant-read probe.
     */
    public boolean isAliveById(long id) {
        return entityWorld.getFloat(id, components.HEALTH, BattleComponents.HEALTH_HP, 0f) > 0f;
    }

    /**
     * Returns the {@link Entity} for {@code id}, or {@code null} if the id is
     * unknown (never allocated) or released. The lazy-validity replacement for the
     * old {@code target != null && target.isAlive()} idiom — a dangling {@code long}
     * resolves cleanly to null. {@code id == 0L} (the "no entity" sentinel) returns
     * null without a map probe — the fast path every "do I have a target" check hits.
     */
    public Entity getOrNull(long id) {
        if (id == 0L) return null;
        int idx = indexById.get(id);
        if (idx == INVALID_INDEX) return null;
        return dense[idx];
    }

    /** Returns the unit at dense slot {@code idx}. Callers iterate over {@code [0, liveCount())}; no bounds check. */
    public Entity get(int idx) {
        return dense[idx];
    }

    public int liveCount() {
        return liveCount;
    }

    /**
     * Direct access to the backing array. Indices {@code [0, liveCount())} are
     * live; slots beyond that are null. Exposed so hot loops can avoid the
     * per-iteration accessor hop.
     *
     * <p><b>Do not cache across allocations.</b> The backing array is replaced by
     * {@link #allocate(Entity)} when {@link #liveCount()} hits {@code dense.length};
     * a cached reference becomes a stale view of an abandoned array. Safe to alias
     * for the duration of a single tick phase that doesn't allocate (the parallel
     * UPDATE_UNITS dispatch — spawns are queued and flushed in a separate serial
     * phase, so the array is stable across the dispatch).
     */
    public Entity[] denseArray() {
        return dense;
    }

    /**
     * Drains {@link #pendingSpawns} in FIFO order, adopting each queued spec into the
     * roster + entity world (minting its id) and mirroring it into the spatial index.
     * Runs in APPLY_SPAWNS, between APPLY_OCCUPANCY and INFANTRY_TICK. Returns the minted
     * ids in drain order for the caller's post-adopt bookkeeping (fog-contributor
     * registration); empty when nothing was queued.
     */
    public LongList flushPendingSpawns() {
        if (pendingSpawns.isEmpty()) return LongLists.emptyList();
        LongList spawned = new LongArrayList(pendingSpawns.size());
        for (int i = 0, n = pendingSpawns.size(); i < n; i++) {
            long id = adopt(pendingSpawns.get(i).spec());
            unitIndex.add(this, id);
            spawned.add(id);
        }
        pendingSpawns.clear();
        return spawned;
    }

    /**
     * Returns the squad with the given id, or {@code null} if
     * {@code id == Entity.NO_SQUAD} or the squad was never registered.
     * Synchronized on the same monitor as {@link #mintSquad}'s put — with
     * the pre-sized {@link #squads} (no rehash) the put is a single-slot
     * store, but without happens-before a concurrent get can still see
     * partial / missing entries. Drone-hub same-tick spawn is the only
     * mid-dispatch caller of mintSquad; everyone else mints at setup.
     */
    public Squad getSquad(int id) {
        if (id == Entity.NO_SQUAD) return null;
        synchronized (squads) {
            return squads.get(id);
        }
    }

    /** All squads currently registered. Used by the per-tick alert update; behaviors should read individual squads via {@link #getSquad(int)} keyed off {@link Entity#squadId}. */
    public Collection<Squad> getSquads() { return squads.values(); }

    /**
     * Mints a new squad with the given faction + leader, returns its id.
     * Synchronized because {@code DroneSpawner.tryLaunch} can call this
     * from the parallel UPDATE_UNITS dispatch when multiple hubs spawn
     * the same tick. The squads map is pre-sized to avoid rehash, so
     * concurrent get() callers see consistent state.
     */
    public int mintSquad(Faction faction, long leaderId) {
        synchronized (squads) {
            Squad squad = new Squad(nextSquadId++, faction);
            // leaderId may be 0L (some callers mint an empty squad first, then
            // attach members) — 0L is the no-leader sentinel.
            squad.leaderId = leaderId;
            // Denormalize squad type from the leader (squads are homogeneous) so
            // isMechSquad() needs no leader deref and survives leader death. Read
            // off the immutable IDENTITY archetype rather than the loadout
            // component because the component is attached after the unit is
            // allocated (post-mint), so the store isn't populated yet here; the
            // leader (an already-live unit) has its IDENTITY_TYPE seeded at adopt.
            squad.mechSquad = leaderId != 0L && identityService.type(leaderId).isMech();
            squads.put(squad.id, squad);
            return squad.id;
        }
    }

    /**
     * Mints a new (as-yet-leaderless) squad for {@code faction}, denormalizing
     * {@code mechSquad} from {@code type}; returns its id. The pre-spawn mint the
     * spec-based construction path uses: the caller holds an {@link EntitySpec},
     * not a live {@link Entity}, so the squad is born with {@code leaderId == 0}
     * and leadership (if any) is set after the members spawn. Sibling to
     * {@link #mintSquad(Faction, long)} (mint led by an existing unit);
     * synchronized for the same drone-hub same-tick reason.
     */
    public int mintSquad(Faction faction, UnitType type) {
        synchronized (squads) {
            Squad squad = new Squad(nextSquadId++, faction);
            squad.mechSquad = type.isMech();
            squads.put(squad.id, squad);
            return squad.id;
        }
    }

    /** Bumps the deboarded-marine counter and returns the next id in {@code "m<n>"} format. */
    public String nextMarineId() {
        return "m" + deboardedMarineCount++;
    }
}
