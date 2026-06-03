package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Always-feasible reinforcement floor. Spawns a fresh squad of infantry on
 * a side-appropriate, viable perimeter cell (walkable + outside buildings,
 * via {@link LandingZoneScorer}) — defender = "end" side of the
 * {@link TraversalAxis} (north for SOUTH_TO_NORTH, east for WEST_TO_EAST),
 * marine = "start" side. The squad's {@link Squad#assignedNode} is set to
 * the closest compound-kind {@link TacticalNode} to the rally so
 * {@code PatrolRoute} pulls them off the perimeter and into the fight; if
 * no compound is nearby the squad spawns as a free agent and falls through
 * to ambient engagement.
 *
 * <p>This is the {@code canFulfill = true} fallback under
 * {@link ConvoyMeans} (and, eventually, {@code ShuttleMeans}): a road-less
 * map or a rally with no LZ still gets reinforcement instead of a dropped
 * request.
 */
public final class WalkInMeans implements ReinforcementMeans {

    private static final Logger LOG = Global.getLogger(WalkInMeans.class);

    /**
     * Squad size for {@link ReinforcementRequest.Strength#SMALL}. Matches the
     * strength scaling table in {@code roadmap/reinforcement/architecture.md}
     * (1 squad = 3 infantry); MEDIUM/LARGE will scale to multiple squads in
     * a later slice.
     */
    public static final int SQUAD_SIZE = 3;

    /** Manhattan radius from the rally to look for a compound-kind tactical node to anchor on. Tight — the rally hint already points at a meaningful cell, so we shouldn't drift far. */
    private static final int RALLY_NODE_SEARCH_RADIUS = 6;

    /** Max BFS hops from the primary spawn cell when finding adjacent walkable cells for additional squad members. */
    private static final int MEMBER_SCAN_RADIUS = 4;

    private final TraversalAxis axis;
    private int nextUnitId = 0;

    public WalkInMeans(TraversalAxis axis) {
        this.axis = axis;
    }

    @Override
    public boolean canFulfill(BattleView sim, ReinforcementRequest req) {
        if (!req.hasRally()) return false;
        if (req.side != Faction.DEFENDER) return false;
        // Compound-as-supply gate: walk-in is the BARRACKS-supplied means.
        // When every BARRACKS has flipped marine-held the defender can't
        // mobilise patrol-grade infantry from the city any more — the
        // dispatcher falls through to whatever still has a live supply
        // structure, or drops the request if all chains are dead.
        if (!sim.getCompoundService().hasAliveCompound(
                TacticalNode.Kind.BARRACKS, Faction.DEFENDER)) {
            return false;
        }
        return pickPrimaryCell(sim, req) != null;
    }

    @Override
    public void dispatch(BattleControl sim, ReinforcementRequest req) {
        int[] primary = pickPrimaryCell(sim, req);
        if (primary == null) {
            LOG.warn("WalkInMeans: no walkable perimeter cell for side=" + req.side
                    + " rally=(" + req.rallyX + "," + req.rallyY + ")");
            return;
        }
        LandingZoneScorer scorer = new LandingZoneScorer(sim.getGrid(), sim.getTopology());
        List<int[]> spawnCells = collectAdjacentCells(sim.getGrid(), scorer, primary[0], primary[1], SQUAD_SIZE);
        if (spawnCells.isEmpty()) {
            LOG.warn("WalkInMeans: primary cell (" + primary[0] + "," + primary[1]
                    + ") had no walkable BFS neighborhood");
            return;
        }

        TacticalNode anchor = nearestCompoundNode(sim, req.rallyX, req.rallyY);
        UnitType infantryType = FactionUnitRoster.forFaction(req.side).infantry();

        Squad squad = null;
        int spawned = 0;
        for (int[] cell : spawnCells) {
            Entity unit = new Entity("r" + (nextUnitId++), req.side, infantryType, cell[0], cell[1]);
            unit.role = UnitRole.PATROL;
            if (squad == null) {
                int sid = sim.mintSquad(req.side, unit);
                squad = sim.getSquad(sid);
                if (squad != null) squad.assignedNode = anchor;
            }
            if (squad != null) unit.squadId = squad.id;
            sim.addUnit(unit);
            spawned++;
        }
        if (squad != null) squad.originalSize = spawned;

        LOG.info("WalkInMeans: dispatched " + spawned + " " + req.side + " at perimeter cell ("
                + primary[0] + "," + primary[1] + ")"
                + (anchor != null ? " anchor=(" + anchor.anchorX + "," + anchor.anchorY + ")" : " free-agent")
                + " rally=(" + req.rallyX + "," + req.rallyY + ")");
    }

    /**
     * Walkable perimeter cell on the side-appropriate edge of the map nearest
     * the rally's lateral coordinate. Returns {@code null} when no edge has
     * any walkable cell (degenerate — the map is fully walled in along that
     * edge), so {@link #canFulfill} can reject the request cleanly.
     */
    private int[] pickPrimaryCell(BattleView sim, ReinforcementRequest req) {
        NavigationGrid grid = sim.getGrid();
        LandingZoneScorer scorer = new LandingZoneScorer(grid, sim.getTopology());
        int gw = grid.getWidth();
        int gh = grid.getHeight();
        Edge edge = pickEdge(req, gw, gh);
        int[] cell = scanEdge(grid, scorer, edge, req.rallyX, req.rallyY);
        if (cell != null) return cell;
        for (Edge fallback : Edge.values()) {
            if (fallback == edge) continue;
            cell = scanEdge(grid, scorer, fallback, req.rallyX, req.rallyY);
            if (cell != null) return cell;
        }
        return null;
    }

