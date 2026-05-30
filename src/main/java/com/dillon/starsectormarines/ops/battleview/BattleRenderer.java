package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.render2d.DecalAccumulator;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.flyby.FlybyOverlay;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.TimeOfDay;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.UrbanTile3;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ui.compound.CompoundMarkerRenderer;
import com.dillon.starsectormarines.render2d.ContrailStyle;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.render2d.DrawListRenderer;
import com.dillon.starsectormarines.render2d.ContrailTrail;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.LightAccumulator;
import com.dillon.starsectormarines.render2d.LightKernel;
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

    private static final Color MARINE_COLOR   = new Color(0x5A, 0xA0, 0xE0);
    private static final Color DEFENDER_COLOR = new Color(0xE0, 0x6A, 0x6A);
    private static final Color CIVILIAN_COLOR = new Color(0xC8, 0xC8, 0x80);
    /** Dual-use in BattleScreen (spawnImpactFx); duplicated here for zero back-dependency. */
    private static final Color MARINE_TRACER  = new Color(0xFF, 0xE0, 0x70);
    /** Dual-use in BattleScreen (spawnImpactFx); duplicated here for zero back-dependency. */
    private static final Color DEFENDER_TRACER = new Color(0xFF, 0x70, 0x40);

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

    /** Window (s) after a unit fires during which we show the weapon-up pose. */
    private static final float WEAPON_UP_TIME = 0.25f;

    /**
     * Sim-seconds the barrel sprite eases forward to its at-rest position after a shot.
     * Package-visible: shared by the map-turret pass here and {@link ShuttleRenderSystem}.
     */
    static final float RECOIL_DURATION = 0.12f;
    /** Peak backward displacement of the barrel sprite, as a fraction of the turret's visual long-axis (cells). Shared with {@link ShuttleRenderSystem}. */
    static final float RECOIL_DISTANCE_FRAC = 0.10f;

    /** Turret-pad fill (shared road color). The tile/crosswalk/courtyard fills moved to {@code GroundRenderSystem}. */
    private static final Color ROAD_FILL      = new Color(TileManifest.ROAD_FILL_RGB);

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
     * {@link #drainLayer} can resolve a {@link DrawCommand.SheetQuad}'s sheet to
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
     * Solid-color ribbon batch for in-flight projectile contrails.
     */
    private final RibbonBatch contrailBatch = new RibbonBatch(256);

    /**
     * Active contrails keyed by their owning shot. Identity map.
     */
    private final java.util.IdentityHashMap<ShotEvent, ContrailTrail> contrailsLive =
            new java.util.IdentityHashMap<>();

    /**
     * Trails whose owning shot has expired but whose samples haven't all aged out yet.
     */
    private final java.util.ArrayList<ContrailTrail> contrailsDecaying = new java.util.ArrayList<>();

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
        this.worldSystems = List.of(
                new GroundRenderSystem(sprites),
                new VehicleRenderSystem(sprites),
                new DoodadRenderSystem(sprites),
                new UnitRenderService(sprites),
                new DroneRenderSystem(sprites),
                new ConvoyRenderSystem(sprites),
                new ShuttleRenderSystem(sprites));
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

        // Dead-unit corpse sheets for the UNITS layer (UnitRenderService dead
        // sweep, slice J3) so dead infantry batch via SHEET_QUAD. Loaded by
        // ensureUnitSprites() before this runs (BattleScreen.attach order).
        registerSpriteSheetBatches(sprites.unitDeadSprites().values());
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

    /** Accessor for {@code BattleScreen.advance()} — pulse compound markers on wall-clock. */
    public CompoundMarkerRenderer getCompoundMarkers() { return compoundMarkers; }

    /** Accessor for {@code BattleScreen.advance()} — tick transient lights + retain persistent halos. */
    public LightAccumulator getLightAccumulator() { return lightAccumulator; }

    /** Accessor for {@code BattleScreen.detach()} — release FBO resources. */
    public DecalAccumulator getDecalAccumulator() { return decalAccumulator; }

    private void renderZoneOverlay(BattleSimulation sim, float alphaMult) {
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

    private void renderUnits(BattleSimulation sim, List<Unit> units, float alphaMult) {
        float unitSize = rc.camera.cellPxSize() * UNIT_FRAC;
        float half = unitSize / 2f;
        com.dillon.starsectormarines.battle.vision.VisionService vis = sim.getVision();

        renderTurrets(units, alphaMult);
        renderDroneHubs(units, alphaMult);
        // Dead-unit corpse sweep — migrated to the UNITS layer (UnitRenderService,
        // slice J3); drained here at the dead-units slot so paint order holds
        // (turrets → hubs → dead → live → bars). Collected up front in renderWorld.
        drainLayer(RenderLayer.UNITS);

        java.util.Set<UnitSpriteCache> tintedThisFrame = new java.util.HashSet<>();
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            if (u instanceof MapTurret) continue;
            if (u instanceof com.dillon.starsectormarines.battle.drone.DroneHubUnit) continue;
            if (u instanceof com.dillon.starsectormarines.battle.drone.Drone) continue;
            byte uv = vis.getUnitVisibility(u.denseIdx);
            if (uv == com.dillon.starsectormarines.battle.vision.VisionService.VIS_HIDDEN) continue;
            float unitAlpha = alphaMult;
            if (uv == com.dillon.starsectormarines.battle.vision.VisionService.VIS_FADING) {
                unitAlpha *= vis.getFadeAlpha(u.denseIdx);
            }
            UnitSpriteCache cache = sprites.unitSprites().get(u.type);
            if (u.getSecondaryActionTimer() > 0f && u.secondaryWeapon != null) {
                UnitSpriteCache aim = sprites.marineSecondaryAimSheets().get(u.secondaryWeapon);
                if (aim != null && aim.sheet != null && aim.frames != null
                        && aim.frames.frames.length > 0) {
                    cache = aim;
                }
            }
            if (cache == null || cache.sheet == null || cache.frames == null
                    || cache.frames.frames.length == 0) {
                renderUnitQuadFallback(u, unitSize, half, unitAlpha);
                continue;
            }
            renderUnitSprite(u, cache, unitSize, unitAlpha);
            tintedThisFrame.add(cache);
        }
        for (UnitSpriteCache cache : tintedThisFrame) {
            cache.sheet.setColor(Color.WHITE);
        }

        for (Unit u : units) {
            if (!u.isAlive()) continue;
            if (!u.type.combatant) continue;
            if (u instanceof com.dillon.starsectormarines.battle.drone.Drone) continue;
            byte uv = vis.getUnitVisibility(u.denseIdx);
            if (uv == com.dillon.starsectormarines.battle.vision.VisionService.VIS_HIDDEN) continue;
            float barAlpha = alphaMult;
            if (uv == com.dillon.starsectormarines.battle.vision.VisionService.VIS_FADING) {
                barAlpha *= vis.getFadeAlpha(u.denseIdx);
            }
            float cx = rc.camera.cellToScreenX(u.getRenderX() + 0.5f);
            float cy = rc.camera.cellToScreenY(u.getRenderY() + 0.5f);
            float barW = unitSize;
            float barX = cx - barW / 2f;
            float barY;
            if (u instanceof MapTurret) {
                float visual = ((MapTurret) u).kind.visualCells;
                barY = cy + visual * rc.camera.cellPxSize() / 2f + HP_BAR_GAP;
            } else if (u instanceof com.dillon.starsectormarines.battle.drone.DroneHubUnit) {
                float visual = com.dillon.starsectormarines.battle.drone.DroneHubUnit.VISUAL_CELLS;
                barY = cy + visual * rc.camera.cellPxSize() / 2f + HP_BAR_GAP;
            } else {
                barY = cy + half + HP_BAR_GAP;
            }
            fillRect(barX, barY, barW, HpBarDecor.HP_BAR_H, HpBarDecor.HP_BG, barAlpha);
            float frac = Math.max(0f, Math.min(1f, u.getHp() / u.getMaxHp()));
            fillRect(barX, barY, barW * frac, HpBarDecor.HP_BAR_H, HpBarDecor.HP_FG, barAlpha);
        }
    }

    private void renderUnitSprite(Unit u, UnitSpriteCache cache, float unitSize, float alphaMult) {
        SpriteAPI sheet = cache.sheet;
        SpriteSheetFrames frames = cache.frames;
        float texW = sheet.getTextureWidth();
        float texH = sheet.getTextureHeight();
        int sheetW = frames.sheetWidth;
        int sheetH = frames.sheetHeight;

        boolean inAim = u.getSecondaryActionTimer() > 0f && u.secondaryWeapon != null;
        boolean weaponUp = inAim || (u.type.combatant
                && u.getCooldownTimer() > (u.attackCooldown - WEAPON_UP_TIME)
                && u.getCooldownTimer() > 0f);

        int frameIdx;
        boolean flipY;
        if (u.type.frameLayout == UnitType.FrameLayout.EIGHT_WAY_NO_WEAPON_UP) {
            EightWayFacing ef = computeEightWayFacing(u, rc.sim);
            frameIdx = pickFrameEightWay(ef);
            flipY = false;
        } else {
            Facing facing = computeFacing(u, rc.sim);
            frameIdx = pickFrame(facing, weaponUp);
            flipY = weaponUp && facing == Facing.SOUTH;
        }
        if (frameIdx >= frames.frames.length) frameIdx = 0;
        SpriteSheetFrames.Frame f = frames.frames[frameIdx];

        sheet.setTexX((float) f.x * texW / sheetW);
        sheet.setTexWidth((float) f.w * texW / sheetW);
        if (flipY) {
            sheet.setTexY((float) (sheetH - f.y) * texH / sheetH);
            sheet.setTexHeight(-(float) f.h * texH / sheetH);
        } else {
            sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
            sheet.setTexHeight((float) f.h * texH / sheetH);
        }
        float targetH = unitSize * u.type.renderScale;
        float targetW = targetH * f.w / (float) f.h;
        sheet.setSize(targetW, targetH);
        sheet.setAlphaMult(alphaMult);
        sheet.setNormalBlend();
        sheet.setColor(Color.WHITE);
        float cx = rc.camera.cellToScreenX(u.getRenderX() + 0.5f);
        float cy = rc.camera.cellToScreenY(u.getRenderY() + 0.5f);
        sheet.renderAtCenter(cx, cy);
    }

    private void renderTurrets(List<Unit> units, float alphaMult) {
        boolean any = false;
        for (Unit u : units) {
            if (u instanceof MapTurret && u.isAlive()) { any = true; break; }
        }
        if (!any) return;

        float cellPx = rc.camera.cellPxSize();

        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        glColor4f(ROAD_FILL.getRed() / 255f, ROAD_FILL.getGreen() / 255f,
                ROAD_FILL.getBlue() / 255f, alphaMult);
        for (Unit u : units) {
            if (!(u instanceof MapTurret) || !u.isAlive()) continue;
            float x0 = rc.camera.cellToScreenX(u.getCellX());
            float y0 = rc.camera.cellToScreenY(u.getCellY());
            glVertex2f(x0,          y0);
            glVertex2f(x0 + cellPx, y0);
            glVertex2f(x0 + cellPx, y0 + cellPx);
            glVertex2f(x0,          y0 + cellPx);
        }
        glEnd();

        java.util.Set<TurretKind> touched = new java.util.HashSet<>();
        java.util.Set<TurretKind> touchedRecoil = new java.util.HashSet<>();
        for (Unit u : units) {
            if (!(u instanceof MapTurret) || !u.isAlive()) continue;
            MapTurret t = (MapTurret) u;
            ShuttleSpriteCache base = sprites.turretSprites().get(t.kind);
            if (base == null) {
                renderTurretQuadFallback(t, cellPx * UNIT_FRAC, alphaMult);
                continue;
            }
            float cx = rc.camera.cellToScreenX(t.getCellX() + 0.5f);
            float cy = rc.camera.cellToScreenY(t.getCellY() + 0.5f);

            ShuttleSpriteCache barrel = sprites.turretRecoilSprites().get(t.kind);
            if (barrel != null) {
                float recoilT = 0f;
                if (t.recoilTimer < RECOIL_DURATION) {
                    recoilT = 1f - t.recoilTimer / RECOIL_DURATION;
                }
                float pushPx = recoilT * RECOIL_DISTANCE_FRAC * t.kind.visualCells * cellPx;
                double rad = Math.toRadians(t.facingDegrees);
                float bx =  (float) Math.sin(rad)  * pushPx;
                float by = -(float) Math.cos(rad)  * pushPx;
                drawTurretLayer(barrel, t.facingDegrees, t.kind.visualCells, cellPx, cx + bx, cy + by, alphaMult);
                touchedRecoil.add(t.kind);
            }
            drawTurretLayer(base, t.facingDegrees, t.kind.visualCells, cellPx, cx, cy, alphaMult);
            touched.add(t.kind);
        }
        for (TurretKind k : touched) {
            ShuttleSpriteCache c = sprites.turretSprites().get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
        for (TurretKind k : touchedRecoil) {
            ShuttleSpriteCache c = sprites.turretRecoilSprites().get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
    }

    private void renderDroneHubs(List<Unit> units, float alphaMult) {
        boolean any = false;
        for (Unit u : units) {
            if (u instanceof com.dillon.starsectormarines.battle.drone.DroneHubUnit && u.isAlive()) { any = true; break; }
        }
        if (!any) return;
        sprites.ensureDroneHubSprite();

        float cellPx = rc.camera.cellPxSize();

        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        glColor4f(ROAD_FILL.getRed() / 255f, ROAD_FILL.getGreen() / 255f,
                ROAD_FILL.getBlue() / 255f, alphaMult);
        for (Unit u : units) {
            if (!(u instanceof com.dillon.starsectormarines.battle.drone.DroneHubUnit) || !u.isAlive()) continue;
            float x0 = rc.camera.cellToScreenX(u.getCellX());
            float y0 = rc.camera.cellToScreenY(u.getCellY());
            glVertex2f(x0,          y0);
            glVertex2f(x0 + cellPx, y0);
            glVertex2f(x0 + cellPx, y0 + cellPx);
            glVertex2f(x0,          y0 + cellPx);
        }
        glEnd();

        if (sprites.droneHubSprite() == null) return;
        float visual = com.dillon.starsectormarines.battle.drone.DroneHubUnit.VISUAL_CELLS;
        for (Unit u : units) {
            if (!(u instanceof com.dillon.starsectormarines.battle.drone.DroneHubUnit) || !u.isAlive()) continue;
            float cx = rc.camera.cellToScreenX(u.getCellX() + 0.5f);
            float cy = rc.camera.cellToScreenY(u.getCellY() + 0.5f);
            drawTurretLayer(sprites.droneHubSprite(), 0f, visual, cellPx, cx, cy, alphaMult);
        }
        sprites.droneHubSprite().sprite.setAngle(0f);
    }

    private static void drawTurretLayer(ShuttleSpriteCache cache, float facingDegrees, float visualCells,
                                        float cellPx, float cx, float cy, float alphaMult) {
        SpriteAPI sprite = cache.sprite;
        float pxH = visualCells * cellPx;
        float pxW = pxH * cache.aspect;
        sprite.setSize(pxW, pxH);
        sprite.setAngle(facingDegrees);
        sprite.setAlphaMult(alphaMult);
        sprite.setNormalBlend();
        sprite.setColor(Color.WHITE);
        sprite.renderAtCenter(cx, cy);
    }

    private void renderTurretQuadFallback(MapTurret t, float unitSize, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(DEFENDER_COLOR.getRed() / 255f, DEFENDER_COLOR.getGreen() / 255f,
                DEFENDER_COLOR.getBlue() / 255f, alphaMult);
        float cx = rc.camera.cellToScreenX(t.getCellX() + 0.5f);
        float cy = rc.camera.cellToScreenY(t.getCellY() + 0.5f);
        float half = unitSize / 2f;
        glBegin(GL_QUADS);
        glVertex2f(cx - half, cy - half);
        glVertex2f(cx + half, cy - half);
        glVertex2f(cx + half, cy + half);
        glVertex2f(cx - half, cy + half);
        glEnd();
    }

    private void renderUnitQuadFallback(Unit u, float unitSize, float half, float alphaMult) {
        Color c;
        if (u.faction == Faction.MARINE)       c = MARINE_COLOR;
        else if (u.faction == Faction.DEFENDER) c = DEFENDER_COLOR;
        else                                    c = CIVILIAN_COLOR;
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alphaMult);
        float cx = rc.camera.cellToScreenX(u.getRenderX() + 0.5f);
        float cy = rc.camera.cellToScreenY(u.getRenderY() + 0.5f);
        glVertex2f(cx - half, cy - half);
        glVertex2f(cx + half, cy - half);
        glVertex2f(cx + half, cy + half);
        glVertex2f(cx - half, cy + half);
        glEnd();
    }

    /** Debug flag — draws Reeds-Shepp docking paths under each docking truck for math iteration. */
    public static boolean DEBUG_RENDER_DOCKING_PATHS = true;

    private void renderConvoyDockingPaths(java.util.List<com.dillon.starsectormarines.battle.vehicle.Vehicle> convoy,
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

    /**
     * Collects the {@link RenderLayer#SHOTS} layer into the {@link #drawList}
     * instead of drawing inline — the first pass migrated to the command model
     * (Story C). Submission order matches the old {@code renderShots}: contrails,
     * then hitscan tracers, then projectile sprites.
     *
     * <p>The contrails {@link DrawCommand.Custom} is emitted unconditionally — its
     * callback ages and decays existing trails every frame, even with no live
     * shots — so it must not be gated on {@code shots} being non-empty.
     */
    private void collectShots(List<ShotEvent> shots, float alphaMult) {
        drawList.addCustom(RenderLayer.SHOTS, () -> renderContrails(shots, alphaMult));
        if (shots.isEmpty()) return;

        drawList.addCustom(RenderLayer.SHOTS, () -> drawTracers(shots, alphaMult));

        for (ShotEvent s : shots) {
            ShuttleSpriteCache cache;
            float visualCells;
            if (s.turretKind != null) {
                cache = sprites.turretProjectileSprites().get(s.turretKind);
                visualCells = s.turretKind.projectileVisualCells;
            } else if (s.marineSecondary != null) {
                cache = sprites.marineSecondarySprites().get(s.marineSecondary);
                visualCells = s.marineSecondary.projectileVisualCells;
            } else if (s.marineWeapon != null && s.marineWeapon.projectileSpritePath != null) {
                cache = sprites.marineWeaponProjectileSprites().get(s.marineWeapon);
                visualCells = s.marineWeapon.projectileVisualCells;
            } else if (s.mechWeapon != null && s.mechWeapon.projectileSpritePath != null) {
                cache = sprites.mechWeaponProjectileSprites().get(s.mechWeapon);
                visualCells = s.mechWeapon.projectileVisualCells;
            } else {
                continue;
            }
            if (cache == null) continue;
            float linearProgress = 1f - Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
            float progress = (s.turretKind != null && s.turretKind.hasBoostRamp())
                    ? com.dillon.starsectormarines.battle.combat.Projectile.applyBoostCurve(linearProgress)
                    : linearProgress;
            float px = s.fromX + (s.toX - s.fromX) * progress;
            float py = s.fromY + (s.toY - s.fromY) * progress;
            float bearing;
            float arcH = 0f;
            if (s.mechWeapon != null) arcH = s.mechWeapon.arcHeight;
            else if (s.turretKind != null) arcH = s.turretKind.arcHeight;
            if (arcH > 0f) {
                py += arcH * 4f * progress * (1f - progress);
                float tangentDy = (s.toY - s.fromY) + arcH * 4f * (1f - 2f * progress);
                bearing = bearingDeg(0f, 0f, s.toX - s.fromX, tangentDy);
            } else {
                bearing = bearingDeg(s.fromX, s.fromY, s.toX, s.toY);
            }
            float cellPxLocal = rc.camera.cellPxSize();
            float pxH = visualCells * cellPxLocal;
            float pxW = pxH * cache.aspect;
            drawList.addSprite(RenderLayer.SHOTS,
                    cache.sprite,
                    rc.camera.cellToScreenX(px), rc.camera.cellToScreenY(py),
                    pxW, pxH, bearing,
                    1f, 1f, 1f, alphaMult);
            boolean engineTrail = s.mechWeapon != null && s.mechWeapon.engineTrail;
            boolean smokeTrail  = s.turretKind != null && s.turretKind.smokeTrail
                    && !kindUsesContrailRibbon(s.turretKind);
            if ((engineTrail || smokeTrail) && progress > 0.02f && progress < 0.98f) {
                float headingRad = (float) Math.toRadians(bearing);
                float tailDx = -(float) Math.sin(headingRad) * 0.15f;
                float tailDy = -(float) Math.cos(headingRad) * 0.15f;
                if (engineTrail) impactFx.spawnEngineTrail(px + tailDx, py + tailDy, 0.18f);
                else             impactFx.spawnSmokeTrail (px + tailDx, py + tailDy, 0.20f);
            }
        }
    }

    /**
     * Hitscan tracer lines for shots without a projectile sprite. Drawn as one
     * {@code GL_LINES} block faithful to the ambient mid-{@code renderWorld} GL
     * state, invoked as a {@link DrawCommand.Custom} from {@link #drainLayer}.
     */
    private void drawTracers(List<ShotEvent> shots, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glLineWidth(2f);
        glBegin(org.lwjgl.opengl.GL11.GL_LINES);
        for (ShotEvent s : shots) {
            if (s.turretKind != null) continue;
            if (s.marineSecondary != null) continue;
            if (s.marineWeapon != null && s.marineWeapon.projectileSpritePath != null) continue;
            if (s.mechWeapon != null && s.mechWeapon.projectileSpritePath != null) continue;
            float t = Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
            Color c;
            if (s.marineWeapon != null) {
                c = s.marineWeapon.tracerColor;
            } else {
                c = s.shooterFaction == Faction.MARINE ? MARINE_TRACER : DEFENDER_TRACER;
            }
            glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, t * alphaMult);
            float x0 = rc.camera.cellToScreenX(s.fromX);
            float y0 = rc.camera.cellToScreenY(s.fromY);
            float x1 = rc.camera.cellToScreenX(s.toX);
            float y1 = rc.camera.cellToScreenY(s.toY);
            glVertex2f(x0, y0);
            glVertex2f(x1, y1);
        }
        glEnd();
        org.lwjgl.opengl.GL11.glLineWidth(1f);
    }

    private void renderContrails(List<ShotEvent> shots, float alphaMult) {
        if (!contrailsLive.isEmpty()) {
            java.util.Set<ShotEvent> current = java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<>());
            current.addAll(shots);
            java.util.Iterator<java.util.Map.Entry<ShotEvent, ContrailTrail>> it =
                    contrailsLive.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<ShotEvent, ContrailTrail> e = it.next();
                if (!current.contains(e.getKey())) {
                    contrailsDecaying.add(e.getValue());
                    it.remove();
                }
            }
        }

        for (ShotEvent s : shots) {
            if (s.turretKind == null || !kindUsesContrailRibbon(s.turretKind)) continue;
            float linearProgress = 1f - Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
            float progress = s.turretKind.hasBoostRamp()
                    ? com.dillon.starsectormarines.battle.combat.Projectile.applyBoostCurve(linearProgress)
                    : linearProgress;
            float px = s.fromX + (s.toX - s.fromX) * progress;
            float py = s.fromY + (s.toY - s.fromY) * progress;
            float arcH = s.turretKind.arcHeight;
            if (arcH > 0f) py += arcH * 4f * progress * (1f - progress);
            ContrailTrail trail = contrailsLive.get(s);
            if (trail == null) {
                trail = new ContrailTrail(styleFor(s.turretKind), 32);
                contrailsLive.put(s, trail);
            }
            trail.pushSample(px, py);
        }

        float dt = rc.realDt;
        if (dt > 0f) {
            for (ContrailTrail t : contrailsLive.values()) t.advance(dt);
            for (ContrailTrail t : contrailsDecaying)      t.advance(dt);
        }
        contrailsDecaying.removeIf(ContrailTrail::isEmpty);

        if (contrailsLive.isEmpty() && contrailsDecaying.isEmpty()) return;
        for (ContrailTrail t : contrailsLive.values()) contrailBatch.append(t, rc.camera, alphaMult);
        for (ContrailTrail t : contrailsDecaying)      contrailBatch.append(t, rc.camera, alphaMult);
        if (contrailBatch.isEmpty()) return;
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            contrailBatch.flush();
        }
    }

    private static ContrailStyle styleFor(TurretKind kind) {
        return ContrailStyle.MISSILE_SMOKE;
    }

    private static boolean kindUsesContrailRibbon(TurretKind kind) {
        return kind == TurretKind.LOCUST;
    }

    private static float bearingDeg(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }

    private enum Facing { WEST, NORTH, EAST, SOUTH }

    private static Facing computeFacing(Unit u, BattleSimulation sim) {
        Unit target = sim != null ? sim.targetOf(u) : null;
        if (target != null) {
            int dx = target.getCellX() - u.getCellX();
            int dy = target.getCellY() - u.getCellY();
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        if (u.pathIdx < u.pathCellCount()) {
            int dx = u.pathCellX(u.pathIdx) - u.getCellX();
            int dy = u.pathCellY(u.pathIdx) - u.getCellY();
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        return Facing.SOUTH;
    }

    private static Facing facingFromDelta(int dx, int dy) {
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? Facing.EAST : Facing.WEST;
        return dy > 0 ? Facing.NORTH : Facing.SOUTH;
    }

    private static int pickFrame(Facing facing, boolean weaponUp) {
        if (weaponUp) {
            switch (facing) {
                case WEST:  return 4;
                case EAST:  return 5;
                case NORTH: return 6;
                case SOUTH: return 6; // vertical mirror applied at draw time
            }
        } else {
            switch (facing) {
                case WEST:  return 0;
                case NORTH: return 1;
                case EAST:  return 2;
                case SOUTH: return 3;
            }
        }
        return 3;
    }

    private enum EightWayFacing { W, NW, N, NE, E, SE, S, SW }

    private static EightWayFacing computeEightWayFacing(Unit u, BattleSimulation sim) {
        Unit target = sim != null ? sim.targetOf(u) : null;
        if (target != null) {
            int dx = target.getCellX() - u.getCellX();
            int dy = target.getCellY() - u.getCellY();
            if (dx != 0 || dy != 0) return eightWayFromDelta(dx, dy);
        }
        if (u.pathIdx < u.pathCellCount()) {
            int dx = u.pathCellX(u.pathIdx) - u.getCellX();
            int dy = u.pathCellY(u.pathIdx) - u.getCellY();
            if (dx != 0 || dy != 0) return eightWayFromDelta(dx, dy);
        }
        return EightWayFacing.S;
    }

    private static EightWayFacing eightWayFromDelta(int dx, int dy) {
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);
        boolean diag = Math.min(adx, ady) * 1000 >= Math.max(adx, ady) * 414;
        if (diag) {
            if (dx > 0 && dy > 0) return EightWayFacing.NE;
            if (dx > 0 && dy < 0) return EightWayFacing.SE;
            if (dx < 0 && dy > 0) return EightWayFacing.NW;
            return EightWayFacing.SW;
        }
        if (adx > ady) return dx > 0 ? EightWayFacing.E : EightWayFacing.W;
        return dy > 0 ? EightWayFacing.N : EightWayFacing.S;
    }

    private static int pickFrameEightWay(EightWayFacing f) {
        switch (f) {
            case W:  return 0;
            case NW: return 1;
            case SE: return 2;
            case S:  return 3;
            case SW: return 4;
            case NE: return 5;
            case E:  return 6;
            case N:  return 1; // no dedicated N — borrow NW
        }
        return 3;
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
        DrawListRenderer.drain(drawList.buffer(layer), drawList.count(layer), batchBySheet, solidBatch);
    }

    // ---- main entry point ----------------------------------------------------

    /**
     * Renders all world-layer passes. Called from {@code BattleScreen.render()} between
     * scissor setup and {@code glPopAttrib}.
     */
    public void renderWorld(RenderContext rc) {
        this.rc = rc;
        drawList.clear();
        BattleSimulation sim = rc.sim;
        // Collect phase — every migrated system appends into its own layer buffer.
        // Order is immaterial (each command is layer-tagged + GL-free); the drains
        // below replay layers in paint order, interleaving the inline passes that
        // haven't migrated yet.
        for (RenderSystem system : worldSystems) {
            system.collect(rc, drawList);
        }
        // GROUND layer — tiled floor/wall terrain via GroundRenderSystem (pooled
        // per-tile commands), drained through the strict-painter batch path.
        drainLayer(RenderLayer.GROUND);
        if (rc.debugZonesVisible) renderZoneOverlay(sim, rc.alphaMult);
        // Decals sit between the floor pass and vehicles so parked trucks
        // (and later units) draw on top of bullet holes and craters.
        renderDecals(sim, rc.alphaMult);
        // VEHICLES layer — parked map vehicles via VehicleRenderSystem, one
        // batched sheet-quad each (drained through the strict-painter path).
        drainLayer(RenderLayer.VEHICLES);
        // DOODADS layer — command-driven via DoodadRenderSystem (Story D): the
        // first sheet-batched pass routed through the draw list.
        drainLayer(RenderLayer.DOODADS);
        // Debug cell highlights — published by HUD panels (plan-step cells,
        // selected squad members, captain). Paints above ground decals/
        // doodads, below units so unit sprites stay legible over the tint.
        rc.highlights.render(rc.camera, rc.alphaMult);
        renderFogOverlay(sim, rc.alphaMult);
        renderUnits(sim, sim.getUnits(), rc.alphaMult);
        // Fog-of-war roof pass — paints opaque BRICK tiles over the
        // interiors of buildings the player can't see, hiding any units
        // (and decals / doodads) inside. Sits above units but below
        // objective markers, shuttles (aircraft), projectiles, and flyby
        // — all of which should pierce the roof.
        renderRoofs(sim, rc.alphaMult);
        // DRONES layer — drones live above the roof layer (they hover at roof
        // altitude, so a drone over a building overlays the BRICK roof tile rather
        // than being occluded). Hull SPRITE + HP-bar SOLID_RECTs via DroneRenderSystem.
        drainLayer(RenderLayer.DRONES);
        // Charge sites + equipment drops sit above units so the player can
        // always see where the objectives are.
        renderObjectiveMarkers(sim, rc.alphaMult);
        // Compound capture-state markers — faction-coloured ring +
        // capture-progress arc + kind glyph at each defender compound.
        compoundMarkers.render(sim, sim.getCompoundService(), rc.camera, rc.alphaMult);
        // CONVOY layer — convoy trucks + turrets via ConvoyRenderSystem (rotated
        // batched sheet-quads), just under shuttles. The debug overlays the old
        // pass dispatched are own-GL line passes and run inline after the drain.
        java.util.List<com.dillon.starsectormarines.battle.vehicle.Vehicle> convoy = sim.getConvoyVehicles();
        drainLayer(RenderLayer.CONVOY);
        if (DEBUG_RENDER_DOCKING_PATHS) renderConvoyDockingPaths(convoy, rc.alphaMult);
        renderSelectedVehicleDebug(convoy, rc.alphaMult);
        // SHUTTLES layer — aircraft hulls + turrets (SPRITE) + engine FX (Custom)
        // via ShuttleRenderSystem.
        drainLayer(RenderLayer.SHUTTLES);
        // SHOTS layer — command-driven (Story C): collect into the draw list,
        // then drain it through the batch/flush path instead of drawing inline.
        collectShots(sim.getActiveShots(), rc.alphaMult);
        drainLayer(RenderLayer.SHOTS);
        // Impact FX: sparks, dust, smoke at shot endpoints.
        impactFx.render(rc.camera, rc.alphaMult);
        // Flyby layer lives above everything ground-side.
        flybyOverlay.render(rc.camera, rc.alphaMult);
        // Lightmap multiply — last world-layer pass so darkness covers everything.
        TimeOfDay tod = timeOfDay.evaluateAt(0f);
        if (!tod.bypass) {
            lightAccumulator.render(rc.camera, tod,
                    sim.getGrid().getWidth(), sim.getGrid().getHeight(),
                    rc.alphaMult);
        }
    }
}
