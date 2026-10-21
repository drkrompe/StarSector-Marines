package com.dillon.starsectormarines.ops.battleview;

/**
 * This frame's creep, queued as one custom command: the extent to draw, in map
 * cells, the screen mapping the collector saw, the clock, the palette and the sun.
 * Running it uploads what changed and draws through {@link CreepShader}; the
 * offline drain reads the same numbers and the layer's CPU mirror through
 * {@link CreepShading}.
 */
final class CreepPass implements Runnable {

    private final CreepLayer layer;
    int minX, minY, maxX, maxY;
    float screenX0, screenY0, cellPx;
    float timeSeconds;
    float alpha;
    CreepPalette palette;
    CreepShading.Sun sun = new CreepShading.Sun(-0.7071f, 0.7071f, 0.78f, 0.75f);

    CreepPass(CreepLayer layer) {
        this.layer = layer;
    }

    CreepLayer layer() { return layer; }

    float cellToScreenX(float cellX) { return screenX0 + cellX * cellPx; }
    float cellToScreenY(float cellY) { return screenY0 + cellY * cellPx; }
    float screenToCellX(float px) { return (px - screenX0) / cellPx; }
    float screenToCellY(float py) { return (py - screenY0) / cellPx; }

    @Override
    public void run() {
        layer.draw(this);
    }
}
