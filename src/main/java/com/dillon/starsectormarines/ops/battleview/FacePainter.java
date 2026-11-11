package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.WallScars;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.render2d.PolyMesh;

import java.util.Arrays;

/**
 * Paints one cell's length of a stood-up wall ({@link BuildingFaces}) in its dress ({@link WallDress}): the material
 * shaded from a dark foot to a lit eave, its course, the world's weather at its foot, the trim (plinth, storey lines,
 * eave, corners), each storey's bay, a downpipe, and the damage the battle has done (soot over blown windows, torn
 * holes, a top broken off where the roof fell in, rubble on the ground below). A face is addressed as {@code t} along
 * the wall from its start and {@code z} up it as a share of its height, its foot on the ground and its top at the
 * wall's lift about the view's centre. GL-free.
 */
final class FacePainter {

    /** The wall faces, drawn before every roof. */
    final PolyMesh faces = new PolyMesh(1024);
    /**
     * Rubble at the foot of hurt walls ({@link WallRubble}), on the ground under the units: {@link #DEBRIS} floats a
     * piece, its centre and size on screen, its turn, its sheet frame and its tint {@code r, g, b, a}.
     */
    private float[] debris = new float[DEBRIS * 64];
    private int debrisCount;
    static final int DEBRIS = 9;
    private final float[] placed = new float[WallRubble.PIECE * WallRubble.MOST];

    private PolyMesh into = faces;
    private float ax, ay, bx, by, fax, fay, fbx, fby, sx, sy, lift, alpha, nxPx, nyPx;
    private final float[] corners = new float[12];
    private final float[] cutBuf = new float[3];
    private final float[] xy = new float[8];

    void reset() {
        faces.reset();
        glow.reset();
        debrisCount = 0;
        into = faces;
    }

    /** The lit windows of a night ({@link #night}), drawn over the night shade. */
    final PolyMesh glow = new PolyMesh(256);
    /** Whether this frame is a night's, so some windows are lit ({@link #lit}). */
    boolean night;
    /** Whether the storey being painted is above the ground floor, where the lit windows are. */
    private boolean upper;

    /** The share of upper windows lit at night. UNTUNED. */
    static final float LIT_SHARE = .3f;
    /** A lit window's warm glass. */
    private static final float LIT_R = 1f, LIT_G = .78f, LIT_B = .42f;

    /** Whether the window of bay {@code seed} is lit at night: about {@link #LIT_SHARE} of them, by its place. */
    static boolean lit(int seed) {
        return BuildingFaces.hash(seed, 140) < LIT_SHARE;
    }

    /** A part of the wall that glows rather than takes the light, into {@link #glow}. */
    private void glowQuad(float t0, float t1, float z0, float z1, float r, float g, float b) {
        PolyMesh was = into;
        into = glow;
        quad(t0, t1, z0, z1, r, g, b);
        into = was;
    }

    /** Draws what follows into {@code mesh} ({@link #faces} unless said otherwise). */
    void into(PolyMesh mesh) {
        into = mesh;
    }

    /**
     * The wall now being painted: its top's line from {@code a} to {@code b} on screen as it lies on the ground (the
     * roof's edge, before its lift), its foot's line from {@code fa} to {@code fb} (the wall's outer side;
     * {@link BuildingFaces#segments}), the view's centre {@code s}, its lift, its outward normal on screen scaled to
     * one cell, and its alpha.
     */
    void wall(float ax, float ay, float bx, float by, float fax, float fay, float fbx, float fby, float sx, float sy,
              float lift, float nxPx, float nyPx, float alpha) {
        this.ax = ax; this.ay = ay; this.bx = bx; this.by = by;
        this.fax = fax; this.fay = fay; this.fbx = fbx; this.fby = fby;
        this.sx = sx; this.sy = sy; this.lift = lift; this.nxPx = nxPx; this.nyPx = nyPx; this.alpha = alpha;
    }

