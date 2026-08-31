package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.drone.DroneHub;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.evacuation.RescueShelterGarrison;
import com.dillon.starsectormarines.battle.evacuation.RescuePickupSupportSystem;
import com.dillon.starsectormarines.battle.evacuation.SwarmDefenseRoster;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.colony.SilentColonyThreatProfile;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.gen.UrbanMapGenerator;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.mech.MechVariant;

import com.dillon.starsectormarines.battle.air.AirArmament;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.MountedTurret;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.air.TurretMount;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.command.AssaultCommand;
import com.dillon.starsectormarines.battle.command.AssaultCommandDisclosure;
import com.dillon.starsectormarines.battle.command.AssaultDefenderCommand;
import com.dillon.starsectormarines.battle.command.AssaultDefenderCommandDisclosure;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ConquestCommand;
import com.dillon.starsectormarines.battle.command.ConquestCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ConquestDefenderCommand;
import com.dillon.starsectormarines.battle.command.ConquestDefenderStartingForce;
import com.dillon.starsectormarines.battle.command.ConquestTrackLayout;
import com.dillon.starsectormarines.battle.command.ExtractionCommand;
import com.dillon.starsectormarines.battle.command.ExtractionCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ExtractionDefenderCommand;
import com.dillon.starsectormarines.battle.command.ExtractionDefenderCommandDisclosure;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandDisclosure;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandFacts;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.RaidCommand;
import com.dillon.starsectormarines.battle.command.RaidCommandDisclosure;
import com.dillon.starsectormarines.battle.command.RaidDefenderCommand;
import com.dillon.starsectormarines.battle.command.RaidDefenderCommandDisclosure;
import com.dillon.starsectormarines.battle.command.SabotageCommand;
import com.dillon.starsectormarines.battle.command.SabotageCommandDisclosure;
import com.dillon.starsectormarines.battle.command.SabotageDefenderCommand;
import com.dillon.starsectormarines.battle.command.SabotageDefenderCommandDisclosure;
import com.dillon.starsectormarines.battle.command.SilentColonyCommand;
import com.dillon.starsectormarines.battle.command.SilentColonyCommandDisclosure;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.compound.CompoundGarrisonSystem;
import com.dillon.starsectormarines.battle.vehicle.ConvoyPlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.command.reinforcement.ConvoyMeans;
import com.dillon.starsectormarines.battle.command.reinforcement.DeliveryDeploymentPolicy;
import com.dillon.starsectormarines.battle.command.reinforcement.CounterattackSystem;
import com.dillon.starsectormarines.battle.command.reinforcement.FrontLineReinforcementTrigger;
import com.dillon.starsectormarines.battle.command.reinforcement.GarrisonDepletedTrigger;
import com.dillon.starsectormarines.battle.command.reinforcement.ObjectiveLostTrigger;
import com.dillon.starsectormarines.battle.command.reinforcement.RecaptureTargetService;
import com.dillon.starsectormarines.battle.command.reinforcement.RecaptureTargetSystem;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService;
import com.dillon.starsectormarines.battle.command.reinforcement.ShuttleMeans;
import com.dillon.starsectormarines.battle.command.reinforcement.WalkInMeans;
import com.dillon.starsectormarines.battle.ui.debug.ConvoySpawnDumper;
import org.apache.log4j.Logger;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.MapGenerator;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.ColonyArchiveObjective;
import com.dillon.starsectormarines.battle.command.objective.ConquestObjective;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.objective.ExtractionObjective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Builds a battle scenario for the auto-battler. v3: marines arrive via
 * scheduled shuttle drops rather than pre-spawning. {@link UrbanMapGenerator}
 * carves the city; we pick spread-out landing zones in the marine quadrant,
 * stagger the drops, and let {@link BattleSimulation} run the state machine.
 * Defenders still pre-spawn (lore-correct: they're already on the ground).
 */
public final class BattleSetup {

    private static final Logger LOG = Logger.getLogger(BattleSetup.class);

    /** Default battle grid size (cells) — matches {@link MapScale#MEDIUM}. Used as the {@link com.dillon.starsectormarines.ops.BattleScreen} fallback when no simulation is active yet. The actual generated map dimensions come from {@link MapScale#forTier}. */
    public static final int GRID_W = MapScale.MEDIUM.width;
    public static final int GRID_H = MapScale.MEDIUM.height;
    /** Canonical Conquest battlefield. Conquest does not scale with operation tier; change these together when the mode is deliberately resized. */
    public static final int CONQUEST_GRID_W = MapScale.LARGE.width;
    public static final int CONQUEST_GRID_H = MapScale.LARGE.height;

    /** Three drops × 4 marines/shuttle keeps total marine count at 12 — matches pre-shuttle balance. */
    private static final int SHUTTLE_COUNT = 3;
    /** Sim-seconds between successive shuttle launches. Spaces out drops so the LZs aren't all active at once. */
    private static final float SHUTTLE_DROP_STAGGER_SEC = 1.5f;
    /** Minimum cell-distance between landing zones — avoids stacking all shuttles on the spawn anchor. */
    private static final int LZ_MIN_SEPARATION = 8;
    /** Entry/exit Y offset above the grid (in cells). Long enough that shuttles are visible during their descent. */
    private static final float SHUTTLE_OFFMAP_Y = 8f;

    /** BFS radius around the defender anchor we scan for candidate spawn cells. Larger than the largest expected defender count so we have a pool to cherry-pick high-cover cells from. */
    private static final int DEFENDER_SPAWN_SCAN_RADIUS = 14;
    /** BFS radius around a tactical-node anchor for picking garrison spawn cells. Tight — defenders should appear inside or right next to their post. */
    private static final int GARRISON_SPAWN_RADIUS = 5;

    /** Fixed opening-operation forces: two local fireteams and three raider fireteams. */
    private static final int OPENING_LOCAL_MILITIA = 8;
    private static final int OPENING_RAIDERS = 12;
    private static final int OPENING_FIRETEAM_SIZE = 4;
    private static final int OPENING_LINE_OFFSET = 9;

    /** Total ambient civilians (mix of CIVILIAN/ENGINEER/SCIENTIST) scattered around residential POIs as map flavor. */
    private static final int AMBIENT_CIVILIAN_COUNT = 8;
    /** BFS radius around each residential POI when looking for civilian spawn cells. */
    private static final int CIVILIAN_SPAWN_RADIUS = 5;

    /** Min/max parked vehicles scattered on streets and courtyards. Trucks block pathing + LOS, so they act as movable map terrain. */
    private static final int VEHICLE_COUNT_MIN = 3;
    private static final int VEHICLE_COUNT_MAX = 6;
    /** Doodad pool the street/courtyard scatter draws parked vehicles from. */
    private static final String PARKED_VEHICLE_POOL = "PARKED_VEHICLES";
    /** Narrower pool for a working spaceport apron - ground traffic that belongs beside a berth. */
    private static final String SERVICE_VEHICLE_POOL = "SPACEPORT_SERVICE_VEHICLES";

    /**
     * The active {@link MapGenerator}. {@link BspCityGenerator} produces
     * irregular block-mosaic maps with per-{@link com.dillon.starsectormarines.battle.world.gen.BlockKind}
     * fillers (parks, plazas, industrial yards, waterfronts, fortified posts,
     * landing zones, etc.) on top of a BSP partition. {@link UrbanMapGenerator}
     * is kept around as a legacy fallback — flip this line if the new gen
     * needs to be temporarily disabled.
     */
    private static final MapGenerator MAP_GEN = new BspCityGenerator();

    /** SABOTAGE: number of charge sites to plant. One per shuttle = one planter per drop. */
    private static final int SABOTAGE_CHARGE_SITES = SabotageSiteLayout.REQUIRED_SITE_COUNT;
    /** SABOTAGE: sim-seconds a planter must dwell on a charge site to complete the plant. */
    private static final float SABOTAGE_PLANT_DURATION = 5.0f;

    private BattleSetup() {}

    /**
     * Result of {@link #buildMap}: the constructed sim plus the structure units
     * spawned for its defense posts (turrets + drone hubs, in spawn order).
     * Standalone factories ignore {@code structures}; the combat-bridge host
     * mirrors them as targetable proxies.
     */
    public record MapBuild(BattleSimulation sim, LongList structures) {}

    private record DefenderForcePlan(DefenderRoster roster,
                                     List<DefensePost> defensePosts,
                                     FlybyRoster enemyFighterSupport) {}

    /**
     * Builds the host-agnostic <b>map layer</b> — the part shared by every
     * {@code createX} factory and the combat-bridge host. Constructs the sim
     * from the grid, installs the tactical map / buildings / defense posts /
     * parked vehicles / doodads, and spawns the defense-post structure units.
     *
     * <p>This is the "build a {@link BattleSimulation}, then choose a host" seam:
     * the standalone path follows this with live-scenario population (defenders,
     * objectives, commander, reinforcement) + internal air; the bridge follows it
     * with {@code setAirProvider(EXTERNAL)} + proxy wiring.
     *
     * <p><b>Pre-condition:</b> {@code vehicles} and {@code defensePosts} must
     * already be stamped into {@code map.grid} — both flip cell walkability that
     * the zone graph reads when the sim is constructed here. Callers stamp them
     * (via {@link #stampVehicles} and either the generator's conquest recipe or
     * {@link DefensePostStamper#stampNonConquest}) <em>before</em> calling this,
     * then hand the resolved lists in. {@code spawnDefensePostTurrets} runs after
     * construction (it flips the turret-pad cells non-walkable post-bake), so the
     * historical turret-after-vehicle cover-bake ordering is preserved.
     */
    public static MapBuild buildMap(MapResult map, List<Doodad> parkedVehicles,
                                    List<DefensePost> defensePosts, long seed) {
        return buildMap(map, parkedVehicles, defensePosts, Collections.emptyList(), seed);
    }

    /**
     * @param seed the battle seed, carried through to {@link BattleSimulation}'s random
     *             stream so the fight is as reproducible as the map already is. Hosts
     *             pass the same seed they generated the map with.
     */
    public static MapBuild buildMap(MapResult map, List<Doodad> parkedVehicles,
                                    List<DefensePost> defensePosts,
                                    List<ParkedAircraft> parkedAircraft, long seed) {
        BattleSimulation sim = new BattleSimulation(map.grid, map.topology, seed);
        sim.addNatureOverlayCover(TileRegistry.installed());
        sim.setTacticalMap(map.tacticalMap);
        sim.setBuildings(map.buildings);
        sim.setDefensePosts(defensePosts);
        for (ParkedAircraft aircraft : parkedAircraft) sim.addParkedAircraft(aircraft);
        for (Doodad d : map.doodads) sim.addDoodad(d);
        for (Doodad d : parkedVehicles) sim.addDoodad(d);
        LongList structures = spawnDefensePostTurrets(sim, defensePosts);
        return new MapBuild(sim, structures);
    }

    public static BattleSimulation createPlaceholder() {
        return createPlaceholder(System.currentTimeMillis(), defaultManifest(), false);
    }

    public static BattleSimulation createPlaceholder(long seed) {
        return createPlaceholder(seed, defaultManifest(), false);
    }

    public static BattleSimulation createSabotage() {
        return createSabotage(System.currentTimeMillis(), defaultManifest(), false);
    }

    public static BattleSimulation createSabotage(long seed) {
        return createSabotage(seed, defaultManifest(), false);
    }

    public static BattleSimulation createRaid(long seed) {
        RiskLevel risk = RiskLevel.MEDIUM;
        return createRaid(seed, defaultManifest(), false,
                OperationTier.forRisk(risk), risk, TargetProfile.NEUTRAL,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY);
    }

    public static BattleSimulation createExtraction(long seed) {
        RiskLevel risk = RiskLevel.MEDIUM;
        return createExtraction(seed, defaultManifest(), false,
                OperationTier.forRisk(risk), risk, TargetProfile.NEUTRAL,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY);
    }

    /** Back-compat overload — assumes no heavy armor on the defender side. */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest) {
        return createSabotage(seed, manifest, false);
    }

    /** Back-compat overload — assumes no heavy armor on the defender side. */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest) {
        return createPlaceholder(seed, manifest, false);
    }

    /** Risk-defaulted overloads — map size collapses to {@link MapScale#MEDIUM}. */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor) {
        return createSabotage(seed, manifest, enemyHasHeavyArmor, RiskLevel.MEDIUM);
    }

    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, RiskLevel.MEDIUM);
    }

    /** Default manifest used by no-arg factories — three single-cycle Aeroshuttles, matching pre-cycling behavior. */
    private static List<ShuttleAssignment> defaultManifest() {
        List<ShuttleAssignment> out = new ArrayList<>(SHUTTLE_COUNT);
        for (int i = 0; i < SHUTTLE_COUNT; i++) {
            out.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));
        }
        return out;
    }

    /**
     * SABOTAGE variant: marines must plant charges on {@link #SABOTAGE_CHARGE_SITES}
     * target structures while defenders try to keep them off the sites. One
     * marine per shuttle drops in with the PLANTER role pre-assigned; the rest
     * deboard as combatants and provide cover fire.
     *
     * <p>Marine win: every {@link ChargeSiteObjective} completes AND at least
     * one marine is alive when the last charge sets. Defender win: kill every
     * marine before the charges go off.
     */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor, RiskLevel risk) {
        return createSabotage(seed, manifest, enemyHasHeavyArmor, risk, TargetProfile.NEUTRAL);
    }

    /** Campaign-aware sabotage factory; the target profile drives civilian port generation. */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor, RiskLevel risk,
                                                  TargetProfile profile) {
        return createSabotage(seed, manifest, enemyHasHeavyArmor,
                OperationTier.forRisk(risk), risk, profile);
    }

    /** Tier-aware sabotage factory — scale comes from the tier, variance from the risk. */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile) {
        return createSabotage(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY);
    }

    /** Tier-aware sabotage with both sides' authored fighter commitments. */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport) {
        GroundRosterProfile groundRoster = GroundRosterRegistry.resolve(
                profile != null ? profile.factionId() : "");
        MapScale scale = MapScale.forTier(tier);
        MapResult map = MAP_GEN.generate(scale.width, scale.height, seed, null, profile);
        Random rng = new Random(seed);
        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        // Vehicles stamp before sim construction so the BattleSimulation's
        // zone-graph rebuild sees the final walkability — trucks partition zones.
        List<Doodad> vehiclePlacements = stampVehicles(map, rng);
        // Defense posts stamp before sim construction for the same reason —
        // the embankment ring cells flip walkability, and the zone graph the
        // sim builds on construction needs to reflect that.
        List<DefensePost> defensePosts = new ArrayList<>();
        DefensePostStamper.stampNonConquest(map.grid, map.topology,
                RoadReservation.mask(map.roadGraph, map.grid.getWidth(), map.grid.getHeight()),
                map.pointsOfInterest, map.doodads, defensePosts, rng);
        DefenderForcePlan defenders = defenderForcePlan(
                MissionType.SABOTAGE, tier, risk, enemyHasHeavyArmor,
                assignments, defensePosts, marineFighterSupport,
                enemyFighterSupport, groundRoster);
        List<LandingPad> lzCells = LandingPadSelector.select(
                map, assignments.size(), LZ_MIN_SEPARATION);
        SabotageSiteLayout siteLayout = SabotageSiteLayout.select(
                map, lzCells, LZ_MIN_SEPARATION);
        List<ParkedAircraft> parkedAircraft = stampParkedAircraft(map, lzCells, rng);
        BattleSimulation sim = buildMap(
                map, vehiclePlacements, defenders.defensePosts(), parkedAircraft, seed).sim();
        sim.setGroundRoster(groundRoster);
        sim.setFlybyRoster(defenders.enemyFighterSupport());

        List<ChargeSiteObjective> objectives = new ArrayList<>(siteLayout.sites().size());
        for (SabotageSiteLayout.Site site : siteLayout.sites()) {
            ChargeSiteObjective obj = new ChargeSiteObjective(
                    site.cellX(), site.cellY(), SABOTAGE_PLANT_DURATION,
                    site.id(), site.displayName());
            objectives.add(obj);
            sim.addObjective(obj);
        }
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));

        // Marines: one Shuttle per assignment, each flying assignment.cycles
        // sorties. Each sortie counts as one "drop"; the globalDropIdx threads
        // through assignments + cycles so per-cycle planter targeting hits a
        // different charge site each time (cycle 0 of shuttle 0 → site 0,
        // cycle 1 of shuttle 0 → site 1, etc.).
        stampLzPads(sim, lzCells);
        int globalDropIdx = 0;
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            LandingPad lz = lzCells.get(i);
            float lzCenterX = lz.centerX + 0.5f;
            float lzCenterY = lz.centerY + 0.5f;
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY, scale.width, scale.height, lz.approach);
            long shuttleId = sim.spawnShuttle(
                    a.type, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entry[0], entry[1],
                    entry[2], entry[3],
                    i * SHUTTLE_DROP_STAGGER_SEC,
                    a.seatsPerSortie);
            ShuttleMission mission = sim.world().mission(shuttleId);
            mission.totalCycles = a.cycles;
            MarineLoadout[][] cycleLoadouts = new MarineLoadout[a.cycles][];
            for (int c = 0; c < a.cycles; c++) {
                cycleLoadouts[c] = buildSabotageLoadout(a.seatsPerSortie, objectives, globalDropIdx, rng);
                globalDropIdx++;
            }
            mission.cycleLoadouts = cycleLoadouts;
            mission.marineLoadout = cycleLoadouts[0];
            equipDefaultTurrets(sim, shuttleId);
        }

        int sabotageMobileMembers = Math.min(
                Math.max(0, defenders.roster().totalCount - 2),
                objectives.size() * defenders.roster().patrolSquadSize + 1);
        allocateDefenders(sim, map, defenders.roster(), groundRoster, rng,
                sabotageMobileMembers);
        Set<Integer> sabotageMobileSquads = captureDefenderMobileSquads(sim);
        claimSetupGarrisons(sim, "sabotage-setup-garrison",
                "authored Sabotage garrison");
        claimMissionMobileSquads(sim, sabotageMobileSquads,
                "sabotage-defender", "initial Sabotage mobile security");
        spawnAmbientCivilians(sim, map, rng);
        spawnSpaceportGroundCrew(sim, map, parkedAircraft, rng);
        sim.setAutonomousCommander(Faction.MARINE, new SabotageCommand(),
                SabotageCommandDisclosure.INSTANCE);
        sim.setAutonomousCommander(Faction.DEFENDER,
                new SabotageDefenderCommand(sabotageMobileSquads),
                SabotageDefenderCommandDisclosure.INSTANCE);
        installReinforcementLayer(sim, map, MissionType.SABOTAGE, null,
                groundRoster, risk, null);
        return sim;
    }

    /** Falls back to the default manifest when callers pass null or an empty list. Keeps test entry points working without a manifest. */
    private static List<ShuttleAssignment> resolveManifest(List<ShuttleAssignment> manifest) {
        if (manifest == null || manifest.isEmpty()) return defaultManifest();
        return manifest;
    }

    /** Drops a yellow-striped landing-pad doodad under each LZ cell so the touchdown reads as a deliberate landing on a marked pad. Lives on the road sheet, drawn between floor and units. */
    private static void stampLzPads(BattleSimulation sim, List<LandingPad> lzCells) {
        for (LandingPad lz : lzCells) {
            sim.addDoodad(new Doodad(lz.centerX, lz.centerY, TileManifest.lzPad(), true, Doodad.COVER_NONE));
        }
    }

    /** Conquest keeps its beachhead-cell picker rather than consuming civilian berths. */
    private static void stampLzCellMarkers(BattleSimulation sim, List<int[]> lzCells) {
        for (int[] lz : lzCells) {
            sim.addDoodad(new Doodad(lz[0], lz[1], TileManifest.lzPad(), true, Doodad.COVER_NONE));
        }
    }

    /** Dedicated Raid factory; target seizure and egress replace elimination. */
    public static BattleSimulation createRaid(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile profile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk,
                MissionType.RAID, profile, marineFighterSupport,
                enemyFighterSupport);
    }

    /** Dedicated generic Extraction factory; payload escort replaces elimination. */
    public static BattleSimulation createExtraction(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile profile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk,
                MissionType.EXTRACTION, profile, marineFighterSupport,
                enemyFighterSupport);
    }

    /**
     * Slot 0 of each shuttle gets a PLANTER assigned to a charge site (paired
     * by shuttle index, wrapping around if shuttle count and site count differ).
     * Remaining slots are plain combatants.
     */
    /**
     * Builds one sortie's loadout — slot 0 is a PLANTER targeting the charge
     * site keyed by {@code dropIndex}, the rest are plain combatants. {@code
     * dropIndex} is the GLOBAL drop number across all shuttles + cycles (not
     * the shuttle index), so a single cycling shuttle hits each site in turn
     * on successive sorties.
     */
    private static MarineLoadout[] buildSabotageLoadout(int capacity, List<ChargeSiteObjective> sites, int dropIndex, Random rng) {
        MarineLoadout[] roster = InfantryLoadoutRolls.playerSquad(capacity, rng);
        if (!sites.isEmpty()) {
            // Slot 0 becomes the planter — keep their primary weapon assignment
            // (so they fire opportunistically en route), drop any secondary so
            // they're not lugging a rocket launcher to a charge plant.
            ChargeSiteObjective site = sites.get(dropIndex % sites.size());
            MarineLoadout prior = roster[0];
            roster[0] = new MarineLoadout(UnitRole.PLANTER, site, prior.primaryWeaponId,
                    prior.equipmentGrade, prior.soldierProfile, null, 0);
        }
        return roster;
    }

    /**
     * Builds a per-slot loadout for one shuttle. Every slot gets a rolled
     * primary (50% pulse rifle, 25% SMG, 25% DMR); the last slot also picks
     * up a rocket launcher with full ammo as the squad's anti-armor specialist.
     * Capacity-1 shuttles skip the secondary — a one-marine drop is a courier,
     * not a fireteam.
     */
    /**
     * Populates a freshly-constructed shuttle's turret mounts with the
     * default A2G kit for its type, when the type has hardpoints &gt; 0.
     * Wired in here so every spawn path picks up fire support automatically
     * — a follow-up briefing-UI slice will let the player override the role
     * per shuttle, which slots in via {@code shuttle.assignedRole} before
     * this helper runs.
     *
     * <p>Mounts start with their facing aligned to the shuttle's nose so
     * the first hover-station tick doesn't snap turrets through a 90° swing.
     */
    private static void equipDefaultTurrets(BattleSimulation sim, long shuttleId) {
        AirArmament.equip(sim, shuttleId, TurretRole.A2G);
    }

    /** Back-compat overload — assumes generic ASSAULT mission type. */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor, RiskLevel risk) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, risk, MissionType.ASSAULT);
    }

    /**
     * Catch-all construction pipeline shared by Assault, Raid, and Extraction.
     * Dedicated entry points replace the generic Marine objective with their
     * mission-owned contract while retaining shared map and force construction.
     */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor, RiskLevel risk,
                                                     MissionType type) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, risk, type,
                TargetProfile.NEUTRAL);
    }

    /** Campaign-aware catch-all factory; the target profile drives economic districts. */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor, RiskLevel risk,
                                                     MissionType type, TargetProfile profile) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor,
                OperationTier.forRisk(risk), risk, type, profile);
    }

    /** Tier-aware catch-all factory — scale comes from the tier, variance from the risk. */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor,
                                                     OperationTier tier, RiskLevel risk,
                                                     MissionType type, TargetProfile profile) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk,
                type, profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY);
    }

    /** Tier-aware catch-all with both sides' authored fighter commitments. */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor,
                                                     OperationTier tier, RiskLevel risk,
                                                     MissionType type, TargetProfile profile,
                                                     FlybyRoster marineFighterSupport,
                                                     FlybyRoster enemyFighterSupport) {
        GroundRosterProfile groundRoster = GroundRosterRegistry.resolve(
                profile != null ? profile.factionId() : "");
        MapScale scale = MapScale.forTier(tier);
        MapResult map = MAP_GEN.generate(scale.width, scale.height, seed, null, profile);
        Random rng = new Random(seed);
        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        List<Doodad> vehiclePlacements = stampVehicles(map, rng);
        List<DefensePost> defensePosts = new ArrayList<>();
        DefensePostStamper.stampNonConquest(map.grid, map.topology,
                RoadReservation.mask(map.roadGraph, map.grid.getWidth(), map.grid.getHeight()),
                map.pointsOfInterest, map.doodads, defensePosts, rng);
        DefenderForcePlan defenders = defenderForcePlan(
                type, tier, risk, enemyHasHeavyArmor, assignments, defensePosts,
                marineFighterSupport, enemyFighterSupport, groundRoster);
        List<LandingPad> lzCells = LandingPadSelector.select(
                map, assignments.size(), LZ_MIN_SEPARATION);
        RaidTargetLayout raidLayout = type == MissionType.RAID
                ? RaidTargetLayout.select(map, lzCells) : null;
        ExtractionPayloadLayout extractionLayout = type == MissionType.EXTRACTION
                ? ExtractionPayloadLayout.select(map, lzCells) : null;
        List<ParkedAircraft> parkedAircraft = stampParkedAircraft(map, lzCells, rng);
        BattleSimulation sim = buildMap(
                map, vehiclePlacements, defenders.defensePosts(), parkedAircraft, seed).sim();
        sim.setGroundRoster(groundRoster);
        sim.setFlybyRoster(defenders.enemyFighterSupport());

        if (raidLayout != null) {
            int targetZone = sim.getZoneGraph().zoneIdAt(
                    raidLayout.targetCellX(), raidLayout.targetCellY());
            sim.addObjective(new RaidObjective(raidLayout.targetId(),
                    raidLayout.targetName(), raidLayout.targetCellX(),
                    raidLayout.targetCellY(), targetZone,
                    raidLayout.egressCellX(), raidLayout.egressCellY()));
        } else if (extractionLayout != null) {
            int sourceZone = sim.getZoneGraph().zoneIdAt(
                    extractionLayout.sourceCellX(),
                    extractionLayout.sourceCellY());
            sim.addObjective(new ExtractionObjective(
                    extractionLayout.payloadId(), extractionLayout.payloadName(),
                    sourceZone, extractionLayout.route()));
        } else {
            sim.addObjective(new EliminateFactionObjective(
                    Faction.MARINE, Faction.DEFENDER));
        }
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));

        // Marines: one Shuttle per assignment, each flying assignment.cycles sorties.
        // ASSAULT has no per-cycle role distinction (everyone is a combatant), so no
        // cycleLoadouts setup is needed — Shuttle.totalCycles drives repeat behavior.
        stampLzPads(sim, lzCells);
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            LandingPad lz = lzCells.get(i);
            float lzCenterX = lz.centerX + 0.5f;
            float lzCenterY = lz.centerY + 0.5f;
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY, scale.width, scale.height, lz.approach);
            long shuttleId = sim.spawnShuttle(
                    a.type, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entry[0], entry[1],
                    entry[2], entry[3],
                    i * SHUTTLE_DROP_STAGGER_SEC,
                    a.seatsPerSortie);
            ShuttleMission mission = sim.world().mission(shuttleId);
            mission.totalCycles = a.cycles;
            // Per-cycle weapon loadouts — re-rolled each sortie so a cycling
            // shuttle doesn't deboard the same exact fireteam composition twice.
            MarineLoadout[][] cycleLoadouts = new MarineLoadout[a.cycles][];
            for (int c = 0; c < a.cycles; c++) {
                cycleLoadouts[c] = InfantryLoadoutRolls.playerSquad(a.seatsPerSortie, rng);
            }
            mission.cycleLoadouts = cycleLoadouts;
            mission.marineLoadout = cycleLoadouts[0];
            equipDefaultTurrets(sim, shuttleId);
        }

        // Defenders pre-spawn around tactical-node anchors (garrison squads
        // pegged to the highest-priority posts; leftovers form patrol squads).
        // Legacy maps with no tactical layer fall back to the single-cluster
        // spawn around the defender anchor.
        int assaultMobileMembers = switch (type) {
            case ASSAULT -> Math.min(Math.max(0,
                    defenders.roster().totalCount - 2),
                    defenders.roster().patrolSquadSize);
            case RAID -> Math.min(Math.max(0,
                    defenders.roster().totalCount - 2),
                    defenders.roster().patrolSquadSize * 3);
            case EXTRACTION -> Math.min(Math.max(0,
                    defenders.roster().totalCount - 2),
                    defenders.roster().patrolSquadSize * 3);
            default -> 0;
        };
        allocateDefenders(sim, map, defenders.roster(), groundRoster, rng,
                assaultMobileMembers);
        Set<Integer> assaultMobileSquads = type == MissionType.ASSAULT
                ? captureDefenderMobileSquads(sim) : Set.of();
        Set<Integer> raidMobileSquads = type == MissionType.RAID
                ? captureDefenderMobileSquads(sim) : Set.of();
        Set<Integer> extractionMobileSquads = type == MissionType.EXTRACTION
                ? captureDefenderMobileSquads(sim) : Set.of();
        if (type == MissionType.ASSAULT) {
            claimSetupGarrisons(sim, "assault-setup-garrison",
                    "authored Assault strongpoint garrison");
            claimMissionMobileSquads(sim, assaultMobileSquads,
                    "assault-defender", "initial Assault mobile security");
        }
        if (type == MissionType.RAID) {
            claimSetupGarrisons(sim, "raid-setup-garrison",
                    "authored Raid strongpoint garrison");
            claimMissionMobileSquads(sim, raidMobileSquads,
                    "raid-defender", "initial Raid mobile security");
        }
        if (type == MissionType.EXTRACTION) {
            claimSetupGarrisons(sim, "extraction-setup-garrison",
                    "authored Extraction strongpoint garrison");
            claimMissionMobileSquads(sim, extractionMobileSquads,
                    "extraction-defender",
                    "initial Extraction mobile security");
        }
        spawnAmbientCivilians(sim, map, rng);
        spawnSpaceportGroundCrew(sim, map, parkedAircraft, rng);
        installReinforcementLayer(sim, map, type, null, groundRoster, risk,
                null);
        if (type == MissionType.ASSAULT) {
            sim.setAutonomousCommander(Faction.MARINE, new AssaultCommand(),
                    AssaultCommandDisclosure.INSTANCE);
            sim.setAutonomousCommander(Faction.DEFENDER,
                    new AssaultDefenderCommand(assaultMobileSquads),
                    AssaultDefenderCommandDisclosure.INSTANCE);
        }
        if (type == MissionType.RAID) {
            sim.setAutonomousCommander(Faction.MARINE, new RaidCommand(),
                    RaidCommandDisclosure.INSTANCE);
            sim.setAutonomousCommander(Faction.DEFENDER,
                    new RaidDefenderCommand(raidMobileSquads),
                    RaidDefenderCommandDisclosure.INSTANCE);
        }
        if (type == MissionType.EXTRACTION) {
            sim.setAutonomousCommander(Faction.MARINE,
                    new ExtractionCommand(),
                    ExtractionCommandDisclosure.INSTANCE);
            sim.setAutonomousCommander(Faction.DEFENDER,
                    new ExtractionDefenderCommand(extractionMobileSquads),
                    ExtractionDefenderCommandDisclosure.INSTANCE);
        }
        return sim;
    }

    /**
     * Authored militia-support operation: finite militia-only opposition, no
     * turrets, mechs, fighters, or reinforcement layer. Employer transports
     * deboard local militia while later manifest entries retain player seats.
     *
     * <p>Deliberately small at every scale, and it stays that way however good
     * the company gets — these contracts recur, and a veteran outfit is meant
     * to look at the payout and decline rather than find them withheld.
     */
    public static BattleSimulation createOpeningOperation(
            long seed, List<ShuttleAssignment> manifest, int employerShips,
            OpeningOperationKind kind, TargetProfile profile) {
        if (kind == null) throw new IllegalArgumentException(
                "opening operation kind is required");

        MapScale scale = MapScale.forTier(OperationTier.FIRST_CONTRACT);
        MapResult map = MAP_GEN.generate(scale.width, scale.height, seed, null,
                profile != null ? profile : TargetProfile.NEUTRAL);
        Random rng = new Random(seed);
        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        List<Doodad> vehiclePlacements = stampVehicles(map, rng);
        List<LandingPad> lzCells = LandingPadSelector.select(
                map, assignments.size(), LZ_MIN_SEPARATION);
        List<ParkedAircraft> parkedAircraft = stampParkedAircraft(map, lzCells, rng);
        BattleSimulation sim = buildMap(map, vehiclePlacements,
                Collections.emptyList(), parkedAircraft, seed).sim();

        sim.addObjective(new EliminateFactionObjective(
                Faction.MARINE, Faction.DEFENDER));
        sim.addObjective(new EliminateFactionObjective(
                Faction.DEFENDER, Faction.MARINE));

        stampLzPads(sim, lzCells);
        int localTransports = Math.max(0, Math.min(employerShips,
                assignments.size()));
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment assignment = assignments.get(i % assignments.size());
            LandingPad lz = lzCells.get(i);
            float lzCenterX = lz.centerX + 0.5f;
            float lzCenterY = lz.centerY + 0.5f;
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY,
                    scale.width, scale.height, lz.approach);
            long shuttleId = sim.spawnShuttle(
                    assignment.type, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entry[0], entry[1], entry[2], entry[3],
                    i * SHUTTLE_DROP_STAGGER_SEC,
                    assignment.seatsPerSortie);
            ShuttleMission shuttleMission = sim.world().mission(shuttleId);
            shuttleMission.totalCycles = assignment.cycles;
            shuttleMission.commandClaim = SquadCommandClaim.mission(
                    OpeningOperationCommand.issuer(Faction.MARINE),
                    "opening-operation landing force");
            MarineLoadout[][] cycleLoadouts =
                    new MarineLoadout[assignment.cycles][];
            boolean localMilitia = i < localTransports;
            for (int cycle = 0; cycle < assignment.cycles; cycle++) {
                cycleLoadouts[cycle] = localMilitia
                        ? InfantryLoadoutRolls.defenderSquad(
                                assignment.seatsPerSortie, UnitType.MILITIA,
                                RiskLevel.LOW, rng)
                        : InfantryLoadoutRolls.playerSquad(
                                assignment.seatsPerSortie, rng);
            }
            shuttleMission.cycleLoadouts = cycleLoadouts;
            shuttleMission.marineLoadout = cycleLoadouts[0];
            if (localMilitia) shuttleMission.deboardUnitType = UnitType.MILITIA;
            // Deliberately do not install default shuttle turrets. These jobs
            // are for companies whose lift is transport, not close support.
        }

        int[] reliefAnchor = kind == OpeningOperationKind.RELIEF
                ? spawnOpeningDefenseLine(sim, map, lzCells.get(0), rng)
                : null;
        int[] banditDepot = spawnOpeningRaiders(sim, map, rng);
        spawnAmbientCivilians(sim, map, rng);
        spawnSpaceportGroundCrew(sim, map, parkedAircraft, rng);

        int[] commandPlace = kind == OpeningOperationKind.RELIEF
                ? reliefAnchor : banditDepot;
        OpeningOperationCommandFacts commandFacts =
                new OpeningOperationCommandFacts(kind,
                        kind == OpeningOperationKind.RELIEF
                                ? "relief-anchor" : "bandit-depot",
                        kind == OpeningOperationKind.RELIEF
                                ? "Relief anchor" : "Bandit depot",
                        commandPlace[0], commandPlace[1]);
        OpeningOperationCommandDisclosure disclosure =
                new OpeningOperationCommandDisclosure(commandFacts);
        sim.setAutonomousCommander(Faction.MARINE,
                new OpeningOperationCommand(Faction.MARINE), disclosure);
        sim.setAutonomousCommander(Faction.DEFENDER,
                new OpeningOperationCommand(Faction.DEFENDER), disclosure);
        return sim;
    }

    /**
     * Dedicated civilian-rescue battle. Unlike generic EXTRACTION, this installs
     * the registered shelter-to-lift payload and uses its evacuation objective
     * as the marine win condition. Map retries are deterministic and discard an
     * unsuitable attempt before any simulation becomes player-visible.
     */
    public static BattleSimulation createCivilianRescue(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, RiskLevel risk) {
        return createCivilianRescue(seed, manifest, enemyHasHeavyArmor, risk,
                SwarmDefenseRoster.countFor(risk), TargetProfile.NEUTRAL, false);
    }

    /** Civilian rescue with an explicit initial swarm size. */
    public static BattleSimulation createCivilianRescue(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, RiskLevel risk, int swarmCount) {
        return createCivilianRescue(seed, manifest, enemyHasHeavyArmor, risk,
                swarmCount, TargetProfile.NEUTRAL);
    }

    /** Campaign-aware civilian rescue; preserves the target world's economic districts. */
    public static BattleSimulation createCivilianRescue(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, RiskLevel risk, int swarmCount,
            TargetProfile profile) {
        return createCivilianRescue(seed, manifest, enemyHasHeavyArmor, risk,
                swarmCount, profile, true);
    }

    /**
     * Civilian rescue with explicit pressure and deployment semantics. Stress
     * tests retain the wider debug approach band; canonical launches use the
     * same opening distance as the campaign mission.
     */
    public static BattleSimulation createCivilianRescue(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, RiskLevel risk, int swarmCount,
            TargetProfile profile, boolean stressTest) {
        for (int attempt = 0; attempt < 8; attempt++) {
            long battleSeed = seed + attempt * 0x9E3779B97F4A7C15L;
            MapScale scale = MapScale.forRisk(risk);
            MapResult map = MAP_GEN.generate(
                    scale.width, scale.height, battleSeed, null, profile);
            Random rng = new Random(battleSeed);
            List<ShuttleAssignment> assignments = resolveManifest(manifest);
            List<Doodad> vehiclePlacements = stampVehicles(map, rng);
            List<LandingPad> lzCells = LandingPadSelector.select(map,
                    assignments.size(), LZ_MIN_SEPARATION);
            List<ParkedAircraft> parkedAircraft = stampParkedAircraft(map, lzCells, rng);
            BattleSimulation sim =
                    // battleSeed, not seed: this attempt's map was generated from it,
                    // so the fight on that map must be too.
                    buildMap(map, vehiclePlacements,
                            Collections.emptyList(), parkedAircraft, battleSeed).sim();

            CivilianEvacuationPayload payload =
                    CivilianEvacuationPayload.install(sim, map, battleSeed);
            if (payload == null) continue;
            // The payload installs the marine evacuation objective. Defenders
            // win by eliminating the responding marine force.
            sim.addObjective(new EliminateFactionObjective(
                    Faction.DEFENDER, Faction.MARINE));

            stampLzPads(sim, lzCells);
            for (int i = 0; i < lzCells.size(); i++) {
                ShuttleAssignment assignment =
                        assignments.get(i % assignments.size());
                LandingPad lz = lzCells.get(i);
                float lzCenterX = lz.centerX + 0.5f;
                float lzCenterY = lz.centerY + 0.5f;
                float[] entry = shuttleEntryFor(lzCenterX, lzCenterY,
                        scale.width, scale.height, lz.approach);
                long shuttleId = sim.spawnShuttle(
                        assignment.type, Faction.MARINE,
                        lzCenterX, lzCenterY,
                        entry[0], entry[1], entry[2], entry[3],
                        i * SHUTTLE_DROP_STAGGER_SEC,
                        assignment.seatsPerSortie);
                ShuttleMission shuttleMission = sim.world().mission(shuttleId);
                shuttleMission.totalCycles = assignment.cycles;
                MarineLoadout[][] cycleLoadouts =
                        new MarineLoadout[assignment.cycles][];
                for (int cycle = 0; cycle < assignment.cycles; cycle++) {
                    cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(
                            assignment.seatsPerSortie, rng);
                }
                shuttleMission.cycleLoadouts = cycleLoadouts;
                shuttleMission.marineLoadout = cycleLoadouts[0];
                equipDefaultTurrets(sim, shuttleId);
            }

            spawnAmbientCivilians(sim, map, rng);
            spawnSpaceportGroundCrew(sim, map, parkedAircraft, rng);
            if (RescueShelterGarrison.install(sim, map,
                    payload.placement, risk, battleSeed) == null) {
                continue;
            }
            installRescuePickup(sim, payload.placement, payload.size(), battleSeed,
                    risk, scale.width, scale.height);
            SwarmDefenseRoster swarm = stressTest
                    ? SwarmDefenseRoster.install(
                            sim, payload.placement, swarmCount, battleSeed)
                    : SwarmDefenseRoster.installCanonical(
                            sim, payload.placement, swarmCount, battleSeed);
            if (swarm == null) continue;
            if (!sim.configureSwarmReinforcements(
                    payload.placement, swarm.size(), battleSeed)) {
                throw new IllegalStateException(
                        "civilian rescue swarm reinforcement configuration failed");
            }
            return sim;
        }
        throw new IllegalStateException(
                "unable to place civilian rescue payload after 8 map attempts");
    }

    /**
     * Dedicated Silent Colony expedition. The frozen threat seed owns the
     * ruined map and autonomous-defense placement; the ordinary battle seed
     * affects only the responding marine loadouts.
     */
    public static BattleSimulation createSilentColony(
            long battleSeed, long threatSeed, int survivorCount,
            List<ShuttleAssignment> manifest, RiskLevel risk) {
        if (threatSeed < 0L || survivorCount <= 0 || risk == null) {
            throw new IllegalArgumentException(
                    "valid Silent Colony mission facts required");
        }
        SilentColonyThreatProfile threat =
                SilentColonyThreatProfile.fromSeed(threatSeed);
        for (int attempt = 0; attempt < 8; attempt++) {
            long scenarioSeed = threatSeed
                    + attempt * 0x9E3779B97F4A7C15L;
            MapScale scale = MapScale.forRisk(risk);
            MapResult map = MAP_GEN.generate(scale.width, scale.height,
                    scenarioSeed, null, TargetProfile.NEUTRAL);
            Random scenarioRng = new Random(
                    scenarioSeed ^ 0x4155544F4D415445L);
            List<ShuttleAssignment> assignments = resolveManifest(manifest);
            List<Doodad> vehiclePlacements = stampVehicles(
                    map, scenarioRng);
            List<DefensePost> candidates = new ArrayList<>();
            DefensePostStamper.stampNonConquest(map.grid, map.topology,
                    RoadReservation.mask(map.roadGraph, map.grid.getWidth(),
                            map.grid.getHeight()),
                    map.pointsOfInterest, map.doodads, candidates,
                    scenarioRng);
            List<DefensePost> automatedPosts = threat.select(candidates);
            if (automatedPosts.isEmpty()) continue;

            List<LandingPad> lzCells = LandingPadSelector.select(
                    map, assignments.size(), LZ_MIN_SEPARATION);
            List<ParkedAircraft> parkedAircraft = stampParkedAircraft(
                    map, lzCells, scenarioRng);
            // scenarioSeed, not battleSeed: the map, the vehicle stamp, and the
            // defense-post selection for this attempt all derive from it, so the
            // fight has to as well or a retried attempt would replay a different
            // battle on the same ground.
            BattleSimulation sim = buildMap(map, vehiclePlacements,
                    automatedPosts, parkedAircraft, scenarioSeed).sim();

            CivilianEvacuationPayload survivors =
                    CivilianEvacuationPayload.install(sim, map,
                            scenarioSeed, survivorCount, false,
                            "COLONY-SURVIVORS", "colony survivor cohort");
            if (survivors == null) continue;
            PointOfInterest archiveSite = pickColonyArchiveSite(
                    map.pointsOfInterest, survivors.placement,
                    scenarioSeed);
            if (archiveSite == null) continue;
            int archiveZone = sim.getZoneGraph().zoneIdAt(
                    archiveSite.interiorAnchorX,
                    archiveSite.interiorAnchorY);
            if (archiveZone < 0) continue;
            ColonyArchiveObjective archive = new ColonyArchiveObjective(
                    archiveSite.interiorAnchorX,
                    archiveSite.interiorAnchorY, archiveZone);
            sim.addObjective(archive);
            sim.addObjective(new EliminateFactionObjective(
                    Faction.DEFENDER, Faction.MARINE));

            Random marineRng = new Random(
                    battleSeed ^ 0x455850454449544EL);
            stampLzPads(sim, lzCells);
            for (int i = 0; i < lzCells.size(); i++) {
                ShuttleAssignment assignment =
                        assignments.get(i % assignments.size());
                LandingPad lz = lzCells.get(i);
                float lzCenterX = lz.centerX + 0.5f;
                float lzCenterY = lz.centerY + 0.5f;
                float[] entry = shuttleEntryFor(lzCenterX, lzCenterY,
                        scale.width, scale.height, lz.approach);
                long shuttleId = sim.spawnShuttle(
                        assignment.type, Faction.MARINE,
                        lzCenterX, lzCenterY,
                        entry[0], entry[1], entry[2], entry[3],
                        i * SHUTTLE_DROP_STAGGER_SEC,
                        assignment.seatsPerSortie);
                ShuttleMission shuttleMission = sim.world().mission(shuttleId);
                shuttleMission.totalCycles = assignment.cycles;
                shuttleMission.commandClaim = SquadCommandClaim.mission(
                        SilentColonyCommand.ISSUER,
                        "Silent Colony expedition force");
                MarineLoadout[][] cycleLoadouts =
                        new MarineLoadout[assignment.cycles][];
                for (int cycle = 0; cycle < assignment.cycles; cycle++) {
                    cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(
                            assignment.seatsPerSortie, marineRng);
                }
                shuttleMission.cycleLoadouts = cycleLoadouts;
                shuttleMission.marineLoadout = cycleLoadouts[0];
                equipDefaultTurrets(sim, shuttleId);
            }
            sim.setAutonomousCommander(Faction.MARINE,
                    new SilentColonyCommand(),
                    new SilentColonyCommandDisclosure(survivors.placement));
            return sim;
        }
        throw new IllegalStateException(
                "unable to place Silent Colony objectives after 8 map attempts");
    }

    private static PointOfInterest pickColonyArchiveSite(
            List<PointOfInterest> sites,
            CivilianEvacuationPlacement placement,
            long seed) {
        List<PointOfInterest> candidates = new ArrayList<>();
        for (PointOfInterest site : sites) {
            if (site == null) continue;
            if (site.interiorAnchorX == placement.shelterX
                    && site.interiorAnchorY == placement.shelterY) {
                continue;
            }
            candidates.add(site);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator
                .comparingInt((PointOfInterest site) -> site.interiorAnchorY)
                .thenComparingInt(site -> site.interiorAnchorX)
                .thenComparingInt(site -> site.kind.ordinal()));
        int index = Math.floorMod((int) (seed ^ (seed >>> 32)),
                candidates.size());
        return candidates.get(index);
    }

    /**
     * CONQUEST variant: full beach→port→city→fortress biome push with the
     * super-wall stamper active. Map size is the mode-wide
     * {@link #CONQUEST_GRID_W}×{@link #CONQUEST_GRID_H}, independent of tier,
     * risk, or host; the traversal axis is rolled per-seed —
     * SOUTH_TO_NORTH or WEST_TO_EAST, so two conquest missions on the same
     * world play with a different attacker approach. Marines win only after
     * capturing every defender compound, including the one required central
     * keep ({@code COMMAND_POST}); defenders win by eliminating the marines.
     *
     * <p>Marines and defenders pin to their respective biome anchors instead
     * of the legacy left/right halves — marine LZ in BEACH, defender garrison
     * in FORTRESS_DISTRICT (with garrison squads at the wall's tactical nodes).
     *
     * <p>Returns the full {@link MapBuild} (sim + the spawned defense-post
     * structures). Most callers want just the sim — {@link #createConquest} is
     * the thin delegating overload for them. The combat bridge needs the
     * structures list to mirror them as targetable proxies, so it calls this.
     */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor, RiskLevel risk,
                                               TargetProfile profile) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor,
                OperationTier.forRisk(risk), risk, profile);
    }

    /** Tier-aware Conquest build. Tier and risk shape the roster, never the map dimensions. */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor,
                                               OperationTier tier, RiskLevel risk,
                                               TargetProfile profile) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY);
    }

    /** Tier-aware Conquest build with both sides' authored fighter commitments. */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor,
                                               OperationTier tier, RiskLevel risk,
                                               TargetProfile profile,
                                               FlybyRoster marineFighterSupport,
                                               FlybyRoster enemyFighterSupport) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport,
                new ShuttleArrivalPlan(
                        MarineArrivalPolicy.PAIRED_HALF_SQUAD,
                        0));
    }

    /** Conquest build with a fixture-captured mission arrival plan. */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor,
                                               OperationTier tier, RiskLevel risk,
                                               TargetProfile profile,
                                               FlybyRoster marineFighterSupport,
                                               FlybyRoster enemyFighterSupport,
                                               ShuttleArrivalPlan arrivalPlan) {
        GroundRosterProfile groundRoster = GroundRosterRegistry.resolve(
                profile != null ? profile.factionId() : "");
        int gridW = CONQUEST_GRID_W;
        int gridH = CONQUEST_GRID_H;
        Random rng = new Random(seed);
        TraversalAxis axis = rng.nextBoolean() ? TraversalAxis.SOUTH_TO_NORTH : TraversalAxis.WEST_TO_EAST;
        // Generator uses its own seeded RNG — pass the same seed so different
        // mission types from the same seed still produce comparable layouts;
        // axis flips deterministically off the first bit of our wrapper RNG.
        // The target world's profile (planetary defenses, …) rides in so the
        // overwatch line reflects how fortified the planet is.
        ConquestMap generated = conquestMap(gridW, gridH, seed, axis, profile);
        MapResult map = generated.map();

        List<Doodad> vehiclePlacements = stampVehicles(map, rng);
        ShuttleArrivalPlan requestedArrivalPlan = arrivalPlan != null
                ? arrivalPlan : ShuttleArrivalPlan.legacy();
        ShuttleArrivalPlan.ResolvedManifest resolvedManifest =
                requestedArrivalPlan.resolveManifest(resolveManifest(manifest));
        List<ShuttleAssignment> assignments = resolvedManifest.assignments();
        ShuttleArrivalPlan resolvedArrivalPlan = new ShuttleArrivalPlan(
                requestedArrivalPlan.policy(), resolvedManifest.firstPlayerShuttle(),
                requestedArrivalPlan.arrivalConfig());
        DefenderForcePlan defenders = defenderForcePlan(
                MissionType.CONQUEST, tier, risk, enemyHasHeavyArmor,
                assignments, map.defensePosts, marineFighterSupport,
                enemyFighterSupport, groundRoster);
        // Conquest defense posts come pre-stamped by the biome-aware
        // DefensePostStamper inside BspCityGenerator (BEACH→PORT→kill-zone
        // tiers + rear ARTILLERY battery), so buildMap consumes map.defensePosts
        // directly. Each post is paired with a manned GUARDPOST squad via
        // {@link #linkGuardpostSquads} below — that's the difference from the
        // non-conquest path, which stamps the same shapes unmanned via
        // {@code DefensePostStamper.stampNonConquest}.
        MapBuild build = buildMap(map, vehiclePlacements, defenders.defensePosts(),
                generated.seed());
        BattleSimulation sim = build.sim();
        sim.setGroundRoster(groundRoster);
        sim.setFlybyRoster(defenders.enemyFighterSupport());

        // Conquest win condition: marines dismantle defender supply
        // structures, not "kill every defender." Pre-slice-4 this was
        // EliminateFactionObjective on both sides, which never resolved
        // because reinforcement kept spawning fresh militia after every
        // hardpoint fell. ConquestObjective reads CompoundService and
        // latches when every defender compound is MARINE_HELD. Defender
        // side keeps the elimination shape so "every marine died" still
        // terminates the battle. See conquest-nouns.md
        // slice 4.
        sim.addObjective(new ConquestObjective(sim.getCompoundService()));
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));

        List<ConquestArrivalSlot> arrivalSlots = conquestArrivalSlots(
                map, assignments, axis, rng, resolvedArrivalPlan);
        List<int[]> lzCells = arrivalSlots.stream()
                .map(slot -> new int[]{slot.pad().centerX, slot.pad().centerY})
                .toList();
        stampLzCellMarkers(sim, lzCells);
        for (int i = 0; i < arrivalSlots.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            ConquestArrivalSlot slot = arrivalSlots.get(i);
            float lzCenterX = slot.pad().centerX + 0.5f;
            float lzCenterY = slot.pad().centerY + 0.5f;
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY, gridW, gridH, axis);
            long shuttleId = sim.spawnShuttle(
                    resolvedArrivalPlan.deliveryCraft(a.type), Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entry[0], entry[1],
                    entry[2], entry[3], slot.pendingDelay(),
                    a.seatsPerSortie);
            ShuttleMission mission = sim.world().mission(shuttleId);
            mission.totalCycles = a.cycles;
            mission.rearmDelay = slot.rearmDelay();
            mission.manifestOrdinal = i < resolvedManifest.firstPlayerShuttle()
                    ? i
                    : requestedArrivalPlan.firstPlayerShuttle()
                            + i - resolvedManifest.firstPlayerShuttle();
            mission.landingAreaId = slot.landingAreaId();
            mission.arrivalGroupId = slot.arrivalGroupId();
            mission.expectedArrivalStrength = slot.expectedStrength();
            MarineLoadout[][] cycleLoadouts = new MarineLoadout[a.cycles][];
            for (int c = 0; c < a.cycles; c++) {
                cycleLoadouts[c] = InfantryLoadoutRolls.playerSquad(a.seatsPerSortie, rng);
            }
            mission.cycleLoadouts = cycleLoadouts;
            mission.marineLoadout = cycleLoadouts[0];
            equipDefaultTurrets(sim, shuttleId);
        }

        allocateDefenders(sim, map, defenders.roster(), groundRoster, rng);
        linkGuardpostSquads(sim, defenders.defensePosts());
        claimConquestSetupGarrisons(sim);
        spawnAmbientCivilians(sim, map, rng);
        // Both Conquest commanders share one physical three-track layout but
        // retain separate, faction-honest influence pictures and policies.
        ConquestTrackLayout tracks = new ConquestTrackLayout(
                axis, map.grid.getWidth(), map.grid.getHeight());
        sim.setAutonomousCommander(Faction.MARINE, new ConquestCommand(tracks),
                ConquestCommandDisclosure.INSTANCE);
        ConquestDefenderStartingForce startingForce =
                ConquestDefenderStartingForce.capture(sim, tracks);
        ConquestDefenderCommand defenderCommand = new ConquestDefenderCommand(
                tracks, startingForce);
        for (int squadId : startingForce.mobileSquadIds()) {
            sim.claimSquadCommand(squadId, CommandAuthority.MISSION_COMMAND,
                    defenderCommand.strategyId(), "authored Conquest patrol reserve");
        }
        sim.setAutonomousCommander(Faction.DEFENDER, defenderCommand,
                ConquestCommandDisclosure.INSTANCE);
        sim.setGarrisonSystem(new CompoundGarrisonSystem(axis));
        installReinforcementLayer(sim, map, MissionType.CONQUEST, axis,
                groundRoster, risk, defenderCommand);
        return new MapBuild(sim, build.structures());
    }

    private record ConquestArrivalSlot(
            LandingPad pad, int landingAreaId, int arrivalGroupId,
            int expectedStrength, float pendingDelay, float rearmDelay) {}

    private static List<ConquestArrivalSlot> conquestArrivalSlots(
            MapResult map, List<ShuttleAssignment> assignments,
            TraversalAxis axis, Random rng, ShuttleArrivalPlan plan) {
        ShuttleArrivalPlan resolved = plan != null ? plan : ShuttleArrivalPlan.legacy();
        if (!resolved.paired()) {
            List<int[]> cells = pickConquestLandingZones(map.grid,
                    map.marineSpawnX, map.marineSpawnY, assignments.size(), axis, rng);
            List<ConquestArrivalSlot> legacy = new ArrayList<>(cells.size());
            for (int i = 0; i < cells.size(); i++) {
                int[] cell = cells.get(i);
                legacy.add(new ConquestArrivalSlot(
                        LandingPad.fallback(cell[0], cell[1]), -1, -1, 0,
                        i * SHUTTLE_DROP_STAGGER_SEC,
                        ShuttleMission.DEFAULT_REARM_DELAY_SEC));
            }
            return legacy;
        }

        int employerEnd = Math.min(resolved.firstPlayerShuttle(), assignments.size());
        int areaCount = Math.min(resolved.arrivalConfig().dropZoneCount(),
                Math.max(pairCount(employerEnd),
                        pairCount(assignments.size() - employerEnd)));
        List<Integer> selectedAreas = evenlySpacedAreaIndexes(
                map.landingAreas.size(), areaCount);
        if (selectedAreas.size() < areaCount) {
            throw new IllegalStateException("Conquest map authored "
                    + map.landingAreas.size() + " arrival areas but " + areaCount
                    + " drop zones are requested by the mission");
        }

        List<ConquestArrivalSlot> slots = new ArrayList<>(assignments.size());
        int[] groupCursor = {0};
        addPairedArrivalSlots(slots, assignments, map.landingAreas,
                selectedAreas, 0, employerEnd, groupCursor, rng,
                resolved.arrivalConfig().timingJitterSec());
        addPairedArrivalSlots(slots, assignments, map.landingAreas,
                selectedAreas, employerEnd, assignments.size(), groupCursor, rng,
                resolved.arrivalConfig().timingJitterSec());
        return slots;
    }

    private static void addPairedArrivalSlots(
            List<ConquestArrivalSlot> slots,
            List<ShuttleAssignment> assignments,
            List<LandingArea> areas, List<Integer> selectedAreas,
            int from, int to, int[] groupCursor, Random rng,
            float timingJitterSec) {
        int segmentGroup = 0;
        for (int i = from; i < to; i += LandingArea.BERTH_COUNT) {
            int group = groupCursor[0]++;
            int areaIndex = selectedAreas.get(segmentGroup % selectedAreas.size());
            LandingArea area = areas.get(areaIndex);
            int groupEnd = Math.min(to, i + LandingArea.BERTH_COUNT);
            int expected = 0;
            for (int member = i; member < groupEnd; member++) {
                expected += assignments.get(member).seatsPerSortie;
            }
            for (int member = i; member < groupEnd; member++) {
                float pendingJitter = timingJitter(rng, timingJitterSec);
                float rearmJitter = timingJitter(rng, timingJitterSec);
                slots.add(new ConquestArrivalSlot(
                        area.berth(member - i), areaIndex, group, expected,
                        group * SHUTTLE_DROP_STAGGER_SEC + pendingJitter,
                        ShuttleMission.DEFAULT_REARM_DELAY_SEC + rearmJitter));
            }
            segmentGroup++;
        }
    }

    private static float timingJitter(Random rng, float maximumSeconds) {
        if (maximumSeconds <= 0f) return 0f;
        return rng.nextFloat() * maximumSeconds;
    }

    private static int pairCount(int count) {
        return (Math.max(0, count) + LandingArea.BERTH_COUNT - 1)
                / LandingArea.BERTH_COUNT;
    }

    private static List<Integer> evenlySpacedAreaIndexes(int available, int needed) {
        if (needed <= 0 || available <= 0 || needed > available) return List.of();
        if (needed == 1) return List.of(available / 2);
        List<Integer> selected = new ArrayList<>(needed);
        for (int i = 0; i < needed; i++) {
            selected.add(Math.round(i * (available - 1f) / (needed - 1f)));
        }
        return selected;
    }

    private static void claimConquestSetupGarrisons(BattleSimulation sim) {
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER
                    || sim.squadMemberCount(squad.id) <= 0
                    || sim.role().role(sim.squadMemberAt(squad.id, 0))
                    != UnitRole.GARRISON) {
                continue;
            }
            sim.claimSquadCommand(squad.id, CommandAuthority.GARRISON,
                    "conquest-setup-garrison", "authored Conquest garrison");
        }
    }

    private static Set<Integer> captureDefenderMobileSquads(BattleSimulation sim) {
        Set<Integer> mobile = new java.util.TreeSet<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER
                    || sim.squadMemberCount(squad.id) <= 0) continue;
            long anchor = sim.squadMemberAt(squad.id, 0);
            if (sim.role().role(anchor) == UnitRole.PATROL) mobile.add(squad.id);
        }
        return mobile;
    }

    private static void claimSetupGarrisons(BattleSimulation sim,
                                             String issuer, String reason) {
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER
                    || sim.squadMemberCount(squad.id) <= 0
                    || sim.role().role(sim.squadMemberAt(squad.id, 0))
                    != UnitRole.GARRISON) continue;
            sim.claimSquadCommand(squad.id, CommandAuthority.GARRISON,
                    issuer, reason);
        }
    }

    private static void claimMissionMobileSquads(BattleSimulation sim,
                                                  Set<Integer> mobileSquads,
                                                  String issuer,
                                                  String reason) {
        for (int squadId : mobileSquads) {
            sim.claimSquadCommand(squadId, CommandAuthority.MISSION_COMMAND,
                    issuer, reason);
        }
    }

    private static DefenderForcePlan defenderForcePlan(
            MissionType type, OperationTier tier, RiskLevel risk,
            boolean enemyHasHeavyArmor, List<ShuttleAssignment> assignments,
            List<DefensePost> defensePosts, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport, GroundRosterProfile groundRoster) {
        // Conquest is an authored late-game set piece, not an encounter that
        // softens itself to match the committed detachment. Its population,
        // mech groups, fighter wings, and fortifications all survive intact
        // however much or little force the campaign player can afford to bring.
        float attackerScore = type == MissionType.CONQUEST
                ? Float.POSITIVE_INFINITY
                : BattleForceScore.attackers(assignments, marineFighterSupport);
        DefenderRoster roster = DefenderRoster.forMission(
                type, tier, risk, enemyHasHeavyArmor, attackerScore, groundRoster);
        FlybyRoster affordableFighters = BattleForceScore.affordableFighterSupport(
                enemyFighterSupport, roster, attackerScore);
        List<DefensePost> affordablePosts = BattleForceScore.affordableDefensePosts(
                defensePosts, roster, attackerScore,
                BattleForceScore.fighterSupport(affordableFighters));
        return new DefenderForcePlan(roster, affordablePosts, affordableFighters);
    }

    /**
     * Thin overload: a live Conquest battle for callers that only need the sim
     * (the campaign mission flow). Delegates to {@link #createConquestBuild}.
     */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor, RiskLevel risk,
                                                  TargetProfile profile) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor, risk, profile).sim();
    }

    /** Tier-aware conquest factory — the campaign mission flow's entry point. */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor,
                tier, risk, profile).sim();
    }

    /** Tier-aware conquest with both sides' authored fighter commitments. */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor,
                tier, risk, profile, marineFighterSupport,
                enemyFighterSupport).sim();
    }

    /** Tier-aware Conquest with a fixture-captured mission arrival plan. */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport,
                                                  ShuttleArrivalPlan arrivalPlan) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor,
                tier, risk, profile, marineFighterSupport,
                enemyFighterSupport, arrivalPlan).sim();
    }

    /**
     * Install the reinforcement layer on the sim. The trigger set depends on
     * mission semantics. Conquest with a biome layer and non-empty
     * {@link TacticalMap} gets the front-line dispatcher; every other mission
     * keeps the legacy compound-only trigger:
     * <ul>
     *   <li><b>Conquest (biome layer present):</b> {@link RecaptureTargetService}
     *       tracks every defender tactical node's garrison state, driven each
     *       tick by {@link RecaptureTargetSystem} (installed via
     *       {@link BattleSimulation#setRecaptureSystem}); {@link
     *       FrontLineReinforcementTrigger} round-robins dispatch across the
     *       nearest-to-defender contested biome slice. {@link
     *       GarrisonDepletedTrigger} is <em>not</em> registered here — both
     *       triggers would otherwise post duplicate requests for the same
     *       depleted compound. {@link CounterattackSystem} (installed via
     *       {@link BattleSimulation#setCounterattackSystem}) rides the same
     *       {@link RecaptureTargetService} to stage the offensive-inverse
     *       bulge — see {@code reinforcement-nouns.md}.</li>
     *   <li><b>Everything else:</b> {@link GarrisonDepletedTrigger} — defender
     *       compound strength drops below threshold. Only reacts to
     *       COMMAND_POST/BARRACKS/ARMORY, not the wider defender node set.</li>
     * </ul>
     * Non-Assault configurations also register {@link ObjectiveLostTrigger} —
     * a previously defender-held zone has been taken by marines. Assault omits
     * that exact-occupancy trigger; its defender commander reacts only to
     * faction-local reports while own-force garrison depletion remains legal.
     * <p>Means (all feasible ones compete on {@code arrivalSeconds}; the
     * soonest wins and registration order breaks a tie):
     * <ul>
     *   <li>{@link ConvoyMeans} — readable truck delivery; needs a road
     *       graph and a reachable rally.</li>
     *   <li>{@link ShuttleMeans} — air-drop; needs a walkable LZ within
     *       8 cells of the rally. Reuses the existing {@code AirSystem}
     *       state machine.</li>
     *   <li>{@link WalkInMeans} — the slowest over any real distance, and so
     *       the floor in practice rather than by placement; requires a held
     *       BARRACKS, then spawns infantry on the side-appropriate perimeter
     *       and pulls them toward the rally via {@code assignedNode}.</li>
     * </ul>
     * Non-Conquest maps register the same means set and self-gate harmlessly
     * (no compounds → no trigger or means supply; no road graph → convoy
     * yields; no LZ → shuttle yields; a held BARRACKS enables walk-in).
     * Replaces the prior
     * {@link #maybeSpawnDebugConvoy} debug-spawn path.
     *
     * @param axis traversal axis for the map; nullable on non-Conquest paths
     *             where there's no defender/attacker rear edge — walk-in
     *             falls back to a stable default edge.
     */
    private static void installReinforcementLayer(BattleSimulation sim, MapResult map,
                                                  MissionType missionType,
                                                  TraversalAxis axis,
                                                  GroundRosterProfile groundRoster,
                                                  RiskLevel risk,
                                                  DeliveryDeploymentPolicy deliveryPolicy) {
        ReinforcementService rs = sim.getReinforcementService();
        if (missionType == MissionType.CONQUEST && map.biomeMap != null
                && map.tacticalMap != null && map.tacticalMap.size() > 0) {
            RecaptureTargetService recaptureTargets = new RecaptureTargetService(map.tacticalMap, map.biomeMap);
            sim.setRecaptureSystem(new RecaptureTargetSystem(recaptureTargets, map.biomeMap));
            rs.addTrigger(new FrontLineReinforcementTrigger(recaptureTargets, axis));
            sim.setCounterattackSystem(new CounterattackSystem(
                    recaptureTargets, rs, sim.getBattleResources(), axis));
        } else {
            rs.addTrigger(new GarrisonDepletedTrigger());
        }
        if (missionType != MissionType.ASSAULT) {
            rs.addTrigger(new ObjectiveLostTrigger());
        }
        basedAircraft(sim, map, groundRoster == null ? null : groundRoster.primaryFactionId());
        rs.addMeans(new ConvoyMeans(map.roadGraph, axis, groundRoster, risk,
                deliveryPolicy));
        rs.addMeans(new ShuttleMeans(axis, groundRoster, risk,
                deliveryPolicy, map.landingPads));
        rs.addMeans(new WalkInMeans(axis, groundRoster, risk));
    }

    /** A generated conquest map and the seed that actually produced it. */
    private record ConquestMap(MapResult map, long seed) { }

    /** Seeds tried before a map that does not meet the mission's requirements is an error. */
    private static final int CONQUEST_MAP_ATTEMPTS = 8;

    /**
     * A conquest map that is actually a conquest map.
     *
     * <p>Generation declines politely all the way down — a ward with no room
     * builds no airfield, a claim that came up short takes a smaller lot — and
     * none of those passes knows what the mission was promised. A quarter of
     * conquest battles shipped without a garrison airfield that way, and the
     * only symptom was an enemy whose reinforcements all came from off map.
     *
     * <p>So the finished map is checked against what the mission requires, and
     * a map that falls short is re-rolled rather than played. The seed is the
     * generator's only input, so a different seed is the whole of the fix; the
     * first attempt uses the caller's own seed, which is why an ordinary battle
     * is bit-for-bit what it was. Running out of seeds is a real fault and says
     * so rather than handing back a map the mission cannot be played on.
     */
    private static ConquestMap conquestMap(int gridW, int gridH, long seed,
                                           TraversalAxis axis, TargetProfile profile) {
        EnumSet<MapFeature> missing = EnumSet.noneOf(MapFeature.class);
        for (int attempt = 0; attempt < CONQUEST_MAP_ATTEMPTS; attempt++) {
            long mapSeed = seed + attempt * 0x9E3779B97F4A7C15L;
            MapResult map = MAP_GEN.generate(gridW, gridH, mapSeed, axis, profile);
            missing = MissionMapRequirements.missingFrom(MissionType.CONQUEST, map);
            if (missing.isEmpty()) return new ConquestMap(map, mapSeed);
        }
        throw new IllegalStateException(MissionMapRequirements.describeFailure(
                MissionType.CONQUEST, seed, CONQUEST_MAP_ATTEMPTS, missing));
    }

    /**
     * Registers a berth on every garrison hardstand, so the field the air arm
     * flies from has actual aircraft standing on it.
     *
     * <p>The berths are what {@link ShuttleMeans} draws sorties from and what
     * an attacker destroys to end them, so they are installed with the
     * reinforcement layer rather than with the map's scenery. A battle whose
     * map has no authored airfield registers nothing and behaves exactly as it
     * did — the field cannot become a requirement on battles that were never
     * given one.
     *
     * <p>Distinct from {@link #stampParkedAircraft}, which dresses surplus
     * <em>civilian</em> port berths with scenery hulls. Those are props: no
     * unit, no HP, and nothing flies them.
     *
     * @param factionId the defending campaign faction, which decides what the
     *                  sheds have in them; null falls back to the full fighter
     *                  pool rather than to no aircraft
     */
    private static void basedAircraft(BattleSimulation sim, MapResult map, String factionId) {
        for (LandingPad pad : map.landingPads) {
            if (pad.purpose != LandingPad.Purpose.GARRISON_AIRFIELD) continue;
            sim.getAirfieldService().addBerth(pad, ShuttleMeans.SORTIE_TYPE,
                    AirBody.facingToward(pad.approach.dx, pad.approach.dy));
        }
        // The strip, if the map laid one. A field takes the first: a battle has
        // one garrison airfield, and a second strip would belong to a second
        // field this service does not yet model.
        if (map.runways.isEmpty()) return;
        sim.getAirfieldService().installRunway(map.runways.get(0));
        // Aircraft in the sheds, but only on a field that has somewhere for
        // them to roll. A shelter berth on a strip-less lot is an aircraft
        // sealed in a shed for the battle: it cannot lift off where it stands
        // and there is nothing to taxi to.
        List<FighterProfile> based = shedAircraft(
                FighterProfile.poolForFaction(factionId), map.shelters);
        for (int i = 0; i < map.shelters.size(); i++) {
            sim.getAirfieldService().addShelterBerth(map.shelters.get(i), based.get(i));
        }
    }

    /**
     * Which of the faction's fighters is kept in each shed, in shed order.
     *
     * <p>The pool is walked from a start decided by where the sheds are, so
     * consecutive sheds get consecutive aircraft: a field with three of them
     * has three different hulls in it, which is what a real dispersal looks
     * like and is also how a player learns what this faction flies. Nothing
     * is rolled, so a replay of a battle finds the same aircraft in the same
     * shed.
     *
     * <p>The offset is taken once rather than per shed, and that is the whole
     * of the design. Mixing each shed's own position into the walk was tried
     * and measured: it put the same aircraft in every shed on all
     * twenty-four fields sampled, because a station's sheds sit sixteen cells
     * apart along one axis and that step moved the pool index by exactly minus
     * one per shed — cancelling the walk precisely. A stride only strides if
     * nothing else is moving underneath it, so nothing else does.
     */
    static List<FighterProfile> shedAircraft(List<FighterProfile> pool,
                                             List<Gantry> shelters) {
        List<FighterProfile> based = new ArrayList<>(shelters.size());
        if (shelters.isEmpty()) return based;
        Gantry anyShelter = shelters.get(0);
        int start = Math.floorMod(
                anyShelter.centerX * 0x9E3779B9 + anyShelter.centerY * 0x85EBCA6B,
                pool.size());
        for (int i = 0; i < shelters.size(); i++) {
            based.add(pool.get((start + i) % pool.size()));
        }
        return based;
    }

    /**
     * Dev-only flag — when true, the (legacy) {@link #maybeSpawnDebugConvoy}
     * path drops a single defender-side militia truck per battle. Retained
     * for emergency rollback while the {@link ReinforcementService} v1 cut
     * beds in; not called from any active code path.
     */
    public static boolean DEBUG_SPAWN_TEST_CONVOY = true;
    /** Sim-seconds before the test convoy emerges from off-map. Long enough that the player sees the battle start before reinforcements arrive. */
    private static final float DEBUG_CONVOY_PENDING_SEC = 6f;
    /** Cells the off-map staging waypoint sits beyond the perimeter — the truck's first INCOMING waypoint, so it drives onto the map rather than popping in at the edge. */
    private static final float DEBUG_CONVOY_OFFMAP_PAD = 6f;

    /**
     * V1 debug spawn — drops one {@link VehicleType#HEAVY_APC} into the
     * sim if {@link #DEBUG_SPAWN_TEST_CONVOY} is on and the map has a
     * non-empty {@link RoadGraph}. Picks the perimeter node closest to the
     * defender spawn as the entry, the highest-degree non-perimeter node
     * closest to map center as the dropoff, and routes between them with
     * {@link ConvoyPlanner#planPath}. Outbound is the inbound path
     * reversed — the APC retreats the way it arrived (unused for
     * non-departing variants, but kept for compatibility).
     *
     * <p>Verbose-logs every step so a "I don't see the APC" report can be
     * traced through the starsector.log without code changes.
     */
    private static void maybeSpawnDebugConvoy(BattleSimulation sim, MapResult map) {
        if (!DEBUG_SPAWN_TEST_CONVOY) return;
        int gw = sim.getGrid().getWidth();
        int gh = sim.getGrid().getHeight();
        RoadGraph graph = map.roadGraph;
        if (graph == null || graph.nodes().isEmpty()) {
            LOG.warn("convoy: skip — roadGraph "
                    + (graph == null ? "null" : "empty"));
            ConvoySpawnDumper.dump("roadGraph " + (graph == null ? "null" : "empty"),
                    graph, null, null, gw, gh, map.defenderSpawnX, map.defenderSpawnY);
            return;
        }
        List<RoadGraph.Node> perim = graph.perimeterNodes();
        if (perim.isEmpty()) {
            LOG.warn("convoy: skip — no perimeter nodes in graph "
                    + "(" + graph.nodes().size() + " nodes, " + graph.edges().size() + " edges)");
            ConvoySpawnDumper.dump("no perimeter nodes",
                    graph, null, null, gw, gh, map.defenderSpawnX, map.defenderSpawnY);
            return;
        }
        // Iterate perimeter nodes in order of distance to the defender spawn.
        // For each candidate entry, restrict the destination search to the
        // graph component reachable from that entry — fixes the
        // disconnected-graph case where the closest-to-spawn perimeter node
        // sits in a tiny stub component and the actual interior junctions
        // live in a separate component. First entry that yields a usable
        // junction wins.
        List<RoadGraph.Node> perimByDist = sortedByDistance(perim, map.defenderSpawnX, map.defenderSpawnY);
        RoadGraph.Node entry = null;
        RoadGraph.Node dest = null;
        for (RoadGraph.Node candidate : perimByDist) {
            Set<RoadGraph.Node> reachable = reachableFrom(candidate);
            RoadGraph.Node candDest = bestInteriorJunctionWithin(reachable, gw / 2, gh / 2);
            if (candDest != null && candDest != candidate) {
                entry = candidate;
                dest = candDest;
                break;
            }
        }
        if (entry == null) {
            // Last perimeter we tried is the most useful one to dump for diagnostics.
            RoadGraph.Node lastEntry = perimByDist.isEmpty() ? null : perimByDist.get(perimByDist.size() - 1);
            LOG.warn("convoy: skip — no entry/dest pair in the same component "
                    + "(" + perimByDist.size() + " perimeter candidates tried)");
            ConvoySpawnDumper.dump("no entry/dest pair in any component",
                    graph, lastEntry, null, gw, gh, map.defenderSpawnX, map.defenderSpawnY);
            return;
        }
        List<RoadGraph.Edge> path = ConvoyPlanner.planPath(graph, entry, dest);
        if (path == null || path.isEmpty()) {
            // Shouldn't happen now that entry/dest are in the same component,
            // but keep the dump in case the reachable-set scan and BFS ever
            // disagree (e.g. graph mutation between the two queries).
            LOG.warn("convoy: skip — planPath failed entry→dest "
                    + "(" + entry.cellX + "," + entry.cellY + ")→("
                    + dest.cellX + "," + dest.cellY + ")");
            ConvoySpawnDumper.dump("planPath failed despite component check",
                    graph, entry, dest, gw, gh, map.defenderSpawnX, map.defenderSpawnY);
            return;
        }
        // Coarse road-graph corridor only — no spawn-time HA* refine, no
        // synthetic per-waypoint headings. The VehicleController's rolling
        // local planner rounds corners on the fly (see convoy-nouns.md).
        float[][] inboundCells = ConvoyPlanner.expandToWaypoints(path, entry);

        // Prepend an off-map staging waypoint perpendicular to the entry's
        // edge so the truck visibly drives onto the map rather than popping
        // in at the perimeter cell.
        float offX = entry.cellX + 0.5f;
        float offY = entry.cellY + 0.5f;
        if (entry.cellY == 0)            offY = -DEBUG_CONVOY_OFFMAP_PAD;
        else if (entry.cellY == gh - 1)  offY = gh + DEBUG_CONVOY_OFFMAP_PAD;
        else if (entry.cellX == 0)       offX = -DEBUG_CONVOY_OFFMAP_PAD;
        else if (entry.cellX == gw - 1)  offX = gw + DEBUG_CONVOY_OFFMAP_PAD;

        int len = inboundCells[0].length;
        float[] inX = new float[len + 1];
        float[] inY = new float[len + 1];
        inX[0] = offX;
        inY[0] = offY;
        System.arraycopy(inboundCells[0], 0, inX, 1, len);
        System.arraycopy(inboundCells[1], 0, inY, 1, len);

        RoadGraph.Node exitNode = ConvoyPlanner.pickExitNode(graph, dest, entry);
        List<RoadGraph.Edge> outPath = ConvoyPlanner.planPath(graph, dest, exitNode);
        float[][] outCells;
        if (outPath != null && !outPath.isEmpty()) {
            outCells = ConvoyPlanner.expandToWaypoints(outPath, dest);
        } else {
            outCells = new float[][]{ new float[]{dest.cellX + 0.5f}, new float[]{dest.cellY + 0.5f} };
        }

        int inLast = inboundCells[0].length - 1;
        float lzX = inboundCells[0][inLast];
        float lzY = inboundCells[1][inLast];
        float distLzToDest = (float) Math.sqrt(
                (lzX - outCells[0][0]) * (lzX - outCells[0][0])
              + (lzY - outCells[1][0]) * (lzY - outCells[1][0]));
        if (distLzToDest > 0.5f) {
            float[] pX = new float[outCells[0].length + 1];
            float[] pY = new float[outCells[1].length + 1];
            pX[0] = lzX;
            pY[0] = lzY;
            System.arraycopy(outCells[0], 0, pX, 1, outCells[0].length);
            System.arraycopy(outCells[1], 0, pY, 1, outCells[1].length);
            outCells = new float[][] { pX, pY };
        }

        float exitOffX = exitNode.cellX + 0.5f;
        float exitOffY = exitNode.cellY + 0.5f;
        if (exitNode.cellY == 0)            exitOffY = -DEBUG_CONVOY_OFFMAP_PAD;
        else if (exitNode.cellY == gh - 1)  exitOffY = gh + DEBUG_CONVOY_OFFMAP_PAD;
        else if (exitNode.cellX == 0)       exitOffX = -DEBUG_CONVOY_OFFMAP_PAD;
        else if (exitNode.cellX == gw - 1)  exitOffX = gw + DEBUG_CONVOY_OFFMAP_PAD;
        int outLen = outCells[0].length;
        float[] outX = new float[outLen + 1];
        float[] outY = new float[outLen + 1];
        System.arraycopy(outCells[0], 0, outX, 0, outLen);
        System.arraycopy(outCells[1], 0, outY, 0, outLen);
        outX[outLen] = exitOffX;
        outY[outLen] = exitOffY;

        VehicleMission mission = new VehicleMission(
                inX, inY, outX, outY,
                DEBUG_CONVOY_PENDING_SEC, VehicleType.HEAVY_APC.capacity);
        sim.addConvoyVehicle(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        LOG.info("convoy: spawned HEAVY_APC entry=(" + entry.cellX + "," + entry.cellY
                + ") exit=(" + exitNode.cellX + "," + exitNode.cellY
                + ") dest=(" + dest.cellX + "," + dest.cellY
                + ") path=" + path.size() + "edges/" + inX.length + "wps");
    }

    /** Sort {@code nodes} by squared distance to ({@code x, y}), ascending. Defensive copy — input list is not mutated. */
    private static List<RoadGraph.Node> sortedByDistance(List<RoadGraph.Node> nodes, int x, int y) {
        List<RoadGraph.Node> out = new ArrayList<>(nodes);
        out.sort((a, b) -> {
            int adx = a.cellX - x, ady = a.cellY - y;
            int bdx = b.cellX - x, bdy = b.cellY - y;
            return Integer.compare(adx*adx + ady*ady, bdx*bdx + bdy*bdy);
        });
        return out;
    }

    /** BFS flood from {@code seed} over edges — returns the seed's connected component as a Set. */
    private static Set<RoadGraph.Node> reachableFrom(RoadGraph.Node seed) {
        Set<RoadGraph.Node> seen = new HashSet<>();
        Deque<RoadGraph.Node> q = new ArrayDeque<>();
        q.add(seed);
        seen.add(seed);
        while (!q.isEmpty()) {
            RoadGraph.Node n = q.poll();
            for (RoadGraph.Edge e : n.edges()) {
                RoadGraph.Node nxt = e.otherEnd(n);
                if (seen.add(nxt)) q.add(nxt);
            }
        }
        return seen;
    }

    /**
     * Best interior junction within a reachable set, near ({@code cx, cy}).
     * Walks degree thresholds from {@code 3} down to {@code 2} — a degree-2
     * interior node is a worse drop-off (no choice but to turn around at
     * arrival) but still better than a failed spawn, especially when a
     * stub component has only chain nodes.
     */
    private static RoadGraph.Node bestInteriorJunctionWithin(Set<RoadGraph.Node> reachable, int cx, int cy) {
        for (int minDegree = 3; minDegree >= 2; minDegree--) {
            RoadGraph.Node best = null;
            int bestD2 = Integer.MAX_VALUE;
            for (RoadGraph.Node n : reachable) {
                if (n.perimeter) continue;
                if (n.degree() < minDegree) continue;
                int dx = n.cellX - cx;
                int dy = n.cellY - cy;
                int d2 = dx * dx + dy * dy;
                if (d2 < bestD2) { bestD2 = d2; best = n; }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * Post-hoc wiring of GUARDPOST defender squads to their {@link DefensePost}.
     * Done after {@link #allocateDefenders} rather than threading the post list
     * into the allocator: the allocator stays oblivious to the post tier (it
     * just sees a tactical node), and the GUARDPOST-specific tuning (patrol
     * radius pulled from {@link DefensePostKind#patrolRadius}, post linkage for
     * release-on-turrets-dead) lives in one localized pass here.
     *
     * <p>Match by anchor position: the stamper emits one GUARDPOST node per
     * post at the post's anchor cell, so anchor equality is a 1:1 lookup.
     */
    private static void linkGuardpostSquads(BattleSimulation sim, List<DefensePost> posts) {
        if (posts == null || posts.isEmpty()) return;
        for (Squad squad : sim.getSquads()) {
            TacticalNode node = squad.assignedNode;
            if (node == null || node.kind != TacticalNode.Kind.GUARDPOST) continue;
            for (DefensePost post : posts) {
                if (post.anchorX == node.anchorX && post.anchorY == node.anchorY) {
                    squad.defensePost = post;
                    squad.patrolRadius = post.tier.patrolRadius;
                    break;
                }
            }
        }
    }

    /**
     * Pick the entry/exit off-map points for a shuttle drop. Returns
     * {@code {entryX, entryY, exitX, exitY}}. In legacy mode ({@code axis}
     * null) the shuttle drops in from above the top of the grid; in conquest
     * mode the entry is off the attacker-facing edge derived from the axis
     * (south edge for SOUTH_TO_NORTH, west edge for WEST_TO_EAST). Exit sits
     * one extra step beyond entry so the departing shuttle has a moment of
     * visible climb before it disappears.
     */
    private static float[] shuttleEntryFor(float lzCenterX, float lzCenterY,
                                           int gridW, int gridH, TraversalAxis axis) {
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            // Attacker side = south = low y. Entry below the grid, exit further below.
            return new float[]{
                    lzCenterX, -SHUTTLE_OFFMAP_Y,
                    lzCenterX, -SHUTTLE_OFFMAP_Y - 4f };
        }
        if (axis == TraversalAxis.WEST_TO_EAST) {
            // Attacker side = west = low x. Entry left of the grid.
            return new float[]{
                    -SHUTTLE_OFFMAP_Y,        lzCenterY,
                    -SHUTTLE_OFFMAP_Y - 4f,   lzCenterY };
        }
        // Legacy: drop from above the top edge.
        return new float[]{
                lzCenterX, gridH + SHUTTLE_OFFMAP_Y,
                lzCenterX, gridH + SHUTTLE_OFFMAP_Y + 4f };
    }

    /** Entry/exit vector authored by a civilian berth's clear approach side. */
    private static float[] shuttleEntryFor(float lzCenterX, float lzCenterY,
                                           int gridW, int gridH,
                                           LandingPad.Approach approach) {
        switch (approach) {
            case SOUTH:
                return new float[]{lzCenterX, -SHUTTLE_OFFMAP_Y,
                        lzCenterX, -SHUTTLE_OFFMAP_Y - 4f};
            case EAST:
                return new float[]{gridW + SHUTTLE_OFFMAP_Y, lzCenterY,
                        gridW + SHUTTLE_OFFMAP_Y + 4f, lzCenterY};
            case WEST:
                return new float[]{-SHUTTLE_OFFMAP_Y, lzCenterY,
                        -SHUTTLE_OFFMAP_Y - 4f, lzCenterY};
            case NORTH:
            default:
                return new float[]{lzCenterX, gridH + SHUTTLE_OFFMAP_Y,
                        lzCenterX, gridH + SHUTTLE_OFFMAP_Y + 4f};
        }
    }

    /** Installs the physical evac craft and the local defense-line reserve. */
    private static void installRescuePickup(
            BattleSimulation sim, CivilianEvacuationPlacement placement,
            int evacueeCount, long seed, RiskLevel risk,
            int gridW, int gridH) {
        LandingPad.Approach approach = nearestEdgeApproach(
                placement.liftX, placement.liftY, gridW, gridH);
        float pickupX = placement.liftX + 0.5f;
        float pickupY = placement.liftY + 0.5f;
        float[] pickupEntry = shuttleEntryFor(
                pickupX, pickupY, gridW, gridH, approach);
        long pickupId = sim.spawnShuttle(
                ShuttleType.VALKYRIE, Faction.CIVILIAN,
                pickupX, pickupY,
                pickupEntry[0], pickupEntry[1],
                pickupEntry[2], pickupEntry[3],
                RescuePickupSupportSystem.INITIAL_ARRIVAL_DELAY_SECONDS);
        ShuttleMission pickup = sim.world().mission(pickupId);
        pickup.marinesRemaining = 0;
        pickup.awaitingEvacuees = true;
        pickup.evacueeCapacity = evacueeCount;
        if (!sim.attachCivilianPickupShuttle(pickupId)) {
            throw new IllegalStateException(
                    "civilian rescue pickup shuttle attachment failed");
        }

        int[] route = GridPathfinder.findPath(sim.getGrid(),
                placement.liftX, placement.liftY,
                placement.shelterApproachX, placement.shelterApproachY);
        int supportCell = Math.min(7, Paths.cellCount(route) - 1);
        float supportX = Paths.isEmpty(route)
                ? pickupX : Paths.cellX(route, supportCell) + 0.5f;
        float supportY = Paths.isEmpty(route)
                ? pickupY : Paths.cellY(route, supportCell) + 0.5f;
        float[] supportEntry = shuttleEntryFor(
                supportX, supportY, gridW, gridH, approach);
        if (!sim.configureRescuePickupSupport(
                placement, supportX, supportY,
                supportEntry[0], supportEntry[1],
                supportEntry[2], supportEntry[3], seed, risk)) {
            throw new IllegalStateException(
                    "civilian rescue pickup support configuration failed");
        }
    }

    private static LandingPad.Approach nearestEdgeApproach(
            int x, int y, int width, int height) {
        int nearest = y;
        LandingPad.Approach approach = LandingPad.Approach.SOUTH;
        if (height - 1 - y < nearest) {
            nearest = height - 1 - y;
            approach = LandingPad.Approach.NORTH;
        }
        if (x < nearest) {
            nearest = x;
            approach = LandingPad.Approach.WEST;
        }
        if (width - 1 - x < nearest) {
            approach = LandingPad.Approach.EAST;
        }
        return approach;
    }

    private static int[] spawnOpeningDefenseLine(
            BattleSimulation sim, MapResult map, LandingPad firstLz,
            Random rng) {
        int towardEnemyX = Integer.compare(map.defenderSpawnX, firstLz.centerX);
        int towardEnemyY = Integer.compare(map.defenderSpawnY, firstLz.centerY);
        int anchorX = clamp(firstLz.centerX + towardEnemyX * OPENING_LINE_OFFSET,
                0, map.grid.getWidth() - 1);
        int anchorY = clamp(firstLz.centerY + towardEnemyY * OPENING_LINE_OFFSET,
                0, map.grid.getHeight() - 1);
        List<int[]> cells = pickDefensiveCluster(
                map.grid, anchorX, anchorY, OPENING_LOCAL_MILITIA);
        spawnOpeningMilitiaSquads(sim, cells, Faction.MARINE,
                "local", true, rng);
        return cells.isEmpty()
                ? new int[]{anchorX, anchorY} : cells.get(0).clone();
    }

    private static int[] spawnOpeningRaiders(
            BattleSimulation sim, MapResult map, Random rng) {
        List<int[]> cells = pickDefensiveCluster(map.grid,
                map.defenderSpawnX, map.defenderSpawnY, OPENING_RAIDERS);
        spawnOpeningMilitiaSquads(sim, cells, Faction.DEFENDER,
                "raider", false, rng);
        return cells.isEmpty()
                ? new int[]{map.defenderSpawnX, map.defenderSpawnY}
                : cells.get(0).clone();
    }

    /** Spawns intentionally low-grade four-person militia squads for either side. */
    private static void spawnOpeningMilitiaSquads(
            BattleSimulation sim, List<int[]> cells, Faction faction,
            String idPrefix, boolean localGarrison, Random rng) {
        Squad squad = null;
        int squadMembers = 0;
        int squadIndex = -1;
        for (int index = 0; index < cells.size(); index++) {
            int[] cell = cells.get(index);
            if (squad == null || squadMembers >= OPENING_FIRETEAM_SIZE) {
                if (squad != null) squad.originalSize = squadMembers;
                squadIndex++;
                squadMembers = 0;
                int squadId = sim.mintSquad(faction, UnitType.MILITIA);
                squad = sim.getSquad(squadId);
                if (localGarrison) {
                    squad.assignedNode = openingDefenseNode(
                            cell[0], cell[1], faction, sim.getGrid());
                    squad.patrolRadius = 4;
                    sim.assignSquadCommand(ObjectiveAssignment.holdNode(
                                    squad.id, squad.assignedNode),
                            CommandAuthority.GARRISON,
                            "opening-local-garrison",
                            "preserve authored relief post");
                } else {
                    sim.claimSquadCommand(squad.id,
                            CommandAuthority.MISSION_COMMAND,
                            OpeningOperationCommand.issuer(faction),
                            "opening-operation starting force");
                }
            }
            EntitySpec unit = makeOpeningMilitia(
                    idPrefix + "-" + squadIndex + "-" + squadMembers,
                    faction, cell[0], cell[1], rng)
                    .role(localGarrison ? UnitRole.GARRISON : UnitRole.PATROL)
                    .squad(squad.id);
            if (localGarrison) unit.home(cell[0], cell[1]);
            sim.spawn(unit);
            squadMembers++;
        }
        if (squad != null) squad.originalSize = squadMembers;
    }

    private static EntitySpec makeOpeningMilitia(
            String id, Faction faction, int x, int y, Random rng) {
        return new EntitySpec(id, faction, UnitType.MILITIA, x, y)
                .primaryWeapon(
                        InfantryLoadoutRolls.defenderPrimary(
                                UnitType.MILITIA, rng),
                        InfantryLoadoutRolls.defenderEquipmentGrade(
                                UnitType.MILITIA, RiskLevel.LOW, rng),
                        InfantryLoadoutRolls.defenderProfile(
                                UnitType.MILITIA, RiskLevel.LOW, rng));
    }

    private static TacticalNode openingDefenseNode(
            int x, int y, Faction faction, NavigationGrid grid) {
        return new TacticalNode(TacticalNode.Kind.OBJECTIVE, x, y,
                clamp(x - 2, 0, grid.getWidth() - 1),
                clamp(y - 2, 0, grid.getHeight() - 1),
                clamp(x + 2, 0, grid.getWidth() - 1),
                clamp(y + 2, 0, grid.getHeight() - 1),
                faction, 50, OPENING_FIRETEAM_SIZE);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Distributes defender units across the map. When the {@link TacticalMap}
     * carries DEFENDER-leaning nodes (towers, gates, command posts, etc.),
     * top-priority nodes get GARRISON squads of their declared
     * {@link TacticalNode#garrisonSize}, with stiffening regulars tucked into
     * the highest-priority posts and any HEAVY_MECH lance bundled at the very
     * top slot so a 3-mech lance lands as one coordinated garrison. Any
     * defenders remaining after garrisons are filled bundle into PATROL squads
     * anchored to spare nodes, so the city has foot traffic instead of all
     * defenders sitting on the wall.
     *
     * <p>Force size + composition come from the {@link DefenderRoster} —
     * derived from {@link com.dillon.starsectormarines.ops.MissionType} +
     * {@link com.dillon.starsectormarines.ops.RiskLevel}. HIGH CONQUEST can
     * land 200 defenders; LOW SABOTAGE bottoms out at 12. The roster also
     * carries {@link DefenderRoster#patrolSquadSize} so larger forces don't
     * fragment into dozens of three-member patrols.
     *
     * <p>Legacy maps with no tactical nodes fall back to the original single-
     * cluster spawn around {@code map.defenderSpawnX/Y}: a flat list of
     * defenders, biased to high-cover cells, all as plain COMBATANTs. This
     * preserves placeholder/legacy generator output until those gens grow a
     * tactical layer of their own.
     */
    private static void allocateDefenders(BattleSimulation sim, MapResult map,
                                          DefenderRoster roster,
                                          GroundRosterProfile groundRoster,
                                          Random rng) {
        allocateDefenders(sim, map, roster, groundRoster, rng, 0);
    }

    /**
     * Variant for missions that author a mobile command pool. Pass 1 will not
     * consume the final {@code minimumPatrolMembers} as garrisons; those members
     * flow through the ordinary homogeneous patrol-squad pass below.
     */
    private static void allocateDefenders(BattleSimulation sim, MapResult map,
                                          DefenderRoster roster,
                                          GroundRosterProfile groundRoster,
                                          Random rng,
                                          int minimumPatrolMembers) {
        TacticalMap tactical = map.tacticalMap;
        List<TacticalNode> defenderNodes = (tactical != null)
                ? new ArrayList<>(tactical.forFaction(Faction.DEFENDER))
                : Collections.emptyList();
        if (defenderNodes.isEmpty()) {
            List<int[]> cells = pickDefensiveCluster(map.grid, map.defenderSpawnX, map.defenderSpawnY, roster.totalCount);
            spawnLegacyDefenderCluster(sim, cells, roster, groundRoster, rng);
            return;
        }
        // Highest priority first — these get garrisons; the rest become patrol anchors.
        defenderNodes.sort(Comparator.comparingInt((TacticalNode n) -> -n.priorityScore));

        // Two separate queues — mechs and infantry never share a squad.
        // The planner ships distinct goal/action libraries for each, and a
        // mixed-arms squad would force a unified planner with two different
        // kinematic profiles. Adjacency between squads (Story E,
        // mech-screened advance) is the integration point instead. Mechs
        // drain first into the highest-priority slots; once exhausted,
        // infantry fills the rest.
        UnitType mechType = UnitType.HEAVY_MECH;
        Deque<MechVariant> mechQueue = new ArrayDeque<>(roster.mechVariants);
        Deque<GroundRosterProfile.ForceTier> infQueue = new ArrayDeque<>();
        for (int i = 0; i < roster.eliteCount; i++) {
            infQueue.add(GroundRosterProfile.ForceTier.ELITE);
        }
        for (int i = 0; i < roster.militiaCount; i++) {
            infQueue.add(GroundRosterProfile.ForceTier.BULK);
        }

        int defenderIdx = 0;
        List<TacticalNode> patrolAnchors = new ArrayList<>();

        // Pass 1 — garrison the highest-priority nodes. Each garrison draws
        // from a single source queue (mechs first while available, then
        // infantry) so the resulting squad is homogeneous.
        for (TacticalNode node : defenderNodes) {
            boolean spawningMechs = !mechQueue.isEmpty();
            int sourceSize = spawningMechs ? mechQueue.size() : infQueue.size();
            if (sourceSize == 0) { patrolAnchors.add(node); continue; }
            int remaining = mechQueue.size() + infQueue.size();
            int garrisonBudget = Math.max(0, remaining
                    - Math.max(0, minimumPatrolMembers));
            if (garrisonBudget == 0) {
                patrolAnchors.add(node);
                continue;
            }
            int want = Math.min(node.garrisonSize,
                    Math.min(sourceSize, garrisonBudget));
            List<int[]> cells = pickCellsForNode(map.grid, sim.getZoneGraph(),
                    node, GARRISON_SPAWN_RADIUS, want);
            if (cells.isEmpty()) { patrolAnchors.add(node); continue; }
            Squad squad = null;
            int spawned = 0;
            for (int[] cell : cells) {
                if ((spawningMechs ? mechQueue : infQueue).isEmpty()) break;
                MechVariant mechVariant = spawningMechs ? mechQueue.poll() : null;
                GroundRosterProfile.ForceTier forceTier = mechVariant == null ? infQueue.poll() : null;
                UnitType type = mechVariant != null ? mechType : groundRoster.unitType(forceTier);
                EntitySpec unit = makeDefender("d" + defenderIdx++, type, cell[0], cell[1],
                        roster.risk, groundRoster, forceTier, rng, mechVariant);
                unit.role(UnitRole.GARRISON);
                unit.home(cell[0], cell[1]);
                if (squad == null) {
                    int sid = sim.mintSquad(Faction.DEFENDER, type);
                    squad = sim.getSquad(sid);
                    squad.assignedNode = node;
                    // Story A: infantry garrisons hold fire until the
                    // kill-zone gate trips. Mech garrisons skip the gate —
                    // overwatch discipline lives in their planner-side
                    // doctrine (LR Support withholds short-range weapons),
                    // not in a fire-suppression flag on the squad.
                    squad.holdsFireUntilKillZone = (mechVariant == null);
                }
                unit.squad(squad.id);
                long member = sim.spawn(unit);
                attachMechLoadout(sim, member, mechVariant);
                spawned++;
            }
            if (squad != null) squad.originalSize = spawned;
        }

        // Pass 2 — leftover defenders form patrol squads. Each patrol takes
        // up to roster.patrolSquadSize members from a single source queue
        // (mechs first while available, then infantry), anchored at a spare
        // node (or a top-priority node if there are no spares). Cycles
        // through the anchor list when defenders exceed
        // anchors * patrolSquadSize.
        if (mechQueue.isEmpty() && infQueue.isEmpty()) return;
        List<TacticalNode> anchorPool = patrolAnchors.isEmpty() ? defenderNodes : patrolAnchors;
        int anchorIdx = 0;
        while (!mechQueue.isEmpty() || !infQueue.isEmpty()) {
            if (anchorPool.isEmpty()) break;
            boolean spawningMechs = !mechQueue.isEmpty();
            TacticalNode anchor = anchorPool.get(anchorIdx % anchorPool.size());
            anchorIdx++;
            int sourceSize = spawningMechs ? mechQueue.size() : infQueue.size();
            int want = Math.min(roster.patrolSquadSize, sourceSize);
            List<int[]> cells = pickCellsForNode(map.grid, sim.getZoneGraph(),
                    anchor, GARRISON_SPAWN_RADIUS + 2, want);
            if (cells.isEmpty()) {
                // Couldn't spawn here — drop this anchor from the pool so we
                // don't get stuck cycling. If the pool empties, the remaining
                // queues are silently dropped (lore: "they didn't make it to
                // the city in time"). Better than infinite-looping the spawn.
                anchorPool.remove(anchor);
                if (anchorPool.isEmpty()) break;
                anchorIdx = 0;
                continue;
            }
            Squad squad = null;
            int spawned = 0;
            for (int[] cell : cells) {
                if ((spawningMechs ? mechQueue : infQueue).isEmpty()) break;
                MechVariant mechVariant = spawningMechs ? mechQueue.poll() : null;
                GroundRosterProfile.ForceTier forceTier = mechVariant == null ? infQueue.poll() : null;
                UnitType type = mechVariant != null ? mechType : groundRoster.unitType(forceTier);
                EntitySpec unit = makeDefender("d" + defenderIdx++, type, cell[0], cell[1],
                        roster.risk, groundRoster, forceTier, rng, mechVariant);
                unit.role(UnitRole.PATROL);
                if (squad == null) {
                    int sid = sim.mintSquad(Faction.DEFENDER, type);
                    squad = sim.getSquad(sid);
                    squad.assignedNode = anchor;
                }
                unit.squad(squad.id);
                long member = sim.spawn(unit);
                attachMechLoadout(sim, member, mechVariant);
                spawned++;
            }
            if (squad != null) squad.originalSize = spawned;
        }
    }

    /** Original cluster-spawn behavior — kept around for maps with no tactical layer (legacy UrbanMapGenerator, placeholder gens). Composition follows the roster: mechs first, then red marines, then militia. */
    /**
     * Fallback spawn used when the map has no defender tactical nodes (any
     * mission whose generator skips BspCityGenerator's tactical pass). Mints
     * {@link UnitRole#PATROL} squads of up to {@link DefenderRoster#patrolSquadSize}
     * each, with {@code assignedNode = null} — {@link com.dillon.starsectormarines.battle.infantry.PatrolRoute}
     * seeds off {@link Squad#centroidX}/{@code centroidY} when the anchor is
     * null, so the squad wanders from its spawn cluster outward and engages
     * on enemy LOS like the tactical-node patrol path.
     *
     * <p>Type queue order (mechs → elites → militia) matches
     * {@link #allocateDefenders}'s Pass-1/2 ordering so the highest-rank
     * defenders cluster into the first squad rather than scattering.
     */
    private static void spawnLegacyDefenderCluster(BattleSimulation sim, List<int[]> cells,
                                                   DefenderRoster roster,
                                                   GroundRosterProfile groundRoster,
                                                   Random rng) {
        // Same mech-vs-infantry split as allocateDefenders — each squad
        // drains from a single source queue so mechs and infantry never
        // share membership.
        UnitType mechType = UnitType.HEAVY_MECH;
        Deque<MechVariant> mechQueue = new ArrayDeque<>(roster.mechVariants);
        Deque<GroundRosterProfile.ForceTier> infQueue = new ArrayDeque<>();
        for (int i = 0; i < roster.eliteCount; i++) {
            infQueue.add(GroundRosterProfile.ForceTier.ELITE);
        }
        for (int i = 0; i < roster.militiaCount; i++) {
            infQueue.add(GroundRosterProfile.ForceTier.BULK);
        }

        int defenderIdx = 0;
        int cellIdx = 0;
        while ((!mechQueue.isEmpty() || !infQueue.isEmpty()) && cellIdx < cells.size()) {
            boolean spawningMechs = !mechQueue.isEmpty();
            int sourceSize = spawningMechs ? mechQueue.size() : infQueue.size();
            int squadSize = Math.min(roster.patrolSquadSize,
                    Math.min(sourceSize, cells.size() - cellIdx));
            Squad squad = null;
            int spawned = 0;
            for (int s = 0; s < squadSize; s++) {
                int[] cell = cells.get(cellIdx++);
                MechVariant mechVariant = spawningMechs ? mechQueue.poll() : null;
                GroundRosterProfile.ForceTier forceTier = mechVariant == null ? infQueue.poll() : null;
                UnitType type = mechVariant != null ? mechType : groundRoster.unitType(forceTier);
                EntitySpec unit = makeDefender("d" + defenderIdx++, type, cell[0], cell[1],
                        roster.risk, groundRoster, forceTier, rng, mechVariant);
                unit.role(UnitRole.PATROL);
                if (squad == null) {
                    int sid = sim.mintSquad(Faction.DEFENDER, type);
                    squad = sim.getSquad(sid);
                    // No assignedNode — PatrolRoute falls back to the
                    // squad centroid as its wander seed when this is null.
                }
                unit.squad(squad.id);
                long member = sim.spawn(unit);
                attachMechLoadout(sim, member, mechVariant);
                spawned++;
            }
            if (squad != null) squad.originalSize = spawned;
        }
    }

    /** Builds a bare defender {@code Entity}. Mech loadout (for mech types) is a
     * presence component attached <em>after</em> the unit is added to the sim —
     * see {@link #attachMechLoadout} — because the loadout store is keyed by the
     * entity id, which isn't assigned until {@code addUnit}. */
    private static EntitySpec makeDefender(String id, UnitType type, int x, int y,
                                           RiskLevel risk, GroundRosterProfile groundRoster,
                                           GroundRosterProfile.ForceTier forceTier,
                                           Random rng, MechVariant mechVariant) {
        EntitySpec unit = new EntitySpec(id, Faction.DEFENDER, type, x, y);
        if (mechVariant != null) return mechVariant.applyTo(unit);
        if (!type.drawnAsLayers()) return unit;
        InfantryLoadoutRolls.defenderLoadout(
                groundRoster, forceTier, risk, rng).seedInto(unit);
        return unit;
    }

    /**
     * Attaches a {@link MechLoadoutComponent} (the world's {@code MECH_LOADOUT}
     * component) to a just-added unit that spawned as a mech ({@code mechVariant !=
     * null} — the caller already decided mech-ness from the source queue). No-op
     * for infantry. <b>Must run after {@code sim.addUnit}</b>: the attach is an
     * {@code addComponent} row-move keyed by {@code entityId}, which the registry
     * assigns at allocate time.
     */
    private static void attachMechLoadout(BattleSimulation sim, long unit, MechVariant mechVariant) {
        if (mechVariant != null) {
            sim.world().attachMechLoadout(unit,
                    mechVariant.createLoadout(mechVariant.defaultRole));
        }
    }

    /**
     * Resolves member cells for an authored tactical place. Valid authored
     * stand positions win in their declared order; a derived nearby pool fills
     * any remaining slots. Nodes without stand positions retain the historical
     * cover-sorted nearby-cell behavior exactly.
     */
    public static List<int[]> pickCellsForNode(NavigationGrid grid, ZoneGraph zones,
                                               TacticalNode node, int radius, int count) {
        if (count <= 0) return Collections.emptyList();
        List<TacticalNode.StandPosition> authored = node.standPositions();
        if (authored.isEmpty()) {
            return pickCellsNear(grid, zones, node.anchorX, node.anchorY, radius, count);
        }

        List<int[]> out = new ArrayList<>(count);
        Set<Long> claimed = new HashSet<>();
        for (TacticalNode.StandPosition position : authored) {
            if (out.size() >= count) break;
            if (!grid.inBounds(position.x(), position.y())
                    || !grid.isWalkable(position.x(), position.y())
                    || zones.zoneIdAt(position.x(), position.y()) < 0) continue;
            long cellKey = key(position.x(), position.y());
            if (!claimed.add(cellKey)) continue;
            out.add(new int[]{position.x(), position.y()});
        }
        if (out.size() >= count) return out;

        for (int[] fallback : pickCellsNear(grid, zones,
                node.anchorX, node.anchorY, radius, count)) {
            if (out.size() >= count) break;
            if (!claimed.add(key(fallback[0], fallback[1]))) continue;
            out.add(fallback);
        }
        return out;
    }

    /**
     * Pick walkable cells around {@code (ax, ay)} for spawning a squad — the
     * top {@code count} cells within Manhattan {@code radius} sorted by cover
     * desc, distance asc. Used by the defender allocator at setup time and by
     * {@link com.dillon.starsectormarines.battle.squad.SquadFallbackSystem}
     * when a squad falls back to a sibling tactical node mid-battle.
     *
     * <p>Routes through the live {@link ZoneGraph} so the spawn pool respects
     * the same room-partition the AI tier already uses everywhere else. Three
     * seed cases to handle, all reading off the zone structure:
     *
     * <ol>
     *   <li><b>Indoor walkable seed</b> (compound interior anchor) — seed's
     *       zone is the room. Pool draws from that one zone, bounded to
     *       Manhattan radius. A multi-room building's partition doorway is
     *       its own zone (per {@link com.dillon.starsectormarines.battle.nav.zone.ZoneDetector}),
     *       so cells in the antechamber are in a separate zone and never
     *       leak into the throne-room garrison's pool.</li>
     *   <li><b>Walkable doorway seed</b> (a GATE anchor) — seed's zone is a
     *       1-cell doorway zone. We extend the pool to the doorway's adjacent
     *       zones via the portal graph, so gate defenders still spawn on
     *       both sides of the gap.</li>
     *   <li><b>Unwalkable seed</b> (3×3 wall-mount tower, turret pylon) —
     *       seed has no zone. Walk outward cell-by-cell with walls
     *       transparent until we accumulate every zone reachable within
     *       radius, then draw from all of them. Matches the historical
     *       wall-mounted-tower spawn behavior (cells on both sides of the
     *       wall ring); the cover-based sort handles the defender-side bias.
     *   </li>
     * </ol>
     */
    public static List<int[]> pickCellsNear(NavigationGrid grid, ZoneGraph zones,
                                            int ax, int ay, int radius, int count) {
        java.util.Set<Integer> spawnZones = resolveSpawnZones(grid, zones, ax, ay, radius);
        if (spawnZones.isEmpty()) return Collections.emptyList();

        // Sweep the (2r+1)² rectangle around the seed and pick cells whose
        // zone is in the spawn set. Bounded by the rectangle (not by zone
        // membership iteration) so outdoor anchors with huge zones don't
        // pay O(|zone|) per call — perimeter towers in a 5000-cell
        // courtyard zone now stay O(radius²) like the historical BFS.
        List<int[]> pool = new ArrayList<>();
        for (int y = ay - radius; y <= ay + radius; y++) {
            for (int x = ax - radius; x <= ax + radius; x++) {
                int dist = Math.abs(x - ax) + Math.abs(y - ay);
                if (dist > radius) continue;
                if (!grid.inBounds(x, y)) continue;
                int zid = zones.zoneIdAt(x, y);
                if (zid < 0 || !spawnZones.contains(zid)) continue;
                pool.add(new int[]{x, y, dist});
            }
        }

        pool.sort(Comparator
                .comparingInt((int[] p) -> -grid.getCoverAt(p[0], p[1]))
                .thenComparingInt(p -> p[2]));
        int take = Math.min(count, pool.size());
        List<int[]> out = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            int[] p = pool.get(i);
            out.add(new int[]{p[0], p[1]});
        }
        return out;
    }

    /**
     * Resolve the set of zones {@link #pickCellsNear} draws cells from for a
     * given seed. See that method's javadoc for the three-case rationale.
     */
    private static java.util.Set<Integer> resolveSpawnZones(NavigationGrid grid, ZoneGraph zones,
                                                            int ax, int ay, int radius) {
        java.util.Set<Integer> result = new java.util.LinkedHashSet<>();
        if (!grid.inBounds(ax, ay)) return result;

        int seedZoneId = zones.zoneIdAt(ax, ay);
        if (seedZoneId >= 0) {
            result.add(seedZoneId);
            // Doorway seeds: 1-cell doorway zone; gate-defender pattern needs
            // both adjacent rooms in the pool.
            if (grid.isDoorway(ax, ay)) {
                result.addAll(zones.adjacentZones(seedZoneId));
            }
            return result;
        }

        // Unwalkable seed (wall-mount). Walk outward through walls until
        // every zone reachable within radius is collected. The radius bound
        // keeps this O(radius²) — same envelope as the original
        // pickCellsNear BFS, just collecting zone ids instead of cells.
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[]{ax, ay, 0});
        seen.add(key(ax, ay));
        int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > radius) continue;
            int zid = zones.zoneIdAt(p[0], p[1]);
            if (zid >= 0) result.add(zid);
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return result;
    }

    /**
     * Scatters {@link #AMBIENT_CIVILIAN_COUNT} non-combatants near residential
     * POIs as map flavor — they panic and flee gunfire via
     * {@code FleeBehavior}. Civilians belong to {@link Faction#CIVILIAN}, which
     * means they don't count toward either side's elimination objective and
     * aren't targeted by combatants. If no residential POIs were carved (rare
     * — small or unusually industrial seeds), this is a no-op.
     */
    private static void spawnAmbientCivilians(BattleSimulation sim, MapResult map, Random rng) {
        List<PointOfInterest> residential = new ArrayList<>();
        for (PointOfInterest poi : map.pointsOfInterest) {
            if (poi.kind == PointOfInterest.Kind.RESIDENTIAL) residential.add(poi);
        }
        if (residential.isEmpty()) return;
        UnitType[] roles = { UnitType.CIVILIAN, UnitType.ENGINEER, UnitType.SCIENTIST };
        Set<Long> claimed = new HashSet<>();
        int spawned = 0;
        int attempts = 0;
        while (spawned < AMBIENT_CIVILIAN_COUNT && attempts < AMBIENT_CIVILIAN_COUNT * 8) {
            attempts++;
            PointOfInterest poi = residential.get(rng.nextInt(residential.size()));
            int[] cell = findCivilianCell(map.grid, poi.anchorCellX, poi.anchorCellY, claimed, rng);
            if (cell == null) continue;
            UnitType type = roles[rng.nextInt(roles.length)];
            sim.spawn(new EntitySpec("c" + spawned, Faction.CIVILIAN, type, cell[0], cell[1]).role(UnitRole.FLEE));
            claimed.add(key(cell[0], cell[1]));
            spawned++;
        }
    }

    /**
     * Random walkable cell within {@link #CIVILIAN_SPAWN_RADIUS} BFS-steps of
     * (cx, cy) that isn't already claimed by another civilian. Returns null if
     * the area is fully clogged. Picks via reservoir-style random selection
     * rather than first-found so multiple civilians on the same POI don't all
     * cluster on its anchor cell.
     */
    private static int[] findCivilianCell(NavigationGrid grid, int cx, int cy, Set<Long> claimed, Random rng) {
        List<int[]> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy, 0});
        seen.add(key(cx, cy));
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > CIVILIAN_SPAWN_RADIUS) continue;
            if (grid.isWalkable(p[0], p[1]) && !claimed.contains(key(p[0], p[1]))) {
                candidates.add(new int[]{p[0], p[1]});
            }
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return candidates.isEmpty() ? null : candidates.get(rng.nextInt(candidates.size()));
    }

    /**
     * Adds a two-person service crew near each occupied civilian berth, capped
     * at four people. The crew starts on the service side (opposite the clear
     * flight approach), never inside any authored pad, doorway, building, or
     * setup obstacle, and uses ordinary FLEE behavior once combat begins.
     */
    static int spawnSpaceportGroundCrew(BattleSimulation sim, MapResult map,
                                        List<ParkedAircraft> parkedAircraft,
                                        Random rng) {
        if (parkedAircraft == null || parkedAircraft.isEmpty()) return 0;
        Set<Long> claimed = new HashSet<>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            claimed.add(key(sim.world().cellX(unit), sim.world().cellY(unit)));
        }

        int spawned = 0;
        int target = Math.min(4, parkedAircraft.size() * 2);
        for (ParkedAircraft aircraft : parkedAircraft) {
            LandingPad pad = landingPadAt(map.landingPads,
                    aircraft.centerX, aircraft.centerY);
            if (pad == null) continue;
            int serviceX = pad.centerX - pad.approach.dx * 4;
            int serviceY = pad.centerY - pad.approach.dy * 4;
            for (int member = 0; member < 2 && spawned < target; member++) {
                int[] cell = findPortCrewCell(map, serviceX, serviceY, claimed, rng);
                if (cell == null) break;
                UnitType type = member == 0 ? UnitType.ENGINEER : UnitType.CIVILIAN;
                sim.spawn(new EntitySpec("port-crew-" + spawned,
                        Faction.CIVILIAN, type, cell[0], cell[1]).role(UnitRole.FLEE));
                claimed.add(key(cell[0], cell[1]));
                spawned++;
            }
        }
        return spawned;
    }

    private static LandingPad landingPadAt(List<LandingPad> pads, int centerX, int centerY) {
        for (LandingPad pad : pads) {
            if (pad.centerX == centerX && pad.centerY == centerY) return pad;
        }
        return null;
    }

    private static int[] findPortCrewCell(MapResult map, int anchorX, int anchorY,
                                          Set<Long> claimed, Random rng) {
        List<int[]> candidates = new ArrayList<>();
        for (int radius = 0; radius <= 3; radius++) {
            for (int y = anchorY - radius; y <= anchorY + radius; y++) {
                for (int x = anchorX - radius; x <= anchorX + radius; x++) {
                    if (Math.max(Math.abs(x - anchorX), Math.abs(y - anchorY)) != radius) continue;
                    if (!map.grid.inBounds(x, y) || !map.grid.isWalkable(x, y)) continue;
                    if (map.grid.isDoorway(x, y) || claimed.contains(key(x, y))) continue;
                    if (map.topology.getBuildingId(x, y) != 0) continue;
                    boolean insideBerth = false;
                    for (LandingPad pad : map.landingPads) {
                        if (pad.contains(x, y)) {
                            insideBerth = true;
                            break;
                        }
                    }
                    if (insideBerth) continue;
                    boolean onProp = false;
                    for (Doodad doodad : map.doodads) {
                        if (doodad.occupiesCell(x, y)) {
                            onProp = true;
                            break;
                        }
                    }
                    if (!onProp) candidates.add(new int[]{x, y});
                }
            }
            if (!candidates.isEmpty()) break;
        }
        return candidates.isEmpty() ? null : candidates.get(rng.nextInt(candidates.size()));
    }

    /**
     * CONQUEST-specific LZ picker — drops along the beachhead line instead of
     * a tight cluster around the marine anchor. The line runs parallel to the
     * attacker edge at the marine anchor's perpendicular coordinate
     * ({@code anchorY} for SOUTH_TO_NORTH, {@code anchorX} for WEST_TO_EAST),
     * so LZs land inside the beach biome strip. Drops are evenly spaced
     * across the attacker frontage with small per-cell jitter for visual
     * variety; if a target cell is unwalkable (wall, water, dock structure)
     * we slide along the line to the nearest walkable cell. Even spacing
     * implicitly enforces separation — no min-distance gate needed when the
     * map is wide enough.
     */
    private static List<int[]> pickConquestLandingZones(NavigationGrid grid,
                                                        int anchorX, int anchorY,
                                                        int count, TraversalAxis axis,
                                                        Random rng) {
        boolean vertical = (axis == TraversalAxis.WEST_TO_EAST);
        int lineLength = vertical ? grid.getHeight() : grid.getWidth();
        // Leave the corners alone — the very edge of the beach reads as
        // off-map rather than "landing zone". Margin scales with map width.
        int margin = Math.max(6, lineLength / 12);
        int span = Math.max(1, lineLength - 2 * margin);

        List<int[]> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            float t = (count == 1) ? 0.5f : (float) i / (count - 1);
            int along = margin + Math.round(t * span);
            // ±3 cells of jitter so a 3-drop mission doesn't land on the
            // identical t=0/0.5/1 slots every seed. Keeps drops feeling
            // hand-placed rather than mathematically pinned.
            along += rng.nextInt(7) - 3;
            along = Math.max(margin, Math.min(margin + span, along));
            int x = vertical ? anchorX : along;
            int y = vertical ? along : anchorY;
            int[] cell = slideLzAlongLine(grid, x, y, vertical);
            if (cell == null) continue;
            if (!seen.add(key(cell[0], cell[1]))) continue;
            picked.add(cell);
        }
        // Tight maps / line of all-unwalkable can leave us short. Better
        // a stacked LZ on the anchor than zero shuttles.
        while (picked.size() < count) picked.add(new int[]{anchorX, anchorY});
        return picked;
    }

    /**
     * Find the nearest walkable cell on the LZ line, sliding outward from the
     * target along the parallel axis. Returns null only if the entire line is
     * unwalkable, which would be a degenerate map.
     */
    private static int[] slideLzAlongLine(NavigationGrid grid, int x, int y, boolean vertical) {
        if (grid.inBounds(x, y) && grid.isWalkable(x, y)) return new int[]{x, y};
        int max = vertical ? grid.getHeight() : grid.getWidth();
        for (int d = 1; d < max; d++) {
            if (vertical) {
                if (grid.inBounds(x, y - d) && grid.isWalkable(x, y - d)) return new int[]{x, y - d};
                if (grid.inBounds(x, y + d) && grid.isWalkable(x, y + d)) return new int[]{x, y + d};
            } else {
                if (grid.inBounds(x - d, y) && grid.isWalkable(x - d, y)) return new int[]{x - d, y};
                if (grid.inBounds(x + d, y) && grid.isWalkable(x + d, y)) return new int[]{x + d, y};
            }
        }
        return null;
    }

    /**
     * Scans walkable cells within {@link #DEFENDER_SPAWN_SCAN_RADIUS} of the
     * anchor, then sorts the pool by cover descending (proximity to anchor
     * breaks ties). Keeps the top {@code count}. Defenders end up tucked into
     * wall edges and building corners — "they prepared the position" emerges
     * from picking which cells they camp, not from stat asymmetry.
     */
    private static List<int[]> pickDefensiveCluster(NavigationGrid grid, int cx, int cy, int count) {
        List<int[]> pool = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy, 0});
        seen.add(key(cx, cy));
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > DEFENDER_SPAWN_SCAN_RADIUS) continue;
            if (grid.isWalkable(p[0], p[1])) pool.add(p);
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        pool.sort(Comparator
                .comparingInt((int[] p) -> -grid.getCoverAt(p[0], p[1])) // higher cover first
                .thenComparingInt(p -> p[2])); // closer to anchor first on cover ties
        List<int[]> picked = new ArrayList<>(count);
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            int[] p = pool.get(i);
            picked.add(new int[]{p[0], p[1]});
        }
        // Backfill if the scan didn't return enough — fall back to plain BFS from
        // the anchor so we never spawn fewer defenders than requested.
        if (picked.size() < count) {
            picked.addAll(pickSpawnCluster(grid, cx, cy, count - picked.size()));
        }
        return picked;
    }

    /** BFS from (cx, cy) over walkable cells, returning the first {@code count} cells in BFS order. */
    private static List<int[]> pickSpawnCluster(NavigationGrid grid, int cx, int cy, int count) {
        List<int[]> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy});
        seen.add(key(cx, cy));
        while (!q.isEmpty() && picked.size() < count) {
            int[] p = q.poll();
            if (!grid.isWalkable(p[0], p[1])) continue;
            picked.add(p);
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                if (!seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny});
            }
        }
        return picked;
    }

    /**
     * Occupies up to two surplus civilian-port berths with small local craft.
     * Mission-selected berths are reserved first, and the craft blocks only
     * the inner 3x3 so the pad's outer safety ring remains traversable.
     */
    static List<ParkedAircraft> stampParkedAircraft(MapResult map,
                                                    List<LandingPad> reserved,
                                                    Random rng) {
        Set<LandingPad> reservedSet = new HashSet<>(reserved);
        List<LandingPad> candidates = new ArrayList<>();
        for (LandingPad pad : map.landingPads) {
            if (pad.purpose != LandingPad.Purpose.CIVILIAN_SPACEPORT) continue;
            if (reservedSet.contains(pad) || !pad.isClear(map.grid, map.topology)) continue;
            candidates.add(pad);
        }
        candidates.sort(Comparator
                .comparingInt((LandingPad pad) -> -distanceSq(
                        pad.centerX, pad.centerY, map.marineSpawnX, map.marineSpawnY))
                .thenComparingInt(pad -> pad.centerY)
                .thenComparingInt(pad -> pad.centerX));

        ShuttleType[] civilianTypes = {
                ShuttleType.AEROSHUTTLE,
                ShuttleType.HERMES,
                ShuttleType.MUDSKIPPER
        };
        int target = Math.min(2, candidates.size());
        List<ParkedAircraft> parked = new ArrayList<>(target);
        for (int i = 0; i < target; i++) {
            LandingPad pad = candidates.get(i);
            ShuttleType type = civilianTypes[rng.nextInt(civilianTypes.length)];
            float facing = AirBody.facingToward(pad.approach.dx, pad.approach.dy);
            stampParkedAircraftFootprint(map.grid, map.topology, pad.centerX, pad.centerY);
            parked.add(new ParkedAircraft(type, pad.centerX, pad.centerY, facing));
        }
        return parked;
    }

    private static int distanceSq(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }

    private static void stampParkedAircraftFootprint(NavigationGrid grid,
                                                     CellTopology topology,
                                                     int centerX, int centerY) {
        int half = ParkedAircraft.FOOTPRINT_HALF;
        for (int y = centerY - half; y <= centerY + half; y++) {
            for (int x = centerX - half; x <= centerX + half; x++) {
                grid.setWalkable(x, y, false);
                topology.setVehicle(x, y, true);
            }
        }
        for (int y = centerY - half - 1; y <= centerY + half + 1; y++) {
            for (int x = centerX - half - 1; x <= centerX + half + 1; x++) {
                grid.recomputeCoverAt(x, y);
            }
        }
    }

    /**
     * Parks 3-6 vehicles on open outdoor pavement (streets or super-block
     * courtyards) as ordinary registry doodads, drawn from the
     * {@code PARKED_VEHICLES} pool.
     *
     * <p>Vehicle anchors are required to sit on a street or courtyard cell,
     * never on an indoor floor - a truck parked in a living room would read
     * wrong. {@link PlacementGuards#touchesDoorway Doorway-adjacent} cells are
     * excluded so the vehicle doesn't seal a building's only egress (the
     * doorway's perpendicular through-cell is walkable and unflagged, but
     * blocking it traps the interior), and
     * {@link PlacementGuards#wouldPartitionWalkable connectivity} is checked
     * so the truck can't sever a thin walkable strip from the main graph.
     */
    static List<Doodad> stampVehicles(MapResult map, Random rng) {
        NavigationGrid grid = map.grid;
        CellTopology topology = map.topology;
        GenMappingRegistry mappings = GenMappingRegistry.installed();
        List<DoodadDef> kinds = mappings.doodadPool(PARKED_VEHICLE_POOL);
        List<DoodadDef> serviceKinds = mappings.doodadPool(SERVICE_VEHICLE_POOL);
        if (kinds.isEmpty()) return List.of();
        int target = VEHICLE_COUNT_MIN + rng.nextInt(VEHICLE_COUNT_MAX - VEHICLE_COUNT_MIN + 1);
        List<Doodad> placed = new ArrayList<>(target);

        // An operating civilian port parks purpose-appropriate ground traffic
        // on the service side of some berths. Keep other berths empty so the
        // apron doesn't become a solid vehicle lot.
        List<LandingPad> portPads = new ArrayList<>();
        for (LandingPad pad : map.landingPads) {
            if (pad.purpose == LandingPad.Purpose.CIVILIAN_SPACEPORT) portPads.add(pad);
        }
        int serviceTarget = serviceKinds.isEmpty()
                ? 0 : Math.min(target, Math.min(3, (portPads.size() + 1) / 2));
        for (int i = 0; i < serviceTarget; i++) {
            LandingPad pad = portPads.get(i * 2);
            DoodadDef kind = serviceKinds.get(rng.nextInt(serviceKinds.size()));
            int[] anchor = findServiceVehicleAnchor(map, pad, kind);
            if (anchor == null) continue;
            placed.add(stampOneVehicle(grid, topology, anchor[0], anchor[1], kind));
        }

        int attempts = 0;
        int maxAttempts = target * 50;
        while (placed.size() < target && attempts < maxAttempts) {
            attempts++;
            DoodadDef kind = kinds.get(rng.nextInt(kinds.size()));
            int x = rng.nextInt(Math.max(1, grid.getWidth()  - kind.footprintCellsX));
            int y = rng.nextInt(Math.max(1, grid.getHeight() - kind.footprintCellsY));
            if (!canPlaceVehicle(map, x, y, kind, false)) continue;
            if (PlacementGuards.wouldPartitionWalkable(
                    grid, x, y, kind.footprintCellsX, kind.footprintCellsY)) continue;
            placed.add(stampOneVehicle(grid, topology, x, y, kind));
        }
        return placed;
    }

    private static int[] findServiceVehicleAnchor(MapResult map, LandingPad pad,
                                                   DoodadDef kind) {
        int x;
        int y;
        if (pad.approach.dx != 0) {
            x = pad.approach == LandingPad.Approach.EAST
                    ? pad.left() - kind.footprintCellsX - 1
                    : pad.right() + 2;
            y = pad.centerY - kind.footprintCellsY / 2;
        } else {
            x = pad.centerX - kind.footprintCellsX / 2;
            y = pad.approach == LandingPad.Approach.NORTH
                    ? pad.bottom() - kind.footprintCellsY - 1
                    : pad.top() + 2;
        }

        int step = pad.approach.dx != 0 ? kind.footprintCellsY : kind.footprintCellsX;
        int[] offsets = {0, step, -step, step * 2, -step * 2};
        for (int offset : offsets) {
            int candidateX = pad.approach.dx != 0 ? x : x + offset;
            int candidateY = pad.approach.dx != 0 ? y + offset : y;
            if (!canPlaceVehicle(map, candidateX, candidateY, kind, true)) continue;
            if (PlacementGuards.wouldPartitionWalkable(map.grid, candidateX, candidateY,
                    kind.footprintCellsX, kind.footprintCellsY)) continue;
            return new int[]{candidateX, candidateY};
        }
        return null;
    }

    private static boolean canPlaceVehicle(MapResult map, int x, int y,
                                           DoodadDef kind, boolean allowApron) {
        NavigationGrid grid = map.grid;
        CellTopology topology = map.topology;
        for (int dy = 0; dy < kind.footprintCellsY; dy++) {
            for (int dx = 0; dx < kind.footprintCellsX; dx++) {
                int cx = x + dx;
                int cy = y + dy;
                if (!grid.inBounds(cx, cy)) return false;
                if (!grid.isWalkable(cx, cy)) return false;
                if (PlacementGuards.touchesDoorway(grid, cx, cy)) return false;
                boolean ordinaryPavement = topology.isStreet(cx, cy) || topology.isCourtyard(cx, cy);
                boolean portApron = allowApron
                        && topology.getGroundKind(cx, cy) == CellTopology.GroundKind.STRIPED;
                if (!ordinaryPavement && !portApron) return false;
                for (LandingPad pad : map.landingPads) {
                    if (pad.contains(cx, cy)) return false;
                }
                if (allowApron) {
                    for (Doodad doodad : map.doodads) {
                        if (doodad.occupiesCell(cx, cy)) return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Closes one parked vehicle's footprint and returns the prop that sits on it.
     *
     * <p>A truck is not a wall. Each cell becomes non-walkable, see-through, and
     * edge-cover-suppressed, and the three together are what makes it read as a
     * truck: you cannot walk it, you shoot over the hood, and the cover it gives
     * is the authored level its {@link DoodadDef} carries rather than the flat
     * level the grid derives from any blocker. Suppression is what keeps that
     * single - without it one silhouette would publish wall cover and doodad
     * cover at the same time.
     */
    private static Doodad stampOneVehicle(NavigationGrid grid, CellTopology topology,
                                          int x, int y, DoodadDef kind) {
        for (int dy = 0; dy < kind.footprintCellsY; dy++) {
            for (int dx = 0; dx < kind.footprintCellsX; dx++) {
                grid.setWalkable(x + dx, y + dy, false);
                grid.setSeeThrough(x + dx, y + dy, true);
                grid.setEdgeCoverSuppressed(x + dx, y + dy, true);
                topology.setVehicle(x + dx, y + dy, true);
            }
        }
        return new Doodad(x, y, kind);
    }

    /**
     * Defense-post turret spawner. Each {@link DefensePost} carries 1-3 turret
     * specs (LIGHT/MEDIUM = 1, LARGE = 2) at cells already stamped by
     * {@link com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper}
     * as non-walkable STONE pads with manually baked directional cover. The live
     * turret body owns projectile interception, so its mount cell is see-through:
     * otherwise the structural-wall ray stops at the cell boundary before it can
     * reach the turret's inset collision radius. Non-walkability still keeps
     * marines from pathing through the emplacement and still contributes edge
     * cover to adjacent cells. {@code TurretDemolitionSystem} flips the cell back
     * to walkable + rubble on death, so destroyed turrets open up traversal again.
     *
     * <p>Cover is recomputed on the cardinal neighbors so adjacent walkable
     * cells (corner cells around the ring, the middle pad on a LARGE post)
     * pick up the +1 facing cover from the turret now reading as a wall in
     * that direction. The cell itself isn't recomputed — non-walkable cells
     * don't carry valid cover values; the demolition path re-bakes on death.
     */
    /**
     * Public so the debug combat-bridge creation plugin spawns
     * the planet's defenses through the <em>same</em> path the standalone battle uses —
     * no reimplementation to drift from this one (its earlier copy omitted the cover
     * recompute). Returns the spawned structure units (turrets + drone hubs) in spawn
     * order, for callers that need to reference them (the bridge mirrors them as proxies).
     */
    public static LongList spawnDefensePostTurrets(BattleSimulation sim, List<DefensePost> posts) {
        LongArrayList spawned = new LongArrayList();
        int i = 0;
        int h = 0;
        for (DefensePost post : posts) {
            for (DefensePost.TurretSpec spec : post.turrets) {
                long turret = sim.spawn(MapTurret.create("t" + i++, Faction.DEFENDER,
                        spec.structureId, spec.cellX, spec.cellY));
                sim.getGrid().setWalkable(spec.cellX, spec.cellY, false);
                sim.getGrid().setSeeThrough(spec.cellX, spec.cellY, true);
                sim.getGrid().recomputeCoverAt(spec.cellX + 1, spec.cellY);
                sim.getGrid().recomputeCoverAt(spec.cellX - 1, spec.cellY);
                sim.getGrid().recomputeCoverAt(spec.cellX, spec.cellY + 1);
                sim.getGrid().recomputeCoverAt(spec.cellX, spec.cellY - 1);
                spawned.add(turret);
            }
            // DRONE_HUB has no turrets — the hub structure occupies the sealed
            // center cell (already flipped non-walkable by the stamper's
            // sealInnerCell call). Spawning the hub here gives it HP
            // and a render target; the drones it'll launch come in a follow-up.
            if (post.droneHubCellX != null && post.droneHubCellY != null) {
                long hub = sim.spawn(DroneHub.create("dh" + h++, Faction.DEFENDER,
                        post.droneHubCellX, post.droneHubCellY));
                spawned.add(hub);
            }
        }
        return spawned;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
