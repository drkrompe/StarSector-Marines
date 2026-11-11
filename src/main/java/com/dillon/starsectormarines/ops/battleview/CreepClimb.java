package com.dillon.starsectormarines.ops.battleview;

/**
 * Hive creep up a stood-up wall's face and over its top ({@code voids.md}, buildings in depth;
 * {@code colony-breach-nouns.md}, Creep). The creep claims the wall cells beside the ground it holds and fills them
 * slowly, so a wall cell's coverage is how long the creep has stood on it
 * ({@link com.dillon.starsectormarines.battle.creep.CreepField}). A young wall takes veins reaching up from its foot;
 * an old one a crust grown up from the foot with the veins running on above it, and the crust comes over the wall's
 * top from its outer side. Dying creep greys to the palette's dead tone.
 *
 * <p>Both are drawn from the creep sheet ({@link BattleSprites#SPRITE_CREEP_WALL}), which {@link CreepShading}, the
 * ground creep's own model, bakes over a creep edge along the top of each strip: the crust is the ground's crust, its
 * beaded lip and plates and veins, and the veins are those it lays over a cliff face. A wall shows the top of a strip
 * as tall as its creep stands, in cells, so the plates keep the ground's size. The face is addressed as {@code t}
 * along it and {@code z} up it as a share of its height, as {@link FacePainter} addresses it. GL-free.
 */
final class CreepClimb {

    /** How high creep at full coverage climbs a wall, metres: a storey and a little more. UNTUNED. */
    static final float CLIMB_METERS = 4.5f;
    /** Coverage below which a wall shows no creep: the first veins of a claim. */
    static final int FIRST = 16;
    /** Coverage at which a crust starts growing up from the foot; it covers the creep's reach at full coverage. UNTUNED. */
    static final int CRUST_FROM = 140;

    // The creep sheet's layout (CreepWallBaker): four strips, living crust, dead crust, living veins, dead veins.
    static final int SHEET_CELL_PX = 32, SHEET_CELLS_ALONG = 16, SHEET_CELLS_UP = 5;
    /** Where each strip's edge lies under its top, cells. */
    static final float SHEET_EDGE = 0.9f;
    /** How far above the edge a wall's creep starts its strip, cells: the lip's lobes reach up past the edge. */
    static final float LOBES = 0.6f;
    static final int CRUST = 0, DEAD_CRUST = 1, VEINS = 2, DEAD_VEINS = 3;

    /**
     * Where a face's creep is drawn: {@code t0..t1} along and {@code z0..z1} up the face, from strip {@code row} of
     * the sheet, {@code u0..u1} along it and {@code v0} (at {@code z1}) down to {@code v1} (at {@code z0}), in cells,
     * tinted and at {@code alpha}.
     */
    interface Sink {
        void patch(float t0, float t1, float z0, float z1, int row, float u0, float u1, float v0, float v1,
                   float light, float alpha);
    }

    /**
     * Where a wall top's crust is drawn: the quad from its outer side ({@code x0,y0} to {@code x1,y1}, cells) in to
     * ({@code x2,y2}, {@code x3,y3}), from strip {@code row}, {@code u0..u1} along it and {@code vOut} at the outer side
     * up to {@code vIn} at the inner, in cells.
     */
    interface TopSink {
        void patch(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int row,
                   float u0, float u1, float vOut, float vIn, float light, float alpha);
    }

    private CreepClimb() { }

    /** How far up a wall of {@code meters} the creep reaches, as a share of its height: 0 below {@link #FIRST}. */
    static float reach(int coverage, float meters) {
        if (coverage < FIRST || meters <= 0f) return 0f;
        return Math.min(1f, CLIMB_METERS * Math.min(255, coverage) / 255f / meters);
    }

    /** How much of the creep's reach is crust rather than veins: 0 young, 1 at full coverage. */
    static float crust(int coverage) {
        return Math.max(0f, Math.min(1f, (coverage - CRUST_FROM) / (float) (255 - CRUST_FROM)));
    }

    /** Where along the sheet's strips a wall cell starts, cells: walls' cells side by side run on along it. */
    static float along(int along) {
        return Math.floorMod(along, SHEET_CELLS_ALONG);
    }

    /**
     * Paints one cell's face of creep: its veins as high as the creep reaches and its crust over them as high as it has
     * crusted, each the living strip with the dead one over it as the creep withers. Nothing stands above {@code top}
     * (how much of the wall stands: 1 whole, less where its building is cut away or broken). Veins are fine work, drawn
     * only with {@code detail}. {@code along} is the cell's place along its wall (its column or row).
     */
    static void paint(int coverage, int vitality, float meters, float top, int along, float light, boolean detail,
                      Sink sink) {
        float reach = Math.min(top, reach(coverage, meters));
        if (reach <= 0f) return;
        float crusted = reach * crust(coverage);
        float u0 = along(along), u1 = u0 + 1f, dead = 1f - Math.max(0, Math.min(255, vitality)) / 255f;
        if (detail || crusted <= 0f) strip(VEINS, DEAD_VEINS, reach, meters, u0, u1, dead, light, sink);
        if (crusted > 0f) strip(CRUST, DEAD_CRUST, crusted, meters, u0, u1, dead, light, sink);
    }

    /** One strip from the foot to {@code z}: its top {@code z * meters} cells, stretched if taller than the strip. */
    private static void strip(int living, int deadRow, float z, float meters, float u0, float u1, float dead,
                              float light, Sink sink) {
        float v0 = SHEET_EDGE - LOBES, v1 = Math.min(SHEET_CELLS_UP, v0 + z * meters);
        if (dead < 1f) sink.patch(0f, 1f, 0f, z, living, u0, u1, v0, v1, light, 1f);
        if (dead > 0f) sink.patch(0f, 1f, 0f, z, deadRow, u0, u1, v0, v1, light, dead);
    }

    /** How far in from a wall's outer side the crust grows over its top, cells: none until it crusts. */
    static float overTop(int coverage) {
        float c = crust(coverage);
        return c <= 0f ? 0f : LOBES + .4f * c;
    }

    /**
     * Paints an old crust over a wall's top from its outer side ({@code ax,ay} to {@code bx,by}, outward
     * {@code nx,ny}, cells) in toward the wall's middle, its lip toward the middle: the creep that crusted the face come
     * over the top, which is what a view from above sees of it. Nothing on a young claim.
     */
    static void paintTop(int coverage, int vitality, int along, float ax, float ay, float bx, float by, float nx,
                         float ny, float light, TopSink sink) {
        float depth = overTop(coverage);
        if (depth <= 0f) return;
        float u0 = along(along), u1 = u0 + 1f, dead = 1f - Math.max(0, Math.min(255, vitality)) / 255f;
        float vIn = SHEET_EDGE - LOBES, vOut = vIn + depth;
        float cx = bx - nx * depth, cy = by - ny * depth, dx = ax - nx * depth, dy = ay - ny * depth;
        if (dead < 1f) sink.patch(ax, ay, bx, by, cx, cy, dx, dy, CRUST, u0, u1, vOut, vIn, light, 1f);
        if (dead > 0f) sink.patch(ax, ay, bx, by, cx, cy, dx, dy, DEAD_CRUST, u0, u1, vOut, vIn, light, dead);
    }
}
