package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.ambient.WorksCrewService;
import com.dillon.starsectormarines.battle.ambient.WorksCrewSystem;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.battle.contact.CloseContactService;
import com.dillon.starsectormarines.battle.deployable.DeployedCoverService;
import com.dillon.starsectormarines.battle.deployable.PointDefenseService;
import com.dillon.starsectormarines.battle.satchel.SatchelChargeService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.marine.SatchelChargeSpec;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.appearance.SystemFxSystem;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.turret.TurretFireSystem;
import com.dillon.starsectormarines.battle.unit.DeadBodySystem;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.combat.fx.Decal;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.SmokingWreck;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.unit.BodyService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitDestinationSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import com.dillon.starsectormarines.battle.air.AirCoverSystem;
import com.dillon.starsectormarines.battle.air.AirLoss;
import com.dillon.starsectormarines.battle.air.AirStrikeSystem;
import com.dillon.starsectormarines.battle.air.BoatSortieSystem;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.AirframeCookOffSystem;
import com.dillon.starsectormarines.battle.air.AirProvider;
import com.dillon.starsectormarines.battle.air.AirSystem;
import com.dillon.starsectormarines.battle.command.BattleResources;
import com.dillon.starsectormarines.battle.fabrication.FabricationService;
import com.dillon.starsectormarines.battle.fabrication.FabricationSystem;
import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceRecorder;
import com.dillon.starsectormarines.battle.squad.AssaultCoordinationSystem;
import com.dillon.starsectormarines.battle.squad.SquadContactOnsetSystem;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceService;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.air.AirfieldCrewSystem;
import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.AirfieldSystem;
import com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem;
import com.dillon.starsectormarines.battle.command.compound.CompoundGarrisonSystem;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.ops.FieldPresencePolicy;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.combat.fx.OrdnanceRelease;
import com.dillon.starsectormarines.battle.vehicle.GroundSystem;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.air.MountedTurret;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.command.MissionCommand;
import com.dillon.starsectormarines.battle.command.CommandStrategy;
import com.dillon.starsectormarines.battle.command.CommandFrame;
import com.dillon.starsectormarines.battle.command.CommandFrameDisclosure;
import com.dillon.starsectormarines.battle.command.AutonomousMissionCommand;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.BodyDamageResolver;
import com.dillon.starsectormarines.battle.combat.DamageResolver;
import com.dillon.starsectormarines.battle.combat.EngagementService;
import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.control.DirectControlSession;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService;
import com.dillon.starsectormarines.battle.combat.MitigationSystem;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemSystem;
import com.dillon.starsectormarines.battle.infantry.EquipmentDropService;
import com.dillon.starsectormarines.battle.infantry.EquipmentDropSystem;
import com.dillon.starsectormarines.battle.mech.MechGaitSystem;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.MechDoctrineService;
import com.dillon.starsectormarines.battle.mech.MechDoctrineSystem;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.vehicle.VehicleTransportService;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderSystem;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderSystem;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.AsyncDefendTrackRoutes;
import com.dillon.starsectormarines.battle.nav.PathRequestStatus;
import com.dillon.starsectormarines.battle.mech.MechLocomotionSystem;
import com.dillon.starsectormarines.battle.mech.MechTurretSystem;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.nav.SharedGoalPolicy;
import com.dillon.starsectormarines.battle.nav.SquadRouteRequest;
import com.dillon.starsectormarines.battle.nav.mesh.GreedyNavigationMesh;
import com.dillon.starsectormarines.battle.squad.SquadRoutePreparationSystem;
import com.dillon.starsectormarines.battle.squad.SquadReplanSystem;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectivesService;
import com.dillon.starsectormarines.battle.command.objective.WinCheckSystem;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationSystem;
import com.dillon.starsectormarines.battle.evacuation.SwarmReinforcementSystem;
import com.dillon.starsectormarines.battle.evacuation.SwarmPressureSnapshot;
import com.dillon.starsectormarines.battle.evacuation.RescuePickupSupportSystem;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickProfile;
import com.dillon.starsectormarines.battle.profile.TickStallWatchdog;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService;
import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementSystem;
import com.dillon.starsectormarines.battle.command.reinforcement.RecaptureTargetSystem;
import com.dillon.starsectormarines.battle.command.reinforcement.CounterattackSystem;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.perception.NoiseEventBus;
import com.dillon.starsectormarines.battle.decision.TacticalContextService;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.combat.Detonations;
import com.dillon.starsectormarines.battle.combat.FiringSystem;
import com.dillon.starsectormarines.battle.combat.HeavyWeapons;
import com.dillon.starsectormarines.battle.combat.HitResponseSystem;
import com.dillon.starsectormarines.battle.infantry.CloseContactTactics;
import com.dillon.starsectormarines.battle.infantry.InfantryWeapons;
import com.dillon.starsectormarines.ops.RiskLevel;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
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
 *   <li>If a target is in {@code world.attackRange(id)}, the unit stops moving and
 *       fires on {@code world.attackCooldown(id)}, dealing {@code world.attackDamage(id)}.</li>
 *   <li>Otherwise the unit re-pathfinds (throttled by
 *       {@code MovementService.mayRepath}) and carrot-follows the path
 *       continuously at {@code world.moveSpeed(id)} cells/sec.</li>
 *   <li>When one faction runs out of alive units, the sim flags
 *       {@link #isComplete()} and records the winner.</li>
 * </ul>
 *
 * <p>Pathfinding runs every tick a unit is moving — at 16 units × 30Hz on a
 * 24×16 grid it's a few thousand cell expansions per second, well inside the
 * budget. Spatial indexing for target search can come later if we scale up.
 */
public class BattleSimulation implements BattleControl, AutoCloseable {

    /** Fixed simulation timestep — 30Hz. */
    public static final float TICK_DT = 1f / 30f;
    /**
     * The most ticks one rendered frame may owe the simulation; see
     * {@link #frameBudget}. 4x speed at 30 frames a second is four ticks a
     * frame; twice that leaves room for an uneven frame without letting a
     * slow tick compound.
     */
    public static final int MAX_CATCH_UP_TICKS = 8;

    /**
     * Bounds the sim time one rendered frame hands to {@link #advance}. A
     * frame that took longer than the sim time it owes is a slow tick's
     * doing, and passing the whole debt through asks the next frame for
     * every tick the last one could not afford: a 460 ms tick became a
     * 14-tick frame, then a 190-tick one. Capping the debt at
     * {@link #MAX_CATCH_UP_TICKS} turns that spiral into slow motion, which
     * is the honest presentation of a simulation running behind.
     *
     * <p>The cap lives at the frame seam rather than inside {@link #advance}
     * because a harness advancing whole seconds of ship's time in one call is
     * asking for every one of those ticks, and gets them.
     */
    public static float frameBudget(float frameDt) {
        return Math.min(frameDt, MAX_CATCH_UP_TICKS * TICK_DT);
    }

    /** Navigation slice: grid + topology + zone graph + occupancy map + spatial indices + vantage cache + LosCache lifecycle. {@link #grid} / {@link #topology} / {@link #zoneGraph} / {@link #occupancyMap} / {@link #unitIndex} / {@link #destIndex} below are alias fields that share the same instances. */
    private final NavigationService navigation;
    /** Production-on; tests and deterministic debugging can opt into synchronous routing. */
    private final AsyncDefendTrackRoutes asyncDefendTrackRoutes =
            Boolean.parseBoolean(System.getProperty(
                    AsyncDefendTrackRoutes.ENABLED_PROPERTY, "true"))
                    ? new AsyncDefendTrackRoutes() : null;
    /** Alias of {@link NavigationService#getGrid()}. Same instance — kept as a field so the sim's 80+ {@code grid.*} reads don't pay a per-call accessor hop. */
    private final NavigationGrid grid;
    /** Temporary faction-neutral visual opacity and grenade-flight lifecycle. */
    private final SmokeFieldService smokeFields;
    /** Contact-demolition reservations, target attachments, and fuse lifecycle. */
    private final SatchelChargeService satchelCharges;
    /** Owner of carrier-placed point-defence emplacements: placement queue, live pods, and their remaining lifetime / engagement budget. Ticked at the SATCHELS phase, immediately before the projectile advance it marks rounds for. */
    private final PointDefenseService pointDefense;
    /** Owner of carrier-placed cover screens: placement queue, live screens, and their remaining lifetime. Holds no entities — a screen is a property of a cell boundary, published through {@link MapEditor}. */
    private final DeployedCoverService deployedCover;
    private final CloseContactService closeContact;
    /** Committed anti-personnel grenade footprints used for squad overkill prevention. */
    private final com.dillon.starsectormarines.battle.grenade.FragGrenadeService fragGrenades;
    /** Alias of {@link NavigationService#getTopology()}. */
    private final CellTopology topology;
    /** Runtime map-modification coordinator: wall breach / roof crack / structure-to-rubble. Sequences the topology writes + navigation walkability/zone-graph writes + the roof-collapse decal sink. Owns behavior {@link NavigationService} no longer holds. */
    private final MapEditor mapEditor;
    /** Roster service: live unit registry + squad registry + spawn queue + ID counters. */
    private final UnitRosterService rosterService;
    private final AirSystem airSystem;
    private final GroundSystem groundSystem;
    /** Fog-of-war state — building registry + faction contributor set + the every-3rd-tick {@link com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass} driver. The {@link #getBuildings}/{@link #setBuildings}/{@link #getVisionState} delegates below forward here. */
    private final FogOfWarService fogOfWar = new FogOfWarService();
    /** Handheld squad weapons (rifle / SMG / DMR / rocket launcher). Owns fireShot, fireSecondary, and the per-tick burst continuation pass. Pumped each tick via {@code infantry.tick()}; behavior call sites go through the delegating {@link #fireShot} / {@link #fireSecondary} wrappers on this class. */
    private final InfantryWeapons infantry;
    /** Fire-time ballistic resolution for infantry primaries — ray vs walls/doodads/unit radii, walked in time order. Pure/stateless (reads only); constructed once and shared. See {@code ballistics-nouns.md}. */
    private final BallisticResolver ballisticResolver;
    /**
     * Consumes the per-tick {@code COMBAT} fire intent behaviors queue
     * instead of firing inline — the FiringSystem proving slice
     * ({@code ecs-nouns.md}), wired for
     * {@code EngagePosture} only today. Ticked after the spawn flush and
     * before {@link #infantry}'s burst continuation, so the continuation
     * sees this tick's {@code beginBurst} state exactly as it did when
     * postures fired inline.
     */
    private final FiringSystem firingSystem;
    /** Chassis-mounted weapons on motorized / heavy units (mech today, future tanks/hovercraft). Owns fireMechWeapon and the per-tick mech continuation + wreck-spawn passes. */
    private final HeavyWeapons heavy;
    /** Physics-based AoE pipeline — owns the in-flight rocket queue and drains expired entries into splash + wall damage. Both infantry rockets and mech HE rockets queue here through {@link Detonations#queue}. */
    private final Detonations detonations;
    /** Sets off a parked airframe destroyed on its hardstand. Subscribed to the death mailbox. */
    private final AirframeCookOffSystem airframeCookOff;
    /** Mission objective list + per-tick dispatch + the default eliminate-each-other backstop. The {@link #addObjective}/{@link #getObjectives} delegates below forward here; the OBJECTIVES phase + first-tick backstop install go through it. */
    private final ObjectivesService objectivesService = new ObjectivesService();
    /**
     * Mission-only representative civilian accounting. Generic battles leave
     * the tracker empty; rescue setup registers its full cohort and terminal
     * objective handling seals it before Results computes an outcome.
     */
    private final CivilianEvacuationTracker civilianEvacuation =
            new CivilianEvacuationTracker();
    /** Serial route/boarding driver, inert until a rescue payload configures it. */
    private final CivilianEvacuationSystem civilianEvacuationSystem =
            new CivilianEvacuationSystem(civilianEvacuation);
    /** Mission-local perimeter waves; inert unless civilian-rescue setup configures them. */
    private final SwarmReinforcementSystem swarmReinforcements =
            new SwarmReinforcementSystem(civilianEvacuation);
    /** Allied pickup garrison and casualty-driven shuttle reserve. */
    private final RescuePickupSupportSystem rescuePickupSupport =
            new RescuePickupSupportSystem(civilianEvacuation);
    /** Active equipment drops + per-tick pickup/retriever sweep + emit-on-death plumbing. Initialized in the constructor once {@link #rosterService} is available. */
    private final EquipmentDropService equipmentDropService;
    private final EquipmentDropSystem equipmentDropSystem;
    private final IntegralSystemSystem integralSystemSystem;
    /** Per-tick aim + drain of every raised mitigation screen. See {@code combat-durability-nouns.md}. */
    private final MitigationSystem mitigationSystem;
    /** Death-event handler for destroyed turrets ({@code UnitType.isTurret()}) — flips mount cell to walkable rubble + releases the guardpost if every turret on the post is down. Subscribed to {@link #deathDispatcher} in the constructor; fires on {@link #deathDispatcher}{@code .drain()} at the DEMOLISH phase. */
    private final com.dillon.starsectormarines.battle.turret.TurretDemolitionSystem turretDemolition;
    /** Death-event handler for destroyed drone hubs ({@code UnitType.isDroneHub()}) — flips hub cell to walkable rubble + cascade-kills the launched drones. Subscribed to {@link #deathDispatcher} in the constructor; fires on {@link #deathDispatcher}{@code .drain()} at the DEMOLISH phase. */
    private final com.dillon.starsectormarines.battle.drone.HubDemolitionSystem hubDemolition;
    /** Drone-crash system — death-event handler that attaches a {@code CRASHING} component to a dead drone + the per-tick processor that drives the fall/impact lifecycle over the world's {@code CRASHING} query. Subscribed to {@link #deathDispatcher} in the constructor. */
    private final com.dillon.starsectormarines.battle.drone.DroneCrashSystem droneCrashes;
    /** The battle's transient archetype-table world, shared by ground, air, convoy, corpse, presentation, and reporting entity families. Owned by {@link UnitRosterService}; lifecycle-specific queries come from {@link #battleComponents}. */
    private final EntityWorld entityWorld;
    /** Game component-type registrations + shared queries over {@link #entityWorld}. Alias of the roster service's instance. */
    private final BattleComponents battleComponents;
    /** Dead-body system — death-event handler that transmutes the dead unit's world entity to the corpse archetype in {@link #entityWorld}. Subscribed to {@link #deathDispatcher} in the constructor. */
    private final DeadBodySystem deadBodySystem;
    /** Presentation system that authors every live sheet-drawn unit's {@code SPRITE} facing/pose frame each tick — see {@link FacingSystem}. Ticked at the tail of {@link #tick}, just before the entity-world flush. */
    private final FacingSystem facingSystem;
    /** Presentation system that authors what a running integral system looks like — see {@link SystemFxSystem}. Ticked beside {@link #facingSystem} for the same reason. */
    private final SystemFxSystem systemFxSystem;
    /** Simulation-authoritative mech pivoting, settled before appearance authoring. */
    private final MechLocomotionSystem mechLocomotionSystem;
    /** Fixed-tick, presentation-only planted-foot and waist solver. */
    private final MechGaitSystem mechGaitSystem;
    /** Simulation-authoritative upper-torso traverse; gates mech weapons as well as their render heading. */
    private final MechTurretSystem mechTurretSystem;
    /** Mech-wreck system — death-event handler that drops a smoking wreck on a dead chassis unit's cell (replaces the former HeavyWeapons per-tick scan). Subscribed to {@link #deathDispatcher} in the constructor. */
    private final com.dillon.starsectormarines.battle.mech.MechWreckSystem mechWreckSystem;
    /** Broad by-id entity-access facade over {@link #entityWorld}; focused component owners remain separate Services. */
    private final World world;
    /** Per-tick squad fall-back driver — arrival detection + trigger evaluation. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.squad.SquadFallbackSystem squadFallback;
    /** Per-tick squad alert / awareness driver — drives the ENGAGED/SUSPICIOUS/UNAWARE state machine + kill-zone gating + audible-gunfire promotion. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.squad.SquadAlertSystem squadAlert;
    /** Per-tick squad morale recovery + hysteresis + near-miss drain. Drain on hit/death fires from {@link DamageResolver#resolve}; this system owns the passive recovery + flag transitions. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.squad.SquadMoraleSystem squadMorale;
    /** Per-tick squad-level GOAP replan pass — dispatches each squad to drone / mech / infantry behavior. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.squad.SquadReplanSystem squadReplan;
    /** Divides a shared contact between squads under one attack-move order. */
    private final AssaultCoordinationSystem assaultCoordination;
    private final SquadContactOnsetSystem squadContactOnset;
    /** Per-tick win-condition evaluator — pure function over the objective list; sim writes the {@link #complete}/{@link #winner} fields on terminal result. Initialized in the constructor. */
    private final com.dillon.starsectormarines.battle.command.objective.WinCheckSystem winCheck =
            new com.dillon.starsectormarines.battle.command.objective.WinCheckSystem();
    /** Persistent {@link Doodad} list + per-cell/per-facing cover lookup the AI consults when scoring firing positions. Initialized in the constructor once {@link #grid} is available. */
    private final DoodadService doodadService;
    private final List<ParkedAircraft> parkedAircraft = new ArrayList<>();
    /**
     * Transient visual side-effects — persistent ground decals, smoking wrecks,
     * HE smoke plumes, per-frame puff / fire-burst / wall-dust event queues.
     * First slice of the services refactor; the {@link #getDecals} et al.
     * accessors below delegate here. Initialized in the constructor once
     * {@link #rng} is available.
     */
    private final EffectsService effects;
    /** Persistent physical supply caches and their finite transfer state. */
    private final com.dillon.starsectormarines.battle.logistics.ResupplyService resupply =
            new com.dillon.starsectormarines.battle.logistics.ResupplyService();
    private final com.dillon.starsectormarines.battle.logistics.ResupplySystem resupplySystem;
    /** Per-faction strategic commander tier. Owns the slow-tick cadence; the {@link #setCommander}/{@link #getCommander} delegates below forward here, and the COMMANDER phase calls {@link CommanderService#tick}. */
    private final CommanderService commanders = new CommanderService();
    /** Opt-in battle-long perspective/referee diagnostic stream. */
    private CommandTraceRecorder commandTrace;
    private boolean commandTraceEnabled;
    /** Holds a campaign squad at its LZ until its remaining lifts land. */
    private final SquadFormUpSystem squadFormUp;
    /** Read-only per-faction belief aggregation and topology-aware tactical fields. */
    private final CommanderInfluenceService commanderInfluence;

    /** Player command-power layer — the in-battle activation economy (command-point pool + per-power cooldowns), the UI-requested activation queue, and in-flight transient effects. State owner, ticked by {@link #commandPowerSystem} in the command tier. The available-power roster is injected at battle setup via {@link #setCommandPowers} from the detachment resolver ({@code ops.detachment}); it starts empty. {@link #getCommandPowerService} below exposes it to the UI + the view-layer fog projection. */
    private final com.dillon.starsectormarines.battle.power.CommandPowerService commandPowers =
            new com.dillon.starsectormarines.battle.power.CommandPowerService();
    /** Stateless consumer that drains queued activations (commit cost + cooldown + resolve), regens command points, and ages cooldowns + transient pings each tick. */
    private final com.dillon.starsectormarines.battle.power.CommandPowerSystem commandPowerSystem;
    /** Player-requested per-mech doctrine overrides, drained in the command phase. */
    private final MechDoctrineService mechDoctrines = new MechDoctrineService();
    private final MechDoctrineSystem mechDoctrineSystem =
            new MechDoctrineSystem(mechDoctrines);
    /** Exact-player-mech one-shot movement overrides, drained beside doctrine commands. */
    private final VehicleTransportService transport;
    private final MechMoveOrderService mechMoveOrders = new MechMoveOrderService();
    private final MechMoveOrderSystem mechMoveOrderSystem =
            new MechMoveOrderSystem(mechMoveOrders);
    /** Squad-scoped player movement requests below mission assignment. */
    private final SquadMoveOrderService squadMoveOrders = new SquadMoveOrderService();
    private final SquadMoveOrderSystem squadMoveOrderSystem =
            new SquadMoveOrderSystem(squadMoveOrders);

    /** Per-faction resource pools (reinforcement tickets, airstrike tickets). Compounds produce; dispatch layers consume. Ticked after compound capture so production reflects freshest capture state. Declared before {@link #reinforcement} so it can be constructor-injected into it. */
    private final BattleResources battleResources = new BattleResources();

    /** Reinforcement data owner — trigger registry + means provider list + request queue. Mission setup registers triggers/means. Full design: {@code reinforcement-nouns.md}. */
    private final ReinforcementService reinforcement = new ReinforcementService();
    /** Stateless slow-tick driver that polls the reinforcement triggers, drains the request queue, and dispatches each through the priority-ordered means (resource-gated). */
    private final ReinforcementSystem reinforcementSystem =
            new ReinforcementSystem(reinforcement, battleResources);

    /** Per-compound capture state — defender supply structures (COMMAND_POST / BARRACKS / ARMORY) and their DEFENDER_HELD / CONTESTED / MARINE_HELD state. Populated from the {@link TacticalMap} in {@link #setTacticalMap}; ticked by {@link #compoundCapture}. Slice 1 of the conquest design ({@code conquest-nouns.md}). */
    private final CompoundService compoundService = new CompoundService();
    /** Set once by {@code BattleSetup}; see {@link #getMarineLandingPads()}. Empty on a battle nobody told. */
    private List<LandingPad> marineLandingPads = List.of();
    /** Set once by {@code BattleSetup}; see {@link #getLaneRoutes()}. Empty on a map with no lanes. */
    private List<LaneRoute> laneRoutes = List.of();
    /** Stateless tick consumer that drives the compound capture state machine. Reads zone occupancy, writes {@link #compoundService} records on its slow-tick cadence. */
    private final CompoundCaptureSystem compoundCapture = new CompoundCaptureSystem();
    /** Per-hardstand berth state for a garrison airfield — what is parked, away, refitting, or burned. Empty on a battle with no authored field. */
    private final AirfieldService airfieldService = new AirfieldService();
    private final AirfieldCrewSystem airfieldCrew = new AirfieldCrewSystem();
    /** Stateless tick consumer that stands airframes on their pads, writes off one destroyed where it sat, and counts down a turnaround. */
    private final AirfieldSystem airfieldSystem = new AirfieldSystem();
    /** Decides when the field puts an armed aircraft over the battle. Self-gating: a field with no strip or no sheds flies nothing. */
    private final BoatSortieSystem boatSorties = new BoatSortieSystem();
    private final AirStrikeSystem airStrikeSystem =
            new AirStrikeSystem(Faction.DEFENDER);
    /** Flies the committed fighter wings as off-map sorties. Self-gating: an empty roster dispatches nothing. */
    private final AirCoverSystem airCoverSystem = new AirCoverSystem();
    /** Marine-side garrison shuttle spawner — drops friendly troops at captured compounds. Conquest-only; null on other mission types. Set via {@link #setGarrisonSystem}. */
    private CompoundGarrisonSystem garrisonSystem;
    /**
     * What the map's vehicle bays have on the stocks, or null on a map with
     * none. Installed by {@code BattleSetup}, which is where the map's berths
     * and work points are in hand.
     */
    private FabricationService fabrication;
    private final FabricationSystem fabricationSystem = new FabricationSystem();
    /**
     * The watches standing on this map's worked structures, or null on a map
     * with none. Installed by {@code BattleSetup}, which is where the rooms and
     * the approach axis are in hand.
     */
    private WorksCrewService worksCrews;
    private final WorksCrewSystem worksCrewSystem = new WorksCrewSystem();

    /**
     * Per-tick recompute driver for the defender's recapture-target registry
     * (progressive reinforcement, {@code reinforcement-nouns.md}).
     * Conquest-only; null on mission types with no biome layer. Set via
     * {@link #setRecaptureSystem}.
     */
    private RecaptureTargetSystem recaptureSystem;

    /**
     * Staged bulge counterattack state machine (progressive reinforcement's
     * offensive inverse, {@code reinforcement-nouns.md}).
     * Conquest-only; null on mission types with no biome layer. Set via
     * {@link #setCounterattackSystem}. Ticks after {@link #recaptureSystem}
     * (so it sees this tick's fresh contested state) and before {@link
     * #reinforcementSystem} (so its prepaid wave posts drain this same tick).
     */
    private CounterattackSystem counterattackSystem;

    /**
     * Per-target attacker index — wraps the {@code Entity → attacker list} map
     * that drives O(1)-lookup crowding scoring in
     * {@link com.dillon.starsectormarines.battle.decision.TacticalScoring}. Rebuilt
     * once at tick top in the serial phase; read in parallel during
     * UPDATE_UNITS against the frozen snapshot. Sibling slice to
     * {@link #rosterService} / {@link #navigation}; constructed after the
     * roster since {@link com.dillon.starsectormarines.battle.decision.AttackerIndexService#rebuild()}
     * iterates the live unit registry.
     */
    private final com.dillon.starsectormarines.battle.decision.AttackerIndexService attackerIndex;

    /** Shared scoring service for target selection, firing-position, fallback, and cover queries. Constructor-injected with NavigationService, UnitRosterService, AttackerIndexService, ShotService, DoodadService. */
    private final com.dillon.starsectormarines.battle.decision.TacticalScoring tacticalScoring;

    /** In-flight tracers + projectiles + per-frame event drains. Sibling slice to {@link #effects} / {@link #fogOfWar}; the {@link #postShot}, {@link #queueProjectile}, {@link #getActiveShots} et al. delegates below forward here. */
    private final NoiseEventBus noiseEvents;
    private final ShotService shots;
    /** Turret-kind fire procedure — modeled ground rounds plus legacy aerial/indirect fire, payload queuing, and shot-event posting. Extracted from the sim's former {@code fireShotFrom} methods. */
    private final TurretFireSystem turretFire;
    /** Per-hit response logic — fallback rolls + target-reprioritization rolls. Extracted from the sim's former {@code rollFallbackOnHit}/{@code rollReprioritizeOnHit}. */
    private final HitResponseSystem hitResponse;
    /** Ids of units that transitioned from alive to dead during the last {@link #advance(float)} call. Same lifecycle as {@link #getShotsThisFrame()}. */
    private final LongList deathsThisFrame = new LongArrayList();
    /** Marine squad ids whose members were actually hit by friendly rounds during the last {@link #advance(float)} call. */
    private final IntList friendlyFireSquadsThisFrame = new IntArrayList();
    /** Death-event mailbox — {@code DamageResolver} publishes a {@link com.dillon.starsectormarines.battle.unit.DeathEvent} per death; subscribed handlers (turret + hub demolition today) react on {@link com.dillon.starsectormarines.battle.unit.DeathDispatcher#drain()} at the demolition phase. The seam that lets post-death behavior migrate off the legacy units-list scan. */
    private final com.dillon.starsectormarines.battle.unit.DeathDispatcher deathDispatcher =
            new com.dillon.starsectormarines.battle.unit.DeathDispatcher();
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
    /**
     * The same law applied to a body that is not a roster unit — a chassis, an
     * aircraft, whatever registers next. One route; the carrier owns what
     * happens when the body runs out of structure.
     */
    private final BodyDamageResolver bodyDamageResolver;
    /** Who can engage what, right now — the relation that replaced the absolute {@code isCombatTarget}. */
    private final EngagementService engagement;
    /**
     * The battle's single random stream, seeded at construction.
     *
     * <p><b>Every roll in the sim draws from here.</b> Nothing under {@code battle/}
     * may call {@code ThreadLocalRandom} or {@code Math.random} — a battle whose hit
     * rolls, morale breaks, and patrol wander come from an unseeded global source
     * cannot be reproduced from a bug report, cannot be replayed, and cannot be tested
     * without saturating the inputs until the roll stops mattering. It also silently
     * changes behaviour the moment any sim work moves off this thread, because
     * {@code ThreadLocalRandom} is per-thread by definition.
     *
     * <p>Not thread-safe, deliberately: the sim ticks serially, and a stream that could
     * be drawn from concurrently would not be reproducible even with a seed.
     */
    private final Random rng;

    /** Alias of {@link NavigationService#getOccupancyMap()}. */
    private final byte[] occupancyMap;

    /** Alias of {@link NavigationService#getUnitIndex()}. */
    private final UnitSpatialIndex unitIndex;

    /** Alias of {@link NavigationService#getDestIndex()}. */
    private final UnitDestinationSpatialIndex destIndex;

    private float tickAccumulator = 0f;
    /** Monotonic sim-tick counter incremented at the top of every {@link #tick}. Read by per-hit gates that want to fire at most once per tick (e.g. {@link HitResponseSystem#rollReprioritizeOnHit}). */
    public int simTickIndex = 0;
    /** Per-phase wall-clock profile of {@link #tick()}. Always-on (cost is a handful of {@code nanoTime} calls per tick); read by the {@code TickProfileDebugPanel} HUD overlay and the {@code TickProfileDumper} JSON dumper. */
    private final TickProfile tickProfile = new TickProfile();
    /** Per-tick sub-step profile (behavior buckets + heavy primitives like pathfind / target-pick). Reset at the top of every {@link #tick()}; snapshotted into {@link TickProfile.Spike#innerSnapshot} when a spike fires so spike JSONs carry the diagnostic breakdown. Exposed via the static {@link TickInnerProfile#current()} slot so non-sim call sites (GridPathfinder, TacticalScoring) can record without threading the sim reference through. */
    private final TickInnerProfile tickInnerProfile = new TickInnerProfile();
    // The LoS caches are per-thread and per navigation grid, held by the grid
    // (see LosCaches). The sim owns none directly — navigation.beginTick()
    // sweeps this grid's worker slots and opens their window.

    /** Owns the parallel UPDATE_UNITS dispatch + the worker {@code ForkJoinPool} + per-role behavior dispatch. This is the entity-for-loop seam — see the class doc for the ECS/SoA promotion plan. */
    private final com.dillon.starsectormarines.battle.decision.UnitUpdateSystem unitUpdate;
    /** Interruptible authored work shared by live battles and bounded scene hosts. */
    private final AmbientTaskService ambientTasks;
    /** Exclusive interaction-site claims shared by ambient work and ordinary battle tasks. */
    private final TaskPointService taskPoints;
    /** Post-movement ground-unit separation and terrain-aware squad-formation relaxation. See {@link SeparationSystem} class doc; ticked right after the occupancy-delta drain, before the spawn flush. */
    private final SeparationSystem separation;
    /** Off-by-default, bounded quiet-infantry spreading/yielding experiment. */
    private final SquadTrafficSystem squadTraffic;
    /** Short-range allied-infantry steer away from hostile alien bodies. */
    private final SwarmAvoidanceSystem swarmAvoidance;
    /** Detects stalled mechs and grants a temporary mech-vs-mech separation escape hatch. */
    private final com.dillon.starsectormarines.battle.mech.MechCollisionEscapeSystem mechCollisionEscape;
    private boolean complete = false;
    private final DirectControlSession directControl;
    private Faction winner;
    /** False for bounded non-mission hosts such as shipboard room previews. */
    private boolean missionCompletionEnabled = true;

    /** Alias of {@link NavigationService#getZoneGraph()}. */
    private final ZoneGraph zoneGraph;

    /** Fighter wings committed to this battle. Lives on the sim so the overlay can read it without coupling to the briefing screen. */
    private FlybyRoster flybyRoster = FlybyRoster.EMPTY;
    /** Campaign-faction doctrine resolved once at setup and reused by every defender source. */
    private GroundRosterProfile groundRoster;

    /** Who owns the air layer. {@link AirProvider#INTERNAL} by default — standalone battles <em>and</em> the current combat-bridge host both run the sim's own shuttles + flyby (the S3d drop-ship invasion depends on it: dropships are the sim's own air craft, spawned via {@link #spawnShuttle}). {@link AirProvider#EXTERNAL} is the alternative where a host owns the air — the sim's air tick is skipped and internal air-install is rejected — kept for a future direct-injection bridge, but not used today. See {@link AirProvider}. */
    private AirProvider airProvider = AirProvider.INTERNAL;

    /** Battle-scoped tactical data from the map generator — TacticalMap hint graph + DefensePost list. The {@link #getTacticalMap}/{@link #setTacticalMap}/{@link #setDefensePosts} delegates below forward here. */
    private final TacticalContextService tactical =
            new TacticalContextService();

    /**
     * Fixed seed for the no-seed constructor, so tests and any host that does not care
     * still get a reproducible battle. Production goes through
     * {@code BattleSetup.buildMap}, which always passes the real battle seed.
     */
    public static final long DEFAULT_SEED = 0xB4771E5EEDL;

    /**
     * Deterministic by default. A battle built this way replays identically every run,
     * which is what test fixtures want; {@link #BattleSimulation(NavigationGrid,
     * CellTopology, long)} is the production path.
     */
    public BattleSimulation(NavigationGrid grid, CellTopology topology) {
        this(grid, topology, DEFAULT_SEED);
    }

    public BattleSimulation(NavigationGrid grid, CellTopology topology, long seed) {
        this.rng = new Random(seed);
        this.noiseEvents = new NoiseEventBus(() -> simTickIndex);
        this.shots = new ShotService(noiseEvents);
        this.commandPowerSystem = new com.dillon.starsectormarines.battle.power.CommandPowerSystem(
                commandPowers, this);
        this.navigation = new NavigationService(grid, topology);
        this.mapEditor = new MapEditor(navigation);
        // Alias-fields share the same instances as the service so the sim's
        // 80+ internal `grid.*`/`topology.*`/`zoneGraph.*`/`occupancyMap[...]`
        // reads stay direct (no per-call accessor hop).
        this.grid = navigation.getGrid();
        this.smokeFields = new SmokeFieldService(this.grid);
        this.satchelCharges = new SatchelChargeService();
        this.pointDefense = new PointDefenseService(rng);
        this.deployedCover = new DeployedCoverService(mapEditor, this.grid);
        this.closeContact = new CloseContactService();
        this.fragGrenades = new com.dillon.starsectormarines.battle.grenade.FragGrenadeService();
        this.topology = navigation.getTopology();
        this.zoneGraph = navigation.getZoneGraph();
        this.occupancyMap = navigation.getOccupancyMap();
        this.unitIndex = navigation.getUnitIndex();
        this.destIndex = navigation.getDestIndex();
        this.effects = new com.dillon.starsectormarines.battle.combat.fx.EffectsService(rng);
        this.doodadService = new DoodadService(grid);
        // DamageService construction is staged: the resolver needs the roster
        // (squad map + units list) and the equipment-drop service, both of
        // which we build right after. We construct the service second and
        // wire it with damageResolver::resolve as the applier method ref.
        this.rosterService = new UnitRosterService(unitIndex, null);
        this.commanderInfluence = new CommanderInfluenceService(grid, rosterService,
                () -> navigation.getNavigationMesh().snapshot().revision(),
                Boolean.parseBoolean(System.getProperty(
                        CommanderInfluenceService.ASYNC_PROPERTY, "true")));
        this.resupplySystem = new com.dillon.starsectormarines.battle.logistics.ResupplySystem(
                resupply, rosterService);
        // The entity world + component registrations are owned by the roster
        // service (allocate is the spawn seam that adopts ids into the world); the
        // sim aliases them for its tick barrier + getters.
        this.entityWorld = rosterService.entityWorld();
        this.battleComponents = rosterService.components();
        this.equipmentDropService = new EquipmentDropService(rosterService);
        this.equipmentDropSystem = new EquipmentDropSystem(rosterService, this::clearPath, equipmentDropService);
        this.mitigationSystem = new MitigationSystem(rosterService);
        this.damageResolver = new DamageResolver(
                navigation, rosterService, equipmentDropService,
                // deathSink takes the dying id straight into the id-native
                // deathsThisFrame list (read post-advance by the death-voice
                // consumers via identity()/world() by-id — IDENTITY survives release).
                id -> deathsThisFrame.add(id), deathDispatcher, () -> simTickIndex, rng);
        this.bodyDamageResolver = new BodyDamageResolver(rosterService);
        this.engagement = new EngagementService(rosterService);
        this.damageService = new DamageService(
                (target, attacker, damage, penetration, moraleImpact) -> {
                    BodyCarrier carrier = rosterService.bodies().carrierOf(target);
                    if (carrier != null) {
                        bodyDamageResolver.resolve(carrier, target, attacker,
                                damage, penetration);
                    } else {
                        damageResolver.resolve(target, attacker, damage,
                                penetration, moraleImpact);
                    }
                },
                this::writeReprioInline,
                this::writeFallbackInline,
                navigation::applyOccupancyDeltaInline,
                rosterService::isLive);
        // setPath/clearPath bodies live on NavigationService; they enqueue
        // their occupancy/destIndex delta through the damage service's
        // queued (parallel-safe) path. Wired here since the navigation
        // service is built before the damage service exists.
        navigation.setOccupancyDeltaSink(damageService::applyOccupancyDelta);
        navigation.setRoster(rosterService);
        rosterService.setDamageService(damageService);
        rosterService.setNavigationGrid(grid);
        // Entity-access facade — by-id accessors that read the archetype entity
        // world's component columns directly. Owned by the roster service (which
        // owns the world it reads); the sim aliases it for its world() getter.
        this.world = rosterService.world();
        this.taskPoints = new TaskPointService(grid);
        this.turretDemolition = new com.dillon.starsectormarines.battle.turret.TurretDemolitionSystem(
                mapEditor, effects, tactical, rosterService);
        deathDispatcher.subscribe(turretDemolition::onDeath);
        this.hubDemolition = new com.dillon.starsectormarines.battle.drone.HubDemolitionSystem(
                mapEditor, effects, rosterService, deathDispatcher);
        deathDispatcher.subscribe(hubDemolition::onDeath);
        this.droneCrashes = new com.dillon.starsectormarines.battle.drone.DroneCrashSystem(
                navigation, effects, entityWorld, battleComponents);
        deathDispatcher.subscribe(droneCrashes::onDeath);
        // A side remembers where it lost people. Subscribed rather than polled:
        // the dispatcher sees every death exactly once, and a commander that
        // scanned for corpses would count the same loss on every pulse. Ahead
        // of the corpse transmute, so the identity it reads is still the
        // combatant that died rather than whatever the body becomes.
        deathDispatcher.subscribe(commanderInfluence.casualties()::onDeath);
        this.deadBodySystem = new DeadBodySystem(entityWorld, battleComponents);
        deathDispatcher.subscribe(deadBodySystem::onDeath);
        deathDispatcher.subscribe(event -> taskPoints.release(event.unitId()));
        // Registered rescue civilians report loss through the same once-only
        // death mailbox as every other post-death reaction. Non-cohort deaths
        // are harmless tracker no-ops.
        deathDispatcher.subscribe(event ->
                civilianEvacuation.markLost(event.unitId()));
        deathDispatcher.subscribe(this::recordCommandTraceCasualty);
        this.facingSystem = new FacingSystem(entityWorld, battleComponents, rosterService);
        this.systemFxSystem = new SystemFxSystem(rosterService);
        this.mechLocomotionSystem = new MechLocomotionSystem(
                entityWorld, battleComponents, rosterService);
        this.mechGaitSystem = new MechGaitSystem(entityWorld, battleComponents);
        this.mechTurretSystem = new MechTurretSystem(
                entityWorld, battleComponents, rosterService);
        this.mechWreckSystem = new com.dillon.starsectormarines.battle.mech.MechWreckSystem(effects, rosterService);
        deathDispatcher.subscribe(mechWreckSystem::onDeath);
        this.squadFallback = new com.dillon.starsectormarines.battle.squad.SquadFallbackSystem(
                navigation, rosterService, this::clearPath);
        this.squadAlert = new com.dillon.starsectormarines.battle.squad.SquadAlertSystem(
                navigation, rosterService, shots, noiseEvents);
        this.squadMorale = new com.dillon.starsectormarines.battle.squad.SquadMoraleSystem(
                rosterService, shots);
        this.squadReplan = new com.dillon.starsectormarines.battle.squad.SquadReplanSystem(rosterService);
        this.assaultCoordination = new AssaultCoordinationSystem(rosterService);
        this.squadFormUp = new SquadFormUpSystem(rosterService);
        this.attackerIndex = new com.dillon.starsectormarines.battle.decision.AttackerIndexService(rosterService);
        this.tacticalScoring = new com.dillon.starsectormarines.battle.decision.TacticalScoring(
                navigation, rosterService, attackerIndex, shots, doodadService);
        this.squadContactOnset = new SquadContactOnsetSystem(
                rosterService, tacticalScoring);
        this.unitUpdate = new com.dillon.starsectormarines.battle.decision.UnitUpdateSystem(
                rosterService, damageService, tickInnerProfile,
                this.grid.losCaches());
        this.ambientTasks = new AmbientTaskService(
                rosterService, navigation, taskPoints);
        deathDispatcher.subscribe(event -> ambientTasks.release(event.unitId()));
        this.swarmAvoidance = new SwarmAvoidanceSystem(
                rosterService, unitIndex, grid);
        this.separation = new SeparationSystem(rosterService, unitIndex, grid);
        this.squadTraffic = Boolean.getBoolean(SquadTrafficSystem.PROPERTY)
                ? new SquadTrafficSystem(grid, rosterService) : null;
        this.mechCollisionEscape = new com.dillon.starsectormarines.battle.mech.MechCollisionEscapeSystem(
                entityWorld, battleComponents);
        this.hitResponse = new HitResponseSystem(
                grid, rosterService, tacticalScoring, damageService,
                () -> simTickIndex, rng);
        this.detonations = new Detonations(rosterService, grid, topology, damageService,
                mapEditor, effects, noiseEvents, this::applyPendingImpact);
        // Subscribed here rather than with the other demolition handlers above
        // because a burning airframe needs the detonation pipeline, and that
        // is the line it comes into existence on.
        this.airframeCookOff = new AirframeCookOffSystem(effects, detonations, rosterService);
        deathDispatcher.subscribe(airframeCookOff::onDeath);
        this.ballisticResolver = new BallisticResolver(grid, doodadService, unitIndex, rosterService);
        // Constructed here (rather than alongside the other early per-unit
        // systems above) because a missile-pod salvo needs the same
        // resolver/shots pipeline InfantryWeapons uses, and both exist only
        // from this point on.
        this.integralSystemSystem = new IntegralSystemSystem(rosterService, ballisticResolver, shots, rng);
        this.turretFire = new TurretFireSystem(
                rng, topology, shots, damageService,
                det -> { synchronized (detonations) { detonations.queue(det); } },
                hitResponse, world, ballisticResolver, rosterService.telemetry());
        this.infantry = new InfantryWeapons(rosterService, ballisticResolver, shots, grid, rng);
        this.ambientTasks.setLiveFireSink((actorId, targetId) -> {
            if (rosterService.isLive(actorId) && rosterService.isAliveById(targetId)) {
                infantry.fireDrillShot(actorId, targetId, FireStance.STANCED);
                rosterService.combat().beginBurst(actorId, targetId);
            }
        });
        this.firingSystem = new FiringSystem(grid, rosterService);
        this.heavy = new HeavyWeapons(rosterService, grid, ballisticResolver, shots, detonations, rng);
        this.airSystem = new AirSystem(navigation, rosterService, tacticalScoring, world, turretFire,
                rng, this::spawn, effects, resupply, this);
        this.airSystem.setAirfield(airfieldService);
        // A gun run delivers through the same AoE pipeline as every other
        // explosion, so the air system needs it too.
        this.airSystem.setDetonations(detonations);
        // A craft the air system kills on the ground shares the hardstand
        // kill's own cook-off — one definition of the blast, not two.
        this.airSystem.setAirframeCookOff(airframeCookOff);
        this.transport = new VehicleTransportService(rosterService, rosterService.convoy(),
                entityWorld, battleComponents, navigation);
        this.groundSystem = new GroundSystem(navigation, rosterService, tacticalScoring, world,
                turretFire, rng, this::spawn, this, effects, transport);
        rosterService.convoy().setDestructionSink(groundSystem::destroyVehicle);
        rosterService.airTargets().setDestructionSink(airSystem::destroyAircraft);
        mapEditor.setRoofCollapseSink((x, y) -> {
            float jx = x + 0.5f + (rng.nextFloat() * 2f - 1f) * 0.25f;
            float jy = y + 0.5f + (rng.nextFloat() * 2f - 1f) * 0.25f;
            int rubbleIdx = rng.nextFloat() < 0.5f
                    ? com.dillon.starsectormarines.battle.combat.fx.DecalKind.RUBBLE.index
                    : com.dillon.starsectormarines.battle.combat.fx.DecalKind.RUBBLE_ALT.index;
            effects.addDecal(new com.dillon.starsectormarines.battle.combat.fx.Decal(
                    jx, jy, rubbleIdx, rng.nextFloat() * 360f, 1.10f));
        });
        fogOfWar.init(grid, 256);
        this.directControl = new DirectControlSession(this, rosterService,
                id -> transport.isRiding(id) || ambientTasks.isControlling(id), () -> complete);
    }

    public DirectControlSession directControl() { return directControl; }

    public NavigationGrid getGrid() { return grid; }
    /** Published derived navigation geometry; exposed for battle diagnostics. */
    public GreedyNavigationMesh getNavigationMesh() {
        return navigation.getNavigationMesh();
    }
    @Override public AsyncDefendTrackRoutes asyncDefendTrackRoutes() {
        return asyncDefendTrackRoutes;
    }
    @Override public long getNavigationGridRevision() {
        return grid.topologyRevision();
    }
    @Override public long getNavigationTopologyRevision() {
        return navigation.getNavigationMesh().snapshot().revision();
    }
    @Override public SmokeFieldService smokeFields() { return smokeFields; }
    @Override public SatchelChargeService satchelCharges() { return satchelCharges; }
    @Override public PointDefenseService pointDefense() { return pointDefense; }
    @Override public DeployedCoverService deployedCover() { return deployedCover; }
    @Override public CloseContactService closeContact() { return closeContact; }
    @Override public com.dillon.starsectormarines.battle.grenade.FragGrenadeService fragGrenades() { return fragGrenades; }
    /** Categorization tags (street / rubble / wall / vehicle / etc.) for renderer + placement filters. Sibling to {@link #grid}; the pathfinder doesn't touch this. */
    public CellTopology getTopology()      { return topology; }
    /** Zone+portal graph layered on the {@link NavigationGrid}. Rebuilt on wall destruction so AI queries reflect the current map. */
    public ZoneGraph getZoneGraph()        { return zoneGraph; }

    public boolean damageCell(int x, int y, int amount) {
        return mapEditor.damageWall(x, y, amount);
    }

    /** Damages an authored feature occupying one shared cardinal edge. */
    public boolean damageEdgeBarrier(int x, int y, Direction direction,
                                     int amount) {
        return mapEditor.damageEdgeBarrier(x, y, direction, amount);
    }

    public boolean isRoofShielded(long target) {
        if (target == 0L) return false;
        return topology.isRoofIntact(world.cellX(target), world.cellY(target));
    }

    @Override public int liveUnitCount() { return rosterService.liveCount(); }
    @Override public long liveUnitAt(int index) { return rosterService.get(index); }
    @Override public int liveUnitIndexOf(long id) { return rosterService.indexOf(id); }
    @Override public int squadMemberCount(int squadId) { return rosterService.squadMemberCount(squadId); }
    @Override public long squadMemberAt(int squadId, int index) {
        return rosterService.squadMemberArray(squadId)[index];
    }

    /** Entity-access facade — by-id hot primitives ({@code world().hp(id)}) over the dense SoA + cold {@code world().id(id).getOrNull(Cmp.class)} projection over the sparse stores. See {@link World}. */
    public World world() { return world; }

    /**
     * The battle's single seeded random stream. Every roll in the sim — hit, morale,
     * patrol wander, drone slotting — draws from here so a battle is reproducible from
     * its seed. See the {@link #rng} field for why nothing may use
     * {@code ThreadLocalRandom} instead.
     */
    @Override
    public Random random() { return rng; }

    /** Data owner for the IDENTITY component (type/faction/name) — {@code sim.identity().name(id)} is the greppable-name read for debug dumps / logs. */
    public IdentityService identity() { return rosterService.identity(); }

    /** Data owner for the COMBAT component (the World decomposition) — consumers reach it here as {@code sim.combat().attackCooldown(id)}. */
    public CombatService combat() { return rosterService.combat(); }

    @Override
    public IntegralSystemService integralSystems() { return rosterService.integralSystems(); }

    /** Data owner for the MOVEMENT component — {@code sim.movement().moveSpeed(id)}. */
    public MovementService movement() { return rosterService.movement(); }

    /** Data owner for the VISION component (sight stats) — {@code sim.vision().airLosRadius(id)}. */
    public VisionService vision() { return rosterService.vision(); }

    /** Data owner for the SQUAD component (membership) — {@code sim.squad().hasSquad(id)} / {@code squadId(id)}. Distinct from {@link #getSquad(int)} (the squad-object registry). */
    public SquadService squad() { return rosterService.squad(); }

    /** Data owner for the ROLE component (behavior-dispatch role) — {@code sim.role().role(id)} / {@code setRole(id, r)}. */
    public RoleService role() { return rosterService.role(); }

    /** Data owner for the HOME component (garrison idle-post) — {@code sim.home().hasHome(id)} / {@code homeCellX(id)}. */
    public HomeService home() { return rosterService.home(); }

    /** Data owner for the HUB_STATE component (drone-hub spawn cadence) — {@code sim.hubState().isHub(id)} / {@code spawnCooldown(id)}. */
    public HubStateService hubState() { return rosterService.hubState(); }

    /** Data owner for the TURRET_STATE component (turret facing/recoil/burst) — {@code sim.turretState().isTurret(id)} / {@code facingDegrees(id)}. */
    public TurretStateService turretState() { return rosterService.turretState(); }

    /** Data owner for the DRONE_STATE component (drone patrol/pursuit vectors) — {@code sim.droneState().isDrone(id)} / {@code patrolGoalX(id)}. */
    public DroneStateService droneState() { return rosterService.droneState(); }

    /** Data owner for the TASK component (objective/kit assignment) — {@code sim.task().assignedObjective(id)} / {@code equipmentDropTarget(id)}. */
    public TaskService task() { return rosterService.task(); }

    /** Interruptible authored world-work assignments for civilians, workers, guards, and embedded scenes. */
    public AmbientTaskService ambientTasks() {
        return ambientTasks;
    }

    /** Exclusive task-point registry for generated fixtures and mission-owned interaction sites. */
    public TaskPointService taskPoints() {
        return taskPoints;
    }

    /** The battle's archetype-table entity world — every unit as {@code {IDENTITY, HEALTH}}, corpses as the corpse archetype. Walk it via {@link #getBattleComponents()}' shared queries. */
    public EntityWorld getEntityWorld() { return entityWorld; }

    /** Game component-type registrations + shared queries for {@link #getEntityWorld()}. */
    public BattleComponents getBattleComponents() { return battleComponents; }

    public long[] getAirEntityIds()        { return airSystem.airEntityIds(); }
    /** Every craft this battle destroyed, in the order it lost them — read once, at resolution, long after the entities were reaped. */
    public List<AirLoss> getAirLosses()    { return airSystem.airLosses(); }
    /** Smoothed per-slot engine-FX demand for an air entity, or {@code null} if it has no engine plumes. The render + light passes feed it to {@code EngineFxRenderer}; advanced each tick by {@code AirSystem}. */
    public float[] getThrusterGlow(long airEntityId) { return airSystem.thrusterGlow(airEntityId); }
    /** Attaches a turret loadout to an air entity (presence component). Called at setup once the craft is spawned (id minted); no-op for an empty loadout. */
    public void attachAirTurrets(long airEntityId, MountedTurret[] mounts) { requireInternalAir("attachAirTurrets"); airSystem.attachTurrets(airEntityId, mounts); }
    /** An air entity's mounted turrets (by id), or {@code null} if it carries no turret component. Read by the shuttle render pass. */
    public MountedTurret[] getAirTurretMounts(long airEntityId) { return airSystem.mountsFor(airEntityId); }
    /** The live convoy-vehicle entity ids — walk these and read each vehicle by id via {@link #convoy()} / {@link #convoyMission(long)}. Mirrors {@link #getAirEntityIds()}; distinct from the parked road vehicles, which are ordinary doodads. */
    public long[] getConvoyVehicleIds() { return groundSystem.vehicleEntityIds(); }
    @Override
    public float physicalRadius(long id) {
        return rosterService.radius(id);
    }
    /** The convoy-vehicle data owner — by-id reads of the {@code GROUND_IDENTITY} / {@code GROUND_KINEMATICS} / {@code GROUND_TURRET} / {@code VEHICLE_MISSION} columns for the render / picking / debug passes. Service-direct, not via {@link #world()} ({@code World} is deprecated for migrated state). */
    public ConvoyService convoy() { return rosterService.convoy(); }
    /** Who is riding in what — the data owner for {@code RIDING}, and the gate a pass consults to skip a passenger. */
    public VehicleTransportService transport() { return transport; }

    @Override
    public boolean isRiding(long id) { return rosterService.isRiding(id); }
    /** The {@link VehicleMission} for a convoy-vehicle id (has-gated, {@code null} if not live) — the by-id read path {@link BattleView} consumers use. */
    public VehicleMission convoyMission(long id) { return rosterService.convoy().mission(id); }
    public List<Objective> getObjectives() { return objectivesService.getObjectives(); }
    public CivilianEvacuationTracker getCivilianEvacuationTracker() {
        return civilianEvacuation;
    }
    public boolean configureCivilianEvacuation(
            CivilianEvacuationPlacement placement) {
        return civilianEvacuationSystem.configure(placement);
    }
    public boolean attachCivilianPickupShuttle(long shuttleId) {
        return civilianEvacuationSystem.attachPickupShuttle(shuttleId);
    }
    /** Whether the rescue cohort is still sealed inside its opening shelter. */
    public boolean isCivilianShelterProtected() {
        return civilianEvacuationSystem.isShelterProtected();
    }
    /** Records the squad sealed in the rescue shelter. One only. */
    public boolean registerShelterGuardSquad(int squadId) {
        return civilianEvacuationSystem.registerShelterGuard(squadId);
    }
    @Override
    public boolean isShelterGuard(int squadId) {
        return civilianEvacuationSystem.isShelterGuard(squadId);
    }
    /** Whether a cell belongs to the rescue shelter or opening pickup exclusion footprint. */
    public boolean isInsideRescueOpeningProtectedZone(int x, int y) {
        return civilianEvacuationSystem.isInsideOpeningProtectedZone(x, y);
    }
    /** Whether a marine has reached the bunker entrance and begun evacuation. */
    public boolean isCivilianEvacuationTriggered() {
        return civilianEvacuationSystem.isEvacuationTriggered();
    }
    @Override
    public boolean hasCivilianPickupShuttle() {
        return civilianEvacuationSystem.hasPickupShuttle();
    }
    public boolean configureSwarmReinforcements(
            CivilianEvacuationPlacement placement, int targetPopulation, long seed) {
        return swarmReinforcements.configure(placement, targetPopulation, seed,
                this);
    }
    public boolean isSwarmReinforcementConfigured() {
        return swarmReinforcements.isConfigured();
    }
    public int swarmTargetPopulation() {
        return swarmReinforcements.targetPopulation();
    }
    public SwarmPressureSnapshot getSwarmPressureSnapshot() {
        return swarmReinforcements.snapshot();
    }
    public boolean configureRescuePickupSupport(
            CivilianEvacuationPlacement placement,
            float reinforcementLzX, float reinforcementLzY,
            float entryX, float entryY, float exitX, float exitY,
            long seed, RiskLevel risk) {
        return rescuePickupSupport.configure(placement,
                reinforcementLzX, reinforcementLzY,
                entryX, entryY, exitX, exitY, seed, risk, this);
    }
    public boolean isRescuePickupSupportConfigured() {
        return rescuePickupSupport.isConfigured();
    }
    public int liveRescuePickupGuards() {
        return rescuePickupSupport.liveGuardCount(this);
    }
    public List<EquipmentDrop> getEquipmentDrops() { return equipmentDropService.getEquipmentDrops(); }
    public List<com.dillon.starsectormarines.battle.logistics.ResupplyCache> getResupplyCaches() {
        return resupply.caches();
    }
    public List<Doodad> getDoodads()       { return doodadService.getDoodads(); }
    /** Building registry for the roof-render + fog-of-war passes. Never null. */
    public com.dillon.starsectormarines.battle.world.model.Buildings getBuildings() { return fogOfWar.getBuildings(); }
    /** Faction-contributor set for the fog-of-war reveal. */
    public com.dillon.starsectormarines.battle.vision.PlayerVisionState getVisionState() { return fogOfWar.getVisionState(); }
    /** Fog-of-war service — per-cell reveal state + per-unit visibility. The renderer reads this for the fog overlay and unit visibility gate. */
    public FogOfWarService getFogOfWar() { return fogOfWar; }
    /** Player command-power layer. The battle UI reads the pool / cooldowns and calls {@link com.dillon.starsectormarines.battle.power.CommandPowerService#requestActivation}; {@code BattleScreen.advance} projects its active recon pings into the fog as ephemeral vision sources. */
    public com.dillon.starsectormarines.battle.power.CommandPowerService getCommandPowerService() { return commandPowers; }
    /** Player battle-only mech doctrine command mailbox. */
    public MechDoctrineService getMechDoctrineService() { return mechDoctrines; }
    /** Player battle-only exact-mech move-order mailbox and active projection. */
    public MechMoveOrderService getMechMoveOrderService() { return mechMoveOrders; }

    @Override public void cancelMechMoveOrder(long member) { mechMoveOrders.cancel(member); }

    @Override public boolean beginVehicleDirectControl(long vehicle) {
        return groundSystem.beginDirectControl(vehicle);
    }

    @Override public void suspendVehicleDirectInput(long vehicle) {
        groundSystem.suspendDirectInput(vehicle);
    }

    @Override public void endVehicleDirectControl(long vehicle) {
        groundSystem.endDirectControl(vehicle);
    }


    @Override public boolean canFireMechMount(long shooter, MechWeaponMount mount) {
        return heavy.canFireMechMount(shooter, mount);
    }

    /** Player move orders for exact ground vehicles; owned by {@code GroundSystem} beside the driver they use. */
    public VehicleMoveOrderService getVehicleMoveOrderService() {
        return groundSystem == null ? null : groundSystem.moveOrders();
    }
    /** Exact-mech move executor used by the mech unit-dispatch path. */
    @Override
    public MechMoveOrderSystem getMechMoveOrderSystem() { return mechMoveOrderSystem; }
    /** Player battle-only infantry squad move-order mailbox and projection. */
    public SquadMoveOrderService getSquadMoveOrderService() { return squadMoveOrders; }
    /** Applies squad tactical moves at the serialized command boundary. */
    public SquadMoveOrderSystem getSquadMoveOrderSystem() { return squadMoveOrderSystem; }
    public void setCommandPowerResources(com.dillon.starsectormarines.battle.power.CommandPowerResources resources) {
        commandPowers.setResources(resources);
    }
    /** Inject the detachment-resolved command-power roster. Called once at battle setup ({@code ops.MissionLaunch}), mirroring {@link #setFlybyRoster}; an empty/{@code null} list leaves the power UI hidden. */
    public void setCommandPowers(List<com.dillon.starsectormarines.battle.power.CommandPower> powers) { commandPowers.setPowers(powers); }

    /** Installs the mission-owned concurrent player-squad limit before ticking. */
    public void setFieldPresencePolicy(FieldPresencePolicy policy) {
        airSystem.setFieldPresencePolicy(policy);
    }
    /** Hands the sim the map's building registry. Called by BattleSetup after generation. Subsequent visibility passes will reveal/hide these buildings as contributor units move. */
    public void setBuildings(com.dillon.starsectormarines.battle.world.model.Buildings buildings) {
        fogOfWar.setBuildings(buildings);
    }
    public void addDoodad(Doodad d) { doodadService.addDoodad(d); }

    /** Publishes rendered nature-overlay metadata into tactical and ballistic cover without adding render doodads. */
    public void addNatureOverlayCover(TileRegistry registry) {
        doodadService.addNatureOverlayCover(topology, registry);
    }

    /** Directional doodad cover at (x, y) against a threat in direction {@code (fromDx, fromDy)} (offset from this cell to the threat). 0 if no doodad covers that facing. */
    public int getDoodadCoverAt(int x, int y, int fromDx, int fromDy) {
        return doodadService.getDoodadCoverAt(x, y, fromDx, fromDy);
    }

    public int getDoodadCoverAtFacing(int x, int y, int facing) {
        return doodadService.getDoodadCoverAtFacing(x, y, facing);
    }

    /** Direction-agnostic doodad cover at (x, y) — max across all 4 facings. Back-compat accessor for {@link com.dillon.starsectormarines.battle.decision.TacticalScoring#findFallbackPosition} and other callers that don't carry a threat direction. */
    public int getDoodadCoverAt(int x, int y) {
        return doodadService.getDoodadCoverAt(x, y);
    }
    /** Parked vehicles that occupy multi-cell footprints. Cells were flagged non-walkable at setup time, so the sim doesn't need to consult this list for pathing/LOS — only the renderer does. */
    public List<ParkedAircraft> getParkedAircraft() { return parkedAircraft; }
    public void addParkedAircraft(ParkedAircraft aircraft) { parkedAircraft.add(aircraft); }
    /** Persistent visual decals — bullet holes, craters, rubble. Pure render data; combat ignores them. */
    public java.util.Collection<Decal> getDecals() { return effects.getDecals(); }
    /** Monotonic count of decals ever added. Read by the render layer's accumulator to drive incremental stamping that survives FIFO eviction at the cap. */
    public long getDecalsEverAdded() { return effects.getDecalsEverAdded(); }
    public void addDecal(Decal d) { effects.addDecal(d); }
    /** Smoke-puff events emitted by smoking wrecks during the last advance. Each entry is {x, y, radiusCells}. Drained by the renderer per frame. */
    public List<float[]> getSmokePuffsThisFrame() { return effects.getSmokePuffsThisFrame(); }
    /** Fire-burst events emitted by smoking wrecks during the last advance (burn phase only). Each entry is {x, y, radiusCells}. Drained by the renderer per frame. */
    public List<float[]> getFireBurstsThisFrame() { return effects.getFireBurstsThisFrame(); }
    public List<float[]> getHeavyImpactsThisFrame() { return effects.getHeavyImpactsThisFrame(); }
    /** Rounds an aircraft (or any other carrier) put on the ground during the last advance. Presentation only — the delivery itself already resolved. */
    public List<OrdnanceRelease> getOrdnanceReleasesThisFrame() { return effects.getOrdnanceReleasesThisFrame(); }
    /** Wall-collapse dust-burst events queued this advance. Each entry is {x, y} at the collapsed cell's center. Drained once per frame by the host into {@code ImpactFx.spawnWallCollapse}, so a collapse looks the same however it happened. */
    public List<float[]> getWallDustsThisFrame() { return effects.getWallDustsThisFrame(); }

    /** Live smoking wrecks. Read-only view (consumed by the demolition/crash tests). */
    public List<SmokingWreck> getSmokingWrecks() { return effects.getSmokingWrecks(); }
    /** Fighter wings committed to this battle. {@code AirCoverSystem} reads this each tick and flies each wing's schedule as off-map sorties. Defaults to {@link FlybyRoster#EMPTY}; missions assign via {@link #setFlybyRoster}. */
    public FlybyRoster getFlybyRoster()    { return flybyRoster; }
    public void setFlybyRoster(FlybyRoster roster) {
        requireInternalAir("setFlybyRoster");
        this.flybyRoster = roster != null ? roster : FlybyRoster.EMPTY;
    }

    public GroundRosterProfile getGroundRoster() { return groundRoster; }
    public void setGroundRoster(GroundRosterProfile groundRoster) {
        if (groundRoster == null) throw new IllegalArgumentException("groundRoster");
        if (this.groundRoster != null && this.groundRoster != groundRoster) {
            throw new IllegalStateException("battle ground roster is already frozen as "
                    + this.groundRoster.id());
        }
        this.groundRoster = groundRoster;
    }

    /** Who owns the air layer for this battle. See {@link AirProvider}. */
    public AirProvider getAirProvider() { return airProvider; }
    /**
     * Declare who owns the air. {@link AirProvider#EXTERNAL} hands the air off to the
     * combat-bridge host: the internal air tick is skipped and internal air-install
     * ({@link #spawnShuttle}/{@link #attachAirTurrets}/{@link #setFlybyRoster}) fails loud.
     * Set once at setup, before any air is installed.
     */
    public void setAirProvider(AirProvider provider) {
        this.airProvider = provider != null ? provider : AirProvider.INTERNAL;
    }

    /** Guards the internal-air installers — they are a contract violation under {@link AirProvider#EXTERNAL}, where the host owns the air. */
    private void requireInternalAir(String op) {
        if (airProvider != AirProvider.INTERNAL) {
            throw new IllegalStateException(op + " requires AirProvider.INTERNAL; this sim is "
                    + airProvider + " (the host owns the air — deliver via external events instead).");
        }
    }
    public List<ShotEvent> getActiveShots(){ return shots.getActiveShots(); }

    /** Thread-safe snapshot of active shots for callers iterating during the parallel UPDATE_UNITS dispatch. See {@link com.dillon.starsectormarines.battle.combat.ShotService#snapshotActiveShots()}. */
    public List<ShotEvent> snapshotActiveShots() { return shots.snapshotActiveShots(); }
    public List<ShotEvent> getShotsThisFrame() { return shots.getShotsThisFrame(); }
    /** Shots whose lifetime ended this advance — the "projectile arrived" event. Renderer reads this to spawn impact FX + arrival sounds at the moment a turret-shot sprite reaches its endpoint. */
    public List<ShotEvent> getShotsExpiredThisFrame() { return shots.getShotsExpiredThisFrame(); }
    /** In-flight {@link Projectile}s — slow-velocity AoE kinds. Renderer reads positions for sprite + contrail drawing. */
    public List<Projectile> getActiveProjectiles() { return shots.getActiveProjectiles(); }
    /** Phase-start hazard view during member updates. See {@link ShotService#snapshotActiveProjectiles()}. */
    public List<Projectile> snapshotActiveProjectiles() { return shots.snapshotActiveProjectiles(); }
    /** Fresh launches remain visible to coordination without changing hazard perception mid-phase. */
    public Iterable<Projectile> committedProjectiles() { return shots.committedProjectiles(); }
    /** Projectiles that arrived this tick — parallel to {@link #getShotsExpiredThisFrame} for the renderer's impact-FX dispatch. */
    public List<Projectile> getProjectilesArrivedThisFrame() { return shots.getProjectilesArrivedThisFrame(); }
    /**
     * The shot / projectile / pending-impact service, for callers that drive a
     * sub-phase of the tick directly instead of running {@link #tick()}. Today
     * that is the balance TTK harness, which fires through
     * {@link #fireShot(long, long, FireStance)} and then drains the round's
     * delayed damage itself so a measurement isn't perturbed by AI, movement,
     * or morale. Same service-direct exposure as {@link #getRoster()}.
     */
    public ShotService getShots() { return shots; }
    public LongList getDeathsThisFrame()         { return deathsThisFrame; }

    /**
     * Actors whose integral system was spent during the last {@link #advance(float)}
     * call. Presentation-only, with the {@link #getDeathsThisFrame()} lifecycle:
     * the audio tier plays one positional cue per entry and nothing else reads it.
     */
    public LongList getSystemActivationsThisFrame() { return systemFxSystem.activationsThisFrame(); }
    /** Presentation event seam for friendly-fire radio callouts; values may repeat when several rounds land in one frame. */
    public IntList getFriendlyFireSquadsThisFrame() { return friendlyFireSquadsThisFrame; }
    public boolean isComplete()            { return complete; }
    public Faction getWinner()             { return winner; }

    /**
     * Controls mission terminal evaluation without disabling the simulation.
     * Embedded rooms use a real battle clock and physics but have no winner,
     * so they opt out once during setup.
     */
    public void setMissionCompletionEnabled(boolean enabled) {
        missionCompletionEnabled = enabled;
        if (!enabled) {
            complete = false;
            winner = null;
        }
    }
    /** Per-cell unit count, indexed by {@link NavigationGrid#index(int, int)}. Exposed for AI scoring; do not mutate directly — go through {@link #setPath}. */
    public byte[] getOccupancyMap()        { return occupancyMap; }
    /** Bucketed spatial index over alive units. Rebuilt at the top of each tick by {@link #tick()}. */
    public UnitSpatialIndex getUnitIndex() { return unitIndex; }
    /** Monotonic tick counter, for time-parametrized AI motion (drone orbit phase, etc.). */
    public int getSimTickIndex() { return simTickIndex; }
    /**
     * The live entity roster for SoA consumers — bulk readers that iterate
     * {@code [0, liveCount())} over {@link UnitRosterService#denseArray()} (or
     * gate ids via {@link UnitRosterService#isLive(long)}) and pull
     * component columns by id through {@link #world()}. Same roster instance
     * {@link #targetOf(long)} and the spatial-index rebuilds use; exposed here
     * so static scorers (TacticalScoring, etc.) can match the established consumer
     * pattern without threading the roster service through every helper signature.
     */
    public UnitRosterService getRoster() { return rosterService; }

    @Override
    public BodyService bodies() { return rosterService.bodies(); }

    /**
     * Data owner for the {@code TELEMETRY} component: what each combatant did
     * this battle. Service-direct, like {@link #getShots()}. Lifecycle-stable,
     * so the end-of-battle gather reads it long after the dead were released.
     */
    public CombatTelemetryService telemetry() { return rosterService.telemetry(); }
    /**
     * Returns the entity id {@code u} is currently targeting, or {@code 0L} when
     * none is set <em>or the target is no longer live</em>. The lazy-validity
     * gate: a target released since it was locked isn't proactively cleared from
     * {@code world.targetId(u)}, so a stale id resolves to {@code 0L} here (the
     * long-native replacement for the old {@code getOrNull(targetId) == null}
     * check) — callers treat {@code 0L} as "no target, re-acquire".
     */
    public long targetOf(long u) {
        long t = world.targetId(u);
        return resolveUnit(t);
    }

    /**
     * Resolves an arbitrary entity id to itself if it's a live roster unit, or
     * {@code 0L} when the id is unknown / released. The long-native liveness gate
     * (the old {@code getOrNull(id) != null} check) — used by readers of id-typed
     * combat/secondary cross-references (the {@code COMBAT.burstTargetId} / {@code
     * SECONDARY_WEAPON.aimTargetId} world columns, {@code TURRET_STATE.burstTargetId}
     * via {@code TurretStateService}) where there's no companion holder unit to thread.
     */
    public long resolveUnit(long id) {
        return rosterService.bodies().isTargetable(id) ? id : 0L;
    }

    @Override
    public boolean canEngage(long shooterId, long candidateId) {
        return engagement.canEngage(shooterId, candidateId);
    }

    @Override
    public boolean isHardenedTarget(long id) {
        return rosterService.bodies().isTargetable(id)
                && tacticalScoring.isHardenedTarget(id);
    }
    /** Bucketed spatial index over alive units keyed on path destination (not current cell). Rebuilt alongside {@link #unitIndex} each tick. */
    public UnitDestinationSpatialIndex getDestIndex() { return destIndex; }
    /** Per-phase wall-clock profile of the most recent completed window of ticks. Read by the {@code TickProfileDebugPanel} HUD overlay + dump-to-disk button. */
    public TickProfile getTickProfile() { return tickProfile; }
    /** Per-tick sub-step profile (per-behavior + per-primitive nanos). Reset every tick; snapshotted onto the spike record when one fires. Read by the JSON dumper. */
    public TickInnerProfile getTickInnerProfile() { return tickInnerProfile; }
    /** Opt-in detail for the serial squad-replan pass. */
    public SquadReplanSystem getSquadReplanSystem() { return squadReplan; }
    /** Opt-in detail for the parallel unit-update dispatch. */
    public UnitUpdateSystem getUnitUpdateSystem() { return unitUpdate; }
    /** Shared scoring service — target selection, firing-position, fallback, cover queries. Thread-safe for reads (constructor-injected immutable service refs). */
    public com.dillon.starsectormarines.battle.decision.TacticalScoring getTacticalScoring() { return tacticalScoring; }
    /** Per-hit response logic — fallback rolls + target-reprioritization rolls. */
    public HitResponseSystem getHitResponseSystem() { return hitResponse; }
    /** Death-event handler for destroyed drone hubs — exposes {@code isDemolished(id)} for tests, replacing the old subclass's {@code demolished} field. */
    public com.dillon.starsectormarines.battle.drone.HubDemolitionSystem getHubDemolitionSystem() { return hubDemolition; }
    /** Death-event handler for destroyed turrets — exposes {@code isDemolished(id)} for tests, replacing the old subclass's {@code demolished} field. */
    public com.dillon.starsectormarines.battle.turret.TurretDemolitionSystem getTurretDemolitionSystem() { return turretDemolition; }
    /** Delegates to {@link UnitRosterService#getSquad(int)}. Synchronized lookup; safe to call from the parallel UPDATE_UNITS dispatch (concurrent {@link #mintSquad} from drone-hub spawns publishes through the same monitor). */
    public Squad getSquad(int id) {
        return rosterService.getSquad(id);
    }
    /** All squads currently registered. Used by the per-tick alert update; behaviors should read individual squads via {@link #getSquad(int)} keyed off {@code squadId}. */
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

    /**
     * How far each cell is from the thing the battle is about, or {@code null}
     * on a map with nothing to hold.
     *
     * <p>Set once by {@code BattleSetup.buildMap} from the generator's own
     * answer. The reinforcement layer is handed its own reference at install
     * time and does not read this; what needs it here is the trace, which
     * labels every compound with the band it stands in so a balance run can say
     * <em>where</em> captures happened rather than only how many there were.
     * Read-only map data, like the tactical map and the buildings beside it.
     */
    public void setFrontDepth(FrontDepth frontDepth) { this.frontDepth = frontDepth; }

    /** Where the front is, or {@code null} on a map that states none. */
    public FrontDepth getFrontDepth() { return frontDepth; }

    private FrontDepth frontDepth;

    /**
     * Spawn a ground-roster unit from an {@link EntitySpec} (identity-collapse Phase C):
     * mints the unit + seeds its world columns via {@link UnitRosterService#spawn},
     * registers it as a fog contributor if its faction contributes vision, and returns
     * the minted id. The single immediate-spawn seam.
     */
    public long spawn(EntitySpec spec) {
        long id = rosterService.spawn(spec);
        if (fogOfWar.getVisionState().isContributor(rosterService.identity().faction(id))) {
            fogOfWar.addContributor(id, rosterService);
        }
        return id;
    }

    /**
     * Deferred spec spawn — the {@link #spawn(EntitySpec)} twin for callers inside the
     * parallel UPDATE_UNITS dispatch. Serial callers adopt inline (fog registered here);
     * parallel callers are queued and get {@code 0L} back (the id is minted at the
     * APPLY_SPAWNS drain, where {@link #flushPendingSpawns} registers fog). Returns the
     * minted id, or {@code 0L} for the queued path.
     */
    public long queueSpawn(EntitySpec spec) {
        long id = rosterService.queueSpawn(spec);
        if (id != 0L && fogOfWar.getVisionState().isContributor(rosterService.identity().faction(id))) {
            fogOfWar.addContributor(id, rosterService);
        }
        return id;
    }

    /** Mirrors queued drone-hub spawns into the units list. Delegates to {@link UnitRosterService#flushPendingSpawns()} (which mints the ids); registers fog-of-war contributors for any player-faction spawns. */
    private void flushPendingSpawns() {
        LongList spawned = rosterService.flushPendingSpawns();
        for (int i = 0, n = spawned.size(); i < n; i++) {
            long id = spawned.getLong(i);
            if (fogOfWar.getVisionState().isContributor(rosterService.identity().faction(id))) {
                fogOfWar.addContributor(id, rosterService);
            }
        }
    }

    /**
     * Delegates to {@link UnitRosterService#releaseFromRegistry(long)}. Two
     * known production callers (the death cascade in
     * {@link com.dillon.starsectormarines.battle.combat.DamageResolver} and
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

    /**
     * Takes a living unit off the battlefield entirely — see
     * {@link UnitRosterService#takeOffTheField}. Distinct from
     * {@link #releaseFromRegistry} above, which is the death path's half and
     * deliberately leaves the row for the corpse transmute.
     */
    @Override
    public void takeOffTheField(long entityId) {
        rosterService.takeOffTheField(entityId);
    }

    public long spawnShuttle(ShuttleType type, Faction faction,
                             float lzX, float lzY, float entryX, float entryY,
                             float exitX, float exitY, float pendingDelay) {
        requireInternalAir("spawnShuttle");
        return airSystem.spawn(type, faction, lzX, lzY, entryX, entryY, exitX, exitY, pendingDelay);
    }

    public long spawnShuttle(ShuttleType type, Faction faction,
                             float lzX, float lzY, float entryX, float entryY,
                             float exitX, float exitY, float pendingDelay,
                             int seatsPerSortie) {
        return spawnShuttle(type, type, faction, lzX, lzY, entryX, entryY,
                exitX, exitY, pendingDelay, seatsPerSortie);
    }

    /** A shuttle of {@code type} flying as {@code frame}; see {@code AirSystem.spawn}. */
    public long spawnShuttle(ShuttleType type, Airframe frame, Faction faction,
                             float lzX, float lzY, float entryX, float entryY,
                             float exitX, float exitY, float pendingDelay,
                             int seatsPerSortie) {
        requireInternalAir("spawnShuttle");
        return airSystem.spawn(type, frame, faction, lzX, lzY, entryX, entryY,
                exitX, exitY, pendingDelay, seatsPerSortie);
    }

    /** Puts an aircraft with nothing in its hold into the air; see {@code AirSystem.spawnSortie}. */
    public long spawnSortie(Airframe frame, Faction faction,
                            float lzX, float lzY, float entryX, float entryY,
                            float exitX, float exitY, float pendingDelay) {
        requireInternalAir("spawnSortie");
        return airSystem.spawnSortie(frame, faction, lzX, lzY, entryX, entryY,
                exitX, exitY, pendingDelay);
    }

    public void addConvoyVehicle(VehicleType type, Faction faction, VehicleMission mission) {
        groundSystem.add(type, faction, mission);
    }

    /** Unattributed damage: no entity is credited. See {@link #applyDamage(long, long, float, float, float)}. */
    public void applyDamage(long target, float damage, float penetration) {
        applyDamage(target, CombatTelemetryService.NO_ATTACKER, damage, penetration, 1.0f);
    }

    /** Unattributed damage: no entity is credited. See {@link #applyDamage(long, long, float, float, float)}. */
    public void applyDamage(long target, float damage, float penetration, float moraleImpact) {
        applyDamage(target, CombatTelemetryService.NO_ATTACKER, damage, penetration, moraleImpact);
    }

    /**
     * Damage entry point, crediting {@code attackerId} in the target's and the
     * attacker's {@code TELEMETRY} record. Pass
     * {@link CombatTelemetryService#NO_ATTACKER} when nothing in the sim is
     * responsible; the id changes nothing about what the hit does.
     */
    public void applyDamage(long target, long attackerId, float damage, float penetration, float moraleImpact) {
        damageService.applyDamage(target, attackerId, damage, penetration, moraleImpact);
    }

    /** Drains all damage queued this tick. Delegates to {@link DamageService#flushPendingDamage()}. */
    private void flushPendingDamage() {
        damageService.flushPendingDamage();
    }

    public void postShot(ShotEvent shot) {
        shots.postShot(shot);
    }

    /** Read-only view of in-flight rocket / missile detonations. Used by squad-coordination scorers (avoid rocket volleys against an already-doomed turret). */
    public List<PendingDetonation> getInflightDetonations() {
        return detonations.getPending();
    }

    /**
     * Thread-safe snapshot of in-flight detonations — same justification as
     * {@link #snapshotActiveShots()}. {@link Detonations#queue} synchronizes
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
     * resolved (a projectile whose own visual already covers the flight time, detonating on
     * contact with its target's AoE radius). Same damage / wall / roof / dust
     * pipeline as a queued detonation — just without the timer delay.
     */
    public void detonateNow(PendingDetonation det) {
        detonations.detonateNow(det);
    }

    @Override
    public void spawnHeavyImpact(float x, float y, float radius) {
        effects.spawnHeavyImpact(x, y, radius);
    }

    /** Drains target-side reprio / fall-back enqueues from this tick's weapon hits. Delegates to {@link DamageService#flushPendingTargetMutations()}. */
    private void flushPendingTargetMutations() {
        damageService.flushPendingTargetMutations();
    }

    /** Inline reprio write — invoked by the damage service on the serial path AND on the queued path. Clears the targetId only if it still matches {@code expectedTargetId} (the race-check now lives here, registry-side, instead of a no-arg {@code target.getTargetId()} read in the flush drain); the next behavior tick re-picks via {@code findBestTarget}. */
    private void writeReprioInline(long targetId, long expectedTargetId) {
        if (world.targetId(targetId) == expectedTargetId) world.setTargetId(targetId, 0L);
    }

    /** Inline fallback write — invoked by the damage service on the serial path AND from the queued-flush. Writes the 3 fb fields and clears the stale path so the target re-paths to the fall-back cell on its next updateUnit pass. */
    private void writeFallbackInline(long targetId, int fbX, int fbY) {
        world.setFallbackCell(targetId, fbX, fbY);
        world.setFallbackTimer(targetId, HitResponseSystem.FALLBACK_DURATION);
        clearPath(targetId);
    }

    public int mintSquad(Faction faction, long leaderId) {
        return rosterService.mintSquad(faction, leaderId);
    }

    public int mintSquad(Faction faction, UnitType type) {
        return rosterService.mintSquad(faction, type);
    }

    public void addObjective(Objective o) {
        objectivesService.addObjective(o);
    }

    /**
     * Subscribe to the death-event mailbox — the sim→consumer channel for "a unit
     * died." Forwards to {@link com.dillon.starsectormarines.battle.unit.DeathDispatcher#subscribe};
     * the handler fires on the per-tick {@code drain()} (the DEMOLISH phase), same
     * timing the built-in demolition handlers see. Subscribe once at setup — there
     * is no unsubscribe.
     *
     * <p>Exists for out-of-tree consumers (the combat-bridge adapter) that must
     * react to sim death without importing the dispatcher or scanning the roster,
     * keeping the dependency one-way: the adapter knows the sim, the sim never
     * knows the adapter.
     */
    public void subscribeDeath(java.util.function.Consumer<com.dillon.starsectormarines.battle.unit.DeathEvent> handler) {
        deathDispatcher.subscribe(handler);
    }

    /** Install (or replace) the strategic commander for one faction. Pass {@code null} to clear. Typically called once during {@code BattleSetup} per faction that wants the layer. */
    public void setCommander(Faction faction, MissionCommand commander) {
        commanders.setCommander(faction, commander);
    }

    /** Installs a frame-only strategy with its trusted battle disclosure. */
    public <F extends CommandFrame, D> void setAutonomousCommander(
            Faction faction, AutonomousMissionCommand<F, D> commander,
            CommandFrameDisclosure<F> disclosure) {
        commanders.setAutonomousCommander(faction, commander, disclosure);
    }

    /** The commander for {@code faction}, or {@code null} if none is wired. Read by debug UI and by integration tests that poke at commander state directly. */
    public CommandStrategy getCommander(Faction faction) {
        return commanders.getCommander(faction);
    }

    /** Latest post-commit autonomous command snapshot for one perspective. */
    public CommanderSnapshot<?> getCommanderSnapshot(Faction faction) {
        return commanders.snapshot(faction);
    }

    public void setCommandTraceEnabled(boolean enabled, String fixtureKind) {
        if (enabled && commandTrace == null) {
            commandTrace = new CommandTraceRecorder(fixtureKind,
                    commandTraceSchedulerMode(), simTickIndex);
            commandTraceEnabled = true;
            commandTrace.sample(this);
            return;
        }
        if (enabled && commandTrace.isSealed()) {
            commandTraceEnabled = false;
            return;
        }
        if (enabled == commandTraceEnabled) return;
        if (enabled) {
            commandTrace.recordCaptureResumed(simTickIndex);
            commandTraceEnabled = true;
            commandTrace.sample(this);
            return;
        }
        commandTrace.recordCapturePaused(simTickIndex);
        commandTraceEnabled = enabled;
    }

    public boolean isCommandTraceEnabled() {
        return commandTraceEnabled;
    }

    public String getCommandTraceJsonLines() {
        return commandTrace != null ? commandTrace.canonicalJsonLines() : "";
    }

    /** Labels an external headless-run bound without fabricating a winner. */
    public void recordCommandTraceTimeout(int maxTicks) {
        if (commandTraceEnabled && commandTrace != null && !complete) {
            commandTrace.recordTimeout(simTickIndex, maxTicks);
            commandTraceEnabled = false;
        }
    }

    private void recordCommandTraceCasualty(DeathEvent event) {
        if (!commandTraceEnabled || commandTrace == null) return;
        long unitId = event.unitId();
        commandTrace.recordCasualty(simTickIndex, unitId,
                identity().faction(unitId), identity().type(unitId),
                event.cellX(), event.cellY());
    }

    private static String commandTraceSchedulerMode() {
        int minimumParallel = UnitUpdateSystem.configuredMinimumParallelUnits();
        return minimumParallel == Integer.MAX_VALUE
                ? "SERIAL_DETERMINISTIC" : "PRODUCTION_SCHEDULER";
    }

    /**
     * Current authoritative command-ledger entry for {@code squadId}. Unlike
     * a commander snapshot, this is also available for externally owned
     * squads in missions without an autonomous commander.
     */
    @Override
    public CommandDirective getSquadCommandDirective(int squadId) {
        return commanders.activeDirective(squadId);
    }

    /**
     * What {@code squadId} goes back to when the lease standing on it ends, or
     * {@code null} when nothing is being held for it. While a player's order
     * stands, this is the commander's own directive — the one
     * {@link #getSquadCommandDirective} is temporarily not reporting.
     */
    @Override
    public CommandDirective getShelvedSquadDirective(int squadId) {
        return commanders.assignments().shelvedDirective(squadId);
    }

    /** The per-faction commander tier, and through it the assignment ledger. */
    public CommanderService getCommanderService() {
        return commanders;
    }

    @Override
    public void claimSquadCommand(int squadId, CommandAuthority authority,
                                  String issuer, String reason) {
        commanders.assignments().claimExternal(requireSquad(squadId), authority,
                issuer, reason, simTickIndex);
    }

    @Override
    public void assignSquadCommand(ObjectiveAssignment assignment,
                                   CommandAuthority authority,
                                   String issuer, String reason) {
        commanders.assignments().assignExternal(
                requireSquad(assignment.squadId()), assignment, authority,
                issuer, reason, simTickIndex);
    }

    @Override
    public boolean handoffSquadCommand(int squadId, String currentIssuer,
                                       CommandAuthority nextAuthority,
                                       String nextIssuer,
                                       ObjectiveAssignment nextAssignment,
                                       String reason) {
        return commanders.assignments().handoff(requireSquad(squadId),
                currentIssuer, nextAuthority, nextIssuer, nextAssignment,
                reason, simTickIndex);
    }

    @Override
    public boolean releaseSquadCommand(int squadId, String issuer,
                                       String reason) {
        return commanders.assignments().releaseExternal(requireSquad(squadId),
                issuer, reason, simTickIndex);
    }

    private Squad requireSquad(int squadId) {
        Squad squad = getSquad(squadId);
        if (squad == null) {
            throw new IllegalArgumentException("unknown squad " + squadId);
        }
        return squad;
    }

    @Override
    public CommanderInfluenceSnapshot getCommanderInfluence(Faction faction) {
        // Production reads request a future refresh and keep the previous
        // paired snapshot. Publication happens only at the serial tick boundary.
        commanderInfluence.tick(simTickIndex);
        return commanderInfluence.snapshot(faction);
    }

    /** Non-advancing diagnostic read of the most recently published field. */
    public CommanderInfluenceSnapshot peekCommanderInfluence(Faction faction) {
        return commanderInfluence.snapshot(faction);
    }

    public CommanderInfluenceService.Metrics getCommanderInfluenceMetrics() {
        return commanderInfluence.metrics();
    }

    /** Reinforcement service for trigger / means registration. {@code BattleSetup} populates this per mission. */
    public com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementService getReinforcementService() {
        return reinforcement;
    }

    /** Compound capture-state registry. Read by slice-2 marker renderer, slice-3 trigger/means gates, and slice-4 win-condition objective. Initialized from {@link TacticalMap} during {@link #setTacticalMap}. */
    /** The garrison airfield's berths — read by reinforcement as a supply question, by the render pass, and by tests. */
    @Override
    public AirfieldService getAirfieldService() {
        return airfieldService;
    }

    public CompoundService getCompoundService() {
        return compoundService;
    }

    /**
     * The marine landing pads this battle was set up on, in arrival order.
     *
     * <p>Map geometry is consumed at setup and not otherwise retained: the
     * berths a lift comes down on are read out of the {@code MapResult},
     * turned into shuttle missions and pad doodads, and then thrown away. That
     * leaves an offline review with no way to say where the marines came
     * ashore except by re-deriving it from the generator, which would be a
     * second answer that can disagree with the one the battle actually used.
     * So {@code BattleSetup} hands the pads it chose over here, and this list
     * is exactly those — never every berth the map authored.
     *
     * <p>Read-only, and read only by review and diagnostic code. Nothing in
     * the tick loop consults it.
     */
    public List<LandingPad> getMarineLandingPads() {
        return marineLandingPads;
    }

    /** Records the pads setup landed the marines on. {@code BattleSetup} owns the call. */
    public void setMarineLandingPads(List<LandingPad> pads) {
        this.marineLandingPads = pads == null ? List.of() : List.copyOf(pads);
    }

    /**
     * The lanes this battle's map laid: each one's places from the beachhead to
     * the objective, and the road route through them. Empty on a map with none.
     *
     * <p>Here for the same reason {@link #getMarineLandingPads()} is. Map
     * geometry is consumed at setup and thrown away, so a review or a diagnostic
     * asking which way a lane runs would otherwise have to re-derive the answer
     * from the generator — a second answer that can disagree with the one the
     * battle was built on. The commander's own lane chain will read this too;
     * until then nothing in the tick loop consults it.
     */
    public List<LaneRoute> getLaneRoutes() {
        return laneRoutes;
    }

    /** Records the lanes the map laid. {@code BattleSetup} owns the call. */
    public void setLaneRoutes(List<LaneRoute> routes) {
        this.laneRoutes = routes == null ? List.of() : List.copyOf(routes);
    }

    public void setGarrisonSystem(CompoundGarrisonSystem system) {
        this.garrisonSystem = system;
    }

    /** The watches on this map's worked structures, or null where there are none. */
    public WorksCrewService getWorksCrews() {
        return worksCrews;
    }

    /** Installs the map's works watches. {@code BattleSetup} owns the call. */
    public void setWorksCrews(WorksCrewService crews) {
        this.worksCrews = crews;
    }

    /** What every vehicle bay on this map is building, or null where none does. */
    public FabricationService getFabrication() {
        return fabrication;
    }

    /** Installs the map's vehicle-bay stocks. {@code BattleSetup} owns the call. */
    public void setFabrication(FabricationService works) {
        this.fabrication = works;
    }

    /**
     * Installs the recapture-target recompute driver. {@code BattleSetup}
     * calls this from {@code installReinforcementLayer} on conquest maps
     * (biome layer present); left null elsewhere, in which case the tick
     * loop skips it entirely.
     */
    public void setRecaptureSystem(RecaptureTargetSystem system) {
        this.recaptureSystem = system;
    }

    /**
     * Installs the bulge counterattack state machine. {@code BattleSetup}
     * calls this from {@code installReinforcementLayer} on conquest maps
     * (biome layer present); left null elsewhere, in which case the tick
     * loop skips it entirely.
     */
    public void setCounterattackSystem(CounterattackSystem system) {
        this.counterattackSystem = system;
    }

    /** Player-facing read seam for the conquest counterattack HUD; null on missions without the system installed. */
    public CounterattackSystem getCounterattackSystem() {
        return counterattackSystem;
    }

    /** Per-faction resource pools (reinforcement tickets, airstrike tickets). {@code BattleSetup} reads this to wire up {@link CounterattackSystem}'s earmark debits. */
    public BattleResources getBattleResources() {
        return battleResources;
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
        friendlyFireSquadsThisFrame.clear();
        effects.beginFrame();
        systemFxSystem.beginFrame();
        directControl.validate();
        if (complete) return;
        tickAccumulator += dt;
        while (tickAccumulator >= TICK_DT) {
            tick();
            tickAccumulator -= TICK_DT;
            if (complete) break;
        }
    }

    /** Releases battle-owned worker resources after the simulation leaves service. */
    @Override
    public void close() {
        directControl.exit();
        commanderInfluence.close();
        if (asyncDefendTrackRoutes != null) asyncDefendTrackRoutes.close();
        navigation.close();
        unitUpdate.close();
        // The host thread participates in profiling/LoS work outside the
        // parallel dispatch, so release its slots at the same ownership edge.
        TickInnerProfile.releaseCurrentThread();
        navigation.releaseCurrentThreadLosCache();
    }

    private void tick() {
        simTickIndex++;
        // The ordinary phase profiler can report only after a tick returns.
        // Arm an out-of-band daemon around the whole tick so a permanent stall
        // still leaves every JVM thread and owned monitor in the common folder.
        try (TickStallWatchdog.TickGuard ignored =
                     TickStallWatchdog.watchTick(simTickIndex)) {
            tickGuarded();
        }
    }

    /** Fixed-tick phase pipeline, bracketed by {@link #tick()}'s stall watchdog. */
    private void tickGuarded() {
        // Backstop: if a caller (currently BattleSetup) hasn't registered
        // objectives, install the default eliminate-each-other pair so the
        // old behavior keeps working untouched. Run-once on first tick.
        // Each side counts the other and nothing else. An allied wipe is not a
        // marine defeat and does not end the battle; a marine wipe is, even
        // with allies still standing.
        objectivesService.installEliminationBackstopIfEmpty(
                Faction.MARINE, EnumSet.of(Faction.DEFENDER),
                Faction.DEFENDER, EnumSet.of(Faction.MARINE));
        // Start the per-tick phase profiler. Each lap() call below records
        // wall-time spent in the preceding block; endTick() at the bottom
        // snapshots into the rolling display buffer the debug panel reads.
        // Pass simTickIndex so the profile can gate JIT/load-time warmup.
        tickProfile.begin(simTickIndex);
        // Per-tick sub-step counters (behavior buckets + primitives like
        // pathfind / target-pick). Reset then advertised via the static
        // TickInnerProfile.current() slot so GridPathfinder / TacticalScoring
        // can record without threading the sim through their signatures.
        TickInnerProfile.resetAllWorkers();
        tickInnerProfile.reset();
        TickInnerProfile.setCurrent(tickInnerProfile);
        // Per-tick LoS cache + spatial-state setup — sweeps this grid's
        // worker LoS slots so cached pairs can't outlive a prior-tick wall
        // breach, then enables auto-init for the duration of the tick. Paired
        // with navigation.endTick() at the bottom.
        navigation.beginTick();
        // Zero applied velocity before either ambient or ordinary movement.
        // Ambient actors now use this same path follower instead of writing
        // POSITION directly, so their movement remains visible to facing and
        // separation exactly like every other ground actor.
        movement().beginTick(TICK_DT);
        directControl.validate();
        // Authored ambient work owns assigned actors only while its threat
        // policy remains quiet. It claims a destination, then advances through
        // ordinary navigation. Position first so occupancy, spatial indices,
        // and ordinary unit dispatch all observe the resulting physical pose.
        // A released actor falls through to its existing role this tick.
        ambientTasks.advance(TICK_DT);
        // Smoke lands/expires before perception so stationary observers recast
        // against the same opacity state direct-fire AI sees this tick.
        smokeFields.tick(TICK_DT);
        // Fog-of-war visibility pass — recomputed every 3rd tick (~10 Hz at
        // 30 Hz sim). The render path lerps current→target alpha per frame so
        // this cadence stays invisible. Host-projected temporary sources
        // (shuttles, fighters, recon pings) are pushed by BattleScreen.advance()
        // each frame before this call; simulation-carried ones (a running sensor
        // sweep) are republished by integralSystemSystem below, so they are read
        // here one tick later — well inside the vision cadence either way.
        fogOfWar.tick(simTickIndex, rosterService);
        tickProfile.lap(TickProfile.Phase.VISION);
        navigation.rebuildOccupancyMap(rosterService);
        tickProfile.lap(TickProfile.Phase.REBUILD_OCCUPANCY);
        // Rebuild the spatial index BEFORE the AI passes so per-tick scoring
        // (exposure, threat density, allies-near) reads a consistent
        // snapshot. Same single-pass-per-tick semantics as the attacker
        // index below — mid-tick repath shifts aren't reflected until next
        // tick, matching the pre-spatial behavior.
        navigation.rebuildSpatialIndices(rosterService);
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
        squadAlert.tick(TICK_DT, simTickIndex);
        tickProfile.lap(TickProfile.Phase.SQUAD_ALERT);
        tacticalScoring.updateContactPictures(simTickIndex);
        tickProfile.lap(TickProfile.Phase.CONTACT_PICTURE);
        // Age each side's memory of its own dead and republish what it makes
        // ground cost to cross, before the replan and the per-unit dispatch
        // that read it. Driven here rather than from a snapshot reader: every
        // repath consults this, and a battle nobody is watching still has to
        // forget.
        commanderInfluence.advanceCasualties(simTickIndex);
        commanderInfluence.advanceAsync(simTickIndex);
        // Divide a shared contact between the squads attacking it, after every
        // contact picture exists and before any of them plans against it — so
        // cooperating squads read one answer rather than each deciding it is
        // the one who should form the firing line.
        // One member's contact becomes the squad's before anything plans
        // against it: the cooperating group below reads it to know who is
        // looking at whom, and the advance reads it to know not to walk past
        // somebody a member is already face to face with.
        squadContactOnset.tick(simTickIndex);
        assaultCoordination.tick(simTickIndex);
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
        squadFallback.tick();
        tickProfile.lap(TickProfile.Phase.SQUAD_FALLBACK);
        // Commander-tier slow tick — runs before per-squad replan so any
        // assignment written this tick is visible to the GOAP relevance pass
        // below. Cadence + early-skip-when-empty live inside the registry.
        commanders.tick(TICK_DT, this);
        // A campaign squad still arriving by lift holds at its LZ. The form-up
        // execution accessor masks orders without deleting the command directive.
        // Must run after command so same-tick intent is retained under the mask.
        squadFormUp.tick(TICK_DT);
        // Player command powers — commit any activations the UI queued this
        // frame (pay command points + start cooldown + resolve the effect),
        // regen the pool, and age cooldowns + transient reveals down. Folds
        // into the COMMANDER region's lap; cost is trivial.
        commandPowerSystem.tick(TICK_DT);
        // Apply exact-mech doctrine changes and lance-wide cohesion orders
        // after strategic command has written assignments and before GOAP
        // replans. A changed member or lance therefore executes its new
        // battlefield manner on this same fixed tick.
        mechDoctrineSystem.tick(this);
        mechMoveOrderSystem.tick(this);
        squadMoveOrderSystem.tick(this);
        tickProfile.lap(TickProfile.Phase.COMMANDER);
        // Squad-level GOAP replan pass. See SquadReplanSystem class doc for
        // ordering + parallelism notes.
        long goapStageStart = System.nanoTime();
        directControl.validate();
        squadReplan.tick(this);
        tickInnerProfile.record(TickInnerProfile.Bucket.GOAP_SQUAD_REPLAN,
                System.nanoTime() - goapStageStart);
        // Capture assigned starts and build route fields while every member is
        // still at its tick-start position. Workers only read the published
        // fields; a step advanced during dispatch falls back until next tick.
        goapStageStart = System.nanoTime();
        List<SquadRouteRequest> routeRequests =
                SharedGoalPolicy.usesSquadRouteCorridors(liveUnitCount())
                        ? SquadRoutePreparationSystem.collect(this)
                        : List.of();
        tickInnerProfile.record(TickInnerProfile.Bucket.GOAP_ROUTE_COLLECTION,
                System.nanoTime() - goapStageStart);
        goapStageStart = System.nanoTime();
        navigation.prepareSquadRoutes(routeRequests, simTickIndex);
        tickInnerProfile.record(TickInnerProfile.Bucket.GOAP_ROUTE_PREPARATION,
                System.nanoTime() - goapStageStart);
        if (squadTraffic != null) {
            goapStageStart = System.nanoTime();
            squadTraffic.prepare(this, directControl.activeUnitId(), TICK_DT);
            tickInnerProfile.record(TickInnerProfile.Bucket.SQUAD_TRAFFIC_PREPARE,
                    System.nanoTime() - goapStageStart);
        }
        tickProfile.lap(TickProfile.Phase.GOAP_REPLAN);
        if (asyncDefendTrackRoutes != null) {
            asyncDefendTrackRoutes.beginTick(simTickIndex);
        }
        navigation.beginClearanceTick(simTickIndex);
        // Parallel per-unit dispatch — entity for-loop. See UnitUpdateSystem
        // class doc for the parallelism + ECS-promotion notes.
        // Ahead of the per-unit dispatch so an activation this tick is already
        // reflected in MOVEMENT_MOVE_SPEED when the mover steps, and an expiry
        // has already put the speed back.
        // Ahead of the integral sweep so a screen raised this tick spends its
        // whole authored duration instead of losing its first tick to this drain.
        mitigationSystem.tick(TICK_DT);
        integralSystemSystem.tick(TICK_DT, this);
        directControl.tick();
        navigation.beginSharedGoalPathSnapshot();
        try {
            unitUpdate.tick(this);
        } finally {
            navigation.endSharedGoalPathSnapshot();
        }
        tickProfile.lap(TickProfile.Phase.UPDATE_UNITS);
        // Apply occupancy + destIndex deltas queued by setPath during the
        // per-unit dispatch. Runs at the end of UPDATE_UNITS, before any
        // subsequent serial phase reads the spatial state. FIRING (below)
        // does call setPath — via a chained RepositionToCover — but that
        // runs AFTER this drain, so its occupancy delta applies inline by
        // design (DamageService.applyOccupancyDelta gates on insideParallel
        // only, deliberately not on the FIRING-phase deferral flag; see its
        // class doc). A single drain here still keeps the bookkeeping
        // consistent for the rest of the tick (and the next tick's
        // REBUILD_OCCUPANCY rebuilds from each unit's MOVEMENT path
        // regardless).
        flushPendingOccupancyDeltas();
        tickProfile.lap(TickProfile.Phase.APPLY_OCCUPANCY);
        // Threat steer then soft-collision relaxation. The first bends allied
        // infantry away from nearby aliens without replacing authored paths;
        // the second nudges body overlaps apart and steers coherently moving
        // infantry and mech squads toward terrain-scaled tactical formations.
        // Runs here (serial, after every UPDATE_UNITS position write has
        // landed) and before APPEARANCE (facingSystem/mechLocomotionSystem
        // read final POSITION). See SeparationSystem class doc.
        swarmAvoidance.tick(TICK_DT, directControl.activeUnitId());
        separation.tick(TICK_DT, directControl.activeUnitId());
        mechCollisionEscape.tick(TICK_DT);
        tickProfile.lap(TickProfile.Phase.SEPARATION);
        // Mirror queued drone-hub spawns into the units list. Only callers
        // running inside UPDATE_UNITS route through queueSpawn; AIR_SYSTEM /
        // GROUND_SYSTEM deboards keep using inline addUnit because they
        // already run in serial phases.
        flushPendingSpawns();
        tickProfile.lap(TickProfile.Phase.APPLY_SPAWNS);
        // Execute the fire intent behaviors queued this tick (EngagePosture
        // today; the sweep phase flips the rest) — cooldown/range/LoS gate,
        // fireShot, cooldown reset, beginBurst, optional reposition. Must
        // run before infantry.tick()'s burst continuation below so it sees
        // this tick's beginBurst state exactly as it did when postures fired
        // inline. See FiringSystem's class doc for the full contract.
        //
        // Bracketed in the combat-effect deferral window: FIRING runs
        // serially (no parallel-write hazard), but it sits ahead of this
        // tick's APPLY_DAMAGE drain — so damage/reprio/fallback triggered
        // here must still QUEUE, the same barrier fires used to hit back
        // when they ran inside the parallel UPDATE_UNITS dispatch. Restores
        // the doomed-unit-final-action / both-shooters-overkill / queued-
        // guarded-reprio semantics the inline-during-FIRING flip had
        // silently repealed. Occupancy (the reposition's setPath) is exempt
        // — see DamageService.deferCombatEffects's class doc.
        damageService.enterCombatEffectDeferral();
        try {
            firingSystem.tick(this);
        } finally {
            damageService.exitCombatEffectDeferral();
        }
        tickProfile.lap(TickProfile.Phase.FIRING);
        // Burst-fire rounds queued after a primary shot — fire them now so
        // they emit at the right per-weapon spacing without piling onto the
        // AI's single-decision-per-tick model. Lives on the InfantryWeapons
        // subsystem; this call drains every unit's burst state.
        infantry.tick();
        tickProfile.lap(TickProfile.Phase.INFANTRY_TICK);
        // Mech chassis weapons run on their own state bag (MechLoadoutComponent).
        // Continuation handling for chaingun bursts + SRM salvos, plus cooldown
        // tick-down for all three tracks. New triggers (start a burst / salvo /
        // LRM) come from CombatantBehavior. (The dead-mech smoking wreck now
        // fires off the MechWreckSystem death handler, not this pass.)
        heavy.tick(directControl.controlledMechId(), directControl.pointAim(), directControl.intent().firing(),
                directControl.selectedWeapon());
        tickProfile.lap(TickProfile.Phase.HEAVY_TICK);
        // Armed contact charges follow their target and resolve through the
        // ordinary AoE/durability pipeline when their fixed fuse expires.
        satchelCharges.tick(TICK_DT, this);
        // Point-defence emplacements mint, age, and engage here — deliberately
        // ahead of shots.tickProjectiles below, so a round marked intercepted
        // this tick is dropped on the same beat it would have detonated.
        pointDefense.tick(TICK_DT, this);
        // Cover screens build, age, and retire here. Nothing downstream of
        // this pass depends on the ordering: a screen changes only cover,
        // which every reader samples live at the moment a round resolves.
        deployedCover.tick(TICK_DT, this);
        closeContact.tick(this);
        com.dillon.starsectormarines.battle.infantry.FragGrenadeTactics.cleanupReservations(this);
        tickProfile.lap(TickProfile.Phase.SATCHELS);
        // Simulated-projectile path — advance each in-flight Projectile by dt,
        // detonate its onArrival payload when remainingTime hits zero, and
        // emit an arrival record for the renderer's impact-FX dispatch.
        // Runs BEFORE detonations.tick so a projectile arriving this tick
        // contributes its detonation to the same wave as legacy queue entries.
        shots.tickProjectiles(TICK_DT, detonations::detonateNow);
        tickProfile.lap(TickProfile.Phase.PROJECTILES);
        // Physics-based rocket/missile damage — each pending detonation ticks
        // down its arrival timer and applies splash + wall damage when it
        // expires. Pairs with the visual ShotEvent flight; the visual and the
        // damage are queued together and arrive together.
        detonations.tick();
        tickProfile.lap(TickProfile.Phase.DETONATIONS);
        // Drain all damage queued this tick — from UPDATE_UNITS direct fire,
        // FIRING (deferred per DamageService.enterCombatEffectDeferral),
        // INFANTRY_TICK / HEAVY_TICK burst continuations, PROJECTILES
        // arrivals, and DETONATIONS AoE. Single late drain (rather than one
        // after every damage-emitter) keeps the rule simple: damage applies
        // before any phase that reads alive-state — DEMOLISH /
        // DRONE_CRASHES / WIN_CHECK all run after this.
        // Trade-off: a target queued for death in UPDATE_UNITS OR FIRING is
        // still alive during the subsystem ticks this tick, so its burst
        // continuations fire one more round. Considered "doomed unit gets a
        // final action" — arguably more consistent than the pre-deferral
        // order-dependent skip and the prerequisite for parallelizing the
        // dispatch loop. FIRING's deferral (added alongside the FiringSystem
        // proving-slice critique fix) extends this same trade-off to shots
        // fired from the serial fire-intent phase, restoring the semantics
        // those shots had when they ran inline during UPDATE_UNITS.
        flushPendingDamage();
        // Drain target-side reprio / fall-back enqueues from this tick's
        // weapon hits. Ordered AFTER flushPendingDamage so we skip mutations
        // on targets the queued damage just killed (the drain resolves each
        // queued targetId and skips a null/released resolve). Shares the
        // APPLY_DAMAGE phase — both are serial fixups for state the parallel
        // UPDATE_UNITS dispatch couldn't touch.
        flushPendingTargetMutations();
        tickProfile.lap(TickProfile.Phase.APPLY_DAMAGE);
        // Drain the death mailbox: fan this tick's deaths out to the
        // subscribed handlers. Turret + hub demolition both react here — turret
        // demolition flips dead turret cells to walkable rubble (so next tick's
        // pathfinding + zone graph see the hole, and the floor pass picks the
        // cell up as rubble), hub demolition does the same for destroyed drone
        // hubs (static STRUCTUREs on sealed non-walkable cells — leaving the
        // cell sealed would orphan an invisible obstacle) and cascade-kills the
        // hub's drones. The drone-crash lifecycle migrates onto this drain in a
        // later slice. By this point flushPendingDamage has run, so every unit
        // that died this tick is fully dead — the handlers see the same settled
        // state the old end-of-tick scan did.
        deathDispatcher.drain();
        tickProfile.lap(TickProfile.Phase.DEMOLISH);
        // Drone crash sequence: advance every entity that has a CrashingComponent
        // component (attached in the death drain above) — spin the fall, drop a
        // SmokingWreck on impact, detach on settle. Runs after the demolition
        // drain so a hub destruction (which cascade-kills + publishes deaths for
        // its drones) gets those crashes attached on the same tick.
        droneCrashes.tick(TICK_DT);
        tickProfile.lap(TickProfile.Phase.DRONE_CRASHES);
        // Age smoking wrecks + emit any puff events that came due this tick.
        effects.tickWrecks(TICK_DT);
        tickProfile.lap(TickProfile.Phase.WRECKS);
        // Lingering smoke plumes parked at HE impact sites — same per-frame
        // puff drain as the wrecks, just on a shorter, fire-less timer.
        effects.tickPlumes(TICK_DT);
        tickProfile.lap(TickProfile.Phase.PLUMES);
        // Compound capture state machine — updates DEFENDER_HELD / CONTESTED /
        // MARINE_HELD state. Runs before resource production and reinforcement
        // so both see the freshest capture state this tick.
        compoundCapture.tick(TICK_DT, this, compoundService);
        // Before the field's own pass, so an airframe the crew finished this
        // tick is airworthy on this tick rather than on the next one.
        airfieldCrew.tick(TICK_DT, this, airfieldService);
        airfieldSystem.tick(TICK_DT, this, airfieldService);
        // After the berths, so a shed that just took an aircraft back is
        // airworthy in the same tick a strike might want it.
        airStrikeSystem.tick(TICK_DT, this);
        // The ship-side sibling: a bay puts a boat off the hull the way a field
        // puts an aircraft over the battle. Ticked unconditionally like the
        // strike system and, like it, does nothing where the place it is on has
        // nothing of its kind — a garrison hardstand has nowhere off this map to
        // send anything to.
        boatSorties.tick(TICK_DT, this, airfieldService);
        if (garrisonSystem != null) garrisonSystem.tick(TICK_DT, this, compoundService);
        // Resource production — alive compounds generate tickets (reinforcement,
        // airstrike) into per-faction pools. Ticked after capture so a
        // just-flipped compound stops producing immediately.
        battleResources.tick(TICK_DT, compoundService);
        // A vehicle bay is the one structure that makes something rather than
        // supplying it, and what it makes is counted off the technicians
        // actually welding rather than off this clock. Next to resource
        // production because it is the same kind of fact about a held building,
        // and after capture for the same reason.
        fabricationSystem.tick(TICK_DT, this, fabrication);
        // After capture, because who a replacement belongs to is read off this
        // tick's holder rather than last tick's, and before nothing in
        // particular: somebody walking on at the map edge has a long way to go.
        worksCrewSystem.tick(TICK_DT, this, worksCrews);
        tickProfile.lap(TickProfile.Phase.COMPOUND_ECONOMY);
        // Recapture-target recompute must precede the reinforcement trigger
        // poll below so FrontLineReinforcementTrigger dispatches against this
        // tick's fresh contested/open state, not last tick's.
        if (recaptureSystem != null) recaptureSystem.tick(TICK_DT, this);
        // Bulge counterattack must run after the recapture recompute (sees
        // this tick's fresh contested state for its muster/resolve checks)
        // and before the reinforcement dispatch below (its ASSAULT-phase
        // prepaid posts drain this same tick, not next).
        if (counterattackSystem != null) counterattackSystem.tick(TICK_DT, this);
        // Reinforcement slow-tick: poll triggers, drain the request queue, and
        // dispatch via the first feasible means provider. Dispatch debits
        // resource tickets; insufficient balance defers the request.
        reinforcementSystem.tick(TICK_DT, this);
        // The recapture recompute, the counterattack muster and the dispatch
        // above are one cluster — each reads the tick's fresh state from the one
        // before it — and all three can prove a convoy route, which is the
        // expensive thing in this stretch of the tick. Lapped together, and
        // apart from the air below, so a proof that blows up is charged where a
        // reader would look for it. See TickProfile.Phase.
        tickProfile.lap(TickProfile.Phase.REINFORCEMENT);
        // Air vehicles tick AFTER units so new deboarded marines aren't iterated
        // mid-loop. They'll be picked up by next tick's occupancy + target pass.
        // Internal air only — under AirProvider.EXTERNAL the host's real ships own the
        // air layer, so the sim runs no internal shuttle/flyby state machine.
        if (airProvider == AirProvider.INTERNAL) {
            // Before the state machine, so a wing dispatched this tick starts
            // flying on it rather than a tick late. Internal air only: a
            // corridor sortie is an internal air entity, and under
            // AirProvider.EXTERNAL the host's own ships are the air cover.
            airCoverSystem.tick(TICK_DT, this);
            airSystem.tick(TICK_DT);
        }
        tickProfile.lap(TickProfile.Phase.AIR_SYSTEM);
        // Ground convoys ride the same ordering rule for the same reason —
        // deboarded militia join the roster between ticks, not mid-loop.
        groundSystem.tick(TICK_DT, directControl.controlledVehicleId(), directControl.intent());
        tickProfile.lap(TickProfile.Phase.GROUND_SYSTEM);
        // Ballistic-round impact clock — a resolved round's damage/hit-response
        // applies here, on its flight-time delay, rather than inline at fire
        // time (see BallisticResolver / InfantryWeapons.fireShot). Runs before
        // tickShots so a round's damage lands the same tick its visual ShotEvent
        // expires. Outside the parallel dispatch and FIRING's deferral window,
        // so DamageService.applyDamage resolves inline through this sink rather
        // than re-queuing for a drain that already ran this tick.
        shots.tickImpacts(TICK_DT, this::applyPendingImpact);
        shots.tickShots(TICK_DT);
        tickProfile.lap(TickProfile.Phase.SHOTS);
        equipmentDropSystem.tick();
        tickProfile.lap(TickProfile.Phase.EQUIPMENT_DROPS);
        resupplySystem.tick(TICK_DT);
        civilianEvacuationSystem.tick(this);
        rescuePickupSupport.tick(TICK_DT, this);
        swarmReinforcements.tick(TICK_DT, this);
        objectivesService.tick(o -> o.tick(this));
        tickProfile.lap(TickProfile.Phase.OBJECTIVES);
        // Single navigation-topology drain for the whole tick — takes any wall
        // breaches or turret demolishes that happened this tick, rebuilds the
        // zone graph, then the mesh and the caches that hang off it. Multiple
        // breaches in one tick (e.g., a rocket shredding a wall section)
        // collapse into one rebuild. Lapped in two because the zone graph is
        // incremental and the derived layer beside it was not, and one lap over
        // both charged the cheap half for the dear one.
        boolean navigationTopologyFlushed = navigation.flushZoneTopologyIfDirty();
        tickProfile.lap(TickProfile.Phase.ZONE_GRAPH);
        if (navigationTopologyFlushed) navigation.rebuildDerivedNavigation();
        tickProfile.lap(TickProfile.Phase.NAV_FLUSH);
        if (missionCompletionEnabled) {
            WinCheckSystem.WinResult result =
                    winCheck.tick(objectivesService.getObjectives());
            if (result.complete()) {
                complete = true;
                winner = result.winner();
                // A terminal battle makes every registered civilian still outside
                // the evacuation boundary unsaved. Incomplete setup cannot seal,
                // preserving the no-report sentinel instead of scaling bad data.
                civilianEvacuation.seal();
            }
        }
        tickProfile.lap(TickProfile.Phase.WIN_CHECK);
        // Authors every live sheet-drawn unit's SPRITE (facing/pose frame) from
        // this tick's settled state — the tail placement is load-bearing: it
        // runs after every target/path/cooldown write and the air/ground
        // deboards above, and before any render read of SPRITE this frame.
        mechLocomotionSystem.tick(TICK_DT, directControl.controlledMechId());
        mechGaitSystem.tick(TICK_DT);
        mechTurretSystem.tick(TICK_DT, directControl.controlledMechId(), directControl.pointAim());
        directControl.validate();
        facingSystem.tick(directControl.activeUnitId(), directControl.intent().aimX(),
                directControl.intent().aimY());
        // Authors what a running integral system looks like, from the same
        // settled state and for the same reason: a treatment written here is
        // present on the tick of activation and gone on the tick the effect
        // expires, because both have already happened by the time it runs.
        systemFxSystem.tick();
        // FacingSystem authors the ordinary battle pose. Active ambient work
        // reasserts its narrower presentation contract afterwards, including
        // dry-fire use of the actor's already-issued primary weapon.
        ambientTasks.applyAppearance();
        tickProfile.lap(TickProfile.Phase.APPEARANCE);
        if (commandTraceEnabled) commandTrace.sample(this);
        // Tick barrier for the entity world: apply structural changes queued on
        // its command buffer during this tick's query walks. Today's structural
        // changes (corpse spawn is a walk-safe create; death transmute, air reap
        // and drone-crash run serially at their own barriers) don't queue — so this
        // drains an empty buffer. The barrier stays here intentionally: its first
        // real consumer is a column-walking system that destroys/transmutes
        // mid-Query-walk and MUST defer (the swap-pop-during-iteration trap). See
        // Structural changes flush at the phase barrier described by ecs-nouns.md.
        entityWorld.flush();
        tickProfile.endTick(simTickIndex, tickInnerProfile);
        // Clear the inner-profile slot so any stray call outside the tick
        // window (e.g., test harness, mid-frame UI hook) is a clean no-op
        // rather than silently writing into the previous tick's counters.
        TickInnerProfile.setCurrent(null);
        // Switch this grid's per-thread LoS caches off so off-tick callers see null
        // and fall through to live Bresenham (preserving the old off-tick
        // behavior that tests + UI hooks depend on).
        navigation.endTick();
    }

    /** Shared arrival seam for ordinary rounds and direct-contact explosive payloads. */
    private void applyPendingImpact(ShotService.PendingImpact impact) {
        if (!rosterService.isAliveById(impact.victimId)) return;
        int friendlyFireSquad = Squad.NO_SQUAD;
        if (impact.friendly
                && rosterService.identity().faction(impact.victimId) == Faction.MARINE
                && rosterService.squad().hasSquad(impact.victimId)) {
            friendlyFireSquad = rosterService.squad().squadId(impact.victimId);
        }
        rosterService.telemetry().recordRoundHit(impact.shooterId);
        damageService.applyDamage(impact.victimId, impact.shooterId, impact.damage,
                impact.penetration, impact.moraleImpact);
        if (friendlyFireSquad != Squad.NO_SQUAD && impact.damage > 0f) {
            friendlyFireSquadsThisFrame.add(friendlyFireSquad);
        }
        if (rosterService.isLive(impact.victimId)) {
            hitResponse.rollFallbackOnHit(impact.victimId);
            hitResponse.rollReprioritizeOnHit(impact.victimId, impact.shooterId);
        }
    }

    /** Delegates to {@link com.dillon.starsectormarines.battle.decision.AttackerIndexService#getAttackersOf(long)}. The list is mutated in-place each tick — callers must not retain it across tick boundaries. */
    public LongArrayList getAttackersOf(long target) {
        return attackerIndex.getAttackersOf(target);
    }

    /** Delegates to {@link NavigationService#getVantagePointsFor(int, int)}. Cached per-battle; invalidated in lockstep with the navigation-topology rebuild driven by {@link NavigationService#flushNavigationTopologyIfDirty}. */
    public int[][] getVantagePointsFor(int tx, int ty) {
        return navigation.getVantagePointsFor(tx, ty);
    }

    /**
     * Delegates to {@link NavigationService#setPath(long, int[])}. Kept on the
     * sim's surface so AI behaviors in {@code battle.ai} can route movement
     * through {@code sim.setPath(...)} — which keeps the occupancy/destIndex
     * bookkeeping in sync — rather than writing the MOVEMENT path by id directly.
     * Pass {@link GridPathfinder#EMPTY_PATH} (or call
     * {@link #clearPath(long)}) to drop the current path.
     */
    public void setPath(long u, int[] newPath) {
        navigation.setPath(u, newPath);
    }

    @Override
    public PathRequestStatus requestPath(long unit, int goalX, int goalY) {
        return navigation.requestPath(unit, goalX, goalY);
    }

    /** Occupancy-aware hierarchical route for ordinary one-off movement. */
    public int[] findPath(int startX, int startY, int goalX, int goalY) {
        return navigation.findPath(startX, startY, goalX, goalY);
    }

    /** Shared-goal path seam for dense target-pursuit behaviors. */
    public int[] findSharedPathToGoal(int startX, int startY,
                                      int goalX, int goalY) {
        return navigation.findSharedPathToGoal(startX, startY, goalX, goalY);
    }

    @Override
    public int[] findSharedPathToGoal(int startX, int startY,
                                      int goalX, int goalY,
                                      RouteCostField cost) {
        return navigation.findSharedPathToGoal(startX, startY, goalX, goalY,
                cost);
    }

    @Override
    public RouteCostField getRouteCostField(Faction faction) {
        return commanderInfluence.casualties().routeCost(faction);
    }

    @Override
    public int[] findSquadPathToGoal(int squadId, long routingEpoch, Object routeToken,
                                     int startX, int startY, int goalX, int goalY,
                                     RouteCostField cost) {
        return navigation.findSquadPathToGoal(squadId, routingEpoch, routeToken,
                startX, startY, goalX, goalY, cost);
    }

    @Override
    public boolean isSquadRoutePending(int squadId, long routingEpoch, Object routeToken,
                                       int goalX, int goalY) {
        return navigation.isSquadRoutePending(squadId, routingEpoch, routeToken, goalX, goalY);
    }

    public int lastSquadRouteOldestWaitTicks() { return navigation.lastSquadRouteOldestWaitTicks(); }

    public int lastSquadRouteAdmittedWaitTicks() { return navigation.lastSquadRouteAdmittedWaitTicks(); }

    public int lastSquadRouteDeferred() { return navigation.lastSquadRouteDeferred(); }

    /** Applies occupancy + destIndex deltas queued by {@link #setPath} during the per-unit dispatch. Delegates to {@link DamageService#flushPendingOccupancyDeltas()}. */
    private void flushPendingOccupancyDeltas() {
        damageService.flushPendingOccupancyDeltas();
    }

    /** Convenience: drop the unit's path. Delegates to {@link NavigationService#clearPath(long)}. */
    public void clearPath(long u) {
        navigation.clearPath(u);
    }

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
    // The dead-mech smoking wreck moved off HeavyWeapons onto the
    // MechWreckSystem death-event handler (one death seam, no per-tick scan).
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
     * Each surviving member gets a new {@code homeCellX} near
     * the new anchor (picked via {@link com.dillon.starsectormarines.battle.setup.BattleSetup#pickCellsNear} so cover is
     * preserved at the new post). {@link Squad#fallbackInProgress} is set so
     * {@link com.dillon.starsectormarines.battle.infantry.HoldPost} routes
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
    /** Delegates to {@link MovementService#advanceAlongPath(World, long, float)}. Kept so existing behavior call sites compile unchanged. */
    public void advanceMovement(long u) {
        if (identity().type(u).isMech()) {
            MovementService.MotionResult result = movement().advanceAlongPath(
                    world, u, TICK_DT, grid, physicalRadius(u));
            if (result == MovementService.MotionResult.BLOCKED) navigation.clearPath(u);
        } else {
            movement().advanceAlongPath(world, u, TICK_DT);
        }
    }

    @Override
    public void advanceSquadTravel(long member, Squad squad, int goalX, int goalY) {
        if (squadTraffic == null || !squadTraffic.advance(member, squad, goalX, goalY, TICK_DT)) {
            advanceMovement(member);
        }
    }

    /**
     * Stanced-fire convenience: most callers fire from a stationary position
     * (engage loops, garrisons, turrets, mech chassis) and don't need to
     * think about stance. Routes to {@link #fireShot(long, long,
     * com.dillon.starsectormarines.battle.combat.FireStance)} with
     * {@link com.dillon.starsectormarines.battle.combat.FireStance#STANCED}.
     * Callers firing while walking should call the stance-aware overload
     * with {@code MOVING} so the accuracy penalty applies.
     */
    public void fireShot(long shooter, long target) {
        fireShot(shooter, target, com.dillon.starsectormarines.battle.combat.FireStance.STANCED);
    }

    /**
     * Stance-aware fire. {@link com.dillon.starsectormarines.battle.combat.FireStance#STANCED}
     * preserves the base accuracy roll;
     * {@link com.dillon.starsectormarines.battle.combat.FireStance#MOVING}
     * halves it. Implementation lives in
     * {@code battle/weapons/InfantryWeapons.java}; this method exists so AI
     * behaviors can call {@code sim.fireShot(...)} without reaching into the
     * subsystem accessor.
     */
    public void fireShot(long shooter, long target,
                         com.dillon.starsectormarines.battle.combat.FireStance stance) {
        infantry.fireShot(shooter, target, stance);
    }

    @Override
    public void firePointShot(long shooter, PointFireAim aim, FireStance stance) {
        infantry.firePointShot(shooter, aim, stance);
    }

    /**
     * Delegates to {@link InfantryWeapons#fireSecondary}. Same delegation
     * rationale as {@link #fireShot}.
     */
    public void fireSecondary(long shooter, long target) {
        infantry.fireSecondary(shooter, target);
    }

    @Override
    public void throwFragmentationGrenade(long carrier, float targetX, float targetY) {
        infantry.throwFragmentationGrenade(carrier, targetX, targetY);
        fragGrenades.release(carrier);
    }

    @Override
    public void throwSmoke(long carrier, float targetX, float targetY) {
        if (!world.hasSecondaryWeapon(carrier)) return;
        SpecialEquipmentDef secondary = world.specialEquipment(carrier);
        if (secondary.activation() != SpecialActivation.UTILITY_SMOKE) return;
        int ammo = world.secondaryAmmo(carrier);
        if (ammo <= 0) return;
        SmokeGrenadeSpec spec = secondary.smokeGrenadeSpec();
        float fromX = world.renderX(carrier);
        float fromY = world.renderY(carrier);
        float dx = targetX - fromX;
        float dy = targetY - fromY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance > spec.throwRange() && distance > 0f) {
            targetX = fromX + dx / distance * spec.throwRange();
            targetY = fromY + dy / distance * spec.throwRange();
        }
        targetX = Math.max(0.5f, Math.min(grid.getWidth() - 0.5f, targetX));
        targetY = Math.max(0.5f, Math.min(grid.getHeight() - 0.5f, targetY));
        world.setSecondaryAmmo(carrier, ammo - 1);
        rosterService.telemetry().recordSecondaryUsed(carrier);
        smokeFields.launch(carrier, identity().faction(carrier), fromX, fromY,
                targetX, targetY, spec);
    }

    @Override
    public boolean plantSatchel(long carrier, long target) {
        if (!world.hasSecondaryWeapon(carrier) || resolveUnit(target) == 0L) return false;
        SpecialEquipmentDef secondary = world.specialEquipment(carrier);
        if (secondary.activation() != SpecialActivation.UTILITY_SATCHEL
                || !isHardenedTarget(target)
                || identity().faction(carrier) == identity().faction(target)) return false;
        SatchelChargeSpec spec = secondary.satchelChargeSpec();
        float dx = world.x(target) - world.x(carrier);
        float dy = world.y(target) - world.y(carrier);
        if (dx * dx + dy * dy > spec.contactRange() * spec.contactRange()) return false;
        boolean planted = satchelCharges.plant(carrier, target,
                identity().faction(carrier), world.x(target), world.y(target), spec);
        if (!planted) return false;
        world.setSecondaryCooldownTimer(carrier, spec.cooldownSeconds());
        rosterService.telemetry().recordSecondaryUsed(carrier);
        return true;
    }

    @Override
    public boolean applyContactStrike(long carrier, long target) {
        if (!world.hasSecondaryWeapon(carrier) || resolveUnit(carrier) == 0L) return false;
        SpecialEquipmentDef contactTool = world.specialEquipment(carrier);
        if (!contactTool.isCloseContactWeapon()) return false;
        // Re-validated here as well as inside the channel: the payload seam is
        // the last honest moment, and a contact that became illegal on the
        // final tick must not land anyway.
        if (!CloseContactTactics.isLegalUnitContact(carrier, contactTool, target, this)) {
            return false;
        }
        infantry.strikeContact(carrier, target);
        return true;
    }

    @Override
    public boolean applyContactBreach(long carrier, int cellX, int cellY) {
        if (!world.hasSecondaryWeapon(carrier) || resolveUnit(carrier) == 0L) return false;
        SpecialEquipmentDef contactTool = world.specialEquipment(carrier);
        if (!contactTool.isCloseContactWeapon()) return false;
        if (!CloseContactTactics.isLegalBreachPoint(carrier, contactTool, cellX, cellY, this)) {
            return false;
        }
        int wallDamage = contactTool.wallDamage();
        if (wallDamage <= 0) return false;
        // Bounded and local: exactly one authored cell, through the ordinary
        // map-edit authority, with no radius and no blast.
        boolean opened = mapEditor.damageWall(cellX, cellY, wallDamage);
        if (opened) closeContact.consumeBreachPoint(cellX, cellY);
        infantry.reportContactBreach(carrier, cellX + 0.5f, cellY + 0.5f);
        return true;
    }

    /** Delegates to {@link TurretFireSystem}. Kept for TurretBehavior and any remaining sim-surface callers on the deprecation path. */
    public void fireShotFrom(float fromX, float fromY, Faction shooterFaction,
                             StructureDef structure, long target, boolean aerialShooter) {
        turretFire.fire(fromX, fromY, shooterFaction, structure, target, aerialShooter);
    }

    /** Delegates to {@link TurretFireSystem}. */
    public void fireShotFrom(float fromX, float fromY, Faction shooterFaction,
                             StructureDef structure, long target, boolean aerialShooter, boolean hasLos) {
        turretFire.fire(fromX, fromY, shooterFaction, structure, target, aerialShooter, hasLos);
    }

    /** Ground-entity turret overload that preserves the shooter id for resolver exclusion and hit response. */
    public void fireShotFrom(long shooterId, float fromX, float fromY,
                             Faction shooterFaction, StructureDef structure, long target,
                             boolean aerialShooter, boolean hasLos) {
        turretFire.fire(shooterId, fromX, fromY, shooterFaction,
                structure, target, aerialShooter, hasLos);
    }

    /** Ground-entity turret overload with the posed barrel and burst release. */
    public void fireShotFrom(long shooterId, float fromX, float fromY,
                             Faction shooterFaction, StructureDef structure, long target,
                             boolean aerialShooter, boolean hasLos,
                             float mountFacingDegrees, int releaseIndex) {
        turretFire.fire(shooterId, fromX, fromY, shooterFaction,
                structure, target, aerialShooter, hasLos,
                mountFacingDegrees, releaseIndex);
    }

    /**
     * Delegates to {@link HeavyWeapons#fireMechWeapon}. Kept on the sim's
     * surface because AI behaviors call {@code sim.fireMechWeapon(...)}
     * directly. Implementation lives in {@code battle/weapons/HeavyWeapons.java}.
     */
    public void fireMechWeapon(long shooter, long target, WeaponDef weapon) {
        heavy.fireMechWeapon(shooter, target, weapon);
    }

    /**
     * Delegates to {@link HeavyWeapons#fireMechWeapon} with explicit accuracy
     * multiplier. Used by the LRM indirect-fire path (no LOS = reduced acc).
     */
    public void fireMechWeapon(long shooter, long target, WeaponDef weapon, float accuracyMult) {
        heavy.fireMechWeapon(shooter, target, weapon, accuracyMult);
    }

    @Override
    public void fireMechWeapon(long shooter, long target, MechWeaponMount mount,
                               float accuracyMult) {
        heavy.fireMechWeapon(shooter, target, mount, accuracyMult);
    }

    // advanceMechWeapons moved to HeavyWeapons.tick; the dead-mech wreck moved
    // to the MechWreckSystem death-event handler.

    /**
     * Applies damage from an external source (flyby strafing run) to a unit
     * already tracked by the sim. Routes through the same {@link DamageResolver}
     * the normal weapon path uses but with {@code moraleImpact = 0f}, which
     * short-circuits the morale branch — strafes are too short-lived for the
     * morale model to model meaningfully. Cover reduction, HP write, death
     * cascade (death FX + equipment drop + squad-leader promotion) all run
     * normally. The caller supplies explicit penetration for the source.
     * No {@link ShotEvent} is emitted — flyby tracers draw via the overlay,
     * not the ground combat tracer pass. Fall-back is also intentionally
     * skipped (strafes pin you down rather than break contact).
     */
    public void applyExternalDamage(long target, float damage, float penetration) {
        if (target == 0L || !world.isAlive(target) || damage <= 0f) return;
        damageResolver.resolve(target, CombatTelemetryService.NO_ATTACKER, damage, penetration, 0f);
    }


}
