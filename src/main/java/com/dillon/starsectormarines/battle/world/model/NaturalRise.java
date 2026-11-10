package com.dillon.starsectormarines.battle.world.model;

import java.util.ArrayList;
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
 * @param marks          where the rise climbs higher and sinks lower along its length; empty for a rise as high as
 *                       its {@code steps} everywhere
 */
public record NaturalRise(Style style, List<Step> steps, float eyeMeters, String wallTexture,
                          float wallTileMeters, int seed, List<HeightMark> marks) {

    /** A natural rise style: what the cliffs and the top are, and where a generator lays it. */
    public enum Style {
        /** A ravine's walls: rock strata cliffs with mossy ledges up to scrub and woods along the rim. */
        RAVINE_WALL,
        /**
         * A great crevasse's walls seen from its floor: blue-white ice banks with snow drifted on every terrace, up
         * to the wind-scoured snowfield, the gale streaming snow over the lip far above while the floor lies calm.
         */
        ICE_WALL
    }

    /** A ledge: its height above the walkable ground, and how far into the rise from the foot it begins. */
    public record Step(float heightMeters, float insetCells) { }

    /** How many terraces a bank climbs in, the top among them: the owner's pick (voids-bank-width, 16 cells and 8). */
    public static final int BANK_TERRACES = 8;
    /** How far the top stands back from the bank's foot, in cells: the owner's pick. */
    public static final float BANK_CELLS = 16f;
    /** The first terrace's set-back from the foot, in cells. */
    static final float BANK_FIRST_CELLS = .6f;

    /**
     * A bank rather than a cliff (owner, 2026-11-09: "widen out the hills ... it doesn't need to be a sheer drop"):
     * {@link #BANK_TERRACES} even terraces climbing to {@code topMeters}, the top standing {@link #BANK_CELLS} back
     * from the foot. Each face is short, so it leans out little under the camera, and the high ground stands well
     * back from the floor, so the camera looking down sees the fighting at the foot clearly rather than past the
     * lip of a wall.
     */
    public static List<Step> bank(float topMeters) {
        return bank(topMeters, BANK_CELLS, BANK_TERRACES);
    }

    /** A bank of {@code terraces} even terraces, its top standing {@code cells} back from the foot. */
    public static List<Step> bank(float topMeters, float cells, int terraces) {
        if (!(topMeters > 0f)) throw new IllegalArgumentException("a bank's top stands above its foot");
        if (terraces < 2 || !(cells > BANK_FIRST_CELLS)) throw new IllegalArgumentException("a bank climbs in terraces");
        List<Step> steps = new ArrayList<>();
        for (int k = 1; k <= terraces; k++) {
            float t = (k - 1) / (float) (terraces - 1);
            steps.add(new Step(topMeters * k / terraces, BANK_FIRST_CELLS + (cells - BANK_FIRST_CELLS) * t));
        }
        return steps;
    }

    /**
     * How high the rise stands at one place along it: at map cell {@code (x, y)} its top stands {@code topMeters}
     * up, and every ledge below at the same share of its own height there as on the {@code steps}. Between marks the
     * height blends smoothly, as a {@link NaturalVoid.DepthMark}'s depth does.
     */
    public record HeightMark(float x, float y, float topMeters) {
        public HeightMark {
            if (!(topMeters > 0f)) throw new IllegalArgumentException("a rise's top stands above its foot");
        }
    }

    /** A rise as high as its steps everywhere. */
    public NaturalRise(Style style, List<Step> steps, float eyeMeters, String wallTexture, float wallTileMeters,
                       int seed) {
        this(style, steps, eyeMeters, wallTexture, wallTileMeters, seed, List.of());
    }

    public NaturalRise {
        steps = List.copyOf(steps);
        marks = marks == null ? List.of() : List.copyOf(marks);
        if (steps.isEmpty()) throw new IllegalArgumentException("a natural rise needs at least its top");
        float highest = steps.get(steps.size() - 1).heightMeters();
        for (HeightMark mark : marks) highest = Math.max(highest, mark.topMeters());
        if (highest >= eyeMeters) throw new IllegalArgumentException("the camera must stand above the top of the rise");
    }

    /** This rise climbing and sinking along its length as {@code marks} say. */
    public NaturalRise withMarks(List<HeightMark> marks) {
        return new NaturalRise(style, steps, eyeMeters, wallTexture, wallTileMeters, seed, marks);
    }

    /** How high the top stands on the {@code steps}, in metres above the walkable ground: everywhere, without marks. */
    public float topMeters() { return steps.get(steps.size() - 1).heightMeters(); }
}