    /** One flat part of the wall, {@code t0..t1} along and {@code z0..z1} up. */
    void quad(float t0, float t1, float z0, float z1, float r, float g, float b) {
        if (t1 <= t0 || z1 <= z0) return;
        point(t0, z0, 0);
        point(t1, z0, 2);
        point(t1, z1, 4);
        point(t0, z1, 6);
        into.appendQuad(xy[0], xy[1], xy[2], xy[3], xy[4], xy[5], xy[6], xy[7], r, g, b, alpha);
    }

    /** One part of the wall shaded from {@code low} at its foot to {@code high} at its top. */
    void shaded(float t0, float t1, float z0, float z1, float lr, float lg, float lb, float hr, float hg, float hb) {
        if (t1 <= t0 || z1 <= z0) return;
        point(t0, z0, 0);
        point(t1, z0, 2);
        point(t1, z1, 4);
        point(t0, z1, 6);
        corners[0] = lr; corners[1] = lg; corners[2] = lb;
        corners[3] = lr; corners[4] = lg; corners[5] = lb;
        corners[6] = hr; corners[7] = hg; corners[8] = hb;
        corners[9] = hr; corners[10] = hg; corners[11] = hb;
        into.appendShadedQuad(xy[0], xy[1], xy[2], xy[3], xy[4], xy[5], xy[6], xy[7], corners, 0, alpha);
    }

    /**
     * The point {@code t} along the wall and {@code z} up it, into {@link #xy} at {@code at}: between its foot on the
     * ground and its top at the lift about the view's centre.
     */
    private void point(float t, float z, int at) {
        xy[at] = BuildingFaces.onFace(fax + (fbx - fax) * t, ax + (bx - ax) * t, sx, lift, z);
        xy[at + 1] = BuildingFaces.onFace(fay + (fby - fay) * t, ay + (by - ay) * t, sy, lift, z);
    }

    /**
     * The rubble a wall cell has shed ({@link WallRubble#place}) laid on the ground at the current wall's foot, each
     * piece tinted by the wall's material, {@code fog} its shade.
     */
    void rubble(int cell, int amount, float[] wall, float fog) {
        float cellPx = (float) Math.sqrt(nxPx * nxPx + nyPx * nyPx);
        if (amount <= 0 || cellPx < 1e-3f) return;
        int n = WallRubble.place(cell, amount, placed, 0);
        float nx = nxPx / cellPx, ny = nyPx / cellPx;
        float square = WallRubble.towardWall(nx, ny);
        // Broken wall is mostly its grey core, with a hint of its finish.
        float r = rubbleTint(wall[0]) * fog, g = rubbleTint(wall[1]) * fog, b = rubbleTint(wall[2]) * fog;
        if (debris.length < (debrisCount + n) * DEBRIS) debris = Arrays.copyOf(debris, debris.length * 2);
        for (int k = 0; k < n; k++) {
            int p = k * WallRubble.PIECE, o = debrisCount * DEBRIS;
            float t = placed[p], out = placed[p + 1] * cellPx;
            debris[o] = fax + (fbx - fax) * t + nx * out;
            debris[o + 1] = fay + (fby - fay) * t + ny * out;
            debris[o + 2] = placed[p + 2] * cellPx;
            debris[o + 3] = square + placed[p + 3];
            debris[o + 4] = placed[p + 4];
            debris[o + 5] = r;
            debris[o + 6] = g;
            debris[o + 7] = b;
            debris[o + 8] = alpha;
            debrisCount++;
        }
    }

    private static float rubbleTint(float wall) {
        return .5f + .5f * Math.min(1f, wall * 1.25f);
    }

    /** This frame's rubble pieces, {@link #DEBRIS} floats each; {@link #debrisCount} of them. */
    float[] debris() {
        return debris;
    }

    int debrisCount() {
        return debrisCount;
    }

