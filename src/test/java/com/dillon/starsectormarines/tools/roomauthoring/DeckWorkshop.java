package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayouts;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckSizing;
import com.dillon.starsectormarines.battle.world.gen.ship.HullSilhouette;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.gen.ship.VanillaHullSilhouettes;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.List;

/**
 * The ship the editor works against, and the pictures it takes of her rooms.
 *
 * <p><b>Law 17 decides the whole of this class.</b> A deck is seen through the
 * battle renderer and never through a second painter, so a room is shown by
 * generating the ship and rendering the compartment where it actually landed —
 * not by drawing the room on its own. A tool that redrew the deck its own way
 * would be measuring its own drawing, and every drift would read as a fill
 * defect until somebody went looking.
 *
 * <p>The same rule is what makes the comparison worth having. Before and after
 * are <b>the same hull at the same seed</b>, generated twice with the layout
 * suppressed and applied, so the only difference on screen is the edit. A
 * mock-up of the room alone could not show that a room drawn larger no longer
 * fits, which is exactly the kind of thing an author needs to find out before
 * saving rather than afterwards.
 */
public final class DeckWorkshop implements AutoCloseable {

    /**
     * The hull the editor works on. A personnel transport carries the widest
     * spread of purposes, so most rooms can be reached without switching ships.
     */
    private static final String HULL = "valkyrie";

    /** One seed throughout, so before and after differ by the edit and nothing else. */
    private static final long SEED = 42L;

    private final Path modRoot;
    private final HullSilhouette silhouette;
    private final DeckSizing.DeckPlan deckPlan;
    private final RoomFit fit;

    private HeadlessBattleSceneRenderer scenes;
    private HeadlessUiRenderer drain;

    public DeckWorkshop(Path projectRoot, Path starsectorCore, RoomFit fit) throws Exception {
        this.modRoot = projectRoot.resolve("mod");
        this.fit = fit;

        VanillaHullSilhouettes vanilla = new VanillaHullSilhouettes(starsectorCore);
        if (vanilla.available()) {
            VanillaHullSilhouettes.Hull hull = vanilla.read(HULL);
            this.silhouette = hull.silhouette();
            this.deckPlan = DeckSizing.planFor(hull.hullClass(), hull.role(), hull.minCrew(),
                    hull.maxCrew(), hull.cargo(), hull.silhouette().aspect());
        } else {
            // Without the install the family still generates, so the tool still
            // works — on a synthetic taper rather than on real proportions.
            this.silhouette = null;
            this.deckPlan = new DeckSizing.DeckPlan(96, 28, List.of());
        }
    }

    /** Which purposes this ship actually has rooms for, in program order. */
    public List<RoomPurpose> purposesAboard() {
        List<RoomPurpose> purposes = new ArrayList<>();
        try (Deck deck = generate(null)) {
            for (DeckGraph.Compartment room : deck.graph().compartments()) {
                if (room.purpose() != null && !purposes.contains(room.purpose())) {
                    purposes.add(room.purpose());
                }
            }
        }
        return purposes;
    }

    /**
     * Draw every room the ship has, handing each one over as it is finished.
     *
     * <p>One generation for the whole set rather than one per room. Rendering
     * twenty rooms by asking twenty times would lay out the ship twenty times
     * over, which is most of a minute to look at a menu.
     *
     * <p>Handed over one at a time because the caller is a window: a grid that
     * waited for the last of twenty would show nothing at all until every one of
     * them was ready.
     */
    public void eachRoom(int cellPx, int surround, BiConsumer<RoomPurpose, BufferedImage> onRoom) {
        try (Deck deck = generate(null)) {
            List<RoomPurpose> drawn = new ArrayList<>();
            for (DeckGraph.Compartment room : deck.graph().compartments()) {
                if (room.purpose() == null || drawn.contains(room.purpose())) continue;
                drawn.add(room.purpose());
                onRoom.accept(room.purpose(), frame(deck, room, cellPx, surround));
            }
        }
    }

