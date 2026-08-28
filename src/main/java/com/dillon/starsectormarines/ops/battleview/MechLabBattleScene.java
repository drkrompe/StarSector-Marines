package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskPose;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientTaskService;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-interactive battle-renderer host for the lance-scale fabrication garage.
 *
 * <p>The garage is a real, bounded {@link BattleSimulation}. It deliberately owns
 * no battle HUD, input adapter, audio loop, selection publisher, or simulation
 * advance. The shared renderer is invoked in two layer subsets so fitting
 * overlays can paint between physical room content and the real unit dolls.</p>
 */
public final class MechLabBattleScene implements AutoCloseable {

    static final int GRID_WIDTH = MechLabSceneLayout.WIDTH;
    static final int GRID_HEIGHT = MechLabSceneLayout.HEIGHT;
    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(RenderLayer.UNITS);

    private final BattleRenderer renderer;
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();
    private BattleSimulation simulation;
    private List<MechVariant> renderedVariants = List.of();

    public MechLabBattleScene(BattleSprites sprites) {
        if (sprites == null) throw new IllegalArgumentException("battle sprites are required");
        renderer = new BattleRenderer(sprites);
        renderer.buildTileBatches();
    }

    /** Headless scene model; a tooling drain supplies its own renderer and assets. */
    public MechLabBattleScene() {
        renderer = null;
    }

    public BattleSceneHostPass backdropPass(List<MechVariant> variants,
                                            MechLabCameraController.CameraPose cameraPose,
                                            float elapsedSeconds) {
        return pass(variants, cameraPose, elapsedSeconds, BACKDROP_LAYERS);
    }

    public BattleSceneHostPass actorPass(List<MechVariant> variants,
                                         MechLabCameraController.CameraPose cameraPose,
                                         float elapsedSeconds) {
        return pass(variants, cameraPose, elapsedSeconds, ACTOR_LAYERS);
    }

    static BattleCamera cameraForSurface(float width, float height, int selectedGantry) {
        return cameraForSurface(width, height, new MechLabCameraController.CameraPose(
                mechWorldX(selectedGantry), mechWorldY(selectedGantry),
                MechLabCameraController.FITTING_ZOOM_NOTCHES));
    }

    static BattleCamera cameraForSurface(float width, float height,
                                         MechLabCameraController.CameraPose cameraPose) {
        return cameraForViewport(0f, 0f, width, height, cameraPose);
    }