    /**
     * Side-and-axis-aware edge pick. Defender comes from the "end" side of
     * the traversal axis (their rear); marine comes from the "start" side
     * (their staging side). Null axis defaults to NORTH for defender / SOUTH
     * for marine — arbitrary but stable, the fallback in {@link #pickPrimaryCell}
     * will iterate the other edges if the chosen one has no walkable cell.
     */
    private Edge pickEdge(ReinforcementRequest req, int gw, int gh) {
        boolean defender = req.side == Faction.DEFENDER;
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            return defender ? Edge.NORTH : Edge.SOUTH;
        }
        if (axis == TraversalAxis.WEST_TO_EAST) {
            return defender ? Edge.EAST : Edge.WEST;
        }
        return defender ? Edge.NORTH : Edge.SOUTH;
    }

    /**
     * Walk one edge of the map and return the walkable cell whose lateral
     * coordinate is closest to the rally's. {@code null} when the edge has
     * no walkable cells at all.
     */
    private static int[] scanEdge(NavigationGrid grid, LandingZoneScorer scorer, Edge edge, int rallyX, int rallyY) {
        int gw = grid.getWidth();
        int gh = grid.getHeight();
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        switch (edge) {
            case NORTH -> {
                int y = gh - 1;
                for (int x = 0; x < gw; x++) {
                    if (!scorer.isViable(x, y)) continue;
                    int d = Math.abs(x - rallyX);
                    if (d < bestDist) { bestDist = d; best = x; }
                }
                return best < 0 ? null : new int[]{best, y};
            }
            case SOUTH -> {
                int y = 0;
                for (int x = 0; x < gw; x++) {
                    if (!scorer.isViable(x, y)) continue;
                    int d = Math.abs(x - rallyX);
                    if (d < bestDist) { bestDist = d; best = x; }
                }
                return best < 0 ? null : new int[]{best, y};
            }
            case EAST -> {
                int x = gw - 1;
                for (int y = 0; y < gh; y++) {
                    if (!scorer.isViable(x, y)) continue;
                    int d = Math.abs(y - rallyY);
                    if (d < bestDist) { bestDist = d; best = y; }
                }
                return best < 0 ? null : new int[]{x, best};
            }
            case WEST -> {
                int x = 0;
                for (int y = 0; y < gh; y++) {
                    if (!scorer.isViable(x, y)) continue;
                    int d = Math.abs(y - rallyY);
                    if (d < bestDist) { bestDist = d; best = y; }
                }
                return best < 0 ? null : new int[]{x, best};
            }
        }
        return null;
    }

    /**
     * BFS outward from {@code (px, py)} collecting up to {@code count}
     * viable cells (walkable, outside buildings — via {@link LandingZoneScorer}),
     * including the seed itself. Same shape as {@code AirSystem#findDeboardCell}
     * — radius-limited, breadth-first so results stay clustered.
     */
    private static List<int[]> collectAdjacentCells(NavigationGrid grid, LandingZoneScorer scorer,
                                                    int px, int py, int count) {
        List<int[]> out = new ArrayList<>(count);
        Set<Long> seen = new HashSet<>();
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{px, py, 0});
        seen.add(((long) px << 32) | (py & 0xFFFFFFFFL));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty() && out.size() < count) {
            int[] p = q.poll();
            if (p[2] > MEMBER_SCAN_RADIUS) continue;
            if (scorer.isViable(p[0], p[1])) {
                out.add(new int[]{p[0], p[1]});
            }
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                long k = ((long) nx << 32) | (ny & 0xFFFFFFFFL);
                if (!seen.add(k)) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return out;
    }

    /**
     * Closest COMMAND_POST / BARRACKS / ARMORY {@link TacticalNode} to the
     * rally within {@link #RALLY_NODE_SEARCH_RADIUS} Manhattan cells, or
     * {@code null} when no compound node sits near the rally (the spawned
     * squad becomes a free agent — ambient engagement picks them up once
     * they spot enemies, but they won't have a directional pull off the
     * perimeter).
     */
    private static TacticalNode nearestCompoundNode(BattleView sim, int rallyX, int rallyY) {
        TacticalMap map = sim.getTacticalMap();
        if (map == null) return null;
        List<TacticalNode> near = map.nearest(rallyX, rallyY, 1,
                EnumSet.of(TacticalNode.Kind.COMMAND_POST,
                        TacticalNode.Kind.BARRACKS,
                        TacticalNode.Kind.ARMORY));
        if (near.isEmpty()) return null;
        TacticalNode candidate = near.get(0);
        int d = Math.abs(candidate.anchorX - rallyX) + Math.abs(candidate.anchorY - rallyY);
        return d <= RALLY_NODE_SEARCH_RADIUS ? candidate : null;
    }

    private enum Edge { NORTH, SOUTH, EAST, WEST }
}
