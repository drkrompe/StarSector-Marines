package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.MapGenerator;
import com.dillon.starsectormarines.battle.mapgen.MapResult;
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
 * Some adjacent block cells are merged into multi-cell super-blocks before
 * placement (see {@link #buildMegaBlocks} / {@link #dissolveMegaInteriors})
 * so the city has occasional larger plots with multiple buildings sharing a
 * private interior, instead of every house standing across a public street
 * from the next.
 *
 * <p>The result hands back two spawn anchor cells — top-left for marines,
 * bottom-right for defenders. Callers BFS outward from each anchor to place
 * individual units, which lets the same generator drop units into a wide
 * street, a narrow alley, or a plaza without per-shape logic.
 */
public final class UrbanMapGenerator implements MapGenerator {

    private static final int STREET_WIDTH_MIN = 3;
    private static final int STREET_WIDTH_MAX = 4;
    private static final int BLOCK_LEN_MIN    = 8;
    private static final int BLOCK_LEN_MAX    = 14;
    /** Probability a block is left as an open plaza instead of carrying a building. */
    private static final float PLAZA_CHANCE   = 0.12f;
    /**
     * Per-direction roll for merging a block-grid cell with its E or S neighbor
     * into a multi-cell super-block. Two passing rolls produce a 2×2 merge
     * (subject to the SE corner being free); a single pass produces 2×1 or
     * 1×2. The resulting mega-block keeps each member cell's individual
     * building but dissolves the street/sidewalk between them into shared
     * private interior — reads as a city block with multiple buildings sharing
     * a courtyard, instead of standalone houses across a public road.
     */
    private static final float MEGA_BLOCK_CHANCE = 0.30f;
    /** Inset between block boundary and building footprint — keeps buildings off the curb. */
    private static final int BUILDING_INSET   = 1;
    /** Starting HP for every wall cell. Sized so a typical strafe shot chips, a missile breaches in one or two hits. Tune alongside damage values. */
    private static final int WALL_HP_DEFAULT  = 100;
    /** Building footprints with both dimensions ≥ this are carved hollow (walkable interior + one doorway). Smaller fall back to solid. */
    private static final int HOLLOW_MIN_SIZE  = 4;
    /**
     * Minimum building dimension along the split axis to qualify for an
     * interior partition wall. With a 1-cell-thick wall and ≥2 walkable cells
     * on each side, the split axis needs at least 5 interior cells (so the
     * outer footprint needs ≥7 in that direction). Below this the rooms come
     * out too narrow to feel like separate spaces.
     */
    private static final int MULTI_ROOM_MIN_DIM = 7;
    /** Chance a sufficiently-large hollow building gets subdivided into multiple rooms. */
    private static final float MULTI_ROOM_CHANCE = 0.65f;
    /** Minimum dim (both axes) for a hollow building to qualify for a second perimeter doorway. */
    private static final int SECOND_DOORWAY_MIN_DIM = 5;
    /**
     * Chance a qualifying hollow building gets a second perimeter doorway on
     * the opposite side. Two-door buildings act as throughways — AI uses them
     * as shortcuts between street segments and as flanking cover rather than
     * just defensive fallback positions.
     */
    private static final float SECOND_DOORWAY_CHANCE = 0.7f;

    /** Probability a hollow building gets at least one prop placed. */
    private static final float DOODAD_PER_BUILDING_CHANCE = 0.8f;
    /** Cap on doodads per building — small interiors look junked with too many. */
    private static final int DOODAD_MAX_PER_BUILDING = 3;
    /** Probability each interior cell (after the first) gets a prop, until the cap is hit. */
    private static final float DOODAD_EXTRA_CELL_CHANCE = 0.4f;

    /** Stateless — singleton-friendly. Kept public so callers can `new UrbanMapGenerator()` if they don't want to hold the instance. */
    public UrbanMapGenerator() {}

    @Override
    public MapResult generate(int width, int height, long seed) {
        Random rng = new Random(seed);
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);

        // Everything starts walkable + street; placing buildings clears walkable
        // (perimeter) or just the street flag (interior) so what remains street
        // is exactly the outdoor cells the road autotile should render.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }

        List<int[]> blockRowsY = blockStripsAlongAxis(height, rng);
        List<int[]> blockColsX = blockStripsAlongAxis(width,  rng);

        // Decide super-block membership before placing any buildings so each
        // member cell can be tagged with its mega-block's shared theme.
        List<MegaBlock> megaBlocks = buildMegaBlocks(blockRowsY.size(), blockColsX.size(), rng);
        int[][] megaIndex = indexMegaBlocks(megaBlocks, blockRowsY.size(), blockColsX.size());

        List<PointOfInterest> pois = new ArrayList<>();
        // Per-POI theme map so doodad scatter can match each building's flavor
        // without re-rolling.
        java.util.Map<PointOfInterest, DistrictTheme> poiThemes = new java.util.HashMap<>();
        List<int[]> skyPortPlazas = new ArrayList<>();
        for (int r = 0; r < blockRowsY.size(); r++) {
            int[] row = blockRowsY.get(r);
            for (int c = 0; c < blockColsX.size(); c++) {
                int[] col = blockColsX.get(c);
                DistrictTheme theme = megaBlocks.get(megaIndex[r][c]).theme;
                PointOfInterest poi = placeBuilding(grid, topology, col[0], row[0], col[1], row[1], rng, theme);
                if (poi != null) {
                    pois.add(poi);
                    poiThemes.put(poi, theme);
                } else if (theme == DistrictTheme.SKY_PORT) {
                    // Plaza in a sky-port = open landing pad area. Stamp at the
                    // block center; placement avoids the curb so the pad reads
                    // as a deliberate clearing.
                    int cx = (col[0] + col[1]) / 2;
                    int cy = (row[0] + row[1]) / 2;
                    if (grid.inBounds(cx, cy) && grid.isWalkable(cx, cy)) {
                        skyPortPlazas.add(new int[]{cx, cy});
                    }
                }
            }
        }

        // Now that every building is carved, fold the absorbed strips between
        // merged cells into private interior. Has to run after placeBuilding so
        // the wall perimeters are in place — the dissolution preserves walls
        // and only clears the street flag on the walkable in-between cells.
        dissolveMegaInteriors(grid, topology, megaBlocks, blockRowsY, blockColsX);

        seedWallHp(grid);
        bakeCoverFromWalls(grid);
        paintCrosswalks(grid, topology);

        List<Doodad> doodads = scatterDoodads(grid, pois, poiThemes, rng);
        for (int[] plaza : skyPortPlazas) {
            doodads.add(new Doodad(plaza[0], plaza[1], TileManifest.LZ_PAD, true));
        }

        int[] marine   = pickSpawnAnchor(grid, skyPortPlazas, 1, 1, width / 2,        height - 1, rng);
        int[] defender = pickSpawnAnchor(grid, skyPortPlazas, width / 2, 1, width - 1, height - 1, rng);

        // Flag every remaining non-walkable cell as a wall so the renderer's
        // wall pass and adjacency predicates can ask topology.isWall(x,y)
        // directly — without inferring it from "non-walkable AND not any other
        // kind of non-walkable thing". Vehicles stamp AFTER this in
        // BattleSetup, so they keep WALL cleared.
        topology.tagDefaultWalls(grid);

        return new MapResult(grid, topology, marine[0], marine[1], defender[0], defender[1], pois, doodads);
    }

    /**
     * Picks a spawn anchor in the rectangle {@code [xMin, xMax) × [yMin, yMax)}.
     * Prefers a sky-port plaza inside the rect — marines land at the spaceport,
     * defenders dig in around theirs. Falls back to a random walkable cell, then
     * to a linear scan if that fails (very tight maps). The legacy hardcoded
     * (2, 2) / (width-3, height-3) anchors always ended up at the same corner;
     * randomization gives each generated map a different "where do they meet"
     * shape.
     */
    private static int[] pickSpawnAnchor(NavigationGrid grid, List<int[]> skyPortPlazas,
                                          int xMin, int yMin, int xMax, int yMax, Random rng) {
        List<int[]> halfPlazas = new ArrayList<>();
        for (int[] plaza : skyPortPlazas) {
            if (plaza[0] >= xMin && plaza[0] < xMax && plaza[1] >= yMin && plaza[1] < yMax) {
                halfPlazas.add(plaza);
            }
        }
        if (!halfPlazas.isEmpty()) {
            return halfPlazas.get(rng.nextInt(halfPlazas.size()));
        }
        int spanX = Math.max(1, xMax - xMin);
        int spanY = Math.max(1, yMax - yMin);
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = xMin + rng.nextInt(spanX);
            int y = yMin + rng.nextInt(spanY);
            if (grid.inBounds(x, y) && grid.isWalkable(x, y)) {
                return new int[]{x, y};
            }
        }
        for (int y = yMin; y < yMax; y++) {
            for (int x = xMin; x < xMax; x++) {
                if (grid.isWalkable(x, y)) return new int[]{x, y};
            }
        }
        return new int[]{(xMin + xMax) / 2, (yMin + yMax) / 2};
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
    private static List<Doodad> scatterDoodads(NavigationGrid grid, List<PointOfInterest> pois,
                                               java.util.Map<PointOfInterest, DistrictTheme> poiThemes,
                                               Random rng) {
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

            DistrictTheme theme = poiThemes.getOrDefault(poi, DistrictTheme.MIXED);
            TileManifest.TileFrame[] pool = TileManifest.doodadPoolFor(theme);

            // First placement is guaranteed; subsequent are a coin flip until
            // the cap is hit. Keeps small rooms from collecting four chairs.
            for (int i = 0; i < interior.size() && i < DOODAD_MAX_PER_BUILDING; i++) {
                if (i > 0 && rng.nextFloat() >= DOODAD_EXTRA_CELL_CHANCE) break;
                int pickIdx = rng.nextInt(interior.size());
                int[] cell = interior.remove(pickIdx);
                TileManifest.TileFrame tile = pool[rng.nextInt(pool.length)];
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
     * Block-grid rectangle describing one super-block. {@code (r0, c0)}..{@code (r1, c1)}
     * are inclusive indices into {@code blockRowsY} / {@code blockColsX}. A
     * singleton mega-block has {@code r0 == r1 && c0 == c1} and is just a
     * regular standalone block — the theme is still rolled once and applied
     * uniformly. Theme is per mega-block (not per cell) so all sub-buildings
     * inside a merge share an identity.
     */
    private static final class MegaBlock {
        final int r0, c0, r1, c1;
        final DistrictTheme theme;
        MegaBlock(int r0, int c0, int r1, int c1, DistrictTheme theme) {
            this.r0 = r0; this.c0 = c0;
            this.r1 = r1; this.c1 = c1;
            this.theme = theme;
        }
    }

    /**
     * Walks the block grid greedily in row-major order, deciding for each
     * un-claimed cell whether to extend E and/or S into a 2×1, 1×2, or 2×2
     * super-block. The greedy walk is stable: each cell decides its own
     * extension once, with no back-tracking, so the resulting partition is
     * deterministic given the seed. The SE-corner check prevents a 2×2 merge
     * from clobbering a cell already claimed by an earlier mega — keeps the
     * partition shape rectangular (no L-shapes).
     */
    private static List<MegaBlock> buildMegaBlocks(int rows, int cols, Random rng) {
        List<MegaBlock> out = new ArrayList<>();
        boolean[][] taken = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (taken[r][c]) continue;
                boolean canExtE = c + 1 < cols && !taken[r][c + 1];
                boolean canExtS = r + 1 < rows && !taken[r + 1][c];
                boolean extE = canExtE && rng.nextFloat() < MEGA_BLOCK_CHANCE;
                boolean extS = canExtS && rng.nextFloat() < MEGA_BLOCK_CHANCE;
                if (extE && extS && taken[r + 1][c + 1]) {
                    // SE corner is already claimed — can't form a 2×2. Drop one
                    // direction to keep the mega-block rectangular.
                    if (rng.nextBoolean()) extE = false; else extS = false;
                }
                int r1 = extS ? r + 1 : r;
                int c1 = extE ? c + 1 : c;
                for (int rr = r; rr <= r1; rr++) {
                    for (int cc = c; cc <= c1; cc++) {
                        taken[rr][cc] = true;
                    }
                }
                out.add(new MegaBlock(r, c, r1, c1, pickTheme(rng)));
            }
        }
        return out;
    }

    /** Inverts the mega-block list so each block-grid cell can look up its owning mega in O(1). */
    private static int[][] indexMegaBlocks(List<MegaBlock> megas, int rows, int cols) {
        int[][] idx = new int[rows][cols];
        for (int i = 0; i < megas.size(); i++) {
            MegaBlock m = megas.get(i);
            for (int r = m.r0; r <= m.r1; r++) {
                for (int c = m.c0; c <= m.c1; c++) {
                    idx[r][c] = i;
                }
            }
        }
        return idx;
    }

    /**
     * For every multi-cell super-block, clears the street flag on each walkable
     * cell within the mega-block's nav-cell bounds. This dissolves both the
     * absorbed street strip(s) between merged cells AND the sidewalk-inset
     * cells around each member building, leaving them as indoor-floor cells
     * that render with directional autotiles against the perimeter walls.
     *
     * <p>The visual result: a 2×1 merge becomes two buildings sharing a
     * private alley; a 2×2 merge becomes four buildings around a +-shaped
     * private courtyard. Singleton megas are skipped — nothing to dissolve.
     *
     * <p>The dissolution only touches cells inside the mega-block's bounding
     * box, so the street segments north/south/east/west of the mega remain
     * public roads (with sidewalks against the mega's outer perimeter walls,
     * via the renderer's normal autotile path).
     */
    private static void dissolveMegaInteriors(NavigationGrid grid, CellTopology topology,
                                              List<MegaBlock> megaBlocks,
                                              List<int[]> blockRowsY, List<int[]> blockColsX) {
        for (MegaBlock m : megaBlocks) {
            if (m.r0 == m.r1 && m.c0 == m.c1) continue;
            int xMin = blockColsX.get(m.c0)[0];
            int xMax = blockColsX.get(m.c1)[1];
            int yMin = blockRowsY.get(m.r0)[0];
            int yMax = blockRowsY.get(m.r1)[1];
            for (int y = yMin; y <= yMax; y++) {
                for (int x = xMin; x <= xMax; x++) {
                    // Only convert what's currently outdoor street/sidewalk —
                    // skip cells that already had their street flag cleared by
                    // placeBuilding, which are the hollow building interiors
                    // and doorways. Those keep rendering as indoor floor.
                    if (!grid.isWalkable(x, y) || !topology.isStreet(x, y)) continue;
                    // Promote street → courtyard so the renderer paints this
                    // cell with the dark navy autotile — reads as private
                    // open-air pavement, distinct from public road.
                    topology.setGroundKind(x, y, GroundKind.COURTYARD);
                }
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
     *
     * <p>Hollow buildings that exceed {@link #MULTI_ROOM_MIN_DIM} on at least
     * one axis also get a chance to receive an interior partition wall + door
     * (see {@link #addInteriorWall}), turning the single shell into two
     * connected rooms. The zone graph picks up the interior doorway
     * automatically as a portal, so AI gets multi-room vocabulary inside one
     * building for free.
     */
    private static PointOfInterest placeBuilding(NavigationGrid grid, CellTopology topology,
                                                 int l, int t, int r, int b, Random rng, DistrictTheme theme) {
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
            // Interior cells are no longer street — they're indoor floor.
            for (int x = bl; x <= br; x++) {
                grid.setWalkable(x, bt, false);
                grid.setWalkable(x, bb, false);
            }
            for (int y = bt + 1; y <= bb - 1; y++) {
                grid.setWalkable(bl, y, false);
                grid.setWalkable(br, y, false);
            }
            for (int y = bt + 1; y <= bb - 1; y++) {
                for (int x = bl + 1; x <= br - 1; x++) {
                    topology.setGroundKind(x, y, GroundKind.INDOOR);
                }
            }
            // Subdivide first so the perimeter-doorway picker can align with
            // the partition (vertical wall → left/right doors; horizontal
            // wall → top/bottom doors), giving each room its own exterior
            // access when the building scores a 2nd doorway.
            InteriorWall wall = maybeAddInteriorWall(grid, topology, bl, bt, br, bb, rng);
            punchPerimeterDoorways(grid, topology, bl, bt, br, bb, wall, rng);
        } else {
            // Too small to enclose anything readable — solid block.
            for (int y = bt; y <= bb; y++) {
                for (int x = bl; x <= br; x++) {
                    grid.setWalkable(x, y, false);
                }
            }
        }

        PointOfInterest.Kind kind = pickPoiKindForTheme(theme, rng);
        int cx = (bl + br) / 2;
        int cy = (bt + bb) / 2;
        int[] anchor = findNearestWalkableFromBuilding(grid, cx, cy, bl, bt, br, bb);
        return new PointOfInterest(kind, bl, bt, br, bb, anchor[0], anchor[1]);
    }

    /**
     * Picks 1 or 2 perimeter doorways for a hollow building. Single-door
     * buildings get a random side; two-door buildings get opposite sides
     * (top/bottom or left/right) so the building reads as a throughway. When
     * the building has an interior partition, the side pair is aligned with
     * the partition so each room receives its own exterior door — vertical
     * wall → left+right pair, horizontal wall → top+bottom pair.
     *
     * <p>Two-door buildings exist so the AI has reasons to enter buildings
     * besides "I'm being shot at." Multiple entries make interiors
     * shortcut/flank routes, not dead-end fallback zones.
     */
    private static void punchPerimeterDoorways(NavigationGrid grid, CellTopology topology,
                                               int bl, int bt, int br, int bb,
                                               InteriorWall wall, Random rng) {
        int w = br - bl + 1;
        int h = bb - bt + 1;
        boolean twoDoors = w >= SECOND_DOORWAY_MIN_DIM
                        && h >= SECOND_DOORWAY_MIN_DIM
                        && rng.nextFloat() < SECOND_DOORWAY_CHANCE;

        if (!twoDoors) {
            punchDoorwayOnSide(grid, topology, bl, bt, br, bb, rng.nextInt(4), wall, rng);
            return;
        }

        // Pick a side pair that matches the interior partition (if any). Side
        // codes 0/1 are top/bottom (vertical axis), 2/3 are left/right
        // (horizontal axis); XOR with 1 toggles between paired sides.
        int firstSide;
        switch (wall.orient) {
            case VERTICAL:   firstSide = rng.nextBoolean() ? 2 : 3; break;
            case HORIZONTAL: firstSide = rng.nextBoolean() ? 0 : 1; break;
            default:         firstSide = rng.nextInt(4);           break;
        }
        punchDoorwayOnSide(grid, topology, bl, bt, br, bb, firstSide,     wall, rng);
        punchDoorwayOnSide(grid, topology, bl, bt, br, bb, firstSide ^ 1, wall, rng);
    }

    /**
     * Stamps a single perimeter doorway on the specified side. Corners are
     * excluded because a corner doorway would face diagonally into nothing —
     * the agent on the outside would step diagonally onto the doorway, which
     * reads as awkward "wall hugger" geometry.
     *
     * <p>When {@code wall} is a vertical partition and the side is top/bottom,
     * the partition's column is skipped from the random offset — otherwise
     * the doorway opens directly onto the interior wall and the agent can't
     * step through. Same for a horizontal partition with a left/right side.
     *
     * <p>Side codes: {@code 0}=top, {@code 1}=bottom, {@code 2}=left,
     * {@code 3}=right. The XOR-with-1 trick in {@link #punchPerimeterDoorways}
     * relies on this pairing.
     */
    private static void punchDoorwayOnSide(NavigationGrid grid, CellTopology topology,
                                           int bl, int bt, int br, int bb,
                                           int side, InteriorWall wall, Random rng) {
        int doorX, doorY;
        switch (side) {
            case 0:  // top
                doorX = pickAlongRangeExcluding(bl + 1, br - 1,
                            wall.orient == InteriorWallOrient.VERTICAL ? wall.axis : Integer.MIN_VALUE, rng);
                doorY = bt;
                break;
            case 1:  // bottom
                doorX = pickAlongRangeExcluding(bl + 1, br - 1,
                            wall.orient == InteriorWallOrient.VERTICAL ? wall.axis : Integer.MIN_VALUE, rng);
                doorY = bb;
                break;
            case 2:  // left
                doorX = bl;
                doorY = pickAlongRangeExcluding(bt + 1, bb - 1,
                            wall.orient == InteriorWallOrient.HORIZONTAL ? wall.axis : Integer.MIN_VALUE, rng);
                break;
            default: // right
                doorX = br;
                doorY = pickAlongRangeExcluding(bt + 1, bb - 1,
                            wall.orient == InteriorWallOrient.HORIZONTAL ? wall.axis : Integer.MIN_VALUE, rng);
                break;
        }
        grid.setWalkable(doorX, doorY, true);
        grid.setDoorway(doorX, doorY, true);
        grid.openAllEdges(doorX, doorY);
        // Doorway is part of the building — the door overhead overlay reads
        // against interior floor underneath.
        topology.setGroundKind(doorX, doorY, GroundKind.INDOOR);
    }

    /**
     * Returns a uniformly-random integer in {@code [min, max]} (inclusive)
     * skipping {@code exclude} if it falls inside the range. Used by
     * {@link #punchDoorwayOnSide} to avoid landing on the column/row occupied
     * by an interior partition wall — that would point the doorway into a
     * wall cell and seal the room off from the outside.
     */
    private static int pickAlongRangeExcluding(int min, int max, int exclude, Random rng) {
        int size = max - min + 1;
        boolean hasExclude = exclude >= min && exclude <= max;
        if (hasExclude) size -= 1;
        if (size <= 0) return min; // pathological — shouldn't happen given min building sizes
        int pick = rng.nextInt(size);
        int v = min + pick;
        if (hasExclude && v >= exclude) v += 1;
        return v;
    }

    /**
     * Orientation tag carried inside {@link InteriorWall}. Three cases the
     * doorway pickers need to distinguish: no partition (free placement),
     * vertical partition (avoid the partition column on top/bottom doors),
     * horizontal partition (avoid the partition row on left/right doors).
     */
    private enum InteriorWallOrient { NONE, VERTICAL, HORIZONTAL }

    /**
     * Tag returned by {@link #maybeAddInteriorWall} describing the partition
     * (if any). {@link #axis} is the partition column when
     * {@link #orient} is {@link InteriorWallOrient#VERTICAL}, the partition
     * row when {@link InteriorWallOrient#HORIZONTAL}, and {@code -1} for
     * {@link InteriorWallOrient#NONE}.
     *
     * <p>{@link #punchPerimeterDoorways} uses {@link #orient} to align the
     * 2-door side pair; {@link #punchDoorwayOnSide} uses {@link #axis} to
     * skip the partition coordinate when its side's varying-axis would
     * collide — fixes the bug where a top doorway picked at the partition
     * column would open onto an interior wall cell and leave the building
     * unreachable.
     */
    private static final class InteriorWall {
        static final InteriorWall NONE = new InteriorWall(InteriorWallOrient.NONE, -1);
        final InteriorWallOrient orient;
        final int axis;
        InteriorWall(InteriorWallOrient orient, int axis) {
            this.orient = orient;
            this.axis = axis;
        }
    }

    /**
     * Optionally subdivides a hollow building with one interior partition wall
     * plus a doorway through it, turning a single hollow shell into two
     * connected rooms. Wall orientation is picked to split the longer
     * dimension (so the resulting rooms are as square as possible); a 1-cell
     * doorway is punched at a random position along the partition so each
     * room is reachable.
     *
     * <p>Returns the orientation so the caller can align the perimeter doors
     * to the partition. {@link InteriorWallOrient#VERTICAL} means the wall
     * runs N-S (splitting the building into left + right rooms);
     * {@link InteriorWallOrient#HORIZONTAL} means E-W (top + bottom rooms).
     *
     * <p>Connectivity is preserved: each exterior doorway opens into one
     * room, and that room reaches the other via the interior doorway. The
     * {@link com.dillon.starsectormarines.battle.nav.zone.ZoneDetector} picks
     * up the interior doorway as a 1-cell zone with portals on each side, so
     * the room graph naturally inherits the multi-room shape.
     *
     * <p>The interior wall cell is left to the renderer's existing autotile
     * picker. With floor on both E and W (or N and S), the picker resolves to
     * a single-sided edge tile — the visible "face" of the wall ends up on
     * one room's side, which reads as functional but not perfectly symmetric.
     */
    private static InteriorWall maybeAddInteriorWall(NavigationGrid grid, CellTopology topology,
                                                     int bl, int bt, int br, int bb, Random rng) {
        int w = br - bl + 1;
        int h = bb - bt + 1;
        boolean canVert  = w >= MULTI_ROOM_MIN_DIM;
        boolean canHoriz = h >= MULTI_ROOM_MIN_DIM;
        if (!canVert && !canHoriz) return InteriorWall.NONE;
        if (rng.nextFloat() >= MULTI_ROOM_CHANCE) return InteriorWall.NONE;

        // Split the longer dimension so rooms come out roughly square. If only
        // one dimension qualifies, take it. Equal dims fall back to a coin flip.
        boolean vertical;
        if (canVert && canHoriz) {
            if (w > h)      vertical = true;
            else if (h > w) vertical = false;
            else            vertical = rng.nextBoolean();
        } else {
            vertical = canVert;
        }

        if (vertical) {
            // wx is the partition column. Leave ≥2 walkable cells of room on
            // each side: range is [bl+3, br-3].
            int wx = bl + 3 + rng.nextInt(w - 6);
            for (int y = bt + 1; y <= bb - 1; y++) {
                grid.setWalkable(wx, y, false);
            }
            // Doorway anywhere along the partition's interior length — the
            // partition continues above and below it (or terminates against
            // the perimeter on the far side if the doorway is at the end).
            int dy = bt + 1 + rng.nextInt(h - 2);
            openInteriorDoorway(grid, topology, wx, dy);
            return new InteriorWall(InteriorWallOrient.VERTICAL, wx);
        } else {
            int wy = bt + 3 + rng.nextInt(h - 6);
            for (int x = bl + 1; x <= br - 1; x++) {
                grid.setWalkable(x, wy, false);
            }
            int dx = bl + 1 + rng.nextInt(w - 2);
            openInteriorDoorway(grid, topology, dx, wy);
            return new InteriorWall(InteriorWallOrient.HORIZONTAL, wy);
        }
    }

    /** Restores a single partition cell to a doorway (walkable + interior floor + zone-graph portal). */
    private static void openInteriorDoorway(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, true);
        grid.setDoorway(x, y, true);
        grid.openAllEdges(x, y);
        topology.setGroundKind(x, y, GroundKind.INDOOR);
    }

    /**
     * Paints crosswalk stripes on road cells that mark the entrance to a street
     * intersection. A road cell qualifies when:
     * <ul>
     *   <li>It's a street cell that's not itself a sidewalk (mid-road, not at the curb).</li>
     *   <li>Two opposite cardinal neighbors are sidewalks — meaning the cell is
     *       between two buildings (a "throat" segment of road).</li>
     *   <li>The next cell along the road's flow direction breaks that pattern
     *       (one or both perpendicular sidewalks ends) — i.e., the road opens
     *       into a perpendicular street.</li>
     * </ul>
     * Stripes are oriented perpendicular to traffic flow (zebra-crossing
     * convention): N-S road gets horizontal stripes, E-W road gets vertical.
     */
    private static void paintCrosswalks(NavigationGrid grid, CellTopology topology) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!topology.isStreet(x, y)) continue;
                if (isSidewalkCell(grid, topology, x, y)) continue;

                boolean ewRoad = isSidewalkCell(grid, topology, x, y + 1) && isSidewalkCell(grid, topology, x, y - 1);
                boolean nsRoad = isSidewalkCell(grid, topology, x + 1, y) && isSidewalkCell(grid, topology, x - 1, y);

                if (ewRoad && !nsRoad) {
                    // Road runs E-W between two N/S buildings. Check whether
                    // the next cell east or west breaks the sidewalk pattern.
                    boolean eOpen = !isSidewalkCell(grid, topology, x + 1, y + 1) || !isSidewalkCell(grid, topology, x + 1, y - 1);
                    boolean wOpen = !isSidewalkCell(grid, topology, x - 1, y + 1) || !isSidewalkCell(grid, topology, x - 1, y - 1);
                    if (eOpen || wOpen) {
                        topology.setCrosswalk(x, y, true);
                        // E-W traffic flow → N-S oriented stripes (vertical).
                        topology.setCrosswalkStripesHorizontal(x, y, false);
                    }
                } else if (nsRoad && !ewRoad) {
                    boolean nOpen = !isSidewalkCell(grid, topology, x + 1, y + 1) || !isSidewalkCell(grid, topology, x - 1, y + 1);
                    boolean sOpen = !isSidewalkCell(grid, topology, x + 1, y - 1) || !isSidewalkCell(grid, topology, x - 1, y - 1);
                    if (nOpen || sOpen) {
                        topology.setCrosswalk(x, y, true);
                        // N-S traffic flow → E-W oriented stripes (horizontal).
                        topology.setCrosswalkStripesHorizontal(x, y, true);
                    }
                }
            }
        }
    }

    /** Mirror of the renderer's same-named check — needed at gen time so {@link #paintCrosswalks} can decide where intersections begin. */
    private static boolean isSidewalkCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        if (!grid.isWalkable(x, y)) return false;
        if (!topology.isStreet(x, y))   return false;
        return isInBoundsWall(grid, x + 1, y)
                || isInBoundsWall(grid, x - 1, y)
                || isInBoundsWall(grid, x, y + 1)
                || isInBoundsWall(grid, x, y - 1);
    }

    private static boolean isInBoundsWall(NavigationGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        return !grid.isWalkable(x, y);
    }

    /**
     * Picks a POI kind biased by the block's theme. {@link DistrictTheme#MIXED}
     * falls through to the legacy weighted roll where residential dominates so
     * landmark buildings (lab, comms, depot) stand out.
     */
    private static PointOfInterest.Kind pickPoiKindForTheme(DistrictTheme theme, Random rng) {
        switch (theme) {
            case RESIDENTIAL: return PointOfInterest.Kind.RESIDENTIAL;
            case WAREHOUSE:   return PointOfInterest.Kind.DEPOT;
            case SKY_PORT:    return rng.nextFloat() < 0.5f ? PointOfInterest.Kind.COMMS : PointOfInterest.Kind.DEPOT;
            case MIXED:
            default: {
                float r = rng.nextFloat();
                if (r < 0.55f) return PointOfInterest.Kind.RESIDENTIAL;
                if (r < 0.75f) return PointOfInterest.Kind.DEPOT;
                if (r < 0.90f) return PointOfInterest.Kind.LABORATORY;
                return PointOfInterest.Kind.COMMS;
            }
        }
    }

    /**
     * Picks a per-block theme. Weighting leans heavily toward {@link DistrictTheme#MIXED}
     * so themed districts read as occasional landmark blocks rather than a city
     * built entirely out of warehouses or sky-ports. Tune the cutoffs to shift
     * the mix.
     */
    private static DistrictTheme pickTheme(Random rng) {
        float r = rng.nextFloat();
        if (r < 0.20f) return DistrictTheme.RESIDENTIAL;
        if (r < 0.35f) return DistrictTheme.WAREHOUSE;
        if (r < 0.45f) return DistrictTheme.SKY_PORT;
        return DistrictTheme.MIXED;
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
