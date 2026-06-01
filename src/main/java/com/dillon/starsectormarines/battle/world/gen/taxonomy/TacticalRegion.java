package com.dillon.starsectormarines.battle.world.gen.taxonomy;

/**
 * One segmented tactical region — a cardinally-connected run of walkable cells
 * sharing a {@link RegionKind}, tagged with the attributes a placement pass
 * queries instead of re-deriving geometry. Immutable; built by
 * {@link TacticalRegionMap}.
 *
 * <p>Cells are NOT stored on the region (a 240×160 map holds tens of thousands
 * of walkable cells). The owning {@link TacticalRegionMap} keeps the
 * {@code regionIdAt(x,y)} lookup grid; consumers wanting per-cell membership go
 * through it. The region carries only the aggregate stats + bbox + centroid.
 *
 * <p>Attribute semantics (all derived in one pass over the region's cells):
 * <ul>
 *   <li>{@link #coverDensity} — fraction of cells adjacent (cardinally) to a
 *       non-walkable cell. High → cover-rich (alleys, courtyards); low → open
 *       killing ground (plazas, wide streets).</li>
 *   <li>{@link #meanExposure} — mean cardinal open-cross extent (cells visible
 *       along the four cardinals before a wall). High → crossroads/plaza; low
 *       → tight/blind. The overwatch signal.</li>
 *   <li>{@link #enclosure} — fraction of the region's boundary edges that
 *       border non-walkable rather than open onto another region. A <em>local</em>
 *       measure, robust to global porosity: a courtyard ringed by buildings is
 *       high-enclosure even on an otherwise wide-open map.</li>
 *   <li>{@link #openingCount} — number of distinct walkable "mouths" (connected
 *       runs of boundary cells that open onto another region). 1–2 = a
 *       chokepointed pocket; many = a thoroughfare. {@code 0} would mean an
 *       isolated walkable island (a connectivity bug).</li>
 *   <li>{@link #depthBand} / {@link #depth01} — geometric assault-gradient
 *       position; {@link DepthBand#UNSET} / {@code -1f} in legacy mode.</li>
 * </ul>
 */
public final class TacticalRegion {

    public final int id;
    public final RegionKind kind;

    /** Cell count. */
    public final int area;

    /** Inclusive bounding box in cell coords. */
    public final int left, top, right, bottom;
    /** Area-weighted centroid (integer cell coords). */
    public final int centroidX, centroidY;

    public final float coverDensity;
    public final float meanExposure;
    public final float enclosure;
    public final int openingCount;

    /** Normalized {@code [0,1]} distance from the attacker edge, or {@code -1f} when no axis. */
    public final float depth01;
    public final DepthBand depthBand;

    public TacticalRegion(int id, RegionKind kind, int area,
                          int left, int top, int right, int bottom,
                          int centroidX, int centroidY,
                          float coverDensity, float meanExposure,
                          float enclosure, int openingCount,
                          float depth01, DepthBand depthBand) {
        this.id = id;
        this.kind = kind;
        this.area = area;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.centroidX = centroidX;
        this.centroidY = centroidY;
        this.coverDensity = coverDensity;
        this.meanExposure = meanExposure;
        this.enclosure = enclosure;
        this.openingCount = openingCount;
        this.depth01 = depth01;
        this.depthBand = depthBand;
    }

    public int width()  { return right  - left + 1; }
    public int height() { return bottom - top  + 1; }

    @Override
    public String toString() {
        return String.format(
                "Region#%d[%s area=%d cover=%.2f exp=%.1f encl=%.2f mouths=%d depth=%s]",
                id, kind, area, coverDensity, meanExposure, enclosure, openingCount, depthBand);
    }
}
