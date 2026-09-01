package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * The selected squad's own berthing aboard the company ship.
 *
 * <p>Theirs, not the ship's biggest bunkroom. A company is billeted across
 * however many berthings the deck laid down, so a fixed framing would show the
 * player a squad list beside a room that squad does not sleep in, and selecting
 * a different formation would change nothing on screen. Which room is theirs is
 * the ship's answer, not this canvas's, so the heading above the room and the
 * room itself cannot disagree.
 *
 * <p>The room is not the squad, either. Everybody quartered there is drawn,
 * and selecting a squad marks its members rather than emptying the room of
 * their bunkmates - and half of them will be out at the mess or on the range,
 * because that is where marines off watch are.
 *
 * <p>Marks rather than a highlight layer: the deck's render passes are the
 * ordinary world layers, and a selection cue is a fact about this screen rather
 * than about the ship.
 */
public final class BarracksCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);
    private static final Color MARK = new Color(0x80, 0xFF, 0xA0, 190);
    /** Half-width of a selection bracket, in cells. */
    private static final float MARK_REACH = 0.62f;
    /** How far along each side of the bracket a corner runs, in cells. */
    private static final float MARK_CORNER = 0.26f;
    /** Hull kept around the room, so a doorway is not clipped into a gap. */
    private static final int SURROUND_CELLS = 2;

    private static final EnumSet<RenderLayer> BACKDROP_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);
    private static final EnumSet<RenderLayer> ACTOR_LAYERS = EnumSet.of(
            RenderLayer.UNITS, RenderLayer.SHOTS);

    private final CompanyDeck ship;
    private final Supplier<List<MarineSoldier>> selected;

    /**
     * @param ship the company ship; drawn only once she is running, so a canvas
     *     that outlives the dialog cannot bring her back
     * @param selected the squad the list has selected, which decides both the
     *     room framed and who is marked in it
     */
    public BarracksCanvas(CompanyDeck ship, Supplier<List<MarineSoldier>> selected) {
        if (ship == null || selected == null) {
            throw new IllegalArgumentException("a ship and a selected squad are required");
        }
        this.ship = ship;
        this.selected = selected;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        List<MarineSoldier> squad = selected.get();
        ShipDeckBattleScene aboard = ship.live() ? ship.scene() : null;
        DeckGraph.Compartment berthing = aboard != null ? ship.quartersFor(squad) : null;
        if (berthing == null) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }
        ShipDeckBattleScene.RoomView view =
                ShipDeckBattleScene.RoomView.of(berthing, SURROUND_CELLS);

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
        if (!rendered) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        if (host[0] != null) {
            markSelected(context, aboard, squad,
                    Projection.forHost(aboard, view, host[0]));
        }
        context.hostPass(aboard.pass(view, ACTOR_LAYERS));
    }

    /**
     * Bracket each selected marine where they are standing right now.
     *
     * <p>Read off the simulation rather than off a remembered bunk: the watch is
     * walking to the mess and the range while the player reads the list, and a
     * mark on where somebody was billeted would sit on an empty rack.
     */
    private void markSelected(CanvasContext context, ShipDeckBattleScene aboard,
                              List<MarineSoldier> squad, Projection projection) {
        World world = aboard.simulation().world();
        UnitRosterService roster = aboard.simulation().getRoster();
        for (MarineSoldier soldier : squad) {
            long marine = ship.marineFor(soldier.id());
            if (marine == 0L || !roster.isLive(marine)) continue;
            bracket(context, projection, world.x(marine), world.y(marine));
        }
    }

    private static void bracket(CanvasContext context, Projection projection,
                                float worldX, float worldY) {
        float left = projection.x(worldX - MARK_REACH);
        float right = projection.x(worldX + MARK_REACH);
        float top = projection.y(worldY + MARK_REACH);
        float bottom = projection.y(worldY - MARK_REACH);
        float run = Math.abs(projection.x(MARK_CORNER) - projection.x(0f));
        for (float[] corner : new float[][]{
                {left, top, 1f, 1f}, {right, top, -1f, 1f},
                {left, bottom, 1f, -1f}, {right, bottom, -1f, -1f}}) {
            float x = corner[0];
            float y = corner[1];
            context.line(x, y, x + run * corner[2], y, MARK, 1f);
            context.line(x, y, x, y + run * corner[3], MARK, 1f);
        }
    }
}
