package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.Collections;
import java.util.EnumSet;

/**
 * Battle-renderer host for a generated ship deck.
 *
 * <p>A generated deck is already a {@link MapResult} — grid, topology, doodads,
 * buildings, tactical map — which is to say it is already a battle map rather
 * than a preview artifact. So looking at one is not a separate problem from
 * fighting on one: both are {@link BattleRenderer} over a {@link BattleSimulation},
 * and they differ only in the camera, the layer set, and which drain collects
 * the frame. Authoring evidence, an in-game deck view, and a boarding action all
 * arrive here.
 *
 * <p>This owns no HUD, input, audio, or simulation advance. It is a still deck
 * and a camera over it.
 *
 * <p><b>Pre-condition:</b> the tile catalogs must be installed before
 * construction — the sim bakes overlay cover from {@code TileRegistry} as it is
 * built. In game that happens at application load; a tool must install them (or
 * construct its headless drain, which does) first.
 */
public final class ShipDeckBattleScene implements AutoCloseable {

    /**
     * What an authoring or hosted deck view draws.
     *
     * <p>Roofs and fog are deliberately absent. Both exist to hide interior
     * space from a player who has not earned sight of it, and a deck is
     * interior everywhere — drawn here they would black out the whole map to
     * conceal it from a player who is not present. A view that looks
     * <em>into</em> the deck opts out of concealment; a boarding action, which
     * has a player, would ask for the full set.
     *
     * <p>Decals are absent for a duller reason: the decal pass owns its own GL
     * and has no drain outside a live context. Nothing is lost — a deck that has
     * not been fought over has no bullet holes in it — but a boarding action
     * drawn to an image would notice, so this is a real bound and not a taste.
     */
    public static final EnumSet<RenderLayer> DECK_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.VEHICLES,
            RenderLayer.DOODADS, RenderLayer.UNITS);

    private final BattleRenderer renderer;
    private final BattleSimulation simulation;
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();

    /** Scene model only; a tooling drain brings its own renderer and assets. */
    public ShipDeckBattleScene(MapResult deck, long seed) {
        this(deck, seed, null);
    }

    public ShipDeckBattleScene(MapResult deck, long seed, BattleSprites sprites) {
        if (deck == null) throw new IllegalArgumentException("a generated deck is required");
        simulation = BattleSetup.buildMap(deck, Collections.emptyList(),
                Collections.emptyList(), seed).sim();
        simulation.getFogOfWar().tick(0, simulation.getRoster());
        if (sprites == null) {
            renderer = null;
        } else {
            renderer = new BattleRenderer(sprites);
            renderer.buildTileBatches();
        }
    }

    public BattleSimulation simulation() {
        return simulation;
    }

    public BattleSceneHostPass pass(DeckView view) {
        return pass(view, DECK_LAYERS);
    }

    public BattleSceneHostPass pass(DeckView view, EnumSet<RenderLayer> layers) {
        if (view == null) throw new IllegalArgumentException("a camera framing is required");
        EnumSet<RenderLayer> selected = EnumSet.copyOf(layers);
        return new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                return prepareFrame(viewport, view, alphaMult, selected);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                if (renderer == null) {
                    throw new IllegalStateException("This deck scene has no live renderer");
                }
                BattleSceneFrame frame = prepare(viewport, alphaMult);
                renderer.renderWorld(frame.context(), frame.layers());
            }
        };
    }

    private BattleSceneFrame prepareFrame(CanvasHostViewport viewport, DeckView view,
                                          float alphaMult, EnumSet<RenderLayer> layers) {
        if (viewport.width() <= 0f || viewport.height() <= 0f) {
            throw new IllegalArgumentException("a deck scene requires a visible viewport");
        }
        BattleCamera camera = new BattleCamera(
                simulation.getGrid().getWidth(), simulation.getGrid().getHeight());
        camera.setViewport(viewport.screenX(), viewport.screenY(),
                viewport.width(), viewport.height(), view.cellPx());
        camera.centerOn(view.centerCellX(), view.centerCellY());
        RenderContext context = new RenderContext(simulation, camera, null,
                alphaMult, 0f, false, highlights, selection,
                BattleRenderHostProfile.EMBEDDED_SCENE);
        return new BattleSceneFrame(context, layers);
    }

    @Override
    public void close() {
        simulation.close();
    }

    /**
     * Where the camera looks and how much of the image one cell gets.
     *
     * <p>Pixels-per-cell is stated rather than derived, because the whole point
     * of an authoring view is to see the art at the size it was drawn: a deck
     * squeezed to fit an arbitrary image is a deck resampled, which is how a
     * fill defect and a scaling defect end up looking the same.
     */
    public record DeckView(float centerCellX, float centerCellY, float cellPx) {

        public DeckView {
            if (!(cellPx > 0f)) {
                throw new IllegalArgumentException("cell size must be positive");
            }
        }

        /** Frame a cell rect at a chosen scale. The image is then {@code cells * cellPx}. */
        public static DeckView over(int left, int top, int width, int height, float cellPx) {
            return new DeckView(left + width * 0.5f, top + height * 0.5f, cellPx);
        }

        /** Frame a cell rect into a fixed image, taking whatever scale that implies. */
        public static DeckView fitting(int left, int top, int width, int height,
                                       float imageWidth, float imageHeight) {
            float cellPx = Math.min(imageWidth / Math.max(1, width),
                    imageHeight / Math.max(1, height));
            return over(left, top, width, height, cellPx);
        }
    }
}
