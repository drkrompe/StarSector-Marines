package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Single owner of "where can troops safely land or deboard" for reinforcement
 * delivery. Every means (convoy, shuttle, walk-in, and the front-line
 * delivery from progressive reinforcement) routes its final dropoff-cell pick
 * through here rather than re-rolling its own walkability BFS — so the rule
 * "never deboard inside a building or on impassable ground" lives in exactly
 * one place.
 *
 * <p>Design: {@code roadmap/conquest/stories/progressive-reinforcement.md}
 * (slice 3a). Candidate <em>generation</em> stays per-means (convoy walks
 * road-graph junctions, walk-in scans the map edge, shuttle / front-line
 * delivery ring-search out from a rally hint); candidate <em>validation and
 * ranking</em> is owned here.
 *
 * <p>Viability is the load-bearing guarantee: a cell is a viable dropoff only
 * if it is in bounds, walkable, and not inside a building footprint. Water and
 * walls are already non-walkable, so {@link NavigationGrid#isWalkable} covers
 * them; the building-footprint check ({@link CellTopology#getBuildingId})
 * is what stops a drop onto a walkable building <em>interior</em> cell.
 */
public final class LandingZoneScorer {

    /** Quality bonus for a cell whose ground reads as an open outdoor LZ surface. */
    static final int OPEN_GROUND_BONUS = 3;

    private final NavigationGrid grid;
    private final CellTopology topo;

    public LandingZoneScorer(NavigationGrid grid, CellTopology topo) {
        this.grid = grid;
        this.topo = topo;
    }

    /**
     * A cell troops may be put on: in bounds, walkable, and not inside a
     * building footprint. (Water and walls are non-walkable, so they're
     * already excluded by the walkability check.)
     */
    public boolean isViable(int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y) && topo.getBuildingId(x, y) == 0;
    }

    /**
     * Suitability of a viable cell as a dropoff: open-space clearance (count of
     * viable 8-neighbours) plus a bonus for open outdoor ground. Returns
     * {@link Integer#MIN_VALUE} for a non-viable cell so it can never win a
     * ranking.
     */
    public int quality(int x, int y) {
        if (!isViable(x, y)) return Integer.MIN_VALUE;
        int clearance = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                if (isViable(x + dx, y + dy)) clearance++;
            }
        }
        return clearance + (isOpenGround(x, y) ? OPEN_GROUND_BONUS : 0);
    }

    /**
     * Best viable dropoff within Chebyshev {@code radius} of the hint, ranked
     * by {@code quality - manhattanDistance} so a nearby decent cell beats a
     * distant pristine one (and the hint itself wins when it's already a good
     * open cell). Returns {@code {x, y}}, or {@code null} when nothing in the
     * search box is viable — e.g. the hint sits deep inside a building with no
     * open ground in reach.
     */
    public int[] bestNear(int hintX, int hintY, int radius) {
        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int y = hintY - radius; y <= hintY + radius; y++) {
            for (int x = hintX - radius; x <= hintX + radius; x++) {
                if (!isViable(x, y)) continue;
                int score = quality(x, y) - (Math.abs(x - hintX) + Math.abs(y - hintY));
                if (score > bestScore) {
                    bestScore = score;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /**
     * Best viable cell among caller-supplied candidates (flat {@code x,y}
     * pairs), ranked the same way relative to the hint. For means that
     * generate their own candidate source — convoy road-graph junctions,
     * walk-in map-edge cells. Returns {@code {x, y}}, or {@code null} when no
     * candidate is viable.
     */
    public int[] bestAmong(int[] candidatesXY, int hintX, int hintY) {
        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i + 1 < candidatesXY.length; i += 2) {
            int x = candidatesXY[i];
            int y = candidatesXY[i + 1];
            if (!isViable(x, y)) continue;
            int score = quality(x, y) - (Math.abs(x - hintX) + Math.abs(y - hintY));
            if (score > bestScore) {
                bestScore = score;
                best = new int[]{x, y};
            }
        }
        return best;
    }

    private boolean isOpenGround(int x, int y) {
        switch (topo.getGroundKind(x, y)) {
            case STREET:
            case COURTYARD:
            case STRIPED:
            case LZ_MARKER:
            case SIDEWALK:
            case BRICK:
                return true;
            default:
                return false;
        }
    }
}
