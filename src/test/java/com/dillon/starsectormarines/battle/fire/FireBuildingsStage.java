package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.creep.CreepVegetationSnapshotSuite;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A grass fire into two houses on the green colony (`fire-nouns.md`, Buildings),
 * shared by the {@code fire-buildings} scene and snapshot suite: the light-walled
 * house standing most fully in grass, and the best other house in the same view,
 * built fortified for the occasion (its walls raised to {@link #FORTIFIED_HP}
 * before the fire is made). A fire line is lit west of both, wind toward them.
 */
final class FireBuildingsStage {

    static final int FORTIFIED_HP = 150;
    /** The furthest the fortified house's centre may be from the light house's: both in one play-zoom view. */
    private static final int NEIGHBOUR_RANGE = 22;

    final BattleSimulation sim;
    final FireField fire;
    /** {id, x0, y0, x1, y1}. */
    final int[] light, fortified;

    private FireBuildingsStage(BattleSimulation sim, int[] light, int[] fortified) {
        this.sim = sim;
        this.light = light;
        this.fortified = fortified;
        NavigationGrid grid = sim.getGrid();
        for (int y = fortified[2]; y <= fortified[4]; y++)
            for (int x = fortified[1]; x <= fortified[3]; x++)
                if (!grid.isWalkable(x, y) && sim.getTopology().isWall(x, y)) grid.setWallHp(x, y, FORTIFIED_HP);
        this.fire = sim.fireField();
        fire.setWind(1f, 0f, 0.5f);
        // A grass fire carries only a few cells (FireFuel.spread), so each house gets its own line, lit just
        // upwind of its west wall, rather than one line a dozen cells off that would go out in the grass.
        lightUpwindOf(grid, light);
        lightUpwindOf(grid, fortified);
    }

    private void lightUpwindOf(NavigationGrid grid, int[] house) {
        int lx = Math.max(0, house[1] - 2);
        int ya = Math.max(0, house[2] - 3);
        int yb = Math.min(grid.getHeight() - 1, house[4] + 3);
        for (int y = ya; y <= yb; y++) fire.addHeat(lx, y, 3000);
    }

    static FireBuildingsStage build() throws Exception {
        BattleSimulation sim = CreepVegetationSnapshotSuite.verdantColony();
        List<int[]> houses = candidates(sim);
        if (houses.isEmpty()) throw new IllegalStateException("no light-walled house in grass on the colony");
        int[] light = houses.get(0);
        int[] fort = null;
        for (int i = 1; i < houses.size() && fort == null; i++) {
            int[] h = houses.get(i);
            if (Math.abs(cx(h) - cx(light)) <= NEIGHBOUR_RANGE && Math.abs(cy(h) - cy(light)) <= NEIGHBOUR_RANGE * 0.55f) fort = h;
        }
        if (fort == null) throw new IllegalStateException("no second house beside the first");
        return new FireBuildingsStage(sim, light, fort);
    }

    static float cx(int[] b) { return (b[1] + b[3] + 1) / 2f; }
    static float cy(int[] b) { return (b[2] + b[4] + 1) / 2f; }

    float viewX() { return (cx(light) + cx(fortified)) / 2f; }
    float viewY() { return (cy(light) + cy(fortified)) / 2f; }

    static boolean inside(int[] b, int x, int y) { return x >= b[1] && x <= b[3] && y >= b[2] && y <= b[4]; }

    /** Whether a cell is the building's own: a floor of it, or a wall or window in its outline. */
    boolean ofBuilding(int[] b, int x, int y) {
        if (!inside(b, x, y)) return false;
        CellTopology topo = sim.getTopology();
        return topo.getBuildingId(x, y) == b[0] || topo.isWall(x, y) || topo.isWindow(x, y);
    }

    /** Light-walled houses of modest size with a doorway, most fully in grass first. */
    private static List<int[]> candidates(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topo = sim.getTopology();
        int w = grid.getWidth(), h = grid.getHeight(), max = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) max = Math.max(max, topo.getBuildingId(x, y));
        int[] minX = new int[max + 1], minY = new int[max + 1], maxX = new int[max + 1], maxY = new int[max + 1];
        boolean[] heavy = new boolean[max + 1], door = new boolean[max + 1];
        Arrays.fill(minX, Integer.MAX_VALUE);
        Arrays.fill(minY, Integer.MAX_VALUE);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int b = topo.getBuildingId(x, y);
            if (b <= 0) continue;
            minX[b] = Math.min(minX[b], x); minY[b] = Math.min(minY[b], y);
            maxX[b] = Math.max(maxX[b], x); maxY[b] = Math.max(maxY[b], y);
            if (!grid.isWalkable(x, y) && grid.getWallHp(x, y) > BattleFire.LIGHT_WALL_HP) heavy[b] = true;
            if (grid.isDoorway(x, y)) door[b] = true;
        }
        List<int[]> out = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        for (int b = 1; b <= max; b++) {
            if (minX[b] == Integer.MAX_VALUE || heavy[b] || !door[b]) continue;
            int bw = maxX[b] - minX[b] + 1, bh = maxY[b] - minY[b] + 1;
            if (bw < 6 || bh < 6 || bw > 18 || bh > 18 || minX[b] < 16) continue;
            int grass = 0, ring = 0;
            for (int y = minY[b] - 3; y <= maxY[b] + 3; y++) for (int x = minX[b] - 3; x <= maxX[b] + 3; x++) {
                if (x >= minX[b] && x <= maxX[b] && y >= minY[b] && y <= maxY[b]) continue;
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                ring++;
                if (topo.getGroundKind(x, y) == GroundKind.GRASS) grass++;
            }
            float score = ring == 0 ? 0f : grass / (float) ring;
            int at = 0;
            while (at < scores.size() && scores.get(at) >= score) at++;
            scores.add(at, score);
            out.add(at, new int[]{b, minX[b], minY[b], maxX[b], maxY[b]});
        }
        return out;
    }
}
