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

    /**
     * Nominal anchor of the unit system: one sim cell is one "metre". At the
     * gameplay-tuned density below these are play-scale units, not literal
     * metres — see {@link #METERS_PER_PX}.
     */
    public static final float METERS_PER_CELL = 1f;

    /**
     * The one factor that sizes every air hull: render length =
     * {@code spriteHeightPx × this}. Because all vanilla art shares one pixel
     * density, this single number reproduces the whole relative-size ladder —
     * base and modded — for free.
     *
     * <p><b>Gameplay scale, not literal metres.</b> The realistic calibration
     * (~0.65 → a Kite at ~43 cells, a Valkyrie at ~172) dwarfed current maps, so
     * this is dialled down for playability: at {@code 0.045} the smallest shuttle
     * (Kite, 66 px) reads at ~3 cells and a Valkyrie (264 px) at ~12 — a true
     * ~4× ladder, sized to sit beside ~1-cell infantry. Re-dial freely; turret
     * mount positions track it automatically (turret <em>size</em> is fixed per
     * kind — see {@link #TURRET_AUTHORING_HULL_CELLS}).
     */
    public static final float METERS_PER_PX = 0.045f;

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

    /**
     * The hull render length (cells) the shuttle turret <b>mount offsets</b>
     * ({@code ShuttleType.a2gKit}) were authored against. A mount's offset is
     * scaled by {@code derivedHullLength / this}, so it sits at the right spot on
     * a hull of any size and spreads across a large hull — which is what lets
     * each turret's line of sight differ front-to-rear.
     *
     * <p>Drives turret <b>placement</b> only, never turret <b>size</b>: a turret
     * is fixed hardware — the same {@code TurretKind} renders at its
     * {@code visualCells} on every hull, exactly like a ground {@code MapTurret}.
     * Raise this to pull mounts toward center, lower it to spread them out.
     * Independent of {@link #METERS_PER_PX} (overall ship size).
     */
    public static final float TURRET_AUTHORING_HULL_CELLS = 4f;

    /** Forward render extent, in cells, for a sprite/hull this many pixels tall. */
    public static float cellsForHeightPx(float spriteHeightPx) {
        return spriteHeightPx * METERS_PER_PX / METERS_PER_CELL;
    }
}
