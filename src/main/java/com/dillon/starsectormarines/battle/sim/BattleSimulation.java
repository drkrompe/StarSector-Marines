package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.drone.DroneHubUnit;
import com.dillon.starsectormarines.battle.equipment.EquipmentDrop;
import com.dillon.starsectormarines.battle.fx.Decal;
import com.dillon.starsectormarines.battle.fx.DecalKind;
import com.dillon.starsectormarines.battle.fx.PendingDetonation;
import com.dillon.starsectormarines.battle.fx.Projectile;
import com.dillon.starsectormarines.battle.fx.ShotEvent;
import com.dillon.starsectormarines.battle.fx.SmokingWreck;
import com.dillon.starsectormarines.battle.map.Doodad;
import com.dillon.starsectormarines.battle.map.DoodadService;
import com.dillon.starsectormarines.battle.map.MapVehicle;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitDestinationSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.weapons.MechLoadoutState;
import com.dillon.starsectormarines.battle.weapons.MechWeapon;

import com.dillon.starsectormarines.battle.air.AirSystem;
import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.compound.CompoundCaptureSystem;
import com.dillon.starsectormarines.battle.compound.CompoundService;
import com.dillon.starsectormarines.battle.fx.EffectsService;
import com.dillon.starsectormarines.battle.ground.GroundSystem;
import com.dillon.starsectormarines.battle.ground.Vehicle;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.command.MissionCommand;
import com.dillon.starsectormarines.battle.damage.DamageResolver;
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
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vision.VisionService;
import com.dillon.starsectormarines.battle.weapons.Detonations;
import com.dillon.starsectormarines.battle.weapons.HeavyWeapons;
import com.dillon.starsectormarines.battle.weapons.InfantryWeapons;
import com.dillon.starsectormarines.battle.weapons.WeaponSimContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

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
public class BattleSimulation implements WeaponSimContext {

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

    /** Sim seconds a tracer stays visible after being fired. */
    private static final float SHOT_LIFETIME = 0.15f;
    /** Min/max near-miss offset (cells) from target cell-center on a missed shot. */
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

    /** Probability a hit puts the target into fall-back. Rolled once per hit; ignored if already falling back. */
    private static final float FALLBACK_CHANCE   = 0.25f;
    /** Sim seconds a unit stays in fall-back state once entered. After this, normal engagement resumes. */
    private static final float FALLBACK_DURATION = 3.5f;

    /** Navigation slice: grid + topology + zone graph + occupancy map + spatial indices + vantage cache + LosCache lifecycle. {@link #grid} / {@link #topology} / {@link #zoneGraph} / {@link #occupancyMap} / {@link #unitIndex} / {@link #destIndex} below are alias fields that share the same instances. */
    private final NavigationService navigation;
    /** Alias of {@link NavigationService#getGrid()}. Same instance — kept as a field so the sim's 80+ {@code grid.*} reads don't pay a per-call accessor hop. */
    private final NavigationGrid grid;
    /** Alias of {@link NavigationService#getTopology()}. */
    private final CellTopology topology;
    /** Roster service: units list + squad registry + spawn queue + ID counters. {@link #units} is an alias field that shares the same {@link List} instance. */
    private final UnitRosterService rosterService;
    /** Alias of {@link UnitRosterService#getUnits()}. Same {@link List} instance — kept as a field so the sim's iteration / size / subList reads don't pay a per-call accessor hop. */
    private final List<Unit> units;
    private final AirSystem airSystem;
    private final GroundSystem groundSystem;
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
    /** Per-tick squad alert / awareness driver — drives the ENGAGED/SUSPICIOUS/UNAWARE state machine + kill-zone gating + audible-gunfire promotion. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.squad.SquadAlertSystem squadAlert;
    /** Per-tick squad morale recovery + hysteresis + near-miss drain. Drain on hit/death fires from {@link DamageResolver#resolve}; this system owns the passive recovery + flag transitions. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.squad.SquadMoraleSystem squadMorale;
    /** Per-tick squad-level GOAP replan pass — dispatches each squad to drone / mech / infantry behavior. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.squad.SquadReplanSystem squadReplan;
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
    /** Per-faction strategic commander tier. Owns the slow-tick cadence; the {@link #setCommander}/{@link #getCommander} delegates below forward here, and the COMMANDER phase calls {@link CommanderService#tick}. */
    private final CommanderService commanders = new CommanderService();