    /**
     * Everything a standing wall's cell shows. {@code top} is how much of it stands (1 whole, less where its building
     * is cut away), and {@code broken} whether its roof has fallen in and taken its upper storeys with it.
     *
     * @param ground   what the map laid at its foot
     * @param corners  {@link BuildingFaces#CORNER_START} and {@link BuildingFaces#CORNER_END}
     * @param along    its place along its wall, cells
     * @param glassHurt    how far gone the battle's window at its foot is: 0 whole, 1 shot out to an empty frame
     * @param upperWindows whether its building's upper storeys have windows ({@link BuildingFaces#upperWindows})
     * @param light    its side's light and its fog, multiplied
     * @param detail   whether a cell is big enough on screen for bays and course
     */
    void paint(WallDress.Material m, WallDress.Rhythm rhythm, BuildingKind kind, BuildingFaces.Look look,
               BuildingFaces.Segment ground, int corners, int along, int cell, float meters, int storeys,
               float top, boolean broken, float hurt, float glassHurt, boolean upperWindows, float light,
               boolean detail) {
        float j = 1f + (BuildingFaces.hash(cell, 31) - .5f) * .07f;
        float r = m.wall()[0] * light * j, g = m.wall()[1] * light * j, b = m.wall()[2] * light * j;
        float soot = 1f - .55f * Math.max(0f, hurt - .2f) / .8f;
        float story = 1f / storeys;
        float[] cut = null;
        if (broken) {
            // The upper storeys fell in with the roof: the wall stands to a ragged edge, three teeth a cell.
            cut = cutBuf;
            for (int k = 0; k < 3; k++) cut[k] = Math.min(top, WallDress.brokenTop(cell, storeys, (k + .5f) / 3f));
        }
        // The material, dark at the foot where the ground holds it, lit toward the eave; soot darkens the top.
        if (cut == null) {
            shaded(0f, 1f, 0f, top, r * .78f, g * .78f, b * .78f, r * 1.04f * soot, g * 1.04f * soot, b * 1.04f * soot);
        } else {
            for (int k = 0; k < 3; k++) {
                float s = .55f * soot;
                shaded(k / 3f, (k + 1) / 3f, 0f, cut[k], r * .78f, g * .78f, b * .78f, r * s, g * s, b * s);
            }
        }
        float height = cut == null ? top : Math.max(cut[0], Math.max(cut[1], cut[2]));
        // Too small on screen for trim to read: the shaded material alone, at a piece a cell.
        if (!detail) return;
        course(m, r, g, b, height, storeys);
        // The world's weather at the foot, blending up into the wall.
        WallDress.Weather weather = m.weather();
        if (weather != WallDress.Weather.NONE) {
            float[] w = WallDress.weatherColour(weather);
            float up = Math.min(height, WallDress.weatherMeters(weather) / meters * (.7f + .6f * j - .35f));
            float wl = weather == WallDress.Weather.SNOW || weather == WallDress.Weather.SAND ? light : 1f;
            float mix = .82f;
            shaded(0f, 1f, 0f, up, w[0] * wl, w[1] * wl, w[2] * wl,
                    r * .8f * mix + w[0] * wl * (1f - mix), g * .8f * mix + w[1] * wl * (1f - mix),
                    b * .8f * mix + w[2] * wl * (1f - mix));
        }
        // A toxic world's run-off streaking down from the eave.
        if (m.stain() > 0f && cut == null && BuildingFaces.hash(cell, 41) < m.stain()) {
            float u = .15f + .6f * BuildingFaces.hash(cell, 42);
            float[] w = WallDress.weatherColour(WallDress.Weather.SLUDGE);
            shaded(u, u + .14f, top * .35f, top, r, g, b, w[0] * light * .8f, w[1] * light * .8f, w[2] * light * .8f);
        }
        float tr = m.trim()[0] * light, tg = m.trim()[1] * light, tb = m.trim()[2] * light;
        // Plinth: the darker band the wall stands on.
        float plinth = Math.min(height, m.plinthMeters() / meters);
        quad(0f, 1f, 0f, plinth, tr * .62f, tg * .62f, tb * .62f);
        quad(0f, 1f, plinth, Math.min(height, plinth + .025f), tr * .9f, tg * .9f, tb * .9f);
        // A line at every storey.
        for (int f = 1; f < storeys; f++) {
            float z = f * story;
            if (z + .012f >= height) break;
            quad(0f, 1f, z - .012f, z + .012f, tr * .85f, tg * .85f, tb * .85f);
        }
        // Corners picked out in the trim, full height.
        if ((corners & BuildingFaces.CORNER_START) != 0) cornerPost(0f, .09f, height, tr, tg, tb, cut);
        if ((corners & BuildingFaces.CORNER_END) != 0) cornerPost(.91f, 1f, height, tr, tg, tb, cut);
        // The eave's band under the roof's edge; a cut-away wall's top gets a cap line instead.
        if (cut == null) {
            if (top >= .999f) {
                float eave = Math.min(.3f, m.eaveMeters() / meters);
                shaded(0f, 1f, 1f - eave, 1f, tr * .75f, tg * .75f, tb * .75f, tr * soot, tg * soot, tb * soot);
                if (weather == WallDress.Weather.SNOW) quad(0f, 1f, 1f - eave * .35f, 1f, .93f * light, .95f * light,
                        .98f * light);
            } else {
                quad(0f, 1f, top - Math.min(.03f, top * .3f), top, tr, tg, tb);
            }
        } else {
            // Charred and broken along the ragged edge.
            for (int k = 0; k < 3; k++) quad(k / 3f, (k + 1) / 3f, cut[k] - .04f, cut[k], .1f, .09f, .08f);
        }
        // Bays storey by storey, clipped to what stands.
        for (int f = 0; f < storeys; f++) {
            float lo = f * story, hi = (f + 1) * story;
            if (lo >= height - .02f) break;
            float clip = cut == null ? height : Math.min(cut[0], Math.min(cut[1], cut[2]));
            if (lo >= clip - .02f && f > 0) break;
            float limit = Math.min(hi, cut == null ? height : clip);
            int seed = cell * 7 + f;
            upper = f > 0;
            if (f == 0 && (ground == BuildingFaces.Segment.DOOR || ground == BuildingFaces.Segment.LOADING)) {
                door(m, look, ground == BuildingFaces.Segment.LOADING, lo, limit, story, light, seed, hurt);
            } else {
                WallDress.Bay bay = f == 0 ? WallDress.ground(rhythm, kind, look, ground, along)
                        : WallDress.upper(rhythm, kind, look, ground, upperWindows, along, f, storeys);
                boolean shotOut = f == 0 && glassHurt >= 1f && bay != WallDress.Bay.BLIND;
                bay(m, rhythm, look, bay, lo, limit, story, light, seed, f == 0 ? Math.max(hurt, glassHurt) : hurt,
                        shotOut, r, g, b);
            }
        }
        upper = false;
        if (WallDress.pipe(rhythm, along)) {
            float pr = .34f * light, pg = .35f * light, pb = .36f * light;
            quad(.03f, .08f, plinth, height - .02f, pr, pg, pb);
            for (int f = 0; f < storeys; f++) {
                float z = (f + .5f) * story;
                if (z < height) quad(.01f, .1f, z - .015f, z + .015f, pr * .7f, pg * .7f, pb * .7f);
            }
        }
    }

