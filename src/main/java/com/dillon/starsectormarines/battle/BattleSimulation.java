package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.air.AirSimContext;
import com.dillon.starsectormarines.battle.air.AirSystem;
import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.fx.EffectsService;
import com.dillon.starsectormarines.battle.ground.GroundSystem;
import com.dillon.starsectormarines.battle.ground.Vehicle;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.ai.CombatantBehavior;
import com.dillon.starsectormarines.battle.ai.goap.GoapDroneBehavior;
import com.dillon.starsectormarines.battle.ai.DroneHubBehavior;
import com.dillon.starsectormarines.battle.ai.FallbackBehavior;
import com.dillon.starsectormarines.battle.ai.FleeBehavior;
import com.dillon.starsectormarines.battle.ai.KitRetrieverBehavior;
import com.dillon.starsectormarines.battle.ai.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ai.StructureBehavior;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.TurretBehavior;
import com.dillon.starsectormarines.battle.ai.UnitBehavior;
import com.dillon.starsectormarines.battle.ai.goap.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.ai.goap.GoapMechBehavior;
import com.dillon.starsectormarines.battle.command.MissionCommand;
import com.dillon.starsectormarines.battle.damage.DamageService;
import com.dillon.starsectormarines.battle.equipment.EquipmentDropService;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.objective.Objective;
import com.dillon.starsectormarines.battle.objective.ObjectivesService;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickProfile;
import com.dillon.starsectormarines.battle.reinforcement.ReinforcementService;
import com.dillon.starsectormarines.battle.shots.ShotService;
import com.dillon.starsectormarines.battle.tactical.TacticalContextService;
import com.dillon.starsectormarines.battle.tactical.TacticalMap;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vision.VisionService;
import com.dillon.starsectormarines.battle.weapons.Detonations;
import com.dillon.starsectormarines.battle.weapons.HeavyWeapons;
import com.dillon.starsectormarines.battle.weapons.InfantryWeapons;
import com.dillon.starsectormarines.battle.weapons.WeaponSimContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Headless auto-battler simulation. Owns the {@link NavigationGrid}, the unit
 * roster, and the per-tick logic that drives target acquisition, pathfinding,
 * movement interpolation, and combat resolution.
 *
 * <p>The caller drives time via {@link #advance(float)}, which accumulates real
 * time into fixed 30Hz ticks. That keeps simulation determinism independent of
 * the render framerate and lets speed/pause controls work by scaling the input
 * dt (or feeding zero) — the sim itself doesn't care.
 *
 * <p>v1 behavior, each tick:
 * <ul>
 *   <li>Each alive unit refreshes its target to the nearest alive enemy.</li>
 *   <li>If a target is in {@link Unit#attackRange}, the unit stops moving and
 *       fires on {@link Unit#attackCooldown}, dealing {@link Unit#attackDamage}.</li>
 *   <li>Otherwise the unit re-pathfinds (only when between cells, to avoid a
 *       visual jump mid-step) and advances {@code moveProgress} along the path
 *       at {@link Unit#moveSpeed} cells/sec.</li>
 *   <li>When one faction runs out of alive units, the sim flags
 *       {@link #isComplete()} and records the winner.</li>
 * </ul>
 *
 * <p>Pathfinding runs every tick a unit is moving — at 16 units × 30Hz on a
 * 24×16 grid it's a few thousand cell expansions per second, well inside the
 * budget. Spatial indexing for target search can come later if we scale up.
 */
public class BattleSimulation implements AirSimContext, WeaponSimContext {

    /**
     * CAS handle for {@link Unit#lastReprioTickIndex}. The
     * {@link #rollReprioritizeOnHit} gate runs from the parallel UPDATE_UNITS
     * dispatch — two shooters from different workers can hit the same target
     * in the same tick, and a plain read-then-write of {@code lastReprioTickIndex}
     * lets both fall through the "one roll per sim-tick" guard. CAS keeps the
     * gate atomic so exactly one shooter wins per (target, tick).
     */
    private static final java.util.concurrent.atomic.AtomicIntegerFieldUpdater<Unit> LAST_REPRIO_TICK =
            java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater(Unit.class, "lastReprioTickIndex");

    /** Fixed simulation timestep — 30Hz. */
    public static final float TICK_DT = 1f / 30f;

    /** Damage reduction per cover level (0..MAX_COVER). Open ground = 0%; 1 wall = 15%; 2 = 30%; 3+ = 45%. Applied multiplicatively in {@link #fireShot}. */
    private static final float[] COVER_DAMAGE_REDUCTION = { 0f, 0.15f, 0.30f, 0.45f };

    /** Sim seconds a tracer stays visible after being fired. */
    private static final float SHOT_LIFETIME = 0.15f;
    /** Min/max near-miss offset (cells) from target cell-center on a missed shot. */
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

    /** Probability a hit puts the target into fall-back. Rolled once per hit; ignored if already falling back. */
    private static final float FALLBACK_CHANCE   = 0.25f;
    /** Sim seconds a unit stays in fall-back state once entered. After this, normal engagement resumes. */
    private static final float FALLBACK_DURATION = 3.5f;

    /** Base squad morale drained per non-fatal hit on a squadmate, at full strength (cap = 1.0). At lower caps the per-hit drain is scaled by {@code 1 / cap} — a heavily mauled squad is more brittle per shot, so a lone survivor (cap = 0.25) takes a 0.20 drain per hit and folds on the first incoming. */
    public static final float MORALE_DROP_ON_HIT   = 0.05f;
    /** Additional morale drop when a hit kills the squadmate. Kept absolute (not cap-scaled) — it's a one-off event correlated with the cap reduction that the death itself triggers. */
    public static final float MORALE_DROP_ON_DEATH = 0.30f;
    /** Base morale recovered per sim-second while the squad isn't being shot at ({@link Squad#timeSinceUnderFire} {@code >=} {@link #MORALE_RECOVER_AFTER_FIRE_SECONDS}), at full strength. Effective rate is scaled by cap so a mauled squad takes proportionally longer to reach their (lower) ceiling — at base 0.20 this gives a constant ~2.5s recovery to the clear threshold and ~5s to full cap across all squad sizes. */
    public static final float MORALE_RECOVERY_RATE = 0.20f;
    /** Cooldown between morale-drain events on a single squad (sim seconds). A burst of incoming bullets in one tick still counts as one drain — prevents a hail of fire from insta-breaking a full squad. At 0.2s a full squad endures sustained fire for ~2.8s before breaking (5 hits/sec × 0.05/hit = 0.25/sec, 0.7 margin). Doesn't shield mauled squads: their per-hit drain (0.05/cap) is large enough that a single hit folds them on the first cooldown window. */
    public static final float MORALE_DRAIN_COOLDOWN = 0.2f;
    /** Near-miss morale drain — applied when a hostile shot's endpoint lands near a squad member but no damage is taken. Cap-scaled like hit drain. Suppressing fire that doesn't connect still rattles them, just less than a landed hit. */
    public static final float MORALE_DROP_ON_NEAR_MISS = 0.01f;
    /** Squared cell-distance from a shot's endpoint to a squad member that counts as a "near miss." 2.25 = 1.5 cells radius. */
    public static final float NEAR_MISS_RADIUS_SQ = 2.25f;
    /** Hysteresis broken threshold, as a <em>fraction of cap</em>. Squad flips to broken when {@code morale < MORALE_BROKEN_THRESHOLD * cap}. Scaling by cap keeps the model coherent for mauled squads: a lone survivor (cap = 0.25) breaks below 0.075 absolute morale, a fresh squad (cap = 1.0) breaks below 0.30. */
    public static final float MORALE_BROKEN_THRESHOLD = 0.30f;
    /** Hysteresis clear threshold, as a <em>fraction of cap</em>. Broken squad reverts once {@code morale > MORALE_CLEAR_THRESHOLD * cap}. Fixes the pre-scaling pathology where a solo survivor's cap (0.25) was below the absolute clear threshold (0.5) and they could never recover. */
    public static final float MORALE_CLEAR_THRESHOLD  = 0.50f;
    /** Sim seconds since the last hit/near-miss on a squadmate before morale recovery resumes. Decouples recovery from raw LoS — a broken squad in cover that can see distant enemies (and is firing back) but isn't actually being shot at composes itself; a pinned-down squad still taking incoming stays locked. Without this gate, a fallback that lands on a still-exposed cell (BreakContact's picker minimizes exposure but doesn't guarantee a true hide) keeps {@code _engagedThisTick=true} every tick via STANCED return fire and the squad never recovers. */
    public static final float MORALE_RECOVER_AFTER_FIRE_SECONDS = 2.0f;

    // ---- Mech morale (Stage 2) ----
    //
    // Per-roadmap/ai/14-mech-stage1.md "Mech survival" — mechs use a tougher
    // morale model than infantry: HP-threshold drain (not per-hit), stricter
    // broken/clear thresholds, faster recovery, hard cap once damaged. Read
    // by {@link #updateMechSquadMorale} + the HP-drain pass folded into
    // {@link #applyDamage}.

    /** Fraction-of-maxHp marks where a mech bleeds morale. Crossing each drops {@link #MECH_MORALE_DROP_PER_THRESHOLD} once (monotonic via {@link MechLoadoutState#hpThresholdsCrossed}). Descending order — first entry trips at 75% HP. */
    public static final float[] MECH_HP_DRAIN_THRESHOLDS = {0.75f, 0.50f, 0.25f, 0.10f};
    /** Per-threshold morale drop. Sized so all four thresholds drained drops a fresh mech (morale 1.0) to 0.0 — total wipe at 10% HP matches the "wounded mech withdraws" target. */
    public static final float MECH_MORALE_DROP_PER_THRESHOLD = 0.25f;
    /** Hysteresis broken threshold for mechs, as a fraction of cap. Tuned with the cap drop at {@link #MECH_MORALE_ARMOR_GONE_HP_FRAC}: with default drops the mech breaks just after the 25% HP threshold crosses (morale 0.25 < 0.60×0.5 = 0.30), leaving headroom to disengage before destruction. Earlier values (0.15) only tripped break at 10% HP — too late to survive the retreat. */
    public static final float MECH_MORALE_BROKEN_THRESHOLD = 0.60f;
    /** Hysteresis clear threshold for mechs, as a fraction of cap. At cap=0.5 (damaged), clear sits at 0.425 absolute — reachable from a broken mech (morale=0.25) in ~0.6s of recovery, so a successful disengage clears the flag and re-engages the planner. */
    public static final float MECH_MORALE_CLEAR_THRESHOLD = 0.85f;
    /** Multiplier on {@link #MORALE_RECOVERY_RATE} for mech-side recovery. 1.5× — a mech that broke recomposes faster than infantry once safe. */
    public static final float MECH_MORALE_RECOVERY_RATE_MULT = 1.5f;
    /** HP fraction below which a mech's morale cap drops to {@link #MECH_MORALE_ARMOR_GONE_CAP} — the "armor is gone, this thing can be rattled" gate. */
    public static final float MECH_MORALE_ARMOR_GONE_HP_FRAC = 0.50f;
    /** Hard cap on mech morale once HP drops below {@link #MECH_MORALE_ARMOR_GONE_HP_FRAC}. With the clear threshold at 0.85 × 0.50 = 0.425 absolute and broken at 0.60 × 0.50 = 0.30, a damaged mech that breaks (morale 0.25 after the 25% HP threshold) only needs to climb 0.175 to clear — fast enough that a successful disengage actually un-breaks. */
    public static final float MECH_MORALE_ARMOR_GONE_CAP = 0.50f;

    /** Navigation slice: grid + topology + zone graph + occupancy map + spatial indices + vantage cache + LosCache lifecycle. {@link #grid} / {@link #topology} / {@link #zoneGraph} / {@link #occupancyMap} / {@link #unitIndex} / {@link #destIndex} below are alias fields that share the same instances. */
    private final NavigationService navigation;
    /** Alias of {@link NavigationService#getGrid()}. Same instance — kept as a field so the sim's 80+ {@code grid.*} reads don't pay a per-call accessor hop. */
    private final NavigationGrid grid;
    /** Alias of {@link NavigationService#getTopology()}. */
    private final CellTopology topology;
    /** Roster service: units list + squad registry + spawn queue + ID counters. {@link #units} and {@link #squads} are alias fields that share the same instances. */
    private final UnitRosterService rosterService;
    /** Alias of {@link UnitRosterService#getUnits()}. Same {@link List} instance — kept as a field so the sim's iteration / size / subList reads don't pay a per-call accessor hop. */
    private final List<Unit> units;
    private final AirSystem airSystem = new AirSystem();
    private final GroundSystem groundSystem = new GroundSystem();
    /** Fog-of-war state — building registry + faction contributor set + the every-3rd-tick {@link com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass} driver. The {@link #getBuildings}/{@link #setBuildings}/{@link #getVisionState} delegates below forward here. */
    private final VisionService vision = new VisionService();
    /** Handheld squad weapons (rifle / SMG / DMR / rocket launcher). Owns fireShot, fireSecondary, and the per-tick burst continuation pass. Pumped each tick via {@code infantry.tick(this)}; behavior call sites still go through the delegating {@link #fireShot} / {@link #fireSecondary} wrappers on this class. */
    private final InfantryWeapons infantry = new InfantryWeapons();
    /** Chassis-mounted weapons on motorized / heavy units (mech today, future tanks/hovercraft). Owns fireMechWeapon and the per-tick mech continuation + wreck-spawn passes. */
    private final HeavyWeapons heavy = new HeavyWeapons();
    /** Physics-based AoE pipeline — owns the in-flight rocket queue and drains expired entries into splash + wall damage. Both infantry rockets and mech HE rockets queue here through {@link #queueDetonation}. */
    private final Detonations detonations = new Detonations();
    /** Mission objective list + per-tick dispatch + the default eliminate-each-other backstop. The {@link #addObjective}/{@link #getObjectives} delegates below forward here; the OBJECTIVES phase + first-tick backstop install go through it. */
    private final ObjectivesService objectivesService = new ObjectivesService();
    /** Active equipment drops + per-tick pickup/retriever sweep + emit-on-death plumbing. Initialized in the constructor once {@link #rosterService} is available. */
    private final EquipmentDropService equipmentDropService;
    /** Per-tick demolition pass for destroyed {@link MapTurret}s — flips mount cell to walkable rubble + releases the guardpost if every turret on the post is down. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.turret.TurretDemolitionSystem turretDemolition;
    /** Per-tick demolition pass for destroyed {@link DroneHubUnit}s — flips hub cell to walkable rubble + cascade-kills the launched drones. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.drone.HubDemolitionSystem hubDemolition;
    /** Per-tick crash sequence for dead {@link Drone}s — three-phase falling / impact lifecycle. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.drone.DroneCrashSystem droneCrashes;
    /** Per-tick squad fall-back driver — arrival detection + trigger evaluation. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.squad.SquadFallbackSystem squadFallback;
    /** Per-tick win-condition evaluator — pure function over the objective list; sim writes the {@link #complete}/{@link #winner} fields on terminal result. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.objective.WinCheckSystem winCheck =
            new com.dillon.starsectormarines.battle.objective.WinCheckSystem();
    /** Persistent {@link Doodad} list + per-cell/per-facing cover lookup the AI consults when scoring firing positions. Initialized in the constructor once {@link #grid} is available. */
    private final DoodadService doodadService;
    private final List<MapVehicle> vehicles = new ArrayList<>();
    /**
     * Transient visual side-effects — persistent ground decals, smoking wrecks,
     * HE smoke plumes, per-frame puff / fire-burst / wall-dust event queues.
     * First slice of the services refactor; the {@link #getDecals} et al.
     * accessors below delegate here. Initialized in the constructor once
     * {@link #rng} is available.
     */
    private final EffectsService effects;
    /** Alias of {@link UnitRosterService#getSquadsMap()}. Same {@link Int2ObjectMap} instance — kept as a field so the sim's per-tick {@code squads.get} / {@code squads.values()} iteration doesn't pay a per-call accessor hop and the existing un-synchronized direct reads in serial tick phases preserve their no-lock cost. */
    private final Int2ObjectMap<Squad> squads;

    /** Per-faction strategic commander tier. Owns the slow-tick cadence; the {@link #setCommander}/{@link #getCommander} delegates below forward here, and the COMMANDER phase calls {@link CommanderService#tick}. */
    private final CommanderService commanders = new CommanderService();

    /** Reinforcement orchestration — trigger registry + means provider list + request queue. Mission setup registers triggers/means; the slow-tick polls them. Full design: {@code roadmap/reinforcement/architecture.md}. */
    private final ReinforcementService reinforcement =
            new ReinforcementService();

    /**
     * Per-target attacker index: for each unit currently targeted by at least
     * one alive attacker, the bucket holds that attacker list. Rebuilt at the
     * top of each tick by {@link #rebuildAttackersByTarget()}. Unit doesn't
     * override equals/hashCode, so {@code Object2ObjectOpenHashMap} gives
     * identity-key semantics for free.
     *
     * <p>Drives O(1)-lookup crowding scoring in {@link com.dillon.starsectormarines.battle.ai.TacticalScoring}:
     * instead of scanning every unit per candidate enemy, the scorer walks the
     * (typically &lt; 6 entry) attacker list for that enemy. Buckets are
     * recycled from {@link #attackerListPool} so steady-state allocation is
     * zero.
     */
    private final Object2ObjectMap<Unit, ArrayList<Unit>> attackersByTarget = new Object2ObjectOpenHashMap<>();
    /** Recycled {@code ArrayList<Unit>} buckets. {@link #rebuildAttackersByTarget()} clears + returns every bucket here before re-populating, so the steady-state allocation is zero. */
    private final ArrayList<ArrayList<Unit>> attackerListPool = new ArrayList<>();

    /** In-flight tracers + projectiles + per-frame event drains. Sibling slice to {@link #effects} / {@link #vision}; the {@link #postShot}, {@link #queueProjectile}, {@link #getActiveShots} et al. delegates below forward here. */
    private final ShotService shots = new ShotService();
    /** Units that transitioned from alive to dead during the last {@link #advance(float)} call. Same lifecycle as {@link #shotsThisFrame}. */
    private final List<Unit> deathsThisFrame = new ArrayList<>();
    /**
     * Parallel-dispatch safety queues + the {@code insideParallel} flag.
     * Owns {@link PendingDamage}, {@link PendingTargetMutation}, and
     * {@link PendingOccupancyDelta} queues with their pools. Constructed in
     * the sim ctor with bound method-ref appliers for each kind, so the
     * {@code applyDamage} / {@code applyReprio} / {@code applyFallback} /
     * {@code applyOccupancyDelta} delegates below forward straight in.
     */
    private final DamageService damageService;
    private final Random rng = new Random();

    /** Alias of {@link NavigationService#getOccupancyMap()}. */
    private final byte[] occupancyMap;

    /** Alias of {@link NavigationService#getUnitIndex()}. */
    private final UnitSpatialIndex unitIndex;

    /** Alias of {@link NavigationService#getDestIndex()}. */
    private final UnitDestinationSpatialIndex destIndex;

    private float tickAccumulator = 0f;
    /** Monotonic sim-tick counter incremented at the top of every {@link #tick}. Read by per-hit gates that want to fire at most once per tick (e.g. {@link #rollReprioritizeOnHit}). */
    public int simTickIndex = 0;
    /** Per-phase wall-clock profile of {@link #tick()}. Always-on (cost is a handful of {@code nanoTime} calls per tick); read by the {@code TickProfileDebugPanel} HUD overlay and the {@code TickProfileDumper} JSON dumper. */
    private final TickProfile tickProfile = new TickProfile();
    /** Per-tick sub-step profile (behavior buckets + heavy primitives like pathfind / target-pick). Reset at the top of every {@link #tick()}; snapshotted into {@link TickProfile.Spike#innerSnapshot} when a spike fires so spike JSONs carry the diagnostic breakdown. Exposed via the static {@link TickInnerProfile#current()} slot so non-sim call sites (GridPathfinder, TacticalScoring) can record without threading the sim reference through. */
    private final TickInnerProfile tickInnerProfile = new TickInnerProfile();
    // LosCache is per-thread (ThreadLocal lazy-init in LosCache itself);
    // the sim no longer owns a single instance — clearAll() at tick top
    // sweeps every worker's slot.
    /**
     * Worker pool for the parallel UPDATE_UNITS dispatch. Sized one less than
     * available cores so the main thread, OS, and render have headroom.
     * Daemon worker threads so the pool doesn't keep the JVM alive past the
     * sim's lifetime (Starsector spawns and discards battles repeatedly).
     */
    private final ForkJoinPool updatePool = new ForkJoinPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
            pool -> {
                ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                t.setDaemon(true);
                t.setName("BattleSim-Update-" + t.getPoolIndex());
                return t;
            },
            null, false);
    private boolean complete = false;
    private Faction winner;

    /** Alias of {@link NavigationService#getZoneGraph()}. */
    private final ZoneGraph zoneGraph;

    /** Fighter wings committed to this battle. Lives on the sim so the overlay can read it without coupling to the briefing screen. */
    private FlybyRoster flybyRoster = FlybyRoster.EMPTY;

    /** Battle-scoped tactical data from the map generator — TacticalMap hint graph + DefensePost list. The {@link #getTacticalMap}/{@link #setTacticalMap}/{@link #setDefensePosts} delegates below forward here. */
    private final TacticalContextService tactical =
            new TacticalContextService();

    public BattleSimulation(NavigationGrid grid, CellTopology topology) {
        this.navigation = new NavigationService(grid, topology);
        // Alias-fields share the same instances as the service so the sim's
        // 80+ internal `grid.*`/`topology.*`/`zoneGraph.*`/`occupancyMap[...]`
        // reads stay direct (no per-call accessor hop).
        this.grid = navigation.getGrid();
        this.topology = navigation.getTopology();
        this.zoneGraph = navigation.getZoneGraph();
        this.occupancyMap = navigation.getOccupancyMap();
        this.unitIndex = navigation.getUnitIndex();
        this.destIndex = navigation.getDestIndex();
        this.effects = new com.dillon.starsectormarines.battle.fx.EffectsService(rng);
        this.doodadService = new DoodadService(grid);
        this.damageService = new DamageService(
                this::applyDamageNow,
                this::writeReprioInline,
                this::writeFallbackInline,
                navigation::applyOccupancyDeltaInline);
        this.rosterService = new UnitRosterService(unitIndex, damageService);
        // Alias-fields share the same collection instances as the service so
        // the 100+ internal `units` / `squads` reads stay direct (no per-call
        // accessor hop, no synchronization on the serial-phase squads.get).
        this.units = rosterService.getUnits();
        this.squads = rosterService.getSquadsMap();
        this.equipmentDropService = new EquipmentDropService(rosterService, this::clearPath);
        this.turretDemolition = new com.dillon.starsectormarines.battle.turret.TurretDemolitionSystem(
                navigation, effects, tactical, rosterService);
        this.hubDemolition = new com.dillon.starsectormarines.battle.drone.HubDemolitionSystem(
                navigation, effects);
        this.droneCrashes = new com.dillon.starsectormarines.battle.drone.DroneCrashSystem(
                navigation, effects);
        this.squadFallback = new com.dillon.starsectormarines.battle.squad.SquadFallbackSystem(
                navigation, rosterService, this::clearPath);
    }

    @Override public NavigationGrid getGrid() { return grid; }
    /** Categorization tags (street / rubble / wall / vehicle / etc.) for renderer + placement filters. Sibling to {@link #grid}; the pathfinder doesn't touch this. */
    public CellTopology getTopology()      { return topology; }
    /** Zone+portal graph layered on the {@link NavigationGrid}. Rebuilt on wall destruction so AI queries reflect the current map. */
    public ZoneGraph getZoneGraph()        { return zoneGraph; }

    /**
     * Wall-damage entry point that callers should prefer over
     * {@link NavigationGrid#damageCell} directly — it pipes through to the
     * grid and triggers a {@link ZoneGraph#rebuild()} when a wall actually
     * collapses, so the AI's zone vocabulary stays in sync with reality.
     * Returns true the call that knocks the wall down.
     */
    @Override
    public boolean damageCell(int x, int y, int amount) {
        if (!grid.damageCell(x, y, amount)) return false;
        // A wall that just collapsed is now walkable + a zone-graph portal
        // (handled inside grid.damageCell). Topology needs the visual swap:
        // clear WALL so the wall pass stops drawing tile art, set the ground
        // kind to RUBBLE so the floor pass picks the damaged-floor autotile.
        topology.setWall(x, y, false);
        topology.setGroundKind(x, y, CellTopology.GroundKind.RUBBLE);
        // Roof cave-in: a wall just collapsed, so any building cell adjacent
        // to this wall loses its roof (and drops a rubble decal). Without
        // this the roof stays intact while the wall under it is gone, which
        // reads jarringly. The four-neighbor reach is intentional — a single
        // wall hit peels at most two cells (one on each side for interior
        // partitions, just one for a perimeter wall).
        peelRoofAround(x, y);
        navigation.markZoneGraphDirty();
        return true;
    }

    private void peelRoofAround(int wallX, int wallY) {
        destroyRoofCell(wallX - 1, wallY);
        destroyRoofCell(wallX + 1, wallY);
        destroyRoofCell(wallX, wallY - 1);
        destroyRoofCell(wallX, wallY + 1);
    }

    /**
     * True iff {@code target} is standing on a cell that's part of a building
     * and still has its roof intact — the discriminator for the aerial /
     * indirect-fire shield rule. Used by both the AoE pipeline (see
     * {@link com.dillon.starsectormarines.battle.weapons.Detonations}) and
     * the direct-fire aerial path (turret + flyby) so all elevated weapons
     * are intercepted by intact ceilings consistently.
     */
    public boolean isRoofShielded(Unit target) {
        if (target == null) return false;
        return topology.getBuildingId(target.cellX, target.cellY) != 0
                && !topology.isRoofDestroyed(target.cellX, target.cellY);
    }

    @Override
    public void destroyRoofCell(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        if (topology.getBuildingId(x, y) == 0) return;
        if (topology.isRoofDestroyed(x, y)) return;
        topology.setRoofDestroyed(x, y, true);
        // Rubble decal at the cell center with a small jitter + random pose,
        // matching the ImpactDecals.PRESET HE-rubble look — sells the cave-in
        // as "ceiling collapsed onto this tile" rather than a clean wipe.
        float jx = x + 0.5f + (rng.nextFloat() * 2f - 1f) * 0.25f;
        float jy = y + 0.5f + (rng.nextFloat() * 2f - 1f) * 0.25f;
        int rubbleIdx = rng.nextFloat() < 0.5f
                ? com.dillon.starsectormarines.battle.DecalKind.RUBBLE.index
                : com.dillon.starsectormarines.battle.DecalKind.RUBBLE_ALT.index;
        addDecal(new com.dillon.starsectormarines.battle.Decal(
                jx, jy, rubbleIdx, rng.nextFloat() * 360f, 1.10f));
    }
    public List<Unit> getUnits()           { return units; }
    public List<Shuttle> getShuttles()     { return airSystem.getShuttles(); }
    /** Active convoy / ground transport craft (moving trucks, APCs). Distinct from {@link #getVehicles()}, which lists the static map-vehicle obstacles. */
    public List<Vehicle> getConvoyVehicles() { return groundSystem.getVehicles(); }
    public List<Objective> getObjectives() { return objectivesService.getObjectives(); }
    public List<EquipmentDrop> getEquipmentDrops() { return equipmentDropService.getEquipmentDrops(); }
    public List<Doodad> getDoodads()       { return doodadService.getDoodads(); }
    /** Building registry for the roof-render + fog-of-war passes. Never null. */
    public com.dillon.starsectormarines.battle.map.Buildings getBuildings() { return vision.getBuildings(); }
    /** Faction-contributor set for the fog-of-war reveal. */
    public com.dillon.starsectormarines.battle.vision.PlayerVisionState getVisionState() { return vision.getVisionState(); }
    /** Hands the sim the map's building registry. Called by BattleSetup after generation. Subsequent visibility passes will reveal/hide these buildings as contributor units move. */
    public void setBuildings(com.dillon.starsectormarines.battle.map.Buildings buildings) {
        vision.setBuildings(buildings);
    }
    public void addDoodad(Doodad d) { doodadService.addDoodad(d); }

    /** Directional doodad cover at (x, y) against a threat in direction {@code (fromDx, fromDy)} (offset from this cell to the threat). 0 if no doodad covers that facing. */
    public int getDoodadCoverAt(int x, int y, int fromDx, int fromDy) {
        return doodadService.getDoodadCoverAt(x, y, fromDx, fromDy);
    }

    public int getDoodadCoverAtFacing(int x, int y, int facing) {
        return doodadService.getDoodadCoverAtFacing(x, y, facing);
    }

    /** Direction-agnostic doodad cover at (x, y) — max across all 4 facings. Back-compat accessor for {@link com.dillon.starsectormarines.battle.ai.TacticalScoring#findFallbackPosition} and other callers that don't carry a threat direction. */
    public int getDoodadCoverAt(int x, int y) {
        return doodadService.getDoodadCoverAt(x, y);
    }
    /** Parked vehicles that occupy multi-cell footprints. Cells were flagged non-walkable at setup time, so the sim doesn't need to consult this list for pathing/LOS — only the renderer does. */
    public List<MapVehicle> getVehicles()  { return vehicles; }
    public void addVehicle(MapVehicle v)   { vehicles.add(v); }
    /** Persistent visual decals — bullet holes, craters, rubble. Pure render data; combat ignores them. */
    public java.util.Collection<Decal> getDecals() { return effects.getDecals(); }
    /** Monotonic count of decals ever added. Read by the render layer's accumulator to drive incremental stamping that survives FIFO eviction at the cap. */
    public long getDecalsEverAdded() { return effects.getDecalsEverAdded(); }
    public void addDecal(Decal d) { effects.addDecal(d); }
    /** Smoke-puff events emitted by smoking wrecks during the last advance. Each entry is {x, y, radiusCells}. Drained by the renderer per frame. */
    public List<float[]> getSmokePuffsThisFrame() { return effects.getSmokePuffsThisFrame(); }
    /** Fire-burst events emitted by smoking wrecks during the last advance (burn phase only). Each entry is {x, y, radiusCells}. Drained by the renderer per frame. */
    public List<float[]> getFireBurstsThisFrame() { return effects.getFireBurstsThisFrame(); }
    /** Wall-collapse dust-burst events queued this advance. Each entry is {x, y} at the collapsed cell's center. Drained by {@code FlybyOverlay} which owns the dust-particle pool. */
    public List<float[]> getWallDustsThisFrame() { return effects.getWallDustsThisFrame(); }

    @Override
    public void spawnDustBurst(float cellX, float cellY) {
        effects.spawnDustBurst(cellX, cellY);
    }
    /** Live smoking wrecks. Read-only view — the lightmap pump iterates this each frame to assert persistent wreck-fire lights during the burn phase. */
    public List<SmokingWreck> getSmokingWrecks() { return effects.getSmokingWrecks(); }
    /** Fighter wings committed to this battle. {@code FlybyOverlay} reads this on first tick and drives spawns from the per-wing schedules. Defaults to {@link FlybyRoster#EMPTY}; missions assign via {@link #setFlybyRoster}. */
    public FlybyRoster getFlybyRoster()    { return flybyRoster; }
    public void setFlybyRoster(FlybyRoster roster) { this.flybyRoster = roster != null ? roster : FlybyRoster.EMPTY; }
    public List<ShotEvent> getActiveShots(){ return shots.getActiveShots(); }

    /** Thread-safe snapshot of active shots for callers iterating during the parallel UPDATE_UNITS dispatch. See {@link com.dillon.starsectormarines.battle.shots.ShotService#snapshotActiveShots()}. */
    public List<ShotEvent> snapshotActiveShots() { return shots.snapshotActiveShots(); }
    public List<ShotEvent> getShotsThisFrame() { return shots.getShotsThisFrame(); }
    /** Shots whose lifetime ended this advance — the "projectile arrived" event. Renderer reads this to spawn impact FX + arrival sounds at the moment a turret-shot sprite reaches its endpoint. */
    public List<ShotEvent> getShotsExpiredThisFrame() { return shots.getShotsExpiredThisFrame(); }
    /** In-flight {@link Projectile}s — slow-velocity AoE kinds. Renderer reads positions for sprite + contrail drawing. */
    public List<Projectile> getActiveProjectiles() { return shots.getActiveProjectiles(); }
    /** Projectiles that arrived this tick — parallel to {@link #getShotsExpiredThisFrame} for the renderer's impact-FX dispatch. */
    public List<Projectile> getProjectilesArrivedThisFrame() { return shots.getProjectilesArrivedThisFrame(); }
    public List<Unit> getDeathsThisFrame()     { return deathsThisFrame; }
    public boolean isComplete()            { return complete; }
    public Faction getWinner()             { return winner; }
    /** Per-cell unit count, indexed by {@link NavigationGrid#index(int, int)}. Exposed for AI scoring; do not mutate directly — go through {@link #setPath}. */
    public byte[] getOccupancyMap()        { return occupancyMap; }
    /** Bucketed spatial index over alive units. Rebuilt at the top of each tick by {@link #tick()}. */
    public UnitSpatialIndex getUnitIndex() { return unitIndex; }
    /** Bucketed spatial index over alive units keyed on path destination (not current cell). Rebuilt alongside {@link #unitIndex} each tick. */
    public UnitDestinationSpatialIndex getDestIndex() { return destIndex; }
    /** Per-phase wall-clock profile of the most recent completed window of ticks. Read by the {@code TickProfileDebugPanel} HUD overlay + dump-to-disk button. */
    public TickProfile getTickProfile() { return tickProfile; }
    /** Per-tick sub-step profile (per-behavior + per-primitive nanos). Reset every tick; snapshotted onto the spike record when one fires. Read by the JSON dumper. */
    public TickInnerProfile getTickInnerProfile() { return tickInnerProfile; }
    @Override public Random getRng()       { return rng; }
    /** Delegates to {@link UnitRosterService#getSquad(int)}. Synchronized lookup; safe to call from the parallel UPDATE_UNITS dispatch (concurrent {@link #mintSquad} from drone-hub spawns publishes through the same monitor). */
    public Squad getSquad(int id) {
        return rosterService.getSquad(id);
    }
    /** All squads currently registered. Used by the per-tick alert update; behaviors should read individual squads via {@link #getSquad(int)} keyed off {@link Unit#squadId}. */
    public Collection<Squad> getSquads()   { return rosterService.getSquads(); }
    /** Tactical hint graph produced by the map generator. Never null; an empty graph for legacy maps. */
    public TacticalMap getTacticalMap()    { return tactical.getTacticalMap(); }
    /** Set the tactical map for this battle. Called once by {@code BattleSetup} right after construction, before the first {@link #advance} call. */
    public void setTacticalMap(TacticalMap map) { tactical.setTacticalMap(map); }
    /** Stamped defense posts (conquest only). Called once by {@code BattleSetup} right after construction; safe to pass null/empty for missions without posts. */
    public void setDefensePosts(List<DefensePost> posts) { tactical.setDefensePosts(posts); }

    @Override
    public void addUnit(Unit u) {
        rosterService.addUnit(u);
    }

    /** Delegates to {@link UnitRosterService#queueSpawn(Unit)}. Routes serial callers through inline {@link #addUnit} and parallel callers through the spawn queue (drained in APPLY_SPAWNS). */
    public void queueSpawn(Unit u) {
        rosterService.queueSpawn(u);
    }

    /** Delegates to {@link UnitRosterService#flushPendingSpawns()}. Public so tests can force the drain after a {@code queueSpawn} call. */
    public void flushPendingSpawns() {
        rosterService.flushPendingSpawns();
    }

    public void addShuttle(Shuttle s) {
        airSystem.add(s);
    }

    public void addConvoyVehicle(Vehicle v) {
        groundSystem.add(v);
    }

    // ---- WeaponSimContext: services the weapon subsystems reach back for ----

    @Override
    public void applyDamage(Unit target, float damage, float vsTurretMult) {
        applyDamage(target, damage, vsTurretMult, 1.0f);
    }

    @Override
    public void applyDamage(Unit target, float damage, float vsTurretMult, float moraleImpact) {
        damageService.applyDamage(target, damage, vsTurretMult, moraleImpact);
    }

    /** Delegates to {@link DamageService#flushPendingDamage()}. Public so tests can force the drain after a direct {@code applyDamage} call to assert immediate side effects. */
    public void flushPendingDamage() {
        damageService.flushPendingDamage();
    }

    /**
     * The pre-deferral body of {@link #applyDamage} — performs the actual HP /
     * morale / death-cascade mutations. Called by {@link #flushPendingDamage()}
     * once per queued entry. Kept private; external callers always go through
     * the queue.
     */
    private void applyDamageNow(Unit target, float damage, float vsTurretMult, float moraleImpact) {
        boolean wasAlive = target.isAlive();
        int targetCover = grid.getCoverAt(target.cellX, target.cellY);
        float dr = COVER_DAMAGE_REDUCTION[Math.min(targetCover, COVER_DAMAGE_REDUCTION.length - 1)];
        float effectiveMult = (target instanceof MapTurret) ? vsTurretMult : 1f;
        target.hp -= damage * effectiveMult * (1f - dr);
        boolean died = wasAlive && !target.isAlive();
        if (died) {
            target.deathPoseIdx = rng.nextInt(4);
            deathsThisFrame.add(target);
            equipmentDropService.emitIfApplicable(target);
            // Squad leader promotion — if the dead unit was leading a
            // squad, hand the badge to the closest still-alive member.
            // Preserves direction of travel: the new leader stands roughly
            // where the old one fell, so followers don't get yanked
            // sideways when the leader dies mid-maneuver. See
            // InfantryCohesion.cohesionOverride for how the leader cell
            // pulls cohesion. NO_SQUAD units (turrets, civilians, etc.)
            // skip — no leader to promote.
            if (target.squadId != Unit.NO_SQUAD) {
                Squad ls = squads.get(target.squadId);
                if (ls != null && ls.leader == target) {
                    ls.leader = pickPromotionCandidate(ls, target);
                }
            }
        }
        // Morale drain — branches on unit type.
        //
        // Mech-class targets use per-chassis morale: HP threshold crossings
        // drain {@link MechLoadoutState#morale} (squad-level aggregation
        // happens in {@link #updateMechSquadMorale}). The squad's
        // {@link Squad#morale} field is unused for mech squads — leaving it
        // at its initial value is harmless because the GOAP predicate reads
        // {@link Squad#moraleBroken}, not the raw float.
        //
        // Infantry squad members feed the legacy squad-level drain (hit
        // event + cap scaling + death bonus). Solo units (turrets, civilians)
        // skip both — their behaviors don't consult MORALE_BROKEN.
        if (wasAlive && target.mech != null) {
            applyMechHpThresholdDrain(target);
        } else if (wasAlive && target.squadId != Unit.NO_SQUAD) {
            Squad sq = squads.get(target.squadId);
            if (sq != null) {
                // Death drain stacks on top of hit drain and bypasses the
                // cooldown — a kill is a discrete event the model should
                // always reflect, even if the squad just took a hit a tick
                // ago. The hit-component of the drain only fires when the
                // cooldown is clear; otherwise a burst of multiple hits in
                // one tick would each stack their per-hit drain and break
                // a full squad in a single frame.
                float cap = (sq.originalSize > 0 && sq.aliveMembers > 0)
                        ? (float) sq.aliveMembers / sq.originalSize
                        : 1f;
                float drop = 0f;
                if (sq.moraleDrainCooldown <= 0f) {
                    float hit = (cap > 0f) ? MORALE_DROP_ON_HIT / cap : MORALE_DROP_ON_HIT;
                    drop += hit * moraleImpact;
                    sq.moraleDrainCooldown = MORALE_DRAIN_COOLDOWN;
                }
                if (died) drop += MORALE_DROP_ON_DEATH;
                if (drop > 0f) sq.morale = Math.max(0f, sq.morale - drop);
                // Recovery gate: a hit always counts as "under fire," even
                // when the drain cooldown blocked the drop. Otherwise a
                // sustained burst would only reset the timer on the first
                // bullet, letting recovery resume mid-volley.
                sq.timeSinceUnderFire = 0f;
            }
        }
    }

    /**
     * Mech-side morale drain on damage. Counts how many entries in
     * {@link #MECH_HP_DRAIN_THRESHOLDS} the chassis HP just crossed and drops
     * {@link MechLoadoutState#morale} by {@link #MECH_MORALE_DROP_PER_THRESHOLD}
     * per crossing. Always resets {@link MechLoadoutState#timeSinceUnderFire}
     * so recovery pauses through sustained fire — even a hit that didn't
     * cross a fresh threshold counts as "still under fire."
     *
     * <p>Monotonic via {@link MechLoadoutState#hpThresholdsCrossed} — a
     * healed mech (none today, but defensive) wouldn't refund drains. The
     * drain is keyed to "how far through this fight have you been damaged,"
     * not to instantaneous HP.
     */
    private void applyMechHpThresholdDrain(Unit target) {
        MechLoadoutState m = target.mech;
        m.timeSinceUnderFire = 0f;
        if (target.maxHp <= 0f) return;
        // Re-count by the post-damage HP fraction — once a threshold is
        // crossed it stays counted. Compare against the running monotonic
        // tracker so a heal followed by re-damage doesn't double-drain.
        float frac = Math.max(0f, target.hp) / target.maxHp;
        int newCount = 0;
        for (float t : MECH_HP_DRAIN_THRESHOLDS) {
            if (frac <= t) newCount++;
        }
        int crossings = newCount - m.hpThresholdsCrossed;
        if (crossings <= 0) return;
        m.hpThresholdsCrossed = newCount;
        m.morale = Math.max(0f, m.morale - crossings * MECH_MORALE_DROP_PER_THRESHOLD);
    }

    @Override
    public void postShot(ShotEvent shot) {
        shots.postShot(shot);
    }

    @Override
    public void queueDetonation(PendingDetonation det) {
        synchronized (detonations) {
            detonations.queue(det);
        }
    }

    /** Read-only view of in-flight rocket / missile detonations. Used by squad-coordination scorers (avoid rocket volleys against an already-doomed turret). */
    public List<PendingDetonation> getInflightDetonations() {
        return detonations.getPending();
    }

    /**
     * Thread-safe snapshot of in-flight detonations — same justification as
     * {@link #snapshotActiveShots()}. {@link #queueDetonation} synchronizes
     * on the {@link #detonations} monitor, so locking it here gives readers a
     * consistent view.
     */
    public List<PendingDetonation> snapshotInflightDetonations() {
        synchronized (detonations) {
            return new ArrayList<>(detonations.getPending());
        }
    }

    /**
     * Detonates a {@link PendingDetonation} this tick instead of going through
     * the in-flight queue. Used by callers whose visible flight is already
     * resolved (today: {@code FlybyOverlay} fighter missile, detonating on
     * contact with its target's AoE radius). Same damage / wall / roof / dust
     * pipeline as a queued detonation — just without the timer delay.
     */
    public void detonateNow(PendingDetonation det) {
        detonations.detonateNow(det, this);
    }

    @Override
    public void spawnSmokingWreck(int x, int y) {
        effects.spawnSmokingWreck(x, y);
    }

    @Override
    public void spawnSmokePlume(float x, float y) {
        effects.spawnSmokePlume(x, y);
    }

    /**
     * Base chance a hit triggers a mech target re-evaluation when the mech
     * still has line-of-sight to its current target. Moderate — keeps the
     * mech from twitchy-switching every burst it takes but eventually
     * forces a look when flankers stack up hits.
     */
    private static final float REPRIORITIZE_BASE_CHANCE = 0.35f;
    /**
     * Bump when the mech's current target has no LoS. Compounds with
     * {@link #REPRIORITIZE_BASE_CHANCE} for a much higher reprio chance —
     * the "I'm chasing what I can't see while someone else shoots me"
     * failure mode that prompted this hook.
     */
    private static final float REPRIORITIZE_NO_LOS_CHANCE = 0.85f;

    @Override
    public void rollReprioritizeOnHit(Unit target, Unit shooter) {
        if (!target.isAlive()) return;
        // Only target-latching units reprio — mechs (lock until reset) and
        // turrets (lock once and slew). Infantry GOAP refreshes targets on
        // the squad replan, so the per-hit reprio would just step on the
        // planner. Both qualifying types share the "locked on someone I
        // can't see while flankers shoot me free" failure mode.
        boolean qualifies = target.mech != null || target instanceof MapTurret;
        if (!qualifies) return;
        // One roll per sim-tick. A 4-marine squad firing in the same tick
        // would otherwise compound a 0.35 base chance into ~0.82 cumulative
        // — near-constant target twitching. Latch the tick index on first
        // attempt regardless of success/failure so subsequent hits this
        // tick wait for the next one.
        //
        // CAS via {@link #LAST_REPRIO_TICK} because the parallel UPDATE_UNITS
        // dispatch can fire two shooters at the same target in the same tick
        // from different workers — a plain read-then-write lets both fall
        // through and burn redundant RNG.
        int prev = LAST_REPRIO_TICK.get(target);
        if (prev == simTickIndex) return;
        if (!LAST_REPRIO_TICK.compareAndSet(target, prev, simTickIndex)) return;
        // Snapshot the target's current enemy BEFORE the rng roll — the rest
        // of this method makes decisions on this view, and the drain uses it
        // to detect a concurrent self-retarget (target's own worker picked a
        // new enemy during the same UPDATE_UNITS phase) so we don't clobber
        // that newer choice with null.
        Unit expectedTarget = target.target;
        // No current target → next behavior tick will pick fresh anyway.
        if (expectedTarget == null || !expectedTarget.isAlive()) return;
        // Already targeting the shooter → no point re-rolling.
        if (shooter != null && expectedTarget == shooter) return;
        // Reprio chance bumps heavily when current target is out of LoS —
        // chasing a target you can't see while taking incoming is the
        // failure mode this hook exists to break.
        boolean hasLosToCurrentTarget = TacticalScoring.canSeePair(grid,
                target.cellX, target.cellY,
                expectedTarget.cellX, expectedTarget.cellY,
                target.airLosRadius, expectedTarget.airLosRadius);
        float chance = hasLosToCurrentTarget ? REPRIORITIZE_BASE_CHANCE : REPRIORITIZE_NO_LOS_CHANCE;
        if (target.rng.nextFloat() >= chance) return;
        // Clear the target — next behavior tick (EngageAtCurrentBand /
        // OverwatchKillZone / BackstopAssignedSquad for mechs, TurretAim
        // for turrets) calls its target-picker on the null check.
        // findBestTarget already weights distance + LOS + threat density,
        // so a closer-with-LOS flanker beats an out-of-LOS chase target
        // naturally. The damage service routes serial callers through
        // the inline applier and parallel callers through the pool-backed
        // queue (which retains expectedTarget for the flush-side race
        // resolution against a concurrent self-retarget).
        damageService.applyReprio(target, expectedTarget);
    }

    @Override
    public void rollFallbackOnHit(Unit target) {
        if (!target.isAlive()) return;
        if (target.fallbackTimer > 0f) return;
        if (target instanceof MapTurret) return;
        // GOAP-driven squad members — both infantry and mechs — own their
        // retreat through their per-squad planner now. Infantry routes
        // through SurviveContact / BreakContact via squad morale; mech
        // squads run on GoapMechBehavior with no flinch (Stage 1 design:
        // mechs are implacable; morale-driven mech retreat queues for a
        // later slice, see roadmap/ai/14-mech-stage1.md "Mech survival").
        // The legacy per-unit fall-back roll conflicts with both planners
        // (it can yank a planter off the charge site or break a mech's
        // overwatch posture), so we skip every squad member here.
        // Civilians (NO_SQUAD) keep the legacy roll — FleeBehavior depends
        // on it.
        if (target.squadId != Unit.NO_SQUAD) return;
        if (target.rng.nextFloat() >= FALLBACK_CHANCE) return;
        // Heavy compute — done on the shooter's worker by design; the result is
        // just int coords carried to the serial drain.
        int[] fallback = TacticalScoring.findFallbackPosition(target, this);
        if (fallback[0] == target.cellX && fallback[1] == target.cellY) return;
        // Serial callers apply the 3 fb-field writes + clearPath inline via
        // the damage service's inline applier; parallel callers queue (writes
        // to fb fields + {@code target.path} via clearPath would race the
        // target's own worker reading those fields).
        damageService.applyFallback(target, fallback[0], fallback[1]);
    }

    /** Delegates to {@link DamageService#flushPendingTargetMutations()}. Public so tests can force the drain after a direct {@code rollReprio} / {@code rollFallback} call. */
    public void flushPendingTargetMutations() {
        damageService.flushPendingTargetMutations();
    }

    /** Inline reprio write — invoked by the damage service on the serial path AND on the queued path (after the expectedTarget race-check). The shape stays just "null the target field"; the next behavior tick re-picks via {@code findBestTarget}. */
    private void writeReprioInline(Unit target) {
        target.target = null;
    }

    /** Inline fallback write — invoked by the damage service on the serial path AND from the queued-flush. Writes the 3 fb fields and clears the stale path so the target re-paths to the fall-back cell on its next updateUnit pass. */
    private void writeFallbackInline(Unit target, int fbX, int fbY) {
        target.fallbackCellX = fbX;
        target.fallbackCellY = fbY;
        target.fallbackTimer = FALLBACK_DURATION;
        clearPath(target);
    }

    // ---- AirSimContext: services the AirSystem reaches back for during a tick ----

    @Override
    public boolean isCellOccupied(int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        // Read the precomputed occupancy map instead of scanning units. The map
        // counts each unit's current cell + path destination, so a value > 0
        // means at least one unit physically stands on (x, y) right now.
        // Destination-only contributions count too, but a shuttle picking a
        // deboard cell should still avoid those — a marine en route to (x, y)
        // is about to be there.
        return (occupancyMap[y * grid.getWidth() + x] & 0xFF) > 0;
    }

    @Override
    public String nextMarineId() {
        return rosterService.nextMarineId();
    }

    @Override
    public int mintSquad(Faction faction, Unit leader) {
        return rosterService.mintSquad(faction, leader);
    }

    public void addObjective(Objective o) {
        objectivesService.addObjective(o);
    }

    /** Install (or replace) the strategic commander for one faction. Pass {@code null} to clear. Typically called once during {@code BattleSetup} per faction that wants the layer. */
    public void setCommander(Faction faction, MissionCommand commander) {
        commanders.setCommander(faction, commander);
    }

    /** The commander for {@code faction}, or {@code null} if none is wired. Read by debug UI and by integration tests that poke at commander state directly. */
    public MissionCommand getCommander(Faction faction) {
        return commanders.getCommander(faction);
    }

    /** Reinforcement service for trigger / means registration. {@code BattleSetup} populates this per mission. */
    public com.dillon.starsectormarines.battle.reinforcement.ReinforcementService getReinforcementService() {
        return reinforcement;
    }

    /**
     * Drives the simulation forward. Accepts any real-time delta; internally
     * runs zero or more fixed 30Hz ticks until the accumulator is drained.
     * Returns immediately once the battle is complete.
     */
    public void advance(float dt) {
        // Clear unconditionally so a paused caller doesn't keep replaying the previous frame's events.
        shots.beginFrame();
        deathsThisFrame.clear();
        effects.beginFrame();
        if (complete) return;
        tickAccumulator += dt;
        while (tickAccumulator >= TICK_DT) {
            tick();
            tickAccumulator -= TICK_DT;
            if (complete) break;
        }
    }

    private void tick() {
        simTickIndex++;
        // Backstop: if a caller (currently BattleSetup) hasn't registered
        // objectives, install the default eliminate-each-other pair so the
        // old behavior keeps working untouched. Run-once on first tick.
        objectivesService.installEliminationBackstopIfEmpty(Faction.MARINE, Faction.DEFENDER);
        // Start the per-tick phase profiler. Each lap() call below records
        // wall-time spent in the preceding block; endTick() at the bottom
        // snapshots into the rolling display buffer the debug panel reads.
        // Pass simTickIndex so the profile can gate JIT/load-time warmup.
        tickProfile.begin(simTickIndex);
        // Per-tick sub-step counters (behavior buckets + primitives like
        // pathfind / target-pick). Reset then advertised via the static
        // TickInnerProfile.current() slot so GridPathfinder / TacticalScoring
        // can record without threading the sim through their signatures.
        tickInnerProfile.reset();
        TickInnerProfile.setCurrent(tickInnerProfile);
        // Per-tick LoS cache + spatial-state setup — sweeps every worker's
        // LosCache slot so cached pairs can't outlive a prior-tick wall
        // breach, then enables auto-init for the duration of the tick. Paired
        // with navigation.endTick() at the bottom.
        navigation.beginTick();
        // Fog-of-war visibility pass — recomputed every 3rd tick (~10 Hz at
        // 30 Hz sim). The render path lerps current→target alpha per frame so
        // this cadence stays invisible.
        vision.tick(simTickIndex, units, grid);
        tickProfile.lap(TickProfile.Phase.VISION);
        navigation.rebuildOccupancyMap(units);
        tickProfile.lap(TickProfile.Phase.REBUILD_OCCUPANCY);
        // Rebuild the spatial index BEFORE the AI passes so per-tick scoring
        // (exposure, threat density, allies-near) reads a consistent
        // snapshot. Same single-pass-per-tick semantics as the attacker
        // index below — mid-tick repath shifts aren't reflected until next
        // tick, matching the pre-spatial behavior.
        navigation.rebuildSpatialIndices(units);
        tickProfile.lap(TickProfile.Phase.REBUILD_UNIT_INDEX);
        // Rebuild the attacker index BEFORE per-unit updates so target-
        // selection's crowding scoring (TacticalScoring.findBestTarget) sees a
        // consistent snapshot of last-tick's targets. We deliberately don't
        // re-rebuild mid-tick as units pick new targets — the snapshot model
        // means a squad's crowding cost reflects the previous frame, which
        // matches the prior O(U²) behavior's semantics anyway.
        rebuildAttackersByTarget();
        tickProfile.lap(TickProfile.Phase.REBUILD_ATTACKERS);
        // Refresh squad-level awareness BEFORE individual unit updates so the
        // garrison/patrol behavior dispatch this tick sees fresh ENGAGED /
        // SUSPICIOUS / UNAWARE state. Solo units (squadId == NO_SQUAD) skip
        // the squad path entirely.
        updateSquadAlertLevels();
        tickProfile.lap(TickProfile.Phase.SQUAD_ALERT);
        // Morale recovery + hysteresis. Reads the freshly-set _engagedThisTick
        // flag from updateSquadAlertLevels: a squad out of contact this tick
        // recovers; a squad in contact holds. Runs before the GOAP replan so
        // SurviveContact relevance sees the up-to-date moraleBroken flag.
        updateSquadMorale();
        tickProfile.lap(TickProfile.Phase.SQUAD_MORALE);
        // Evaluate fallback chains after alert state is current: an engaged
        // garrison that's lost half its members reassigns to its FALLBACK_TO
        // link, and a squad whose members have all arrived at their new post
        // clears the in-progress flag. Runs after alerts (so we see fresh
        // aliveMembers) and before updateUnit (so the new home cells are
        // visible to garrison dispatch this same tick).
        squadFallback.tick(units);
        tickProfile.lap(TickProfile.Phase.SQUAD_FALLBACK);
        // Commander-tier slow tick — runs before per-squad replan so any
        // assignment written this tick is visible to the GOAP relevance pass
        // below. Cadence + early-skip-when-empty live inside the registry.
        commanders.tick(TICK_DT, cmd -> cmd.tick(this));
        tickProfile.lap(TickProfile.Phase.COMMANDER);
        // Squad-level GOAP replan pass. Piggybacks the alert-update phase so
        // plans reflect THIS tick's fresh aliveMembers + centroid + alert
        // level before any unit executes. Serial today; the planner +
        // WorldStateBuilder + actions are designed for parallel execution
        // across squads (see roadmap/ai/README.md parallelism section) and
        // we'll fork-join here once we feel the cost.
        for (Squad squad : squads.values()) {
            if (squad.isDroneSquad()) {
                GoapDroneBehavior.replanIfNeeded(squad, this);
            } else if (squad.isMechSquad()) {
                GoapMechBehavior.replanIfNeeded(squad, this);
            } else {
                GoapInfantryBehavior.replanIfNeeded(squad, this);
            }
        }
        tickProfile.lap(TickProfile.Phase.GOAP_REPLAN);
        // Parallel per-unit dispatch on the sim's ForkJoinPool. Pre-Phase-A
        // this loop was serial and mutated shared sim state inline; Phase A
        // refactored every shared mutation (damage, occupancy, spawns, shots,
        // projectiles, detonations) into thread-safe queue / synchronized
        // enqueue paths, so workers can dispatch in parallel without
        // corrupting sim state. After the parallel section, per-worker
        // TickInnerProfile recordings (PATHFIND / TARGET_PICK / FIRING_POSITION
        // / behavior buckets) are merged into the canonical sim profile so
        // the dumper + debug panel read aggregate numbers.
        //
        // Snapshot size: queueSpawn defers drone-hub additions to the
        // APPLY_SPAWNS phase, so the units list is stable during this
        // dispatch — no CME risk on the parallel iterator.
        int unitCount = units.size();
        List<Unit> view = units.subList(0, unitCount);
        damageService.enterParallel();
        try {
            updatePool.submit(() -> view.parallelStream()
                    .filter(Unit::isAlive)
                    .forEach(this::updateUnit))
                    .get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("UPDATE_UNITS dispatch interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("UPDATE_UNITS dispatch failed", cause);
        } finally {
            damageService.exitParallel();
        }
        TickInnerProfile.mergeAllInto(tickInnerProfile);
        tickProfile.lap(TickProfile.Phase.UPDATE_UNITS);
        // Apply occupancy + destIndex deltas queued by setPath during the
        // per-unit dispatch. Runs at the end of UPDATE_UNITS, before any
        // subsequent serial phase reads the spatial state. None of the
        // post-UPDATE_UNITS phases call setPath today, so a single drain
        // here keeps the bookkeeping consistent for the rest of the tick
        // (and the next tick's REBUILD_OCCUPANCY rebuilds from u.path
        // regardless).
        flushPendingOccupancyDeltas();
        tickProfile.lap(TickProfile.Phase.APPLY_OCCUPANCY);
        // Mirror queued drone-hub spawns into the units list. Only callers
        // running inside UPDATE_UNITS route through queueSpawn; AIR_SYSTEM /
        // GROUND_SYSTEM deboards keep using inline addUnit because they
        // already run in serial phases.
        flushPendingSpawns();
        tickProfile.lap(TickProfile.Phase.APPLY_SPAWNS);
        // Burst-fire rounds queued after a primary shot — fire them now so
        // they emit at the right per-weapon spacing without piling onto the
        // AI's single-decision-per-tick model. Lives on the InfantryWeapons
        // subsystem; this call drains every unit's burst state.
        infantry.tick(this);
        tickProfile.lap(TickProfile.Phase.INFANTRY_TICK);
        // Mech chassis weapons run on their own state bag (MechLoadoutState).
        // Continuation handling for chaingun bursts + SRM salvos, plus cooldown
        // tick-down for all three tracks. New triggers (start a burst / salvo /
        // LRM) come from CombatantBehavior; the subsystem pass also runs the
        // mech-wreck spawn for any chassis units that died this tick.
        heavy.tick(this);
        tickProfile.lap(TickProfile.Phase.HEAVY_TICK);
        // Simulated-projectile path — advance each in-flight Projectile by dt,
        // detonate its onArrival payload when remainingTime hits zero, and
        // emit an arrival record for the renderer's impact-FX dispatch.
        // Runs BEFORE detonations.tick so a projectile arriving this tick
        // contributes its detonation to the same wave as legacy queue entries.
        shots.tickProjectiles(TICK_DT, det -> detonations.detonateNow(det, this));
        tickProfile.lap(TickProfile.Phase.PROJECTILES);
        // Physics-based rocket/missile damage — each pending detonation ticks
        // down its arrival timer and applies splash + wall damage when it
        // expires. Pairs with the visual ShotEvent flight; the visual and the
        // damage are queued together and arrive together.
        detonations.tick(this);
        tickProfile.lap(TickProfile.Phase.DETONATIONS);
        // Drain all damage queued this tick — from UPDATE_UNITS direct fire,
        // INFANTRY_TICK / HEAVY_TICK burst continuations, PROJECTILES
        // arrivals, and DETONATIONS AoE. Single late drain (rather than one
        // after every damage-emitter) keeps the rule simple: damage applies
        // before any phase that reads alive-state — DEMOLISH_TURRETS /
        // DEMOLISH_HUBS / DRONE_CRASHES / WIN_CHECK all run after this.
        // Trade-off: a target queued for death in UPDATE_UNITS is still alive
        // during the subsystem ticks this tick, so its burst continuations
        // fire one more round. Considered "doomed unit gets a final action"
        // — arguably more consistent than the pre-deferral order-dependent
        // skip and the prerequisite for parallelizing the dispatch loop.
        flushPendingDamage();
        // Drain target-side reprio / fall-back enqueues from this tick's
        // weapon hits. Ordered AFTER flushPendingDamage so we skip mutations
        // on targets the queued damage just killed (the drain checks
        // isAlive). Shares the APPLY_DAMAGE phase — both are serial fixups
        // for state the parallel UPDATE_UNITS dispatch couldn't touch.
        flushPendingTargetMutations();
        tickProfile.lap(TickProfile.Phase.APPLY_DAMAGE);
        // Convert any turrets that just died into walkable rubble so the next
        // tick's pathfinding + zone graph sees the hole, and the floor pass
        // picks the cell up as rubble.
        turretDemolition.tick(units);
        tickProfile.lap(TickProfile.Phase.DEMOLISH_TURRETS);
        // Same rubble-conversion pass for destroyed drone hubs — they're
        // static STRUCTUREs sitting on sealed non-walkable cells, so leaving
        // the cell sealed after death would orphan an invisible obstacle.
        hubDemolition.tick(units);
        tickProfile.lap(TickProfile.Phase.DEMOLISH_HUBS);
        // Drone crash sequence: detect newly-dead drones, tick their fall
        // timer, drop a SmokingWreck on impact. Runs after the hub demolition
        // pass so a hub destruction (which kills its drones via setting hp=0)
        // gets the crashes started on the same tick.
        droneCrashes.tick(units, TICK_DT);
        tickProfile.lap(TickProfile.Phase.DRONE_CRASHES);
        // Age smoking wrecks + emit any puff events that came due this tick.
        effects.tickWrecks(TICK_DT);
        tickProfile.lap(TickProfile.Phase.WRECKS);
        // Lingering smoke plumes parked at HE impact sites — same per-frame
        // puff drain as the wrecks, just on a shorter, fire-less timer.
        effects.tickPlumes(TICK_DT);
        tickProfile.lap(TickProfile.Phase.PLUMES);
        // Reinforcement slow-tick: poll triggers, drain the request queue, and
        // dispatch via the first feasible means provider. Runs before air/ground
        // systems so a dispatched Shuttle or Vehicle gets ticked the same frame
        // it spawns, matching the rest of the spawn ordering for those systems.
        reinforcement.tick(TICK_DT, this);
        // Air vehicles tick AFTER units so new deboarded marines aren't iterated
        // mid-loop. They'll be picked up by next tick's occupancy + target pass.
        airSystem.tick(this, TICK_DT);
        tickProfile.lap(TickProfile.Phase.AIR_SYSTEM);
        // Ground convoys ride the same ordering rule for the same reason —
        // deboarded militia join the roster between ticks, not mid-loop.
        groundSystem.tick(this, TICK_DT);
        tickProfile.lap(TickProfile.Phase.GROUND_SYSTEM);
        shots.tickShots(TICK_DT);
        tickProfile.lap(TickProfile.Phase.SHOTS);
        equipmentDropService.tick();
        tickProfile.lap(TickProfile.Phase.EQUIPMENT_DROPS);
        objectivesService.tick(o -> o.tick(this));
        tickProfile.lap(TickProfile.Phase.OBJECTIVES);
        // Single zone-graph rebuild for the whole tick — drains any wall
        // breaches or turret demolishes that happened this tick + clears the
        // vantage-point cache in lockstep. Multiple breaches in one tick
        // (e.g., a rocket shredding a wall section) collapse into one rebuild.
        navigation.flushZoneGraphIfDirty();
        tickProfile.lap(TickProfile.Phase.ZONE_GRAPH);
        com.dillon.starsectormarines.battle.objective.WinCheckSystem.WinResult result =
                winCheck.tick(objectivesService.getObjectives());
        if (result.complete()) {
            complete = true;
            winner = result.winner();
        }
        tickProfile.lap(TickProfile.Phase.WIN_CHECK);
        tickProfile.endTick(simTickIndex, tickInnerProfile);
        // Clear the inner-profile slot so any stray call outside the tick
        // window (e.g., test harness, mid-frame UI hook) is a clean no-op
        // rather than silently writing into the previous tick's counters.
        TickInnerProfile.setCurrent(null);
        // Switch the per-thread LosCache off so off-tick callers see null
        // and fall through to live Bresenham (preserving the old off-tick
        // behavior that tests + UI hooks depend on).
        navigation.endTick();
    }

    /**
     * Returns the alive attackers currently aiming at {@code target}, or null
     * if no one is targeting it. The list is mutated in-place each tick by
     * {@link #rebuildAttackersByTarget()} — callers must not retain it across
     * tick boundaries.
     */
    public ArrayList<Unit> getAttackersOf(Unit target) {
        return attackersByTarget.get(target);
    }

    /** Delegates to {@link NavigationService#getVantagePointsFor(int, int)}. Cached per-battle; invalidated in lockstep with the zone-graph rebuild driven by {@link NavigationService#flushZoneGraphIfDirty}. */
    public int[][] getVantagePointsFor(int tx, int ty) {
        return navigation.getVantagePointsFor(tx, ty);
    }

    /**
     * Rebuilds {@link #attackersByTarget} from the current {@code Unit.target}
     * pointers. Recycles bucket lists via {@link #attackerListPool} so the
     * steady-state allocation is zero — buckets grow once, then live forever.
     *
     * <p>Skips dead attackers and dead targets so a unit holding a stale
     * pointer at its dying enemy doesn't pollute the next tick's lookup.
     */
    private void rebuildAttackersByTarget() {
        for (ArrayList<Unit> bucket : attackersByTarget.values()) {
            bucket.clear();
            attackerListPool.add(bucket);
        }
        attackersByTarget.clear();
        for (Unit u : units) {
            if (!u.isAlive() || u.target == null || !u.target.isAlive()) continue;
            ArrayList<Unit> bucket = attackersByTarget.get(u.target);
            if (bucket == null) {
                bucket = attackerListPool.isEmpty()
                        ? new ArrayList<>(4)
                        : attackerListPool.remove(attackerListPool.size() - 1);
                attackersByTarget.put(u.target, bucket);
            }
            bucket.add(u);
        }
    }

    /**
     * Replaces a unit's path and queues a deferred {@link NavigationService#getOccupancyMap()
     * occupancyMap} + {@link NavigationService#getDestIndex() destIndex}
     * update. Public so AI behaviors in {@code battle.ai} can route their
     * movement through this method instead of touching {@code u.path}
     * directly. Pass {@link GridPathfinder#EMPTY_PATH} (or call
     * {@link #clearPath(Unit)}) to drop the current path.
     *
     * <p>{@code u.path} / {@code u.pathIdx} are unit-local and mutated inline.
     * Shared spatial state goes through {@link DamageService}'s occupancy
     * queue so the per-unit dispatch never races on the occupancy map or
     * destIndex; the delta drains in APPLY_OCCUPANCY at the end of
     * UPDATE_UNITS, before any subsequent phase reads the map.
     */
    public void setPath(Unit u, int[] newPath) {
        int oldDestX = NavigationService.pathDestX(u);
        int oldDestY = NavigationService.pathDestY(u);
        u.path = newPath;
        u.pathIdx = newPath.length == 0 ? 0 : 1;
        int newDestX;
        int newDestY;
        if (newPath.length > 0) {
            newDestX = newPath[newPath.length - 2];
            newDestY = newPath[newPath.length - 1];
        } else {
            newDestX = Integer.MIN_VALUE;
            newDestY = Integer.MIN_VALUE;
        }
        // Self-cell destinations don't claim occupancy in the original
        // setPath, so skip them on both sides to keep the queued deltas
        // pure no-ops-free.
        boolean hasOld = oldDestX != Integer.MIN_VALUE && (oldDestX != u.cellX || oldDestY != u.cellY);
        boolean hasNew = newDestX != Integer.MIN_VALUE && (newDestX != u.cellX || newDestY != u.cellY);
        if (!hasOld && !hasNew) return;
        damageService.applyOccupancyDelta(u,
                hasOld ? oldDestX : Integer.MIN_VALUE,
                hasOld ? oldDestY : Integer.MIN_VALUE,
                hasNew ? newDestX : Integer.MIN_VALUE,
                hasNew ? newDestY : Integer.MIN_VALUE);
    }

    /** Delegates to {@link DamageService#flushPendingOccupancyDeltas()}. Public so tests can force the drain after a direct {@code setPath} call. */
    public void flushPendingOccupancyDeltas() {
        damageService.flushPendingOccupancyDeltas();
    }

    /** Convenience: drop the unit's path. Equivalent to {@code setPath(u, GridPathfinder.EMPTY_PATH)}. */
    public void clearPath(Unit u) {
        setPath(u, GridPathfinder.EMPTY_PATH);
    }

    // advanceBursts moved to InfantryWeapons.tick — pumped from the tick loop
    // via `infantry.tick(this)`.

    /**
     * Ticks every queued {@link PendingDetonation}; when one's timer drains,
     * applies its splash damage at the endpoint and removes it from the list.
     * Reverse iteration for in-place removal.
     *
     * <p>This is the physics-based damage path — rockets / missiles fire,
     * fly visibly for {@code flightSec}, and damage whatever's at the endpoint
     * when they arrive (not when they launched). Friendly fire is ON: every
     * unit in radius takes damage regardless of faction.
     */
    // advancePendingDetonations + detonate moved to weapons/Detonations.
    // spawnMechWrecks moved to weapons/HeavyWeapons (pumped from heavy.tick).
    // Both subsystems own their own state; the sim just calls their tick()
    // from the tick loop and exposes spawnSmokingWreck + damageCell as
    // context primitives.

    /** Queues a {@link Projectile} for the per-tick advance. Called from the AoE projectile fire path in {@link #fireShotFrom}. */
    public void queueProjectile(Projectile p) {
        shots.queueProjectile(p);
    }

    /**
     * Dispatch entry point — pulls fall-back out so it applies to every role,
     * then routes to the role-specific behavior. PLANTER / OBJECTIVE_CAMPER /
     * VIP currently fall through to combatant behavior; mission-specific
     * subsystems (SABOTAGE first) override these branches as they land.
     */
    /**
     * Routes the per-tick update for one unit. Fall-back is a pre-dispatch
     * override so it applies regardless of role; otherwise the per-role
     * behavior instance handles the unit. Behavior classes live in
     * {@code battle.ai}; this method holds no per-role logic.
     */
    private void updateUnit(Unit u) {
        long t0 = System.nanoTime();
        TickInnerProfile.Bucket bucket;
        if (u.fallbackTimer > 0f) {
            FallbackBehavior.INSTANCE.update(u, this);
            bucket = TickInnerProfile.Bucket.BEHAVIOR_FALLBACK;
        } else {
            behaviorFor(u.role).update(u, this);
            bucket = innerBucketForRole(u.role);
        }
        // Route through TickInnerProfile.current() so workers in the parallel
        // dispatch write to their per-thread profile (ThreadLocal auto-init),
        // not directly to the canonical sim instance. mergeAllInto folds the
        // per-worker recordings into the canonical at the end of UPDATE_UNITS.
        TickInnerProfile.current().record(bucket, System.nanoTime() - t0);
    }

    /**
     * Maps a {@link UnitRole} to its inner-profile behavior bucket. Mirrors
     * {@link #behaviorFor(UnitRole)} — every role that returns the same
     * behavior instance there should map to the same bucket here, so the
     * inner profile partitions {@code updateUnit} time correctly across
     * behavior classes. Default falls into {@code BEHAVIOR_COMBATANT}
     * because {@code behaviorFor} also defaults to {@code CombatantBehavior}.
     */
    private static TickInnerProfile.Bucket innerBucketForRole(UnitRole role) {
        switch (role) {
            case KIT_RETRIEVER: return TickInnerProfile.Bucket.BEHAVIOR_KIT_RETRIEVER;
            case FLEE:          return TickInnerProfile.Bucket.BEHAVIOR_FLEE;
            case TURRET:        return TickInnerProfile.Bucket.BEHAVIOR_TURRET;
            case STRUCTURE:     return TickInnerProfile.Bucket.BEHAVIOR_STRUCTURE;
            case DRONE_HUB:     return TickInnerProfile.Bucket.BEHAVIOR_DRONE_HUB;
            case DRONE_PATROL:  return TickInnerProfile.Bucket.BEHAVIOR_GOAP_DRONE;
            default:            return TickInnerProfile.Bucket.BEHAVIOR_COMBATANT;
        }
    }

    private UnitBehavior behaviorFor(UnitRole role) {
        switch (role) {
            // PLANTER routes through CombatantBehavior → GoapInfantryBehavior.
            // The plant action is a squad-plan slot inside HoldPortalCordon
            // (Story J); the unit keeps role=PLANTER so ChargeSiteObjective.tick
            // still finds it on-site, but no longer has its own per-unit dispatch.
            case KIT_RETRIEVER:  return KitRetrieverBehavior.INSTANCE;
            case FLEE:           return FleeBehavior.INSTANCE;
            case TURRET:         return TurretBehavior.INSTANCE;
            case GARRISON:       return CombatantBehavior.INSTANCE;
            case PATROL:         return CombatantBehavior.INSTANCE;
            case STRUCTURE:      return StructureBehavior.INSTANCE;
            case DRONE_HUB:      return DroneHubBehavior.INSTANCE;
            case DRONE_PATROL:   return GoapDroneBehavior.INSTANCE;
            case OBJECTIVE_CAMPER:
            case VIP:
            case COMBATANT:
            default:             return CombatantBehavior.INSTANCE;
        }
    }

    /**
     * Refreshes {@link SquadAlertLevel} on every registered squad. Promotion
     * rules:
     * <ul>
     *   <li><b>ENGAGED</b> — any living squadmate has LOS to an alive enemy
     *       combatant. {@code timeSinceContact} resets to zero and
     *       {@code lastSeenEnemy} captures that enemy's cell.</li>
     *   <li><b>SUSPICIOUS</b> — no current LOS, but a squadmate is in a
     *       fall-back (recently hit). The squad converges on the last known
     *       enemy cell so a patrol that gets sniped doesn't keep walking
     *       its route obliviously.</li>
     *   <li><b>UNAWARE</b> — neither of the above. After
     *       {@link Squad#ENGAGED_DECAY_SECONDS} of no contact an ENGAGED
     *       squad drops to SUSPICIOUS, and after another
     *       {@link Squad#SUSPICIOUS_DECAY_SECONDS} a SUSPICIOUS squad drops
     *       to UNAWARE. The decay lets garrisons hold their state across
     *       brief duck-behind-cover moments and patrols commit to
     *       investigation before giving up.</li>
     * </ul>
     *
     * <p>Empty squads (all members dead) are left in their last state — the
     * GC cleans them up on save; the next tick's behaviors won't dispatch
     * because no member is alive.
     */
    /** Cell radius around a squadmate inside which an enemy shot's origin counts as "audible gunfire" and promotes the squad to SUSPICIOUS. Bigger than weapon ranges so a distant firefight pulls patrols in to investigate — that's the whole point. */
    public static final float GUNFIRE_ALERT_RADIUS = 18f;

    /**
     * Story A: cell range within which an enemy is considered "in the kill
     * zone" for a garrison squad's ambush trigger. The kill zone is intentionally
     * close — garrisons want the first shot to land, not to give away their
     * post at extreme range. {@code 8} cells matches typical rifle effective
     * range while staying short of the renderer's visibility horizon.
     */
    public static final int KILL_ZONE_RANGE_CELLS = 8;
    /**
     * Story A: consecutive sim ticks of LOS to a close enemy required before
     * the kill-zone gate trips. At {@code TICK_DT = 1/30 sec}, 6 ticks ≈ 0.2s
     * of stable sight — short enough to feel reflexive, long enough that a
     * single transient LoS frame (target dancing through a doorway) doesn't
     * spring the ambush prematurely.
     */
    public static final int KILL_ZONE_LOS_TICKS_THRESHOLD = 6;

    /**
     * Story A backstop: cumulative sim-seconds a garrison squad must take
     * LoS-confirmed incoming fire before the kill-zone gate is forced open,
     * regardless of whether the shooter ever entered the 8-cell kill zone.
     * Prevents a long-LOS attacker (mech with 30-40-cell range, distant
     * sniper) from chipping a holdsFireUntilKillZone garrison that's
     * forbidden to fire back. {@code 3s} is the user-feel sweet spot:
     * long enough that a one-off pot shot doesn't blow a planned ambush,
     * short enough that the squad isn't a free target for the rest of the
     * engagement.
     */
    public static final float KILL_ZONE_AMBUSH_BLOWN_SECONDS = 3.0f;

    /**
     * Single per-tick pass that fills in every squad's derived aggregates
     * (alive member count, centroid, alert level) and drives the
     * ENGAGED/SUSPICIOUS/UNAWARE state machine.
     *
     * <p>Restructured from the prior squads-outer/units-inner form into a
     * units-outer pass that posts each alive unit's contribution to its
     * squad in one walk: increments the alive count, accumulates centroid,
     * notes if any member is in fall-back, and (only if the squad isn't
     * already tagged ENGAGED this tick) runs a LoS scan for visible enemies.
     * Engaged squads short-circuit subsequent LoS scans for the same squad
     * within this tick.
     *
     * <p>The audible-gunfire promotion only runs for squads that finished the
     * first pass still un-engaged. Final state transitions are applied once
     * per squad at the end.
     */
    /**
     * Picks the closest still-alive squad member to {@code deadLeader}'s
     * last cell — the unit that takes over as squad leader. Returns null
     * if no member is alive (squad fully wiped this same tick). "Closest
     * to former leader" is the user-chosen promotion rule: it preserves
     * the squad's direction of travel through the promotion event, so
     * followers don't pivot when the badge changes hands mid-maneuver.
     */
    private Unit pickPromotionCandidate(Squad squad, Unit deadLeader) {
        Unit best = null;
        float bestDistSq = Float.MAX_VALUE;
        int lx = deadLeader.cellX;
        int ly = deadLeader.cellY;
        for (Unit u : units) {
            if (u == deadLeader || !u.isAlive()) continue;
            if (u.squadId != squad.id) continue;
            int dx = u.cellX - lx;
            int dy = u.cellY - ly;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = u;
            }
        }
        return best;
    }

    private void updateSquadAlertLevels() {
        // Per-tick transient flags. Boxed onto Squad to keep allocation out of
        // the hot path; reset at the top so a dead squad's leftover flags
        // don't leak into next tick.
        for (Squad squad : squads.values()) {
            squad.aliveMembers = 0;
            squad.centroidX = 0f;
            squad.centroidY = 0f;
            squad._engagedThisTick = false;
            squad._suspiciousThisTick = false;
            squad._killZoneSightedThisTick = false;
            squad._underFireAtLosThisTick = false;
        }

        // Pass 1: accumulate squad aggregates + per-squad engagement LoS.
        // For garrison squads (holdsFireUntilKillZone), also drive the kill-zone
        // LOS-stability counter — incremented when any squadmate has LOS to an
        // enemy within KILL_ZONE_RANGE_CELLS this tick, reset to 0 otherwise.
        // We track this independently of the early-exit ENGAGED flag because
        // the ENGAGED scan stops at the first sighted enemy, and we need the
        // tighter "close + visible" predicate for the kill-zone gate.
        for (Unit u : units) {
            if (!u.isAlive() || u.squadId == Unit.NO_SQUAD) continue;
            Squad squad = squads.get(u.squadId);
            if (squad == null) continue;
            squad.aliveMembers++;
            squad.centroidX += u.cellX;
            squad.centroidY += u.cellY;
            if (u.fallbackTimer > 0f) squad._suspiciousThisTick = true;

            // Kill-zone LOS scan for garrison squads only. Looks for ANY
            // squadmate with LOS to a close enemy combatant — a single
            // qualifying sighting per tick increments the counter for the
            // squad. The scan is keyed on holdsFireUntilKillZone so non-
            // garrison squads pay nothing.
            if (squad.holdsFireUntilKillZone && !squad._killZoneSightedThisTick) {
                for (Unit other : units) {
                    if (!other.isAlive() || other.faction == squad.faction) continue;
                    if (!other.type.combatant) continue;
                    int dx = other.cellX - u.cellX;
                    int dy = other.cellY - u.cellY;
                    if (dx * dx + dy * dy > KILL_ZONE_RANGE_CELLS * KILL_ZONE_RANGE_CELLS) continue;
                    if (!TacticalScoring.canSeePair(grid, u.cellX, u.cellY, other.cellX, other.cellY,
                            u.airLosRadius, other.airLosRadius)) continue;
                    squad._killZoneSightedThisTick = true;
                    break;
                }
            }

            // LoS scan only if no squadmate has tripped ENGAGED yet this tick —
            // one engaged squadmate is enough to commit the whole squad.
            if (squad._engagedThisTick) continue;
            for (Unit other : units) {
                if (!other.isAlive() || other.faction == squad.faction) continue;
                if (!other.type.combatant) continue;
                if (!TacticalScoring.canSeePair(grid, u.cellX, u.cellY, other.cellX, other.cellY,
                        u.airLosRadius, other.airLosRadius)) continue;
                squad._engagedThisTick = true;
                squad.lastSeenEnemyX = other.cellX;
                squad.lastSeenEnemyY = other.cellY;
                break;
            }
        }

        // Audible-gunfire promotion runs only for not-yet-engaged squads.
        // Iterate units once more, but only do the shot scan for squads that
        // still need promoting — the early-skip means engaged squads pay
        // nothing here.
        List<ShotEvent> activeShots = shots.getActiveShots();
        if (!activeShots.isEmpty()) {
            for (Unit u : units) {
                if (!u.isAlive() || u.squadId == Unit.NO_SQUAD) continue;
                Squad squad = squads.get(u.squadId);
                if (squad == null || squad._engagedThisTick || squad._suspiciousThisTick) continue;
                for (ShotEvent shot : activeShots) {
                    if (shot.shooterFaction == squad.faction) continue;
                    float dx = shot.fromX - (u.cellX + 0.5f);
                    float dy = shot.fromY - (u.cellY + 0.5f);
                    if (dx * dx + dy * dy <= GUNFIRE_ALERT_RADIUS * GUNFIRE_ALERT_RADIUS) {
                        squad._suspiciousThisTick = true;
                        squad.lastSeenEnemyX = Math.round(shot.fromX);
                        squad.lastSeenEnemyY = Math.round(shot.fromY);
                        break;
                    }
                }
            }
        }

        // Story A backstop: per-tick under-fire-at-LoS scan for garrison
        // squads with the kill-zone gate set. Mirrors WorldStateBuilder's
        // evalUnderFireAtLos predicate but runs every tick (not per replan)
        // so timeUnderSustainedFire accumulates accurately across the
        // 2-second replan window. Scoped to holdsFireUntilKillZone garrisons
        // because the field is only consumed by their gate override.
        if (!activeShots.isEmpty()) {
            for (Unit u : units) {
                if (!u.isAlive() || u.squadId == Unit.NO_SQUAD) continue;
                Squad squad = squads.get(u.squadId);
                if (squad == null || !squad.holdsFireUntilKillZone) continue;
                if (squad._underFireAtLosThisTick) continue;
                for (ShotEvent shot : activeShots) {
                    if (shot.shooterFaction == squad.faction) continue;
                    float dx = shot.toX - (u.cellX + 0.5f);
                    float dy = shot.toY - (u.cellY + 0.5f);
                    // Same 2-cell-squared "shot landed near me" gate the
                    // predicate evaluator uses — keeps the two paths in sync.
                    if (dx * dx + dy * dy > 4f) continue;
                    int fromCellX = (int) Math.floor(shot.fromX);
                    int fromCellY = (int) Math.floor(shot.fromY);
                    if (grid.hasLineOfSight(u.cellX, u.cellY, fromCellX, fromCellY)) {
                        squad._underFireAtLosThisTick = true;
                        break;
                    }
                }
            }
        }

        // Finalize: divide centroids, apply alert-state transitions.
        for (Squad squad : squads.values()) {
            if (squad.aliveMembers > 0) {
                squad.centroidX /= squad.aliveMembers;
                squad.centroidY /= squad.aliveMembers;
            }
            // Story A: garrison kill-zone LOS hysteresis. Increments when any
            // squadmate sighted a close enemy this tick; resets to 0 when no
            // close-LOS sighting was recorded. Only garrison squads keep this
            // counter — non-garrison squads have holdsFireUntilKillZone=false
            // and never get a sighting flagged (skipped in pass 1).
            if (squad.holdsFireUntilKillZone) {
                if (squad._killZoneSightedThisTick) {
                    if (squad.killZoneLosTicks < KILL_ZONE_LOS_TICKS_THRESHOLD) {
                        squad.killZoneLosTicks++;
                    }
                } else {
                    squad.killZoneLosTicks = 0;
                }
                // Story A backstop: accumulate sim-time under LoS-confirmed
                // incoming fire. Monotonic — never resets within a battle so
                // the ambush-blown threshold is a one-way door. Once it
                // crosses KILL_ZONE_AMBUSH_BLOWN_SECONDS, WorldStateBuilder
                // forces the kill-zone predicate true regardless of enemy
                // proximity.
                if (squad._underFireAtLosThisTick) {
                    squad.timeUnderSustainedFire += TICK_DT;
                }
            }
            if (squad._engagedThisTick) {
                squad.alertLevel = SquadAlertLevel.ENGAGED;
                squad.timeSinceContact = 0f;
            } else if (squad._suspiciousThisTick) {
                if (squad.alertLevel != SquadAlertLevel.ENGAGED) {
                    squad.alertLevel = SquadAlertLevel.SUSPICIOUS;
                }
                squad.timeSinceContact = 0f;
            } else {
                squad.timeSinceContact += TICK_DT;
                if (squad.alertLevel == SquadAlertLevel.ENGAGED
                        && squad.timeSinceContact >= Squad.ENGAGED_DECAY_SECONDS) {
                    squad.alertLevel = SquadAlertLevel.SUSPICIOUS;
                    // SQ-82 fix: at ENGAGED→SUSPICIOUS, the squad has gone
                    // ENGAGED_DECAY_SECONDS with no squadmate-LOS to anything.
                    // shouldKeepPursuing returns true for an invisible target
                    // when no closer visible enemy exists, so the stale target
                    // can outlive contact indefinitely (SQ-82: 8 marines kept
                    // walking toward a defender they hadn't seen in 13s,
                    // pinned against unreachable cells inside the target
                    // zone). Drop targets here; next behavior tick re-picks
                    // via findBestTarget — no LOS to anything = null target,
                    // which is the correct posture for a squad in SUSPICIOUS.
                    clearSquadMemberTargets(squad.id);
                } else if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                        && squad.timeSinceContact >= Squad.ENGAGED_DECAY_SECONDS + Squad.SUSPICIOUS_DECAY_SECONDS) {
                    squad.alertLevel = SquadAlertLevel.UNAWARE;
                    squad.lastSeenEnemyX = -1;
                    squad.lastSeenEnemyY = -1;
                    // Belt-and-braces: any target re-acquired during
                    // SUSPICIOUS (via a transient LOS flicker that didn't
                    // bump back to ENGAGED) shouldn't survive into UNAWARE.
                    clearSquadMemberTargets(squad.id);
                }
            }
        }
    }

    /**
     * Clears {@code u.target} for every alive squadmate of {@code squadId}.
     * Called from {@link #updateSquadAlertLevels} at the ENGAGED→SUSPICIOUS
     * (and SUSPICIOUS→UNAWARE) transitions so a stale target reference —
     * one {@link com.dillon.starsectormarines.battle.ai.TacticalScoring#shouldKeepPursuing
     * shouldKeepPursuing} happily keeps alive past LOS — doesn't drag the
     * squad toward an enemy they last saw seconds ago. Action {@code execute}
     * paths null-check {@code member.target} (they already cope with the
     * reprio-on-hit clear at line ~706), so the next behavior tick repicks
     * via {@link com.dillon.starsectormarines.battle.ai.TacticalScoring#findBestTarget
     * findBestTarget} or holds null if nobody's visible.
     */
    private void clearSquadMemberTargets(int squadId) {
        for (Unit u : units) {
            if (u.squadId == squadId) u.target = null;
        }
    }

    /**
     * Drives the FALLBACK_TO retreat chain for garrison squads. Runs once per
     * tick after {@link #updateSquadAlertLevels} so the fresh
     * {@link Squad#aliveMembers} is visible.
     *
     * <h2>Trigger pass</h2>
     * For each squad with an {@link Squad#assignedNode} that hasn't already
     * fired its one-shot fallback: if alive-strength drops to or below
     * {@link #FALLBACK_TRIGGER_RATIO} of {@link Squad#originalSize}, the squad
     * reassigns to the first {@link TacticalNode.LinkKind#FALLBACK_TO} target.
     * Each surviving member gets a new {@link Unit#homeCellX home cell} near
     * the new anchor (picked via {@link BattleSetup#pickCellsNear} so cover is
     * preserved at the new post). {@link Squad#fallbackInProgress} is set so
     * {@link com.dillon.starsectormarines.battle.ai.goap.actions.HoldPost} routes
     * members to their new homes regardless of alert level.
     *
     * <h2>Arrival pass</h2>
     * For each squad already mid-retreat: when every surviving member is
     * within {@link #HOME_ARRIVAL_RADIUS} of their new home, the in-progress
     * flag clears and the squad resumes normal garrison engagement at the
     * new post. The squad's alert level isn't reset — if there's still an
     * enemy in LoS, the next tick's promotion will pick it up.
     *
     * <p>Fallback is one-shot per squad to prevent cascading: a battered
     * garrison falls back once and then holds, even if the new post is also
     * overrun. Chained retreats would need explicit gating that we don't
     * have yet.
     */
    /**
     * Per-tick morale recovery + hysteresis flag update. Drain hooks live in
     * {@link #applyDamage} (per-hit + per-death); this pass only handles the
     * passive recovery side and the broken/cleared transitions.
     *
     * <p>Recovery gates on "haven't been shot at recently"
     * ({@link Squad#timeSinceUnderFire} {@code >=} {@link #MORALE_RECOVER_AFTER_FIRE_SECONDS}),
     * not on raw LoS. A broken squad behind imperfect cover can see — and
     * fire opportunistically at — distant enemies and still compose itself
     * once incoming hits/near-misses lull. Pre-fix, any LoS kept
     * {@code _engagedThisTick=true} and locked the squad broken indefinitely
     * once BreakContact's picker landed them on a least-exposed-but-not-
     * truly-hidden cell. Capped by {@code aliveMembers / originalSize} so a
     * squad that's lost half its members can't climb back above 0.5 no matter
     * how long they hide; a third casualty drops the cap to 0.25 and they
     * stay broken indefinitely. Squads with {@code originalSize == 0} (an
     * unstamped sentinel — shouldn't happen post-Story-B but guarded for
     * safety) treat the cap as 1.0.
     *
     * <p>Hysteresis: {@link Squad#moraleBroken} flips true when morale dips
     * below {@link #MORALE_BROKEN_THRESHOLD}; flips false again once morale
     * climbs above the (higher) {@link #MORALE_CLEAR_THRESHOLD}. The gap
     * prevents a squad hovering near 0.3 from flickering between
     * SurviveContact and EliminateEnemies on every replan.
     */
    private void updateSquadMorale() {
        // Near-miss drain pass: hostile shots that landed near a squadmate
        // but didn't connect still rattle the squad. Same cooldown gate as
        // hits — a hail of misses can't insta-break either. One drain event
        // per squad per tick max (the cooldown bails on the second pass).
        List<ShotEvent> shotsThisFrame = shots.getShotsThisFrame();
        if (!shotsThisFrame.isEmpty()) {
            for (ShotEvent shot : shotsThisFrame) {
                if (shot.hit) continue;
                Squad target = squadHitByMiss(shot);
                if (target == null) continue;
                // Mech squads don't take near-miss morale — their drain model
                // is HP-threshold only (per roadmap/ai/14-mech-stage1.md). A
                // mech that didn't catch a round isn't rattled by air.
                if (target.isMechSquad()) continue;
                // Recovery gate: a near-miss always resets the "under fire"
                // timer, even when the drain cooldown blocks the morale drop.
                target.timeSinceUnderFire = 0f;
                if (target.moraleDrainCooldown > 0f) continue;
                float cap = (target.originalSize > 0 && target.aliveMembers > 0)
                        ? (float) target.aliveMembers / target.originalSize
                        : 1f;
                float base = (cap > 0f) ? MORALE_DROP_ON_NEAR_MISS / cap : MORALE_DROP_ON_NEAR_MISS;
                float drop = base * shot.moraleImpact;
                target.morale = Math.max(0f, target.morale - drop);
                target.moraleDrainCooldown = MORALE_DRAIN_COOLDOWN;
            }
        }

        for (Squad squad : squads.values()) {
            if (squad.aliveMembers <= 0) continue;
            // Mech squads run a separate per-chassis morale pass — recovery,
            // hysteresis, hard cap, squad-level aggregation. The infantry
            // body below would otherwise drain {@link Squad#morale} on a flag
            // that isn't read for mech squads (predicate consults
            // {@link Squad#moraleBroken}, not raw morale).
            if (squad.isMechSquad()) {
                updateMechSquadMorale(squad);
                continue;
            }

            // Tick "time since under fire" before reading it. Saturate to
            // avoid overflow on long quiet stretches — the threshold check
            // only cares about >= MORALE_RECOVER_AFTER_FIRE_SECONDS.
            if (squad.timeSinceUnderFire < 1e9f) squad.timeSinceUnderFire += TICK_DT;

            float cap = (squad.originalSize > 0)
                    ? (float) squad.aliveMembers / squad.originalSize
                    : 1f;
            // Recovery gates on "haven't been shot at recently," not on raw
            // LoS. A broken squad that pulled back to cover with imperfect
            // hide (BreakContact's picker minimizes exposure but the
            // geometry doesn't always allow a true hide) can still see — and
            // fire back at — distant enemies. As long as no incoming
            // hits/near-misses arrive within MORALE_RECOVER_AFTER_FIRE_SECONDS,
            // they compose themselves. Pre-fix: any LoS kept _engagedThisTick
            // true forever, locking the squad in SurviveContact.
            if (squad.timeSinceUnderFire >= MORALE_RECOVER_AFTER_FIRE_SECONDS) {
                // Recovery rate scales with cap: a mauled squad recovers
                // proportionally slower toward their (lower) ceiling. With
                // base 0.20 this gives constant ~2.5s time-to-clear and
                // ~5s time-to-full-cap across all squad sizes. A solo
                // survivor's previous 1.25s full recovery was too fast to
                // read as "they're composing themselves."
                float rate = MORALE_RECOVERY_RATE * cap;
                squad.morale = Math.min(cap, squad.morale + rate * TICK_DT);
            } else {
                // Under fire — no recovery; also re-clamp in case the cap
                // dropped (member died this tick) below current morale.
                squad.morale = Math.min(cap, squad.morale);
            }

            // Tick down the drain cooldown so the next incoming hit /
            // near-miss can register.
            if (squad.moraleDrainCooldown > 0f) {
                squad.moraleDrainCooldown = Math.max(0f, squad.moraleDrainCooldown - TICK_DT);
            }

            // Thresholds scale with cap so the model stays coherent for
            // mauled squads. Solo cap = 0.25 → broken below 0.075, clears
            // above 0.125. Without scaling, the absolute clear threshold
            // (0.5) was above solo cap (0.25) and they could never recover.
            float brokenAt = MORALE_BROKEN_THRESHOLD * cap;
            float clearAt  = MORALE_CLEAR_THRESHOLD  * cap;
            if (squad.moraleBroken) {
                if (squad.morale > clearAt) squad.moraleBroken = false;
            } else {
                if (squad.morale < brokenAt) squad.moraleBroken = true;
            }
        }
    }

    /**
     * Per-mech morale tick + squad-level aggregation. Called from
     * {@link #updateSquadMorale} for each alive mech squad. For each member:
     * tick the under-fire timer, derive the cap (1.0 above the armor-gone HP
     * fraction, {@link #MECH_MORALE_ARMOR_GONE_CAP} below), recover passively
     * when out of fire, apply {@link MechLoadoutState#moraleBroken} hysteresis
     * with the mech thresholds. Then set {@link Squad#moraleBroken} from the
     * count of broken members — majority-broken trips the squad (one mech
     * cracking out of four isn't enough; two or more is).
     *
     * <p>Squad-level GOAP doesn't yet route only broken members to
     * BreakContact (the action just takes all members in one "any" slot);
     * once that lands, this aggregator could be relaxed to "any broken."
     * Today majority is what gives a stable squad-level signal.
     */
    private void updateMechSquadMorale(Squad squad) {
        int aliveMechs = 0;
        int brokenMechs = 0;
        for (Unit u : units) {
            if (!u.isAlive() || u.squadId != squad.id || u.mech == null) continue;
            aliveMechs++;
            MechLoadoutState m = u.mech;
            if (m.timeSinceUnderFire < 1e9f) m.timeSinceUnderFire += TICK_DT;

            float cap = (u.maxHp > 0f && u.hp < MECH_MORALE_ARMOR_GONE_HP_FRAC * u.maxHp)
                    ? MECH_MORALE_ARMOR_GONE_CAP
                    : 1.0f;
            if (m.timeSinceUnderFire >= MORALE_RECOVER_AFTER_FIRE_SECONDS) {
                float rate = MORALE_RECOVERY_RATE * MECH_MORALE_RECOVERY_RATE_MULT;
                m.morale = Math.min(cap, m.morale + rate * TICK_DT);
            } else {
                m.morale = Math.min(cap, m.morale);
            }

            float brokenAt = MECH_MORALE_BROKEN_THRESHOLD * cap;
            float clearAt  = MECH_MORALE_CLEAR_THRESHOLD  * cap;
            if (m.moraleBroken) {
                if (m.morale > clearAt) m.moraleBroken = false;
            } else {
                if (m.morale < brokenAt) m.moraleBroken = true;
            }
            if (m.moraleBroken) brokenMechs++;
        }
        squad.moraleBroken = aliveMechs > 0 && (brokenMechs * 2 >= aliveMechs);
    }

    /**
     * Returns the friendly squad most affected by {@code shot} as a near
     * miss — first squad whose member is within {@link #NEAR_MISS_RADIUS_SQ}
     * cells of the shot's endpoint. Returns null when the shot is a self-
     * faction shot, no squad member is in range, or the shooter's faction
     * matches the candidate. One assignment per shot — a stray that grazes
     * two squad members only rattles one of them (the first found), which
     * matches the "single drain event per shot" intent.
     */
    private Squad squadHitByMiss(ShotEvent shot) {
        for (Squad sq : squads.values()) {
            if (sq.aliveMembers <= 0) continue;
            if (sq.faction == shot.shooterFaction) continue;
            for (Unit member : units) {
                if (!member.isAlive() || member.squadId != sq.id) continue;
                float dx = shot.toX - (member.cellX + 0.5f);
                float dy = shot.toY - (member.cellY + 0.5f);
                if (dx * dx + dy * dy <= NEAR_MISS_RADIUS_SQ) return sq;
            }
        }
        return null;
    }

    /**
     * Advances a unit one tick along its current path. Public so behaviors
     * call this after re-pathing or as the last step of their per-tick
     * update.
     */
    public void advanceMovement(Unit u) {
        if (u.pathIdx >= u.pathCellCount()) return;

        int nextX = u.pathCellX(u.pathIdx);
        int nextY = u.pathCellY(u.pathIdx);
        float dx = nextX - u.cellX;
        float dy = nextY - u.cellY;
        float cellDist = (float) Math.sqrt(dx * dx + dy * dy);
        if (cellDist < 0.0001f) {
            u.pathIdx++;
            return;
        }

        float stepLength = u.moveSpeed * TICK_DT; // cell-units this tick
        u.moveProgress += stepLength / cellDist;

        if (u.moveProgress >= 1f) {
            u.cellX = nextX;
            u.cellY = nextY;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
            u.moveProgress = 0f;
            u.pathIdx++;
        } else {
            u.renderX = u.cellX + dx * u.moveProgress;
            u.renderY = u.cellY + dy * u.moveProgress;
        }
    }

    /**
     * Stanced-fire convenience: most callers fire from a stationary position
     * (engage loops, garrisons, turrets, mech chassis) and don't need to
     * think about stance. Routes to {@link #fireShot(Unit, Unit,
     * com.dillon.starsectormarines.battle.weapons.FireStance)} with
     * {@link com.dillon.starsectormarines.battle.weapons.FireStance#STANCED}.
     * Callers firing while walking should call the stance-aware overload
     * with {@code MOVING} so the accuracy penalty applies.
     */
    public void fireShot(Unit shooter, Unit target) {
        fireShot(shooter, target, com.dillon.starsectormarines.battle.weapons.FireStance.STANCED);
    }

    /**
     * Stance-aware fire. {@link com.dillon.starsectormarines.battle.weapons.FireStance#STANCED}
     * preserves the base accuracy roll;
     * {@link com.dillon.starsectormarines.battle.weapons.FireStance#MOVING}
     * halves it. Implementation lives in
     * {@code battle/weapons/InfantryWeapons.java}; this method exists so AI
     * behaviors can call {@code sim.fireShot(...)} without reaching into the
     * subsystem accessor.
     */
    public void fireShot(Unit shooter, Unit target,
                         com.dillon.starsectormarines.battle.weapons.FireStance stance) {
        infantry.fireShot(this, shooter, target, stance);
    }

    /**
     * Delegates to {@link InfantryWeapons#fireSecondary}. Same delegation
     * rationale as {@link #fireShot}.
     */
    public void fireSecondary(Unit shooter, Unit target) {
        infantry.fireSecondary(this, shooter, target);
    }

    /**
     * Float-origin counterpart to {@link #fireShot(Unit, Unit)} for shooters
     * that aren't on the unit list — today, shuttle-mounted turrets fired by
     * {@link com.dillon.starsectormarines.battle.air.AirSystem}. Stats come
     * from {@code kind}; origin is a world-space float pair so a hovering
     * shuttle's mount fires from its actual rendered position rather than the
     * floored cell center.
     *
     * <p>Damage / cover / fall-back / death pipeline is identical to
     * {@link #fireShot} — implemented in terms of the same {@link WeaponSimContext}
     * methods so a single change to applyDamage or rollFallbackOnHit picks up
     * both fire paths. The vsTurret bonus is fixed at 1.0 because
     * {@link TurretKind} doesn't carry a per-kind multiplier yet; if mounted
     * heavy mortars ever want extra punch vs static defenses, this is the
     * lever.
     */
    public void fireShotFrom(float fromX, float fromY, Faction shooterFaction,
                             TurretKind kind, Unit target, boolean aerialShooter) {
        // Default to hasLos=true — direct-fire callers (every existing path)
        // already gate LoS in the aim loop, so by the time the shot is taken,
        // the shooter sees the target. Indirect-fire callers use the explicit
        // overload to pass the actual LoS state.
        fireShotFrom(fromX, fromY, shooterFaction, kind, target, aerialShooter, /*hasLos*/ true);
    }

    /**
     * LoS-aware fire. For indirect-fire kinds ({@link TurretKind#indirectFire})
     * applies two accuracy modifiers on top of the kind's base accuracy:
     * <ul>
     *   <li><b>Quadratic distance falloff</b> — effective accuracy scales by
     *       {@code 1 - (d/range)²}. Preserves base accuracy near the battery,
     *       drops off fast at the edge of envelope. Direct-fire kinds skip
     *       this; their effective accuracy is the flat per-kind value.</li>
     *   <li><b>No-LoS multiplier</b> — when {@code hasLos} is false, accuracy
     *       is also multiplied by {@link TurretKind#noLosAccuracyMult}.
     *       Reads as "battery firing on a spotted target without optics on it
     *       — the salvo lands in the area but individual rockets fly wider."
     *       Mirrors the mech LRM path's
     *       {@link com.dillon.starsectormarines.battle.MechWeapon#LRM_NO_LOS_ACC_MULT}.</li>
     * </ul>
     */
    public void fireShotFrom(float fromX, float fromY, Faction shooterFaction,
                             TurretKind kind, Unit target, boolean aerialShooter, boolean hasLos) {
        float distToTarget = (float) Math.sqrt(
                (target.cellX + 0.5f - fromX) * (target.cellX + 0.5f - fromX) +
                (target.cellY + 0.5f - fromY) * (target.cellY + 0.5f - fromY));
        float effectiveAccuracy = kind.accuracy;
        if (kind.indirectFire) {
            float distNorm = Math.min(1f, distToTarget / Math.max(0.0001f, kind.range));
            float distFalloff = Math.max(0f, 1f - distNorm * distNorm);
            float losMult = hasLos ? 1f : kind.noLosAccuracyMult;
            effectiveAccuracy *= distFalloff * losMult;
        }

        // Simulated-projectile path: kinds with a real velocity (cellsPerSec
        // > 0) spawn a Projectile entity that travels at dist/cellsPerSec,
        // detonates AoE on arrival, and is queryable mid-flight (point
        // defense future). Per the simplified model, no hit/miss roll —
        // accuracy widens the scatter cone, the AoE radius decides what gets
        // hurt at detonation.
        if (kind.cellsPerSec() > 0f) {
            spawnProjectile(fromX, fromY, shooterFaction, kind, target, aerialShooter,
                    distToTarget, effectiveAccuracy);
            return;
        }

        // Legacy path — direct-fire tracers (VULCAN, ARBALEST, HEPHAESTUS,
        // HEAVY_MG, ...) still roll hit/miss and emit a ShotEvent + (for AoE
        // sub-kinds) a PendingDetonation. Velocity is implicit in flightSec.
        boolean hit = rng.nextFloat() < effectiveAccuracy;
        boolean isAoe = kind.aoeRadius > 0f;
        // Aerial delivery if the shooter is elevated (shuttle mount) or the
        // weapon kind inherently lobs (grenade launcher). Used to decide
        // whether intact roofs intercept this shot.
        boolean aerialDelivery = aerialShooter || kind.arcHeight > 0f;
        float effectiveSpread = kind.hitSpread * Math.min(1f, distToTarget / kind.range);

        // Compute the visual endpoint with hit-spread / miss-scatter first —
        // damage application is deferred until after the wall raycast so a
        // raycast-blocked shot doesn't telepathically land damage past the
        // wall it actually splattered on.
        float toX, toY;
        if (hit) {
            toX = target.cellX + 0.5f;
            toY = target.cellY + 0.5f;
            // Endpoint scatter on a hit — purely visual for direct-fire kinds,
            // but for AoE it also scatters the splash center so a 4-round
            // grenade burst sprays the cell cluster instead of stacking on one.
            if (effectiveSpread > 0f) {
                float angle = rng.nextFloat() * (float) (Math.PI * 2);
                float r = rng.nextFloat() * effectiveSpread;
                toX += (float) Math.cos(angle) * r;
                toY += (float) Math.sin(angle) * r;
            }
        } else {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            // Misses get the baseline near-miss scatter plus the kind's
            // distance-scaled spread — a stray salvo at close range scatters
            // less than a stray salvo at long range.
            spread += effectiveSpread;
            toX = target.cellX + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.cellY + 0.5f + (float) Math.sin(angle) * spread;
        }
        // Wall raycast — for ground-deployed area-spread weapons, a scattered
        // round that would fly past a wall instead splatters on that wall.
        // Air-mounted variants leave kind.raycastShots = false.
        com.dillon.starsectormarines.battle.weapons.ShotRaycast.Result snapped =
                com.dillon.starsectormarines.battle.weapons.ShotRaycast.resolve(
                        grid, kind.raycastShots, fromX, fromY, toX, toY, hit);
        toX = snapped.toX();
        toY = snapped.toY();
        hit = snapped.hit();
        // Direct-fire damage — applied after raycast so a wall-blocked shot
        // can correctly count as a miss. AoE kinds skip this path; their
        // damage resolves at endpoint via the Detonations pipeline below.
        if (!isAoe && hit) {
            // Aerial direct-fire (shuttle ARBALEST / HEPHAESTUS strafing) is
            // intercepted by an intact roof — the round physically can't
            // reach the unit underneath. Ground direct-fire (turret shooting
            // through a doorway) is governed by the 2D LOS check inside the
            // raycast and doesn't need this shield.
            if (!aerialDelivery || !isRoofShielded(target)) {
                applyDamage(target, kind.damage, 1f);
                rollFallbackOnHit(target);
            }
        }
        // AoE path — register the detonation on the queue. Lifetime matches
        // the projectile's visible flight time so the explosion lines up with
        // the rendered round arriving at the endpoint.
        if (isAoe) {
            float flight = kind.flightSec > 0f ? kind.flightSec : SHOT_LIFETIME;
            queueDetonation(new com.dillon.starsectormarines.battle.PendingDetonation(
                    toX, toY, flight,
                    kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                    kind.wallDamage, shooterFaction, aerialDelivery));
        }
        float lifetime = kind.flightSec > 0f ? kind.flightSec : SHOT_LIFETIME;
        postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooterFaction,
                lifetime, kind, null, null));
    }

    /**
     * Simulated-projectile fire path. Used by {@link #fireShotFrom} when the
     * kind has {@code cellsPerSec > 0}.
     *
     * <p>No hit/miss roll — accuracy widens the scatter cone, the AoE radius
     * at detonation decides who gets hurt. Endpoint is sampled from a uniform
     * circle around the target cell with radius
     * {@code hitSpread × min(1, dist/range) × (2 - effectiveAccuracy)}: a
     * perfect-accuracy shot has scatter equal to the base spread (still
     * non-zero — even precision artillery has dispersion), a half-accuracy
     * shot doubles it, a no-LoS shot with quadratic falloff at max range
     * goes to maximum scatter. Combined with the kind's
     * {@link Projectile#onArrival} AoE radius, the spray pattern of a salvo
     * blankets a believably wide area instead of stacking all rockets on the
     * target cell.
     *
     * <p>Builds a {@link PendingDetonation} as the projectile's arrival
     * payload — same damage / AoE / wall / roof / friendly-fire logic as the
     * legacy queueDetonation path, just owned by the Projectile so a future
     * point-defense intercept can cancel it atomically.
     */
    private void spawnProjectile(float fromX, float fromY, Faction shooterFaction,
                                 TurretKind kind, Unit target, boolean aerialShooter,
                                 float distToTarget, float effectiveAccuracy) {
        boolean aerialDelivery = aerialShooter || kind.arcHeight > 0f;

        // Scatter scales with (1 - accuracy) — at acc=1.0 the multiplier is
        // 1.0 (still some dispersion via hitSpread), at acc=0.0 it doubles.
        // Tied to the distance-scaled effectiveSpread so close shots still
        // cluster tightly even at low accuracy.
        float distScale = Math.min(1f, distToTarget / Math.max(0.0001f, kind.range));
        float accScatterMult = 2f - Math.max(0f, Math.min(1f, effectiveAccuracy));
        float scatterRadius = kind.hitSpread * distScale * accScatterMult;
        float angle = rng.nextFloat() * (float) (Math.PI * 2);
        float r = rng.nextFloat() * scatterRadius;
        float toX = target.cellX + 0.5f + (float) Math.cos(angle) * r;
        float toY = target.cellY + 0.5f + (float) Math.sin(angle) * r;

        float flightTime = distToTarget / kind.cellsPerSec();

        PendingDetonation onArrival = new PendingDetonation(
                toX, toY, flightTime,
                kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                kind.wallDamage, shooterFaction, aerialDelivery);
        queueProjectile(new Projectile(fromX, fromY, toX, toY,
                kind, shooterFaction, aerialDelivery, flightTime, onArrival));
        // Pair with a ShotEvent so the existing audio (shotsThisFrame) +
        // impact-FX (shotsExpiredThisFrame) dispatchers run unchanged. Same
        // flight time keeps the two in sync — they expire on the same tick,
        // so arrival FX line up with the projectile reaching its endpoint.
        // The renderer skips projectile-sprite drawing for ShotEvents whose
        // kind is on the simulated path (cellsPerSec > 0) and reads position
        // from the paired Projectile instead.
        postShot(new ShotEvent(fromX, fromY, toX, toY, /*hit*/ true, shooterFaction,
                flightTime, kind, null, null));
    }

    /**
     * Delegates to {@link HeavyWeapons#fireMechWeapon}. Kept on the sim's
     * surface because AI behaviors call {@code sim.fireMechWeapon(...)}
     * directly. Implementation lives in {@code battle/weapons/HeavyWeapons.java}.
     */
    public void fireMechWeapon(Unit shooter, Unit target, MechWeapon weapon) {
        heavy.fireMechWeapon(this, shooter, target, weapon);
    }

    /**
     * Delegates to {@link HeavyWeapons#fireMechWeapon} with explicit accuracy
     * multiplier. Used by the LRM indirect-fire path (no LOS = reduced acc).
     */
    public void fireMechWeapon(Unit shooter, Unit target, MechWeapon weapon, float accuracyMult) {
        heavy.fireMechWeapon(this, shooter, target, weapon, accuracyMult);
    }

    // advanceMechWeapons + spawnMechWrecks moved to HeavyWeapons.tick.

    /**
     * Applies damage from an external source (flyby strafing run) to a unit
     * already tracked by the sim. Mirrors the post-hit half of {@link #fireShot}:
     * cover-reduces, applies HP, emits death + equipment drop if applicable.
     * Skips accuracy roll (the overlay already decided the round connected) and
     * fall-back (strafes pin you down rather than break contact). No
     * {@link ShotEvent} is emitted — flyby tracers are drawn by the overlay
     * itself, not the ground combat tracer pass.
     */
    public void applyExternalDamage(Unit target, float damage) {
        if (target == null || !target.isAlive() || damage <= 0f) return;
        int cover = grid.getCoverAt(target.cellX, target.cellY);
        float dr = COVER_DAMAGE_REDUCTION[Math.min(cover, COVER_DAMAGE_REDUCTION.length - 1)];
        target.hp -= damage * (1f - dr);
        if (!target.isAlive()) {
            target.deathPoseIdx = rng.nextInt(4);
            deathsThisFrame.add(target);
            equipmentDropService.emitIfApplicable(target);
        }
    }


}
