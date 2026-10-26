package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.List;

/**
 * Loopholes a holdout's people knock through the walls of the building they
 * hold ({@code colony-breach-nouns.md}, Redoubt). A colony building chosen for
 * a holdout may be a blank block, and blank it is a box its garrison can fight
 * from only through its door; every wall gets openings to shoot out of.
 *
 * <p>A loophole is the window an ordinary building shell authors, authored the
 * same way: the wall cell becomes floor and a window barrier stands on its
 * outward edge, so a defender stands in it and shoots through and nobody walks
 * through it. Only a straight run of wall with the building's floor behind it
 * and open ground before it takes one, and an existing window or door is left
 * as it is.
 */
public final class HoldoutLoopholes {

    /** Cells between loopholes along a wall. */
    static final int PITCH = 4;
    /** Shortest run of wall worth a loophole, so a stub between a door and a corner stays solid. */
    static final int MIN_RUN = 3;

    private HoldoutLoopholes() {}

    /** {@code -Dbattle.colonyBreach.loopholes=false} is the control: holdouts keep the walls they were generated with. */
    static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("battle.colonyBreach.loopholes", "true"));
    }

    /**
     * Where the loopholes go along a run of {@code run} wall cells: every
     * {@link #PITCH} cells, centred on the run so a wall's openings sit
     * symmetrically on it; none on a run shorter than {@link #MIN_RUN}.
     */
    static int[] positions(int run) {
        if (run < MIN_RUN) return new int[0];
        int count = 1 + (run - 1) / PITCH;
        int first = (run - 1 - (count - 1) * PITCH) / 2;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = first + i * PITCH;
        return out;
    }

    /** Cuts the loopholes in the walls of the building at {@code place}; returns how many. */
    static int cut(BattleSimulation sim, PointOfInterest place) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        int building = topology.getBuildingId(place.interiorAnchorX, place.interiorAnchorY);
        if (building <= 0) return 0;
        int cut = 0;
        // A place's top is its low-y edge, so the wall there faces S (decreasing y).
        Object[][] walls = {
                {Direction.W, place.left, true}, {Direction.E, place.right, true},
                {Direction.S, place.top, false}, {Direction.N, place.bottom, false}};
        for (Object[] wall : walls) {
            Direction outward = (Direction) wall[0];
            int fixed = (Integer) wall[1];
            boolean vertical = (Boolean) wall[2];
            int from = vertical ? place.top : place.left, to = vertical ? place.bottom : place.right;
            List<int[]> run = new ArrayList<>();
            for (int along = from; along <= to + 1; along++) {
                int x = vertical ? fixed : along, y = vertical ? along : fixed;
                if (along <= to && eligible(grid, topology, building, x, y, outward)) {
                    run.add(new int[]{x, y});
                    continue;
                }
                for (int i : positions(run.size())) {
                    int[] c = run.get(i);
                    if (open(grid, topology, c[0], c[1], outward)) cut++;
                }
                run.clear();
            }
        }
        return cut;
    }

    /**
     * Cuts loopholes along one row of wall facing {@code outward} (N or S), from {@code left} to {@code right}: the
     * same openings, every {@link #PITCH} cells along each straight run of wall with walkable ground behind it and
     * before it, a building's wall or a yard's. A Cordon school's courtyard block and yard wall are its redoubt's
     * front face, and the troops holding it knock their firing slits through them ({@code cordon-nouns.md},
     * Construction); returns how many.
     */
    public static int cutRow(BattleSimulation sim, int left, int right, int y, Direction outward) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        int cut = 0;
        List<int[]> run = new ArrayList<>();
        for (int x = left; x <= right + 1; x++) {
            if (x <= right && slit(grid, topology, x, y, outward)) {
                run.add(new int[]{x, y});
                continue;
            }
            for (int i : positions(run.size())) {
                int[] c = run.get(i);
                if (open(grid, topology, c[0], c[1], outward)) cut++;
            }
            run.clear();
        }
        return cut;
    }

    /** A solid straight wall cell with walkable ground behind it and before it. */
    private static boolean slit(NavigationGrid grid, CellTopology topology, int x, int y, Direction outward) {
        int ix = x - outward.dx, iy = y - outward.dy, ox = x + outward.dx, oy = y + outward.dy;
        if (!grid.inBounds(ix, iy) || !grid.inBounds(ox, oy) || !grid.inBounds(x, y)) return false;
        if (grid.isWalkable(x, y) || grid.isDoorway(x, y) || !topology.isWall(x, y)) return false;
        if (!grid.isWalkable(ix, iy) || !grid.isWalkable(ox, oy)) return false;
        int ax = outward.dy, ay = outward.dx;
        return topology.isWall(x + ax, y + ay) && topology.isWall(x - ax, y - ay);
    }

    /** A solid wall cell of the building with its floor behind it and open ground before it. */
    private static boolean eligible(NavigationGrid grid, CellTopology topology, int building,
                                    int x, int y, Direction outward) {
        int ix = x - outward.dx, iy = y - outward.dy, ox = x + outward.dx, oy = y + outward.dy;
        if (!grid.inBounds(ix, iy) || !grid.inBounds(ox, oy) || !grid.inBounds(x, y)) return false;
        if (grid.isWalkable(x, y) || grid.isDoorway(x, y) || !topology.isWall(x, y)) return false;
        if (!grid.isWalkable(ix, iy) || topology.getBuildingId(ix, iy) != building) return false;
        if (!grid.isWalkable(ox, oy) || topology.getBuildingId(ox, oy) == building) return false;
        // A straight stretch of wall: both neighbours along it are wall too, so opening the cell opens one face only.
        int ax = outward.dy, ay = outward.dx;
        return topology.isWall(x + ax, y + ay) && topology.isWall(x - ax, y - ay);
    }

    private static boolean open(NavigationGrid grid, CellTopology topology, int x, int y, Direction outward) {
        if (grid.getEdgeBarrier(x, y, outward) != null) return false;
        if (!grid.isEdgePassable(x + outward.dx, y + outward.dy, outward.opposite())) return false;
        grid.setWalkableFloor(x, y);
        topology.setWallDirMask(x, y, 0);
        topology.setWindow(x, y, false);
        grid.placeEdgeBarrier(x, y, outward, SharedEdgeBarrier.Kind.WINDOW);
        for (int cy = y - 1; cy <= y + 1; cy++)
            for (int cx = x - 1; cx <= x + 1; cx++)
                if (grid.inBounds(cx, cy)) grid.recomputeCoverAt(cx, cy);
        return true;
    }
}