    private void cornerPost(float t0, float t1, float height, float tr, float tg, float tb, float[] cut) {
        float h = cut == null ? height : (t0 < .5f ? cut[0] : cut[2]);
        shaded(t0, t1, 0f, h, tr * .7f, tg * .7f, tb * .7f, tr * .98f, tg * .98f, tb * .98f);
    }

    /** The wall's course across the face: block joints, panel seams or sheet ribs. */
    private void course(WallDress.Material m, float r, float g, float b, float height, int storeys) {
        float story = 1f / storeys;
        switch (m.course()) {
            case BLOCK:
                for (int f = 0; f < storeys * 3; f++) {
                    float z = (f + 1) * story / 3f;
                    if (z >= height - .01f) break;
                    quad(0f, 1f, z - .005f, z + .005f, r * .84f, g * .84f, b * .84f);
                }
                break;
            case PANEL:
                quad(0f, .025f, 0f, height, r * .78f, g * .78f, b * .78f);
                quad(.025f, .045f, 0f, height, r * 1.08f, g * 1.08f, b * 1.08f);
                for (int f = 0; f < storeys * 2; f++) {
                    float z = (f + 1) * story / 2f;
                    if (z >= height - .01f) break;
                    quad(0f, 1f, z - .006f, z + .006f, r * .82f, g * .82f, b * .82f);
                }
                break;
            case RIB:
                for (int k = 0; k < 4; k++) {
                    float t = (k + .5f) / 4f;
                    quad(t - .05f, t, 0f, height, r * .82f, g * .82f, b * .82f);
                    quad(t, t + .03f, 0f, height, r * 1.1f, g * 1.1f, b * 1.1f);
                }
                break;
            default:
                break;
        }
    }