    /** Reinforcement orchestration — trigger registry + means provider list + request queue. Mission setup registers triggers/means; the slow-tick polls them. Full design: {@code roadmap/reinforcement/architecture.md}. */
    private final ReinforcementService reinforcement =
            new ReinforcementService();

    /** Per-compound capture state — defender supply structures (COMMAND_POST / BARRACKS / ARMORY) and their DEFENDER_HELD / CONTESTED / MARINE_HELD state. Populated from the {@link TacticalMap} in {@link #setTacticalMap}; ticked by {@link #compoundCapture}. Slice 1 of the central-keep design ({@code roadmap/conquest/central-keep.md}). */
    private final CompoundService compoundService = new CompoundService();
    /** Stateless tick consumer that drives the compound capture state machine. Reads zone occupancy, writes {@link #compoundService} records on its slow-tick cadence. */
    private final CompoundCaptureSystem compoundCapture = new CompoundCaptureSystem();

    /**
     * Per-target attacker index — wraps the {@code Unit → attacker list} map
     * that drives O(1)-lookup crowding scoring in
     * {@link com.dillon.starsectormarines.battle.ai.TacticalScoring}. Rebuilt
     * once at tick top in the serial phase; read in parallel during
     * UPDATE_UNITS against the frozen snapshot. Sibling slice to
     * {@link #rosterService} / {@link #navigation}; constructed after the
     * roster since {@link com.dillon.starsectormarines.battle.ai.AttackerIndexService#rebuild()}
     * iterates {@link UnitRosterService#getUnits()}.
     */
    private final com.dillon.starsectormarines.battle.ai.AttackerIndexService attackerIndex;

    /** Shared scoring service for target selection, firing-position, fallback, and cover queries. Constructor-injected with NavigationService, UnitRosterService, AttackerIndexService, ShotService, DoodadService. */
    private final com.dillon.starsectormarines.battle.ai.TacticalScoring tacticalScoring;

    /** In-flight tracers + projectiles + per-frame event drains. Sibling slice to {@link #effects} / {@link #vision}; the {@link #postShot}, {@link #queueProjectile}, {@link #getActiveShots} et al. delegates below forward here. */
    private final ShotService shots = new ShotService();
    /** Units that transitioned from alive to dead during the last {@link #advance(float)} call. Same lifecycle as {@link #shotsThisFrame}. */
    private final List<Unit> deathsThisFrame = new ArrayList<>();
    /**
     * Parallel-dispatch safety queues + the {@code insideParallel} flag.
     * Owns the SoA damage queue + {@link PendingTargetMutation} and
     * {@link PendingOccupancyDelta} queues with their pools. Constructed in
     * the sim ctor with bound method-ref appliers for each kind, so the
     * {@code applyDamage} / {@code applyReprio} / {@code applyFallback} /
     * {@code applyOccupancyDelta} delegates below forward straight in.
     */
    private final DamageService damageService;
    /** Stateless body of {@code applyDamage} — cover-curve / HP write / death cascade / leader promotion / morale drain. Wired into {@link #damageService} as the damage applier so inline and queued paths share semantics. */
    private final DamageResolver damageResolver;
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

