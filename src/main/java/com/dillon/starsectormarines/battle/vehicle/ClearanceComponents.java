package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Arrays;

/**
 * The connected components of a {@link VehicleClearance} mask, labelled once so
 * that "can this chassis get from here to there at all" is an array compare
 * rather than a search.
 *
 * <p>It exists because the convoy route proof was asking that question with the
 * router. {@code ConvoyMeans} enumerates perimeter entries against interior
 * junctions, and its reachability filter was the <em>road graph</em>, which
 * knows nothing about whether a full vehicle body fits anywhere along the way.
 * A destination that is graph-reachable and mask-unreachable makes the
 * cost-field A* flood the whole reachable component of the grid before
 * returning no route — about five milliseconds on a 560x336 map — and the
 * enumeration then does it again for the next candidate. One labelling pass
 * answers every one of those candidates.
 *
 * <p><b>What it is worth, honestly.</b> On the traced Conquest dispatch that
 * prompted it, this filter skipped nothing: every ranked junction there was
 * genuinely reachable for the chassis, and the 745 searches that stalled the
 * game thread went on kinematic retries rather than on unreachable ground —
 * which is what {@code RouteProofJob.SEARCH_BUDGET} bounds. The case this
 * covers is the other one in the same log, where dispatch after dispatch
 * reported no complete route at the same price. A route proof that pays a
 * full-grid flood to learn something a labelling pass already knows is the
 * fault; that it is not the whole of the bill on one fixture does not make it
 * a fair charge.
 *
 * <p><b>The step rule is the pathfinder's own.</b> A component here must mean
 * exactly what the router means by reachable, so the labelling expands through
 * {@link NavigationGrid#canTraverseCellStep(int, int, Direction)} — the same
 * walkability, reciprocal-edge and diagonal-corner contract
 * {@code GridPathfinder.canStep} applies — gated additionally on the clearance
 * mask, which is what {@code findPath}'s {@code passable} argument does. It
 * honours {@link GridPathfinder#USE_CARDINAL_NAVIGATION} for the same reason:
 * a diagonal the router will not take must not join two components here.
 *
 * <p><b>Labelled in tiles, so a breach relabels its own neighbourhood and not
 * the map.</b> Connectivity is the one derivation of the grid a local change
 * can alter globally — a wreck can cut a component in two and a breach can
 * join two into one — so a local patch of the labels is not enough on its own.
 * The standard answer is two levels: each {@link #TILE}-square tile is
 * labelled on its own, ignoring everything outside it, and the components of
 * the whole map are the union of those local labels across every adjacency
 * that crosses a tile edge. A catch-up then relabels only the tiles that hold
 * a cell whose mask or edges moved and re-runs the union — whose cost is the
 * length of the tile seams, not the area of the map. On the 560x336 Conquest
 * map that is 198 tiles and a few thousand seam adjacencies against 188,160
 * cells. {@link #floodOf} is the single whole-map flood this replaces, kept for
 * the tests as the oracle the tiled answer must agree with.
 *
 * <p>Pure: {@code (grid, mask) -> labels}, and a catch-up is
 * {@code (labels, grid, mask, changes) -> labels} on a copy — the instance a
 * caller already holds is never rewritten under it, for the same reason
 * {@link VehicleClearance#copyOf} exists. A vehicle-scoped derived view of an
 * already-derived mask, off every hot path.
 */
public final class ClearanceComponents {

    /** Label of a cell no vehicle of this clearance can stand on, and of any out-of-bounds cell. */
    public static final int NONE = -1;

    /**
     * Tile edge in cells. Thirty-two puts a 560x336 map at 198 tiles, so a
     * breach relabels about a two-hundredth of it, while the seam the union
     * walks stays a few thousand adjacencies; a smaller tile would shrink the
     * relabel further but lengthen the seam, which is the part every catch-up
     * pays whether or not anything nearby changed.
     */
    static final int TILE = 32;
    private static final int TILE_SHIFT = 5;
    /** A tile in which every cell is its own component still fits: one node per cell. */
    private static final int NODES_PER_TILE = TILE * TILE;
    private static final int NODE_SHIFT = 10;
    private static final int NODE_MASK = NODES_PER_TILE - 1;

