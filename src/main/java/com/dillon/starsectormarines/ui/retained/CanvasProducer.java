package com.dillon.starsectormarines.ui.retained;

/** Projects application state into one canvas in canvas-local coordinates. */
@FunctionalInterface
public interface CanvasProducer {
    void draw(CanvasContext context);
}
