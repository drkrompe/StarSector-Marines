package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import java.awt.Color;

/**
 * Where the fleet is, with no ship drawn over it: Engineering's set dressing for a company
 * whose facilities belong to the fleet rather than to a hull (`fleet-facilities.md`). The
 * same {@link ShipBackdrop} the deck view paints behind her, held still at its centre.
 */
public final class BackdropCanvas implements CanvasProducer {
    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);
    private final ShipBackdrop backdrop;

    public BackdropCanvas(ShipBackdrop backdrop) { this.backdrop = backdrop; }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        context.fillRect(0f, 0f, width, height, BACKGROUND);
        CanvasHostViewport rect = context.hostViewport();
        if (backdrop == null || rect == null || !(rect.scaleX() > 0f) || !(rect.scaleY() > 0f)) return;
        backdrop.draw(context, rect, 0f, 0f);
    }
}
