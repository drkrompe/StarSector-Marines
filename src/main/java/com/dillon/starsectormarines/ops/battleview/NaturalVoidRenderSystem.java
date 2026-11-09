package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalRise;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;

import java.util.ArrayList;
import java.util.List;

/**
 * The land falling away through a map's void cells (the natural void styles, `voids.md`): a ragged rock lip,
 * strata walls stepping down in ledges, and the floor far below, running water for a gorge river. Drawn first
 * in the ground layer, as {@link VoidBackdropRenderSystem} draws the city drop, so the ground tiles cover it
 * everywhere but the void cells.
 *
 * <p>The shape is {@link NaturalVoidField}'s, worked out once per map and sun. The view is the city drop's
 * pinhole camera: a point {@code d} metres down stands at {@code eye / (eye + d)} of its distance from the
 * view's centre, so a ledge is its plan scaled about the centre, and a wall is the quad between its line at
 * its top's scale and at its foot's. Everything is drawn deepest first, a step's walls after the ledge at
 * their foot and before the ledge at their top, so a wall turned from the camera falls under the ground above
 * it and a wall facing the camera stands over the ledges below.
 *
 * <p><b>Rises.</b> A second instance draws a map's {@link NaturalRise}, the same shape turned up: a ravine's
 * walls standing over the walkable floor. Higher is nearer the eye, so a rise's ledges scale away from the
 * view's centre and its walls lean out; it is drawn lowest first, and a face turned from the camera is not
 * drawn at all, since the front faces and the top cover where it would be. Unlike a drop it is drawn after
 * the ground tiles, so a rise that leans out past its own cells (a rock stack's top, seen from beside it) is
 * seen whole; but before every unit, so a unit at its foot is never hidden, and one standing where a top
 * leans over the floor is drawn over the rock. Its talus and its shadow on the floor are laid over the tiles
 * first, with the fray pass.
 *
 * <p>Presentation only. It reads the map's void cells, the sight tag that tells a rise's from a drop's, and
 * the {@link NaturalVoid} or {@link NaturalRise}, never the battle.
 */
final class NaturalVoidRenderSystem implements RenderSystem {

    /** The depth tint: the cool of shade deep in a drop, and how much of it the floor takes. */
    static final float DEEP_R = .16f, DEEP_G = .22f, DEEP_B = .30f, DEEP_MIX = .28f;
    /**
     * Cells the drop is worked out past each edge of the map, its edge cells carried outward, so a drop that
     * runs off the map runs on rather than ending in a wall; the field's cells are the map's shifted by this.
     */
    static final int MARGIN = 24;
    /** Water: how fast it runs, metres a second, and how long one pattern lasts before it fades into the next. */
    static final float FLOW_SPEED = 1.6f, FLOW_PERIOD = 5f;
    /** How dark a rise's shadow lies on the floor at its foot, at its fullest. */
    static final float FLOOR_SHADE = .32f;

    private final BattleSprites sprites;
    /** This instance draws the map's rise rather than its drop. */
    private final boolean rising;
    /** A lava rift's floor, light and air, drawn into this drop's order and over the ground after it. */
    private final MoltenVoidRenderSystem molten;
    /** A glacial crevasse's floor, mist and blown snow, drawn into this drop's order and over the ground after it. */
    private final GlacialVoidRenderSystem glacial;
    /** The drop this instance draws is a lava rift. */
    private boolean lava;
    /** The drop this instance draws is an open pit: benches, a haul road down them, conveyors out. */
    private boolean pit;
    /** An open pit's haul road, and the conveyors it draws over the ground. */
    private List<PitVoidLook.Ramp> ramps = List.of();
    private List<NaturalVoid.Belt> belts = List.of();
    private List<PitMachines.Machine> machines = List.of();
    /** The drop this instance draws is a glacial crevasse. */
    private boolean ice;
    private SunLight sun;

    private CellTopology builtFor;
    private Object builtWith;
    private String wallTexture;
    private float wallTileMeters, eyeMeters;
    private int seed;
    private String builtSun;
    private int mapW, mapH;
    private NaturalVoidField field;
    /** Each band's flat colour per quad, before depth tint; the floor's is worked out each frame. */
    private float[][] colours;
    private float[] frayColours;

    private DrawList out;
    private BattleCamera cam;
    private float eye, cx, cy, alpha;
    private final List<PolyMesh> polys = new ArrayList<>();
    private int polysUsed;

    NaturalVoidRenderSystem(BattleSprites sprites) {
        this(sprites, false);
    }

    NaturalVoidRenderSystem(BattleSprites sprites, boolean rising) {
        this.sprites = sprites;
        this.rising = rising;
        this.molten = new MoltenVoidRenderSystem(this, sprites);
        this.glacial = new GlacialVoidRenderSystem(this, sprites);
    }

    /** A lava rift's drawing over the ground beside it, which the renderer lists after the {@link #fray()}. */
    RenderSystem molten() {
        return molten;
    }

    /** A glacial crevasse's drawing over the ground beside it, which the renderer lists after the {@link #fray()}. */
    RenderSystem glacial() {
        return glacial;
    }

    /** The map's drop or rise, whichever this instance draws, or null. */
    private Object look(CellTopology topology) {
        return rising ? topology.naturalRise() : topology.naturalVoid();
    }

    void useSun(SunLight sun) {
        this.sun = sun;
    }

