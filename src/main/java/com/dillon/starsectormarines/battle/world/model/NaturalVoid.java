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
 */
public record NaturalVoid(Style style, List<Step> steps, float eyeMeters, String wallTexture,
                          float wallTileMeters, int seed) {

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
        ICE
    }

    /** A ledge: its depth below the surface, and how far into the void from the edge it begins. */
    public record Step(float depthMeters, float insetCells) { }

    public NaturalVoid {
        steps = List.copyOf(steps);
        if (steps.isEmpty()) throw new IllegalArgumentException("a natural void needs at least its floor");
    }

    /** How deep the floor lies, in metres below the surface. */
    public float floorMeters() { return steps.get(steps.size() - 1).depthMeters(); }
}
