package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * The rubble a damaged building wall leaves on the ground at its foot, drawn from the wall rubble sheet
 * ({@code graphics/decals/wall-rubble.png}, made by {@code art-source/wall-rubble}): a 4 x 4 grid of neutral grey
 * pieces, each tinted by its wall's material. Rows: {@link #HEAP} (a long pile heaped against where the wall stood,
 * along its cell's top edge), {@link #SLAB} (big broken pieces), {@link #CLUSTER} (a few blocks together) and
 * {@link #SCATTER} (fragments over a faint dust film).
 *
 * <p>Rubble lies on the open ground outside the wall, so it is laid for every wall that has come down or been hurt,
 * whichever way its face is turned: only a face turned to the view is stood up, but the ground in front of the far
 * walls is just as open. The placing is GL-free ({@link #place}); {@link #emit} draws it under the units.
 */
final class WallRubble {

    /** Rows of the sheet. */
    static final int HEAP = 0, SLAB = 1, CLUSTER = 2, SCATTER = 3;
    static final int GRID = 4;
    /** Logical pixels per cell of the shipped sheet. */
    static final int CELL_PX = 128;
    /** How far down its cell a heap's top edge, the wall's side, lies ({@code art-source/wall-rubble/export.py}). */
    static final float HEAP_TOP = .11f;
    /** How much has come down: a hurt wall's, a wall whose roof fell in, a breached or razed wall's. */
    static final int HURT = 1, BROKEN = 2, BREACHED = 3, RAZED = 3;
    /** Floats per placed piece: {@code t, out, size, turn, frame}. */
    static final int PIECE = 5;
    /** The most pieces one wall cell lays. */
    static final int MOST = 4;

    /** How much rubble a wall cell has shed: none until it is badly hurt. */
    static int amount(boolean breached, boolean roofFallen, float hurt) {
        if (breached) return BREACHED;
        if (roofFallen) return BROKEN;
        return hurt > .45f ? HURT : 0;
    }

    /**
     * Lays a wall cell's rubble into {@code out} from {@code at}, each piece as {@link #PIECE} floats in the wall's
     * own frame: {@code t} along the wall from its start, {@code out} cells out from its foot to the piece's centre,
     * its {@code size} in cells, its {@code turn} in degrees from lying square to the wall (its top toward the wall),
     * and its sheet {@code frame}. Returns the pieces laid. A heap's top edge lies on the wall's foot; every other
     * piece lies wholly beyond it, on the open ground.
     */
    static int place(int cell, int amount, float[] out, int at) {
        int n = 0;
        if (amount >= BROKEN) {
            // The heap against the wall, wider than the cell so neighbouring heaps run together.
            float size = amount >= BREACHED ? 1.5f : 1.15f;
            n = put(out, at, n, .5f + .1f * (BuildingFaces.hash(cell, 210) - .5f), (.5f - HEAP_TOP) * size, size,
                    12f * (BuildingFaces.hash(cell, 211) - .5f), frame(HEAP, cell, 212));
        }
        int loose = amount >= BREACHED ? 3 : amount >= BROKEN ? 2 : 1;
        for (int k = 0; k < loose; k++) {
            int row = amount == HURT && k == 0 ? (BuildingFaces.hash(cell, 220) < .5f ? SLAB : CLUSTER)
                    : k == loose - 1 && amount >= BROKEN ? SCATTER : CLUSTER;
            float size = row == SCATTER ? .9f + .3f * BuildingFaces.hash(cell, 230 + k)
                    : .55f + .3f * BuildingFaces.hash(cell, 230 + k);
            // Beyond the heap, or near the foot where there is none; a piece's half-size keeps it off the wall.
            float from = amount >= BROKEN ? .55f * (amount >= BREACHED ? 1.5f : 1.15f) : .1f;
            float dist = from + size * .4f + .5f * BuildingFaces.hash(cell, 240 + k);
            n = put(out, at, n, BuildingFaces.hash(cell, 250 + k), dist, size,
                    360f * BuildingFaces.hash(cell, 260 + k), frame(row, cell, 270 + k));
        }
        return n;
    }

    private static int put(float[] out, int at, int n, float t, float dist, float size, float turn, int frame) {
        int o = at + n * PIECE;
        out[o] = t;
        out[o + 1] = dist;
        out[o + 2] = size;
        out[o + 3] = turn;
        out[o + 4] = frame;
        return n + 1;
    }

    private static int frame(int row, int cell, int salt) {
        return row * GRID + Math.min(GRID - 1, (int) (BuildingFaces.hash(cell, salt) * GRID));
    }

    /**
     * The turn, degrees, that lays a piece's top toward the wall whose outward normal on screen is {@code (nx, ny)}:
     * a quad's top is {@code +y} unturned, and a turn is counter-clockwise.
     */
    static float towardWall(float nx, float ny) {
        return (float) Math.toDegrees(Math.atan2(nx, -ny));
    }

    /** The sheet's frames: every cell whole, in reading order. */
    static SpriteSheetFrames frames() {
        SpriteSheetFrames.Frame[] frames = new SpriteSheetFrames.Frame[GRID * GRID];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new SpriteSheetFrames.Frame(i % GRID * CELL_PX, i / GRID * CELL_PX, CELL_PX, CELL_PX);
        }
        return new SpriteSheetFrames(GRID * CELL_PX, GRID * CELL_PX, frames);
    }

    /** Every piece of a frame's rubble, as {@link FacePainter#debris} holds them, drawn tinted under the units. */
    void emit(DrawList out, float[] debris, int count, SpriteAPI sheet, SpriteSheetFrames frames) {
        if (sheet == null || frames == null || count == 0) return;
        for (int i = 0; i < count; i++) {
            int o = i * FacePainter.DEBRIS;
            SpriteSheetFrames.Frame f = frames.frames[(int) debris[o + 4]];
            out.addSheetQuad(RenderLayer.DECALS, sheet, f.x, f.y, f.w, f.h, debris[o], debris[o + 1], debris[o + 2],
                    debris[o + 2], debris[o + 3], debris[o + 5], debris[o + 6], debris[o + 7], debris[o + 8]);
        }
    }
}
