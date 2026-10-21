package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.List;

/**
 * The ground an open-field landing keeps clear: a disc round the field's
 * centre inside which nothing is built, walled or tall.
 *
 * <p>It is a statement about what the landing force can see, so it is checked
 * as one — against the finished map, after every pass that might have put
 * something on it — rather than trusted from the claim that reserved it. A
 * claim keeps a neighbour's buildings out; it says nothing about a parked
 * truck, a tree the landscape pass liked, or a fence a fill left on a road.
 *
 * <p>The map edge is not an obstruction. Ground off the map is ground nobody
 * stands on, and a field laid against the edge it lands across is the point.
 *
 * @param radius cells from the centre inside which nothing may stand
 */
public record FieldOfFire(int centerX, int centerY, int radius) {

    /** The field the plan's landing place promises, or {@code null} when it promises none. */
    public static FieldOfFire of(PrecinctPlan plan) {
        Precinct landing = plan == null ? null : plan.landingPlace();
        if (landing == null || !landing.landing().hasFieldOfFire()) return null;
        return new FieldOfFire(landing.seedX(), landing.seedY(),
                landing.landing().fieldOfFire());
    }

    /**
     * Cells of open country past the disc's far side before the town may begin,
     * so the colony's first wall stands outside the radius rather than on it.
     */
    public static final int COUNTRY_MARGIN = 8;

    /**
     * How far from the edge it lies against the field's open country reaches:
     * the centre's own depth, the radius and {@link #COUNTRY_MARGIN}.
     *
     * <p>The country is a band across the whole edge, not a ring round the
     * disc. A ring leaves the town free to grow up both flanks to the map edge,
     * and a field with the town on three sides of it is a clearing in the
     * colony rather than fields outside it: the company would be landing
     * inside what it came to fight its way into.
     */
    public int countryDepth(int width, int height) {
        return edgeDistance(centerX, centerY, width, height) + radius + COUNTRY_MARGIN;
    }

    /** Whether a cell is the field's open country: its band along the edge. */
    public boolean inCountry(int x, int y, int width, int height) {
        return edgeDistance(x, y, width, height) <= countryDepth(width, height);
    }

    /**
     * The map left for the town once the country is taken, as an inclusive
     * {@code {x0, y0, x1, y1}} rect.
     */
    public int[] townBounds(int width, int height) {
        int depth = countryDepth(width, height) + 1;
        return switch (edge(width, height)) {
            case 0 -> new int[]{depth, 0, width - 1, height - 1};
            case 1 -> new int[]{0, 0, width - 1 - depth, height - 1};
            case 2 -> new int[]{0, depth, width - 1, height - 1};
            default -> new int[]{0, 0, width - 1, height - 1 - depth};
        };
    }

    /**
     * Cells from a cell to the map edge the field lies against.
     *
     * <p>That edge is the one nearest the centre. A field sits less than its
     * radius in from the edge it lands across, and a map the field fits on is
     * more than twice that from its other edges, so the nearest is the one.
     */
    private int edgeDistance(int x, int y, int width, int height) {
        return switch (edge(width, height)) {
            case 0 -> x;
            case 1 -> width - 1 - x;
            case 2 -> y;
            default -> height - 1 - y;
        };
    }

    /** The edge nearest the centre: 0 low x, 1 high x, 2 low y, 3 high y. */
    private int edge(int width, int height) {
        int[] distance = {centerX, width - 1 - centerX, centerY, height - 1 - centerY};
        int best = 0;
        for (int i = 1; i < distance.length; i++) {
            if (distance[i] < distance[best]) best = i;
        }
        return best;
    }

    /** Whether a cell lies inside the disc. */
    public boolean contains(int x, int y) {
        long dx = x - centerX;
        long dy = y - centerY;
        return dx * dx + dy * dy <= (long) radius * radius;
    }

    /**
     * The first cell inside the disc something stands on, or {@code null} when
     * the field is clear. Scanned in a fixed order, so the answer is the same
     * cell every time it is asked.
     */
    public int[] firstObstruction(NavigationGrid grid, CellTopology topology,
                                  List<Doodad> doodads) {
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (!grid.inBounds(x, y) || !contains(x, y)) continue;
                if (obstructs(grid, topology, x, y)) return new int[]{x, y};
            }
        }
        if (doodads != null) {
            for (Doodad doodad : doodads) {
                if (doodad.cover <= Doodad.COVER_NONE) continue;
                for (int y = doodad.cellY; y < doodad.cellY + doodad.footprintCellsY; y++) {
                    for (int x = doodad.cellX; x < doodad.cellX + doodad.footprintCellsX; x++) {
                        if (contains(x, y)) return new int[]{x, y};
                    }
                }
            }
        }
        return null;
    }

    /**
     * How far out from the centre the ground is actually clear: the distance to
     * the nearest obstruction, or the radius the map affords when there is
     * none. Evidence reads this; generation asks {@link #firstObstruction}.
     */
    public double clearRadius(NavigationGrid grid, CellTopology topology,
                              List<Doodad> doodads, int searchRadius) {
        double best = searchRadius;
        for (int y = centerY - searchRadius; y <= centerY + searchRadius; y++) {
            for (int x = centerX - searchRadius; x <= centerX + searchRadius; x++) {
                if (!grid.inBounds(x, y) || !obstructs(grid, topology, x, y)) continue;
                best = Math.min(best, Math.hypot(x - centerX, y - centerY));
            }
        }
        if (doodads != null) {
            for (Doodad doodad : doodads) {
                if (doodad.cover <= Doodad.COVER_NONE) continue;
                for (int y = doodad.cellY; y < doodad.cellY + doodad.footprintCellsY; y++) {
                    for (int x = doodad.cellX; x < doodad.cellX + doodad.footprintCellsX; x++) {
                        best = Math.min(best, Math.hypot(x - centerX, y - centerY));
                    }
                }
            }
        }
        return best;
    }

    /**
     * Whether one cell is something a body could hide behind or that stops a
     * shot: a building, a wall, a fixture, a parked vehicle, natural solid
     * ground, or anything that blocks sight. Water stops a walk and not a
     * round, so it is open ground for this question.
     */
    static boolean obstructs(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (topology.getBuildingId(x, y) != 0) return true;
        if (topology.isWall(x, y) || topology.isFixture(x, y) || topology.isVehicle(x, y)) {
            return true;
        }
        if (grid.blocksLineOfSight(x, y)) return true;
        return !grid.isWalkable(x, y) && !topology.isWater(x, y);
    }
}
