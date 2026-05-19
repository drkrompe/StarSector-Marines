package com.dillon.starsectormarines.battle.air.engine;

/**
 * One engine slot scraped from a vanilla {@code .ship} hull spec, with
 * coordinates already transformed into this mod's frame:
 *
 * <ul>
 *   <li>{@link #localX}, {@link #localY} — slot position in shuttle-local
 *       cells. {@code +Y = nose}, {@code +X = starboard} (matches the
 *       turret-mount convention in
 *       {@link com.dillon.starsectormarines.battle.air.ShuttleType}).</li>
 *   <li>{@link #angleDegrees} — direction the plume fires, degrees. Same
 *       convention as our sprite-angle ({@code 0 = +Y / nose}, CCW-positive).
 *       Vanilla's convention is identical numerically (0 = forward, CCW),
 *       just defined off a different world axis — so the value transfers
 *       through without an offset.</li>
 *   <li>{@link #lengthCells}, {@link #widthCells} — plume size in cells.</li>
 *   <li>{@link #contrailSizeCells} — vanilla's {@code contrailSize} (the
 *       trail-width hint), converted to cells. Kept around for the eventual
 *       trail-emitter pass; the immediate render only needs length+width.</li>
 *   <li>{@link #style} — the raw vanilla style id ({@code MIDLINE},
 *       {@code LOW_TECH}, {@code HIGH_TECH}, ...). Drives flame color in
 *       the renderer once we wire a style-to-palette table.</li>
 * </ul>
 *
 * <p>Pure data — no behavior, no JSON. The parser
 * ({@link ShipSpecEngineParser}) does the vanilla-frame → our-frame
 * conversion and hands back arrays of these.
 */
public final class EngineSlotData {

    public final float localX;
    public final float localY;
    public final float angleDegrees;
    public final float lengthCells;
    public final float widthCells;
    public final float contrailSizeCells;
    public final String style;

    public EngineSlotData(float localX, float localY, float angleDegrees,
                          float lengthCells, float widthCells,
                          float contrailSizeCells, String style) {
        this.localX = localX;
        this.localY = localY;
        this.angleDegrees = angleDegrees;
        this.lengthCells = lengthCells;
        this.widthCells = widthCells;
        this.contrailSizeCells = contrailSizeCells;
        this.style = style;
    }
}