    /** A door at the foot of the wall: a framed leaf, an airlock, or a works' roller door with hazard jambs. */
    private void door(WallDress.Material m, BuildingFaces.Look look, boolean loading, float lo, float hi,
                      float story, float light, int seed, float hurt) {
        float tr = m.trim()[0] * light, tg = m.trim()[1] * light, tb = m.trim()[2] * light;
        if (loading) {
            float top = Math.min(hi, lo + story * .8f);
            quad(.04f, .96f, lo, Math.min(hi, top + story * .06f), tr * .6f, tg * .6f, tb * .6f);
            float dr = .44f * light, dg = .45f * light, db = .44f * light;
            shaded(.1f, .9f, lo, top, dr * .7f, dg * .7f, db * .7f, dr, dg, db);
            for (int k = 1; k < 6; k++) {
                float z = lo + (top - lo) * k / 6f;
                quad(.1f, .9f, z - .01f, z + .01f, dr * .6f, dg * .6f, db * .6f);
            }
            if (m.hazard()) {
                for (int k = 0; k < 6; k++) {
                    float z0 = lo + (top - lo) * k / 6f, z1 = lo + (top - lo) * (k + 1) / 6f;
                    boolean yellow = k % 2 == 0;
                    float yr = yellow ? .86f : .12f, yg = yellow ? .68f : .11f, yb = yellow ? .12f : .1f;
                    quad(.04f, .1f, z0, z1, yr * light, yg * light, yb * light);
                    quad(.9f, .96f, z0, z1, yr * light, yg * light, yb * light);
                }
            }
            return;
        }
        boolean lock = look == BuildingFaces.Look.SEALED || look == BuildingFaces.Look.SHIELDED;
        float top = Math.min(hi, lo + story * (lock ? .74f : .7f));
        // Frame.
        quad(.18f, .82f, lo, Math.min(hi, top + story * .06f), tr * .8f, tg * .8f, tb * .8f);
        float dr = m.door()[0] * light, dg = m.door()[1] * light, db = m.door()[2] * light;
        if (hurt > .5f && BuildingFaces.hash(seed, 12) < hurt) { dr = .05f; dg = .045f; db = .045f; }
        shaded(.26f, .74f, lo, top, dr * .75f, dg * .75f, db * .75f, dr, dg, db);
        if (lock) {
            quad(.49f, .51f, lo, top, dr * .5f, dg * .5f, db * .5f);
            quad(.26f, .74f, top - story * .05f, top - story * .02f, .86f * light, .66f * light, .14f * light);
        } else {
            // A lintel shadow and a step.
            quad(.22f, .78f, top + story * .06f, Math.min(hi, top + story * .11f), tr * .45f, tg * .45f, tb * .45f);
        }
        if (m.hazard() && lock) {
            for (int k = 0; k < 5; k++) {
                float z0 = lo + (top - lo) * k / 5f, z1 = lo + (top - lo) * (k + 1) / 5f;
                boolean yellow = k % 2 == 0;
                float yr = yellow ? .86f : .12f, yg = yellow ? .68f : .11f, yb = yellow ? .12f : .1f;
                quad(.18f, .25f, z0, z1, yr * light, yg * light, yb * light);
                quad(.75f, .82f, z0, z1, yr * light, yg * light, yb * light);
            }
        }
    }

