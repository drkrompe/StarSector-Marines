package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.render2d.DecalAccumulator;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.flyby.FlybyOverlay;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.MapVehicle;
import com.dillon.starsectormarines.battle.vehicle.VehicleKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.TimeOfDay;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.world.tiles.UrbanTile3;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.ui.compound.CompoundMarkerRenderer;
import com.dillon.starsectormarines.render2d.ContrailStyle;
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

    private static final Color FLOOR_COLOR    = new Color(0x18, 0x22, 0x30);
    private static final Color WALL_COLOR     = new Color(0x06, 0x0A, 0x10);
    private static final Color MARINE_COLOR   = new Color(0x5A, 0xA0, 0xE0);
    private static final Color DEFENDER_COLOR = new Color(0xE0, 0x6A, 0x6A);
    private static final Color CIVILIAN_COLOR = new Color(0xC8, 0xC8, 0x80);
    private static final Color HP_BG          = new Color(0x60, 0x20, 0x20);
    private static final Color HP_FG          = new Color(0x40, 0xC0, 0x40);
    /** Dual-use in BattleScreen (spawnImpactFx); duplicated here for zero back-dependency. */
    private static final Color MARINE_TRACER  = new Color(0xFF, 0xE0, 0x70);
    /** Dual-use in BattleScreen (spawnImpactFx); duplicated here for zero back-dependency. */
    private static final Color DEFENDER_TRACER = new Color(0xFF, 0x70, 0x40);

    /** Sim-seconds shots live for — must match {@code BattleSimulation.SHOT_LIFETIME}. Used to fade tracer alpha. */
    private static final float SHOT_LIFETIME_REF = 0.15f;

    private static final float UNIT_FRAC      = 1.00f; // sprite fills the cell
    private static final float HP_BAR_H       = 3f;
    private static final float HP_BAR_GAP     = 2f;

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

    /** Sim-seconds the barrel sprite eases forward to its at-rest position after a shot. */
    private static final float RECOIL_DURATION = 0.12f;
    /** Peak backward displacement of the barrel sprite, as a fraction of the turret's visual long-axis (cells). */
    private static final float RECOIL_DISTANCE_FRAC = 0.10f;

    private static final Color ROAD_FILL      = new Color(TileManifest.ROAD_FILL_RGB);
    /** Solid fill for the open-courtyard case. */
    private static final Color COURTYARD_FILL = new Color(TileManifest.COURTYARD_FILL_RGB);
    /** Painted crosswalk stripe color. Slightly off-white so it doesn't compete with HP-bar greens. */
    private static final Color CROSSWALK_STRIPE = new Color(0xE8, 0xE8, 0xD0);
    /** Stripes per crosswalk cell. */
    private static final int CROSSWALK_STRIPE_COUNT = 5;
    private static final float CROSSWALK_STRIPE_FRAC = 0.10f;
    private static final float CROSSWALK_GAP_FRAC    = 0.10f;
    private static final float CROSSWALK_ALPHA       = 0.85f;
    /** Inset along the perpendicular axis. */
    private static final float CROSSWALK_INSET_FRAC  = 0.08f;

    private static final int GROUND_TILE_EDGE_INSET_PX       = com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer.GROUND_INSET_PX_LARGE;
    private static final int GROUND_SMALL_TILE_EDGE_INSET_PX = com.dillon.starsectormarines.battle.world.tiles.FixedGridTileDrawer.GROUND_INSET_PX_SMALL;

    // ---- owned state ---------------------------------------------------------

    private final BattleSprites sprites;
    private RenderContext rc;

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

    // ---- render methods — tile drawing helpers --------------------------------

    /**
     * Renders walkable cells as directional floor (or rubble) tiles and
     * non-walkable cells as wall autotiles, both picked from 4-neighbor
     * exposure. Floors connect their frame edges to adjacent walls; walls pick
     * the matching corner/edge piece.
     */
    private void renderTiledFloorsAndWalls(NavigationGrid grid, CellTopology topology, float alphaMult) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (topology.isWall(x, y)) continue;
                boolean nWall = isInBoundsWall(topology, x, y + 1);
                boolean sWall = isInBoundsWall(topology, x, y - 1);
                boolean eWall = isInBoundsWall(topology, x + 1, y);
                boolean wWall = isInBoundsWall(topology, x - 1, y);

                CellTopology.GroundKind kind = topology.getGroundKind(x, y);
                switch (kind) {
                    case RUBBLE: {
                        TileManifest.TileFrame f = TileManifest.pickRubbleTile(nWall, sWall, eWall, wWall);
                        drawTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                    case STREET:
                        if (urbanTile3Batch != null) {
                            if (isSidewalkCell(grid, topology, x, y)) {
                                boolean nNotSw = !isSidewalkLikeCell(grid, topology, x, y + 1);
                                boolean sNotSw = !isSidewalkLikeCell(grid, topology, x, y - 1);
                                boolean eNotSw = !isSidewalkLikeCell(grid, topology, x + 1, y);
                                boolean wNotSw = !isSidewalkLikeCell(grid, topology, x - 1, y);
                                UrbanTile3 frame = TileManifest.pickStreet3SidewalkFrame(
                                        nNotSw, sNotSw, eNotSw, wNotSw);
                                drawUrbanTile3Frame(frame, x, y, alphaMult);
                            } else {
                                drawUrbanTile3Frame(UrbanTile3.STREET_SQUARE, x, y, alphaMult);
                                if (topology.isCrosswalk(x, y)) {
                                    drawCrosswalkStripes(x, y, topology.isCrosswalkStripesHorizontal(x, y), alphaMult);
                                }
                            }
                        } else if (sprites.roadSheet() != null) {
                            if (isSidewalkCell(grid, topology, x, y)) {
                                drawRoadTile(TileManifest.SIDEWALK, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                            } else {
                                boolean nB = isRoadBoundary(grid, topology, x, y + 1);
                                boolean sB = isRoadBoundary(grid, topology, x, y - 1);
                                boolean eB = isRoadBoundary(grid, topology, x + 1, y);
                                boolean wB = isRoadBoundary(grid, topology, x - 1, y);
                                TileManifest.TileFrame f = TileManifest.pickRoadTile(nB, sB, eB, wB);
                                if (f == null) {
                                    fillCell(x, y, ROAD_FILL, alphaMult);
                                } else {
                                    drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                                }
                                if (topology.isCrosswalk(x, y)) {
                                    drawCrosswalkStripes(x, y, topology.isCrosswalkStripesHorizontal(x, y), alphaMult);
                                }
                            }
                        }
                        break;
                    case COURTYARD:
                        if (sprites.roadSheet() != null) {
                            TileManifest.TileFrame f = TileManifest.pickCourtyardTile(nWall, sWall, eWall, wWall);
                            if (f == null) {
                                fillCell(x, y, COURTYARD_FILL, alphaMult);
                            } else {
                                drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                            }
                        }
                        break;
                    case GRASS:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case DIRT:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case STONE:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case SAND:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case SNOW:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case WATER:
                        drawSameKindAutotile(topology, kind, x, y, alphaMult);
                        break;
                    case TILE: {
                        TileManifest.TileFrame f = TileManifest.pickTileGroundTile(x, y);
                        if (sprites.roadSheet() != null) drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                    case BRICK: {
                        TileManifest.TileFrame f = TileManifest.pickBrickTile(x, y);
                        drawFloorsTile(f, x, y, alphaMult);
                        break;
                    }
                    case SIDEWALK: {
                        boolean nNotSw = !isSidewalkLikeCell(grid, topology, x, y + 1);
                        boolean sNotSw = !isSidewalkLikeCell(grid, topology, x, y - 1);
                        boolean eNotSw = !isSidewalkLikeCell(grid, topology, x + 1, y);
                        boolean wNotSw = !isSidewalkLikeCell(grid, topology, x - 1, y);
                        UrbanTile3 frame = TileManifest.pickStreet3SidewalkFrame(
                                nNotSw, sNotSw, eNotSw, wNotSw);
                        drawUrbanTile3Frame(frame, x, y, alphaMult);
                        break;
                    }
                    case STRIPED: {
                        TileManifest.TileFrame f = TileManifest.pickStripedTile(nWall, sWall, eWall, wWall);
                        if (sprites.roadSheet() != null) drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                    case LZ_MARKER: {
                        TileManifest.TileFrame f = TileManifest.pickLzMarkerTile();
                        if (sprites.roadSheet() != null) drawRoadTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                    case INDOOR:
                    default: {
                        TileManifest.TileFrame f = TileManifest.pickFloorTile(nWall, sWall, eWall, wWall);
                        drawTile(f, x, y, alphaMult, GROUND_TILE_EDGE_INSET_PX);
                        break;
                    }
                }

                com.dillon.starsectormarines.battle.world.tiles.NatureTile overlay =
                        topology.getNatureOverlay(x, y);
                if (overlay != null) {
                    drawNatureTile(overlay, x, y, alphaMult);
                }

                if (grid.isDoorway(x, y) && !topology.isRubble(x, y)) {
                    drawTile(TileManifest.DOOR_OPEN, x, y, alphaMult, 0);
                }
            }
        }

        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            if (roadBatch       != null) roadBatch.flush();
            if (urbanTile3Batch != null) urbanTile3Batch.flush();
            if (natureBatch     != null) natureBatch.flush();
            if (floorsBatch     != null) floorsBatch.flush();
            if (waterBatch      != null) waterBatch.flush();
            if (urbanBatch      != null) urbanBatch.flush();
            solidBatch.flush();
        }

        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!topology.isWall(x, y)) continue;
                int mask = topology.getWallDirMask(x, y);
                TileManifest.TileFrame tile = WallMasks.pickTileFromMask(mask);
                if (tile == null) {
                    fillCell(x, y, WALL_COLOR, alphaMult);
                } else {
                    drawTile(tile, x, y, alphaMult, 0);
                }
            }
        }

        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            if (urbanBatch != null) urbanBatch.flush();
            solidBatch.flush();
        }
    }

    private void fillCell(int gridX, int gridY, Color color, float alphaMult) {
        float x0 = rc.camera.cellToScreenX(gridX);
        float y0 = rc.camera.cellToScreenY(gridY);
        float c = rc.camera.cellPxSize();
        solidBatch.appendRect(
                x0, y0, x0 + c, y0 + c,
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                alphaMult);
    }

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

    private void drawCrosswalkStripes(int gridX, int gridY, boolean stripesHorizontal, float alphaMult) {
        float cell = rc.camera.cellPxSize();
        float x0 = rc.camera.cellToScreenX(gridX);
        float y0 = rc.camera.cellToScreenY(gridY);
        float stripeW = cell * CROSSWALK_STRIPE_FRAC;
        float gapW    = cell * CROSSWALK_GAP_FRAC;
        float bandSpan = CROSSWALK_STRIPE_COUNT * stripeW + (CROSSWALK_STRIPE_COUNT - 1) * gapW;
        float marginAlong = (cell - bandSpan) / 2f;
        float perpInset = cell * CROSSWALK_INSET_FRAC;
        float alpha = CROSSWALK_ALPHA * alphaMult;
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
            solidBatch.appendRect(rx, ry, rx + rw, ry + rh, sr, sg, sb, alpha);
        }
    }

    private void renderDoodads(BattleSimulation sim, float alphaMult) {
        if (sprites.tileSheet() == null) return;
        for (Doodad d : sim.getDoodads()) {
            if (d.fromRoadSheet) {
                if (sprites.roadSheet() != null) drawRoadTile(d.tile, d.cellX, d.cellY, alphaMult, 0);
            } else {
                drawTile(d.tile, d.cellX, d.cellY, alphaMult, 0);
            }
        }
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            if (roadBatch       != null) roadBatch.flush();
            if (urbanTile3Batch != null) urbanTile3Batch.flush();
            if (urbanBatch      != null) urbanBatch.flush();
        }
    }

    private void drawRoadTile(TileManifest.TileFrame f, int gridX, int gridY, float alphaMult,
                              int srcEdgeInsetPx) {
        if (roadBatch == null) return;
        int srcPxX = f.col * TileManifest.TILE_SIZE + srcEdgeInsetPx;
        int srcTopPxY = f.row * TileManifest.TILE_SIZE + srcEdgeInsetPx;
        int srcPxW = TileManifest.TILE_SIZE - 2 * srcEdgeInsetPx;
        int srcPxH = TileManifest.TILE_SIZE - 2 * srcEdgeInsetPx;

        float cellPx = rc.camera.cellPxSize();
        float cx = rc.camera.cellToScreenX(gridX + 0.5f);
        float cy = rc.camera.cellToScreenY(gridY + 0.5f);
        roadBatch.append(srcPxX, srcTopPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }

    private void drawUrbanTile3Frame(UrbanTile3 frame, int gridX, int gridY, float alphaMult) {
        if (urbanTile3Batch == null || sprites.urbanTile3Frames() == null || frame == null) return;
        int idx = frame.frameIndex();
        if (idx < 0 || idx >= sprites.urbanTile3Frames().frames.length) return;
        SpriteSheetFrames.Frame f = sprites.urbanTile3Frames().frames[idx];
        int inset = frame.isGround() ? GROUND_TILE_EDGE_INSET_PX : 0;
        int srcPxX = f.x + inset;
        int srcPxY = f.y + inset;
        int srcPxW = Math.max(1, f.w - 2 * inset);
        int srcPxH = Math.max(1, f.h - 2 * inset);

        float cellPx = rc.camera.cellPxSize();
        float cx = rc.camera.cellToScreenX(gridX + 0.5f);
        float cy = rc.camera.cellToScreenY(gridY + 0.5f);
        urbanTile3Batch.append(srcPxX, srcPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }

    private void drawNatureTile(com.dillon.starsectormarines.battle.world.tiles.NatureTile tile,
                                int gridX, int gridY, float alphaMult) {
        if (natureBatch == null || sprites.natureFrames() == null || tile == null) return;
        int idx = tile.frameIndex();
        if (idx < 0 || idx >= sprites.natureFrames().frames.length) return;
        SpriteSheetFrames.Frame f = sprites.natureFrames().frames[idx];
        int inset = tile.isGround() ? GROUND_TILE_EDGE_INSET_PX : 0;
        int srcPxX = f.x + inset;
        int srcPxY = f.y + inset;
        int srcPxW = Math.max(1, f.w - 2 * inset);
        int srcPxH = Math.max(1, f.h - 2 * inset);

        float cellPx = rc.camera.cellPxSize();
        float cx = rc.camera.cellToScreenX(gridX + 0.5f);
        float cy = rc.camera.cellToScreenY(gridY + 0.5f);
        natureBatch.append(srcPxX, srcPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }

    private void drawSameKindAutotile(CellTopology topology, CellTopology.GroundKind kind,
                                      int x, int y, float alphaMult) {
        if (natureBatch != null) {
            if (kind == CellTopology.GroundKind.GRASS) {
                drawNatureTile(TileManifest.pickNatureGrassTile(x, y), x, y, alphaMult);
                return;
            }
            if (kind == CellTopology.GroundKind.DIRT) {
                drawNatureTile(TileManifest.pickNatureDirtTile(x, y), x, y, alphaMult);
                return;
            }
        }
        TileManifest.TileFrame f;
        switch (kind) {
            case GRASS: f = TileManifest.pickGrassTile(false, false, false, false, x, y); break;
            case DIRT:  f = TileManifest.pickDirtTile (false, false, false, false, x, y); break;
            case STONE: f = TileManifest.pickStoneTile(false, false, false, false, x, y); break;
            case SAND:  f = TileManifest.pickSandTile (false, false, false, false, x, y); break;
            case SNOW:  f = TileManifest.pickSnowTile (false, false, false, false, x, y); break;
            case WATER: f = TileManifest.pickWaterTile(false, false, false, false, x, y); break;
            default: return;
        }
        if (kind == CellTopology.GroundKind.WATER) {
            if (sprites.waterSheet() != null) drawWaterTile(f, x, y, alphaMult);
        } else {
            if (sprites.floorsSheet() != null) drawFloorsTile(f, x, y, alphaMult);
        }
    }

    private void drawFloorsTile(TileManifest.TileFrame f, int gridX, int gridY, float alphaMult) {
        appendSmallTile(floorsBatch, f, gridX, gridY, alphaMult);
    }

    private void drawWaterTile(TileManifest.TileFrame f, int gridX, int gridY, float alphaMult) {
        appendSmallTile(waterBatch, f, gridX, gridY, alphaMult);
    }

    private void appendSmallTile(QuadBatch batch, TileManifest.TileFrame f,
                                 int gridX, int gridY, float alphaMult) {
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
                1f, 1f, 1f, alphaMult);
    }

    private void drawTile(TileManifest.TileFrame f, int gridX, int gridY,
                          float alphaMult, int srcEdgeInsetPx) {
        if (urbanBatch == null) return;
        int srcPxX = f.col * TileManifest.TILE_SIZE + srcEdgeInsetPx;
        int srcTopPxY = f.row * TileManifest.TILE_SIZE + srcEdgeInsetPx;
        int srcPxW = TileManifest.TILE_SIZE - 2 * srcEdgeInsetPx;
        int srcPxH = TileManifest.TILE_SIZE - 2 * srcEdgeInsetPx;

        float cellPx = rc.camera.cellPxSize();
        float cx = rc.camera.cellToScreenX(gridX + 0.5f);
        float cy = rc.camera.cellToScreenY(gridY + 0.5f);
        urbanBatch.append(srcPxX, srcTopPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }

    private static int cellHash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        return h & 0x7FFFFFFF;
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

    private void renderGrid(NavigationGrid grid, CellTopology topology, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        float worldX0 = rc.camera.cellToScreenX(0);
        float worldY0 = rc.camera.cellToScreenY(0);
        float worldX1 = rc.camera.cellToScreenX(grid.getWidth());
        float worldY1 = rc.camera.cellToScreenY(grid.getHeight());
        glColor4f(FLOOR_COLOR.getRed() / 255f, FLOOR_COLOR.getGreen() / 255f,
                FLOOR_COLOR.getBlue() / 255f, alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(worldX0, worldY0);
        glVertex2f(worldX1, worldY0);
        glVertex2f(worldX1, worldY1);
        glVertex2f(worldX0, worldY1);
        glEnd();

        if (sprites.tileSheet() != null) {
            renderTiledFloorsAndWalls(grid, topology, alphaMult);
        } else {
            float cellPx = rc.camera.cellPxSize();
            glColor4f(WALL_COLOR.getRed() / 255f, WALL_COLOR.getGreen() / 255f,
                    WALL_COLOR.getBlue() / 255f, alphaMult);
            glBegin(GL_QUADS);
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) {
                    if (grid.isWalkable(x, y)) continue;
                    float x0 = rc.camera.cellToScreenX(x);
                    float y0 = rc.camera.cellToScreenY(y);
                    float x1 = x0 + cellPx;
                    float y1 = y0 + cellPx;
                    glVertex2f(x0, y0);
                    glVertex2f(x1, y0);
                    glVertex2f(x1, y1);
                    glVertex2f(x0, y1);
                }
            }
            glEnd();
        }
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
        renderDeadUnits(units, unitSize, alphaMult);

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
            fillRect(barX, barY, barW, HP_BAR_H, HP_BG, barAlpha);
            float frac = Math.max(0f, Math.min(1f, u.getHp() / u.getMaxHp()));
            fillRect(barX, barY, barW * frac, HP_BAR_H, HP_FG, barAlpha);
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

    private void renderVehicles(BattleSimulation sim, float alphaMult) {
        java.util.List<MapVehicle> vehicles = sim.getVehicles();
        if (vehicles.isEmpty()) return;

        float cellPx = rc.camera.cellPxSize();
        java.util.Set<VehicleKind.VehicleSheet> touched = new java.util.HashSet<>();
        for (MapVehicle v : vehicles) {
            UnitSpriteCache cache = sprites.vehicleSheets().get(v.kind.sheet);
            if (cache == null || cache.sheet == null || cache.frames == null) continue;
            if (v.kind.frameIndex >= cache.frames.frames.length) continue;
            SpriteAPI sheet = cache.sheet;
            SpriteSheetFrames frames = cache.frames;
            SpriteSheetFrames.Frame f = frames.frames[v.kind.frameIndex];

            float texW = sheet.getTextureWidth();
            float texH = sheet.getTextureHeight();
            int sheetW = frames.sheetWidth;
            int sheetH = frames.sheetHeight;
            sheet.setTexX((float) f.x * texW / sheetW);
            sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
            sheet.setTexWidth((float) f.w * texW / sheetW);
            sheet.setTexHeight((float) f.h * texH / sheetH);

            float footW = v.kind.footprintCellsX * cellPx;
            float footH = v.kind.footprintCellsY * cellPx;
            float frameAspect = (float) f.w / (float) f.h;
            float footAspect  = footW / footH;
            float drawW, drawH;
            if (frameAspect > footAspect) {
                drawW = footW;
                drawH = footW / frameAspect;
            } else {
                drawH = footH;
                drawW = footH * frameAspect;
            }
            sheet.setSize(drawW, drawH);
            sheet.setAlphaMult(alphaMult);
            sheet.setNormalBlend();
            sheet.setColor(Color.WHITE);
            float cx = rc.camera.cellToScreenX(v.cellX + v.kind.footprintCellsX / 2f);
            float cy = rc.camera.cellToScreenY(v.cellY + v.kind.footprintCellsY / 2f);
            sheet.renderAtCenter(cx, cy);
            touched.add(v.kind.sheet);
        }
        for (VehicleKind.VehicleSheet s : touched) {
            UnitSpriteCache cache = sprites.vehicleSheets().get(s);
            if (cache != null && cache.sheet != null) cache.sheet.setColor(Color.WHITE);
        }
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

    private void renderDrones(BattleSimulation sim, List<Unit> units, float alphaMult) {
        boolean any = false;
        for (Unit u : units) {
            if (!(u instanceof com.dillon.starsectormarines.battle.drone.Drone)) continue;
            com.dillon.starsectormarines.battle.drone.Drone d = (com.dillon.starsectormarines.battle.drone.Drone) u;
            if (d.crashed) continue;
            if (d.isAlive() || d.crashStarted) { any = true; break; }
        }
        if (!any) return;
        sprites.ensureDroneSprite();
        if (sprites.droneSprite() == null) return;

        com.dillon.starsectormarines.battle.vision.VisionService vis = sim.getVision();
        float cellPx = rc.camera.cellPxSize();
        float visual = com.dillon.starsectormarines.battle.drone.Drone.VISUAL_CELLS;
        float barW = rc.camera.cellPxSize() * 0.9f;
        for (Unit u : units) {
            if (!(u instanceof com.dillon.starsectormarines.battle.drone.Drone)) continue;
            com.dillon.starsectormarines.battle.drone.Drone d = (com.dillon.starsectormarines.battle.drone.Drone) u;
            if (d.crashed) continue;
            boolean alive = d.isAlive();
            if (!alive && !d.crashStarted) continue;
            byte uv = vis.getUnitVisibility(d.denseIdx);
            if (alive && uv == com.dillon.starsectormarines.battle.vision.VisionService.VIS_HIDDEN) continue;
            float cx = rc.camera.cellToScreenX(d.body.x);
            float cy = rc.camera.cellToScreenY(d.body.y);
            float drawAlpha = alphaMult;
            if (alive && uv == com.dillon.starsectormarines.battle.vision.VisionService.VIS_FADING) {
                drawAlpha *= vis.getFadeAlpha(d.denseIdx);
            }
            if (!alive) {
                float t = Math.max(0f, Math.min(1f, d.crashTimer / com.dillon.starsectormarines.battle.drone.Drone.CRASH_DURATION_SEC));
                drawAlpha *= t;
            }
            drawTurretLayer(sprites.droneSprite(), d.body.facingDegrees, visual, cellPx, cx, cy, drawAlpha);
            if (alive) {
                float barY = cy + visual * cellPx / 2f + HP_BAR_GAP;
                float barX = cx - barW / 2f;
                fillRect(barX, barY, barW, HP_BAR_H, HP_BG, drawAlpha);
                float frac = Math.max(0f, Math.min(1f, d.getHp() / d.getMaxHp()));
                fillRect(barX, barY, barW * frac, HP_BAR_H, HP_FG, drawAlpha);
            }
        }
        sprites.droneSprite().sprite.setAngle(0f);
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

    private void renderDeadUnits(List<Unit> units, float unitSize, float alphaMult) {
        java.util.Set<UnitSpriteCache> touched = new java.util.HashSet<>();
        for (Unit u : units) {
            if (u.isAlive()) continue;
            if (u.deathPoseIdx < 0) continue;
            UnitSpriteCache cache = sprites.unitDeadSprites().get(u.type);
            if (cache == null || cache.sheet == null || cache.frames == null
                    || cache.frames.frames.length == 0) continue;
            SpriteSheetFrames frames = cache.frames;
            int frameIdx = ((u.deathPoseIdx % frames.frames.length) + frames.frames.length) % frames.frames.length;
            SpriteSheetFrames.Frame f = frames.frames[frameIdx];
            SpriteAPI sheet = cache.sheet;
            float texW = sheet.getTextureWidth();
            float texH = sheet.getTextureHeight();
            int sheetW = frames.sheetWidth;
            int sheetH = frames.sheetHeight;
            sheet.setTexX((float) f.x * texW / sheetW);
            sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
            sheet.setTexWidth((float) f.w * texW / sheetW);
            sheet.setTexHeight((float) f.h * texH / sheetH);
            float scaledSize = unitSize * u.type.renderScale;
            float targetW, targetH;
            if (f.w >= f.h) {
                targetW = scaledSize;
                targetH = scaledSize * f.h / (float) f.w;
            } else {
                targetH = scaledSize;
                targetW = scaledSize * f.w / (float) f.h;
            }
            sheet.setSize(targetW, targetH);
            sheet.setAngle(0f);
            sheet.setAlphaMult(alphaMult);
            sheet.setNormalBlend();
            sheet.setColor(Color.WHITE);
            float cx = rc.camera.cellToScreenX(u.getRenderX() + 0.5f);
            float cy = rc.camera.cellToScreenY(u.getRenderY() + 0.5f);
            sheet.renderAtCenter(cx, cy);
            touched.add(cache);
        }
        for (UnitSpriteCache c : touched) {
            c.sheet.setColor(Color.WHITE);
        }
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

    private void renderConvoyVehicles(java.util.List<com.dillon.starsectormarines.battle.vehicle.Vehicle> convoy,
                                      float alphaMult) {
        if (convoy.isEmpty()) return;
        float cellPx = rc.camera.cellPxSize();
        java.util.Set<UnitSpriteCache> touched = new java.util.HashSet<>();
        for (com.dillon.starsectormarines.battle.vehicle.Vehicle v : convoy) {
            if (!v.isVisible()) continue;
            UnitSpriteCache cache = sprites.convoySprites().get(v.type);
            if (cache == null || cache.sheet == null || cache.frames == null) continue;
            if (v.type.spriteFrame < 0 || v.type.spriteFrame >= cache.frames.frames.length) continue;
            SpriteAPI sheet = cache.sheet;
            SpriteSheetFrames frames = cache.frames;
            SpriteSheetFrames.Frame f = frames.frames[v.type.spriteFrame];

            float texW = sheet.getTextureWidth();
            float texH = sheet.getTextureHeight();
            int sheetW = frames.sheetWidth;
            int sheetH = frames.sheetHeight;
            sheet.setTexX((float) f.x * texW / sheetW);
            sheet.setTexY((float) (sheetH - f.y - f.h) * texH / sheetH);
            sheet.setTexWidth((float) f.w * texW / sheetW);
            sheet.setTexHeight((float) f.h * texH / sheetH);

            float frameAspect = (float) f.w / (float) f.h;
            float drawLong  = v.type.visualLengthCells * cellPx;
            float drawShort = drawLong / frameAspect;
            sheet.setSize(drawLong, drawShort);
            sheet.setAngle(v.body.facingDegrees + v.type.spriteFacingOffsetDeg);
            sheet.setAlphaMult(alphaMult);
            sheet.setNormalBlend();
            sheet.setColor(java.awt.Color.WHITE);

            float cx = rc.camera.cellToScreenX(v.body.x);
            float cy = rc.camera.cellToScreenY(v.body.y);
            sheet.renderAtCenter(cx, cy);

            if (v.type.turretFrame >= 0
                    && v.type.turretFrame < frames.frames.length) {
                SpriteSheetFrames.Frame tf = frames.frames[v.type.turretFrame];
                sheet.setTexX((float) tf.x * texW / sheetW);
                sheet.setTexY((float) (sheetH - tf.y - tf.h) * texH / sheetH);
                sheet.setTexWidth((float) tf.w * texW / sheetW);
                sheet.setTexHeight((float) tf.h * texH / sheetH);

                float turretAspect = (float) tf.w / (float) tf.h;
                float tDrawLong = v.type.turretVisualCells * cellPx;
                float tDrawShort = tDrawLong / turretAspect;
                sheet.setSize(tDrawLong, tDrawShort);

                float chassisFacingDeg = v.body.facingDegrees + v.type.spriteFacingOffsetDeg;
                float turretFacingDeg = v.turretFacingDeg + v.type.turretSpriteFacingOffsetDeg;
                float cRad = (float) Math.toRadians(chassisFacingDeg);
                float cc = (float) Math.cos(cRad);
                float cs = (float) Math.sin(cRad);
                float mountWorldX = v.type.turretMountX * cc - v.type.turretMountY * cs;
                float mountWorldY = v.type.turretMountX * cs + v.type.turretMountY * cc;
                float tRad = (float) Math.toRadians(turretFacingDeg);
                float tc = (float) Math.cos(tRad);
                float ts = (float) Math.sin(tRad);
                float pivotWorldX = v.type.turretPivotX * tc - v.type.turretPivotY * ts;
                float pivotWorldY = v.type.turretPivotX * ts + v.type.turretPivotY * tc;
                float drawCellX = v.body.x + mountWorldX - pivotWorldX;
                float drawCellY = v.body.y + mountWorldY - pivotWorldY;

                sheet.setAngle(turretFacingDeg);
                sheet.renderAtCenter(
                        rc.camera.cellToScreenX(drawCellX),
                        rc.camera.cellToScreenY(drawCellY));
            }

            touched.add(cache);
        }
        for (UnitSpriteCache cache : touched) {
            if (cache != null && cache.sheet != null) cache.sheet.setAngle(0f);
        }
        if (DEBUG_RENDER_DOCKING_PATHS) renderConvoyDockingPaths(convoy, alphaMult);
        renderSelectedVehicleDebug(convoy, alphaMult);
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

    private void renderShuttles(List<Shuttle> shuttles, float alphaMult) {
        if (shuttles.isEmpty()) return;
        for (Shuttle s : shuttles) {
            if (!s.isVisible()) continue;
            ShuttleSpriteCache cache = sprites.shuttleSprites().get(s.type);
            if (cache == null) continue;
            SpriteAPI sprite = cache.sprite;
            float pxLen = s.type.visualLengthCells * rc.camera.cellPxSize() * s.scaleMult;
            float pxH = pxLen;
            float pxW = pxLen * cache.aspect;
            sprite.setSize(pxW, pxH);
            sprite.setAngle(s.body.facingDegrees);
            sprite.setAlphaMult(alphaMult);
            sprite.setNormalBlend();
            sprite.setColor(Color.WHITE);
            float altOffset = s.visualAltitudeOffsetCells();
            float cx = rc.camera.cellToScreenX(s.body.x);
            float cy = rc.camera.cellToScreenY(s.body.y + altOffset);
            renderShuttleEngines(s, alphaMult, altOffset);
            sprite.renderAtCenter(cx, cy);
            renderShuttleTurrets(s, alphaMult, altOffset);
        }
        for (ShuttleSpriteCache cache : sprites.shuttleSprites().values()) {
            cache.sprite.setAngle(0f);
        }
    }

    private void renderShuttleEngines(com.dillon.starsectormarines.battle.air.Shuttle s, float alphaMult,
                                      float altOffsetCells) {
        com.dillon.starsectormarines.battle.air.engine.EngineFxRenderer.draw(
                com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver.resolve(s.type),
                s.body.x, s.body.y,
                s.body.facingDegrees,
                s.scaleMult,
                altOffsetCells,
                s.engineFxIntensity(),
                alphaMult,
                rc.camera,
                sprites.engineGlowSprite(), sprites.engineFlameSprite());
    }

    private void renderShuttleTurrets(com.dillon.starsectormarines.battle.air.Shuttle s, float alphaMult,
                                      float altOffsetCells) {
        if (s.turrets.length == 0) return;
        float rad = (float) Math.toRadians(s.body.facingDegrees);
        float c = (float) Math.cos(rad);
        float si = (float) Math.sin(rad);
        float cellPx = rc.camera.cellPxSize();
        for (com.dillon.starsectormarines.battle.air.MountedTurret mt : s.turrets) {
            ShuttleSpriteCache base = sprites.turretSprites().get(mt.mount.kind);
            if (base == null) continue;
            float lx = mt.mount.localOffsetX * s.scaleMult;
            float ly = mt.mount.localOffsetY * s.scaleMult;
            float worldOffsetX = lx * c - ly * si;
            float worldOffsetY = lx * si + ly * c;
            float wx = s.body.x + worldOffsetX;
            float wy = s.body.y + worldOffsetY + altOffsetCells;
            float screenX = rc.camera.cellToScreenX(wx);
            float screenY = rc.camera.cellToScreenY(wy);
            float layerVisualCells = mt.mount.kind.visualCells * s.scaleMult * s.type.turretVisualScale;

            ShuttleSpriteCache barrel = sprites.turretRecoilSprites().get(mt.mount.kind);
            if (barrel != null) {
                float recoilT = 0f;
                if (mt.recoilTimer < RECOIL_DURATION) {
                    recoilT = 1f - mt.recoilTimer / RECOIL_DURATION;
                }
                float pushPx = recoilT * RECOIL_DISTANCE_FRAC * layerVisualCells * cellPx;
                double brad = Math.toRadians(mt.facingDegrees);
                float bx =  (float) Math.sin(brad)  * pushPx;
                float by = -(float) Math.cos(brad)  * pushPx;
                drawTurretLayer(barrel, mt.facingDegrees, layerVisualCells, cellPx,
                        screenX + bx, screenY + by, alphaMult);
            }
            drawTurretLayer(base, mt.facingDegrees, layerVisualCells, cellPx,
                    screenX, screenY, alphaMult);
        }
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

    private void renderShots(List<ShotEvent> shots, float alphaMult) {
        renderContrails(shots, alphaMult);
        if (shots.isEmpty()) return;

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

        java.util.Set<TurretKind> touchedTurret = new java.util.HashSet<>();
        java.util.Set<MarineSecondary> touchedSecondary = new java.util.HashSet<>();
        java.util.Set<MarineWeapon> touchedPrimary = new java.util.HashSet<>();
        java.util.Set<com.dillon.starsectormarines.battle.mech.MechWeapon> touchedMech = new java.util.HashSet<>();
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
            SpriteAPI sprite = cache.sprite;
            float cellPxLocal = rc.camera.cellPxSize();
            float pxH = visualCells * cellPxLocal;
            float pxW = pxH * cache.aspect;
            sprite.setSize(pxW, pxH);
            sprite.setAngle(bearing);
            sprite.setAlphaMult(alphaMult);
            sprite.setNormalBlend();
            sprite.setColor(Color.WHITE);
            sprite.renderAtCenter(rc.camera.cellToScreenX(px), rc.camera.cellToScreenY(py));
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
            if (s.turretKind != null) touchedTurret.add(s.turretKind);
            else if (s.marineSecondary != null) touchedSecondary.add(s.marineSecondary);
            else if (s.marineWeapon != null) touchedPrimary.add(s.marineWeapon);
            else if (s.mechWeapon != null) touchedMech.add(s.mechWeapon);
        }
        for (TurretKind k : touchedTurret) {
            ShuttleSpriteCache c = sprites.turretProjectileSprites().get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
        for (MarineSecondary k : touchedSecondary) {
            ShuttleSpriteCache c = sprites.marineSecondarySprites().get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
        for (MarineWeapon k : touchedPrimary) {
            ShuttleSpriteCache c = sprites.marineWeaponProjectileSprites().get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
        for (com.dillon.starsectormarines.battle.mech.MechWeapon k : touchedMech) {
            ShuttleSpriteCache c = sprites.mechWeaponProjectileSprites().get(k);
            if (c != null) c.sprite.setAngle(0f);
        }
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

    // ---- main entry point ----------------------------------------------------

    /**
     * Renders all world-layer passes. Called from {@code BattleScreen.render()} between
     * scissor setup and {@code glPopAttrib}.
     */
    public void renderWorld(RenderContext rc) {
        this.rc = rc;
        BattleSimulation sim = rc.sim;
        renderGrid(sim.getGrid(), sim.getTopology(), rc.alphaMult);
        if (rc.debugZonesVisible) renderZoneOverlay(sim, rc.alphaMult);
        // Decals sit between the floor pass and vehicles so parked trucks
        // (and later units) draw on top of bullet holes and craters.
        renderDecals(sim, rc.alphaMult);
        renderVehicles(sim, rc.alphaMult);
        renderDoodads(sim, rc.alphaMult);
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
        // Drones live above the roof layer — they hover at roof altitude,
        // so a drone over a building should visually overlay the BRICK roof
        // tile rather than be occluded by it.
        renderDrones(sim, sim.getUnits(), rc.alphaMult);
        // Charge sites + equipment drops sit above units so the player can
        // always see where the objectives are.
        renderObjectiveMarkers(sim, rc.alphaMult);
        // Compound capture-state markers — faction-coloured ring +
        // capture-progress arc + kind glyph at each defender compound.
        compoundMarkers.render(sim, sim.getCompoundService(), rc.camera, rc.alphaMult);
        // Ground convoys layer just under shuttles.
        renderConvoyVehicles(sim.getConvoyVehicles(), rc.alphaMult);
        renderShuttles(sim.getShuttles(), rc.alphaMult);
        renderShots(sim.getActiveShots(), rc.alphaMult);
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
