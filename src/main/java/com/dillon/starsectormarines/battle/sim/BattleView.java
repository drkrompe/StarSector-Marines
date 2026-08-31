package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.deployable.DeployedCoverService;
import com.dillon.starsectormarines.battle.deployable.PointDefenseService;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService;
import com.dillon.starsectormarines.battle.unit.BodyService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.battle.contact.CloseContactService;
import com.dillon.starsectormarines.battle.satchel.SatchelChargeService;
import com.dillon.starsectormarines.battle.grenade.FragGrenadeService;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;

import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Read-only window onto the battle, for code that runs during the
 * <b>parallel replan</b> window — GOAP {@code cost} / {@code roles} /
 * {@code relevance} / {@code desiredState} / {@code highlightCells} and the
 * stateless query helpers they call. {@link BattleSimulation} implements this
 * (via {@link BattleControl}); narrowing a consumer's parameter from
 * {@code BattleSimulation} to {@code BattleView} makes it a <em>compile-time
 * error</em> to mutate the sim from a context the thread-safety contract
 * requires to be read-only — a guarantee that was previously Javadoc-only
 * (see {@link com.dillon.starsectormarines.battle.decision.goap.Action}).
 *
 * <p>This is the standing read boundary for parallel planning. Keep the surface
 * scoped to observations a planner genuinely needs rather than exposing the
 * whole orchestrator. See {@code ecs-nouns.md}.
 *
 * <p><b>Caveat:</b> some accessors return service objects
 * ({@link TacticalScoring}) that carry their own mutators; the read-only
 * guarantee this interface gives is at the sim-mutation level (no
 * {@code setPath} / {@code fireShot} / {@code advanceMovement}), not
 * transitively through every returned handle.
 */
public interface BattleView {

    NavigationGrid getGrid();

    /** Raw grid structural revision; changes with authored or runtime cell/edge mutation. */
    long getNavigationGridRevision();

    /** Derived navigation revision; advances after each flushed breach batch. */
    long getNavigationTopologyRevision();

    /** Faction equipment doctrine frozen at battle creation, or {@code null} in legacy fixtures. */
    GroundRosterProfile getGroundRoster();

    /** Simulation-owned, faction-neutral smoke fields and throws. */
    SmokeFieldService smokeFields();

    /** Reusable contact-demolition reservations and armed target attachments. */
    SatchelChargeService satchelCharges();

    /** Close-contact commitments and the authored breach points a cutter may work. */
    CloseContactService closeContact();

    /** Committed fragmentation-grenade landing footprints. */
    FragGrenadeService fragGrenades();

    /** Honest per-faction commander picture, or {@code null} for non-combat factions. */
    CommanderInfluenceSnapshot getCommanderInfluence(Faction faction);

    /** Zone+portal graph layered on the {@link NavigationGrid}. Rebuilt on wall destruction so AI queries reflect the current map. */
    ZoneGraph getZoneGraph();

    /**
     * Number of live units in the dense roster — the corpse-free count for live
     * iteration. Paired with {@link #liveUnitAt(int)} this is the read-only,
     * allocation-free live-iteration path: {@link UnitRosterService} retains
     * only live-unit ids, so no {@code isAlive()} skip is needed. Corpse state
     * remains in the shared {@code EntityWorld} under the same entity id.
     */
    int liveUnitCount();

    /**
     * Entity id of the live unit at dense index {@code [0, liveUnitCount())}.
     * Iteration order is roster-dense (insertion order with swap-and-pop on
     * release), <b>not</b> stable across releases — fine for a within-tick read
     * pass, not for anything that assumes a fixed battle-long ordering. Safe to
     * call during the parallel replan window (read-only; the dense array is
     * stable across the dispatch since spawns are queued). Never {@code 0L}: the
     * dense roster holds only live entities, so every index in range is a real id.
     */
    long liveUnitAt(int index);

    /**
     * Every body in the battle that is not a row in the dense roster — a convoy
     * chassis, an aircraft on its wheels, whatever registers next.
     *
     * <p>Paired with {@link #liveUnitCount()} / {@link #liveUnitAt(int)} this is
     * the whole population a scan has to consider, and it is deliberately one
     * accessor rather than one per kind. A consumer that walked the roster and
     * then a named list of vehicles was a consumer that stopped being right the
     * day a second kind of off-roster body existed — which is exactly how a mech
     * came to be unable to target an aircraft that infantry could shoot at.
     */
    BodyService bodies();

    /** Number of live members in one squad's primitive member slice. */
    int squadMemberCount(int squadId);

    /**
     * Live member id at {@code [0, squadMemberCount(squadId))}. Member order
     * follows spawn order and remains stable when another member is released.
     */
    long squadMemberAt(int squadId, int index);

    /**
     * Whether {@code id} is riding inside a vehicle rather than standing on the
     * map. It is alive and still one of its squad, but it holds no ground, sees
     * nothing and has no position to read — so any evaluator that asks where a
     * unit is must ask this first.
     */
    boolean isRiding(long id);

