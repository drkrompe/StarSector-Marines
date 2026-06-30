package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.VisionService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

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
 *       by monotonic {@code long} entity ids ({@link #allocate}/{@link #release}
 *       with swap-and-pop), plus the {@link EntityWorld} + {@link BattleComponents}
 *       that hold every per-entity component, plus the {@link World} by-id access
 *       facade over them. {@link #addUnit} allocates; the death cascade in
 *       {@code com.dillon.starsectormarines.battle.combat.DamageResolver#resolve}
 *       releases via {@link #releaseFromRegistry}. Post-death state is carried by
 *       the corpse archetype in the {@link EntityWorld} (a corpse entity spawned
 *       per death) plus the surviving id-keyed components (CrashingComponent /
 *       MechLoadout / render position) — there is no retained live+dead list.</li>
 *   <li>Squad registry (keyed by {@link Squad#id}). Lookup via
 *       {@link #getSquad(int)} (synchronized to fence against same-tick
 *       {@link #mintSquad} from drone-hub spawns), iteration via
 *       {@link #getSquads()} (live values view; safe in serial phases).</li>
 *   <li>Parallel-safe spawn queue — {@link #queueSpawn(Entity)} routes serial
 *       callers through the inline {@link #addUnit(Entity)} path and parallel
 *       callers through {@link #pendingSpawns}, drained by
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
 * <p>Single-writer / multi-reader within a tick. {@link #allocate(Entity)} and
 * {@link #release(long)} run in serial sim phases (spawn flush and the
 * post-UPDATE_UNITS death drain); the parallel UPDATE_UNITS dispatch reads
 * {@link Entity#entityId} fields and may call {@link #isLive(long)} /
 * {@link #indexOf(long)} but never mutates.
 *
 * <p>Sibling slice to {@link DamageService},
 * {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService}, et al.
 * Constructor-injected dependencies: {@link UnitSpatialIndex} (mirrored
 * from {@link #addUnit} so off-tick / mid-tick deboard callers see the new
 * unit on the next AI query), {@link DamageService} (for the parallel-flag
 * read inside {@link #queueSpawn}).
 */
public final class UnitRosterService {

    /** Sentinel returned by {@link #indexOf(long)} when the id is unknown. Matches the {@code -1} convention used by {@code LongIntMap.NOT_FOUND}. */
    public static final int INVALID_INDEX = -1;

    private static final int INITIAL_CAPACITY = 64;

    private final UnitSpatialIndex unitIndex;
    /** Set post-construction by {@link #setDamageService} — the sim's wiring loop is
     *  {@code rosterService → damageResolver → damageService} so this field gets
     *  bound on the last step. Only read inside {@link #queueSpawn} for the
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
     * registrations + the by-id access facade. Owned here because {@link #allocate}
     * is the single spawn seam — minting the id and adopting it into the world stay
     * in one place, and every per-entity component lives keyed by id in the world
     * (immune to the dense {@code Entity[]} reshuffle on release). The world,
     * components, and facade are all transient — battles never save/load mid-fight.
     */
    private final EntityWorld entityWorld = new EntityWorld();
    private final BattleComponents components = new BattleComponents(entityWorld);
    // Per-component data-owner Services (the World decomposition). World delegates
    // its COMBAT/MOVEMENT accessors to these; consumers inject them via combat()/movement().
    private final CombatService combatService = new CombatService(entityWorld, components);
    private final MovementService movementService = new MovementService(entityWorld, components);
    private final VisionService visionService = new VisionService(entityWorld, components);
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
     * Units queued for addition during UPDATE_UNITS (drone hub spawns today),
     * drained in APPLY_SPAWNS via {@link #flushPendingSpawns()}. Routes the
     * one mid-dispatch caller of {@link #addUnit} through a queue so the
     * units list isn't mutated from inside a per-unit task; AIR_SYSTEM /
     * GROUND_SYSTEM deboard paths stay on inline {@link #addUnit} because
     * they run in serial phases.
     */
    private final ArrayList<Entity> pendingSpawns = new ArrayList<>();
    /** Read-only view for callers that need to inspect pending spawns before the drain (e.g., fog-of-war contributor registration). */
    public List<Entity> getPendingSpawns() { return pendingSpawns; }

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
     * Adds a unit to the roster and mirrors into the spatial index so
     * callers running outside the tick loop (test fixtures, AirSystem
     * mid-tick deboard) see the unit on the next AI query. {@code tick()}
     * still does the full {@link UnitSpatialIndex#rebuild} each frame, so
     * this mirror is purely additive.
     */
    public void addUnit(Entity u) {
        allocate(u);
        unitIndex.add(this, u);
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

    // ---- allocate / release (the spawn + death seam) ----

    /**
     * Adds {@code u} to the next dense slot, assigns its {@link Entity#entityId},
     * adopts the minted id into the entity world with its live archetype, seeds the
     * world columns from the unit's write-only {@code seed*} fields, and returns the
     * id. Grows the backing array by doubling on overflow.
     *
     * <p>Rejects re-allocation: a {@link Entity} whose {@code entityId} is non-zero
     * already lives in the roster, and re-allocating would mint a new id pointing at
     * the same instance while the old id stays mapped to a now-stale dense slot. The
     * throw makes the double-add a loud setup bug rather than a silent corruption.
     */
    public long allocate(Entity u) {
        if (u.entityId != 0L) {
            throw new IllegalStateException(
                    "Entity '" + u.id + "' already has entityId " + u.entityId + " — double allocate");
        }
        if (liveCount == dense.length) {
            dense = Arrays.copyOf(dense, dense.length * 2);
        }
        long id = nextId++;
        u.entityId = id;
        dense[liveCount] = u;
        // Adopt the minted id into the entity world. Every live unit is at least
        // {IDENTITY, POSITION, RENDER_POSITION, HEALTH, VISION} (VISION universal —
        // sight stats; removed on death); on top of that:
        //   - COMBAT iff the unit is a combatant. A non-combatant (civilian /
        //     engineer / scientist; UnitType.combatant == false) never fires and is
        //     never targeted, so "has COMBAT" defines a combatant — presence IS the
        //     capability (like MOVEMENT/AI_STATE; no inert attack/cooldown columns on
        //     a fleeing civilian). Readers that walk the whole roster must gate on
        //     u.type.combatant before any COMBAT read (the accessors are fail-loud).
        //   - MOVEMENT + AI_STATE iff the unit is mobile. A static emplacement (a
        //     turret or drone hub; UnitType.isStatic) neither paths nor decides, so
        //     "has MOVEMENT" defines a mover and "has AI_STATE" a thinker — presence
        //     IS the capability (like SECONDARY_WEAPON; no inert path/cadence columns
        //     on a turret).
        //   - SECONDARY_WEAPON iff the unit carries one.
        // Identity is written once here and persists alive→dead (the corpse
        // transmute's row-move carries it — as does the cell, which IS the death cell
        // by the time the corpse forms); Position and Health seed from the
        // write-only seed* fields and are canonical thereafter — "has HEALTH with
        // hp > 0" is the liveness definition (isAliveById). Combat seeds the same way
        // when present. The corpse transmute removes HEALTH and any COMBAT /
        // MOVEMENT / AI_STATE / SECONDARY_WEAPON.
        boolean mobile = !u.type.isStatic();
        boolean combatant = u.type.combatant;
        boolean hasSecondary = u.seedSecondaryWeapon != null;
        // KINEMATICS iff the unit carries a continuous-flight body (a drone today).
        // Optional like SECONDARY_WEAPON — presence IS the "is a flier" capability;
        // a ground unit has none. It is kept OFF the corpse-remove mask
        // (DeadBodySystem), so a dead drone's body rides the death transmute for the
        // crash handler to read before it detaches it.
        boolean hasBody = u.seedBody != null;
        ComponentType[] archetype = new ComponentType[
                5 + (combatant ? 1 : 0) + (mobile ? 2 : 0) + (hasSecondary ? 1 : 0) + (hasBody ? 1 : 0)];
        int c = 0;
        archetype[c++] = components.IDENTITY;
        archetype[c++] = components.POSITION;
        archetype[c++] = components.RENDER_POSITION;
        archetype[c++] = components.HEALTH;
        archetype[c++] = components.VISION;
        if (combatant) archetype[c++] = components.COMBAT;
        if (mobile) {
            archetype[c++] = components.MOVEMENT;
            archetype[c++] = components.AI_STATE;
        }
        if (hasSecondary) archetype[c++] = components.SECONDARY_WEAPON;
        if (hasBody) archetype[c++] = components.KINEMATICS;
        entityWorld.createEntity(id, archetype);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_TYPE, u.type);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_FACTION, u.faction);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_X, u.seedCellX);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_Y, u.seedCellY);
        entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_HP, u.seedHp);
        entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_MAX_HP, u.seedMaxHp);
        // VISION is universal — sight stats seeded from the unit's write-only seeds
        // (a ground unit's airLosRadius just seeds to 0). Removed on death.
        entityWorld.setFloat(id, components.VISION, BattleComponents.VISION_RANGE, u.seedVisionRange);
        entityWorld.setFloat(id, components.VISION, BattleComponents.VISION_AIR_LOS_RADIUS, u.seedAirLosRadius);
        // Seed the COMBAT stat columns from the unit's pre-allocation seed* fields
        // (only for combatants — a non-combatant has no COMBAT component); the
        // mid-combat COMBAT scalars start at zero (a fresh world row appends
        // zero-initialised — no slot-reuse reset needed).
        if (combatant) {
            entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_DAMAGE, u.seedAttackDamage);
            entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE, u.seedAttackRange);
            entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ACCURACY, u.seedAccuracy);
            entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_COOLDOWN, u.seedAttackCooldown);
            // primaryWeapon is the OBJECT stat — null for a combatant with no
            // per-weapon profile (militia/aliens/turrets); a fresh row appends null,
            // so this seed is what makes a marine's deboard loadout canonical.
            entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_PRIMARY_WEAPON, u.seedPrimaryWeapon);
        }
        if (hasSecondary) {
            entityWorld.setObject(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC, u.seedSecondaryWeapon);
            entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO, u.seedSecondaryAmmo);
        }
        // Seed the flier's KINEMATICS body — the SAME AirBody instance the unit's
        // ctor created and positioned, now world-resident and aliased by the unit's
        // steering reads (zero-churn, the shuttle-KINEMATICS precedent).
        if (hasBody) {
            entityWorld.setObject(id, components.KINEMATICS, BattleComponents.KINEMATICS_BODY, u.seedBody);
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
            entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_SPEED, u.seedMoveSpeed);
        }
        // Seed the smooth render position from the unit's pre-allocation seed.
        // RENDER_POSITION is universal and kept OFF the corpse-remove mask, so it
        // rides the death transmute — a released corpse still resolves its
        // death-pose location with no post-release snapshot.
        entityWorld.setFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_X, u.localRenderX);
        entityWorld.setFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_Y, u.localRenderY);
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
     * Parallel-safe addition variant for callers running inside UPDATE_UNITS.
     * Routes serial callers through inline {@link #addUnit(Entity)}; parallel
     * callers append to {@link #pendingSpawns}, drained by
     * {@link #flushPendingSpawns()} in the APPLY_SPAWNS phase. The
     * parallel-flag itself lives on {@link DamageService} so the two
     * queue patterns share one source of truth.
     *
     * <p>Within-tick drift: a queued unit isn't visible in the live registry
     * until the drain. {@code DroneSpawner.isCellOccupied} iterates the live
     * registry, so if two hubs spawn in the same tick the second won't see the
     * first and could pick the same cell. Hub spawn intervals make same-tick
     * double-spawn rare; the next tick's REBUILD_OCCUPANCY restores the picture.
     */
    public void queueSpawn(Entity u) {
        if (!damageService.isParallel()) {
            addUnit(u);
            return;
        }
        synchronized (pendingSpawns) {
            pendingSpawns.add(u);
        }
    }

    /**
     * Drains {@link #pendingSpawns} in FIFO order, mirroring each queued unit
     * through {@link #addUnit}. Runs in APPLY_SPAWNS, between APPLY_OCCUPANCY
     * and INFANTRY_TICK.
     */
    public void flushPendingSpawns() {
        if (pendingSpawns.isEmpty()) return;
        for (int i = 0, n = pendingSpawns.size(); i < n; i++) {
            addUnit(pendingSpawns.get(i));
        }
        pendingSpawns.clear();
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
    public int mintSquad(Faction faction, Entity leader) {
        synchronized (squads) {
            Squad squad = new Squad(nextSquadId++, faction);
            // leader may be null (some callers mint an empty squad first, then
            // attach members) — 0L is the no-leader sentinel.
            squad.leaderId = (leader != null) ? leader.entityId : 0L;
            // Denormalize squad type from the first member (squads are
            // homogeneous) so isMechSquad() needs no leader deref and survives
            // leader death. Keyed off the archetype rather than the loadout
            // component because the component is attached after the unit is
            // allocated (post-mint), so the store isn't populated yet here.
            squad.mechSquad = leader != null && leader.type.isMech();
            squads.put(squad.id, squad);
            return squad.id;
        }
    }

    /** Bumps the deboarded-marine counter and returns the next id in {@code "m<n>"} format. */
    public String nextMarineId() {
        return "m" + deboardedMarineCount++;
    }
}
