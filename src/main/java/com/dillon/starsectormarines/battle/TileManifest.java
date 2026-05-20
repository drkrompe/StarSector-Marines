package com.dillon.starsectormarines.battle;

/**
 * Hand-curated mapping from semantic battle-tile categories to source
 * {@code (col, row)} positions in {@code graphics/tilesets/urban-tileset.png}.
 *
 * <p>This sheet is a top-down 32px set with no 2-tall walls (the previous sheet
 * had wall pieces drawn in 3/4 perspective, which had to be squashed vertically
 * and looked awkward next to the marine sprites). All tiles here are 1×1, so
 * walls slot cleanly into one nav-grid cell.
 *
 * <p>Layout on the sheet (10×10 cells):
 * <ul>
 *   <li>{@code (0..2, 0..2)} — clean floor 3×3 (nine variants pulled into the floor pool)</li>
 *   <li>{@code (3..5, 0..2)} — clean wall 3×3 autotile, center cell empty</li>
 *   <li>{@code (6, 2)} — closed door with green LED (decorative doodad)</li>
 *   <li>{@code (7, 2)} — open door overhead (transparent overlay rendered above floor at doorway cells)</li>
 *   <li>{@code (0..2, 4..6)} — damaged floor 3×3 (rubble pool)</li>
 *   <li>{@code (3..5, 4..6)} — damaged wall 3×3 autotile, center cell empty (reserved for a future "damaged-but-standing" wall state)</li>
 *   <li>row 7 cols 6-9 — chairs + chest doodads</li>
 *   <li>row 3 cols 3-4, row 1 cols 8-9 — crate doodads</li>
 *   <li>remaining cells — grates, bookshelves, terminals, rubble piles (reserved for prop placement later)</li>
 * </ul>
 *
 * <p>Walls are picked via {@link #pickWallTile} from the 4-neighbor exposure
 * pattern — top-edge cells use the top row, left-edge use the left column,
 * corners use the matching corner cell. The center cell {@code (4, 1)} is
 * transparent in the source art and is reserved for the "all four neighbors
 * are walls" case — the renderer paints a solid color there instead of stamping
 * the empty tile.
 */
public final class TileManifest {

    public static final String SHEET = "graphics/tilesets/urban-tileset.png";
    public static final int TILE_SIZE = 32;

    /**
     * Second sheet — road autotile lives here. Same 32px cell size, drawn
     * separately from {@link #SHEET} so the road art (dashed perimeter +
     * red safety stripe) can iterate independently of the indoor floor set.
     */
    public static final String ROAD_SHEET = "graphics/tilesets/urban-tileset-2.png";

    /**
     * Third sheet — outdoor surface autotiles (grass, dirt, stone, sand,
     * snow) plus the polished interior {@code fl-tile} cluster. Drawn at
     * 16px source-cell size and upscaled 2x to fit the 32px nav grid.
     */
    public static final String FLOORS_SHEET = "graphics/tilesets/Floors_Tiles.png";
    public static final int FLOORS_TILE_SIZE = 16;

    /** Fourth sheet — water autotile. 16px cells, upscaled 2x like {@link #FLOORS_SHEET}. */
    public static final String WATER_SHEET = "graphics/tilesets/Water_tiles.png";

    /**
     * Fifth sheet — sliced strip carrying the modern road + sidewalk look
     * plus a culvert/bench doodad set. Variable-width frames separated by
     * alpha gutters, indexed by {@link com.dillon.starsectormarines.battle.sprites.UrbanTile3}.
     * Loaded by {@link com.dillon.starsectormarines.battle.sprites.UrbanTile3Tileset};
     * the renderer dispatches STREET cells through this sheet when it's
     * loaded and falls back to the {@link #ROAD_SHEET} autotile otherwise.
     */
    public static final String STREET3_SHEET = "graphics/tilesets/urban-tileset-3.png";

    /**
     * Top-left cell of the road 3×3 autotile block on {@link #ROAD_SHEET}.
     * Center cell (13, 1) is the open-road interior; pickRoadTile returns it
     * for the all-walls-around-me case and falls back to {@link #ROAD_FILL_RGB}
     * only for fully-interior cells (no wall neighbors at all). Ground-truth
     * for this cell is {@code mod/data/tilesets/urban-tileset-2.catalog.json}'s
     * {@code road-nw} entry — moved here from col 6 after the sheet was redrawn.
     */
    private static final int ROAD_COL_ORIGIN = 12;
    private static final int ROAD_ROW_ORIGIN = 0;
    /** Open-road surface color, sampled at the center pixel of the road autotile center cell (13, 1). Verified by {@code TileManifestFillColorTest}. */
    public static final int ROAD_FILL_RGB = 0x7983A1; // 121, 131, 161