    /** One storey's bay: windows framed and silled, slits, ports, a shopfront, a glazed band or a vent. */
    private void bay(WallDress.Material m, WallDress.Rhythm rhythm, BuildingFaces.Look look, WallDress.Bay bay,
                     float lo, float hi, float story, float light, int seed, float hurt, boolean shotOut, float r,
                     float g, float b) {
        if (bay == WallDress.Bay.BLIND) return;
        float tr = m.trim()[0] * light, tg = m.trim()[1] * light, tb = m.trim()[2] * light;
        if (shotOut) {
            // The battle's window shot out: its frame's dark opening, low enough to climb through, as the simulation
            // now has it, the wall above it sooted.
            float z0 = lo + story * .12f, z1 = Math.min(hi, lo + story * .72f);
            quad(.14f, .86f, z0 - story * .03f, Math.min(hi, z1 + story * .03f), tr * .55f, tg * .55f, tb * .55f);
            quad(.18f, .82f, z0, z1, .05f, .045f, .045f);
            shaded(.22f, .78f, z1, Math.min(hi + story * .35f, z1 + story * .5f), .06f, .055f, .05f, r * .9f,
                    g * .9f, b * .9f);
            return;
        }
        boolean blown = hurt > .3f && BuildingFaces.hash(seed, 11) < hurt;
        float gl = blown ? .05f : 1f;
        float gr = m.glass()[0] * light * gl, gg = m.glass()[1] * light * gl, gb = m.glass()[2] * light * gl;
        // Glass catches the sky toward its top; a few panes curtained warm.
        float hiR = gr * 1.7f, hiG = gg * 1.6f, hiB = gb * 1.5f;
        if (!blown && BuildingFaces.hash(seed, 13) < .22f) { hiR = .55f * light; hiG = .44f * light; hiB = .30f * light; }
        float sootTop = -1f;
        switch (bay) {
            case WINDOW: case TALL: {
                boolean tall = bay == WallDress.Bay.TALL;
                float w = tall ? rhythm.windowWidth() * .9f : rhythm.windowWidth();
                float h = tall ? .72f : rhythm.windowHeight();
                float t0 = .5f - w / 2f, t1 = .5f + w / 2f;
                float z0 = lo + story * (tall ? .12f : .3f), z1 = Math.min(hi, z0 + story * h);
                if (z1 <= z0) return;
                boolean deep = look == BuildingFaces.Look.INSULATED;
                if (deep) quad(t0 - .07f, t1 + .07f, z0 - story * .06f, Math.min(hi, z1 + story * .06f),
                        r * .42f, g * .42f, b * .42f);
                else quad(t0 - .04f, t1 + .04f, z0 - story * .03f, Math.min(hi, z1 + story * .03f), tr * .9f,
                        tg * .9f, tb * .9f);
                shaded(t0, t1, z0, z1, gr, gg, gb, hiR, hiG, hiB);
                if (night && upper && !blown && lit(seed)) {
                    // A room lit behind it: warm glass, its lower half the brighter, through the night's shade.
                    glowQuad(t0, t1, z0, z1, LIT_R * .8f, LIT_G * .8f, LIT_B * .8f);
                    glowQuad(t0 + .02f, t1 - .02f, z0, z0 + (z1 - z0) * .55f, LIT_R, LIT_G, LIT_B);
                }
                // Mullion and transom.
                quad(.495f, .505f, z0, z1, tr * .8f, tg * .8f, tb * .8f);
                if (tall) quad(t0, t1, z0 + (z1 - z0) * .7f, z0 + (z1 - z0) * .7f + story * .02f, tr * .8f,
                        tg * .8f, tb * .8f);
                // The sill, snowed on a frozen world.
                float sill = story * .045f;
                boolean snow = m.weather() == WallDress.Weather.SNOW;
                quad(t0 - .07f, t1 + .07f, z0 - sill, z0, snow ? .93f * light : tr, snow ? .95f * light : tg,
                        snow ? .98f * light : tb);
                if (rhythm.shutters() && !tall) {
                    float dr = m.door()[0] * light, dg = m.door()[1] * light, db = m.door()[2] * light;
                    quad(t0 - .16f, t0 - .045f, z0, z1, dr, dg, db);
                    quad(t1 + .045f, t1 + .16f, z0, z1, dr, dg, db);
                }
                sootTop = z1;
                break;
            }
            case SLIT: {
                float z0 = lo + story * .5f, z1 = Math.min(hi, lo + story * .6f);
                quad(.16f, .84f, Math.min(hi, z1), Math.min(hi, z1 + story * .07f), tr * .8f, tg * .8f, tb * .8f);
                quad(.2f, .8f, z0, z1, gr * .6f, gg * .6f, gb * .6f);
                sootTop = z1;
                break;
            }
            case PORT: {
                float z0 = lo + story * .36f, z1 = Math.min(hi, lo + story * .64f);
                quad(.34f, .66f, z0, z1, tr, tg, tb);
                shaded(.39f, .61f, z0 + story * .04f, z1 - story * .04f, gr, gg, gb, hiR, hiG, hiB);
                sootTop = z1;
                break;
            }
            case SHOP: {
                float z0 = lo + story * .08f, z1 = Math.min(hi, lo + story * .64f);
                quad(.04f, .96f, z0 - story * .04f, z1, tr * .7f, tg * .7f, tb * .7f);
                shaded(.07f, .93f, z0, z1 - story * .02f, gr, gg, gb, hiR, hiG, hiB);
                quad(.495f, .505f, z0, z1, tr * .7f, tg * .7f, tb * .7f);
                // The awning, striped in the shop's colour.
                float az0 = Math.min(hi, z1), az1 = Math.min(hi, z1 + story * .12f);
                float[] door = m.door();
                for (int k = 0; k < 4; k++) {
                    boolean pale = k % 2 == 1;
                    quad(k / 4f, (k + 1) / 4f, az0, az1, pale ? tr : door[0] * light * 1.4f,
                            pale ? tg : door[1] * light * 1.4f, pale ? tb : door[2] * light * 1.4f);
                }
                quad(0f, 1f, az0 - story * .02f, az0, door[0] * light * .6f, door[1] * light * .6f,
                        door[2] * light * .6f);
                sootTop = az1;
                break;
            }
            case CLERESTORY: {
                float z0 = lo + story * .45f, z1 = Math.min(hi, lo + story * .78f);
                if (z1 <= z0) return;
                quad(0f, 1f, z0 - story * .03f, Math.min(hi, z1 + story * .03f), tr * .8f, tg * .8f, tb * .8f);
                shaded(0f, 1f, z0, z1, gr, gg, gb, hiR, hiG, hiB);
                for (int k = 0; k < 4; k++) quad(k / 4f - .012f, k / 4f + .012f, z0, z1, tr * .7f, tg * .7f, tb * .7f);
                sootTop = z1;
                break;
            }
            case VENT: {
                float z0 = lo + story * .3f, z1 = Math.min(hi, lo + story * .62f);
                quad(.3f, .7f, z0, z1, tr * .7f, tg * .7f, tb * .7f);
                for (int k = 0; k < 4; k++) {
                    float z = z0 + (z1 - z0) * (k + .5f) / 4f;
                    quad(.33f, .67f, z - story * .025f, z + story * .02f, .12f * light, .12f * light, .13f * light);
                }
                break;
            }
            default:
                break;
        }
        // Fire through a blown window blackens the wall above it.
        if (blown && sootTop > 0f) {
            float up = Math.min(hi + story * .35f, sootTop + story * .55f);
            shaded(.28f, .72f, sootTop, up, .06f, .055f, .05f, r * .9f, g * .9f, b * .9f);
        }
    }