    private final int width;
    private final int height;
    private final int tilesX;
    private final int tilesY;
    /**
     * Per cell, the local node {@code (tile << NODE_SHIFT) | local} it belongs
     * to, or {@link #NONE} off the mask. Stable across other tiles' relabels,
     * which is what lets a catch-up touch one tile and leave the rest.
     */
    private final int[] node;
    /** Local components in each tile; the tile's nodes are {@code [0, localCount)}. */
    private final int[] localCount;
    /** Prefix sum of {@link #localCount}: where each tile's nodes start in {@link #label}. */
    private final int[] denseBase;
    /** Per dense node, the global component label. */
    private final int[] label;
    private final int componentCount;
    private final int boundaryAdjacencies;
    private final int tilesRelabelled;

    private ClearanceComponents(int width, int height, int[] node, int[] localCount,
                                int[] denseBase, int[] label, int componentCount,
                                int boundaryAdjacencies, int tilesRelabelled) {
        this.width = width;
        this.height = height;
        this.tilesX = (width + TILE - 1) >> TILE_SHIFT;
        this.tilesY = (height + TILE - 1) >> TILE_SHIFT;
        this.node = node;
        this.localCount = localCount;
        this.denseBase = denseBase;
        this.label = label;
        this.componentCount = componentCount;
        this.boundaryAdjacencies = boundaryAdjacencies;
        this.tilesRelabelled = tilesRelabelled;
    }

    /**
     * Labels every passable cell of {@code clearance} with the index of the
     * component it belongs to: every tile labelled locally, then the seams
     * united. Cells outside the mask take {@link #NONE}.
     */
    public static ClearanceComponents of(NavigationGrid grid, VehicleClearance clearance) {
        int w = clearance.getWidth();
        int h = clearance.getHeight();
        int tilesX = (w + TILE - 1) >> TILE_SHIFT;
        int tilesY = (h + TILE - 1) >> TILE_SHIFT;
        int tiles = tilesX * tilesY;
        int[] node = new int[w * h];
        Arrays.fill(node, NONE);
        int[] localCount = new int[tiles];
        Workspace ws = WORKSPACE.get();
        for (int tile = 0; tile < tiles; tile++) {
            labelTile(grid, clearance, node, localCount, tile, tilesX, ws);
        }
        return unite(grid, clearance, node, localCount, tiles);
    }

