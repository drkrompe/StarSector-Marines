package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

/**
 * Emits the {@link RenderLayer#GROUND} layer — the tiled floor/wall terrain pass.
 * A faithful migration of {@code BattleRenderer.renderGrid} +
 * {@code renderTiledFloorsAndWalls} into the command model.
 *
 * <p><strong>Dense pass.</strong> It walks the whole grid every frame (up to ~38k
 * cells) and emits one pooled {@link com.dillon.starsectormarines.render2d.DrawCommand}
 * per tile/fill — zero steady-state allocation, since {@link DrawList} recycles
 * the slots. Emission is in strict paint order: a full-grid backing fill, then per
 * non-wall cell its base tile, then any nature overlay, then any doorway, then a
 * second pass for wall tiles. The strict-painter drain coalesces consecutive
 * same-sheet tiles into one batch flush, so spatially-coherent terrain (streets,
 * grass regions) batches just as tightly as the old per-sheet-batch pass.
 *
 * <p>Per-cell submission order (base → overlay → doorway) is what guarantees
 * overlays/doorways land on top; the drain never reorders. Crosswalk stripes and
 * the road/courtyard fallback fills are {@code SOLID_RECT}s; everything else is a
 * {@code SHEET_QUAD} on one of the six terrain sheets.
 */
public final class GroundRenderSystem implements RenderSystem {

    // Terrain fill colors (independent of BattleRenderer; sourced from TileManifest / literals).
    private static final Color FLOOR_COLOR     = new Color(0x18, 0x22, 0x30);
    private static final Color WALL_COLOR      = new Color(0x06, 0x0A, 0x10);
    private static final Color ROAD_FILL       = new Color(TileManifest.ROAD_FILL_RGB);
    private static final Color CROSSWALK_STRIPE = new Color(0xE8, 0xE8, 0xD0);

    private static final int CROSSWALK_STRIPE_COUNT = 5;
    private static final float CROSSWALK_STRIPE_FRAC = 0.10f;
    private static final float CROSSWALK_GAP_FRAC    = 0.10f;
    private static final float CROSSWALK_ALPHA       = 0.85f;
    private static final float CROSSWALK_INSET_FRAC  = 0.08f;

    private static final int GROUND_TILE_EDGE_INSET_PX       = FixedGridTileDrawer.GROUND_INSET_PX_LARGE;
    private static final int GROUND_SMALL_TILE_EDGE_INSET_PX = FixedGridTileDrawer.GROUND_INSET_PX_SMALL;

    private final BattleSprites sprites;

    // Per-collect scratch (single-threaded; overwritten each frame).
    private DrawList out;
    private BattleCamera cam;
    private float alpha;
    private SpriteAPI urban, road, floors, water, urbanTile3, nature;
    private SpriteSheetFrames urbanTile3Frames, natureFrames;
    private TileRegistry tileReg;
    private GenMappingRegistry genMapping;

    public GroundRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.GROUND;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        this.out = out;
        this.cam = ctx.camera;
        this.alpha = ctx.alphaMult;
        this.urban = sprites.tileSheet();
        this.road = sprites.roadSheet();
        this.floors = sprites.floorsSheet();
        this.water = sprites.waterSheet();
        this.urbanTile3 = sprites.urbanTile3Sheet();
        this.nature = sprites.natureSheet();
        this.urbanTile3Frames = sprites.urbanTile3Frames();
        this.natureFrames = sprites.natureFrames();
        this.tileReg = TileRegistry.installed();
        this.genMapping = GenMappingRegistry.installed();

        NavigationGrid grid = ctx.sim.getGrid();
        CellTopology topology = ctx.sim.getTopology();

        // Full-grid backing fill — under everything (matches renderGrid's backing quad).
        float wx0 = cam.cellToScreenX(0);
        float wy0 = cam.cellToScreenY(0);
        float wx1 = cam.cellToScreenX(grid.getWidth());
        float wy1 = cam.cellToScreenY(grid.getHeight());
        fillRect(wx0, wy0, wx1, wy1, FLOOR_COLOR);

