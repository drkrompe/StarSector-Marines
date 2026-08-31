package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

/**
 * What the world's own ground is made of, distilled from the target planet so
 * {@code battle.world.gen} never sees a game API. The campaign-decoupled
 * vocabulary for terrain, exactly as {@link EconomicFunction} is for industry.
 *
 * <p><b>This is the wild surface, not every green thing on the map.</b> A
 * settlement's parks and street verges are ground the colonists made and
 * maintain — irrigated lawn on an airless rock is a statement about the
 * colony's wealth, not about the planet — so cultivated fills keep their own
 * character and do not consult this. What follows the palette is ground nobody
 * planted: the hinterland the road growth never reached, and the wild
 * {@code NATURE_*} lots inside the built area.
 *
 * <p>Most of the Sector is not a garden. {@link #ROCK} is the baseline rather
 * than {@link #VERDANT}, because a habitable world is the rare case worth
 * remarking on and defaulting to grass quietly asserts the opposite on every
 * map that has not said otherwise.
 *
 * <p>Each palette is a key into the mapping registry's authored surface
 * fillers, so adding one is a constant plus a JSON block — no new
 * {@link BlockKind}, and no fork of the district weight tables.
 */
public enum SurfacePalette {

    /** Bare regolith — stone, dirt and broken rubble. Rocks scatter; nothing grows. */
    ROCK(false, GroundKind.STONE),

    /** Dust and sand over hardpan. Rocks scatter; nothing grows. */
    ARID(false, GroundKind.SAND),

    /** A living world: grass and soil, with shrubs and tufts among the stones. */
    VERDANT(true, GroundKind.SAND);

    private final boolean openWater;
    private final GroundKind approachGround;

    SurfacePalette(boolean openWater, GroundKind approachGround) {
        this.openWater = openWater;
        this.approachGround = approachGround;
    }

    /**
     * Whether standing bodies of water belong on this world.
     *
     * <p>Every conquest map used to get a shoreline: measured over twelve seeds
     * a barren rock with no port carried the same five hundred cells of open
     * water as a garden world. Water is impassable, so that was not only an odd
     * picture — it was terrain shaping the approach on a world that has none.
     */
    public boolean bearsOpenWater() {
        return openWater;
    }

    /**
     * What the attacker's approach band is floored with.
     *
     * <p>The band is where the assault arrives and pushes inland from; on a
     * living world that is a sand shore, and on a barren one it is a stone
     * flat. It was {@link GroundKind#SAND} on every world, which is the same
     * Earth assumption this enum exists to remove.
     */
    public GroundKind approachGround() {
        return approachGround;
    }
}