    /**
     * Top-left cell of the courtyard 3×3 autotile block on {@link #ROAD_SHEET}.
     * Dark navy bevelled stone — visually distinct from both the road autotile
     * and the light beige indoor floor. Used on private interior pavement
     * inside multi-cell super-blocks (see {@link NavigationGrid#isCourtyard}),
     * so a row-house or warehouse complex with a shared courtyard reads as one
     * plot rather than two buildings across a street.
     *
     * <p>Note: the catalog file currently has no entries for cells (0..2, 0..2)
     * because the in-game labeller's alpha-threshold misses the dark navy stone
     * art. Visually the cells ARE present and the renderer reads them fine —
     * this is a known catalog gap, not an art gap.
     */
    private static final int COURTYARD_COL_ORIGIN = 0;
    private static final int COURTYARD_ROW_ORIGIN = 0;
    /** Open-courtyard surface color, sampled inside the open-area quadrant of the courtyard NW edge tile. Verified by {@code TileManifestFillColorTest}. */
    public static final int COURTYARD_FILL_RGB = 0x555A74; // 85, 90, 116

    /**
     * Sidewalk tile stamped on any street cell adjacent to a building wall —
     * forms a 1-cell buffer ring around every building. The road autotile
     * treats sidewalk cells as a boundary, so the road's dashed perimeter
     * art lights up against the sidewalk edge instead of pressing straight
     * into the wall.
     *
     * <p>Placeholder: the current sheet has no dedicated sidewalk art, so
     * we point at cell (11, 1) — labelled {@code fl-3} in the catalog, a
     * plain-floor variant. Reads visually as light pavement next to the
     * darker road autotile, which is close enough to "sidewalk" for now.
     * Replace once a real sidewalk tile gets added to the sheet.
     */
    public static final TileFrame SIDEWALK = new TileFrame(11, 1);

    /**
     * Landing-zone pad decal stamped under each shuttle's touchdown cell.
     *
     * <p>Placeholder: the current sheet has no dedicated LZ pad art, so we
     * point at cell (16, 2) — labelled {@code grate-2} in the catalog. Visually
     * it's a small grate, not a yellow-striped pad; the sky-port plaza still
     * reads as deliberate because of the surrounding open ground, but the
     * decal itself is off. Replace once a real LZ marker tile gets added.
     */
    public static final TileFrame LZ_PAD = new TileFrame(16, 2);

    /**
     * Top-left cell of the turret-wall 3×3 autotile block on {@link #ROAD_SHEET}.
     * Same shape as the urban-1 wall block at cols 3..5 rows 0..2 — directional
     * caps on the outside of each cell, transparent center — but the art reads
     * as a sandbag embankment rather than masonry. Used by
     * {@link com.dillon.starsectormarines.battle.mapgen.bsp.DefensePostStamper}
     * to ring MEDIUM/LARGE turret emplacements: 8 cells around the turret stamp
     * non-walkable + SEE_THROUGH + a doodad from this block, granting cover via
     * the standard wall-adjacency bake without blocking LoS or shots.
     */
    private static final int TURRET_EMBANKMENT_COL_ORIGIN = 3;
    private static final int TURRET_EMBANKMENT_ROW_ORIGIN = 0;

    /**
     * Vent grate used as the LIGHT-tier defense-post ring tile. Single tile,
     * non-directional — LIGHT posts ring the turret with 4 cardinal vent cells
     * rather than the full 8-cell embankment. {@code grate-1} on the road sheet
     * (col 11, row 2) reads as an industrial fixture and visually distinguishes
     * a beach LIGHT post from a port MEDIUM embankment.
     */
    public static final TileFrame LIGHT_POST_VENT = new TileFrame(11, 2);

    /**
     * Tile frame for one cell of a MEDIUM/LARGE defense-post embankment ring,
     * keyed by the cell's position relative to the post's center turret.
     * {@code relX, relY} are in {@code [-1, +1]} (the 3×3 around the turret).
     * {@code (relX=0, relY=0)} is the turret cell itself — callers don't paint
     * a doodad there. {@code relY > 0} means the cell is north of the center
     * (higher world Y), which picks source row 0 (the north-facing edge art),
     * matching {@link #pickWallTile}'s row convention.
     */
    public static TileFrame turretEmbankment(int relX, int relY) {
        return new TileFrame(
                TURRET_EMBANKMENT_COL_ORIGIN + (relX + 1),
                TURRET_EMBANKMENT_ROW_ORIGIN + (1 - relY));
    }

