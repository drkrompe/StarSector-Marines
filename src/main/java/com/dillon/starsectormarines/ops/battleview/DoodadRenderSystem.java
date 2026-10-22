package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.util.Map;
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
 */
public final class DoodadRenderSystem implements RenderSystem {

    private final BattleSprites sprites;
    /** Crowns and stage doodads of the trees the creep is overgrowing, made once per placement. */
    private final Map<Doodad, CreepTreeTwins.Entry> creepTreeStages = CreepTreeTwins.cache();

    public DoodadRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
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
        collectPlacements(ctx.sim.getDoodads(), out, cam, view, ctx.alphaMult, twins);
        fire = null;
        // Facility tiers fitted into existing rooms stand with the furniture, presentation only.
        if (!ctx.facilityPieces.isEmpty() && ctx.hostProfile == BattleRenderHostProfile.DECK_SCENE)
            collectPlacements(ctx.facilityPieces, out, cam, view, ctx.alphaMult);
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
        for (Doodad placed : placements) {
            if (!view.intersectsCells(placed.cellX, placed.cellY, placed.footprintCellsX, placed.footprintCellsY)) continue;
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

    private static boolean isLegacySheet(String path) {
        return path.equals(TileManifest.SHEET) || path.equals(TileManifest.ROAD_SHEET)
                || path.equals(TileManifest.DOODAD_SHEET) || path.equals(TileManifest.PARKED_VEHICLE_SHEET);
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

    private static void emitLegacySheet(Iterable<Doodad> placements, DrawList out, BattleCamera cam,
                                        VisibleCellRect view, SpriteAPI sheet, String path, float alpha) {
        if (sheet == null) return;
        for (Doodad d : placements) {
            if (path.equals(d.sheetPath)
                    && view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY))
                emit(out, cam, sheet, d, cam.cellPxSize(), alpha);
        }
    }

    private static void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                             Doodad d, float cellPx, float alphaMult) {
        emit(out, cam, sheet, d, cellPx, alphaMult, RenderLayer.DOODADS);
    }

    private static void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                             Doodad d, float cellPx, float alphaMult, RenderLayer layer) {
        emit(out, cam, sheet, d, cellPx, alphaMult, layer, 1f, 1f, 1f);
    }

    private static void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                             Doodad d, float cellPx, float alphaMult, RenderLayer layer,
                             float r, float g, float b) {
        // Source rectangle is read at the sheet's own cell size, not the
        // game's. A sheet drawn finer than the grid keeps its detail; one drawn
        // at the grid behaves exactly as before. The quad then stretches that
        // rectangle over the prop's footprint in cells, so what an authored
        // footprint really says is how much deck the art is stretched across.
        // Rotation keeps that source span canonical and turns the destination
        // around the center of the already-rotated tactical footprint.
        TileManifest.TileFrame f = d.tile;
        int cell = d.sourceCellPx;
        int srcX = f.col * cell;
        int srcY = f.row * cell;
        int sourceWidth = cell * d.sourceCellsX;
        int sourceHeight = cell * d.sourceCellsY;
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
}
