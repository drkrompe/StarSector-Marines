package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground far below a map's void cells, and the playing surface's walls going down to it (the city drop
 * void style, `voids.md`). Drawn first in the ground layer, so the ground tiles cover it everywhere but the
 * void cells, which paint nothing.
 *
 * <p><b>Depth.</b> The view is a pinhole camera looking straight down from a height that grows with how much
 * of the map is in view, so a point {@code d} metres below the surface stands on screen at
 * {@code h / (h + d)} of its distance from the view's centre. The ground below therefore moves slower than
 * the surface when the camera pans, and grows less when it zooms. A wall going down from a surface edge is
 * drawn storey by storey at each storey's own scale. Where a wall faces away from the camera its storeys
 * project in under the surface, whose tiles cover them, so only the walls the camera could see show.
 *
 * <p>Presentation only. It reads the map's void cells and its {@link VoidBackdrop}, never the battle.
 */
final class VoidBackdropRenderSystem implements RenderSystem {

    /** Camera height as a multiple of the view's width in cells: about a 53 degree field of view. */
    static final float EYE_HEIGHT_PER_VIEW = 1.0f;
    /** One storey of the walls, in metres: the tier rise. */
    static final float STOREY_METERS = 4f;

    /** Shadow on the ground below: a cool, dark veil, as the composite's own shadow tint. */
    static final float SHADOW_R = .04f, SHADOW_G = .06f, SHADOW_B = .12f;
    /** The railing along the surface's edge, as a fraction of a cell. */
    static final float RAIL_CELLS = .16f;
    /** Metres between the pilasters running down a wall. */
    static final float PILASTER_METERS = 8f;
    /** Shadow mask cells per map cell: fine enough that the shadow's diagonal edges do not step visibly. */
    static final int SHADOW_RES = 4;
    /** The ground system's own backing colour, laid under the surface. */
    static final float BACKING_R = 0x18 / 255f, BACKING_G = 0x22 / 255f, BACKING_B = 0x30 / 255f;

    private final BattleSprites sprites;
    private SunLight sun;
    private CellTopology runsFor;
    /** Each edge run: {@code {x0, y0, x1, y1, normalX, normalY}} in cell coordinates along a cell boundary. */
    private final List<float[]> runs = new ArrayList<>();
    /** Everything not void, as rectangles {@code {x0, y0, x1, y1}} in cells. */
    private final List<float[]> surface = new ArrayList<>();
    private String shadowKey;
    /** The structure's shadow on the ground below, as rectangles {@code {x0, y0, x1, y1}} in cells. */
    private final List<float[]> shadow = new ArrayList<>();

    VoidBackdropRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    /** The scene's sun: the structure casts on the ground below, and its walls are lit, by it. */
    void useSun(SunLight sun) {
        this.sun = sun;
    }

    @Override public RenderLayer layer() { return RenderLayer.GROUND; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        CellTopology topology = ctx.sim.getTopology();
        VoidBackdrop backdrop = topology.voidBackdrop();
        if (backdrop == null) return;
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        if (cellPx <= 0f) return;
        float eye = cam.vpW() / cellPx * EYE_HEIGHT_PER_VIEW;
        float cx = cam.cellToScreenX(cam.panCellX());
        float cy = cam.cellToScreenY(cam.panCellY());

        drawGround(out, backdrop, cam, cellPx, eye, cx, cy, ctx.alphaMult);
        if (runsFor != topology) findRuns(topology);
        drawShadow(out, topology, backdrop, cam, eye, cx, cy, ctx.alphaMult);
        // Walls far from the camera's point first, so a nearer face covers one behind it at a re-entrant corner.
        float px = cam.panCellX(), py = cam.panCellY();
        List<float[]> order = new ArrayList<>(runs);
        order.sort((a, b) -> Float.compare(planDistance(b, px, py), planDistance(a, px, py)));
        for (float[] run : order) drawWall(out, backdrop, cam, eye, cx, cy, run, ctx.alphaMult);
        for (float[] run : runs) drawRail(out, cam, run, ctx.alphaMult);
        // The surface's own backing, which the ground system leaves off on a map with a backdrop: wall art is
        // not opaque everywhere, and through it the walls going down under the surface would show.
        for (float[] r : surface) {
            rect(out, cam, r[0], r[1], r[2], r[3], BACKING_R, BACKING_G, BACKING_B, ctx.alphaMult);
        }
    }