    /**
     * Matching 3×3 to {@link #turretEmbankment} from the first block of
     * urban-tileset-2 (cols 0-2). Chunkier wall art that "bows outward" —
     * used for defense-post shapes that protrude into the kill zone (WEDGE,
     * TRAPEZOID) so the embankment reads as a heavier earthwork than the
     * thinner block-2 ring used for straight LINE emplacements. Same
     * {@code relX, relY} convention as {@link #turretEmbankment}.
     */
    private static final int TURRET_BOW_COL_ORIGIN = 0;
    private static final int TURRET_BOW_ROW_ORIGIN = 0;

    public static TileFrame turretBowOut(int relX, int relY) {
        return new TileFrame(
                TURRET_BOW_COL_ORIGIN + (relX + 1),
                TURRET_BOW_ROW_ORIGIN + (1 - relY));
    }

    /** Top-left cell of the clean-wall 3×3 autotile block. */
    private static final int WALL_COL_ORIGIN = 3;
    private static final int WALL_ROW_ORIGIN = 0;
    /** Top-left cell of the clean-floor 3×3 autotile block. Center (1,1) has no frame edges and renders for open floor. */
    private static final int FLOOR_COL_ORIGIN = 0;
    private static final int FLOOR_ROW_ORIGIN = 0;
    /** Top-left cell of the damaged-floor 3×3 autotile block. Same directional shape as clean floor, just at row 4. */
    private static final int RUBBLE_COL_ORIGIN = 0;
    private static final int RUBBLE_ROW_ORIGIN = 4;

    /**
     * Overhead-door overlay stamped on top of the floor for doorway cells (the
     * cells {@link com.dillon.starsectormarines.battle.UrbanMapGenerator#punchDoorway
     * punches} through building perimeters). Source art is mostly transparent
     * with a slim overhead bar — units walk underneath cleanly.
     */
    public static final TileFrame DOOR_OPEN = new TileFrame(7, 2);

    /**
     * Pool of decorative props scattered through hollow building interiors.
     * Visual-only — placed on walkable cells, never block movement. Mix of
     * crates, chairs, a chest, and the closed-door panel to read as
     * lived-in rooms. This is the {@link DistrictTheme#MIXED} fallback;
     * themed districts pull from the narrower pools below.
     */
    public static final TileFrame[] DOODAD_POOL = {
            new TileFrame(8, 1), new TileFrame(9, 1),       // tan + amber crates
            new TileFrame(3, 3), new TileFrame(4, 3),       // gold + green crates
            new TileFrame(6, 7),                            // bench / paired-seat
            new TileFrame(7, 7),                            // brown chest
            new TileFrame(8, 7), new TileFrame(9, 7),       // small stools
            new TileFrame(6, 2),                            // closed-door panel (decoration only)
    };

    /** Homely furnishings — clean chairs + chests from urban-tileset rows 1-3. Used inside RESIDENTIAL districts. */
    public static final TileFrame[] RESIDENTIAL_DOODADS = {
            new TileFrame(6, 1),                            // chair-south-yellow
            new TileFrame(7, 1),                            // chair-south-green
            new TileFrame(3, 3),                            // chest-1
            new TileFrame(4, 3),                            // chest-2
    };

    /** Stacked crates only — fills warehouse interiors with cargo. */
    public static final TileFrame[] WAREHOUSE_DOODADS = {
            new TileFrame(8, 1), new TileFrame(9, 1),
            new TileFrame(3, 3), new TileFrame(4, 3),
    };

    /** Cargo + a chest + the marker panel — sky-port stations mix crates and freight. */
    public static final TileFrame[] SKYPORT_DOODADS = {
            new TileFrame(8, 1), new TileFrame(9, 1),
            new TileFrame(7, 7),                            // chest
            new TileFrame(6, 2),                            // marker panel
    };

    /** Returns the per-theme doodad pool. {@link DistrictTheme#MIXED} returns the full {@link #DOODAD_POOL}. */
    public static TileFrame[] doodadPoolFor(DistrictTheme theme) {
        switch (theme) {
            case RESIDENTIAL: return RESIDENTIAL_DOODADS;
            case WAREHOUSE:   return WAREHOUSE_DOODADS;
            case SKY_PORT:    return SKYPORT_DOODADS;
            case MIXED:
            default:          return DOODAD_POOL;
        }
    }

