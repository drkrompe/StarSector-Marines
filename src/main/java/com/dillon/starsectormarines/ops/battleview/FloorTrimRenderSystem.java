package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The trim round a built floor: a ruins hall's raised floor and its stage are panels laid on the hall's slab, so where
 * one meets any other floor it keeps a clean edge and steps down over a trim (`ruins-expedition.md`, Themed halls are
 * environments). Each such edge lays the floor's trim strip inside the panel cell, half a cell deep, its bottom row at
 * the step, turned to face the side it steps down to, as a foundry's kerb is laid; a faint shadow falls on the lower
 * floor beyond. Until a floor's strip has shipped, its step is drawn plain: a dark riser at the edge and a lit nosing
 * above it. Drawn over the tiles, under everything standing on them. Presentation only.
 */
final class FloorTrimRenderSystem implements RenderSystem {

    /** Each built floor's surface id and the strip laid as its trim, in the foundry lip's format. */
    static final Map<String, String> TRIMS = Map.of(
            "ruins-ground.raised-floor", "graphics/battle/floors/raised-floor-trim.png",
            "ruins-ground.stage-floor", "graphics/battle/floors/stage-floor-trim.png");

    /** The plain step's riser and nosing, and the shadow on the floor below, as shares of a cell. */
    private static final float RISER = .05f, NOSING = .06f, SHADOW = .12f;

    private final BattleSprites sprites;
    private CellTopology builtFor;
    /** Each trimmed edge: {@code {x, y, side}}, side 0 south, 1 east, 2 north, 3 west of the cell. */
    private final List<int[]> edges = new ArrayList<>();
    /** The trim's strip for each entry of {@link #edges}. */
    private final List<String> strips = new ArrayList<>();

    FloorTrimRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override public RenderLayer layer() { return RenderLayer.GROUND; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        CellTopology topology = ctx.sim.getTopology();
        if (builtFor != topology) build(topology);
        if (edges.isEmpty()) return;
        BattleCamera cam = ctx.camera;
        float cell = cam.cellPxSize(), a = ctx.alphaMult;
        for (int i = 0; i < edges.size(); i++) {
            int[] e = edges.get(i);
            int x = e[0], y = e[1], side = e[2];
            int dx = side == 1 ? 1 : side == 3 ? -1 : 0, dy = side == 0 ? -1 : side == 2 ? 1 : 0;
            // The shadow the panel throws on the floor below, just past the step.
            band(cam, out, x + dx, y + dy, side, 1f, 1f - SHADOW, 0f, 0f, 0f, .22f * a);
            ShuttleSpriteCache strip = sprites == null ? null : sprites.spriteByPath(strips.get(i));
            if (strip != null && strip.pxW > 4) {
                float px = cam.cellToScreenX(x + .5f + dx * .25f), py = cam.cellToScreenY(y + .5f + dy * .25f);
                out.addSheetQuad(RenderLayer.GROUND, strip.sprite, 0, 0, strip.pxW, strip.pxH, px, py, cell,
                        cell * .5f, side * 90f, 1f, 1f, 1f, a);
            } else {
                band(cam, out, x, y, side, 0f, RISER, .03f, .03f, .04f, .55f * a);
                band(cam, out, x, y, side, RISER, RISER + NOSING, .86f, .85f, .80f, .45f * a);
            }
        }
    }

    /**
     * A band of cell {@code (x, y)} along its {@code side}, from {@code from} to {@code to} of the way in from that
     * edge (a band on the far cell measured back from its own opposite edge when {@code from} exceeds {@code to}).
     */
    private static void band(BattleCamera cam, DrawList out, int x, int y, int side, float from, float to,
                             float r, float g, float b, float alpha) {
        float lo = Math.min(from, to), hi = Math.max(from, to);
        float x0 = x, x1 = x + 1, y0 = y, y1 = y + 1;
        switch (side) {
            case 0 -> { y0 = y + lo; y1 = y + hi; }
            case 2 -> { y0 = y + 1 - hi; y1 = y + 1 - lo; }
            case 1 -> { x0 = x + 1 - hi; x1 = x + 1 - lo; }
            default -> { x0 = x + lo; x1 = x + hi; }
        }
        float sx0 = cam.cellToScreenX(x0), sx1 = cam.cellToScreenX(x1);
        float sy0 = cam.cellToScreenY(y0), sy1 = cam.cellToScreenY(y1);
        out.addSolidRect(RenderLayer.GROUND, Math.min(sx0, sx1), Math.min(sy0, sy1), Math.max(sx0, sx1),
                Math.max(sy0, sy1), r, g, b, alpha);
    }

    private void build(CellTopology topology) {
        builtFor = topology;
        edges.clear();
        strips.clear();
        int w = topology.getWidth(), h = topology.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                String own = floor(topology, x, y);
                String strip = own == null ? null : TRIMS.get(own);
                if (strip == null) continue;
                for (int side = 0; side < 4; side++) {
                    int nx = x + (side == 1 ? 1 : side == 3 ? -1 : 0), ny = y + (side == 0 ? -1 : side == 2 ? 1 : 0);
                    if (!steps(own, topology.inBounds(nx, ny) && !topology.isWall(nx, ny), floor(topology, nx, ny))) {
                        continue;
                    }
                    edges.add(new int[]{x, y, side});
                    strips.add(strip);
                }
            }
        }
    }

    /** A cell's surface id, or null for none or off the map. */
    private static String floor(CellTopology topology, int x, int y) {
        if (!topology.inBounds(x, y)) return null;
        int surface = topology.getSurface(x, y);
        return surface > 0 ? topology.surfaceId(surface) : null;
    }

    /**
     * Whether a built floor of surface {@code own} steps down to its neighbour: to any open floor that is not the
     * same panel, never against a wall, which stands on the panel's edge.
     */
    static boolean steps(String own, boolean neighbourOpen, String neighbour) {
        return TRIMS.containsKey(own) && neighbourOpen && !own.equals(neighbour);
    }
}
