package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Which natural drop a world's ground is cut by ({@code voids.md}, placement in generated maps): the selection-layer
 * policy beside {@link SurfaceZoning}, from the {@link SurfacePalette} it picks to the {@link NaturalVoid.Style} a
 * generated outdoor map lays where it carves a drop ({@code NaturalChasm}). One table, {@link #forPalette}: a new
 * palette adds one row, and the switch does not compile until it has.
 *
 * <p>A row is the style with its steps, its wall image and the image's tile size; the look follows the style. The
 * camera is the natural styles' 120 m ({@code voids.md}, gorge river). A row with a depth band has no one depth: its
 * floor deepens and shallows along the cut within the band ({@link #marks}), the steps giving the ledges' shares of
 * it; a row without one is as deep everywhere as its steps.
 */
public final class NaturalVoidZoning {

    /** The camera the natural styles are drawn from. */
    public static final float EYE_METERS = 120f;

    /** Cells along a cut between two depth marks: a few swings across a map, each gentler than the marks' blend. */
    public static final int MARK_SPACING = 30;

    /**
     * One row: a style, its steps going down (shallowest first), its wall image and the metres one tile of it
     * spans; and the band the floor's depth varies in along a cut, {@code shallowest} to {@code deepest} metres, or
     * 0 and 0 for a drop as deep everywhere as its steps.
     */
    public record Drop(NaturalVoid.Style style, List<NaturalVoid.Step> steps, String wallTexture,
                       float wallTileMeters, float shallowest, float deepest) {
        /** A drop as deep everywhere as its steps. */
        public Drop(NaturalVoid.Style style, List<NaturalVoid.Step> steps, String wallTexture, float wallTileMeters) {
            this(style, steps, wallTexture, wallTileMeters, 0f, 0f);
        }

        /** Whether this drop's depth varies along a cut. */
        public boolean varies() { return deepest > shallowest; }

        /** The drop's look on one map, as deep everywhere as its steps. */
        public NaturalVoid look(int seed) {
            return new NaturalVoid(style, steps, EYE_METERS, wallTexture, wallTileMeters, seed, List.of());
        }

        /** The drop's look over a cut's cells ({@code w} wide, row-major): its depth varying along it if it varies. */
        public NaturalVoid look(int seed, boolean[] cells, int w, int h) {
            NaturalVoid look = look(seed);
            return varies() ? look.withMarks(marks(cells, w, h, shallowest, deepest, seed)) : look;
        }
    }

    /**
     * Depth marks along a cut ({@code cells}, {@code w} by {@code h}, row-major): one every {@link #MARK_SPACING}
     * cells along its longer run, at the middle of the cut there, its floor somewhere in {@code shallowest} to
     * {@code deepest} metres. The depths are spread over the band (one draw in each of as many equal slices of it
     * as there are marks, in a shuffled order), so every cut reaches near both ends of it rather than settling on
     * one depth. Empty when the cut has no cells.
     */
    public static List<NaturalVoid.DepthMark> marks(boolean[] cells, int w, int h, float shallowest, float deepest,
                                                    int seed) {
        int x0 = w, x1 = -1, y0 = h, y1 = -1;
        for (int i = 0; i < cells.length; i++) {
            if (!cells[i]) continue;
            int x = i % w, y = i / w;
            x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
        }
        if (x1 < 0) return List.of();
        boolean alongX = x1 - x0 >= y1 - y0;
        int from = alongX ? x0 : y0, to = alongX ? x1 : y1, across = alongX ? h : w;
        int count = Math.max(2, Math.round((to - from) / (float) MARK_SPACING) + 1);
        Random random = new Random(seed * 0x9E3779B97F4A7C15L ^ 0x44455054L);
        int[] order = new int[count];
        for (int k = 0; k < count; k++) order[k] = k;
        for (int k = count - 1; k > 0; k--) {
            int j = random.nextInt(k + 1), t = order[k];
            order[k] = order[j];
            order[j] = t;
        }
        List<NaturalVoid.DepthMark> marks = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            int at = Math.round(from + (to - from) * k / (float) (count - 1));
            long sum = 0;
            int n = 0;
            for (int c = 0; c < across; c++) {
                if (!cells[alongX ? c * w + at : at * w + c]) continue;
                sum += c;
                n++;
            }
            if (n == 0) continue;
            float mid = sum / (float) n + .5f;
            float depth = shallowest + (deepest - shallowest) * (order[k] + random.nextFloat()) / count;
            marks.add(alongX ? new NaturalVoid.DepthMark(at + .5f, mid, depth)
                    : new NaturalVoid.DepthMark(mid, at + .5f, depth));
        }
        return marks;
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
     * A barren world's dry gorge: grey regolith strata, grit on the ledges and a rubble floor 28 to 55 m down along
     * it (owner, 2026-11-10: "Should we just vary instead of having a hard floor?"), the steps' 40 m its middle.
     * Its own regolith wall: the basalt wall was tried on rock and read as holes at a wide zoom.
     */
    public static final Drop DRY_GORGE = new Drop(NaturalVoid.Style.DRY_GORGE, List.of(
            new NaturalVoid.Step(13f, .6f), new NaturalVoid.Step(27f, 1.3f), new NaturalVoid.Step(40f, 1.9f)),
            "graphics/battle/voids/regolith-wall.png", 12f, 28f, 55f);
    /**
     * A toxic world's gorge: acid-pitted olive strata, sulphur on the ledges, acid sludge 22 to 48 m down along it
     * (owner, as the dry gorge), the steps' 34 m its middle.
     */
    public static final Drop TOXIC_GORGE = new Drop(NaturalVoid.Style.TOXIC, List.of(
            new NaturalVoid.Step(11f, .6f), new NaturalVoid.Step(23f, 1.3f), new NaturalVoid.Step(34f, 1.9f)),
            "graphics/battle/voids/toxic-wall.png", 11f, 22f, 48f);
    /** A volcanic rift: basalt walls lit from below by a river of molten rock 40 m down. */
    public static final Drop LAVA_RIFT = new Drop(NaturalVoid.Style.LAVA, List.of(
            new NaturalVoid.Step(14f, .6f), new NaturalVoid.Step(27f, 1.2f), new NaturalVoid.Step(40f, 1.7f)),
            "graphics/battle/voids/basalt-wall.png", 12f);
    /**
     * A shallow lava river: a low bank of cooled crust and the melt 4 m down, running so its crust is seen to travel
     * (owner pick, 2026-11-09, of 2.5, 4 and 8 m). A volcanic world's drop (owner pick, 2026-11-10,
     * over the rift), with its older flows on the plain ({@code LavaFlows}).
     */
    public static final Drop LAVA_RIVER = new Drop(NaturalVoid.Style.LAVA, List.of(
            new NaturalVoid.Step(1.6f, .45f), new NaturalVoid.Step(4f, .9f)),
            "graphics/battle/voids/basalt-wall.png", 12f);

    /**
     * A foundry's melt: a kerb a metre deep and the melt 2 m down, between firebrick walls
     * (`ruins-expedition.md`, A forge works has a foundry). Laid by the ruins ground, never by a palette.
     */
    public static final Drop FOUNDRY = new Drop(NaturalVoid.Style.FOUNDRY, List.of(
            new NaturalVoid.Step(1f, .06f), new NaturalVoid.Step(2f, .12f)),
            "graphics/battle/voids/firebrick-wall.png", 2f);

    private NaturalVoidZoning() { }

    /**
     * The drop a world of this palette is cut by. A volcanic world takes {@link #LAVA_RIVER}.
     */
    public static Drop forPalette(SurfacePalette palette) {
        return switch (palette) {
            case VERDANT -> GORGE;
            case FROZEN -> CREVASSE;
            case ARID -> CANYON;
            // An irradiated world's drop is rock's dry gorge until a glassed wall of its own exists.
            case ROCK, IRRADIATED -> DRY_GORGE;
            case TOXIC -> TOXIC_GORGE;
            // The cut is the palette's layout's, the style only its look, so a lava world's river is rock's canyon line.
            case VOLCANIC -> LAVA_RIVER;
        };
    }
}
