package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * A frozen vehicle-routing world whose expensive per-cell derivatives are
 * computed only where a route search reaches. Unknown clearance is distinct
 * from blocked clearance: a detour outside the explored area stays available.
 *
 * <p>The raw navigation and ground-kind snapshots are taken together on the
 * game thread. A running proof is still invalidated by a live grid revision,
 * while a committed route may retain this view for recovery without later
 * wrecks changing the meaning of an unexamined cell.
 */
public final class ProgressiveVehicleField implements
        GridPathfinder.IndexedPassability, GridPathfinder.IndexedCost {

    private static final byte UNKNOWN = 0;
    private static final byte BLOCKED = 1;
    private static final byte PASSABLE = 2;
    private static final GroundKind[] KINDS = GroundKind.values();

    private final NavigationGrid grid;
    private final byte[] groundKinds;
    private final byte[] clearance;
    private final float[] cost;
    private final int width;
    private final int height;
    private final int radiusCells;
    private int clearanceEvaluations;
    private int costEvaluations;

    /** Captures raw routing inputs, but does not erode or cost a cell. */
    public static ProgressiveVehicleField capture(NavigationGrid liveGrid,
                                                  CellTopology topology,
                                                  int radiusCells) {
        if (liveGrid.getWidth() != topology.getWidth()
                || liveGrid.getHeight() != topology.getHeight()) {
            throw new IllegalArgumentException("Routing grid/topology dimensions differ");
        }
        return new ProgressiveVehicleField(liveGrid.copyVehicleRoutingTopology(),
                topology.copyGroundKinds(), radiusCells);
    }

    private ProgressiveVehicleField(NavigationGrid grid, byte[] groundKinds,
                                    int radiusCells) {
        this.grid = grid;
        this.groundKinds = groundKinds;
        this.width = grid.getWidth();
        this.height = grid.getHeight();
        this.radiusCells = Math.max(0, radiusCells);
        this.clearance = new byte[width * height];
        this.cost = new float[width * height];
    }

    /** The unchanging navigation topology used by search and turn refinement. */
    public NavigationGrid grid() { return grid; }

    public int width() { return width; }

    public int height() { return height; }

    public int clearanceEvaluations() { return clearanceEvaluations; }

    public int costEvaluations() { return costEvaluations; }

    /** Exposes tri-state coverage to diagnostics; zero means unexamined. */
    public int unexploredCells() { return clearance.length - clearanceEvaluations; }

    @Override
    public boolean isPassable(int index) {
        if (index < 0 || index >= clearance.length) return false;
        byte cached = clearance[index];
        if (cached == UNKNOWN) {
            cached = VehicleClearance.fitsAt(grid, index % width, index / width,
                    radiusCells) ? PASSABLE : BLOCKED;
            clearance[index] = cached;
            clearanceEvaluations++;
        }
        return cached == PASSABLE;
    }

    public boolean isPassable(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height
                && isPassable(y * width + x);
    }

    @Override
    public float costAt(int index) {
        if (index < 0 || index >= cost.length) return TerrainCostField.COST_AVOID;
        float cached = cost[index];
        if (cached == 0f) {
            cached = TerrainCostField.costFor(KINDS[Byte.toUnsignedInt(groundKinds[index])]);
            cost[index] = cached;
            costEvaluations++;
        }
        return cached;
    }
}
