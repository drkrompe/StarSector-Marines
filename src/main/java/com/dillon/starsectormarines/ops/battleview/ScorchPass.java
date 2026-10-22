package com.dillon.starsectormarines.ops.battleview;

/**
 * This frame's burnt ground, queued as one custom command: the extent to draw, in
 * map cells, the screen mapping the collector saw and the alpha. Running it
 * uploads what changed and draws through {@link ScorchShader}; the offline drain
 * reads the same numbers and the layer's mirror through {@link ScorchShading}.
 */
final class ScorchPass implements Runnable {

    private final ScorchLayer layer;
    int minX, minY, maxX, maxY;
    float screenX0, screenY0, cellPx;
    float alpha;

    ScorchPass(ScorchLayer layer) {
        this.layer = layer;
    }

    ScorchLayer layer() { return layer; }

    float cellToScreenX(float cellX) { return screenX0 + cellX * cellPx; }
    float cellToScreenY(float cellY) { return screenY0 + cellY * cellPx; }
    float screenToCellX(float px) { return (px - screenX0) / cellPx; }
    float screenToCellY(float py) { return (py - screenY0) / cellPx; }

    @Override
    public void run() {
        layer.draw(this);
    }
}