    /**
     * Returns the wall tile for a cell given which cardinal neighbors are also
     * walls (or out-of-bounds — treated identically). Returns {@code null} when
     * the cell is fully enclosed (all four neighbors are walls) — the caller
     * paints a solid fill there because the source sheet's center cell is
     * transparent.
     *
     * <p>The 3×3 wall block on the source sheet is laid out spatially: source
     * row 0 holds the north-facing wall art, row 2 the south-facing, col 0 the
     * west-facing, col 2 the east-facing. So a wall at the north perimeter of
     * a building (nWall=true because OOB or another wall is to the north,
     * sWall=false because the interior is to the south) picks source row 0;
     * a wall at the building's south perimeter picks source row 2; and the
     * four perimeter cells adjacent to a building's convex corners pick the
     * matching L-bracket cell at e.g. (3, 0) for the NW interior corner.
     *
     * <p>Same shape as {@link #pickFloorTile} — the floor's "decoration on the
     * side that touches a wall" rule corresponds to the wall's "decoration on
     * the side that touches an opening." Stranded-wall edge cases (a 1-cell
     * wall strip exposed on opposite sides — e.g. an interior partition wall
     * with rooms on both sides) fall through to {@code (col=1, row=1)} which
     * is the empty center cell, but that case can only arise when both N and S
     * have walls *and* both E and W have walls, which the early null-return
     * already catches. Single-axis stranding (e.g. a vertical partition wall
     * with N and S walls but neither E nor W) resolves to one of the four
     * mid-edge tiles, which works visually as a vertical-wall stub.
     */
    public static TileFrame pickWallTile(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        if (nWall && sWall && eWall && wWall) return null;

        int col, row;
        if (nWall) {
            row = 0;
        } else if (sWall) {
            row = 2;
        } else {
            row = 1;
        }

        if (wWall) {
            col = 0;
        } else if (eWall) {
            col = 2;
        } else {
            col = 1;
        }

        return new TileFrame(WALL_COL_ORIGIN + col, WALL_ROW_ORIGIN + row);
    }

    /**
     * Returns the clean-floor tile for a walkable cell given which cardinal
     * neighbors are walls (in-bounds non-walkable cells only — OOB is treated
     * as open so streets at the map edge stay center-tiled). The floor 3×3 has
     * its frame edges drawn on the side that touches a wall, so picking
     * directionally makes the floor "kiss" each wall it abuts. Cells with no
     * wall neighbors get the open-floor center {@code (1, 1)}.
     */
    public static TileFrame pickFloorTile(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        int col = wWall ? 0 : (eWall ? 2 : 1);
        int row = nWall ? 0 : (sWall ? 2 : 1);
        return new TileFrame(FLOOR_COL_ORIGIN + col, FLOOR_ROW_ORIGIN + row);
    }

    /**
     * Damaged-floor counterpart to {@link #pickFloorTile} — same directional
     * shape, drawn from the damaged 3×3 block. Used on rubble cells (former
     * walls that were knocked down by damage) so the breach reads as broken
     * masonry rather than fresh paneling.
     */
    public static TileFrame pickRubbleTile(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        int col = wWall ? 0 : (eWall ? 2 : 1);
        int row = nWall ? 0 : (sWall ? 2 : 1);
        return new TileFrame(RUBBLE_COL_ORIGIN + col, RUBBLE_ROW_ORIGIN + row);
    }

    /**
     * Returns the road tile (from {@link #ROAD_SHEET}) for a street cell given
     * which cardinal neighbors are walls. Same hollow-perimeter shape as the
     * wall picker — returns {@code null} for the open-road case (no wall
     * neighbors). The caller paints a solid {@link #ROAD_FILL_RGB} quad for
     * the null case because the source 3×3's center cell is transparent.
     *
     * <p>Out-of-bounds is treated as <em>not</em> a wall here (matches floor
     * picker semantics) so a road at the map edge stays open instead of
     * picking up an edge marking against nothing.
     */
    public static TileFrame pickRoadTile(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        if (!nWall && !sWall && !eWall && !wWall) return null;

        // Same convention as pickFloorTile: the decorated edge of the road
        // faces the wall it abuts. Inverted from pickWallTile, which orients
        // its edges outward toward the open side.
        int col = wWall ? 0 : (eWall ? 2 : 1);
        int row = nWall ? 0 : (sWall ? 2 : 1);

        return new TileFrame(ROAD_COL_ORIGIN + col, ROAD_ROW_ORIGIN + row);
    }