    /** Owns the parallel UPDATE_UNITS dispatch + the worker {@code ForkJoinPool} + per-role behavior dispatch. This is the entity-for-loop seam — see the class doc for the ECS/SoA promotion plan. */
    private final com.dillon.starsectormarines.battle.ai.UnitUpdateSystem unitUpdate;
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
        // DamageService construction is staged: the resolver needs the roster
        // (squad map + units list) and the equipment-drop service, both of
        // which we build right after. We construct the service second and
        // wire it with damageResolver::resolve as the applier method ref.
        this.rosterService = new UnitRosterService(unitIndex, null);
        // Alias-fields share the same collection instances as the service so
        // the internal `units` iteration stays direct (no per-call accessor
        // hop). Squad reads now route through rosterService directly — the
        // squads alias was dropped when SquadReplanSystem absorbed the last
        // inline iterator.
        this.units = rosterService.getUnits();
        this.equipmentDropService = new EquipmentDropService(rosterService, this::clearPath);
        this.damageResolver = new DamageResolver(
                navigation, rosterService, equipmentDropService,
                deathsThisFrame::add, rng);
        this.damageService = new DamageService(
                damageResolver::resolve,
                this::writeReprioInline,
                this::writeFallbackInline,
                navigation::applyOccupancyDeltaInline);
        rosterService.setDamageService(damageService);
        this.turretDemolition = new com.dillon.starsectormarines.battle.turret.TurretDemolitionSystem(
                navigation, effects, tactical, rosterService);
        this.hubDemolition = new com.dillon.starsectormarines.battle.drone.HubDemolitionSystem(
                navigation, effects, rosterService);
        this.droneCrashes = new com.dillon.starsectormarines.battle.drone.DroneCrashSystem(
                navigation, effects);
        this.squadFallback = new com.dillon.starsectormarines.battle.squad.SquadFallbackSystem(
                navigation, rosterService, this::clearPath);
        this.squadAlert = new com.dillon.starsectormarines.battle.squad.SquadAlertSystem(
                navigation, rosterService, shots);
        this.squadMorale = new com.dillon.starsectormarines.battle.squad.SquadMoraleSystem(
                rosterService, shots);
        this.squadReplan = new com.dillon.starsectormarines.battle.squad.SquadReplanSystem(rosterService);
        this.attackerIndex = new com.dillon.starsectormarines.battle.ai.AttackerIndexService(rosterService);
        this.tacticalScoring = new com.dillon.starsectormarines.battle.ai.TacticalScoring(
                navigation, rosterService, attackerIndex, shots, doodadService);
        this.unitUpdate = new com.dillon.starsectormarines.battle.ai.UnitUpdateSystem(
                rosterService.getRegistry(), damageService, tickInnerProfile);
        this.airSystem = new AirSystem(navigation, rosterService, tacticalScoring, rng, this::addUnit);
        this.groundSystem = new GroundSystem(navigation, rosterService, tacticalScoring, rng, this::addUnit);
        vision.init(grid, 256);
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
        return topology.getBuildingId(target.getCellX(), target.getCellY()) != 0
                && !topology.isRoofDestroyed(target.getCellX(), target.getCellY());
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
                ? com.dillon.starsectormarines.battle.fx.DecalKind.RUBBLE.index
                : com.dillon.starsectormarines.battle.fx.DecalKind.RUBBLE_ALT.index;
        addDecal(new com.dillon.starsectormarines.battle.fx.Decal(
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
    /** Fog-of-war service — per-cell reveal state + per-unit visibility. The renderer reads this for the fog overlay and unit visibility gate. */
    public VisionService getVision() { return vision; }
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
    /** Thread-safe snapshot of active projectiles for callers iterating during the parallel UPDATE_UNITS dispatch (today: squad-coordination scorers checking projected rocket damage). See {@link com.dillon.starsectormarines.battle.shots.ShotService#snapshotActiveProjectiles()}. */
    public List<Projectile> snapshotActiveProjectiles() { return shots.snapshotActiveProjectiles(); }
    /** Projectiles that arrived this tick — parallel to {@link #getShotsExpiredThisFrame} for the renderer's impact-FX dispatch. */
    public List<Projectile> getProjectilesArrivedThisFrame() { return shots.getProjectilesArrivedThisFrame(); }
    public List<Unit> getDeathsThisFrame()     { return deathsThisFrame; }
    public boolean isComplete()            { return complete; }
    public Faction getWinner()             { return winner; }
    /** Per-cell unit count, indexed by {@link NavigationGrid#index(int, int)}. Exposed for AI scoring; do not mutate directly — go through {@link #setPath}. */
    public byte[] getOccupancyMap()        { return occupancyMap; }
    /** Bucketed spatial index over alive units. Rebuilt at the top of each tick by {@link #tick()}. */
    public UnitSpatialIndex getUnitIndex() { return unitIndex; }
    /**
     * Dense entity registry for SoA consumers — bulk readers that want to
     * iterate {@code [0, liveCount())} over {@link UnitRegistry#denseArray()}
     * and pull cellX/cellY/hp/maxHp from the parallel primitive arrays.
     * Same registry instance {@link #targetOf(Unit)} and the spatial-index
     * rebuilds already use; exposed here so static scorers (TacticalScoring,
     * etc.) can match the established consumer pattern without threading
     * the roster service through every helper signature.
     */
    public UnitRegistry getUnitRegistry() { return rosterService.getRegistry(); }
    /**
     * Resolves a unit's {@link Unit#targetId} to the current target reference,
     * or {@code null} when the target was released / never set. Short delegate
     * over {@link com.dillon.starsectormarines.battle.unit.UnitRegistry#getOrNull(long)};
     * the canonical read path replacing the old {@code u.target} field.
     */
    public Unit targetOf(Unit u) {
        return rosterService.getRegistry().getOrNull(u.targetId);
    }

    /**
     * Resolves an arbitrary entity id to its {@link Unit}, or {@code null} when
     * the id is unknown / released. The generic counterpart to
     * {@link #targetOf(Unit)} — used by readers of the secondary
     * id-typed fields ({@link Unit#burstTargetId},
     * {@link Unit#secondaryAimTargetId}, {@link com.dillon.starsectormarines.battle.turret.MapTurret#burstTargetId})
     * where there's no companion holder unit to thread.
     */
    @Override
    public Unit resolveUnit(long id) {
        return rosterService.getRegistry().getOrNull(id);
    }
    /** Bucketed spatial index over alive units keyed on path destination (not current cell). Rebuilt alongside {@link #unitIndex} each tick. */
    public UnitDestinationSpatialIndex getDestIndex() { return destIndex; }
    /** Per-phase wall-clock profile of the most recent completed window of ticks. Read by the {@code TickProfileDebugPanel} HUD overlay + dump-to-disk button. */
    public TickProfile getTickProfile() { return tickProfile; }
    /** Per-tick sub-step profile (per-behavior + per-primitive nanos). Reset every tick; snapshotted onto the spike record when one fires. Read by the JSON dumper. */
    public TickInnerProfile getTickInnerProfile() { return tickInnerProfile; }
    @Override public Random getRng()       { return rng; }
    /** Shared scoring service — target selection, firing-position, fallback, cover queries. Thread-safe for reads (constructor-injected immutable service refs). */
    public com.dillon.starsectormarines.battle.ai.TacticalScoring getTacticalScoring() { return tacticalScoring; }
    /** Delegates to {@link UnitRosterService#getSquad(int)}. Synchronized lookup; safe to call from the parallel UPDATE_UNITS dispatch (concurrent {@link #mintSquad} from drone-hub spawns publishes through the same monitor). */
    public Squad getSquad(int id) {
        return rosterService.getSquad(id);
    }
    /** All squads currently registered. Used by the per-tick alert update; behaviors should read individual squads via {@link #getSquad(int)} keyed off {@link Unit#squadId}. */
    public Collection<Squad> getSquads()   { return rosterService.getSquads(); }
    /** Tactical hint graph produced by the map generator. Never null; an empty graph for legacy maps. */
    public TacticalMap getTacticalMap()    { return tactical.getTacticalMap(); }
    /** Set the tactical map for this battle. Called once by {@code BattleSetup} right after construction, before the first {@link #advance} call. */
    public void setTacticalMap(TacticalMap map) {
        tactical.setTacticalMap(map);
        // Compound capture layer needs the COMMAND_POST/BARRACKS/ARMORY nodes
        // registered before the first tick — slice 1 ticks the state machine
        // on whatever the service has, so a missed init leaves the layer
        // silently inert.
        compoundService.initFrom(map);
    }
    /** Stamped defense posts (conquest only). Called once by {@code BattleSetup} right after construction; safe to pass null/empty for missions without posts. */
    public void setDefensePosts(List<DefensePost> posts) { tactical.setDefensePosts(posts); }

    public void addUnit(Unit u) {
        rosterService.addUnit(u);
        if (vision.getVisionState().isContributor(u.faction)) {
            vision.addContributor(u);
        }
    }

    /**
     * Delegates to {@link UnitRosterService#queueSpawn(Unit)}. Routes serial
     * callers through inline addUnit (which allocates the entity immediately)
     * and parallel callers through the spawn queue (drained in APPLY_SPAWNS).
     * Serial-path fog contributor registration is done here after the inline
     * allocation; parallel-path registration is done in {@link #flushPendingSpawns}.
     */
    public void queueSpawn(Unit u) {
        long idBefore = u.entityId;
        rosterService.queueSpawn(u);
        if (idBefore == 0L && u.entityId != 0L
                && vision.getVisionState().isContributor(u.faction)) {
            vision.addContributor(u);
        }
    }

    /** Delegates to {@link UnitRosterService#flushPendingSpawns()}. Public so tests can force the drain after a {@code queueSpawn} call. Registers fog-of-war contributors for any player-faction spawns. */
    public void flushPendingSpawns() {
        List<Unit> pending = rosterService.getPendingSpawns();
        int spawnCount = pending.size();
        if (spawnCount == 0) return;
        Unit[] snapshot = pending.toArray(new Unit[0]);
        rosterService.flushPendingSpawns();
        for (Unit u : snapshot) {
            if (vision.getVisionState().isContributor(u.faction)) {
                vision.addContributor(u);
            }
        }
    }

    /**
     * Delegates to {@link UnitRosterService#releaseFromRegistry(long)}. Two
     * known production callers (the death cascade in
     * {@link com.dillon.starsectormarines.battle.damage.DamageResolver} and
     * the drone cascade in
     * {@link com.dillon.starsectormarines.battle.drone.HubDemolitionSystem})
     * release via {@code rosterService} directly; this delegate exists for
     * test helpers that simulate kills without routing through
     * {@code applyDamage}, so the registry contract ("released entities
     * resolve to null") holds in test fixtures the same way it does in
     * production.
     */
    public void releaseFromRegistry(long entityId) {
        rosterService.releaseFromRegistry(entityId);
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
        long expectedTargetId = target.targetId;
        Unit expectedTarget = targetOf(target);
        // No current target → next behavior tick will pick fresh anyway.
        if (expectedTarget == null) return;
        // Already targeting the shooter → no point re-rolling.
        if (shooter != null && expectedTarget == shooter) return;
        // Reprio chance bumps heavily when current target is out of LoS —
        // chasing a target you can't see while taking incoming is the
        // failure mode this hook exists to break.
        boolean hasLosToCurrentTarget = TacticalScoring.canSeePair(grid,
                target.getCellX(), target.getCellY(),
                expectedTarget.getCellX(), expectedTarget.getCellY(),
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
        // queue (which retains expectedTargetId for the flush-side race
        // resolution against a concurrent self-retarget).
        damageService.applyReprio(target, expectedTargetId);
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
        int[] fallback = tacticalScoring.findFallbackPosition(target);
        if (fallback[0] == target.getCellX() && fallback[1] == target.getCellY()) return;
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

    /** Inline reprio write — invoked by the damage service on the serial path AND on the queued path (after the expectedTargetId race-check). The shape stays just "clear the targetId field"; the next behavior tick re-picks via {@code findBestTarget}. */
    private void writeReprioInline(Unit target) {
        target.targetId = 0L;
    }

    /** Inline fallback write — invoked by the damage service on the serial path AND from the queued-flush. Writes the 3 fb fields and clears the stale path so the target re-paths to the fall-back cell on its next updateUnit pass. */
    private void writeFallbackInline(Unit target, int fbX, int fbY) {
        target.fallbackCellX = fbX;
        target.fallbackCellY = fbY;
        target.fallbackTimer = FALLBACK_DURATION;
        clearPath(target);
    }

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

    /** Compound capture-state registry. Read by slice-2 marker renderer, slice-3 trigger/means gates, and slice-4 win-condition objective. Initialized from {@link TacticalMap} during {@link #setTacticalMap}. */
    public CompoundService getCompoundService() {
        return compoundService;
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
        // this cadence stays invisible. Ephemeral sources (shuttles, fighters)
        // are pushed by BattleScreen.advance() each frame before this call.
        vision.tick(simTickIndex, units, grid, rosterService.getRegistry());
        tickProfile.lap(TickProfile.Phase.VISION);
        navigation.rebuildOccupancyMap(units);
        tickProfile.lap(TickProfile.Phase.REBUILD_OCCUPANCY);
        // Rebuild the spatial index BEFORE the AI passes so per-tick scoring
        // (exposure, threat density, allies-near) reads a consistent
        // snapshot. Same single-pass-per-tick semantics as the attacker
        // index below — mid-tick repath shifts aren't reflected until next
        // tick, matching the pre-spatial behavior.
        navigation.rebuildSpatialIndices(rosterService.getRegistry());
        tickProfile.lap(TickProfile.Phase.REBUILD_UNIT_INDEX);
        // Rebuild the attacker index BEFORE per-unit updates so target-
        // selection's crowding scoring (TacticalScoring.findBestTarget) sees a
        // consistent snapshot of last-tick's targets. We deliberately don't
        // re-rebuild mid-tick as units pick new targets — the snapshot model
        // means a squad's crowding cost reflects the previous frame, which
        // matches the prior O(U²) behavior's semantics anyway.
        attackerIndex.rebuild();
        tickProfile.lap(TickProfile.Phase.REBUILD_ATTACKERS);
        // Refresh squad-level awareness BEFORE individual unit updates so the
        // garrison/patrol behavior dispatch this tick sees fresh ENGAGED /
        // SUSPICIOUS / UNAWARE state. Solo units (squadId == NO_SQUAD) skip
        // the squad path entirely.
        squadAlert.tick(units, TICK_DT);
        tickProfile.lap(TickProfile.Phase.SQUAD_ALERT);
        // Morale recovery + hysteresis. Reads the freshly-set _engagedThisTick
        // flag from SquadAlertSystem: a squad out of contact this tick
        // recovers; a squad in contact holds. Runs before the GOAP replan so
        // SurviveContact relevance sees the up-to-date moraleBroken flag.
        squadMorale.tick(TICK_DT);
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
        // Squad-level GOAP replan pass. See SquadReplanSystem class doc for
        // ordering + parallelism notes.
        squadReplan.tick(this);
        tickProfile.lap(TickProfile.Phase.GOAP_REPLAN);
        // Parallel per-unit dispatch — entity for-loop. See UnitUpdateSystem
        // class doc for the parallelism + ECS-promotion notes.
        unitUpdate.tick(this);
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
        // Compound capture state machine — same 1Hz cadence as reinforcement,
        // intentionally co-located so the two layers reach the same compound
        // state within at most a tick of each other. Slice 1 has no consumers;
        // the system writes state but nothing reads yet.
        compoundCapture.tick(TICK_DT, this, compoundService);
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

    /** Delegates to {@link com.dillon.starsectormarines.battle.ai.AttackerIndexService#getAttackersOf(Unit)}. The list is mutated in-place each tick — callers must not retain it across tick boundaries. */
    public ArrayList<Unit> getAttackersOf(Unit target) {
        return attackerIndex.getAttackersOf(target);
    }

    /** Delegates to {@link NavigationService#getVantagePointsFor(int, int)}. Cached per-battle; invalidated in lockstep with the zone-graph rebuild driven by {@link NavigationService#flushZoneGraphIfDirty}. */
    public int[][] getVantagePointsFor(int tx, int ty) {
        return navigation.getVantagePointsFor(tx, ty);
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
        boolean hasOld = oldDestX != Integer.MIN_VALUE && (oldDestX != u.getCellX() || oldDestY != u.getCellY());
        boolean hasNew = newDestX != Integer.MIN_VALUE && (newDestX != u.getCellX() || newDestY != u.getCellY());
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
     * Advances a unit one tick along its current path. Public so behaviors
     * call this after re-pathing or as the last step of their per-tick
     * update.
     */
    public void advanceMovement(Unit u) {
        if (u.pathIdx >= u.pathCellCount()) return;

        int nextX = u.pathCellX(u.pathIdx);
        int nextY = u.pathCellY(u.pathIdx);
        float dx = nextX - u.getCellX();
        float dy = nextY - u.getCellY();
        float cellDist = (float) Math.sqrt(dx * dx + dy * dy);
        if (cellDist < 0.0001f) {
            u.pathIdx++;
            return;
        }

        float stepLength = u.moveSpeed * TICK_DT; // cell-units this tick
        u.moveProgress += stepLength / cellDist;

        if (u.moveProgress >= 1f) {
            u.setCellPos(nextX, nextY);
            u.renderX = nextX;
            u.renderY = nextY;
            u.moveProgress = 0f;
            u.pathIdx++;
        } else {
            u.renderX = u.getCellX() + dx * u.moveProgress;
            u.renderY = u.getCellY() + dy * u.moveProgress;
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
     *       {@link com.dillon.starsectormarines.battle.weapons.MechWeapon#LRM_NO_LOS_ACC_MULT}.</li>
     * </ul>
     */
    public void fireShotFrom(float fromX, float fromY, Faction shooterFaction,
                             TurretKind kind, Unit target, boolean aerialShooter, boolean hasLos) {
        float distToTarget = (float) Math.sqrt(
                (target.getCellX() + 0.5f - fromX) * (target.getCellX() + 0.5f - fromX) +
                (target.getCellY() + 0.5f - fromY) * (target.getCellY() + 0.5f - fromY));
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
            toX = target.getCellX() + 0.5f;
            toY = target.getCellY() + 0.5f;
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
            toX = target.getCellX() + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.getCellY() + 0.5f + (float) Math.sin(angle) * spread;
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
            queueDetonation(new com.dillon.starsectormarines.battle.fx.PendingDetonation(
                    toX, toY, flight,
                    kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                    kind.wallDamage, shooterFaction, aerialDelivery,
                    kind.wallDamageRadius, /*spawnDustOnWallBreak*/ true, /*friendlyFireImmune*/ false));
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
        float toX = target.getCellX() + 0.5f + (float) Math.cos(angle) * r;
        float toY = target.getCellY() + 0.5f + (float) Math.sin(angle) * r;

        float flightTime = distToTarget / kind.cellsPerSec();

        PendingDetonation onArrival = new PendingDetonation(
                toX, toY, flightTime,
                kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                kind.wallDamage, shooterFaction, aerialDelivery,
                kind.wallDamageRadius, /*spawnDustOnWallBreak*/ true, /*friendlyFireImmune*/ false);
        queueProjectile(new Projectile(fromX, fromY, toX, toY,
                kind.hasBoostRamp(), kind.arcHeight,
                shooterFaction, aerialDelivery, flightTime, onArrival));
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
     * already tracked by the sim. Routes through the same {@link DamageResolver}
     * the normal weapon path uses but with {@code moraleImpact = 0f}, which
     * short-circuits the morale branch — strafes are too short-lived for the
     * morale model to model meaningfully. Cover reduction, HP write, death
     * cascade (death FX + equipment drop + squad-leader promotion) all run
     * normally. {@code vsTurretMult = 1f} since strafing isn't turret-specific.
     * No {@link ShotEvent} is emitted — flyby tracers draw via the overlay,
     * not the ground combat tracer pass. Fall-back is also intentionally
     * skipped (strafes pin you down rather than break contact).
     */
    public void applyExternalDamage(Unit target, float damage) {
        if (target == null || !target.isAlive() || damage <= 0f) return;
        damageResolver.resolve(target, damage, 1f, 0f);
    }


}