    @Override public RenderLayer layer() { return RenderLayer.GROUND; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        CellTopology topology = ctx.sim.getTopology();
        Object look = look(topology);
        if (look == null) return;
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        if (cellPx <= 0f) return;
        this.out = out;
        this.cam = cam;
        this.alpha = ctx.alphaMult;
        if (!rising) this.polysUsed = 0;
        this.cx = cam.cellToScreenX(cam.panCellX());
        this.cy = cam.cellToScreenY(cam.panCellY());
        ensureBuilt(ctx, look);
        this.eye = eyeMeters;
        float time = FireLight.clock(ctx.animationTimeSeconds);
        int n = field.steps();
        if (rising) {
            // Lowest first: the foot, then each face and the ledge on top of it, the plateau last.
            for (int k = 0; k <= n; k++) {
                drawBand(field.bands.get(k), false, time);
                if (k < n) drawWalls(field.walls.get(k));
            }
            return;
        }
        for (int k = n; k >= 0; k--) {
            drawBand(field.bands.get(k), k == n, time);
            if (pit) drawMachines(k, -1);
            if (k >= 1) drawWalls(field.walls.get(k - 1));
            if (k >= 1 && pit) {
                drawRamps(k - 1);
                drawMachines(k, k - 1);
            }
        }
        drawBridgeSides();
    }

    /** Works the shape out again when the map, its look or the sun has changed. */
    private void ensureBuilt(RenderContext ctx, Object look) {
        CellTopology topology = ctx.sim.getTopology();
        String sunKey = sun == null ? "none" : sun.azimuthDegrees() + "/" + sun.elevationDegrees();
        if (builtFor != topology || builtWith != look || !sunKey.equals(builtSun)) {
            build(topology, ctx.sim.getGrid(), look);
            builtFor = topology;
            builtWith = look;
            builtSun = sunKey;
        }
    }