    /** Per-cell unit count, indexed by {@link NavigationGrid#index(int, int)}. */
    byte[] getOccupancyMap();

    /**
     * Occupancy-aware route to a cell many movers are heading for, served off
     * the shared reverse field for that goal. Only valid inside the frozen
     * unit-update snapshot; outside it the implementation falls back to A*.
     * Gate the choice on {@code SharedGoalPolicy.usesSharedGoalFields} rather
     * than calling this unconditionally — a field only pays for itself once a
     * battle is dense enough to reuse it.
     */
    int[] findSharedPathToGoal(int startX, int startY, int goalX, int goalY);

    /**
     * As above, biased by what {@code cost} makes each cell worth crossing.
     * Pass {@link #getRouteCostField} for the mover's own side to route around
     * ground it has recently been killed on.
     */
    int[] findSharedPathToGoal(int startX, int startY, int goalX, int goalY,
                               RouteCostField cost);

    /**
     * What this faction's own recent losses make ground cost to cross, or
     * {@code null} while it has lost nobody worth routing around. Published on
     * a fixed cadence, so it is a frozen snapshot that may be held for the
     * duration of a tick.
     */
    RouteCostField getRouteCostField(Faction faction);

    /** The entity id that {@code u} is currently targeting, or {@code 0L} if none. */
    long targetOf(long u);

    Squad getSquad(int id);

    Collection<Squad> getSquads();

    /** Current command-ledger generation for one squad, or {@code null}. */
    CommandDirective getSquadCommandDirective(int squadId);

    /** Tactical scoring service — firing-position / vantage queries. Read-only in the replan window. */
    TacticalScoring getTacticalScoring();

    /** Per-tick spatial index for radius/proximity unit queries. */
    UnitSpatialIndex getUnitIndex();

    /** Monotonic tick counter, for time-parametrized AI motion (drone orbit phase, etc.). */
    int getSimTickIndex();

    /** Thread-safe snapshot of active shots, safe to iterate during the parallel replan window. */
    List<ShotEvent> snapshotActiveShots();

    /** Live projectiles in flight. */
    List<Projectile> getActiveProjectiles();

    /** Thread-safe projectile snapshot for planning and opportunity-fire scoring. */
    List<Projectile> snapshotActiveProjectiles();

    /** Resolve any held live roster entity or targetable convoy vehicle, else {@code 0L}. */
    long resolveUnit(long id);

    /** Data owner for the {@code TELEMETRY} component — what each entity actually did this battle. Lifecycle-stable: readable and writable on a dead entity, so a record survives its subject. */
    CombatTelemetryService telemetry();

    /** Live point-defence emplacements placed out of the carried special slot, and their remaining bounds. */
    PointDefenseService pointDefense();

    /** Live cover screens placed out of the carried special slot, and their remaining lifetime. */
    DeployedCoverService deployedCover();

    /**
     * Whether {@code shooterId} may engage {@code candidateId} right now.
     *
     * <p>Relational on purpose. The absolute predicate this replaces had to
     * answer for the most limited shooter on the map, so the one case that
     * differed — a defence post reaching something in the air — lived
     * elsewhere as a hardcoded filter, and the shared answer was wrong for it.
     * See {@code EngagementService}.
     */
    boolean canEngage(long shooterId, long candidateId);

    /**
     * Armor-aware target classification used by rockets, satchels, and mech
     * preference. Absolute on purpose: how hard a thing is to hurt is a
     * property of the thing, not of the pair.
     */
    boolean isHardenedTarget(long id);

    /** Entity-access facade for broad by-id component reads ({@code world().hp(id)}); focused consumers should prefer the owning component service. See {@link World}. */
    World world();

    /**
     * The battle's single seeded random stream.
     *
     * <p>On the read interface because the AI tier consults it and holds nothing else,
     * and because determinism requires <em>one</em> stream — a behavior that reached for
     * its own source would put the battle back to being unreproducible. Drawing advances
     * it; that is the point.
     */
    Random random();

    /** Data owner for the COMBAT component — {@code combat().attackCooldown(id)} etc. The per-component Service that replaces piling combat accessors onto {@link World}. */
    CombatService combat();

    /** Data owner for the MOVEMENT component — {@code movement().moveSpeed(id)} etc. */
    MovementService movement();

    /** Data owner for the INTEGRAL_SYSTEM component — the capability a unit's armour pattern carries, and its live clocks. */
    IntegralSystemService integralSystems();

    /** Data owner for the VISION component (sight stats) — {@code vision().airLosRadius(id)} / {@code visionRange(id)}. The per-component Service that lands VISION off the {@link World} god-facade. */
    VisionService vision();

    /** Data owner for the SQUAD component (membership) — {@code squad().hasSquad(id)} / {@code squadId(id)}. Distinct from {@link #getSquad(int)} (the squad-object registry); {@link #squadOf(long)} composes the two. */
    SquadService squad();

