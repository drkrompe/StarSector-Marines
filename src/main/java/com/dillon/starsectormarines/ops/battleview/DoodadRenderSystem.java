package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.tender.BuildPlot;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.ui.highlight.BuildPreview;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;

/**
 * Emits the {@link RenderLayer#DOODADS} layer — props (rocks, plants, debris,
 * parked road vehicles) painted above ground/decals/aircraft and below units. Each doodad uses
 * the canonical fixed-grid source rectangle and the placement's rotated world
 * footprint; the drain batches them per sheet. Off-camera footprints are skipped
 * against {@link VisibleCellRect}.
 *
 * <p>Legacy-only maps retain their established sheet grouping. Maps using added
 * catalog sheets preserve placement order: a room lays its deck covering before
 * its fixtures, and grouping an unknown sheet last could hide that machinery.
 * The drain batches adjacent runs on that path.
 *
 * <p><b>One sheet for the whole layer.</b> Either path, a run of props breaks
 * at every change of sheet, so each battle gets a {@link DecorationAtlas}
 * planned from its own placements the first time this layer sees them. Quads
 * are re-addressed into it in {@link #emit} and nowhere else; order, position
 * and tint are untouched. A placement the atlas does not hold draws from its
 * own sheet as before.
 */
public final class DoodadRenderSystem implements RenderSystem {

    private final BattleSprites sprites;
    /** Crowns and stage doodads of the trees the creep is overgrowing, made once per placement. */
    private final Map<Doodad, CreepTreeTwins.Entry> creepTreeStages = CreepTreeTwins.cache();

    /** Where a sheet's batch lives, for the atlas's own batch to be put beside them. */
    interface Batches {
        QuadBatch get(SpriteAPI sheet);
        void put(SpriteAPI sheet, QuadBatch batch);
        void remove(SpriteAPI sheet);
    }

    /** Null in a host that never registers sheet batches; there is then no atlas. */
    private final Batches batches;
    /** The current battle's atlas, planned from {@link #atlasFor}. */
    private DecorationAtlas atlas;
    /** The placement list {@link #atlas} was planned from: a different list is a different battle. */
    private List<Doodad> atlasFor;
    /** {@link #atlas} while it serves, and only during {@link #collect}. */
    private DecorationAtlas serving;

    public DoodadRenderSystem(BattleSprites sprites) {
        this(sprites, null);
    }

