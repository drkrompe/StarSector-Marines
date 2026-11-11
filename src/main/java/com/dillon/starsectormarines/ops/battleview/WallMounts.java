package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;

/**
 * The props hung on a building's wall (a shop awning, a loading canopy, a wall lamp) and where they hang: on the face
 * the wall stands up as ({@link BuildingFaces}), so they move with it as the view pans.
 *
 * <p>A mount lies on the ground cell in front of its wall, its frame's north edge (its rail) along the wall's outer
 * side, which is the face's foot. Drawn there it lay on the street in front of a face leaning up and away from it. So
 * each corner of its frame is stood up at its own height: the rail's corners on the face itself, {@code railMeters}
 * up it, between the face's foot and its lifted eave exactly as the face is painted
 * ({@link BuildingFaces#onFace}); the outer corners, which hang free over the street, by the same pinhole lift as
 * everything else {@code outerMeters} up ({@link BuildingFaces#lift}). Where the wall stands lower (cut away to its
 * stub), the mount comes down with it. GL-free: only decisions.
 */
final class WallMounts {

    /** An awning's rail over a shop door, and its valance, which slopes down toward the street. */
    static final float AWNING_RAIL_METERS = 3.0f, AWNING_VALANCE_METERS = 2.5f;
    /** A wall lamp's bracket and the lamp at its end. */
    static final float LAMP_METERS = 3.4f, LAMP_HEAD_METERS = 3.2f;

    private WallMounts() { }

    /** Whether a doodad hangs off the wall its frame's north points to. */
    static boolean hangs(String id) {
        return id != null && (id.startsWith("doodad.street-awning-") || id.startsWith("doodad.street-lamp-bracket-"));
    }

    /** The height a mount's rail is fixed to its wall at, in metres. */
    static float railMeters(String id) {
        return id.startsWith("doodad.street-lamp-bracket-") ? LAMP_METERS : AWNING_RAIL_METERS;
    }

    /** The height of a mount's outer edge, in metres. */
    static float outerMeters(String id) {
        return id.startsWith("doodad.street-lamp-bracket-") ? LAMP_HEAD_METERS : AWNING_VALANCE_METERS;
    }

    /** The grid step from a mount toward its wall: its frame's north, turned by its quarter turns. */
    static int[] wallStep(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new int[]{0, 1};
            case 1 -> new int[]{-1, 0};
            case 2 -> new int[]{0, -1};
            default -> new int[]{1, 0};
        };
    }

    /**
     * The four corners of a placed mount's frame on the ground, in cells, into {@code out}: its rail's start and end
     * (the frame's north-west and north-east), then its outer edge's end and start (south-east, south-west). A
     * mirrored mount keeps its rail on the wall: its frame is mirrored across, and its texture's left and right swap
     * ({@link #MIRRORED_U}).
     */
    static void frame(Doodad d, float[] out) {
        float cx = d.cellX + d.footprintCellsX * .5f, cy = d.cellY + d.footprintCellsY * .5f;
        float hw = d.sourceCellsX * .5f, hh = d.sourceCellsY * .5f;
        // North is +y unturned; a quarter turn takes north to west.
        float[] ux = {-hw, hw, hw, -hw}, uy = {hh, hh, -hh, -hh};
        double theta = Math.toRadians(d.quarterTurns * 90.0);
        float cos = (float) Math.cos(theta), sin = (float) Math.sin(theta);
        for (int k = 0; k < 4; k++) {
            // A turned frame's corners land on the grid's lines; rounding drops the turn's float error.
            out[k * 2] = Math.round((cx + ux[k] * cos - uy[k] * sin) * 2f) / 2f;
            out[k * 2 + 1] = Math.round((cy + ux[k] * sin + uy[k] * cos) * 2f) / 2f;
        }
    }

    /** The texture's {@code u} at each corner of {@link #frame}, as laid and mirrored. */
    static final float[] U = {0f, 1f, 1f, 0f}, MIRRORED_U = {1f, 0f, 0f, 1f};
    /** The texture's {@code v} at each corner of {@link #frame}: 1 at the frame's top, its rail. */
    static final float[] V = {1f, 1f, 0f, 0f};

    /** How far a face's eave stands in from its foot where no face segment says: a plain wall cell's. */
    static final float EAVE_INSET = .3f;

    /**
     * The eave over a rail corner {@code (px, py)}, on the ground, into {@code out} at {@code at}: read off the face
     * segment ({@link BuildingFaces#segments}) turned {@code (nx, ny)} whose foot runs past {@code (sx, sy)} (a point
     * just inside the rail from the corner), at the corner's place along it, as the face is painted; where none does,
     * {@link #EAVE_INSET} in from the corner.
     */
    static void eave(float[] segs, float nx, float ny, float px, float py, float sx, float sy, float[] out, int at) {
        boolean alongX = ny != 0f;
        for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
            if (segs[e + 4] != nx || segs[e + 5] != ny) continue;
            float f0x = segs[e + 8], f0y = segs[e + 9], f1x = segs[e + 10], f1y = segs[e + 11];
            float across = alongX ? f0y : f0x, mine = alongX ? sy : sx;
            if (Math.abs(across - mine) > .05f) continue;
            float a0 = alongX ? f0x : f0y, a1 = alongX ? f1x : f1y, s = alongX ? sx : sy;
            if (s < Math.min(a0, a1) || s > Math.max(a0, a1) || a1 == a0) continue;
            float t = ((alongX ? px : py) - a0) / (a1 - a0);
            out[at] = segs[e] + (segs[e + 2] - segs[e]) * t;
            out[at + 1] = segs[e + 1] + (segs[e + 3] - segs[e + 1]) * t;
            return;
        }
        out[at] = px - nx * EAVE_INSET;
        out[at + 1] = py - ny * EAVE_INSET;
    }

    /**
     * Stands a mount's frame up off the ground, in place in {@code corners} (as {@link #frame} lays it, in cells):
     * its rail on its wall's face {@code railMeters} up, its outer edge hanging {@code outerMeters} up, the view's
     * centre at {@code (cx, cy)}.
     *
     * @param eaves      the face's eave over each rail corner, on the ground ({@code x0, y0, x1, y1}): where the roof
     *                   it runs up to stands before its lift
     * @param wallMeters the wall's full height; {@code lift} is its eave's lift
     * @param upright    how much of the wall stands, as a share of its height ({@link BuildingFaces#standing})
     * @param rise       how far the eye has risen ({@link BuildingFaces#rise})
     */
    static void standUp(float[] corners, float[] eaves, float cx, float cy, float wallMeters, float lift,
                        float upright, float railMeters, float outerMeters, float rise) {
        float z = Math.max(0f, Math.min(railMeters / wallMeters, upright));
        float rail = z * wallMeters;
        float outerLift = BuildingFaces.lift(Math.max(0f, rail - (railMeters - outerMeters)), rise);
        for (int k = 0; k < 2; k++) {
            int at = k * 2;
            corners[at] = BuildingFaces.onFace(corners[at], eaves[at], cx, lift, z);
            corners[at + 1] = BuildingFaces.onFace(corners[at + 1], eaves[at + 1], cy, lift, z);
        }
        for (int at = 4; at < 8; at++) {
            corners[at] = BuildingFaces.lifted(corners[at], at % 2 == 0 ? cx : cy, outerLift);
        }
    }
}
