package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/** Shared command-driven lighting for live and headless deck views; never darkens UI or space. */
final class ShipLightingRenderSystem implements RenderSystem {
    private final PolyMesh shade = new PolyMesh(4096);
    private float[] gains = new float[0];
    private final float[] rgb = new float[3];
    private float[] colours = new float[0];
    @Override public RenderLayer layer() { return RenderLayer.DECK_LIGHTING; }

    @Override public void collect(RenderContext ctx, DrawList out) {
        var lights = ctx.deckLighting;
        if (lights == null || ctx.hostProfile != BattleRenderHostProfile.DECK_SCENE) return;
        var camera = ctx.camera;
        var visible = camera.visibleCells(1, lights.width, lights.height);
        float time = Float.isFinite(ctx.animationTimeSeconds) ? ctx.animationTimeSeconds : 0f;
        if (gains.length < lights.lamps.size()) gains = new float[lights.lamps.size()];
        // Mid-wave, each lamp moves from the old condition's light to the new as the wave reaches it.
        DressingWave wave = ctx.dressingWave;
        ShipDeckLighting before = wave == null ? null : wave.fromLighting;
        boolean blend = before != null && before != lights && before.lamps.size() == lights.lamps.size()
                && before.cells.length == lights.cells.length;
        if (blend && colours.length < lights.lamps.size() * 3) colours = new float[lights.lamps.size() * 3];
        for (int i = 0; i < lights.lamps.size(); i++) {
            gains[i] = lights.gain(i, time);
            if (!blend) continue;
            var lamp = lights.lamps.get(i);
            var was = before.lamps.get(i);
            // Room strips and strung lamps are both on the Power core's distribution.
            ShipCore core = lamp.style() == ShipDeckLighting.Style.SCREEN ? ShipCore.COMPUTE : ShipCore.POWER;
            float w = !wave.moves(core) ? 1f : wave.flicker(core, lamp.x(), lamp.y(), time, i);
            gains[i] = before.gain(i, time) + (gains[i] - before.gain(i, time)) * w;
            colours[i * 3] = was.r() + (lamp.r() - was.r()) * w;
            colours[i * 3 + 1] = was.g() + (lamp.g() - was.g()) * w;
            colours[i * 3 + 2] = was.b() + (lamp.b() - was.b()) * w;
        }
        boolean powerMoves = blend && wave.moves(ShipCore.POWER);
        shade.reset();
        int steps = camera.cellPxSize() >= 32f ? ShipDeckLighting.SAMPLES : camera.cellPxSize() >= 16f ? 4 : 2;
        float step = 1f / steps;
        for (int y = visible.minY(); y <= visible.maxY(); y++) for (int x = visible.minX(); x <= visible.maxX(); x++) {
            var topology = ctx.sim.getTopology();
            if (topology.isWall(x, y) || topology.getGroundKind(x, y) == GroundKind.VOID) continue;
            var cell = lights.cells[y * lights.width + x];
            boolean emergency = cell != null && cell.emergency();
            for (int sy = 0; sy < steps; sy++) for (int sx = 0; sx < steps; sx++) {
                int sampleX = (2 * sx + 1) * ShipDeckLighting.SAMPLES / (2 * steps);
                int sampleY = (2 * sy + 1) * ShipDeckLighting.SAMPLES / (2 * steps);
                // A freshly demolished wall was absent from the floor bake. Its rubble
                // still receives ambient darkness rather than glowing through a missing mask.
                int sample = sampleY * ShipDeckLighting.SAMPLES + sampleX;
                float strength = cell == null ? 0f : blend ? ShipDeckLighting.colour(cell, sample, gains, colours, rgb)
                        : lights.colour(cell, sample, gains, rgb);
                float shadow = cell == null ? 1f : cell.shadow()[sample];
                float lit = Math.min(1f, strength);
                float base = cell == null ? ShipDeckLighting.shade(emergency, 0f) : ShipDeckLighting.shade(cell, lit);
                if (powerMoves && cell != null) {
                    var wasCell = before.cells[y * lights.width + x];
                    if (wasCell != null) {
                        float w = wave.weight(ShipCore.POWER, x + .5f, y + .5f, time);
                        base = ShipDeckLighting.shade(wasCell, lit) + (base - ShipDeckLighting.shade(wasCell, lit)) * w;
                    }
                }
                float darkness = 1f - (1f - Math.max(0f, base)) * shadow;
                // The light's own colour shows as a spill over the dark mask.
                float tint = cell == null ? 0f : cell.tint();
                float warmth = tint * lit;
                float hr = strength > 0f ? rgb[0] / strength : 0f;
                float hg = strength > 0f ? rgb[1] / strength : 0f;
                float hb = strength > 0f ? rgb[2] / strength : 0f;
                // Compose the dark mask and the spill into one quad, retaining the deck's texture.
                float alpha = darkness + warmth * (1f - darkness);
                float darkWeight = darkness * (1f - warmth);
                float r = (.008f * darkWeight + warmth * hr) / alpha;
                float g = (.012f * darkWeight + warmth * hg) / alpha;
                float b = (.022f * darkWeight + warmth * hb) / alpha;
                // Share exact pixel edges: fractional alpha coverage otherwise leaves a bright lattice
                // between adjacent translucent tiles in the headless rasterizer.
                float x0 = Math.round(camera.cellToScreenX(x + sx * step));
                float x1 = Math.round(camera.cellToScreenX(x + (sx + 1) * step));
                float y0 = Math.round(camera.cellToScreenY(y + sy * step));
                float y1 = Math.round(camera.cellToScreenY(y + (sy + 1) * step));
                shade.appendQuad(x0, y0, x1, y0, x1, y1, x0, y1,
                        r, g, b, alpha * ctx.alphaMult);
            }
        }
        out.addPoly(layer(), shade);
        for (int i = 0; i < lights.lamps.size(); i++) {
            if (!lights.present(i)) continue;
            var lamp = lights.lamps.get(i);
            if (lamp.style() == ShipDeckLighting.Style.SCREEN) continue;
            if (!visible.contains((int) lamp.x(), (int) lamp.y())) continue;
            float cx = camera.cellToScreenX(lamp.x()), cy = camera.cellToScreenY(lamp.y());
            float px = camera.cellPxSize();
            if (lamp.style() == ShipDeckLighting.Style.STRUNG) {
                // The caged housing is drawn on the deck beneath the shade; its bulb glows over it.
                if (gains[i] <= 0f) continue;
                for (int halo = 3; halo >= 1; halo--) {
                    float h = px * (.10f + halo * .07f);
                    out.addLine(layer(), cx - h * .5f, cy, cx + h * .5f, cy, h,
                            lamp.r(), lamp.g(), lamp.b(), .07f * gains[i] * ctx.alphaMult);
                }
                float bulb = Math.max(1.5f, px * .09f);
                out.addLine(layer(), cx - bulb * .5f, cy, cx + bulb * .5f, cy, bulb,
                        1f, .86f, .6f, (.25f + .75f * gains[i]) * ctx.alphaMult);
                continue;
            }
            float dx = lamp.vertical() ? 0f : px * .34f;
            float dy = lamp.vertical() ? px * .34f : 0f;
            for (int halo = 3; halo >= 1; halo--)
                out.addLine(layer(), cx - dx, cy - dy, cx + dx, cy + dy, px * (.12f + halo * .08f),
                        lamp.r(), lamp.g(), lamp.b(), .045f * gains[i] * ctx.alphaMult);
            StripFixtureArt.draw(out, layer(), cx, cy, px, lamp.vertical(),
                    lamp.r(), lamp.g(), lamp.b(), gains[i], ctx.alphaMult);
        }
    }
}