    /**
     * Courtyard counterpart to {@link #pickRoadTile} — same hollow-perimeter
     * shape on the dark steel autotile. Returns {@code null} for the open-
     * courtyard case (no wall neighbors) — caller paints a solid
     * {@link #COURTYARD_FILL_RGB} quad because the source center is transparent.
     *
     * <p>The "wall" inputs here include any non-walkable cell that bounds the
     * courtyard — typically the perimeter of a super-block's member buildings.
     * Out-of-bounds is treated as <em>not</em> a wall (same as floor/road).
     */
    public static TileFrame pickCourtyardTile(boolean nWall, boolean sWall, boolean eWall, boolean wWall) {
        if (!nWall && !sWall && !eWall && !wWall) return null;
        int col = wWall ? 0 : (eWall ? 2 : 1);
        int row = nWall ? 0 : (sWall ? 2 : 1);
        return new TileFrame(COURTYARD_COL_ORIGIN + col, COURTYARD_ROW_ORIGIN + row);
    }

    // -----------------------------------------------------------------------
    // Floors_Tiles autotile origins. The grass / stone / dirt blocks each
    // occupy a 5x5 region with corners on the inner cells and edges on the
    // outer cells, "facing-outward" layout (a tile on the top row of the
    // block has its decorated edge on the SOUTH side, i.e. that cell has no
    // same-kind neighbor below). Snow / sand use a more conventional 3x3
    // layout centered in their region. Pickers below resolve all 9 logical
    // cases to explicit PNG (col, row) coords.
    // -----------------------------------------------------------------------

    /** Grass block top-left on {@link #FLOORS_SHEET}. Edge tiles span rows 5..9, cols 0..4; center variants at row 10 cols 1..3. */
    private static final int GRASS_COL_ORIGIN = 0;
    private static final int GRASS_ROW_ORIGIN = 5;
    private static final int STONE_COL_ORIGIN = 5;
    private static final int STONE_ROW_ORIGIN = 5;
    private static final int DIRT_COL_ORIGIN  = 10;
    private static final int DIRT_ROW_ORIGIN  = 5;

    /** Snow autotile centered around (2, 14). Standard 3x3 layout — edges directly opposite from their named direction. */
    private static final int SNOW_CENTER_COL = 2;
    private static final int SNOW_CENTER_ROW = 14;
    /** Sand autotile centered around (7, 14). */
    private static final int SAND_CENTER_COL = 7;
    private static final int SAND_CENTER_ROW = 14;

    /** Water autotile lives on {@link #WATER_SHEET}; edges arranged on cols 6/10 and rows 0/4 with conventional 3x3 logic. Center variants at row 7. */
    private static final int WATER_EAST_COL  = 6;
    private static final int WATER_WEST_COL  = 10;
    private static final int WATER_NORTH_ROW = 4;
    private static final int WATER_SOUTH_ROW = 0;
    private static final int WATER_CENTER_ROW = 7;

    /**
     * Returns the grass autotile cell for the 9-case neighbor mask.
     * "Wall" inputs here are negated — they represent "neighbor is NOT
     * grass" (i.e., the edge faces that direction). When all four neighbors
     * are grass, picks one of 3 center variants by {@code (x, y)} hash.
     */
    public static TileFrame pickGrassTile(boolean nNotKind, boolean sNotKind, boolean eNotKind, boolean wNotKind, int x, int y) {
        return pickFloorsAutotile(GRASS_COL_ORIGIN, GRASS_ROW_ORIGIN, nNotKind, sNotKind, eNotKind, wNotKind, x, y);
    }

    /** Stone counterpart to {@link #pickGrassTile}. Same 5x5 block layout, offset on the sheet. */
    public static TileFrame pickStoneTile(boolean nNotKind, boolean sNotKind, boolean eNotKind, boolean wNotKind, int x, int y) {
        return pickFloorsAutotile(STONE_COL_ORIGIN, STONE_ROW_ORIGIN, nNotKind, sNotKind, eNotKind, wNotKind, x, y);
    }

    /** Dirt counterpart to {@link #pickGrassTile}. */
    public static TileFrame pickDirtTile(boolean nNotKind, boolean sNotKind, boolean eNotKind, boolean wNotKind, int x, int y) {
        return pickFloorsAutotile(DIRT_COL_ORIGIN, DIRT_ROW_ORIGIN, nNotKind, sNotKind, eNotKind, wNotKind, x, y);
    }

