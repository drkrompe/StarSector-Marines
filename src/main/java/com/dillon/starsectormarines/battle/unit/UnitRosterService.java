package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.damage.DamageService;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Owns the unit + squad rosters and the spawn-queue plumbing that
 * {@code BattleSimulation} previously held inline. Combines four state
 * slices that all share a lifetime ("until the battle ends"):
 *
 * <ul>
 *   <li>Live unit list ({@link #getUnits()}) — single canonical view; the
 *       sim aliases this on construction so internal iteration reads the
 *       same instance.</li>
 *   <li>Squad registry (keyed by {@link Squad#id}). Lookup via
 *       {@link #getSquad(int)} (synchronized to fence against same-tick
 *       {@link #mintSquad} from drone-hub spawns), iteration via
 *       {@link #getSquads()} (live values view; safe in serial phases).</li>
 *   <li>Parallel-safe spawn queue — {@link #queueSpawn(Unit)} routes serial
 *       callers through the inline {@link #addUnit(Unit)} path and parallel
 *       callers through {@link #pendingSpawns}, drained by
 *       {@link #flushPendingSpawns()} in the APPLY_SPAWNS phase. Sibling
 *       pattern to {@link DamageService}'s three queues; the in-flight
 *       parallel-flag itself lives on {@code DamageService} so this service
 *       reads {@link DamageService#isParallel()} to make the same decision.</li>
 *   <li>Monotonic ID counters — {@code nextSquadId} bumped by
 *       {@link #mintSquad}, {@code deboardedMarineCount} bumped by
 *       {@link #nextMarineId()}.</li>
 * </ul>
 *
 * <p>Sibling slice to {@link DamageService},
 * {@link com.dillon.starsectormarines.battle.fx.EffectsService}, et al.
 * Constructor-injected dependencies: {@link UnitSpatialIndex} (mirrored
 * from {@link #addUnit} so off-tick / mid-tick deboard callers see the new
 * unit on the next AI query), {@link DamageService} (for the parallel-flag
 * read inside {@link #queueSpawn}).
 */
public final class UnitRosterService {

    private final UnitSpatialIndex unitIndex;
    /** Set post-construction by {@link #setDamageService} — the sim's wiring loop is
     *  {@code rosterService → damageResolver → damageService} so this field gets
     *  bound on the last step. Only read inside {@link #queueSpawn} for the
     *  parallel-flag check (drone-hub same-tick spawn), so a null read here
     *  would only fire if a spawn happens before sim construction completes,
     *  which the harness prevents. */
    private DamageService damageService;

    private final List<Unit> units = new ArrayList<>();

    /**
     * Dense entity registry — Phase 1 of the SoA migration. Held alongside
     * {@link #units} and kept in sync on {@link #addUnit}; releases happen
     * out of the death cascade in
     * {@code com.dillon.starsectormarines.battle.damage.DamageResolver#resolve}
     * via {@link #releaseFromRegistry(long)}. The list continues to carry
     * dead entries so existing post-death consumers (turret demolition,
     * drone crash) keep working until they migrate to event-driven death
     * emit in a later phase.
     */
    private final UnitRegistry registry = new UnitRegistry();
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
    private final ArrayList<Unit> pendingSpawns = new ArrayList<>();

    /** Next squad id to assign on shuttle deboard. Monotonically increasing across the battle's lifetime. */
    private int nextSquadId = 0;
    /** Counter for IDs of marines deboarded from shuttles. Bumped via {@link #nextMarineId()} when {@code AirSystem} deboards. Format: "m0", "m1", ... matches the pre-shuttle setup convention. */
    private int deboardedMarineCount = 0;

    public UnitRosterService(UnitSpatialIndex unitIndex, DamageService damageService) {
        this.unitIndex = unitIndex;
        this.damageService = damageService;
    }

    /** Bind the damage service after construction — used by the sim ctor to break
     *  the {@code rosterService ↔ damageService} circular dependency. Only legal
     *  once during sim setup. */
    public void setDamageService(DamageService damageService) {
        this.damageService = damageService;
    }

    public List<Unit> getUnits() { return units; }

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
    public void addUnit(Unit u) {
        registry.allocate(u);
        units.add(u);
        unitIndex.add(u);
    }

    /** Returns the dense entity registry. See {@link UnitRegistry} for the contract + migration phasing. */
    public UnitRegistry getRegistry() { return registry; }

    /**
     * Drops the registry entry for {@code entityId} via swap-and-pop.
     * Called by the death cascade in
     * {@code com.dillon.starsectormarines.battle.damage.DamageResolver#resolve}.
     * Doesn't touch the legacy {@link #units} list — dead units stay there
     * for the post-death consumers that still iterate it.
     */
    public void releaseFromRegistry(long entityId) {
        registry.release(entityId);
    }

    /**
     * Parallel-safe addition variant for callers running inside UPDATE_UNITS.
     * Routes serial callers through inline {@link #addUnit(Unit)}; parallel
     * callers append to {@link #pendingSpawns}, drained by
     * {@link #flushPendingSpawns()} in the APPLY_SPAWNS phase. The
     * parallel-flag itself lives on {@link DamageService} so the two
     * queue patterns share one source of truth.
     *
     * <p>Within-tick drift: a queued unit isn't visible to {@link #getUnits()}
     * until the drain. {@code DroneSpawner.isCellOccupied} iterates units, so
     * if two hubs spawn in the same tick the second won't see the first and
     * could pick the same cell. Hub spawn intervals make same-tick double-
     * spawn rare; the next tick's REBUILD_OCCUPANCY restores the picture.
     */
    public void queueSpawn(Unit u) {
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
     * {@code id == Unit.NO_SQUAD} or the squad was never registered.
     * Synchronized on the same monitor as {@link #mintSquad}'s put — with
     * the pre-sized {@link #squads} (no rehash) the put is a single-slot
     * store, but without happens-before a concurrent get can still see
     * partial / missing entries. Drone-hub same-tick spawn is the only
     * mid-dispatch caller of mintSquad; everyone else mints at setup.
     */
    public Squad getSquad(int id) {
        if (id == Unit.NO_SQUAD) return null;
        synchronized (squads) {
            return squads.get(id);
        }
    }

    /** All squads currently registered. Used by the per-tick alert update; behaviors should read individual squads via {@link #getSquad(int)} keyed off {@link Unit#squadId}. */
    public Collection<Squad> getSquads() { return squads.values(); }

    /**
     * Mints a new squad with the given faction + leader, returns its id.
     * Synchronized because {@code DroneSpawner.tryLaunch} can call this
     * from the parallel UPDATE_UNITS dispatch when multiple hubs spawn
     * the same tick. The squads map is pre-sized to avoid rehash, so
     * concurrent get() callers see consistent state.
     */
    public int mintSquad(Faction faction, Unit leader) {
        synchronized (squads) {
            Squad squad = new Squad(nextSquadId++, faction);
            squad.leader = leader;
            squads.put(squad.id, squad);
            return squad.id;
        }
    }

    /** Bumps the deboarded-marine counter and returns the next id in {@code "m<n>"} format. */
    public String nextMarineId() {
        return "m" + deboardedMarineCount++;
    }
}
