package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
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
    public ActionStatus execute(Unit member, Squad squad, BattleControl sim) {
        Faction enemy = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;

        // CONTEST — re-clear the first room (largest first) that holds an enemy.
        // ClearZone owns the engage-in-zone / no-chase-across-portals logic; we
        // coerce its SUCCESS to RUNNING since garrisoning never terminates.
        for (int zoneId : garrisonZones) {
            if (!ZoneQueries.zoneClear(zoneId, enemy, sim)) {
                new ClearZone(zoneId).execute(member, squad, sim);
                return ActionStatus.RUNNING;
            }
        }

        // QUIET — rooms clear: patrol the room interiors, firing opportunistically.
        return PatrolMotion.advance(member, squad, sim, waypointSource, /*fireWhilePatrolling*/ true);
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
