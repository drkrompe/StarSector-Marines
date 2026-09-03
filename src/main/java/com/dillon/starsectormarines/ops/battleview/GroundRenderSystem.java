package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.SurfaceRole;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

/**
 * Emits the {@link RenderLayer#GROUND} layer — the tiled floor/wall terrain pass.
 *
 * <p><strong>One resolution, two destinations.</strong> A cell's base terrain —
 * the single tile it draws from one of the six sheets, or the solid colour it
 * paints when its block has no frame for it — is resolved by
 * {@link BaseTerrain}, which reports into a {@link GroundMesh.CellSink}. The
 * ordinary path's sink appends a pooled draw command; {@link GroundMesh}'s sink
 * writes four vertices into a resident buffer. There is deliberately one
 * resolver rather than one per destination: the two could not then disagree
 * about what a cell looks like, which is the failure a second copy of this
 * dispatch would eventually produce.
 *
 * <p><strong>Dense pass.</strong> Without the mesh it walks the camera's
 * {@link VisibleCellRect} every frame (the whole grid at zoom 1.0; a slice once
 * zoomed in) and emits one pooled command per tile — zero steady-state
 * allocation, since {@link DrawList} recycles the slots. With the mesh the base
 * terrain is one custom pass and the walk is only over what the mesh does not
 * hold: the fills, and the decorations below.
 *
 * <p><strong>Paint order.</strong> A full-grid backing fill; the base terrain;
 * then the decorations that sit on a cell already drawn — crosswalk stripes,
 * nature scatter, doorway decals — then window panes and shared-edge features.
 * Base terrain is at most one quad per cell and cells do not overlap, so floors
 * and walls may be drawn in either order relative to each other; a decoration
 * must follow the cell it decorates, and does. Crosswalk stripes and the
 * road/courtyard fallback fills are {@code SOLID_RECT}s; everything else is a
 * {@code SHEET_QUAD} on one of the six terrain sheets.
 */
public final class GroundRenderSystem implements RenderSystem {

    // Terrain fill colors (independent of BattleRenderer; sourced from TileManifest / literals).
    private static final Color FLOOR_COLOR     = new Color(0x18, 0x22, 0x30);
    private static final Color WALL_COLOR      = new Color(0x06, 0x0A, 0x10);
    private static final Color WINDOW_FRAME    = new Color(0x08, 0x12, 0x18);
    private static final Color WINDOW_GLASS    = new Color(0x3A, 0x72, 0x84);
    private static final Color REVETMENT_BODY  = new Color(0x6B, 0x60, 0x3C);
    private static final Color REVETMENT_RIB   = new Color(0x39, 0x33, 0x1E);
    private static final Color ROAD_FILL       = new Color(TileManifest.ROAD_FILL_RGB);
    private static final Color CROSSWALK_STRIPE = new Color(0xE8, 0xE8, 0xD0);

    private static final int CROSSWALK_STRIPE_COUNT = 5;
    private static final float CROSSWALK_STRIPE_FRAC = 0.10f;
    private static final float CROSSWALK_GAP_FRAC    = 0.10f;
    private static final float CROSSWALK_ALPHA       = 0.85f;
    private static final float CROSSWALK_INSET_FRAC  = 0.08f;

    /** Visual width is deliberately larger than the mathematical navigation edge. */
    private static final float EDGE_WINDOW_FRAME_THICKNESS_FRAC = 0.30f;
    private static final float EDGE_WINDOW_GLASS_THICKNESS_FRAC = 0.12f;
    private static final float EDGE_WINDOW_GLASS_END_INSET_FRAC = 0.08f;
    /**
     * A deployed revetment straddles its boundary rather than being projected
     * inward off an owning wall, because it has no owning wall — it is the
     * boundary. Thicker than a window frame and drawn across the whole edge, so
     * "which way does this protect me" is answerable at a glance.
     */
    private static final float EDGE_REVETMENT_THICKNESS_FRAC = 0.26f;
    /** Ribs down the run, so a hand-built barricade does not read as a painted line. */
    private static final int EDGE_REVETMENT_RIBS = 3;
    private static final float EDGE_REVETMENT_RIB_LENGTH_FRAC = 0.16f;

    private static final int GROUND_TILE_EDGE_INSET_PX       = FixedGridTileDrawer.GROUND_INSET_PX_LARGE;
    private static final int GROUND_SMALL_TILE_EDGE_INSET_PX = FixedGridTileDrawer.GROUND_INSET_PX_SMALL;

    private final BattleSprites sprites;

