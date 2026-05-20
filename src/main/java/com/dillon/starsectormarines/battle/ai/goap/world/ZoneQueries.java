package com.dillon.starsectormarines.battle.ai.goap.world;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.actions.ClearZone;
import com.dillon.starsectormarines.battle.ai.goap.actions.EnterZone;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.Portal;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

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
     * Zone id containing the squad centroid, or {@code -1} when the centroid
     * doesn't resolve to any zone — e.g., the squad has no live members
     * (centroid undefined) or sits on a wall cell.
     */
    public static int squadCurrentZone(Squad squad, BattleSimulation sim) {
        if (squad == null || sim == null) return -1;
        if (squad.aliveMembers <= 0) return -1;
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
    public static int objectiveZone(Squad squad, BattleSimulation sim) {
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
    public static List<Integer> portalsOf(int zoneId, BattleSimulation sim) {
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
    public static boolean zoneClear(int zoneId, Faction enemyFaction, BattleSimulation sim) {
        if (sim == null || enemyFaction == null) return true;
        ZoneGraph graph = sim.getZoneGraph();
        if (graph.zoneById(zoneId) == null) return true;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive()) continue;
            if (u.faction != enemyFaction) continue;
            if (graph.zoneIdAt(u.cellX, u.cellY) == zoneId) return false;
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
    public static List<Integer> zonePathBfs(int fromZone, int toZone, BattleSimulation sim) {
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
     * {@link com.dillon.starsectormarines.battle.ai.goap.goals.SecureObjectiveZone}
     * (unit-level planter target) and
     * {@link com.dillon.starsectormarines.battle.ai.goap.goals.ClearAssignedZoneGoal}
     * (commander-issued squad assignment). Both compose the same step
     * sequence — just differ in how they pick the target zone.
     */
    public static SquadPlan synthesizeZonePushPlan(int fromZone, int toZone, BattleSimulation sim) {
        if (sim == null || fromZone < 0 || toZone < 0 || fromZone == toZone) return null;
        List<Integer> path = zonePathBfs(fromZone, toZone, sim);
        if (path.size() < 2) return null;

        ZoneGraph graph = sim.getZoneGraph();
        List<SquadPlan.Step> steps = new ArrayList<>(2 * (path.size() - 1));
        for (int i = 1; i < path.size(); i++) {
            int zoneId = path.get(i);
            NavigationZone zone = graph.zoneById(zoneId);
            if (zone == null) return null;
            steps.add(new SquadPlan.Step(EnterZone.forZone(zone, sim.getGrid())));
            steps.add(new SquadPlan.Step(new ClearZone(zoneId)));
        }
        return new SquadPlan(steps);
    }
}
