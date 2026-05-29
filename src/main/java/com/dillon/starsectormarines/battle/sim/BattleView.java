package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.Vehicle;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;

import java.util.Collection;
import java.util.List;

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
 * <p>Part of the {@code drop-sim-facade-delegators} migration: the GOAP
 * {@code sim} parameter is being replaced by these narrowed types so consumers
 * depend on a scoped contract, not the whole orchestrator. The surface is
 * grown incrementally as consumers migrate — every method here already exists
 * on {@link BattleSimulation}, so adding one is zero-risk.
 *
 * <p><b>Caveat:</b> some accessors return service objects
 * ({@link TacticalScoring}) that carry their own mutators; the read-only
 * guarantee this interface gives is at the sim-mutation level (no
 * {@code setPath} / {@code fireShot} / {@code advanceMovement}), not
 * transitively through every returned handle.
 */
public interface BattleView {

    NavigationGrid getGrid();

    /** Zone+portal graph layered on the {@link NavigationGrid}. Rebuilt on wall destruction so AI queries reflect the current map. */
    ZoneGraph getZoneGraph();

    /** Live + corpse unit list. Callers must not retain across tick boundaries. */
    List<Unit> getUnits();

    /** Per-cell unit count, indexed by {@link NavigationGrid#index(int, int)}. */
    byte[] getOccupancyMap();

    /** The unit {@code u} is currently targeting, resolved through the registry, or {@code null}. */
    Unit targetOf(Unit u);

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

    /** Resolve a unit id through the registry, or {@code null} if no live unit holds it. */
    Unit resolveUnit(long id);

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

    /** Active air shuttles in the battle. */
    List<Shuttle> getShuttles();

    /** Active convoy ground vehicles in the battle. */
    List<Vehicle> getConvoyVehicles();

    /** Mission objectives carried by both sides. */
    List<Objective> getObjectives();
}
