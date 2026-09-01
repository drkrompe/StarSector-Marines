package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The company ship's own hangar, with her boats standing in it.
 *
 * <p>The room rather than a diagram of it: the deck scene draws the bay, and
 * the boats on screen are the aircraft the ship actually keeps in her berths,
 * turned round by her own hands. What this adds is the one thing a render
 * cannot say — which berth the player has selected — so the marks are drawn
 * over the floor and the boats are drawn over the marks, the way
 * {@link BarracksCanvas} marks a squad.
 *
 * <p><b>The campaign deck and the generated bay are counted separately and may
 * disagree.</b> The berth count comes off the hull's room program and the
 * berths on screen come off a laid deck, and the two are measured equal rather
 * than guaranteed equal. So a berth with no boat behind it simply carries no
 * mark, and a boat with no berth on screen is a card the player can still open
 * — never an exception, because the picture failing is not a reason the room
 * cannot be used.
 */
public final class BoatDeckCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);
    private static final Color MARK = new Color(0xFF, 0xD4, 0x64, 226);
    private static final Color HOVER = new Color(0x6D, 0xD5, 0xF2, 170);
    /** How far along each side of the berth a selection corner runs, in cells. */
    private static final float MARK_CORNER = 0.9f;
    /** Hull kept around the bay, so its door is not clipped into a gap. */
    private static final int SURROUND_CELLS = 2;

    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(
            RenderLayer.UNITS, RenderLayer.SHUTTLES, RenderLayer.SHOTS);

    private final CompanyDeck ship;
    private final Supplier<DeckGraph.Compartment> bay;
    private final IntSupplier selectedBerth;
    private final IntSupplier berthOffset;
    private List<BerthTarget> targets = List.of();
    private int hovered = -1;

    /**
     * @param selectedBerth the campaign deck's berth index, which counts every
     *     hangar's berths in deck order rather than restarting in each bay
     * @param berthOffset how many boat berths lie in the hangars before this
     *     one, which is what turns a berth on screen into that index
     */
    public BoatDeckCanvas(CompanyDeck ship, Supplier<DeckGraph.Compartment> bay,
                          IntSupplier selectedBerth, IntSupplier berthOffset) {
        if (ship == null || bay == null || selectedBerth == null || berthOffset == null) {
            throw new IllegalArgumentException(
                    "a ship, a bay, a selected berth and its offset are required");
        }
        this.ship = ship;
        this.bay = bay;
        this.selectedBerth = selectedBerth;
        this.berthOffset = berthOffset;
    }

    /**
     * The boat berths standing in one hangar, in generation order.
     *
     * <p>Filtered rather than taken whole: a deck keeps one berth list for
     * machines and boats alike, so a bay that also held a machine berth would
     * shift every boat's index by one and silently mark the wrong berth.
     */
    public static List<Gantry> boatBerthsIn(ShipDeckBattleScene scene,
                                            DeckGraph.Compartment room) {
        if (scene == null || room == null) return List.of();
        List<Gantry> boats = new ArrayList<>();
        for (Gantry berth : scene.berthsIn(room)) {
            if (berth.holds == Gantry.Holds.BOAT) boats.add(berth);
        }
        return List.copyOf(boats);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        ShipDeckBattleScene aboard = ship.live() ? ship.scene() : null;
        DeckGraph.Compartment hangar = aboard != null ? bay.get() : null;
        if (hangar == null) {
            targets = List.of();
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }
        ShipDeckBattleScene.RoomView view =
                ShipDeckBattleScene.RoomView.of(hangar, SURROUND_CELLS);

        CanvasHostViewport[] host = new CanvasHostViewport[1];
        BattleSceneHostPass backdrop = aboard.pass(view, BACKDROP_LAYERS);
        boolean rendered = context.hostPass(new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                host[0] = viewport;
                return backdrop.prepare(viewport, alphaMult);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                host[0] = viewport;
                backdrop.draw(viewport, alphaMult);
            }
        });
        if (!rendered || host[0] == null) {
            targets = List.of();
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        markBerths(context, Projection.forHost(aboard, view, host[0]),
                boatBerthsIn(aboard, hangar));
        context.hostPass(aboard.pass(view, ACTOR_LAYERS));
    }

    /** Records hover over the berths drawn during the last frame. */
    public void pointAt(float canvasX, float canvasY) {
        hovered = localBerthAt(canvasX, canvasY);
    }

    /**
     * The campaign deck's index for the berth at this canvas-local point, or -1
     * outside every berth. Projected from the last frame's framing, which is
     * the one the player clicked on.
     */
    public int berthAt(float canvasX, float canvasY) {
        int local = localBerthAt(canvasX, canvasY);
        return local < 0 ? -1 : berthOffset.getAsInt() + local;
    }

    private int localBerthAt(float canvasX, float canvasY) {
        if (!Float.isFinite(canvasX) || !Float.isFinite(canvasY)) return -1;
        for (BerthTarget target : targets) {
            if (target.contains(canvasX, canvasY)) return target.index();
        }
        return -1;
    }

    private void markBerths(CanvasContext context, Projection projection,
                            List<Gantry> berths) {
        int offset = berthOffset.getAsInt();
        int selected = selectedBerth.getAsInt();
        List<BerthTarget> found = new ArrayList<>(berths.size());
        for (int index = 0; index < berths.size(); index++) {
            Gantry berth = berths.get(index);
            float left = projection.x(berth.left());
            float right = projection.x(berth.right() + 1f);
            float top = projection.y(berth.top() + 1f);
            float bottom = projection.y(berth.bottom());
            BerthTarget target = new BerthTarget(index,
                    Math.min(left, right), Math.min(top, bottom),
                    Math.abs(right - left), Math.abs(bottom - top));
            found.add(target);
            if (offset + index == selected) {
                bracket(context, projection, berth);
            } else if (index == hovered) {
                context.strokeRect(target.x(), target.y(),
                        Math.max(1f, target.width()), Math.max(1f, target.height()),
                        HOVER, 1f);
            }
        }
        targets = List.copyOf(found);
    }

    /** Corner marks on the berth's own footprint, so the boat stays legible. */
    private static void bracket(CanvasContext context, Projection projection, Gantry berth) {
        float left = projection.x(berth.left());
        float right = projection.x(berth.right() + 1f);
        float top = projection.y(berth.top() + 1f);
        float bottom = projection.y(berth.bottom());
        float run = Math.abs(projection.x(MARK_CORNER) - projection.x(0f));
        for (float[] corner : new float[][]{
                {left, top, 1f, 1f}, {right, top, -1f, 1f},
                {left, bottom, 1f, -1f}, {right, bottom, -1f, -1f}}) {
            float x = corner[0];
            float y = corner[1];
            context.line(x, y, x + run * corner[2], y, MARK, 2f);
            context.line(x, y, x, y + run * corner[3], MARK, 2f);
        }
    }

    private record BerthTarget(int index, float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= x + width
                    && pointY >= y && pointY <= y + height;
        }
    }
}
