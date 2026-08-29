package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * Compatibility vocabulary for battle tile rendering that has not yet moved
 * behind the data registries. The authoritative built-in tile, block, and prop
 * catalog lives in {@code TileRegistry}; this class retains source-sheet
 * constants and {@link TileFrame} for older rendering/model boundaries, plus
 * code-owned topology helpers and explicit no-registry fallbacks.
 *
 * <p>The grass/dirt helpers still hardcode sliced variant-pool membership. That
 * narrow authority exception is tracked by
 * {@code nature-variant-pool-authority-cleanup.md}. See
 * {@code moddable-tilesets-nouns.md} for the standing ownership model.
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
     * 56px source-cell size and downsampled to fit the 32px nav grid. The
     * extra source density keeps outdoor materials crisp under zoom.
     */
    public static final String FLOORS_SHEET = "graphics/tilesets/Floors_Tiles.png";
    public static final int FLOORS_TILE_SIZE = 56;

    /** Fourth sheet — water autotile. Its legacy cells remain 16px and upscale to the nav grid. */
    public static final String WATER_SHEET = "graphics/tilesets/Water_tiles.png";

    /**
     * Fifth sheet — sliced strip carrying the modern road + sidewalk look
     * plus a culvert/bench doodad set. Variable-width frames separated by
     * alpha gutters, indexed by tile ids in the form {@code "urban3.*"} via
     * {@link com.dillon.starsectormarines.battle.world.tiles.TileRegistry}.
     * Loaded as a sliced sheet via
     * {@link com.dillon.starsectormarines.battle.world.tiles.SheetTexture};
     * the renderer dispatches STREET cells through this sheet when it's
     * loaded and falls back to the {@link #ROAD_SHEET} autotile otherwise.
     */
    public static final String STREET3_SHEET = "graphics/tilesets/urban-tileset-3.png";

    /**
     * Sixth sheet — the nature strip (grass / dirt / sand / water ground
     * variants plus plant / rock overlay frames). Variable-width frames
     * separated by alpha gutters, indexed by {@code "nature.*"} ids via
     * {@link com.dillon.starsectormarines.battle.world.tiles.TileRegistry};
     * loaded as a sliced sheet via
     * {@link com.dillon.starsectormarines.battle.world.tiles.SheetTexture}.
     */
    public static final String NATURE_SHEET = "graphics/tilesets/nature-tiles.png";

    /** Dedicated fixed-grid sheet for generated 32px doodad cutouts. */
    public static final String DOODAD_SHEET = "graphics/doodads/doodads.png";

    /**
     * Packed atlas of parked road vehicles — the trucks and vans scattered on
     * streets and courtyards at generation. Its own sheet rather than a corner
     * of {@link #DOODAD_SHEET} because these are drawn several times finer than
     * the prop atlas, and sampling them down to it would throw the detail away.
     */
    public static final String PARKED_VEHICLE_SHEET = "graphics/tilesets/parked-vehicles.png";

    /** Open-road surface color, sampled at the center pixel of the road autotile center cell (13, 1). Verified by {@code TileManifestFillColorTest}. */
    public static final int ROAD_FILL_RGB = 0x2F3D4A; // 47, 61, 74

    /** Open-courtyard surface color, sampled inside the open-area quadrant of the courtyard NW edge tile. Verified by {@code TileManifestFillColorTest}. */
    public static final int COURTYARD_FILL_RGB = 0x3B4753; // 59, 71, 83

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
    public static TileFrame sidewalk() {
        return single("road.sidewalk", 11, 1);
    }

    /**
     * Landing-zone pad decal stamped under each shuttle's touchdown cell.
     *
     * <p>Placeholder: the current sheet has no dedicated LZ pad art, so we
     * point at cell (16, 2) — labelled {@code grate-2} in the catalog. Visually
     * it's a small grate, not a yellow-striped pad; the sky-port plaza still
     * reads as deliberate because of the surrounding open ground, but the
     * decal itself is off. Replace once a real LZ marker tile gets added.
     */
    public static TileFrame lzPad() {
        return single("road.lz-marker", 16, 2);
    }

    /**
     * Top-left cell of the turret-wall 3×3 autotile block on {@link #ROAD_SHEET}.
     * Same shape as the urban-1 wall block at cols 3..5 rows 0..2 — directional
     * caps on the outside of each cell, transparent center — but the art reads
     * as a sandbag embankment rather than masonry. Used by
     * {@link com.dillon.starsectormarines.battle.world.gen.bsp.DefensePostStamper}
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
    public static TileFrame lightPostVent() {
        return single("road.vent", 11, 2);
    }

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
        return ringCell("road.embankment", relX, relY,
                TURRET_EMBANKMENT_COL_ORIGIN, TURRET_EMBANKMENT_ROW_ORIGIN);
    }

    /**
     * Matching 3×3 to {@link #turretEmbankment}: the {@code road.revetment}
     * block. Chunkier wall art that "bows outward" — used for defense-post
     * shapes that protrude into the kill zone (WEDGE, TRAPEZOID) so the
     * earthwork reads as heavier than the thinner ring used for straight LINE
     * emplacements. Same {@code relX, relY} convention as
     * {@link #turretEmbankment}.
     *
     * <p>This was {@code road.courtyard} until 2026-08-29, when the two were
     * split. One block was serving as a ground surface and as defensive cover
     * at once, so redrawing a courtyard as flatter paving would have quietly
     * changed what a turret hides behind. They share their art today and are
     * free to stop.
     */
    private static final int TURRET_BOW_COL_ORIGIN = 0;
    private static final int TURRET_BOW_ROW_ORIGIN = 0;

    public static TileFrame turretBowOut(int relX, int relY) {
        return ringCell("road.revetment", relX, relY,
                TURRET_BOW_COL_ORIGIN, TURRET_BOW_ROW_ORIGIN);
    }

    /**
     * One cell of a 3x3 ring block, chosen by where the cell sits relative to
     * the post's centre.
     *
     * <p>{@code relX, relY} are in {@code [-1, +1]}, and {@code relY > 0} means
     * north of the centre. Both are turned into the block layout's own
     * "the exterior is on this side" mask, which is what puts the outward-facing
     * cap on the outward-facing edge.
     *
     * <p>The block is asked for by id. The origin arguments are the fallback
     * this sheet was hand-cut at, for a caller with no registry installed - a
     * preview scene, or a test that stamps a post without the catalog.
     */
    private static TileFrame ringCell(String blockId, int relX, int relY,
                                      int fallbackCol, int fallbackRow) {
        GridBlockDef block = block(blockId);
        if (block == null) {
            return new TileFrame(fallbackCol + (relX + 1), fallbackRow + (1 - relY));
        }
        int[] cell = block.resolve(relY > 0, relY < 0, relX > 0, relX < 0);
        return cell == null
                ? new TileFrame(fallbackCol + (relX + 1), fallbackRow + (1 - relY))
                : new TileFrame(cell[0], cell[1]);
    }

    /**
     * The one cell of a single-cell block, or the coordinate the sheet was
     * hand-cut at when no registry is installed.
     */
    private static TileFrame single(String blockId, int fallbackCol, int fallbackRow) {
        GridBlockDef block = block(blockId);
        if (block == null) return new TileFrame(fallbackCol, fallbackRow);
        int[] cell = block.resolve(false, false, false, false);
        return cell == null ? new TileFrame(fallbackCol, fallbackRow)
                : new TileFrame(cell[0], cell[1]);
    }

    private static GridBlockDef block(String blockId) {
        TileRegistry registry = TileRegistry.installed();
        return registry == null ? null : registry.block(blockId);
    }

    /**
     * Top-left cell of the clean-wall 3×3 autotile block, as the sheet was
     * hand-cut. The authoritative origin is the {@code urban.wall}
     * {@link com.dillon.starsectormarines.battle.world.tiles.GridBlockDef}'s,
     * because the atlas is packed by the tileset exporter and the block is
     * placed wherever the pack put it. This constant only serves
     * {@link #pickWallTile}, which exists for the case where there is no
     * registry to ask.
     */
    private static final int WALL_COL_ORIGIN = 3;
    private static final int WALL_ROW_ORIGIN = 0;

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
     * <p>Same shape as the {@code urban.floor} autotile — the floor's "decoration
     * on the side that touches a wall" rule corresponds to the wall's "decoration
     * on the side that touches an opening." Stranded-wall edge cases (a 1-cell
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
     * Two-variant grass pool from {@code nature-tiles.png}, hash-picked by
     * cell coordinate. Returns the tile id — resolve via
     * {@code TileRegistry.installed().tile(id)} before rendering.
     *
     * <p>Center-variant only: the nature-tile sheet has no edge frames so
     * per-kind edges between e.g. grass and dirt show a hard cell boundary
     * (matches the flat-edges-between-kinds convention).
     */
    public static String pickNatureGrassTileId(int x, int y) {
        return (stableHash(x, y) & 1) == 0 ? "nature.grass-1" : "nature.grass-2";
    }

    /**
     * Two-variant dirt pool from {@code nature-tiles.png}, hash-picked by
     * cell coordinate. Returns the tile id — resolve via
     * {@code TileRegistry.installed().tile(id)} before rendering.
     * See {@link #pickNatureGrassTileId} for rationale.
     */
    public static String pickNatureDirtTileId(int x, int y) {
        return (stableHash(x, y) & 1) == 0 ? "nature.dirt-1" : "nature.dirt-2";
    }

    /**
     * Picks between {@code "urban3.sidewalk"} and {@code "urban3.sidewalk-corner"}
     * for a sidewalk cell on the {@link #STREET3_SHEET}. A cell counts as a
     * corner when two perpendicular cardinal neighbors are <em>not</em>
     * sidewalk (i.e. the sidewalk strip bends here — a building wall on one
     * side and the road or another non-sidewalk surface on a perpendicular
     * side). Straight runs (one non-sidewalk neighbor) get the plain variant.
     *
     * <p>Returns the tile id — resolve via
     * {@code TileRegistry.installed().tile(id)} before rendering.
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
    public static String pickStreet3SidewalkFrame(
            boolean nNotSidewalk, boolean sNotSidewalk,
            boolean eNotSidewalk, boolean wNotSidewalk) {
        boolean nwBend = nNotSidewalk && wNotSidewalk;
        boolean neBend = nNotSidewalk && eNotSidewalk;
        boolean swBend = sNotSidewalk && wNotSidewalk;
        boolean seBend = sNotSidewalk && eNotSidewalk;
        if (nwBend || neBend || swBend || seBend) {
            return "urban3.sidewalk-corner";
        }
        return "urban3.sidewalk";
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