        if (urban == null) {
            // No tile sheet: solid-fill non-walkable cells (renderGrid's fallback branch).
            float cellPx = cam.cellPxSize();
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) {
                    if (grid.isWalkable(x, y)) continue;
                    float x0 = cam.cellToScreenX(x);
                    float y0 = cam.cellToScreenY(y);
                    fillRect(x0, y0, x0 + cellPx, y0 + cellPx, WALL_COLOR);
                }
            }
            return;
        }

        emitFloors(grid, topology);
        emitWalls(grid, topology);
    }

    // ---- floor + overlay pass ------------------------------------------------

    private void emitFloors(NavigationGrid grid, CellTopology topology) {
        // Resolve the data-driven GroundKind -> render-block mapping once per
        // pass (GenMappingRegistry.groundBlockId). Each kind's block carries its
        // own resolver (autotile layout / variant pool / single) + sheet + cellPx,
        // so the generic drawGroundBlock path handles every "regular" kind. STREET
        // maps to a sliced urban3 TILE id (not a block), so tileReg.block() is null
        // and it falls through to its special case; SIDEWALK/SNOW are unmapped.
        CellTopology.GroundKind[] kinds = CellTopology.GroundKind.values();
        GridBlockDef[] kindBlock = new GridBlockDef[kinds.length];
        SpriteAPI[] kindSheet = new SpriteAPI[kinds.length];
        Color[] kindFill = new Color[kinds.length];
        for (CellTopology.GroundKind k : kinds) {
            String id = (genMapping == null || tileReg == null) ? null : genMapping.groundBlockId(k);
            if (id == null) continue;
            GridBlockDef b = tileReg.block(id);
            if (b == null) continue; // a sliced-tile mapping (e.g. STREET) — special-cased below
            kindBlock[k.ordinal()] = b;
            kindSheet[k.ordinal()] = sheetFor(b.sheetPath);
            if (b.fillRgb != null) kindFill[k.ordinal()] = new Color(b.fillRgb);
        }

        // STREET's road-sheet fallback (urban3 not loaded) paints road.road's open fill.
        Color roadFill = blockFill("road.road", ROAD_FILL);
        String streetTileId = (genMapping == null) ? "urban3.street-square"
                : genMapping.groundBlockId(CellTopology.GroundKind.STREET);

        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (topology.isWall(x, y)) continue;
                boolean nWall = isInBoundsWall(topology, x, y + 1);
                boolean sWall = isInBoundsWall(topology, x, y - 1);
                boolean eWall = isInBoundsWall(topology, x + 1, y);
                boolean wWall = isInBoundsWall(topology, x - 1, y);

                CellTopology.GroundKind kind = topology.getGroundKind(x, y);
                int ord = kind.ordinal();
                switch (kind) {
                    case STREET:
                        if (urbanTile3 != null) {
                            if (isSidewalkCell(grid, topology, x, y)) {
                                if (tileReg != null) urbanTile3Frame(tileReg.tile(TileManifest.pickStreet3SidewalkFrame(
                                        !isSidewalkLikeCell(grid, topology, x, y + 1),
                                        !isSidewalkLikeCell(grid, topology, x, y - 1),
                                        !isSidewalkLikeCell(grid, topology, x + 1, y),
                                        !isSidewalkLikeCell(grid, topology, x - 1, y))), x, y);
                            } else {
                                if (tileReg != null) urbanTile3Frame(tileReg.tile(streetTileId), x, y);
                                if (topology.isCrosswalk(x, y)) {
                                    crosswalkStripes(x, y, topology.isCrosswalkStripesHorizontal(x, y));
                                }
                            }
                        } else if (road != null && tileReg != null) {
                            if (isSidewalkCell(grid, topology, x, y)) {
                                roadTile(blockFrame("road.sidewalk", false, false, false, false), x, y, GROUND_TILE_EDGE_INSET_PX);
                            } else {
                                roadPerimeter("road.road", roadFill,
                                        isRoadBoundary(grid, topology, x, y + 1),
                                        isRoadBoundary(grid, topology, x, y - 1),
                                        isRoadBoundary(grid, topology, x + 1, y),
                                        isRoadBoundary(grid, topology, x - 1, y), x, y);
                                if (topology.isCrosswalk(x, y)) {
                                    crosswalkStripes(x, y, topology.isCrosswalkStripesHorizontal(x, y));
                                }
                            }
                        }
                        break;
                    case SIDEWALK:
                        if (tileReg != null) urbanTile3Frame(tileReg.tile(TileManifest.pickStreet3SidewalkFrame(
                                !isSidewalkLikeCell(grid, topology, x, y + 1),
                                !isSidewalkLikeCell(grid, topology, x, y - 1),
                                !isSidewalkLikeCell(grid, topology, x + 1, y),
                                !isSidewalkLikeCell(grid, topology, x - 1, y))), x, y);
                        break;
                    case GRASS:
                    case DIRT:
                        // Prefer the sliced nature sheet; the Floors variant block is the fallback.
                        if (nature != null && tileReg != null) {
                            natureTile(tileReg.tile(kind == CellTopology.GroundKind.GRASS
                                    ? TileManifest.pickNatureGrassTileId(x, y)
                                    : TileManifest.pickNatureDirtTileId(x, y)), x, y);
                        } else {
                            drawGroundBlock(kindBlock[ord], kindSheet[ord], kindFill[ord], nWall, sWall, eWall, wWall, x, y);
                        }
                        break;
                    case SNOW:
                        break; // defined in GroundKind but no generator emits it (dead)
                    default:
                        // INDOOR/RUBBLE/COURTYARD/TILE/STRIPED/LZ_MARKER/STONE/SAND/WATER/BRICK:
                        // generic resolve+draw from the kind's mapped block.
                        drawGroundBlock(kindBlock[ord], kindSheet[ord], kindFill[ord], nWall, sWall, eWall, wWall, x, y);
                        break;
                }

                int oi = topology.getNatureOverlayIndex(x, y);
                if (oi >= 0 && tileReg != null) natureTile(tileReg.byIndex(oi), x, y);

                if (grid.isDoorway(x, y) && !topology.isRubble(x, y) && tileReg != null) {
                    urbanTile(blockFrame("urban.door-open", false, false, false, false), x, y, 0);
                }
            }
        }
    }

    // ---- wall pass -----------------------------------------------------------

    private void emitWalls(NavigationGrid grid, CellTopology topology) {
        // Fill color for the enclosed (no-frame) wall cell comes from the
        // urban.wall block's fillRgb — data-driven, falling back to WALL_COLOR.
        GridBlockDef wallBlock = (tileReg == null) ? null : tileReg.block("urban.wall");
        Color wallFill = (wallBlock != null && wallBlock.fillRgb != null) ? new Color(wallBlock.fillRgb) : WALL_COLOR;
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!topology.isWall(x, y)) continue;
                TileManifest.TileFrame tile = WallMasks.pickTileFromMask(topology.getWallDirMask(x, y));
                if (tile == null) fillCell(x, y, wallFill);
                else urbanTile(tile, x, y, 0);
            }
        }
    }

    /**
     * Resolves a fixed-grid block (caller ensures {@code tileReg != null}) to
     * its {@link TileManifest.TileFrame} source cell for the given wall-neighbor
     * mask; {@code null} is the block's enclosed/fill case (only WALL_3X3
     * returns it — the floor/rubble/door blocks never do).
     */
    private TileManifest.TileFrame blockFrame(String blockId, boolean n, boolean s, boolean e, boolean w) {
        int[] c = tileReg.block(blockId).resolve(n, s, e, w);
        return c == null ? null : new TileManifest.TileFrame(c[0], c[1]);
    }

    /**
     * Fill color for a perimeter block's open (null-resolve) case, from its
     * {@code fillRgb} ({@code fallback} when unset / no registry). Resolve once
     * per pass — avoids per-cell {@link Color} allocation in the dense loop.
     */
    private Color blockFill(String blockId, Color fallback) {
        GridBlockDef b = (tileReg == null) ? null : tileReg.block(blockId);
        return (b != null && b.fillRgb != null) ? new Color(b.fillRgb) : fallback;
    }

    /** Draw a road-sheet perimeter block (caller ensures {@code tileReg != null}); the open (null) case paints {@code fill}. */
    private void roadPerimeter(String blockId, Color fill, boolean n, boolean s, boolean e, boolean w, int gridX, int gridY) {
        int[] c = tileReg.block(blockId).resolve(n, s, e, w);
        if (c == null) fillCell(gridX, gridY, fill);
        else roadTile(new TileManifest.TileFrame(c[0], c[1]), gridX, gridY, GROUND_TILE_EDGE_INSET_PX);
    }

    // ---- tile emitters (port of BattleRenderer's draw* helpers) --------------

    private void urbanTile(TileManifest.TileFrame f, int gridX, int gridY, int inset) {
        if (urban == null || f == null) return;
        emitCellPx(urban, TileManifest.TILE_SIZE, f.col, f.row, inset, gridX, gridY);
    }

    private void roadTile(TileManifest.TileFrame f, int gridX, int gridY, int inset) {
        if (road == null || f == null) return;
        emitCellPx(road, TileManifest.TILE_SIZE, f.col, f.row, inset, gridX, gridY);
    }

    /** The loaded sheet for a tileset block's {@code sheetPath}, or {@code null} if that sheet isn't loaded (sliced urban3/nature sheets use their own frame paths, not this). */
    private SpriteAPI sheetFor(String sheetPath) {
        switch (sheetPath) {
            case TileManifest.SHEET:        return urban;
            case TileManifest.ROAD_SHEET:   return road;
            case TileManifest.FLOORS_SHEET: return floors;
            case TileManifest.WATER_SHEET:  return water;
            default:                        return null;
        }
    }

    /**
     * Generic data-driven ground draw: resolves {@code b} for this cell
     * ({@link GridBlockDef#resolve} dispatches on the block's own type — autotile
     * wall-mask, variant-pool {@code (x,y)} hash, or single), then emits it on the
     * block's {@code sheet} at its {@code cellPx} + matching inset. The enclosed/
     * open ({@code null}) case paints {@code fillIfNull} (a perimeter block's
     * hoisted {@code fillRgb}).
     */
    private void drawGroundBlock(GridBlockDef b, SpriteAPI sheet, Color fillIfNull,
                                 boolean n, boolean s, boolean e, boolean w, int x, int y) {
        if (b == null || sheet == null) return;
        int[] c = b.resolve(n, s, e, w, x, y);
        if (c == null) {
            if (fillIfNull != null) fillCell(x, y, fillIfNull);
            return;
        }
        int inset = (b.cellPx >= TileManifest.TILE_SIZE) ? GROUND_TILE_EDGE_INSET_PX : GROUND_SMALL_TILE_EDGE_INSET_PX;
        emitCellPx(sheet, b.cellPx, c[0], c[1], inset, x, y);
    }

    /** Source rect for a {@code cellPx}-grid sheet (32px urban/road, 16px floors/water): col/row * cellPx, inset, cell-center dst. */
    private void emitCellPx(SpriteAPI sheet, int cellPx, int col, int row, int inset, int gridX, int gridY) {
        int srcX = col * cellPx + inset;
        int srcY = row * cellPx + inset;
        int srcW = cellPx - 2 * inset;
        int srcH = cellPx - 2 * inset;
        emitSheetCell(sheet, srcX, srcY, srcW, srcH, gridX, gridY);
    }

    private void urbanTile3Frame(TileDef frame, int gridX, int gridY) {
        if (urbanTile3 == null || urbanTile3Frames == null || frame == null) return;
        int idx = frame.frame;
        if (idx < 0 || idx >= urbanTile3Frames.frames.length) return;
        emitFrame(urbanTile3, urbanTile3Frames.frames[idx], frame.isGround(), gridX, gridY);
    }

    private void natureTile(TileDef tile, int gridX, int gridY) {
        if (nature == null || natureFrames == null || tile == null) return;
        int idx = tile.frame;
        if (idx < 0 || idx >= natureFrames.frames.length) return;
        emitFrame(nature, natureFrames.frames[idx], tile.isGround(), gridX, gridY);
    }

    /** Packed-frame sheet (urbanTile3 / nature): explicit frame rect, ground frames inset. */
    private void emitFrame(SpriteAPI sheet, SpriteSheetFrames.Frame f, boolean ground, int gridX, int gridY) {
        int inset = ground ? GROUND_TILE_EDGE_INSET_PX : 0;
        int srcX = f.x + inset;
        int srcY = f.y + inset;
        int srcW = Math.max(1, f.w - 2 * inset);
        int srcH = Math.max(1, f.h - 2 * inset);
        emitSheetCell(sheet, srcX, srcY, srcW, srcH, gridX, gridY);
    }

    private void emitSheetCell(SpriteAPI sheet, int srcX, int srcY, int srcW, int srcH, int gridX, int gridY) {
        float cellPx = cam.cellPxSize();
        float cx = cam.cellToScreenX(gridX + 0.5f);
        float cy = cam.cellToScreenY(gridY + 0.5f);
        out.addSheetQuad(RenderLayer.GROUND, sheet, srcX, srcY, srcW, srcH,
                cx, cy, cellPx, cellPx, 1f, 1f, 1f, alpha);
    }

    // ---- solid fills ---------------------------------------------------------

    private void fillCell(int gridX, int gridY, Color color) {
        float x0 = cam.cellToScreenX(gridX);
        float y0 = cam.cellToScreenY(gridY);
        float c = cam.cellPxSize();
        fillRect(x0, y0, x0 + c, y0 + c, color);
    }

    private void fillRect(float x0, float y0, float x1, float y1, Color color) {
        out.addSolidRect(RenderLayer.GROUND, x0, y0, x1, y1,
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha);
    }

    private void crosswalkStripes(int gridX, int gridY, boolean stripesHorizontal) {
        float cell = cam.cellPxSize();
        float x0 = cam.cellToScreenX(gridX);
        float y0 = cam.cellToScreenY(gridY);
        float stripeW = cell * CROSSWALK_STRIPE_FRAC;
        float gapW    = cell * CROSSWALK_GAP_FRAC;
        float bandSpan = CROSSWALK_STRIPE_COUNT * stripeW + (CROSSWALK_STRIPE_COUNT - 1) * gapW;
        float marginAlong = (cell - bandSpan) / 2f;
        float perpInset = cell * CROSSWALK_INSET_FRAC;
        float a = CROSSWALK_ALPHA * alpha;
        float sr = CROSSWALK_STRIPE.getRed()   / 255f;
        float sg = CROSSWALK_STRIPE.getGreen() / 255f;
        float sb = CROSSWALK_STRIPE.getBlue()  / 255f;
        for (int i = 0; i < CROSSWALK_STRIPE_COUNT; i++) {
            float bandStart = marginAlong + i * (stripeW + gapW);
            float rx, ry, rw, rh;
            if (stripesHorizontal) {
                rx = x0 + perpInset; ry = y0 + bandStart;
                rw = cell - 2 * perpInset; rh = stripeW;
            } else {
                rx = x0 + bandStart; ry = y0 + perpInset;
                rw = stripeW; rh = cell - 2 * perpInset;
            }
            out.addSolidRect(RenderLayer.GROUND, rx, ry, rx + rw, ry + rh, sr, sg, sb, a);
        }
    }

    // ---- neighbor predicates (ported verbatim) -------------------------------

    private static boolean isInBoundsWall(CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && topology.isWall(x, y);
    }

    private static boolean isSidewalkCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || !topology.isStreet(x, y)) return false;
        return isInBoundsWall(topology, x + 1, y)
                || isInBoundsWall(topology, x - 1, y)
                || isInBoundsWall(topology, x, y + 1)
                || isInBoundsWall(topology, x, y - 1);
    }

    private static boolean isSidewalkLikeCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!topology.inBounds(x, y)) return false;
        if (topology.getGroundKind(x, y) == CellTopology.GroundKind.SIDEWALK) return true;
        return isSidewalkCell(grid, topology, x, y);
    }

    private static boolean isRoadBoundary(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        if (topology.isWall(x, y)) return true;
        return isSidewalkCell(grid, topology, x, y);
    }
}
