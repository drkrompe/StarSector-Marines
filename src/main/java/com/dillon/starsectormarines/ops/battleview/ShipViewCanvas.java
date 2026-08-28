package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;

/**
 * The whole ship, running, with the camera in the player's hands.
 *
 * <p>Every other view of the company ship is pointed at one compartment,
 * because it is there to show the player a room. This one is pointed at the
 * vessel: the watch walking between the mess and the range, machines standing
 * in their berths, the length of her. A ship you can only ever see one room of
 * is a set of rooms, and the whole point of generating a deck rather than
 * authoring rooms is that she is one place.
 *
 * <p><b>Nothing is commanded here.</b> The player looks; the ship carries on.
 * That is what makes it worth having a camera at all — there is something to
 * find by flying around it rather than a diagram to read.
 *
 * <p>The camera is an ordinary {@link BattleCamera}, framed so that fully
 * zoomed out is the whole hull. Its own zoom floor then means the player cannot
 * pull back into empty space around a ship, and its pan clamp means they cannot
 * lose her off the edge of the view.
 */
public final class ShipViewCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);

    /**
     * What the hull is darkened to behind the deck.
     *
     * <p>A tint multiplies, so this cannot flatten the art into one colour —
     * bright plating stays faintly brighter than the panel lines. That is the
     * point rather than a limitation: the plating is most of what makes a hull
     * recognisable, and a flat cutout of a Valkyrie is a flat cutout.
     */
    private static final Color HULL = new Color(0x2C, 0x39, 0x4C, 0xFF);

    /**
     * A vanilla hull sprite is drawn bow-up and the deck is generated bow-left,
     * so the art is turned a quarter to lie along her.
     */
    private static final float BOW_LEFT = -90f;

    /**
     * How much larger the hull is drawn than the deck laid inside her.
     *
     * <p>A deck is a slice through a ship, not her outline: the plating, the
     * spaces below and the structure the compartments are hung on all sit
     * outside it. Drawing the art to the deck's own bounds would put her
     * skin exactly on the walls of her rooms, which is the one place it
     * certainly is not.
     */
    private static final float HULL_OVERHANG = 1.06f;

    private final CompanyDeck ship;
    private final String hullArt;
    private BattleCamera camera;

    public ShipViewCanvas(CompanyDeck ship) {
        this(ship, null);
    }

    /**
     * @param hullArt the hull's own sprite, drawn darkened behind the deck, or
     *     null for a ship whose art cannot be resolved. The deck is generated
     *     from the same {@code .ship} file the art belongs to, so the two are
     *     the same vessel rather than a plan with a picture behind it.
     */
    public ShipViewCanvas(CompanyDeck ship, String hullArt) {
        if (ship == null) throw new IllegalArgumentException("a ship is required");
        this.ship = ship;
        this.hullArt = hullArt;
    }

    /**
     * The camera being flown, or null before the first frame has sized it.
     *
     * <p>Built on the first draw rather than in the constructor because it is
     * framed to fit the surface, and how large the surface is is not known
     * until the document has laid out.
     */
    public BattleCamera camera() {
        return camera;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        ShipDeckBattleScene aboard = ship.live() ? ship.scene() : null;
        if (aboard == null || width <= 0f || height <= 0f) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        MapResult deck = ship.map();
        int cellsAcross = Math.max(1, deck.grid.getWidth());
        int cellsDown = Math.max(1, deck.grid.getHeight());
        // Fully zoomed out is the whole hull, so the camera's own zoom floor
        // becomes "you cannot pull back past the ship" for free.
        float baseCell = Math.min(width / cellsAcross, height / cellsDown);
        if (camera == null) {
            camera = new BattleCamera(cellsAcross, cellsDown);
            camera.centerOn(cellsAcross * 0.5f, cellsDown * 0.5f);
        }
        // Re-stated every frame so a resized dialog re-fits rather than
        // keeping a scale that was right for a window that is gone.
        camera.setViewport(0f, 0f, width, height, baseCell);

        drawHull(context, cellsAcross, cellsDown);

        ShipDeckBattleScene.DeckView view = new ShipDeckBattleScene.DeckView(
                camera.panCellX(), camera.panCellY(), camera.cellPxSize());
        if (!context.hostPass(aboard.pass(view, ShipDeckBattleScene.DECK_LAYERS))) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
        }
    }

    /**
     * The hull herself, darkened, under the deck.
     *
     * <p>Drawn first so the compartments sit on top of her: what shows is the
     * art around and between the rooms, which is where a hull's shape actually
     * lives. A backdrop over the deck would be a picture of a ship with a plan
     * hidden behind it.
     *
     * <p>Scaled to the deck's own extent, and turned to match. The sprite's
     * long axis is her length and the deck's long axis is the same length, so
     * the two agree by construction rather than by being nudged into place.
     */
    private void drawHull(CanvasContext context, int cellsAcross, int cellsDown) {
        if (hullArt == null || hullArt.isBlank()) return;
        float cell = camera.cellPxSize();
        context.sprite(hullArt, null,
                camera.cellToScreenX(cellsAcross * 0.5f),
                camera.cellToScreenY(cellsDown * 0.5f),
                cellsDown * cell * HULL_OVERHANG, cellsAcross * cell * HULL_OVERHANG,
                BOW_LEFT, HULL);
    }
}
