package com.dillon.starsectormarines.battle.mapgen;

/**
 * Per-BSP-leaf identity tag. A "block" is one rectangular leaf of the
 * partition tree; the BlockKind decides which filler hollows the rect out
 * and which ground / doodads land inside.
 *
 * <p>Distinct from {@link com.dillon.starsectormarines.battle.map.CellTopology.GroundKind}
 * — BlockKind is the high-level "what is this block?" label (used by the
 * generator), GroundKind is the per-cell surface (used by the renderer). A
 * single {@code PLAZA} block contains a mix of {@code TILE}, {@code STONE},
 * and {@code STREET} ground kinds in its cells.
 */
public enum BlockKind {

    /** Hollow building with floor + perimeter wall + 1-2 doorways + chair/chest doodads. Most common in residential districts. */
    BUILDING_RESIDENTIAL,

    /** Hollow building like residential but with tiled floor accents and shop-style doodads (shelves, displays, registers). */
    BUILDING_COMMERCIAL,

    /** Hollow building with striped-safety-floor interior + crate/grate doodads. Larger footprints; warehouse / depot use. */
    BUILDING_INDUSTRIAL,

    /** Hardened post with turret-wall 3×3 perimeter and striped-floor interior. Hosts a single heavy turret POI (the eventual #3 turret apron lands inside this kind). */
    FORTIFIED_POST,

    /** Open public space — tile-cluster center with stone-path accents and sparse seating. Low cover, high traversal. */
    PLAZA,

    /** Marine touchdown area — striped floor + directional arrows + LZ marker decal. Marine spawn anchors prefer this kind when present. */
    LANDING_ZONE,

    /** Green block — grass blob center, sparse stone paths, optional benches. Low cover. */
    PARK,

    /** Outdoor depot — dirt blob ground with scattered crate / box clusters. Dense outdoor cover. */
    INDUSTRIAL_YARD,

    /** Impassable water with a walkable sand/stone shore strip. Map-edge or shoreline only — natural flank denial. */
    WATERFRONT,

    /** Broken ground — damaged-floor + damaged-wall art with rubble decals and damaged doodads. Ambush territory. */
    WASTELAND_RUBBLE,

    /**
     * Dense urban infill — 2×2 sub-buildings packed tight inside the leaf
     * with 1-cell alleys between them instead of proper roads. Reads as
     * tenements, market alleys, or shanty quarters depending on doodads.
     * The leaf still has the BSP road frame around it (the macro street);
     * density is internal. Multi-room close-quarters combat is the point.
     */
    DENSE_BLOCK,
}
