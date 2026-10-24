package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.util.Objects;

/**
 * One boat fitting or door gun as a catalog thumbnail: its piece on its own
 * bricks, at the size it has on the board, the way the Mech Lab's stores
 * show a part before it is fitted.
 */
public final class BoatHardwareCanvas implements CanvasProducer {

    private final BoatDoll.Kind kind;
    private final String itemId;

    public BoatHardwareCanvas(BoatDoll.Kind kind, String itemId) {
        if (kind == null) throw new IllegalArgumentException("a socket kind is required");
        this.kind = kind;
        this.itemId = itemId;
    }

    /** Whether this already draws that piece, so a picture is replaced only when its part changes. */
    public boolean shows(BoatDoll.Kind kind, String itemId) {
        return this.kind == kind && Objects.equals(this.itemId, itemId);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        BoatHardware.Piece piece = BoatHardware.piece(kind, itemId);
        // The board's own brick: a part is the same size in the stores as in its socket.
        HardpointBricks.Layout bricks = HardpointBricks.centred(0f, 0f, width, height,
                piece.columns(), piece.rows(), context.metrics());
        BoatHardware.draw(context, bricks, piece, 255);
    }
}