    /**
     * The battle's resident ground, or null for a renderer that does not keep
     * one. Shared with the owning {@link BattleRenderer}, which disposes it.
     */
    private final GroundMesh mesh;

    /**
     * The layer's sheets as one texture, or null for a renderer that does not
     * keep one. Shared with the owning {@link BattleRenderer}, which disposes
     * it.
     */
    private final GroundAtlas atlas;

    // Per-collect scratch (single-threaded; overwritten each frame).
    private DrawList out;
    private BattleCamera cam;
    private float alpha;
    private SpriteAPI urban, road, floors, water, urbanTile3, nature;
    private SpriteSheetFrames urbanTile3Frames, natureFrames;
    private TileRegistry tileReg;
    private GenMappingRegistry genMapping;

    /**
     * The atlas this collect addresses, or null while it is not serving.
     *
     * <p>Held beside the six sheet handles, with their origins packed the same
     * way, so remapping a resolved quad is six reference compares and a shift
     * rather than a map probe on each of twenty thousand quads.
     */
    private SpriteAPI atlasSheet;
    private int urbanOrigin, roadOrigin, floorsOrigin,
            waterOrigin, urbanTile3Origin, natureOrigin;

    /** Appends this frame's commands; the counterpart of the mesh's own sink. */
    private final CommandSink commandSink = new CommandSink();

    public GroundRenderSystem(BattleSprites sprites) {
        this(sprites, null, null);
    }

    public GroundRenderSystem(BattleSprites sprites, GroundMesh mesh, GroundAtlas atlas) {
        this.sprites = sprites;
        this.mesh = mesh;
        this.atlas = atlas;
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
        bindAtlas(ctx);

        NavigationGrid grid = ctx.sim.getGrid();
        CellTopology topology = ctx.sim.getTopology();
        VisibleCellRect view = cam.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS, grid.getWidth(), grid.getHeight());

        // Full-grid backing fill — under everything (matches renderGrid's backing quad).
        // One quad; the scissor bracket clips it to the viewport. A host whose
        // world does not fill its own grid declines it, because the quad would
        // paint over whatever it has put behind the world.
        if (ctx.hostProfile.worldBackingPainted()) {
            float wx0 = cam.cellToScreenX(0);
            float wy0 = cam.cellToScreenY(0);
            float wx1 = cam.cellToScreenX(grid.getWidth());
            float wy1 = cam.cellToScreenY(grid.getHeight());
            fillRect(wx0, wy0, wx1, wy1, FLOOR_COLOR);
        }

        if (urban == null) {
            // No tile sheet: solid-fill non-walkable cells (renderGrid's fallback branch).
            float cellPx = cam.cellPxSize();
            for (int y = view.minY(); y <= view.maxY(); y++) {
                for (int x = view.minX(); x <= view.maxX(); x++) {
                    if (grid.isWalkable(x, y)) continue;
                    float x0 = cam.cellToScreenX(x);
                    float y0 = cam.cellToScreenY(y);
                    fillRect(x0, y0, x0 + cellPx, y0 + cellPx, WALL_COLOR);
                    if (topology.isWindow(x, y)) windowPane(topology, x, y);
                }
            }
            emitEdgeBarriers(grid, view);
            return;
        }

