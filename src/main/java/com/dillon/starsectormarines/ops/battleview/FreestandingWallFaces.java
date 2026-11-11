package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofCover;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.VisibleCellRect;

import java.util.Arrays;
import java.util.List;

/**
 * Freestanding walls stood up ({@link FreestandingWalls}): each run's faces turned to the view's centre, from their
 * foot on the ground to the run's height at its lift about the view's centre, lit by their side, then the run's cells
 * lifted to that height as its cap. Drawn in the roof layer before the buildings' faces, so a building stands over a
 * wall beside it; above the fog, so each carries its cell's fog itself, as a roof does. The runs are settled once per
 * topology change: a breached cell is no wall, and its run re-forms without it.
 */
final class FreestandingWallFaces {

    private final PolyMesh mesh = new PolyMesh(256);
    /** How far this frame's eye has risen ({@link BuildingFaces#rise}). */
    private float rise = 1f;
    /** The footing along a diagonal wall, on the ground: it covers the corners of its cells the slanted wall leaves. */
    private final PolyMesh footing = new PolyMesh(32);
    private CellTopology runsOf;
    private long runsAt = -1;
    private List<FreestandingWalls.Run> runs = List.of();
    /** Each cell's part of a sealed town's envelope ({@link FreestandingWalls#part}), its ordinal + 1; 0 where none. */
    private byte[] parts = new byte[0];
    private final CreepPalette creepPalette = CreepPalette.active();
    private final float[] capTone = new float[3];
    /** The share of the face now held that the cell being painted spans, and the creep's alpha. */
    private float cu0, cu1, calpha;
    private final float[] creepTone = new float[3];
    /** Creep up the stroke's side now held in {@link #face} ({@link CreepClimb}), within one cell's share of it. */
    private final CreepClimb.Sink creepOnFace = (t0, t1, z0, z1, r, g, b) -> {
        creepTone[0] = r; creepTone[1] = g; creepTone[2] = b;
        this.face.band(cu0 + (cu1 - cu0) * t0, cu0 + (cu1 - cu0) * t1, z0, z1, creepTone, 1f, calpha);
    };

