package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
 * <p>Presentation only. It reads the map's void cells and its {@link VoidBackdrop}, never the battle but for its
 * clock, which places the craft a mission has called to a berth.
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
    /** How far the dark at a wall's foot fades out across the terrace it stands on, at most, in metres. */
    static final float FOOTING_METERS = 6f;
    /** The ground system's own backing colour, laid under the surface. */
    static final float BACKING_R = 0x18 / 255f, BACKING_G = 0x22 / 255f, BACKING_B = 0x30 / 255f;
    /** Freight bay mouths: spacing along a wall, width and height in metres, and the lane each feeds. */
    static final float MOUTH_SPACING = 64f, MOUTH_W = 28f, MOUTH_H = 16f, LANE_W = 24f;
    /** The least width of terrace a freight bay opens onto, in metres. */
    static final float BAY_REACH = 8f;
    /** A roof below the surface is veiled this dark at least, so no roof in the drop reads as walkable. */
    static final float ROOF_VEIL = .28f;
    /**
     * Craft flying in and out of a bay: how far out they come from, about how fast they fly, and about how many
     * seconds pass between one arriving (or leaving) and the next.
     */
    static final float APPROACH_METERS = 170f, BAY_SPEED = 7f, BAY_INTERVAL = 80f;
    /**
     * Skyways: the length of one crossing a drop with no streets, about how fast craft fly them, and about how
     * many seconds between one craft and the next on a route.
     */
    static final float SKYWAY_METERS = 1600f, SKYWAY_SPEED = 9f, SKYWAY_INTERVAL = 20f;
    /** A called craft: how long it flies in or out, and how long it takes to turn for the way out. */
    static final float CALL_FLIGHT_SECONDS = 40f, CALL_TURN_SECONDS = 8f;
    /** Slips: about how fast craft fly in and out of one, and about how many seconds between one and the next. */
    static final float SLIP_SPEED = 8f, SLIP_INTERVAL = 70f;
    /** Street routes: how many at most and to a skyway, how far right of a street's middle they keep, and a turn's reach. */
    static final int STREET_ROUTES_MAX = 40, STREET_ROUTES_PER_SKYWAY = 5;
    static final float STREET_LANE_OFFSET = 4f, TURN_METERS = 22f;
    /** How wide ground must be to stand on a tower of its own; anything narrower is a walkway. */
    static final int SPAN_CELLS = 7;
    /** How deep a walkway's girders run under its deck, in metres. */
    static final float SPAN_METERS = 2.5f;
    /** The least width of terrace a freighter is parked on, in metres. */
    static final float PARK_REACH = 20f;
    /**
     * The roof radius a structure's corners are rounded to, picked per structure: a square corner, or a round one
     * of these metres. The terraces below round on the same centres, so each step is a ring of even width.
     */
    static final int[] ROOF_RADII = {0, 6, 10, 14};
    /** How much of the traffic flies the backdrop's first two hulls, the common ones (owner: mostly shuttles and Hounds). */
    static final int COMMON_HULL_PERCENT = 80;
    /**
     * How much of the ground image a backdrop with an atmosphere covers in its haze before the city's lights are laid
     * on it: the image carries a daylight haze of its own.
     */
    static final float GROUND_GRADE = .42f;
    /** Lights see through the haze better than what they light: this much of the haze dims them. */
    static final float GLOW_HAZE = .3f;
    /** The cool of ambient light on unlit metal, against the lights' warmth. */
    static final float AMBIENT_R = .86f, AMBIENT_G = .9f, AMBIENT_B = 1f;
    /** A soft round light, white, for lamps and beacons. */
    static final String LIGHT_SPRITE = "graphics/fx/particlealpha64sq.png";
    /** Lamps along a roof's edge: their spacing and how far in from the edge, in metres. */
    static final float EDGE_LAMP_SPACING = 7f, EDGE_LAMP_INSET = 1.1f;
    /** Fog: the deep decks' colour, lit from the city below, and how fast the puffs drift, in metres a second. */
    static final float FOG_WARM_R = .86f, FOG_WARM_G = .64f, FOG_WARM_B = .44f, FOG_DRIFT = 1.6f;
    /** The shallow decks' fog, cool under the sky. */
    static final float FOG_COOL_R = .58f, FOG_COOL_G = .64f, FOG_COOL_B = .76f;
    /** Floodlight thrown up a wall from the terrace at its foot: how high it reaches, in metres, and its colour. */
    static final float FLOOD_METERS = 14f, FLOOD_R = 1f, FLOOD_G = .66f, FLOOD_B = .34f;
    /** Wall facets to a rounded corner's quarter turn. */
    static final int CORNER_FACETS = 5;
    /** Marker lights down a skyway's edges: their spacing in metres and how often they blink. */
    static final float LANE_LIGHT_SPACING = 34f, LANE_LIGHT_HZ = .7f;

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
    private final VoidTraffic traffic = new VoidTraffic();
    /** This frame's craft in the air, reused frame to frame. */
    private final List<float[]> aloft = new ArrayList<>();
    /** The battle's own clock this frame, in seconds, for the calls. */
    private float battleSeconds;
    /** The skyways' marker lights: {@code {x, y, z, distance along the lane}}. */
    private final List<float[]> laneLights = new ArrayList<>();

    // The frame being drawn.
    private DrawList out;
    private BattleCamera cam;
    private VoidBackdrop backdrop;
    private float eye, cx, cy, alpha, time;

    /**
     * One thing drawn at one depth: a band of wall, a girder side, a roof, a freight bay or a band of a rounded
     * corner. {@code piece} is the bay or the corner; a corner's {@code run} is its chord, for culling and order.
     */
    private record Item(float depth, int kind, Level level, float[] run, float z0, float z1, float[] piece) {
        static final int WALL = 0, GIRDER = 1, ROOF = 2, MOUTH = 3, CORNER = 4, FOG = 5;
    }

    /** Everything the structures draw, deepest first: the painter's order before the camera's far-first pass. */
    private final List<Item> items = new ArrayList<>();
    /** Single-quad meshes for this frame's slanted bands, reused frame to frame. */
    private final List<PolyMesh> polys = new ArrayList<>();
    private int polysUsed;

    /** A footprint and the depths its walls run between. */
    private static final class Level {
        float top, bottom, outset;
        /** How far this level's roof reaches out past the walls of the level above, in metres. */
        float reach;
        /** Its roof is the playing surface, which the ground tiles draw. */
        boolean surface;
        /** The top of its structure: its roof's corners carry beacons. */
        boolean crown;
        /** A walkway's girders, not a tower's walls. */
        boolean span;
        /** Each edge run: {@code {x0, y0, x1, y1, normalX, normalY}} in cells along a cell boundary. */
        final List<float[]> runs = new ArrayList<>();
        /** The footprint as rectangles in cells, less its rounded corners' squares. */
        final List<float[]> roofs = new ArrayList<>();
        /**
         * Rounded corners: {@code {centreX, centreY, radius, hX, hY, vX, vY}}, the arc a quarter turn from
         * {@code centre + radius * h} to {@code centre + radius * v}, {@code h} and {@code v} the outward normals of
         * the two runs it joins.
         */
        final List<float[]> corners = new ArrayList<>();
        /**
         * The roof's curved pieces, as convex quads (a triangle repeats a point):
         * {@code {boxX0, boxY0, boxX1, boxY1, x0, y0, x1, y1, x2, y2, x3, y3}}, the box the corner square its roof
         * texture is laid over.
         */
        final List<float[]> fans = new ArrayList<>();
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
        // A host that keeps no animation clock (the game itself) passes NaN: fly by the wall clock then, or every
        // craft's place along its route is NaN and none is drawn.
        this.time = FireLight.clock(ctx.animationTimeSeconds);
        // A call keeps the battle's own clock, not the presentation's: the freighter stands at its berth while the
        // mission says it does.
        this.battleSeconds = ctx.sim.getSimTickIndex() * BattleSimulation.TICK_DT;
        fly(time);
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
            while (craft < flying.size() && flying.get(craft)[2] > depth) drawFlying(flying.get(craft++));
            List<Item> slice = items.subList(i, j);
            if (slice.size() > 1) {
                slice = new ArrayList<>(slice);
                slice.sort((a, b) -> Float.compare(itemDistance(b, px, py), itemDistance(a, px, py)));
            }
            for (Item item : slice) if (onScreen(item)) draw(item);
            i = j;
        }
        while (craft < flying.size()) drawFlying(flying.get(craft++));
        for (float[] run : rails) drawRail(run);
        // The surface's own backing, which the ground system leaves off on a map with a backdrop: wall art is
        // not opaque everywhere, and through it the structure under the surface would show.
        for (float[] r : surface) rect(r[0], r[1], r[2], r[3], 0f, BACKING_R, BACKING_G, BACKING_B, alpha);
    }

    /** A craft, or a lane light where its hull is negative. */
    private void drawFlying(float[] c) {
        if (c[4] < 0f) drawLaneLight(c[0], c[1], c[2], c[5]);
        else drawCraft(c[0], c[1], c[2], c[3], (int) c[4], c[5]);
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
        tileGround(image, cellPx, 1f, 1f, 1f, alpha);
        VoidBackdrop.Atmosphere air = backdrop.atmosphere();
        if (air == null) return;
        // The ground's own daylight haze sunk into the drop's, then the city's lights laid on it, through it.
        out.addSolidRect(RenderLayer.GROUND, cam.vpX(), cam.vpY(), cam.vpX() + cam.vpW(), cam.vpY() + cam.vpH(),
                backdrop.hazeR(), backdrop.hazeG(), backdrop.hazeB(), GROUND_GRADE * alpha);
        ShuttleSpriteCache glow = sprites.spriteByPath(air.cityGlow());
        if (glow != null) tileGround(glow, cellPx, 1f, 1f, 1f, alpha);
    }

    /** The ground image, or its light mask, tiled over the view at the ground's depth. */
    private void tileGround(ShuttleSpriteCache image, float cellPx, float r, float g, float b, float a) {
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
                            tx, ty, size, size, 0f, r, g, b, a);
                } else {
                    out.addSprite(RenderLayer.GROUND, image.sprite, tx, ty, size, size, 0f, r, g, b, a);
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
            case Item.WALL -> drawWallBand(item.run(), item.z0(), item.z1(), item.level().bottom,
                    item.level().bottom < backdrop.depthMeters());
            case Item.GIRDER -> drawGirder(item.run());
            case Item.ROOF -> drawRoof(item.level());
            case Item.CORNER -> drawCornerBand(item.piece(), item.z0(), item.z1());
            case Item.FOG -> drawFog(item.depth(), (int) item.z0());
            default -> {
                float[] run = item.run();
                boolean horizontal = run[1] == run[3];
                drawMouth(item.level(), horizontal, horizontal ? run[1] : run[0], item.piece()[1]);
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
            for (float[] corner : level.corners) {
                float[] chord = arc(corner)[0];
                float[] last = arc(corner)[CORNER_FACETS];
                float[] run = {chord[0], chord[1], last[0], last[1], 0f, 0f};
                float z = level.top;
                while (z < level.bottom - .01f) {
                    float z1 = Math.min(level.bottom, (float) (Math.floor(z / BAND_METERS) + 1) * BAND_METERS);
                    items.add(new Item((z + z1) / 2f, Item.CORNER, level, run, z, z1, corner));
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
        VoidBackdrop.Atmosphere air = builtWith.atmosphere();
        if (air != null && air.fogTexture() != null) {
            for (int k = 0; k < air.fogDecks().size(); k++) {
                items.add(new Item(air.fogDecks().get(k), Item.FOG, null, null, k, 0f, null));
            }
        }
        // Deepest first; at one depth a roof after the walls standing to it, so it covers its own far faces.
        items.sort((a, b) -> a.depth() != b.depth() ? Float.compare(b.depth(), a.depth())
                : Integer.compare(a.kind() == Item.ROOF ? 1 : 0, b.kind() == Item.ROOF ? 1 : 0));
    }

    /** One band of a face, between two depths, in tiles of facade under the haze at its depth. */
    private void drawWallBand(float[] run, float z, float z1, float foot, boolean onTerrace) {
        boolean horizontal = run[1] == run[3];
        float a = horizontal ? run[0] : run[1], b = horizontal ? run[2] : run[3];
        float line = horizontal ? run[1] : run[0];
        float toSun = sun == null ? 0f : run[4] * sun.dirX() + run[5] * sun.dirY();
        float lit = .82f + .18f * toSun;
        ShuttleSpriteCache facade = backdrop.facadeTexture() == null ? null
                : sprites.spriteByPath(backdrop.facadeTexture());
        float tile = backdrop.facadeTileMeters();
        boolean air = backdrop.atmosphere() != null;
        float ambR = air ? AMBIENT_R : 1f, ambG = air ? AMBIENT_G : 1f, ambB = air ? AMBIENT_B : 1f;
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
                    facadeQuad(facade, horizontal, line, u, u1, z, z1, s0, s1, sm, lit * ambR, lit * ambG, lit * ambB, alpha);
                    u = u1;
                }
            } else {
                float g = .40f * lit;
                band(horizontal, line, a, b, s0, s1, sm, g, g * 1.02f, g * 1.08f, alpha);
            }
            float haze = hazeAt((z + z1) / 2f);
            band(horizontal, line, a, b, s0, s1, sm, backdrop.hazeR(), backdrop.hazeG(), backdrop.hazeB(),
                    haze * alpha);
            // Floodlight thrown up the wall from the terrace it stands on, fading as it climbs.
            if (air && onTerrace && z1 > foot - FLOOD_METERS) {
                float near = 1f - Math.max(0f, foot - z1) / FLOOD_METERS;
                float far = 1f - Math.max(0f, foot - z) / FLOOD_METERS;
                float fa = .42f * (1f - haze * GLOW_HAZE) * alpha;
                PolyMesh mesh = poly();
                if (horizontal) {
                    float y0 = sy(line, s0), y1 = sy(line, s1);
                    mesh.appendQuad(sx(a, s0), y0, sx(b, s0), y0, sx(b, s1), y1, sx(a, s1), y1,
                            FLOOD_R, FLOOD_G, FLOOD_B, fa * (far + near) / 2f);
                } else {
                    float x0 = sx(line, s0), x1 = sx(line, s1);
                    mesh.appendQuad(x0, sy(a, s0), x0, sy(b, s0), x1, sy(b, s1), x1, sy(a, s1),
                            FLOOD_R, FLOOD_G, FLOOD_B, fa * (far + near) / 2f);
                }
                out.addPoly(RenderLayer.GROUND, mesh);
            }
            // The facade's own lights, over the haze.
            ShuttleSpriteCache glow = glow(true);
            if (glow != null) {
                float u = a;
                float ga = (1f - haze * GLOW_HAZE) * alpha;
                while (u < b - .01f) {
                    float u1 = Math.min(b, (float) (Math.floor(u / tile) + 1) * tile);
                    facadeQuad(glow, horizontal, line, u, u1, z, z1, s0, s1, sm, 1f, 1f, 1f, ga);
                    u = u1;
                }
            }
        }
    }

    /** The facade's light mask, or the roof's, when the backdrop has an atmosphere that names it. */
    private ShuttleSpriteCache glow(boolean facade) {
        VoidBackdrop.Atmosphere air = backdrop.atmosphere();
        if (air == null) return null;
        ShuttleSpriteCache glow = sprites.spriteByPath(facade ? air.facadeGlow() : air.roofGlow());
        return glow == null || glow.pxW <= 0 ? null : glow;
    }

    /**
     * One band of a rounded corner, between two depths: each facet of its arc a slanted quad, its facade laid along
     * it, under the haze at its depth.
     */
    private void drawCornerBand(float[] corner, float z, float z1) {
        float[][] arc = arc(corner);
        float s0 = scale(z), s1 = scale(z1), sm = scale((z + z1) / 2f);
        ShuttleSpriteCache facade = backdrop.facadeTexture() == null ? null
                : sprites.spriteByPath(backdrop.facadeTexture());
        float tile = backdrop.facadeTileMeters();
        float haze = hazeAt((z + z1) / 2f);
        PolyMesh under = poly();
        PolyMesh shade = poly();
        PolyMesh veil = poly();
        List<float[]> glows = new ArrayList<>();
        List<float[]> glowPatches = new ArrayList<>();
        float u = 0f;
        for (int k = 0; k < CORNER_FACETS; k++) {
            float[] a = arc[k], b = arc[k + 1];
            float nx = (a[2] + b[2]) / 2f, ny = (a[3] + b[3]) / 2f;
            float toSun = sun == null ? 0f : nx * sun.dirX() + ny * sun.dirY();
            float lit = .82f + .18f * toSun;
            float ax0 = sx(a[0], s0), ay0 = sy(a[1], s0), bx0 = sx(b[0], s0), by0 = sy(b[1], s0);
            float ax1 = sx(a[0], s1), ay1 = sy(a[1], s1), bx1 = sx(b[0], s1), by1 = sy(b[1], s1);
            float g = .19f * lit;
            under.appendQuad(ax0, ay0, bx0, by0, bx1, by1, ax1, ay1, g, g * .98f, g * .96f, alpha);
            veil.appendQuad(ax0, ay0, bx0, by0, bx1, by1, ax1, ay1, backdrop.hazeR(), backdrop.hazeG(),
                    backdrop.hazeB(), haze * alpha);
            float length = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (facade != null && facade.pxW > 0 && length > 0f) {
                // The facet's piece of facade, its corners pinned to the facet's four: shallow edge at the top.
                int pw = facade.pxW, ph = facade.pxH;
                float fu = (float) (u / tile - Math.floor(u / tile));
                float fz = (float) (z / tile - Math.floor(z / tile));
                int srcW = Math.max(1, Math.min(pw, Math.round(length / tile * pw)));
                int srcX = Math.min(Math.round(fu * pw), pw - srcW);
                int srcY = Math.round(fz * ph), srcH = Math.max(1, Math.round((z1 - z) / tile * ph));
                srcH = Math.min(srcH, ph - srcY);
                float minX = Math.min(Math.min(ax0, bx0), Math.min(ax1, bx1));
                float maxX = Math.max(Math.max(ax0, bx0), Math.max(ax1, bx1));
                float minY = Math.min(Math.min(ay0, by0), Math.min(ay1, by1));
                float maxY = Math.max(Math.max(ay0, by0), Math.max(ay1, by1));
                float w = maxX - minX, h = maxY - minY;
                if (srcH > 0 && w > 0f && h > 0f) {
                    out.addPoly(RenderLayer.GROUND, under);
                    under = poly();
                    float[] patch = {
                            (ax0 - minX) / w, (ay0 - minY) / h, (bx0 - minX) / w, (by0 - minY) / h,
                            (bx1 - minX) / w, (by1 - minY) / h, (ax1 - minX) / w, (ay1 - minY) / h,
                            0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f};
                    out.addSheetPatch(RenderLayer.GROUND, facade.sprite, srcX, srcY, srcW, srcH,
                            minX + w / 2f, minY + h / 2f, w, h, alpha, patch);
                    glows.add(new float[]{srcX / (float) pw, srcY / (float) ph, srcW / (float) pw, srcH / (float) ph,
                            minX + w / 2f, minY + h / 2f, w, h});
                    glowPatches.add(patch);
                    // The patch takes no tint: the facet's turn from the sun is a shade laid over it.
                    shade.appendQuad(ax0, ay0, bx0, by0, bx1, by1, ax1, ay1, 0f, 0f, 0f, (1f - lit) * alpha);
                }
            }
            u += length;
        }
        out.addPoly(RenderLayer.GROUND, under);
        out.addPoly(RenderLayer.GROUND, shade);
        out.addPoly(RenderLayer.GROUND, veil);
        ShuttleSpriteCache glow = glow(true);
        if (glow != null) {
            float ga = (1f - haze * GLOW_HAZE) * alpha;
            for (int k = 0; k < glows.size(); k++) {
                float[] g = glows.get(k);
                int gx = Math.round(g[0] * glow.pxW), gy = Math.round(g[1] * glow.pxH);
                int gw = Math.max(1, Math.round(g[2] * glow.pxW)), gh = Math.max(1, Math.round(g[3] * glow.pxH));
                out.addSheetPatch(RenderLayer.GROUND, glow.sprite, gx, gy, gw, gh, g[4], g[5], g[6], g[7], ga,
                        glowPatches.get(k));
            }
        }
    }

    /**
     * A rounded corner's arc: {@link #CORNER_FACETS} + 1 points a quarter turn round its centre, each
     * {@code {x, y, normalX, normalY}}.
     */
    private static float[][] arc(float[] corner) {
        float[][] points = new float[CORNER_FACETS + 1][];
        for (int k = 0; k <= CORNER_FACETS; k++) {
            double t = Math.PI / 2 * k / CORNER_FACETS;
            float c = (float) Math.cos(t), sn = (float) Math.sin(t);
            float nx = corner[3] * c + corner[5] * sn, ny = corner[4] * c + corner[6] * sn;
            points[k] = new float[]{corner[0] + corner[2] * nx, corner[1] + corner[2] * ny, nx, ny};
        }
        return points;
    }

    /** The square a rounded corner cuts from its footprint: {@code {x0, y0, x1, y1}} in cells. */
    private static float[] cornerSquare(float[] corner) {
        float px = corner[0] + corner[2] * (corner[3] + corner[5]), py = corner[1] + corner[2] * (corner[4] + corner[6]);
        return new float[]{Math.min(px, corner[0]), Math.min(py, corner[1]), Math.max(px, corner[0]),
                Math.max(py, corner[1])};
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
                            float z0, float z1, float s0, float s1, float sm, float r, float g, float b,
                            float a) {
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
                qx, qy, w, h, angle, r, g, b, a);
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
        if (roof == null || roof.pxW <= 0) {
            for (float[] r : level.roofs) rect(r[0], r[1], r[2], r[3], depth, .30f, .29f, .28f, alpha);
        } else {
            boolean air = backdrop.atmosphere() != null;
            roofTiles(level, s, roof, air ? AMBIENT_R : 1f, air ? AMBIENT_G : 1f, air ? AMBIENT_B : 1f, alpha);
        }
        for (float[] fan : level.fans) drawFan(fan, s, roof, tile, alpha);
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
        // Ambient dark at the foot of each wall standing on this roof, fading out across the terrace and never
        // past its edge: a terrace narrower than the fade would otherwise hang a dark band in the air beside it.
        float fade = Math.min(FOOTING_METERS, level.reach) / 4f;
        for (float[] run : level.footings) {
            if (fade <= 0f) break;
            boolean horizontal = run[1] == run[3];
            for (int k = 0; k < 4; k++) {
                float a0 = k * fade, a1 = a0 + fade, al = (.42f - k * .1f) * alpha;
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
        // The lip round each rounded corner, facet by facet.
        if (!level.corners.isEmpty()) {
            PolyMesh lip = poly();
            for (float[] corner : level.corners) {
                float[][] arc = arc(corner);
                for (int k = 0; k < CORNER_FACETS; k++) {
                    float[] a = arc[k], b = arc[k + 1];
                    lip.appendQuad(sx(a[0], s), sy(a[1], s), sx(b[0], s), sy(b[1], s),
                            sx(b[0] - b[2] * .6f, s), sy(b[1] - b[3] * .6f, s),
                            sx(a[0] - a[2] * .6f, s), sy(a[1] - a[3] * .6f, s), .44f, .42f, .39f, .7f * alpha);
                }
            }
            out.addPoly(RenderLayer.GROUND, lip);
        }
        float haze = hazeAt(depth);
        for (float[] r : level.roofs) {
            rect(r[0], r[1], r[2], r[3], depth, 0f, 0f, 0f, ROOF_VEIL * alpha);
            rect(r[0], r[1], r[2], r[3], depth, backdrop.hazeR(), backdrop.hazeG(), backdrop.hazeB(), haze * alpha);
        }
        if (!level.fans.isEmpty()) {
            PolyMesh veil = poly();
            for (float[] fan : level.fans) {
                float x0 = sx(fan[4], s), y0 = sy(fan[5], s), x1 = sx(fan[6], s), y1 = sy(fan[7], s);
                float x2 = sx(fan[8], s), y2 = sy(fan[9], s), x3 = sx(fan[10], s), y3 = sy(fan[11], s);
                veil.appendQuad(x0, y0, x1, y1, x2, y2, x3, y3, 0f, 0f, 0f, ROOF_VEIL * alpha);
                veil.appendQuad(x0, y0, x1, y1, x2, y2, x3, y3, backdrop.hazeR(), backdrop.hazeG(),
                        backdrop.hazeB(), haze * alpha);
            }
            out.addPoly(RenderLayer.GROUND, veil);
        }
        // The roof's own lights over its veil and haze, lamps along its edge, beacons on a crown's corners.
        ShuttleSpriteCache glow = glow(false);
        float ga = (1f - haze * GLOW_HAZE) * alpha;
        if (glow != null) {
            roofTiles(level, s, glow, 1f, 1f, 1f, ga);
            for (float[] fan : level.fans) drawFan(fan, s, glow, tile, ga);
        }
        if (backdrop.atmosphere() != null) edgeLights(level, depth, ga);
    }

    /** The roof image, or its light mask, laid over the level's roof rectangles a tile at a time. */
    private void roofTiles(Level level, float s, ShuttleSpriteCache roof, float r, float g, float b, float a) {
        float tile = backdrop.roofTileMeters();
        float vx0 = cam.vpX(), vx1 = cam.vpX() + cam.vpW(), vy0 = cam.vpY(), vy1 = cam.vpY() + cam.vpH();
        int pw = roof.pxW, ph = roof.pxH;
        for (float[] rr : level.roofs) {
            for (int ty = (int) Math.floor(rr[1] / tile); ty * tile < rr[3]; ty++) {
                for (int tx = (int) Math.floor(rr[0] / tile); tx * tile < rr[2]; tx++) {
                    float ux0 = Math.max(rr[0], tx * tile), ux1 = Math.min(rr[2], (tx + 1) * tile);
                    float uy0 = Math.max(rr[1], ty * tile), uy1 = Math.min(rr[3], (ty + 1) * tile);
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
                            0f, r, g, b, a);
                }
            }
        }
    }

    /**
     * Warm lamps a little in from a roof's edge, and on the top of a structure a red beacon at each corner, blinking
     * out of step with its neighbours.
     */
    private void edgeLights(Level level, float depth, float a) {
        float s = scale(depth);
        float vx0 = cam.vpX(), vx1 = cam.vpX() + cam.vpW(), vy0 = cam.vpY(), vy1 = cam.vpY() + cam.vpH();
        for (float[] run : level.runs) {
            boolean horizontal = run[1] == run[3];
            float a0 = horizontal ? run[0] : run[1], a1 = horizontal ? run[2] : run[3];
            float line = horizontal ? run[1] - run[5] * EDGE_LAMP_INSET : run[0] - run[4] * EDGE_LAMP_INSET;
            float ex0 = sx(horizontal ? a0 : line, s), ey0 = sy(horizontal ? line : a0, s);
            float ex1 = sx(horizontal ? a1 : line, s), ey1 = sy(horizontal ? line : a1, s);
            if (Math.max(ex0, ex1) < vx0 || Math.min(ex0, ex1) > vx1 || Math.max(ey0, ey1) < vy0
                    || Math.min(ey0, ey1) > vy1) continue;
            int count = (int) Math.floor((a1 - a0) / EDGE_LAMP_SPACING);
            for (int k = 0; k < count; k++) {
                float t = a0 + (k + .5f) * (a1 - a0) / count;
                float x = horizontal ? t : line, y = horizontal ? line : t;
                lamp(x, y, depth, 1f, .80f, .52f, .45f, a);
            }
            // A warm strip of light along the edge itself.
            if (horizontal) rect(a0, run[1] - run[5] * .45f, a1, run[1] - run[5] * .15f, depth, 1f, .72f, .40f, .55f * a);
            else rect(run[0] - run[4] * .45f, a0, run[0] - run[4] * .15f, a1, depth, 1f, .72f, .40f, .55f * a);
        }
        if (!level.crown) return;
        for (float[] run : level.runs) {
            for (int end = 0; end < 2; end++) {
                float x = run[end * 2] - run[4] * EDGE_LAMP_INSET, y = run[end * 2 + 1] - run[5] * EDGE_LAMP_INSET;
                beacon(x, y, depth, a);
            }
        }
        for (float[] corner : level.corners) {
            float[] mid = arc(corner)[CORNER_FACETS / 2];
            beacon(mid[0] - mid[2] * EDGE_LAMP_INSET, mid[1] - mid[3] * EDGE_LAMP_INSET, depth, a);
        }
    }

    private void beacon(float x, float y, float depth, float a) {
        double phase = time * .8 + Math.floorMod((int) (x * 7 + y * 13), 97) / 97.0;
        float on = phase - Math.floor(phase) < .45 ? 1f : .18f;
        lamp(x, y, depth, 1f, .16f, .10f, .7f, a * on);
    }

    /** A point of light at a depth: a bright core in a soft round halo, {@code size} metres across its core. */
    private void lamp(float x, float y, float depth, float r, float g, float b, float size, float a) {
        float s = scale(depth);
        float qx = sx(x, s), qy = sy(y, s);
        if (qx < cam.vpX() || qx > cam.vpX() + cam.vpW() || qy < cam.vpY() || qy > cam.vpY() + cam.vpH()) return;
        float px = size * s * cam.cellPxSize();
        ShuttleSpriteCache dot = sprites.spriteByPath(LIGHT_SPRITE);
        if (dot == null || px < .6f) {
            rect(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f, depth, r, g, b, .95f * a);
            return;
        }
        out.addSprite(RenderLayer.GROUND, dot.sprite, qx, qy, px * 10f, px * 10f, 0f, r, g, b, .45f * a);
        out.addSprite(RenderLayer.GROUND, dot.sprite, qx, qy, px * 3f, px * 3f, 0f, Math.min(1f, r + .2f),
                Math.min(1f, g + .2f), Math.min(1f, b + .2f), a);
    }

    // ----- fog -----

    /**
     * A bank of fog at one deck's depth: soft puffs from the fog sheet on a grid that drifts with the wind, thick
     * where a slow noise says so and thin or clear between, so the city shows through the gaps. Drawn in the
     * painter's order at its depth, so the towers rise out of it and what is below it is veiled. A deeper deck is
     * lit warmer by the city's lights below it.
     */
    private void drawFog(float depth, int deck) {
        VoidBackdrop.Atmosphere air = backdrop.atmosphere();
        ShuttleSpriteCache sheet = sprites.spriteByPath(air.fogTexture());
        if (sheet == null || sheet.pxW <= 0) return;
        float s = scale(depth);
        float cellPx = cam.cellPxSize();
        float x0c = cam.cellToScreenX(0f), y0c = cam.cellToScreenY(0f);
        float dxc = cam.cellToScreenX(1f) - x0c, dyc = cam.cellToScreenY(1f) - y0c;
        if (dxc == 0f || dyc == 0f) return;
        float ax = (cx + (cam.vpX() - cx) / s - x0c) / dxc, bx = (cx + (cam.vpX() + cam.vpW() - cx) / s - x0c) / dxc;
        float ay = (cy + (cam.vpY() - cy) / s - y0c) / dyc, by = (cy + (cam.vpY() + cam.vpH() - cy) / s - y0c) / dyc;
        float puff = air.fogPuffMeters(), step = puff * .5f;
        float drift = time * FOG_DRIFT * (deck % 2 == 0 ? 1f : -.7f);
        int i0 = (int) Math.floor((Math.min(ax, bx) - puff - drift) / step);
        int i1 = (int) Math.ceil((Math.max(ax, bx) + puff - drift) / step);
        int j0 = (int) Math.floor((Math.min(ay, by) - puff) / step), j1 = (int) Math.ceil((Math.max(ay, by) + puff) / step);
        if ((long) (i1 - i0) * (j1 - j0) > 6000) return;
        float t = air.fogDecks().size() <= 1 ? 0f : deck / (float) (air.fogDecks().size() - 1);
        float fr = lerp(FOG_COOL_R, FOG_WARM_R, t), fg = lerp(FOG_COOL_G, FOG_WARM_G, t);
        float fb = lerp(FOG_COOL_B, FOG_WARM_B, t);
        int cells = Math.max(1, air.fogSheetCells());
        int cw = sheet.pxW / cells, ch = sheet.pxH / cells;
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                float n = valueNoise(i * .16f, j * .16f, deck * 101);
                if (n < .42f) continue;
                int h = hash(i, j, deck);
                float x = i * step + drift + ((h & 255) / 255f - .5f) * step;
                float y = j * step + (((h >> 8) & 255) / 255f - .5f) * step;
                float size = puff * (.8f + ((h >> 16) & 255) / 255f * .6f);
                float px = sx(x, s), py = sy(y, s), pz = size * cellPx * s;
                int cell = (h >>> 24) % (cells * cells);
                // A deeper deck is thinner, so the city's lights below still show through it.
                float a = air.fogAlpha() * (1f - .5f * t) * Math.min(1f, (n - .42f) / .3f) * alpha;
                out.addImageRegion(RenderLayer.GROUND, sheet.sprite, sheet.pxW, sheet.pxH,
                        cell % cells * cw, cell / cells * ch, cw, ch, px, py, pz, pz,
                        (h >> 4 & 1023) * .35f, fr, fg, fb, a);
            }
        }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static int hash(int i, int j, int k) {
        int h = i * 374761393 + j * 668265263 + k * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        return h ^ (h >>> 16);
    }

    /** Smooth noise in 0..1 from a hashed lattice, bilinear with a smoothstep. */
    private static float valueNoise(float x, float y, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        float v00 = (hash(xi, yi, seed) & 1023) / 1023f, v10 = (hash(xi + 1, yi, seed) & 1023) / 1023f;
        float v01 = (hash(xi, yi + 1, seed) & 1023) / 1023f, v11 = (hash(xi + 1, yi + 1, seed) & 1023) / 1023f;
        return lerp(lerp(v00, v10, fx), lerp(v01, v11, fx), fy);
    }

    /**
     * One quad of a roof's curved piece, cut from the roof texture laid over its corner square as the
     * rectangles are laid over theirs.
     */
    private void drawFan(float[] fan, float s, ShuttleSpriteCache roof, float tile, float a) {
        float bx0 = sx(fan[0], s), bx1 = sx(fan[2], s), by0 = sy(fan[1], s), by1 = sy(fan[3], s);
        float minX = Math.min(bx0, bx1), minY = Math.min(by0, by1);
        float w = Math.abs(bx1 - bx0), h = Math.abs(by1 - by0);
        if (w <= 0f || h <= 0f) return;
        if (Math.max(bx0, bx1) < cam.vpX() || minX > cam.vpX() + cam.vpW()
                || Math.max(by0, by1) < cam.vpY() || minY > cam.vpY() + cam.vpH()) return;
        float[] patch = new float[8];
        for (int k = 0; k < 4; k++) {
            patch[2 * k] = (sx(fan[4 + 2 * k], s) - minX) / w;
            patch[2 * k + 1] = (sy(fan[5 + 2 * k], s) - minY) / h;
        }
        if (roof == null || roof.pxW <= 0) {
            PolyMesh mesh = poly();
            mesh.appendQuad(sx(fan[4], s), sy(fan[5], s), sx(fan[6], s), sy(fan[7], s), sx(fan[8], s),
                    sy(fan[9], s), sx(fan[10], s), sy(fan[11], s), .30f, .29f, .28f, alpha);
            out.addPoly(RenderLayer.GROUND, mesh);
            return;
        }
        int pw = roof.pxW, ph = roof.pxH;
        int srcW = Math.max(1, Math.min(pw, Math.round((fan[2] - fan[0]) / tile * pw)));
        int srcH = Math.max(1, Math.min(ph, Math.round((fan[3] - fan[1]) / tile * ph)));
        int srcX = Math.min(pw - srcW, Math.round((float) (fan[0] / tile - Math.floor(fan[0] / tile)) * pw));
        int srcY = Math.min(ph - srcH, Math.round((float) (fan[1] / tile - Math.floor(fan[1] / tile)) * ph));
        out.addSheetPatch(RenderLayer.GROUND, roof.sprite, srcX, srcY, srcW, srcH,
                minX + w / 2f, minY + h / 2f, w, h, a, patch);
    }

    // ----- traffic -----

    /** Places every craft in the air at {@code time}, in the band of depth of the level it flies beside. */
    private void fly(float time) {
        for (Level level : levels) level.flying.clear();
        if (backdrop.craft().isEmpty()) return;
        aloft.clear();
        traffic.place(time, aloft);
        for (float[] c : aloft) {
            float z = c[2];
            if (z >= backdrop.depthMeters()) continue;
            Level band = null;
            for (Level level : levels) if (level.top <= z && (band == null || level.top > band.top)) band = level;
            c[4] = flyingHull((int) c[4], backdrop.craft().size());
            if (band != null) band.flying.add(c);
        }
        for (VoidBackdrop.Call call : backdrop.calls()) {
            if (call.slip() < 0 || call.slip() >= backdrop.slips().size()) continue;
            float[] c = callCraft(call, backdrop.slips().get(call.slip()), battleSeconds);
            if (c == null) continue;
            c[4] = Math.max(0, backdrop.craft().indexOf(call.hull()));
            Level band = null;
            for (Level level : levels) if (level.top <= c[2] && (band == null || level.top > band.top)) band = level;
            if (band != null) band.flying.add(c);
        }
        // The lane lights run a chase along each lane, so the lane reads as a lane between the craft.
        for (float[] light : laneLights) {
            double run = time * LANE_LIGHT_HZ - light[3] / (LANE_LIGHT_SPACING * 6f);
            float phase = (float) (run - Math.floor(run));
            float glow = phase < .25f ? 1f : .35f;
            Level band = null;
            for (Level level : levels) if (level.top <= light[2] && (band == null || level.top > band.top)) band = level;
            if (band != null) band.flying.add(new float[]{light[0], light[1], light[2], 0f, -1f, glow});
        }
    }

    /** A skyway's marker light, {@code glow} bright, under the haze at its depth. */
    private void drawLaneLight(float x, float y, float z, float glow) {
        float s = scale(z);
        float qx = sx(x, s), qy = sy(y, s);
        if (qx < cam.vpX() || qx > cam.vpX() + cam.vpW() || qy < cam.vpY() || qy > cam.vpY() + cam.vpH()) return;
        float a = glow * (1f - hazeAt(z) * .5f) * alpha;
        rect(x - .35f, y - .35f, x + .35f, y + .35f, z, .55f, .85f, 1f, a);
        rect(x - .9f, y - .9f, x + .9f, y + .9f, z, .3f, .6f, 1f, .22f * a);
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
        // Round, as the lamps are: a square of light read as a block stuck to the hull.
        lamp(tx, ty, z, 1f, .62f, .3f, g * 1.4f, .9f * a);
    }

    /**
     * The paths craft fly, each on its own irregular schedule ({@link VoidTraffic}): into each bay, swooping in
     * from one side and slowing to the mouth, and out of it, gathering speed away to the other; down the city's
     * skyways, turning at a crossing onto the street across (climbing or sinking to its depth on the turn) or
     * running a street's length, keeping to the right; and the hulls parked on each terrace, between its bays.
     */
    private void plotTraffic(VoidBackdrop backdrop) {
        traffic.clear();
        laneLights.clear();
        if (backdrop.craft().isEmpty()) return;
        int bay = 0;
        for (List<Level> structure : structures) for (int i = 0; i + 1 < structure.size(); i++) {
            Level level = structure.get(i), below = structure.get(i + 1);
            float reach = below.outset - level.outset;
            for (float[] mouth : level.mouths) {
                float[] run = level.runs.get((int) mouth[0]);
                boolean horizontal = run[1] == run[3];
                float wx = horizontal ? mouth[1] : run[0], wy = horizontal ? run[1] : mouth[1];
                float nx = run[4], ny = run[5], tx = horizontal ? 1f : 0f, ty = horizontal ? 0f : 1f;
                float zIn = level.bottom - MOUTH_H / 2f - 1f;
                bay++;
                for (int way = 0; way < 2; way++) {
                    // Arrivals swoop in from one side, departures leave toward the other; how far by the bay.
                    float side = ((bay + way) % 2 == 0 ? 1f : -1f) * (.6f + VoidTraffic.hash(bay, way) % 100 / 100f);
                    float[] far = {wx + nx * (reach + APPROACH_METERS) + tx * side * 70f,
                            wy + ny * (reach + APPROACH_METERS) + ty * side * 70f, level.bottom - 30f};
                    float[] mid = {wx + nx * (reach + 45f) + tx * side * 18f, wy + ny * (reach + 45f) + ty * side * 18f,
                            level.bottom - 16f};
                    float[] edge = {wx + nx * reach, wy + ny * reach, level.bottom - 7f};
                    float[] in = {wx, wy, zIn};
                    if (way == 0) {
                        traffic.add(new float[][]{far, mid, edge, in}, BAY_SPEED, BAY_INTERVAL,
                                VoidTraffic.Pace.ARRIVE, bay * 2);
                    } else {
                        traffic.add(new float[][]{in, edge, mid, far}, BAY_SPEED, BAY_INTERVAL,
                                VoidTraffic.Pace.DEPART, bay * 2 + 1);
                    }
                }
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
        // Slips: craft fly in down a canyon to berth at its head, slowing, and back out, gathering speed.
        int slip = 0;
        for (VoidBackdrop.Slip berth : backdrop.slips()) {
            boolean called = false;
            for (VoidBackdrop.Call call : backdrop.calls()) called |= call.slip() == slip;
            float dx = berth.x1() - berth.x0(), dy = berth.y1() - berth.y0();
            float length = (float) Math.hypot(dx, dy);
            if (length <= 0f) continue;
            float ux = dx / length, uy = dy / length;
            float ex = -uy * 6f, ey = ux * 6f;
            for (float t = LANE_LIGHT_SPACING / 2f; t < length; t += LANE_LIGHT_SPACING) {
                float px = berth.x0() + dx * t / length, py = berth.y0() + dy * t / length;
                laneLights.add(new float[]{px + ex, py + ey, berth.depthMeters(), t});
                laneLights.add(new float[]{px - ex, py - ey, berth.depthMeters(), t});
            }
            float z = berth.depthMeters();
            if (called) {
                slip++;
                continue;
            }
            // Arrivals keep the right of the canyon, departures the other side; both come down from above it.
            for (int way = 0; way < 2; way++) {
                float side = way == 0 ? 2.5f : -2.5f;
                float sx = -uy * side, sy = ux * side;
                float[] far = {berth.x0() - ux * APPROACH_METERS + sx, berth.y0() - uy * APPROACH_METERS + sy, z - 25f};
                float[] mouth = {berth.x0() + sx, berth.y0() + sy, z - 6f};
                float[] mid = {berth.x0() + dx * .6f + sx, berth.y0() + dy * .6f + sy, z};
                float[] home = {berth.x1() + sx, berth.y1() + sy, z};
                if (way == 0) {
                    traffic.add(new float[][]{far, mouth, mid, home}, SLIP_SPEED, SLIP_INTERVAL,
                            VoidTraffic.Pace.ARRIVE, 500 + slip * 2);
                } else {
                    traffic.add(new float[][]{home, mid, mouth, far}, SLIP_SPEED, SLIP_INTERVAL,
                            VoidTraffic.Pace.DEPART, 500 + slip * 2 + 1);
                }
            }
            slip++;
        }
        List<VoidBackdrop.Skyway> ways = backdrop.skyways();
        if (!ways.isEmpty()) {
            for (VoidBackdrop.Skyway way : ways) {
                float dx = way.x1() - way.x0(), dy = way.y1() - way.y0();
                float length = (float) Math.hypot(dx, dy);
                if (length <= 0f) continue;
                // Marker lights down both edges of the lane.
                float ex = -dy / length * 7f, ey = dx / length * 7f;
                for (float t = LANE_LIGHT_SPACING / 2f; t < length; t += LANE_LIGHT_SPACING) {
                    float px = way.x0() + dx * t / length, py = way.y0() + dy * t / length;
                    laneLights.add(new float[]{px + ex, py + ey, way.depthMeters(), t});
                    laneLights.add(new float[]{px - ex, py - ey, way.depthMeters(), t});
                }
            }
            // Routes picked until there are enough, skipping a pick with no room for its turn.
            int count = Math.min(STREET_ROUTES_MAX, ways.size() * STREET_ROUTES_PER_SKYWAY);
            for (int r = 0, made = 0; made < count && r < count * 4; r++) {
                float[][] controls = streetRoute(ways, r);
                if (controls == null) continue;
                traffic.add(controls, SKYWAY_SPEED, SKYWAY_INTERVAL, VoidTraffic.Pace.CRUISE, 1000 + r);
                made++;
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
                // A long, gentle bend rather than a ruled line: the middle pushed off to one side.
                float bend = (lane == 0 ? 1f : -1f) * 60f;
                float[] a = {ox - dx * SKYWAY_METERS / 2f, oy - dy * SKYWAY_METERS / 2f, z};
                float[] m = {ox - dy * bend, oy + dx * bend, z + 15f};
                float[] b = {ox + dx * SKYWAY_METERS / 2f, oy + dy * SKYWAY_METERS / 2f, z};
                traffic.add(lane == 0 ? new float[][]{a, m, b} : new float[][]{b, m, a}, SKYWAY_SPEED,
                        SKYWAY_INTERVAL, VoidTraffic.Pace.CRUISE, 2000 + k * 2 + lane);
            }
        }
    }

    /**
     * Street route {@code r} through the skyways: along one street, keeping to its right, and, for most routes, a
     * rounded turn at a crossing onto the street across, to that street's end; the depth eases from one street's
     * to the other's through the turn. Null when there is no room for the route picked.
     */
    static float[][] streetRoute(List<VoidBackdrop.Skyway> ways, int r) {
        int h = VoidTraffic.hash(r, 77);
        VoidBackdrop.Skyway a = ways.get(h % ways.size());
        boolean forward = (h >>> 8 & 1) == 0;
        float ax0 = forward ? a.x0() : a.x1(), ay0 = forward ? a.y0() : a.y1();
        float ax1 = forward ? a.x1() : a.x0(), ay1 = forward ? a.y1() : a.y0();
        float la = (float) Math.hypot(ax1 - ax0, ay1 - ay0);
        if (la <= 0f) return null;
        float dax = (ax1 - ax0) / la, day = (ay1 - ay0) / la;
        // Keep right: the lane's offset from the street's middle, to the right of the way it is flown.
        float rax = -day * STREET_LANE_OFFSET, ray = dax * STREET_LANE_OFFSET;
        float za = a.depthMeters();
        List<VoidBackdrop.Skyway> crossings = new ArrayList<>();
        for (VoidBackdrop.Skyway b : ways) if (b != a && crosses(a, b) != null) crossings.add(b);
        if (crossings.isEmpty() || (h >>> 9) % 4 == 0) {
            float[] start = {ax0 + rax, ay0 + ray, za}, end = {ax1 + rax, ay1 + ray, za};
            float[] mid = {(ax0 + ax1) / 2f + rax, (ay0 + ay1) / 2f + ray, za};
            return new float[][]{start, mid, end};
        }
        VoidBackdrop.Skyway b = crossings.get((h >>> 11) % crossings.size());
        float[] p = crosses(a, b);
        // The turn needs room before it on this street and after it on the next.
        float before = (p[0] - ax0) * dax + (p[1] - ay0) * day;
        if (before < TURN_METERS * 2f) return null;
        boolean bForward = (h >>> 20 & 1) == 0;
        float bx1 = bForward ? b.x1() : b.x0(), by1 = bForward ? b.y1() : b.y0();
        float lb = (float) Math.hypot(bx1 - p[0], by1 - p[1]);
        if (lb < TURN_METERS * 2f) {
            bx1 = bForward ? b.x0() : b.x1();
            by1 = bForward ? b.y0() : b.y1();
            lb = (float) Math.hypot(bx1 - p[0], by1 - p[1]);
        }
        if (lb < TURN_METERS * 2f) return null;
        float dbx = (bx1 - p[0]) / lb, dby = (by1 - p[1]) / lb;
        float rbx = -dby * STREET_LANE_OFFSET, rby = dbx * STREET_LANE_OFFSET;
        float zb = b.depthMeters();
        float[] start = {ax0 + rax, ay0 + ray, za};
        float[] in = {p[0] - dax * TURN_METERS + rax, p[1] - day * TURN_METERS + ray, za};
        float[] corner = {p[0] + (rax + rbx) * .5f, p[1] + (ray + rby) * .5f, (za + zb) / 2f};
        float[] out = {p[0] + dbx * TURN_METERS + rbx, p[1] + dby * TURN_METERS + rby, zb};
        float[] end = {bx1 + rbx, by1 + rby, zb};
        return new float[][]{start, in, corner, out, end};
    }

    /** Where two skyways cross, within both their lengths, or null where they do not. */
    private static float[] crosses(VoidBackdrop.Skyway a, VoidBackdrop.Skyway b) {
        boolean aAlongX = a.y0() == a.y1(), bAlongX = b.y0() == b.y1();
        if (aAlongX == bAlongX) return null;
        VoidBackdrop.Skyway h = aAlongX ? a : b, v = aAlongX ? b : a;
        float x = v.x0(), y = h.y0();
        if (x < Math.min(h.x0(), h.x1()) || x > Math.max(h.x0(), h.x1())) return null;
        if (y < Math.min(v.y0(), v.y1()) || y > Math.max(v.y0(), v.y1())) return null;
        return new float[]{x, y};
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
        // The towers on one tier are one structure, so where their terraces grow into each other below they join
        // in one podium, as the halls and platforms of a station rise from one base, rather than overlapping.
        Map<Integer, boolean[]> byTier = new TreeMap<>();
        for (boolean[] footprint : pieces(towers, mw, mh)) {
            int tier = 0;
            for (int k = 0; elevation != null && k < footprint.length; k++) {
                int x = k % mw - m, y = k / mw - m;
                if (footprint[k] && elevation.inBounds(x, y)) tier = Math.max(tier, elevation.eyeTier(x, y));
            }
            boolean[] group = byTier.computeIfAbsent(tier, t -> new boolean[mw * mh]);
            for (int k = 0; k < footprint.length; k++) group[k] |= footprint[k];
        }
        for (Map.Entry<Integer, boolean[]> group : byTier.entrySet()) {
            addStructure(group.getValue(), mw, mh, m, -group.getKey() * ElevationMap.TIER_RISE_METERS, backdrop);
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
        boolean[] above = null;
        List<float[]> aboveCorners = List.of();
        // The roof's corner radius, by the structure's place: the playing surface's own corners stay as the map
        // lays them, and only its terraces round.
        int first = 0;
        while (first < footprint.length && !footprint[first]) first++;
        int roofRadius = top <= 0f ? 0 : roofRadius(first);
        for (int i = 0; i <= tiers.size(); i++) {
            Level level = new Level();
            level.top = i == 0 ? top : tiers.get(i - 1).depthMeters();
            level.crown = i == 0;
            level.bottom = i < tiers.size() ? tiers.get(i).depthMeters() : backdrop.depthMeters();
            level.outset = i == 0 ? 0f : tiers.get(i - 1).outsetMeters();
            level.surface = i == 0 && top <= 0f;
            boolean[] mask = i == 0 ? footprint : dilate(footprint, mw, mh, Math.round(level.outset));
            findRuns(mask, mw, mh, m, level.runs);
            // Rounded on the roof's centres: each terrace's radius grows by its outset, so the step is even.
            int radius = level.surface || (roofRadius == 0 && i == 0) ? 0 : roofRadius + Math.round(level.outset);
            roundCorners(level.runs, radius, level.corners);
            // A terrace's roof is only the ring left open round the level above: the part under that level is
            // never seen, and drawn whole it would lay the roof, its veil and its haze over the walls above
            // wherever they lean toward the view's centre. Its own rounded corners' squares come out too, and
            // their curved pieces go in as triangles: the quarter disc inside its own arc, and the sliver
            // between the level above's arc and that level's square corner.
            boolean[] open = mask.clone();
            if (above != null) for (int k = 0; k < open.length; k++) open[k] &= !above[k];
            List<float[]> slivers = new ArrayList<>(aboveCorners);
            for (float[] corner : level.corners) {
                float[] box = cornerSquare(corner);
                for (int y = Math.round(box[1]); y < Math.round(box[3]); y++) {
                    for (int x = Math.round(box[0]); x < Math.round(box[2]); x++) open[(y + m) * mw + x + m] = false;
                }
                // Round the level above's corner on the same centre, the roof here is the ring between the arcs;
                // the level above's square corner lies inside this one's square, so its sliver is in the ring.
                float inner = 0f;
                for (float[] up : aboveCorners) {
                    if (up[0] == corner[0] && up[1] == corner[1] && up[3] == corner[3] && up[6] == corner[6]) {
                        inner = up[2];
                        slivers.remove(up);
                    }
                }
                float[][] arc = arc(corner);
                for (int k = 0; k < CORNER_FACETS; k++) {
                    float[] a = arc[k], b = arc[k + 1];
                    float ax = corner[0] + a[2] * inner, ay = corner[1] + a[3] * inner;
                    float bx = corner[0] + b[2] * inner, by = corner[1] + b[3] * inner;
                    level.fans.add(new float[]{box[0], box[1], box[2], box[3], ax, ay, a[0], a[1], b[0], b[1], bx, by});
                }
            }
            for (float[] corner : slivers) {
                float[] box = cornerSquare(corner);
                float px = corner[0] + corner[2] * (corner[3] + corner[5]);
                float py = corner[1] + corner[2] * (corner[4] + corner[6]);
                float[][] arc = arc(corner);
                for (int k = 0; k < CORNER_FACETS; k++) {
                    level.fans.add(new float[]{box[0], box[1], box[2], box[3], px, py,
                            arc[k][0], arc[k][1], arc[k + 1][0], arc[k + 1][1], px, py});
                }
            }
            mergeRects(open, mw, mh, 1, -m, -m, level.roofs);
            above = mask;
            aboveCorners = level.corners;
            if (i > 0) {
                level.footings = structure.get(i - 1).runs;
                level.reach = level.outset - structure.get(i - 1).outset;
            }
            structure.add(level);
        }
        structures.add(structure);
    }

    /**
     * Where a called craft is at {@code seconds} of the battle, as {@code {x, y, z, heading, hull, fade}} with the
     * hull left for the caller, or null while it is not in the air or at its berth: it flies in over {@link
     * #CALL_FLIGHT_SECONDS}, slowing, to stand at the berth from its arrival, and out again from its departure,
     * gathering speed, turning to face the way out as it goes.
     */
    static float[] callCraft(VoidBackdrop.Call call, VoidBackdrop.Slip berth, float seconds) {
        float s, turn;
        if (seconds < call.arriveSeconds() - CALL_FLIGHT_SECONDS || seconds >= call.departSeconds() + CALL_FLIGHT_SECONDS) {
            return null;
        } else if (seconds < call.arriveSeconds()) {
            float u = 1f - (call.arriveSeconds() - seconds) / CALL_FLIGHT_SECONDS;
            s = 1f - (1f - u) * (1f - u);
            turn = 0f;
        } else if (seconds < call.departSeconds()) {
            s = 1f;
            turn = 0f;
        } else {
            float u = (seconds - call.departSeconds()) / CALL_FLIGHT_SECONDS;
            s = 1f - u * u;
            turn = Math.min(1f, u * CALL_FLIGHT_SECONDS / CALL_TURN_SECONDS);
        }
        float dx = berth.x1() - berth.x0(), dy = berth.y1() - berth.y0();
        float length = (float) Math.hypot(dx, dy);
        float ux = length > 0f ? dx / length : 1f, uy = length > 0f ? dy / length : 0f;
        // From far out over the drop, down to the canyon's mouth, then along it to the berth.
        float approach = APPROACH_METERS, along = approach + length;
        float d = s * along, x, y, z;
        if (d < approach) {
            float k = d / approach;
            x = berth.x0() - ux * (approach - d);
            y = berth.y0() - uy * (approach - d);
            z = berth.depthMeters() - 25f * (1f - k);
        } else {
            x = berth.x0() + ux * (d - approach);
            y = berth.y0() + uy * (d - approach);
            z = berth.depthMeters();
        }
        float heading = (float) Math.atan2(uy, ux) + turn * (float) Math.PI;
        float fade = Math.min(1f, d / (approach * .3f));
        return new float[]{x, y, z, heading, 0f, fade};
    }

    /**
     * A structure's roof corner radius from {@code key} (where its footprint starts), hashed so towers laid on a
     * regular grid still take every radius: a plain sum of the key's multiples falls on one radius whenever the
     * grid's pitch and the mask's width share a factor with the number of radii.
     */
    static int roofRadius(int key) {
        return ROOF_RADII[VoidTraffic.hash(key, 41) % ROOF_RADII.length];
    }

    /** The hull a craft flies, from its hash: mostly one of the first two, now and then any of them. */
    static int flyingHull(int hash, int hulls) {
        int pick = hash >>> 8;
        return hash % 100 < COMMON_HULL_PERCENT ? pick % Math.min(2, hulls) : pick % hulls;
    }

    /**
     * Rounds the convex corners of a footprint's runs to {@code radius} cells, or less where a run is too short to
     * take it: each run meeting a convex corner is cut back by the corner's radius, and the corner is added to
     * {@code into} as {@code {centreX, centreY, radius, hX, hY, vX, vY}}. Concave corners stay square.
     */
    static void roundCorners(List<float[]> runs, int radius, List<float[]> into) {
        if (radius <= 0) return;
        // Every convex corner first, from the runs as found; then the cuts, so a run with two ends rounded is
        // measured whole for both.
        List<float[][]> found = new ArrayList<>();
        List<int[]> ends = new ArrayList<>();
        for (float[] h : runs) {
            if (h[1] != h[3]) continue;
            for (int he = 0; he < 2; he++) {
                float px = he == 0 ? h[0] : h[2], py = h[1];
                float ix = he == 0 ? 1f : -1f;
                for (float[] v : runs) {
                    if (v[0] != v[2] || v[0] != px) continue;
                    int ve;
                    if (v[1] == py) ve = 0;
                    else if (v[3] == py) ve = 1;
                    else continue;
                    float iy = ve == 0 ? 1f : -1f;
                    // Convex: each run leaves the corner toward the inside of the other.
                    if (ix != -v[4] || iy != -h[5]) continue;
                    int r = Math.min(radius, (int) Math.floor(Math.min(h[2] - h[0], v[3] - v[1]) / 2f));
                    if (r < 2) continue;
                    found.add(new float[][]{h, v, {px + ix * r, py + iy * r, r, 0f, h[5], v[4], 0f}});
                    ends.add(new int[]{he, ve});
                }
            }
        }
        for (int k = 0; k < found.size(); k++) {
            float[] h = found.get(k)[0], v = found.get(k)[1], corner = found.get(k)[2];
            float r = corner[2];
            if (ends.get(k)[0] == 0) h[0] += r;
            else h[2] -= r;
            if (ends.get(k)[1] == 0) v[1] += r;
            else v[3] -= r;
            into.add(corner);
        }
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
