package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.render2d.DecalAccumulator;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.flyby.FlybyOverlay;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.TimeOfDay;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ui.compound.CompoundMarkerRenderer;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.render2d.DrawListRenderer;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.LightAccumulator;
import com.dillon.starsectormarines.render2d.LineBatch;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.dillon.starsectormarines.render2d.RibbonBatch;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * World-layer render pipeline extracted from {@code BattleScreen}.
 *
 * <p>Owns the world-pass machinery (tile batches, overlays, FX systems) and all
 * {@code render*}/{@code draw*} methods. {@code BattleScreen} retains the scissor
 * bracket, chrome ({@code renderSpeedMarker}, {@code renderBanner}), loop, input,
 * and audio. The seam is {@link #renderWorld(RenderContext)}, called from
 * {@code BattleScreen.render()} between scissor setup and pop.
 */
public class BattleRenderer {

    private static final Logger LOG = Global.getLogger(BattleRenderer.class);

    // ---- render-only constants -----------------------------------------------

    /** Sim-seconds shots live for — must match {@code BattleSimulation.SHOT_LIFETIME}. Used to fade tracer alpha. */
    private static final float SHOT_LIFETIME_REF = 0.15f;

    /** Base unit-sprite size as a fraction of the cell (sprite fills the cell).
     *  Package-visible: the base size is shared across UNITS strata — the
     *  {@link UnitRenderService} dead sweep multiplies it by {@code renderScale}
     *  exactly as the inline live/dead passes do, so they must read one constant. */
    static final float UNIT_FRAC      = 1.00f;
    /** Gap between an entity's top edge and its HP bar (placement, caller-owned).
     *  Bar style (height/colors) lives in {@link HpBarDecor}. */
    static final float HP_BAR_GAP     = 2f;

    /** Icon tints + sizes. Sizes are fractions of {@code layout.cellSize}. */
    private static final Color  CHARGE_TINT_ACTIVE   = new Color(0xFF, 0x9A, 0x40);
    private static final Color  CHARGE_TINT_COMPLETE = new Color(0xE0, 0x40, 0x40);
    private static final Color  CHARGE_TINT_ARC      = new Color(0xFF, 0xC8, 0x70);
    private static final Color  KIT_DROP_TINT        = new Color(0x80, 0xE8, 0xFF);
    private static final float  CHARGE_ICON_SIZE     = 1.5f;
    private static final float  KIT_DROP_SIZE        = 1.0f;
    private static final float  CHARGE_PULSE_AMP     = 0.10f;
    private static final float  CHARGE_PULSE_HZ      = 1.5f;
    private static final float  KIT_DROP_PULSE_AMP   = 0.10f;
    private static final float  KIT_DROP_PULSE_HZ    = 0.6f;
    private static final int    PROGRESS_ARC_SEGMENTS = 32;

    /**
     * Sim-seconds the barrel sprite eases forward to its at-rest position after a shot.
     * Package-visible: shared by the map-turret pass here and {@link ShuttleRenderSystem}.
     */
    static final float RECOIL_DURATION = 0.12f;
    /** Peak backward displacement of the barrel sprite, as a fraction of the turret's visual long-axis (cells). Shared with {@link ShuttleRenderSystem}. */
    static final float RECOIL_DISTANCE_FRAC = 0.10f;

    private static final int GROUND_SMALL_TILE_EDGE_INSET_PX = com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer.GROUND_INSET_PX_SMALL;

    // ---- owned state ---------------------------------------------------------

    private final BattleSprites sprites;
    private RenderContext rc;

    /**
     * Per-frame draw-command collector. Cleared at the top of {@link #renderWorld}.
     * Migrated layers route through it ({@link RenderLayer#SHOTS},
     * {@link RenderLayer#DOODADS}); the rest still draw inline.
     */
    private final DrawList drawList = new DrawList();

    /**
     * Maps a sprite sheet to its per-sheet {@link QuadBatch}, so
     * {@link #drainLayer} can resolve a {@link DrawCommand} {@code SHEET_QUAD}'s sheet to
     * the batch that owns it. Populated in {@link #buildTileBatches()}; reuses the
     * same batch instances the inline tile passes use (batches reset after each
     * flush, so cross-pass reuse within a frame is fine).
     */
    private final java.util.Map<SpriteAPI, QuadBatch> batchBySheet = new java.util.IdentityHashMap<>();

    /**
     * The migrated world-render producers, in paint order. {@link #renderWorld}
     * collects every system into {@link #drawList} up front (each tags its own
     * {@link RenderSystem#layer()}, so collect order is immaterial), then drains
     * each layer in {@link RenderLayer} order — interleaving the not-yet-migrated
     * inline passes at their layer slots. As an inline pass migrates it joins this
     * list and its bespoke drain slot folds into the layer drain sequence; the
     * endgame is collect-all then drain-all with no inline passes left.
     */
    private final List<RenderSystem> worldSystems;

    /**
     * Per-sheet quad batchers. Lazily constructed in {@link #buildTileBatches()}.
     * Reused across passes.
     */
    private QuadBatch urbanBatch;
    private QuadBatch roadBatch;
    private QuadBatch floorsBatch;
    private QuadBatch waterBatch;
    private QuadBatch urbanTile3Batch;
    private QuadBatch natureBatch;

    /**
     * Solid-color batch for in-loop fills that need to share painter
     * ordering with the textured batches.
     */
    private final SolidQuadBatch solidBatch = new SolidQuadBatch(256);

    /**
     * Shared line batch for {@code LINE} commands (hitscan tracers; later the
     * convoy-debug paths + zone grid). Width is per-flush state — see {@link LineBatch}.
     */
    private final LineBatch lineBatch = new LineBatch(256);

    /**
     * Drain-owned ribbon batch for {@code RIBBON} commands (in-flight projectile
     * contrails). Threaded into {@link DrawListRenderer#drain}; the trail lifecycle
     * itself lives in {@link #contrailFx}.
     */
    private final RibbonBatch contrailBatch = new RibbonBatch(256);

    /**
     * Contrail trail lifecycle (state) — ticked from {@code BattleScreen.advance},
     * emits {@code RIBBON} commands in its world-pass {@link RenderSystem}.
     */
    private final ContrailFxService contrailFx = new ContrailFxService();

    /**
     * Persistent decal-accumulator FBO.
     */
    private final DecalAccumulator decalAccumulator =
            new DecalAccumulator(com.dillon.starsectormarines.DevConfig.DECAL_FBO_PX_PER_CELL);

    /**
     * Lightmap accumulator driving the pseudo time-of-day pass.
     */
    private final LightAccumulator lightAccumulator =
            new LightAccumulator(com.dillon.starsectormarines.DevConfig.DECAL_FBO_PX_PER_CELL);

    /**
     * Current ambient lighting preset.
     */
//    private TimeOfDay timeOfDay = TimeOfDay.NIGHT;
    private TimeOfDay timeOfDay = TimeOfDay.DAY;

    /** Ground-combat impact FX engine. */
    private final ImpactFx impactFx = new ImpactFx();

    /** Atmosphere layer — vanilla fighters flying overhead. */
    private final FlybyOverlay flybyOverlay = new FlybyOverlay();

    /** World-layer renderer for the compound capture-state markers. */
    private final CompoundMarkerRenderer compoundMarkers = new CompoundMarkerRenderer();

    // ---- constructor ---------------------------------------------------------

    public BattleRenderer(BattleSprites sprites) {
        this.sprites = sprites;
        // The full world-render pass list, in paint order — every pass now lives
        // here (collect-all → drain-all; see renderWorld). Order is verbatim today's
        // pass sequence; RenderLayer ordinal mirrors it. Within a shared layer
        // (GROUND, CONVOY) list order = submission order = paint sub-order, so the
        // debug overlay producers sit right after the body producer they paint over.
        // Stateful/own-GL passes keep their state + render* bodies on this class and
        // join via RenderSystem.of(...) emitting a Custom (the FBO/own-GL escape hatch).
        this.worldSystems = List.of(
                new GroundRenderSystem(sprites),
                // Zone debug overlay paints on top of ground tiles, under decals.
                RenderSystem.of(RenderLayer.GROUND, (ctx, out) -> {
                    if (ctx.debugZonesVisible)
                        out.addCustom(RenderLayer.GROUND, () -> renderZoneOverlayDebug(ctx.sim, ctx.alphaMult));
                }),
                RenderSystem.of(RenderLayer.DECALS, (ctx, out) ->
                        out.addCustom(RenderLayer.DECALS, () -> renderDecals(ctx.sim, ctx.alphaMult))),
                new VehicleRenderSystem(sprites),
                new DoodadRenderSystem(sprites),
                RenderSystem.of(RenderLayer.HIGHLIGHTS, (ctx, out) ->
                        out.addCustom(RenderLayer.HIGHLIGHTS, () -> ctx.highlights.render(ctx.camera, ctx.alphaMult))),
                RenderSystem.of(RenderLayer.FOG, (ctx, out) ->
                        out.addCustom(RenderLayer.FOG, () -> renderFogOverlay(ctx.sim, ctx.alphaMult))),
                new UnitRenderService(sprites),
                RenderSystem.of(RenderLayer.ROOFS, (ctx, out) ->
                        out.addCustom(RenderLayer.ROOFS, () -> renderRoofs(ctx.sim, ctx.alphaMult))),
                new DroneRenderSystem(sprites),
                RenderSystem.of(RenderLayer.OBJECTIVES, (ctx, out) ->
                        out.addCustom(RenderLayer.OBJECTIVES, () -> renderObjectiveMarkers(ctx.sim, ctx.alphaMult))),
                RenderSystem.of(RenderLayer.COMPOUND, (ctx, out) ->
                        out.addCustom(RenderLayer.COMPOUND, () -> compoundMarkers.render(
                                ctx.sim, ctx.sim.getCompoundService(), ctx.camera, ctx.alphaMult))),
                new ConvoyRenderSystem(sprites),
                // Convoy debug overlays (own-GL line passes) paint over the convoy sprites.
                RenderSystem.of(RenderLayer.CONVOY, (ctx, out) ->
                        out.addCustom(RenderLayer.CONVOY, () -> {
                            java.util.List<com.dillon.starsectormarines.battle.vehicle.Vehicle> convoy =
                                    ctx.sim.getConvoyVehicles();
                            if (DEBUG_RENDER_DOCKING_PATHS) renderConvoyDockingPathsDebug(convoy, ctx.alphaMult);
                            renderSelectedVehicleDebug(convoy, ctx.alphaMult);
                        })),
                new ShuttleRenderSystem(sprites),
                // SHOTS: contrails first (the ContrailFxService emits RIBBON commands; its
                // trail lifecycle is ticked from BattleScreen.advance), then the
                // ShotRenderService body sweeps (tracers → projectile sprites). Listed
                // contrails-first so submission order stays contrails → tracers → sprites.
                RenderSystem.of(RenderLayer.SHOTS, (ctx, out) ->
                        contrailFx.collect(out, ctx.alphaMult)),
                new ShotRenderService(sprites, impactFx),
                RenderSystem.of(RenderLayer.IMPACT_FX, (ctx, out) ->
                        out.addCustom(RenderLayer.IMPACT_FX, () -> impactFx.render(ctx.camera, ctx.alphaMult))),
                RenderSystem.of(RenderLayer.FLYBY, (ctx, out) ->
                        out.addCustom(RenderLayer.FLYBY, () -> flybyOverlay.render(ctx.camera, ctx.alphaMult))),
                RenderSystem.of(RenderLayer.LIGHTING, (ctx, out) ->
                        out.addCustom(RenderLayer.LIGHTING, () -> {
                            TimeOfDay tod = timeOfDay.evaluateAt(0f);
                            if (!tod.bypass)
                                lightAccumulator.render(ctx.camera, tod,
                                        ctx.sim.getGrid().getWidth(), ctx.sim.getGrid().getHeight(),
                                        ctx.alphaMult);
                        })));
    }

    // ---- lifecycle -----------------------------------------------------------

    /**
     * Wires the lightmap sink into the flyby overlay and ensures impact-FX sprites.
     * Called from {@code BattleScreen.attach()} after all {@code sprites.ensureX()} calls.
     */
    public void onAttach() {
        flybyOverlay.setLightAccumulator(lightAccumulator);
        impactFx.ensureSprites();
    }

    /** Builds the six per-sheet tile batches from the loaded sheets. Called from {@code BattleScreen.attach()}. */
    public void buildTileBatches() {
        if (urbanBatch == null && sprites.tileSheet() != null)
            urbanBatch = new QuadBatch(sprites.tileSheet(), sprites.tileSheetPxW(), sprites.tileSheetPxH(), 16384);
        if (roadBatch == null && sprites.roadSheet() != null)
            roadBatch = new QuadBatch(sprites.roadSheet(), sprites.roadSheetPxW(), sprites.roadSheetPxH(), 4096);
        if (floorsBatch == null && sprites.floorsSheet() != null)
            floorsBatch = new QuadBatch(sprites.floorsSheet(), sprites.floorsSheetPxW(), sprites.floorsSheetPxH(), 4096);
        if (waterBatch == null && sprites.waterSheet() != null)
            waterBatch = new QuadBatch(sprites.waterSheet(), sprites.waterSheetPxW(), sprites.waterSheetPxH(), 2048);
        if (urbanTile3Batch == null && sprites.urbanTile3Sheet() != null)
            urbanTile3Batch = new QuadBatch(sprites.urbanTile3Sheet(), sprites.urbanTile3SheetPxW(), sprites.urbanTile3SheetPxH(), 4096);
        if (natureBatch == null && sprites.natureSheet() != null)
            natureBatch = new QuadBatch(sprites.natureSheet(), sprites.natureSheetPxW(), sprites.natureSheetPxH(), 4096);

        // Register the per-sheet batches so drainLayer can resolve a SheetQuad's
        // sheet to its batch. Same instances the inline tile passes use.
        registerBatch(sprites.tileSheet(), urbanBatch);
        registerBatch(sprites.roadSheet(), roadBatch);
        registerBatch(sprites.floorsSheet(), floorsBatch);
        registerBatch(sprites.waterSheet(), waterBatch);
        registerBatch(sprites.urbanTile3Sheet(), urbanTile3Batch);
        registerBatch(sprites.natureSheet(), natureBatch);

        // Sprite-sheet batches for the VEHICLES + CONVOY layers (Vehicle/Convoy
        // RenderSystems). Their sheets are loaded by ensureVehicleSheets() /
        // ensureConvoySprites() before this runs (BattleScreen.attach order).
        // batchBySheet owns the QuadBatch refs.
        registerSpriteSheetBatches(sprites.vehicleSheets().values());
        registerSpriteSheetBatches(sprites.convoySprites().values());

        // Unit sheets for the UNITS layer (UnitRenderService sprite sweeps) so
        // dead + live infantry and secondary-aim poses batch via SHEET_QUAD.
        // Loaded by ensureUnitSheets()/ensureMarineSecondarySprites() before this
        // runs (BattleScreen.attach order).
        registerSpriteSheetBatches(sprites.unitDeadSprites().values());
        registerSpriteSheetBatches(sprites.unitSprites().values());
        registerSpriteSheetBatches(sprites.marineSecondaryAimSheets().values());
    }

    /** Builds + registers one {@link QuadBatch} per distinct sheet in {@code caches} (idempotent). */
    private void registerSpriteSheetBatches(java.util.Collection<UnitSpriteCache> caches) {
        for (UnitSpriteCache cache : caches) {
            if (cache == null || cache.sheet == null || cache.frames == null) continue;
            if (batchBySheet.containsKey(cache.sheet)) continue;
            registerBatch(cache.sheet,
                    new QuadBatch(cache.sheet, cache.frames.sheetWidth, cache.frames.sheetHeight, 256));
        }
    }

    private void registerBatch(SpriteAPI sheet, QuadBatch batch) {
        if (sheet != null && batch != null) batchBySheet.put(sheet, batch);
    }

    // ---- accessors for BattleScreen.advance() --------------------------------

    /** Accessor for {@code BattleScreen.advance()} — push fighter vision each frame. */
    public FlybyOverlay getFlybyOverlay() { return flybyOverlay; }

    /** Accessor for {@code BattleScreen.advance()} — spawn and advance impact FX particles. */
    public ImpactFx getImpactFx() { return impactFx; }

    /** Accessor for {@code BattleScreen.advance()} — tick the contrail trail lifecycle on real dt. */
    public ContrailFxService getContrailFx() { return contrailFx; }

    /** Accessor for {@code BattleScreen.advance()} — pulse compound markers on wall-clock. */
    public CompoundMarkerRenderer getCompoundMarkers() { return compoundMarkers; }

    /** Accessor for {@code BattleScreen.advance()} — tick transient lights + retain persistent halos. */
    public LightAccumulator getLightAccumulator() { return lightAccumulator; }

    /** Accessor for {@code BattleScreen.detach()} — release FBO resources. */
    public DecalAccumulator getDecalAccumulator() { return decalAccumulator; }

    @DebugOnly
    private void renderZoneOverlayDebug(BattleSimulation sim, float alphaMult) {
        com.dillon.starsectormarines.battle.nav.zone.ZoneGraph zones = sim.getZoneGraph();
        NavigationGrid grid = sim.getGrid();
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int zoneId = zones.zoneIdAt(x, y);
                if (zoneId < 0) continue;
                int h = zoneId * 0x9E3779B1; // Knuth multiplicative hash for stable color spread
                float r = ((h       ) & 0xFF) / 255f;
                float g = ((h >>> 8 ) & 0xFF) / 255f;
                float b = ((h >>> 16) & 0xFF) / 255f;
                glColor4f(r, g, b, 0.30f * alphaMult);
                float x0 = rc.camera.cellToScreenX(x);
                float y0 = rc.camera.cellToScreenY(y);
                float c = rc.camera.cellPxSize();
                float x1 = x0 + c;
                float y1 = y0 + c;
                glVertex2f(x0, y0);
                glVertex2f(x1, y0);
                glVertex2f(x1, y1);
                glVertex2f(x0, y1);
            }
        }
        glEnd();
    }

    private void renderDecals(BattleSimulation sim, float alphaMult) {
        if (sprites.decalSheet() == null || sprites.decalFrames() == null) return;
        decalAccumulator.render(
                rc.camera,
                sim.getGrid().getWidth(), sim.getGrid().getHeight(),
                sim.getDecals(), sim.getDecalsEverAdded(),
                sprites.decalSheet(), sprites.decalFrames(),
                alphaMult);
    }

    private void renderFogOverlay(BattleSimulation sim, float alphaMult) {
        com.dillon.starsectormarines.battle.vision.VisionService vis = sim.getVision();
        if (!vis.isInitialized()) return;

        boolean[] revealed = vis.cellRevealedArray();
        int gw = vis.gridWidth();
        int gh = vis.gridHeight();
        float cellPx = rc.camera.cellPxSize();

        int margin = 8;
        int minCellX = Math.max(0, (int) Math.floor(rc.camera.screenToCellX(rc.camera.vpX())) - margin);
        int maxCellX = Math.min(gw - 1, (int) Math.ceil(rc.camera.screenToCellX(rc.camera.vpX() + rc.camera.vpW())) + margin);
        int minCellY = Math.max(0, (int) Math.floor(rc.camera.screenToCellY(rc.camera.vpY())) - margin);
        int maxCellY = Math.min(gh - 1, (int) Math.ceil(rc.camera.screenToCellY(rc.camera.vpY() + rc.camera.vpH())) + margin);

        for (int cy = minCellY; cy <= maxCellY; cy++) {
            int rowBase = cy * gw;
            for (int cx = minCellX; cx <= maxCellX; cx++) {
                int idx = rowBase + cx;
                float fogAlpha;
                if (!revealed[idx]) {
                    fogAlpha = 0.85f;
                } else {
                    int darkNeighbors = 0;
                    if (cy > 0    && !revealed[idx - gw]) darkNeighbors++;
                    if (cy < gh-1 && !revealed[idx + gw]) darkNeighbors++;
                    if (cx > 0    && !revealed[idx - 1])  darkNeighbors++;
                    if (cx < gw-1 && !revealed[idx + 1])  darkNeighbors++;
                    if (darkNeighbors == 0) continue;
                    fogAlpha = 0.15f * darkNeighbors;
                }

                float sx = rc.camera.cellToScreenX(cx);
                float sy = rc.camera.cellToScreenY(cy);
                solidBatch.appendRect(sx, sy, sx + cellPx, sy + cellPx,
                        0f, 0f, 0f, fogAlpha * alphaMult);
            }
        }

        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            solidBatch.flush();
        }
    }

    private void renderRoofs(BattleSimulation sim, float alphaMult) {
        com.dillon.starsectormarines.battle.world.model.Buildings buildings = sim.getBuildings();
        if (buildings == null || buildings.isEmpty()) return;
        sprites.ensureFloorsSheet();
        if (sprites.floorsSheet() == null || floorsBatch == null) return;

        CellTopology topology = sim.getTopology();
        for (com.dillon.starsectormarines.battle.world.model.Building b : buildings.all()) {
            float roofAlpha = b.currentAlpha;
            if (roofAlpha <= 0.01f) continue;
            for (int i = 0, n = b.cellCount(); i < n; i++) {
                int cx = b.cellsX[i];
                int cy = b.cellsY[i];
                if (topology.isRoofDestroyed(cx, cy)) continue;
                TileManifest.TileFrame f = TileManifest.pickBrickTile(cx, cy);
                appendSmallTileTinted(floorsBatch, f, cx, cy,
                        b.tintR, b.tintG, b.tintB, roofAlpha * alphaMult);
            }
        }

        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            floorsBatch.flush();
        }
    }

    private void appendSmallTileTinted(QuadBatch batch, TileManifest.TileFrame f,
                                       int gridX, int gridY,
                                       float r, float g, float b, float alphaMult) {
        if (batch == null) return;
        int inset = GROUND_SMALL_TILE_EDGE_INSET_PX;
        int srcPxX = f.col * TileManifest.FLOORS_TILE_SIZE + inset;
        int srcTopPxY = f.row * TileManifest.FLOORS_TILE_SIZE + inset;
        int srcPxW = TileManifest.FLOORS_TILE_SIZE - 2 * inset;
        int srcPxH = TileManifest.FLOORS_TILE_SIZE - 2 * inset;

        float cellPx = rc.camera.cellPxSize();
        float cx = rc.camera.cellToScreenX(gridX + 0.5f);
        float cy = rc.camera.cellToScreenY(gridY + 0.5f);
        batch.append(srcPxX, srcTopPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                r, g, b, alphaMult);
    }

    /** Debug flag — draws Reeds-Shepp docking paths under each docking truck for math iteration. */
    @DebugOnly
    public static boolean DEBUG_RENDER_DOCKING_PATHS = true;

    @DebugOnly
    private void renderConvoyDockingPathsDebug(java.util.List<com.dillon.starsectormarines.battle.vehicle.Vehicle> convoy,
                                               float alphaMult) {
        boolean any = false;
        for (com.dillon.starsectormarines.battle.vehicle.Vehicle v : convoy) {
            if (v.dockingPath != null) { any = true; break; }
        }
        if (!any) return;

        org.lwjgl.opengl.GL11.glPushAttrib(
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
                | org.lwjgl.opengl.GL11.GL_ENABLE_BIT
                | org.lwjgl.opengl.GL11.GL_CURRENT_BIT
                | org.lwjgl.opengl.GL11.GL_LINE_BIT);
        org.lwjgl.opengl.GL11.glColorMask(true, true, true, true);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glBlendFunc(
                org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glLineWidth(2.5f);

        final float STEP_CELLS = 0.2f;
        for (com.dillon.starsectormarines.battle.vehicle.Vehicle v : convoy) {
            com.dillon.starsectormarines.battle.vehicle.ReedsShepp.Path path = v.dockingPath;
            if (path == null) continue;
            com.dillon.starsectormarines.battle.vehicle.Pose start = v.dockingStartPose;
            float R = v.dockingTurnRadius;

            float cursor = 0f;
            for (com.dillon.starsectormarines.battle.vehicle.ReedsShepp.Element e : path.elements) {
                float segCells = e.length * R;
                if (segCells <= 0f) continue;
                float segEnd = cursor + segCells;

                if (e.forward) org.lwjgl.opengl.GL11.glColor4f(0.2f, 1f, 0.3f, 0.85f * alphaMult);
                else           org.lwjgl.opengl.GL11.glColor4f(1f, 0.25f, 0.2f, 0.85f * alphaMult);

                org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINE_STRIP);
                for (float d = cursor; d <= segEnd; d += STEP_CELLS) {
                    com.dillon.starsectormarines.battle.vehicle.Pose p =
                            com.dillon.starsectormarines.battle.vehicle.ReedsShepp.sample(start, R, path, d);
                    org.lwjgl.opengl.GL11.glVertex2f(rc.camera.cellToScreenX(p.x), rc.camera.cellToScreenY(p.y));
                }
                com.dillon.starsectormarines.battle.vehicle.Pose endP =
                        com.dillon.starsectormarines.battle.vehicle.ReedsShepp.sample(start, R, path, segEnd);
                org.lwjgl.opengl.GL11.glVertex2f(rc.camera.cellToScreenX(endP.x), rc.camera.cellToScreenY(endP.y));
                org.lwjgl.opengl.GL11.glEnd();

                cursor = segEnd;
            }
        }

        org.lwjgl.opengl.GL11.glPopAttrib();
    }

    @DebugOnly
    private void renderSelectedVehicleDebug(
            java.util.List<com.dillon.starsectormarines.battle.vehicle.Vehicle> convoy,
            float alphaMult) {
        int idx = rc.selection.getSelectedVehicleIdx();
        if (idx < 0 || idx >= convoy.size()) return;
        com.dillon.starsectormarines.battle.vehicle.Vehicle v = convoy.get(idx);

        org.lwjgl.opengl.GL11.glPushAttrib(
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
                | org.lwjgl.opengl.GL11.GL_ENABLE_BIT
                | org.lwjgl.opengl.GL11.GL_CURRENT_BIT
                | org.lwjgl.opengl.GL11.GL_LINE_BIT);
        org.lwjgl.opengl.GL11.glColorMask(true, true, true, true);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glBlendFunc(
                org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);

        float[] xs = (v.state == com.dillon.starsectormarines.battle.vehicle.Vehicle.State.DEPARTING)
                ? v.outboundX : v.inboundX;
        float[] ys = (v.state == com.dillon.starsectormarines.battle.vehicle.Vehicle.State.DEPARTING)
                ? v.outboundY : v.inboundY;

        org.lwjgl.opengl.GL11.glLineWidth(2f);
        if (xs.length > 1) {
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINE_STRIP);
            for (int i = 0; i < xs.length; i++) {
                if (i < v.waypointIndex) {
                    org.lwjgl.opengl.GL11.glColor4f(0.5f, 0.5f, 0.5f, 0.4f * alphaMult);
                } else {
                    org.lwjgl.opengl.GL11.glColor4f(0.2f, 0.8f, 1f, 0.8f * alphaMult);
                }
                org.lwjgl.opengl.GL11.glVertex2f(
                        rc.camera.cellToScreenX(xs[i]), rc.camera.cellToScreenY(ys[i]));
            }
            org.lwjgl.opengl.GL11.glEnd();
        }

        org.lwjgl.opengl.GL11.glPointSize(6f);
        if (v.waypointIndex >= 0 && v.waypointIndex < xs.length) {
            org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 0f, 0.9f * alphaMult);
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_POINTS);
            org.lwjgl.opengl.GL11.glVertex2f(
                    rc.camera.cellToScreenX(xs[v.waypointIndex]),
                    rc.camera.cellToScreenY(ys[v.waypointIndex]));
            org.lwjgl.opengl.GL11.glEnd();
        }

        org.lwjgl.opengl.GL11.glColor4f(0f, 1f, 0.3f, 0.9f * alphaMult);
        org.lwjgl.opengl.GL11.glPointSize(5f);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_POINTS);
        org.lwjgl.opengl.GL11.glVertex2f(
                rc.camera.cellToScreenX(v.body.x), rc.camera.cellToScreenY(v.body.y));
        org.lwjgl.opengl.GL11.glEnd();

        org.lwjgl.opengl.GL11.glPopAttrib();

        float textX = rc.camera.cellToScreenX(v.body.x) + 20f;
        float textY = rc.camera.cellToScreenY(v.body.y) + 40f;
        float lineH = 16f;
        com.dillon.starsectormarines.ui.BitmapFont font = com.dillon.starsectormarines.ui.Fonts.INSIGNIA_15_AA;
        java.awt.Color c = new java.awt.Color(0.8f, 1f, 0.8f, 1f);

        font.drawString(String.format("state: %s  wp: %d/%d", v.state, v.waypointIndex, xs.length),
                textX, textY, c, alphaMult);
        textY -= lineH;
        font.drawString(String.format("speed: %.1f  facing: %.0f  stuck: %.2fs",
                v.body.speed, v.body.facingDegrees, v.wallStuckTime), textX, textY, c, alphaMult);
        textY -= lineH;
        String pathLabel = (v.inboundHeading != null || v.outboundHeading != null)
                ? "playback" : v.pathRefined ? "HA*" : "coarse";
        font.drawString(String.format("pos: (%.1f, %.1f)  path: %s  wps: %d+%d  prog: %.1f",
                v.body.x, v.body.y, pathLabel,
                v.inboundX.length, v.outboundX.length,
                v.playbackProgress), textX, textY, c, alphaMult);
        textY -= lineH;
        if (v.type.hasTurretWeapon()) {
            font.drawString(String.format("turret: ammo=%d  facing=%.0f",
                    v.turretAmmo, v.turretFacingDeg), textX, textY, c, alphaMult);
            textY -= lineH;
        }
        font.drawString("[F5] dump state to JSON", textX, textY,
                new java.awt.Color(0.6f, 0.6f, 0.6f, 1f), alphaMult * 0.7f);
    }

    private void renderObjectiveMarkers(BattleSimulation sim, float alphaMult) {
        float now = (float) (System.currentTimeMillis() / 1000.0);

        float cellPx = rc.camera.cellPxSize();
        for (Objective o : sim.getObjectives()) {
            if (!(o instanceof ChargeSiteObjective)) continue;
            ChargeSiteObjective site = (ChargeSiteObjective) o;
            float cx = rc.camera.cellToScreenX(site.cellX() + 0.5f);
            float cy = rc.camera.cellToScreenY(site.cellY() + 0.5f);
            if (site.isComplete()) {
                drawTintedIcon(sprites.iconDanger(), cx, cy,
                        cellPx * CHARGE_ICON_SIZE,
                        CHARGE_TINT_COMPLETE, alphaMult);
            } else {
                float pulse = site.planterOnSite()
                        ? 1f + CHARGE_PULSE_AMP * (float) Math.sin(now * 2.0 * Math.PI * CHARGE_PULSE_HZ)
                        : 1f;
                drawTintedIcon(sprites.iconAlarm(), cx, cy,
                        cellPx * CHARGE_ICON_SIZE * pulse,
                        CHARGE_TINT_ACTIVE, alphaMult);
                float progress = site.progress() / Math.max(0.001f, site.plantDuration());
                float innerR = cellPx * 0.55f;
                float outerR = innerR + Math.max(3f, cellPx * 0.12f);
                drawProgressArc(cx, cy, innerR, outerR, progress, CHARGE_TINT_ARC, alphaMult);
            }
        }

        for (EquipmentDrop drop : sim.getEquipmentDrops()) {
            if (drop.consumed) continue;
            float cx = rc.camera.cellToScreenX(drop.cellX + 0.5f);
            float cy = rc.camera.cellToScreenY(drop.cellY + 0.5f);
            float pulse = 1f + KIT_DROP_PULSE_AMP * (float) Math.sin(now * 2.0 * Math.PI * KIT_DROP_PULSE_HZ);
            drawTintedIcon(sprites.iconStar(), cx, cy,
                    cellPx * KIT_DROP_SIZE * pulse,
                    KIT_DROP_TINT, alphaMult);
        }
    }

    private void drawTintedIcon(SpriteAPI sprite, float cx, float cy, float size, Color tint, float alphaMult) {
        if (sprite == null) {
            fillRect(cx - size / 2f, cy - size / 2f, size, size, tint, alphaMult);
            return;
        }
        sprite.setSize(size, size);
        sprite.setColor(tint);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.renderAtCenter(cx, cy);
        sprite.setColor(Color.WHITE);
    }

    private void drawProgressArc(float cx, float cy, float innerR, float outerR,
                                 float progress, Color color, float alphaMult) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (progress <= 0f) return;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alphaMult);

        int filled = (int) Math.ceil(PROGRESS_ARC_SEGMENTS * progress);
        glBegin(GL_QUADS);
        for (int i = 0; i < filled; i++) {
            float t1 = (float) i / PROGRESS_ARC_SEGMENTS;
            float t2 = Math.min(progress, (float) (i + 1) / PROGRESS_ARC_SEGMENTS);
            float a1 = (float) (Math.PI / 2.0) - t1 * (float) (Math.PI * 2.0);
            float a2 = (float) (Math.PI / 2.0) - t2 * (float) (Math.PI * 2.0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            glVertex2f(cx + c1 * innerR, cy + s1 * innerR);
            glVertex2f(cx + c1 * outerR, cy + s1 * outerR);
            glVertex2f(cx + c2 * outerR, cy + s2 * outerR);
            glVertex2f(cx + c2 * innerR, cy + s2 * innerR);
        }
        glEnd();
    }


    private static void fillRect(float rx, float ry, float rw, float rh, Color c, float alpha) {
        if (rw <= 0f || rh <= 0f) return;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(rx,      ry);
        glVertex2f(rx + rw, ry);
        glVertex2f(rx + rw, ry + rh);
        glVertex2f(rx,      ry + rh);
        glEnd();
    }

    // ---- draw-list drain ------------------------------------------------------

    /**
     * Drains one layer's queued {@link DrawCommand}s through the engine
     * {@link DrawListRenderer}. The game side owns <em>which</em> layer and the
     * paint order; the engine owns <em>how</em> the command stream batches and
     * flushes. Generalizes the manual batch-and-flush pattern formerly inlined
     * per tile pass, now driven by {@link GroundRenderSystem}.
     */
    private void drainLayer(RenderLayer layer) {
        DrawListRenderer.drain(drawList.buffer(layer), drawList.count(layer), batchBySheet, solidBatch,
                lineBatch, contrailBatch, rc.camera);
    }

    // ---- main entry point ----------------------------------------------------

    /**
     * Renders all world-layer passes. Called from {@code BattleScreen.render()} between
     * scissor setup and {@code glPopAttrib}.
     *
     * <p><strong>collect-all → drain-all.</strong> Every pass now lives in
     * {@link #worldSystems} (in paint order). The collect phase appends each
     * system's commands into its layer buffer — GL-free and order-immaterial
     * <em>across</em> layers (commands are layer-tagged); within a shared layer,
     * registry list order is submission order. The drain phase then replays layers
     * in {@link RenderLayer} order, which <em>is</em> paint order (ordinal = paint
     * order), batching/flushing each. The per-seam ordering rationale lives in the
     * {@link RenderLayer} javadoc — do not re-derive it here.
     */
    public void renderWorld(RenderContext rc) {
        this.rc = rc;
        drawList.clear();
        for (RenderSystem system : worldSystems) {
            system.collect(rc, drawList);
        }
        for (RenderLayer layer : RenderLayer.values()) {
            drainLayer(layer);
        }
    }
}
