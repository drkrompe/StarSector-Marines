package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan;
import com.dillon.starsectormarines.battle.world.model.RoofDamage;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.model.RoofCover;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.render2d.texture.TextureUsage;
import com.dillon.starsectormarines.render2d.DecalAccumulator;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.infantry.EquipmentDrop;
import com.dillon.starsectormarines.battle.logistics.ResupplyCache;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.Pose;
import com.dillon.starsectormarines.battle.vehicle.ReedsShepp;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.model.SurfaceRole;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.render2d.DrawListRenderer;
import com.dillon.starsectormarines.render2d.LineBatch;
import com.dillon.starsectormarines.render2d.PagedQuadRun;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.dillon.starsectormarines.render2d.RibbonBatch;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.awt.Color;
import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Function;

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
    /** Gap between an entity's top edge and its durability bar (placement, caller-owned).
     *  Bar style (height/colors/ownership coding) lives in {@link DurabilityBarDecor}. */
    static final float HP_BAR_GAP     = 2f;

    /** Icon tints + sizes. Sizes are fractions of {@code layout.cellSize}. */
    private static final Color  OBJECTIVE_CORE        = new Color(0x08, 0x10, 0x16);
    private static final Color  KIT_DROP_TINT        = new Color(0x80, 0xE8, 0xFF);
    private static final Color  RESUPPLY_TINT         = new Color(0x70, 0xD8, 0x78);
    private static final Color  RESUPPLY_CONTESTED    = new Color(0xF0, 0x70, 0x50);
    private static final Color  ORBITAL_WARNING        = new Color(0xFF, 0x48, 0x38);
    /** A fort aspect that has been wrecked: grey, whoever it belonged to. */
    private static final Color  FORT_WRECKED           = new Color(0x90, 0x90, 0x90);
    private static final float  KIT_DROP_SIZE        = 1.0f;
    private static final float  RESUPPLY_SIZE         = 1.25f;
    private static final float  KIT_DROP_PULSE_AMP   = 0.10f;
    private static final float  KIT_DROP_PULSE_HZ    = 0.6f;
    private static final int    PROGRESS_ARC_SEGMENTS = 32;
    private static final int    OBJECTIVE_RING_SEGMENTS = 40;

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
    private final NightLightingRenderSystem nightLighting;
    private final ShuttleRenderSystem aircraft;
    private RenderContext rc;

    /**
     * Per-frame draw-command collector. Every world producer appends to it;
     * it is cleared at the top of {@link #renderWorld}.
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
     * All world-render producers, in same-layer submission order.
     * {@link #renderWorld} collects every selected producer into {@link #drawList}
     * and then drains layers in {@link RenderLayer} order. Across layers collect
     * order is immaterial; within one layer this list preserves painter order.
     */
    private final List<RenderSystem> worldSystems;

    /** One ground pass running {@code pass} of every drop's renderer in turn, the first drop's first. */
    private static RenderSystem eachDrop(List<NaturalVoidRenderSystem> drops,
                                         Function<NaturalVoidRenderSystem, RenderSystem> pass) {
        List<RenderSystem> passes = new ArrayList<>();
        for (NaturalVoidRenderSystem drop : drops) passes.add(pass.apply(drop));
        return RenderSystem.of(RenderLayer.GROUND, (ctx, out) -> {
            for (RenderSystem p : passes) p.collect(ctx, out);
        });
    }

    /**
     * Per-sheet quad batchers. Lazily constructed in {@link #buildTileBatches()}.
     * Reused across passes.
     */
    private QuadBatch urbanBatch;
    private QuadBatch roadBatch;
    private QuadBatch floorsBatch;
    /** The roof sheet: the streamed roof path draws through it on the frame before the resident mesh serves. */
    private QuadBatch roofBatch;
    private QuadBatch waterBatch;
    private QuadBatch urbanTile3Batch;
    private QuadBatch natureBatch;
    private QuadBatch doodadBatch;
    private QuadBatch parkedVehicleBatch;

    /**
     * Solid-color batch for in-loop fills that need to share painter
     * ordering with the textured batches.
     */
    private final SolidQuadBatch solidBatch = new SolidQuadBatch(256);

    /**
     * One draw across several sheets and both blends where the context allows it (law 27); a context that cannot
     * build its program drains one fixed-function run per sheet.
     */
    private final PagedQuadRun pagedRun = new PagedQuadRun();

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
    private final ProjectileTrailFxService projectileTrailFx = new ProjectileTrailFxService();

    /**
     * In-flight rounds an aircraft is putting on the ground. Owns their state
     * the same way {@link #contrailFx} owns trail state; the world pass below
     * is a pure emit. Constructed with the sprite store because a falling bomb
     * is drawn from its own texture.
     */
    private final OrdnanceTraceFxService ordnanceTraceFx;

    /**
     * Persistent decal-accumulator FBO.
     */
    private final DecalAccumulator decalAccumulator =
            new DecalAccumulator(DevConfig.DECAL_FBO_PX_PER_CELL);

    /**
     * The battle's resident ground, baked once and patched per changed cell.
     * Held here because it owns GL buffers and the host releases it with the
     * rest of the renderer's GPU state.
     */
    private final GroundMesh groundMesh = new GroundMesh();

    /**
     * Every sheet the ground layer draws from, as one texture. Held here
     * because it owns a GL texture and the host releases it with the rest of
     * the renderer's GPU state.
     */
    private final GroundAtlas groundAtlas = new GroundAtlas();

    /**
     * Every image a unit's body is composed from, as one texture. Held here for
     * the ground atlas's reason: it owns a GL texture, and the host releases it
     * with the rest of the renderer's GPU state.
     */
    private final UnitAtlas unitAtlas = new UnitAtlas();

    /**
     * The props layer, held for its {@link DecorationAtlas}: planned per battle
     * by the layer itself, and released here with the rest of the renderer's
     * GPU state.
     */
    private final DoodadRenderSystem doodads;

    /**
     * The battle's roofs, baked once and patched from the same change log the
     * ground reads. Held here because it owns GL buffers and the host releases
     * it with the rest of the renderer's GPU state.
     */
    private final RoofMesh roofMesh = new RoofMesh();
    /** Each building's roof style and the cells it covers, shared by the mesh and the stream. */
    private final RoofPlans roofPlans = new RoofPlans();
    private final RoofStream roofStream = new RoofStream();
    /** Buildings stood up ({@link BuildingFaces}): this frame's wall faces in their dress, and their rubble. */
    private final FacePainter facePainter = new FacePainter();
    /** Creep up a stood-up wall's face ({@link CreepClimb}), painted onto the wall {@link #facePainter} holds. */
    private final CreepClimb.Sink creepOnFace = facePainter::quad;
    private final CreepPalette creepPalette = CreepPalette.active();
    /** Draws the rubble at damaged walls' feet ({@link WallRubble}). */
    private final WallRubble wallRubble = new WallRubble();
    /** Razed buildings' broken shells, drawn over the roofs' remnants. */
    private final PolyMesh ruinShells = new PolyMesh(64);
    /** The overhead props' pass, which also draws the props hung on walls for the roof pass ({@link WallMounts}). */
    private PropMotionRenderSystem overheadProps;
    /** A wall mount's rail corners' eaves, reused. */
    private final float[] mountEaves = new float[4];
    /** Each building's wall dress ({@link WallDress}), settled with its outline. */
    private final IdentityHashMap<Building, Dress> buildingDress = new IdentityHashMap<>();
    /** The most hit points each wall cell has been seen to hold, so its hurt reads against its own ({@link WallDress#hurt}). */
    private int[] fullWallHp;
    /** Marks over the roofs where a unit stands hidden behind or inside a closed building ({@link #occludedUnits}). */
    private final PolyMesh occludedMarks = new PolyMesh(16);
    /** Walls that are no building's, stood up at their own heights ({@link FreestandingWalls}). */
    private final FreestandingWallFaces freestandingWalls = new FreestandingWallFaces();
    /** Whether this frame draws the fog: what is drawn above it carries the fog itself only then. */
    private boolean fogDrawn = true;
    private final IdentityHashMap<Building, float[]> buildingEdges = new IdentityHashMap<>();
    /** Each building's outline as built ({@link #builtOutline}), kept for the battle's map whatever falls. */
    private final IdentityHashMap<Building, float[]> buildingOutlines = new IdentityHashMap<>();
    /**
     * Cells beside a window the map was built with, kept across topology changes for this map: a window shot out
     * leaves no barrier behind, and its cell still shows the empty frame rather than a door.
     */
    private final BitSet windowCells = new BitSet();
    private Buildings edgesOf;
    private CellTopology edgesAt;

    /**
     * The battle's fog, resident as one map-sized alpha texture and patched from
     * the vision service's own changed-extent log. Held here because it owns a
     * GL texture and the host releases it with the rest of the renderer's GPU
     * state.
     */
    private final FogField fogField = new FogField();
    /** Where a night burn thins the fog over its own glow; see {@link FireFogClearing}. */
    private final FireFogClearing fireFogClearing = new FireFogClearing();
    private final CreepLayer creep = new CreepLayer();
    /** Grass-topped raised ground, baked once per battle and drawn over the ground tiles. */
    private final CliffArtLayer cliffArt = new CliffArtLayer();
    private final ScorchLayer scorch = new ScorchLayer();
    private final LavaFlowLayer lavaFlows = new LavaFlowLayer();
    private final FireRenderSystem fire;

    /** S3 render-side event lights consumed by the surface-relief composite. */
    private final GroundLightService groundLights = new GroundLightService();
    /** The bodies, lit by {@link #groundLights} or a review's own (law 27). */
    private final UnitRenderService units;
    /** Barrel smoke, ejected brass and damage smoke off map turrets; fed by the screen with the simulation's shots. */
    private final TurretFx turretFx = new TurretFx(0x7A11E7L);

    /** S2/S3 ground FBO + parallax/bump-light composite. {@link DevConfig#SURFACE_RELIEF_PARALLAX}-gated. */
    private final SunLight sun = new SunLight();
    private final GroundParallaxPipeline groundParallax;

    /** Ground-combat impact FX engine. */
    private final ImpactFx impactFx = new ImpactFx();
    /** Presentation-owned full-path beams whose afterimage outlives the ballistic event. */
    private final BeamFxService beamFx = new BeamFxService();

    /** World-layer renderer for the compound capture-state markers. */
    private final CompoundMarkerRenderer compoundMarkers = new CompoundMarkerRenderer();

    /** Reused per-frame fan for the charge-site progress arcs (one POLY for all sites). */
    private final PolyMesh objectiveArcMesh = new PolyMesh(64);
    private final BitmapFont objectiveMarkerFont = Fonts.ORBITRON_12_BOLD;

    /** Cell-highlight overlay renderer (selected-squad cue + debug sources). */
    private final HighlightRenderer highlightRenderer = new HighlightRenderer();

    // ---- constructor ---------------------------------------------------------

    public BattleRenderer(BattleSprites sprites) {
        this.sprites = sprites;
        this.fire = new FireRenderSystem(sprites);
        this.ordnanceTraceFx = new OrdnanceTraceFxService(sprites);
        this.groundParallax = new GroundParallaxPipeline(sprites, groundLights, sun);
        creep.useSun(sun);
        cliffArt.useSun(sun);
        VoidBackdropRenderSystem voidBackdrop = new VoidBackdropRenderSystem(sprites);
        voidBackdrop.useSun(sun);
        // One instance per drop a map may carry (a rift outside, a foundry's melt within), each over its own cells.
        List<NaturalVoidRenderSystem> drops = new ArrayList<>();
        for (int slot = 0; slot < CellTopology.MAX_NATURAL_VOIDS; slot++) {
            NaturalVoidRenderSystem drop = NaturalVoidRenderSystem.drop(sprites, slot);
            drop.useSun(sun);
            drops.add(drop);
        }
        RenderSystem naturalVoid = eachDrop(drops, d -> d);
        NaturalVoidRenderSystem naturalRise = new NaturalVoidRenderSystem(sprites, true);
        naturalRise.useSun(sun);
        SignatureRenderSystem signature = new SignatureRenderSystem(sprites);
        // The full world-render pass list, in paint order — every pass now lives
        // here (collect-all → drain-all; see renderWorld). Order is verbatim today's
        // pass sequence; RenderLayer ordinal mirrors it. Within a shared layer
        // (GROUND, CONVOY) list order = submission order = paint sub-order, so the
        // debug overlay producers sit right after the body producer they paint over.
        // Stateful/own-GL passes keep their state + render* bodies on this class and
        // join via RenderSystem.of(...) emitting a Custom (the FBO/own-GL escape hatch).
        this.doodads = new DoodadRenderSystem(sprites, new DoodadRenderSystem.Batches() {
            @Override public QuadBatch get(SpriteAPI sheet) { return batchBySheet.get(sheet); }
            @Override public void put(SpriteAPI sheet, QuadBatch batch) { registerBatch(sheet, batch); }
            @Override public void remove(SpriteAPI sheet) { batchBySheet.remove(sheet); }
        });
        ShipDressingRenderSystem dressing = new ShipDressingRenderSystem(doodads);
        PropMotionRenderSystem.Phases propPhases = new PropMotionRenderSystem.Phases();
        this.overheadProps = new PropMotionRenderSystem(sprites, PropMotion.Layer.OVERHEAD, propPhases);
        this.nightLighting = new NightLightingRenderSystem(sprites);
        this.aircraft = new ShuttleRenderSystem(sprites);
        this.units = new UnitRenderService(sprites);
        units.useLights(groundLights);
        nightLighting.useLights(groundLights);
        ColonyThreatFx colonyThreat = new ColonyThreatFx(sprites);
        // One shader for every shield; each host's shields drawn in its own layer, over it (ShieldDomeRenderSystem).
        ShieldDomeRenderSystem shields = new ShieldDomeRenderSystem();
        this.worldSystems = List.of(
                // The ground far below a map's void cells, under the tiles that cover it everywhere else.
                voidBackdrop,
                // The land falling away below a map's void cells: a gorge, a crevasse, a sea cliff.
                naturalVoid,
                new GroundRenderSystem(sprites, groundMesh, groundAtlas),
                // A volcanic world's ash drifts and glowing cracks, vents and embers, over its basalt tiles.
                new VolcanicGroundRenderSystem(sprites, lavaFlows),
                // A natural drop's lip fraying onto the ground beside it, over the tiles.
                eachDrop(drops, NaturalVoidRenderSystem::fray),
                // A lava rift's light on the ground at its lip, its embers, smoke and ash, over the tiles and the fray.
                eachDrop(drops, NaturalVoidRenderSystem::molten),
                // A glacial crevasse's cold breath, snow blown off its lip and snow falling, over the tiles and the fray.
                eachDrop(drops, NaturalVoidRenderSystem::glacial),
                // A rise's talus at its foot and its shadow on the floor, over the tiles.
                naturalRise.fray(),
                // The land standing up from the walkable ground through a map's rise cells, a ravine's walls:
                // over the tiles, so a rise leaning out over the floor is seen whole, and under every unit.
                naturalRise,
                naturalRise.glacial(),
                // Raised ground's cliffs and ramp steps over the tiles, inside the ground's colour target.
                cliffArt,
                // The steps of a ramp laid on built ground, which the cliff art leaves to the tiles.
                new BuiltStairRenderSystem(),
                // A built floor's trim where its panels step down to the slab: a ruins hall's raised floor and stage.
                new FloorTrimRenderSystem(sprites),
                // The planet type's grade over its wild ground and hills, and a metal world's flecks.
                signature,
                // Zone debug overlay paints on top of ground tiles, under decals.
                RenderSystem.of(RenderLayer.GROUND, (ctx, out) -> {
                    if (ctx.debugZonesVisible)
                        out.addCustom(RenderLayer.GROUND, () -> renderZoneOverlayDebug(ctx.sim, ctx.alphaMult));
                }),
                RenderSystem.of(RenderLayer.DECALS, (ctx, out) -> {
                    if (ctx.hostProfile.residentDecalsAllowed())
                        out.addCustom(RenderLayer.DECALS, () -> renderDecals(ctx.sim, ctx.alphaMult));
                    else DecalRenderSystem.collect(ctx.sim.getDecals(),sprites.decalSheet(),sprites.decalFrames(),
                            ctx.camera,ctx.alphaMult,out);
                }),
                // A cryovolcanic world's vent mounds and a bombarded world's craters lie on the ground with the decals.
                signature.marks(),
                // A nest's clutter lies on the creep with the decals, under everything that stands on it.
                new NestClutterRenderSystem(sprites),
                new ParkedAircraftRenderSystem(sprites),
                // A deck's Deficit dressing lies under its fixtures; its dead screens are the fixtures, unlit.
                RenderSystem.of(RenderLayer.DOODADS, dressing::collectBeneath),
                doodads,
                RenderSystem.of(RenderLayer.DOODADS, dressing::collectOver),
                // A hot world's haze and the vents' vapour, over the props and under the fog and the units.
                signature.air(),
                // Compartment doors stand in their doorways, over the floor and under the crew.
                new RoomDoorRenderSystem(sprites),
                // A battle map's one-cell doorways hang hinged doors that swing for whoever walks through.
                new SwingDoorRenderSystem(),
                // A locked door stands shut over the wall it is shut as, its sprite saying whether a charge can open it.
                new LockedDoorRenderSystem(sprites),
                // The ruins' hall and cargo doors, sliding leaf doors as a ship's are.
                new RuinsLeafDoorRenderSystem(sprites),
                // A walled place's gates, a pair of leaves that part for whoever comes through.
                new WallGateRenderSystem(sprites),
                // Burnt ground under the creep, inside the ground's colour target.
                scorch,
                creep,
                // A spine strike's run through the creep lies on the ground.
                colonyThreat.ground(),
                // Moving parts ride on top of their static bases: belts, fans, sliding seats.
                new PropMotionRenderSystem(sprites, PropMotion.Layer.DECK, propPhases),
                // Bodies cast here rather than through the ground composite:
                // terrain carries a height per cell and a marine does not.
                new UnitShadowRenderSystem(sprites, sun),
                RenderSystem.of(RenderLayer.HIGHLIGHTS, (ctx, out) ->
                        highlightRenderer.collect(ctx.highlights, ctx.camera, out, ctx.alphaMult)),
                new SelectionRingRenderSystem(),
                new OrderLineRenderSystem(),
                RenderSystem.of(RenderLayer.FOG, (ctx, out) -> collectFogOverlay(ctx, out)),
                RenderSystem.of(RenderLayer.FOG, doodads::collectVisibleSurfaces),
                // The unit atlas is built inside the layer it serves, the way
                // the ground atlas is, and only while it has GL work waiting (a
                // texture to build, or images it took in to upload): a settled
                // frame has no custom pass here at all, so it does not close and
                // reopen the drain's state bracket for a no-op. The bodies
                // collected below still draw as whole sprites in the frame that
                // bakes them.
                RenderSystem.of(RenderLayer.UNITS, (ctx, out) -> {
                    if (unitAtlas.needsSync()) {
                        out.addCustom(RenderLayer.UNITS, unitAtlas::sync);
                    }
                }),
                units,
                // Over the bodies, like the health bars: who the medic is and what they are doing.
                new MedicMarkerRenderSystem(sprites),
                // Beside it: the winged foot over a squad told to run.
                new SquadRunMarkerRenderSystem(),
                new SatchelRenderSystem(sprites),
                new PointDefenseRenderSystem(),
                shields,
                new TenderSiteRenderSystem(),
                // With the other threat marks: spines breaking out, and the spores a colony lifts.
                colonyThreat.over(),
                new SmokeRenderSystem(sprites),
                fire.flames(),
                RenderSystem.of(RenderLayer.ROOFS, (ctx, out) ->
                        collectRoofs(ctx, out, ctx.alphaMult)),
                new FortBuildingRenderSystem(sprites),
                // Overhead machinery such as a bay's travelling crane passes over whoever stands beneath it.
                overheadProps,
                new DroneRenderSystem(sprites),
                new SpirePerchRenderSystem(sprites),
                new HarrierRenderSystem(sprites),
                new SalvageCacheRenderSystem(sprites),
                new MissionMarkerRenderSystem(),
                // When the next shuttle sets down at each of the player's drop-off points.
                new LiftTimerRenderSystem(),
                RenderSystem.of(RenderLayer.OBJECTIVES, (ctx, out) ->
                        collectObjectiveMarkers(ctx.sim, out, ctx.alphaMult)),
                RenderSystem.of(RenderLayer.COMPOUND, (ctx, out) ->
                        { if (MissionObjectiveProjection.capture(ctx.sim).objectives().stream()
                                .noneMatch(item -> item.id().startsWith("territory-")))
                            compoundMarkers.collect(ctx.sim, ctx.sim.getCompoundService(), ctx.camera, out, ctx.alphaMult); }),
                new ConvoyRenderSystem(sprites),
                // A hull's shield stands over the hull it protects, as a boat's over her.
                shields.hullShields(),
                // Convoy debug overlays (own-GL line passes) paint over the convoy sprites.
                RenderSystem.of(RenderLayer.CONVOY, (ctx, out) ->
                        out.addCustom(RenderLayer.CONVOY, () -> {
                            long[] convoyIds = ctx.sim.getConvoyVehicleIds();
                            ConvoyService convoy = ctx.sim.convoy();
                            if (DEBUG_RENDER_DOCKING_PATHS) renderConvoyDockingPathsDebug(convoyIds, convoy, ctx.alphaMult);
                            renderSelectedVehicleDebug(convoy, ctx.alphaMult);
                        })),
                aircraft,
                shields.boatShields(),
                new ShipLightingRenderSystem(),
                // Dead air over the shade: it hangs in front of the deck rather than being darkened with it.
                RenderSystem.of(RenderLayer.DECK_LIGHTING, dressing::collectHaze),
                nightLighting,
                new WarshipRenderSystem(sprites),
                new LanceSweepRenderSystem(),
                // SHOTS: contrails first (the ContrailFxService emits RIBBON commands; its
                // trail lifecycle is ticked from BattleScreen.advance), then delivered
                // ordnance, lingering beams, and ShotRenderService body sweeps
                // (ordinary tracers → bolts → projectile sprites).
                // Listed in this order so submission stays stable.
                RenderSystem.of(RenderLayer.SHOTS, (ctx, out) ->
                        contrailFx.collect(out, ctx.alphaMult)),
                // Delivered ordnance sits with the shot bodies: it is rounds in
                // flight, and it belongs over the ground and under the impact FX
                // its own arrival spawns.
                RenderSystem.of(RenderLayer.SHOTS, (ctx, out) ->
                        ordnanceTraceFx.collect(ctx.camera, out, ctx.alphaMult)),
                RenderSystem.of(RenderLayer.SHOTS, (ctx, out) ->
                        beamFx.collect(ctx.camera, out, ctx.alphaMult)),
                new ShotRenderService(sprites),
                RenderSystem.of(RenderLayer.IMPACT_FX, (ctx, out) -> {
                    if (!ZoomDetail.impactParticlesVisible(ctx.camera)) return;
                    out.addCustom(RenderLayer.IMPACT_FX,
                            () -> impactFx.render(ctx.camera, ctx.alphaMult));
                }));
    }

    // ---- lifecycle -----------------------------------------------------------

    /**
     * Ensures impact-FX sprites. Called from {@code BattleScreen.attach()} after
     * all {@code sprites.ensureX()} calls.
     */
    public void onAttach() {
        sprites.ensureDecalSheet();
        registerDecalBatch();
        sprites.ensureRubbleSheet();
        SpriteAPI rubble = sprites.rubbleSheet();
        if (rubble != null && sprites.rubbleFrames() != null && !batchBySheet.containsKey(rubble)) {
            registerBatch(rubble, new QuadBatch(rubble, sprites.rubbleFrames().sheetWidth,
                    sprites.rubbleFrames().sheetHeight, 64));
        }
        sprites.ensureSmokeSprites();
        registerSmokeFieldBatch();
        sprites.ensureSatchelSprite();
        impactFx.ensureSprites(sprites.lease());
    }

    /** Builds the six per-sheet tile batches from the loaded sheets. Called from {@code BattleScreen.attach()}. */
    public void buildTileBatches() {
        if (urbanBatch == null && sprites.tileSheet() != null)
            urbanBatch = new QuadBatch(sprites.tileSheet(), sprites.tileSheetPxW(), sprites.tileSheetPxH(), 16384);
        if (roadBatch == null && sprites.roadSheet() != null)
            roadBatch = new QuadBatch(sprites.roadSheet(), sprites.roadSheetPxW(), sprites.roadSheetPxH(), 4096);
        if (floorsBatch == null && sprites.floorsSheet() != null)
            floorsBatch = new QuadBatch(sprites.floorsSheet(), sprites.floorsSheetPxW(), sprites.floorsSheetPxH(), 4096);
        if (roofBatch == null && sprites.roofSheet() != null)
            roofBatch = new QuadBatch(sprites.roofSheet(), sprites.roofSheetPxW(), sprites.roofSheetPxH(), 4096);
        if (waterBatch == null && sprites.waterSheet() != null)
            waterBatch = new QuadBatch(sprites.waterSheet(), sprites.waterSheetPxW(), sprites.waterSheetPxH(), 2048);
        if (urbanTile3Batch == null && sprites.urbanTile3Sheet() != null)
            urbanTile3Batch = new QuadBatch(sprites.urbanTile3Sheet(), sprites.urbanTile3SheetPxW(), sprites.urbanTile3SheetPxH(), 4096);
        if (natureBatch == null && sprites.natureSheet() != null)
            natureBatch = new QuadBatch(sprites.natureSheet(), sprites.natureSheetPxW(), sprites.natureSheetPxH(), 4096);
        if (doodadBatch == null && sprites.doodadSheet() != null)
            doodadBatch = new QuadBatch(sprites.doodadSheet(), sprites.doodadSheetPxW(), sprites.doodadSheetPxH(), 4096);
        if (parkedVehicleBatch == null && sprites.parkedVehicleSheet() != null)
            parkedVehicleBatch = new QuadBatch(sprites.parkedVehicleSheet(),
                    sprites.parkedVehicleSheetPxW(), sprites.parkedVehicleSheetPxH(), 256);

        // The ground layer's sheets as one texture. Planned here rather than at
        // first collect because the layout is GL-free arithmetic and its batch
        // has to be registered at the same seam as every other sheet's — the
        // drain resolves a sheet quad through this map and has nowhere else to
        // look. The texture itself is built later, inside the ground layer's
        // own custom pass.
        if (groundAtlas.plan(sprites)) {
            registerBatch(groundAtlas.sheet(), new QuadBatch(groundAtlas.sheet(),
                    groundAtlas.width(), groundAtlas.height(), 16384));
        }

        // The unit layer's composed bodies as one texture. Same seam and same
        // reason as the ground atlas above: the layout is GL-free arithmetic and
        // its batch has to be registered where every other sheet's is. The
        // DrawList is what re-addresses a whole-image draw onto it.
        if (unitAtlas.plan(sprites)) {
            registerBatch(unitAtlas.sheet(), new QuadBatch(unitAtlas.sheet(),
                    unitAtlas.width(), unitAtlas.height(), 2048));
            drawList.setSpriteAtlas(unitAtlas);
        }

        // Register the per-sheet batches so drainLayer can resolve a SheetQuad's
        // sheet to its batch. Same instances the inline tile passes use.
        registerBatch(sprites.tileSheet(), urbanBatch);
        registerBatch(sprites.roadSheet(), roadBatch);
        registerBatch(sprites.floorsSheet(), floorsBatch);
        registerBatch(sprites.roofSheet(), roofBatch);
        // The fallen hull's roof painting (HullRoof), cut cell by cell like the roof sheet.
        if (sprites.hullRoofSheet() != null && !batchBySheet.containsKey(sprites.hullRoofSheet())) {
            registerBatch(sprites.hullRoofSheet(), new QuadBatch(sprites.hullRoofSheet(),
                    sprites.hullRoofSheetPxW(), sprites.hullRoofSheetPxH(), 4096));
        }
        registerBatch(sprites.waterSheet(), waterBatch);
        registerBatch(sprites.urbanTile3Sheet(), urbanTile3Batch);
        registerBatch(sprites.natureSheet(), natureBatch);
        registerBatch(sprites.doodadSheet(), doodadBatch);
        registerBatch(sprites.parkedVehicleSheet(), parkedVehicleBatch);
        registerDecalBatch();
        for (SpriteAPI sheet : sprites.extraTerrainSheets()) {
            if (batchBySheet.containsKey(sheet)) continue;
            registerBatch(sheet, new QuadBatch(sheet,
                    Math.max(1, Math.round(sheet.getWidth())),
                    Math.max(1, Math.round(sheet.getHeight())), 4096));
        }

        // Sprite-sheet batches for the CONVOY layer (ConvoyRenderSystem). Its
        // sheets are loaded by ensureConvoySprites() before this runs
        // (BattleScreen.attach order). batchBySheet owns the QuadBatch refs.
        registerSpriteSheetBatches(sprites.allConvoySheets());
        // Worn plate over live hulls and mechs (ConvoyRenderSystem, MechScuffSink) addresses cells of the scuff
        // sheet, so it needs a batch or the drain drops its quads.
        ShuttleSpriteCache scuffs = sprites.spriteByPath(VehicleScuffs.SHEET);
        if (scuffs != null) registerHullBatches(List.of(scuffs));
        // And where on each mech part a mark may sit, read off its pixels now rather than on its first damaged frame.
        MechScuffSink.prepare(sprites, sprites.layeredMechSprites());
        // A Rav's worn chitin (RavWear) is cells of its own sheet, laid on the Matriarch's and the Brute's body parts.
        ShuttleSpriteCache ravWear = sprites.spriteByPath(RavWear.SHEET);
        if (ravWear != null) registerHullBatches(List.of(ravWear));
        RavMatriarchAssets matriarch = sprites.ravMatriarchSprites();
        if (matriarch != null) RavWear.Painter.prepare(sprites, matriarch.body, matriarch.head, matriarch.gape);
        RavBruteAssets brute = sprites.ravBruteSprites();
        if (brute != null) RavWear.Painter.prepare(sprites, brute.bodyFront, brute.bodyRear, brute.head);
        for (LayeredMechAssets livery : sprites.layeredMechLiveries().values()) MechScuffSink.prepare(sprites, livery);

        // Corpse sheets for the UNITS layer (UnitRenderService's corpse sweep) so
        // the fallen no layered or rig art lays down batch via SHEET_QUAD.
        // Loaded by ensureUnitSheets() before this runs (BattleScreen.attach order).
        registerSpriteSheetBatches(sprites.unitDeadSprites().values());

        // Hull sheets for the UNITS layer, so a burnt airframe can be drawn as
        // source strips of its own sprite (UnitRenderService's wreck pass).
        // Loaded by ensureAirframeSprites() before this runs, in both hosts.
        registerHullBatches(sprites.airframeSprites().values());

        // Some embedded hosts hand us an already-loaded sprite registry and do
        // not call onAttach(). The standalone battle screen does the reverse:
        // it builds its terrain batches first, then onAttach() loads smoke.
        // Register from both lifecycle seams so whichever one sees the sheet
        // first makes deployed smoke drawable by the live GL drain.
        registerSmokeFieldBatch();
    }

    private void registerDecalBatch() {
        SpriteAPI sheet = sprites.decalSheet();
        var frames = sprites.decalFrames();
        if (sheet != null && frames != null && !batchBySheet.containsKey(sheet))
            registerBatch(sheet,new QuadBatch(sheet,frames.sheetWidth,frames.sheetHeight,128));
    }

    private void registerSmokeFieldBatch() {
        registerSheetBatch(sprites.smokeFieldSheet());
        registerSheetBatch(sprites.fireSheet());
        registerSheetBatch(sprites.flameSheet());
    }

    private void registerSheetBatch(SpriteAPI sheet) {
        if (sheet == null || batchBySheet.containsKey(sheet)) return;
        int width = Math.max(1, Math.round(sheet.getWidth()));
        int height = Math.max(1, Math.round(sheet.getHeight()));
        registerBatch(sheet, new QuadBatch(sheet, width, height, 128));
    }

    /**
     * Builds + registers one {@link QuadBatch} per distinct hull sprite
     * (idempotent — several shuttle types share one image).
     *
     * <p>A hull is a single-frame sprite rather than a sheet, so it is only
     * here because something addresses part of it; a cache that never recorded
     * its pixel size cannot be addressed that way and is skipped.
     */
    private void registerHullBatches(java.util.Collection<ShuttleSpriteCache> caches) {
        for (ShuttleSpriteCache cache : caches) {
            if (cache == null || cache.sprite == null) continue;
            if (cache.pxW <= 0 || cache.pxH <= 0) continue;
            if (batchBySheet.containsKey(cache.sprite)) continue;
            registerBatch(cache.sprite, new QuadBatch(cache.sprite, cache.pxW, cache.pxH, 128));
        }
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

    /** Tracks {@link BattleSprites#terrainSheetCount()} so terrain batches are brought up to date only on growth. */
    private final GrowthGate terrainGate = new GrowthGate();
    /** Tracks the convoy sheet count; a vehicle line the texture set did not plan loads on first draw. */
    private final GrowthGate convoyGate = new GrowthGate();
    /** Tracks the images loaded by path; a warship's hull loads on its first draw. */
    private final GrowthGate pathSpriteGate = new GrowthGate();

    /**
     * A batch for every image loaded by path since attach ({@link BattleSprites#spriteByPath}): a warship's hull is
     * one, and once it is holed it is drawn as strips of its own sprite ({@code WarshipHullDamageRenderer}), so those
     * quads draw rather than drop in the drain and the hull does not vanish under its turrets.
     */
    private void registerLatePathBatches() {
        if (!pathSpriteGate.changed(sprites.pathSprites().size())) return;
        registerHullBatches(sprites.pathSprites());
    }

    /**
     * A batch for every catalog sheet loaded since attach — one a host loads on first ask
     * ({@link BattleSprites#extraTerrainSheet}) — so its quads draw rather than drop in the drain.
     */
    private void registerLateTerrainBatches() {
        if (!terrainGate.changed(sprites.terrainSheetCount())) return;
        for (SpriteAPI sheet : sprites.extraTerrainSheets()) {
            if (batchBySheet.containsKey(sheet)) continue;
            registerBatch(sheet, new QuadBatch(sheet,
                    Math.max(1, Math.round(sheet.getWidth())),
                    Math.max(1, Math.round(sheet.getHeight())), 4096));
        }
    }

    /**
     * A batch for every convoy sheet loaded since attach (a vehicle line {@code BattleTextureSet} did not plan loads
     * on first draw), and the scuff sheet worn plate draws from, so those quads draw rather than drop in the drain.
     */
    private void registerLateConvoyBatches() {
        List<UnitSpriteCache> sheets = sprites.allConvoySheets();
        if (!convoyGate.changed(sheets.size())) return;
        registerSpriteSheetBatches(sheets);
        ShuttleSpriteCache scuffs = sprites.spriteByPath(VehicleScuffs.SHEET);
        if (scuffs != null) registerHullBatches(List.of(scuffs));
        ShuttleSpriteCache ravWear = sprites.spriteByPath(RavWear.SHEET);
        if (ravWear != null) registerHullBatches(List.of(ravWear));
    }

    /**
     * Takes into the unit atlas every image the last collect drew in its layers without finding it there. Before the
     * collect, never between a collect and its drain, because a relayout moves every slot; a relayout is a texture of
     * a new size, so the atlas's batch is built again.
     */
    private void growUnitAtlas() {
        if (unitAtlas.grow() == SpriteAtlas.Growth.RELAID) {
            registerBatch(unitAtlas.sheet(), new QuadBatch(unitAtlas.sheet(),
                    unitAtlas.width(), unitAtlas.height(), 2048));
        }
    }

    private void registerBatch(SpriteAPI sheet, QuadBatch batch) {
        if (sheet != null && batch != null) batchBySheet.put(sheet, batch);
    }

    // ---- accessors for BattleScreen.advance() --------------------------------

    /** Accessor for {@code BattleScreen.advance()} — spawn and advance impact FX particles. */
    public ImpactFx getImpactFx() { return impactFx; }

    /** Authored lingering beam traces spawned and aged by the active presentation host. */
    public BeamFxService getBeamFx() { return beamFx; }

    /** Accessor for {@code BattleScreen.advance()} — tick the contrail trail lifecycle on real dt. */
    public ContrailFxService getContrailFx() { return contrailFx; }
    public ProjectileTrailFxService getProjectileTrailFx() { return projectileTrailFx; }
    /** In-flight delivered rounds. Spawned and advanced by the host that drives the battle. */
    public OrdnanceTraceFxService getOrdnanceTraceFx() { return ordnanceTraceFx; }

    /** Accessor for {@code BattleScreen.advance()} — pulse compound markers on wall-clock. */
    public CompoundMarkerRenderer getCompoundMarkers() { return compoundMarkers; }

    /** Accessor for the host's combat-event presentation pass. */
    public GroundLightService getGroundLights() { return groundLights; }

    public TurretFx getTurretFx() { return turretFx; }

    /**
     * Lights the ground composite and the bodies from {@code lights} instead of this renderer's own; null restores its own. A
     * headless review keeps the presentation state the screen feeds every tick in a feed of its own, lights among it.
     */
    void useGroundLights(GroundLightService lights) {
        groundParallax.useLights(lights != null ? lights : groundLights);
        units.useLights(lights != null ? lights : groundLights);
        nightLighting.useLights(lights != null ? lights : groundLights);
    }

    /**
     * Releases night lighting targets, the aircraft's reflected-light shader, the creep's textures and the paged
     * run's program.
     */
    public void disposeLighting() {
        creep.dispose();
        cliffArt.dispose();
        scorch.dispose();
        lavaFlows.dispose();
        nightLighting.dispose();
        aircraft.disposeLighting();
        pagedRun.dispose();
    }

    /**
     * Releases every GL resource this renderer made for itself — targets, resident meshes, the
     * fog texture, lighting and the three atlases — for a host that is done with it and will
     * build another before it draws again. The sprites it drew from are not its own and stay.
     */
    public void disposeGlResources() {
        decalAccumulator.dispose();
        groundParallax.dispose();
        groundMesh.dispose();
        roofMesh.dispose();
        fogField.dispose();
        disposeLighting();
        groundAtlas.dispose();
        unitAtlas.dispose();
        disposeDecorationAtlas();
    }

    /** Accessor for {@code BattleScreen.detach()} — release FBO resources. */
    public DecalAccumulator getDecalAccumulator() { return decalAccumulator; }

    /** Accessor for {@code BattleScreen.detach()} — release the resident ground buffers. */
    public GroundMesh getGroundMesh() { return groundMesh; }

    /** Accessor for {@code BattleScreen.detach()} — release the composited ground atlas. */
    public GroundAtlas getGroundAtlas() { return groundAtlas; }

    /** Accessor for {@code BattleScreen.detach()} — release the composited unit atlas. */
    public UnitAtlas getUnitAtlas() { return unitAtlas; }

    /** The current battle's decoration atlas, or null before the props layer has seen one. */
    public DecorationAtlas getDecorationAtlas() { return doodads.atlas(); }

    /** Accessor for {@code BattleScreen.detach()} — release the current battle's decoration atlas. */
    public void disposeDecorationAtlas() { doodads.disposeAtlas(); }

    /** Accessor for {@code BattleScreen.detach()} — release the resident roof buffers. */
    public RoofMesh getRoofMesh() { return roofMesh; }

    /** Accessor for {@code BattleScreen.detach()} — release the resident fog texture. */
    public FogField getFogField() { return fogField; }

    /** Accessor for {@code BattleScreen.detach()} — release the S2/S3 ground FBO set. */
    public GroundParallaxPipeline getGroundParallax() { return groundParallax; }

    /** The scene's sun, shared by every caster. */
    public SunLight getSun() { return sun; }

    @DebugOnly
    private void renderZoneOverlayDebug(BattleSimulation sim, float alphaMult) {
        com.dillon.starsectormarines.battle.nav.zone.ZoneGraph zones = sim.getZoneGraph();
        NavigationGrid grid = sim.getGrid();
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBegin(GL_QUADS);
        VisibleCellRect view = rc.camera.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS, grid.getWidth(), grid.getHeight());
        for (int y = view.minY(); y <= view.maxY(); y++) {
            for (int x = view.minX(); x <= view.maxX(); x++) {
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

    /**
     * The player's fog — from the resident field where there is one, and cell by
     * cell where there is not.
     *
     * <p>A battle's first frame always takes the second path: {@link FogField}
     * is GL-free to ask and cannot have baked anything before its own custom
     * pass has run, so the frame that builds the field also draws the ordinary
     * stream and the field serves from the next one. That is one frame of the
     * old cost per battle, and it is what keeps the collector free of GL.
     */
    private void collectFogOverlay(RenderContext ctx, DrawList out) {
        FogOfWarService vis = ctx.sim.getFogOfWar();
        if (!vis.isInitialized()) return;

        fireFogClearing.update(FireRenderSystem.enabled() ? ctx.sim.fireFieldIfPresent() : null,
                ctx.sim.getTopology().nightLighting() != null, vis.gridWidth(), vis.gridHeight());
        boolean resident = ctx.hostProfile.residentFogAllowed();
        if (resident && fogField.isServing(vis)) {
            float alpha = ctx.alphaMult;
            out.addCustom(RenderLayer.FOG, () -> {
                if (fogField.sync(vis)) fogField.draw(rc.camera, alpha, fireFogClearing);
            });
            return;
        }
        if (resident) {
            // Bakes during this frame's drain; draws nothing, because the stream
            // below is already this frame's fog.
            out.addCustom(RenderLayer.FOG, () -> fogField.sync(vis));
        }
        collectFogCells(ctx.sim, out, ctx.alphaMult);
    }

    private void collectFogCells(BattleSimulation sim, DrawList out, float alphaMult) {
        FogOfWarService vis = sim.getFogOfWar();
        boolean[] revealed = vis.cellShownArray();
        int gw = vis.gridWidth();
        int gh = vis.gridHeight();
        float cellPx = rc.camera.cellPxSize();
        VisibleCellRect view = rc.camera.visibleCells(VisibleCellRect.FOG_MARGIN_CELLS, gw, gh);

        for (int cy = view.minY(); cy <= view.maxY(); cy++) {
            int rowBase = cy * gw;
            for (int cx = view.minX(); cx <= view.maxX(); cx++) {
                int idx = rowBase + cx;
                int darkNeighbors = 0;
                if (revealed[idx]) {
                    if (cy > 0    && !revealed[idx - gw]) darkNeighbors++;
                    if (cy < gh-1 && !revealed[idx + gw]) darkNeighbors++;
                    if (cx > 0    && !revealed[idx - 1])  darkNeighbors++;
                    if (cx < gw-1 && !revealed[idx + 1])  darkNeighbors++;
                }
                float fogAlpha = fogAlphaForCell(revealed[idx], darkNeighbors,
                        vis.wouldBeRevealedWithoutTransientOpacity(cx, cy)) * fireFogClearing.keep(idx);
                if (fogAlpha <= 0f) continue;

                float sx = rc.camera.cellToScreenX(cx);
                float sy = rc.camera.cellToScreenY(cy);
                out.addSolidRect(RenderLayer.FOG, sx, sy, sx + cellPx, sy + cellPx,
                        0f, 0f, 0f, fogAlpha * alphaMult);
            }
        }
    }

    /**
     * The shadow one cell draws. {@link FogField#levelFor} owns the scale; this
     * is the per-cell stream's view of the same answer, so the resident field
     * and the commands cannot disagree about the picture.
     */
    static float fogAlphaForCell(boolean revealed, int darkNeighbors,
                                 boolean clearAirRevealed) {
        return FogField.alphaForLevel(
                FogField.levelFor(revealed, darkNeighbors, clearAirRevealed));
    }

    /**
     * The roof layer, from the resident mesh where there is one and cell by cell
     * where there is not.
     *
     * <p>A battle's first frame always takes the streamed path: {@link RoofMesh}
     * is GL-free to ask and cannot have baked anything before its own custom
     * pass has run, so the frame that builds the mesh also draws the ordinary
     * stream and the mesh serves from the next one. That is one frame of the old
     * cost per battle, and it is what keeps the collector free of GL (law 2).
     *
     * <p>What a roof is drawn as is {@link RoofTiles}: each building in its own
     * {@link RoofStyle}, over its walls, and broken by what hit it. A host
     * without the roof sheet falls back on paving every floor cell with the
     * {@code surfaceRender.ROOF} block, as roofs were drawn before they had art
     * of their own.
     */
    private void collectRoofs(RenderContext ctx, DrawList out, float alphaMult) {
        BattleSimulation sim = ctx.sim;
        riseEye();
        boolean standing = standing();
        // Freestanding walls stood up first, so every building's faces and roof stand over them. Flat (the Settings
        // page's 3D walls off, WallDepth) they are the map's wall tiles alone.
        if (standing) freestandingWalls.collect(sim, out, alphaMult, rc.camera, fogDrawn);
        Buildings buildings = sim.getBuildings();
        CellTopology topology = sim.getTopology();
        if (buildings == null || buildings.isEmpty()) {
            overheadProps.collectMounts(ctx, out, (d, corners) -> true);
            return;
        }
        // Sheets are ensured at BattleScreen.attach; collect stays GL-free. Guards cover the not-loaded case.
        if (sprites.roofSheet() == null) {
            streamPavedRoofs(sim, out, alphaMult, topology);
            overheadProps.collectMounts(ctx, out, (d, corners) -> true);
            return;
        }
        if (standing) {
            // Every building stood up: its walls turned to the camera, under every roof (BuildingFaces).
            buildingFaces(sim, out, alphaMult, topology);
            occludedUnits(sim, out, alphaMult, topology);
            // What hangs on the walls, on the faces it hangs from, under the roofs that stand in front of it.
            overheadProps.collectMounts(ctx, out, (d, corners) -> standMount(sim, d, corners));
        } else {
            // Flat: every roof over its footprint, what hangs on a wall lying where it was laid.
            facePainter.reset();
            ruinShells.reset();
            overheadProps.collectMounts(ctx, out, (d, corners) -> true);
        }
        if (rc.hostProfile.residentGroundAllowed()) {
            HullRoof ship = hullRoof(topology, buildings);
            RoofMesh.RoofResolver resolver = (building, x, y, sink) -> {
                RoofPlans.Plan plan = roofPlans.of(topology, buildings, building);
                // The ship's field and trims are drawn from its painting each frame (streamShipRoofs), not baked.
                RoofTiles.Sink to = ship != null && ship.of(building) != null ? (column, row, turns, x0, y0, x1, y1) -> {
                    if (RoofStream.damage(column)) sink.quad(column, row, turns, x0, y0, x1, y1);
                } : sink;
                RoofTiles.resolve(topology, x, y, plan.style, plan.grainTurns, to);
            };
            boolean serving = roofMesh.isServing(topology, buildings);
            out.addCustom(RenderLayer.ROOFS, () -> {
                boolean drawable = roofMesh.sync(topology, buildings, sprites.roofSheet(),
                        sprites.roofSheetPxW(), sprites.roofSheetPxH(), resolver, alphaMult,
                        b -> buildingClass(topology, buildings, b));
                if (drawable && serving) roofMesh.draw(rc.camera, roofLifts);
            });
            if (!serving) streamRoofs(sim, out, alphaMult, topology);
            else if (ship != null) streamShipRoofs(sim, out, alphaMult, topology, ship);
        } else {
            streamRoofs(sim, out, alphaMult, topology);
        }
        // A razed building's broken shell stands over its roof's remnants.
        if (!ruinShells.isEmpty()) out.addPoly(RenderLayer.ROOFS, ruinShells);
    }

    /**
     * Stands a wall mount up on its wall's face ({@link WallMounts#standUp}): the wall is the building's beyond the
     * mount's rail, at its height now and as much of it as stands, and each rail corner's eave is read off the face
     * segment over it. A mount on a razed building is gone with its wall; one on no building's wall lies as laid.
     */
    private boolean standMount(BattleSimulation sim, Doodad d, float[] corners) {
        CellTopology topology = sim.getTopology();
        Buildings buildings = sim.getBuildings();
        int[] step = WallMounts.wallStep(d.quarterTurns);
        float midX = (corners[0] + corners[2]) * .5f, midY = (corners[1] + corners[3]) * .5f;
        Building b = wallOf(topology, buildings, corners, step);
        if (b == null) return true;
        int heightClass = buildingClass(topology, buildings, b);
        if (heightClass == BuildingFaces.RAZED) return false;
        boolean frozen = topology.groundPalette() == SurfacePalette.FROZEN;
        float[] segs = buildingEdges.computeIfAbsent(b,
                k -> faceSegments(topology, sim.getGrid(), buildings, k, frozen));
        for (int k = 0; k < 2; k++) {
            float px = corners[k * 2], py = corners[k * 2 + 1];
            // Just inside the rail's span, so a corner on a segment's end reads the segment the mount hangs over.
            float sx = px + (midX - px) * .02f, sy = py + (midY - py) * .02f;
            WallMounts.eave(segs, -step[0], -step[1], px, py, sx, sy, mountEaves, k * 2);
        }
        float meters = BuildingFaces.HEIGHTS[heightClass];
        WallMounts.standUp(corners, mountEaves, rc.camera.panCellX(), rc.camera.panCellY(), meters,
                roofLifts[heightClass], BuildingFaces.standing(meters, 1f - b.currentAlpha),
                WallMounts.railMeters(d.definitionId()), WallMounts.outerMeters(d.definitionId()), roofRise);
        return true;
    }

    /**
     * The building whose wall a mount's rail runs along: the first building cell met on the wall's side of the rail,
     * cell by cell along it from its middle out (a doorway's own cell belongs to no building), a cell deep or two.
     */
    private static Building wallOf(CellTopology topology, Buildings buildings, float[] corners, int[] step) {
        float x0 = corners[0], y0 = corners[1], x1 = corners[2], y1 = corners[3];
        int cells = Math.max(1, Math.round(Math.abs(x1 - x0) + Math.abs(y1 - y0)));
        float ux = (x1 - x0) / cells, uy = (y1 - y0) / cells;
        for (int i = 0; i < cells; i++) {
            // From the middle out: middle, then either side of it.
            int k = cells / 2 + ((i & 1) == 0 ? i / 2 : -(i + 1) / 2);
            if (k < 0 || k >= cells) continue;
            float cx = x0 + ux * (k + .5f), cy = y0 + uy * (k + .5f);
            for (int depth = 0; depth < 2; depth++) {
                int wx = (int) Math.floor(cx + step[0] * (depth + .5f));
                int wy = (int) Math.floor(cy + step[1] * (depth + .5f));
                if (!topology.inBounds(wx, wy)) continue;
                Building b = buildings.get(topology.getBuildingId(wx, wy));
                if (b != null) return b;
            }
        }
        return null;
    }

    /**
     * Each height class's lift ({@link BuildingFaces#HEIGHTS}), the roofs' scale about the view's centre, for the
     * eye risen by {@link #roofRise}; set each frame from the view's width ({@link #riseEye}).
     */
    private final float[] roofLifts = new float[BuildingFaces.HEIGHTS.length];
    private float roofRise = -1f;

    {
        riseTo(1f);
    }

    /** Raises the eye for this frame's view ({@link BuildingFaces#rise}) and the roofs' lifts with it. */
    private void riseEye() {
        float rise = WallDepth.enabled() ? BuildingFaces.rise(rc.camera) : FLAT;
        if (rise != roofRise) riseTo(rise);
    }

    private void riseTo(float rise) {
        roofRise = rise;
        for (int c = 0; c < roofLifts.length; c++) {
            roofLifts[c] = rise == FLAT ? 1f : BuildingFaces.lift(BuildingFaces.HEIGHTS[c], rise);
        }
    }

    /** {@link #roofRise} for a flat view ({@link WallDepth} off): every roof lies over its footprint. */
    private static final float FLAT = Float.POSITIVE_INFINITY;

    /** Whether this frame's buildings and walls stand up ({@link WallDepth}), as {@link #riseEye} settled it. */
    private boolean standing() {
        return roofRise != FLAT;
    }

    /** Each building's height class now, worked out again whenever the topology changes. */
    private final IdentityHashMap<Building, Integer> buildingClass = new IdentityHashMap<>();
    private long classesAt = -1;

    /** A building's height class: its storeys, or rubble once destroyed ({@link BuildingFaces}). */
    private int buildingClass(CellTopology topology, Buildings buildings, Building b) {
        long now = topology.changeCount();
        if (now != classesAt || edgesAt != topology) {
            buildingClass.clear();
            buildingEdges.clear();
            classesAt = now;
        }
        return buildingClass.computeIfAbsent(b, k -> BuildingFaces.heightClass(built(topology, k), breachedShare(topology, k)));
    }

    /**
     * The share of a building's outer walls breached to rubble: each wall cell beside its floor that is not its own
     * floor, counted as standing (a wall or window) or breached (rubble). A roof fallen in (creep, a shell) or burnt
     * away leaves the walls standing, so it counts for nothing here.
     */
    private static float breachedShare(CellTopology topology, Building b) {
        int standing = 0, breached = 0;
        for (int i = 0; i < b.cellsX.length; i++) {
            for (int d = 0; d < 4; d++) {
                int x = b.cellsX[i] + (d == 0 ? 1 : d == 1 ? -1 : 0), y = b.cellsY[i] + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (!topology.inBounds(x, y) || topology.getBuildingId(x, y) == b.id) continue;
                if (topology.isWall(x, y) || topology.isWindow(x, y)) standing++;
                else if (topology.isRubble(x, y)) breached++;
            }
        }
        return standing + breached == 0 ? 0f : breached / (float) (standing + breached);
    }

    /** What a building was built as, for its height ({@link BuildingFaces#heightClass}). */
    private static BuildingFaces.Built built(CellTopology topology, Building b) {
        boolean sealed = false, tower = false;
        for (int i = 0; i < b.cellsX.length; i++) {
            if (topology.getRoomPurpose(b.cellsX[i], b.cellsY[i]) == RoomPurpose.GATE_TOWER) tower = true;
        }
        for (int y = b.minY - 1; y <= b.maxY + 1 && !sealed; y++) {
            for (int x = b.minX - 1; x <= b.maxX + 1; x++) {
                if (!topology.inBounds(x, y) || !topology.isWall(x, y)) continue;
                sealed = BuildingFaces.look(topology.getSurfaceId(x, y), false) == BuildingFaces.Look.SEALED;
                break;
            }
        }
        return new BuildingFaces.Built(b.kind, b.cellsX.length, sealed, tower, BuildingFaces.hash(b.id, 21));
    }

    /** A building's wall dress: its material and its facade's rhythm ({@link WallDress}). */
    private record Dress(WallDress.Material material, WallDress.Rhythm rhythm) { }

    /**
     * Buildings stood up ({@link BuildingFaces}): each building's walls turned to the camera, as faces from the foot
     * of its roof's outline on the ground to the same outline at its eave, the roof's own lift about the view's
     * centre, dressed in its kind's and world's material ({@link WallDress}) and painted cell by cell
     * ({@link FacePainter}): what stands under each (a window, a door, a loading door, a blind wall, or a breach open
     * to the room behind), its storeys' bays in the building's rhythm, and what the battle has done to it. A building
     * whose roof has fallen in over a wall loses that wall's upper storeys; a destroyed building stands as a broken
     * shell over its rubble. Emitted before the roofs, so every roof stands over the faces behind it; the shells after.
     */
    private void buildingFaces(BattleSimulation sim, DrawList out, float alphaMult, CellTopology topology) {
        Buildings buildings = sim.getBuildings();
        NavigationGrid grid = sim.getGrid();
        if (edgesOf != buildings || edgesAt != topology) {
            buildingEdges.clear();
            buildingOutlines.clear();
            // Every building's outline now, while its walls stand as the map built them.
            boolean frozenWorld = topology.groundPalette() == SurfacePalette.FROZEN;
            for (Building b : buildings.all()) {
                buildingOutlines.put(b, builtOutline(topology, roofPlans.of(topology, buildings, b).cells, frozenWorld));
            }
            // Every window the map was built with, on both cells beside its edge, before any is shot out.
            windowCells.clear();
            for (SharedEdgeBarrier barrier : grid.getEdgeBarriers()) {
                if (barrier.kind() != SharedEdgeBarrier.Kind.WINDOW) continue;
                Direction d = barrier.direction();
                windowCells.set(barrier.cellX() + barrier.cellY() * topology.getWidth());
                windowCells.set(barrier.cellX() + d.dx + (barrier.cellY() + d.dy) * topology.getWidth());
            }
            buildingClass.clear();
            buildingDress.clear();
            fullWallHp = null;
            edgesOf = buildings;
            edgesAt = topology;
        }
        int gridW = topology.getWidth();
        if (fullWallHp == null || fullWallHp.length != gridW * topology.getHeight()) {
            fullWallHp = new int[gridW * topology.getHeight()];
        }
        SurfacePalette palette = topology.groundPalette();
        boolean frozen = palette == SurfacePalette.FROZEN;
        float viewX = rc.camera.panCellX(), viewY = rc.camera.panCellY();
        float sx = rc.camera.cellToScreenX(viewX), sy = rc.camera.cellToScreenY(viewY);
        float cellX = rc.camera.cellToScreenX(1f) - rc.camera.cellToScreenX(0f);
        float cellY = rc.camera.cellToScreenY(1f) - rc.camera.cellToScreenY(0f);
        VisibleCellRect view = rc.camera.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS + 6,
                grid.getWidth(), grid.getHeight());
        float cellPx = Math.abs(cellX);
        FacePainter paint = facePainter;
        CreepField creep = sim.creepFieldIfPresent();
        if (creep != null && creep.isEmpty()) creep = null;
        paint.reset();
        ruinShells.reset();
        for (Building b : buildings.all()) {
            if (!view.intersects(b.minX - 1, b.maxX + 1, b.minY - 1, b.maxY + 1)) continue;
            float roofAlpha = alphaMult;
            if (roofAlpha <= 0.01f) continue;
            int heightClass = buildingClass(topology, buildings, b);
            float[] segs = buildingEdges.computeIfAbsent(b, k -> faceSegments(topology, grid, buildings, k, frozen));
            if (segs.length == 0) continue;
            float fog = FogField.roofShade(b.currentFog);
            BuildingFaces.Look look = LOOKS[((int) segs[7] >> 2) / SEGMENTS.length];
            Dress dress = buildingDress.computeIfAbsent(b, k -> new Dress(
                    WallDress.material(k.kind, look, palette, BuildingFaces.hash(k.id, 23)),
                    WallDress.rhythm(k.kind, look, BuildingFaces.hash(k.id, 24))));
            if (heightClass == BuildingFaces.RAZED) {
                // Destroyed: a broken shell a storey high at most, its corners standing tallest, rubble at its foot.
                float lift = BuildingFaces.lift(SHELL_METERS, roofRise);
                paint.into(ruinShells);
                for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
                    float x0 = segs[e], y0 = segs[e + 1], x1 = segs[e + 2], y1 = segs[e + 3];
                    float nx = segs[e + 4], ny = segs[e + 5];
                    boolean facing = BuildingFaces.facing(nx, ny, (x0 + x1) / 2f, (y0 + y1) / 2f, viewX, viewY);
                    paint.wall(rc.camera.cellToScreenX(x0), rc.camera.cellToScreenY(y0), rc.camera.cellToScreenX(x1),
                            rc.camera.cellToScreenY(y1), rc.camera.cellToScreenX(segs[e + 8]),
                            rc.camera.cellToScreenY(segs[e + 9]), rc.camera.cellToScreenX(segs[e + 10]),
                            rc.camera.cellToScreenY(segs[e + 11]), sx, sy, lift, nx * cellX, ny * cellY, roofAlpha);
                    float light = (facing ? BuildingFaces.shade(nx, ny) : BuildingFaces.shade(-nx, -ny)) * fog;
                    paint.shell(dress.material(), (int) segs[e + 6], ((int) segs[e + 7] & 3) != 0, light, !facing);
                    // Rubble lies on the open ground outside every side, whichever way the shell is seen from.
                    paint.rubble((int) segs[e + 6], WallRubble.RAZED, dress.material().wall(), fog);
                }
                paint.into(paint.faces);
                continue;
            }
            // Cut away (its roof faded to show the rooms): the near walls drop to a knee-high stub with the roof's
            // fade and the far walls' inner sides come up to frame the rooms. The walls stay opaque throughout.
            float cut = 1f - b.currentAlpha;
            float meters = BuildingFaces.HEIGHTS[heightClass];
            float lift = roofLifts[heightClass];
            boolean industrial = b.kind == BuildingKind.INDUSTRIAL || b.kind == BuildingKind.FORTIFIED;
            int storeys = BuildingFaces.storeys(meters);
            float upright = BuildingFaces.standing(meters, cut);
            boolean upper = BuildingFaces.upperWindows(b.kind, look);
            WallDress.Material m = dress.material();
            for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
                float x0 = segs[e], y0 = segs[e + 1], x1 = segs[e + 2], y1 = segs[e + 3];
                float nx = segs[e + 4], ny = segs[e + 5];
                float ax = rc.camera.cellToScreenX(x0), ay = rc.camera.cellToScreenY(y0);
                float bx = rc.camera.cellToScreenX(x1), by = rc.camera.cellToScreenY(y1);
                float fax = rc.camera.cellToScreenX(segs[e + 8]), fay = rc.camera.cellToScreenY(segs[e + 9]);
                float fbx = rc.camera.cellToScreenX(segs[e + 10]), fby = rc.camera.cellToScreenY(segs[e + 11]);
                paint.wall(ax, ay, bx, by, fax, fay, fbx, fby, sx, sy, lift, nx * cellX, ny * cellY, roofAlpha);
                // How tall it stands on screen: its foot's distance from the view's centre, times how far it lifts.
                float facePx = (float) Math.hypot((ax + bx) / 2f - sx, (ay + by) / 2f - sy) * (lift - 1f);
                boolean detail = BuildingFaces.detailed(cellPx, facePx / storeys);
                int code = (int) segs[e + 7];
                int corners = code & 3;
                int cell = (int) segs[e + 6], cx = cell % gridW, cy = cell / gridW;
                BuildingFaces.Segment kind = SEGMENTS[(code >> 2) % SEGMENTS.length];
                if (kind == BuildingFaces.Segment.LOADING && !industrial) kind = BuildingFaces.Segment.DOOR;
                // What has fallen in over the wall: the roof over the building's room just inside it. (A doorway's
                // own cell is open to the sky by design, so it says nothing.)
                int ix = cx - Math.round(nx), iy = cy - Math.round(ny);
                RoofDamage roof = topology.inBounds(ix, iy) && topology.getBuildingId(ix, iy) == b.id
                        ? topology.getRoofDamage(ix, iy) : RoofDamage.INTACT;
                boolean broken = roof == RoofDamage.COLLAPSED || roof == RoofDamage.BURNT;
                float hurt = 1f;
                if (kind != BuildingFaces.Segment.BREACH) {
                    int hp = grid.getWallHp(cx, cy);
                    int full = Math.max(fullWallHp[cell], hp);
                    fullWallHp[cell] = full;
                    hurt = WallDress.hurt(hp, full);
                    if (roof == RoofDamage.HOLED) hurt = Math.max(hurt, .35f);
                    else if (broken) hurt = Math.max(hurt, .5f);
                }
                // What came down lies on the open ground at the wall's foot, seen whichever way the wall faces.
                int fallen = WallRubble.amount(kind == BuildingFaces.Segment.BREACH, broken, hurt);
                if (fallen > 0) paint.rubble(cell, fallen, m.wall(), fog);
                if (!BuildingFaces.facing(nx, ny, (x0 + x1) / 2f, (y0 + y1) / 2f, viewX, viewY)) {
                    if (cut > .01f) {
                        // A far wall's inner side, seen over the cut-away room: plaster over a darker dado.
                        float in = (.62f + .1f * BuildingFaces.shade(-nx, -ny)) * fog;
                        float ir = (m.wall()[0] * .4f + .45f) * in, ig = (m.wall()[1] * .4f + .44f) * in;
                        float ib = (m.wall()[2] * .4f + .42f) * in;
                        paint.wall(ax, ay, bx, by, fax, fay, fbx, fby, sx, sy, lift, nx * cellX, ny * cellY,
                                alphaMult * Math.min(1f, cut));
                        paint.shaded(0f, 1f, 0f, 1f, ir * .8f, ig * .8f, ib * .8f, ir, ig, ib);
                        paint.quad(0f, 1f, 0f, .9f / meters, ir * .72f, ig * .72f, ib * .72f);
                        if (creep != null) {
                            // The creep climbs the room's side of the wall as well as the street's.
                            int at = (int) segs[e + 6];
                            CreepClimb.paint(creep.coverageAt(at), creep.vitalityAt(at), meters, 1f, at, creepPalette,
                                    in, detail, creepOnFace);
                        }
                    }
                    continue;
                }
                // An edge window is read live: its frame takes fire without the topology changing, and once shot out
                // the edge opens to a body, so it shows as the empty frame it now is.
                float glassHurt = 0f;
                if (kind == BuildingFaces.Segment.WINDOW) {
                    SharedEdgeBarrier pane = windowEdge(grid, cx, cy, nx, ny);
                    glassHurt = pane == null ? 1f : BuildingFaces.windowHurt(pane.structure(), pane.maxStructure());
                }
                float shade = BuildingFaces.shade(nx, ny) * fog;
                int along = ny != 0f ? cx : cy;
                if (kind == BuildingFaces.Segment.BREACH) {
                    // Open to the room behind: dark to the first storey's head, ragged; wall standing above it if any
                    // does.
                    float ground = 1f / storeys;
                    float top = broken ? Math.min(upright, WallDress.brokenTop(cell, storeys, .5f)) : upright;
                    if (top > ground) {
                        paint.paint(m, dress.rhythm(), b.kind, look, BuildingFaces.Segment.BLIND, corners, along, cell,
                                meters, storeys, top, broken, 1f, 0f, upper, shade, detail);
                    }
                    float head = Math.min(top, ground * .85f);
                    paint.quad(0f, 1f, 0f, head, .07f * fog, .06f * fog, .06f * fog);
                    paint.quad(.15f, .55f, head, Math.min(top, head + ground * .18f), .07f * fog, .06f * fog,
                            .06f * fog);
                    continue;
                }
                paint.paint(m, dress.rhythm(), b.kind, look, kind, corners, along, cell, meters, storeys, upright,
                        broken, hurt, glassHurt, upper, shade, detail);
                if (creep != null) {
                    CreepClimb.paint(creep.coverageAt(cell), creep.vitalityAt(cell), meters, upright, cell,
                            creepPalette, shade, detail, creepOnFace);
                }
            }
        }
        if (!paint.faces.isEmpty()) out.addPoly(RenderLayer.ROOFS, paint.faces);
        wallRubble.emit(out, paint.debris(), paint.debrisCount(), sprites.rubbleSheet(), sprites.rubbleFrames());
    }

    /** How high a razed building's shell would stand whole, metres: one storey. */
    private static final float SHELL_METERS = 3.6f;

    private static final BuildingFaces.Segment[] SEGMENTS = BuildingFaces.Segment.values();
    private static final BuildingFaces.Look[] LOOKS = BuildingFaces.Look.values();

    /**
     * A building's faces cut by cell ({@link BuildingFaces#segments}), each segment's spare slot holding its corners
     * in its low two bits and, above them, what stands under it ({@link BuildingFaces.Segment}, a door taken as a
     * loading door; the caller narrows it by kind) plus the building's habitat ({@link BuildingFaces.Look}) times the
     * segment kinds' count. The outline is the building's as built ({@link #builtOutline}), kept while the battle
     * lasts; what stands on each piece of it is read again at each topology change ({@link #stamp}), so a frame reads
     * it rather than the cells.
     */
    private float[] faceSegments(CellTopology topology, NavigationGrid grid, Buildings buildings, Building b,
                                 boolean frozen) {
        float[] built = buildingOutlines.computeIfAbsent(b,
                k -> builtOutline(topology, roofPlans.of(topology, buildings, k).cells, frozen));
        return stamp(built, topology, windowCells);
    }

    /**
     * The outline a building's faces stand on, from its walls as built, its habitat in each segment's spare slot.
     * Settled once: read again from the live cells, a wall's destroyed cells would move the roof's edge in behind them
     * and stand fresh, whole walls up inside the building, where the wall it had is a breach.
     */
    static float[] builtOutline(CellTopology topology, int[] roofCells, boolean frozen) {
        int gridW = topology.getWidth();
        float[] segs = BuildingFaces.segments(BuildingFaces.outline(roofCells, gridW,
                at -> RoofCover.rects(RoofCover.of(topology, at % gridW, at / gridW))), gridW);
        int look = BuildingFaces.look(wallSurface(topology, segs, gridW), frozen).ordinal();
        for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
            segs[e + 7] = ((int) segs[e + 7] & 3) | (look * SEGMENTS.length) << 2;
        }
        return segs;
    }

    /**
     * A copy of a {@link #builtOutline} with what stands on each segment now: a wall, a window (one the map was built
     * with, in {@code windowCells}, stays an empty frame once shot out), a door, or a breach where the wall's cell is
     * rubble.
     */
    static float[] stamp(float[] built, CellTopology topology, BitSet windowCells) {
        int gridW = topology.getWidth();
        float[] segs = built.clone();
        for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
            int code = (int) built[e + 7];
            int look = (code >> 2) / SEGMENTS.length;
            int cell = (int) segs[e + 6], cx = cell % gridW, cy = cell / gridW;
            int cover = RoofCover.of(topology, cx, cy);
            boolean pane = topology.isWindow(cx, cy);
            boolean window = pane || windowCells.get(cell) && !topology.isWall(cx, cy);
            boolean wall = window || topology.isWall(cx, cy);
            // A short piece is the reveal where the eave steps in at a doorway, over the floor beside it: wall, never
            // a breach. An opening in the wall's line is a doorway the map laid, unless it is the rubble a breach
            // leaves.
            float length = Math.abs(segs[e + 2] - segs[e]) + Math.abs(segs[e + 3] - segs[e + 1]);
            boolean reveal = length < .5f;
            boolean rubble = topology.isRubble(cx, cy);
            boolean door = !window && !rubble && (RoofCover.edge(cover) == RoofCover.Edge.DOOR || !wall && !reveal);
            boolean standing = door || wall || reveal;
            BuildingFaces.Segment kind = BuildingFaces.segment(standing, door, window, pane, true);
            segs[e + 7] = (code & 3) | (kind.ordinal() + look * SEGMENTS.length) << 2;
        }
        return segs;
    }

    /**
     * The window on a wall cell's outer edge, the side its face looks out of ({@code nx, ny}), or null: the
     * simulation's own window ({@link SharedEdgeBarrier.Kind#WINDOW}), which sight and fire pass and a body does not.
     */
    private static SharedEdgeBarrier windowEdge(NavigationGrid grid, int cx, int cy, float nx, float ny) {
        Direction out = nx > .5f ? Direction.E : nx < -.5f ? Direction.W : ny > .5f ? Direction.N : Direction.S;
        SharedEdgeBarrier barrier = grid.getEdgeBarrier(cx, cy, out);
        return barrier != null && barrier.kind() == SharedEdgeBarrier.Kind.WINDOW ? barrier : null;
    }

    private static final Faction[] FACTIONS = Faction.values();

    /**
     * A closed building hides the ground behind it and the rooms under it. A unit standing out past its far side, or
     * one the player sees inside it through a door or window, gets a small mark in its side's colour over the roofs,
     * so it stays readable; a hidden enemy stays hidden, as the fog of war has it.
     */
    private void occludedUnits(BattleSimulation sim, DrawList out, float alphaMult, CellTopology topology) {
        occludedMarks.reset();
        Buildings buildings = sim.getBuildings();
        UnitRosterService roster = sim.getRoster();
        FogOfWarService vis = sim.getFogOfWar();
        boolean fogHides = rc.hostProfile.fogHidesUnits() && vis != null;
        World world = sim.world();
        float viewX = rc.camera.panCellX(), viewY = rc.camera.panCellY();
        VisibleCellRect view = rc.camera.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                topology.getWidth(), topology.getHeight());
        float cellPx = Math.abs(rc.camera.cellToScreenX(1f) - rc.camera.cellToScreenX(0f));
        float r = Math.max(4f, cellPx * .3f), edge = Math.max(1.5f, cellPx * .08f);
        for (Faction faction : FACTIONS) {
            float cr, cg, cb;
            switch (faction) {
                case MARINE: case ALLY: cr = .35f; cg = .85f; cb = 1f; break;
                case DEFENDER: cr = 1f; cg = .38f; cb = .3f; break;
                case CIVILIAN: cr = .95f; cg = .9f; cb = .6f; break;
                default: cr = 1f; cg = .7f; cb = .2f;
            }
            for (long id : roster.factionDenseArray(faction)) {
                if (!world.isAlive(id)) continue;
                float x = world.x(id), y = world.y(id);
                int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
                if (!topology.inBounds(cx, cy) || !view.intersects(cx, cx, cy, cy)) continue;
                if (fogHides && vis.getUnitVisibility(roster.indexOf(id)) == FogOfWarService.VIS_HIDDEN) continue;
                int inside = topology.getBuildingId(cx, cy);
                float mx = x, my = y;
                if (inside != 0) {
                    // Seen inside a closed building (through a door or window): marked on its roof over where it
                    // stands, since the roof covers it. An open building shows its units as they are.
                    Building home = buildings.get(inside);
                    if (home == null || home.currentAlpha < .5f) continue;
                    int c = buildingClass(topology, buildings, home);
                    if (c == BuildingFaces.RAZED) continue;
                    mx = viewX + (x - viewX) * roofLifts[c];
                    my = viewY + (y - viewY) * roofLifts[c];
                } else if (!hiddenBehindBuilding(topology, buildings, x, y, viewX, viewY)) continue;
                float px = rc.camera.cellToScreenX(mx), py = rc.camera.cellToScreenY(my);
                float o = r + edge;
                occludedMarks.appendQuad(px, py - o, px + o, py, px, py + o, px - o, py, .05f, .05f, .06f,
                        .85f * alphaMult);
                occludedMarks.appendQuad(px, py - r, px + r, py, px, py + r, px - r, py, cr, cg, cb, .9f * alphaMult);
            }
        }
        if (!occludedMarks.isEmpty()) out.addPoly(RenderLayer.DRONES, occludedMarks);
    }

    /**
     * The building whose roof is drawn at {@code (x, y)} (cells, as the pointer reads the ground) under the camera's
     * view now, the tallest first, or 0: what the pointer is over once the roofs are lifted.
     */
    public int buildingUnderPointer(BattleSimulation sim, float x, float y, float viewX, float viewY) {
        CellTopology topology = sim.getTopology();
        Buildings buildings = sim.getBuildings();
        if (topology == null || buildings == null || buildings.isEmpty()) return 0;
        for (int c = BuildingFaces.RAZED - 1; c >= 0; c--) {
            float lift = roofLifts[c];
            int bx = (int) Math.floor(BuildingFaces.unlifted(x, viewX, lift));
            int by = (int) Math.floor(BuildingFaces.unlifted(y, viewY, lift));
            if (!topology.inBounds(bx, by)) continue;
            int owner = RoofCover.owner(topology, bx, by);
            if (owner == 0) continue;
            Building b = buildings.get(owner);
            if (b != null && buildingClass(topology, buildings, b) == c) return owner;
        }
        return 0;
    }

    /** Whether some stood-up building's roof or wall is drawn over the ground at {@code (x, y)}. */
    private boolean hiddenBehindBuilding(CellTopology topology, Buildings buildings, float x, float y, float viewX,
                                         float viewY) {
        for (int c = 0; c < BuildingFaces.RAZED; c++) {
            float lift = roofLifts[c];
            int bx = (int) Math.floor(BuildingFaces.unlifted(x, viewX, lift));
            int by = (int) Math.floor(BuildingFaces.unlifted(y, viewY, lift));
            if (!topology.inBounds(bx, by)) continue;
            int owner = RoofCover.owner(topology, bx, by);
            if (owner == 0) continue;
            Building b = buildings.get(owner);
            // An open building's roof is gone and its near walls are at the knee: it hides nothing behind it.
            if (b != null && b.currentAlpha >= .5f && buildingClass(topology, buildings, b) == c) return true;
        }
        return false;
    }

    /** The surface of the first standing wall under a building's outline: what its habitat built it of. */
    private static String wallSurface(CellTopology topology, float[] segs, int gridW) {
        for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
            int cell = (int) segs[e + 6], x = cell % gridW, y = cell / gridW;
            if (topology.isWall(x, y)) return topology.getSurfaceId(x, y);
        }
        return null;
    }

    /** Every visible roof cell's pieces as commands of their own — the path residency replaces. */
    private void streamRoofs(BattleSimulation sim, DrawList out, float alphaMult, CellTopology topology) {
        Buildings buildings = sim.getBuildings();
        HullRoof ship = hullRoof(topology, buildings);
        int gridW = topology.getWidth();
        VisibleCellRect view = rc.camera.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                sim.getGrid().getWidth(), sim.getGrid().getHeight());
        for (Building b : buildings.all()) {
            float roofAlpha = b.currentAlpha;
            if (roofAlpha <= 0.01f) continue;
            // A roof reaches one cell past its floor, over the walls that carry it.
            if (!view.intersects(b.minX - 1, b.maxX + 1, b.minY - 1, b.maxY + 1)) continue;
            RoofPlans.Plan plan = roofPlans.of(topology, buildings, b);
            roofStream.ship = ship != null ? ship.of(b) : null;
            roofStream.shipOnly = false;
            roofStream.begin(out, b, roofAlpha * alphaMult);
            roofStream.lift(roofLifts[buildingClass(topology, buildings, b)],
                    rc.camera.cellToScreenX(rc.camera.panCellX()), rc.camera.cellToScreenY(rc.camera.panCellY()));
            for (int at : plan.cells) {
                int cx = at % gridW;
                int cy = at / gridW;
                if (!view.contains(cx, cy)) continue;
                roofStream.cellX = cx;
                roofStream.cellY = cy;
                RoofTiles.resolve(topology, cx, cy, plan.style, plan.grainTurns, roofStream);
            }
        }
        roofStream.ship = null;
    }

    /**
     * The fallen hull's roof over the resident mesh: the mesh holds only its damage, so its field is cut from the
     * ship's painting here, cell by cell, each frame ({@link HullRoof}).
     */
    private void streamShipRoofs(BattleSimulation sim, DrawList out, float alphaMult, CellTopology topology,
                                 HullRoof ship) {
        Buildings buildings = sim.getBuildings();
        int gridW = topology.getWidth();
        VisibleCellRect view = rc.camera.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                sim.getGrid().getWidth(), sim.getGrid().getHeight());
        roofStream.shipOnly = true;
        for (Building b : buildings.all()) {
            HullRoof.Section section = ship.of(b);
            if (section == null || b.currentAlpha <= 0.01f) continue;
            roofStream.ship = section;
            if (!view.intersects(b.minX - 1, b.maxX + 1, b.minY - 1, b.maxY + 1)) continue;
            RoofPlans.Plan plan = roofPlans.of(topology, buildings, b);
            roofStream.begin(out, b, b.currentAlpha * alphaMult);
            roofStream.lift(roofLifts[buildingClass(topology, buildings, b)],
                    rc.camera.cellToScreenX(rc.camera.panCellX()), rc.camera.cellToScreenY(rc.camera.panCellY()));
            for (int at : plan.cells) {
                int cx = at % gridW;
                int cy = at / gridW;
                if (!view.contains(cx, cy)) continue;
                roofStream.cellX = cx;
                roofStream.cellY = cy;
                RoofTiles.resolve(topology, cx, cy, plan.style, plan.grainTurns, roofStream);
            }
        }
        roofStream.ship = null;
        roofStream.shipOnly = false;
    }

    private HullRoof hullRoof;
    private CellTopology hullRoofTopology;
    private Buildings hullRoofBuildings;

    /** The fallen hull among this battle's roofs, found once a battle; null without one or without its painting. */
    private HullRoof hullRoof(CellTopology topology, Buildings buildings) {
        if (sprites.hullRoofSheet() == null) return null;
        if (topology != hullRoofTopology || buildings != hullRoofBuildings) {
            hullRoofTopology = topology;
            hullRoofBuildings = buildings;
            hullRoof = HullRoof.find(topology, buildings, roofPlans);
        }
        return hullRoof;
    }

    /**
     * The streamed path's sink: a piece is its frame's rectangle cut from the
     * sheet, turned onto the cell. The turn is a rotation of the command about
     * the piece's centre, counter-clockwise on screen, so a clockwise quarter
     * turn is minus ninety degrees; the source rectangle is the piece seen in
     * its frame before the turn, so its width and height are the frame's.
     */
    private final class RoofStream implements RoofTiles.Sink {
        private DrawList out;
        private float r;
        private float g;
        private float b;
        private float a;
        int cellX;
        int cellY;
        /** The ship's section the building being drawn is part of: its field comes from the ship's painting. */
        HullRoof.Section ship;
        /** Draw only the ship's field: its damage is in the resident mesh. */
        boolean shipOnly;
        /** A roof stood up ({@link BuildingFaces}): its scale about the view's centre, on screen; 1 lies flat. */
        private float lift = 1f, liftX, liftY;

        void lift(float lift, float x, float y) {
            this.lift = lift;
            liftX = x;
            liftY = y;
        }

        void begin(DrawList out, Building building, float alpha) {
            this.out = out;
            // Over the fog, so a roof nobody can see is darkened here instead.
            float fogShade = FogField.roofShade(building.currentFog);
            // The ship is painted in its own colours; a town's tint is for telling neighbouring roofs apart.
            r = (ship != null ? 1f : RoofTiles.tint(building.tintR)) * fogShade;
            g = (ship != null ? 1f : RoofTiles.tint(building.tintG)) * fogShade;
            b = (ship != null ? 1f : RoofTiles.tint(building.tintB)) * fogShade;
            a = alpha;
        }

        /** Whether sheet column {@code column} is a broken roof's frame, which the ship draws as any roof does. */
        static boolean damage(int column) {
            return column == RoofTiles.HOLED || column == RoofTiles.COLLAPSED || column == RoofTiles.BURNT;
        }

        /** A field piece of the ship: the painting where the piece's rectangle falls on the hull's plan. */
        private void shipQuad(float x0, float y0, float x1, float y1) {
            HullRoof.Section s = ship;
            float ua = s.planU(cellX + x0, cellY + y0), ub = s.planU(cellX + x1, cellY + y1);
            float va = s.planV(cellX + x0, cellY + y0), vb = s.planV(cellX + x1, cellY + y1);
            float u0 = Math.min(ua, ub), u1 = Math.max(ua, ub);
            float v0 = Math.min(va, vb), v1 = Math.max(va, vb);
            float pxU = sprites.hullRoofSheetPxW() / (float) HullPlan.LENGTH;
            float pxV = sprites.hullRoofSheetPxH() / (float) HullPlan.BREADTH;
            int srcX = Math.round(u0 * pxU);
            int srcY = Math.round((HullPlan.BREADTH - v1) * pxV);
            int srcW = Math.max(1, Math.round(u1 * pxU) - srcX);
            int srcH = Math.max(1, Math.round((HullPlan.BREADTH - v0) * pxV) - srcY);
            float cellPx = rc.camera.cellPxSize();
            float cx = rc.camera.cellToScreenX(cellX + (x0 + x1) * 0.5f);
            float cy = rc.camera.cellToScreenY(cellY + (y0 + y1) * 0.5f);
            if (lift != 1f) {
                cx = liftX + (cx - liftX) * lift;
                cy = liftY + (cy - liftY) * lift;
                cellPx *= lift;
            }
            out.addSheetQuad(RenderLayer.ROOFS, sprites.hullRoofSheet(), srcX, srcY, srcW, srcH,
                    cx, cy, (u1 - u0) * cellPx, (v1 - v0) * cellPx, s.angleDeg(), r, g, b, a);
        }

        @Override
        public void quad(int column, int row, int turns, float x0, float y0, float x1, float y1) {
            if (ship != null) {
                if (column >= RoofTiles.FIELD && column < RoofTiles.FIELD + 4) {
                    shipQuad(x0, y0, x1, y1);
                    return;
                }
                // Its own rim stands in for the eave trims; its damage is the mesh's when the mesh serves.
                if (!damage(column) || shipOnly) return;
            }
            float frameW = sprites.roofSheetPxW() / (float) RoofTiles.COLUMNS;
            float frameH = sprites.roofSheetPxH() / (float) RoofStyle.COUNT;
            float ua = RoofTiles.frameU(turns, x0, y0);
            float ub = RoofTiles.frameU(turns, x1, y1);
            float va = RoofTiles.frameV(turns, x0, y0);
            float vb = RoofTiles.frameV(turns, x1, y1);
            float u0 = Math.min(ua, ub);
            float u1 = Math.max(ua, ub);
            float v0 = Math.min(va, vb);
            float v1 = Math.max(va, vb);
            // The sheet pixels RoofMesh addresses too, so the resident roof draws what this draws.
            int srcX = RoofTiles.framePixel(column, u0, frameW);
            int srcY = RoofTiles.framePixel(row, 1f - v1, frameH);
            int srcW = Math.max(1, RoofTiles.framePixel(column, u1, frameW) - srcX);
            int srcH = Math.max(1, RoofTiles.framePixel(row, 1f - v0, frameH) - srcY);
            float cellPx = rc.camera.cellPxSize();
            float cx = rc.camera.cellToScreenX(cellX + (x0 + x1) * 0.5f);
            float cy = rc.camera.cellToScreenY(cellY + (y0 + y1) * 0.5f);
            if (lift != 1f) {
                cx = liftX + (cx - liftX) * lift;
                cy = liftY + (cy - liftY) * lift;
                cellPx *= lift;
            }
            out.addSheetQuad(RenderLayer.ROOFS, sprites.roofSheet(),
                    srcX, srcY, srcW, srcH,
                    cx, cy, (u1 - u0) * cellPx, (v1 - v0) * cellPx, -90f * (turns & 3),
                    r, g, b, a);
        }
    }

    /**
     * Roofs paved with the {@code surfaceRender.ROOF} block, one tile per floor
     * cell, for a host that has the floors sheet but not the roof sheet.
     */
    private void streamPavedRoofs(BattleSimulation sim, DrawList out, float alphaMult,
                                  CellTopology topology) {
        if (sprites.floorsSheet() == null) return;
        GridBlockDef brick = GenMappingRegistry.installedSurfaceBlock(SurfaceRole.ROOF);
        if (brick == null) return;
        Buildings buildings = sim.getBuildings();
        VisibleCellRect view = rc.camera.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                sim.getGrid().getWidth(), sim.getGrid().getHeight());
        for (Building b : buildings.all()) {
            float roofAlpha = b.currentAlpha;
            if (roofAlpha <= 0.01f) continue;
            if (!view.intersects(b.minX, b.maxX, b.minY, b.maxY)) continue;
            float fogShade = FogField.roofShade(b.currentFog);
            for (int i = 0, n = b.cellCount(); i < n; i++) {
                int cx = b.cellsX[i];
                int cy = b.cellsY[i];
                if (!view.contains(cx, cy)) continue;
                if (topology.isRoofDestroyed(cx, cy)) continue;
                int[] c = brick.resolve(false, false, false, false, cx, cy);
                TileManifest.TileFrame f = new TileManifest.TileFrame(c[0], c[1]);
                addSmallTileTinted(out, f, cx, cy,
                        b.tintR * fogShade, b.tintG * fogShade, b.tintB * fogShade, roofAlpha * alphaMult);
            }
        }
    }

    private void addSmallTileTinted(DrawList out, TileManifest.TileFrame f,
                                    int gridX, int gridY,
                                    float r, float g, float b, float alphaMult) {
        int inset = GROUND_SMALL_TILE_EDGE_INSET_PX;
        int srcPxX = f.col * TileManifest.FLOORS_TILE_SIZE + inset;
        int srcTopPxY = f.row * TileManifest.FLOORS_TILE_SIZE + inset;
        int srcPxW = TileManifest.FLOORS_TILE_SIZE - 2 * inset;
        int srcPxH = TileManifest.FLOORS_TILE_SIZE - 2 * inset;

        float cellPx = rc.camera.cellPxSize();
        float cx = rc.camera.cellToScreenX(gridX + 0.5f);
        float cy = rc.camera.cellToScreenY(gridY + 0.5f);
        out.addSheetQuad(RenderLayer.ROOFS, sprites.floorsSheet(),
                srcPxX, srcTopPxY, srcPxW, srcPxH,
                cx, cy, cellPx, cellPx,
                r, g, b, alphaMult);
    }

    /** Debug flag — draws Reeds-Shepp docking and turnaround paths under each hull; off until toggled from the battle debug menu. */
    @DebugOnly
    public static boolean DEBUG_RENDER_DOCKING_PATHS = false;

    @DebugOnly
    private void renderConvoyDockingPathsDebug(long[] ids, ConvoyService convoy, float alphaMult) {
        boolean any = false;
        for (long id : ids) {
            VehicleControlComponent ctl = convoy.control(id);
            if (ctl != null && ctl.dockingPath() != null) { any = true; break; }
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
        for (long id : ids) {
            VehicleMission v = convoy.mission(id);
            if (v == null) continue;
            VehicleControlComponent ctrl = convoy.control(id);
            if (ctrl == null) continue;
            ReedsShepp.Path path = ctrl.dockingPath();
            if (path == null) continue;
            Pose start = ctrl.dockingStartPose();
            float R = ctrl.dockingTurnRadius();

            float cursor = 0f;
            for (ReedsShepp.Element e : path.elements) {
                float segCells = e.length * R;
                if (segCells <= 0f) continue;
                float segEnd = cursor + segCells;

                if (e.forward) org.lwjgl.opengl.GL11.glColor4f(0.2f, 1f, 0.3f, 0.85f * alphaMult);
                else           org.lwjgl.opengl.GL11.glColor4f(1f, 0.25f, 0.2f, 0.85f * alphaMult);

                org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINE_STRIP);
                for (float d = cursor; d <= segEnd; d += STEP_CELLS) {
                    Pose p = ReedsShepp.sample(start, R, path, d);
                    org.lwjgl.opengl.GL11.glVertex2f(rc.camera.cellToScreenX(p.x), rc.camera.cellToScreenY(p.y));
                }
                Pose endP = ReedsShepp.sample(start, R, path, segEnd);
                org.lwjgl.opengl.GL11.glVertex2f(rc.camera.cellToScreenX(endP.x), rc.camera.cellToScreenY(endP.y));
                org.lwjgl.opengl.GL11.glEnd();

                cursor = segEnd;
            }
        }

        org.lwjgl.opengl.GL11.glPopAttrib();
    }

    @DebugOnly
    private void renderSelectedVehicleDebug(ConvoyService convoy, float alphaMult) {
        // Hosts without a selection model (selection=null) have no
        // selected vehicle and no use for this dev overlay — skip rather than NPE.
        if (rc.selection == null) return;
        long selId = rc.selection.getSelectedVehicleId();
        if (selId == 0L) return;
        VehicleMission v = convoy.mission(selId);
        if (v == null) return;   // stale selection (reaped vehicle) — has-gated, no throw
        GroundBody body = convoy.body(selId);
        VehicleType type = convoy.vehicleType(selId);
        GroundTurret turret = convoy.turret(selId);
        VehicleControlComponent ctrl = convoy.control(selId);
        int wp = (ctrl != null) ? ctrl.waypointIndex() : 1;
        float stuckSecs = (ctrl != null) ? ctrl.wallStuckTime() : 0f;
        float trajProg = (ctrl != null) ? ctrl.trajectoryProgress() : 0f;

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

        float[] xs = (v.state == VehicleState.DEPARTING)
                ? v.outboundX : v.inboundX;
        float[] ys = (v.state == VehicleState.DEPARTING)
                ? v.outboundY : v.inboundY;

        org.lwjgl.opengl.GL11.glLineWidth(2f);
        if (xs.length > 1) {
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINE_STRIP);
            for (int i = 0; i < xs.length; i++) {
                if (i < wp) {
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
        if (wp >= 0 && wp < xs.length) {
            org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 0f, 0.9f * alphaMult);
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_POINTS);
            org.lwjgl.opengl.GL11.glVertex2f(
                    rc.camera.cellToScreenX(xs[wp]),
                    rc.camera.cellToScreenY(ys[wp]));
            org.lwjgl.opengl.GL11.glEnd();
        }

        org.lwjgl.opengl.GL11.glColor4f(0f, 1f, 0.3f, 0.9f * alphaMult);
        org.lwjgl.opengl.GL11.glPointSize(5f);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_POINTS);
        org.lwjgl.opengl.GL11.glVertex2f(
                rc.camera.cellToScreenX(body.x), rc.camera.cellToScreenY(body.y));
        org.lwjgl.opengl.GL11.glEnd();

        org.lwjgl.opengl.GL11.glPopAttrib();

        float textX = rc.camera.cellToScreenX(body.x) + 20f;
        float textY = rc.camera.cellToScreenY(body.y) + 40f;
        float lineH = 16f;
        com.dillon.starsectormarines.ui.BitmapFont font = com.dillon.starsectormarines.ui.Fonts.INSIGNIA_15_AA;
        java.awt.Color c = new java.awt.Color(0.8f, 1f, 0.8f, 1f);

        font.drawString(String.format("state: %s  wp: %d/%d", v.state, wp, xs.length),
                textX, textY, c, alphaMult);
        textY -= lineH;
        font.drawString(String.format("structure: %.0f/%.0f  armor: %.0f/%.0f (%.0f)",
                convoy.structure(selId), convoy.maxStructure(selId),
                convoy.armor(selId), convoy.maxArmor(selId), convoy.armorRating(selId)),
                textX, textY, c, alphaMult);
        textY -= lineH;
        font.drawString(String.format("speed: %.1f  facing: %.0f  stuck: %.2fs",
                body.speed, body.facingDegrees, stuckSecs), textX, textY, c, alphaMult);
        textY -= lineH;
        String pathLabel = (ctrl != null && ctrl.dockingPath() != null) ? "docking"
                : (ctrl != null && ctrl.hasTrajectory()) ? "tracking" : "corridor";
        font.drawString(String.format("pos: (%.1f, %.1f)  path: %s  wps: %d+%d  prog: %.1f",
                body.x, body.y, pathLabel,
                v.inboundX.length, v.outboundX.length,
                trajProg), textX, textY, c, alphaMult);
        textY -= lineH;
        if (type.hasTurretWeapon() && turret != null) {
            font.drawString(String.format("turret: ammo=%d  facing=%.0f",
                    turret.ammo, turret.facingDeg), textX, textY, c, alphaMult);
            textY -= lineH;
        }
        font.drawString("[F5] dump state to JSON", textX, textY,
                new java.awt.Color(0.6f, 0.6f, 0.6f, 1f), alphaMult * 0.7f);
    }

    /**
     * World objectives as commands. Charge sites use the same compact beacon
     * grammar as capture points; equipment and pending-fire markers retain
     * their distinct simulation-owned symbology.
     */
    private void collectObjectiveMarkers(BattleSimulation sim, DrawList out, float alphaMult) {
        // A host that owns its clock (a deterministic review, a deck scene) pins the markers' pulse to it.
        float now = Float.isFinite(rc.animationTimeSeconds) ? rc.animationTimeSeconds
                : (float) (System.currentTimeMillis() / 1000.0);
        float cellPx = rc.camera.cellPxSize();
        objectiveArcMesh.reset();
        if (sim.getColdAccount() != null) {
            var intervention = sim.getColdAccount();
            for (var marker : ColdAccountMarkers.forPhase(intervention.phase(), intervention.layout(), intervention.companyRecovered())) {
                if (marker.kind() != ColdAccountMarkers.Kind.DANGER) continue;
                float cx = rc.camera.cellToScreenX(marker.x());
                float cy = rc.camera.cellToScreenY(marker.y());
                float radius = cellPx * marker.radius();
                Color tint = marker.kind() == ColdAccountMarkers.Kind.DANGER
                        ? ORBITAL_WARNING : BattlefieldMarkerPresentation.PROGRESS;
                appendObjectiveAnnulus(cx, cy, radius - 2f, radius, tint, .8f * alphaMult);
                out.addCustom(RenderLayer.OBJECTIVES, () -> {
                    objectiveMarkerFont.ensureLoaded();
                    drawObjectiveText(marker.label(), cx, cy, tint, .8f, alphaMult);
                });
            }
        }

        for (var marker : HostileShipMarkers.forSupport(sim.getHostileShipSupport())) {
            float cx = rc.camera.cellToScreenX(marker.x());
            float cy = rc.camera.cellToScreenY(marker.y());
            float radius = cellPx * marker.radius();
            appendObjectiveAnnulus(cx, cy, radius - 2f, radius, ORBITAL_WARNING, .8f * alphaMult);
            out.addCustom(RenderLayer.OBJECTIVES, () -> {
                objectiveMarkerFont.ensureLoaded();
                drawObjectiveText(marker.label(), cx, cy, ORBITAL_WARNING, .8f, alphaMult);
            });
        }

        for (var marker : FortressBatteryMarkers.forBatteries(sim.fortressBatteries(), Faction.MARINE)) {
            float cx = rc.camera.cellToScreenX(marker.x());
            float cy = rc.camera.cellToScreenY(marker.y());
            float radius = cellPx * marker.radius();
            Color tint = marker.hostile() ? ORBITAL_WARNING : BattlefieldMarkerPresentation.PROGRESS;
            float alpha = (marker.kind() == FortressBatteryMarkers.Kind.ENVELOPE ? .45f : .9f) * alphaMult;
            appendObjectiveAnnulus(cx, cy, radius - 2f, radius, tint, alpha);
            out.addCustom(RenderLayer.OBJECTIVES, () -> {
                objectiveMarkerFont.ensureLoaded();
                drawObjectiveText(marker.label(), cx, cy, tint, .8f, alphaMult);
            });
        }

        for (var marker : FortMarkers.forForts(sim.forts(), Faction.MARINE)) {
            float cx = rc.camera.cellToScreenX(marker.x());
            float cy = rc.camera.cellToScreenY(marker.y());
            Color tint = marker.kind() == FortMarkers.Kind.WRECKED ? FORT_WRECKED
                    : marker.hostile() ? ORBITAL_WARNING : BattlefieldMarkerPresentation.PROGRESS;
            if (marker.radius() > 0f) {
                float radius = cellPx * marker.radius();
                appendObjectiveAnnulus(cx, cy, radius - 2f, radius, tint, .7f * alphaMult);
            }
            out.addCustom(RenderLayer.OBJECTIVES, () -> {
                objectiveMarkerFont.ensureLoaded();
                drawObjectiveText(marker.label(), cx, cy, tint, .8f, alphaMult);
            });
        }

        for (EquipmentDrop drop : sim.getEquipmentDrops()) {
            if (drop.consumed) continue;
            float cx = rc.camera.cellToScreenX(drop.cellX + 0.5f);
            float cy = rc.camera.cellToScreenY(drop.cellY + 0.5f);
            float pulse = 1f + KIT_DROP_PULSE_AMP * (float) Math.sin(now * 2.0 * Math.PI * KIT_DROP_PULSE_HZ);
            emitIcon(out, sprites.iconStar(), cx, cy,
                    cellPx * KIT_DROP_SIZE * pulse, KIT_DROP_TINT, alphaMult);
        }

        for (ResupplyCache cache : sim.getResupplyCaches()) {
            float cx = rc.camera.cellToScreenX(cache.centerX());
            float cy = rc.camera.cellToScreenY(cache.centerY());
            float pulse = !cache.serving() ? 0.8f
                    : 1f + KIT_DROP_PULSE_AMP * (float) Math.sin(now * 2.0 * Math.PI * KIT_DROP_PULSE_HZ);
            Color tint = cache.contested ? RESUPPLY_CONTESTED : RESUPPLY_TINT;
            float alpha = !cache.serving() ? alphaMult * 0.35f : alphaMult;
            emitIcon(out, sprites.iconStar(), cx, cy,
                    cellPx * RESUPPLY_SIZE * pulse, tint, alpha);
            float barW = cellPx * 1.3f;
            float barH = Math.max(2f, cellPx * 0.10f);
            float fill = barW * cache.stockFraction();
            out.addSolidRect(RenderLayer.OBJECTIVES, cx - barW / 2f, cy - cellPx * 0.85f,
                    cx + barW / 2f, cy - cellPx * 0.85f + barH,
                    0.08f, 0.12f, 0.10f, alpha * 0.8f);
            if (fill > 0f) out.addSolidRect(RenderLayer.OBJECTIVES, cx - barW / 2f,
                    cy - cellPx * 0.85f, cx - barW / 2f + fill,
                    cy - cellPx * 0.85f + barH,
                    tint.getRed() / 255f, tint.getGreen() / 255f,
                    tint.getBlue() / 255f, alpha);
        }

        for (PendingDetonation mission : sim.getCommandPowerService().getActiveFireMissions()) {
            if (mission.isUnmarked()) continue;
            float cx = rc.camera.cellToScreenX(mission.endpointX);
            float cy = rc.camera.cellToScreenY(mission.endpointY);
            float pulse = 1f + 0.18f * (float) Math.sin(now * 2.0 * Math.PI * 3.0);
            emitIcon(out, sprites.iconDanger(), cx, cy,
                    cellPx * 1.6f * pulse, ORBITAL_WARNING, alphaMult);
            float progress = Math.max(0f, Math.min(1f,
                    mission.remainingTime / Math.max(1e-3f, mission.flightTime)));
            PolyTess.appendArc(objectiveArcMesh, cx, cy,
                    cellPx * mission.aoeRadius - 2f, cellPx * mission.aoeRadius + 2f,
                    progress, 64,
                    ORBITAL_WARNING.getRed() / 255f, ORBITAL_WARNING.getGreen() / 255f,
                    ORBITAL_WARNING.getBlue() / 255f, alphaMult * 0.9f);
        }

        if (!objectiveArcMesh.isEmpty()) out.addPoly(RenderLayer.OBJECTIVES, objectiveArcMesh);
    }

    private void appendObjectiveAnnulus(float cx, float cy, float inner,
                                        float outer, Color color, float alpha) {
        PolyTess.appendAnnulus(objectiveArcMesh, cx, cy, inner, outer,
                OBJECTIVE_RING_SEGMENTS,
                color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
    }

    private void drawObjectiveText(String text, float centerX, float y,
                                   Color color, float scale, float alphaMult) {
        float width = objectiveMarkerFont.measureWidth(text) * scale;
        objectiveMarkerFont.drawStringScaled(text, centerX - width * 0.5f, y,
                scale, scale, color, alphaMult);
    }

    /** A tinted marker icon → {@code SPRITE}, or a {@code SOLID_RECT} fill if the icon texture is missing. */
    private static void emitIcon(DrawList out, SpriteAPI sprite, float cx, float cy,
                                 float size, Color tint, float alphaMult) {
        float r = tint.getRed() / 255f, g = tint.getGreen() / 255f, b = tint.getBlue() / 255f;
        if (sprite == null) {
            out.addSolidRect(RenderLayer.OBJECTIVES, cx - size / 2f, cy - size / 2f,
                    cx + size / 2f, cy + size / 2f, r, g, b, alphaMult);
        } else {
            out.addSprite(RenderLayer.OBJECTIVES, sprite, cx, cy, size, size, 0f, r, g, b, alphaMult);
        }
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
        drainLayer(layer, null);
    }

    /** As {@link #drainLayer(RenderLayer)}, tallying into {@code census} when one is supplied. */
    private void drainLayer(RenderLayer layer, FrameCensus census) {
        DrawListRenderer.drain(drawList.buffer(layer), drawList.count(layer), batchBySheet, solidBatch,
                lineBatch, contrailBatch, rc.camera,
                census == null ? null : census.drain(layer), pagedRun);
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
        renderWorld(rc, EnumSet.allOf(RenderLayer.class));
    }

    /**
     * Renders only the requested {@link RenderLayer}s — the render-target seam for
     * hosts that want a subset of the scene. A system is collected only if its
     * {@link RenderSystem#layer()} is in {@code layers} (so passes whose sheets a
     * host didn't load never run), and only those layers are drained. Paint order
     * within the subset is unchanged (drain still walks the enum in ordinal order).
     */
    public void renderWorld(RenderContext rc, EnumSet<RenderLayer> layers) {
        renderWorld(rc, layers, null);
    }

    /**
     * As {@link #renderWorld(RenderContext, java.util.EnumSet)}, recording what
     * the frame cost per layer into {@code census}.
     *
     * <p>{@code null} for every host: this is the seam the render-budget
     * evidence measures the real pipeline through, so that the profile is taken
     * from the shipping collect-and-drain rather than from a copy of it. See
     * {@link FrameCensus}.
     */
    public void renderWorld(RenderContext rc, EnumSet<RenderLayer> layers,
                            FrameCensus census) {
        renderWorld(rc, layers, census, null);
    }

    /**
     * As {@link #renderWorld(RenderContext, EnumSet, FrameCensus)}, draining {@code leading}'s commands for each
     * layer ahead of the layer's own. A headless review feeds presentation services the live screen ticks itself
     * (contrails, delivered rounds, beams) and hands their commands in here, the order the screen draws them in.
     */
    void renderWorld(RenderContext rc, EnumSet<RenderLayer> layers, FrameCensus census, DrawList leading) {
        collectWorld(rc, layers, census);
        registerLateTerrainBatches();
        registerLateConvoyBatches();
        registerLatePathBatches();
        boolean parallax = (rc.hostProfile.surfaceReliefEnabled() || GroundParallaxPipeline.deckWaterVisible(rc))
                && DevConfig.SURFACE_RELIEF_PARALLAX
                && layers.contains(RenderLayer.GROUND);
        for (RenderLayer layer : RenderLayer.values()) {
            if (!layer.drawnFor(layers)) continue;
            long started = census == null ? 0L : System.nanoTime();
            if (leading != null && leading.count(layer) > 0) {
                DrawListRenderer.drain(leading.buffer(layer), leading.count(layer), batchBySheet, solidBatch,
                        lineBatch, contrailBatch, rc.camera, null, pagedRun);
            }
            if (layer == RenderLayer.GROUND && parallax) {
                // S2/S3: redirect GROUND through the relief FBOs + composite instead
                // of draining straight to the backbuffer. groundParallax falls back to
                // drainLayer(GROUND) itself (a plain Runnable) on any failure, so this
                // call always ends up painting the layer exactly once.
                // Creep drains inside the same colour target, straight after the ground, so the
                // composite displaces it with the ground under it and shadows it with the walls.
                boolean creepToo = layers.contains(RenderLayer.CREEP);
                long[] creepNanos = {0L};
                groundParallax.renderGround(rc, () -> {
                    drainLayer(RenderLayer.GROUND, census);
                    if (!creepToo) return;
                    long creepStarted = census == null ? 0L : System.nanoTime();
                    drainLayer(RenderLayer.CREEP, census);
                    if (census != null) creepNanos[0] += System.nanoTime() - creepStarted;
                });
                if (census != null && creepNanos[0] > 0L) {
                    // Charge the creep its own drain, not the ground's.
                    census.addDrainNanos(RenderLayer.CREEP, creepNanos[0]);
                    started += creepNanos[0];
                }
            } else if (layer == RenderLayer.CREEP && parallax) {
                // Already drained inside the ground's colour target.
            } else {
                drainLayer(layer, census);
            }
            if (census != null) census.addDrainNanos(layer, System.nanoTime() - started);
        }
    }

    /**
     * Collects the selected world layers without touching GL. The returned
     * command list is renderer-owned and remains valid only until this renderer
     * collects another frame. Live rendering drains it immediately; headless
     * evidence replays the same commands through its raster backend.
     */
    public DrawList collectWorld(RenderContext rc, EnumSet<RenderLayer> layers) {
        return collectWorld(rc, layers, null);
    }

    /**
     * As {@link #collectWorld(RenderContext, java.util.EnumSet)}, attributing
     * each producer's wall-clock cost to the layer it feeds.
     *
     * <p>Per producer rather than per layer, then summed into the layer, because
     * several producers share a layer and the census reports the layer: a
     * shared-layer sum is the honest reading of "what does GROUND cost", and
     * timing the whole loop once would attribute all of it to nothing.
     */
    public DrawList collectWorld(RenderContext rc, EnumSet<RenderLayer> layers,
                                 FrameCensus census) {
        if (rc == null || layers == null) {
            throw new IllegalArgumentException("render context and layers are required");
        }
        this.rc = rc;
        fogDrawn = layers.contains(RenderLayer.FOG);
        growUnitAtlas();
        drawList.clear();
        drawList.setClosestZoomFactor(rc.camera == null || !(rc.camera.zoom() > 0f)
                ? Float.NaN : BattleCamera.MAX_ZOOM / rc.camera.zoom());
        if (TextureUsage.isEnabled() && rc.camera != null) {
            TextureUsage.beginWorld(rc.camera.cellPxSize(), rc.camera.worldCellsW(), rc.camera.worldCellsH());
        }
        for (RenderSystem system : worldSystems) {
            if (!layers.contains(system.layer())) continue;
            if (census == null) {
                system.collect(rc, drawList);
            } else {
                long started = System.nanoTime();
                system.collect(rc, drawList);
                census.addCollectNanos(system.layer(), System.nanoTime() - started);
            }
        }
        return drawList;
    }
}
