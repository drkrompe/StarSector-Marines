package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.evacuation.LiftHardpoints;
import com.dillon.starsectormarines.battle.command.RescueCommandDisclosure;
import com.dillon.starsectormarines.battle.command.RescueEscortCommand;
import com.dillon.starsectormarines.battle.command.HivePosture;
import com.dillon.starsectormarines.battle.world.gen.HeldPlaces;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;
import com.dillon.starsectormarines.battle.world.gen.DustLedgerCrossing;
import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;
import com.dillon.starsectormarines.battle.world.gen.NorthWeir;
import com.dillon.starsectormarines.battle.command.AssaultStyle;
import com.dillon.starsectormarines.battle.command.WaterUnderSealCommand;
import com.dillon.starsectormarines.battle.command.LimitedWarrantCommand;
import com.dillon.starsectormarines.battle.command.DistrictEscortCommand;
import com.dillon.starsectormarines.battle.story.DistrictEscortProgress;
import com.dillon.starsectormarines.battle.world.gen.LowQuay;
import com.dillon.starsectormarines.battle.world.gen.LatchkeyYard;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import com.dillon.starsectormarines.battle.command.SmallWarCommand;
import com.dillon.starsectormarines.battle.story.SmallWarProgress;
import com.dillon.starsectormarines.battle.command.YardOwnersCommand;
import com.dillon.starsectormarines.battle.command.TermsOfProtectionCommand;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.battle.command.SalvageRightsCommand;
import com.dillon.starsectormarines.battle.command.ColdStartCommand;
import com.dillon.starsectormarines.battle.command.WorkingPassageCommand;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.power.MechDropService;
import com.dillon.starsectormarines.battle.power.YardConstruction;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;

import com.dillon.starsectormarines.battle.world.gen.fortress.MaintenanceOutpost;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundMissionMap;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;

import com.dillon.starsectormarines.battle.command.ThunderRunCommand;
import com.dillon.starsectormarines.battle.command.ThunderRunCommandFrame;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.battle.world.gen.LastLiftCompound;
import com.dillon.starsectormarines.battle.evacuation.LastLiftAttempt;
import com.dillon.starsectormarines.campaign.LastLiftProgress;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.drone.DroneHub;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.evacuation.ColonyBreachObjectives;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.evacuation.ColonyLivingField;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.evacuation.ColonyPopulation;
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
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.world.gen.UrbanMapGenerator;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.staffed.StaffedBuildings;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.ambient.BuildingWork;
import com.dillon.starsectormarines.battle.ambient.RoomSite;
import com.dillon.starsectormarines.battle.ambient.WorksCrewService;
import com.dillon.starsectormarines.battle.fabrication.FabricationService;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;

