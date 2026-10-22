package com.dillon.starsectormarines.ops.battleview;

/**
 * This frame's baked cliffs, queued as one custom command in the ground: the
 * chunks in view and the screen mapping the collector saw. Running it draws
 * the layer's atlas; the offline drain samples the layer's bake through
 * {@link CliffArtLayer#sample} instead.
 */
final class CliffArtPass implements Runnable {

    private final CliffArtLayer layer;
    int minChunkX, minChunkY, maxChunkX, maxChunkY;
    float screenX0, screenY0, cellPx;
    float alpha;

    CliffArtPass(CliffArtLayer layer) {
        this.layer = layer;
    }

    CliffArtLayer layer() { return layer; }

    float cellToScreenX(float cellX) { return screenX0 + cellX * cellPx; }
    float cellToScreenY(float cellY) { return screenY0 + cellY * cellPx; }
    float screenToCellX(float px) { return (px - screenX0) / cellPx; }
    float screenToCellY(float py) { return (py - screenY0) / cellPx; }

    @Override
    public void run() {
        layer.draw(this);
    }
}