    /**
     * Two-variant grass pool from {@code nature-tiles.png}, hash-picked by
     * cell coordinate. Returns a {@link com.dillon.starsectormarines.battle.sprites.NatureTile}
     * — the sliced-sheet picker, not the Floors_Tiles autotile path —
     * because the nature-tile art reads better as ground for parks / open
     * space than the legacy Floors_Tiles grass blob. Render via the
     * sliced-sheet draw path ({@code drawNatureTile} in BattleScreen).
     *
     * <p>Center-variant only: the nature-tile sheet has no edge frames so
     * per-kind edges between e.g. grass and dirt show a hard cell boundary
     * (matches the flat-edges-between-kinds convention).
     */
    public static com.dillon.starsectormarines.battle.sprites.NatureTile pickNatureGrassTile(int x, int y) {
        return (stableHash(x, y) & 1) == 0
                ? com.dillon.starsectormarines.battle.sprites.NatureTile.GRASS_1
                : com.dillon.starsectormarines.battle.sprites.NatureTile.GRASS_2;
    }

    /** Two-variant dirt pool from {@code nature-tiles.png}, hash-picked by cell coordinate. See {@link #pickNatureGrassTile} for rationale. */
    public static com.dillon.starsectormarines.battle.sprites.NatureTile pickNatureDirtTile(int x, int y) {
        return (stableHash(x, y) & 1) == 0
                ? com.dillon.starsectormarines.battle.sprites.NatureTile.DIRT_1
                : com.dillon.starsectormarines.battle.sprites.NatureTile.DIRT_2;
    }

    /**
     * Shared resolver for the grass / stone / dirt 5x5 blocks. The blocks
     * are laid out "facing-outward":
     * <pre>
     *               W not-kind   middle      E not-kind
     * N not-kind:   (+4, +3)     (+2, +4)    (+0, +3)
     * middle:       (+4, +2)     center      (+0, +2)
     * S not-kind:   (+3, +0)     (+2, +0)    (+1, +0)
     * </pre>
     * Center variants live at row+5 cols+1..3 (3 variants picked by hash).
     */
    private static TileFrame pickFloorsAutotile(int originCol, int originRow,
                                                boolean nNot, boolean sNot, boolean eNot, boolean wNot,
                                                int x, int y) {
        // No neighbors are different — pick a center variant by stable hash.
        if (!nNot && !sNot && !eNot && !wNot) {
            int variant = stableHash(x, y) % 3;
            return new TileFrame(originCol + 1 + variant, originRow + 5);
        }
        int dCol, dRow;
        if (nNot && wNot)        { dCol = 4; dRow = 3; }   // NW corner — N+W not kind
        else if (nNot && eNot)   { dCol = 0; dRow = 3; }   // NE corner
        else if (sNot && wNot)   { dCol = 3; dRow = 0; }   // SW corner
        else if (sNot && eNot)   { dCol = 1; dRow = 0; }   // SE corner
        else if (nNot)           { dCol = 2; dRow = 4; }   // N edge
        else if (sNot)           { dCol = 2; dRow = 0; }   // S edge
        else if (wNot)           { dCol = 4; dRow = 2; }   // W edge
        else /* eNot */          { dCol = 0; dRow = 2; }   // E edge
        return new TileFrame(originCol + dCol, originRow + dRow);
    }

    /**
     * Snow autotile — conventional 3x3 layout drawn around {@link #SNOW_CENTER_COL},
     * {@link #SNOW_CENTER_ROW}. Edges sit one cell away from center in the
     * named direction. Center variants live on the center row at cols 1..3.
     */
    public static TileFrame pickSnowTile(boolean nNot, boolean sNot, boolean eNot, boolean wNot, int x, int y) {
        return pickStandard3x3(SNOW_CENTER_COL, SNOW_CENTER_ROW, nNot, sNot, eNot, wNot, x, y, /*centerVariants=*/3);
    }

    /** Sand counterpart to {@link #pickSnowTile}. */
    public static TileFrame pickSandTile(boolean nNot, boolean sNot, boolean eNot, boolean wNot, int x, int y) {
        return pickStandard3x3(SAND_CENTER_COL, SAND_CENTER_ROW, nNot, sNot, eNot, wNot, x, y, /*centerVariants=*/3);
    }