import com.dillon.starsectormarines.battle.air.AirArmament;
import com.dillon.starsectormarines.battle.air.AirCorridor;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.AirfieldWork;
import com.dillon.starsectormarines.battle.air.MountedTurret;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.GroundMarineArrival;
import com.dillon.starsectormarines.battle.air.ShuttleState;
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
import com.dillon.starsectormarines.battle.command.ConquestLaneChain;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.ConquestCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ConquestDefenderCommand;
import com.dillon.starsectormarines.battle.command.ConquestDefenderStartingForce;
import com.dillon.starsectormarines.battle.command.ConquestTrackLayout;
import com.dillon.starsectormarines.battle.command.ExtractionCommand;
import com.dillon.starsectormarines.battle.command.ExtractionCommandDisclosure;
import com.dillon.starsectormarines.battle.command.ExtractionDefenderCommand;
import com.dillon.starsectormarines.battle.command.ExtractionDefenderCommandDisclosure;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.command.ReliefAssault;
import com.dillon.starsectormarines.battle.world.gen.ReliefCheckpoint;
import com.dillon.starsectormarines.battle.world.gen.CounterattackDepot;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import com.dillon.starsectormarines.battle.world.gen.RelayNineCompound;
import com.dillon.starsectormarines.battle.command.JunctionCommand;
import com.dillon.starsectormarines.battle.command.RelayNineCommand;
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
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
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
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.MapGenerator;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.SettlementZoning;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper;
import com.dillon.starsectormarines.battle.world.gen.precinct.LanePath;
import com.dillon.starsectormarines.battle.world.gen.precinct.MapPlacement;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBrief;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctCharacter;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.precinct.LandingKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
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
import com.dillon.starsectormarines.battle.shield.ShieldGenerator;
import com.dillon.starsectormarines.battle.shield.ShieldGeneratorPlacement;
import com.dillon.starsectormarines.battle.fort.FortAspectCore;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.ops.ConquestArrivalConfig;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.MarineArrivalPolicy;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.battle.salvage.SalvagePlacement;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;

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
    /** Canonical Conquest battlefield. Conquest does not scale with operation tier — the size is the mission's own, and {@link MapScale#CONQUEST} says why. */
    public static final int CONQUEST_GRID_W = MapScale.CONQUEST.width;
    public static final int CONQUEST_GRID_H = MapScale.CONQUEST.height;

    /** Three drops × 4 marines/shuttle keeps total marine count at 12 — matches pre-shuttle balance. */
    private static final int SHUTTLE_COUNT = 3;
    /** Sim-seconds between successive shuttle launches. Spaces out drops so the LZs aren't all active at once. */
    private static final float SHUTTLE_DROP_STAGGER_SEC = 1.5f;
    /** Minimum cell-distance between landing zones — avoids stacking all shuttles on the spawn anchor. */
    static final int LZ_MIN_SEPARATION = 8;
    /** Entry/exit Y offset above the grid (in cells). Long enough that shuttles are visible during their descent. */
    private static final float SHUTTLE_OFFMAP_Y = 8f;

    /** BFS radius around the defender anchor we scan for candidate spawn cells. Larger than the largest expected defender count so we have a pool to cherry-pick high-cover cells from. */
    private static final int DEFENDER_SPAWN_SCAN_RADIUS = 14;
    /** BFS radius around a tactical-node anchor for picking garrison spawn cells. Tight — defenders should appear inside or right next to their post. */
    private static final int GARRISON_SPAWN_RADIUS = 5;

    /** Relief opens with two local posts and two raider fireteams, followed by two six-person road groups. */
    private static final int OPENING_LOCAL_MILITIA = 8;
    private static final int OPENING_RAIDERS = 8;
    private static final int RELIEF_ROAD_RESERVE = 6;
    private static final int OPENING_FIRETEAM_SIZE = 4;
    /** Give the local line time to make contact before transport descent. */
    private static final float RELIEF_LIFT_DELAY_SEC = 6f;

    /** Keeps the allied garrison's kit rolls off the setup stream the defenders share. */
    private static final long ALLIED_GARRISON_SEED_SALT = 0x414C4C59474152L;

    /** Total ambient civilians (mix of CIVILIAN/ENGINEER/SCIENTIST) scattered around residential POIs as map flavor. */
    private static final int AMBIENT_CIVILIAN_COUNT = 8;
    /** Civilian warehouses a procedural operation map puts a crew to work in ({@code staffed-buildings.md}). */
    private static final int STAFFED_WAREHOUSES = 2;
    /** Civilian factories it works. */
    private static final int STAFFED_FACTORIES = 1;
    /** Civilian shops it keeps open. */
    private static final int STAFFED_GROCERIES = 2;
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
     * Result of {@link #buildMap}: the constructed sim, the map it was built
     * from, and the structure units spawned for its defense posts (turrets +
     * drone hubs, in spawn order). Standalone factories ignore
     * {@code structures}; the combat-bridge host mirrors them as targetable
     * proxies.
     *
     * <p>The {@link MapResult} rides along because it is the only statement of
     * what the generator actually produced — the front depth, the arrival areas
     * and the landing pads are read off it and are not re-derivable from the
     * finished sim.
     */
    public record MapBuild(BattleSimulation sim, MapResult map, LongList structures) {}

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
    /** The surface palette a target profile stands for; null (bare rock) when the battle has no planet behind it. */
    static SurfacePalette surfaceOf(TargetProfile profile) {
        return profile == null ? null : profile.surface();
    }

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
        sim.setMarineLandingPads(map.landingPads);
        sim.setDefensePosts(defensePosts);
        // The map's own statement of where the front is, so the trace can say
        // which band a compound stands in without re-deriving it.
        sim.setFrontDepth(map.frontDepth);
        for (ParkedAircraft aircraft : parkedAircraft) sim.addParkedAircraft(aircraft);
        for (Doodad d : map.doodads) sim.addDoodad(d);
        for (Doodad d : parkedVehicles) sim.addDoodad(d);
        sim.applyInitialDamage(map.initialDamage);
        LongList structures = spawnDefensePostTurrets(sim, defensePosts);
        // The mesh is constructed with the simulation, before turret mounts
        // are made transparent for projectile interception. Consume those
        // setup-time grid writes here so the first live breach does not also
        // re-cover every tile that happened to contain a defense post.
        if (sim.getNavigationMesh().pendingGridChanges() > 0L) {
            sim.getNavigationMesh().rebuild();
        }
        return new MapBuild(sim, map, structures);
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
    public static List<ShuttleAssignment> defaultManifest() {
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
        return createSabotage(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport, null);
    }

    /** Explicit sprawl opts into an installation map; absent sprawl preserves old fixtures. */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport,
                                                  PrecinctPlan.Sprawl sprawl) {
        return createSabotage(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport, sprawl, true);
    }

    /** Authored reinforcement eligibility, independent of the map's sprawl. */
    public static BattleSimulation createSabotage(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport,
                                                  PrecinctPlan.Sprawl sprawl,
                                                  boolean defenderReinforcements) {
        GroundRosterProfile groundRoster = GroundRosterRegistry.resolve(
                profile != null ? profile.factionId() : "");
        MapScale scale = MapScale.forTier(tier);
        PrecinctPlan precinct = sprawl == null ? null : precinctPlanFor(
                MissionType.SABOTAGE, tier, risk, profile, scale, seed, sprawl);
        MapResult map = MAP_GEN.generate(scale.width, scale.height, seed, null, profile, precinct);
        publishHeldPlaces(map, Faction.DEFENDER);
        Random rng = new Random(seed);
        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        // Vehicles stamp before sim construction so the BattleSimulation's
        // zone-graph rebuild sees the final walkability — trucks partition zones.
        List<Doodad> vehiclePlacements = stampVehicles(map, rng);
        // Defense posts stamp before sim construction for the same reason —
        // the embankment ring cells flip walkability, and the zone graph the
        // sim builds on construction needs to reflect that.
        List<DefensePost> defensePosts = new ArrayList<>(map.defensePosts);
        // A fortified place stated its own emplacements, so the setup-time
        // scatter is skipped: a second random layer on top of an authored
        // fortification was never a decision anybody made.
        if (defensePosts.isEmpty()) {
            DefensePostStamper.stampNonConquest(map.grid, map.topology,
                    RoadReservation.mask(map.roadGraph, map.grid.getWidth(), map.grid.getHeight()),
                    map.pointsOfInterest, map.doodads, defensePosts, rng);
        }
        DefenderForcePlan defenders = defenderForcePlan(
                MissionType.SABOTAGE, tier, risk, enemyHasHeavyArmor,
                assignments, defensePosts, marineFighterSupport,
                enemyFighterSupport, groundRoster, garrisonOf(profile));
        List<LandingPad> lzCells = LandingPadSelector.select(
                map, assignments.size(), LZ_MIN_SEPARATION);
        SabotageSiteLayout siteLayout = SabotageSiteLayout.select(
                map, lzCells, LZ_MIN_SEPARATION);
        List<ParkedAircraft> parkedAircraft = stampParkedAircraft(map, lzCells, rng);
        BattleSimulation sim = buildMap(
                map, vehiclePlacements, defenders.defensePosts(), parkedAircraft, seed).sim();
        sim.setSurface(surfaceOf(profile));
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
        recordLzPads(sim, lzCells);
        int globalDropIdx = 0;
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            LandingPad lz = lzCells.get(i);
            float lzCenterX = lz.centerX + 0.5f;
            float lzCenterY = lz.centerY + 0.5f;
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY, scale.width, scale.height, lz.approach);
            long shuttleId = sim.spawnShuttle(
                    a.type, a.airframe, Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entry[0], entry[1],
                    entry[2], entry[3],
                    i * SHUTTLE_DROP_STAGGER_SEC,
                    a.seatsPerSortie);
            ShuttleMission mission = sim.world().mission(shuttleId);
            mission.totalCycles = a.cycles;
            MarineLoadout[][] cycleLoadouts = new MarineLoadout[a.cycles][];
            for (int c = 0; c < a.cycles; c++) {
                cycleLoadouts[c] = buildSabotageLoadout(
                        a.seatsForCycle(c), objectives, globalDropIdx, rng);
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
        if (defenderReinforcements) {
            installReinforcementLayer(sim, map, MissionType.SABOTAGE, null,
                    groundRoster, risk, null);
        }
        return sim;
    }

    /** Falls back to the default manifest when callers pass null or an empty list. Keeps test entry points working without a manifest. */
    private static List<ShuttleAssignment> resolveManifest(List<ShuttleAssignment> manifest) {
        if (manifest == null || manifest.isEmpty()) return defaultManifest();
        return manifest;
    }

    /**
     * Tells the simulation which berths the marines land on. Every other trace
     * of the map's geometry is dropped once setup finishes, so an offline
     * review would otherwise have to re-derive the landing ground from the
     * generator. The landing zone marks nothing on the ground: the shuttles
     * set down on whatever the map has there.
     */
    static void recordLzPads(BattleSimulation sim, List<LandingPad> lzCells) {
        sim.setMarineLandingPads(lzCells);
    }

    /**
     * The company's boats, one per pad, each flying its manifest line's
     * sorties onto its pad from the pad's clear side; the squads they land are
     * claimed by {@code claim}. An empty manifest is the default lift.
     */
    static void landCompany(BattleSimulation sim, List<LandingPad> pads, List<ShuttleAssignment> manifest,
                            Random rng, SquadCommandClaim claim) {
        landCompany(sim, pads, manifest, rng, claim, InfantryLoadoutRolls::playerSquad);
    }

    /** As {@link #landCompany(BattleSimulation, List, List, Random, SquadCommandClaim)}, each sortie's seats rolled by {@code squad}. */
    static void landCompany(BattleSimulation sim, List<LandingPad> pads, List<ShuttleAssignment> manifest,
                            Random rng, SquadCommandClaim claim, BiFunction<Integer, Random, MarineLoadout[]> squad) {
        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
        for (int i = 0; i < pads.size(); i++) {
            ShuttleAssignment assignment = assignments.get(i % assignments.size());
            LandingPad pad = pads.get(i);
            float x = pad.centerX + 0.5f, y = pad.centerY + 0.5f;
            float[] entry = shuttleEntryFor(x, y, width, height, pad.approach);
            long shuttle = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE, x, y,
                    entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            ShuttleMission mission = sim.world().mission(shuttle);
            mission.totalCycles = assignment.cycles;
            mission.commandClaim = claim;
            MarineLoadout[][] cycles = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++) {
                cycles[cycle] = squad.apply(assignment.seatsForCycle(cycle), rng);
            }
            mission.cycleLoadouts = cycles;
            mission.marineLoadout = cycles[0];
            equipDefaultTurrets(sim, shuttle);
        }
    }

    /** Dedicated Raid factory; target seizure and egress replace elimination. */
    public static BattleSimulation createRaid(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile profile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport) {
        return createRaid(seed, manifest, enemyHasHeavyArmor, tier, risk, profile,
                marineFighterSupport, enemyFighterSupport, null);
    }

    /** Raid against a map whose settled share the mission stated; null derives it. */
    public static BattleSimulation createRaid(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile profile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport, PrecinctPlan.Sprawl sprawl) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk,
                MissionType.RAID, profile, marineFighterSupport,
                enemyFighterSupport, sprawl);
    }

    /** Authored Raid target meaning, retained alongside the generated service geometry. */
    public static BattleSimulation createRaid(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile profile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport, PrecinctPlan.Sprawl sprawl, String targetName,
            PointOfInterest.Kind targetKind) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk,
                MissionType.RAID, profile, marineFighterSupport, enemyFighterSupport,
                sprawl, null, 1f, false, targetName, targetKind);
    }

    /** Dedicated generic Extraction factory; payload escort replaces elimination. */
    public static BattleSimulation createExtraction(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile profile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport) {
        return createExtraction(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport, null);
    }

    /** Extraction against a map whose settled share the mission stated; null derives it. */
    public static BattleSimulation createExtraction(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk,
            TargetProfile profile, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport, PrecinctPlan.Sprawl sprawl) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk,
                MissionType.EXTRACTION, profile, marineFighterSupport,
                enemyFighterSupport, sprawl);
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
                    prior.equipmentGrade, prior.soldierProfile, null, 0, prior.campaignSoldierId,
                    prior.armorFamily, prior.armorCapacity, prior.armorRating, prior.armorMoveSpeedMult,
                    prior.armorIncomingAccuracyMult, prior.campaignSquad, prior.integralSystem, prior.armorClass);
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

    /**
     * Mixed into the map seed for the plan's own draws.
     *
     * <p>The plan and the generator would otherwise read the same stream from
     * the same start, so where a place is seeded and how the map is built from
     * it would be correlated for no reason anybody chose. A fixed salt keeps
     * the plan deterministic in the battle's seed while leaving it an
     * independent draw; the value carries no meaning beyond being distinctive.
     */
    private static final long PRECINCT_SEED_SALT = 0x9E3779B97F4A7C15L;

    /**
     * The places this battle's map is made of, or {@code null} to take the map
     * the generator already builds.
     *
     * <p>Assault and Raid derive installation geometry by default. Sabotage
     * opts in only when its construction explicitly states sprawl; old
     * fixtures keep their settlement recipe. Conquest has its own derivation.
     *
     * <p><b>A market is what there is to derive from.</b> The gate mirrors
     * {@code BspCityGenerator.recipeFor}: a battle with no market behind it —
     * a headless fixture, a story op with no target planet — has no size, no
     * defence rating and no economy to seed places from, so it takes the stock
     * map exactly as it does today rather than a plan derived from zeroes.
     *
     * <p><b>{@code BALANCED} for Raid too</b>, not {@code REMOTE}. A raid wants
     * a target, and targets are points of interest, which only settlement fills
     * emit; a remote map is an installation in open country and has none, so
     * {@code RaidTargetLayout} would find no candidate and throw. Making
     * tactical nodes eligible targets is what would unlock a remote raid.
     */
    static PrecinctPlan precinctPlanFor(MissionType type, OperationTier tier, RiskLevel risk,
                                        TargetProfile profile, MapScale scale, long seed) {
        return precinctPlanFor(type, tier, risk, profile, scale, seed, null);
    }

    /**
     * As above, with the battle's own statement of how settled its map is.
     *
     * @param sprawl the mission's stated sprawl, or {@code null} to derive it
     *               from the market the way {@code SettlementZoning} does.
     */
    static PrecinctPlan precinctPlanFor(MissionType type, OperationTier tier, RiskLevel risk,
                                        TargetProfile profile, MapScale scale, long seed,
                                        PrecinctPlan.Sprawl sprawl) {
        if (type != MissionType.ASSAULT && type != MissionType.RAID
                && type != MissionType.SABOTAGE) return null;
        if (type == MissionType.SABOTAGE && sprawl == null) return null;
        if (profile == null || profile.marketSize() <= 0) return null;
        PrecinctPlan.Sprawl resolved = sprawl != null
                ? sprawl : SettlementZoning.sprawlFor(profile.marketSize());
        // A raid's target is a point of interest, and a remote map has none.
        if (type == MissionType.RAID && resolved == PrecinctPlan.Sprawl.REMOTE) {
            resolved = PrecinctPlan.Sprawl.BALANCED;
        }
        // FAR is the beachhead on the map edge, which is the only approach these
        // maps have ever had. Nothing about a search-and-strike mission has been
        // measured against a shorter one, so it keeps what it was measured on.
        return PrecinctPlan.derive(profile, resolved,
                MissionFortification.demand(tier, risk),
                scale.width, scale.height, new Random(seed ^ PRECINCT_SEED_SALT))
                .withStandoff(Standoff.FAR);
    }

    /**
     * The places a Conquest map is made of, or {@code null} to take the stock
     * crossroad.
     *
     * <p>Conquest's own derivation, separate from {@link #precinctPlanFor}
     * because it states two things that mission cannot: the objective is
     * placed, and the sprawl is honoured exactly as it arrives.
     *
     * <p><b>The placement pair follows the axis.</b> Conquest already rolls a
     * {@link TraversalAxis} per seed, and everything that plays on the map is
     * keyed on it — both commanders' three-track layout, the shuttles' entry
     * bearing, and the rear edge the reinforcement layer rallies toward. Until
     * now the map underneath was a biome band painted along that same axis; a
     * grown map has neither bands nor an axis, so the axis is instead spent
     * saying where the two places go. {@code SOUTH_TO_NORTH} puts the objective
     * in the {@link MapPlacement#NORTH} region and the attacker in the south,
     * {@code WEST_TO_EAST} puts them east and west, and the map now agrees with
     * the commanders rather than being read by them.
     *
     * <p><b>The sprawl is honoured as given</b>, with none of the clamping Raid
     * needs. A lone fortress in open country is a perfectly legitimate Conquest
     * — arguably the purest one — because everything the mission requires comes
     * off the garrison itself: the keep, the airfield, the guard posts and the
     * ground to land on are all the installation's, and none of them is a point
     * of interest a settlement fill has to emit. So a {@code REMOTE} world
     * generates as an installation in country and plays.
     *
     * <p>The market gate is {@link #precinctPlanFor}'s, and the defence gate is
     * this method's own: a plan derives a garrison only from a defended world,
     * and a Conquest map with no garrison has no keep to take. Rather than
     * re-rolling eight seeds into an error, an undefended world falls back to
     * the stock recipe, which stamps a fortress ward whatever the profile says.
     */
    static PrecinctPlan conquestPlanFor(OperationTier tier, RiskLevel risk,
                                        TargetProfile profile,
                                        PrecinctPlan.Sprawl sprawl,
                                        TraversalAxis axis, long seed) {
        return conquestPlanFor(tier, risk, profile, sprawl, null,
                PrecinctPlan.Lanes.derived(), axis, seed);
    }

    /** As above, with the battle's own statement of how far out it lands. */
    static PrecinctPlan conquestPlanFor(OperationTier tier, RiskLevel risk,
                                        TargetProfile profile,
                                        PrecinctPlan.Sprawl sprawl, Standoff standoff,
                                        TraversalAxis axis, long seed) {
        return conquestPlanFor(tier, risk, profile, sprawl, standoff,
                PrecinctPlan.Lanes.derived(), axis, seed);
    }

    /**
     * Conquest's default approach: the short one, on the evidence.
     *
     * <p>The near third of a 560x336 map is a long way from a claim in the far
     * third — measured, nothing changed hands for the first four battle-minutes
     * of a matrix fixture, because the marines were walking two thirds of a map
     * twice as long on each axis as the one the balance was judged on. The map
     * stays large and the landing moves in.
     *
     * <p>{@link Standoff#STANDARD} was the first choice, being the walk the
     * 280x168 map had, and the matrix rejected it: reinforced-south took eleven
     * compounds and then lost four back, timing out with seven held and twice
     * the losses, while {@link Standoff#CLOSE} won four thousand ticks earlier
     * than the edge landing with the fewest losses on either side and costs the
     * other fixture the least too. The table is in {@code precincts.md}; change
     * this against fresh matrix evidence, not a feeling about distance.
     */
    static final Standoff CONQUEST_STANDOFF = Standoff.CLOSE;

    /**
     * As above, with the battle's own statements of how far out it lands and
     * how many lanes of resistance lie in front of it.
     *
     * <p><b>Conquest is the only mission that lays lanes</b>, for the same
     * reason it is the only one that states a standoff: its commanders already
     * divide the map into lateral tracks and advance up all of them, and until
     * this the map had nothing standing on any of them — three tracks arriving
     * at one wall with open city behind them. Assault and Raid state none and
     * generate exactly as they did.
     *
     * <p><b>{@code null} lanes means none, and the overloads above state the
     * default instead.</b> The two readings of null cannot both live here: a
     * mission that says nothing wants the default ladder, and a mission that
     * says zero wants the map this feature replaced, which is the control a
     * balance run is read against. Resolving the mission's statement is the
     * caller's job — {@code conquestMap} does it — and this takes the answer
     * literally. It read null as "the default" for a while, which made the
     * stated control unreachable and a control run a re-run.
     *
     * @param standoff the mission's stated standoff, or {@code null} for
     *                 Conquest's own default
     * @param lanes    the lanes this map lays, or {@code null} for none
     */
    static PrecinctPlan conquestPlanFor(OperationTier tier, RiskLevel risk,
                                        TargetProfile profile,
                                        PrecinctPlan.Sprawl sprawl, Standoff standoff,
                                        PrecinctPlan.Lanes lanes,
                                        TraversalAxis axis, long seed) {
        return conquestPlanFor(tier, risk, profile, sprawl, standoff, lanes, null,
                axis, seed);
    }

    /**
     * As above, with the battle's own statement of what it comes down on.
     *
     * <p><b>A Conquest always has a landing place</b>, stated or derived — the
     * beachhead is a precinct on this map, claimed before the town floods, and
     * a Conquest with none would be back to landing in whatever street a scan
     * found first. That is the one way this dial differs from the sprawl, the
     * standoff and the lanes, where {@code null} can honestly mean "none".
     *
     * @param landing what the force comes down on, or {@code null} for the kind
     *                the target world offers
     */
    static PrecinctPlan conquestPlanFor(OperationTier tier, RiskLevel risk,
                                        TargetProfile profile,
                                        PrecinctPlan.Sprawl sprawl, Standoff standoff,
                                        PrecinctPlan.Lanes lanes, LandingKind landing,
                                        TraversalAxis axis, long seed) {
        return conquestPlanFor(tier, risk, profile, sprawl, standoff, lanes, landing,
                LandingKind.AREAS_THE_APRON_SEATS, axis, seed);
    }

    /**
     * As above, told how many arrival areas the lift needs berthing room for.
     *
     * @param arrivalAreas berthing pairs the beachhead's apron has to seat; see
     *                     {@link OrbitalLift}
     */
    static PrecinctPlan conquestPlanFor(OperationTier tier, RiskLevel risk,
                                        TargetProfile profile,
                                        PrecinctPlan.Sprawl sprawl, Standoff standoff,
                                        PrecinctPlan.Lanes lanes, LandingKind landing,
                                        int arrivalAreas,
                                        TraversalAxis axis, long seed) {
        if (profile == null || profile.marketSize() <= 0) return null;
        if (profile.defenseLevel() <= 0) return null;
        PrecinctPlan.Sprawl resolved = sprawl != null
                ? sprawl : SettlementZoning.sprawlFor(profile.marketSize());
        MapPlacement objective = axis == TraversalAxis.SOUTH_TO_NORTH
                ? MapPlacement.NORTH : MapPlacement.EAST;
        MapPlacement attackerFrom = axis == TraversalAxis.SOUTH_TO_NORTH
                ? MapPlacement.SOUTH : MapPlacement.WEST;
        // Resolved here rather than layered on afterwards: the landing place is
        // seeded against the standoff, so the derivation has to be told it.
        Standoff resolvedStandoff = standoff != null ? standoff : CONQUEST_STANDOFF;
        LandingKind resolvedLanding = landing != null
                ? landing : LandingKind.derive(profile, resolved);
        return PrecinctPlan.derive(profile, resolved,
                MissionFortification.demand(tier, risk),
                objective, attackerFrom,
                lanes, resolvedStandoff, resolvedLanding, arrivalAreas,
                MapScale.CONQUEST.width, MapScale.CONQUEST.height,
                new Random(seed ^ PRECINCT_SEED_SALT));
    }

    /** Tier-aware catch-all with both sides' authored fighter commitments. */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor,
                                                     OperationTier tier, RiskLevel risk,
                                                     MissionType type, TargetProfile profile,
                                                     FlybyRoster marineFighterSupport,
                                                     FlybyRoster enemyFighterSupport) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk, type,
                profile, marineFighterSupport, enemyFighterSupport, null);
    }

    /**
     * Tier-aware catch-all with the battle's own statement of how settled its
     * map is.
     *
     * @param sprawl the mission's stated sprawl, or {@code null} to derive it
     *               from the target market.
     */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor,
                                                     OperationTier tier, RiskLevel risk,
                                                     MissionType type, TargetProfile profile,
                                                     FlybyRoster marineFighterSupport,
                                                     FlybyRoster enemyFighterSupport,
                                                     PrecinctPlan.Sprawl sprawl) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk, type,
                profile, marineFighterSupport, enemyFighterSupport, sprawl, null);
    }

    /**
     * Tier-aware catch-all that also names whose garrison fights beside the
     * company.
     *
     * @param alliedGarrisonFactionId faction whose own troops hold the line with
     *     the company — the defended market's owner on a defence, and {@code null}
     *     on every other mission, which installs nothing and costs nothing. The id
     *     selects the ground doctrine those troops wear; how many of them there
     *     are is read off {@code profile} by {@link AlliedGarrisonSize}, so a
     *     defender-faction override cannot move it.
     */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor,
                                                     OperationTier tier, RiskLevel risk,
                                                     MissionType type, TargetProfile profile,
                                                     FlybyRoster marineFighterSupport,
                                                     FlybyRoster enemyFighterSupport,
                                                     PrecinctPlan.Sprawl sprawl,
                                                     String alliedGarrisonFactionId) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk, type,
                profile, marineFighterSupport, enemyFighterSupport, sprawl,
                alliedGarrisonFactionId, 1f);
    }

    /**
     * Tier-aware catch-all that also carries the allied faction's own numbers
     * doctrine.
     *
     * @param alliedGarrisonStrengthMult how much of the market's own strength turns
     *     out, from {@code Mission.alliedGarrisonStrengthMult}; {@code 1} is
     *     doctrine-neutral and is what every mission but the polity's own defence
     *     carries.
     */
    public static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
                                                     boolean enemyHasHeavyArmor,
                                                     OperationTier tier, RiskLevel risk,
                                                     MissionType type, TargetProfile profile,
                                                     FlybyRoster marineFighterSupport,
                                                     FlybyRoster enemyFighterSupport,
                                                     PrecinctPlan.Sprawl sprawl,
                                                     String alliedGarrisonFactionId,
                                                     float alliedGarrisonStrengthMult) {
        return createPlaceholder(seed, manifest, enemyHasHeavyArmor, tier, risk, type, profile,
                marineFighterSupport, enemyFighterSupport, sprawl, alliedGarrisonFactionId,
                alliedGarrisonStrengthMult, false, null, null);
    }

    public static BattleSimulation createColdAccount(long seed, List<ShuttleAssignment> manifest,
            boolean heavy, OperationTier tier, RiskLevel risk, TargetProfile profile,
            FlybyRoster marineAir, FlybyRoster enemyAir, PrecinctPlan.Sprawl sprawl) {
        return createPlaceholder(seed, manifest, heavy, tier, risk, MissionType.ASSAULT, profile,
                marineAir, enemyAir, sprawl, null, 1f, true, null, null);
    }

    /**
     * Publishes every building the map's tactical nodes do not already stand on as a held place for
     * {@code owner}. Held places carry no garrison and are not objectives; see {@code HeldPlaces}.
     */
    private static void publishHeldPlaces(MapResult map, Faction owner) {
        if (map.tacticalMap == null) return;
        for (TacticalNode place : HeldPlaces.fromBuildingsNotIn(map.pointsOfInterest, map.tacticalMap.all(), poi -> owner))
            map.tacticalMap.add(place);
    }

    /** Whole campaign-derived sites are an ordinary remote-assault choice when the tier affords their approach. */
    static boolean usesCompoundTemplate(MissionType type, MapScale scale, PrecinctPlan.Sprawl sprawl) {
        return type == MissionType.ASSAULT && sprawl == PrecinctPlan.Sprawl.REMOTE
                && CompoundMissionMap.fits(scale.width, scale.height);
    }

    static MapResult proceduralOperationMap(MissionType type, OperationTier tier, RiskLevel risk,
            TargetProfile profile, MapScale scale, long seed, PrecinctPlan.Sprawl sprawl, PrecinctPlan precincts) {
        PrecinctPlan.Sprawl resolved = sprawl != null ? sprawl
                : profile != null && profile.marketSize() > 0 ? SettlementZoning.sprawlFor(profile.marketSize()) : null;
        var fortification = MissionFortification.demand(tier, risk).resolve(profile == null ? 0 : profile.defenseLevel());
        if (usesCompoundTemplate(type, scale, resolved) && CompoundMissionMap.supports(profile, fortification)) {
            return CompoundMissionMap.generate(scale.width, scale.height, seed, profile, fortification);
        }
        return MAP_GEN.generate(scale.width, scale.height, seed, null, profile, precincts);
    }

    private static BattleSimulation createPlaceholder(long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, OperationTier tier, RiskLevel risk, MissionType type,
            TargetProfile profile, FlybyRoster marineFighterSupport, FlybyRoster enemyFighterSupport,
            PrecinctPlan.Sprawl sprawl, String alliedGarrisonFactionId,
            float alliedGarrisonStrengthMult, boolean coldAccount, String raidTargetName,
            PointOfInterest.Kind raidTargetKind) {
        GroundRosterProfile groundRoster = GroundRosterRegistry.resolve(
                profile != null ? profile.factionId() : "");
        MapScale scale = MapScale.forTier(tier);
        PrecinctPlan precincts = precinctPlanFor(type, tier, risk, profile, scale, seed, sprawl);
        MapResult map = coldAccount
                ? MaintenanceOutpost.generate(MaintenanceOutpost.WIDTH, MaintenanceOutpost.HEIGHT, seed, profile,
                        precincts.objective().fortification())
                : proceduralOperationMap(type, tier, risk, profile, scale, seed, sprawl, precincts);
        // A raided site is defender-held: the city's garrison nodes keep their buildings and every other
        // building is a place the player may order held once taken.
        if (!coldAccount) publishHeldPlaces(map, Faction.DEFENDER);
        Random rng = new Random(seed);
        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        List<Doodad> vehiclePlacements = stampVehicles(map, rng);
        List<DefensePost> defensePosts = new ArrayList<>(map.defensePosts);
        // A fortified place stated its own emplacements, so the setup-time
        // scatter is skipped: a second random layer on top of an authored
        // fortification was never a decision anybody made.
        if (defensePosts.isEmpty()) {
            DefensePostStamper.stampNonConquest(map.grid, map.topology,
                    RoadReservation.mask(map.roadGraph, map.grid.getWidth(), map.grid.getHeight()),
                    map.pointsOfInterest, map.doodads, defensePosts, rng);
        }
        DefenderForcePlan defenders = defenderForcePlan(
                type, tier, risk, enemyHasHeavyArmor, assignments, defensePosts,
                marineFighterSupport, enemyFighterSupport, groundRoster, garrisonOf(profile));
        List<LandingPad> lzCells = LandingPadSelector.select(
                map, assignments.size(), LZ_MIN_SEPARATION);
        RaidTargetLayout raidLayout = type == MissionType.RAID
                ? RaidTargetLayout.select(map, lzCells, raidTargetKind) : null;
        ExtractionPayloadLayout extractionLayout = type == MissionType.EXTRACTION
                ? ExtractionPayloadLayout.select(map, lzCells) : null;
        List<ParkedAircraft> parkedAircraft = stampParkedAircraft(map, lzCells, rng);
        BattleSimulation sim = buildMap(
                map, vehiclePlacements, defenders.defensePosts(), parkedAircraft, seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.setGroundRoster(groundRoster);
        sim.setFlybyRoster(defenders.enemyFighterSupport());

        if (coldAccount) {
            sim.installColdAccount(ColdAccountLayout.select(map, lzCells));
        } else if (raidLayout != null) {
            int targetZone = sim.getZoneGraph().zoneIdAt(
                    raidLayout.targetCellX(), raidLayout.targetCellY());
            sim.addObjective(new RaidObjective(raidLayout.targetId(),
                    raidTargetName == null ? raidLayout.targetName() : raidTargetName, raidLayout.targetCellX(),
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
        recordLzPads(sim, lzCells);
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            LandingPad lz = lzCells.get(i);
            float lzCenterX = lz.centerX + 0.5f;
            float lzCenterY = lz.centerY + 0.5f;
            // Authored installations may be larger than the tier's nominal map scale.
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY,
                    map.grid.getWidth(), map.grid.getHeight(), lz.approach);
            long shuttleId = sim.spawnShuttle(
                    a.type, a.airframe, Faction.MARINE,
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
                cycleLoadouts[c] = InfantryLoadoutRolls.playerSquad(
                        a.seatsForCycle(c), rng);
            }
            mission.cycleLoadouts = cycleLoadouts;
            mission.marineLoadout = cycleLoadouts[0];
            equipDefaultTurrets(sim, shuttleId);
        }

        // Defenders pre-spawn around tactical-node anchors (garrison squads
        // pegged to the highest-priority posts; leftovers form patrol squads).
        // Legacy maps with no tactical layer fall back to the single-cluster
        // spawn around the defender anchor.
        // A mission whose defender commander is expected to hold a mobile
        // reserve must be handed enough members for more than one squad.
        // Pass 1 garrisons greedily down to this floor, so on a map with
        // plenty of tactical nodes the floor is exactly what the commander
        // ends up owning: one squad's worth of members is one squad, and one
        // squad can only ever be routine coverage — the Assault defender's
        // reserve law deliberately keeps a lone squad guarding an area rather
        // than waiting in readiness. Assault kept the single-squad floor from
        // before Raid and Extraction were written and was never revisited; a
        // fortress map, which emits a tactical node per packed room, then
        // garrisoned everything else and left the commander with nothing to
        // dispatch.
        int assaultMobileMembers = switch (type) {
            case ASSAULT, RAID, EXTRACTION -> Math.min(Math.max(0,
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
        // After the defenders and their claims, so the mobile-squad capture and
        // the setup-garrison claim above see the battle they were written for and
        // an ALLY squad can never fall into either.
        installAlliedGarrison(sim, map, profile, alliedGarrisonFactionId,
                alliedGarrisonStrengthMult, risk, seed);
        spawnAmbientCivilians(sim, map, rng);
        StaffedBuildings.install(sim, map, seed, EnumSet.of(BuildingWork.Kind.WAREHOUSE), STAFFED_WAREHOUSES);
        StaffedBuildings.install(sim, map, seed, EnumSet.of(BuildingWork.Kind.FACTORY), STAFFED_FACTORIES);
        StaffedBuildings.install(sim, map, seed, EnumSet.of(BuildingWork.Kind.GROCERY), STAFFED_GROCERIES);
        spawnSpaceportGroundCrew(sim, map, parkedAircraft, rng);
        if (!coldAccount) installReinforcementLayer(sim, map, type, null, groundRoster, risk,
                null);
        if (type == MissionType.ASSAULT) {
            if (coldAccount) sim.setAutonomousCommander(Faction.MARINE, new RaidCommand(),
                    RaidCommandDisclosure.INSTANCE);
            else sim.setAutonomousCommander(Faction.MARINE, new AssaultCommand(),
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
        return createOpeningOperation(seed, manifest, employerShips, kind, profile, false);
    }

    /** The unique Wayline chapter may include its recoverable walker; repeatables never do. */
    public static BattleSimulation createOpeningOperation(
            long seed, List<ShuttleAssignment> manifest, int employerShips,
            OpeningOperationKind kind, TargetProfile profile, boolean recoverHound) {
        return createOpeningOperation(seed, manifest, employerShips, kind, profile, recoverHound, false);
    }

    /**
     * {@code groundStart} is the story Relief chapter: the company rode up on Wayline's truck as its ore
     * dust's escort, so it starts ashore on the rear apron beside the parked hauler and only the employer's
     * militia lift flies. Committed company boats stay in reserve. The repeatable posting keeps its boats.
     */
    public static BattleSimulation createOpeningOperation(
            long seed, List<ShuttleAssignment> manifest, int employerShips,
            OpeningOperationKind kind, TargetProfile profile, boolean recoverHound, boolean groundStart) {
        if (kind == null) throw new IllegalArgumentException(
                "opening operation kind is required");
        if (groundStart && kind != OpeningOperationKind.RELIEF) throw new IllegalArgumentException(
                "only Relief authors a company ground start");

        TargetProfile effectiveProfile = profile != null ? profile : TargetProfile.NEUTRAL;
        List<ShuttleAssignment> assignments = resolveManifest(manifest);
        ReliefCheckpoint.Layout checkpoint = null;
        CounterattackDepot.Layout depot = null;
        MapResult map;
        PointOfInterest authoredPlace;
        List<LandingPad> lzCells;
        List<int[]> raiderCells;
        List<int[]> localMilitiaCells;
        int[] requestedAnchor;
        TacticalNode reliefPost = null;
        if (kind == OpeningOperationKind.RELIEF) {
            checkpoint = ReliefCheckpoint.generate(ReliefCheckpoint.WIDTH, ReliefCheckpoint.HEIGHT, seed, effectiveProfile);
            map = checkpoint.map();
            authoredPlace = checkpoint.relay();
            lzCells = LandingPadSelector.select(map, assignments.size(), LZ_MIN_SEPARATION);
            requestedAnchor = new int[]{checkpoint.gate().x(), checkpoint.gate().y()};
            localMilitiaCells = new ArrayList<>(pickSpawnCluster(map.grid, checkpoint.gate().x(),
                    checkpoint.gate().y(), OPENING_LOCAL_MILITIA / 2));
            localMilitiaCells.addAll(pickSpawnCluster(map.grid, checkpoint.forwardPost().x(),
                    checkpoint.forwardPost().y(), OPENING_LOCAL_MILITIA / 2));
            raiderCells = pickSpawnCluster(map.grid, checkpoint.openingAttack().x(), checkpoint.openingAttack().y(), OPENING_RAIDERS);
            reliefPost = openingDefenseNode(checkpoint.gate().x(), checkpoint.gate().y(), Faction.ALLY, map.grid);
        } else {
            depot = CounterattackDepot.generate(seed, effectiveProfile);
            map = depot.map();
            authoredPlace = depot.depot();
            lzCells = LandingPadSelector.select(map, assignments.size(), LZ_MIN_SEPARATION);
            localMilitiaCells = List.of();
            requestedAnchor = new int[]{authoredPlace.interiorAnchorX, authoredPlace.interiorAnchorY};
            raiderCells = new ArrayList<>();
            for (var post : depot.stages())
                raiderCells.addAll(pickSpawnCluster(map.grid, post.x(), post.y(), OPENING_FIRETEAM_SIZE));
        }

        Random rng = new Random(seed);
        List<Doodad> vehiclePlacements = List.of();
        validateOpeningOperationLayout(map, authoredPlace, lzCells,
                localMilitiaCells, raiderCells, requestedAnchor);
        List<ParkedAircraft> parkedAircraft = List.of();
        BattleSimulation sim = buildMap(map, vehiclePlacements,
                Collections.emptyList(), parkedAircraft, seed).sim();
        sim.setSurface(surfaceOf(effectiveProfile));

        if (checkpoint != null) {
            // Relief ends when its finite assault is broken. Keep the first exploration find
            // in the defended inner yard, where the company can reach it during that fight.
            sim.setFieldSalvageSites(List.of(new SalvagePlacement.Site(SalvageResource.METALS,
                    new SalvagePlacement.Cell(77, 36), 68, 35, 82, 38)));
        }

        sim.addObjective(new EliminateFactionObjective(
                Faction.MARINE, Faction.DEFENDER));
        sim.addObjective(new EliminateFactionObjective(
                Faction.DEFENDER, Faction.MARINE));

        recordLzPads(sim, lzCells);
        int localTransports = Math.max(0, Math.min(employerShips,
                assignments.size()));
        if (groundStart) {
            ReliefCheckpoint.Cell start = ReliefCheckpoint.COMPANY_START;
            sim.setGroundMarineArrival(new GroundMarineArrival(start.x() + .5f, start.y() + .5f,
                    SquadCommandClaim.mission(OpeningOperationCommand.issuer(Faction.MARINE),
                            "opening-operation landing force")));
            // Wayline's hauler stands where it unloaded the company: a civilian hull, never a target.
            ReliefCheckpoint.Cell truck = ReliefCheckpoint.HAULER;
            float hx = truck.x() + .5f, hy = truck.y() + .5f;
            VehicleMission parked = new VehicleMission(new float[]{hx, hx}, new float[]{hy, hy - 1},
                    new float[]{hx, hx}, new float[]{hy, hy - 1}, 0f, 0);
            parked.state = VehicleState.DEPLOYED;
            sim.convoy().spawn(VehicleType.CARGO_CARRIER, Faction.CIVILIAN, parked);
        }
        for (int i = 0; i < lzCells.size(); i++) {
            ShuttleAssignment assignment = assignments.get(i % assignments.size());
            LandingPad lz = lzCells.get(i);
            float lzCenterX = lz.centerX + 0.5f;
            float lzCenterY = lz.centerY + 0.5f;
            float[] entry = shuttleEntryFor(lzCenterX, lzCenterY,
                    map.grid.getWidth(), map.grid.getHeight(), lz.approach);
            boolean localMilitia = i < localTransports;
            long shuttleId = sim.spawnShuttle(
                    assignment.type, assignment.airframe,
                    localMilitia ? Faction.ALLY : Faction.MARINE,
                    lzCenterX, lzCenterY,
                    entry[0], entry[1], entry[2], entry[3],
                    (kind == OpeningOperationKind.RELIEF ? RELIEF_LIFT_DELAY_SEC : 0f)
                            + i * SHUTTLE_DROP_STAGGER_SEC,
                    assignment.seatsPerSortie);
            ShuttleMission shuttleMission = sim.world().mission(shuttleId);
            if (groundStart && !localMilitia) {
                // The company is already ashore; a committed boat waits in reserve with no inbound recipe.
                shuttleMission.totalCycles = 1;
                shuttleMission.marinesRemaining = 0;
                shuttleMission.marineLoadout = new MarineLoadout[0];
                shuttleMission.cycleLoadouts = new MarineLoadout[][]{shuttleMission.marineLoadout};
                shuttleMission.state = ShuttleState.GONE;
                continue;
            }
            shuttleMission.totalCycles = assignment.cycles;
            if (localMilitia) {
                shuttleMission.commandClaim = SquadCommandClaim.mission(
                        OpeningOperationCommand.issuer(Faction.ALLY), "Wayline reinforcement lift");
            } else {
                shuttleMission.commandClaim = SquadCommandClaim.mission(
                        OpeningOperationCommand.issuer(Faction.MARINE),
                        "opening-operation landing force");
            }
            MarineLoadout[][] cycleLoadouts =
                    new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++) {
                cycleLoadouts[cycle] = localMilitia
                        ? InfantryLoadoutRolls.defenderSquad(
                                assignment.seatsForCycle(cycle), UnitType.MILITIA,
                                RiskLevel.LOW, rng, kind == OpeningOperationKind.RELIEF
                                        ? WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID) : null)
                        : InfantryLoadoutRolls.playerSquad(
                                assignment.seatsForCycle(cycle), rng);
            }
            shuttleMission.cycleLoadouts = cycleLoadouts;
            shuttleMission.marineLoadout = cycleLoadouts[0];
            if (localMilitia) shuttleMission.deboardUnitType = UnitType.MILITIA;
            // Deliberately do not install default shuttle turrets. These jobs
            // are for companies whose lift is transport, not close support.
        }

        if (kind == OpeningOperationKind.RELIEF) {
            spawnOpeningMilitiaSquads(sim, localMilitiaCells.subList(0, OPENING_LOCAL_MILITIA / 2),
                    Faction.ALLY, "local-gate", reliefPost, rng, OPENING_FIRETEAM_SIZE,
                    WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID));
            TacticalNode forwardPost = openingDefenseNode(checkpoint.forwardPost().x(),
                    checkpoint.forwardPost().y(), Faction.ALLY, map.grid);
            spawnOpeningMilitiaSquads(sim, localMilitiaCells.subList(OPENING_LOCAL_MILITIA / 2, OPENING_LOCAL_MILITIA),
                    Faction.ALLY, "local-screen", forwardPost, rng, OPENING_FIRETEAM_SIZE,
                    WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID));
        }
        // Relief meets a Rook-equipped starter company. Keep its entire finite
        // assault at that weapon tier, including road arrivals.
        if (depot == null) spawnOpeningMilitiaSquads(sim, raiderCells, Faction.DEFENDER,
                "raider", null, rng, OPENING_FIRETEAM_SIZE,
                WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID));
        else {
            spawnOpeningMilitiaSquads(sim, raiderCells.subList(0, 8), Faction.DEFENDER, "yard-raider", null, rng);
            spawnOpeningMilitiaSquads(sim, raiderCells.subList(8, 12), Faction.DEFENDER, "depot-raider", null, rng, 2);
        }
        spawnAmbientCivilians(sim, map, rng);
        spawnSpaceportGroundCrew(sim, map, parkedAircraft, rng);

        if (checkpoint != null) {
            sim.setReliefAssault(new ReliefAssault(checkpoint.gate(),
                    openingReserve(map, checkpoint.mainApproach().get(0), "freight-reserve", rng), checkpoint.mainApproach(),
                    openingReserve(map, checkpoint.serviceApproach().get(0), "service-reserve", rng), checkpoint.serviceApproach()));
        }
        int[] commandPlace = requestedAnchor;
        OpeningOperationCommandFacts commandFacts =
                new OpeningOperationCommandFacts(kind,
                        kind == OpeningOperationKind.RELIEF
                                ? "relief-checkpoint" : "bandit-depot",
                        kind == OpeningOperationKind.RELIEF
                                ? "Wayline checkpoint" : "Wayline freight depot",
                        commandPlace[0], commandPlace[1], depot == null ? List.of() : depot.stages());
        OpeningOperationCommandDisclosure disclosure =
                new OpeningOperationCommandDisclosure(commandFacts,
                        () -> sim.getDepotHold() != null && sim.getDepotHold().progress().committed());
        sim.setAutonomousCommander(Faction.MARINE,
                new OpeningOperationCommand(Faction.MARINE), disclosure);
        sim.setAutonomousCommander(Faction.DEFENDER,
                new OpeningOperationCommand(Faction.DEFENDER), disclosure);
        sim.setAutonomousCommander(Faction.ALLY,
                new OpeningOperationCommand(Faction.ALLY), disclosure);
        sim.installOpeningStoryHooks(commandFacts);
        if (recoverHound && depot != null) {
            sim.installWaylineHound(CounterattackDepot.HOUND_X, CounterattackDepot.HOUND_Y);
            List<List<EntitySpec>> reserves = new ArrayList<>();
            for (int wave = 0; wave < 2; wave++) for (int road = 0; road < 2; road++) {
                List<EntitySpec> force = new ArrayList<>();
                int y = road == 0 ? 3 : CounterattackDepot.HEIGHT - 4;
                for (int[] cell : pickSpawnCluster(map.grid, 78, y, 4))
                    force.add(makeOpeningMilitia("depot-road-" + wave + "-" + road + "-" + force.size(),
                            Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL));
                reserves.add(force);
            }
            sim.installDepotHold(authoredPlace, reserves);
        }
        return sim;
    }

    /** Vale's finite night recovery operation, with physical company lift and a vulnerable inspection party. */
    public static BattleSimulation createSalvageRights(long seed, List<ShuttleAssignment> manifest, TargetProfile profile) {
        if(manifest==null || manifest.isEmpty() || manifest.size()>4)
            throw new IllegalArgumentException("Salvage Rights requires one to four company boats");
        MapResult map=ShepherdBasin.generate(seed,profile,ShepherdBasin.Visit.SALVAGE_RIGHTS);
        BattleSimulation sim=buildMap(map,List.of(),List.of(),List.of(),seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER,Faction.MARINE));
        Random rng=new Random(seed);
        var pads=LandingPadSelector.select(map,manifest.size(),LZ_MIN_SEPARATION);
        recordLzPads(sim,pads);
        for(int i=0;i<manifest.size();i++){
            var assignment=manifest.get(i);var pad=pads.get(i);
            float x=pad.centerX+.5f,y=pad.centerY+.5f;
            float[] entry=shuttleEntryFor(x,y,map.grid.getWidth(),map.grid.getHeight(),pad.approach);
            long air=sim.spawnShuttle(assignment.type,assignment.airframe,Faction.MARINE,x,y,
                    entry[0],entry[1],entry[2],entry[3],i*SHUTTLE_DROP_STAGGER_SEC,assignment.seatsPerSortie);
            var mission=sim.world().mission(air);
            mission.totalCycles=assignment.cycles;mission.manifestOrdinal=i;
            mission.commandClaim=SquadCommandClaim.mission(SalvageRightsCommand.issuer(Faction.MARINE),"Company recovery lift");
            mission.cycleLoadouts=new MarineLoadout[assignment.cycles][];
            for(int cycle=0;cycle<assignment.cycles;cycle++)
                mission.cycleLoadouts[cycle]=InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle),rng);
            mission.marineLoadout=mission.cycleLoadouts[0];
        }
        var receiving=map.pointsOfInterest.get(0);var workshop=map.pointsOfInterest.get(1);
        for(var post:List.of(receiving,workshop)) {
            var node=openingDefenseNode(post.interiorAnchorX,post.interiorAnchorY,Faction.DEFENDER,map.grid);
            spawnOpeningMilitiaSquads(sim,pickSpawnCluster(map.grid,post.interiorAnchorX,post.interiorAnchorY,4),
                    Faction.DEFENDER,"scrapper-watch",node,rng);
        }
        var cradle=openingDefenseNode(ShepherdBasin.CRADLE_CUTTERS.x(),ShepherdBasin.CRADLE_CUTTERS.y(),Faction.DEFENDER,map.grid);
        spawnOpeningMilitiaSquads(sim,pickSpawnCluster(map.grid,ShepherdBasin.CRADLE_CUTTERS.x(),ShepherdBasin.CRADLE_CUTTERS.y(),4),Faction.DEFENDER,"scrapper-cutters",cradle,rng);
        spawnOpeningMilitiaSquads(sim,pickSpawnCluster(map.grid,ShepherdBasin.MILITIA.x(),ShepherdBasin.MILITIA.y(),8),Faction.ALLY,"basin-militia",null,rng);
        for(var squad:sim.getSquads())if(squad.faction==Faction.ALLY)
            if(!sim.handoffSquadCommand(squad.id,OpeningOperationCommand.issuer(Faction.ALLY),CommandAuthority.MISSION_COMMAND,
                    SalvageRightsCommand.issuer(Faction.ALLY),null,"Vale's militia waits for company gains"))
                throw new IllegalStateException("Basin militia handoff failed");
        int engineers=sim.mintSquad(Faction.ALLY,UnitType.TECHNICIAN);
        for(int[] cell:pickSpawnCluster(map.grid,ShepherdBasin.REPAIR_PARTY.x(),ShepherdBasin.REPAIR_PARTY.y(),2))
            sim.spawn(new EntitySpec("Cooperative inspection technician",Faction.ALLY,UnitType.TECHNICIAN,cell[0],cell[1]).squad(engineers).role(UnitRole.PATROL));
        sim.getSquad(engineers).originalSize=2;
        sim.claimSquadCommand(engineers,CommandAuthority.MISSION_COMMAND,SalvageRightsCommand.issuer(Faction.ALLY),"Repair party awaits a secured hull");
        List<EntitySpec> reserve=new ArrayList<>();
        for(int[] cell:pickSpawnCluster(map.grid,ShepherdBasin.SOUTH_HAUL_ROAD.x(),ShepherdBasin.SOUTH_HAUL_ROAD.y(),8))
            reserve.add(makeOpeningMilitia("south-haul-scrapper-"+reserve.size(),Faction.DEFENDER,cell[0],cell[1],rng).role(UnitRole.PATROL));
        sim.installSalvageRights(receiving,workshop,engineers,reserve);
        for(Faction faction:List.of(Faction.MARINE,Faction.ALLY,Faction.DEFENDER))
            sim.setAutonomousCommander(faction,new SalvageRightsCommand(faction,receiving,workshop,engineers),
                    SalvageRightsCommand.disclosure(sim.getSalvageRights().progress()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(SalvageRightsCommand.issuer(Faction.MARINE),"Company support joins recovery"));
        return sim;
    }


    /**
     * The Dust Ledger on Saltwell Cross ({@code dust-ledger-crossing.md}): the company lands openly as Wayline's
     * freight drop, Sable Wake's finite crew holds the pump house, and the Raid on the ledger room runs under the
     * quiet phase. No reinforcements, vehicles or aircraft; the cantina drinkers and the Harrow salvage team wait
     * for the alarm.
     */
    /**
     * Sable Wake's rocket truck lies in wait in the east yard, among its cargo and barriers, covering the ledger room's
     * east door: the pump house hides it from the south yard, so a mech walking round to that door meets its rockets
     * at a dozen cells rather than seeing it from beyond their reach. The aim is a cell; the hull takes the nearest its
     * footprint clears. It does not drive: the yard's cover leaves a hull no way out of it.
     */
    private static final int[] DUST_LEDGER_TECHNICAL_AMBUSH = {206, 48};

    private static void spawnDustLedgerTechnical(BattleSimulation sim, DustLedgerSequence ledger) {
        VehicleType type = VehicleType.SRM_TECHNICAL;
        VehicleClearance mask = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(type.visualWidthCells));
        int[] at = nearestClear(mask, DUST_LEDGER_TECHNICAL_AMBUSH);
        long technical = sim.convoy().spawn(type, Faction.DEFENDER, VehicleMission.deployed(at[0] + .5f, at[1] + .5f));
        sim.convoy().body(technical).teleport(at[0] + .5f, at[1] + .5f, 180f);
        ledger.technical(technical);
    }

    /** The cell nearest {@code aim} (ring by ring) where a hull of {@code mask}'s clearance fits. */
    private static int[] nearestClear(VehicleClearance mask, int[] aim) {
        for (int r = 0; r < 12; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++)
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == r && mask.isPassable(aim[0] + dx, aim[1] + dy))
                        return new int[]{aim[0] + dx, aim[1] + dy};
        throw new IllegalStateException("no cell clear for a hull near " + aim[0] + "," + aim[1]);
    }

    /** How far a pillbox crew strays from its slits: they fight from inside. */
    private static final int PILLBOX_PATROL_RADIUS = 1;

    public static BattleSimulation createDustLedger(long seed, List<ShuttleAssignment> manifest, RiskLevel risk,
                                                    TargetProfile profile) {
        if (manifest == null || manifest.isEmpty() || manifest.size() > 4)
            throw new IllegalArgumentException("The Dust Ledger requires one to four company boats");
        DustLedgerCrossing.Layout layout = DustLedgerCrossing.generate(seed, profile);
        MapResult map = layout.map();
        GroundRosterProfile roster = GroundRosterRegistry.resolve("pirates");
        // The gun nests are the map's stamped posts, spawned on Sable Wake's side by the ordinary defence-post path.
        List<DefensePost> nestPosts = layout.nests().stream().map(DustLedgerCrossing.Nest::post).toList();
        MapBuild build = buildMap(map, List.of(), nestPosts, List.of(), seed);
        BattleSimulation sim = build.sim();
        List<Long> nests = new ArrayList<>();
        for (int i = 0; i < build.structures().size(); i++) nests.add(build.structures().getLong(i));
        sim.setSurface(surfaceOf(profile));
        sim.setGroundRoster(roster);
        sim.getBattleResources().disableCompoundProduction();
        Random rng = new Random(seed);
        var pads = LandingPadSelector.select(map, manifest.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        DustLedgerCrossing.Cell target = layout.ledgerTarget(), egress = layout.companyStart();
        RaidObjective raid = new RaidObjective("dust-ledger", Strings.get("corvusDustLedgerTarget"), target.x(), target.y(),
                sim.getZoneGraph().zoneIdAt(target.x(), target.y()), egress.x(), egress.y());
        sim.addObjective(raid);
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        for (int i = 0; i < manifest.size(); i++) {
            var assignment = manifest.get(i); var pad = pads.get(i);
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE, x, y,
                    entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            var mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles; mission.manifestOrdinal = i;
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++)
                mission.cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            mission.marineLoadout = mission.cycleLoadouts[0];
            equipDefaultTurrets(sim, air);
        }
        // A Low-quality crew of plain militia: elite clerks in the pump house's rooms cost a two-squad company all 24
        // in headless playback. The crawler crew is the one mobile squad the Raid defender raises at the alarm; the
        // cantina's drinkers spawn then, if at all.
        // The pillboxes are manned from the posts that stand at their slits: each is its own garrison squad, holding
        // its node and firing from the slits, while the rest of that role's posts stay one squad as before.
        Set<Integer> mobile = new TreeSet<>();
        List<Integer> pillboxSquads = new ArrayList<>();
        Set<DustLedgerCrossing.Cell> slits = new HashSet<>();
        for (DustLedgerCrossing.Pillbox pillbox : layout.pillboxes()) slits.addAll(pillbox.posts());
        List<Map.Entry<DustLedgerCrossing.PostRole, List<DustLedgerCrossing.Cell>>> crews = new ArrayList<>();
        for (var post : layout.posts().entrySet()) {
            if (post.getKey() == DustLedgerCrossing.PostRole.CANTINA) continue;
            List<DustLedgerCrossing.Cell> open = post.getValue().stream().filter(cell -> !slits.contains(cell)).toList();
            if (!open.isEmpty()) crews.add(Map.entry(post.getKey(), open));
        }
        int firstPillboxCrew = crews.size();
        for (DustLedgerCrossing.Pillbox pillbox : layout.pillboxes()) crews.add(Map.entry(pillbox.role(), pillbox.posts()));
        for (int crew = 0; crew < crews.size(); crew++) {
            DustLedgerCrossing.PostRole role = crews.get(crew).getKey();
            boolean crawler = role == DustLedgerCrossing.PostRole.CRAWLER;
            boolean manned = crew >= firstPillboxCrew;
            UnitType type = UnitType.MILITIA;
            int squadId = sim.mintSquad(Faction.DEFENDER, type);
            Squad squad = sim.getSquad(squadId);
            List<DustLedgerCrossing.Cell> cells = crews.get(crew).getValue();
            if (crawler) mobile.add(squadId);
            else {
                DustLedgerCrossing.Cell anchor = cells.get(0);
                squad.assignedNode = openingDefenseNode(anchor.x(), anchor.y(), Faction.DEFENDER, map.grid);
                squad.patrolRadius = manned ? PILLBOX_PATROL_RADIUS : 3;
                // A pillbox crew is a guard post: it holds its slits and waits for the kill zone.
                squad.holdsFireUntilKillZone = manned;
                if (manned) pillboxSquads.add(squadId);
                sim.assignSquadCommand(ObjectiveAssignment.holdNode(squadId, squad.assignedNode),
                        CommandAuthority.GARRISON, "dust-ledger-post", "Sable Wake " + role.name().toLowerCase()
                                + (manned ? " pillbox" : " post"));
            }
            for (int i = 0; i < cells.size(); i++) {
                DustLedgerCrossing.Cell cell = cells.get(i);
                EntitySpec unit = makeDefender("sable-wake-" + role.name().toLowerCase() + "-" + i, type, cell.x(), cell.y(),
                        risk, roster, GroundRosterProfile.ForceTier.BULK, rng, null).role(crawler ? UnitRole.PATROL : UnitRole.GARRISON).squad(squadId);
                if (!crawler) unit.home(cell.x(), cell.y());
                sim.spawn(unit);
            }
            squad.originalSize = cells.size();
        }
        claimMissionMobileSquads(sim, mobile, "raid-defender", "Sable Wake crawler crew");
        List<EntitySpec> drinkers = new ArrayList<>();
        for (DustLedgerCrossing.Cell cell : layout.posts().get(DustLedgerCrossing.PostRole.CANTINA))
            drinkers.add(makeDefender("sable-wake-drinker-" + drinkers.size(), UnitType.MILITIA, cell.x(), cell.y(),
                    risk, roster, GroundRosterProfile.ForceTier.BULK, rng, null).role(UnitRole.PATROL));
        List<EntitySpec> salvageTeam = new ArrayList<>();
        for (DustLedgerCrossing.Cell cell : layout.salvageTeam())
            salvageTeam.add(makeOpeningMilitia("harrow-salvager-" + salvageTeam.size(), Faction.ALLY, cell.x(), cell.y(), rng)
                    .role(UnitRole.PATROL));
        // Townsfolk live in the market, never in the compound's bunkhouse or the south ruins.
        spawnAmbientCivilians(sim, map.grid, layout.homes(), rng);
        StaffedBuildings.install(sim, map, seed, EnumSet.of(BuildingWork.Kind.WAREHOUSE), layout.warehouses().size(),
                layout.warehouses()::contains);
        StaffedBuildings.install(sim, map, seed, EnumSet.of(BuildingWork.Kind.GROCERY), layout.shops().size(),
                layout.shops()::contains);
        DustLedgerSequence ledger = sim.installDustLedger(DustLedgerSites.from(layout), raid, drinkers, salvageTeam);
        ledger.defences(sim, nests, pillboxSquads);
        spawnDustLedgerTechnical(sim, ledger);
        sim.setAutonomousCommander(Faction.MARINE, new RaidCommand(), RaidCommandDisclosure.INSTANCE);
        sim.setAutonomousCommander(Faction.DEFENDER, new RaidDefenderCommand(mobile),
                RaidDefenderCommandDisclosure.INSTANCE);
        return sim;
    }

    /** Agreus repair mobilization. Construction is supplied only by a committed fleet tender called in battle. */
    public static BattleSimulation createWaterUnderSeal(long seed, List<ShuttleAssignment> manifest, TargetProfile profile) {
        if (manifest == null || manifest.isEmpty() || manifest.size() > 4)
            throw new IllegalArgumentException("Water Under Seal requires one to four fitted company boats");
        MapResult map = NorthWeir.generate(seed, profile);
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        var pads = LandingPadSelector.select(map, manifest.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        for (int i = 0; i < manifest.size(); i++) {
            var assignment = manifest.get(i); var pad = pads.get(i);
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE, x, y,
                    entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            var mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles; mission.manifestOrdinal = i;
            mission.commandClaim = SquadCommandClaim.mission(WaterUnderSealCommand.issuer(Faction.MARINE), "Company fitted weir lift");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++)
                mission.cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            mission.marineLoadout = mission.cycleLoadouts[0];
        }
        for (int[] guard : new int[][]{{78,42},{82,75}}) {
            var post = openingDefenseNode(guard[0], guard[1], Faction.DEFENDER, map.grid);
            spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, guard[0], guard[1], 4), Faction.DEFENDER, "Berth Four switch watch", post, rng);
        }
        spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, 32, 59, 8), Faction.ALLY, "North Weir constable", null, rng);
        List<Integer> militia = new ArrayList<>();
        for (var squad : sim.getSquads()) if (squad.faction == Faction.ALLY) {
            militia.add(squad.id);
            if (!sim.handoffSquadCommand(squad.id, OpeningOperationCommand.issuer(Faction.ALLY), CommandAuthority.MISSION_COMMAND,
                    WaterUnderSealCommand.issuer(Faction.ALLY), null, "Constables inherit reopened services"))
                throw new IllegalStateException("Constable command handoff failed");
        }
        int technicians = sim.mintSquad(Faction.ALLY, UnitType.TECHNICIAN);
        for (int[] cell : pickSpawnCluster(map.grid, 28, 59, 2))
            sim.spawn(new EntitySpec("North Weir replacement repair technician", Faction.ALLY, UnitType.TECHNICIAN, cell[0], cell[1])
                    .squad(technicians).role(UnitRole.PATROL));
        sim.getSquad(technicians).originalSize = 2;
        sim.claimSquadCommand(technicians, CommandAuthority.MISSION_COMMAND, WaterUnderSealCommand.issuer(Faction.ALLY), "Edek's identified repair watch");
        List<EntitySpec> reserve = new ArrayList<>();
        for (int batch = 0; batch < 2; batch++) for (int[] cell : pickSpawnCluster(map.grid, 170, batch == 0 ? 88 : 94, 6)) {
            var member = makeOpeningMilitia("Berth Four road element " + batch + " / " + reserve.size(), Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL);
            if (reserve.size() < 2) WaterUnderSealIssue.recoveredAssaultRig(member, MarineArmorCatalogRegistry.require("armor.specter-recovered"));
            reserve.add(member);
        }
        long carrier = sim.convoy().spawn(VehicleType.CARGO_CARRIER, Faction.ALLY, VehicleMission.deployed(9, 59));
        sim.convoy().body(carrier).teleport(9, 59, 0f);
        sim.installWaterUnderSeal(technicians, carrier, militia, reserve);
        for (Faction faction : List.of(Faction.MARINE, Faction.ALLY, Faction.DEFENDER))
            sim.setAutonomousCommander(faction, new WaterUnderSealCommand(faction), WaterUnderSealCommand.disclosure(sim.getWaterUnderSeal()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(WaterUnderSealCommand.issuer(Faction.MARINE), "Company support joins weir protection"));
        return sim;
    }
    /** Bounded bonded-store recovery with ordinary fitted lift and a vulnerable calibration load. */
    public static BattleSimulation createLimitedWarrant(long seed, List<ShuttleAssignment> manifest, TargetProfile profile, boolean recordsRecovered) {
        if (manifest == null || manifest.isEmpty() || manifest.size() > 4)
            throw new IllegalArgumentException("Limited Warrant requires one to four fitted company boats");
        MapResult map = LowQuay.generate(seed, profile);
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        var pads = LandingPadSelector.select(map, manifest.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        for (int i = 0; i < manifest.size(); i++) {
            var assignment = manifest.get(i); var pad = pads.get(i);
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE, x, y,
                    entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            var mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles; mission.manifestOrdinal = i;
            mission.commandClaim = SquadCommandClaim.mission(LimitedWarrantCommand.issuer(Faction.MARINE), "Company fitted recovery lift");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++)
                mission.cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            mission.marineLoadout = mission.cycleLoadouts[0];
        }
        for (int[] guard : LowQuay.GUARD_POSTS) {
            var post = openingDefenseNode(guard[0], guard[1], Faction.DEFENDER, map.grid);
            spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, guard[0], guard[1], 4), Faction.DEFENDER, "Drent Haulage store guard", post, rng);
        }
        spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, 33, 59, 4), Faction.ALLY, "Low Quay constable", null, rng);
        int constables = sim.getSquads().stream().filter(squad -> squad.faction == Faction.ALLY).findFirst().orElseThrow().id;
        if (!sim.handoffSquadCommand(constables, OpeningOperationCommand.issuer(Faction.ALLY), CommandAuthority.MISSION_COMMAND,
                LimitedWarrantCommand.issuer(Faction.ALLY), null, "Constables cover the cleared loading exit"))
            throw new IllegalStateException("Constable command handoff failed");
        int technician = sim.mintSquad(Faction.ALLY, UnitType.TECHNICIAN);
        sim.spawn(new EntitySpec("Supplier calibration technician", Faction.ALLY, UnitType.TECHNICIAN, 28, 59)
                .squad(technician).role(UnitRole.PATROL));
        sim.getSquad(technician).originalSize = 1;
        sim.claimSquadCommand(technician, CommandAuthority.MISSION_COMMAND, LimitedWarrantCommand.issuer(Faction.ALLY), "Supplier recovery party");
        List<EntitySpec> reserve = new ArrayList<>();
        for (int[] cell : pickSpawnCluster(map.grid, LowQuay.RESERVE_X, LowQuay.RESERVE_Y, 6))
            reserve.add(makeOpeningMilitia("Runner rearguard " + reserve.size(), Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL));
        long carrier = sim.convoy().spawn(VehicleType.CARGO_CARRIER, Faction.ALLY, VehicleMission.deployed(LowQuay.LOAD_X, LowQuay.LOAD_Y));
        sim.convoy().body(carrier).teleport(LowQuay.LOAD_X, LowQuay.LOAD_Y, (float)Math.PI);
        sim.installLimitedWarrant(technician, constables, carrier, reserve, recordsRecovered);
        for (Faction faction : List.of(Faction.MARINE, Faction.ALLY, Faction.DEFENDER))
            sim.setAutonomousCommander(faction, new LimitedWarrantCommand(faction), LimitedWarrantCommand.disclosure(sim.getLimitedWarrant()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(LimitedWarrantCommand.issuer(Faction.MARINE), "Company recovery support"));
        return sim;
    }

    /**
     * District Escort: a finite two-squad escort job over Low Quay's ordinary freight lane. A fresh
     * consignment and its two-person handover party wait at the loading lane; two raider pickets
     * sit on the lane and one finite raider group commits once the company links up. The party's
     * name is the issuing office's only difference: both agreements field the same support.
     */
    public static BattleSimulation createDistrictEscort(long seed, List<ShuttleAssignment> manifest, TargetProfile profile,
                                                        String partyName) {
        if (manifest == null || manifest.isEmpty() || manifest.size() > 4)
            throw new IllegalArgumentException("District Escort requires one to four fitted company boats");
        if (partyName == null || partyName.isBlank()) throw new IllegalArgumentException("District Escort needs its party's office");
        MapResult map = LowQuay.generate(seed, profile);
        float[] route = DistrictEscortSequence.route(map.grid, VehicleType.CARGO_CARRIER.visualWidthCells);
        if (route.length < 8) throw new IllegalStateException("Low Quay freight lane has no carrier route to the apron");
        float[] routeX = new float[route.length / 2], routeY = new float[route.length / 2];
        for (int i = 0; i < routeX.length; i++) { routeX[i] = route[2 * i]; routeY[i] = route[2 * i + 1]; }
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        var pads = LandingPadSelector.select(map, manifest.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        for (int i = 0; i < manifest.size(); i++) {
            var assignment = manifest.get(i); var pad = pads.get(i);
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE, x, y,
                    entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            var mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles; mission.manifestOrdinal = i;
            mission.commandClaim = SquadCommandClaim.mission(DistrictEscortCommand.issuer(Faction.MARINE), "Company fitted escort lift");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++)
                mission.cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            mission.marineLoadout = mission.cycleLoadouts[0];
        }
        // Two pickets astride the lane, set back from the road on whatever ground the map gives.
        for (float share : new float[]{.35f, .6f}) {
            int index = Math.round((routeX.length - 1) * share);
            int px = Math.round(routeX[index]), py = Math.round(routeY[index]);
            var post = openingDefenseNode(px, py, Faction.DEFENDER, map.grid);
            spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, px, py, 4), Faction.DEFENDER, "Freight-lane raider picket", post, rng);
        }
        int apronX = LowQuay.LANDING_X + 4, apronY = LowQuay.LANDING_Y;
        spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, apronX, apronY, DistrictEscortProgress.CONSTABLES),
                Faction.ALLY, "Low Quay district constable", null, rng);
        int constables = sim.getSquads().stream().filter(squad -> squad.faction == Faction.ALLY).findFirst().orElseThrow().id;
        if (!sim.handoffSquadCommand(constables, OpeningOperationCommand.issuer(Faction.ALLY), CommandAuthority.MISSION_COMMAND,
                DistrictEscortCommand.issuer(Faction.ALLY), null, "Constables hold the western apron"))
            throw new IllegalStateException("Constable command handoff failed");
        int party = sim.mintSquad(Faction.ALLY, UnitType.TECHNICIAN);
        for (int[] cell : pickSpawnCluster(map.grid, LowQuay.LOAD_X + 6, LowQuay.LOAD_Y, DistrictEscortProgress.PARTY))
            sim.spawn(new EntitySpec(partyName, Faction.ALLY, UnitType.TECHNICIAN, cell[0], cell[1]).squad(party).role(UnitRole.PATROL));
        sim.getSquad(party).originalSize = DistrictEscortProgress.PARTY;
        sim.claimSquadCommand(party, CommandAuthority.MISSION_COMMAND, DistrictEscortCommand.issuer(Faction.ALLY), "Consignment handover party");
        List<EntitySpec> reserve = new ArrayList<>();
        // The committed group is the quarter's own: it comes through the north freight gate to the crossing.
        for (int[] cell : pickSpawnCluster(map.grid, LowQuay.RESERVE_X, LowQuay.RESERVE_Y, 6))
            reserve.add(makeOpeningMilitia("Freight-lane raider " + reserve.size(), Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL));
        long carrier = sim.convoy().spawn(VehicleType.CARGO_CARRIER, Faction.ALLY, VehicleMission.deployed(LowQuay.LOAD_X, LowQuay.LOAD_Y));
        sim.convoy().body(carrier).teleport(LowQuay.LOAD_X, LowQuay.LOAD_Y, (float) Math.PI);
        sim.installDistrictEscort(party, constables, carrier, reserve, routeX, routeY);
        for (Faction faction : List.of(Faction.MARINE, Faction.ALLY, Faction.DEFENDER))
            sim.setAutonomousCommander(faction, new DistrictEscortCommand(faction), DistrictEscortCommand.disclosure(sim.getDistrictEscort()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(DistrictEscortCommand.issuer(Faction.MARINE), "Company escort support"));
        return sim;
    }

    /** Latchkey territorial inspection with preserved tooling and optional owned support. */
    public static BattleSimulation createYardOwners(long seed, List<ShuttleAssignment> manifest, TargetProfile profile) {
        if (manifest == null || manifest.isEmpty() || manifest.size() > 4)
            throw new IllegalArgumentException("The Yard's Owners requires one to four fitted company boats");
        MapResult map = LatchkeyYard.generate(seed, profile);
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        var pads = LandingPadSelector.select(map, manifest.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        for (int i = 0; i < manifest.size(); i++) {
            var assignment = manifest.get(i); var pad = pads.get(i);
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE, x, y,
                    entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            var mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles; mission.manifestOrdinal = i;
            mission.commandClaim = SquadCommandClaim.mission(YardOwnersCommand.issuer(Faction.MARINE), "Company fitted inspection lift");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++)
                mission.cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            mission.marineLoadout = mission.cycleLoadouts[0];
        }
        for (var site : LatchkeyYard.SITES) {
            var post = openingDefenseNode(site.x(), site.y(), Faction.DEFENDER, map.grid);
            spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, site.x(), site.y(), 4), Faction.DEFENDER, "Prize crew yard guard", post, rng);
        }
        List<Integer> constables = new ArrayList<>();
        for (int x : new int[]{32,38}) {
            int squad = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
            for (int[] cell : pickSpawnCluster(map.grid,x,59,4))
                sim.spawn(makeOpeningMilitia("Latchkey constable",Faction.ALLY,cell[0],cell[1],rng).squad(squad).role(UnitRole.PATROL));
            sim.getSquad(squad).originalSize = 4;
            sim.claimSquadCommand(squad,CommandAuthority.MISSION_COMMAND,YardOwnersCommand.issuer(Faction.ALLY),"Constables inherit company gains");
            constables.add(squad);
        }
        int technicians = sim.mintSquad(Faction.ALLY, UnitType.TECHNICIAN);
        for (int y : new int[]{57,61}) sim.spawn(new EntitySpec("Latchkey inspection technician",Faction.ALLY,UnitType.TECHNICIAN,28,y)
                .squad(technicians).role(UnitRole.PATROL));
        sim.getSquad(technicians).originalSize = 2;
        sim.claimSquadCommand(technicians,CommandAuthority.MISSION_COMMAND,YardOwnersCommand.issuer(Faction.ALLY),"Living supplier inspection party");
        List<List<EntitySpec>> reserves = new ArrayList<>();
        for (var ingress : LatchkeyYard.RESERVE_INGRESS) {
            List<EntitySpec> group = new ArrayList<>();
            for (int[] cell : pickSpawnCluster(map.grid,ingress.x(),ingress.y(),4))
                group.add(makeOpeningMilitia("Prize crew counterattack",Faction.DEFENDER,cell[0],cell[1],rng).role(UnitRole.PATROL));
            reserves.add(group);
        }
        sim.installYardOwners(technicians,constables,reserves);
        for (Faction faction : List.of(Faction.MARINE,Faction.ALLY,Faction.DEFENDER))
            sim.setAutonomousCommander(faction,new YardOwnersCommand(faction),YardOwnersCommand.disclosure(sim.getYardOwners()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(YardOwnersCommand.issuer(Faction.MARINE),"Company inspection support"));
        return sim;
    }

    /**
     * Terms of Protection: the signed route's custody party crosses the apron under escort to the
     * Latchkey dispatch handover bay, technicians commission the captured rig tooling in the
     * calibration room, and Berth Four's finite assault arrives with a mortar team and two crude
     * powered rigs, followed by a prize-crew party on the Cinder Steps lane. The route chooses which
     * employer party comes — Rusk's estate guards or Lenna's fitters — and never the opposition.
     */
    public static BattleSimulation createTermsOfProtection(long seed, List<ShuttleAssignment> manifest, TargetProfile profile,
                                                           TermsOfProtectionProgress.Route route) {
        if (manifest == null || manifest.isEmpty() || manifest.size() > 4)
            throw new IllegalArgumentException("Terms of Protection requires one to four fitted company boats");
        if (route == null) throw new IllegalArgumentException("Terms of Protection launches under a signed agreement");
        MapResult map = LatchkeyYard.generate(seed, profile, LatchkeyYard.Visit.TERMS_OF_PROTECTION);
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        var pads = LandingPadSelector.select(map, manifest.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        String claim = TermsOfProtectionCommand.issuer(Faction.MARINE);
        for (int i = 0; i < manifest.size(); i++) {
            var assignment = manifest.get(i); var pad = pads.get(i);
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE, x, y,
                    entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            var mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles; mission.manifestOrdinal = i;
            mission.commandClaim = SquadCommandClaim.mission(claim, "Company fitted handover lift");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++)
                mission.cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            mission.marineLoadout = mission.cycleLoadouts[0];
        }
        String allyClaim = TermsOfProtectionCommand.issuer(Faction.ALLY);
        boolean custody = route == TermsOfProtectionProgress.Route.HOUSE_CUSTODY;
        var start = LatchkeyYard.PARTY_START;
        // The inspection's gains stand: constables hold the operator entrance they were handed.
        int constables = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
        for (int[] cell : pickSpawnCluster(map.grid, 88, 13, 4))
            sim.spawn(makeOpeningMilitia("Latchkey constable", Faction.ALLY, cell[0], cell[1], rng).squad(constables).role(UnitRole.PATROL));
        sim.getSquad(constables).originalSize = 4;
        sim.claimSquadCommand(constables, CommandAuthority.MISSION_COMMAND, allyClaim, "Constables hold the secured operator entrance");
        int representatives = partySquad(sim, map, start.x(), start.y() + 2, custody
                ? List.of("House Rusk custodian", "House Rusk custodian")
                : List.of("Joint party custodian / House Rusk", "Joint party delegate / Latchkey association"),
                allyClaim, custody ? "Rusk's appointed custodians" : "Joint custody party");
        int technicians = partySquad(sim, map, start.x() + 2, start.y() - 2,
                List.of("Latchkey utilities technician", "Latchkey utilities technician"), allyClaim, "Utilities crew commissioning the tooling");
        int fitters = custody ? -1 : partySquad(sim, map, start.x() - 2, start.y() - 3,
                List.of("Association fitter", "Association fitter", "Association fitter"), allyClaim, "Lenna's fitters stay at the benches");
        int guards = -1;
        if (custody) {
            guards = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
            for (int[] cell : pickSpawnCluster(map.grid, start.x() + 4, start.y() + 6, 4))
                sim.spawn(makeOpeningMilitia("Rusk estate guard", Faction.ALLY, cell[0], cell[1], rng).squad(guards).role(UnitRole.PATROL));
            sim.getSquad(guards).originalSize = 4;
            sim.claimSquadCommand(guards, CommandAuthority.MISSION_COMMAND, allyClaim, "Estate guards hold the Cinder Steps lane on Rusk's terms");
        }
        long carrier = sim.convoy().spawn(VehicleType.CARGO_CARRIER, Faction.ALLY, VehicleMission.deployed(start.x(), start.y()));
        sim.convoy().body(carrier).teleport(start.x(), start.y(), 0f);
        var jack = MarineArmorCatalogRegistry.require(GreyhookRigIssue.PATTERN);
        List<List<EntitySpec>> sections = new ArrayList<>();
        for (int section = 0; section < 2; section++) {
            List<EntitySpec> group = new ArrayList<>();
            for (int[] cell : pickSpawnCluster(map.grid, LatchkeyYard.SOUTH_ROAD.x(), LatchkeyYard.SOUTH_ROAD.y() + section * 2 - 1, 5)) {
                var member = makeOpeningMilitia("Berth Four assault " + section + " / " + group.size(), Faction.DEFENDER, cell[0], cell[1], rng)
                        .role(UnitRole.PATROL);
                if (group.isEmpty()) GreyhookRigIssue.boardingJack(member, jack);
                group.add(member);
            }
            sections.add(group);
        }
        List<EntitySpec> mortarTeam = new ArrayList<>();
        var tube = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FIELD_MORTAR_ID);
        for (int[] cell : pickSpawnCluster(map.grid, LatchkeyYard.CINDER_LANE.x(), LatchkeyYard.CINDER_LANE.y() - 2, 3)) {
            var member = makeOpeningMilitia("Berth Four mortar team " + mortarTeam.size(), Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL);
            if (mortarTeam.isEmpty()) member.specialEquipment(tube, tube.startingAmmo());
            mortarTeam.add(member);
        }
        List<EntitySpec> laneParty = new ArrayList<>();
        for (int[] cell : pickSpawnCluster(map.grid, LatchkeyYard.CINDER_LANE.x(), LatchkeyYard.CINDER_LANE.y() + 2, 6))
            laneParty.add(makeOpeningMilitia("Prize crew lane party " + laneParty.size(), Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL));
        sim.installTermsOfProtection(route, carrier,
                new TermsOfProtectionSequence.Parties(representatives, technicians, fitters, guards, constables),
                new TermsOfProtectionSequence.Reserve(sections, mortarTeam, laneParty));
        for (Faction faction : List.of(Faction.MARINE, Faction.ALLY, Faction.DEFENDER))
            sim.setAutonomousCommander(faction, new TermsOfProtectionCommand(faction), TermsOfProtectionCommand.disclosure(sim.getTermsOfProtection()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(claim, "Company handover support"));
        return sim;
    }

    /**
     * Black Sluice, the chapter-five foothold. Greyhook's groups are the finite authored table in
     * {@link SmallWarForces}; holders and the custody party are the employer's. {@code houseCustody}
     * names who walks the handover (Rusk's custodians or the joint party); it changes no strength.
     * The duel overhead ({@link BlackSluiceDuel}) is installed: the Greyhook alone in her cradle
     * south of the ridge from the start, and Rusk's lent Enforcer and Wolf once Voss orders the withdrawal.
     */
    public static BattleSimulation createSmallWar(long seed, List<ShuttleAssignment> manifest, TargetProfile profile, boolean houseCustody) {
        return createSmallWar(seed, manifest, profile, houseCustody, true);
    }

    /**
     * As {@link #createSmallWar(long, List, TargetProfile, boolean)}; {@code duel} false leaves the sky empty, the
     * control that shows the ground chapter does not depend on the duel.
     */
    public static BattleSimulation createSmallWar(long seed, List<ShuttleAssignment> manifest, TargetProfile profile,
                                                  boolean houseCustody, boolean duel) {
        if (manifest == null || manifest.isEmpty() || manifest.size() > 4)
            throw new IllegalArgumentException("A Small War requires one to four fitted company boats");
        MapResult map = BlackSluice.generate(seed, profile);
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        var pads = LandingPadSelector.select(map, manifest.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        for (int i = 0; i < manifest.size(); i++) {
            var assignment = manifest.get(i); var pad = pads.get(i);
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE, x, y,
                    entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            var mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles; mission.manifestOrdinal = i;
            mission.commandClaim = SquadCommandClaim.mission(SmallWarCommand.issuer(Faction.MARINE), "Company fitted Black Sluice lift");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++)
                mission.cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            mission.marineLoadout = mission.cycleLoadouts[0];
        }
        Map<Integer, SmallWarForces.Group> starting = new LinkedHashMap<>();
        Map<SmallWarForces.Group, List<EntitySpec>> reserves = new LinkedHashMap<>();
        for (var group : SmallWarForces.GROUPS) {
            List<EntitySpec> members = new ArrayList<>();
            for (int[] cell : pickSpawnCluster(map.grid, group.x(), group.y(), group.size()))
                members.add(makeOpeningMilitia(group.name() + " / " + members.size(), Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL));
            if (group.reserve()) { reserves.put(group, members); continue; }
            int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
            for (var member : members) sim.spawn(member.squad(squad));
            sim.getSquad(squad).originalSize = group.size();
            sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, SmallWarCommand.issuer(Faction.DEFENDER), group.name() + " holds the " + group.ingress());
            starting.put(squad, group);
        }
        // One visible support gun covers the causeway; the north gallery flanks it.
        long gun = sim.spawn(MapTurret.create("greyhook-causeway-gun", Faction.DEFENDER,
                TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, BlackSluice.GUN_X, BlackSluice.GUN_Y));
        List<Integer> holders = new ArrayList<>();
        for (int duty = 0; duty < 3; duty++) {
            int squad = sim.mintSquad(Faction.ALLY, UnitType.MILITIA);
            for (int[] cell : pickSpawnCluster(map.grid, BlackSluice.LANDING_X + 10 + 4 * duty, BlackSluice.LANDING_Y - 7, 4))
                sim.spawn(makeOpeningMilitia(houseCustody ? "Rusk estate guard" : "Black Sluice constable", Faction.ALLY, cell[0], cell[1], rng)
                        .squad(squad).role(UnitRole.PATROL));
            sim.getSquad(squad).originalSize = 4;
            sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, SmallWarCommand.issuer(Faction.ALLY), "Holders occupy actual company gains");
            holders.add(squad);
        }
        int custody = sim.mintSquad(Faction.ALLY, UnitType.TECHNICIAN);
        for (int[] cell : pickSpawnCluster(map.grid, BlackSluice.LANDING_X + 4, BlackSluice.LANDING_Y - 7, SmallWarProgress.CUSTODIANS))
            sim.spawn(new EntitySpec(houseCustody ? "House Rusk appointed custodian" : "Joint custody party member",
                    Faction.ALLY, UnitType.TECHNICIAN, cell[0], cell[1]).squad(custody).role(UnitRole.PATROL));
        sim.getSquad(custody).originalSize = SmallWarProgress.CUSTODIANS;
        sim.claimSquadCommand(custody, CommandAuthority.MISSION_COMMAND, SmallWarCommand.issuer(Faction.ALLY), "Agreed local custody party");
        sim.installSmallWar(custody, holders, starting, reserves, gun, houseCustody);
        if (duel) {
            var overhead = new BlackSluiceDuel();
            overhead.stage(sim);
            sim.getSmallWar().installDuel(overhead);
        }
        for (Faction faction : List.of(Faction.MARINE, Faction.ALLY, Faction.DEFENDER))
            sim.setAutonomousCommander(faction, new SmallWarCommand(faction), SmallWarCommand.disclosure(sim.getSmallWar()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(SmallWarCommand.issuer(Faction.MARINE), "Company Black Sluice support"));
        return sim;
    }

    /** One named employer party on foot: armed with sidearms, vulnerable, and nobody's soldiers. */
    private static int partySquad(BattleSimulation sim, MapResult map, int x, int y, List<String> names, String claim, String why) {
        int squad = sim.mintSquad(Faction.ALLY, UnitType.TECHNICIAN);
        var cells = pickSpawnCluster(map.grid, x, y, names.size());
        for (int i = 0; i < names.size(); i++)
            sim.spawn(new EntitySpec(names.get(i), Faction.ALLY, UnitType.TECHNICIAN, cells.get(i)[0], cells.get(i)[1])
                    .squad(squad).role(UnitRole.PATROL));
        sim.getSquad(squad).originalSize = names.size();
        sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, claim, why);
        return squad;
    }

    /** Repair mobilization already ashore at the recovered basin, with optional reserve boats and one physical cargo carrier. */
    public static BattleSimulation createColdStart(long seed, List<ShuttleAssignment> manifest, TargetProfile profile, boolean cargoDelivered, boolean shorePower) {
        if(manifest==null || manifest.size()>4)
            throw new IllegalArgumentException("Cold Start supports up to four company boats");
        MapResult map=ShepherdBasin.generate(seed,profile,ShepherdBasin.Visit.COLD_START);
        if (shorePower && map.topology.nightLighting() != null)
            map.topology.setNightLighting(map.topology.nightLighting().withPower("yard"));
        BattleSimulation sim=buildMap(map,List.of(),List.of(),List.of(),seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER,Faction.MARINE));
        sim.setGroundMarineArrival(new GroundMarineArrival(18, 68,
                SquadCommandClaim.mission(ColdStartCommand.issuer(Faction.MARINE), "Company holds the secured receiving apron")));
        Random rng=new Random(seed);
        var pads=LandingPadSelector.select(map,manifest.size(),LZ_MIN_SEPARATION);
        recordLzPads(sim,pads);
        for(int i=0;i<manifest.size();i++){
            var assignment=manifest.get(i);var pad=pads.get(i);
            float x=pad.centerX+.5f,y=pad.centerY+.5f;
            float[] entry=shuttleEntryFor(x,y,map.grid.getWidth(),map.grid.getHeight(),pad.approach);
            long air=sim.spawnShuttle(assignment.type,assignment.airframe,Faction.MARINE,x,y,
                    entry[0],entry[1],entry[2],entry[3],i*SHUTTLE_DROP_STAGGER_SEC,assignment.seatsPerSortie);
            var mission=sim.world().mission(air);
            mission.manifestOrdinal=i;
            // Committed fitted boats remain in reserve;
            // the company is already on site and carries no inbound recipe.
            mission.totalCycles=1;
            mission.marinesRemaining=0;
            mission.marineLoadout=new MarineLoadout[0];
            mission.cycleLoadouts=new MarineLoadout[][]{mission.marineLoadout};
            mission.state=ShuttleState.GONE;
        }
        // The earlier workshop gain persists. Scrappers now occupy the pump work site.
        var pump=openingDefenseNode(171,117,Faction.DEFENDER,map.grid);
        if (!shorePower)
            spawnOpeningMilitiaSquads(sim,pickSpawnCluster(map.grid,171,117,8),Faction.DEFENDER,"pump-scrapper-watch",pump,rng);
        spawnOpeningMilitiaSquads(sim,pickSpawnCluster(map.grid,ShepherdBasin.MILITIA.x(),ShepherdBasin.MILITIA.y(),8),Faction.ALLY,"basin-militia",null,rng);
        for(var squad:sim.getSquads())if(squad.faction==Faction.ALLY)
            if(!sim.handoffSquadCommand(squad.id,OpeningOperationCommand.issuer(Faction.ALLY),CommandAuthority.MISSION_COMMAND,
                    ColdStartCommand.issuer(Faction.ALLY),null,"Vale's militia waits for company gains"))
                throw new IllegalStateException("Basin militia handoff failed");
        int engineers=sim.mintSquad(Faction.ALLY,UnitType.TECHNICIAN);
        for(int[] cell:pickSpawnCluster(map.grid,ShepherdBasin.REPAIR_PARTY.x(),ShepherdBasin.REPAIR_PARTY.y(),2))
            sim.spawn(new EntitySpec("Cooperative repair technician",Faction.ALLY,UnitType.TECHNICIAN,cell[0],cell[1]).squad(engineers).role(UnitRole.PATROL));
        sim.getSquad(engineers).originalSize=2;
        sim.claimSquadCommand(engineers,CommandAuthority.MISSION_COMMAND,ColdStartCommand.issuer(Faction.ALLY),"Replacement party joins repair mobilization");
        List<EntitySpec> reserve=new ArrayList<>();
        for(int batch=0;batch<4;batch++) {
            ShepherdBasin.Place road=batch==2?ShepherdBasin.QUARRY_ROAD:ShepherdBasin.SOUTH_HAUL_ROAD;
            for(int[] cell:pickSpawnCluster(map.grid,road.x(),road.y(),6))
                reserve.add(makeOpeningMilitia("repair-road-"+batch+"-"+reserve.size(),Faction.DEFENDER,cell[0],cell[1],rng).role(UnitRole.PATROL));
        }
        long carrier=0L;
        if(!cargoDelivered) {
            carrier=sim.convoy().spawn(VehicleType.CARGO_CARRIER,Faction.ALLY,VehicleMission.deployed(8,68));
            sim.convoy().body(carrier).teleport(8,68,-90f);
        }
        sim.installColdStart(engineers,carrier,reserve,cargoDelivered,shorePower);
        // These exposed listening posts have their own duties, separate from the yard holders.
        String[] postNames={"south_haul","quarry"};
        ShepherdBasin.Place[] picketPlaces={ShepherdBasin.SOUTH_PICKET,ShepherdBasin.QUARRY_PICKET};
        int[][] posts={{picketPlaces[0].x(),picketPlaces[0].y(),151,96},{picketPlaces[1].x(),picketPlaces[1].y(),188,112}};
        for(int i=0;i<posts.length;i++) {
            int[] post=posts[i];
            int squad=sim.mintSquad(Faction.ALLY,UnitType.MILITIA);
            for(int[] cell:pickSpawnCluster(map.grid,post[0],post[1],3))
                sim.spawn(makeOpeningMilitia("basin-picket-"+postNames[i],Faction.ALLY,cell[0],cell[1],rng)
                        .squad(squad).role(UnitRole.PATROL));
            sim.getSquad(squad).originalSize=3;
            sim.claimSquadCommand(squad,CommandAuthority.MISSION_COMMAND,ColdStartCommand.issuer(Faction.ALLY),
                    "Thin outer picket covers the recovery yard");
            sim.getColdStart().registerPicket(postNames[i],squad,post[0],post[1],post[2],post[3],3);
        }
        for(Faction faction:List.of(Faction.MARINE,Faction.ALLY,Faction.DEFENDER))
            sim.setAutonomousCommander(faction,new ColdStartCommand(faction,engineers),ColdStartCommand.disclosure(sim.getColdStart()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(ColdStartCommand.issuer(Faction.MARINE),"Company support joins recovery"));
        return sim;
    }

    /** Departure operation with ordinary company lift and one real low-hover Shepherd. */
    public static BattleSimulation createWorkingPassage(long seed, List<ShuttleAssignment> manifest, TargetProfile profile) {
        if(manifest==null || manifest.isEmpty() || manifest.size()>4)
            throw new IllegalArgumentException("Working Passage requires one to four company boats");
        MapResult map=ShepherdBasin.generate(seed,profile,ShepherdBasin.Visit.WORKING_PASSAGE);
        BattleSimulation sim=buildMap(map,List.of(),List.of(),List.of(),seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER,Faction.MARINE));
        Random rng=new Random(seed);
        var pads=LandingPadSelector.select(map,manifest.size(),LZ_MIN_SEPARATION);
        recordLzPads(sim,pads);
        for(int i=0;i<manifest.size();i++){
            var assignment=manifest.get(i);var pad=pads.get(i);
            float x=pad.centerX+.5f,y=pad.centerY+.5f;
            float[] entry=shuttleEntryFor(x,y,map.grid.getWidth(),map.grid.getHeight(),pad.approach);
            long air=sim.spawnShuttle(assignment.type,assignment.airframe,Faction.MARINE,x,y,
                    entry[0],entry[1],entry[2],entry[3],i*SHUTTLE_DROP_STAGGER_SEC,assignment.seatsPerSortie);
            var mission=sim.world().mission(air);
            mission.totalCycles=assignment.cycles;mission.manifestOrdinal=i;
            mission.commandClaim=SquadCommandClaim.mission(WorkingPassageCommand.issuer(Faction.MARINE),"Company recovery lift");
            mission.cycleLoadouts=new MarineLoadout[assignment.cycles][];
            for(int cycle=0;cycle<assignment.cycles;cycle++)
                mission.cycleLoadouts[cycle]=InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle),rng);
            mission.marineLoadout=mission.cycleLoadouts[0];
        }
        for(ShepherdBasin.Place guard:ShepherdBasin.PASSAGE_WATCHES) {
            var post=openingDefenseNode(guard.x(),guard.y(),Faction.DEFENDER,map.grid);
            spawnOpeningMilitiaSquads(sim,pickSpawnCluster(map.grid,guard.x(),guard.y(),4),Faction.DEFENDER,"passage-scrapper-watch",post,rng);
        }
        spawnOpeningMilitiaSquads(sim,pickSpawnCluster(map.grid,ShepherdBasin.MILITIA.x(),ShepherdBasin.MILITIA.y(),8),Faction.ALLY,"basin-militia",null,rng);
        for(var squad:sim.getSquads())if(squad.faction==Faction.ALLY)
            if(!sim.handoffSquadCommand(squad.id,OpeningOperationCommand.issuer(Faction.ALLY),CommandAuthority.MISSION_COMMAND,
                    WorkingPassageCommand.issuer(Faction.ALLY),null,"Vale's militia waits for company gains"))
                throw new IllegalStateException("Basin militia handoff failed");
        List<Integer> militia=new ArrayList<>();
        for(var squad:sim.getSquads())if(squad.faction==Faction.ALLY)militia.add(squad.id);
        List<EntitySpec> reserve=new ArrayList<>();
        for(int batch=0;batch<4;batch++) {
            ShepherdBasin.Place road=batch==1?ShepherdBasin.QUARRY_ROAD:ShepherdBasin.SOUTH_HAUL_ROAD;int size=batch==0||batch==2?4:6;
            for(int[] cell:pickSpawnCluster(map.grid,road.x(),road.y(),size))
                reserve.add(makeOpeningMilitia("passage-road-"+batch+"-"+reserve.size(),Faction.DEFENDER,cell[0],cell[1],rng).role(UnitRole.PATROL));
        }
        Tender tender;
        try { tender=Tender.lowHover(Faction.MARINE,131,115,24,20); }
        catch(Exception ex) { throw new IllegalStateException("Unable to launch Morrow",ex); }
        sim.tenders().add(sim,tender);
        sim.getCommandPowerService().register(new YardConstruction(tender));
        sim.installWorkingPassage(tender,militia,reserve);
        for(Faction faction:List.of(Faction.MARINE,Faction.ALLY,Faction.DEFENDER))
            sim.setAutonomousCommander(faction,new WorkingPassageCommand(faction),WorkingPassageCommand.disclosure(sim.getWorkingPassage()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(WorkingPassageCommand.issuer(Faction.MARINE),"Company support joins recovery"));
        return sim;
    }

    /** Finite territorial prototype; deliberately bypasses the generic siege production/airfield profile. */
    /** Fifteen seconds of holding every Junction site together before the win is confirmed. */
    static final int JUNCTION_CONFIRM_TICKS = 15 * 30;

    public static BattleSimulation createJunction(long seed, List<ShuttleAssignment> playerManifest, TargetProfile profile) {
        if (playerManifest == null || playerManifest.isEmpty() || playerManifest.size() > 4)
            throw new IllegalArgumentException("Junction requires one to four committed player boats");
        var layout = JunctionCompound.generate(seed, profile);
        MapResult map = layout.map();
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        // The sites must be held together briefly before the junction counts as open: a last-second
        // capture is not a hold, and Dispatch's report has time to be heard before the outcome.
        sim.addObjective(new ConquestObjective(sim.getCompoundService(), JUNCTION_CONFIRM_TICKS));
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        List<ShuttleAssignment> assignments = new ArrayList<>();
        assignments.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6));
        assignments.addAll(playerManifest);
        List<LandingPad> pads = LandingPadSelector.select(map, assignments.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        for (int i = 0; i < assignments.size(); i++) {
            var assignment = assignments.get(i);
            var pad = pads.get(i);
            Faction faction = i == 0 ? Faction.ALLY : Faction.MARINE;
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, faction,
                    x, y, entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            ShuttleMission mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles;
            mission.manifestOrdinal = i - 1;
            mission.commandClaim = SquadCommandClaim.mission(JunctionCommand.issuer(faction), "Junction committed lift");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++) {
                mission.cycleLoadouts[cycle] = faction == Faction.ALLY
                        ? InfantryLoadoutRolls.defenderSquad(assignment.seatsForCycle(cycle), UnitType.MILITIA, RiskLevel.LOW, rng)
                        : InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            }
            mission.marineLoadout = mission.cycleLoadouts[0];
            if (faction == Faction.ALLY) mission.deboardUnitType = UnitType.MILITIA;
        }
        for (int i = 1; i < layout.sites().size(); i++) {
            var post = layout.sites().get(i);
            spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, post.anchorX, post.anchorY, post.garrisonSize),
                    Faction.DEFENDER, "junction-guard-" + i, post, rng);
        }
        spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, layout.reserve().interiorAnchorX, layout.reserve().interiorAnchorY, 8), Faction.DEFENDER, "junction-reserve", null, rng);
        // Each trench is manned by its own squad, holding the trench itself rather than the site behind it.
        // The squad is detached from the site, and the trench order comes from the same garrison issuer that
        // posted it there: a different issuer at the same authority is refused, which left the squads under
        // their spawn order and walking into the building behind the trench.
        Set<Integer> trenchSquads = new HashSet<>();
        for (int t = 0; t < JunctionCompound.TRENCHES.size(); t++) {
            int[] trench = JunctionCompound.TRENCHES.get(t);
            int[] centre = JunctionCompound.trenchCentre(t);
            var post = layout.sites().get(t + 2);
            Set<Integer> before = new HashSet<>();
            for (Squad squad : sim.getSquads()) before.add(squad.id);
            List<int[]> cells = new ArrayList<>();
            for (int y = trench[1]; y <= trench[2] && cells.size() < 6; y += 2) cells.add(new int[]{trench[0], y});
            spawnOpeningMilitiaSquads(sim, cells, Faction.DEFENDER, "junction-trench-" + t, post, rng, 6);
            int run = sim.fightingPositions().runIdAt(centre[0], centre[1]);
            if (run < 0) throw new IllegalStateException("Junction trench " + t + " is not a fighting position");
            for (Squad squad : List.copyOf(sim.getSquads())) if (!before.contains(squad.id)) {
                squad.assignedNode = null;
                trenchSquads.add(squad.id);
                sim.assignSquadCommand(ObjectiveAssignment.defendRun(squad.id, centre[0], centre[1],
                                (trench[2] - trench[1]) / 2 + 1, run),
                        CommandAuthority.GARRISON, "opening-local-garrison", "man the trench across the approach");
            }
        }
        // A second finite counterattack waits on the road from Rill and comes in once a second site falls.
        spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, JunctionCompound.RILL_ROAD_X, JunctionCompound.RILL_ROAD_Y, 8),
                Faction.DEFENDER, "junction-rill-road", null, rng);
        for (Squad squad : sim.getSquads()) if (squad.faction == Faction.DEFENDER && squad.assignedNode == null
                && !trenchSquads.contains(squad.id))
            if (!sim.handoffSquadCommand(squad.id, OpeningOperationCommand.issuer(Faction.DEFENDER),
                    CommandAuthority.MISSION_COMMAND, JunctionCommand.issuer(Faction.DEFENDER), null, "finite Junction reserve"))
                throw new IllegalStateException("Junction reserve command handoff failed");
        for (Faction faction : List.of(Faction.MARINE, Faction.ALLY, Faction.DEFENDER))
            sim.setAutonomousCommander(faction, new JunctionCommand(faction, layout.reserve().interiorAnchorX, layout.reserve().interiorAnchorY,
                    JunctionCompound.RILL_ROAD_X, JunctionCompound.RILL_ROAD_Y), ConquestCommandDisclosure.INSTANCE);
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(JunctionCommand.issuer(Faction.MARINE), "Company support joins the operation"));
        sim.installJunctionStoryHooks();
        // Control: -Dbattle.junction.talonTwo=false fights the junction with no gunship, so its trenches show their cost.
        // Wayline's impounded haulers stand in their lot nose-north: civilian hulls, never a target.
        for (float[] h : JunctionCompound.IMPOUNDED_HAULERS) {
            VehicleMission parked = new VehicleMission(new float[]{h[0], h[0]}, new float[]{h[1], h[1] + 1},
                    new float[]{h[0], h[0]}, new float[]{h[1], h[1] + 1}, 0f, 0);
            parked.state = VehicleState.DEPLOYED;
            sim.convoy().spawn(VehicleType.CARGO_CARRIER, Faction.CIVILIAN, parked);
        }
        if (Boolean.parseBoolean(System.getProperty("battle.junction.talonTwo", "true"))) sim.installWaylineGunship(false);
        return sim;
    }

    /** Finite territorial prototype; deliberately bypasses the generic siege production/airfield profile. */
    public static BattleSimulation createRelayNine(long seed, List<ShuttleAssignment> playerManifest, TargetProfile profile) {
        if (playerManifest == null || playerManifest.isEmpty() || playerManifest.size() > 4)
            throw new IllegalArgumentException("RelayNine requires one to four committed player boats");
        var layout = RelayNineCompound.generate(seed, profile);
        MapResult map = layout.map();
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new ConquestObjective(sim.getCompoundService()));
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        List<ShuttleAssignment> assignments = new ArrayList<>();
        assignments.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 4));
        assignments.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 4));
        assignments.addAll(playerManifest);
        List<LandingPad> pads = LandingPadSelector.select(map, assignments.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        for (int i = 0; i < assignments.size(); i++) {
            var assignment = assignments.get(i);
            var pad = pads.get(i);
            Faction faction = i < 2 ? Faction.ALLY : Faction.MARINE;
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, faction,
                    x, y, entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            ShuttleMission mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles;
            mission.manifestOrdinal = i < 2 ? -1 : i - 2;
            mission.commandClaim = SquadCommandClaim.mission(RelayNineCommand.issuer(faction), "RelayNine committed lift");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++) {
                mission.cycleLoadouts[cycle] = faction == Faction.ALLY
                        ? InfantryLoadoutRolls.defenderSquad(assignment.seatsForCycle(cycle), UnitType.MILITIA, RiskLevel.LOW, rng)
                        : InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            }
            mission.marineLoadout = mission.cycleLoadouts[0];
            if (faction == Faction.ALLY) mission.deboardUnitType = UnitType.MILITIA;
        }
        for (int i = 1; i < layout.sites().size(); i++) {
            var post = layout.sites().get(i);
            spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, post.anchorX, post.anchorY, post.garrisonSize),
                    Faction.DEFENDER, "relay-nine-guard-" + i, post, rng);
        }
        List<EntitySpec> reserve = new ArrayList<>();
        for (int[] cell : pickSpawnCluster(map.grid, 85, 2, 8))
            reserve.add(makeOpeningMilitia("relay-reserve-" + reserve.size(), Faction.DEFENDER,
                    cell[0], cell[1], rng).role(UnitRole.PATROL));
        for (Faction faction : List.of(Faction.MARINE, Faction.ALLY, Faction.DEFENDER))
            sim.setAutonomousCommander(faction, new RelayNineCommand(faction), ConquestCommandDisclosure.INSTANCE);
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(RelayNineCommand.issuer(Faction.MARINE), "Company support joins the operation"));
        sim.installRelayNine(reserve);
        return sim;
    }

    /** Employer column finale; committed player boats retain their exact airframes. */
    public static BattleSimulation createThunderRun(long seed, List<ShuttleAssignment> playerManifest, TargetProfile profile, FreightRunProgress.Attempt attempt, int delivered) {
        if (playerManifest == null || playerManifest.isEmpty() || playerManifest.size() > 4)
            throw new IllegalArgumentException("ThunderRun requires one to four committed player boats");
        var layout = ThunderRunCompound.generate(seed, profile);
        MapResult map = layout.map();
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        List<ShuttleAssignment> assignments = new ArrayList<>();
        assignments.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 4));
        assignments.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 4));
        assignments.addAll(playerManifest);
        List<LandingPad> pads = LandingPadSelector.select(map, assignments.size(), LZ_MIN_SEPARATION);
        recordLzPads(sim, pads);
        for (int i = 0; i < assignments.size(); i++) {
            var assignment = assignments.get(i);
            var pad = pads.get(i);
            Faction faction = i < 2 ? Faction.ALLY : Faction.MARINE;
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, map.grid.getWidth(), map.grid.getHeight(), pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, faction,
                    x, y, entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            ShuttleMission mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles;
            mission.manifestOrdinal = i < 2 ? -1 : i - 2;
            mission.commandClaim = SquadCommandClaim.mission(ThunderRunCommand.issuer(faction), "ThunderRun committed lift");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++) {
                mission.cycleLoadouts[cycle] = faction == Faction.ALLY
                        ? InfantryLoadoutRolls.defenderSquad(assignment.seatsForCycle(cycle), UnitType.MILITIA, RiskLevel.LOW, rng)
                        : InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            }
            mission.marineLoadout = mission.cycleLoadouts[0];
            if (faction == Faction.ALLY) mission.deboardUnitType = UnitType.MILITIA;
        }
        for (int i = 1; i < layout.sites().size(); i++) {
            var post = layout.sites().get(i);
            spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, post.anchorX, post.anchorY, post.garrisonSize),
                    Faction.DEFENDER, "thunder-run-guard-" + i, post, rng);
        }
        for (var gun : ThunderRunCompound.EXIT_GUNS) sim.spawn(MapTurret.create("freight-road-gun-" + gun.y(),
                Faction.DEFENDER, TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, gun.x(), gun.y()));
        // Six of the finite raiders man the prepared checkpoint before the road reserve arrives.
        for (var cell : ThunderRunCompound.EXIT_RIFLE_POSTS) {
            var post = openingDefenseNode(cell.x(), cell.y(), Faction.DEFENDER, map.grid);
            spawnOpeningMilitiaSquads(sim, List.of(new int[]{cell.x() - 1, cell.y()},
                            new int[]{cell.x(), cell.y()}, new int[]{cell.x() + 1, cell.y()}),
                    Faction.DEFENDER, "freight-checkpoint-" + cell.y(), post, rng, 3);
        }
        // The employer's picket already holds the first crossroad's north arm when the company lands;
        // its gun goes up while the company is still fighting for Dispatch.
        var picket = ThunderRunCompound.MILITIA_POSTS.get(0);
        spawnOpeningMilitiaSquads(sim, List.of(new int[]{picket.x() - 1, picket.y()}, new int[]{picket.x(), picket.y()},
                        new int[]{picket.x() - 1, picket.y() + 1}, new int[]{picket.x(), picket.y() + 1}),
                Faction.ALLY, "wayline-crossroad-picket",
                openingDefenseNode(picket.x(), picket.y(), Faction.ALLY, map.grid), rng, 4);
        List<EntitySpec> reserve = new ArrayList<>();
        for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++) {
            int x = batch == 0 ? 128 : ThunderRunPacing.usesOutboundRoad(batch) ? 275 : 165;
            int y = batch == 0 ? 65 : batch % 2 == 0 ? 140 : 2;
            int member = 0;
            for (int[] cell : pickSpawnCluster(map.grid, x, y, ThunderRunPacing.RAIDERS_PER_BATCH)) {
                var raider = makeOpeningMilitia("freight-road-raider-" + reserve.size(), Faction.DEFENDER,
                        cell[0], cell[1], rng).role(UnitRole.PATROL);
                if (member < ThunderRunPacing.rocketCarriers(batch))
                    raider.specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SCRAP_ROCKET_ID), 2);
                reserve.add(raider);
                member++;
            }
        }
        sim.installThunderRun(attempt, delivered, reserve);
        sim.installWaylineGunship(true);
        for (var gun : ThunderRunCompound.ANTI_AIR_POSTS) sim.spawn(MapTurret.create("freight-aa-" + gun.y(),
                Faction.DEFENDER, TurretCatalogRegistry.RIDGE_AA_STRUCTURE_ID, gun.x(), gun.y()));
        for (Faction faction : List.of(Faction.MARINE, Faction.ALLY, Faction.DEFENDER))
            sim.setAutonomousCommander(faction, new ThunderRunCommand(faction),
                    (view, perspective, topology, claims) -> ThunderRunCommandFrame.disclose(
                            view, perspective, topology, claims, sim.getThunderRun().holdSite()));
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission(ThunderRunCommand.issuer(Faction.MARINE), "Company support joins the operation"));
        return sim;
    }

    /** Authored evacuation prototype. The roster comes only from the persisted stranded cohort. */
    public static BattleSimulation createLastLift(long seed, List<ShuttleAssignment> manifest,
            TargetProfile profile, LastLiftProgress.Attempt attempt) {
        if (manifest == null || manifest.isEmpty() || manifest.size() > 4 || attempt == null || attempt.people().isEmpty())
            throw new IllegalArgumentException("Last Lift requires a remaining cohort and one to four boats");
        var layout = LastLiftCompound.generate(seed, profile, attempt.people().size());
        MapResult map = layout.map();
        BattleSimulation sim = buildMap(map, List.of(), List.of(), List.of(), seed).sim();
        sim.setSurface(surfaceOf(profile));
        sim.getBattleResources().disableCompoundProduction();
        sim.setPlayerSupportCommandClaim(SquadCommandClaim.mission("rescue-corridor", "Company support joins the operation"));
        var payload = CivilianEvacuationPayload.installAuthored(sim, layout.placement(), "RILL-CREW", "Rill work crew", true);
        payload.objective.nameLocations("Motor pool shelter", layout.placement().shelterApproachX,
                layout.placement().shelterApproachY, "Southern apron pickup");
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        Random rng = new Random(seed);
        recordLzPads(sim, map.landingPads);
        for (int i = 0; i < manifest.size(); i++) {
            var assignment = manifest.get(i);
            var pad = map.landingPads.get(i);
            float x = pad.centerX + .5f, y = pad.centerY + .5f;
            float[] entry = shuttleEntryFor(x, y, LastLiftCompound.WIDTH, LastLiftCompound.HEIGHT, pad.approach);
            long air = sim.spawnShuttle(assignment.type, assignment.airframe, Faction.MARINE,
                    x, y, entry[0], entry[1], entry[2], entry[3], i * SHUTTLE_DROP_STAGGER_SEC, assignment.seatsPerSortie);
            var mission = sim.world().mission(air);
            mission.totalCycles = assignment.cycles; mission.manifestOrdinal = i;
            mission.commandClaim = SquadCommandClaim.mission("rescue-corridor", "Rill relief and escort");
            mission.cycleLoadouts = new MarineLoadout[assignment.cycles][];
            for (int cycle = 0; cycle < assignment.cycles; cycle++)
                mission.cycleLoadouts[cycle] = InfantryLoadoutRolls.playerSquad(assignment.seatsForCycle(cycle), rng);
            mission.marineLoadout = mission.cycleLoadouts[0];
        }
        float px = LastLiftCompound.LIFT_X + .5f, py = LastLiftCompound.LIFT_Y + .5f;
        float[] entry = shuttleEntryFor(px, py, LastLiftCompound.WIDTH, LastLiftCompound.HEIGHT, LandingPad.Approach.SOUTH);
        long pickup = sim.spawnShuttle(ShuttleType.VALKYRIE, Faction.CIVILIAN,
                px, py, entry[0], entry[1], entry[2], entry[3], 35f);
        var pickupMission = sim.world().mission(pickup);
        pickupMission.marinesRemaining = 0; pickupMission.awaitingEvacuees = true;
        pickupMission.evacueeCapacity = LastLiftProgress.COHORT_SIZE;
        if (!sim.attachCivilianPickupShuttle(pickup)) throw new IllegalStateException("Pickup attachment failed");
        payload.objective.configureStoryPickup(pickup);
        var p = layout.placement();
        var guard = openingDefenseNode(p.shelterApproachX, p.shelterApproachY, Faction.ALLY, map.grid);
        spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, guard.anchorX, guard.anchorY, 6),
                Faction.ALLY, "rill-militia", guard, rng, 6);
        for (Squad squad : sim.getSquads()) if (squad.faction == Faction.ALLY) sim.registerShelterGuardSquad(squad.id);
        for (int[] site : new int[][]{{54,64},{134,64},{124,90}}) {
            var post = openingDefenseNode(site[0], site[1], Faction.DEFENDER, map.grid);
            spawnOpeningMilitiaSquads(sim, pickSpawnCluster(map.grid, site[0], site[1], 4),
                    Faction.DEFENDER, "rill-blockers-" + site[0], post, rng);
        }
        List<EntitySpec> west = new ArrayList<>(), east = new ArrayList<>();
        for (int[] cell : pickSpawnCluster(map.grid, 2, 64, 6))
            west.add(makeOpeningMilitia("rill-west-" + west.size(), Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL));
        for (int[] cell : pickSpawnCluster(map.grid, 189, 64, 6))
            east.add(makeOpeningMilitia("rill-east-" + east.size(), Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL));
        sim.setAutonomousCommander(Faction.DEFENDER, new ExtractionDefenderCommand(Set.of()), ExtractionDefenderCommandDisclosure.INSTANCE);
        sim.installLastLift(new LastLiftAttempt(attempt, payload), west, east, p);
        return sim;
    }

    /** Six precommitted militia enter at a road mouth; loadouts are frozen at setup. */
    private static List<EntitySpec> openingReserve(MapResult map, ReliefCheckpoint.Cell entry,
                                                  String prefix, Random rng) {
        List<EntitySpec> force = new ArrayList<>();
        for (int[] cell : pickSpawnCluster(map.grid, entry.x(), entry.y(), RELIEF_ROAD_RESERVE)) {
            EntitySpec raider = makeOpeningMilitia(prefix + "-" + force.size(), Faction.DEFENDER,
                    cell[0], cell[1], rng).role(UnitRole.PATROL);
            raider.primaryWeapon(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID),
                    raider.equipmentGrade, raider.soldierProfile);
            force.add(raider);
        }
        return force;
    }

    private static void validateOpeningOperationLayout(
            MapResult map, PointOfInterest place, List<LandingPad> landingPads,
            List<int[]> localMilitiaCells, List<int[]> raiderCells,
            int[] originalCommandAnchor) {
        if (place == null || !map.grid.isWalkable(place.anchorCellX, place.anchorCellY)
                || !map.grid.isWalkable(place.interiorAnchorX, place.interiorAnchorY)) {
            throw new IllegalStateException(
                    "opening-operation place has no walkable ingress");
        }
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid,
                place.anchorCellX, place.anchorCellY,
                place.interiorAnchorX, place.interiorAnchorY))) {
            throw new IllegalStateException(
                    "opening-operation place ingress is disconnected");
        }
        if (Paths.isEmpty(GridPathfinder.findPath(map.grid,
                originalCommandAnchor[0], originalCommandAnchor[1],
                place.anchorCellX, place.anchorCellY))) {
            throw new IllegalStateException(
                    "opening-operation place is unreachable from its staging anchor");
        }
        for (LandingPad pad : landingPads) {
            if (!pad.isClear(map.grid, map.topology)) {
                throw new IllegalStateException(
                        "opening-operation facility displaced a shuttle landing pad");
            }
        }
        for (int[] cell : localMilitiaCells) {
            if (!map.grid.isWalkable(cell[0], cell[1])) {
                throw new IllegalStateException(
                        "opening-operation facility displaced local militia staging");
            }
        }
        for (int[] cell : raiderCells) {
            if (!map.grid.isWalkable(cell[0], cell[1])) {
                throw new IllegalStateException(
                        "opening-operation facility displaced raider staging");
            }
        }
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
        return createCivilianRescue(seed, manifest, enemyHasHeavyArmor, risk, swarmCount, profile, stressTest,
                HivePosture.DEFAULT, 0);
    }

    /**
     * Civilian rescue with the hive posture chosen at the briefing, frozen into
     * the corridor commander, and {@code liftHardpoints} Vulcans already standing
     * at the lift (a fixture statement standing in for a tender's finished
     * builds; zero in every launched battle).
     */
    public static BattleSimulation createCivilianRescue(
            long seed, List<ShuttleAssignment> manifest,
            boolean enemyHasHeavyArmor, RiskLevel risk, int swarmCount,
            TargetProfile profile, boolean stressTest, HivePosture posture, int liftHardpoints) {
        boolean colony = isColonyBreachColony(risk, stressTest);
        EnumSet<MapFeature> missing = EnumSet.noneOf(MapFeature.class);
        for (int attempt = 0; attempt < 8; attempt++) {
            long battleSeed = seed + attempt * 0x9E3779B97F4A7C15L;
            MapScale scale = colony ? MapScale.COLONY : MapScale.forRisk(risk);
            PrecinctPlan colonyPlan = colony ? colonyPlanFor(scale, battleSeed) : null;
            MapResult map = MAP_GEN.generate(scale.width, scale.height, battleSeed, null,
                    profile, colonyPlan);
            if (colony) {
                missing = MissionMapRequirements.missingFromColony(map);
                if (!missing.isEmpty()) continue;
            }
            // On the colony the shelter is the civil-defence compound's hall, settled before
            // the colony's places are published so everything reads the same hall.
            List<PointOfInterest> shelters = CivilDefenceCompound.shelters(map.grid, map.pointsOfInterest, colonyPlan);
            if (shelters == null) continue;
            publishHeldPlaces(map, Faction.CIVILIAN);
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
            sim.setSurface(surfaceOf(profile));
            // Heavy assets are what the player brought: the company's mechs and
            // vehicles arrive through its own Mech Support power, which every
            // battle honours. A lance allowance is only what the mission itself
            // declared; deriving one from the same mechs would field them twice.
            if (profile != null) sim.setMechDrops(new MechDropService(profile.declaredMechDrops()));

            CivilianEvacuationPayload payload =
                    CivilianEvacuationPayload.install(sim, shelters, battleSeed);
            if (payload == null) continue;
            sim.setAutonomousCommander(Faction.MARINE,
                    new RescueEscortCommand(payload.placement, posture), RescueCommandDisclosure.INSTANCE);
            LiftHardpoints.standUp(sim, payload.placement, liftHardpoints);
            // The payload installs the marine evacuation objective. Defenders
            // win by eliminating the responding marine force.
            sim.addObjective(new EliminateFactionObjective(
                    Faction.DEFENDER, Faction.MARINE));

            recordLzPads(sim, lzCells);
            for (int i = 0; i < lzCells.size(); i++) {
                ShuttleAssignment assignment =
                        assignments.get(i % assignments.size());
                LandingPad lz = lzCells.get(i);
                float lzCenterX = lz.centerX + 0.5f;
                float lzCenterY = lz.centerY + 0.5f;
                float[] entry = shuttleEntryFor(lzCenterX, lzCenterY,
                        scale.width, scale.height, lz.approach);
                long shuttleId = sim.spawnShuttle(
                        assignment.type, assignment.airframe, Faction.MARINE,
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
                            assignment.seatsForCycle(cycle), rng);
                }
                shuttleMission.cycleLoadouts = cycleLoadouts;
                shuttleMission.marineLoadout = cycleLoadouts[0];
                equipDefaultTurrets(sim, shuttleId);
            }

            spawnAmbientCivilians(sim, map, rng);
            spawnSpaceportGroundCrew(sim, map, parkedAircraft, rng);
            // The colony's militia: the shelter guard always, and on the colony
            // map a few held buildings, their emplacements and technicals.
            if (ColonyMilitia.install(sim, map, payload.placement, lzCells,
                    risk, battleSeed, colony) == null) {
                continue;
            }
            // The cohort is the shelter's people and every holdout's.
            installRescuePickup(sim, payload.placement,
                    sim.getCivilianEvacuationTracker().expectedCount(), battleSeed,
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
            // A DEBUG-stated call lands claimed by the corridor commander, on the marines' own pads.
            sim.mechDrops().installScriptedCaller(Faction.MARINE,
                    SquadCommandClaim.mission("rescue-corridor", "commander-called mech lance"),
                    () -> MechDropService.centroidOf(sim.getMarineLandingPads()));
            if (!stressTest) {
                // The breach: where the colony breaks open and when. The colony
                // path owes a full plan and re-rolls without one; a smaller
                // map carries whatever sites it can seat.
                ColonyEruptionPlan eruption = ColonyEruptionPlan.forColony(
                        sim.getGrid(), sim.getTopology(), map.pointsOfInterest,
                        payload.placement, lzCells, battleSeed ^ ERUPTION_SEED_SALT);
                if (colony && !eruption.isComplete()) continue;
                // The colony's people outside the shelter: what the swarm feeds on.
                List<Long> colonists = colony
                        ? ColonyPopulation.install(sim, map.pointsOfInterest, payload.placement,
                                battleSeed ^ ERUPTION_SEED_SALT)
                        : List.of();
                sim.installColonyBreach(eruption, colonists);
                // The colony as a living field: homesteads, militia knots in the
                // open, families walking out and stragglers, all the swarm's to find.
                if (colony && ColonyLivingField.enabled()) {
                    sim.installColonyLivingField(ColonyLivingField.install(sim, map, payload.placement,
                            lzCells, risk, battleSeed ^ ERUPTION_SEED_SALT));
                }
                // A side objective beside the rescue, rolled from what the colony's set pieces can
                // host, each under its own bore's pressure (set-piece-blocks.md).
                if (colony) ColonyBreachObjectives.install(sim, map, payload.placement, battleSeed);
            }
            return sim;
        }
        if (colony && !missing.isEmpty()) {
            throw new IllegalStateException(MissionMapRequirements.describeFailure(
                    MissionType.EXTRACTION, seed, 8, missing));
        }
        throw new IllegalStateException(
                "unable to place civilian rescue payload after 8 map attempts");
    }

    /** Salt for the eruption-site draw, so it is not the battle stream read again. */
    private static final long ERUPTION_SEED_SALT = 0x424F52455349544EL;

    /**
     * Whether this rescue is fought on the Colony Breach colony: the campaign's
     * high-risk path. Lower risks and the debug stress path keep their tier map.
     */
    public static boolean isColonyBreachColony(RiskLevel risk, boolean stressTest) {
        return !stressTest && risk == RiskLevel.HIGH;
    }

    /**
     * The colony's places: a town in the middle, a working installation off to
     * one side and a dormitory suburb on the other.
     *
     * <p>Laid out rather than derived, because the colony is the same kind of
     * place whatever market the event names, and a rescue fixture carries no
     * market at all — a derivation would hand it the stock city. The town is
     * grown at a town's density and the outlying places at a hamlet's, the
     * {@code BALANCED} reading of a colony that has a centre and ground around
     * it. Which side the installation stands on is the seed's.
     *
     * <p><b>The company lands in the fields, not in the town.</b> Shuttles
     * cannot set down amid an infested colony: bores open underfoot, spore
     * colonies cover the sky over them, and every building is cover for the
     * swarm right up to the hull. So the colony is laid out with an
     * {@link LandingKind#OPEN_FIELD open-field} landing outside its edge, and
     * the company comes down where it can see them coming and fights its way
     * in — which is why it does not land beside the colonists. The fields are
     * north or south of the town, the seed's choice: the three places run
     * across the map's long axis, so that is the colony's far side from every
     * place a bore comes up beside. They lie against the map edge the shuttles
     * arrive across, so a lift pays only the field's own radius in flight.
     * {@code -Dbattle.colonyBreach.fieldLanding=false} is the control: no
     * landing place, and the arrival scans the whole map as it used to.
     */
    static PrecinctPlan colonyPlanFor(MapScale scale, long seed) {
        Random rng = new Random(seed ^ PRECINCT_SEED_SALT);
        boolean installationEast = rng.nextBoolean();
        MapPlacement fields = !colonyLandsInFields() ? null
                : new SplittableRandom(seed ^ FIELD_SIDE_SALT).nextBoolean()
                        ? MapPlacement.NORTH : MapPlacement.SOUTH;
        List<PrecinctBrief> places = new ArrayList<>(List.of(
                PrecinctBrief.settlement("colony", MapPlacement.CENTRE,
                        GrownTrunkPlan.Profile.town(), PrecinctCharacter.TOWN),
                PrecinctBrief.settlement("installation",
                        installationEast ? MapPlacement.EAST : MapPlacement.WEST,
                        GrownTrunkPlan.Profile.hamlet(), PrecinctCharacter.DEPOT),
                PrecinctBrief.settlement("suburb",
                        installationEast ? MapPlacement.WEST : MapPlacement.EAST,
                        GrownTrunkPlan.Profile.hamlet(), PrecinctCharacter.SUBURB)));
        // The civil-defence compound, on the town's far side from the fields:
        // listed last, so the other places are seeded where they always were.
        if (CivilDefenceCompound.enabled()) {
            places.add(PrecinctBrief.garrison(CivilDefenceCompound.PLACE,
                    fields == MapPlacement.SOUTH ? MapPlacement.NORTH : MapPlacement.SOUTH,
                    CIVIL_DEFENCE_GROWTH, FortressProgram.civilDefence(),
                    new Fortification(CivilDefenceCompound.GATES, CivilDefenceCompound.WALL_HP)
                            .laidSquare()));
        }
        return PrecinctPlan.laidOut(places,
                fields, fields == null ? null : LandingKind.OPEN_FIELD,
                scale.width, scale.height, rng);
    }

    /**
     * The compound's own roads: one junction and short arms, enough to cross
     * its wall into the town's streets and no more. A garrison's hamlet arms
     * run a third of the map, and a programmed place is allowed the road it
     * grows on top of its program, so on those arms the compound claimed a
     * quarter of the town.
     */
    private static final GrownTrunkPlan.Profile CIVIL_DEFENCE_GROWTH =
            new GrownTrunkPlan.Profile(0, 0f, 0.04f, 0.06f, 0f, 4, SettlementLink.NONE);

    /** Whether the colony is laid out with fields to land in; false is the control. */
    static boolean colonyLandsInFields() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.fieldLanding", "true"));
    }

    /**
     * Salt for the fields' side, its own draw so the control's map is the old
     * map. A splittable stream because the first draw of an ordinary
     * {@code Random} barely moves between neighbouring seeds: twelve seeds in
     * a row all put the fields north.
     */
    private static final long FIELD_SIDE_SALT = 0x4649454C44534944L;

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
            // A colony is not an objective, but its buildings are still places the player may order held.
            for (TacticalNode place : HeldPlaces.fromBuildings(map.pointsOfInterest, poi -> Faction.CIVILIAN))
                map.tacticalMap.add(place);
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
            recordLzPads(sim, lzCells);
            for (int i = 0; i < lzCells.size(); i++) {
                ShuttleAssignment assignment =
                        assignments.get(i % assignments.size());
                LandingPad lz = lzCells.get(i);
                float lzCenterX = lz.centerX + 0.5f;
                float lzCenterY = lz.centerY + 0.5f;
                float[] entry = shuttleEntryFor(lzCenterX, lzCenterY,
                        scale.width, scale.height, lz.approach);
                long shuttleId = sim.spawnShuttle(
                        assignment.type, assignment.airframe, Faction.MARINE,
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
                            assignment.seatsForCycle(cycle), marineRng);
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
     * CONQUEST variant: a fortified installation with a settlement around it,
     * grown as places. Map size is the mode-wide
     * {@link #CONQUEST_GRID_W}×{@link #CONQUEST_GRID_H} — {@link MapScale#CONQUEST},
     * a mission's own size rather than a tier's, so it is independent of tier,
     * risk, or host. Marines win only after capturing every defender compound,
     * including the one required central keep ({@code COMMAND_POST}); defenders
     * win by eliminating the marines.
     *
     * <p><b>The axis places the map rather than painting it.</b> A traversal
     * axis is still rolled per-seed — SOUTH_TO_NORTH or WEST_TO_EAST, so two
     * conquest missions on the same world play with a different attacker
     * approach — and everything that plays on the map still keys on it: the
     * three-track layout, the shuttle entry bearing, the garrison system and
     * the reinforcement rear edge. What changed is what is underneath: instead
     * of a biome band painted along that axis, the axis chooses the placement
     * pair the map is derived from, and the objective grows where the mission
     * said the defender is. See {@link #conquestPlanFor}.
     *
     * <p><b>The stock crossroad is still what a Conquest with nothing behind it
     * gets.</b> A headless fixture with no market, or a world with no defences
     * to derive a garrison from, takes the biome recipe exactly as before.
     *
     * <p>Returns the full {@link MapBuild} (sim + the map it was built from +
     * the spawned defense-post structures). Most callers want just the sim —
     * {@link #createConquest} is the thin delegating overload for them. The
     * combat bridge needs the structures list to mirror them as targetable
     * proxies, so it calls this.
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

    /** Conquest build with a fixture-captured mission arrival plan; the market derives the sprawl. */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor,
                                               OperationTier tier, RiskLevel risk,
                                               TargetProfile profile,
                                               FlybyRoster marineFighterSupport,
                                               FlybyRoster enemyFighterSupport,
                                               ShuttleArrivalPlan arrivalPlan) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport, arrivalPlan, null);
    }

    /**
     * Conquest build carrying the battle's stated sprawl.
     *
     * @param sprawl how settled the map is, or {@code null} to derive it from
     *               the target market the way {@code SettlementZoning} does
     */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor,
                                               OperationTier tier, RiskLevel risk,
                                               TargetProfile profile,
                                               FlybyRoster marineFighterSupport,
                                               FlybyRoster enemyFighterSupport,
                                               ShuttleArrivalPlan arrivalPlan,
                                               PrecinctPlan.Sprawl sprawl) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport, arrivalPlan,
                sprawl, null);
    }

    /**
     * Conquest build carrying the battle's stated approach length as well.
     *
     * @param standoff how far from the objective the force lands, or
     *                 {@code null} for Conquest's own default
     */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor,
                                               OperationTier tier, RiskLevel risk,
                                               TargetProfile profile,
                                               FlybyRoster marineFighterSupport,
                                               FlybyRoster enemyFighterSupport,
                                               ShuttleArrivalPlan arrivalPlan,
                                               PrecinctPlan.Sprawl sprawl,
                                               Standoff standoff) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport, arrivalPlan,
                sprawl, standoff, null);
    }

    /**
     * Conquest build carrying the battle's stated lane count as well.
     *
     * @param laneCount how many lanes of resistance run between the beachhead
     *                  and the objective, or {@code null} for one per command
     *                  track. A mission states the count; what stands on each
     *                  lane is derived from the objective's own fortification.
     */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor,
                                               OperationTier tier, RiskLevel risk,
                                               TargetProfile profile,
                                               FlybyRoster marineFighterSupport,
                                               FlybyRoster enemyFighterSupport,
                                               ShuttleArrivalPlan arrivalPlan,
                                               PrecinctPlan.Sprawl sprawl,
                                               Standoff standoff,
                                               Integer laneCount) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport, arrivalPlan,
                sprawl, standoff, laneCount, null);
    }

    /**
     * Conquest build carrying the battle's stated landing kind as well.
     *
     * @param landing what the force comes down on, or {@code null} for the kind
     *                the target world offers. A Conquest always lands on a
     *                place; this says which kind of place.
     */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor,
                                               OperationTier tier, RiskLevel risk,
                                               TargetProfile profile,
                                               FlybyRoster marineFighterSupport,
                                               FlybyRoster enemyFighterSupport,
                                               ShuttleArrivalPlan arrivalPlan,
                                               PrecinctPlan.Sprawl sprawl,
                                               Standoff standoff,
                                               Integer laneCount,
                                               LandingKind landing) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport, arrivalPlan,
                sprawl, standoff, laneCount, landing, null);
    }

    /**
     * Conquest build carrying the routes its lanes take as well.
     *
     * @param lanePaths one {@link LanePath} per lane, in lane order, or
     *                  {@code null} for lanes that derive their own. A mission
     *                  writes a zig-zag here; the count still says how many
     *                  lanes there are, and stating fewer than there are paths
     *                  is refused rather than quietly widened.
     */
    public static MapBuild createConquestBuild(long seed, List<ShuttleAssignment> manifest,
                                               boolean enemyHasHeavyArmor,
                                               OperationTier tier, RiskLevel risk,
                                               TargetProfile profile,
                                               FlybyRoster marineFighterSupport,
                                               FlybyRoster enemyFighterSupport,
                                               ShuttleArrivalPlan arrivalPlan,
                                               PrecinctPlan.Sprawl sprawl,
                                               Standoff standoff,
                                               Integer laneCount,
                                               LandingKind landing,
                                               List<LanePath> lanePaths) {
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
        // overwatch line reflects how fortified the planet is, and so the places
        // the map is made of are derived from the world it is on.
        // The lift is resolved before the map is generated, because the pairs
        // it flies are also the berthing areas the landing place has to seat.
        // See {@link OrbitalLift}.
        ShuttleArrivalPlan requestedArrivalPlan = arrivalPlan != null
                ? arrivalPlan : ShuttleArrivalPlan.legacy();
        List<ShuttleAssignment> committedManifest = resolveManifest(manifest);
        ConquestArrivalConfig sizedArrivalConfig =
                requestedArrivalPlan.sizedConfig();
        ConquestMap generated = conquestMap(gridW, gridH, seed, axis, profile,
                tier, risk, sprawl, standoff, laneCount, landing, lanePaths,
                sizedArrivalConfig.dropZoneCount());
        return populateConquest(generated.map(), generated.seed(), axis, rng,
                committedManifest, requestedArrivalPlan, sizedArrivalConfig,
                enemyHasHeavyArmor, tier, risk, profile, marineFighterSupport,
                enemyFighterSupport, groundRoster, null);
    }

    /**
     * The Conquest mission layer over a finished map: objectives, the marine lift, the defender roster and its
     * allocation, both commanders and the reinforcement layer. The city path generates and then calls this; an
     * authored map (a trial ground) calls it directly. Nothing here depends on how the map was made, only on what
     * it carries: compounds with exactly one COMMAND_POST, landing areas for the lift, and defence posts.
     *
     * @param rng            the build's random source, already past any draws the caller made (the city path draws the
     *                       axis first); every draw here follows in the same order as before the extraction
     * @param defenderRoster an explicit defender composition, kept whole, or {@code null} for the one derived from
     *                       tier and risk
     */
    public static MapBuild populateConquest(MapResult map, long mapSeed, TraversalAxis axis, Random rng,
                                            List<ShuttleAssignment> committedManifest,
                                            ShuttleArrivalPlan requestedArrivalPlan,
                                            ConquestArrivalConfig sizedArrivalConfig,
                                            boolean enemyHasHeavyArmor,
                                            OperationTier tier, RiskLevel risk,
                                            TargetProfile profile,
                                            FlybyRoster marineFighterSupport,
                                            FlybyRoster enemyFighterSupport,
                                            GroundRosterProfile groundRoster,
                                            DefenderRoster defenderRoster) {
        List<Doodad> vehiclePlacements = stampVehicles(map, rng);
        // What the finished map actually seated may be fewer areas than the
        // estimate the landing claim was seeded on asked for; a derived shape
        // folds onto them keeping every pair, a stated one is left to fail.
        ConquestArrivalConfig liftArrivalConfig = OrbitalLift.foldOntoAvailableAreas(
                sizedArrivalConfig, requestedArrivalPlan.arrivalConfig(),
                map.landingAreas.size());
        ShuttleArrivalPlan liftedArrivalPlan =
                requestedArrivalPlan.withArrivalConfig(liftArrivalConfig);
        ShuttleArrivalPlan.ResolvedManifest resolvedManifest =
                liftedArrivalPlan.resolveManifest(committedManifest);
        List<ShuttleAssignment> assignments = resolvedManifest.assignments();
        ShuttleArrivalPlan resolvedArrivalPlan = new ShuttleArrivalPlan(
                liftedArrivalPlan.policy(), resolvedManifest.firstPlayerShuttle(),
                liftArrivalConfig);
        // An explicit roster is a trial's own composition, kept whole: nothing is priced against the attackers.
        DefenderForcePlan defenders = defenderRoster != null
                ? new DefenderForcePlan(defenderRoster, map.defensePosts, enemyFighterSupport)
                : defenderForcePlan(
                MissionType.CONQUEST, tier, risk, enemyHasHeavyArmor,
                assignments, map.defensePosts, marineFighterSupport,
                enemyFighterSupport, groundRoster, garrisonOf(profile));
        // Conquest defense posts come pre-stamped by the generator — the
        // biome-aware DefensePostStamper on the stock recipe (BEACH→PORT→
        // kill-zone tiers + rear ARTILLERY battery), PrecinctDefence's stated
        // loadout on a precinct map — so buildMap consumes map.defensePosts
        // directly. Each post is paired with a manned GUARDPOST squad via
        // {@link #linkGuardpostSquads} below — that's the difference from the
        // non-conquest path, which stamps the same shapes unmanned via
        // {@code DefensePostStamper.stampNonConquest}.
        MapBuild build = buildMap(map, vehiclePlacements, defenders.defensePosts(),
                mapSeed);
        BattleSimulation sim = build.sim();
        sim.setSurface(surfaceOf(profile));
        sim.setGroundRoster(groundRoster);
        sim.setFlybyRoster(defenders.enemyFighterSupport());
        // What the mission let each side's commander call down; a mission that declared none leaves the inert service.
        if (profile != null) sim.setMechDrops(new MechDropService(profile.declaredMechDrops()));
        // The pools of airframes the mission gave each side, tasked through comms operators; none leaves the inert service.
        if (profile != null) sim.setAirPool(new AirPoolService(profile.declaredAirPool()));
        if (profile != null) sim.setSquadSupport(profile.declaredSupportKit());

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

        // The lanes the map laid, for the same reason the pads are handed over:
        // map geometry is consumed at setup and dropped, and a review or a
        // commander re-deriving a route would be a second answer to it.
        sim.setLaneRoutes(map.lanes);

        List<ConquestArrivalSlot> arrivalSlots = conquestArrivalSlots(
                map, assignments, axis, rng, resolvedArrivalPlan);
        recordLzPads(sim, arrivalSlots.stream()
                .map(ConquestArrivalSlot::pad)
                .toList());
        SquadSupportKit supportKit = profile != null
                ? profile.declaredSupportKit().forSide(Faction.MARINE) : SquadSupportKit.NONE;
        int squadOrdinal = 0;
        for (int i = 0; i < arrivalSlots.size(); i++) {
            ShuttleAssignment a = assignments.get(i % assignments.size());
            ConquestArrivalSlot slot = arrivalSlots.get(i);
            float lzCenterX = slot.pad().centerX + 0.5f;
            float lzCenterY = slot.pad().centerY + 0.5f;
            float[] entry = marineArrivalEntry(lzCenterX, lzCenterY, map,
                    map.grid.getWidth(), map.grid.getHeight(), axis);
            long shuttleId = sim.spawnShuttle(
                    resolvedArrivalPlan.deliveryCraft(a.type),
                    deliveryFrame(resolvedArrivalPlan, a), Faction.MARINE,
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
                // The mission's support kit (mortar teams, forward observers) is issued by squad ordinal across the force.
                cycleLoadouts[c] = InfantryLoadoutRolls.playerSquad(
                        a.seatsForCycle(c), rng, supportKit.mortarTeam(squadOrdinal), supportKit.observer(squadOrdinal));
                SupportKitIssue.issueBreachKit(cycleLoadouts[c], supportKit, squadOrdinal);
                squadOrdinal++;
            }
            mission.cycleLoadouts = cycleLoadouts;
            mission.marineLoadout = cycleLoadouts[0];
            equipDefaultTurrets(sim, shuttleId);
        }

        allocateDefenders(sim, map, defenders.roster(), groundRoster, rng);
        linkGuardpostSquads(sim, defenders.defensePosts());
        // A mission that declares a shield generator gets one beside the keep, held
        // by the garrison. Whether a mission declares one is its own business (see
        // MissionDefencePolicy); this only honours the declaration.
        if (profile != null && profile.declaredDefences().shieldGenerator()) {
            ShieldGeneratorPlacement.nearKeep(sim, Faction.DEFENDER);
        }
        claimConquestSetupGarrisons(sim);
        spawnAmbientCivilians(sim, map, rng);
        // Both Conquest commanders share one physical three-track layout but
        // retain separate, faction-honest influence pictures and policies.
        ConquestTrackLayout tracks = new ConquestTrackLayout(
                axis, map.grid.getWidth(), map.grid.getHeight());
        ConquestCommand marineCommand = new ConquestCommand(tracks, map.lanes)
                .withAssaultStyle(profile == null ? AssaultStyle.MUSTER : profile.declaredAssault());
        sim.setAutonomousCommander(Faction.MARINE, marineCommand,
                ConquestCommandDisclosure.INSTANCE);
        // A DEBUG-stated call lands claimed by this commander, at the middle of the drop zone it came down on.
        sim.mechDrops().installScriptedCaller(Faction.MARINE,
                SquadCommandClaim.mission(marineCommand.strategyId(), "commander-called mech lance"),
                () -> MechDropService.centroidOf(sim.getMarineLandingPads()));
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
        return new MapBuild(sim, map, build.structures());
    }

    /**
     * The frame a Conquest arrival actually descends in.
     *
     * <p>A paired Conquest arrival substitutes a delivery craft for whatever
     * the company committed, and a fit belongs to a particular boat rather than
     * to whatever ends up flying in its place. So the fitted frame is used only
     * when the substitution is the identity one — the delivery pattern is the
     * assignment's own — and otherwise the delivery type flies plain. A
     * Valkyrie's armoured plate is not transferable to the Aeroshuttle that
     * goes down instead of her.
     */
    private static Airframe deliveryFrame(ShuttleArrivalPlan plan, ShuttleAssignment a) {
        ShuttleType delivery = plan.deliveryCraft(a.type);
        return delivery == a.type ? a.airframe : delivery;
    }

    /**
     * Where a Conquest marine arrival comes from and goes back to.
     *
     * <p>Off orbit: one point {@link OrbitalLift#DESCENT_CELLS} off the berth
     * on the side away from the objective, entered and left at the same place,
     * so the leg is the descent and the round trip is a constant. On a map that
     * gives no bearing to be away from it is the crossing of the map edge
     * nearest the force.
     */
    private static float[] marineArrivalEntry(float lzCenterX, float lzCenterY,
                                              MapResult map, int gridW, int gridH,
                                              TraversalAxis axis) {
        AirCorridor descent = AirCorridor.descent("orbit", lzCenterX, lzCenterY,
                map.defenderSpawnX + 0.5f, map.defenderSpawnY + 0.5f,
                OrbitalLift.DESCENT_CELLS);
        if (descent != null) {
            return new float[]{descent.entryX, descent.entryY,
                    descent.exitX, descent.exitY};
        }
        return shuttleEntryFor(lzCenterX, lzCenterY, gridW, gridH, axis);
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
                expected += assignments.get(member).seatsForCycle(0);
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

    /**
     * The defended market's own troops, on the company's side. The one seam both
     * defence missions reach it through, and inert for every mission that named
     * no allied faction — which is every other mission.
     *
     * <p>Its rng is salted off the battle seed rather than sharing the setup
     * stream, so a battle that gains or loses an allied garrison still rolls the
     * same defenders.
     */
    private static void installAlliedGarrison(BattleSimulation sim, MapResult map,
                                              TargetProfile profile,
                                              String alliedGarrisonFactionId,
                                              float alliedGarrisonStrengthMult,
                                              RiskLevel risk, long seed) {
        if (alliedGarrisonFactionId == null) return;
        int squads = AlliedGarrisonSize.squads(profile, alliedGarrisonStrengthMult);
        if (squads <= 0) return;
        AlliedGarrison.install(sim, map,
                GroundRosterRegistry.resolve(alliedGarrisonFactionId), squads,
                garrisonOf(profile).quality(), risk,
                new Random(seed ^ ALLIED_GARRISON_SEED_SALT));
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

    /** The target market's garrison estimate, or none when no market backs the battle. */
    private static MarketGarrison garrisonOf(TargetProfile profile) {
        return profile != null ? profile.garrison() : MarketGarrison.NONE;
    }

    private static DefenderForcePlan defenderForcePlan(
            MissionType type, OperationTier tier, RiskLevel risk,
            boolean enemyHasHeavyArmor, List<ShuttleAssignment> assignments,
            List<DefensePost> defensePosts, FlybyRoster marineFighterSupport,
            FlybyRoster enemyFighterSupport, GroundRosterProfile groundRoster,
            MarketGarrison garrison) {
        // Conquest is an authored late-game set piece, not an encounter that
        // softens itself to match the committed detachment. Its population,
        // mech groups, fighter wings, and fortifications all survive intact
        // however much or little force the campaign player can afford to bring.
        float attackerScore = type == MissionType.CONQUEST
                ? Float.POSITIVE_INFINITY
                : BattleForceScore.attackers(assignments, marineFighterSupport);
        DefenderRoster roster = DefenderRoster.forMission(
                type, tier, risk, enemyHasHeavyArmor, attackerScore, groundRoster, garrison);
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
     * Tier-aware Conquest carrying the battle's stated sprawl; {@code null}
     * derives it from the target market. See
     * {@link #createConquestBuild(long, List, boolean, OperationTier, RiskLevel,
     * TargetProfile, FlybyRoster, FlybyRoster, ShuttleArrivalPlan,
     * PrecinctPlan.Sprawl)}.
     */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport,
                                                  ShuttleArrivalPlan arrivalPlan,
                                                  PrecinctPlan.Sprawl sprawl) {
        return createConquest(seed, manifest, enemyHasHeavyArmor, tier, risk,
                profile, marineFighterSupport, enemyFighterSupport, arrivalPlan,
                sprawl, null);
    }

    /**
     * Tier-aware Conquest carrying both of the battle's own map statements;
     * {@code null} takes the derived sprawl and Conquest's default standoff.
     */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport,
                                                  ShuttleArrivalPlan arrivalPlan,
                                                  PrecinctPlan.Sprawl sprawl,
                                                  Standoff standoff) {
        return createConquest(seed, manifest, enemyHasHeavyArmor, tier, risk, profile,
                marineFighterSupport, enemyFighterSupport, arrivalPlan, sprawl,
                standoff, null);
    }

    /**
     * Tier-aware Conquest carrying all three of the battle's own map
     * statements; {@code null} takes the derived sprawl, Conquest's default
     * standoff and one lane per command track.
     */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport,
                                                  ShuttleArrivalPlan arrivalPlan,
                                                  PrecinctPlan.Sprawl sprawl,
                                                  Standoff standoff,
                                                  Integer laneCount) {
        return createConquest(seed, manifest, enemyHasHeavyArmor, tier, risk, profile,
                marineFighterSupport, enemyFighterSupport, arrivalPlan, sprawl,
                standoff, laneCount, null);
    }

    /**
     * Tier-aware Conquest carrying all four of the battle's own map statements;
     * {@code null} takes the derived sprawl, Conquest's default standoff, one
     * lane per command track, and the landing kind the world offers.
     */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport,
                                                  ShuttleArrivalPlan arrivalPlan,
                                                  PrecinctPlan.Sprawl sprawl,
                                                  Standoff standoff,
                                                  Integer laneCount,
                                                  LandingKind landing) {
        return createConquest(seed, manifest, enemyHasHeavyArmor, tier, risk, profile,
                marineFighterSupport, enemyFighterSupport, arrivalPlan, sprawl,
                standoff, laneCount, landing, null);
    }

    /**
     * Tier-aware Conquest carrying the routes its lanes take as well.
     *
     * @param lanePaths one route per lane, in lane order, or {@code null} for
     *                  lanes that derive their own
     */
    public static BattleSimulation createConquest(long seed, List<ShuttleAssignment> manifest,
                                                  boolean enemyHasHeavyArmor,
                                                  OperationTier tier, RiskLevel risk,
                                                  TargetProfile profile,
                                                  FlybyRoster marineFighterSupport,
                                                  FlybyRoster enemyFighterSupport,
                                                  ShuttleArrivalPlan arrivalPlan,
                                                  PrecinctPlan.Sprawl sprawl,
                                                  Standoff standoff,
                                                  Integer laneCount,
                                                  LandingKind landing,
                                                  List<LanePath> lanePaths) {
        return createConquestBuild(seed, manifest, enemyHasHeavyArmor,
                tier, risk, profile, marineFighterSupport,
                enemyFighterSupport, arrivalPlan, sprawl, standoff, laneCount,
                landing, lanePaths).sim();
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
    /**
     * The lanes this map laid, read as chains of places, for the defender's
     * reinforcement layer.
     *
     * <p>Built from the same recorded routes the marine commander is handed and
     * the same compound records the capture rule runs on, so the two sides
     * agree about what stands where without sharing anything either of them
     * believes.
     */
    private static ConquestLaneChain conquestLaneChain(BattleSimulation sim,
                                                       MapResult map) {
        CompoundService compounds = sim.getCompoundService();
        if (compounds == null || map.lanes.isEmpty()) return ConquestLaneChain.NONE;
        List<ConquestLaneChain.Compound> places = new ArrayList<>();
        for (CompoundService.Record record : compounds.getRecords()) {
            int captureZone = compounds.captureZoneId(record, sim);
            // The same guard the marine commander's own chain applies. A
            // compound with no resolved capture room is not territory either
            // side can read, and letting -1 onto a link pins that lane's front
            // on a place that can never be held.
            if (captureZone < 0) continue;
            places.add(new ConquestLaneChain.Compound(captureZone,
                    record.node.anchorX, record.node.anchorY));
        }
        return ConquestLaneChain.of(map.lanes, places);
    }

    private static void installReinforcementLayer(BattleSimulation sim, MapResult map,
                                                  MissionType missionType,
                                                  TraversalAxis axis,
                                                  GroundRosterProfile groundRoster,
                                                  RiskLevel risk,
                                                  DeliveryDeploymentPolicy deliveryPolicy) {
        ReinforcementService rs = sim.getReinforcementService();
        if (missionType == MissionType.CONQUEST && map.frontDepth != null
                && map.tacticalMap != null && map.tacticalMap.size() > 0) {
            RecaptureTargetService recaptureTargets =
                    new RecaptureTargetService(map.tacticalMap, map.frontDepth,
                            conquestLaneChain(sim, map));
            sim.setRecaptureSystem(new RecaptureTargetSystem(recaptureTargets, map.frontDepth));
            rs.addTrigger(new FrontLineReinforcementTrigger(recaptureTargets, map.frontDepth));
            sim.setCounterattackSystem(new CounterattackSystem(
                    recaptureTargets, rs, sim.getBattleResources(), map.frontDepth));
        } else {
            rs.addTrigger(new GarrisonDepletedTrigger());
        }
        if (missionType != MissionType.ASSAULT) {
            rs.addTrigger(new ObjectiveLostTrigger());
        }
        basedAircraft(sim, map, groundRoster == null ? null : groundRoster.primaryFactionId());
        // Opened before anything is manned, because a manning pass records its
        // postings here and a posting outlives the people filling it.
        sim.setWorksCrews(new WorksCrewService(axis));
        installAirfieldCrew(sim, map);
        installVehicleBays(sim, map, groundRoster);
        rs.addMeans(new ConvoyMeans(map.roadGraph, axis, groundRoster, risk,
                deliveryPolicy));
        rs.addMeans(new ShuttleMeans(axis, groundRoster, risk,
                deliveryPolicy, map.landingPads));
        rs.addMeans(new WalkInMeans(axis, groundRoster, risk));
    }

    /** A generated conquest map and the seed that actually produced it. */
    private record ConquestMap(MapResult map, long seed) { }

    /**
     * What the mission's two lane statements come to.
     *
     * <p><b>Zero is a statement rather than the absence of one</b>: it asks for
     * the map lanes replaced — three tracks arriving at one wall with open city
     * behind them — which is the control a balance run is read against, and it
     * outranks any route somebody wrote, because a lane that does not exist has
     * nowhere to go.
     *
     * <p>An unstated count with stated routes lays one lane per route where
     * that is more than the default, so a mission may write three zig-zags and
     * say nothing else. A count smaller than the number of routes is refused by
     * {@link PrecinctPlan.Lanes} rather than widened here: quietly laying a
     * fourth lane a mission did not ask for is worse than saying so.
     */
    private static PrecinctPlan.Lanes lanesFor(Integer laneCount, List<LanePath> paths) {
        if (laneCount != null && laneCount <= 0) return null;
        List<LanePath> stated = paths == null ? List.of() : paths;
        if (stated.isEmpty()) {
            return laneCount == null
                    ? PrecinctPlan.Lanes.derived() : PrecinctPlan.Lanes.of(laneCount);
        }
        int count = laneCount != null ? laneCount
                : Math.max(PrecinctPlan.Lanes.DEFAULT_COUNT, stated.size());
        return new PrecinctPlan.Lanes(count, List.of(), stated);
    }

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
                                           TraversalAxis axis, TargetProfile profile,
                                           OperationTier tier, RiskLevel risk,
                                           PrecinctPlan.Sprawl sprawl,
                                           Standoff standoff, Integer laneCount,
                                           LandingKind landing,
                                           List<LanePath> lanePaths) {
        return conquestMap(gridW, gridH, seed, axis, profile, tier, risk, sprawl,
                standoff, laneCount, landing, lanePaths,
                LandingKind.AREAS_THE_APRON_SEATS);
    }

    /**
     * The Conquest map, told how many arrival areas the lift needs berthing
     * room for. See {@link OrbitalLift}.
     */
    private static ConquestMap conquestMap(int gridW, int gridH, long seed,
                                           TraversalAxis axis, TargetProfile profile,
                                           OperationTier tier, RiskLevel risk,
                                           PrecinctPlan.Sprawl sprawl,
                                           Standoff standoff, Integer laneCount,
                                           LandingKind landing,
                                           List<LanePath> lanePaths,
                                           int arrivalAreas) {
        EnumSet<MapFeature> missing = EnumSet.noneOf(MapFeature.class);
        PrecinctPlan.Lanes lanes = lanesFor(laneCount, lanePaths);
        for (int attempt = 0; attempt < CONQUEST_MAP_ATTEMPTS; attempt++) {
            long mapSeed = seed + attempt * 0x9E3779B97F4A7C15L;
            // Derived per attempt rather than once, because the plan is half of
            // what the seed decides: a re-roll that kept the same places would
            // be the same map filled differently, which is not another map.
            PrecinctPlan plan = conquestPlanFor(tier, risk, profile, sprawl, standoff,
                    lanes, landing, arrivalAreas, axis, mapSeed);
            // A plan and an axis are two different maps and the generator
            // refuses both, so the axis rides in only when there is no plan —
            // a marketless or undefended Conquest keeps the stock crossroad.
            MapResult map = plan != null
                    ? MAP_GEN.generate(gridW, gridH, mapSeed, null, profile, plan)
                    : MAP_GEN.generate(gridW, gridH, mapSeed, axis, profile);
            missing = MissionMapRequirements.missingFrom(MissionType.CONQUEST, map);
            if (missing.isEmpty()) return new ConquestMap(map, mapSeed);
        }
        throw new IllegalStateException(MissionMapRequirements.describeFailure(
                MissionType.CONQUEST, seed, CONQUEST_MAP_ATTEMPTS, missing));
    }

    /**
     * How many of one trade stand a watch in one vehicle bay.
     *
     * <p>A shed's crew, not the room's capacity. A rank of six berths worked
     * from five positions each is thirty places to weld, and filling all of them
     * would put a platoon of unarmed engineers in a building a garrison holds
     * with three riflemen. What a motor pool has is a handful of people and a
     * great deal of work waiting on them.
     */
    private static final int BAY_WATCH = 3;

    /**
     * How many technicians stand a watch on one airfield.
     *
     * <p>A ground crew, and a real one. Three was a rounding error — one person
     * per two aircraft, on a rotation that also has them walking between the
     * stands — set to avoid filling all twelve of the apron's servicing
     * positions on the grounds that a platoon of unarmed engineers would swamp a
     * field held by a handful of riflemen. The garrison is not a handful: these
     * maps field ninety to a hundred and fifty defenders, so the thing that
     * argument was protecting against was never the risk.
     *
     * <p>Still short of the apron's capacity, because a field is meant to be
     * short-handed — what an aircraft waits for is its turn, and a field that
     * could work every stand at once would turn its whole establishment round
     * as fast as one aircraft.
     */
    private static final int APRON_WATCH = 8;

    /**
     * Stand a ground crew on the airfield's apron.
     *
     * <p>The same trade that works a motor pool, because it is the same work: a
     * machine standing in a berth with somebody servicing it. What the field
     * offers is that servicing, at both flanks of every stand, and the board
     * ahead of each nose that its state is read off — a rotation of two jobs
     * across every aircraft on the field.
     *
     * <p>Installed after the berths, and it has to be. The work names which
     * berth it services by index, so there is nothing to publish until the
     * berths exist; and a berth's servicing is work only while something is
     * standing on it, which is a question only the field can answer.
     *
     * <p>The crew is the defender's. A garrison's field is worked by the
     * garrison, so its technicians are on the roster, can be shot on the apron,
     * and stop working when they are.
     */
    private static void installAirfieldCrew(BattleSimulation sim, MapResult map) {
        AirfieldService field = sim.getAirfieldService();
        if (field.berths().isEmpty()) return;

        List<FixtureTask> apron = AirfieldWork.onTheApron(field, map.grid);
        if (apron.isEmpty()) return;
        // The field is told where its servicing is done from in the same breath,
        // so the work somebody is doing can be matched to the aircraft it is
        // being done to without either side deriving the other's indices.
        field.installApronWork(apron);

        List<RoomSite> rooms = RoomSite.findAll(map.topology,
                map.grid.getWidth(), map.grid.getHeight());
        StructureWatch.man(sim, Faction.DEFENDER, rooms, apron,
                AirfieldWork.occupied(field), EnumSet.of(RoomPurpose.HANGAR), APRON_WATCH,
                sim.getWorksCrews());
    }

    /**
     * Open the works in every vehicle bay the map has, and stand a crew in it.
     *
     * <p>A vehicle bay is the only structure on a battle map that <em>makes</em>
     * something. Everything else supplies, garrisons or shelters; this one has a
     * machine on the stocks and people working it up, and what comes out is an
     * ordinary unit. Both halves are installed together on purpose, because
     * neither is anything on its own: a shed with no crew builds nothing, and a
     * crew with nothing in the berths has no trade there and is not posted at
     * all.
     *
     * <p>The crew is the defender's. A garrison's motor pool is worked by the
     * garrison, so its technicians are on the roster, can be shot, and stop
     * working when they are.
     *
     * @param groundRoster the defending faction's doctrine, which decides what
     *                     the sheds are tooled for and what they hang on it;
     *                     null builds the whole light catalog on a neutral fit
     */
    private static void installVehicleBays(BattleSimulation sim, MapResult map,
                                           GroundRosterProfile groundRoster) {
        if (map.gantries.isEmpty()) return;
        List<RoomSite> rooms = RoomSite.findAll(map.topology,
                map.grid.getWidth(), map.grid.getHeight());
        FabricationService works = new FabricationService(map.gantries, map.fixtureTasks,
                rooms, groundRoster);
        if (works.isEmpty()) return;

        sim.setFabrication(works);
        StructureWatch.man(sim, Faction.DEFENDER, rooms, map.fixtureTasks,
                works.berthed(), EnumSet.of(RoomPurpose.VEHICLE_BAY), BAY_WATCH,
                sim.getWorksCrews());
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
        Runway strip = map.runways.get(0);
        sim.getAirfieldService().installRunway(strip);
        // Aircraft in the sheds, but only in the sheds of the field that owns
        // the strip. A shelter berth anywhere else is an aircraft that cannot
        // lift off where it stands and has nothing within reach to roll on:
        // measured on conquest maps, three seeds in four put a shed a hundred
        // and ten to a hundred and thirty cells from the only runway, and the
        // fighter kept in it taxied for the city. "The map has a strip" was
        // never the question this asks — "this shed has one" is.
        List<Gantry> sheds = shedsOnTheFieldWith(strip, map.tacticalMap, map.shelters);
        List<FighterProfile> based = shedAircraft(
                FighterProfile.poolForFaction(factionId), sheds);
        for (int i = 0; i < sheds.size(); i++) {
            sim.getAirfieldService().addShelterBerth(sheds.get(i), based.get(i));
        }
    }

    /**
     * The sheds standing on the same airfield as {@code strip}.
     *
     * <p>An airfield is the ground its {@code AIRBASE} node covers, which is
     * the only handle on the map that says where one base stops and the next
     * begins — a shed and a runway are both geometry and neither knows what lot
     * laid it. Both come out of the same lot, so a strip always falls inside
     * exactly one of them; a strip inside none is a map nobody has built yet,
     * and basing nothing in its sheds leaves the field flying off its apron
     * rather than taxiing an aircraft to a runway on the far side of a city.
     */
    static List<Gantry> shedsOnTheFieldWith(Runway strip, TacticalMap fields,
                                            List<Gantry> shelters) {
        for (TacticalNode node : fields.all()) {
            if (node.kind != TacticalNode.Kind.AIRBASE) continue;
            if (!within(node, strip.centreX(), strip.centreY())) continue;
            List<Gantry> sheds = new ArrayList<>();
            for (Gantry shed : shelters) {
                if (within(node, shed.centerX, shed.centerY)) sheds.add(shed);
            }
            return sheds;
        }
        return List.of();
    }

    private static boolean within(TacticalNode node, float x, float y) {
        return x >= node.left && x <= node.right && y >= node.top && y <= node.bottom;
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

    /** Spawns intentionally low-grade four-person militia squads for either side. */
    private static void spawnOpeningMilitiaSquads(
            BattleSimulation sim, List<int[]> cells, Faction faction,
            String idPrefix, TacticalNode post, Random rng) {
        spawnOpeningMilitiaSquads(sim, cells, faction, idPrefix, post, rng, OPENING_FIRETEAM_SIZE);
    }

    private static void spawnOpeningMilitiaSquads(
            BattleSimulation sim, List<int[]> cells, Faction faction,
            String idPrefix, TacticalNode post, Random rng, int fireteamSize) {
        spawnOpeningMilitiaSquads(sim, cells, faction, idPrefix, post, rng, fireteamSize, null);
    }

    private static void spawnOpeningMilitiaSquads(
            BattleSimulation sim, List<int[]> cells, Faction faction,
            String idPrefix, TacticalNode post, Random rng, int fireteamSize, WeaponDef primary) {
        boolean localGarrison = post != null;
        Squad squad = null;
        int squadMembers = 0;
        int squadIndex = -1;
        for (int index = 0; index < cells.size(); index++) {
            int[] cell = cells.get(index);
            if (squad == null || squadMembers >= fireteamSize) {
                if (squad != null) squad.originalSize = squadMembers;
                squadIndex++;
                squadMembers = 0;
                int squadId = sim.mintSquad(faction, UnitType.MILITIA);
                squad = sim.getSquad(squadId);
                if (localGarrison) {
                    squad.assignedNode = post;
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
            if (primary != null) unit.primaryWeapon(primary, unit.equipmentGrade, unit.soldierProfile);
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

    static TacticalNode openingDefenseNode(
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
        DefenderObserverIssue observers = DefenderObserverIssue.of(sim);
        TacticalMap tactical = map.tacticalMap;
        List<TacticalNode> defenderNodes = (tactical != null)
                ? new ArrayList<>(tactical.forFaction(Faction.DEFENDER))
                : Collections.emptyList();
        // A held place nobody is posted to is ground the player may order held, not somewhere the defence lives.
        defenderNodes.removeIf(HeldPlaces::isUnposted);
        if (defenderNodes.isEmpty()) {
            List<int[]> cells = pickDefensiveCluster(map.grid, map.defenderSpawnX, map.defenderSpawnY,
                    roster.mechVariants.isEmpty() ? roster.totalCount
                            : (2 * DEFENDER_SPAWN_SCAN_RADIUS + 1) * (2 * DEFENDER_SPAWN_SCAN_RADIUS + 1),
                    roster.mechVariants.isEmpty());
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
        infQueue.addAll(roster.infantryOrder());

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
            if (spawningMechs) {
                List<int[]> candidates = mechNodeCandidates(sim, node, GARRISON_SPAWN_RADIUS);
                int spawned = spawnPlacedDefenderMechs(sim, candidates, mechQueue, want,
                        roster, groundRoster, rng, node, UnitRole.GARRISON, defenderIdx);
                defenderIdx += spawned;
                if (spawned == 0) patrolAnchors.add(node);
                continue;
            }
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
                observers.issue(unit, mechVariant == null, spawned == 0);
                unit.squad(squad.id);
                long member = sim.spawn(unit);
                attachMechLoadout(sim, member, mechVariant, groundRoster);
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
        List<TacticalNode> originalAnchors = new ArrayList<>(
                patrolAnchors.isEmpty() ? defenderNodes : patrolAnchors);
        List<TacticalNode> anchorPool = new ArrayList<>(originalAnchors);
        int anchorIdx = 0;
        while (!mechQueue.isEmpty() || !infQueue.isEmpty()) {
            if (anchorPool.isEmpty()) break;
            boolean spawningMechs = !mechQueue.isEmpty();
            TacticalNode anchor = anchorPool.get(anchorIdx % anchorPool.size());
            anchorIdx++;
            int sourceSize = spawningMechs ? mechQueue.size() : infQueue.size();
            int want = Math.min(roster.patrolSquadSize, sourceSize);
            if (spawningMechs) {
                List<int[]> candidates = mechNodeCandidates(sim, anchor, GARRISON_SPAWN_RADIUS + 2);
                int spawned = spawnPlacedDefenderMechs(sim, candidates, mechQueue, want,
                        roster, groundRoster, rng, anchor, UnitRole.PATROL, defenderIdx);
                defenderIdx += spawned;
                if (spawned == 0) {
                    anchorPool.remove(anchor);
                    anchorIdx = 0;
                    if (anchorPool.isEmpty()) {
                        // This exact chassis found no legal room at any permitted anchor.
                        // Omit only it, then let smaller chassis and the infantry use their rooms.
                        mechQueue.poll();
                        anchorPool.addAll(originalAnchors);
                    }
                }
                continue;
            }
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
                observers.issue(unit, mechVariant == null, spawned == 0);
                unit.squad(squad.id);
                long member = sim.spawn(unit);
                attachMechLoadout(sim, member, mechVariant, groundRoster);
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
        DefenderObserverIssue observers = DefenderObserverIssue.of(sim);
        // Same mech-vs-infantry split as allocateDefenders — each squad
        // drains from a single source queue so mechs and infantry never
        // share membership.
        UnitType mechType = UnitType.HEAVY_MECH;
        Deque<MechVariant> mechQueue = new ArrayDeque<>(roster.mechVariants);
        Deque<GroundRosterProfile.ForceTier> infQueue = new ArrayDeque<>();
        infQueue.addAll(roster.infantryOrder());

        int defenderIdx = 0;
        int cellIdx = 0;
        while (!mechQueue.isEmpty()) {
            int spawned = spawnPlacedDefenderMechs(sim, cells, mechQueue,
                    roster.patrolSquadSize, roster, groundRoster, rng, null,
                    UnitRole.PATROL, defenderIdx);
            defenderIdx += spawned;
            if (spawned == 0) mechQueue.poll();
        }
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
                observers.issue(unit, mechVariant == null, spawned == 0);
                unit.squad(squad.id);
                long member = sim.spawn(unit);
                attachMechLoadout(sim, member, mechVariant, groundRoster);
                spawned++;
            }
            if (squad != null) squad.originalSize = spawned;
        }
    }

    private static List<int[]> mechNodeCandidates(BattleSimulation sim, TacticalNode node,
                                                   int radius) {
        int count = (2 * radius + 1) * (2 * radius + 1) + node.standPositions().size();
        return pickCellsForNode(sim.getGrid(), sim.getZoneGraph(), node, radius, count);
    }

    /** Candidate membership retains the authored node/zone envelope; nothing snaps across rooms. */
    private static int spawnPlacedDefenderMechs(BattleSimulation sim, List<int[]> candidates,
                                                Deque<MechVariant> queue, int count,
                                                DefenderRoster roster, GroundRosterProfile groundRoster,
                                                Random rng, TacticalNode node, UnitRole role, int nameIndex) {
        Set<Long> domain = new HashSet<>();
        for (int[] cell : candidates) domain.add(key(cell[0], cell[1]));
        Squad squad = null;
        int spawned = 0;
        while (spawned < count && !queue.isEmpty()) {
            MechVariant variant = queue.peek();
            MechSpawnPlacement.Point point = null;
            for (int[] cell : candidates) {
                point = MechSpawnPlacement.nearCell(sim.getGrid(), variant.radius, cell[0], cell[1],
                        (x, y) -> domain.contains(key((int) Math.floor(x), (int) Math.floor(y))),
                        (x, y) -> MechSpawnPlacement.unoccupied(sim.getRoster(), x, y, variant.radius, 0L));
                if (point != null) break;
            }
            if (point == null) break;
            queue.poll();
            EntitySpec unit = makeDefender("d" + (nameIndex + spawned), UnitType.HEAVY_MECH,
                    point.cellX(), point.cellY(), roster.risk, groundRoster, null, rng, variant)
                    .atPosition(point.x(), point.y()).role(role);
            if (role == UnitRole.GARRISON) unit.home(point.cellX(), point.cellY());
            if (squad == null) {
                squad = sim.getSquad(sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH));
                squad.assignedNode = node;
                squad.holdsFireUntilKillZone = false;
            }
            unit.squad(squad.id);
            long member = sim.spawn(unit);
            attachMechLoadout(sim, member, variant, groundRoster);
            spawned++;
        }
        if (squad != null) squad.originalSize = spawned;
        return spawned;
    }

    /** Builds a bare defender {@code Entity}. Mech loadout (for mech types) is a
     * presence component attached <em>after</em> the unit is added to the sim —
     * see {@link #attachMechLoadout} — because the loadout store is keyed by the
     * entity id, which isn't assigned until {@code addUnit}. */
    private static EntitySpec makeDefender(String id, UnitType type, int x, int y,
                                           RiskLevel risk, GroundRosterProfile groundRoster,
                                           GroundRosterProfile.ForceTier forceTier,
                                           Random rng, MechVariant mechVariant) {
        return makeDefender(id, Faction.DEFENDER, type, x, y, risk, groundRoster,
                forceTier, rng, mechVariant);
    }

    /**
     * The same kit under a stated side. A faction's ground doctrine says what its
     * troops carry and nothing about who they are shooting at, so the allied
     * garrison of a defended market is issued through this rather than through a
     * second kitting path that would drift from the defender's.
     */
    static EntitySpec makeDefender(String id, Faction faction, UnitType type, int x, int y,
                                   RiskLevel risk, GroundRosterProfile groundRoster,
                                   GroundRosterProfile.ForceTier forceTier,
                                   Random rng, MechVariant mechVariant) {
        EntitySpec unit = new EntitySpec(id, faction, type, x, y);
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
    private static void attachMechLoadout(BattleSimulation sim, long unit,
                                          MechVariant mechVariant,
                                          GroundRosterProfile groundRoster) {
        if (mechVariant != null) {
            sim.world().attachMechLoadout(unit,
                    FactionMechLoadouts.create(mechVariant, mechVariant.defaultRole,
                            groundRoster.primaryFactionId()));
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
        spawnAmbientCivilians(sim, map.grid, residential, rng);
    }

    /** {@link #spawnAmbientCivilians(BattleSimulation, MapResult, Random)} round the homes an authored map names. */
    private static void spawnAmbientCivilians(BattleSimulation sim, NavigationGrid grid,
                                              List<PointOfInterest> residential, Random rng) {
        if (residential.isEmpty()) return;
        UnitType[] roles = { UnitType.CIVILIAN, UnitType.ENGINEER, UnitType.SCIENTIST };
        Set<Long> claimed = new HashSet<>();
        int spawned = 0;
        int attempts = 0;
        while (spawned < AMBIENT_CIVILIAN_COUNT && attempts < AMBIENT_CIVILIAN_COUNT * 8) {
            attempts++;
            PointOfInterest poi = residential.get(rng.nextInt(residential.size()));
            int[] cell = findCivilianCell(grid, poi.anchorCellX, poi.anchorCellY, claimed, rng);
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
    static List<int[]> pickDefensiveCluster(NavigationGrid grid, int cx, int cy, int count) {
        return pickDefensiveCluster(grid, cx, cy, count, true);
    }

    private static List<int[]> pickDefensiveCluster(NavigationGrid grid, int cx, int cy,
                                                    int count, boolean backfill) {
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
        if (backfill && picked.size() < count) {
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
     * {@link PlacementGuards#wouldStrandGround connectivity} is checked
     * so the truck can't sever a thin walkable strip from the main graph. That
     * guard asks whether <em>this footprint</em> strands ground rather than
     * whether any ground anywhere is stranded; the map-wide form refuses every
     * anchor on a map that carries an orphan pocket, which parks no vehicles at
     * all and says nothing about it.
     */
    static List<Doodad> stampVehicles(MapResult map, Random rng) {
        return stampVehicles(map, rng, null);
    }

    /** {@link #stampVehicles(MapResult, Random)} parking nothing on the reserved cells. */
    static List<Doodad> stampVehicles(MapResult map, Random rng, boolean[][] reserved) {
        NavigationGrid grid = map.grid;
        CellTopology topology = map.topology;
        // A parked truck is cover, and a field promised to stay clear of cover
        // does not get one on the track across it.
        if (map.fieldOfFire != null) {
            boolean[][] field = new boolean[grid.getWidth()][grid.getHeight()];
            for (int x = 0; x < grid.getWidth(); x++) {
                for (int y = 0; y < grid.getHeight(); y++) {
                    field[x][y] = (reserved != null && reserved[x][y])
                            || map.fieldOfFire.contains(x, y);
                }
            }
            reserved = field;
        }
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
            int[] anchor = findServiceVehicleAnchor(map, pad, kind, reserved);
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
            if (!canPlaceVehicle(map, x, y, kind, false, reserved)) continue;
            if (PlacementGuards.wouldStrandGround(
                    grid, x, y, kind.footprintCellsX, kind.footprintCellsY)) continue;
            placed.add(stampOneVehicle(grid, topology, x, y, kind));
        }
        return placed;
    }

    private static int[] findServiceVehicleAnchor(MapResult map, LandingPad pad,
                                                   DoodadDef kind,
                                                   boolean[][] reserved) {
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
            if (!canPlaceVehicle(map, candidateX, candidateY, kind, true, reserved)) continue;
            if (PlacementGuards.wouldStrandGround(map.grid, candidateX, candidateY,
                    kind.footprintCellsX, kind.footprintCellsY)) continue;
            return new int[]{candidateX, candidateY};
        }
        return null;
    }

    private static boolean canPlaceVehicle(MapResult map, int x, int y,
                                           DoodadDef kind, boolean allowApron,
                                           boolean[][] reserved) {
        NavigationGrid grid = map.grid;
        CellTopology topology = map.topology;
        for (int dy = 0; dy < kind.footprintCellsY; dy++) {
            for (int dx = 0; dx < kind.footprintCellsX; dx++) {
                int cx = x + dx;
                int cy = y + dy;
                if (!grid.inBounds(cx, cy)) return false;
                if (reserved != null && reserved[cx][cy]) return false;
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
                String name = "t" + i++;
                long turret = sim.spawn(spec.structure().isFortAspectCore()
                        ? FortAspectCore.create(name, Faction.DEFENDER, spec.structureId, spec.cellX, spec.cellY)
                        : spec.structure().isShieldGenerator()
                        ? ShieldGenerator.create(name, Faction.DEFENDER, spec.cellX, spec.cellY)
                        : MapTurret.create(name, Faction.DEFENDER, spec.structureId, spec.cellX, spec.cellY));
                if (post.fort != null) sim.tagFortPart(post.fort, turret, spec.structure(), Faction.DEFENDER);
                sim.setStructureStyle(turret, post.style);
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
