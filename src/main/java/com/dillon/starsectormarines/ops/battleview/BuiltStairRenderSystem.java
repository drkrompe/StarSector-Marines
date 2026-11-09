package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.ArrayList;
import java.util.List;

/**
 * Stairs on a built ramp: a ramp cell laid with a surface override (deck, grating, a walkway) rather than natural
 * ground, whose steps the baked cliff art does not draw. Each cell is cut into treads across its climb, each with a
 * dark riser at its foot and a lit nosing above it, the lower treads shaded darker, so a walkway climbing onto a
 * raised roof reads as a flight of steps (`elevation-nouns.md`, Look). Drawn over the tiles inside the ground's
 * colour target, as the cliff art is. Presentation only.
 */
final class BuiltStairRenderSystem implements RenderSystem {

    /** Treads to a cell along its climb. */
    static final int TREADS = 3;
    private static final float RISER = .09f, NOSING = .07f;

    private NavigationGrid builtFor;
    /** Each built ramp cell: {@code {x, y, climbX, climbY, rise}}. */
    private final List<float[]> cells = new ArrayList<>();

    @Override public RenderLayer layer() { return RenderLayer.GROUND; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        NavigationGrid grid = ctx.sim.getGrid();
        ElevationMap elevation = grid.elevation();
        if (elevation == null) return;
        if (builtFor != grid) build(grid, elevation, ctx.sim.getTopology());
        if (cells.isEmpty()) return;
        BattleCamera cam = ctx.camera;
        float a = ctx.alphaMult;
        for (float[] c : cells) {
            int x = (int) c[0], y = (int) c[1];
            float cx = c[2], cy = c[3];
            for (int t = 0; t < TREADS; t++) {
                // Along the climb from the cell's low side: tread t spans [t, t+1] / TREADS of the cell.
                float u0 = (float) t / TREADS, u1 = (t + 1f) / TREADS;
                float rise = c[4] + (u0 - .5f) / TREADS;
                tread(cam, out, x, y, cx, cy, u0, u1, 0f, 0f, 0f, .22f * (1f - Math.max(0f, Math.min(1f, rise))) * a);
                tread(cam, out, x, y, cx, cy, u0, u0 + RISER, .02f, .02f, .03f, .62f * a);
                tread(cam, out, x, y, cx, cy, u0 + RISER, u0 + RISER + NOSING, .82f, .80f, .74f, .38f * a);
            }
        }
    }

    /** A band across the cell between {@code u0} and {@code u1} of the way up its climb. */
    private static void tread(BattleCamera cam, DrawList out, int x, int y, float climbX, float climbY,
                              float u0, float u1, float r, float g, float b, float alpha) {
        float x0, x1, y0, y1;
        if (climbX != 0f) {
            float a0 = climbX > 0 ? x + u0 : x + 1 - u1, a1 = climbX > 0 ? x + u1 : x + 1 - u0;
            x0 = a0; x1 = a1; y0 = y; y1 = y + 1;
        } else {
            float a0 = climbY > 0 ? y + u0 : y + 1 - u1, a1 = climbY > 0 ? y + u1 : y + 1 - u0;
            x0 = x; x1 = x + 1; y0 = a0; y1 = a1;
        }
        float sx0 = cam.cellToScreenX(x0), sx1 = cam.cellToScreenX(x1);
        float sy0 = cam.cellToScreenY(y0), sy1 = cam.cellToScreenY(y1);
        out.addSolidRect(RenderLayer.GROUND, Math.min(sx0, sx1), Math.min(sy0, sy1), Math.max(sx0, sx1),
                Math.max(sy0, sy1), r, g, b, alpha);
    }

    private void build(NavigationGrid grid, ElevationMap elevation, CellTopology topology) {
        builtFor = grid;
        cells.clear();
        int w = grid.getWidth(), h = grid.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!elevation.isRampAt(i) || topology.getSurface(x, y) == 0) continue;
                float gx = height(grid, elevation, x + 1, y, x, y) - height(grid, elevation, x - 1, y, x, y);
                float gy = height(grid, elevation, x, y + 1, x, y) - height(grid, elevation, x, y - 1, x, y);
                if (gx == 0f && gy == 0f) continue;
                boolean alongX = Math.abs(gx) >= Math.abs(gy);
                cells.add(new float[]{x, y, alongX ? Math.signum(gx) : 0f, alongX ? 0f : Math.signum(gy),
                        elevation.rampRiseAt(i)});
            }
        }
    }

    /**
     * How high a neighbour stands, in tiers, for finding a ramp cell's climb: a ramp cell by how far up its ramp
     * it is, walkable ground by its tier, anything else (a wall, a void) level with the cell itself.
     */
    static float height(NavigationGrid grid, ElevationMap elevation, int x, int y, int fromX, int fromY) {
        int w = grid.getWidth();
        if (!elevation.inBounds(x, y) || !grid.isWalkable(x, y)) {
            int from = fromY * w + fromX;
            return elevation.eyeTier(fromX, fromY) + elevation.rampRiseAt(from);
        }
        int i = y * w + x;
        return elevation.isRampAt(i) ? elevation.eyeTier(x, y) + elevation.rampRiseAt(i) : elevation.eyeTier(x, y);
    }
}
