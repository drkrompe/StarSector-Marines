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

    private final CompanyDeck ship;
    private BattleCamera camera;

    public ShipViewCanvas(CompanyDeck ship) {
        if (ship == null) throw new IllegalArgumentException("a ship is required");
        this.ship = ship;
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

        ShipDeckBattleScene.DeckView view = new ShipDeckBattleScene.DeckView(
                camera.panCellX(), camera.panCellY(), camera.cellPxSize());
        if (!context.hostPass(aboard.pass(view, ShipDeckBattleScene.DECK_LAYERS))) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
        }
    }
}
