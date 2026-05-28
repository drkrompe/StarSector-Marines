package com.dillon.starsectormarines.battle.world.tiles;

/**
 * Semantic ordering of cells on {@code graphics/tilesets/urban-tileset-3.png} —
 * a single-row sheet with variable-width art separated by transparent gutters.
 * The PNG is fed through {@link SpriteSheetSlicer}; the resulting frame array
 * lines up index-for-index with this enum's declaration order.
 *
 * <p>Adds a top-down road + sidewalk look that replaces the dashed
 * {@code pickRoadTile} autotile previously sourced from
 * {@code urban-tileset-2.png}. Two of the seven frames are road surfaces —
 * pick {@link #STREET_SQUARE} <em>or</em> {@link #STREET_IRREGULAR} for a
 * given road, never both on the same run (mixing produces a patchy read).
 * Two are sidewalk slabs: {@link #SIDEWALK} for straight runs and
 * {@link #SIDEWALK_CORNER} for cells with two perpendicular non-sidewalk
 * neighbors. The remaining three are doodads — placed by the map fillers,
 * not the autotile picker.
 *
 * <p>{@link Kind#GROUND} tiles render with the standard
 * {@link SlicedTileDrawer#DEFAULT_GROUND_INSET_PX} inset to avoid the
 * bilinear-sampler bleed at frame edges; {@link Kind#OVERLAY} tiles render
 * with inset=0 because their edge pixels are real content.
 */
public enum UrbanTile3 {
    STREET_SQUARE   (Kind.GROUND,  "street (square paver)"),
    STREET_IRREGULAR(Kind.GROUND,  "street (irregular paver)"),
    SIDEWALK        (Kind.GROUND,  "sidewalk"),
    SIDEWALK_CORNER (Kind.GROUND,  "sidewalk (corner)"),
    CULVERT         (Kind.OVERLAY, "culvert"),
    BENCH_S         (Kind.OVERLAY, "bench (south-facing)"),
    BENCH_E         (Kind.OVERLAY, "bench (east-facing)");

    /** Render-layer bucket. GROUND tiles get the edge inset; OVERLAY tiles pass through at full bbox. */
    public enum Kind { GROUND, OVERLAY }

    public final Kind kind;
    public final String label;

    UrbanTile3(Kind kind, String label) {
        this.kind = kind;
        this.label = label;
    }

    /** Frame index on the auto-sliced sheet. Position in the enum IS the position on the source PNG. */
    public int frameIndex() { return ordinal(); }

    public boolean isGround()  { return kind == Kind.GROUND; }
    public boolean isOverlay() { return kind == Kind.OVERLAY; }

    /** Indexed lookup by slicer frame index. Returns {@code null} for indices outside the declared range. */
    public static UrbanTile3 byFrame(int index) {
        UrbanTile3[] all = values();
        if (index < 0 || index >= all.length) return null;
        return all[index];
    }
}
