package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofCover;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * What each building's roof is laid over and drawn in, settled once a battle: its style, its grain, and the cells it
 * covers — its floor, then the walls it is carried on ({@link RoofCover#owner}). A wall between two buildings is the
 * first one's. Both roof paths read it, so they cover the same cells.
 *
 * <p>Rebuilt only for another battle's topology or buildings: a roof never spreads to a cell it did not cover at the
 * start, because nothing builds a wall under one.
 */
final class RoofPlans {

    /** One building's roof. */
    static final class Plan {
        final RoofStyle style;
        final int grainTurns;
        /** Cell indices, floor first. */
        final int[] cells;

        Plan(RoofStyle style, int grainTurns, int[] cells) {
            this.style = style;
            this.grainTurns = grainTurns;
            this.cells = cells;
        }
    }

    private CellTopology topology;
    private Buildings buildings;
    private final Map<Building, Plan> plans = new HashMap<>();

    /** The plan of {@code building}, out of the battle's buildings. */
    Plan of(CellTopology topology, Buildings buildings, Building building) {
        bind(topology, buildings);
        return plans.get(building);
    }

    private void bind(CellTopology topology, Buildings buildings) {
        if (topology == this.topology && buildings == this.buildings) return;
        this.topology = topology;
        this.buildings = buildings;
        plans.clear();
        int[] claimed = new int[topology.getWidth() * topology.getHeight()];
        for (Building b : buildings.all()) {
            plans.put(b, new Plan(RoofStyle.of(b, topology), RoofStyle.grainTurns(b),
                    roofedCells(topology, b, claimed)));
        }
    }

    /**
     * A building's floor cells and the walls its roof covers, as cell indices, none of them already {@code claimed}
     * by a building planned before it; the cells it takes are marked with its id.
     */
    static int[] roofedCells(CellTopology topology, Building b, int[] claimed) {
        int gridW = topology.getWidth();
        int[] out = new int[b.cellCount() * 2 + 4];
        int n = 0;
        for (int i = 0; i < b.cellCount(); i++) {
            int cx = b.cellsX[i];
            int cy = b.cellsY[i];
            if (!topology.inBounds(cx, cy)) continue;
            int at = cy * gridW + cx;
            if (claimed[at] != 0) continue;
            claimed[at] = b.id;
            if (n == out.length) out = Arrays.copyOf(out, n * 2);
            out[n++] = at;
        }
        for (int i = 0; i < b.cellCount(); i++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = b.cellsX[i] + dx;
                    int ny = b.cellsY[i] + dy;
                    if (!topology.inBounds(nx, ny)) continue;
                    int at = ny * gridW + nx;
                    if (claimed[at] != 0 || topology.getBuildingId(nx, ny) != 0) continue;
                    if (RoofCover.owner(topology, nx, ny) != b.id) continue;
                    claimed[at] = b.id;
                    if (n == out.length) out = Arrays.copyOf(out, n * 2);
                    out[n++] = at;
                }
            }
        }
        return Arrays.copyOf(out, n);
    }
}
