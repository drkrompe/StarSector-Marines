package com.dillon.starsectormarines.battle.world.model;

import java.util.List;

/**
 * What a map's void cells show when the drop is the land's own (a natural void style, `voids.md`): rock falling
 * away from the walkable ground in ledges to a floor far below. Presentation only, written once at generation;
 * nothing in the battle reads it. A map carries this or a {@link VoidBackdrop}, never both.
 *
 * @param style          which natural style the drop is drawn in
 * @param steps          the ledges going down, shallowest first: each stands {@code depthMeters} below the
 *                       surface and begins {@code insetCells} into the void from the walkable ground's edge, so
 *                       the drop narrows as it falls; the last step is the floor
 * @param eyeMeters      how high above the surface the camera looks down from, as {@link VoidBackdrop#eyeMeters}
 * @param wallTexture    a seamless mod image of the rock face seen straight on, its strata running across
 * @param wallTileMeters how many metres of face, across and down, one copy of the wall image covers
 * @param seed           the wander of the edges and ledges, so two drops never break alike
 * @param belts          ore conveyors on gantries out of an open pit, each from its floor up over the rim to the
 *                       works it feeds; empty for every other drop
 * @param marks          where the drop deepens and shallows along its length; empty for a drop as deep as its
 *                       {@code steps} everywhere. An open pit's benches are cut level, and it ignores them
 */
public record NaturalVoid(Style style, List<Step> steps, float eyeMeters, String wallTexture,
                          float wallTileMeters, int seed, List<Belt> belts, List<DepthMark> marks) {

    /** A natural void style: what the walls and the floor are, and where a generator lays it. */
    public enum Style {
        /** A river cut deep: rock strata walls with mossy ledges down to running water. */
        GORGE_RIVER,
        /** A volcanic rift: basalt walls lit orange from below by a river of molten rock crusting as it flows. */
        LAVA,
        /**
         * A glacial crevasse on a frozen world: blue-white ice walls in layered strata, snow drifted on the
         * ledges and blown off the lip, a cold mist in the deep blue below and a frozen meltwater floor.
         */
        ICE,
        /**
         * A desert canyon on an arid world: banded red and ochre sandstone walls, wind-blown sand on the ledges, and
         * a dry wash on the floor, pale sand braided between gravel bars with boulders fallen from the walls.
         */
        CANYON,
        /**
         * A dry rock gorge on a barren world: grey regolith and bedrock walls in strata, grit on the ledges, and a
         * floor of dust and rubble thick with fallen blocks. No water and no haze.
         */
        DRY_GORGE,
        /**
         * An open-cut mine: blasted benches stepping down evenly, a haul road ramping down them, ore conveyors
         * climbing out to the works, and a sump on the floor.
         */
        OPEN_PIT
    }

    /**
     * An ore conveyor on a gantry, in map cells: from {@code (floorX, floorY)} on the pit's floor it climbs to
     * {@code (rimX, rimY)}, the last void cell on its line, and runs on over the ground to {@code (endX, endY)}
     * at the wall of the works it feeds. Drawn only: nothing walks on it or under it differently.
     */
    public record Belt(float floorX, float floorY, float rimX, float rimY, float endX, float endY) { }

    /** A ledge: its depth below the surface, and how far into the void from the edge it begins. */
    public record Step(float depthMeters, float insetCells) { }

    /**
     * How deep the drop is at one place along it: at map cell {@code (x, y)} its floor lies {@code floorMeters} down,
     * and every ledge above stands at the same share of its own depth there as on the {@code steps}. Between marks
     * the depth blends smoothly, each mark weighing most near itself; a single mark sets the whole drop's depth.
     * Only the depth changes: where the lip, the ledges and the floor lie across the drop is the steps' insets.
     */
    public record DepthMark(float x, float y, float floorMeters) {
        public DepthMark {
            if (!(floorMeters > 0f)) throw new IllegalArgumentException("a drop's floor lies below its surface");
        }
    }

    /** A drop with conveyors, as deep as its steps everywhere. */
    public NaturalVoid(Style style, List<Step> steps, float eyeMeters, String wallTexture, float wallTileMeters,
                       int seed, List<Belt> belts) {
        this(style, steps, eyeMeters, wallTexture, wallTileMeters, seed, belts, List.of());
    }

    /** A drop with no conveyors. */
    public NaturalVoid(Style style, List<Step> steps, float eyeMeters, String wallTexture, float wallTileMeters,
                       int seed) {
        this(style, steps, eyeMeters, wallTexture, wallTileMeters, seed, List.of(), List.of());
    }

    /** This drop deepening and shallowing along its length as {@code marks} say. */
    public NaturalVoid withMarks(List<DepthMark> marks) {
        return new NaturalVoid(style, steps, eyeMeters, wallTexture, wallTileMeters, seed, belts, marks);
    }

    public NaturalVoid {
        steps = List.copyOf(steps);
        belts = belts == null ? List.of() : List.copyOf(belts);
        marks = marks == null ? List.of() : List.copyOf(marks);
        if (steps.isEmpty()) throw new IllegalArgumentException("a natural void needs at least its floor");
    }

    /** How deep the floor lies on the {@code steps}, in metres below the surface: everywhere, without marks. */
    public float floorMeters() { return steps.get(steps.size() - 1).depthMeters(); }
}
