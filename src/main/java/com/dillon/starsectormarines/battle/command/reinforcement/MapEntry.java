package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

/**
 * Where somebody walks onto the map from, which is their own side's rear edge.
 *
 * <p>One answer to one question, asked by everything that brings people on
 * foot. A squad marching to the front arrives from the same place as a crew
 * marching out to board an aircraft and as a technician posted to replace one
 * who was shot, and there was never a reason for those to be three answers —
 * the second was already documented as sharing the first, and this is where
 * they both live now.
 *
 * <p>The edge follows the approach: an attacker comes from the end of the axis
 * they are attacking along and a defender from the other. Where that edge has
 * nowhere viable to stand, the rest are tried in turn rather than the arrival
 * being refused — a map with a walled rear is a worse arrival, not an absent
 * one.
 */
public final class MapEntry {

    private MapEntry() { }

    /** The four ways onto a map. */
    private enum Edge { NORTH, SOUTH, EAST, WEST }

    /**
     * The cell this side walks on at, aiming at {@code (towardX, towardY)}.
     *
     * <p>The target is a lateral preference rather than a destination: of the
     * viable cells along the edge, the one nearest to being in line with where
     * the arrival is going, so a party bound for the west of the map does not
     * come on at the east end of its own edge.
     *
     * @return {@code {x, y}}, or null when no edge of this map has a viable cell
     */
    public static int[] forSide(BattleView sim, Faction side, TraversalAxis axis,
                                int towardX, int towardY) {
        NavigationGrid grid = sim.getGrid();
        LandingZoneScorer scorer = new LandingZoneScorer(grid, sim.getTopology());
        Edge edge = rearEdge(side, axis);
        int[] cell = scanEdge(grid, scorer, edge, towardX, towardY);
        if (cell != null) return cell;
        for (Edge fallback : Edge.values()) {
            if (fallback == edge) continue;
            cell = scanEdge(grid, scorer, fallback, towardX, towardY);
            if (cell != null) return cell;
        }
        return null;
    }

    private static Edge rearEdge(Faction side, TraversalAxis axis) {
        boolean defender = side == Faction.DEFENDER;
        if (axis == TraversalAxis.WEST_TO_EAST) {
            return defender ? Edge.EAST : Edge.WEST;
        }
        // SOUTH_TO_NORTH, and the default for a battle with no stated axis.
        return defender ? Edge.NORTH : Edge.SOUTH;
    }

    /**
     * Walk one edge of the map and return the viable cell whose lateral
     * coordinate is closest to the target's. {@code null} when the edge has no
     * viable cell at all.
     */
    private static int[] scanEdge(NavigationGrid grid, LandingZoneScorer scorer,
                                  Edge edge, int towardX, int towardY) {
        int gw = grid.getWidth();
        int gh = grid.getHeight();
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        switch (edge) {
            case NORTH -> {
                int y = gh - 1;
                for (int x = 0; x < gw; x++) {
                    if (!scorer.isViable(x, y)) continue;
                    int d = Math.abs(x - towardX);
                    if (d < bestDist) { bestDist = d; best = x; }
                }
                return best < 0 ? null : new int[]{best, y};
            }
            case SOUTH -> {
                int y = 0;
                for (int x = 0; x < gw; x++) {
                    if (!scorer.isViable(x, y)) continue;
                    int d = Math.abs(x - towardX);
                    if (d < bestDist) { bestDist = d; best = x; }
                }
                return best < 0 ? null : new int[]{best, y};
            }
            case EAST -> {
                int x = gw - 1;
                for (int y = 0; y < gh; y++) {
                    if (!scorer.isViable(x, y)) continue;
                    int d = Math.abs(y - towardY);
                    if (d < bestDist) { bestDist = d; best = y; }
                }
                return best < 0 ? null : new int[]{x, best};
            }
            case WEST -> {
                int x = 0;
                for (int y = 0; y < gh; y++) {
                    if (!scorer.isViable(x, y)) continue;
                    int d = Math.abs(y - towardY);
                    if (d < bestDist) { bestDist = d; best = y; }
                }
                return best < 0 ? null : new int[]{x, best};
            }
        }
        return null;
    }
}
