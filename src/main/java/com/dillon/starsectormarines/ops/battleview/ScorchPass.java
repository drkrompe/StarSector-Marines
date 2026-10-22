package com.dillon.starsectormarines.ops.battleview;

/**
 * This frame's burnt ground, queued as one custom command: the extent to shade, in
 * map cells, the screen mapping the collector saw, the style and the picture to
 * read ({@link ScorchLayer}). The offline drain shades it through {@link
 * ScorchShading}, multiplying the ground under it and laying the overlay on top.
 * There is no GL program for it yet: until the owner picks a style, the game
 * draws burnt ground with {@link FireRenderSystem}'s flat scorch, and a layered
 * style draws nothing live.
 */
final class ScorchPass implements Runnable {

    final ScorchShading.Field field;
    final ScorchShading.Style style;
    final int minX, minY, maxX, maxY;
    final float screenX0, screenY0, cellPx, alpha;

    ScorchPass(ScorchShading.Field field, ScorchShading.Style style, int minX, int minY, int maxX, int maxY,
               float screenX0, float screenY0, float cellPx, float alpha) {
        this.field = field;
        this.style = style;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.screenX0 = screenX0;
        this.screenY0 = screenY0;
        this.cellPx = cellPx;
        this.alpha = alpha;
    }

    float cellToScreenX(float cellX) { return screenX0 + cellX * cellPx; }
    float cellToScreenY(float cellY) { return screenY0 + cellY * cellPx; }
    float screenToCellX(float px) { return (px - screenX0) / cellPx; }
    float screenToCellY(float py) { return (py - screenY0) / cellPx; }

    @Override
    public void run() { }
}
