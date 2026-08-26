package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
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
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.EnumSet;
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
    private static final float CAMERA_ZOOM_NOTCHES = 5f;
    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(RenderLayer.UNITS);

    private final BattleRenderer renderer;
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();
    private final BattleCamera camera = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);

    private BattleSimulation simulation;
    private List<MechVariant> renderedVariants = List.of();
    private boolean cameraZoomApplied;

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
                                            int selectedGantry) {
        return pass(variants, selectedGantry, BACKDROP_LAYERS);
    }

    public BattleSceneHostPass actorPass(List<MechVariant> variants,
                                         int selectedGantry) {
        return pass(variants, selectedGantry, ACTOR_LAYERS);
    }

    static BattleCamera cameraForSurface(float width, float height, int selectedGantry) {
        BattleCamera result = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);
        configureCamera(result, 0f, 0f, width, height);
        result.zoomAt(CAMERA_ZOOM_NOTCHES, width * 0.5f, height * 0.5f);
        centerOnGantry(result, selectedGantry);
        return result;
    }

    static float mechWorldX(int gantryIndex) {
        return gantry(gantryIndex).cellX() + 0.5f;
    }

    static float mechWorldY(int gantryIndex) {
        return gantry(gantryIndex).cellY() + 0.5f;
    }

    private BattleSceneHostPass pass(List<MechVariant> variants, int selectedGantry,
                                     EnumSet<RenderLayer> layers) {
        List<MechVariant> snapshot = variants != null ? List.copyOf(variants) : List.of();
        return new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                return prepareFrame(viewport, snapshot, selectedGantry, alphaMult, layers);
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
                                           int selectedGantry, float alphaMult,
                                           EnumSet<RenderLayer> layers) {
        if (variants.isEmpty() || viewport.width() <= 0f || viewport.height() <= 0f) {
            throw new IllegalArgumentException("Mech Lab scene requires assets and a visible viewport");
        }
        ensureSimulation(variants);
        configureCamera(camera, viewport.screenX(), viewport.screenY(),
                viewport.width(), viewport.height());
        if (!cameraZoomApplied) {
            camera.zoomAt(CAMERA_ZOOM_NOTCHES,
                    viewport.screenX() + viewport.width() * 0.5f,
                    viewport.screenY() + viewport.height() * 0.5f);
            cameraZoomApplied = true;
        }
        centerOnGantry(camera, selectedGantry);
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

    private static void centerOnGantry(BattleCamera camera, int selectedGantry) {
        MechLabSceneLayout.Gantry gantry = gantry(selectedGantry);
        camera.centerOn(gantry.cellX() + 0.5f, gantry.cellY() + 0.5f);
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
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) grid.recomputeCoverAt(x, y);
        }

        BattleSimulation sim = new BattleSimulation(grid, topology, 0x4D4543484C41424CL);
        addWorkshopProps(sim);
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
        for (MechLabSceneLayout.TechnicianPlacement technician
                : MechLabSceneLayout.TECHNICIANS) {
            spawnTechnician(sim, technician.name(),
                    technician.cellX(), technician.cellY());
        }
        sim.getFogOfWar().tick(0, sim.getRoster());
        return sim;
    }

    private static void spawnTechnician(BattleSimulation sim, String name, int x, int y) {
        sim.spawn(new EntitySpec(name, Faction.MARINE, UnitType.ENGINEER, x, y)
                .layeredArmorFamily(LayeredArmorFamily.ARMY_GREEN));
    }

    private static void addWorkshopProps(BattleSimulation sim) {
        for (MechLabSceneLayout.FloorOverlayPlacement placement
                : MechLabSceneLayout.floorOverlays()) {
            sim.addDoodad(prop(placement.cellX(), placement.cellY(),
                    placement.tileColumn(), placement.tileRow()));
        }
        for (MechLabSceneLayout.PropPlacement placement : MechLabSceneLayout.PROPS) {
            sim.addDoodad(prop(placement.cellX(), placement.cellY(),
                    placement.tileColumn(), placement.tileRow()));
        }
    }

    private static Doodad prop(int x, int y, int column, int row) {
        return new Doodad(x, y, new TileManifest.TileFrame(column, row),
                TileManifest.SHEET, Doodad.COVER_NONE);
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
