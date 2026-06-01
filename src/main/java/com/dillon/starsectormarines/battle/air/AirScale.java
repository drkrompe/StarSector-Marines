package com.dillon.starsectormarines.battle.air;

/**
 * The single pixel-density anchor that sizes every airborne hull — shuttles,
 * fighters, and (overhead) ships alike.
 *
 * <h2>Why one constant</h2>
 * <p>Every Starsector sprite (core <em>and</em> modded) is drawn at one shared
 * pixel density so hulls read correctly next to each other. So a single
 * metres-per-pixel factor, anchored at <b>1 cell = 1 metre</b>, reproduces the
 * whole relative-size ladder — fighter &lt; frigate &lt; destroyer &lt; cruiser —
 * for free, and modded hulls inherit it without special-casing. We do
 * <em>not</em> import vanilla {@code su} as physical size (they're arena units),
 * and we do <em>not</em> author a per-hull length: a hull's render length is
 * derived from its sprite/{@code .ship} pixel extent times this one factor.
 *
 * <p>See {@code roadmap/air/hull-extraction.md} § Scale and
 * {@code roadmap/air/stories/global-pixel-density-scale.md}.
 *
 * <h2>Scope</h2>
 * <p>This is the <em>footprint</em> factor only — it sizes the rendered hull and
 * the geometry derived from sprite pixels (engine slots, and later collision
 * bounds). Kinematic feel (speed/accel, vanilla {@code su} → cells) is a
 * separate, independent scale and is not governed here.
 */
public final class AirScale {

    private AirScale() {}

    /** Anchor of the whole system: one sim cell is one metre. */
    public static final float METERS_PER_CELL = 1f;

    /**
     * Metres per sprite pixel — the one factor that sizes all hulls. {@code 0.65}
     * puts a Talon fighter at ~16 m, a Lasher frigate at ~62 m, an Onslaught
     * capital at ~250 m (off-map). Realistic absolute scale; presumes the
     * planned 512+ cell maps. Tune in playtest.
     */
    public static final float METERS_PER_PX = 0.65f;

    /**
     * The px↔cell ratio. Constant for every hull — this is the collapse that
     * unifies the engine-slot transform with the hull footprint (deriving a
     * hull's length as {@code spriteHeightPx × METERS_PER_PX} makes the old
     * per-hull {@code spriteHeightPx / visualLengthCells} divisor cancel down to
     * exactly this number).
     */
    public static final float PX_PER_CELL = METERS_PER_CELL / METERS_PER_PX;

    /**
     * Render length (cells) to fall back to when a hull's pixel extent can't be
     * read (missing/malformed {@code .ship}). Small but non-zero so a degraded
     * hull still draws something rather than collapsing to a point.
     */
    public static final float FALLBACK_LENGTH_CELLS = 8f;

    /** Forward render extent, in cells, for a sprite/hull this many pixels tall. */
    public static float cellsForHeightPx(float spriteHeightPx) {
        return spriteHeightPx * METERS_PER_PX / METERS_PER_CELL;
    }
}
