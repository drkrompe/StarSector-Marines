package com.dillon.starsectormarines.ops.battleview;

import java.util.Arrays;

/**
 * Which roof the frame shows over a point of the ground, for what lies over the roofs to read them where they are
 * drawn rather than where they stand: a roof is lifted about the view's centre by its building's height class
 * ({@link BuildingFaces#lift}), so its edge stands out over the street beside it. Each cell holds the height class of
 * the roof shown over it ({@link #classAt}, one more than the class, 0 for none: no building, a building cut away or
 * razed); a point shows a roof of class {@code c} where, un-lifted by that class's lift, it falls on a cell holding
 * {@code c} ({@link #at}).
 *
 * <p>Set by the renderer each frame ({@link #set}, {@link #view}); {@link #version} moves whenever the cells change.
 */
final class RoofsShown {

    /** The height classes a roof can stand at: every class but rubble. */
    static final int CLASSES = BuildingFaces.RAZED;

    private byte[] classes = new byte[0];
    private int gridW, gridH;
    private long version;
    private final float[] lifts = new float[CLASSES];
    private float viewX, viewY;

    /** The cells' roofs, {@code classes[x + y * gridW]} one more than the class shown there or 0; kept, not copied. */
    void set(byte[] classes, int gridW, int gridH) {
        this.classes = classes;
        this.gridW = gridW;
        this.gridH = gridH;
        version++;
    }

    /** The view's centre, cells, and each height class's lift about it this frame. */
    void view(float viewX, float viewY, float[] classLifts) {
        this.viewX = viewX;
        this.viewY = viewY;
        System.arraycopy(classLifts, 0, lifts, 0, CLASSES);
    }

    /** One more than the height class of the roof held over cell {@code (x, y)}, or 0. */
    int classAt(int x, int y) {
        if (x < 0 || y < 0 || x >= gridW || y >= gridH) return 0;
        return classes[x + y * gridW];
    }

    /** The height class of the roof drawn over ground point {@code (x, y)}, cells, or -1 where no roof is. */
    int at(float x, float y) {
        for (int c = CLASSES - 1; c >= 0; c--) {
            float lift = lifts[c];
            int bx = (int) Math.floor(BuildingFaces.unlifted(x, viewX, lift));
            int by = (int) Math.floor(BuildingFaces.unlifted(y, viewY, lift));
            if (classAt(bx, by) == c + 1) return c;
        }
        return -1;
    }

    long version() { return version; }

    int gridW() { return gridW; }

    int gridH() { return gridH; }

    float viewX() { return viewX; }

    float viewY() { return viewY; }

    float lift(int c) { return lifts[c]; }

    /** A copy of the cells, for a caller that must not see them change under it. */
    byte[] cells() { return Arrays.copyOf(classes, classes.length); }
}
