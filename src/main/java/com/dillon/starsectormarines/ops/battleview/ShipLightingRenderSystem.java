package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.PolyMesh;

/** Shared command-driven lighting for live and headless deck views; never darkens UI or space. */
final class ShipLightingRenderSystem implements RenderSystem {
    private final PolyMesh shade = new PolyMesh(4096);
    private float[] gains = new float[0];
    @Override public RenderLayer layer() { return RenderLayer.DECK_LIGHTING; }

    @Override public void collect(RenderContext ctx, DrawList out) {
        var lights = ctx.deckLighting;
        if (lights == null || ctx.hostProfile != BattleRenderHostProfile.DECK_SCENE) return;
        var camera = ctx.camera;
        var visible = camera.visibleCells(1, lights.width, lights.height);
        float time = Float.isFinite(ctx.animationTimeSeconds) ? ctx.animationTimeSeconds : 0f;
        if (gains.length < lights.lamps.size()) gains = new float[lights.lamps.size()];
        for (int i = 0; i < lights.lamps.size(); i++) gains[i] = ShipDeckLighting.gain(lights.lamps.get(i), time);
        shade.reset();
        int steps = camera.cellPxSize() >= 24f ? ShipDeckLighting.SAMPLES : 1;
        float step = 1f / steps;
        for (int y = visible.minY(); y <= visible.maxY(); y++) for (int x = visible.minX(); x <= visible.maxX(); x++) {
            var cell = lights.cells[y * lights.width + x];
            if (cell == null) continue;
            for (int sy = 0; sy < steps; sy++) for (int sx = 0; sx < steps; sx++) {
                int sample = steps == 1 ? 5 : sy * steps + sx;
                float alpha = ShipDeckLighting.shade(cell, sample, gains) * ctx.alphaMult;
                float x0 = camera.cellToScreenX(x + sx * step), x1 = camera.cellToScreenX(x + (sx + 1) * step);
                float y0 = camera.cellToScreenY(y + sy * step), y1 = camera.cellToScreenY(y + (sy + 1) * step);
                shade.appendQuad(x0, y0, x1, y0, x1, y1, x0, y1,
                        cell.emergency() ? .09f : .015f, .035f, cell.emergency() ? .015f : .055f, alpha);
            }
        }
        out.addPoly(layer(), shade);
        for (int i = 0; i < lights.lamps.size(); i++) {
            var lamp = lights.lamps.get(i);
            if (!visible.contains((int) lamp.x(), (int) lamp.y())) continue;
            float cx = camera.cellToScreenX(lamp.x()), cy = camera.cellToScreenY(lamp.y());
            float px = camera.cellPxSize(), dx = lamp.vertical() ? 0f : px * .34f;
            float dy = lamp.vertical() ? px * .34f : 0f;
            float r = lamp.emergency() ? 1f : .84f, g = lamp.emergency() ? .57f : .90f;
            float b = lamp.emergency() ? .20f : .86f;
            out.addLine(layer(), cx - dx, cy - dy, cx + dx, cy + dy, px * .18f, .09f, .11f, .11f, ctx.alphaMult);
            for (int halo = 3; halo >= 1; halo--)
                out.addLine(layer(), cx - dx, cy - dy, cx + dx, cy + dy, px * (.12f + halo * .08f),
                        r, g, b, .045f * gains[i] * ctx.alphaMult);
            out.addLine(layer(), cx - dx, cy - dy, cx + dx, cy + dy, Math.max(1f, px * .065f),
                    r, g, b, (.15f + .85f * gains[i]) * ctx.alphaMult);
        }
    }
}
