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
 *
 * <p><b>Covered in tiles, so a breach re-covers its own tile and not the
 * map.</b> A region never crosses a {@link #TILE}-square tile boundary, which
 * makes each tile's cover a function of that tile's cells and edges alone, and
 * the transitions along a seam between two tiles a function of those two
 * tiles. A rebuild therefore asks the grid's change log which cells moved
 * since the last one, re-covers the tiles holding them, re-derives the seams
 * touching those tiles, and assembles the snapshot from the retained covers of
 * every other tile. The cost is the region count (fresh ids and adjacency are
 * assembled every time) rather than the cell count, and on a 560x336 map a
 * wall breach re-covers one tile of 198. A rebuild that finds the log no longer
 * reaches back to its last catch-up re-covers every tile, which is the same
 * answer the whole-map scan used to give. The cover is a little finer than an
 * unconstrained greedy one — a corridor forty cells long is two regions rather
 * than one — and the hierarchical search absorbs that: it accepts a corridor
 * only within a bounded stretch of the grid lower bound and falls back to the
 * grid otherwise, so a finer cover cannot make a path wrong.
 */
public final class GreedyNavigationMesh {

    /**
     * Tile edge in cells. The same size the vehicle clearance labels use, for
     * the same reason: 198 tiles on the 560x336 map is a re-cover of about half
     * a percent of it per breach, while a tile still holds most rooms whole.
     */
    static final int TILE = 32;
    private static final int TILE_SHIFT = 5;
    private static final int NO_REGION = -1;
    private static final long UNBUILT = Long.MIN_VALUE;

    private final NavigationGrid grid;
    private final int width;
    private final int height;
    private final int tilesX;
    private final int tilesY;
    /** Each tile's retained cover; rebuilt only when the tile holds a changed cell. */
    private final TileCover[] covers;
    /** Transitions across the seam to the tile one to the east; rebuilt when either side is. */
    private final LocalTransition[][] seamsEast;
    /** Transitions across the seam to the tile one to the north; rebuilt when either side is. */
    private final LocalTransition[][] seamsNorth;
    /** Per cell, the region's index within its tile's cover, or {@link #NO_REGION}. */
    private final int[] cellToLocal;
    /** {@link NavigationGrid#changeCount()} the covers have been replayed up to. */
    private long caughtUp = UNBUILT;
    private volatile Snapshot snapshot;
    private int lastTilesCovered;
    private int lastSeamsDerived;

    public GreedyNavigationMesh(NavigationGrid grid) {
        this.grid = grid;
        this.width = grid.getWidth();
        this.height = grid.getHeight();
        this.tilesX = (width + TILE - 1) >> TILE_SHIFT;
        this.tilesY = (height + TILE - 1) >> TILE_SHIFT;
        int tiles = tilesX * tilesY;
        this.covers = new TileCover[tiles];
        this.seamsEast = new LocalTransition[tiles][];
        this.seamsNorth = new LocalTransition[tiles][];
        this.cellToLocal = new int[width * height];
        Arrays.fill(cellToLocal, NO_REGION);
        this.snapshot = Snapshot.empty(width, height);
        rebuild();
    }

    /**
     * Rebuilds and atomically publishes the mesh derived from the current grid,
     * re-covering only the tiles the grid's change log says moved since the
     * last rebuild.
     */
    public void rebuild() {
        int tiles = tilesX * tilesY;
        boolean[] dirty = new boolean[tiles];
        long changeCount = grid.changeCount();
        if (caughtUp == UNBUILT || !grid.hasCaughtUpFrom(caughtUp)) {
            Arrays.fill(dirty, true);
        } else {
            for (long seq = caughtUp; seq < changeCount; seq++) {
                int idx = grid.changedCellAt(seq);
                dirty[tileOf(idx % width, idx / width)] = true;
            }
        }
        int covered = 0;
        for (int tile = 0; tile < tiles; tile++) {
            if (!dirty[tile]) continue;
            covers[tile] = coverTile(tile);
            covered++;
        }
        int seams = 0;
        for (int tile = 0; tile < tiles; tile++) {
            int tx = tile % tilesX;
            int ty = tile / tilesX;
            if (tx + 1 < tilesX && (dirty[tile] || dirty[tile + 1])) {
                seamsEast[tile] = seamEast(tile);
                seams++;
            }
            if (ty + 1 < tilesY && (dirty[tile] || dirty[tile + tilesX])) {
                seamsNorth[tile] = seamNorth(tile);
                seams++;
            }
        }
        lastTilesCovered = covered;
        lastSeamsDerived = seams;
        caughtUp = changeCount;
        Snapshot previous = snapshot;
        snapshot = assemble(previous.revision() + 1L);
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

    /** Tiles the map is covered in. Evidence, not behavior. */
    public int tileCount() { return tilesX * tilesY; }

    /** Tiles the last {@link #rebuild()} re-covered. Evidence, not behavior. */
    public int lastTilesCovered() { return lastTilesCovered; }

    /** Seams the last {@link #rebuild()} re-derived. Evidence, not behavior. */
    public int lastSeamsDerived() { return lastSeamsDerived; }

    private int tileOf(int x, int y) {
        return (y >> TILE_SHIFT) * tilesX + (x >> TILE_SHIFT);
    }

    /**
     * Covers one tile with greedy rectangles confined to it, writing each
     * cell's local region index, and derives the transitions between regions
     * of this tile.
     */
    private TileCover coverTile(int tile) {
        int x0 = (tile % tilesX) << TILE_SHIFT;
        int y0 = (tile / tilesX) << TILE_SHIFT;
        int x1 = Math.min(width, x0 + TILE);
        int y1 = Math.min(height, y0 + TILE);
        for (int y = y0; y < y1; y++) {
            int row = y * width;
            for (int x = x0; x < x1; x++) cellToLocal[row + x] = NO_REGION;
        }
        List<LocalRegion> regions = new ArrayList<>();
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int idx = y * width + x;
                if (cellToLocal[idx] >= 0 || !grid.isWalkable(x, y)) continue;
                int regionWidth = 1;
                int regionHeight = 1;
                boolean doorway = grid.isDoorway(x, y);
                if (!doorway) {
                    int[] extent = largestRectangleAt(x, y, x1, y1);
                    regionWidth = extent[0];
                    regionHeight = extent[1];
                }
                int local = regions.size();
                regions.add(new LocalRegion(x, y, regionWidth, regionHeight, doorway));
                for (int ry = y; ry < y + regionHeight; ry++) {
                    int row = ry * width;
                    for (int rx = x; rx < x + regionWidth; rx++) {
                        cellToLocal[row + rx] = local;
                    }
                }
            }
        }
        List<LocalTransition> transitions = new ArrayList<>();
        // Vertical boundaries inside the tile: left cell -> right cell, coalesced along Y.
        for (int x = x0; x + 1 < x1; x++) {
            collectEastTransitions(tile, tile, x, y0, y1, transitions);
        }
        // Horizontal boundaries inside the tile: lower cell -> upper cell, coalesced along X.
        for (int y = y0; y + 1 < y1; y++) {
            collectNorthTransitions(tile, tile, y, x0, x1, transitions);
        }
        return new TileCover(regions.toArray(new LocalRegion[0]),
                transitions.toArray(new LocalTransition[0]));
    }

    private LocalTransition[] seamEast(int tile) {
        int x = ((tile % tilesX) << TILE_SHIFT) + TILE - 1;
        int y0 = (tile / tilesX) << TILE_SHIFT;
        int y1 = Math.min(height, y0 + TILE);
        List<LocalTransition> out = new ArrayList<>();
        collectEastTransitions(tile, tile + 1, x, y0, y1, out);
        return out.toArray(new LocalTransition[0]);
    }

    private LocalTransition[] seamNorth(int tile) {
        int y = ((tile / tilesX) << TILE_SHIFT) + TILE - 1;
        int x0 = (tile % tilesX) << TILE_SHIFT;
        int x1 = Math.min(width, x0 + TILE);
        List<LocalTransition> out = new ArrayList<>();
        collectNorthTransitions(tile, tile + tilesX, y, x0, x1, out);
        return out.toArray(new LocalTransition[0]);
    }

    /** Runs of passable edges between column {@code x} (in {@code tileA}) and {@code x + 1} (in {@code tileB}) over rows {@code [y0, y1)}. */
    private void collectEastTransitions(int tileA, int tileB, int x, int y0, int y1,
                                        List<LocalTransition> out) {
        int y = y0;
        while (y < y1) {
            int left = cellToLocal[y * width + x];
            int right = cellToLocal[y * width + x + 1];
            if (!isTransition(tileA, left, tileB, right, x, y, Direction.E)) {
                y++;
                continue;
            }
            int startY = y++;
            while (y < y1
                    && cellToLocal[y * width + x] == left
                    && cellToLocal[y * width + x + 1] == right
                    && isTransition(tileA, left, tileB, right, x, y, Direction.E)) {
                y++;
            }
            out.add(new LocalTransition(tileA, left, tileB, right, x, startY,
                    Direction.E, y - startY));
        }
    }

    /** Runs of passable edges between row {@code y} (in {@code tileA}) and {@code y + 1} (in {@code tileB}) over columns {@code [x0, x1)}. */
    private void collectNorthTransitions(int tileA, int tileB, int y, int x0, int x1,
                                         List<LocalTransition> out) {
        int x = x0;
        while (x < x1) {
            int lower = cellToLocal[y * width + x];
            int upper = cellToLocal[(y + 1) * width + x];
            if (!isTransition(tileA, lower, tileB, upper, x, y, Direction.N)) {
                x++;
                continue;
            }
            int startX = x++;
            while (x < x1
                    && cellToLocal[y * width + x] == lower
                    && cellToLocal[(y + 1) * width + x] == upper
                    && isTransition(tileA, lower, tileB, upper, x, y, Direction.N)) {
                x++;
            }
            out.add(new LocalTransition(tileA, lower, tileB, upper, startX, y,
                    Direction.N, x - startX));
        }
    }

    private boolean isTransition(int tileA, int localA, int tileB, int localB,
                                 int x, int y, Direction direction) {
        return localA >= 0 && localB >= 0
                && (tileA != tileB || localA != localB)
                && grid.isSharedEdgePassable(x, y, direction);
    }

    /**
     * Finds the largest available rectangle with {@code (x,y)} as its lower-left
     * scan anchor, confined to the tile ending at {@code (limitX, limitY)}
     * exclusive. Width shrinks as rows are considered; each candidate therefore
     * has every cell free and every internal cardinal edge open.
     */
    private int[] largestRectangleAt(int x, int y, int limitX, int limitY) {
        int availableWidth = availableRowWidth(x, y, limitX - x);
        int bestWidth = 1;
        int bestHeight = 1;
        int bestArea = 1;

        for (int rowY = y; rowY < limitY && availableWidth > 0; rowY++) {
            availableWidth = Math.min(availableWidth,
                    availableRowWidth(x, rowY, availableWidth));
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

    private int availableRowWidth(int startX, int y, int limit) {
        int count = 0;
        while (count < limit) {
            int x = startX + count;
            int idx = y * width + x;
            if (cellToLocal[idx] >= 0 || !grid.isWalkable(x, y)
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

    /**
     * Assembles one immutable snapshot from the retained covers: dense region
     * ids tile by tile, every transition mapped onto them, and the adjacency
     * and transition indexes the hierarchical search reads.
     */
    private Snapshot assemble(long revision) {
        int tiles = tilesX * tilesY;
        int[] tileBase = new int[tiles + 1];
        for (int tile = 0; tile < tiles; tile++) {
            tileBase[tile + 1] = tileBase[tile] + covers[tile].regions.length;
        }
        List<Region> regions = new ArrayList<>(tileBase[tiles]);
        for (int tile = 0; tile < tiles; tile++) {
            for (LocalRegion local : covers[tile].regions) {
                regions.add(new Region(regions.size(), local.x, local.y,
                        local.width, local.height, local.doorway));
            }
        }
        List<Transition> transitions = new ArrayList<>();
        for (int tile = 0; tile < tiles; tile++) {
            appendTransitions(covers[tile].transitions, tileBase, transitions);
        }
        for (int tile = 0; tile < tiles; tile++) {
            appendTransitions(seamsEast[tile], tileBase, transitions);
            appendTransitions(seamsNorth[tile], tileBase, transitions);
        }
        return new Snapshot(width, height, revision, tilesX, tileBase,
                regions, transitions, cellToLocal.clone(),
                buildAdjacency(regions.size(), transitions),
                buildTransitionIndex(regions.size(), transitions));
    }

    private static void appendTransitions(LocalTransition[] locals, int[] tileBase,
                                          List<Transition> out) {
        if (locals == null) return;
        for (LocalTransition local : locals) {
            out.add(new Transition(out.size(),
                    tileBase[local.tileA] + local.localA,
                    tileBase[local.tileB] + local.localB,
                    local.x, local.y, local.direction, local.length));
        }
    }

    /**
     * Neighbouring region ids per region, each listed once. Counted into flat
     * arrays rather than boxed lists: this runs on every rebuild, and on a
     * city-sized map the boxing was most of the assembly.
     */
    private static int[][] buildAdjacency(int regionCount,
                                          List<Transition> transitions) {
        int[] capacity = new int[regionCount];
        for (Transition transition : transitions) {
            capacity[transition.regionA()]++;
            capacity[transition.regionB()]++;
        }
        int[][] result = new int[regionCount][];
        int[] used = new int[regionCount];
        for (int i = 0; i < regionCount; i++) result[i] = new int[capacity[i]];
        for (Transition transition : transitions) {
            addUnique(result, used, transition.regionA(), transition.regionB());
            addUnique(result, used, transition.regionB(), transition.regionA());
        }
        for (int i = 0; i < regionCount; i++) {
            if (used[i] != result[i].length) {
                result[i] = Arrays.copyOf(result[i], used[i]);
            }
        }
        return result;
    }

    private static int[][] buildTransitionIndex(int regionCount,
                                                List<Transition> transitions) {
        int[] capacity = new int[regionCount];
        for (Transition transition : transitions) {
            capacity[transition.regionA()]++;
            capacity[transition.regionB()]++;
        }
        int[][] result = new int[regionCount][];
        int[] used = new int[regionCount];
        for (int i = 0; i < regionCount; i++) result[i] = new int[capacity[i]];
        for (Transition transition : transitions) {
            result[transition.regionA()][used[transition.regionA()]++] = transition.id();
            result[transition.regionB()][used[transition.regionB()]++] = transition.id();
        }
        return result;
    }

    private static void addUnique(int[][] rows, int[] used, int region, int value) {
        int[] row = rows[region];
        int count = used[region];
        for (int i = 0; i < count; i++) {
            if (row[i] == value) return;
        }
        row[count] = value;
        used[region] = count + 1;
    }

    /** A region as its tile retains it: absolute cells, no global id yet. */
    private record LocalRegion(int x, int y, int width, int height, boolean doorway) { }

    /** A transition as a tile or seam retains it, naming its regions by tile and local index. */
    private record LocalTransition(int tileA, int localA, int tileB, int localB,
                                   int x, int y, Direction direction, int length) { }

    /** One tile's retained cover and the transitions between its own regions. */
    private record TileCover(LocalRegion[] regions, LocalTransition[] transitions) { }

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
        private final int tilesX;
        private final int[] tileBase;
        private final List<Region> regions;
        private final List<Transition> transitions;
        private final int[] cellToLocal;
        private final int[][] adjacency;
        private final int[][] transitionIdsByRegion;

        private Snapshot(int width, int height, long revision,
                         int tilesX, int[] tileBase,
                         List<Region> regions,
                         List<Transition> transitions,
                         int[] cellToLocal, int[][] adjacency,
                         int[][] transitionIdsByRegion) {
            this.width = width;
            this.height = height;
            this.revision = revision;
            this.tilesX = tilesX;
            this.tileBase = tileBase;
            this.regions = List.copyOf(regions);
            this.transitions = List.copyOf(transitions);
            this.cellToLocal = cellToLocal;
            this.adjacency = adjacency;
            this.transitionIdsByRegion = transitionIdsByRegion;
        }

        private static Snapshot empty(int width, int height) {
            int tilesX = (width + TILE - 1) >> TILE_SHIFT;
            int tilesY = (height + TILE - 1) >> TILE_SHIFT;
            int[] cellToLocal = new int[width * height];
            Arrays.fill(cellToLocal, NO_REGION);
            return new Snapshot(width, height, 0L, tilesX,
                    new int[tilesX * tilesY + 1], Collections.emptyList(),
                    Collections.emptyList(), cellToLocal, new int[0][],
                    new int[0][]);
        }

        public long revision() { return revision; }
        public List<Region> regions() { return regions; }
        public List<Transition> transitions() { return transitions; }

        public int regionIdAt(int x, int y) {
            if (x < 0 || x >= width || y < 0 || y >= height) return -1;
            int local = cellToLocal[y * width + x];
            if (local < 0) return -1;
            return tileBase[(y >> TILE_SHIFT) * tilesX + (x >> TILE_SHIFT)] + local;
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