    private static BattleCamera cameraForViewport(float x, float y, float width, float height,
                                                  MechLabCameraController.CameraPose cameraPose) {
        BattleCamera result = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);
        configureCamera(result, x, y, width, height);
        result.zoomAt(cameraPose.zoomNotches(),
                x + width * 0.5f, y + height * 0.5f);
        result.centerOn(cameraPose.worldX(), cameraPose.worldY());
        return result;
    }

    static float mechWorldX(int gantryIndex) {
        return gantry(gantryIndex).cellX() + 0.5f;
    }

    static float mechWorldY(int gantryIndex) {
        return gantry(gantryIndex).cellY() + 0.5f;
    }

    private BattleSceneHostPass pass(List<MechVariant> variants,
                                     MechLabCameraController.CameraPose cameraPose,
                                     float elapsedSeconds,
                                     EnumSet<RenderLayer> layers) {
        List<MechVariant> snapshot = variants != null ? List.copyOf(variants) : List.of();
        if (cameraPose == null) throw new IllegalArgumentException("camera pose is required");
        return new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                return prepareFrame(viewport, snapshot, cameraPose,
                        elapsedSeconds, alphaMult, layers);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                if (renderer == null) {
                    throw new IllegalStateException("Live Mech Lab renderer is unavailable");
                }
                BattleSceneFrame frame = prepare(viewport, alphaMult);
                renderer.renderWorld(frame.context(), frame.layers());
            }
        };
    }

    private BattleSceneFrame prepareFrame(CanvasHostViewport viewport,
                                           List<MechVariant> variants,
                                           MechLabCameraController.CameraPose cameraPose,
                                           float elapsedSeconds,
                                           float alphaMult,
                                           EnumSet<RenderLayer> layers) {
        if (viewport.width() <= 0f || viewport.height() <= 0f) {
            throw new IllegalArgumentException("Mech Lab scene requires a visible viewport");
        }
        ensureSimulation(variants);
        simulation.ambientTasks().seek(elapsedSeconds);
        BattleCamera camera = cameraForViewport(viewport.screenX(), viewport.screenY(),
                viewport.width(), viewport.height(), cameraPose);
        RenderContext context = new RenderContext(simulation, camera, null,
                alphaMult, 0f, false, highlights, selection,
                BattleRenderHostProfile.EMBEDDED_SCENE);
        return new BattleSceneFrame(context, layers);
    }

    private static void configureCamera(BattleCamera camera, float x, float y,
                                        float width, float height) {
        float fittedCell = Math.min(width / GRID_WIDTH, height / GRID_HEIGHT);
        camera.setViewport(x, y, width, height, fittedCell);
    }

    private static MechLabSceneLayout.Gantry gantry(int index) {
        int safe = Math.max(0, Math.min(MechLabSceneLayout.GANTRIES.size() - 1, index));
        return MechLabSceneLayout.GANTRIES.get(safe);
    }

    private void ensureSimulation(List<MechVariant> variants) {
        List<MechVariant> copy = List.copyOf(variants);
        if (simulation != null && renderedVariants.equals(copy)) return;
        if (simulation != null) simulation.close();
        renderedVariants = copy;
        simulation = buildSimulation(copy);
    }

    static BattleSimulation buildSimulation(List<MechVariant> variants) {
        NavigationGrid grid = new NavigationGrid(GRID_WIDTH, GRID_HEIGHT);
        CellTopology topology = new CellTopology(GRID_WIDTH, GRID_HEIGHT);
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                topology.setGroundKind(x, y, MechLabSceneLayout.groundKind(x, y));
                if (MechLabSceneLayout.wall(x, y)) {
                    topology.setWall(x, y, true);
                } else {
                    grid.setWalkableFloor(x, y);
                }
            }
        }
        ArrayList<Doodad> fixtures = new ArrayList<>();
        for (MechLabSceneLayout.PropPlacement placement : MechLabSceneLayout.PROPS) {
            DoodadDef definition = TileRegistry.installed().doodad(placement.doodadId());
            Doodad fixture = new Doodad(placement.cellX(), placement.cellY(), definition);
            fixtures.add(fixture);
            stampFixture(grid, topology, fixture);
        }
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) grid.recomputeCoverAt(x, y);
        }

        BattleSimulation sim = new BattleSimulation(grid, topology, 0x4D4543484C41424CL);
        addWorkshopProps(sim, fixtures);
        int count = Math.min(variants.size(), MechLabSceneLayout.GANTRIES.size());
        for (int index = 0; index < count; index++) {
            MechVariant variant = variants.get(index);
            MechLabSceneLayout.Gantry gantry = MechLabSceneLayout.GANTRIES.get(index);
            long mech = sim.spawn(new EntitySpec("gantry mech " + (index + 1), Faction.MARINE,
                    UnitType.HEAVY_MECH, gantry.cellX(), gantry.cellY())
                    .mechVariant(variant));
            sim.world().attachMechLoadout(mech,
                    variant.createLoadout(variant.defaultRole));
        }
        for (int index = 0; index < MechLabSceneLayout.FACILITY_JOBS.size(); index++) {
            AmbientTaskRoute job = MechLabSceneLayout.FACILITY_JOBS.get(index);
            AmbientTaskPose pose = AmbientTaskService.sample(job, 0f);
            long technician = spawnTechnician(sim, job.id(),
                    (int) Math.floor(pose.worldX()), (int) Math.floor(pose.worldY()));
            sim.ambientTasks().assign(technician, job);
        }
        sim.ambientTasks().seek(0f);
        sim.getFogOfWar().tick(0, sim.getRoster());
        return sim;
    }

    private static long spawnTechnician(BattleSimulation sim, String name, int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.MARINE, UnitType.ENGINEER, x, y)
                .layeredArmorFamily(LayeredArmorFamily.ARMY_GREEN));
    }

    private static void addWorkshopProps(BattleSimulation sim, List<Doodad> fixtures) {
        for (MechLabSceneLayout.FloorOverlayPlacement placement
                : MechLabSceneLayout.floorOverlays()) {
            sim.addDoodad(floorOverlay(placement.cellX(), placement.cellY(),
                    placement.tileColumn(), placement.tileRow()));
        }
        for (Doodad fixture : fixtures) sim.addDoodad(fixture);
    }

    private static Doodad floorOverlay(int x, int y, int column, int row) {
        return new Doodad(x, y, new TileManifest.TileFrame(column, row),
                TileManifest.SHEET, Doodad.COVER_NONE);
    }

    private static void stampFixture(
            NavigationGrid grid, CellTopology topology, Doodad doodad) {
        for (int dy = 0; dy < doodad.footprintCellsY; dy++) {
            for (int dx = 0; dx < doodad.footprintCellsX; dx++) {
                int x = doodad.cellX + dx;
                int y = doodad.cellY + dy;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, true);
                topology.setWall(x, y, false);
                topology.setFixture(x, y, true);
            }
        }
    }

    @Override
    public void close() {
        if (simulation != null) {
            simulation.close();
            simulation = null;
        }
        renderedVariants = List.of();
    }
}
