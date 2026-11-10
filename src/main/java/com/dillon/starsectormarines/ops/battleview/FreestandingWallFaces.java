package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofCover;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.VisibleCellRect;

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
    private CellTopology runsOf;
    private long runsAt = -1;
    private List<FreestandingWalls.Run> runs = List.of();
    /** Each cell's part of a sealed town's envelope ({@link FreestandingWalls#part}), its ordinal + 1; 0 where none. */
    private byte[] parts = new byte[0];
    private final CreepPalette creepPalette = CreepPalette.active();
    private final float[] capTone = new float[3];
    /** The face creep is now being painted on: its foot from {@code a} to {@code b}, the view's centre and its lift. */
    private float cax, cay, cbx, cby, csx, csy, clift, calpha;
    /** Creep up the face now held ({@link CreepClimb}), each quad mapped from its foot to its lifted top. */
    private final CreepClimb.Sink creepOnFace = (t0, t1, z0, z1, r, g, b) -> mesh.appendQuad(
            faceX(t0, z0), faceY(t0, z0), faceX(t1, z0), faceY(t1, z0),
            faceX(t1, z1), faceY(t1, z1), faceX(t0, z1), faceY(t0, z1), r, g, b, calpha);

    private float faceX(float t, float z) {
        float foot = cax + (cbx - cax) * t;
        return foot + (csx + (foot - csx) * clift - foot) * z;
    }

    private float faceY(float t, float z) {
        float foot = cay + (cby - cay) * t;
        return foot + (csy + (foot - csy) * clift - foot) * z;
    }

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
        for (FreestandingWalls.Run run : runs) {
            if (!view.intersects(run.minX(), run.maxX(), run.minY(), run.maxY())) continue;
            float lift = BuildingFaces.lift(run.meters());
            FreestandingWalls.Kind kind = run.kind();
            if (kind == FreestandingWalls.Kind.ENVELOPE || kind == FreestandingWalls.Kind.ENVELOPE_PLINTH) {
                envelope(run, camera, view, shown, gridW, gridH, unseen, alphaMult);
                continue;
            }
            float[] segs = run.segments();
            for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
                int cell = (int) segs[e + 6];
                if (!view.contains(cell % gridW, cell / gridW)) continue;
                float x0 = segs[e], y0 = segs[e + 1], x1 = segs[e + 2], y1 = segs[e + 3];
                float nx = segs[e + 4], ny = segs[e + 5];
                if (!BuildingFaces.facing(nx, ny, (x0 + x1) / 2f, (y0 + y1) / 2f, viewX, viewY)) continue;
                float fog = seen(shown, cell, gridW, gridH) ? 1f : unseen;
                float light = BuildingFaces.shade(nx, ny) * fog;
                float ax = camera.cellToScreenX(x0), ay = camera.cellToScreenY(y0);
                float bx = camera.cellToScreenX(x1), by = camera.cellToScreenY(y1);
                mesh.appendQuad(ax, ay, bx, by,
                        sx + (bx - sx) * lift, sy + (by - sy) * lift,
                        sx + (ax - sx) * lift, sy + (ay - sy) * lift,
                        kind.r * light, kind.g * light, kind.b * light, alphaMult);
                if (creep != null) {
                    cax = ax; cay = ay; cbx = bx; cby = by; csx = sx; csy = sy; clift = lift; calpha = alphaMult;
                    CreepClimb.paint(creep.coverageAt(cell), creep.vitalityAt(cell), run.meters(), 1f, cell,
                            creepPalette, light, detail, creepOnFace);
                }
            }
            for (int cell : run.cells()) {
                int cx = cell % gridW, cy = cell / gridW;
                if (!view.contains(cx, cy)) continue;
                float fog = seen(shown, cell, gridW, gridH) ? 1f : unseen;
                // A cap faces the sky: the kind's own tint, brighter than any face.
                float light = fog;
                float ax = sx + (camera.cellToScreenX(cx) - sx) * lift;
                float bx = sx + (camera.cellToScreenX(cx + 1) - sx) * lift;
                float ay = sy + (camera.cellToScreenY(cy) - sy) * lift;
                float by = sy + (camera.cellToScreenY(cy + 1) - sy) * lift;
                mesh.appendQuad(ax, ay, bx, ay, bx, by, ax, by,
                        kind.r * light, kind.g * light, kind.b * light, alphaMult);
                float crust = creep == null ? 0f : CreepClimb.crust(creep.coverageAt(cell));
                if (crust > 0f) {
                    // An old creep crusts over the wall's top too, as it did the wall cell on the ground.
                    CreepClimb.tone(creepPalette.flesh(), creepPalette.dead(), creep.vitalityAt(cell), light,
                            capTone);
                    mesh.appendQuad(ax, ay, bx, ay, bx, by, ax, by, capTone[0], capTone[1], capTone[2],
                            alphaMult * crust);
                }
            }
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.ROOFS, mesh);
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
     * with the pipe run along its inner side, each buttress capped light.
     */
    private void envelope(FreestandingWalls.Run run, BattleCamera camera, VisibleCellRect view, boolean[] shown,
                          int gridW, int gridH, float unseen, float alphaMult) {
        float meters = run.meters(), lift = BuildingFaces.lift(meters);
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
        }
    }

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

        void band(float u0, float u1, float h0, float h1, float[] rgb, float light, float alpha) {
            float l0 = BuildingFaces.lift(meters * h0), l1 = BuildingFaces.lift(meters * h1);
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
    private static List<FreestandingWalls.Run> runs(CellTopology topology) {
        int gridW = topology.getWidth();
        return FreestandingWalls.runs(gridW, topology.getHeight(), at -> {
            int x = at % gridW, y = at / gridW;
            return topology.isWall(x, y) && !topology.isWindow(x, y) && RoofCover.owner(topology, x, y) == 0
                    && topology.getGroundKind(x, y) != CellTopology.GroundKind.VOID;
        }, at -> FreestandingWalls.kind(topology.getSurfaceId(at % gridW, at / gridW),
                topology.getWorksStyle(at % gridW, at / gridW)));
    }
}