    /**
     * Standard 3x3 autotile resolver. Edges are at center ± 1 in the named
     * direction; corners at the diagonals; "no-different-neighbors" picks
     * from {@code centerVariants} center cells (always taken from the center
     * row, cols starting at centerCol - 1).
     */
    private static TileFrame pickStandard3x3(int centerCol, int centerRow,
                                             boolean nNot, boolean sNot, boolean eNot, boolean wNot,
                                             int x, int y, int centerVariants) {
        if (!nNot && !sNot && !eNot && !wNot) {
            int variant = (centerVariants <= 1) ? 0 : (stableHash(x, y) % centerVariants);
            return new TileFrame(centerCol - 1 + variant, centerRow);
        }
        int dCol, dRow;
        // Image-space rows: y grows downward, so "north" = row - 1.
        if (nNot && wNot)        { dCol = -1; dRow = -1; }
        else if (nNot && eNot)   { dCol = +1; dRow = -1; }
        else if (sNot && wNot)   { dCol = -1; dRow = +1; }
        else if (sNot && eNot)   { dCol = +1; dRow = +1; }
        else if (nNot)           { dCol = 0;  dRow = -2; }   // top edge sits 2 rows above center for snow/sand
        else if (sNot)           { dCol = 0;  dRow = +2; }
        else if (wNot)           { dCol = -2; dRow = 0; }
        else /* eNot */          { dCol = +2; dRow = 0; }
        return new TileFrame(centerCol + dCol, centerRow + dRow);
    }

    /**
     * Water autotile (Water_tiles sheet). Same outward-facing layout as the
     * grass block but on a different sheet: vertical edges live at cols
     * {@link #WATER_EAST_COL} (E-not-water tiles) and {@link #WATER_WEST_COL}
     * (W-not-water); horizontal edges at row {@link #WATER_NORTH_ROW}
     * (N-not-water) and {@link #WATER_SOUTH_ROW}. Center variants on
     * {@link #WATER_CENTER_ROW} cols 6..8.
     */
    public static TileFrame pickWaterTile(boolean nNot, boolean sNot, boolean eNot, boolean wNot, int x, int y) {
        if (!nNot && !sNot && !eNot && !wNot) {
            int variant = stableHash(x, y) % 3;
            return new TileFrame(6 + variant, WATER_CENTER_ROW);
        }
        int col, row;
        if (nNot && wNot)        { col = WATER_WEST_COL; row = 3; }
        else if (nNot && eNot)   { col = WATER_EAST_COL; row = 3; }
        else if (sNot && wNot)   { col = WATER_WEST_COL; row = 1; }
        else if (sNot && eNot)   { col = WATER_EAST_COL; row = 1; }
        else if (nNot)           { col = 8;              row = WATER_NORTH_ROW; }
        else if (sNot)           { col = 8;              row = WATER_SOUTH_ROW; }
        else if (wNot)           { col = WATER_WEST_COL; row = 2; }
        else /* eNot */          { col = WATER_EAST_COL; row = 2; }
        return new TileFrame(col, row);
    }

    /**
     * Single-cell commercial floor on {@link #ROAD_SHEET} — fl-2 at (11, 0).
     * Used uniformly across {@link com.dillon.starsectormarines.battle.map.CellTopology.GroundKind#TILE}
     * cells, no variant pool (matches how {@code fl} blankets INDOOR interiors).
     * Reads as "polished commercial panel" — squared corner markers, clean
     * mid-cell field.
     */
    private static final TileFrame FL_TILE = new TileFrame(11, 0); // fl-2 on ROAD_SHEET

    /**
     * Returns the polished commercial floor tile. Stable across cells —
     * no per-cell variation — so a commercial building reads as a single
     * uniform floor surface, same model as INDOOR's {@code fl} fill. Lives
     * on {@link #ROAD_SHEET} (32px source cells); callers dispatch through
     * the road-sheet draw path.
     */
    public static TileFrame pickTileGroundTile(int x, int y) {
        return FL_TILE;
    }

    /**
     * Five brick-paver variants (fl-tile-1..5) on {@link #FLOORS_SHEET}.
     * Brown brick that reads as a large uniform paved surface — plaza centers
     * and building roofs (planned). Coords from
     * {@code mod/data/tilesets/Floors_Tiles.catalog.json}; no edge pieces,
     * every cell is a "center" tile and the per-cell hash gives noise variation.
     */
    private static final TileFrame[] FL_BRICK_VARIANTS = {
            new TileFrame(17, 1), // fl-tile-1
            new TileFrame(16, 2), // fl-tile-2
            new TileFrame(17, 2), // fl-tile-3
            new TileFrame(18, 2), // fl-tile-4
            new TileFrame(17, 3), // fl-tile-5
    };

