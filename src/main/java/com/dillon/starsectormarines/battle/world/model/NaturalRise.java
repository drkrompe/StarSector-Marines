package com.dillon.starsectormarines.battle.world.model;

import java.util.List;

/**
 * What a map's rise cells show: the land standing up from the walkable ground in cliffs and ledges to high
 * ground above it, the walls of a ravine whose floor the battle is fought on (a natural rise style,
 * `voids.md`). The inverse of a {@link NaturalVoid}: the same drawing with its depth turned. Presentation
 * only, written once at generation; nothing in the battle reads it.
 *
 * <p>A rise cell is drawn as a void cell is, the ground tiles leaving it to this, and is told from a void cell
 * by the battle's own difference: a rise blocks sight and a void does not.
 *
 * @param style          which natural style the rise is drawn in
 * @param steps          the ledges going up, lowest first: each stands {@code heightMeters} above the walkable
 *                       ground and begins {@code insetCells} into the rise from its foot, so the high ground
 *                       steps back as it climbs; the last step is the top, the plateau beyond the rim
 * @param eyeMeters      how high above the walkable ground the camera looks down from, as
 *                       {@link VoidBackdrop#eyeMeters}; well above the top step
 * @param wallTexture    a seamless mod image of the rock face seen straight on, its strata running across
 * @param wallTileMeters how many metres of face, across and down, one copy of the wall image covers
 * @param seed           the wander of the foot and ledges, so two rises never break alike
 */
public record NaturalRise(Style style, List<Step> steps, float eyeMeters, String wallTexture,
                          float wallTileMeters, int seed) {

    /** A natural rise style: what the cliffs and the top are, and where a generator lays it. */
    public enum Style {
        /** A ravine's walls: rock strata cliffs with mossy ledges up to scrub and woods along the rim. */
        RAVINE_WALL
    }

    /** A ledge: its height above the walkable ground, and how far into the rise from the foot it begins. */
    public record Step(float heightMeters, float insetCells) { }

    public NaturalRise {
        steps = List.copyOf(steps);
        if (steps.isEmpty()) throw new IllegalArgumentException("a natural rise needs at least its top");
        if (steps.get(steps.size() - 1).heightMeters() >= eyeMeters) {
            throw new IllegalArgumentException("the camera must stand above the top of the rise");
        }
    }

    /** How high the top stands, in metres above the walkable ground. */
    public float topMeters() { return steps.get(steps.size() - 1).heightMeters(); }
}
