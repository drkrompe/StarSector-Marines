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
    /** Starting HP for every wall cell. Sized so a typical strafe shot chips, a missile breaches in one or two hits. Tune alongside damage values. */
    private static final int WALL_HP_DEFAULT  = 100;
    /** Building footprints with both dimensions ≥ this are carved hollow (walkable interior + one doorway). Smaller fall back to solid. */
    private static final int HOLLOW_MIN_SIZE  = 4;

    /** Probability a hollow building gets at least one prop placed. */
    private static final float DOODAD_PER_BUILDING_CHANCE = 0.8f;
    /** Cap on doodads per building — small interiors look junked with too many. */
    private static final int DOODAD_MAX_PER_BUILDING = 3;
    /** Probability each interior cell (after the first) gets a prop, until the cap is hit. */
    private static final float DOODAD_EXTRA_CELL_CHANCE = 0.4f;

    public static final class Result {
        public final NavigationGrid grid;
        public final int marineSpawnX;
        public final int marineSpawnY;
        public final int defenderSpawnX;
        public final int defenderSpawnY;
        public final List<PointOfInterest> pointsOfInterest;
        public final List<Doodad> doodads;

        public Result(NavigationGrid grid,
                      int marineSpawnX, int marineSpawnY,
                      int defenderSpawnX, int defenderSpawnY,
                      List<PointOfInterest> pointsOfInterest,
                      List<Doodad> doodads) {
            this.grid = grid;
            this.marineSpawnX = marineSpawnX;
            this.marineSpawnY = marineSpawnY;
            this.defenderSpawnX = defenderSpawnX;
            this.defenderSpawnY = defenderSpawnY;
            this.pointsOfInterest = pointsOfInterest;
            this.doodads = doodads;
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

        List<PointOfInterest> pois = new ArrayList<>();
        for (int[] row : blockRowsY) {
            for (int[] col : blockColsX) {
                PointOfInterest poi = placeBuilding(grid, col[0], row[0], col[1], row[1], rng);
                if (poi != null) pois.add(poi);
            }
        }

        seedWallHp(grid);
        bakeCoverFromWalls(grid);

        List<Doodad> doodads = scatterDoodads(grid, pois, rng);

        int[] marine   = findNearestWalkable(grid, 2,             2);
        int[] defender = findNearestWalkable(grid, width - 3,     height - 3);

        return new Result(grid, marine[0], marine[1], defender[0], defender[1], pois, doodads);
    }

    /**
     * Scatters chair/crate/chest props through hollow building interiors.
     * Each POI's footprint is interior-only (the perimeter is wall), so we
     * sample {@link PointOfInterest#left}+1..{@link PointOfInterest#right}-1
     * × {@link PointOfInterest#top}+1..{@link PointOfInterest#bottom}-1.
     * Doorways are excluded so a prop doesn't stamp on top of the door
     * overlay. Solid (non-hollow) buildings get nothing — there's no interior
     * to dress.
     */
    private static List<Doodad> scatterDoodads(NavigationGrid grid, List<PointOfInterest> pois, Random rng) {
        List<Doodad> out = new ArrayList<>();
        for (PointOfInterest poi : pois) {
            if (rng.nextFloat() >= DOODAD_PER_BUILDING_CHANCE) continue;
            List<int[]> interior = new ArrayList<>();
            for (int y = poi.top + 1; y <= poi.bottom - 1; y++) {
                for (int x = poi.left + 1; x <= poi.right - 1; x++) {
                    if (!grid.isWalkable(x, y)) continue;
                    if (grid.isDoorway(x, y))   continue;
                    interior.add(new int[]{x, y});
                }
            }
            if (interior.isEmpty()) continue;

            // First placement is guaranteed; subsequent are a coin flip until
            // the cap is hit. Keeps small rooms from collecting four chairs.
            for (int i = 0; i < interior.size() && i < DOODAD_MAX_PER_BUILDING; i++) {
                if (i > 0 && rng.nextFloat() >= DOODAD_EXTRA_CELL_CHANCE) break;
                int pickIdx = rng.nextInt(interior.size());
                int[] cell = interior.remove(pickIdx);
                TileManifest.TileFrame tile = TileManifest.DOODAD_POOL[rng.nextInt(TileManifest.DOODAD_POOL.length)];
                out.add(new Doodad(cell[0], cell[1], tile));
                if (interior.isEmpty()) break;
            }
        }
        return out;
    }

    /**
     * Initial wall HP pass — every non-walkable cell gets {@link #WALL_HP_DEFAULT}.
     * Out-of-bounds cells don't exist in the grid and stay "indestructible" by
     * virtue of having no cell to damage. Run after building placement so we
     * tag the right cells.
     */
    private static void seedWallHp(NavigationGrid grid) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) grid.setWallHp(x, y, WALL_HP_DEFAULT);
            }
        }
    }

    /**
     * Sets each walkable cell's cover level to the count of its cardinal neighbors
     * that are non-walkable (walls or out-of-bounds), clamped to
     * {@link NavigationGrid#MAX_COVER}. Run after all buildings are placed so the
     * cover map sees the final wall layout.
     *
     * <p>Cardinal-only (4-neighbor) because diagonal adjacency reads as "you're
     * standing in a corner" not "you're covered" — the bias should be toward
     * peeking out from along a wall edge.
     */
    private static void bakeCoverFromWalls(NavigationGrid grid) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                int walls = 0;
                if (!grid.isWalkable(x + 1, y)) walls++;
                if (!grid.isWalkable(x - 1, y)) walls++;
                if (!grid.isWalkable(x, y + 1)) walls++;
                if (!grid.isWalkable(x, y - 1)) walls++;
                grid.setCoverAt(x, y, walls);
            }
        }
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

    /**
     * Carves a building in the block and returns a tagged POI describing it,
     * or {@code null} if the block was left as a plaza or was too small to
     * fit a footprint. POI kind is rolled with weights leaning toward
     * residential (most common in urban combat scenes).
     *
     * <p>Buildings larger than the {@link #HOLLOW_MIN_SIZE} threshold are carved
     * <em>hollow</em>: only the perimeter is wall, the interior is walkable
     * floor, and one perimeter cell is punched out as a {@link NavigationGrid#isDoorway doorway}.
     * Smaller footprints fall back to solid (no interior to enclose). Hollow
     * buildings give the zone-graph layer something to chew on — each interior
     * is its own zone, the doorway becomes a portal, and breaching a wall
     * cleanly emits a new portal between the interior and the street.
     */
    private static PointOfInterest placeBuilding(NavigationGrid grid, int l, int t, int r, int b, Random rng) {
        if (rng.nextFloat() < PLAZA_CHANCE) return null;
        int bl = l + BUILDING_INSET;
        int bt = t + BUILDING_INSET;
        int br = r - BUILDING_INSET;
        int bb = b - BUILDING_INSET;
        if (br - bl < 1 || bb - bt < 1) return null;

        int w = br - bl + 1;
        int h = bb - bt + 1;
        boolean hollow = w >= HOLLOW_MIN_SIZE && h >= HOLLOW_MIN_SIZE;

        if (hollow) {
            // Perimeter only — interior stays walkable from the initial pass.
            for (int x = bl; x <= br; x++) {
                grid.setWalkable(x, bt, false);
                grid.setWalkable(x, bb, false);
            }
            for (int y = bt + 1; y <= bb - 1; y++) {
                grid.setWalkable(bl, y, false);
                grid.setWalkable(br, y, false);
            }
            punchDoorway(grid, bl, bt, br, bb, rng);
        } else {
            // Too small to enclose anything readable — solid block.
            for (int y = bt; y <= bb; y++) {
                for (int x = bl; x <= br; x++) {
                    grid.setWalkable(x, y, false);
                }
            }
        }

        PointOfInterest.Kind kind = pickPoiKind(rng);
        int cx = (bl + br) / 2;
        int cy = (bt + bb) / 2;
        int[] anchor = findNearestWalkableFromBuilding(grid, cx, cy, bl, bt, br, bb);
        return new PointOfInterest(kind, bl, bt, br, bb, anchor[0], anchor[1]);
    }

    /**
     * Picks one perimeter wall cell (not a corner) and flips it to walkable +
     * doorway. The doorway cell becomes its own 1-cell zone in the graph,
     * with portals connecting the building interior to the street outside.
     *
     * <p>Corners are excluded because a corner doorway would face diagonally
     * into nothing — the agent on the outside would step diagonally onto the
     * doorway, which reads as awkward "wall hugger" geometry.
     */
    private static void punchDoorway(NavigationGrid grid, int bl, int bt, int br, int bb, Random rng) {
        // Edge-cell candidates: non-corner cells on each side.
        int side = rng.nextInt(4);
        int doorX, doorY;
        switch (side) {
            case 0: doorX = bl + 1 + rng.nextInt(br - bl - 1); doorY = bt;     break; // top
            case 1: doorX = bl + 1 + rng.nextInt(br - bl - 1); doorY = bb;     break; // bottom
            case 2: doorX = bl;     doorY = bt + 1 + rng.nextInt(bb - bt - 1); break; // left
            default: doorX = br;    doorY = bt + 1 + rng.nextInt(bb - bt - 1); break; // right
        }
        grid.setWalkable(doorX, doorY, true);
        grid.setFloor(doorX, doorY, true);
        grid.setDoorway(doorX, doorY, true);
        grid.openAllEdges(doorX, doorY);
    }

    /**
     * Picks a POI kind. Residential weight is intentionally higher so that
     * landmark buildings (lab, comms, depot) stand out as unusual targets on
     * a map dominated by ordinary structures.
     */
    private static PointOfInterest.Kind pickPoiKind(Random rng) {
        float r = rng.nextFloat();
        if (r < 0.55f) return PointOfInterest.Kind.RESIDENTIAL;
        if (r < 0.75f) return PointOfInterest.Kind.DEPOT;
        if (r < 0.90f) return PointOfInterest.Kind.LABORATORY;
        return PointOfInterest.Kind.COMMS;
    }

    /**
     * BFS outward from (cx, cy), returning the first walkable cell that is NOT
     * inside the building footprint. This is the "stand here to interact with
     * this building" cell — used as the anchor for placing planters, loot
     * crates, VIPs, etc.
     */
    private static int[] findNearestWalkableFromBuilding(NavigationGrid grid, int cx, int cy, int bl, int bt, int br, int bb) {
        boolean[] visited = new boolean[grid.getWidth() * grid.getHeight()];
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy});
        if (grid.inBounds(cx, cy)) visited[cy * grid.getWidth() + cx] = true;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            boolean inBuilding = p[0] >= bl && p[0] <= br && p[1] >= bt && p[1] <= bb;
            if (!inBuilding && grid.isWalkable(p[0], p[1])) return p;
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
        return new int[]{cx, cy};
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
