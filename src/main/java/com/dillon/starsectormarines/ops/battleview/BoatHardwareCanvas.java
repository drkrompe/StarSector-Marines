package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

/**
 * One boat fitting or door gun as a catalog thumbnail: its piece on its own
 * bricks, at whatever size the row gives it, the way the Mech Lab's stores
 * show a part before it is fitted.
 */
public final class BoatHardwareCanvas implements CanvasProducer {

    private static final float BRICK_GAP = 2f;

    private final BoatDoll.Kind kind;
    private final String itemId;

    public BoatHardwareCanvas(BoatDoll.Kind kind, String itemId) {
        if (kind == null) throw new IllegalArgumentException("a socket kind is required");
        this.kind = kind;
        this.itemId = itemId;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        BoatHardware.Piece piece = BoatHardware.piece(kind, itemId);
        HardpointBricks.Layout bricks = HardpointBricks.fit(4f, 4f, width - 8f, height - 8f,
                piece.columns(), piece.rows(), BRICK_GAP);
        BoatHardware.draw(context, bricks, piece, 255);
    }
}
