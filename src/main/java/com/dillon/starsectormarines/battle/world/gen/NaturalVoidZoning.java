package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.util.List;

/**
 * Which natural drop a world's ground is cut by ({@code voids.md}, placement in generated maps): the selection-layer
 * policy beside {@link SurfaceZoning}, from the {@link SurfacePalette} it picks to the {@link NaturalVoid.Style} a
 * generated outdoor map lays where it carves a drop ({@code NaturalChasm}). One table, {@link #forPalette}: a new
 * palette adds one row, and the switch does not compile until it has.
 *
 * <p>A row is the style with its steps, its wall image and the image's tile size; the look follows the style. The
 * camera is the natural styles' 120 m ({@code voids.md}, gorge river).
 */
public final class NaturalVoidZoning {

    /** The camera the natural styles are drawn from. */
    public static final float EYE_METERS = 120f;

    /**
     * One row: a style, its steps going down (shallowest first), its wall image and the metres one tile of it
     * spans.
     */
    public record Drop(NaturalVoid.Style style, List<NaturalVoid.Step> steps, String wallTexture,
                       float wallTileMeters) {
        /** The drop's look on one map. */
        public NaturalVoid look(int seed) {
            return new NaturalVoid(style, steps, EYE_METERS, wallTexture, wallTileMeters, seed, List.of());
        }
    }

    /** A river cut deep: strata walls with mossy ledges, running water 50 m down. Living worlds. */
    public static final Drop GORGE = new Drop(NaturalVoid.Style.GORGE_RIVER, List.of(
            new NaturalVoid.Step(16f, .6f), new NaturalVoid.Step(33f, 1.3f), new NaturalVoid.Step(50f, 1.9f)),
            "graphics/battle/voids/gorge-wall.png", 14f);
    /** A split in the ice sheet: blue-white ice walls, snow on the ledges, a frozen floor 36 m down. */
    public static final Drop CREVASSE = new Drop(NaturalVoid.Style.ICE, List.of(
            new NaturalVoid.Step(11f, .5f), new NaturalVoid.Step(23f, 1.1f), new NaturalVoid.Step(36f, 1.6f)),
            "graphics/battle/voids/ice-wall.png", 10f);
    /** The gorge's dry sister on a desert: banded sandstone walls, sand on the ledges and a dry wash 42 m down. */
    public static final Drop CANYON = new Drop(NaturalVoid.Style.CANYON, List.of(
            new NaturalVoid.Step(14f, .6f), new NaturalVoid.Step(28f, 1.25f), new NaturalVoid.Step(42f, 1.8f)),
            "graphics/battle/voids/canyon-wall.png", 12f);
    /**
     * A barren world's dry gorge: grey regolith strata, grit on the ledges and a rubble floor 40 m down. Its own
     * regolith wall: the basalt wall was tried on rock and read as holes at a wide zoom.
     */
    public static final Drop DRY_GORGE = new Drop(NaturalVoid.Style.DRY_GORGE, List.of(
            new NaturalVoid.Step(13f, .6f), new NaturalVoid.Step(27f, 1.3f), new NaturalVoid.Step(40f, 1.9f)),
            "graphics/battle/voids/regolith-wall.png", 12f);
    /** A volcanic rift: basalt walls lit from below by a river of molten rock 40 m down. */
    public static final Drop LAVA_RIFT = new Drop(NaturalVoid.Style.LAVA, List.of(
            new NaturalVoid.Step(14f, .6f), new NaturalVoid.Step(27f, 1.2f), new NaturalVoid.Step(40f, 1.7f)),
            "graphics/battle/voids/basalt-wall.png", 12f);
    /**
     * A shallow lava river: a low bank of cooled crust and the melt 4 m down, running so its crust is seen to travel
     * (owner pick, 2026-11-09, of 2.5, 4 and 8 m). A volcanic world's rift is {@link #LAVA_RIFT}; this is its river.
     */
    public static final Drop LAVA_RIVER = new Drop(NaturalVoid.Style.LAVA, List.of(
            new NaturalVoid.Step(1.6f, .45f), new NaturalVoid.Step(4f, .9f)),
            "graphics/battle/voids/basalt-wall.png", 12f);

    private NaturalVoidZoning() { }

    /**
     * The drop a world of this palette is cut by. A volcanic world takes {@link #LAVA_RIFT}.
     */
    public static Drop forPalette(SurfacePalette palette) {
        return switch (palette) {
            case VERDANT -> GORGE;
            case FROZEN -> CREVASSE;
            case ARID -> CANYON;
            // An irradiated world's drop is rock's dry gorge until a glassed wall of its own exists.
            case ROCK, IRRADIATED -> DRY_GORGE;
            // The cut is the palette's layout's, the style only its look, so a lava world's rift is rock's canyon line.
            case VOLCANIC -> LAVA_RIFT;
        };
    }
}