    /**
     * The structure's shadow on the ground below: everything not void, swept from its foot straight below to
     * where the sun lays its top, so the shadow is the whole tower's and not only its roof's.
     */
    private void drawShadow(DrawList out, CellTopology topology, VoidBackdrop backdrop, BattleCamera cam,
                            float eye, float cx, float cy, float alpha) {
        if (sun == null || !sun.casts()) return;
        float depth = backdrop.depthMeters();
        String key = System.identityHashCode(topology) + "/" + depth + "/" + sun.azimuthDegrees() + "/"
                + sun.elevationDegrees();
        if (!key.equals(shadowKey)) {
            shadowKey = key;
            castShadow(topology, depth);
        }
        float s = eye / (eye + depth);
        float a = sun.shadowStrength() * .6f * alpha;
        for (float[] r : shadow) {
            float ax = cx + (cam.cellToScreenX(r[0]) - cx) * s, bx = cx + (cam.cellToScreenX(r[2]) - cx) * s;
            float ay = cy + (cam.cellToScreenY(r[1]) - cy) * s, by = cy + (cam.cellToScreenY(r[3]) - cy) * s;
            out.addSolidRect(RenderLayer.GROUND, Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx),
                    Math.max(ay, by), SHADOW_R, SHADOW_G, SHADOW_B, a);
        }
    }

    /** Sweeps the structure's footprint from straight below to the sun's reach, then merges it into rectangles. */
    private void castShadow(CellTopology topology, float depth) {
        shadow.clear();
        int w = topology.getWidth(), h = topology.getHeight();
        int res = SHADOW_RES;
        float reach = sun.reachCells(depth) * res;
        float vx = -sun.dirX() * reach, vy = -sun.dirY() * reach;
        int ox = (int) Math.floor(Math.min(0f, vx)) - 1, oy = (int) Math.floor(Math.min(0f, vy)) - 1;
        int mw = w * res + (int) Math.ceil(Math.abs(vx)) + 2, mh = h * res + (int) Math.ceil(Math.abs(vy)) + 2;
        boolean[] mask = new boolean[mw * mh];
        // Stamp the footprint at every step of the sweep, one mask cell apart.
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(vx), Math.abs(vy))));
        for (int k = 0; k <= steps; k++) {
            int dx = Math.round(vx * k / steps) - ox, dy = Math.round(vy * k / steps) - oy;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (isVoid(topology, x, y)) continue;
                    for (int sy = 0; sy < res; sy++) {
                        int my = y * res + sy + dy;
                        if (my < 0 || my >= mh) continue;
                        int base = my * mw + x * res + dx;
                        for (int sx = 0; sx < res; sx++) mask[base + sx] = true;
                    }
                }
            }
        }
        mergeRects(mask, mw, mh, res, ox, oy, shadow);
    }

    /**
     * Merges a mask into rectangles, in cells: row spans, each grown downward while the rows below carry the
     * same span. {@code res} mask cells make a cell and {@code (ox, oy)} is the mask's origin in mask cells.
     */
    private static void mergeRects(boolean[] mask, int mw, int mh, int res, int ox, int oy, List<float[]> into) {
        boolean[] used = new boolean[mw * mh];
        for (int y = 0; y < mh; y++) {
            int x = 0;
            while (x < mw) {
                if (!mask[y * mw + x] || used[y * mw + x]) { x++; continue; }
                int x1 = x;
                while (x1 < mw && mask[y * mw + x1] && !used[y * mw + x1]) x1++;
                int y1 = y + 1;
                while (y1 < mh && sameSpan(mask, used, mw, y1, x, x1)) y1++;
                for (int yy = y; yy < y1; yy++) for (int xx = x; xx < x1; xx++) used[yy * mw + xx] = true;
                into.add(new float[]{(x + ox) / (float) res, (y + oy) / (float) res,
                        (x1 + ox) / (float) res, (y1 + oy) / (float) res});
                x = x1;
            }
        }
    }

    private static boolean sameSpan(boolean[] mask, boolean[] used, int mw, int y, int x0, int x1) {
        if (x0 > 0 && mask[y * mw + x0 - 1] && !used[y * mw + x0 - 1]) return false;
        if (x1 < mw && mask[y * mw + x1] && !used[y * mw + x1]) return false;
        for (int x = x0; x < x1; x++) if (!mask[y * mw + x] || used[y * mw + x]) return false;
        return true;
    }

    /** A railing along the surface's edge, on the void side: a lit rail over its own dark shadow line. */
    private void drawRail(DrawList out, BattleCamera cam, float[] run, float alpha) {
        boolean horizontal = run[1] == run[3];
        float nx = run[4], ny = run[5];
        float ax = run[0], ay = run[1], bx = run[2], by = run[3];
        float r0x0, r0y0, r0x1, r0y1;
        if (horizontal) {
            r0x0 = ax; r0x1 = bx; r0y0 = ay; r0y1 = ay + ny * RAIL_CELLS;
        } else {
            r0y0 = ay; r0y1 = by; r0x0 = ax; r0x1 = ax + nx * RAIL_CELLS;
        }
        rect(out, cam, r0x0, r0y0, r0x1, r0y1, .70f, .72f, .74f, alpha);
        float sx0 = horizontal ? r0x0 : r0x1, sx1 = horizontal ? r0x1 : r0x1 + nx * RAIL_CELLS * .6f;
        float sy0 = horizontal ? r0y1 : r0y0, sy1 = horizontal ? r0y1 + ny * RAIL_CELLS * .6f : r0y1;
        rect(out, cam, sx0, sy0, sx1, sy1, .05f, .06f, .09f, .55f * alpha);
    }

    private static void rect(DrawList out, BattleCamera cam, float x0, float y0, float x1, float y1,
                             float r, float g, float b, float a) {
        float ax = cam.cellToScreenX(x0), bx = cam.cellToScreenX(x1);
        float ay = cam.cellToScreenY(y0), by = cam.cellToScreenY(y1);
        out.addSolidRect(RenderLayer.GROUND, Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx),
                Math.max(ay, by), r, g, b, a);
    }

    private void drawGround(DrawList out, VoidBackdrop backdrop, BattleCamera cam, float cellPx, float eye,
                            float cx, float cy, float alpha) {
        ShuttleSpriteCache image = sprites.spriteByPath(backdrop.texturePath());
        if (image == null) return;
        float s = eye / (eye + backdrop.depthMeters());
        float tile = backdrop.tileMeters();
        float tilePx = tile * cellPx * s;
        if (tilePx < 1f) return;
        // The ground below that the view holds, in cells, about the camera's own point.
        float halfW = cam.vpW() / 2f / (cellPx * s) + tile;
        float halfH = cam.vpH() / 2f / (cellPx * s) + tile;
        int i0 = (int) Math.floor((cam.panCellX() - halfW) / tile);
        int i1 = (int) Math.ceil((cam.panCellX() + halfW) / tile);
        int j0 = (int) Math.floor((cam.panCellY() - halfH) / tile);
        int j1 = (int) Math.ceil((cam.panCellY() + halfH) / tile);
        // Tiles overlap by a pixel so no seam of the clear colour shows between them, and each samples two
        // pixels in from its image's edge so the filter never reads the texture's border.
        float size = tilePx + 1f;
        int pw = image.pxW, ph = image.pxH;
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                float sx = cx + (cam.cellToScreenX((i + .5f) * tile) - cx) * s;
                float sy = cy + (cam.cellToScreenY((j + .5f) * tile) - cy) * s;
                if (pw > 4 && ph > 4) {
                    out.addImageRegion(RenderLayer.GROUND, image.sprite, pw, ph, 2, 2, pw - 4, ph - 4,
                            sx, sy, size, size, 0f, 1f, 1f, 1f, alpha);
                } else {
                    out.addSprite(RenderLayer.GROUND, image.sprite, sx, sy, size, size, 0f, 1f, 1f, 1f, alpha);
                }
            }
        }
    }

    private void drawWall(DrawList out, VoidBackdrop backdrop, BattleCamera cam, float eye, float cx, float cy,
                          float[] run, float alpha) {
        float depth = backdrop.depthMeters();
        int storeys = Math.max(1, Math.round(depth / STOREY_METERS));
        float x0 = cam.cellToScreenX(run[0]), y0 = cam.cellToScreenY(run[1]);
        float x1 = cam.cellToScreenX(run[2]), y1 = cam.cellToScreenY(run[3]);
        boolean horizontal = run[1] == run[3];
        // A face turned toward the sun is lit; one turned away stands in its own shade.
        float toSun = sun == null ? 0f : run[4] * sun.dirX() + run[5] * sun.dirY();
        float facing = .78f + .22f * toSun;
        float baseR = .46f * facing, baseG = .47f * facing, baseB = .50f * facing;
        for (int k = 0; k < storeys; k++) {
            float d0 = depth * k / storeys, d1 = depth * (k + 1) / storeys;
            float s0 = eye / (eye + d0), s1 = eye / (eye + d1), sm = (s0 + s1) / 2f;
            float t = (k + .5f) / storeys;
            float haze = (float) Math.pow(t, .55f);
            float r = baseR + (backdrop.hazeR() - baseR) * haze;
            float g = baseG + (backdrop.hazeG() - baseG) * haze;
            float b = baseB + (backdrop.hazeB() - baseB) * haze;
            float ax, ay, bx, by;
            if (horizontal) {
                ax = cx + (x0 - cx) * sm;
                bx = cx + (x1 - cx) * sm;
                ay = cy + (y0 - cy) * s0;
                by = cy + (y0 - cy) * s1;
            } else {
                ay = cy + (y0 - cy) * sm;
                by = cy + (y1 - cy) * sm;
                ax = cx + (x0 - cx) * s0;
                bx = cx + (x0 - cx) * s1;
            }
            float minX = Math.min(ax, bx), maxX = Math.max(ax, bx);
            float minY = Math.min(ay, by), maxY = Math.max(ay, by);
            // Overlap the next storey by half a pixel so the bands join.
            if (horizontal) { minY -= .5f; maxY += .5f; } else { minX -= .5f; maxX += .5f; }
            out.addSolidRect(RenderLayer.GROUND, minX, minY, maxX, maxY, r, g, b, alpha);
            float band = horizontal ? maxY - minY : maxX - minX;
            if (band >= 3f) {
                // A strip of windows across the middle of the storey, lit or dark glass, fading into the haze.
                float w0 = .30f, w1 = .70f;
                float wr = backdrop.lit() ? .98f : .10f, wg = backdrop.lit() ? .80f : .13f;
                float wb = backdrop.lit() ? .52f : .20f;
                float wa = (backdrop.lit() ? .65f : .55f) * (1f - haze) * alpha;
                if (horizontal) {
                    float a0 = minY + band * w0, a1 = minY + band * w1;
                    out.addSolidRect(RenderLayer.GROUND, minX, a0, maxX, a1, wr, wg, wb, wa);
                } else {
                    float a0 = minX + band * w0, a1 = minX + band * w1;
                    out.addSolidRect(RenderLayer.GROUND, a0, minY, a1, maxY, wr, wg, wb, wa);
                }
            }
        }
        // Pilasters every few metres down the whole face: lines that all run toward the view's centre, which
        // is what makes the face read as falling away rather than lying flat.
        float sBottom = eye / (eye + depth);
        float length = horizontal ? Math.abs(run[2] - run[0]) : Math.abs(run[3] - run[1]);
        int bays = Math.max(1, Math.round(length / PILASTER_METERS));
        for (int k = 0; k <= bays; k++) {
            float u = k / (float) bays;
            float px = horizontal ? x0 + (x1 - x0) * u : x0;
            float py = horizontal ? y0 : y0 + (y1 - y0) * u;
            float qx = cx + (px - cx) * sBottom, qy = cy + (py - cy) * sBottom;
            float mx = cx + (px - cx) * .5f * (1f + sBottom), my = cy + (py - cy) * .5f * (1f + sBottom);
            out.addLine(RenderLayer.GROUND, px, py, mx, my, 2f, baseR * .55f, baseG * .55f, baseB * .6f,
                    .8f * alpha);
            out.addLine(RenderLayer.GROUND, mx, my, qx, qy, 1.5f, baseR * .55f, baseG * .55f, baseB * .6f,
                    .35f * alpha);
        }
    }

    /** Collects every straight run of boundary between surface and void, once per map: the map never changes. */
    private void findRuns(CellTopology topology) {
        runs.clear();
        surface.clear();
        runsFor = topology;
        int w = topology.getWidth(), h = topology.getHeight();
        boolean[] solid = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) solid[y * w + x] = !isVoid(topology, x, y);
        mergeRects(solid, w, h, 1, 0, 0, surface);
        // Horizontal boundaries: between (x, y-1) and (x, y).
        for (int y = 1; y < h; y++) {
            int start = -1;
            int normal = 0;
            for (int x = 0; x <= w; x++) {
                int n = 0;
                if (x < w) {
                    boolean above = isVoid(topology, x, y - 1), below = isVoid(topology, x, y);
                    if (above != below) n = below ? 1 : -1;
                }
                if (n != normal) {
                    if (normal != 0) runs.add(new float[]{start, y, x, y, 0, normal});
                    start = x;
                    normal = n;
                }
            }
        }
        // Vertical boundaries: between (x-1, y) and (x, y).
        for (int x = 1; x < w; x++) {
            int start = -1;
            int normal = 0;
            for (int y = 0; y <= h; y++) {
                int n = 0;
                if (y < h) {
                    boolean left = isVoid(topology, x - 1, y), right = isVoid(topology, x, y);
                    if (left != right) n = right ? 1 : -1;
                }
                if (n != normal) {
                    if (normal != 0) runs.add(new float[]{x, start, x, y, normal, 0});
                    start = y;
                    normal = n;
                }
            }
        }
    }

    /** How far a wall's run lies from the camera's point, seen from above. */
    private static float planDistance(float[] run, float px, float py) {
        float qx = Math.max(Math.min(run[0], run[2]), Math.min(px, Math.max(run[0], run[2])));
        float qy = Math.max(Math.min(run[1], run[3]), Math.min(py, Math.max(run[1], run[3])));
        return (qx - px) * (qx - px) + (qy - py) * (qy - py);
    }

    private static boolean isVoid(CellTopology topology, int x, int y) {
        return topology.getGroundKind(x, y) == GroundKind.VOID;
    }
}