    /** Holes torn through a badly hurt wall: a dark core, ragged round its edge, with the masonry showing. */
    /**
     * The hits that landed on the wall just painted ({@link FaceScars#lay}), each where it struck: a chipped pock with
     * a pale rim, a deep gouge, or a burst's soot round a punched hole. Only what stands below {@code top} (a share
     * of the wall's {@code meters}) shows.
     */
    void scars(float[] laid, int count, float meters, float top, float light, WallDress.Material m) {
        float rr = Math.min(1f, m.wall()[0] * 1.12f) * light, rg = Math.min(1f, m.wall()[1] * 1.12f) * light;
        float rb = Math.min(1f, m.wall()[2] * 1.12f) * light;
        for (int i = 0; i < count; i++) {
            int o = i * FaceScars.STRIDE;
            float u = laid[o], z = laid[o + 1] / meters, size = laid[o + 2];
            int mark = (int) laid[o + 3];
            if (z >= top) continue;
            if (mark == WallScars.Mark.CHIP.ordinal()) {
                float w = .13f, h = SCAR_SCALE * .13f / meters;
                quad(u - w * .8f, u + w * .8f, z - h * .8f, Math.min(top, z + h * .8f), rr, rg, rb);
                quad(u - w * .45f, u + w * .45f, z - h * .45f, Math.min(top, z + h * .45f), .07f, .06f, .06f);
            } else if (mark == WallScars.Mark.GOUGE.ordinal()) {
                float w = .24f, h = SCAR_SCALE * .22f / meters;
                quad(u - w * .75f, u + w * .75f, z - h * .7f, Math.min(top, z + h * .7f), rr * .9f, rg * .9f,
                        rb * .9f);
                quad(u - w * .45f, u + w * .45f, z - h * .45f, Math.min(top, z + h * .45f), .05f, .045f, .045f);
                quad(u - w * .15f, u + w * .6f, z + h * .2f, Math.min(top, z + h * .55f), .05f, .045f, .045f);
            } else {
                float w = Math.max(.5f, Math.min(1.2f, size * .7f)), h = SCAR_SCALE * w * .9f / meters;
                float soot = .12f * light;
                shadedBlast(u, w, z, h, top, soot, m, light);
                float hw = w * .32f, hh = h * .3f;
                quad(u - hw, u + hw, z - hh, Math.min(top, z + hh), .04f, .035f, .035f);
            }
        }
    }

