package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

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
 * <p>Rooms are addressable. A deck is one scene, and the screens that look at
 * parts of the ship — the Mech Lab at its vehicle bay, a berthing screen at its
 * barracks — are cameras framing a compartment of it rather than separate rooms
 * built beside it. That is why the compartment graph is carried here and not
 * left behind with the generator: without it a host can draw the deck but cannot
 * say which part of it is the room it is a screen for.
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
    private final List<Gantry> gantries;
    private final DeckGraph rooms;
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();

    /** Scene model only; a tooling drain brings its own renderer and assets. */
    public ShipDeckBattleScene(MapResult deck, long seed) {
        this(deck, null, seed, null);
    }

    public ShipDeckBattleScene(MapResult deck, long seed, BattleSprites sprites) {
        this(deck, null, seed, sprites);
    }

    /**
     * @param rooms the deck's compartment graph, or {@code null} for a scene
     *              nothing will address rooms on
     */
    public ShipDeckBattleScene(MapResult deck, DeckGraph rooms, long seed,
                               BattleSprites sprites) {
        if (deck == null) throw new IllegalArgumentException("a generated deck is required");
        this.rooms = rooms;
        gantries = deck.gantries;
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

    /** The berths this deck authored, in generation order. */
    public List<Gantry> gantries() {
        return gantries;
    }

    /**
     * The compartment this deck means by a purpose.
     *
     * @throws IllegalStateException if the scene was built without a room graph
     * @throws IllegalArgumentException if the deck placed no such room
     */
    public DeckGraph.Compartment room(RoomPurpose purpose) {
        if (rooms == null) {
            throw new IllegalStateException("this deck scene carries no room graph");
        }
        DeckGraph.Compartment found = rooms.largest(purpose);
        if (found == null) {
            throw new IllegalArgumentException("this deck has no " + purpose);
        }
        return found;
    }

    /**
     * The berths standing inside one compartment, in generation order.
     *
     * <p>A screen framed on a room asks about that room's machines, and a deck
     * may carry berths in more than one place. Filtering by the compartment's
     * own floor rather than by index keeps the two facts — which berths exist
     * and which room they are in — from having to be kept in step by hand.
     */
    public List<Gantry> berthsIn(DeckGraph.Compartment compartment) {
        if (compartment == null) throw new IllegalArgumentException("a compartment is required");
        List<Gantry> found = new ArrayList<>();
        for (Gantry gantry : gantries) {
            if (compartment.contains(gantry.centerX, gantry.centerY)) found.add(gantry);
        }
        return List.copyOf(found);
    }

    /**
     * Stand a lance in the deck's berths, in order, and return the machine in
     * each, aligned with {@link #gantries()}.
     *
     * <p>Occupancy is the host's call, not the map's, which is why this is a
     * separate step rather than something the constructor does. On the home
     * deck the lance is the player's own and this vehicle bay <em>is</em> their
     * lab — the machines shown are the machines they own, so a bay with one
     * mech in it and seven berths empty is the honest picture of a company just
     * starting out, not a rendering gap.
     *
     * <p>Machines are units, not scenery. They arrive from a roster with their
     * real variant and loadout, so what stands in the bay is the same entity
     * that would walk out of it.
     *
     * <p>The ids come back because a screen framed on a berth needs the machine
     * standing in it — selecting a gantry and selecting the mech being worked on
     * are the same act, and rediscovering that by searching the roster for
     * whatever is nearest the berth would be inventing a link that is known here.
     */
    public long[] occupyGantries(List<MechVariant> lance) {
        if (lance == null || lance.isEmpty()) return new long[0];
        int berthed = Math.min(lance.size(), gantries.size());
        long[] machines = new long[berthed];
        for (int index = 0; index < berthed; index++) {
            MechVariant variant = lance.get(index);
            if (variant == null) continue;
            Gantry gantry = gantries.get(index);
            long mech = simulation.spawn(new EntitySpec(
                    "berthed mech " + (index + 1), Faction.MARINE, UnitType.HEAVY_MECH,
                    gantry.centerX, gantry.centerY).mechVariant(variant));
            simulation.world().attachMechLoadout(mech,
                    variant.createLoadout(variant.defaultRole));
            // A berth records the way out, and a machine parked in one faces it.
            FacingSystem.faceStanding(simulation.getEntityWorld(),
                    simulation.getBattleComponents(), mech, gantry.facing.degrees());
            machines[index] = mech;
        }
        simulation.getFogOfWar().tick(0, simulation.getRoster());
        return machines;
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

        /**
         * Frame a whole compartment, with a margin of the hull around it.
         *
         * <p>The surround is not decoration. A room drawn to its own bounds has
         * its doors clipped off at the frame edge, so a doorway and a gap in the
         * bulkhead look identical — and which one it is happens to be the thing
         * a room view is most often opened to check.
         */
        public static DeckView over(DeckGraph.Compartment room, int surroundCells,
                                    float cellPx) {
            if (room == null) throw new IllegalArgumentException("a compartment is required");
            int margin = Math.max(0, surroundCells);
            return over(room.left() - margin, room.top() - margin,
                    room.width() + margin * 2, room.depth() + margin * 2, cellPx);
        }

        /** Frame one berth and the working space around it. */
        public static DeckView on(Gantry berth, int surroundCells, float cellPx) {
            if (berth == null) throw new IllegalArgumentException("a berth is required");
            int margin = Math.max(0, surroundCells);
            return over(berth.left() - margin, berth.bottom() - margin,
                    berth.right() - berth.left() + 1 + margin * 2,
                    berth.top() - berth.bottom() + 1 + margin * 2, cellPx);
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
