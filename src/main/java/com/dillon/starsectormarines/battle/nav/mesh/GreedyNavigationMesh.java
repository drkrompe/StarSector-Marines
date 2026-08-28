package com.dillon.starsectormarines.battle.nav.mesh;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic rectangular decomposition of the authoritative navigation grid.
 *
 * <p>The mesh is a derived acceleration structure: cells and shared edges remain
 * authoritative. A region contains only walkable cells whose internal cardinal
 * transitions are all passable. Doorway cells remain singleton regions so the
 * mesh cannot erase an authored routing boundary. Closed shared edges therefore
 * become seams even when both adjacent cells are standable.
 *
 * <p>Construction scans row-major. At each unassigned cell it chooses the
 * largest-area rectangle anchored there, breaking ties toward width and then
 * height. This is deliberately deterministic rather than a globally minimal
 * rectangle cover. {@link #rebuild()} assembles a complete immutable snapshot
 * before publishing it, so readers never observe a half-rebuilt mesh.
 */
public final class GreedyNavigationMesh {

    private final NavigationGrid grid;
    private volatile Snapshot snapshot;

    public GreedyNavigationMesh(NavigationGrid grid) {
        this.grid = grid;
        this.snapshot = Snapshot.empty(grid.getWidth(), grid.getHeight());
        rebuild();
    }

    /** Rebuilds and atomically publishes the mesh derived from the current grid. */
    public void rebuild() {
        Snapshot previous = snapshot;
        snapshot = build(previous.revision() + 1L);
    }

    /** One internally consistent immutable mesh view. */
    public Snapshot snapshot() {
        return snapshot;
    }

    public int regionIdAt(int x, int y) {
        return snapshot.regionIdAt(x, y);
    }

    public Region regionAt(int x, int y) {
        return snapshot.regionAt(x, y);
    }

    private Snapshot build(long revision) {
        int width = grid.getWidth();
        int height = grid.getHeight();
        int[] cellToRegion = new int[width * height];
        Arrays.fill(cellToRegion, -1);
        List<Region> regions = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (cellToRegion[idx] >= 0 || !grid.isWalkable(x, y)) continue;

                int regionWidth = 1;
                int regionHeight = 1;
                boolean doorway = grid.isDoorway(x, y);
                if (!doorway) {
                    int[] extent = largestRectangleAt(x, y, cellToRegion);
                    regionWidth = extent[0];
                    regionHeight = extent[1];
                }

                int id = regions.size();
                Region region = new Region(id, x, y, regionWidth,
                        regionHeight, doorway);
                regions.add(region);
                for (int ry = y; ry < y + regionHeight; ry++) {
                    int row = ry * width;
                    for (int rx = x; rx < x + regionWidth; rx++) {
                        cellToRegion[row + rx] = id;
                    }
                }
            }
        }

        List<Transition> transitions = detectTransitions(cellToRegion);
        return new Snapshot(width, height, revision, regions, transitions,
                cellToRegion, buildAdjacency(regions.size(), transitions),
                buildTransitionIndex(regions.size(), transitions));
    }

    /**
     * Finds the largest available rectangle with {@code (x,y)} as its lower-left
     * scan anchor. Width shrinks as rows are considered; each candidate therefore
     * has every cell free and every internal cardinal edge open.
     */
    private int[] largestRectangleAt(int x, int y, int[] assigned) {
        int width = grid.getWidth();
        int height = grid.getHeight();
        int availableWidth = availableRowWidth(x, y, width - x, assigned);
        int bestWidth = 1;
        int bestHeight = 1;
        int bestArea = 1;

        for (int rowY = y; rowY < height && availableWidth > 0; rowY++) {
            availableWidth = Math.min(availableWidth,
                    availableRowWidth(x, rowY, availableWidth, assigned));
            if (rowY > y) {
                int verticallyJoined = 0;
                while (verticallyJoined < availableWidth
                        && grid.isSharedEdgePassable(x + verticallyJoined,
                        rowY - 1, Direction.N)) {
                    verticallyJoined++;
                }
                availableWidth = verticallyJoined;
            }
            if (availableWidth == 0) break;

            int candidateHeight = rowY - y + 1;
            int candidateArea = availableWidth * candidateHeight;
            if (candidateArea > bestArea
                    || (candidateArea == bestArea
                    && (availableWidth > bestWidth
                    || (availableWidth == bestWidth
                    && candidateHeight > bestHeight)))) {
                bestArea = candidateArea;
                bestWidth = availableWidth;
                bestHeight = candidateHeight;
            }
        }
        return new int[]{bestWidth, bestHeight};
    }

    private int availableRowWidth(int startX, int y, int limit,
                                  int[] assigned) {
        int width = grid.getWidth();
        int count = 0;
        while (count < limit) {
            int x = startX + count;
            int idx = y * width + x;
            if (assigned[idx] >= 0 || !grid.isWalkable(x, y)
                    || grid.isDoorway(x, y)) {
                break;
            }
            if (count > 0
                    && !grid.isSharedEdgePassable(x - 1, y, Direction.E)) {
                break;
            }
            count++;
        }
        return count;
    }

    private List<Transition> detectTransitions(int[] cellToRegion) {
        int width = grid.getWidth();
        int height = grid.getHeight();
        List<Transition> out = new ArrayList<>();

        // Vertical boundaries: left cell -> right cell, coalesced along Y.
        for (int x = 0; x + 1 < width; x++) {
            int y = 0;
            while (y < height) {
                int left = cellToRegion[y * width + x];
                int right = cellToRegion[y * width + x + 1];
                if (!isTransition(left, right, x, y, Direction.E)) {
                    y++;
                    continue;
                }
                int startY = y++;
                while (y < height
                        && cellToRegion[y * width + x] == left
                        && cellToRegion[y * width + x + 1] == right
                        && isTransition(left, right, x, y, Direction.E)) {
                    y++;
                }
                out.add(new Transition(out.size(), left, right, x, startY,
                        Direction.E, y - startY));
            }
        }

        // Horizontal boundaries: lower cell -> upper cell, coalesced along X.
        for (int y = 0; y + 1 < height; y++) {
            int x = 0;
            while (x < width) {
                int lower = cellToRegion[y * width + x];
                int upper = cellToRegion[(y + 1) * width + x];
                if (!isTransition(lower, upper, x, y, Direction.N)) {
                    x++;
                    continue;
                }
                int startX = x++;
                while (x < width
                        && cellToRegion[y * width + x] == lower
                        && cellToRegion[(y + 1) * width + x] == upper
                        && isTransition(lower, upper, x, y, Direction.N)) {
                    x++;
                }
                out.add(new Transition(out.size(), lower, upper, startX, y,
                        Direction.N, x - startX));
            }
        }
        return out;
    }

    private boolean isTransition(int firstRegion, int secondRegion,
                                 int x, int y, Direction direction) {
        return firstRegion >= 0 && secondRegion >= 0
                && firstRegion != secondRegion
                && grid.isSharedEdgePassable(x, y, direction);
    }

    private static int[][] buildAdjacency(int regionCount,
                                          List<Transition> transitions) {
        List<List<Integer>> neighbors = new ArrayList<>(regionCount);
        for (int i = 0; i < regionCount; i++) neighbors.add(new ArrayList<>());
        for (Transition transition : transitions) {
            addUnique(neighbors.get(transition.regionA()), transition.regionB());
            addUnique(neighbors.get(transition.regionB()), transition.regionA());
        }
        int[][] result = new int[regionCount][];
        for (int i = 0; i < regionCount; i++) {
            List<Integer> row = neighbors.get(i);
            result[i] = new int[row.size()];
            for (int j = 0; j < row.size(); j++) result[i][j] = row.get(j);
        }
        return result;
    }

    private static int[][] buildTransitionIndex(int regionCount,
                                                List<Transition> transitions) {
        List<List<Integer>> byRegion = new ArrayList<>(regionCount);
        for (int i = 0; i < regionCount; i++) byRegion.add(new ArrayList<>());
        for (Transition transition : transitions) {
            byRegion.get(transition.regionA()).add(transition.id());
            byRegion.get(transition.regionB()).add(transition.id());
        }
        int[][] result = new int[regionCount][];
        for (int i = 0; i < regionCount; i++) {
            List<Integer> row = byRegion.get(i);
            result[i] = new int[row.size()];
            for (int j = 0; j < row.size(); j++) result[i][j] = row.get(j);
        }
        return result;
    }

    private static void addUnique(List<Integer> values, int value) {
        if (!values.contains(value)) values.add(value);
    }

    /** One axis-aligned rectangular navigation region. */
    public record Region(int id, int x, int y, int width, int height,
                         boolean doorway) {
        public int maxXExclusive() { return x + width; }
        public int maxYExclusive() { return y + height; }
        public int cellCount() { return width * height; }
        public boolean contains(int cellX, int cellY) {
            return cellX >= x && cellX < maxXExclusive()
                    && cellY >= y && cellY < maxYExclusive();
        }
    }

    /**
     * Maximal run of passable cell edges shared by two regions. Direction is
     * canonical: E runs along Y from the left region; N runs along X from the
     * lower region.
     */
    public record Transition(int id, int regionA, int regionB,
                             int x, int y, Direction direction, int length) {
        public Transition {
            if (direction != Direction.E && direction != Direction.N) {
                throw new IllegalArgumentException(
                        "mesh transition direction must be E or N");
            }
            if (length <= 0) {
                throw new IllegalArgumentException(
                        "mesh transition length must be positive");
            }
        }
    }

    /** Immutable, revisioned result of one complete mesh build. */
    public static final class Snapshot {
        private final int width;
        private final int height;
        private final long revision;
        private final List<Region> regions;
        private final List<Transition> transitions;
        private final int[] cellToRegion;
        private final int[][] adjacency;
        private final int[][] transitionIdsByRegion;

        private Snapshot(int width, int height, long revision,
                         List<Region> regions,
                         List<Transition> transitions,
                         int[] cellToRegion, int[][] adjacency,
                         int[][] transitionIdsByRegion) {
            this.width = width;
            this.height = height;
            this.revision = revision;
            this.regions = List.copyOf(regions);
            this.transitions = List.copyOf(transitions);
            this.cellToRegion = cellToRegion;
            this.adjacency = adjacency;
            this.transitionIdsByRegion = transitionIdsByRegion;
        }

        private static Snapshot empty(int width, int height) {
            int[] cellToRegion = new int[width * height];
            Arrays.fill(cellToRegion, -1);
            return new Snapshot(width, height, 0L, Collections.emptyList(),
                    Collections.emptyList(), cellToRegion, new int[0][],
                    new int[0][]);
        }

        public long revision() { return revision; }
        public List<Region> regions() { return regions; }
        public List<Transition> transitions() { return transitions; }

        public int regionIdAt(int x, int y) {
            if (x < 0 || x >= width || y < 0 || y >= height) return -1;
            return cellToRegion[y * width + x];
        }

        public Region regionAt(int x, int y) {
            int id = regionIdAt(x, y);
            return id < 0 ? null : regions.get(id);
        }

        /** Defensive copy of the neighboring region ids. */
        public int[] adjacentRegionIds(int regionId) {
            if (regionId < 0 || regionId >= adjacency.length) return new int[0];
            return Arrays.copyOf(adjacency[regionId], adjacency[regionId].length);
        }

        /** Number of boundary transitions incident to one region. */
        public int transitionCount(int regionId) {
            if (regionId < 0 || regionId >= transitionIdsByRegion.length) {
                return 0;
            }
            return transitionIdsByRegion[regionId].length;
        }

        /**
         * Zero-allocation transition lookup for hierarchical routing. The
         * immutable snapshot retains ownership of its index arrays.
         */
        public int transitionIdAt(int regionId, int offset) {
            if (regionId < 0 || regionId >= transitionIdsByRegion.length) {
                return -1;
            }
            int[] ids = transitionIdsByRegion[regionId];
            return offset < 0 || offset >= ids.length ? -1 : ids[offset];
        }

        public boolean areConnected(int startRegion, int goalRegion) {
            if (startRegion < 0 || goalRegion < 0
                    || startRegion >= regions.size()
                    || goalRegion >= regions.size()) {
                return false;
            }
            if (startRegion == goalRegion) return true;
            boolean[] visited = new boolean[regions.size()];
            int[] queue = new int[regions.size()];
            int head = 0;
            int tail = 0;
            visited[startRegion] = true;
            queue[tail++] = startRegion;
            while (head < tail) {
                int current = queue[head++];
                for (int neighbor : adjacency[current]) {
                    if (visited[neighbor]) continue;
                    if (neighbor == goalRegion) return true;
                    visited[neighbor] = true;
                    queue[tail++] = neighbor;
                }
            }
            return false;
        }
    }
}
