package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * This frame's older lava flows, queued as one custom command: the extent to draw, in map cells, the screen mapping
 * the collector saw, the crust image, the clock and the alpha. Running it draws through {@link LavaFlowShader}; the
 * offline drain reads the same numbers and the layer's mirror through {@link LavaFlowShading}.
 */
final class LavaFlowPass implements Runnable {

    private final LavaFlowLayer layer;
    int minX, minY, maxX, maxY;
    float screenX0, screenY0, cellPx;
    float time, alpha;
    SpriteAPI crust;
    String crustPath;

    LavaFlowPass(LavaFlowLayer layer) {
        this.layer = layer;
    }

    LavaFlowLayer layer() { return layer; }

    float cellToScreenX(float cellX) { return screenX0 + cellX * cellPx; }
    float cellToScreenY(float cellY) { return screenY0 + cellY * cellPx; }
    float screenToCellX(float px) { return (px - screenX0) / cellPx; }
    float screenToCellY(float py) { return (py - screenY0) / cellPx; }

    @Override
    public void run() {
        layer.draw(this);
    }
}
