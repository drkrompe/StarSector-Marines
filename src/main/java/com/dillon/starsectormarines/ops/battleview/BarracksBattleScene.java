package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
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
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;

/** Bounded battle-renderer host for the flagship's marine quarters. */
public final class BarracksBattleScene implements AutoCloseable {

    static final int GRID_WIDTH = BarracksSceneLayout.WIDTH;
    static final int GRID_HEIGHT = BarracksSceneLayout.HEIGHT;
    private static final float MAX_REPLAY_SECONDS = 30f;
    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(
            RenderLayer.UNITS, RenderLayer.SHOTS);

    private final BattleRenderer renderer;
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();
    private final BattleCamera camera = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);
    private BattleSimulation simulation;
    private List<MarineSoldier> renderedMarines = List.of();
    private float simulatedSeconds;

    public BarracksBattleScene(BattleSprites sprites) {
        if (sprites == null) throw new IllegalArgumentException("battle sprites are required");
        renderer = new BattleRenderer(sprites);
        renderer.buildTileBatches();
    }

    /** Headless scene model; a tooling drain supplies its own renderer and assets. */
    public BarracksBattleScene() {
        renderer = null;
    }

    public BattleSceneHostPass backdropPass(List<MarineSoldier> marines, float elapsedSeconds) {
        return pass(marines, elapsedSeconds, BACKDROP_LAYERS);
    }

    public BattleSceneHostPass actorPass(List<MarineSoldier> marines, float elapsedSeconds) {
        return pass(marines, elapsedSeconds, ACTOR_LAYERS);
    }

    /**
     * Advances the embedded room through the ordinary fixed-step battle clock.
     * Repeated calls at the same authored time are no-ops, allowing the
     * backdrop and actor render passes to observe one authoritative frame.
     */
    public void advanceTo(List<MarineSoldier> marines, float elapsedSeconds) {
        if (!Float.isFinite(elapsedSeconds)) {
            throw new IllegalArgumentException("Barracks time must be finite");
        }
        float targetSeconds = Math.max(0f, elapsedSeconds);
        boolean rebuilt = ensureSimulation(marines);
        if (targetSeconds < simulatedSeconds) {
            rebuildSimulation(renderedMarines);
            rebuilt = true;
        }
        if (rebuilt && targetSeconds > MAX_REPLAY_SECONDS) {
            // A Barracks frame contains physical movement and live fire, so it
            // must never use AmbientTaskService.seek(): that presentation-only
            // seam intentionally bypasses collision. Warm the room through the
            // ordinary fixed-step simulation instead, with a bounded catch-up.
            simulation.advance(MAX_REPLAY_SECONDS);
            simulatedSeconds = targetSeconds;
            return;
        }
        float dt = targetSeconds - simulatedSeconds;
        if (dt > 0f) {
            simulation.advance(dt);
            simulatedSeconds = targetSeconds;
        }
    }

    /** New shot events emitted by the most recent embedded-scene advance. */
    public List<ShotEvent> shotsThisFrame() {
        return simulation != null ? simulation.getShotsThisFrame() : List.of();
    }

    private BattleSceneHostPass pass(List<MarineSoldier> marines,
                                     float elapsedSeconds,
                                     EnumSet<RenderLayer> layers) {
        List<MarineSoldier> snapshot = marines != null ? List.copyOf(marines) : List.of();
        return new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                return prepareFrame(viewport, snapshot, elapsedSeconds, alphaMult, layers);
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
                                           float elapsedSeconds,
                                           float alphaMult,
                                           EnumSet<RenderLayer> layers) {
        if (viewport.width() <= 0f || viewport.height() <= 0f) {
            throw new IllegalArgumentException("Barracks viewport must be visible");
        }
        advanceTo(marines, elapsedSeconds);
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

    private boolean ensureSimulation(List<MarineSoldier> marines) {
        List<MarineSoldier> copy = List.copyOf(marines);
        if (simulation != null && renderedMarines.equals(copy)) return false;
        rebuildSimulation(copy);
        return true;
    }

    private void rebuildSimulation(List<MarineSoldier> marines) {
        if (simulation != null) simulation.close();
        renderedMarines = List.copyOf(marines);
        simulation = buildSimulation(renderedMarines);
        simulatedSeconds = 0f;
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
        ArrayList<Doodad> props = new ArrayList<>();
        for (BarracksSceneLayout.PropPlacement placement : BarracksSceneLayout.PROPS) {
            DoodadDef definition = TileRegistry.installed().doodad(placement.doodadId());
            Doodad doodad = new Doodad(placement.cellX(), placement.cellY(), definition);
            props.add(doodad);
            stampFixture(grid, topology, doodad);
        }
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) grid.recomputeCoverAt(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, topology, 0x4241525241434B53L);
        sim.setMissionCompletionEnabled(false);
        for (Doodad prop : props) sim.addDoodad(prop);
        for (TaskPoint point : BarracksSceneLayout.TASK_POINTS) {
            sim.taskPoints().register(point);
        }
        long[] rangeTargets = new long[3];
        for (int lane = 0; lane < rangeTargets.length; lane++) {
            int targetX = 25 + lane * 2;
            rangeTargets[lane] = sim.spawn(new EntitySpec(
                    "range target " + (lane + 1), Faction.DEFENDER,
                    UnitType.RANGE_TARGET, targetX, 13).role(UnitRole.STRUCTURE));
        }
        int count = Math.min(marines.size(), BarracksSceneLayout.MARINE_TASKS.size());
        for (int index = 0; index < count; index++) {
            MarineSoldier soldier = marines.get(index);
            AmbientTaskRoute route = BarracksSceneLayout.MARINE_TASKS.get(index);
            AmbientTaskRoute.Stop berth = route.stops().get(0);
            EntitySpec spec = new EntitySpec(soldier.name(), Faction.MARINE,
                    UnitType.MARINE, (int) Math.floor(berth.worldX()),
                    (int) Math.floor(berth.worldY()));
            MarineLoadout.fromCatalog(UnitRole.COMBATANT, null,
                    soldier.primaryDef(), soldier.primaryGrade(), soldier.profile(),
                    soldier.secondary() != null ? soldier.secondary().specialDef() : null,
                    soldier.id(), soldier.armorDef().appearanceFamily(),
                    soldier.armorDef().armorPool(), soldier.armorDef().armorRating(),
                    soldier.armorDef().moveSpeedMult(),
                    soldier.armorDef().incomingAccuracyMult(), null).seedInto(spec);
            long actor = sim.spawn(spec);
            sim.ambientTasks().assignLiveFire(actor, route, rangeTargets[index % 3]);
        }
        sim.getFogOfWar().tick(0, sim.getRoster());
        return sim;
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
        renderedMarines = List.of();
        simulatedSeconds = 0f;
    }
}
