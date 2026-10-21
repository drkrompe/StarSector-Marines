package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.ship.CoreDressing;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the founding hull's Deficit dressing ({@link ShipDeckDressing}) in a deck
 * scene: stains and wall pieces beneath the deck's fixtures, dead screens over
 * them, and the dead air hanging over the deck, drawn over the deck lighting's shade.
 *
 * <p>Presentation only, and only in the {@link BattleRenderHostProfile#DECK_SCENE}
 * host: a boarding action or a battle map never sees it. The haze is a flat,
 * slowly drifting tint per sample, masked to walkable floor exactly as the deck
 * lighting is, so it never spills over a bulkhead into space. It reads the
 * shared deck clock and nothing random, so a frame is a pure function of the
 * deck, its condition and the time. {@code -Dbattle.render.deadAirHaze=false}
 * is its control.
 */
final class ShipDressingRenderSystem {

    public static final String HAZE_PROPERTY = "battle.render.deadAirHaze";

    private final DoodadRenderSystem doodads;
    private final PolyMesh haze = new PolyMesh(4096);
    private final List<Doodad> housings = new ArrayList<>();

    ShipDressingRenderSystem(DoodadRenderSystem doodads) {
        this.doodads = doodads;
    }

    static boolean hazeEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(HAZE_PROPERTY));
    }

    /**
     * Beneath the fixtures: the passages' strung lamps and their cable, on every
     * deck, then the Deficit pieces on the deck and hugging the bulkheads.
     */
    void collectBeneath(RenderContext ctx, DrawList out) {
        if (ctx.hostProfile != BattleRenderHostProfile.DECK_SCENE) return;
        DressingWave wave = ctx.dressingWave;
        float time = ctx.animationTimeSeconds;
        ShipDeckLighting lights = ctx.deckLighting;
        if (lights != null) {
            ShipDeckLighting before = wave != null && wave.moves(ShipCore.POWER) ? wave.fromLighting : null;
            if (before != null && before.cables.size() == lights.cables.size()
                    && before.lamps.size() == lights.lamps.size()) {
                // The cable is taken up, and the lamps catch, as the wave reaches them.
                for (int i = 0; i < lights.cables.size(); i++) {
                    Doodad now = lights.cables.get(i), was = before.cables.get(i);
                    float w = wave.weight(ShipCore.POWER, now.cellX + .5f, now.cellY + .5f, time);
                    doodads.collectOne(was, out, ctx.camera, view(ctx), (1f - w) * ctx.alphaMult);
                    doodads.collectOne(now, out, ctx.camera, view(ctx), w * ctx.alphaMult);
                }
                for (var fixture : lights.fixtures) {
                    int lamp = fixture.lamp();
                    if (!lights.present(lamp)) continue;
                    Doodad was = before.dead(lamp) ? fixture.dead() : fixture.lit();
                    Doodad now = lights.dead(lamp) ? fixture.dead() : fixture.lit();
                    float w = was == now ? 1f : wave.flicker(ShipCore.POWER, now.cellX + .5f, now.cellY + .5f, time, lamp);
                    if (w < 1f) doodads.collectOne(was, out, ctx.camera, view(ctx), ctx.alphaMult);
                    if (w > 0f) doodads.collectOne(now, out, ctx.camera, view(ctx), w * ctx.alphaMult);
                }
            } else {
                doodads.collectPlacements(lights.cables, out, ctx.camera, view(ctx), ctx.alphaMult);
                housings.clear();
                for (var fixture : lights.fixtures)
                    if (lights.present(fixture.lamp()))
                        housings.add(lights.dead(fixture.lamp()) ? fixture.dead() : fixture.lit());
                doodads.collectPlacements(housings, out, ctx.camera, view(ctx), ctx.alphaMult);
            }
        }
        ShipDeckDressing dressing = ctx.deckDressing;
        if (dressing == null) return;
        if (wave == null) {
            if (ctx.coreDressing.clean()) return;
            doodads.collectPlacements(dressing.doodads(ctx.coreDressing), out, ctx.camera, view(ctx), ctx.alphaMult);
            return;
        }
        // Mid-wave every piece of the full plan shows as much as its core's Deficit does where it lies.
        var pieces = dressing.pieces();
        var drawn = dressing.doodads(CoreDressing.DEFICIT);
        for (int i = 0; i < pieces.size(); i++) {
            var piece = pieces.get(i);
            Doodad d = drawn.get(i);
            float shown = wave.deficit(piece.kind().core, d.cellX + d.footprintCellsX * .5f,
                    d.cellY + d.footprintCellsY * .5f, time);
            doodads.collectOne(d, out, ctx.camera, view(ctx), shown * ctx.alphaMult);
        }
    }

    /** Over the fixtures: a dead screen is the fixture itself, unlit. */
    void collectOver(RenderContext ctx, DrawList out) {
        ShipDeckDressing dressing = ctx.deckDressing;
        if (dressing == null || ctx.hostProfile != BattleRenderHostProfile.DECK_SCENE) return;
        DressingWave wave = ctx.dressingWave;
        if (wave == null || !wave.moves(ShipCore.COMPUTE)) {
            var dead = dressing.deadConsoles(ctx.coreDressing);
            if (dead.isEmpty()) return;
            doodads.collectTinted(dead, out, ctx.camera, view(ctx), .07f, .08f, .10f, .45f * ctx.alphaMult);
            return;
        }
        // Screens catch one by one as processing reaches them, or die the same way.
        var screens = dressing.deadConsoles(CoreDressing.DEFICIT);
        for (int i = 0; i < screens.size(); i++) {
            Doodad screen = screens.get(i);
            float x = screen.cellX + screen.footprintCellsX * .5f, y = screen.cellY + screen.footprintCellsY * .5f;
            float lit = wave.to.compute() ? wave.flicker(ShipCore.COMPUTE, x, y, ctx.animationTimeSeconds, 31 + i)
                    : 1f - wave.weight(ShipCore.COMPUTE, x, y, ctx.animationTimeSeconds);
            float dead = 1f - lit;
            if (dead > 0f) doodads.collectTinted(List.of(screen), out, ctx.camera, view(ctx),
                    .07f, .08f, .10f, .45f * dead * ctx.alphaMult);
        }
    }

    /** Dead air: a faint grey-green that thickens and thins slowly over the deck. */
    void collectHaze(RenderContext ctx, DrawList out) {
        ShipDeckDressing dressing = ctx.deckDressing;
        DressingWave wave = ctx.dressingWave;
        boolean moving = wave != null && wave.moves(ShipCore.LIFE_SUPPORT);
        if (dressing == null || ctx.hostProfile != BattleRenderHostProfile.DECK_SCENE
                || (ctx.coreDressing.lifeSupport() && !moving) || !hazeEnabled()) return;
        BattleCamera camera = ctx.camera;
        var visible = camera.visibleCells(1, dressing.width, dressing.height);
        float time = Float.isFinite(ctx.animationTimeSeconds) ? ctx.animationTimeSeconds : 0f;
        int steps = camera.cellPxSize() >= 24f ? 2 : 1;
        float step = 1f / steps;
        haze.reset();
        for (int y = visible.minY(); y <= visible.maxY(); y++) for (int x = visible.minX(); x <= visible.maxX(); x++) {
            if (!dressing.hazeFloor(x, y)) continue;
            for (int sy = 0; sy < steps; sy++) for (int sx = 0; sx < steps; sx++) {
                float px = x + (sx + .5f) * step, py = y + (sy + .5f) * step;
                float density = density(px, py, time);
                // The dead air drains, or gathers, as the wave reaches it.
                float present = moving ? wave.deficit(ShipCore.LIFE_SUPPORT, px, py, time) : 1f;
                if (present <= 0f) continue;
                float alpha = (.07f + .24f * density) * present * ctx.alphaMult;
                float x0 = Math.round(camera.cellToScreenX(x + sx * step));
                float x1 = Math.round(camera.cellToScreenX(x + (sx + 1) * step));
                float y0 = Math.round(camera.cellToScreenY(y + sy * step));
                float y1 = Math.round(camera.cellToScreenY(y + (sy + 1) * step));
                haze.appendQuad(x0, y0, x1, y0, x1, y1, x0, y1, .46f, .50f, .42f, alpha);
            }
        }
        out.addPoly(RenderLayer.DECK_LIGHTING, haze);
    }

    /** Two drifting octaves of value noise, in [0, 1]. Banks of air, not speckle. */
    static float density(float x, float y, float seconds) {
        float a = noise(x / 7f + seconds * .021f, y / 7f - seconds * .013f, 11);
        float b = noise(x / 3.1f - seconds * .037f, y / 3.1f + seconds * .029f, 29);
        float d = .7f * a + .3f * b;
        return Math.max(0f, Math.min(1f, (d - .25f) / .6f));
    }

    private static float noise(float x, float y, int salt) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3f - 2f * fx); fy = fy * fy * (3f - 2f * fy);
        float a = lattice(ix, iy, salt), b = lattice(ix + 1, iy, salt);
        float c = lattice(ix, iy + 1, salt), d = lattice(ix + 1, iy + 1, salt);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static float lattice(int x, int y, int salt) {
        return (ShipDeckDressing.hash(salt, x, y, 7) >>> 40) / (float) (1L << 24);
    }

    private static VisibleCellRect view(RenderContext ctx) {
        return ctx.camera.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                ctx.sim.getGrid().getWidth(), ctx.sim.getGrid().getHeight());
    }
}
