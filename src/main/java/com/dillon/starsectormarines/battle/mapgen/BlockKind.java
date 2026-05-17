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

    /**
     * Multi-leaf compound seed — fortified base claimed from 2-3 adjacent
     * leaves with a shared outer wall ring, gates, corner gun emplacements,
     * and member leaves carved as command / barracks / armory sub-buildings.
     * Inter-leaf road frames inside the wall become {@link
     * com.dillon.starsectormarines.battle.map.CellTopology.GroundKind#STONE}
     * parade ground — that enclosed courtyard is the visual hinge that
     * makes the cluster read as "one base" rather than "a few buildings".
     *
     * <p>Appears in district theme rolls; if the post-label claim pass fails
     * to grow it (no eligible neighbor, hard cap already met), the leaf is
     * demoted back to {@link #FORTIFIED_POST} before fill dispatch.
     */
    MILITARY_BASE,

    /**
     * Multi-leaf compound — gated residential block claimed from 2-3
     * adjacent leaves. Lighter feel than {@link #MILITARY_BASE}: lower-HP
     * INDOOR wall ring, GRASS-yard interior, a single main gate. Sub-
     * buildings carved as residential dwellings. Reads as "walled-off
     * neighborhood" — suburban gated community in space.
     *
     * <p>Demoted to {@link #BUILDING_RESIDENTIAL} if the claim pass fails
     * to grow it.
     */
    GATED_HOUSING,

    /**
     * Multi-leaf compound — downtown skyscraper district. No outer wall;
     * the cluster reads as a dense cluster of large commercial buildings
     * with TILE-paved alleys between them, like a 2D top-down view of a
     * financial district. Each member leaf carves a big multi-room
     * commercial building filling most of the leaf.
     *
     * <p>Demoted to {@link #BUILDING_COMMERCIAL} if the claim pass fails
     * to grow it.
     */
    DENSE_QUARTER,

    /**
     * Sentinel — this leaf belongs to a multi-leaf compound (e.g. a
     * {@link #MILITARY_BASE}) that owns its fill end-to-end. Per-leaf
     * {@link com.dillon.starsectormarines.battle.mapgen.BlockFiller}
     * dispatch must skip these leaves; the compound's fill pass handles
     * them as a group.
     *
     * <p>Never appears in a {@link com.dillon.starsectormarines.battle.mapgen.MapDistrictTheme}
     * weight table — it's assigned during the post-label claim pass, not
     * during the initial roll.
     */
    COMPOUND_MEMBER,
}
