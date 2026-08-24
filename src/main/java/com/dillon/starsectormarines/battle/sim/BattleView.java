package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.battle.satchel.SatchelChargeService;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;

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

    /** Simulation-owned, faction-neutral smoke fields and throws. */
    SmokeFieldService smokeFields();

    /** Reusable contact-demolition reservations and armed target attachments. */
    SatchelChargeService satchelCharges();

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

    /** Number of live members in one squad's primitive member slice. */
    int squadMemberCount(int squadId);

    /**
     * Live member id at {@code [0, squadMemberCount(squadId))}. Member order
     * follows spawn order and remains stable when another member is released.
     */
    long squadMemberAt(int squadId, int index);

    /** Per-cell unit count, indexed by {@link NavigationGrid#index(int, int)}. */
    byte[] getOccupancyMap();

    /** The entity id that {@code u} is currently targeting, or {@code 0L} if none. */
    long targetOf(long u);

    Squad getSquad(int id);

    Collection<Squad> getSquads();

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

    /** Resolve a unit id to itself if a live unit holds it, else {@code 0L}. */
    long resolveUnit(long id);

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

    /** Per-cell structural topology (walls/roofs/cover classes). Rebuilt on map modification. */
    CellTopology getTopology();

    /** The live air-entity ids (shuttles, and planned fighters) — walk these and read each craft's state by id via {@link #world()}. */
    long[] getAirEntityIds();

    /** The live convoy-vehicle entity ids — walk these and read each vehicle by id via {@link #convoyMission(long)}. Mirrors {@link #getAirEntityIds()}. */
    long[] getConvoyVehicleIds();

    /** The {@link VehicleMission} for a convoy-vehicle id (has-gated, {@code null} if not live). */
    VehicleMission convoyMission(long id);

    /** Mission objectives carried by both sides. */
    List<Objective> getObjectives();

    /** Mission-local rescue cohort lifecycle; empty in ordinary battles. */
    CivilianEvacuationTracker getCivilianEvacuationTracker();

    /** Whether a marine has physically reached the civilian shelter entrance. */
    boolean isCivilianEvacuationTriggered();

    /** Whether boarding is gated by a physical rescue shuttle. */
    boolean hasCivilianPickupShuttle();
}
