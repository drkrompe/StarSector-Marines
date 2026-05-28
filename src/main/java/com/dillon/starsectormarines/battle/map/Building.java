package com.dillon.starsectormarines.battle.map;

/**
 * One closed building region, found by
 * {@link com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill}
 * at map-gen time and held in the {@link Buildings} registry. Carries
 * everything the roof-render and fog-of-war visibility passes need to
 * operate without walking the whole cell grid:
 *
 * <ul>
 *   <li>Geometry — {@link #cellsX}/{@link #cellsY} parallel arrays of every
 *       cell that belongs to the building's interior (the flood-fill seed
 *       set). Roof render paints one quad per entry.</li>
 *   <li>Bounding rect — {@link #minX}/{@link #maxX}/{@link #minY}/{@link #maxY},
 *       inclusive. Used by the visibility pass for the "any contributor unit
 *       inside the bbox?" early-out before raycasting perimeter samples.</li>
 *   <li>Tint — {@link #tintR}/{@link #tintG}/{@link #tintB} in [0..1]. Bakes
 *       per-building flavor variation by multiplying the BRICK tile color at
 *       draw time. Deterministic from id + seed at flood-fill time.</li>
 *   <li>Visibility state — {@link #currentAlpha} is what the renderer reads;
 *       {@link #targetAlpha} is what the 10 Hz visibility pass writes, and
 *       the render path lerps current→target every frame so the cadence
 *       stutter doesn't show as a hard pop. 1.0 = roof fully opaque (hides
 *       interior), 0.0 = fully revealed.</li>
 * </ul>
 *
 * <p>This is a plain POJO — saved-game persistence will piggyback on the
 * generator re-running if it ever becomes a concern, but today buildings are
 * recomputed per battle, so save/load isn't a consumer.
 */
public final class Building {

    public final int id;
    public final BuildingKind kind;

    public final int minX;
    public final int maxX;
    public final int minY;
    public final int maxY;

    /** Interior cells of this building, parallel arrays. {@link #cellsX}[i] / {@link #cellsY}[i] = one cell. */
    public final int[] cellsX;
    public final int[] cellsY;

    public final float tintR;
    public final float tintG;
    public final float tintB;

    /** Renderer-visible alpha, lerped per frame toward {@link #targetAlpha}. Starts hidden. */
    public float currentAlpha = 1f;
    /** Visibility-pass target. 1.0 = hide interior, 0.0 = reveal. */
    public float targetAlpha = 1f;

    public Building(int id, BuildingKind kind,
                    int minX, int maxX, int minY, int maxY,
                    int[] cellsX, int[] cellsY,
                    float tintR, float tintG, float tintB) {
        this.id = id;
        this.kind = kind;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.cellsX = cellsX;
        this.cellsY = cellsY;
        this.tintR = tintR;
        this.tintG = tintG;
        this.tintB = tintB;
    }

    /** True iff the given cell falls inside this building's bounding rect. */
    public boolean containsCell(int x, int y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public int cellCount() {
        return cellsX.length;
    }
}