    /**
     * This frame's walls into the roof layer.
     *
     * @param fogDrawn whether the frame draws the fog; a frame without it (a look shot) shows every wall in light
     */
    void collect(BattleSimulation sim, DrawList out, float alphaMult, BattleCamera camera, boolean fogDrawn) {
        CellTopology topology = sim.getTopology();
        if (topology == null) return;
        if (topology != runsOf || topology.changeCount() != runsAt) {
            runs = runs(topology);
            parts = parts(topology);
            runsOf = topology;
            runsAt = topology.changeCount();
        }
        if (runs.isEmpty()) return;
        rise = BuildingFaces.rise(camera);
        int gridW = topology.getWidth();
        FogOfWarService vis = sim.getFogOfWar();
        boolean[] shown = !fogDrawn || vis == null || !vis.isInitialized() ? null : vis.cellShownArray();
        int gridH = topology.getHeight();
        float unseen = FogField.roofShade(1f);
        float viewX = camera.panCellX(), viewY = camera.panCellY();
        float sx = camera.cellToScreenX(viewX), sy = camera.cellToScreenY(viewY);
        VisibleCellRect view = camera.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS + 6, gridW,
                topology.getHeight());
        mesh.reset();
        CreepField creep = sim.creepFieldIfPresent();
        if (creep != null && creep.isEmpty()) creep = null;
        boolean detail = Math.abs(camera.cellToScreenX(1f) - camera.cellToScreenX(0f)) >= 10f;
        footing.reset();
        for (FreestandingWalls.Run run : runs) {
            if (!view.intersects(run.minX(), run.maxX(), run.minY(), run.maxY())) continue;
            float lift = BuildingFaces.lift(run.meters(), rise);
            FreestandingWalls.Kind kind = run.kind();
            if (kind == FreestandingWalls.Kind.ENVELOPE || kind == FreestandingWalls.Kind.ENVELOPE_PLINTH) {
                envelope(run, camera, view, shown, gridW, gridH, unseen, alphaMult, creep, detail);
                continue;
            }
            strokes(run, camera, view, shown, gridW, gridH, unseen, alphaMult, creep, detail);
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.ROOFS, mesh);
        if (!footing.isEmpty()) out.addPoly(RenderLayer.DECALS, footing);
    }

    // A freestanding wall's dress, by its kind. UNTUNED, all of it.
    /** The footing's half-width along a diagonal: a cell's half-diagonal, so it covers every corner of its cells. */
    private static final float FOOTING_HALF = .71f;
    /** A plinth's and a coping's height, metres; a coping's width on the cap, cells. */
    private static final float PLINTH_METERS = .35f, COPING_METERS = .2f, CAP_COPING = .14f;

    /**
     * One run of freestanding wall as the straight strokes it was built along ({@link FreestandingWalls#strokes}),
     * stood up as one wall round their joint outline ({@link FreestandingWalls#edges}): a diagonal wall stands as one
     * slanted wall, and where it meets a straight wall, a pier or a jog, the two run into each other with no face or
     * coping crossing the joint. The outline's faces turned to the view first, farthest first, each from a dark plinth
     * through the body, shaded up to a light coping, with the kind's joints and weather streaks; then the cap: every
     * stroke's top, the coping round the outline, the joints across. Along a diagonal a footing on the ground covers
     * its cells' corners.
     */
    private void strokes(FreestandingWalls.Run run, BattleCamera camera, VisibleCellRect view, boolean[] shown,
                         int gridW, int gridH, float unseen, float alphaMult, CreepField creep, boolean detail) {
        float meters = run.meters(), lift = BuildingFaces.lift(meters, rise);
        float viewX = camera.panCellX(), viewY = camera.panCellY();
        float sx = camera.cellToScreenX(viewX), sy = camera.cellToScreenY(viewY);
        FreestandingWalls.Kind kind = run.kind();
        float[] base = {kind.r, kind.g, kind.b};
        float[] st = run.strokes();
        float[] q = strokeQuad;
        for (int o = 0; o < st.length; o += FreestandingWalls.STROKE) {
            float x0 = st[o], y0 = st[o + 1], x1 = st[o + 2], y1 = st[o + 3];
            if (x0 == x1 || y0 == y1 || !near(view, x0, y0, x1, y1)) continue;
            float fog = fog(shown, (int) x0 + (int) y0 * gridW, gridW, gridH, unseen);
            footing(x0, y0, x1, y1, foot);
            footing.appendQuad(camera.cellToScreenX(foot[0]), camera.cellToScreenY(foot[1]),
                    camera.cellToScreenX(foot[2]), camera.cellToScreenY(foot[3]),
                    camera.cellToScreenX(foot[4]), camera.cellToScreenY(foot[5]),
                    camera.cellToScreenX(foot[6]), camera.cellToScreenY(foot[7]),
                    base[0] * .72f * fog, base[1] * .72f * fog, base[2] * .72f * fog, alphaMult);
        }
        // The faces turned to the view, farthest first, so a nearer face stands over a farther one where they meet.
        float[] ed = run.edges();
        int shownFaces = 0;
        if (order.length < ed.length / FreestandingWalls.EDGE) {
            order = new long[ed.length / FreestandingWalls.EDGE];
        }
        for (int e = 0; e < ed.length; e += FreestandingWalls.EDGE) {
            float ax = ed[e], ay = ed[e + 1], bx = ed[e + 2], by = ed[e + 3];
            if (!near(view, ax, ay, bx, by)) continue;
            float mx = (ax + bx) / 2f, my = (ay + by) / 2f;
            if (!BuildingFaces.facing(ed[e + 4], ed[e + 5], mx, my, viewX, viewY)) continue;
            float far = (mx - viewX) * (mx - viewX) + (my - viewY) * (my - viewY);
            order[shownFaces++] = (long) Float.floatToIntBits(far) << 32 | e;
        }
        Arrays.sort(order, 0, shownFaces);
        for (int k = shownFaces - 1; k >= 0; k--) {
            int e = (int) order[k];
            float ax = ed[e], ay = ed[e + 1], bx = ed[e + 2], by = ed[e + 3];
            int cell = (int) ed[e + 6];
            float light = BuildingFaces.shade(ed[e + 4], ed[e + 5]) * fog(shown, cell, gridW, gridH, unseen);
            int cells = Math.max(1, Math.round((float) Math.hypot(bx - ax, by - ay)));
            face.set(camera, sx, sy, ax, ay, bx, by, meters);
            dressFace(kind, base, cells, cell * 4 + e % 4, light, alphaMult);
            if (creep != null) {
                // Cell by cell along the face, each the coverage of the wall cell just inside it.
                calpha = alphaMult;
                for (int slice = 0; slice < cells; slice++) {
                    float t = (slice + .5f) / cells;
                    int at = cellAt(ax + (bx - ax) * t - ed[e + 4] * .3f, ay + (by - ay) * t - ed[e + 5] * .3f,
                            gridW, gridH);
                    if (at < 0) continue;
                    cu0 = slice / (float) cells;
                    cu1 = (slice + 1) / (float) cells;
                    CreepClimb.paint(creep.coverageAt(at), creep.vitalityAt(at), meters, 1f, at, creepPalette, light,
                            detail, creepOnFace);
                }
            }
        }
        Cap c = new Cap(camera, sx, sy, lift);
        float[] top = scale(base, 1.06f), coping = scale(base, 1.22f), joint = scale(base, .86f);
        for (int o = 0; o < st.length; o += FreestandingWalls.STROKE) {
            float x0 = st[o], y0 = st[o + 1], x1 = st[o + 2], y1 = st[o + 3];
            if (!near(view, x0, y0, x1, y1)) continue;
            float fog = fog(shown, (int) x0 + (int) y0 * gridW, gridW, gridH, unseen);
            FreestandingWalls.corners(x0, y0, x1, y1, FreestandingWalls.HALF, q);
            c.quad(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], top, fog, alphaMult);
            int cells = Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))) + 1);
            for (int k = 0; creep != null && k < cells; k++) {
                // An old creep crusts over the wall's top too, as it did the wall cell on the ground.
                float f = cells <= 1 ? 0f : k / (float) (cells - 1);
                int at = cellAt(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, gridW, gridH);
                float crust = at < 0 ? 0f : CreepClimb.crust(creep.coverageAt(at));
                if (crust <= 0f) continue;
                CreepClimb.tone(creepPalette.flesh(), creepPalette.dead(), creep.vitalityAt(at), 1f, capTone);
                float u0 = k / (float) cells, u1 = (k + 1) / (float) cells;
                c.quad(q[0] + (q[2] - q[0]) * u0, q[1] + (q[3] - q[1]) * u0,
                        q[0] + (q[2] - q[0]) * u1, q[1] + (q[3] - q[1]) * u1,
                        q[6] + (q[4] - q[6]) * u1, q[7] + (q[5] - q[7]) * u1,
                        q[6] + (q[4] - q[6]) * u0, q[7] + (q[5] - q[7]) * u0, capTone, fog, alphaMult * crust);
            }
        }
        // The coping round the outline, so it turns every joint where strokes meet.
        for (int e = 0; e < ed.length; e += FreestandingWalls.EDGE) {
            float ax = ed[e], ay = ed[e + 1], bx = ed[e + 2], by = ed[e + 3];
            if (!near(view, ax, ay, bx, by)) continue;
            float ix = -ed[e + 4] * CAP_COPING, iy = -ed[e + 5] * CAP_COPING;
            float fog = fog(shown, (int) ed[e + 6], gridW, gridH, unseen);
            c.quad(ax, ay, bx, by, bx + ix, by + iy, ax + ix, ay + iy, coping, fog, alphaMult);
        }
        if (joints(kind)) {
            float[] jt = run.joints();
            for (int j = 0; j < jt.length; j += 8) {
                if (!near(view, jt[j], jt[j + 1], jt[j + 4], jt[j + 5])) continue;
                float fog = fog(shown, (int) jt[j] + (int) jt[j + 1] * gridW, gridW, gridH, unseen);
                c.quad(jt[j], jt[j + 1], jt[j + 2], jt[j + 3], jt[j + 4], jt[j + 5], jt[j + 6], jt[j + 7], joint, fog,
                        alphaMult);
            }
        }
    }

    private final float[] strokeQuad = new float[8], foot = new float[8];
    private long[] order = new long[64];

    /** The cell holding the point {@code (x, y)}, as an index, or -1 off the map. */
    private static int cellAt(float x, float y, int gridW, int gridH) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        return cx < 0 || cy < 0 || cx >= gridW || cy >= gridH ? -1 : cx + cy * gridW;
    }

    private static boolean near(VisibleCellRect view, float x0, float y0, float x1, float y1) {
        return view.intersects((int) Math.min(x0, x1) - 1, (int) Math.max(x0, x1) + 1,
                (int) Math.min(y0, y1) - 1, (int) Math.max(y0, y1) + 1);
    }

    private static float fog(boolean[] shown, int cell, int gridW, int gridH, float unseen) {
        if (cell < 0 || cell >= gridW * gridH) return 1f;
        return seen(shown, cell, gridW, gridH) ? 1f : unseen;
    }

    /** A diagonal's footing on the ground, centre to centre, {@link #FOOTING_HALF} either side: under its piers. */
    private static void footing(float x0, float y0, float x1, float y1, float[] out) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
        float nx = dy / len * FOOTING_HALF, ny = -dx / len * FOOTING_HALF;
        out[0] = x0 + nx; out[1] = y0 + ny;
        out[2] = x1 + nx; out[3] = y1 + ny;
        out[4] = x1 - nx; out[5] = y1 - ny;
        out[6] = x0 - nx; out[7] = y0 - ny;
    }

    /** Whether a kind is built in panels or piers, jointed at every cell. */
    private static boolean joints(FreestandingWalls.Kind kind) {
        return kind != FreestandingWalls.Kind.GARDEN;
    }

    private static float[] scale(float[] rgb, float k) {
        return new float[]{Math.min(1f, rgb[0] * k), Math.min(1f, rgb[1] * k), Math.min(1f, rgb[2] * k)};
    }

    /** One face of a stroke ({@link #face} set), {@code cells} cells long, dressed by its kind. */
    private void dressFace(FreestandingWalls.Kind kind, float[] base, int cells, int salt, float light, float alpha) {
        float meters = face.meters;
        float plinth = Math.min(.3f, PLINTH_METERS / meters), coping = Math.min(.2f, COPING_METERS / meters);
        face.shaded(0f, 1f, plinth, 1f - coping, scale(base, .74f), base, light, alpha);
        face.band(0f, 1f, 0f, plinth, scale(base, .6f), light, alpha);
        face.band(0f, 1f, 1f - coping, 1f, scale(base, 1.18f), light, alpha);
        face.band(0f, 1f, 1f - coping - .02f, 1f - coping, scale(base, .55f), light, alpha);
        float[] joint = scale(base, .72f);
        switch (kind) {
            case COMPOUND, UNKNOWN -> {
                // Masonry: courses every 0.3 m, a pier at every second cell.
                for (float z = plinth + .3f / meters; z < 1f - coping; z += .3f / meters) {
                    face.band(0f, 1f, z - .01f, z + .01f, joint, light, alpha);
                }
                for (int k = 0; k <= cells; k += 2) {
                    float u = k / (float) cells;
                    face.band(Math.max(0f, u - .06f / cells), Math.min(1f, u + .06f / cells), plinth, 1f - coping,
                            scale(base, 1.08f), light, alpha);
                }
            }
            case SEALED_PERIMETER, INSTALLATION, BLAST -> {
                // Cast panels: a joint at every cell, and a seam half-way up the taller walls.
                for (int k = 1; k < cells; k++) {
                    float u = k / (float) cells;
                    face.band(u - .01f, u + .01f, plinth, 1f - coping, joint, light, alpha);
                }
                if (meters > 4f) face.band(0f, 1f, .5f - .012f, .5f + .012f, joint, light, alpha);
            }
            default -> { }
        }
        // Weather: a few dark streaks run down from the coping.
        for (int k = 0; k < cells; k++) {
            float h = BuildingFaces.hash(salt * 31 + k, 300);
            if (h > .45f) continue;
            float u = (k + .2f + .6f * BuildingFaces.hash(salt * 31 + k, 301)) / cells;
            float w = (.03f + .04f * h) / cells;
            face.band(u - w, u + w, .35f + .3f * BuildingFaces.hash(salt * 31 + k, 302), 1f - coping,
                    scale(base, .8f), light, alpha * .6f);
        }
    }

    // A sealed town's envelope, drawn as a raised habitat wall rather than a plain block. UNTUNED, all of it.
    /** Its coping: the light cap edge round the top of every course, as a share of a cell, and its band on a face. */
    private static final float COPING = .12f, COPING_BAND = .1f;
    private static final float[] COPING_RGB = {.72f, .72f, .69f};
    /** The deck inside the coping on the body's top, darker than its faces. */
    private static final float[] DECK_RGB = {.47f, .49f, .50f};
    /** A buttress's face, and its top, lighter than the body behind it. */
    private static final float[] BUTTRESS_RGB = {.66f, .66f, .63f};
    /** The service run along the body's inner face and top: an ochre pipe, its shadow, and its brackets. */
    private static final float[] PIPE_RGB = {.70f, .60f, .38f}, PIPE_SHADOW_RGB = {.26f, .26f, .25f};
    /** Where the pipe runs on the inner face, by share of its height, and in from the deck's inner edge, by cell. */
    private static final float PIPE_LOW = .56f, PIPE_HIGH = .66f, PIPE_IN = .2f, PIPE_WIDE = .2f;
    /** A panel joint's width on a face, and a buttress's set-off (the step half-way up its face), by share. */
    private static final float JOINT = .06f, SET_OFF_LOW = .46f, SET_OFF_HIGH = .52f;
    private static final float[] JOINT_RGB = {.30f, .31f, .31f};

    /**
     * One run of a sealed town's envelope ({@code SealedTown}), as a raised habitat wall: the body's faces in panels,
     * with an ochre service pipe along the inner face; the buttresses lighter, set off half-way up; the plinth's faces
     * along the outer course; then the tops, each course's coping lighter round its edge, the body's deck dark inside it
     * with the pipe run along its inner side, each buttress capped light. Creep climbs it as any wall
     * ({@link CreepClimb}), and an old crust comes over its top from every side open to the ground.
     */
    private void envelope(FreestandingWalls.Run run, BattleCamera camera, VisibleCellRect view, boolean[] shown,
                          int gridW, int gridH, float unseen, float alphaMult, CreepField creep, boolean detail) {
        float meters = run.meters(), lift = BuildingFaces.lift(meters, rise);
        float viewX = camera.panCellX(), viewY = camera.panCellY();
        float sx = camera.cellToScreenX(viewX), sy = camera.cellToScreenY(viewY);
        FreestandingWalls.Kind kind = run.kind();
        float[] plain = {kind.r, kind.g, kind.b};
        float[] segs = run.segments();
        for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
            int cell = (int) segs[e + 6];
            if (!view.contains(cell % gridW, cell / gridW)) continue;
            float x0 = segs[e], y0 = segs[e + 1], x1 = segs[e + 2], y1 = segs[e + 3];
            float nx = segs[e + 4], ny = segs[e + 5];
            if (!BuildingFaces.facing(nx, ny, (x0 + x1) / 2f, (y0 + y1) / 2f, viewX, viewY)) continue;
            float light = BuildingFaces.shade(nx, ny) * (seen(shown, cell, gridW, gridH) ? 1f : unseen);
            int beyond = neighbour(cell, (int) nx, (int) ny, gridW, gridH);
            FreestandingWalls.Part part = partOf(cell), across = beyond < 0 ? null : partOf(beyond);
            boolean buttress = part == FreestandingWalls.Part.BUTTRESS;
            Face f = face.set(camera, sx, sy, x0, y0, x1, y1, meters);
            f.band(0f, 1f, 0f, 1f, buttress ? BUTTRESS_RGB : plain, light, alphaMult);
            f.band(0f, JOINT, 0f, 1f - COPING_BAND, JOINT_RGB, light, alphaMult);
            if (buttress) {
                f.band(0f, 1f, SET_OFF_LOW, SET_OFF_HIGH, JOINT_RGB, light, alphaMult);
            } else if (part == FreestandingWalls.Part.BODY && across == null) {
                // The inner face, toward the town: the service run along it, a bracket at every cell.
                f.band(0f, 1f, PIPE_LOW - .03f, PIPE_LOW, PIPE_SHADOW_RGB, light, alphaMult);
                f.band(0f, 1f, PIPE_LOW, PIPE_HIGH, PIPE_RGB, light, alphaMult);
                f.band(.45f, .55f, PIPE_LOW - .05f, PIPE_HIGH + .03f, PIPE_SHADOW_RGB, light, alphaMult);
            }
            f.band(0f, 1f, 1f - COPING_BAND, 1f, COPING_RGB, light, alphaMult);
            if (creep != null) {
                cu0 = 0f;
                cu1 = 1f;
                calpha = alphaMult;
                CreepClimb.paint(creep.coverageAt(cell), creep.vitalityAt(cell), meters, 1f, cell, creepPalette, light,
                        detail, creepOnFace);
            }
        }
        Cap c = new Cap(camera, sx, sy, lift);
        for (int cell : run.cells()) {
            int cx = cell % gridW, cy = cell / gridW;
            if (!view.contains(cx, cy)) continue;
            float light = seen(shown, cell, gridW, gridH) ? 1f : unseen;
            FreestandingWalls.Part part = partOf(cell);
            boolean plinth = part == FreestandingWalls.Part.PLINTH;
            float[] top = part == FreestandingWalls.Part.BODY ? DECK_RGB
                    : part == FreestandingWalls.Part.BUTTRESS ? BUTTRESS_RGB : plain;
            c.rect(cx, cy, cx + 1, cy + 1, top, light, alphaMult);
            for (int s = 0; s < 4; s++) {
                int beyond = neighbour(cell, SX[s], SY[s], gridW, gridH);
                FreestandingWalls.Part across = beyond < 0 ? null : partOf(beyond);
                if (across != null && (across == FreestandingWalls.Part.PLINTH) == plinth) {
                    // A joint between two of the plinth's slabs; the body's deck runs on unbroken.
                    if (plinth) c.edge(cx, cy, s, 0f, JOINT, JOINT_RGB, light, alphaMult);
                    continue;
                }
                c.edge(cx, cy, s, 0f, COPING, COPING_RGB, light, alphaMult);
                if (part == FreestandingWalls.Part.BODY && across == null) {
                    // The pipe run along the deck's inner side, a bracket over it at every cell.
                    c.edge(cx, cy, s, PIPE_IN, PIPE_IN + PIPE_WIDE, PIPE_RGB, light, alphaMult);
                    c.edge(cx, cy, s, PIPE_IN + PIPE_WIDE, PIPE_IN + PIPE_WIDE + .05f, PIPE_SHADOW_RGB, light,
                            alphaMult);
                    c.bracket(cx, cy, s, PIPE_IN - .03f, PIPE_IN + PIPE_WIDE + .03f, PIPE_SHADOW_RGB, light,
                            alphaMult);
                }
            }
            if (creep != null) {
                // The crust over the top, in from each side open to the ground.
                topCap = c;
                topLight = light;
                topAlpha = alphaMult;
                for (int s = 0; s < 4; s++) {
                    int beyond = neighbour(cell, SX[s], SY[s], gridW, gridH);
                    if (beyond >= 0 && partOf(beyond) != null) continue;
                    // The cell's side s, ends ordered so the outward normal (SX, SY) has the top on its left.
                    float ax = SX[s] > 0 ? cx + 1 : cx, ay = SY[s] > 0 ? cy + 1 : cy;
                    float bx = SY[s] != 0 ? ax + 1 : ax, by = SX[s] != 0 ? ay + 1 : ay;
                    CreepClimb.paintTop(creep.coverageAt(cell), creep.vitalityAt(cell), cell * 4 + s, ax, ay, bx, by,
                            SX[s], SY[s], creepPalette, 1f, creepOnTop);
                }
            }
        }
    }

    /** The envelope top being crusted, its light and alpha. */
    private Cap topCap;
    private float topLight, topAlpha;
    private final float[] topTone = new float[3];
    private final CreepClimb.TopSink creepOnTop = (x0, y0, x1, y1, x2, y2, x3, y3, r, g, b) -> {
        topTone[0] = r; topTone[1] = g; topTone[2] = b;
        topCap.quad(x0, y0, x1, y1, x2, y2, x3, y3, topTone, topLight, topAlpha);
    };

    private static final int[] SX = {1, 0, -1, 0}, SY = {0, 1, 0, -1};
    private final Face face = new Face();

    private FreestandingWalls.Part partOf(int cell) {
        int p = cell < parts.length ? parts[cell] : 0;
        return p == 0 ? null : PARTS[p - 1];
    }

    private static final FreestandingWalls.Part[] PARTS = FreestandingWalls.Part.values();

    private static int neighbour(int cell, int dx, int dy, int gridW, int gridH) {
        int x = cell % gridW + dx, y = cell / gridW + dy;
        return x < 0 || y < 0 || x >= gridW || y >= gridH ? -1 : x + y * gridW;
    }

    /** One face of a run, foot to top: a band of it is a share along it by a share of its height. */
    private final class Face {
        private BattleCamera camera;
        private float sx, sy, x0, y0, x1, y1, meters;

        Face set(BattleCamera camera, float sx, float sy, float x0, float y0, float x1, float y1, float meters) {
            this.camera = camera; this.sx = sx; this.sy = sy;
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.meters = meters;
            return this;
        }

        /** A band shaded from {@code low} at its foot to {@code high} at its top. */
        void shaded(float u0, float u1, float h0, float h1, float[] low, float[] high, float light, float alpha) {
            float l0 = BuildingFaces.lift(meters * h0, rise), l1 = BuildingFaces.lift(meters * h1, rise);
            float ax = camera.cellToScreenX(x0 + (x1 - x0) * u0), ay = camera.cellToScreenY(y0 + (y1 - y0) * u0);
            float bx = camera.cellToScreenX(x0 + (x1 - x0) * u1), by = camera.cellToScreenY(y0 + (y1 - y0) * u1);
            for (int k = 0; k < 2; k++) {
                shade[k * 3] = low[0] * light;
                shade[k * 3 + 1] = low[1] * light;
                shade[k * 3 + 2] = low[2] * light;
                shade[6 + k * 3] = high[0] * light;
                shade[7 + k * 3] = high[1] * light;
                shade[8 + k * 3] = high[2] * light;
            }
            mesh.appendShadedQuad(sx + (ax - sx) * l0, sy + (ay - sy) * l0, sx + (bx - sx) * l0, sy + (by - sy) * l0,
                    sx + (bx - sx) * l1, sy + (by - sy) * l1, sx + (ax - sx) * l1, sy + (ay - sy) * l1, shade, 0,
                    alpha);
        }

        private final float[] shade = new float[12];

        void band(float u0, float u1, float h0, float h1, float[] rgb, float light, float alpha) {
            float l0 = BuildingFaces.lift(meters * h0, rise), l1 = BuildingFaces.lift(meters * h1, rise);
            float ax = camera.cellToScreenX(x0 + (x1 - x0) * u0), ay = camera.cellToScreenY(y0 + (y1 - y0) * u0);
            float bx = camera.cellToScreenX(x0 + (x1 - x0) * u1), by = camera.cellToScreenY(y0 + (y1 - y0) * u1);
            mesh.appendQuad(sx + (ax - sx) * l0, sy + (ay - sy) * l0, sx + (bx - sx) * l0, sy + (by - sy) * l0,
                    sx + (bx - sx) * l1, sy + (by - sy) * l1, sx + (ax - sx) * l1, sy + (ay - sy) * l1,
                    rgb[0] * light, rgb[1] * light, rgb[2] * light, alpha);
        }
    }

    /** A run's top at its lift: rectangles of it in cells. */
    private final class Cap {
        private final BattleCamera camera;
        private final float sx, sy, lift;

        Cap(BattleCamera camera, float sx, float sy, float lift) {
            this.camera = camera; this.sx = sx; this.sy = sy; this.lift = lift;
        }

        void rect(float cx0, float cy0, float cx1, float cy1, float[] rgb, float light, float alpha) {
            float ax = sx + (camera.cellToScreenX(cx0) - sx) * lift, bx = sx + (camera.cellToScreenX(cx1) - sx) * lift;
            float ay = sy + (camera.cellToScreenY(cy0) - sy) * lift, by = sy + (camera.cellToScreenY(cy1) - sy) * lift;
            mesh.appendQuad(ax, ay, bx, ay, bx, by, ax, by, rgb[0] * light, rgb[1] * light, rgb[2] * light, alpha);
        }

        /** A quad of the top, its corners in cells, lifted. */
        void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float[] rgb,
                  float light, float alpha) {
            mesh.appendQuad(lx(x0), ly(y0), lx(x1), ly(y1), lx(x2), ly(y2), lx(x3), ly(y3),
                    rgb[0] * light, rgb[1] * light, rgb[2] * light, alpha);
        }

        /**
         * A strip along one long side of a stroke's quad ({@link #corners}), the side from corner {@code at} to the
         * next, {@code in} of the way across toward the other long side.
         */
        void strip(float[] q, int at, float in, float[] rgb, float light, float alpha) {
            int b = (at + 2) % 8, c = (at + 4) % 8, d = (at + 6) % 8;
            quad(q[at], q[at + 1], q[b], q[b + 1], q[b] + (q[c] - q[b]) * in, q[b + 1] + (q[c + 1] - q[b + 1]) * in,
                    q[at] + (q[d] - q[at]) * in, q[at + 1] + (q[d + 1] - q[at + 1]) * in, rgb, light, alpha);
        }

        /** A line across a stroke's quad at {@code t} of its length, {@code w} wide as a share of it. */
        void across(float[] q, float t, float w, float[] rgb, float light, float alpha) {
            float t0 = t - w / 2f, t1 = t + w / 2f;
            quad(q[0] + (q[2] - q[0]) * t0, q[1] + (q[3] - q[1]) * t0, q[0] + (q[2] - q[0]) * t1,
                    q[1] + (q[3] - q[1]) * t1, q[6] + (q[4] - q[6]) * t1, q[7] + (q[5] - q[7]) * t1,
                    q[6] + (q[4] - q[6]) * t0, q[7] + (q[5] - q[7]) * t0, rgb, light, alpha);
        }

        private float lx(float x) {
            return sx + (camera.cellToScreenX(x) - sx) * lift;
        }

        private float ly(float y) {
            return sy + (camera.cellToScreenY(y) - sy) * lift;
        }

        /** A strip of a cell's top along its side {@code s} ({@link #SX}), from {@code d0} to {@code d1} in from it. */
        void edge(int cx, int cy, int s, float d0, float d1, float[] rgb, float light, float alpha) {
            switch (s) {
                case 0: rect(cx + 1 - d1, cy, cx + 1 - d0, cy + 1, rgb, light, alpha); break;
                case 1: rect(cx, cy + 1 - d1, cx + 1, cy + 1 - d0, rgb, light, alpha); break;
                case 2: rect(cx + d0, cy, cx + d1, cy + 1, rgb, light, alpha); break;
                default: rect(cx, cy + d0, cx + 1, cy + d1, rgb, light, alpha); break;
            }
        }

        /** A short bracket across a strip along side {@code s}, at the middle of the cell's side. */
        void bracket(int cx, int cy, int s, float d0, float d1, float[] rgb, float light, float alpha) {
            float a = .45f, b = .55f;
            switch (s) {
                case 0: rect(cx + 1 - d1, cy + a, cx + 1 - d0, cy + b, rgb, light, alpha); break;
                case 1: rect(cx + a, cy + 1 - d1, cx + b, cy + 1 - d0, rgb, light, alpha); break;
                case 2: rect(cx + d0, cy + a, cx + d1, cy + b, rgb, light, alpha); break;
                default: rect(cx + a, cy + d0, cx + b, cy + d1, rgb, light, alpha); break;
            }
        }
    }

    /** Each cell's part of an envelope, its ordinal + 1, 0 where it is none's. */
    private static byte[] parts(CellTopology topology) {
        int gridW = topology.getWidth(), n = gridW * topology.getHeight();
        byte[] out = new byte[n];
        for (int at = 0; at < n; at++) {
            int x = at % gridW, y = at / gridW;
            if (!topology.isWall(x, y)) continue;
            FreestandingWalls.Part part = FreestandingWalls.part(topology.getSurfaceId(x, y));
            if (part != null) out[at] = (byte) (part.ordinal() + 1);
        }
        return out;
    }

    /**
     * Whether a wall cell is in sight: a wall blocks the sight that reveals it, so it is seen where the ground beside
     * it is, across one of its sides. No fog, all seen.
     */
    static boolean seen(boolean[] shown, int cell, int gridW, int gridH) {
        if (shown == null || shown.length < gridW * gridH) return true;
        if (shown[cell]) return true;
        int x = cell % gridW, y = cell / gridW;
        return x > 0 && shown[cell - 1] || x < gridW - 1 && shown[cell + 1]
                || y > 0 && shown[cell - gridW] || y < gridH - 1 && shown[cell + gridW];
    }

    /** The map's runs of freestanding wall: wall cells no building owns, not out over the void. */
    static List<FreestandingWalls.Run> runs(CellTopology topology) {
        int gridW = topology.getWidth();
        return FreestandingWalls.runs(gridW, topology.getHeight(), at -> {
            int x = at % gridW, y = at / gridW;
            return topology.isWall(x, y) && !topology.isWindow(x, y) && RoofCover.owner(topology, x, y) == 0
                    && topology.getGroundKind(x, y) != CellTopology.GroundKind.VOID;
        }, at -> FreestandingWalls.kind(topology.getSurfaceId(at % gridW, at / gridW),
                topology.getWorksStyle(at % gridW, at / gridW)));
    }
}
