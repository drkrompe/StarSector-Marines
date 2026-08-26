package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.EnumSet;
import java.util.List;

/** Bounded battle-renderer host for the flagship's marine quarters. */
public final class BarracksBattleScene implements AutoCloseable {

    static final int GRID_WIDTH = BarracksSceneLayout.WIDTH;
    static final int GRID_HEIGHT = BarracksSceneLayout.HEIGHT;
    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(RenderLayer.UNITS);

    private final BattleRenderer renderer;
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();
    private final BattleCamera camera = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);
    private BattleSimulation simulation;
    private List<MarineSoldier> renderedMarines = List.of();

    public BarracksBattleScene(BattleSprites sprites) {
        if (sprites == null) throw new IllegalArgumentException("battle sprites are required");
        renderer = new BattleRenderer(sprites);
        renderer.buildTileBatches();
    }

    /** Headless scene model; a tooling drain supplies its own renderer and assets. */
    public BarracksBattleScene() {
        renderer = null;
    }

    public BattleSceneHostPass backdropPass(List<MarineSoldier> marines) {
        return pass(marines, BACKDROP_LAYERS);
    }

    public BattleSceneHostPass actorPass(List<MarineSoldier> marines) {
        return pass(marines, ACTOR_LAYERS);
    }

    private BattleSceneHostPass pass(List<MarineSoldier> marines,
                                     EnumSet<RenderLayer> layers) {
        List<MarineSoldier> snapshot = marines != null ? List.copyOf(marines) : List.of();
        return new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                return prepareFrame(viewport, snapshot, alphaMult, layers);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                if (renderer == null) {
                    throw new IllegalStateException("Live Barracks renderer is unavailable");
                }
                BattleSceneFrame frame = prepare(viewport, alphaMult);
                renderer.renderWorld(frame.context(), frame.layers());
            }
        };
    }

    private BattleSceneFrame prepareFrame(CanvasHostViewport viewport,
                                           List<MarineSoldier> marines,
                                           float alphaMult,
                                           EnumSet<RenderLayer> layers) {
        if (viewport.width() <= 0f || viewport.height() <= 0f) {
            throw new IllegalArgumentException("Barracks viewport must be visible");
        }
        ensureSimulation(marines);
        configureCamera(camera, viewport.screenX(), viewport.screenY(),
                viewport.width(), viewport.height());
        RenderContext context = new RenderContext(simulation, camera, null,
                alphaMult, 0f, false, highlights, selection,
                BattleRenderHostProfile.EMBEDDED_SCENE);
        return new BattleSceneFrame(context, layers);
    }

    static BattleCamera cameraForSurface(float width, float height) {
        BattleCamera result = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);
        configureCamera(result, 0f, 0f, width, height);
        return result;
    }

    private static void configureCamera(BattleCamera camera, float x, float y,
                                        float width, float height) {
        float cell = Math.min(width / GRID_WIDTH, height / GRID_HEIGHT);
        camera.setViewport(x, y, width, height, cell);
        camera.centerOn(GRID_WIDTH * 0.5f, GRID_HEIGHT * 0.5f);
    }

    private void ensureSimulation(List<MarineSoldier> marines) {
        List<MarineSoldier> copy = List.copyOf(marines);
        if (simulation != null && renderedMarines.equals(copy)) return;
        if (simulation != null) simulation.close();
        renderedMarines = copy;
        simulation = buildSimulation(copy);
    }

    static BattleSimulation buildSimulation() {
        return buildSimulation(List.of());
    }

    static BattleSimulation buildSimulation(List<MarineSoldier> marines) {
        NavigationGrid grid = new NavigationGrid(GRID_WIDTH, GRID_HEIGHT);
        CellTopology topology = new CellTopology(GRID_WIDTH, GRID_HEIGHT);
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                topology.setGroundKind(x, y, BarracksSceneLayout.groundKind(x, y));
                if (BarracksSceneLayout.wall(x, y)) topology.setWall(x, y, true);
                else grid.setWalkableFloor(x, y);
            }
        }
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) grid.recomputeCoverAt(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, topology, 0x4241525241434B53L);
        for (BarracksSceneLayout.FloorOverlayPlacement placement
                : BarracksSceneLayout.floorOverlays()) {
            sim.addDoodad(prop(placement.cellX(), placement.cellY(),
                    placement.tileColumn(), placement.tileRow()));
        }
        for (BarracksSceneLayout.PropPlacement placement : BarracksSceneLayout.PROPS) {
            sim.addDoodad(prop(placement.cellX(), placement.cellY(),
                    placement.tileColumn(), placement.tileRow()));
        }
        int count = Math.min(marines.size(), BarracksSceneLayout.MARINES.size());
        for (int index = 0; index < count; index++) {
            MarineSoldier soldier = marines.get(index);
            BarracksSceneLayout.MarinePlacement placement =
                    BarracksSceneLayout.MARINES.get(index);
            EntitySpec spec = new EntitySpec(soldier.name(), Faction.MARINE,
                    UnitType.MARINE, placement.cellX(), placement.cellY());
            MarineLoadout.fromCatalog(UnitRole.COMBATANT, null,
                    soldier.primaryDef(), soldier.primaryGrade(), soldier.profile(),
                    soldier.secondary() != null ? soldier.secondary().specialDef() : null,
                    soldier.id(), soldier.armorDef().appearanceFamily(),
                    soldier.armorDef().armorPool(), soldier.armorDef().armorRating(),
                    soldier.armorDef().moveSpeedMult(),
                    soldier.armorDef().incomingAccuracyMult(), null).seedInto(spec);
            sim.spawn(spec);
        }
        sim.getFogOfWar().tick(0, sim.getRoster());
        return sim;
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
        renderedMarines = List.of();
    }
}
