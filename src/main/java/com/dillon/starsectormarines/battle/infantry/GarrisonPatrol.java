package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;

import java.util.List;

/**
 * <b>Squad posture: garrison a multi-building compound.</b> Custom-plan action
 * emitted by {@link GarrisonCompound} for a squad holding a compound — the
 * marine {@code HOLD_NODE} holder of a captured base, or a defender base
 * garrison. Unlike {@link HoldPost}'s tight 6-cell leash to one post, this
 * patrols the compound's whole garrison area (every room inside the persisted
 * footprint — see {@link com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea})
 * and re-clears rooms on counter-attack.
 *
 * <p>Perpetual (always {@link ActionStatus#RUNNING}); the squad-level replan
 * swaps goals when reality changes (morale break → {@code SurviveContact};
 * chokepoint ambush geometry → {@code GarrisonAmbush}). Two states, checked
 * each tick:
 *
 * <ul>
 *   <li><b>CONTEST</b> — the first garrison room (largest first) that still
 *       holds an enemy is re-cleared by delegating to {@link ClearZone}, which
 *       gives the "engage in-zone, don't chase across the boundary" behavior
 *       for free. The whole squad converges on the same room (the contested
 *       zone is chosen deterministically), so a small garrison masses on the
 *       breach rather than splitting.</li>
 *   <li><b>QUIET</b> — all rooms clear: round-robin a squad-scoped waypoint
 *       across the room interiors via {@link PatrolMotion} (this action only
 *       supplies the room-round-robin {@link PatrolMotion.WaypointSource}),
 *       firing opportunistically at anything visible so an intruder on the
 *       courtyard isn't ignored.</li>
 * </ul>
 *
 * <p>Not a singleton: each plan carries the garrison-zone list computed at
 * replan time (zones are static, so recomputing every replan is cheap and the
 * rotation cursor lives on the {@link Squad}, surviving the rebuild).
 */
public final class GarrisonPatrol implements Action {

    /** Garrison rooms, sorted descending by cell count (as {@link com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea#garrisonZones} returns them). */
    private final List<Integer> garrisonZones;

    /** Room-round-robin waypoint strategy (default staleness: no-waypoint or arrived). Resolve once — zones are fixed for this plan. */
    private final PatrolMotion.WaypointSource waypointSource = (member, squad, sim) -> nextWaypoint(squad, sim);

    public GarrisonPatrol(List<Integer> garrisonZones) {
        this.garrisonZones = garrisonZones;
    }

    @Override public String name() { return "GarrisonPatrol"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        Faction enemy = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;

        // CONTEST — re-clear the first room (largest first) that holds an enemy
        // we can actually engage. ClearZone owns the engage-in-zone /
        // no-chase-across-portals logic; we coerce its SUCCESS to RUNNING since
        // garrisoning never terminates.
        //
        // The engageability gate is load-bearing: a surviving enemy alone isn't
        // enough to enter CONTEST — this marine must have a reachable firing
        // position for it. Otherwise a defender whose only LOS cells sit across
        // a wall (a captured compound's exterior turret floods into the interior
        // room in the zone graph but is fenced off for the pathfinder —
        // [[zone_graph_ignores_edges]]) pins the garrison in a CONTEST it can
        // never act on, freezing it on an unreachable target instead of
        // patrolling. When nothing engageable remains, fall through to QUIET —
        // which still fires opportunistically if the enemy ever comes into view.
        for (int zoneId : garrisonZones) {
            if (ZoneQueries.zoneClear(zoneId, enemy, sim)) continue;
            if (!zoneHasEngageableEnemy(zoneId, member, enemy, sim)) continue;
            new ClearZone(zoneId).execute(member, squad, sim);
            return ActionStatus.RUNNING;
        }

        // QUIET — rooms clear: patrol the room interiors, firing opportunistically.
        return PatrolMotion.advance(member, squad, sim, waypointSource, /*fireWhilePatrolling*/ true);
    }

    /**
     * True iff some live enemy combatant standing in {@code zoneId} has a firing
     * position {@code member} can actually reach — the gate for entering CONTEST
     * on that room. Mirrors the in-zone enemy scan of
     * {@link ZoneQueries#zoneClear}, but adds the reachability test it omits:
     * {@code zoneClear} answers "is anyone here", this answers "can we engage
     * them" via {@link com.dillon.starsectormarines.battle.decision.TacticalScoring#hasReachableFiringSpot}.
     * Without the latter a garrison freezes on a surviving-but-unreachable enemy
     * (see the CONTEST comment + the SQ-96 walled-off-turret case).
     *
     * <p>Per-member by design: a marine that can reach the breach contests it;
     * one walled off from every survivor patrols instead and fires only if the
     * enemy comes into the open. Cost is bounded — the enemy scan is the same
     * O(units) pass {@code zoneClear} already runs, and the reachability probe
     * fires only for the (few) enemies actually inside the room.
     */
    private boolean zoneHasEngageableEnemy(int zoneId, Entity member, Faction enemy, BattleControl sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity e = sim.liveUnitAt(i);
            if (e.faction != enemy || !e.type.combatant) continue;
            if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(e.entityId), sim.world().cellY(e.entityId)) != zoneId) continue;
            if (sim.getTacticalScoring().hasReachableFiringSpot(member, e)) return true;
        }
        return false;
    }

    /**
     * Next room interior in round-robin order: the room after the one the
     * current waypoint sits in. Falls to the first (largest) room when the
     * current waypoint is unset or outside the garrison set.
     */
    private int[] nextWaypoint(Squad squad, BattleView sim) {
        if (garrisonZones.isEmpty()) return null;
        int curZone = sim.getZoneGraph().zoneIdAt(squad.patrolWaypointX, squad.patrolWaypointY);
        int idx = garrisonZones.indexOf(curZone);
        int nextIdx = (idx + 1) % garrisonZones.size(); // idx == -1 → start at 0
        return interiorCellOf(garrisonZones.get(nextIdx), sim);
    }

    private static int[] interiorCellOf(int zoneId, BattleView sim) {
        NavigationZone zone = sim.getZoneGraph().zoneById(zoneId);
        if (zone == null) return null;
        int[] cells = zone.getCellIndices();
        if (cells.length == 0) return null;
        int pick = cells[cells.length / 2];
        int w = sim.getGrid().getWidth();
        return new int[]{ pick % w, pick / w };
    }
}