    /** The size of a room, as the ship laid it down. */
    public String sizeOf(RoomPurpose purpose) {
        RoomShape shape = footprintOf(purpose);
        return shape == null ? "" : shape.width() + "x" + shape.height();
    }

    /**
     * The footprint this room is generated at today, read off the ship rather
     * than off the recipe.
     *
     * <p>Off the ship because that is the shape a layout has to match, and
     * because it is already carried back in the room's canonical frame — a
     * compartment records the pose it was turned to, so the editor never has to
     * work out which way round the one it is looking at ended up.
     */
    public RoomShape footprintOf(RoomPurpose purpose) {
        try (Deck deck = generate(null)) {
            for (DeckGraph.Compartment room : deck.graph().compartments()) {
                if (room.purpose() == purpose) {
                    return room.shape().posed(inverseOf(room.pose()));
                }
            }
        }
        return null;
    }

    /** The pose that undoes this one, so a placed shape reads back canonical. */
    private static RoomPose inverseOf(RoomPose pose) {
        if (pose.mirrored()) return pose;
        return new RoomPose((4 - pose.quarterTurns()) % 4, false);
    }

    /**
     * The room as it generates with this layout in force, or as it ships when
     * handed null.
     *
     * @param surround cells of bulkhead to keep in shot, so a door reads as a
     *     hole in something rather than as a gap at the edge of the picture
     * @return the picture, or null when the ship has no room of that purpose —
     *     which is itself the answer when an enlarged room stopped fitting
     */
    public BufferedImage render(RoomLayout layout, RoomPurpose purpose,
                                int cellPx, int surround) {
        try (Deck deck = generate(layout)) {
            DeckGraph.Compartment room = null;
            for (DeckGraph.Compartment candidate : deck.graph().compartments()) {
                if (candidate.purpose() == purpose) {
                    room = candidate;
                    break;
                }
            }
            if (room == null) return null;

            return frame(deck, room, cellPx, surround);
        }
    }

    /** One compartment, framed with a little of the bulkhead it was cut from. */
    private BufferedImage frame(Deck deck, DeckGraph.Compartment room,
                                int cellPx, int surround) {
        int across = room.width() + surround * 2;
        int down = room.depth() + surround * 2;
        return renderer().renderHostPass(
                deck.scene().pass(ShipDeckBattleScene.DeckView.over(
                        room.left() - surround, room.top() - surround,
                        across, down, cellPx)),
                across * cellPx, down * cellPx);
    }

    /**
     * Generate the ship with exactly this layout installed and nothing else.
     *
     * <p>The install is swapped around the generation and put back afterwards,
     * because the registry is what the game itself reads. Leaving an editor's
     * working draft installed would mean the next thing to generate a deck in
     * this process — another preview, a snapshot suite — silently got the
     * unsaved edit.
     */
    private Deck generate(RoomLayout layout) {
        RoomLayouts before = RoomLayouts.installed();
        try {
            RoomLayouts.install(layout == null
                    ? new RoomLayouts(List.of()) : new RoomLayouts(List.of(layout)));
            ShipDeckGenerator generator = new ShipDeckGenerator();
            MapResult map = generator.generateDeck(deckPlan, SEED, silhouette, fit);
            return new Deck(new ShipDeckBattleScene(map, generator.getLastDeckGraph(), SEED, null),
                    generator.getLastDeckGraph());
        } finally {
            RoomLayouts.install(before);
        }
    }

    private HeadlessUiRenderer renderer() {
        if (drain == null) {
            scenes = new HeadlessBattleSceneRenderer(modRoot);
            drain = new HeadlessUiRenderer(scenes, modRoot);
        }
        return drain;
    }

    private record Deck(ShipDeckBattleScene scene, DeckGraph graph) implements AutoCloseable {
        @Override
        public void close() {
            scene.close();
        }
    }

    @Override
    public void close() {
        scenes = null;
        drain = null;
    }
}
