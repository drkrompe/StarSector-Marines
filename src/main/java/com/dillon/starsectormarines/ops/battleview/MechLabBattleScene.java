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

/**
 * Non-interactive battle-renderer host for the fabrication gantry.
 *
 * <p>The room is a real, small {@link BattleSimulation}. It deliberately owns
 * no battle HUD, input adapter, audio loop, selection publisher, or simulation
 * advance. The shared renderer is invoked in two layer subsets so fitting
 * overlays can paint between physical room content and the real unit dolls.</p>
 */
public final class MechLabBattleScene implements AutoCloseable {

    static final int GRID_WIDTH = 18;
    static final int GRID_HEIGHT = 12;
    static final int MECH_CELL_X = 8;
    static final int MECH_CELL_Y = 5;
    private static final float CAMERA_ZOOM_NOTCHES = 1f;
    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(RenderLayer.UNITS);

    private final BattleRenderer renderer;
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();
    private final BattleCamera camera = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);

    private BattleSimulation simulation;
    private MechVariant renderedVariant;
    private boolean cameraZoomApplied;

    public MechLabBattleScene(BattleSprites sprites) {
        if (sprites == null) throw new IllegalArgumentException("battle sprites are required");
        renderer = new BattleRenderer(sprites);
        renderer.buildTileBatches();
    }

    public void renderBackdrop(CanvasHostViewport viewport, MechVariant variant,
                               float alphaMult) {
        render(viewport, variant, alphaMult, BACKDROP_LAYERS);
    }

    public void renderActors(CanvasHostViewport viewport, MechVariant variant,
                             float alphaMult) {
        render(viewport, variant, alphaMult, ACTOR_LAYERS);
    }

    static BattleCamera cameraForSurface(float width, float height) {
        BattleCamera result = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);
        configureCamera(result, 0f, 0f, width, height);
        result.zoomAt(CAMERA_ZOOM_NOTCHES, width * 0.5f, height * 0.5f);
        return result;
    }

    static float mechWorldX() { return MECH_CELL_X + 0.5f; }

    static float mechWorldY() { return MECH_CELL_Y + 0.5f; }

    private void render(CanvasHostViewport viewport, MechVariant variant,
                        float alphaMult, EnumSet<RenderLayer> layers) {
        if (variant == null || viewport.width() <= 0f || viewport.height() <= 0f) return;
        ensureSimulation(variant);
        configureCamera(camera, viewport.screenX(), viewport.screenY(),
                viewport.width(), viewport.height());
        if (!cameraZoomApplied) {
            camera.zoomAt(CAMERA_ZOOM_NOTCHES,
                    viewport.screenX() + viewport.width() * 0.5f,
                    viewport.screenY() + viewport.height() * 0.5f);
            cameraZoomApplied = true;
        }
        RenderContext context = new RenderContext(simulation, camera, null,
                alphaMult, 0f, false, highlights, selection,
                BattleRenderHostProfile.EMBEDDED_SCENE);
        renderer.renderWorld(context, layers);
    }

    private static void configureCamera(BattleCamera camera, float x, float y,
                                        float width, float height) {
        float fittedCell = Math.min(width / GRID_WIDTH, height / GRID_HEIGHT);
        camera.setViewport(x, y, width, height, fittedCell);
    }

    private void ensureSimulation(MechVariant variant) {
        if (simulation != null && renderedVariant == variant) return;
        if (simulation != null) simulation.close();
        renderedVariant = variant;
        simulation = buildSimulation(variant);
    }

    static BattleSimulation buildSimulation(MechVariant variant) {
        NavigationGrid grid = new NavigationGrid(GRID_WIDTH, GRID_HEIGHT);
        CellTopology topology = new CellTopology(GRID_WIDTH, GRID_HEIGHT);
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
                boolean perimeter = x == 0 || y == 0
                        || x == GRID_WIDTH - 1 || y == GRID_HEIGHT - 1;
                if (perimeter) {
                    topology.setWall(x, y, true);
                } else {
                    grid.setWalkableFloor(x, y);
                }
            }
        }
        // A real renderer-owned maintenance pad: striped safety perimeter with
        // polished service floor beneath the asset. Camera zoom may crop it,
        // but its cell footprint never changes with the UI container.
        for (int y = 3; y <= 8; y++) {
            for (int x = 6; x <= 11; x++) {
                boolean perimeter = x == 6 || x == 11 || y == 3 || y == 8;
                topology.setGroundKind(x, y, perimeter
                        ? CellTopology.GroundKind.STRIPED
                        : CellTopology.GroundKind.TILE);
            }
        }
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) grid.recomputeCoverAt(x, y);
        }

        BattleSimulation sim = new BattleSimulation(grid, topology, 0x4D4543484C41424CL);
        addWorkshopProps(sim);
        long mech = sim.spawn(new EntitySpec("gantry mech", Faction.MARINE,
                UnitType.HEAVY_MECH, MECH_CELL_X, MECH_CELL_Y).mechVariant(variant));
        sim.world().attachMechLoadout(mech, variant.createLoadout(variant.defaultRole));
        spawnTechnician(sim, "fabricator one", 3, 3);
        spawnTechnician(sim, "fabricator two", 14, 4);
        spawnTechnician(sim, "fabricator three", 4, 8);
        sim.getFogOfWar().tick(0, sim.getRoster());
        return sim;
    }

    private static void spawnTechnician(BattleSimulation sim, String name, int x, int y) {
        sim.spawn(new EntitySpec(name, Faction.MARINE, UnitType.ENGINEER, x, y)
                .layeredArmorFamily(LayeredArmorFamily.ARMY_GREEN));
    }

    private static void addWorkshopProps(BattleSimulation sim) {
        sim.addDoodad(prop(2, 3, 5, 3));
        sim.addDoodad(prop(2, 5, 6, 3));
        sim.addDoodad(prop(2, 7, 7, 3));
        sim.addDoodad(prop(15, 3, 9, 2));
        sim.addDoodad(prop(15, 6, 9, 1));
        sim.addDoodad(prop(15, 8, 3, 3));
        sim.addDoodad(prop(6, 10, 8, 2));
        sim.addDoodad(prop(11, 10, 8, 2));
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
    }
}