    /**
     * Metres up a face per cell along it, for a mark's shape: a cell along a face is drawn about as wide as a storey
     * is tall, so a round pock is that much taller in metres than it is wide in cells.
     */
    private static final float SCAR_SCALE = 3f;

    /** A burst's soot: dark at its heart, fading out to the wall's own colour at its edge. */
    private void shadedBlast(float u, float w, float z, float h, float top, float soot, WallDress.Material m,
                             float light) {
        float r = m.wall()[0] * light * .55f, g = m.wall()[1] * light * .55f, b = m.wall()[2] * light * .55f;
        shaded(u - w / 2f, u + w / 2f, Math.max(0f, z - h / 2f), Math.min(top, z), r, g, b, soot, soot, soot);
        shaded(u - w / 2f, u + w / 2f, Math.min(top, z), Math.min(top, z + h / 2f), soot, soot, soot, r, g, b);
    }

    /**
     * A razed building's wall: a broken shell a storey high at most, its corners standing tallest, soot-black along
     * its ragged top, its window holes dark (its rubble is laid by {@link #rubble}).
     */
    void shell(WallDress.Material m, int cell, boolean corner, float light, boolean inner) {
        float r = m.wall()[0] * light * (inner ? .62f : 1f), g = m.wall()[1] * light * (inner ? .62f : 1f);
        float b = m.wall()[2] * light * (inner ? .62f : 1f);
        for (int k = 0; k < 3; k++) {
            float t0 = k / 3f, t1 = (k + 1) / 3f;
            float h = WallDress.shellTop(cell, corner, (k + .5f) / 3f);
            shaded(t0, t1, 0f, h, r * .7f, g * .7f, b * .7f, r * .42f, g * .42f, b * .42f);
            quad(t0, t1, h - .06f, h, .08f, .075f, .07f);
        }
        if (!inner && BuildingFaces.hash(cell, 64) < .5f) {
            float h = WallDress.shellTop(cell, corner, .5f);
            quad(.36f, .64f, .3f, Math.min(h - .06f, .75f), .05f, .045f, .045f);
        }
    }
}
