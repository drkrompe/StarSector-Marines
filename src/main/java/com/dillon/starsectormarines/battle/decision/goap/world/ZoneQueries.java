package com.dillon.starsectormarines.battle.decision.goap.world;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.action.ClearZone;
import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;
import com.dillon.starsectormarines.battle.decision.goap.action.HoldZone;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.Portal;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Stateless convenience layer over {@link ZoneGraph} for the GOAP action
 * library. The graph itself owns the cell→zone map and the portal adjacency;
 * this class adds the squad- and faction-aware queries Stage 2 stories J / K /
 * L need (zone the squad stands in, zone its objective lives in, whether a
 * zone is clear of a faction, the zone-by-zone path between two zones).
 *
 * <p>No caching, no instance state — {@link ZoneGraph#rebuild} already runs at
 * most once per tick, so per-query overhead is just a handful of int lookups.
 */
public final class ZoneQueries {

    private ZoneQueries() {}

    /**
     * Zone id the squad treats as its current room — the cell the
     * <em>leader</em> stands in, falling back to the centroid only when the
     * squad has no live leader (leader dead and the per-tick promotion in
     * {@code BattleSimulation.applyDamage} hasn't filled the slot yet, or a
     * marine deboard squad that hasn't minted one). Returns {@code -1} when
     * the squad has no live members, the centroid is also unresolvable, or
     * the chosen cell sits on a wall and so isn't in any zone.
     *
     * <p>Leader-anchored on purpose: cohesion already pulls drifting members
     * toward the leader (see {@link com.dillon.starsectormarines.battle.infantry.InfantryCohesion#cohesionOverride}
     * and {@code memory/squad_leader_cohesion.md}), so the leader's cell is
     * the most stable point of reference for "where the squad is." Centroid
     * drifts every time a member crosses a portal — fine for the cohesion
     * spring, but it makes the BFS start zone flip on every replan when a
     * squad is bifurcated around an obstacle, which oscillates a zone-push
     * plan in and out of the same building.
     */
    public static int squadCurrentZone(Squad squad, BattleView sim) {
        if (squad == null || sim == null) return -1;
        if (squad.aliveMembers <= 0) return -1;
        Unit leader = sim.resolveUnit(squad.leaderId);
        if (leader != null) {
            int zone = sim.getZoneGraph().zoneIdAt(sim.world().cellX(leader.entityId), sim.world().cellY(leader.entityId));
            if (zone >= 0) return zone;
            // Leader on a wall cell (transient — pathfinder placed them there
            // for a single tick): fall through to the centroid so the goal
            // layer still has a usable answer this tick.
        }
        int x = Math.round(squad.centroidX);
        int y = Math.round(squad.centroidY);
        return sim.getZoneGraph().zoneIdAt(x, y);
    }

    /**
     * Zone id containing the squad's current objective cell, or {@code -1}
     * when the squad has no objective or the objective cell isn't in any
     * zone. Stage 1 reads this off {@link Squad#assignedNode}; Stage 2 stories
     * are expected to extend {@link Squad} with richer per-action target
     * fields, and this method is the seam those reads grow under without
     * forcing callers to change.
     */
    public static int objectiveZone(Squad squad, BattleView sim) {
        if (squad == null || sim == null) return -1;
        TacticalNode node = squad.assignedNode;
        if (node == null) return -1;
        return sim.getZoneGraph().zoneIdAt(node.anchorX, node.anchorY);
    }

    /**
     * Portal ids that border {@code zoneId}. Thin wrapper over
     * {@link NavigationZone#getPortalIds()} that handles invalid zone ids and
     * returns an immutable view, so callers don't have to fetch the zone by
     * id themselves.
     */
    public static List<Integer> portalsOf(int zoneId, BattleView sim) {
        if (sim == null) return Collections.emptyList();
        NavigationZone zone = sim.getZoneGraph().zoneById(zoneId);
        if (zone == null) return Collections.emptyList();
        return Collections.unmodifiableList(zone.getPortalIds());
    }

    /**
     * True iff no alive unit of {@code enemyFaction} stands on any cell of
     * {@code zoneId}. Iterates the unit list (typically tens of entries) and
     * cross-checks each enemy's cell against {@link ZoneGraph#zoneIdAt}; far
     * cheaper than walking the zone's full cell list since zones often hold
     * hundreds of cells.
     */
    public static boolean zoneClear(int zoneId, Faction enemyFaction, BattleView sim) {
        if (sim == null || enemyFaction == null) return true;
        ZoneGraph graph = sim.getZoneGraph();
        if (graph.zoneById(zoneId) == null) return true;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Unit u = sim.liveUnitAt(i);
            if (u.faction != enemyFaction) continue;
            if (graph.zoneIdAt(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId)) == zoneId) return false;
        }
        return true;
    }

    /**
     * BFS over the portal graph returning the sequence of zone ids visited
     * from {@code fromZone} to {@code toZone} inclusive. Returns
     * {@code [fromZone]} when the endpoints match and an empty list when the
     * zones are disconnected or either id is invalid. Used by Story K's
     * room-by-room sweep planner; mirrors {@link ZoneGraph#areConnected} but
     * records parent pointers so the full path can be reconstructed.
     */
    public static List<Integer> zonePathBfs(int fromZone, int toZone, BattleView sim) {
        if (sim == null) return Collections.emptyList();
        ZoneGraph graph = sim.getZoneGraph();
        if (graph.zoneById(fromZone) == null || graph.zoneById(toZone) == null) {
            return Collections.emptyList();
        }
        if (fromZone == toZone) {
            List<Integer> out = new ArrayList<>(1);
            out.add(fromZone);
            return out;
        }
        int zoneCount = graph.getZones().size();
        int[] parent = new int[zoneCount];
        boolean[] visited = new boolean[zoneCount];
        for (int i = 0; i < zoneCount; i++) parent[i] = -1;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(fromZone);
        visited[fromZone] = true;
        boolean found = false;
        while (!queue.isEmpty()) {
            int cur = queue.pollFirst();
            if (cur == toZone) { found = true; break; }
            NavigationZone z = graph.zoneById(cur);
            if (z == null) continue;
            for (int portalId : z.getPortalIds()) {
                Portal p = graph.portalById(portalId);
                if (p == null) continue;
                int other = p.otherZone(cur);
                if (other < 0 || visited[other]) continue;
                visited[other] = true;
                parent[other] = cur;
                queue.add(other);
            }
        }
        if (!found) return Collections.emptyList();
        ArrayList<Integer> reverse = new ArrayList<>();
        for (int cur = toZone; cur != -1; cur = parent[cur]) {
            reverse.add(cur);
            if (cur == fromZone) break;
        }
        Collections.reverse(reverse);
        return reverse;
    }

    /**
     * Synthesize a squad-push plan from {@code fromZone} to {@code toZone}:
     * BFS the zone graph, then emit an alternating {@link EnterZone} +
     * {@link ClearZone} pair per intermediate zone after the starting one.
     * Returns {@code null} when the endpoints are invalid, identical, or
     * disconnected — caller treats that as "no plan, fall through to the
     * next-most-relevant goal."
     *
     * <p>Shared between MISSION-priority zone-push goals:
     * {@link com.dillon.starsectormarines.battle.infantry.SecureObjectiveZone}
     * (unit-level planter target) and
     * {@link com.dillon.starsectormarines.battle.infantry.ClearAssignedZoneGoal}
     * (commander-issued squad assignment). Both compose the same step
     * sequence — just differ in how they pick the target zone.
     */
    public static SquadPlan synthesizeZonePushPlan(int fromZone, int toZone, BattleView sim) {
        if (sim == null || fromZone < 0 || toZone < 0 || fromZone == toZone) return null;
        List<Integer> path = zonePathBfs(fromZone, toZone, sim);
        if (path.size() < 2) return null;

        ZoneGraph graph = sim.getZoneGraph();
        NavigationGrid grid = sim.getGrid();
        List<SquadPlan.Step> steps = new ArrayList<>(2 * (path.size() - 1));
        for (int i = 1; i < path.size(); i++) {
            int zoneId = path.get(i);
            NavigationZone zone = graph.zoneById(zoneId);
            if (zone == null) return null;
            if (i < path.size() - 1
                    && zone.getCellCount() == 1
                    && grid.isDoorwayAt(zone.getCellIndices()[0])) continue;
            steps.add(new SquadPlan.Step(EnterZone.forZone(zone, grid)));
            steps.add(new SquadPlan.Step(new ClearZone(zoneId)));
        }
        return new SquadPlan(steps);
    }

    /**
     * True iff {@code plan} is a still-in-progress zone-push sweep whose
     * terminal zone is {@code targetZoneId} — i.e. the squad is already
     * working toward this exact target and shouldn't have its plan thrown
     * out at the next replan tick.
     *
     * <p>Used by {@link com.dillon.starsectormarines.battle.infantry.ClearAssignedZoneGoal#customPlan}
     * and {@link com.dillon.starsectormarines.battle.infantry.SecureObjectiveZone#customPlan}
     * to keep an active multi-zone plan stable across the periodic 2-second
     * replan. Without this gate, when a squad is bifurcated across a portal
     * the BFS start zone flips with the leader/centroid each tick — even
     * with leader-anchored {@link #squadCurrentZone}, the leader can still
     * cross portals mid-sweep — and the customPlan re-emits a qualitatively
     * different EnterZone/ClearZone sequence, oscillating the squad in and
     * out of a building.
     *
     * <p>Checks the last step's action type and zone id: any non-empty plan
     * whose terminal step is {@code EnterZone(target)} or
     * {@code ClearZone(target)} is considered "on target." A plan that's
     * already advanced past its last step ({@link SquadPlan#isComplete})
     * doesn't qualify — caller will re-synthesize on the next tick anyway.
     */
    public static boolean planEndsAtZone(SquadPlan plan, int targetZoneId) {
        if (plan == null || plan.isComplete()) return false;
        List<SquadPlan.Step> steps = plan.steps();
        if (steps.isEmpty()) return false;
        Action terminal = steps.get(steps.size() - 1).action;
        if (terminal instanceof HoldZone hz) return hz.targetZoneId() == targetZoneId;
        if (terminal instanceof ClearZone cz) return cz.targetZoneId() == targetZoneId;
        if (terminal instanceof EnterZone ez) return ez.targetZoneId() == targetZoneId;
        return false;
    }
}
