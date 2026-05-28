package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
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
 * delivery ring-search out from a rally hint); candidate <em>validation,
 * gating, and ranking</em> are owned here.
 *
 * <p><b>Viability</b> is the load-bearing guarantee: a cell is a viable
 * dropoff only if it is in bounds, walkable, and not inside a building
 * footprint. Water and walls are already non-walkable, so
 * {@link NavigationGrid#isWalkable} covers them; the building-footprint check
 * ({@link CellTopology#getBuildingId}) is what stops a drop onto a walkable
 * building <em>interior</em> cell.
 *
 * <p><b>Selection</b> is proximity-primary: among cells that are viable and
 * clear enough ({@code minClearance} — a hard pad-size gate, so an air drop
 * can demand more open space than a walk-in), the nearest to the hint wins,
 * ties broken by higher {@link #quality} (open ground + clearance), then by
 * row-major iteration order (deterministic — biases up-and-left of the hint on
 * otherwise-equal flat ground). Quality is a tie-breaker, not traded against
 * distance, so the choice never depends on the search radius.
 */
public final class LandingZoneScorer {

    /** {@link #quality} bonus for a cell whose ground reads as an open outdoor LZ surface. */
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

    /** Count of viable cells among the 8 neighbours — how much open space surrounds a dropoff. */
    public int clearance(int x, int y) {
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                if (isViable(x + dx, y + dy)) n++;
            }
        }
        return n;
    }

    /**
     * Tie-break suitability of a viable cell: {@link #clearance} plus a bonus
     * for open outdoor ground. {@link Integer#MIN_VALUE} for a non-viable cell
     * so it can never win a ranking.
     */
    public int quality(int x, int y) {
        if (!isViable(x, y)) return Integer.MIN_VALUE;
        return clearance(x, y) + (isOpenGround(x, y) ? OPEN_GROUND_BONUS : 0);
    }

    /** {@link #bestNear(int, int, int, int)} with no clearance requirement. */
    public int[] bestNear(int hintX, int hintY, int radius) {
        return bestNear(hintX, hintY, radius, 0);
    }

    /**
     * Best viable dropoff within Manhattan {@code radius} of the hint that has
     * at least {@code minClearance} open neighbours. Nearest passing cell wins;
     * ties go to higher {@link #quality}, then row-major order. Returns
     * {@code {x, y}}, or {@code null} when nothing in range passes — e.g. the
     * hint sits deep inside a building with no open ground in reach, or no cell
     * is clear enough for the requested pad size.
     */
    public int[] bestNear(int hintX, int hintY, int radius, int minClearance) {
        int[] best = null;
        int bestDist = Integer.MAX_VALUE;
        int bestQuality = Integer.MIN_VALUE;
        for (int y = hintY - radius; y <= hintY + radius; y++) {
            for (int x = hintX - radius; x <= hintX + radius; x++) {
                int dist = Math.abs(x - hintX) + Math.abs(y - hintY);
                if (dist > radius) continue; // Manhattan diamond — matches the ranking metric
                if (!isViable(x, y) || clearance(x, y) < minClearance) continue;
                int q = quality(x, y);
                if (dist < bestDist || (dist == bestDist && q > bestQuality)) {
                    bestDist = dist;
                    bestQuality = q;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /** {@link #bestAmong(int[], int, int, int)} with no clearance requirement. */
    public int[] bestAmong(int[] candidatesXY, int hintX, int hintY) {
        return bestAmong(candidatesXY, hintX, hintY, 0);
    }

    /**
     * Best viable cell among caller-supplied candidates (flat {@code x,y}
     * pairs), gated and ranked like {@link #bestNear}. For means that generate
     * their own candidate source — convoy road-graph junctions, walk-in
     * map-edge cells. Returns {@code {x, y}}, or {@code null} when no candidate
     * passes.
     *
     * @throws IllegalArgumentException if {@code candidatesXY} is not an even
     *         number of {@code x,y} entries (a caller-construction bug).
     */
    public int[] bestAmong(int[] candidatesXY, int hintX, int hintY, int minClearance) {
        if (candidatesXY.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "candidatesXY must be flat x,y pairs (even length); got " + candidatesXY.length);
        }
        int[] best = null;
        int bestDist = Integer.MAX_VALUE;
        int bestQuality = Integer.MIN_VALUE;
        for (int i = 0; i < candidatesXY.length; i += 2) {
            int x = candidatesXY[i];
            int y = candidatesXY[i + 1];
            if (!isViable(x, y) || clearance(x, y) < minClearance) continue;
            int dist = Math.abs(x - hintX) + Math.abs(y - hintY);
            int q = quality(x, y);
            if (dist < bestDist || (dist == bestDist && q > bestQuality)) {
                bestDist = dist;
                bestQuality = q;
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
