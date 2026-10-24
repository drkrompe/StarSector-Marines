package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.util.Objects;

/**
 * One boat fitting or door gun as a catalog thumbnail: its piece on its own
 * bricks, at the one brick size every part shares, the way the Mech Lab's
 * stores show a part before it is fitted.
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

    /** Whether this already draws that piece, so a picture is replaced only when its part changes. */
    public boolean shows(BoatDoll.Kind kind, String itemId) {
        return this.kind == kind && Objects.equals(this.itemId, itemId);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        BoatHardware.Piece piece = BoatHardware.piece(kind, itemId);
        // One brick size for every part, as on the board: a 2x1 is half a 2x2, not the same box.
        int[] scale = BoatHardware.largestSocket();
        HardpointBricks.Layout bricks = HardpointBricks.fitAtScaleOf(4f, 4f, width - 8f, height - 8f,
                piece.columns(), piece.rows(), scale[0], scale[1], BRICK_GAP);
        BoatHardware.draw(context, bricks, piece, 255);
    }
}
