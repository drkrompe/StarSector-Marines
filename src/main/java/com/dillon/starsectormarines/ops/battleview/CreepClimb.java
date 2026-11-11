package com.dillon.starsectormarines.ops.battleview;

/**
 * Hive creep up a stood-up wall's face ({@code voids.md}, buildings in depth; {@code colony-breach-nouns.md}, Creep).
 * The creep claims the wall cells beside the ground it holds and fills them slowly, so a wall cell's coverage is how
 * long the creep has stood on it ({@link com.dillon.starsectormarines.battle.creep.CreepField}). A young wall takes
 * veins reaching up from its foot; an old one a lumpy crust banded up from the foot with the veins running on above it;
 * living creep wears the palette's flesh and veins with a glow in the crust, and dying creep greys to the palette's
 * dead tone. The face is addressed as {@code t} along it and {@code z} up it as a share of its height, as
 * {@link FacePainter} addresses it. GL-free.
 */
final class CreepClimb {

    /** How high creep at full coverage climbs a wall, metres: a storey and a little more. UNTUNED. */
    static final float CLIMB_METERS = 4.5f;
    /** Coverage below which a wall shows no creep: the first veins of a claim. */
    static final int FIRST = 16;
    /** Coverage at which a crust starts banding the foot; it covers the creep's reach at full coverage. UNTUNED. */
    static final int CRUST_FROM = 140;

    /** Where a face's creep is drawn: one flat quad, {@code t0..t1} along and {@code z0..z1} up, in a colour. */
    interface Sink {
        void quad(float t0, float t1, float z0, float z1, float r, float g, float b);
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

    /** The creep's colour between living ({@code vitality} 255) and dead (0). */
    static void tone(float[] living, float[] dead, int vitality, float light, float[] out) {
        float v = Math.max(0, Math.min(255, vitality)) / 255f;
        for (int k = 0; k < 3; k++) out[k] = (dead[k] + (living[k] - dead[k]) * v) * light;
    }

    private static final float[] FLESH = new float[3], RIM = new float[3], VEIN = new float[3], GLOW = new float[3];

    /** How far in from a wall's outer side the crust grows over its top, as a share of a cell: none until it crusts. */
    static float overTop(int coverage) {
        return .45f * crust(coverage);
    }

    /** Where a wall's top is drawn: one flat quad, its corners in cells, in a colour. */
    interface TopSink {
        void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                  float r, float g, float b);
    }

    /**
     * Paints an old crust over a wall's top from its outer side ({@code ax,ay} to {@code bx,by}, outward
     * {@code nx,ny}) in toward the wall's middle, in four ragged lumps lipped with the palette's rim: the creep that
     * crusted the face come over the top, which is what a view from above sees of it. Nothing on a young claim.
     */
    static void paintTop(int coverage, int vitality, int cell, float ax, float ay, float bx, float by, float nx,
                         float ny, CreepPalette palette, float light, TopSink sink) {
        float depth = overTop(coverage);
        if (depth <= 0f) return;
        tone(palette.flesh(), palette.dead(), vitality, light, FLESH);
        tone(palette.rim(), palette.dead(), vitality, light, RIM);
        for (int k = 0; k < 4; k++) {
            float t0 = k / 4f, t1 = (k + 1) / 4f;
            float d = depth * (.55f + .45f * BuildingFaces.hash(cell, 400 + k));
            float x0 = ax + (bx - ax) * t0, y0 = ay + (by - ay) * t0, x1 = ax + (bx - ax) * t1, y1 = ay + (by - ay) * t1;
            sink.quad(x0, y0, x1, y1, x1 - nx * d, y1 - ny * d, x0 - nx * d, y0 - ny * d, FLESH[0], FLESH[1], FLESH[2]);
            float l = Math.min(.08f, d * .3f);
            sink.quad(x0 - nx * (d - l), y0 - ny * (d - l), x1 - nx * (d - l), y1 - ny * (d - l), x1 - nx * d,
                    y1 - ny * d, x0 - nx * d, y0 - ny * d, RIM[0], RIM[1], RIM[2]);
        }
    }

    /**
     * Paints one cell's face of creep: its crust in four lumps along the face, each lipped with the palette's rim, a
     * glow in a living crust, and two or three veins reaching on above it. Nothing stands above {@code top} (how much of
     * the wall stands: 1 whole, less where its building is cut away or broken). Veins are fine work, drawn only with
     * {@code detail}. Called on the render thread only.
     */
    static void paint(int coverage, int vitality, float meters, float top, int cell, CreepPalette palette,
                      float light, boolean detail, Sink sink) {
        float reach = Math.min(top, reach(coverage, meters));
        if (reach <= 0f) return;
        float crusted = reach * crust(coverage);
        tone(palette.flesh(), palette.dead(), vitality, light, FLESH);
        tone(palette.rim(), palette.dead(), vitality, light, RIM);
        tone(palette.vein(), palette.dead(), vitality, light * .9f, VEIN);
        tone(palette.glow(), palette.dead(), vitality, light, GLOW);
        if (crusted > 0f) {
            float lip = Math.min(.08f, crusted * .25f);
            for (int k = 0; k < 4; k++) {
                float t0 = k / 4f, t1 = (k + 1) / 4f;
                float z = Math.min(reach, crusted * (.7f + .6f * BuildingFaces.hash(cell, 300 + k)));
                sink.quad(t0, t1, 0f, z, FLESH[0], FLESH[1], FLESH[2]);
                sink.quad(t0 + .03f, t1 - .03f, Math.max(0f, z - lip), z, RIM[0], RIM[1], RIM[2]);
                // Its plates: a dark seam under the lip and a raised node in the lump, so a crust reads as grown, not
                // as paint, against a wall of its own tone.
                sink.quad(t0, t1, Math.max(0f, z - lip * 1.8f), Math.max(0f, z - lip), VEIN[0], VEIN[1], VEIN[2]);
                float n = t0 + (.2f + .4f * BuildingFaces.hash(cell, 380 + k)) * (t1 - t0);
                float nz = z * (.25f + .3f * BuildingFaces.hash(cell, 390 + k));
                sink.quad(n, n + .06f, nz, Math.min(z, nz + Math.max(lip, crusted * .12f)), RIM[0], RIM[1], RIM[2]);
                if (vitality >= 160 && BuildingFaces.hash(cell, 310 + k) < .5f) {
                    float g = .3f + .4f * BuildingFaces.hash(cell, 320 + k);
                    sink.quad(t0 + .08f, t0 + .16f, z * g, Math.min(z, z * g + .06f), GLOW[0], GLOW[1], GLOW[2]);
                }
            }
        }
        if (!detail) return;
        int veins = 2 + (BuildingFaces.hash(cell, 330) < .5f ? 1 : 0);
        for (int k = 0; k < veins; k++) {
            float t = .1f + .8f * BuildingFaces.hash(cell, 340 + k);
            float z1 = Math.min(reach, Math.max(crusted, reach * (.55f + .45f * BuildingFaces.hash(cell, 350 + k))));
            if (z1 <= 0f) continue;
            sink.quad(t, t + .05f, 0f, z1, VEIN[0], VEIN[1], VEIN[2]);
            // A branch off it, part-way up, offset along the face as a step.
            float bz = z1 * (.4f + .3f * BuildingFaces.hash(cell, 360 + k));
            float bt = t + (BuildingFaces.hash(cell, 370 + k) < .5f ? -.09f : .05f);
            sink.quad(Math.max(0f, bt), Math.min(1f, bt + .04f), bz, Math.min(z1, bz + reach * .2f),
                    VEIN[0], VEIN[1], VEIN[2]);
        }
    }
}