        emitBaseTerrain(ctx, grid, topology, view);
        emitDecorations(grid, topology, view);
        emitWindows(topology, view);
        emitEdgeBarriers(grid, view);
    }

    /**
     * Points this collect's quads at the atlas, or leaves them on their own
     * sheets and asks for the atlas to be built.
     *
     * <p>The build is a custom pass rather than something the collector does,
     * because a collector performs no GL (law 2) and the atlas is a texture.
     * So the frame that builds it still collects the per-sheet stream, exactly
     * as the frame that bakes the resident mesh still draws the ordinary one.
     */
    private void bindAtlas(RenderContext ctx) {
        atlasSheet = null;
        if (atlas == null || !GroundAtlas.enabled()) return;
        if (!atlas.isServing()) {
            // Planning belongs to whoever built the batch that draws the atlas
            // (BattleRenderer.buildTileBatches). A host that never did has no
            // batch for it, and quads pointed at a sheet with no batch drain to
            // nothing at all — which is a missing picture rather than a slower
            // one, so an unplanned atlas is simply never used.
            if (atlas.isPlanned()) out.addCustom(RenderLayer.GROUND, atlas::sync);
            return;
        }
        atlasSheet = atlas.sheet();
        urbanOrigin = atlas.origin(urban);
        roadOrigin = atlas.origin(road);
        floorsOrigin = atlas.origin(floors);
        waterOrigin = atlas.origin(water);
        urbanTile3Origin = atlas.origin(urbanTile3);
        natureOrigin = atlas.origin(nature);
    }

    /**
     * Reports one resolved quad to {@code sink}, through the atlas where there
     * is one.
     *
     * <p>The single place a sheet and a source rectangle become a quad, so the
     * resident mesh and the command stream address the same texture at the same
     * coordinates without either of them knowing an atlas exists. A sheet the
     * atlas does not hold — a block on some sheet this system was not given —
     * passes through unchanged.
     */
    private void sinkQuad(GroundMesh.CellSink sink, SpriteAPI sheet,
                          int srcX, int srcY, int srcW, int srcH) {
        if (atlasSheet != null) {
            int origin = atlasOrigin(sheet);
            if (origin >= 0) {
                sink.quad(atlasSheet, srcX + (origin >>> 16), srcY + (origin & 0xFFFF),
                        srcW, srcH);
                return;
            }
        }
        sink.quad(sheet, srcX, srcY, srcW, srcH);
    }

    private int atlasOrigin(SpriteAPI sheet) {
        if (sheet == urban) return urbanOrigin;
        if (sheet == road) return roadOrigin;
        if (sheet == floors) return floorsOrigin;
        if (sheet == water) return waterOrigin;
        if (sheet == urbanTile3) return urbanTile3Origin;
        if (sheet == nature) return natureOrigin;
        return -1;
    }

    /**
     * The base tile of every visible cell — from the resident mesh where there is
     * one, and cell by cell where there is not.
     *
     * <p>A battle's first frame always takes the second path: {@link GroundMesh}
     * is GL-free to ask and cannot have baked anything before its own custom pass
     * has run, so the frame that builds the mesh also draws the ordinary stream
     * and the mesh serves from the next one. That is one frame of the old cost
     * per battle, and it is what keeps the collector free of GL.
     */
    private void emitBaseTerrain(RenderContext ctx, NavigationGrid grid,
                                 CellTopology topology, VisibleCellRect view) {
        // The mesh bakes each cell's atlas sub-rectangle once and keeps it, so
        // it must not bake before the atlas exists — a mesh baked against the
        // per-sheet coordinates would draw from them for the rest of the battle
        // and the atlas would serve nothing. One extra warm-up frame per
        // battle, on top of the one the bake already costs.
        boolean atlasReady = atlas == null || !GroundAtlas.enabled() || atlas.isServing();
        boolean resident = mesh != null && ctx.hostProfile.residentGroundAllowed()
                && GroundMesh.enabled() && atlasReady;
        if (resident && mesh.isServing(topology)) {
            BaseTerrain terrain = new BaseTerrain(grid, topology);
            out.addCustom(RenderLayer.GROUND, () -> {
                if (mesh.sync(topology, terrain)) mesh.draw(cam, alpha);
            });
            emitResidentFills(view);
            return;
        }
        if (resident) {
            BaseTerrain terrain = new BaseTerrain(grid, topology);
            // Bakes during this frame's drain; draws nothing, because the stream
            // below is already this frame's ground.
            out.addCustom(RenderLayer.GROUND, () -> mesh.sync(topology, terrain));
        }
        BaseTerrain terrain = new BaseTerrain(grid, topology);
        for (int y = view.minY(); y <= view.maxY(); y++) {
            for (int x = view.minX(); x <= view.maxX(); x++) {
                commandSink.at(x, y);
                terrain.resolve(x, y, commandSink);
            }
        }
    }

    /** The cells the mesh holds no tile for, which still paint their block's colour. */
    private void emitResidentFills(VisibleCellRect view) {
        int width = mesh.gridWidth();
        int[] cells = mesh.fillCells();
        for (int i = 0, n = mesh.fillCellCount(); i < n; i++) {
            int cell = cells[i];
            int x = cell % width;
            int y = cell / width;
            if (!view.contains(x, y)) continue;
            fillCellRgb(x, y, mesh.fillRgb(cell));
        }
    }

    // ---- base terrain resolution --------------------------------------------

    /**
     * What one cell's base terrain is, for whoever is asking.
     *
     * <p>Holds the per-pass lookups the dispatch would otherwise repeat per cell:
     * the {@code GroundKind} to block mapping, the wall block, and the authored
     * per-surface bulkheads. Built once per collect and handed to both sinks, so
     * a mesh bake and a command stream resolve identically by construction.
     */
    private final class BaseTerrain implements GroundMesh.CellResolver {

        private final NavigationGrid grid;
        private final CellTopology topology;

        private final GridBlockDef[] kindBlock;
        private final SpriteAPI[] kindSheet;
        private final Color[] kindFill;

        private final GridBlockDef wallBlock;
        private final Color wallFill;
        private final GridBlockDef[] surfaceBlock;
        private final Color[] surfaceFill;
        private final int surfaces;

        private final Color roadFill;
        private final String streetTileId;

        BaseTerrain(NavigationGrid grid, CellTopology topology) {
            this.grid = grid;
            this.topology = topology;
            this.surfaces = topology.surfaceCount();

            // Resolve the data-driven GroundKind -> render-block mapping once per
            // pass (GenMappingRegistry.groundBlockId). Each kind's block carries
            // its own resolver (autotile layout / variant pool / single) + sheet +
            // cellPx, so the generic drawGroundBlock path handles every "regular"
            // kind. STREET maps to a sliced urban3 TILE id (not a block), so
            // tileReg.block() is null and it falls through to its special case;
            // only SIDEWALK is unmapped.
            CellTopology.GroundKind[] kinds = CellTopology.GroundKind.values();
            kindBlock = new GridBlockDef[kinds.length];
            kindSheet = new SpriteAPI[kinds.length];
            kindFill = new Color[kinds.length];
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
            roadFill = blockFill("road.road", ROAD_FILL);
            streetTileId = (genMapping == null) ? "urban3.street-square"
                    : genMapping.groundBlockId(CellTopology.GroundKind.STREET);

            // Which block is the wall is a surfaceRender.WALL mapping question, and
            // the enclosed (no-frame) cell's fill is that block's own fillRgb.
            wallBlock = (tileReg == null) ? null : tileReg.block(surfaceBlockId(SurfaceRole.WALL));
            wallFill = (wallBlock != null && wallBlock.fillRgb != null)
                    ? new Color(wallBlock.fillRgb) : WALL_COLOR;

            // A room may draw its bulkhead from a block of its own. Resolved once
            // per pass into an array indexed by the topology's own surface index,
            // for the same reason the default is: a lookup per wall cell would put
            // a map probe in the inner loop of the densest pass on the deck.
            surfaceBlock = surfaces == 0 ? null : new GridBlockDef[surfaces + 1];
            surfaceFill = surfaces == 0 ? null : new Color[surfaces + 1];
            for (int i = 1; i <= surfaces; i++) {
                GridBlockDef block = tileReg == null ? null : tileReg.block(topology.surfaceId(i));
                // An id the catalog does not have falls back to the deck's own wall
                // rather than to nothing, so a stale document is a room that looks
                // ordinary instead of a hole in the ship.
                surfaceBlock[i] = block != null ? block : wallBlock;
                surfaceFill[i] = (surfaceBlock[i] != null && surfaceBlock[i].fillRgb != null)
                        ? new Color(surfaceBlock[i].fillRgb) : wallFill;
            }
        }

        @Override
        public void resolve(int x, int y, GroundMesh.CellSink sink) {
            if (topology.isWall(x, y)) resolveWall(x, y, sink);
            else resolveFloor(x, y, sink);
        }

        private void resolveWall(int x, int y, GroundMesh.CellSink sink) {
            GridBlockDef block = wallBlock;
            Color fill = wallFill;
            if (surfaceBlock != null) {
                int surface = topology.getSurface(x, y);
                if (surface > 0 && surface < surfaceBlock.length) {
                    block = surfaceBlock[surface];
                    fill = surfaceFill[surface];
                }
            }
            TileManifest.TileFrame tile =
                    WallMasks.pickTileFromMask(topology.getWallDirMask(x, y), block);
            if (tile == null) {
                sink.fill(fill.getRGB());
                return;
            }
            wallTile(block, tile, sink);
        }

        private void resolveFloor(int x, int y, GroundMesh.CellSink sink) {
            boolean nWall = GroundTileSelector.isInBoundsWall(topology, x, y + 1);
            boolean sWall = GroundTileSelector.isInBoundsWall(topology, x, y - 1);
            boolean eWall = GroundTileSelector.isInBoundsWall(topology, x + 1, y);
            boolean wWall = GroundTileSelector.isInBoundsWall(topology, x - 1, y);

            // A room may draw its deck from a block of its own — vent plate,
            // hazard striping — which is a fact about the picture and not about
            // the floor. Checked before the kind, because that is what "instead
            // of" means.
            GridBlockDef floorBlock = surfaces == 0 ? null : blockFor(topology, x, y);
            if (floorBlock != null) {
                groundBlock(floorBlock, sheetFor(floorBlock.sheetPath),
                        floorBlock.fillRgb == null ? null : new Color(floorBlock.fillRgb),
                        nWall, sWall, eWall, wWall, x, y, sink);
                return;
            }

            CellTopology.GroundKind kind = topology.getGroundKind(x, y);
            int ord = kind.ordinal();
            switch (kind) {
                case STREET:
                    if (urbanTile3 != null) {
                        if (tileReg == null) break;
                        if (GroundTileSelector.isSidewalkCell(grid, topology, x, y)) {
                            urbanTile3Frame(tileReg.tile(GroundTileSelector.urban3TileId(
                                    grid, topology, streetTileId, x, y)), sink);
                        } else {
                            urbanTile3Frame(tileReg.tile(streetTileId), sink);
                        }
                    } else if (road != null && tileReg != null) {
                        if (GroundTileSelector.isSidewalkCell(grid, topology, x, y)) {
                            roadTile(blockFrame("road.sidewalk", false, false, false, false),
                                    GROUND_TILE_EDGE_INSET_PX, sink);
                        } else {
                            roadPerimeter("road.road", roadFill,
                                    GroundTileSelector.isRoadBoundary(grid, topology, x, y + 1),
                                    GroundTileSelector.isRoadBoundary(grid, topology, x, y - 1),
                                    GroundTileSelector.isRoadBoundary(grid, topology, x + 1, y),
                                    GroundTileSelector.isRoadBoundary(grid, topology, x - 1, y),
                                    sink);
                        }
                    }
                    break;
                case SIDEWALK:
                    if (tileReg != null) urbanTile3Frame(tileReg.tile(GroundTileSelector.urban3TileId(
                            grid, topology, streetTileId, x, y)), sink);
                    break;
                case GRASS:
                case DIRT:
                    // Prefer the sliced nature sheet; the Floors variant block is the fallback.
                    if (nature != null && tileReg != null) {
                        natureTile(tileReg.tile(GroundTileSelector.natureTileId(kind, x, y)), sink);
                    } else {
                        groundBlock(kindBlock[ord], kindSheet[ord], kindFill[ord],
                                nWall, sWall, eWall, wWall, x, y, sink);
                    }
                    break;
                case VOID:
                    break; // outside the hull: there is no deck here to paint
                default:
                    // INDOOR/RUBBLE/COURTYARD/TILE/STRIPED/LZ_MARKER/STONE/SAND/SNOW/WATER/BRICK:
                    // generic resolve+draw from the kind's mapped block.
                    groundBlock(kindBlock[ord], kindSheet[ord], kindFill[ord],
                            nWall, sWall, eWall, wWall, x, y, sink);
                    break;
            }
        }
    }

    /**
     * One cell of bulkhead, drawn from its own block's sheet.
     *
     * <p>Not the urban sheet at the urban cell size. That was invisible for as
     * long as every wall on every map came from one block on that sheet — and the
     * moment a room asked for a wall from another sheet, it drew the urban sheet
     * at the other block's coordinates, which is either the wrong picture or, if
     * the two blocks happen to share an origin, exactly the same picture and no
     * way to tell anything went wrong.
     *
     * <p>The frame still comes from the cell's own {@code wallDirMask} rather
     * than from what its neighbours are made of: the mask says which sides face
     * exterior, and deriving that from neighbour type is a different and wrong
     * question.
     */
    private void wallTile(GridBlockDef block, TileManifest.TileFrame f, GroundMesh.CellSink sink) {
        if (f == null) return;
        SpriteAPI sheet = block == null ? urban : sheetFor(block.sheetPath);
        int cellPx = block == null ? TileManifest.TILE_SIZE : block.cellPx;
        if (sheet == null) {
            // A block whose sheet this system does not hold: the urban sheet is
            // the only honest fallback, and it is what the pass drew before.
            sheet = urban;
            cellPx = TileManifest.TILE_SIZE;
        }
        if (sheet == null) return;
        emitCellPx(sheet, cellPx, f.col, f.row, 0, sink);
    }

    private void roadTile(TileManifest.TileFrame f, int inset, GroundMesh.CellSink sink) {
        if (road == null || f == null) return;
        emitCellPx(road, TileManifest.TILE_SIZE, f.col, f.row, inset, sink);
    }

    /** Draw a road-sheet perimeter block (caller ensures {@code tileReg != null}); the open (null) case paints {@code fill}. */
    private void roadPerimeter(String blockId, Color fill, boolean n, boolean s, boolean e, boolean w,
                               GroundMesh.CellSink sink) {
        int[] c = tileReg.block(blockId).resolve(n, s, e, w);
        if (c == null) sink.fill(fill.getRGB());
        else roadTile(new TileManifest.TileFrame(c[0], c[1]), GROUND_TILE_EDGE_INSET_PX, sink);
    }

    /**
     * Generic data-driven ground draw: resolves {@code b} for this cell
     * ({@link GridBlockDef#resolve} dispatches on the block's own type — autotile
     * wall-mask, variant-pool {@code (x,y)} hash, or single), then emits it on the
     * block's {@code sheet} at its {@code cellPx} + matching inset. The enclosed/
     * open ({@code null}) case paints {@code fillIfNull} (a perimeter block's
     * hoisted {@code fillRgb}).
     */
    private void groundBlock(GridBlockDef b, SpriteAPI sheet, Color fillIfNull,
                             boolean n, boolean s, boolean e, boolean w, int x, int y,
                             GroundMesh.CellSink sink) {
        if (b == null || sheet == null) return;
        int[] c = b.resolve(n, s, e, w, x, y);
        if (c == null) {
            if (fillIfNull != null) sink.fill(fillIfNull.getRGB());
            return;
        }
        int inset = (b.cellPx >= TileManifest.TILE_SIZE) ? GROUND_TILE_EDGE_INSET_PX : GROUND_SMALL_TILE_EDGE_INSET_PX;
        emitCellPx(sheet, b.cellPx, c[0], c[1], inset, sink);
    }

    /** Source rect for a {@code cellPx}-grid sheet (56px floors, 32px urban/road, 16px water): col/row * cellPx, inset. */
    private void emitCellPx(SpriteAPI sheet, int cellPx, int col, int row, int inset,
                            GroundMesh.CellSink sink) {
        int srcX = col * cellPx + inset;
        int srcY = row * cellPx + inset;
        int srcW = cellPx - 2 * inset;
        int srcH = cellPx - 2 * inset;
        sinkQuad(sink, sheet, srcX, srcY, srcW, srcH);
    }

    private void urbanTile3Frame(TileDef frame, GroundMesh.CellSink sink) {
        if (urbanTile3 == null || urbanTile3Frames == null || frame == null) return;
        int idx = frame.frame;
        if (idx < 0 || idx >= urbanTile3Frames.frames.length) return;
        emitFrame(urbanTile3, urbanTile3Frames.frames[idx], frame.isGround(), sink);
    }

    private void natureTile(TileDef tile, GroundMesh.CellSink sink) {
        if (nature == null || natureFrames == null || tile == null) return;
        int idx = tile.frame;
        if (idx < 0 || idx >= natureFrames.frames.length) return;
        emitFrame(nature, natureFrames.frames[idx], tile.isGround(), sink);
    }

    /** Packed-frame sheet (urbanTile3 / nature): explicit frame rect, ground frames inset. */
    private void emitFrame(SpriteAPI sheet, SpriteSheetFrames.Frame f, boolean ground,
                           GroundMesh.CellSink sink) {
        int inset = ground ? GROUND_TILE_EDGE_INSET_PX : 0;
        int srcX = f.x + inset;
        int srcY = f.y + inset;
        int srcW = Math.max(1, f.w - 2 * inset);
        int srcH = Math.max(1, f.h - 2 * inset);
        sinkQuad(sink, sheet, srcX, srcY, srcW, srcH);
    }

    /**
     * The command-stream destination for a resolved cell.
     *
     * <p>Carries the cell it is standing on, because a resolved quad describes a
     * sub-rectangle of a sheet and says nothing about where on screen it goes —
     * which is exactly the property that lets the mesh store the same resolution
     * in cell space and apply the camera once for the whole map.
     */
    private final class CommandSink implements GroundMesh.CellSink {
        private int gridX;
        private int gridY;

        void at(int gridX, int gridY) {
            this.gridX = gridX;
            this.gridY = gridY;
        }

        @Override
        public void quad(SpriteAPI sheet, int srcX, int srcY, int srcW, int srcH) {
            if (sheet == null) return;
            float cellPx = cam.cellPxSize();
            float cx = cam.cellToScreenX(gridX + 0.5f);
            float cy = cam.cellToScreenY(gridY + 0.5f);
            out.addSheetQuad(RenderLayer.GROUND, sheet, srcX, srcY, srcW, srcH,
                    cx, cy, cellPx, cellPx, 1f, 1f, 1f, alpha);
        }

        @Override
        public void fill(int rgb) {
            fillCellRgb(gridX, gridY, rgb);
        }
    }

    // ---- decorations on a drawn cell ----------------------------------------

    /**
     * What is laid over a cell once its ground is drawn: crosswalk stripes,
     * nature scatter, and the decal that says an opening is a door.
     *
     * <p>Its own pass rather than a tail on the floor dispatch, because the base
     * terrain may not have been collected at all this frame — it may be resident
     * on the GPU. Order within a cell is unchanged: stripes, then scatter, then
     * the door.
     */
    private void emitDecorations(NavigationGrid grid, CellTopology topology, VisibleCellRect view) {
        String doorOpenId = surfaceBlockId(SurfaceRole.DOOR_OPEN);
        for (int y = view.minY(); y <= view.maxY(); y++) {
            for (int x = view.minX(); x <= view.maxX(); x++) {
                if (topology.isWall(x, y)) continue;
                if (topology.isCrosswalk(x, y)
                        && topology.getGroundKind(x, y) == CellTopology.GroundKind.STREET
                        && !GroundTileSelector.isSidewalkCell(grid, topology, x, y)) {
                    crosswalkStripes(x, y, topology.isCrosswalkStripesHorizontal(x, y));
                }
                int oi = topology.getNatureOverlayIndex(x, y);
                if (oi >= 0 && tileReg != null) {
                    commandSink.at(x, y);
                    natureTile(tileReg.byIndex(oi), commandSink);
                }
                if (grid.isDoorway(x, y) && !topology.isRubble(x, y) && tileReg != null) {
                    commandSink.at(x, y);
                    TileManifest.TileFrame f = blockFrame(doorOpenId, false, false, false, false);
                    if (urban != null && f != null) {
                        emitCellPx(urban, TileManifest.TILE_SIZE, f.col, f.row, 0, commandSink);
                    }
                }
            }
        }
    }

    /** Window slits over the wall cells that carry them. */
    private void emitWindows(CellTopology topology, VisibleCellRect view) {
        for (int y = view.minY(); y <= view.maxY(); y++) {
            for (int x = view.minX(); x <= view.maxX(); x++) {
                if (topology.isWall(x, y) && topology.isWindow(x, y)) windowPane(topology, x, y);
            }
        }
    }

    /** The block a cell has been told to draw from, or null when its kind decides. */
    private GridBlockDef blockFor(CellTopology topology, int x, int y) {
        if (tileReg == null) return null;
        String id = topology.getSurfaceId(x, y);
        return id == null ? null : tileReg.block(id);
    }

    /**
     * The block id an orthogonal surface renders as — this system's mapping
     * when it has one, else the id this mod ships for the role. Sits beside the
     * ground dispatch so both halves of render dispatch read the same way.
     */
    private String surfaceBlockId(SurfaceRole role) {
        String id = (genMapping == null) ? null : genMapping.surfaceBlockId(role);
        return id != null ? id : role.shippedBlockId();
    }

    /** A compact cyan slit makes see-through wall cells readable as firing windows. */
    private void windowPane(CellTopology topology, int gridX, int gridY) {
        float cell = cam.cellPxSize();
        float x0 = cam.cellToScreenX(gridX);
        float y0 = cam.cellToScreenY(gridY);
        int mask = topology.getWallDirMask(gridX, gridY);
        boolean horizontal = (mask & (CellTopology.WALL_DIR_N | CellTopology.WALL_DIR_S)) != 0;
        float frameX = x0 + cell * (horizontal ? 0.12f : 0.31f);
        float frameY = y0 + cell * (horizontal ? 0.31f : 0.12f);
        float frameW = cell * (horizontal ? 0.76f : 0.38f);
        float frameH = cell * (horizontal ? 0.38f : 0.76f);
        fillRect(frameX, frameY, frameX + frameW, frameY + frameH, WINDOW_FRAME);
        float inset = cell * 0.07f;
        fillRect(frameX + inset, frameY + inset,
                frameX + frameW - inset, frameY + frameH - inset, WINDOW_GLASS);
    }

    /** Sparse shared-edge features paint over both adjacent floor cells. */
    private void emitEdgeBarriers(NavigationGrid grid, VisibleCellRect view) {
        float cell = cam.cellPxSize();
        float frameThickness = cell * EDGE_WINDOW_FRAME_THICKNESS_FRAC;
        float glassThickness = cell * EDGE_WINDOW_GLASS_THICKNESS_FRAC;
        float glassEndInset = cell * EDGE_WINDOW_GLASS_END_INSET_FRAC;
        for (SharedEdgeBarrier barrier : grid.getEdgeBarriers()) {
            int x = barrier.cellX();
            int y = barrier.cellY();
            int nx = x + barrier.direction().dx;
            int ny = y + barrier.direction().dy;
            if (!view.contains(x, y) && !view.contains(nx, ny)) continue;
            if (barrier.kind() == SharedEdgeBarrier.Kind.REVETMENT) {
                emitRevetment(barrier, cell);
                continue;
            }
            if (barrier.kind() != SharedEdgeBarrier.Kind.WINDOW) continue;

            if (barrier.direction() == Direction.E) {
                float edgeX = cam.cellToScreenX(x + 1f);
                float y0 = cam.cellToScreenY(y);
                float y1 = cam.cellToScreenY(y + 1f);
                float inward = barrier.structureCellX() == x ? -1f : 1f;
                float centerX = edgeX + inward * frameThickness * 0.5f;
                fillRect(centerX - frameThickness * 0.5f, y0,
                        centerX + frameThickness * 0.5f, y1, WINDOW_FRAME);
                fillRect(centerX - glassThickness * 0.5f, y0 + glassEndInset,
                        centerX + glassThickness * 0.5f, y1 - glassEndInset,
                        WINDOW_GLASS);
            } else {
                float edgeY = cam.cellToScreenY(y + 1f);
                float x0 = cam.cellToScreenX(x);
                float x1 = cam.cellToScreenX(x + 1f);
                float inward = barrier.structureCellY() == y ? -1f : 1f;
                float centerY = edgeY + inward * frameThickness * 0.5f;
                fillRect(x0, centerY - frameThickness * 0.5f,
                        x1, centerY + frameThickness * 0.5f, WINDOW_FRAME);
                fillRect(x0 + glassEndInset, centerY - glassThickness * 0.5f,
                        x1 - glassEndInset, centerY + glassThickness * 0.5f,
                        WINDOW_GLASS);
            }
        }
    }

    /**
     * One deployed revetment: a slab centred on the shared edge with ribs
     * across it. Centred, not offset to one side, because a screen a marine set
     * down belongs to the boundary itself and protects whoever is on either
     * side of it — drawing it inside one of the two cells would claim an
     * ownership it does not have.
     */
    private void emitRevetment(SharedEdgeBarrier barrier, float cell) {
        int x = barrier.cellX();
        int y = barrier.cellY();
        float thickness = cell * EDGE_REVETMENT_THICKNESS_FRAC;
        float ribLength = cell * EDGE_REVETMENT_RIB_LENGTH_FRAC;
        if (barrier.direction() == Direction.E) {
            float centerX = cam.cellToScreenX(x + 1f);
            float y0 = cam.cellToScreenY(y);
            float y1 = cam.cellToScreenY(y + 1f);
            fillRect(centerX - thickness * 0.5f, y0,
                    centerX + thickness * 0.5f, y1, REVETMENT_BODY);
            for (int rib = 1; rib <= EDGE_REVETMENT_RIBS; rib++) {
                float ribY = y0 + (y1 - y0) * rib / (EDGE_REVETMENT_RIBS + 1f);
                fillRect(centerX - thickness * 0.5f, ribY - ribLength * 0.5f,
                        centerX + thickness * 0.5f, ribY + ribLength * 0.5f,
                        REVETMENT_RIB);
            }
        } else {
            float centerY = cam.cellToScreenY(y + 1f);
            float x0 = cam.cellToScreenX(x);
            float x1 = cam.cellToScreenX(x + 1f);
            fillRect(x0, centerY - thickness * 0.5f,
                    x1, centerY + thickness * 0.5f, REVETMENT_BODY);
            for (int rib = 1; rib <= EDGE_REVETMENT_RIBS; rib++) {
                float ribX = x0 + (x1 - x0) * rib / (EDGE_REVETMENT_RIBS + 1f);
                fillRect(ribX - ribLength * 0.5f, centerY - thickness * 0.5f,
                        ribX + ribLength * 0.5f, centerY + thickness * 0.5f,
                        REVETMENT_RIB);
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

    // ---- solid fills ---------------------------------------------------------

    private void fillCellRgb(int gridX, int gridY, int rgb) {
        float x0 = cam.cellToScreenX(gridX);
        float y0 = cam.cellToScreenY(gridY);
        float c = cam.cellPxSize();
        out.addSolidRect(RenderLayer.GROUND, x0, y0, x0 + c, y0 + c,
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f,
                alpha);
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
}
