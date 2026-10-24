package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

/**
 * The part on the end of the pointer: a pointer-transparent canvas over the
 * whole room, drawing whatever is carried just below and right of the pointer
 * at the size it will have in its socket.
 */
public final class FittingDragGhost implements CanvasProducer {

    /** How a room draws one of its parts, top-left at a canvas point. */
    @FunctionalInterface
    public interface CarriedPiece {
        void draw(CanvasContext c, float x, float y);
    }

    /** Clear of the pointer's own arrow, so the piece does not hide where it is going. */
    private static final float OFFSET = 18f;

    private CarriedPiece piece;
    private float documentX;
    private float documentY;

    public void show(CarriedPiece piece, float documentX, float documentY) {
        this.piece = piece;
        this.documentX = documentX;
        this.documentY = documentY;
    }

    public void clear() { piece = null; }

    @Override
    public void draw(CanvasContext c) {
        if (piece == null) return;
        piece.draw(c, c.metrics().toCanvasX(documentX) + OFFSET, c.metrics().toCanvasY(documentY) + OFFSET);
    }
}
