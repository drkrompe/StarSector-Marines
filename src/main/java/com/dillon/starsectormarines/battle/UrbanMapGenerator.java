package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Procedural urban-combat map generator. Produces a {@link NavigationGrid}
 * carved into a city: a grid of streets with rectangular building footprints
 * occupying the blocks between them. Open plazas occasionally replace a
 * building. Streets are guaranteed at all four edges so corner-anchored spawn
 * zones always start on walkable ground.
 *
 * <p>Deterministic given the seed. Streets and blocks are laid out by walking
 * each axis once, alternating street strip → block strip → street → block →
 * ..., then carving a building inside each (blockRow × blockCol) rectangle.
 *
 * <p>The result hands back two spawn anchor cells — top-left for marines,
 * bottom-right for defenders. Callers BFS outward from each anchor to place
 * individual units, which lets the same generator drop units into a wide
 * street, a narrow alley, or a plaza without per-shape logic.
 */
public final class UrbanMapGenerator {

    private static final int STREET_WIDTH_MIN = 3;
    private static final int STREET_WIDTH_MAX = 4;
    private static final int BLOCK_LEN_MIN    = 8;
    private static final int BLOCK_LEN_MAX    = 14;
    /** Probability a block is left as an open plaza instead of carrying a building. */
    private static final float PLAZA_CHANCE   = 0.12f;
    /** Inset between block boundary and building footprint — keeps buildings off the curb. */
    private static final int BUILDING_INSET   = 1;

    public static final class Result {
        public final NavigationGrid grid;
        public final int marineSpawnX;
        public final int marineSpawnY;
        public final int defenderSpawnX;
        public final int defenderSpawnY;

        public Result(NavigationGrid grid,
                      int marineSpawnX, int marineSpawnY,
                      int defenderSpawnX, int defenderSpawnY) {
            this.grid = grid;
            this.marineSpawnX = marineSpawnX;
            this.marineSpawnY = marineSpawnY;
            this.defenderSpawnX = defenderSpawnX;
            this.defenderSpawnY = defenderSpawnY;
        }
    }

    private UrbanMapGenerator() {}

    public static Result generate(int width, int height, long seed) {
        Random rng = new Random(seed);
        NavigationGrid grid = new NavigationGrid(width, height);

        // Everything starts walkable; we'll carve buildings into the block interiors.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid.setWalkableFloor(x, y);
            }
        }

        List<int[]> blockRowsY = blockStripsAlongAxis(height, rng);
        List<int[]> blockColsX = blockStripsAlongAxis(width,  rng);

        for (int[] row : blockRowsY) {
            for (int[] col : blockColsX) {
                placeBuilding(grid, col[0], row[0], col[1], row[1], rng);
            }
        }

        int[] marine   = findNearestWalkable(grid, 2,             2);
        int[] defender = findNearestWalkable(grid, width - 3,     height - 3);

        return new Result(grid, marine[0], marine[1], defender[0], defender[1]);
    }

    /**
     * Walks one axis (length cells), alternating street → block → street → block,
     * starting and ending on a street. Returns inclusive [start, end] for each
     * block strip; street cells fill the gaps by elimination and don't need
     * per-strip metadata.
     */
    private static List<int[]> blockStripsAlongAxis(int length, Random rng) {
        List<int[]> blocks = new ArrayList<>();
        int cursor = 0;
        boolean street = true; // edge of map is always street
        while (cursor < length) {
            int strip = street
                    ? STREET_WIDTH_MIN + rng.nextInt(STREET_WIDTH_MAX - STREET_WIDTH_MIN + 1)
                    : BLOCK_LEN_MIN    + rng.nextInt(BLOCK_LEN_MAX    - BLOCK_LEN_MIN    + 1);
            int end = Math.min(cursor + strip - 1, length - 1);
            // Drop runt blocks at the trailing edge so we don't get a 1-2 cell
            // building flush against the boundary.
            if (!street && end - cursor >= 3) {
                blocks.add(new int[]{cursor, end});
            }
            cursor = end + 1;
            street = !street;
        }
        return blocks;
    }

    private static void placeBuilding(NavigationGrid grid, int l, int t, int r, int b, Random rng) {
        if (rng.nextFloat() < PLAZA_CHANCE) return;
        int bl = l + BUILDING_INSET;
        int bt = t + BUILDING_INSET;
        int br = r - BUILDING_INSET;
        int bb = b - BUILDING_INSET;
        if (br - bl < 1 || bb - bt < 1) return;
        for (int y = bt; y <= bb; y++) {
            for (int x = bl; x <= br; x++) {
                grid.setWalkable(x, y, false);
            }
        }
    }

    private static int[] findNearestWalkable(NavigationGrid grid, int sx, int sy) {
        if (grid.inBounds(sx, sy) && grid.isWalkable(sx, sy)) return new int[]{sx, sy};
        boolean[] visited = new boolean[grid.getWidth() * grid.getHeight()];
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sx, sy});
        if (grid.inBounds(sx, sy)) visited[sy * grid.getWidth() + sx] = true;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (grid.isWalkable(p[0], p[1])) return p;
            int[][] nbrs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nbrs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                int idx = ny * grid.getWidth() + nx;
                if (visited[idx]) continue;
                visited[idx] = true;
                q.add(new int[]{nx, ny});
            }
        }
        return new int[]{sx, sy};
    }
}