    /**
     * Picks one of the {@link #FL_BRICK_VARIANTS} by stable per-cell hash.
     * Lives on {@link #FLOORS_SHEET} (16px source cells); callers must
     * dispatch through the floors-sheet draw path.
     */
    public static TileFrame pickBrickTile(int x, int y) {
        return FL_BRICK_VARIANTS[stableHash(x, y) % FL_BRICK_VARIANTS.length];
    }

    /**
     * Picks between {@link com.dillon.starsectormarines.battle.sprites.UrbanTile3#SIDEWALK}
     * and {@link com.dillon.starsectormarines.battle.sprites.UrbanTile3#SIDEWALK_CORNER}
     * for a sidewalk cell on the {@link #STREET3_SHEET}. A cell counts as a
     * corner when two perpendicular cardinal neighbors are <em>not</em>
     * sidewalk (i.e. the sidewalk strip bends here — a building wall on one
     * side and the road or another non-sidewalk surface on a perpendicular
     * side). Straight runs (one non-sidewalk neighbor) get the plain
     * variant.
     *
     * <p>OOB is treated as "not sidewalk" so a sidewalk strip flush against
     * the map edge picks up a corner at the end rather than rolling off
     * into nothing.
     *
     * @param nNotSidewalk  true if the north neighbor is not a sidewalk cell (wall, road, OOB, etc.)
     * @param sNotSidewalk  true if the south neighbor is not a sidewalk cell
     * @param eNotSidewalk  true if the east neighbor is not a sidewalk cell
     * @param wNotSidewalk  true if the west neighbor is not a sidewalk cell
     */
    public static com.dillon.starsectormarines.battle.sprites.UrbanTile3 pickStreet3SidewalkFrame(
            boolean nNotSidewalk, boolean sNotSidewalk,
            boolean eNotSidewalk, boolean wNotSidewalk) {
        boolean nwBend = nNotSidewalk && wNotSidewalk;
        boolean neBend = nNotSidewalk && eNotSidewalk;
        boolean swBend = sNotSidewalk && wNotSidewalk;
        boolean seBend = sNotSidewalk && eNotSidewalk;
        if (nwBend || neBend || swBend || seBend) {
            return com.dillon.starsectormarines.battle.sprites.UrbanTile3.SIDEWALK_CORNER;
        }
        return com.dillon.starsectormarines.battle.sprites.UrbanTile3.SIDEWALK;
    }

    /**
     * Yellow-striped factory floor autotile on {@link #ROAD_SHEET}, cols 6..8
     * rows 0..2 — standard 3x3 (named direction = side the edge is drawn on
     * = neighbor on that side is not striped). Center cell (7, 1) is empty on
     * the sheet, so the open case falls back to fl-striped-s for now.
     */
    public static TileFrame pickStripedTile(boolean nNot, boolean sNot, boolean eNot, boolean wNot) {
        int col, row;
        if (!nNot && !sNot && !eNot && !wNot) {
            // No dedicated striped center variant on the sheet — pick the
            // south-edge piece as a stand-in until art adds a proper center.
            return new TileFrame(7, 2);
        }
        if (nNot && wNot)        { col = 6; row = 0; }
        else if (nNot && eNot)   { col = 8; row = 0; }
        else if (sNot && wNot)   { col = 6; row = 2; }
        else if (sNot && eNot)   { col = 8; row = 2; }
        else if (nNot)           { col = 7; row = 0; }
        else if (sNot)           { col = 7; row = 2; }
        else if (wNot)           { col = 6; row = 1; }
        else /* eNot */          { col = 8; row = 1; }
        return new TileFrame(col, row);
    }

    /**
     * Landing-zone marker decal — points at the grate-2 tile on
     * {@link #ROAD_SHEET}. Same coords as the existing {@link #LZ_PAD}
     * placeholder; exposed as its own picker so the renderer can dispatch
     * uniformly on {@code GroundKind.LZ_MARKER}. Replace once real LZ art
     * lands.
     */
    public static TileFrame pickLzMarkerTile() {
        return new TileFrame(16, 2);
    }

    /** Stable per-cell hash used for picking from variant pools. Same shape as the renderer's cellHash but a static helper here so pickers don't need an RNG. */
    private static int stableHash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        return h & 0x7FFFFFFF;
    }

    private TileManifest() {}

    /** Source-sheet region for a single 1×1 tile. */
    public static final class TileFrame {
        public final int col;
        public final int row;

        public TileFrame(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }
}
