package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The towers a map's playing surface is the roof of, stepping down in terraces to the ground far below, the
 * walkways spanning the drop between them, the neighbouring towers standing in it, and that ground, seen through
 * the map's void cells (the city drop void style, `voids.md`). Drawn first in the ground layer, so the ground
 * tiles cover it everywhere but the void cells, which paint nothing.
 *
 * <p><b>Towers and walkways.</b> The surface is split by its shape: whatever is at least {@link #SPAN_CELLS}
 * wide stands on a tower of its own, one to each connected piece, and the narrower ground left between them is
 * a walkway, a girder span a few metres deep with the drop under it. Nothing in the map marks which is which.
 *
 * <p><b>Depth.</b> The view is a pinhole camera looking straight down from a fixed height above the surface
 * ({@link VoidBackdrop#eyeMeters}), so a point {@code d} metres below the surface stands on screen at
 * {@code h / (h + d)} of its distance from the view's centre. The ground below moves slower than the surface
 * when the camera pans; zooming is a lens, not a dolly, so the drop keeps its proportions at every zoom rather
 * than plunging as the view closes in.
 *
 * <p><b>Painter's order.</b> Everything is drawn from the bottom up in slices of depth, across every structure
 * at once: the ground, the shadows on it, then each wall in bands of {@link #BAND_METERS}, each roof, each
 * freight bay and each craft by its depth, deepest first, and at one depth the faces far from the camera first.
 * A roof covers its own walls' faces turned away from the camera, and a nearer tower's upper walls cover a deeper
 * one's roof, while its lower walls go behind it: ordering whole towers or whole levels by their roofs would let
 * a tower's walls cut through a neighbour standing in front of them. The surface's own tiles, drawn after, close
 * the top. A level is the surface's footprint grown by its terrace's outset, its walls
 * running from its roof down to the next terrace or the ground. Every tower's levels, the walkways' and the
 * neighbouring towers' are drawn in one order, deepest roof first.
 *
 * <p>Presentation only. It reads the map's void cells and its {@link VoidBackdrop}, never the battle.
 */
final class VoidBackdropRenderSystem implements RenderSystem {

    /** Metres of wall drawn as one band, at one scale: two storeys. Divides any facade tile used. */
    static final float BAND_METERS = 8f;
    /** Shadow on the ground below: a cool, dark veil, as the composite's own shadow tint. */
    static final float SHADOW_R = .04f, SHADOW_G = .05f, SHADOW_B = .08f;
    /** The railing along the surface's edge, as a fraction of a cell. */
    static final float RAIL_CELLS = .16f;
    /** Shadow mask cells per map cell. */
    static final int SHADOW_RES = 4;
    /** The ground system's own backing colour, laid under the surface. */
    static final float BACKING_R = 0x18 / 255f, BACKING_G = 0x22 / 255f, BACKING_B = 0x30 / 255f;
    /** Freight bay mouths: spacing along a wall, width and height in metres, and the lane each feeds. */
    static final float MOUTH_SPACING = 64f, MOUTH_W = 28f, MOUTH_H = 16f, LANE_W = 24f;
    /** The least width of terrace a freight bay opens onto, in metres. */
    static final float BAY_REACH = 8f;
    /** A roof below the surface is veiled this dark at least, so no roof in the drop reads as walkable. */
    static final float ROOF_VEIL = .28f;
    /** Craft flying in and out of a bay: how far out they come from, how fast, how many to a bay. */
    static final float APPROACH_METERS = 170f, BAY_SPEED = 16f;
    static final int BAY_CRAFT = 2;
    /** Skyways crossing the drop: their length, the spacing of craft along them, and their speed. */
    static final float SKYWAY_METERS = 1600f, SKYWAY_SPACING = 70f, SKYWAY_SPEED = 28f;
    /** How wide ground must be to stand on a tower of its own; anything narrower is a walkway. */
    static final int SPAN_CELLS = 7;
    /** How deep a walkway's girders run under its deck, in metres. */
    static final float SPAN_METERS = 2.5f;
    /** The least width of terrace a freighter is parked on, in metres. */
    static final float PARK_REACH = 20f;

    private final BattleSprites sprites;
    private SunLight sun;

    private CellTopology builtFor;
    private VoidBackdrop builtWith;
    /** Every structure: each tower's levels, its surface or roof first, then its terraces; the walkways. */
    private final List<List<Level>> structures = new ArrayList<>();
    /** Every level of every structure, deepest roof first: the order they are drawn in. */
    private final List<Level> levels = new ArrayList<>();
    /** Everything not void, as rectangles {@code {x0, y0, x1, y1}} in cells. */
    private final List<float[]> surface = new ArrayList<>();
    /** The surface's edge along the void, where its railing runs. */
    private final List<float[]> rails = new ArrayList<>();
    private String shadowKey;
    private final List<float[]> shadow = new ArrayList<>();
    /** Paths craft fly: {@code {count, speed, spacing, x0, y0, z0, x1, y1, z1, ...}}, looping from first to last. */
    private final List<float[]> routes = new ArrayList<>();

    // The frame being drawn.
    private DrawList out;
    private BattleCamera cam;
    private VoidBackdrop backdrop;
    private float eye, cx, cy, alpha;

    /** One thing drawn at one depth: a band of wall, a girder side, a roof or a freight bay. */
    private record Item(float depth, int kind, Level level, float[] run, float z0, float z1, float[] mouth) {
        static final int WALL = 0, GIRDER = 1, ROOF = 2, MOUTH = 3;
    }

    /** Everything the structures draw, deepest first: the painter's order before the camera's far-first pass. */
    private final List<Item> items = new ArrayList<>();
    /** Single-quad meshes for this frame's slanted bands, reused frame to frame. */
    private final List<PolyMesh> polys = new ArrayList<>();
    private int polysUsed;

    /** A footprint and the depths its walls run between. */
    private static final class Level {
        float top, bottom, outset;
        /** Its roof is the playing surface, which the ground tiles draw. */
        boolean surface;
        /** A walkway's girders, not a tower's walls. */
        boolean span;
        /** Each edge run: {@code {x0, y0, x1, y1, normalX, normalY}} in cells along a cell boundary. */
        final List<float[]> runs = new ArrayList<>();
        /** The footprint as rectangles in cells. */
        final List<float[]> roofs = new ArrayList<>();
        /** Freight bay mouths in this level's walls: {@code {runIndex, centre along the run}}. */
        final List<float[]> mouths = new ArrayList<>();
        /** Freight lanes on this level's roof, from the bays above: rectangles in cells. */
        final List<float[]> lanes = new ArrayList<>();
        /** The walls standing on this level's roof: the runs of the level above. */
        List<float[]> footings = List.of();
        /** The shadow the levels above lay on this level's roof, for the current sun: rectangles in cells. */
        final List<float[]> shadow = new ArrayList<>();
        /** Hulls parked on this level's roof: {@code {x, y, heading in radians, craft index}}. */
        final List<float[]> parked = new ArrayList<>();
        /** Craft flying in this level's band of depth this frame: {@code {x, y, z, heading, craft, alpha}}. */
        final List<float[]> flying = new ArrayList<>();
    }

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
        this.out = out;
        this.cam = cam;
        this.backdrop = backdrop;
        this.alpha = ctx.alphaMult;
        this.polysUsed = 0;
        this.eye = backdrop.eyeMeters();
        this.cx = cam.cellToScreenX(cam.panCellX());
        this.cy = cam.cellToScreenY(cam.panCellY());
        if (builtFor != topology || builtWith != backdrop) build(topology, ctx.sim.getGrid().elevation(), backdrop);

        drawGround(cellPx);
        drawShadow();
        fly(ctx.animationTimeSeconds);
        float px = cam.panCellX(), py = cam.panCellY();
        List<float[]> flying = new ArrayList<>();
        for (Level level : levels) flying.addAll(level.flying);
        flying.sort((a, b) -> Float.compare(b[2], a[2]));
        int craft = 0;
        int i = 0;
        while (i < items.size()) {
            // One slice: every item at this depth, faces far from the camera first.
            float depth = items.get(i).depth();
            int j = i;
            while (j < items.size() && items.get(j).depth() == depth) j++;
            while (craft < flying.size() && flying.get(craft)[2] > depth) {
                float[] c = flying.get(craft++);
                drawCraft(c[0], c[1], c[2], c[3], (int) c[4], c[5]);
            }
            List<Item> slice = items.subList(i, j);
            if (slice.size() > 1) {
                slice = new ArrayList<>(slice);
                slice.sort((a, b) -> Float.compare(itemDistance(b, px, py), itemDistance(a, px, py)));
            }
            for (Item item : slice) if (onScreen(item)) draw(item);
            i = j;
        }
        while (craft < flying.size()) {
            float[] c = flying.get(craft++);
            drawCraft(c[0], c[1], c[2], c[3], (int) c[4], c[5]);
        }
        for (float[] run : rails) drawRail(run);
        // The surface's own backing, which the ground system leaves off on a map with a backdrop: wall art is
        // not opaque everywhere, and through it the structure under the surface would show.
        for (float[] r : surface) rect(r[0], r[1], r[2], r[3], 0f, BACKING_R, BACKING_G, BACKING_B, alpha);
    }

    // ----- projection -----

    private float scale(float depth) { return eye / (eye + depth); }

    private float sx(float cellX, float s) { return cx + (cam.cellToScreenX(cellX) - cx) * s; }

    private float sy(float cellY, float s) { return cy + (cam.cellToScreenY(cellY) - cy) * s; }

    /** How much of the haze stands between the eye and something {@code depth} metres down. */
    private float hazeAt(float depth) {
        return .85f * (float) Math.pow(Math.min(1f, depth / backdrop.depthMeters()), .7f);
    }

    /** A rectangle in cells at {@code depth}, filled with one colour. */
    private void rect(float x0, float y0, float x1, float y1, float depth, float r, float g, float b, float a) {
        float s = scale(depth);
        float ax = sx(x0, s), bx = sx(x1, s), ay = sy(y0, s), by = sy(y1, s);
        out.addSolidRect(RenderLayer.GROUND, Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx),
                Math.max(ay, by), r, g, b, a);
    }

    // ----- the ground below -----

    private void drawGround(float cellPx) {
        ShuttleSpriteCache image = sprites.spriteByPath(backdrop.cityTexture());
        if (image == null) return;
        float s = scale(backdrop.depthMeters());
        float tile = backdrop.cityTileMeters();
        float tilePx = tile * cellPx * s;
        if (tilePx < 1f) return;
        float halfW = cam.vpW() / 2f / (cellPx * s) + tile;
        float halfH = cam.vpH() / 2f / (cellPx * s) + tile;
        int i0 = (int) Math.floor((cam.panCellX() - halfW) / tile);
        int i1 = (int) Math.ceil((cam.panCellX() + halfW) / tile);
        int j0 = (int) Math.floor((cam.panCellY() - halfH) / tile);
        int j1 = (int) Math.ceil((cam.panCellY() + halfH) / tile);
        float size = tilePx + 1f;
        int pw = image.pxW, ph = image.pxH;
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                float tx = sx((i + .5f) * tile, s), ty = sy((j + .5f) * tile, s);
                if (pw > 4 && ph > 4) {
                    out.addImageRegion(RenderLayer.GROUND, image.sprite, pw, ph, 2, 2, pw - 4, ph - 4,
                            tx, ty, size, size, 0f, 1f, 1f, 1f, alpha);
                } else {
                    out.addSprite(RenderLayer.GROUND, image.sprite, tx, ty, size, size, 0f, 1f, 1f, 1f, alpha);
                }
            }
        }
    }

    private void drawShadow() {
        if (sun == null || !sun.casts()) return;
        String key = sun.azimuthDegrees() + "/" + sun.elevationDegrees();
        if (!key.equals(shadowKey)) {
            shadowKey = key;
            castShadow();
        }
        float a = sun.shadowStrength() * .6f * alpha;
        for (float[] r : shadow) rect(r[0], r[1], r[2], r[3], backdrop.depthMeters(), SHADOW_R, SHADOW_G, SHADOW_B, a);
    }

    /**
     * The structure's shadow on the ground below. Each level is a block from its roof down to its foot, and the
     * sun lays a point {@code d} metres above the ground {@code d / tan(elevation)} metres away from itself, so
     * a level's shadow is the hull of its footprint laid from its foot and from its roof. The levels' shadows
     * are merged into rectangles.
     */
    private void castShadow() {
        shadow.clear();
        float depth = backdrop.depthMeters();
        List<float[]> hulls = new ArrayList<>();
        for (Level level : levels) hulls.add(cast(level, depth));
        shade(hulls, null, shadow);
        // Each roof below the surface is shaded by everything standing higher: its own tower's levels above it,
        // the towers beside it and the walkways.
        for (Level level : levels) {
            level.shadow.clear();
            if (level.surface) continue;
            float[] box = bounds(level.roofs);
            List<float[]> above = new ArrayList<>();
            for (Level other : levels) {
                if (other.top >= level.top) continue;
                float[] hull = cast(other, level.top);
                if (meets(hull, box)) above.add(hull);
            }
            if (!above.isEmpty()) shade(above, level.roofs, level.shadow);
        }
    }

    /**
     * Where a level's shadow falls on ground {@code onto} metres down: the sun lays a point {@code d} metres
     * above that ground {@code d / tan(elevation)} metres away from itself, so it is the hull of the level's
     * footprint laid from its foot and from its roof. Only what stands above that ground casts on it.
     */
    private float[] cast(Level level, float onto) {
        float perMetre = 1f / sun.risePerCell();
        float dx = -sun.dirX(), dy = -sun.dirY();
        List<float[]> points = new ArrayList<>();
        for (float lift : new float[]{onto - Math.min(level.bottom, onto), onto - level.top}) {
            float ox = dx * lift * perMetre, oy = dy * lift * perMetre;
            for (float[] r : level.roofs) {
                points.add(new float[]{r[0] + ox, r[1] + oy});
                points.add(new float[]{r[2] + ox, r[1] + oy});
                points.add(new float[]{r[0] + ox, r[3] + oy});
                points.add(new float[]{r[2] + ox, r[3] + oy});
            }
        }
        return hull(points);
    }

    /** The rectangle {@code {x0, y0, x1, y1}} round some rectangles. */
    private static float[] bounds(List<float[]> rects) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] r : rects) {
            b[0] = Math.min(b[0], r[0]); b[1] = Math.min(b[1], r[1]);
            b[2] = Math.max(b[2], r[2]); b[3] = Math.max(b[3], r[3]);
        }
        return b;
    }

    /** Whether a hull's bounding box meets a rectangle. */
    private static boolean meets(float[] hull, float[] box) {
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int k = 0; k < hull.length; k += 2) {
            x0 = Math.min(x0, hull[k]); x1 = Math.max(x1, hull[k]);
            y0 = Math.min(y0, hull[k + 1]); y1 = Math.max(y1, hull[k + 1]);
        }
        return x1 >= box[0] && x0 <= box[2] && y1 >= box[1] && y0 <= box[3];
    }

    /** The union of some convex hulls, kept only inside {@code clip}'s rectangles if given, as rectangles. */
    private static void shade(List<float[]> hulls, List<float[]> clip, List<float[]> into) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] hull : hulls) {
            for (int k = 0; k < hull.length; k += 2) {
                minX = Math.min(minX, hull[k]); maxX = Math.max(maxX, hull[k]);
                minY = Math.min(minY, hull[k + 1]); maxY = Math.max(maxY, hull[k + 1]);
            }
        }
        if (clip != null) {
            float[] c = bounds(clip);
            minX = Math.max(minX, c[0]); minY = Math.max(minY, c[1]);
            maxX = Math.min(maxX, c[2]); maxY = Math.min(maxY, c[3]);
        }
        if (minX > maxX || minY > maxY) return;
        int res = SHADOW_RES;
        int ox = (int) Math.floor(minX * res) - 1, oy = (int) Math.floor(minY * res) - 1;
        int mw = (int) Math.ceil(maxX * res) - ox + 2, mh = (int) Math.ceil(maxY * res) - oy + 2;
        boolean[] mask = new boolean[mw * mh];
        for (float[] hull : hulls) {
            int n = hull.length / 2;
            for (int my = 0; my < mh; my++) {
                float y = (my + oy + .5f) / res;
                float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                for (int k = 0; k < n; k++) {
                    float ax = hull[2 * k], ay = hull[2 * k + 1];
                    float bx = hull[2 * ((k + 1) % n)], by = hull[2 * ((k + 1) % n) + 1];
                    if ((ay <= y && by > y) || (by <= y && ay > y)) {
                        float x = ax + (y - ay) / (by - ay) * (bx - ax);
                        lo = Math.min(lo, x);
                        hi = Math.max(hi, x);
                    }
                }
                if (lo > hi) continue;
                int x0 = Math.max(0, Math.round(lo * res) - ox), x1 = Math.min(mw, Math.round(hi * res) - ox);
                if (x1 > x0) Arrays.fill(mask, my * mw + x0, my * mw + x1, true);
            }
        }
        if (clip != null) {
            boolean[] inside = new boolean[mw * mh];
            for (float[] r : clip) {
                int x0 = Math.max(0, Math.round(r[0] * res) - ox), x1 = Math.min(mw, Math.round(r[2] * res) - ox);
                int y0 = Math.max(0, Math.round(r[1] * res) - oy), y1 = Math.min(mh, Math.round(r[3] * res) - oy);
                for (int y = y0; y < y1; y++) if (x1 > x0) Arrays.fill(inside, y * mw + x0, y * mw + x1, true);
            }
            for (int k = 0; k < mask.length; k++) mask[k] &= inside[k];
        }
        mergeRects(mask, mw, mh, res, ox, oy, into);
    }

    /** The convex hull of some points, anticlockwise, as {@code {x0, y0, x1, y1, ...}}. */
    private static float[] hull(List<float[]> points) {
        points.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
        int n = points.size();
        float[][] h = new float[2 * n][];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], points.get(i)) <= 0) k--;
            h[k++] = points.get(i);
        }
        for (int i = n - 2, lower = k + 1; i >= 0; i--) {
            while (k >= lower && cross(h[k - 2], h[k - 1], points.get(i)) <= 0) k--;
            h[k++] = points.get(i);
        }
        float[] flat = new float[Math.max(0, k - 1) * 2];
        for (int i = 0; i < k - 1; i++) {
            flat[2 * i] = h[i][0];
            flat[2 * i + 1] = h[i][1];
        }
        return flat;
    }

    private static float cross(float[] o, float[] a, float[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    // ----- the structure -----

    private void draw(Item item) {
        switch (item.kind()) {
            case Item.WALL -> drawWallBand(item.run(), item.z0(), item.z1());
            case Item.GIRDER -> drawGirder(item.run());
            case Item.ROOF -> drawRoof(item.level());
            default -> {
                float[] run = item.run();
                boolean horizontal = run[1] == run[3];
                drawMouth(item.level(), horizontal, horizontal ? run[1] : run[0], item.mouth()[1]);
            }
        }
    }

    /** Whether any of an item can be on screen: a wall band or girder by its run's ends at both its depths. */
    private boolean onScreen(Item item) {
        float[] run = item.run();
        if (run == null || item.kind() == Item.MOUTH) return true;
        float s0 = scale(item.z0()), s1 = scale(item.z1());
        float x0 = Math.min(Math.min(sx(run[0], s0), sx(run[2], s0)), Math.min(sx(run[0], s1), sx(run[2], s1)));
        float x1 = Math.max(Math.max(sx(run[0], s0), sx(run[2], s0)), Math.max(sx(run[0], s1), sx(run[2], s1)));
        float y0 = Math.min(Math.min(sy(run[1], s0), sy(run[3], s0)), Math.min(sy(run[1], s1), sy(run[3], s1)));
        float y1 = Math.max(Math.max(sy(run[1], s0), sy(run[3], s0)), Math.max(sy(run[1], s1), sy(run[3], s1)));
        return x1 >= cam.vpX() && x0 <= cam.vpX() + cam.vpW() && y1 >= cam.vpY() && y0 <= cam.vpY() + cam.vpH();
    }

    /** How far an item lies from the camera's point, seen from above: a roof counts as at the point. */
    private static float itemDistance(Item item, float px, float py) {
        return item.run() == null ? 0f : planDistance(item.run(), px, py);
    }

    /** The painter's list: every wall band, girder side, roof and freight bay, deepest first. */
    private void plotItems() {
        items.clear();
        for (Level level : levels) {
            for (float[] run : level.runs) {
                if (level.span) {
                    items.add(new Item(SPAN_METERS / 2f, Item.GIRDER, level, run, 0f, SPAN_METERS, null));
                    continue;
                }
                float z = level.top;
                while (z < level.bottom - .01f) {
                    float z1 = Math.min(level.bottom, (float) (Math.floor(z / BAND_METERS) + 1) * BAND_METERS);
                    items.add(new Item((z + z1) / 2f, Item.WALL, level, run, z, z1, null));
                    z = z1;
                }
            }
            // A bay is drawn over the wall it opens in, just shallower than the band at its middle.
            for (float[] mouth : level.mouths) {
                float zb = level.bottom - 1f, mid = zb - MOUTH_H / 2f;
                float band = (float) (Math.floor(mid / BAND_METERS) + .5f) * BAND_METERS;
                items.add(new Item(band - .01f, Item.MOUTH, level, level.runs.get((int) mouth[0]), 0f, 0f, mouth));
            }
            if (!level.surface) items.add(new Item(level.top, Item.ROOF, level, null, 0f, 0f, null));
        }
        // Deepest first; at one depth a roof after the walls standing to it, so it covers its own far faces.
        items.sort((a, b) -> a.depth() != b.depth() ? Float.compare(b.depth(), a.depth())
                : Integer.compare(a.kind() == Item.ROOF ? 1 : 0, b.kind() == Item.ROOF ? 1 : 0));
    }

    /** One band of a face, between two depths, in tiles of facade under the haze at its depth. */
    private void drawWallBand(float[] run, float z, float z1) {
        boolean horizontal = run[1] == run[3];
        float a = horizontal ? run[0] : run[1], b = horizontal ? run[2] : run[3];
        float line = horizontal ? run[1] : run[0];
        float toSun = sun == null ? 0f : run[4] * sun.dirX() + run[5] * sun.dirY();
        float lit = .82f + .18f * toSun;
        ShuttleSpriteCache facade = backdrop.facadeTexture() == null ? null
                : sprites.spriteByPath(backdrop.facadeTexture());
        float tile = backdrop.facadeTileMeters();
        {
            float s0 = scale(z), s1 = scale(z1), sm = scale((z + z1) / 2f);
            // An opaque underlay in the band's true shape: the facade art is not opaque everywhere, and its
            // tiles are cut to fit inside the band, so the underlay alone draws the band's slanted ends.
            float under = .19f * lit;
            band(horizontal, line, a, b, s0, s1, sm, under, under * .98f, under * .96f, alpha);
            if (facade != null && facade.pxW > 0) {
                float u = a;
                while (u < b - .01f) {
                    float u1 = Math.min(b, (float) (Math.floor(u / tile) + 1) * tile);
                    facadeQuad(facade, horizontal, line, u, u1, z, z1, s0, s1, sm, lit);
                    u = u1;
                }
            } else {
                float g = .40f * lit;
                band(horizontal, line, a, b, s0, s1, sm, g, g * 1.02f, g * 1.08f, alpha);
            }
            float haze = hazeAt((z + z1) / 2f);
            band(horizontal, line, a, b, s0, s1, sm, backdrop.hazeR(), backdrop.hazeG(), backdrop.hazeB(),
                    haze * alpha);
        }
    }

    /**
     * One side of a walkway: the edge of its deck plate over the girders under it, braced every few metres, lit
     * by the sun on the side turned to it.
     */
    private void drawGirder(float[] run) {
        boolean horizontal = run[1] == run[3];
        float a = horizontal ? run[0] : run[1], b = horizontal ? run[2] : run[3];
        float line = horizontal ? run[1] : run[0];
        float toSun = sun == null ? 0f : run[4] * sun.dirX() + run[5] * sun.dirY();
        float lit = .75f + .25f * toSun;
        float s0 = scale(0f), sd = scale(.5f), s1 = scale(SPAN_METERS), sm = scale(SPAN_METERS / 2f);
        band(horizontal, line, a, b, sd, s1, sm, .13f * lit, .14f * lit, .16f * lit, alpha);
        for (float u = (float) Math.ceil(a / 3f) * 3f; u < b; u += 3f) {
            float u0 = Math.max(a, u - .25f), u1 = Math.min(b, u + .25f);
            band(horizontal, line, u0, u1, sd, s1, sm, .30f * lit, .30f * lit, .31f * lit, alpha);
        }
        band(horizontal, line, a, b, s0, sd, scale(.25f), .46f * lit, .45f * lit, .42f * lit, alpha);
    }

    /** One tile-aligned piece of facade, between two depths and two points along its run. */
    private void facadeQuad(ShuttleSpriteCache facade, boolean horizontal, float line, float u0, float u1,
                            float z0, float z1, float s0, float s1, float sm, float lit) {
        float tile = backdrop.facadeTileMeters();
        int pw = facade.pxW, ph = facade.pxH;
        float fu = (float) (u0 / tile - Math.floor(u0 / tile));
        float fz = (float) (z0 / tile - Math.floor(z0 / tile));
        int srcX = Math.round(fu * pw), srcW = Math.max(1, Math.round((u1 - u0) / tile * pw));
        int srcY = Math.round(fz * ph), srcH = Math.max(1, Math.round((z1 - z0) / tile * ph));
        srcW = Math.min(srcW, pw - srcX);
        srcH = Math.min(srcH, ph - srcY);
        if (srcW <= 0 || srcH <= 0) return;
        float qx, qy, w, h, angle;
        // Along the wall the piece keeps to what the band covers at both its depths, so it never overhangs the
        // band's slanted ends: the underlay draws those.
        if (horizontal) {
            float x0 = Math.max(Math.min(sx(u0, s0), sx(u1, s0)), Math.min(sx(u0, s1), sx(u1, s1)));
            float x1 = Math.min(Math.max(sx(u0, s0), sx(u1, s0)), Math.max(sx(u0, s1), sx(u1, s1)));
            if (x1 <= x0) return;
            float ya = sy(line, s0), yb = sy(line, s1);
            qx = (x0 + x1) / 2f; qy = (ya + yb) / 2f;
            w = Math.abs(x1 - x0) + .5f; h = Math.abs(yb - ya) + .5f;
            // The facade's top is the shallow end: turn it over where the deep end lies above.
            angle = yb < ya ? 180f : 0f;
        } else {
            float y0 = Math.max(Math.min(sy(u0, s0), sy(u1, s0)), Math.min(sy(u0, s1), sy(u1, s1)));
            float y1 = Math.min(Math.max(sy(u0, s0), sy(u1, s0)), Math.max(sy(u0, s1), sy(u1, s1)));
            if (y1 <= y0) return;
            float xa = sx(line, s0), xb = sx(line, s1);
            qx = (xa + xb) / 2f; qy = (y0 + y1) / 2f;
            w = Math.abs(y1 - y0) + .5f; h = Math.abs(xb - xa) + .5f;
            angle = xb > xa ? 90f : 270f;
        }
        out.addImageRegion(RenderLayer.GROUND, facade.sprite, pw, ph, srcX, srcY, srcW, srcH,
                qx, qy, w, h, angle, lit, lit, lit, alpha);
    }

    /**
     * A band of a face between two depths, filled with one colour, in its true shape: the quad between the run's
     * ends at the shallow depth and at the deep one, so a wall converging on the view's centre has straight slanted
     * ends rather than steps.
     */
    private void band(boolean horizontal, float line, float a, float b, float s0, float s1, float sm,
                      float r, float g, float bl, float al) {
        PolyMesh mesh = poly();
        if (horizontal) {
            float y0 = sy(line, s0), y1 = sy(line, s1);
            mesh.appendQuad(sx(a, s0), y0, sx(b, s0), y0, sx(b, s1), y1, sx(a, s1), y1, r, g, bl, al);
        } else {
            float x0 = sx(line, s0), x1 = sx(line, s1);
            mesh.appendQuad(x0, sy(a, s0), x0, sy(b, s0), x1, sy(b, s1), x1, sy(a, s1), r, g, bl, al);
        }
        out.addPoly(RenderLayer.GROUND, mesh);
    }

    /** A cleared mesh from this frame's pool, queued once: each keeps its own place in the painter's order. */
    private PolyMesh poly() {
        if (polysUsed == polys.size()) polys.add(new PolyMesh(1));
        PolyMesh mesh = polys.get(polysUsed++);
        mesh.reset();
        return mesh;
    }

    /** A freight bay: a dark mouth in the foot of a wall, lit along its sill, a beacon at each upper corner. */
    private void drawMouth(Level level, boolean horizontal, float line, float centre) {
        float zb = level.bottom - 1f, zt = zb - MOUTH_H;
        float haze = hazeAt(zb);
        float dim = 1f - haze * .7f;
        mouthRect(horizontal, line, centre - MOUTH_W / 2f, centre + MOUTH_W / 2f, zt, zb,
                .035f, .03f, .03f, alpha * dim);
        mouthRect(horizontal, line, centre - MOUTH_W / 2f, centre + MOUTH_W / 2f, zb - 1.6f, zb,
                1f, .70f, .32f, alpha * .95f * dim);
        mouthRect(horizontal, line, centre - MOUTH_W / 2f, centre + MOUTH_W / 2f, zt - 1f, zt,
                .85f, .66f, .15f, alpha * .8f * dim);
        for (float side : new float[]{-1f, 1f}) {
            float u = centre + side * (MOUTH_W / 2f + 1.2f);
            mouthRect(horizontal, line, u - .7f, u + .7f, zt - .7f, zt + .7f, 1f, .15f, .1f, alpha * dim);
        }
    }

    private void mouthRect(boolean horizontal, float line, float u0, float u1, float z0, float z1,
                           float r, float g, float b, float a) {
        float s0 = scale(z0), s1 = scale(z1), sm = scale((z0 + z1) / 2f);
        float ax, ay, bx, by;
        if (horizontal) {
            ax = sx(u0, sm); bx = sx(u1, sm); ay = sy(line, s0); by = sy(line, s1);
        } else {
            ay = sy(u0, sm); by = sy(u1, sm); ax = sx(line, s0); bx = sx(line, s1);
        }
        out.addSolidRect(RenderLayer.GROUND, Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx),
                Math.max(ay, by), r, g, b, a);
    }

    /** A terrace's roof, tiled with the roof image, its freight lanes, its edge, and the haze over it. */
    private void drawRoof(Level level) {
        float depth = level.top;
        float s = scale(depth);
        ShuttleSpriteCache roof = backdrop.roofTexture() == null ? null : sprites.spriteByPath(backdrop.roofTexture());
        float tile = backdrop.roofTileMeters();
        float vx0 = cam.vpX(), vx1 = cam.vpX() + cam.vpW(), vy0 = cam.vpY(), vy1 = cam.vpY() + cam.vpH();
        for (float[] r : level.roofs) {
            if (roof == null || roof.pxW <= 0) {
                rect(r[0], r[1], r[2], r[3], depth, .30f, .29f, .28f, alpha);
                continue;
            }
            int pw = roof.pxW, ph = roof.pxH;
            for (int ty = (int) Math.floor(r[1] / tile); ty * tile < r[3]; ty++) {
                for (int tx = (int) Math.floor(r[0] / tile); tx * tile < r[2]; tx++) {
                    float ux0 = Math.max(r[0], tx * tile), ux1 = Math.min(r[2], (tx + 1) * tile);
                    float uy0 = Math.max(r[1], ty * tile), uy1 = Math.min(r[3], (ty + 1) * tile);
                    if (ux1 <= ux0 || uy1 <= uy0) continue;
                    float x0 = sx(ux0, s), x1 = sx(ux1, s), y0 = sy(uy0, s), y1 = sy(uy1, s);
                    if (Math.max(x0, x1) < vx0 || Math.min(x0, x1) > vx1
                            || Math.max(y0, y1) < vy0 || Math.min(y0, y1) > vy1) continue;
                    int srcX = Math.round((ux0 - tx * tile) / tile * pw);
                    int srcY = Math.round((uy0 - ty * tile) / tile * ph);
                    int srcW = Math.max(1, Math.min(pw - srcX, Math.round((ux1 - ux0) / tile * pw)));
                    int srcH = Math.max(1, Math.min(ph - srcY, Math.round((uy1 - uy0) / tile * ph)));
                    out.addImageRegion(RenderLayer.GROUND, roof.sprite, pw, ph, srcX, srcY, srcW, srcH,
                            (x0 + x1) / 2f, (y0 + y1) / 2f, Math.abs(x1 - x0) + .5f, Math.abs(y1 - y0) + .5f,
                            0f, 1f, 1f, 1f, alpha);
                }
            }
        }
        for (float[] lane : level.lanes) {
            rect(lane[0], lane[1], lane[2], lane[3], depth, .07f, .07f, .08f, .88f * alpha);
            boolean alongX = lane[2] - lane[0] > lane[3] - lane[1];
            if (alongX) {
                rect(lane[0], lane[1], lane[2], lane[1] + .9f, depth, .86f, .68f, .14f, .85f * alpha);
                rect(lane[0], lane[3] - .9f, lane[2], lane[3], depth, .86f, .68f, .14f, .85f * alpha);
            } else {
                rect(lane[0], lane[1], lane[0] + .9f, lane[3], depth, .86f, .68f, .14f, .85f * alpha);
                rect(lane[2] - .9f, lane[1], lane[2], lane[3], depth, .86f, .68f, .14f, .85f * alpha);
            }
            // Guide lights down both sides of the lane, leading in to the bay.
            float length = alongX ? lane[2] - lane[0] : lane[3] - lane[1];
            for (float t = 2f; t < length - 1f; t += 5f) {
                for (float side : new float[]{2.2f, (alongX ? lane[3] - lane[1] : lane[2] - lane[0]) - 2.2f}) {
                    float lx = alongX ? lane[0] + t : lane[0] + side, ly = alongX ? lane[1] + side : lane[1] + t;
                    rect(lx - .45f, ly - .45f, lx + .45f, ly + .45f, depth, 1f, .78f, .38f, .95f * alpha);
                }
            }
        }
        // Softer than the shadow on the ground: a terrace is lit by the sky and the walls around it.
        for (float[] hull : level.parked) {
            // A parked hull sits on its own soft shadow, laid away from the sun.
            float sdx = sun == null ? .6f : -sun.dirX() * 1.6f, sdy = sun == null ? .6f : -sun.dirY() * 1.6f;
            drawCraft(hull[0] + sdx, hull[1] + sdy, depth, hull[2], (int) hull[3], -.45f);
            drawCraft(hull[0], hull[1], depth, hull[2], (int) hull[3], 1f);
        }
        float shade = sun == null ? 0f : sun.shadowStrength() * .35f * alpha;
        for (float[] r : level.shadow) rect(r[0], r[1], r[2], r[3], depth, SHADOW_R, SHADOW_G, SHADOW_B, shade);
        // Ambient dark at the foot of each wall standing on this roof, fading out across the terrace.
        for (float[] run : level.footings) {
            boolean horizontal = run[1] == run[3];
            for (int k = 0; k < 4; k++) {
                float a0 = k * 1.5f, a1 = a0 + 1.5f, al = (.42f - k * .1f) * alpha;
                if (horizontal) {
                    rect(run[0], run[1] + run[5] * a0, run[2], run[1] + run[5] * a1, depth, 0f, 0f, 0f, al);
                } else {
                    rect(run[0] + run[4] * a0, run[1], run[0] + run[4] * a1, run[3], depth, 0f, 0f, 0f, al);
                }
            }
        }
        // A light lip along the terrace's edge, so each step reads against the one below.
        for (float[] run : level.runs) {
            boolean horizontal = run[1] == run[3];
            float w = .6f;
            if (horizontal) rect(run[0], run[1] - run[5] * w, run[2], run[1], depth, .44f, .42f, .39f, .7f * alpha);
            else rect(run[0] - run[4] * w, run[1], run[0], run[3], depth, .44f, .42f, .39f, .7f * alpha);
        }
        float haze = hazeAt(depth);
        for (float[] r : level.roofs) {
            rect(r[0], r[1], r[2], r[3], depth, 0f, 0f, 0f, ROOF_VEIL * alpha);
            rect(r[0], r[1], r[2], r[3], depth, backdrop.hazeR(), backdrop.hazeG(), backdrop.hazeB(), haze * alpha);
        }
    }

    // ----- traffic -----

    /** Places every craft on its route at {@code time}, in the band of depth of the level it flies beside. */
    private void fly(float time) {
        for (Level level : levels) level.flying.clear();
        if (backdrop.craft().isEmpty()) return;
        for (int r = 0; r < routes.size(); r++) {
            float[] route = routes.get(r);
            int count = (int) route[0];
            float speed = route[1], spacing = route[2];
            int points = (route.length - 3) / 3;
            float total = 0f;
            for (int k = 1; k < points; k++) total += segment(route, k);
            if (total <= 0f) continue;
            float cycle = Math.max(total, count * spacing);
            for (int c = 0; c < count; c++) {
                float d = (float) ((time * speed + c * spacing + r * 37f) % cycle);
                if (d > total) continue;
                int k = 1;
                float along = d;
                while (k < points - 1 && along > segment(route, k)) along -= segment(route, k++);
                float len = segment(route, k), t = len <= 0f ? 0f : Math.min(1f, along / len);
                int a = 3 + (k - 1) * 3, b = a + 3;
                float x = route[a] + (route[b] - route[a]) * t, y = route[a + 1] + (route[b + 1] - route[a + 1]) * t;
                float z = route[a + 2] + (route[b + 2] - route[a + 2]) * t;
                float heading = (float) Math.atan2(route[b + 1] - route[a + 1], route[b] - route[a]);
                // Craft fade in at the start of a path and out at its end, into a bay's dark or the haze.
                float fade = Math.min(1f, Math.min(d, total - d) / 25f);
                int hull = Math.floorMod(r * 5 + c * 3 + r / 3, backdrop.craft().size());
                Level band = null;
                for (Level level : levels) if (level.top <= z && (band == null || level.top > band.top)) band = level;
                if (band != null && z < backdrop.depthMeters()) band.flying.add(new float[]{x, y, z, heading, hull, fade});
            }
        }
    }

    private static float segment(float[] route, int k) {
        int a = 3 + (k - 1) * 3, b = a + 3;
        float dx = route[b] - route[a], dy = route[b + 1] - route[a + 1], dz = route[b + 2] - route[a + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * A hull at {@code (x, y)}, {@code z} metres down, nose along {@code heading}, sized by the scale the
     * backdrop names. A negative {@code fade} draws its silhouette dark at that strength, for a shadow.
     */
    private void drawCraft(float x, float y, float z, float heading, int hull, float fade) {
        ShuttleSpriteCache sprite = sprites.spriteByPath(backdrop.craft().get(hull));
        if (sprite == null || sprite.pxH <= 0) return;
        float s = scale(z);
        float length = sprite.pxH * backdrop.craftMetersPerPx();
        float cellPx = cam.cellPxSize();
        float h = length * s * cellPx, w = h * sprite.aspect;
        if (h < 1.5f) return;
        float qx = sx(x, s), qy = sy(y, s);
        if (qx < cam.vpX() - h || qx > cam.vpX() + cam.vpW() + h || qy < cam.vpY() - h
                || qy > cam.vpY() + cam.vpH() + h) return;
        float nx = sx(x + (float) Math.cos(heading), s) - qx, ny = sy(y + (float) Math.sin(heading), s) - qy;
        float angle = (float) Math.toDegrees(Math.atan2(ny, nx)) - 90f;
        if (fade < 0f) {
            out.addSprite(RenderLayer.GROUND, sprite.sprite, qx, qy, w, h, angle, 0f, 0f, 0f, -fade * alpha);
            return;
        }
        float haze = hazeAt(z);
        float a = fade * (1f - haze * .55f) * alpha;
        out.addSprite(RenderLayer.GROUND, sprite.sprite, qx, qy, w, h, angle, 1f, 1f, 1f, a);
        // An engine's glow at the tail, warm against the haze.
        float tx = x - (float) Math.cos(heading) * length * .5f, ty = y - (float) Math.sin(heading) * length * .5f;
        float g = Math.max(.2f, length * .035f);
        rect(tx - g, ty - g, tx + g, ty + g, z, 1f, .78f, .45f, .9f * a);
        rect(tx - g * 2.2f, ty - g * 2.2f, tx + g * 2.2f, ty + g * 2.2f, z, 1f, .55f, .2f, .25f * a);
    }

    /**
     * The paths craft fly: in from far out over each bay's lane and into the bay (every other bay outbound), and
     * skyways crossing the drop beside the structure at several depths, two streams apiece, opposite ways. And
     * the hulls parked on each terrace, between its bays, along the wall above.
     */
    private void plotTraffic(VoidBackdrop backdrop) {
        routes.clear();
        if (backdrop.craft().isEmpty()) return;
        int bay = 0;
        for (List<Level> structure : structures) for (int i = 0; i + 1 < structure.size(); i++) {
            Level level = structure.get(i), below = structure.get(i + 1);
            float reach = below.outset - level.outset;
            for (float[] mouth : level.mouths) {
                float[] run = level.runs.get((int) mouth[0]);
                boolean horizontal = run[1] == run[3];
                float wx = horizontal ? mouth[1] : run[0], wy = horizontal ? run[1] : mouth[1];
                float nx = run[4], ny = run[5];
                float zIn = level.bottom - MOUTH_H / 2f - 1f;
                float[] far = {wx + nx * (reach + APPROACH_METERS), wy + ny * (reach + APPROACH_METERS),
                        Math.max(4f, level.bottom - 50f)};
                float[] edge = {wx + nx * reach, wy + ny * reach, level.bottom - 8f};
                float[] in = {wx, wy, zIn};
                float[][] path = bay++ % 2 == 0 ? new float[][]{far, edge, in} : new float[][]{in, edge, far};
                routes.add(route(BAY_CRAFT, BAY_SPEED, 90f, path));
            }
            // Parked hulls between the bays, a third of the way out across the terrace, nose along the wall.
            if (reach < PARK_REACH) continue;
            for (int k = 0; k < level.runs.size(); k++) {
                float[] run = level.runs.get(k);
                boolean horizontal = run[1] == run[3];
                List<Float> centres = new ArrayList<>();
                for (float[] mouth : level.mouths) if ((int) mouth[0] == k) centres.add(mouth[1]);
                for (int c = 0; c + 1 < centres.size(); c++) {
                    float u = (centres.get(c) + centres.get(c + 1)) / 2f;
                    float inset = reach * .35f;
                    float x = horizontal ? u : run[0] + run[4] * inset, y = horizontal ? run[1] + run[5] * inset : u;
                    float heading = horizontal ? 0f : (float) (Math.PI / 2);
                    int hull = backdrop.craft().size() - 1 - (k + c) % Math.min(3, backdrop.craft().size());
                    below.parked.add(new float[]{x, y, heading, hull});
                }
            }
        }
        if (!backdrop.skyways().isEmpty()) {
            // Skyways the backdrop names, down the city's streets: two streams apiece, opposite ways, either side
            // of the street's middle.
            for (VoidBackdrop.Skyway way : backdrop.skyways()) {
                float dx = way.x1() - way.x0(), dy = way.y1() - way.y0();
                float length = (float) Math.hypot(dx, dy);
                if (length <= 0f) continue;
                float nx = -dy / length * 4f, ny = dx / length * 4f;
                int count = Math.max(1, Math.round(length / SKYWAY_SPACING));
                float z = way.depthMeters();
                routes.add(route(count, SKYWAY_SPEED, SKYWAY_SPACING, new float[][]{
                        {way.x0() + nx, way.y0() + ny, z}, {way.x1() + nx, way.y1() + ny, z}}));
                routes.add(route(count, SKYWAY_SPEED, SKYWAY_SPACING, new float[][]{
                        {way.x1() - nx, way.y1() - ny, z}, {way.x0() - nx, way.y0() - ny, z}}));
            }
            return;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Level level : levels) for (float[] r : level.roofs) {
            minX = Math.min(minX, r[0]); minY = Math.min(minY, r[1]);
            maxX = Math.max(maxX, r[2]); maxY = Math.max(maxY, r[3]);
        }
        float mx = (minX + maxX) / 2f, my = (minY + maxY) / 2f;
        float clear = (float) Math.hypot(maxX - minX, maxY - minY) / 2f + 40f;
        float[] depths = {.30f, .50f, .70f, .88f};
        float[] bearings = {20f, 112f, 64f, 158f};
        for (int k = 0; k < depths.length; k++) {
            float z = depths[k] * backdrop.depthMeters();
            float th = (float) Math.toRadians(bearings[k]);
            float dx = (float) Math.cos(th), dy = (float) Math.sin(th);
            float side = k % 2 == 0 ? 1f : -1f;
            for (int lane = 0; lane < 2; lane++) {
                float off = side * (clear + lane * 12f);
                float ox = mx - dy * off, oy = my + dx * off;
                float[] a = {ox - dx * SKYWAY_METERS / 2f, oy - dy * SKYWAY_METERS / 2f, z};
                float[] b = {ox + dx * SKYWAY_METERS / 2f, oy + dy * SKYWAY_METERS / 2f, z};
                int count = Math.round(SKYWAY_METERS / SKYWAY_SPACING);
                routes.add(route(count, SKYWAY_SPEED, SKYWAY_SPACING,
                        lane == 0 ? new float[][]{a, b} : new float[][]{b, a}));
            }
        }
    }

    private static float[] route(int count, float speed, float spacing, float[][] points) {
        float[] route = new float[3 + points.length * 3];
        route[0] = count;
        route[1] = speed;
        route[2] = spacing;
        for (int k = 0; k < points.length; k++) System.arraycopy(points[k], 0, route, 3 + k * 3, 3);
        return route;
    }

    /** A railing along the surface's edge, on the void side: a lit rail over its own dark shadow line. */
    private void drawRail(float[] run) {
        boolean horizontal = run[1] == run[3];
        float nx = run[4], ny = run[5];
        if (horizontal) {
            rect(run[0], run[1], run[2], run[1] + ny * RAIL_CELLS, 0f, .70f, .72f, .74f, alpha);
            rect(run[0], run[1] + ny * RAIL_CELLS, run[2], run[1] + ny * RAIL_CELLS * 1.6f, 0f,
                    .05f, .06f, .09f, .55f * alpha);
        } else {
            rect(run[0], run[1], run[0] + nx * RAIL_CELLS, run[3], 0f, .70f, .72f, .74f, alpha);
            rect(run[0] + nx * RAIL_CELLS, run[1], run[0] + nx * RAIL_CELLS * 1.6f, run[3], 0f,
                    .05f, .06f, .09f, .55f * alpha);
        }
    }

    // ----- building the levels, once per map -----

    private void build(CellTopology topology, ElevationMap elevation, VoidBackdrop backdrop) {
        builtFor = topology;
        builtWith = backdrop;
        shadowKey = null;
        structures.clear();
        levels.clear();
        surface.clear();
        rails.clear();
        int w = topology.getWidth(), h = topology.getHeight();
        float widest = 0f;
        for (VoidBackdrop.Tier tier : backdrop.tiers()) widest = Math.max(widest, tier.outsetMeters());
        int m = (int) Math.ceil(widest) + 2;
        // The mask reaches past the map far enough to hold every neighbouring tower and its terraces.
        for (VoidBackdrop.Tower tower : backdrop.neighbours()) {
            m = Math.max(m, Math.max(Math.max(-tower.x0(), -tower.y0()), Math.max(tower.x1() - w, tower.y1() - h))
                    + (int) Math.ceil(widest) + 2);
        }
        int mw = w + 2 * m, mh = h + 2 * m;
        boolean[] base = new boolean[mw * mh];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                base[(y + m) * mw + x + m] = topology.getGroundKind(x, y) != GroundKind.VOID;
            }
        }
        mergeRects(base, mw, mh, 1, -m, -m, surface);
        findRuns(base, mw, mh, m, rails);
        boolean[] towers = towers(base, mw, mh);
        boolean[] spans = new boolean[mw * mh];
        boolean anySpan = false;
        for (int k = 0; k < base.length; k++) {
            spans[k] = base[k] && !towers[k];
            anySpan |= spans[k];
        }
        // A tower on a raised tier stands that much higher: its roof is above the surface, its walls start there.
        for (boolean[] footprint : pieces(towers, mw, mh)) {
            int tier = 0;
            for (int k = 0; elevation != null && k < footprint.length; k++) {
                int x = k % mw - m, y = k / mw - m;
                if (footprint[k] && elevation.inBounds(x, y)) tier = Math.max(tier, elevation.eyeTier(x, y));
            }
            addStructure(footprint, mw, mh, m, -tier * ElevationMap.TIER_RISE_METERS, backdrop);
        }
        if (anySpan) {
            Level span = new Level();
            span.top = 0f;
            span.bottom = SPAN_METERS;
            span.surface = true;
            span.span = true;
            findRuns(spans, mw, mh, m, span.runs);
            mergeRects(spans, mw, mh, 1, -m, -m, span.roofs);
            structures.add(List.of(span));
        }
        for (VoidBackdrop.Tower tower : backdrop.neighbours()) {
            boolean[] footprint = new boolean[mw * mh];
            for (int y = Math.max(-m, tower.y0()); y < Math.min(h + m, tower.y1()); y++) {
                for (int x = Math.max(-m, tower.x0()); x < Math.min(w + m, tower.x1()); x++) {
                    footprint[(y + m) * mw + x + m] = true;
                }
            }
            addStructure(footprint, mw, mh, m, tower.roofDepthMeters(), backdrop);
        }
        for (List<Level> structure : structures) levels.addAll(structure);
        levels.sort((a, b) -> Float.compare(b.top, a.top));
        // Freight bays in the foot of every wall that stands on a terrace, each feeding a lane across it.
        for (List<Level> structure : structures) for (int i = 0; i + 1 < structure.size(); i++) {
            Level level = structure.get(i), below = structure.get(i + 1);
            float reach = below.outset - level.outset;
            if (reach < BAY_REACH || level.bottom - level.top < MOUTH_H + 4f) continue;
            for (int k = 0; k < level.runs.size(); k++) {
                float[] run = level.runs.get(k);
                boolean horizontal = run[1] == run[3];
                float a = horizontal ? run[0] : run[1], b = horizontal ? run[2] : run[3];
                float length = b - a;
                if (length < MOUTH_W + 8f) continue;
                int count = Math.max(1, (int) Math.floor(length / MOUTH_SPACING));
                for (int c = 0; c < count; c++) {
                    float centre = a + length * (c + .5f) / count;
                    level.mouths.add(new float[]{k, centre});
                    float lane0 = centre - LANE_W / 2f, lane1 = centre + LANE_W / 2f;
                    if (horizontal) {
                        float y0 = run[1], y1 = run[1] + run[5] * reach;
                        below.lanes.add(new float[]{lane0, Math.min(y0, y1), lane1, Math.max(y0, y1)});
                    } else {
                        float x0 = run[0], x1 = run[0] + run[4] * reach;
                        below.lanes.add(new float[]{Math.min(x0, x1), lane0, Math.max(x0, x1), lane1});
                    }
                }
            }
        }
        plotTraffic(backdrop);
        plotItems();
    }

    /**
     * One tower: its roof {@code top} metres down (the surface, at 0), then a terrace at each of the backdrop's
     * tiers deeper than that, its footprint grown by the tier's outset, down to the ground.
     */
    private void addStructure(boolean[] footprint, int mw, int mh, int m, float top, VoidBackdrop backdrop) {
        List<VoidBackdrop.Tier> tiers = new ArrayList<>();
        for (VoidBackdrop.Tier tier : backdrop.tiers()) if (tier.depthMeters() > top) tiers.add(tier);
        List<Level> structure = new ArrayList<>();
        for (int i = 0; i <= tiers.size(); i++) {
            Level level = new Level();
            level.top = i == 0 ? top : tiers.get(i - 1).depthMeters();
            level.bottom = i < tiers.size() ? tiers.get(i).depthMeters() : backdrop.depthMeters();
            level.outset = i == 0 ? 0f : tiers.get(i - 1).outsetMeters();
            level.surface = i == 0 && top <= 0f;
            boolean[] mask = i == 0 ? footprint : dilate(footprint, mw, mh, Math.round(level.outset));
            findRuns(mask, mw, mh, m, level.runs);
            mergeRects(mask, mw, mh, 1, -m, -m, level.roofs);
            if (i > 0) level.footings = structure.get(i - 1).runs;
            structure.add(level);
        }
        structures.add(structure);
    }

    /**
     * The part of the surface that stands on towers: the surface opened by a walkway's width, so whatever is at
     * least {@link #SPAN_CELLS} wide stays and the narrower ground the opening takes away is walkway.
     */
    static boolean[] towers(boolean[] surface, int mw, int mh) {
        boolean[] towers = dilate(erode(surface, mw, mh, SPAN_CELLS / 2), mw, mh, SPAN_CELLS / 2);
        for (int k = 0; k < towers.length; k++) towers[k] &= surface[k];
        return towers;
    }

    /** The mask's 4-connected pieces, each as a mask of its own. */
    private static List<boolean[]> pieces(boolean[] mask, int mw, int mh) {
        List<boolean[]> out = new ArrayList<>();
        boolean[] seen = new boolean[mask.length];
        int[] queue = new int[mask.length];
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || seen[start]) continue;
            boolean[] piece = new boolean[mask.length];
            int head = 0, tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            while (head < tail) {
                int k = queue[head++];
                piece[k] = true;
                int x = k % mw, y = k / mw;
                int[] next = {x > 0 ? k - 1 : -1, x + 1 < mw ? k + 1 : -1, y > 0 ? k - mw : -1, y + 1 < mh ? k + mw : -1};
                for (int n : next) {
                    if (n >= 0 && mask[n] && !seen[n]) {
                        seen[n] = true;
                        queue[tail++] = n;
                    }
                }
            }
            out.add(piece);
        }
        return out;
    }

    /** The mask shrunk by {@code r} cells in every direction, square-cornered: its outside grown. */
    private static boolean[] erode(boolean[] mask, int mw, int mh, int r) {
        boolean[] outside = new boolean[mask.length];
        for (int k = 0; k < mask.length; k++) outside[k] = !mask[k];
        boolean[] grown = dilate(outside, mw, mh, r);
        boolean[] shrunk = new boolean[mask.length];
        for (int k = 0; k < mask.length; k++) shrunk[k] = !grown[k];
        return shrunk;
    }

    /** The mask grown by {@code r} cells in every direction, square-cornered. */
    private static boolean[] dilate(boolean[] mask, int mw, int mh, int r) {
        if (r <= 0) return mask;
        boolean[] across = new boolean[mw * mh];
        int[] sum = new int[Math.max(mw, mh) + 1];
        for (int y = 0; y < mh; y++) {
            for (int x = 0; x < mw; x++) sum[x + 1] = sum[x] + (mask[y * mw + x] ? 1 : 0);
            for (int x = 0; x < mw; x++) {
                int lo = Math.max(0, x - r), hi = Math.min(mw, x + r + 1);
                across[y * mw + x] = sum[hi] - sum[lo] > 0;
            }
        }
        boolean[] grown = new boolean[mw * mh];
        for (int x = 0; x < mw; x++) {
            for (int y = 0; y < mh; y++) sum[y + 1] = sum[y] + (across[y * mw + x] ? 1 : 0);
            for (int y = 0; y < mh; y++) {
                int lo = Math.max(0, y - r), hi = Math.min(mh, y + r + 1);
                grown[y * mw + x] = sum[hi] - sum[lo] > 0;
            }
        }
        return grown;
    }

    /** Every straight run of boundary between the mask and its outside, in cells, normals pointing out. */
    private static void findRuns(boolean[] mask, int mw, int mh, int m, List<float[]> into) {
        for (int y = 1; y < mh; y++) {
            int start = -1, normal = 0;
            for (int x = 0; x <= mw; x++) {
                int n = 0;
                if (x < mw) {
                    boolean above = mask[(y - 1) * mw + x], below = mask[y * mw + x];
                    if (above != below) n = above ? 1 : -1;
                }
                if (n != normal) {
                    if (normal != 0) into.add(new float[]{start - m, y - m, x - m, y - m, 0, normal});
                    start = x;
                    normal = n;
                }
            }
        }
        for (int x = 1; x < mw; x++) {
            int start = -1, normal = 0;
            for (int y = 0; y <= mh; y++) {
                int n = 0;
                if (y < mh) {
                    boolean left = mask[y * mw + x - 1], right = mask[y * mw + x];
                    if (left != right) n = left ? 1 : -1;
                }
                if (n != normal) {
                    if (normal != 0) into.add(new float[]{x - m, start - m, x - m, y - m, normal, 0});
                    start = y;
                    normal = n;
                }
            }
        }
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

    /** How far a wall's run lies from the camera's point, seen from above. */
    private static float planDistance(float[] run, float px, float py) {
        float qx = Math.max(Math.min(run[0], run[2]), Math.min(px, Math.max(run[0], run[2])));
        float qy = Math.max(Math.min(run[1], run[3]), Math.min(py, Math.max(run[1], run[3])));
        return (qx - px) * (qx - px) + (qy - py) * (qy - py);
    }
}