    /** The {@link Squad} object {@code id} belongs to, or {@code null} if it's in no squad — composes the {@link #squad()} membership gate with {@link #getSquad(int)}. Replaces the old {@code u.squadId == NO_SQUAD ? null : getSquad(u.squadId)} idiom. */
    default Squad squadOf(long id) {
        SquadService squad = squad();
        return squad.hasSquad(id) ? getSquad(squad.squadId(id)) : null;
    }

    /** Data owner for the ROLE component (behavior-dispatch role) — {@code role().role(id)} / {@code setRole(id, r)}. The per-component Service that lands ROLE off the {@link World} god-facade. */
    RoleService role();

    /** Data owner for the IDENTITY component (immutable archetype) — {@code identity().type(id)} / {@code faction(id)} / {@code name(id)}. The by-id reads that replace the {@code Entity.type} / {@code Entity.faction} handle fields as the handle collapses to a bare {@code long} (identity-collapse Phase D). */
    IdentityService identity();

    /** Data owner for the HOME component (garrison idle-post) — {@code home().hasHome(id)} / {@code homeCellX(id)}. The per-component Service that lands HOME off the {@link World} god-facade. */
    HomeService home();

    /** Data owner for the HUB_STATE component (drone-hub spawn cadence) — {@code hubState().isHub(id)} / {@code spawnCooldown(id)}. The per-component Service the drone-hub type-tag classification ({@code UnitType.isDroneHub()}) replaced the old subclass state-cast with. */
    HubStateService hubState();

    /** Data owner for the TURRET_STATE component (turret facing/recoil/burst) — {@code turretState().isTurret(id)} / {@code facingDegrees(id)}. The per-component Service the turret type-tag classification ({@code UnitType.isTurret()}) replaced the old {@code MapTurret} subclass state-cast with. */
    TurretStateService turretState();

    /** Data owner for the DRONE_STATE component (drone patrol/pursuit vectors) — {@code droneState().isDrone(id)} / {@code patrolGoalX(id)}. The per-component Service the drone type-tag classification ({@code UnitType.isDrone()}) replaced the old {@code Drone} subclass state-cast with. */
    DroneStateService droneState();

    /** Data owner for the TASK component (objective/kit assignment) — {@code task().assignedObjective(id)} / {@code equipmentDropTarget(id)}. Tolerant reads (null when untasked); lands the task fields off the {@link World} god-facade. */
    TaskService task();

    /** Doodad-provided cover at a cell against fire incoming from {@code (fromDx, fromDy)}. */
    int getDoodadCoverAt(int x, int y, int fromDx, int fromDy);

    /** Doodad-provided cover at a cell for a facing octant. */
    int getDoodadCoverAtFacing(int x, int y, int facing);

    /** Doodad-provided cover at a cell, omnidirectional. */
    int getDoodadCoverAt(int x, int y);

    /** Battle-scoped tactical hint map from the map generator (firing-position graph, defense posts). */
    TacticalMap getTacticalMap();

    /** Compound capture/garrison service — zone-ownership queries. Read-only in the replan window (same returned-handle caveat as {@link #getTacticalScoring}). */
    CompoundService getCompoundService();

    /**
     * The garrison airfield's berths — what is parked, away, refitting, or
     * burned. Empty on a battle whose map has no authored field.
     *
     * <p>Read by the air means as a supply question: a field with no airworthy
     * aircraft cannot fly a sortie however firmly its ground is still held.
     */
    AirfieldService getAirfieldService();

    /** Per-cell structural topology (walls/roofs/cover classes). Rebuilt on map modification. */
    CellTopology getTopology();

    /** The live air-entity ids (shuttles, and planned fighters) — walk these and read each craft's state by id via {@link #world()}. */
    long[] getAirEntityIds();

    /** The live convoy-vehicle entity ids — walk these and read each vehicle by id via {@link #convoyMission(long)}. Mirrors {@link #getAirEntityIds()}. */
    long[] getConvoyVehicleIds();

    /**
     * Profile-aware physical radius shared by selection, separation, and
     * ballistics for a live roster actor or targetable convoy vehicle.
     */
    float physicalRadius(long id);

    /** The {@link VehicleMission} for a convoy-vehicle id (has-gated, {@code null} if not live). */
    VehicleMission convoyMission(long id);

    /** Mission objectives carried by both sides. */
    List<Objective> getObjectives();

    /** Active battlefield equipment kits, exposed for trusted mission disclosure. */
    List<EquipmentDrop> getEquipmentDrops();

    /** Mission-local rescue cohort lifecycle; empty in ordinary battles. */
    CivilianEvacuationTracker getCivilianEvacuationTracker();

    /** Whether a marine has physically reached the civilian shelter entrance. */
    boolean isCivilianEvacuationTriggered();

    /** Whether boarding is gated by a physical rescue shuttle. */
    boolean hasCivilianPickupShuttle();
}