    DoodadRenderSystem(BattleSprites sprites, Batches batches) {
        this.sprites = sprites;
        this.batches = batches;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.DOODADS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        VisibleCellRect view = cam.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                ctx.sim.getGrid().getWidth(), ctx.sim.getGrid().getHeight());
        Twins twins = wornTwins(ctx);
        if (twins == null) twins = CreepTreeTwins.of(ctx.sim, creepTreeStages);
        fire = FireRenderSystem.enabled() ? ctx.sim.fireFieldIfPresent() : null;
        serving = settleAtlas(ctx.sim.getDoodads(), out);
        try {
            collectPlacements(ctx.sim.getDoodads(), out, cam, view, ctx.alphaMult, twins);
            if (ctx.highlights != null) {
                for (BuildPreview ghost : ctx.highlights.buildPreviews()) collectGhost(ghost, out, cam, view, ctx.alphaMult);
            }
        } finally {
            fire = null;
            serving = null;
        }
    }

    /** How strongly a build's ghost draws, against its finished art. */
    private static final float GHOST_ALPHA = 0.45f;
    /** The pad's concrete tone, as the ghost's fill. */
    private static final float[] GHOST_PAD = {0.62f, 0.62f, 0.6f};
    /** The refused tone a ghost wears where it cannot be laid. */
    private static final float[] GHOST_REFUSED = {1f, 0.35f, 0.3f};

    /**
     * A staged build's finished layout under the placement cursor, faint: the pad as a concrete fill, the rim and
     * stores in their own art, and the gun on its collar turned to the plot's facing. Refused, the whole of it wears
     * the refused tone. Presentation only ({@link BuildPreview}).
     */
    private void collectGhost(BuildPreview ghost, DrawList out, BattleCamera cam, VisibleCellRect view,
                              float alphaMult) {
        float alpha = GHOST_ALPHA * alphaMult;
        float cellPx = cam.cellPxSize();
        float[] pad = ghost.valid() ? GHOST_PAD : GHOST_REFUSED;
        TileRegistry registry = TileRegistry.installed();
        List<Doodad> pieces = new ArrayList<>();
        for (BuildPlot.Piece piece : ghost.pieces()) {
            if (piece.doodad() == null) {
                float x0 = cam.cellToScreenX(piece.x()), x1 = cam.cellToScreenX(piece.x() + 1f);
                float y0 = cam.cellToScreenY(piece.y()), y1 = cam.cellToScreenY(piece.y() + 1f);
                out.addSolidRect(RenderLayer.DOODADS, Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1),
                        Math.max(y0, y1), pad[0], pad[1], pad[2], alpha * 0.6f);
                continue;
            }
            DoodadDef def = registry == null ? null : registry.doodad(piece.doodad());
            if (def != null) pieces.add(new Doodad(piece.x(), piece.y(), def));
        }
        if (ghost.valid()) collectPlacements(pieces, out, cam, view, alpha);
        else collectTinted(pieces, out, cam, view, pad[0], pad[1], pad[2], alpha);
        StructureDef structure = TurretCatalogRegistry.installed() == null ? null
                : TurretCatalogRegistry.installed().getStructure(ghost.structureId());
        if (structure == null) return;
        float gx = cam.cellToScreenX(ghost.gunX() + 0.5f), gy = cam.cellToScreenY(ghost.gunY() + 0.5f);
        float r = ghost.valid() ? 1f : pad[0], g = ghost.valid() ? 1f : pad[1], b = ghost.valid() ? 1f : pad[2];
        ShuttleSpriteCache collar = structure.art == null || structure.art.basePath == null ? null
                : sprites.spriteByPath(structure.art.basePath);
        if (collar != null) {
            float w = structure.footprintCellsX * cellPx, h = structure.footprintCellsY * cellPx;
            out.addSprite(RenderLayer.DOODADS, collar.sprite, gx, gy, w, h, 0f, r, g, b, alpha);
        }
        ShuttleSpriteCache mount = sprites.turretSprites().get(structure.id);
        if (mount != null) {
            float h = structure.mount.visualCells * cellPx;
            out.addSprite(RenderLayer.DOODADS, mount.sprite, gx, gy, h * mount.aspect, h,
                    AirBody.facingToward(ghost.facingX(), ghost.facingY()), r, g, b, alpha);
        }
    }

    /**
     * The atlas this collect may address, or null.
     *
     * <p>Planned the first time a battle's placements are seen, which is the
     * earliest point every host has them, and GL-free; the texture is built by
     * a custom pass in this layer, so the frame that builds it still draws from
     * the sheets and the atlas serves from the next. A new battle's list
     * retires the last battle's atlas, its texture released by a custom pass
     * since a collector performs no GL.
     */
    private DecorationAtlas settleAtlas(List<Doodad> placed, DrawList out) {
        if (batches == null || !DecorationAtlas.enabled()) return null;
        if (placed != atlasFor) {
            DecorationAtlas retired = atlas;
            if (retired != null) {
                batches.remove(retired.sheet());
                out.addCustom(RenderLayer.DOODADS, retired::dispose);
            }
            atlas = new DecorationAtlas();
            atlasFor = placed;
            if (atlas.plan(placed, TileRegistry.installed(), this::sheetFor, this::loadedSheetFor, batches::get)) {
                batches.put(atlas.sheet(), new QuadBatch(atlas.sheet(), atlas.width(), atlas.height(), 8192));
            }
        }
        if (!atlas.isPlanned()) return null;
        if (!atlas.isServing()) {
            out.addCustom(RenderLayer.DOODADS, atlas::sync);
            return null;
        }
        return atlas;
    }

    /** The current battle's atlas, or null: for evidence that reports on it. */
    DecorationAtlas atlas() {
        return atlas;
    }

    /** Releases the current battle's atlas. The context that made it must still be current. */
    void disposeAtlas() {
        if (atlas == null) return;
        if (batches != null) batches.remove(atlas.sheet());
        atlas.dispose();
        atlas = null;
        atlasFor = null;
    }

    /**
     * A placement's condition twin and how strongly it shows. Presentation only:
     * a twin shares its placement's footprint, cover and blocking.
     */
    interface Twins {
        /** What the placement shows under its twin; the placement itself unless its condition replaces it. */
        default Doodad base(Doodad placed) { return placed; }
        /** How strongly the base shows while its twin is coming in; whole unless the twin replaces it. */
        default float baseWeight(Doodad placed) { return 1f; }
        /** The twin, or the placement itself when it has none. */
        Doodad twin(Doodad placed);
        /** 0 shows the placement, 1 its twin; between, the twin is drawn over it at that alpha. */
        float weight(Doodad placed);
    }

    /** A deck whose Life support is at Deficit shows worn furniture where a worn twin exists. */
    private static Twins wornTwins(RenderContext ctx) {
        ShipDeckDressing dressing = ctx.deckDressing;
        if (dressing == null || ctx.hostProfile != BattleRenderHostProfile.DECK_SCENE) return null;
        DressingWave wave = ctx.dressingWave;
        float time = ctx.animationTimeSeconds;
        if (wave != null && wave.moves(ShipCore.LIFE_SUPPORT)) {
            return new Twins() {
                @Override public Doodad twin(Doodad placed) { return dressing.worn(placed); }
                @Override public float weight(Doodad placed) {
                    return wave.deficit(ShipCore.LIFE_SUPPORT, placed.cellX + placed.footprintCellsX * .5f,
                            placed.cellY + placed.footprintCellsY * .5f, time);
                }
            };
        }
        if (ctx.coreDressing.lifeSupport()) return null;
        return new Twins() {
            @Override public Doodad twin(Doodad placed) { return dressing.worn(placed); }
            @Override public float weight(Doodad placed) { return 1f; }
        };
    }

    /**
     * Finish the fog layer with observed solid prop surfaces. This paints only the
     * sprite's alpha, never its rectangular ground footprint, and changes no
     * reveal cells. Ground and enemies behind the prop stay hidden.
     * Flat debris keeps the ordinary ground-fog treatment.
     */
    void collectVisibleSurfaces(RenderContext ctx, DrawList out) {
        collectVisibleSurfaces(ctx, out, ctx.alphaMult);
    }

    private void collectVisibleSurfaces(RenderContext ctx, DrawList out, float alpha) {
        FogOfWarService fog = ctx.sim.getFogOfWar();
        if (!fog.isInitialized()) return;
        NavigationGrid grid = ctx.sim.getGrid();
        BattleCamera cam = ctx.camera;
        VisibleCellRect view = cam.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                grid.getWidth(), grid.getHeight());
        for (Doodad d : ctx.sim.getDoodads()) {
            if (!view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY)
                    || !hasObservedSurface(d, grid, fog)) continue;
            SpriteAPI sheet = sheetFor(d);
            if (sheet != null) emit(out, cam, sheet, d, cam.cellPxSize(), alpha, RenderLayer.FOG);
        }
    }

    /** Coverage uses the identical sprite projection, without the host fade. */
    void collectLightingSurfaces(RenderContext ctx, DrawList out) {
        collectVisibleSurfaces(ctx, out, 1f);
    }

    static boolean hasObservedSurface(Doodad d, NavigationGrid grid, FogOfWarService fog) {
        if (!d.hasFogSurface()) return false;
        for (int y = 0; y < d.footprintCellsY; y++) for (int x = 0; x < d.footprintCellsX; x++) {
            if (!d.hasFogSurfaceAt(x, y)) continue;
            int gx = d.cellX + x, gy = d.cellY + y;
            if (grid.inBounds(gx, gy) && grid.blocksStructuralLineOfSightAt(grid.index(gx, gy))
                    && fog.isCellRevealed(gx, gy)) return true;
        }
        return false;
    }

    void collectPlacements(Iterable<Doodad> placements, DrawList out, BattleCamera cam,
                           VisibleCellRect view, float alphaMult) {
        collectPlacements(placements, out, cam, view, alphaMult, null);
    }

    /** @param twins each placement's condition twin, or null for none */
    void collectPlacements(Iterable<Doodad> placements, DrawList out, BattleCamera cam,
                           VisibleCellRect view, float alphaMult, Twins twins) {
        boolean additionalSheet = false;
        for (Doodad d : placements) {
            if (!isLegacySheet(d.sheetPath)) { additionalSheet = true; break; }
        }
        if (!additionalSheet) {
            // Preserve the existing draw order and texture-bind count for shipped maps.
            emitLegacySheet(placements, out, cam, view, sprites.roadSheet(), TileManifest.ROAD_SHEET, alphaMult);
            emitLegacySheet(placements, out, cam, view, sprites.doodadSheet(), TileManifest.DOODAD_SHEET, alphaMult);
            emitLegacySheet(placements, out, cam, view, sprites.parkedVehicleSheet(), TileManifest.PARKED_VEHICLE_SHEET, alphaMult);
            emitLegacySheet(placements, out, cam, view, sprites.tileSheet(), TileManifest.SHEET, alphaMult);
            return;
        }
        TileRegistry registry = TileRegistry.installed();
        Function<String, PropMotion> motions = registry == null ? id -> null : registry::motion;
        for (Doodad placed : placements) {
            if (!view.intersectsCells(placed.cellX, placed.cellY, placed.footprintCellsX, placed.footprintCellsY)
                    || drawnOverhead(placed, motions)) continue;
            Doodad shown = twins == null ? placed : twins.base(placed);
            Doodad twin = twins == null ? placed : twins.twin(placed);
            float weight = twin == placed ? 0f : twins.weight(placed);
            // A twin at full weight hides the base, unless the base is fading out under it.
            float baseWeight = twins == null ? 1f : twins.baseWeight(placed);
            if ((weight < 1f || baseWeight < 1f) && baseWeight > 0f) emitOne(shown, out, cam, alphaMult * baseWeight);
            if (weight > 0f) emitOne(twin, out, cam, alphaMult * weight);
        }
    }

    /** One placement at its own alpha, if it is in view. */
    void collectOne(Doodad d, DrawList out, BattleCamera cam, VisibleCellRect view, float alpha) {
        if (alpha <= 0f || !view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY)) return;
        emitOne(d, out, cam, alpha);
    }

    private void emitOne(Doodad d, DrawList out, BattleCamera cam, float alpha) {
        SpriteAPI sheet = sheetFor(d);
        if (sheet == null) return;
        float shade = fire == null ? 1f : FireRenderSystem.treeShade(fire, d);
        emit(out, cam, sheet, d, cam.cellPxSize(), alpha, RenderLayer.DOODADS, shade, shade, shade);
    }

    /** The fire this frame's placements are charred by; set only while {@link #collect} runs. */
    private FireField fire;

    /**
     * Draw placements again over themselves in one flat tint: how an unpowered
     * screen reads without a second sheet of dead variants. Presentation only.
     */
    void collectTinted(Iterable<Doodad> placements, DrawList out, BattleCamera cam, VisibleCellRect view,
                       float r, float g, float b, float alpha) {
        for (Doodad d : placements) {
            if (!view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY)) continue;
            SpriteAPI sheet = sheetFor(d);
            if (sheet != null) emit(out, cam, sheet, d, cam.cellPxSize(), alpha, RenderLayer.DOODADS, r, g, b);
        }
    }

    /**
     * Whether the placement's own art draws overhead ({@link PropMotion#liftsItself}): the roof pass draws it above
     * the people under it, fading over them, so the ground pass leaves it out rather than lay a second, opaque copy
     * beneath them.
     */
    static boolean drawnOverhead(Doodad d, Function<String, PropMotion> motions) {
        String id = d.definitionId();
        if (id == null) return false;
        PropMotion motion = motions.apply(id);
        return motion != null && motion.liftsItself();
    }

    private static boolean isLegacySheet(String path) {
        return path.equals(TileManifest.SHEET) || path.equals(TileManifest.ROAD_SHEET)
                || path.equals(TileManifest.DOODAD_SHEET) || path.equals(TileManifest.PARKED_VEHICLE_SHEET);
    }

    /** {@link #sheetFor} without loading a catalog sheet nobody has loaded. */
    private SpriteAPI loadedSheetFor(Doodad d) {
        return switch (d.sheetPath) {
            case TileManifest.SHEET, TileManifest.ROAD_SHEET, TileManifest.DOODAD_SHEET,
                 TileManifest.PARKED_VEHICLE_SHEET -> sheetFor(d);
            default -> sprites.loadedTerrainSheet(d.sheetPath);
        };
    }

    private SpriteAPI sheetFor(Doodad d) {
        return switch (d.sheetPath) {
            case TileManifest.SHEET -> sprites.tileSheet();
            case TileManifest.ROAD_SHEET -> sprites.roadSheet();
            case TileManifest.DOODAD_SHEET -> sprites.doodadSheet();
            case TileManifest.PARKED_VEHICLE_SHEET -> sprites.parkedVehicleSheet();
            default -> sprites.extraTerrainSheet(d.sheetPath);
        };
    }

    private void emitLegacySheet(Iterable<Doodad> placements, DrawList out, BattleCamera cam,
                                        VisibleCellRect view, SpriteAPI sheet, String path, float alpha) {
        if (sheet == null) return;
        TileRegistry registry = TileRegistry.installed();
        Function<String, PropMotion> motions = registry == null ? id -> null : registry::motion;
        for (Doodad d : placements) {
            if (path.equals(d.sheetPath) && !drawnOverhead(d, motions)
                    && view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY))
                emit(out, cam, sheet, d, cam.cellPxSize(), alpha);
        }
    }

    private void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                      Doodad d, float cellPx, float alphaMult) {
        emit(out, cam, sheet, d, cellPx, alphaMult, RenderLayer.DOODADS);
    }

    private void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                      Doodad d, float cellPx, float alphaMult, RenderLayer layer) {
        emit(out, cam, sheet, d, cellPx, alphaMult, layer, 1f, 1f, 1f);
    }

    private void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                      Doodad d, float cellPx, float alphaMult, RenderLayer layer,
                      float r, float g, float b) {
        // Source rectangle is read at the sheet's own cell size, not the
        // game's. A sheet drawn finer than the grid keeps its detail; one drawn
        // at the grid behaves exactly as before. The quad then stretches that
        // rectangle over the prop's footprint in cells, so what an authored
        // footprint really says is how much deck the art is stretched across.
        // Rotation keeps that source span canonical and turns the destination
        // around the center of the already-rotated tactical footprint.
        int srcX = sourceX(d);
        int srcY = sourceY(d);
        int sourceWidth = sourceWidth(d);
        int sourceHeight = sourceHeight(d);
        // Only this layer's own quads move: the fog and lighting passes that
        // reuse this projection keep their sheets, which is what they had.
        if (serving != null && layer == RenderLayer.DOODADS) {
            int origin = serving.regionOrigin(sheet, srcX, srcY, sourceWidth, sourceHeight);
            if (origin >= 0) {
                sheet = serving.sheet();
                srcX = origin >>> 16;
                srcY = origin & 0xFFFF;
            }
        }
        float cx = cam.cellToScreenX(d.cellX + d.footprintCellsX * 0.5f);
        float cy = cam.cellToScreenY(d.cellY + d.footprintCellsY * 0.5f);
        if (d.mirrored) {
            // A vertical flip through a half turn is a horizontal mirror, which
            // is what a prop drawn from one side needs to face the other way.
            // Rotating it instead lights it from underneath.
            out.addSheetQuadFlippedV(layer, sheet,
                    srcX, srcY, sourceWidth, sourceHeight,
                    cx, cy, cellPx * d.sourceCellsX, cellPx * d.sourceCellsY,
                    d.quarterTurns * 90f + 180f,
                    r, g, b, alphaMult);
            return;
        }
        out.addSheetQuad(layer, sheet,
                srcX, srcY, sourceWidth, sourceHeight,
                cx, cy, cellPx * d.sourceCellsX, cellPx * d.sourceCellsY,
                d.quarterTurns * 90f,
                r, g, b, alphaMult);
    }

    /** Where a placement's art starts on its sheet, read at the sheet's own cell size rather than the game's. */
    static int sourceX(Doodad d) {
        return d.tile.col * d.sourceCellPx;
    }

    static int sourceY(Doodad d) {
        return d.tile.row * d.sourceCellPx;
    }

    static int sourceWidth(Doodad d) {
        return d.sourceCellPx * d.sourceCellsX;
    }

    static int sourceHeight(Doodad d) {
        return d.sourceCellPx * d.sourceCellsY;
    }
}