    private void build(CellTopology topology, NavigationGrid grid, Object look) {
        NaturalVoidField.Shape shape;
        if (look instanceof NaturalRise rise) {
            shape = NaturalVoidField.Shape.of(rise).shifted(MARGIN, MARGIN);
            wallTexture = rise.wallTexture();
            wallTileMeters = rise.wallTileMeters();
            eyeMeters = rise.eyeMeters();
            seed = rise.seed();
        } else {
            NaturalVoid drop = (NaturalVoid) look;
            shape = NaturalVoidField.Shape.of(drop).shifted(MARGIN, MARGIN);
            wallTexture = drop.wallTexture();
            wallTileMeters = drop.wallTileMeters();
            eyeMeters = drop.eyeMeters();
            seed = drop.seed();
        }
        lava = !rising && MoltenVoidRenderSystem.molten((NaturalVoid) look);
        pit = !rising && PitVoidLook.pit((NaturalVoid) look);
        belts = pit ? ((NaturalVoid) look).belts() : List.of();
        ice = !rising && GlacialVoidRenderSystem.glacial((NaturalVoid) look);
        // A map with both tells them apart as the battle does: a rise blocks sight, a drop does not.
        boolean both = topology.naturalVoid() != null && topology.naturalRise() != null;
        int w = topology.getWidth(), h = topology.getHeight();
        mapW = w;
        mapH = h;
        int fw = w + 2 * MARGIN, fh = h + 2 * MARGIN;
        boolean[] voidCell = new boolean[fw * fh];
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < fw; x++) {
                int mx = Math.max(0, Math.min(w - 1, x - MARGIN)), my = Math.max(0, Math.min(h - 1, y - MARGIN));
                boolean cell = topology.getGroundKind(mx, my) == GroundKind.VOID;
                if (cell && (rising || both)) cell = rising != grid.hasTag(mx, my, CellTag.SEE_THROUGH);
                voidCell[y * fw + x] = cell;
            }
        }
        // A sun that does not cast still lights: straight down from the default bearing, a long way up.
        float sx = sun == null ? -.7f : sun.dirX(), sy = sun == null ? -.7f : sun.dirY();
        float rise = sun == null || !sun.casts() ? 6f : sun.risePerCell();
        field = new NaturalVoidField(fw, fh, voidCell, shape, sx, sy, rise);
        int n = field.steps();
        colours = new float[n + 1][];
        // A drop's floor is worked out each frame; a rise's top is coloured as the rest.
        for (int k = 0; k < (rising ? n + 1 : n); k++) {
            NaturalVoidField.Band band = field.bands.get(k);
            float[] c = new float[band.count * 3];
            for (int q = 0; q < band.count; q++) {
                if (rising && k == n) plateau(band, q, seed, c);
                else if (lava) LavaVoidLook.rock(band, q, k, seed, field, c);
                else if (pit) PitVoidLook.rock(band, q, k, seed, c);
                else if (ice) IceVoidLook.ice(band, q, k, seed, c);
                else albedo(band, q, k, seed, c);
            }
            colours[k] = c;
        }
        NaturalVoidField.Band fray = field.fray;
        frayColours = new float[fray.count * 3];
        for (int q = 0; q < fray.count; q++) {
            if (lava) LavaVoidLook.rock(fray, q, 0, seed, field, frayColours);
            else if (pit) PitVoidLook.rock(fray, q, 0, seed, frayColours);
            else if (ice) IceVoidLook.ice(fray, q, 0, seed, frayColours);
            else albedo(fray, q, 0, seed, frayColours);
        }
        ramps = pit ? PitVoidLook.ramps(field) : List.of();
        machines = pit ? PitMachines.place(field, ramps, seed) : List.of();
    }

    /**
     * The lip fraying onto the ground beside the drop, drawn after the ground tiles: the second half of this
     * system, which the renderer lists after the ground.
     */
    RenderSystem fray() {
        return RenderSystem.of(RenderLayer.GROUND, (ctx, out) -> {
            Object look = look(ctx.sim.getTopology());
            if (look == null || ctx.camera.cellPxSize() <= 0f) return;
            // A rise's fray is drawn before the rise itself, so it is the first of the two to run in a frame.
            if (rising) this.polysUsed = 0;
            ensureBuilt(ctx, look);
            this.out = out;
            this.cam = ctx.camera;
            this.alpha = ctx.alphaMult;
            this.cx = cam.cellToScreenX(cam.panCellX());
            this.cy = cam.cellToScreenY(cam.panCellY());
            NaturalVoidField.Band band = field.fray;
            float margin = cam.cellPxSize() * 2f;
            PolyMesh mesh = poly();
            float[] qd = band.quads, at = band.attrs;
            for (int q = 0; q < band.count; q++) {
                int o = q * 8;
                if (!inView(at[o], at[o + 1], 1f, margin) || !onMap(at[o], at[o + 1])) continue;
                // A lava rift's scorched lip ends hard against the plain, softened by the rift's light over it; a
                // crevasse's cornice goes over to the snowfield's own tone, so its end needs no fading. A partly
                // covering piece would also thin the ground target's alpha, which the composite reads as shade.
                if ((lava || ice) && at[o + 7] < 1f) continue;
                mesh.appendQuad(sx(qd[o], 1f), sy(qd[o + 1], 1f), sx(qd[o + 2], 1f), sy(qd[o + 3], 1f),
                        sx(qd[o + 4], 1f), sy(qd[o + 5], 1f), sx(qd[o + 6], 1f), sy(qd[o + 7], 1f),
                        frayColours[q * 3], frayColours[q * 3 + 1], frayColours[q * 3 + 2], at[o + 7] * alpha);
            }
            // A rise's shadow over the floor at its foot, and over the fray.
            NaturalVoidField.Band shade = field.floorShade;
            float strength = FLOOR_SHADE * (sun == null ? 1f : Math.min(1f, sun.shadowStrength() / .75f));
            qd = shade.quads;
            at = shade.attrs;
            for (int q = 0; q < shade.count; q++) {
                int o = q * 8;
                if (!inView(at[o], at[o + 1], 1f, margin) || !onMap(at[o], at[o + 1])) continue;
                mesh.appendQuad(sx(qd[o], 1f), sy(qd[o + 1], 1f), sx(qd[o + 2], 1f), sy(qd[o + 3], 1f),
                        sx(qd[o + 4], 1f), sy(qd[o + 5], 1f), sx(qd[o + 6], 1f), sy(qd[o + 7], 1f),
                        .02f, .03f, .06f, at[o + 7] * strength * alpha);
            }
            if (!mesh.isEmpty()) out.addPoly(RenderLayer.GROUND, mesh);
            if (pit && !belts.isEmpty()) drawBelts(FireLight.clock(ctx.animationTimeSeconds));
        });
    }

    // ----- projection, shared with the lava rift's drawing -----

    NaturalVoidField field() { return field; }

    BattleCamera camera() { return cam; }

    DrawList drawList() { return out; }

    float alpha() { return alpha; }

    int seed() { return seed; }

    /**
     * Readies the projection for a pass drawn later in the frame than this one, as the fray is: false when this map
     * has no drop or its shape is not yet worked out.
     */
    boolean begin(RenderContext ctx, DrawList out) {
        if (ctx.sim.getTopology().naturalVoid() == null || field == null || builtFor != ctx.sim.getTopology()) return false;
        this.out = out;
        this.cam = ctx.camera;
        this.alpha = ctx.alphaMult;
        this.eye = eyeMeters;
        this.cx = cam.cellToScreenX(cam.panCellX());
        this.cy = cam.cellToScreenY(cam.panCellY());
        return true;
    }

    float scale(float depth) { return eye / (eye + depth); }

    /**
     * Whether the rock stands between the eye, over the view's centre, and a point of the field {@code z} metres
     * down: true where the line of sight runs into a wall, a ledge, a bridge's deck or off the field.
     */
    boolean hidden(float x, float y, float z) {
        float ccx = cam.panCellX() + MARGIN, ccy = cam.panCellY() + MARGIN;
        for (int k = 0; k <= 3; k++) {
            float zk = z * k / 4f;
            // The point on the line of sight at depth zk: where the eye's ray through the point passes it.
            float f = scale(z) / scale(zk);
            float rx = ccx + (x - ccx) * f, ry = ccy + (y - ccy) * f;
            int ix = (int) Math.floor(rx), iy = (int) Math.floor(ry);
            if (ix < 0 || iy < 0 || ix >= field.w || iy >= field.h) return true;
            if (k == 0 && !field.open[iy * field.w + ix]) return true;
            if (field.bridge[iy * field.w + ix] && zk < NaturalVoidField.DECK_METERS) return true;
            if (field.depthAt(rx, ry) < zk - .01f) return true;
        }
        return false;
    }

    /** Where a point of the field (map cells shifted by {@link #MARGIN}) stands on screen at scale {@code s}. */
    float sx(float cellX, float s) { return cx + (cam.cellToScreenX(cellX - MARGIN) - cx) * s; }

    float sy(float cellY, float s) { return cy + (cam.cellToScreenY(cellY - MARGIN) - cy) * s; }

    /** Whether a point of the field lies on the map: surface pieces past its edge are not drawn. */
    boolean onMap(float x, float y) {
        return x >= MARGIN && y >= MARGIN && x < MARGIN + mapW && y < MARGIN + mapH;
    }

    /** Whether a plan point at scale {@code s} lands within the view, with a margin in pixels. */
    boolean inView(float x, float y, float s, float margin) {
        float px = sx(x, s), py = sy(y, s);
        return px >= cam.vpX() - margin && px <= cam.vpX() + cam.vpW() + margin
                && py >= cam.vpY() - margin && py <= cam.vpY() + cam.vpH() + margin;
    }

    PolyMesh poly() {
        if (polysUsed == polys.size()) polys.add(new PolyMesh(256));
        PolyMesh mesh = polys.get(polysUsed++);
        mesh.reset();
        return mesh;
    }

    // ----- the look -----

    /**
     * A lip or ledge piece's colour: weathered rock and earth on the lip, rock with moss and fern on the ledges,
     * a little scree where a ledge breaks over the face below, all lit as the field lit it.
     */
    private static void albedo(NaturalVoidField.Band band, int q, int k, int seed, float[] into) {
        float[] a = band.attrs;
        int o = q * 8;
        float x = a[o], y = a[o + 1], light = a[o + 2], edge = a[o + 3];
        float n1 = NaturalVoidField.Noise.fbm(x * .55f, y * .55f, seed * 7 + 11);
        float n2 = NaturalVoidField.Noise.value(x * 3.1f, y * 3.1f, seed * 7 + 13);
        float r, g, b;
        if (k == 0) {
            // The lip: rock breaking through earth, the turf above fraying into it.
            float soil = clamp01(.5f + n1);
            r = lerp(.46f, .33f, soil); g = lerp(.42f, .27f, soil); b = lerp(.35f, .19f, soil);
            float turf = clamp01((edge - .25f) * 1.6f + n2 * .35f);
            r = lerp(r, .34f, turf * .75f); g = lerp(g, .41f, turf * .75f); b = lerp(b, .19f, turf * .75f);
        } else {
            float moss = clamp01((n1 + .15f) * 2.2f - edge * .35f);
            r = lerp(.50f, .25f, moss); g = lerp(.45f, .33f, moss); b = lerp(.37f, .16f, moss);
            // Scree: pale broken stone where the ledge breaks over the face below.
            float scree = clamp01(1f - edge * 2.2f) * clamp01(.6f + n2);
            r = lerp(r, .60f, scree * .5f); g = lerp(g, .56f, scree * .5f); b = lerp(b, .50f, scree * .5f);
        }
        float grain = .86f + .28f * (n2 * .5f + .5f);
        float lit = Math.min(1.15f, light);
        into[q * 3] = r * grain * lit;
        into[q * 3 + 1] = g * grain * lit;
        into[q * 3 + 2] = b * grain * lit;
    }

    /**
     * A rise's top: the plateau beyond the rim, turf with scrub and dark clumps of wood, bare rock breaking
     * through along the rim itself.
     */
    private static void plateau(NaturalVoidField.Band band, int q, int seed, float[] into) {
        float[] a = band.attrs;
        int o = q * 8;
        float x = a[o], y = a[o + 1], light = a[o + 2], inside = a[o + 3];
        float n1 = NaturalVoidField.Noise.fbm(x * .3f, y * .3f, seed * 7 + 17);
        float n2 = NaturalVoidField.Noise.value(x * 1.3f, y * 1.3f, seed * 7 + 19);
        float n3 = NaturalVoidField.Noise.value(x * .7f, y * .7f, seed * 7 + 23);
        float r = .33f, g = .37f, b = .19f;
        // Wood: dark crowns in clumps, thickest a little back from the rim.
        float wood = clamp01((n1 + .5f * n3 + .1f) * 2.4f) * clamp01((inside - .8f) * .8f);
        r = lerp(r, .15f, wood); g = lerp(g, .24f, wood); b = lerp(b, .11f, wood);
        // Rock breaking through at the rim, and a fringe of scrub just behind it.
        float rim = clamp01(1f - inside * 1.4f + n2 * .3f);
        r = lerp(r, .50f, rim * .8f); g = lerp(g, .46f, rim * .8f); b = lerp(b, .39f, rim * .8f);
        float grain = .93f + .14f * (n2 * .5f + .5f);
        float lit = Math.min(1f, light);
        into[q * 3] = r * grain * lit;
        into[q * 3 + 1] = g * grain * lit;
        into[q * 3 + 2] = b * grain * lit;
    }

    private void drawBand(NaturalVoidField.Band band, boolean floor, float time) {
        if (floor && lava) {
            molten.drawFloor(band, time);
            return;
        }
        if (floor && ice) {
            glacial.drawFloor(band, time);
            return;
        }
        float floorDepth = Math.max(1f, field.depth[field.steps()]);
        float deepR = ice ? IceVoidLook.DEEP_R : DEEP_R, deepG = ice ? IceVoidLook.DEEP_G : DEEP_G;
        float deepB = ice ? IceVoidLook.DEEP_B : DEEP_B;
        float margin = cam.cellPxSize() * 2f;
        PolyMesh mesh = poly();
        float[] qd = band.quads, at = band.attrs;
        float[] c = floor ? null : colours[band.step];
        float p1 = frac(time / FLOW_PERIOD), p2 = frac(time / FLOW_PERIOD + .5f);
        float w1 = 1f - Math.abs(2f * p1 - 1f);
        for (int q = 0; q < band.count; q++) {
            int o = q * 8;
            // Each piece at its own depth along the drop: its centre, and each corner.
            float zc = band.z(q, 4);
            if (!inView(at[o], at[o + 1], scale(zc), margin)) continue;
            if (band.step == 0 && !onMap(at[o], at[o + 1])) continue;
            float tint = rising ? 0f : ice ? IceVoidLook.deepTint(zc, floorDepth) : DEEP_MIX * zc / floorDepth;
            float r, g, b;
            if (floor && pit) {
                float[] f = PitVoidLook.floor(at[o], at[o + 1], at[o + 2], at[o + 3], at[o + 6], seed);
                r = f[0]; g = f[1]; b = f[2];
            } else if (floor) {
                float x = at[o], y = at[o + 1], light = at[o + 2], edge = at[o + 3];
                float fx = at[o + 4], fy = at[o + 5], sky = at[o + 6], sunlit = at[o + 7];
                float along = x * fx + y * fy, across = -x * fy + y * fx;
                float run = FLOW_SPEED * FLOW_PERIOD;
                float s1 = NaturalVoidField.Noise.fbm((along - p1 * run) * .28f, across * 1.5f, seed + 3);
                float s2 = NaturalVoidField.Noise.fbm((along - p2 * run) * .28f + 17f, across * 1.5f, seed + 5);
                float streak = w1 * s1 + (1f - w1) * s2;
                r = .10f + .14f * sky + .03f * streak;
                g = .22f + .20f * sky + .035f * streak;
                b = .22f + .18f * sky + .03f * streak;
                float bright = .6f + .5f * light;
                r *= bright; g *= bright; b *= bright;
                // White water along the walls and where the stream breaks.
                float foam = clamp01((.75f - edge) * 1.6f) * (.55f + .45f * streak)
                        + clamp01(streak - .45f) * .8f;
                foam = clamp01(foam) * (.5f + .5f * light);
                r = lerp(r, .80f, foam * .75f); g = lerp(g, .84f, foam * .75f); b = lerp(b, .82f, foam * .75f);
                if (sunlit > 0f && streak > .3f) { r += .08f; g += .08f; b += .07f; }
            } else {
                r = c[q * 3]; g = c[q * 3 + 1]; b = c[q * 3 + 2];
            }
            r = lerp(r, deepR, tint); g = lerp(g, deepG, tint); b = lerp(b, deepB, tint);
            float s0 = scale(band.z(q, 0)), s1 = scale(band.z(q, 1)), s2 = scale(band.z(q, 2)), s3 = scale(band.z(q, 3));
            mesh.appendQuad(sx(qd[o], s0), sy(qd[o + 1], s0), sx(qd[o + 2], s1), sy(qd[o + 3], s1),
                    sx(qd[o + 4], s2), sy(qd[o + 5], s2), sx(qd[o + 6], s3), sy(qd[o + 7], s3), r, g, b, alpha);
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.GROUND, mesh);
    }

    /**
     * One step's faces: the rock image laid along each segment between the step's top and foot, cut where the
     * image repeats down the face, then the face's light and the depth's tint laid over it row by row.
     */
    private void drawWalls(List<NaturalVoidField.Wall> walls) {
        ShuttleSpriteCache rock = sprites.spriteByPath(wallTexture);
        float tile = wallTileMeters;
        float floorDepth = Math.max(1f, field.depth[field.steps()]);
        float margin = cam.cellPxSize() * 2f;
        float px = cam.panCellX() + MARGIN, py = cam.panCellY() + MARGIN;
        PolyMesh shade = poly();
        for (NaturalVoidField.Wall wall : walls) {
            float top = wall.zTop(), bottom = wall.zFoot();
            // Its depths on the steps, scaled at each end by how deep the drop stands there.
            float g0 = wall.g0(), g1 = wall.g1(), gm = (g0 + g1) / 2f;
            float s0 = scale(top * gm), s1 = scale(bottom * gm);
            float mx = (wall.x0() + wall.x1()) / 2f, my = (wall.y0() + wall.y1()) / 2f;
            // A rise's face turned from the camera lies under the top above it: only the top is seen there.
            if (rising && wall.nx() * (px - mx) + wall.ny() * (py - my) <= 0f) continue;
            if (!inView(mx, my, s0, margin) && !inView(mx, my, s1, margin)) continue;
            if (rock != null && rock.pxW > 4) {
                boolean alongX = Math.abs(wall.x1() - wall.x0()) >= Math.abs(wall.y1() - wall.y0());
                float u0 = alongX ? wall.x0() : wall.y0(), u1 = alongX ? wall.x1() : wall.y1();
                float z = top;
                while (z < bottom - .01f) {
                    float z1 = Math.min(bottom, (float) (Math.floor(z / tile) + 1) * tile);
                    rockPatch(rock, wall, Math.min(u0, u1), Math.abs(u1 - u0), z, z1, tile);
                    z = z1;
                }
            }
            float[] light = wall.light();
            int rows = light.length;
            for (int r = 0; r < rows; r++) {
                float za = top + r * (bottom - top) / rows, zb = top + (r + 1) * (bottom - top) / rows;
                float sa0 = scale(za * g0), sa1 = scale(za * g1), sb0 = scale(zb * g0), sb1 = scale(zb * g1);
                float ax0 = sx(wall.x0(), sa0), ay0 = sy(wall.y0(), sa0), bx0 = sx(wall.x1(), sa1), by0 = sy(wall.y1(), sa1);
                float ax1 = sx(wall.x0(), sb0), ay1 = sy(wall.y0(), sb0), bx1 = sx(wall.x1(), sb1), by1 = sy(wall.y1(), sb1);
                float zm = (za + zb) / 2f * gm;
                if (rock == null || rock.pxW <= 4) {
                    shade.appendQuad(ax0, ay0, bx0, by0, bx1, by1, ax1, ay1, .46f, .41f, .34f, alpha);
                }
                float dark = 1f - Math.min(1f, light[r]);
                if (ice) {
                    // Ice in shade is lit through blue, not darkened grey; the crevasse falls to a deep blue.
                    shade.appendQuad(ax0, ay0, bx0, by0, bx1, by1, ax1, ay1, IceVoidLook.SHADE_R,
                            IceVoidLook.SHADE_G, IceVoidLook.SHADE_B, dark * .6f * alpha);
                    float tint = IceVoidLook.deepTint(zm, floorDepth);
                    shade.appendQuad(ax0, ay0, bx0, by0, bx1, by1, ax1, ay1, IceVoidLook.DEEP_R,
                            IceVoidLook.DEEP_G, IceVoidLook.DEEP_B, tint * alpha);
                } else {
                    shade.appendQuad(ax0, ay0, bx0, by0, bx1, by1, ax1, ay1, 0f, 0f, 0f, dark * alpha);
                }
                if (!rising && !ice) {
                    float tint = DEEP_MIX * zm / floorDepth;
                    shade.appendQuad(ax0, ay0, bx0, by0, bx1, by1, ax1, ay1, DEEP_R, DEEP_G, DEEP_B, tint * alpha);
                }
                if (lava) {
                    float glow = LavaVoidLook.wallGlow((floorDepth - (za + zb) / 2f) * gm);
                    shade.appendQuad(ax0, ay0, bx0, by0, bx1, by1, ax1, ay1, LavaVoidLook.GLOW_R,
                            LavaVoidLook.GLOW_G, LavaVoidLook.GLOW_B, glow * alpha);
                }
            }
        }
        if (!shade.isEmpty()) out.addPoly(RenderLayer.GROUND, shade);
    }

    /** The rock image over one segment of face between two depths within one repeat of the image. */
    private void rockPatch(ShuttleSpriteCache rock, NaturalVoidField.Wall wall, float u, float length,
                           float za, float zb, float tile) {
        float sa0 = scale(za * wall.g0()), sa1 = scale(za * wall.g1());
        float sb0 = scale(zb * wall.g0()), sb1 = scale(zb * wall.g1());
        float ax0 = sx(wall.x0(), sa0), ay0 = sy(wall.y0(), sa0), bx0 = sx(wall.x1(), sa1), by0 = sy(wall.y1(), sa1);
        float ax1 = sx(wall.x0(), sb0), ay1 = sy(wall.y0(), sb0), bx1 = sx(wall.x1(), sb1), by1 = sy(wall.y1(), sb1);
        // One winding for every patch, as the city's walls have: a segment runs either way along its line.
        if ((bx0 - ax0) * (ay1 - ay0) - (by0 - ay0) * (ax1 - ax0) < 0f) {
            float t;
            t = ax0; ax0 = bx0; bx0 = t; t = ay0; ay0 = by0; by0 = t;
            t = ax1; ax1 = bx1; bx1 = t; t = ay1; ay1 = by1; by1 = t;
        }
        float minX = Math.min(Math.min(ax0, bx0), Math.min(ax1, bx1));
        float maxX = Math.max(Math.max(ax0, bx0), Math.max(ax1, bx1));
        float minY = Math.min(Math.min(ay0, by0), Math.min(ay1, by1));
        float maxY = Math.max(Math.max(ay0, by0), Math.max(ay1, by1));
        float w = maxX - minX, h = maxY - minY;
        if (w < .2f || h < .2f) return;
        int pw = rock.pxW, ph = rock.pxH;
        int srcW = Math.max(1, Math.min(pw, Math.round(length / tile * pw)));
        int srcX = Math.min(Math.round(frac(u / tile) * pw), pw - srcW);
        int srcY = Math.round(frac(za / tile) * ph);
        int srcH = Math.max(1, Math.min(ph - srcY, Math.round((zb - za) / tile * ph)));
        if (srcY >= ph) return;
        float[] patch = {
                (ax0 - minX) / w, (ay0 - minY) / h, (bx0 - minX) / w, (by0 - minY) / h,
                (bx1 - minX) / w, (by1 - minY) / h, (ax1 - minX) / w, (ay1 - minY) / h,
                0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f};
        out.addSheetPatch(RenderLayer.GROUND, rock.sprite, srcX, srcY, srcW, srcH,
                minX + w / 2f, minY + h / 2f, w, h, alpha, patch);
    }

    /**
     * An open pit's haul road down one face: its surface on the bench below, sloping from the bench above's level
     * to its own, and its inner edge falling to the bench, drawn after the face and before the bench above.
     */
    private void drawRamps(int wall) {
        if (ramps.isEmpty()) return;
        PolyMesh mesh = poly();
        float margin = cam.cellPxSize() * 2f;
        float lit = sun == null ? .9f : .78f + .22f * Math.min(1f, sun.risePerCell());
        float floorDepth = Math.max(1f, field.depth[field.steps()]);
        for (PitVoidLook.Ramp ramp : ramps) {
            if (ramp.wall() != wall) continue;
            float[] p = ramp.corners();
            float s0 = scale(p[2]), s1 = scale(p[5]), s2 = scale(p[8]), s3 = scale(p[11]);
            if (!inView(p[0], p[1], s0, margin)) continue;
            float r, g, b;
            if (ramp.side()) { r = .30f; g = .27f; b = .23f; }
            else if (ramp.lane() == 1) { r = .44f; g = .40f; b = .34f; }
            else if (ramp.lane() == 2) { r = .60f; g = .56f; b = .49f; }
            else { r = .54f; g = .50f; b = .43f; }
            float tint = DEEP_MIX * (p[2] + p[5]) / 2f / floorDepth;
            r = lerp(r * lit, DEEP_R, tint); g = lerp(g * lit, DEEP_G, tint); b = lerp(b * lit, DEEP_B, tint);
            mesh.appendQuad(sx(p[0], s0), sy(p[1], s0), sx(p[3], s1), sy(p[4], s1),
                    sx(p[6], s2), sy(p[7], s2), sx(p[9], s3), sy(p[10], s3), r, g, b, alpha);
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.GROUND, mesh);
    }

    /**
     * The machines working an open pit ({@link PitMachines}) that rest on band {@code step}: those on the haul road
     * down face {@code rampWall}, or with {@code rampWall} -1 those standing on the band itself. Each is its frame of
     * the sheet, turned to its heading and drawn at its depth's scale, shaded as deep rock is; one the rock hides
     * from the eye is not drawn.
     */
    private void drawMachines(int step, int rampWall) {
        if (machines.isEmpty()) return;
        ShuttleSpriteCache sheet = sprites.spriteByPath(PitMachines.SHEET);
        if (sheet == null || sheet.pxW < 3 || sheet.pxH < 2) return;
        int fw = sheet.pxW / 3, fh = sheet.pxH / 2;
        float cell = cam.cellPxSize();
        float margin = cell * 4f;
        float floorDepth = Math.max(1f, field.depth[field.steps()]);
        float lit = sun == null ? .9f : .8f + .2f * Math.min(1f, sun.risePerCell());
        for (PitMachines.Machine m : machines) {
            if (m.step() != step || m.rampWall() != rampWall) continue;
            float s = scale(m.depth());
            if (!inView(m.x(), m.y(), s, margin) || hidden(m.x(), m.y(), m.depth())) continue;
            float px = sx(m.x(), s), py = sy(m.y(), s);
            float angle = (float) Math.toDegrees(Math.atan2(sy(m.y() + m.headingY(), s) - py,
                    sx(m.x() + m.headingX(), s) - px));
            float w = PitMachines.FRAME_W_METERS * PitMachines.CELLS_PER_METER * cell * s;
            float h = PitMachines.FRAME_H_METERS * PitMachines.CELLS_PER_METER * cell * s;
            float shade = lit * (1f - DEEP_MIX * m.depth() / floorDepth);
            int frame = m.kind().ordinal();
            out.addSheetQuad(RenderLayer.GROUND, sheet.sprite, (frame % 3) * fw, (frame / 3) * fh, fw, fh,
                    px, py, w, h, angle, shade, shade, shade * .97f, alpha);
        }
    }

    /**
     * An open pit's conveyors, over the ground tiles and under every unit: each belt climbs from the floor to its
     * gantry's height over the rim and runs on to the works, its shadow laid on the ground beside it and ore
     * riding it toward the works.
     */
    private void drawBelts(float time) {
        PolyMesh mesh = poly();
        float floorDepth = field.depth[field.steps()];
        float lift = -PitVoidLook.BELT_METERS;
        float sdx = sun == null ? .7f : -sun.dirX(), sdy = sun == null ? .7f : -sun.dirY();
        float reach = sun == null || !sun.casts() ? 0f : 1f / Math.max(.25f, sun.risePerCell());
        float shadeA = sun == null ? .3f : .45f * Math.min(1f, sun.shadowStrength() / .75f);
        float half = PitVoidLook.BELT_CELLS / 2f;
        for (NaturalVoid.Belt belt : belts) {
            float[][] legs = {
                    {belt.floorX(), belt.floorY(), floorDepth, belt.rimX(), belt.rimY(), lift},
                    {belt.rimX(), belt.rimY(), lift, belt.endX(), belt.endY(), lift}};
            float along0 = 0f;
            for (float[] leg : legs) {
                float lx = leg[3] - leg[0], ly = leg[4] - leg[1];
                float len = (float) Math.hypot(lx, ly);
                if (len < .01f) continue;
                float ux = lx / len, uy = ly / len, px = -uy * half, py = ux * half;
                int pieces = Math.max(1, (int) Math.ceil(len / .5f));
                for (int i = 0; i < pieces; i++) {
                    float t0 = i / (float) pieces, t1 = (i + 1) / (float) pieces;
                    float ax = leg[0] + lx * t0 + MARGIN, ay = leg[1] + ly * t0 + MARGIN;
                    float bx = leg[0] + lx * t1 + MARGIN, by = leg[1] + ly * t1 + MARGIN;
                    float za = leg[2] + (leg[5] - leg[2]) * t0, zb = leg[2] + (leg[5] - leg[2]) * t1;
                    float sa = scale(za), sb = scale(zb);
                    // The shadow on the ground, only where the belt runs over it.
                    if (za <= 0f && zb <= 0f && reach > 0f) {
                        float oa = -za * reach, ob = -zb * reach;
                        mesh.appendQuad(sx(ax + px + sdx * oa, 1f), sy(ay + py + sdy * oa, 1f),
                                sx(bx + px + sdx * ob, 1f), sy(by + py + sdy * ob, 1f),
                                sx(bx - px + sdx * ob, 1f), sy(by - py + sdy * ob, 1f),
                                sx(ax - px + sdx * oa, 1f), sy(ay - py + sdy * oa, 1f),
                                .02f, .03f, .05f, shadeA * alpha);
                    }
                    // The gantry's steel frame, then the belt and the ore riding it.
                    mesh.appendQuad(sx(ax + px, sa), sy(ay + py, sa), sx(bx + px, sb), sy(by + py, sb),
                            sx(bx - px, sb), sy(by - py, sb), sx(ax - px, sa), sy(ay - py, sa),
                            .22f, .23f, .24f, alpha);
                    float ix = px * .6f, iy = py * .6f;
                    mesh.appendQuad(sx(ax + ix, sa), sy(ay + iy, sa), sx(bx + ix, sb), sy(by + iy, sb),
                            sx(bx - ix, sb), sy(by - iy, sb), sx(ax - ix, sa), sy(ay - iy, sa),
                            .11f, .10f, .09f, alpha);
                    // Ore heaped along the belt's middle, broken into lumps that ride toward the works.
                    float ox = ix * .55f, oy = iy * .55f;
                    for (int part = 0; part < 2; part++) {
                        float u0 = t0 + (t1 - t0) * part / 2f, u1 = t0 + (t1 - t0) * (part + 1) / 2f;
                        float run = along0 + len * (u0 + u1) / 2f - time * PitVoidLook.BELT_SPEED;
                        float lump = NaturalVoidField.Noise.hash((int) Math.floor(run / .25f), 3, seed);
                        if (lump < .3f) continue;
                        float ore = .26f + .14f * lump;
                        float cx0 = leg[0] + lx * u0 + MARGIN, cy0 = leg[1] + ly * u0 + MARGIN;
                        float cx1 = leg[0] + lx * u1 + MARGIN, cy1 = leg[1] + ly * u1 + MARGIN;
                        float s0 = scale(leg[2] + (leg[5] - leg[2]) * u0), s1 = scale(leg[2] + (leg[5] - leg[2]) * u1);
                        mesh.appendQuad(sx(cx0 + ox, s0), sy(cy0 + oy, s0), sx(cx1 + ox, s1), sy(cy1 + oy, s1),
                                sx(cx1 - ox, s1), sy(cy1 - oy, s1), sx(cx0 - ox, s0), sy(cy0 - oy, s0),
                                ore * 1.2f, ore, ore * .82f, alpha);
                    }
                    // A trestle's cross-beam every few cells along the run.
                    float from = along0 + len * t0, to = along0 + len * t1;
                    if (Math.floor(from / PitVoidLook.TRESTLE_EVERY) != Math.floor(to / PitVoidLook.TRESTLE_EVERY)) {
                        float qx = ux * .18f, qy = uy * .18f, wx = px * 1.25f, wy = py * 1.25f;
                        mesh.appendQuad(sx(ax - qx + wx, sa), sy(ay - qy + wy, sa), sx(ax + qx + wx, sa),
                                sy(ay + qy + wy, sa), sx(ax + qx - wx, sa), sy(ay + qy - wy, sa),
                                sx(ax - qx - wx, sa), sy(ay - qy - wy, sa), .30f, .30f, .28f, alpha);
                    }
                }
                along0 += len;
            }
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.GROUND, mesh);
    }

    /** A bridge's deck edge over the drop, lit on the side turned to the sun. */
    private void drawBridgeSides() {
        if (field.bridgeSides.isEmpty()) return;
        PolyMesh mesh = poly();
        float s0 = 1f, s1 = scale(NaturalVoidField.DECK_METERS);
        float margin = cam.cellPxSize() * 2f;
        for (float[] side : field.bridgeSides) {
            if (!inView(side[0], side[1], 1f, margin)) continue;
            float toSun = sun == null ? 0f : side[4] * sun.dirX() + side[5] * sun.dirY();
            float lit = .6f + .4f * Math.max(0f, toSun);
            // A crevasse's bridges are snow: packed snow over the drop, not a timber or stone edge.
            float r = ice ? IceVoidLook.BRIDGE_R : .30f, g = ice ? IceVoidLook.BRIDGE_G : .23f;
            float b = ice ? IceVoidLook.BRIDGE_B : .16f;
            mesh.appendQuad(sx(side[0], s0), sy(side[1], s0), sx(side[2], s0), sy(side[3], s0),
                    sx(side[2], s1), sy(side[3], s1), sx(side[0], s1), sy(side[1], s1),
                    r * lit, g * lit, b * lit, alpha);
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.GROUND, mesh);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float frac(float v) { return v - (float) Math.floor(v); }
}