    /**
     * These labels brought up to date with the grid's changes in
     * {@code [fromSequence, toSequence)} of {@link NavigationGrid#changedCellAt},
     * on a copy. A change at a cell can flip the mask anywhere within
     * {@code radiusCells} of it (see {@link VehicleClearance#refreshAround}) and
     * the step rule at the cell itself, so every tile holding one of those
     * cells is relabelled; every other tile keeps its local labels, and the
     * seam union is re-run in full because that is where a split or a join
     * between tiles shows up. The caller has already replayed the same range
     * into {@code clearance}, and has already checked
     * {@link NavigationGrid#hasCaughtUpFrom} — a range the log no longer holds
     * is a whole rebuild, not a catch-up.
     */
    public ClearanceComponents catchUp(NavigationGrid grid, VehicleClearance clearance,
                                       long fromSequence, long toSequence, int radiusCells) {
        int tiles = tilesX * tilesY;
        boolean[] dirty = new boolean[tiles];
        int r = Math.max(0, radiusCells);
        for (long seq = fromSequence; seq < toSequence; seq++) {
            int idx = grid.changedCellAt(seq);
            int cx = idx % width;
            int cy = idx / width;
            int tx0 = Math.max(0, cx - r) >> TILE_SHIFT;
            int tx1 = Math.min(width - 1, cx + r) >> TILE_SHIFT;
            int ty0 = Math.max(0, cy - r) >> TILE_SHIFT;
            int ty1 = Math.min(height - 1, cy + r) >> TILE_SHIFT;
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int tx = tx0; tx <= tx1; tx++) dirty[ty * tilesX + tx] = true;
            }
        }
        int[] nextNode = node.clone();
        int[] nextLocalCount = localCount.clone();
        Workspace ws = WORKSPACE.get();
        int relabelled = 0;
        for (int tile = 0; tile < tiles; tile++) {
            if (!dirty[tile]) continue;
            labelTile(grid, clearance, nextNode, nextLocalCount, tile, tilesX, ws);
            relabelled++;
        }
        ClearanceComponents next = unite(grid, clearance, nextNode, nextLocalCount, tiles);
        return new ClearanceComponents(next.width, next.height, next.node, next.localCount,
                next.denseBase, next.label, next.componentCount,
                next.boundaryAdjacencies, relabelled);
    }

    /**
     * Labels one tile's passable cells locally, ignoring everything outside
     * it. Breadth-first from each unlabelled cell, expanding through the same
     * directed step rule as {@link #floodOf} so that within a tile the two
     * agree exactly.
     */
    private static void labelTile(NavigationGrid grid, VehicleClearance clearance,
                                  int[] node, int[] localCount, int tile, int tilesX,
                                  Workspace ws) {
        int w = clearance.getWidth();
        int h = clearance.getHeight();
        int x0 = (tile % tilesX) << TILE_SHIFT;
        int y0 = (tile / tilesX) << TILE_SHIFT;
        int x1 = Math.min(w, x0 + TILE);
        int y1 = Math.min(h, y0 + TILE);
        for (int y = y0; y < y1; y++) {
            int row = y * w;
            for (int x = x0; x < x1; x++) node[row + x] = NONE;
        }
        int directionCount = GridPathfinder.USE_CARDINAL_NAVIGATION ? 4 : 8;
        int tileBase = tile << NODE_SHIFT;
        int local = 0;
        int[] queue = ws.queue;
        for (int sy = y0; sy < y1; sy++) {
            for (int sx = x0; sx < x1; sx++) {
                int seed = sy * w + sx;
                if (node[seed] != NONE || !clearance.isPassableAt(seed)) continue;
                int id = tileBase | local++;
                node[seed] = id;
                int head = 0;
                int tail = 0;
                queue[tail++] = seed;
                while (head < tail) {
                    int current = queue[head++];
                    int cx = current % w;
                    int cy = current / w;
                    for (int d = 0; d < directionCount; d++) {
                        Direction direction = Direction.ALL[d];
                        int nx = cx + direction.dx;
                        int ny = cy + direction.dy;
                        if (nx < x0 || nx >= x1 || ny < y0 || ny >= y1) continue;
                        int neighbour = ny * w + nx;
                        if (node[neighbour] != NONE) continue;
                        if (!clearance.isPassableAt(neighbour)) continue;
                        if (!grid.canTraverseCellStep(cx, cy, direction)) continue;
                        node[neighbour] = id;
                        queue[tail++] = neighbour;
                    }
                }
            }
        }
        localCount[tile] = local;
    }

    /**
     * Unites local labels across every adjacency that crosses a tile seam and
     * assigns dense global labels. Walks only seam cells — the outermost ring
     * of every tile — and, for each, only the neighbours in a different tile,
     * uniting the pair when the router could step it in either direction.
     */
    private static ClearanceComponents unite(NavigationGrid grid, VehicleClearance clearance,
                                             int[] node, int[] localCount, int tiles) {
        int w = clearance.getWidth();
        int h = clearance.getHeight();
        int tilesX = (w + TILE - 1) >> TILE_SHIFT;
        int[] denseBase = new int[tiles + 1];
        for (int tile = 0; tile < tiles; tile++) {
            denseBase[tile + 1] = denseBase[tile] + localCount[tile];
        }
        int nodes = denseBase[tiles];
        int[] parent = new int[nodes];
        for (int i = 0; i < nodes; i++) parent[i] = i;

        int directionCount = GridPathfinder.USE_CARDINAL_NAVIGATION ? 4 : 8;
        int adjacencies = 0;
        for (int y = 0; y < h; y++) {
            int ty = y >> TILE_SHIFT;
            boolean seamRow = (y & (TILE - 1)) == 0 || (y & (TILE - 1)) == TILE - 1 || y == h - 1;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                boolean seamCol = (x & (TILE - 1)) == 0 || (x & (TILE - 1)) == TILE - 1 || x == w - 1;
                if (!seamRow && !seamCol) continue;
                int idx = row + x;
                int a = node[idx];
                if (a == NONE) continue;
                int tile = ty * tilesX + (x >> TILE_SHIFT);
                for (int d = 0; d < directionCount; d++) {
                    Direction direction = Direction.ALL[d];
                    int nx = x + direction.dx;
                    int ny = y + direction.dy;
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int otherTile = (ny >> TILE_SHIFT) * tilesX + (nx >> TILE_SHIFT);
                    // Each seam pair is visited from both of its cells; take it once.
                    if (otherTile <= tile) continue;
                    int b = node[ny * w + nx];
                    if (b == NONE) continue;
                    if (!grid.canTraverseCellStep(x, y, direction)
                            && !grid.canTraverseCellStep(nx, ny, direction.opposite())) {
                        continue;
                    }
                    adjacencies++;
                    union(parent, dense(denseBase, a), dense(denseBase, b));
                }
            }
        }

        int[] label = new int[nodes];
        Arrays.fill(label, NONE);
        int next = 0;
        for (int i = 0; i < nodes; i++) {
            int root = find(parent, i);
            if (label[root] == NONE) label[root] = next++;
            label[i] = label[root];
        }
        return new ClearanceComponents(w, h, node, localCount, denseBase, label,
                next, adjacencies, tiles);
    }

    private static int dense(int[] denseBase, int nodeId) {
        return denseBase[nodeId >> NODE_SHIFT] + (nodeId & NODE_MASK);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra == rb) return;
        if (ra < rb) parent[rb] = ra; else parent[ra] = rb;
    }

    /**
     * The whole-map flood these tiles replaced: one breadth-first sweep of the
     * grid, labelling every component in seed order. Kept as the oracle the
     * tiled labelling is tested against, since the two must partition the mask
     * identically; nothing in the battle calls it.
     */
    static int[] floodOf(NavigationGrid grid, VehicleClearance clearance) {
        int w = clearance.getWidth();
        int h = clearance.getHeight();
        int[] labels = new int[w * h];
        Arrays.fill(labels, NONE);
        int[] queue = new int[w * h];
        int directionCount = GridPathfinder.USE_CARDINAL_NAVIGATION ? 4 : 8;
        int nextLabel = 0;
        for (int seed = 0; seed < labels.length; seed++) {
            if (labels[seed] != NONE || !clearance.isPassableAt(seed)) continue;
            int label = nextLabel++;
            labels[seed] = label;
            int head = 0;
            int tail = 0;
            queue[tail++] = seed;
            while (head < tail) {
                int current = queue[head++];
                int cx = current % w;
                int cy = current / w;
                for (int d = 0; d < directionCount; d++) {
                    Direction direction = Direction.ALL[d];
                    int nx = cx + direction.dx;
                    int ny = cy + direction.dy;
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int neighbour = ny * w + nx;
                    if (labels[neighbour] != NONE) continue;
                    if (!clearance.isPassableAt(neighbour)) continue;
                    if (!grid.canTraverseCellStep(cx, cy, direction)) continue;
                    labels[neighbour] = label;
                    queue[tail++] = neighbour;
                }
            }
        }
        return labels;
    }

    /** Component index at {@code (x, y)}, or {@link #NONE} off the mask or off the grid. */
    public int labelAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return NONE;
        int n = node[y * width + x];
        if (n == NONE) return NONE;
        return label[dense(denseBase, n)];
    }

    /**
     * True when both cells are on the mask and in the same component — i.e. the
     * router would find <em>some</em> path between them, whatever it costs.
     * False for either cell off the mask, which is the case a route proof must
     * not pay a search to discover.
     */
    public boolean connected(int fromX, int fromY, int toX, int toY) {
        int from = labelAt(fromX, fromY);
        return from != NONE && from == labelAt(toX, toY);
    }

    /** How many distinct components the mask has. Zero when nothing on the map fits the chassis. */
    public int componentCount() { return componentCount; }

    /** Tiles the map is labelled in. Evidence, not behavior. */
    public int tileCount() { return tilesX * tilesY; }

    /** Tiles this instance's build relabelled: all of them for {@link #of}, the dirty ones for a catch-up. Evidence, not behavior. */
    public int tilesRelabelled() { return tilesRelabelled; }

    /** Seam adjacencies the last union walked — the cost every catch-up pays regardless of what changed. Evidence, not behavior. */
    public int boundaryAdjacencies() { return boundaryAdjacencies; }

    /** Scratch for one tile's flood: bounded by the tile, so it never grows with the map. */
    private static final class Workspace {
        final int[] queue = new int[NODES_PER_TILE];
    }

    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);
}
