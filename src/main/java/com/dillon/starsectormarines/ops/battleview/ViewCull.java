package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * The camera's view in cell space, so a collector visits what can be seen.
 *
 * <p>Every lever before this one was about submitting the same picture in fewer
 * calls. This is the other question: whether a collector has to visit every body
 * to produce the picture at all. On the canonical Conquest a close framing shows
 * a compound — a few dozen bodies out of four hundred — and the layer was
 * composing all four hundred, six or seven authored images each, and throwing
 * the ones off screen away in the drain.
 *
 * <p><b>A body's own extent, never a shared margin.</b> The obvious shape is a
 * viewport rectangle grown by some number of cells, and the number is the
 * problem: a marine is a cell across, a drone hub is 1.6, and a transport parked
 * on its hardstand is twelve, so one margin is either wrong for the transport or
 * useless for the marine. Instead every call passes the extent of the thing it
 * is about to draw, which each call site reads from whatever already told it how
 * big the sprite is — a mount's {@code visualCells}, a hull's resolved visual
 * length, a shadow ellipse's own computed length. Nothing here has to be kept in
 * step with authored art, because nothing here knows what the art is.
 *
 * <p><b>What it is not.</b> This is not visibility and not occlusion: fog and
 * the {@code hp <= 0} gate decide whether a body may be drawn at all, and they
 * are unchanged. This decides only whether a body that would be drawn can land
 * in the viewport, and a body it rejects is one whose pixels the drain was going
 * to discard anyway. It is therefore not a framing gate either — nothing is
 * withheld because it is too small to read (law 19 owns that), so the picture is
 * identical at every framing and that is the acceptance.
 *
 * <p><b>A camera that cannot say withholds nothing.</b> An unsized viewport, or
 * one at a degenerate zoom, yields {@link #EVERYTHING} rather than an empty
 * rectangle — the same reasoning as {@link ZoomDetail}'s. Embedded hosts that
 * fit a camera to a small scene therefore keep collecting whatever they framed,
 * and a host that never called {@code setViewport} draws what it always did
 * rather than nothing at all.
 *
 * <p>Turn culling off for a control run with
 * {@code -Dbattle.render.collectCulling=false}.
 */
public final class ViewCull {

    /** Control-run switch; see the class note. On by default. */
    public static final String PROPERTY = "battle.render.collectCulling";

    /** Rejects nothing: the camera could not describe a view, or culling is off. */
    public static final ViewCull EVERYTHING =
            new ViewCull(false, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

    private final boolean bounded;
    private final float minCellX;
    private final float minCellY;
    private final float maxCellX;
    private final float maxCellY;
    private final float minPx;
    private final float minPy;
    private final float maxPx;
    private final float maxPy;

    private ViewCull(boolean bounded, float minCellX, float minCellY,
                     float maxCellX, float maxCellY,
                     float minPx, float minPy, float maxPx, float maxPy) {
        this.bounded = bounded;
        this.minCellX = minCellX;
        this.minCellY = minCellY;
        this.maxCellX = maxCellX;
        this.maxCellY = maxCellY;
        this.minPx = minPx;
        this.minPy = minPy;
        this.maxPx = maxPx;
        this.maxPy = maxPy;
    }

    /**
     * The viewport's four edges in cell coordinates.
     *
     * <p>Deliberately unclamped to the grid, unlike
     * {@link BattleCamera#visibleCells}: that one exists to bound a walk over
     * cells, which must stay inside the map, while this one tests bodies that
     * carry their own float positions and may legitimately sit off the edge of
     * it. Clamping would reject a body standing outside the grid whose sprite
     * still reaches into the view.
     */
    public static ViewCull of(BattleCamera camera) {
        if (camera == null || !enabled()) return EVERYTHING;
        float cellPx = camera.cellPxSize();
        if (cellPx <= 0f || camera.vpW() <= 0f || camera.vpH() <= 0f) return EVERYTHING;
        float x0 = camera.screenToCellX(camera.vpX());
        float x1 = camera.screenToCellX(camera.vpX() + camera.vpW());
        float y0 = camera.screenToCellY(camera.vpY());
        float y1 = camera.screenToCellY(camera.vpY() + camera.vpH());
        if (!Float.isFinite(x0) || !Float.isFinite(x1)
                || !Float.isFinite(y0) || !Float.isFinite(y1)) {
            return EVERYTHING;
        }
        return new ViewCull(true,
                Math.min(x0, x1), Math.min(y0, y1),
                Math.max(x0, x1), Math.max(y0, y1),
                camera.vpX(), camera.vpY(),
                camera.vpX() + camera.vpW(), camera.vpY() + camera.vpH());
    }

    /**
     * Whether a sprite {@code extentCells} across, centred at {@code (cellX,
     * cellY)}, can land in the view.
     *
     * <p>The extent is a diameter rather than a radius, and is the drawn extent
     * rather than the simulation footprint — a marine's body radius is a
     * collision fact and its sprite is a different size. Pass the larger of the
     * two axes where a sprite is not square, and the whole rotated span where it
     * turns: a rejection here is silent by construction, so the extent a caller
     * passes is the one thing that can make this wrong, and erring large costs
     * only the bodies in a thin band outside the view.
     */
    public boolean visible(float cellX, float cellY, float extentCells) {
        if (!bounded) return true;
        float half = extentCells * 0.5f;
        return cellX + half >= minCellX && cellX - half <= maxCellX
                && cellY + half >= minCellY && cellY - half <= maxCellY;
    }

    /**
     * Whether decoration {@code extentPx} across, centred on a screen point,
     * can land in the viewport.
     *
     * <p>The screen-space form exists for decoration whose size is a number of
     * pixels rather than a number of cells — law 11's "decoration measured in
     * screen pixels stays legible at any zoom" — because such a thing is a
     * different number of cells at every framing and converting back is a
     * division that buys nothing.
     */
    public boolean visibleScreen(float px, float py, float extentPx) {
        if (!bounded) return true;
        float half = extentPx * 0.5f;
        return px + half >= minPx && px - half <= maxPx
                && py + half >= minPy && py - half <= maxPy;
    }

    /**
     * The same rectangle as four primitive bounds, already grown by a body's
     * extent, for a caller to hoist into locals.
     *
     * <p><b>This exists for one measured reason.</b> The live-sprite sweep's
     * inner loop composes a body from six or seven authored images and is very
     * large; a method call added to it cost a quarter of a microsecond per body
     * — about thirty times what four float comparisons can possibly cost — on
     * the framing where the cull rejects almost nothing and the call therefore
     * runs to completion every time. The comparisons are not the price; being a
     * call inside that particular loop is, and the loops small enough not to
     * care still use {@link #visible}.
     *
     * <p>An unbounded cull answers with infinities, so a caller that hoists
     * these into locals and compares against them keeps everything without
     * needing to know whether culling is on.
     */
    public float cullMinX(float extentCells) {
        return bounded ? minCellX - extentCells * 0.5f : Float.NEGATIVE_INFINITY;
    }

    public float cullMaxX(float extentCells) {
        return bounded ? maxCellX + extentCells * 0.5f : Float.POSITIVE_INFINITY;
    }

    public float cullMinY(float extentCells) {
        return bounded ? minCellY - extentCells * 0.5f : Float.NEGATIVE_INFINITY;
    }

    public float cullMaxY(float extentCells) {
        return bounded ? maxCellY + extentCells * 0.5f : Float.POSITIVE_INFINITY;
    }

    /** Whether an axis-aligned span in cell space overlaps the view. */
    public boolean visibleSpan(float minX, float minY, float maxX, float maxY) {
        if (!bounded) return true;
        return maxX >= minCellX && minX <= maxCellX
                && maxY >= minCellY && minY <= maxCellY;
    }

    /** Whether this instance rejects anything at all. */
    public boolean bounded() {
        return bounded;
    }

    /**
     * Whether culling is armed — for evidence that reports which run it was.
     *
     * <p>Read from the property on every call rather than latched at class
     * load, unlike the other render switches. The acceptance for this lever is
     * that a culled frame and an unculled one are the same picture, and that is
     * a comparison of two renders in one process: a latched flag would make it
     * a comparison of two processes, which is a weaker test of a worse kind. One
     * property read per collector per frame is a hashtable probe against a
     * frame's worth of geometry.
     */
    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    }
}
