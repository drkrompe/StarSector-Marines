package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RockDamage;

/**
 * What the world's own ground is made of, distilled from the target planet so
 * {@code battle.world.gen} never sees a game API. The campaign-decoupled
 * vocabulary for terrain, exactly as {@link EconomicFunction} is for industry.
 *
 * <p>It answers for the colony's ground too. Wild ground nobody planted takes
 * {@link #inlandGround()}; the lawn a settlement lays (yards, verges, parks)
 * takes the same {@link #inlandGround()}, through {@link CultivatedGround}.
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
    VERDANT(true, GroundKind.SAND),

    /**
     * Wind-packed snow over ice. Rocks scatter; nothing grows.
     *
     * <p>A quarter of the Sector's landable worlds are in this family — the
     * {@code frozen} types, {@code rocky_ice}, {@code cryovolcanic},
     * {@code toxic_cold} — and every one of them used to come out as bare grey
     * regolith, because {@link GroundKind#SNOW} was declared and then never
     * emitted by anything. It bears no open water: what water a frozen world
     * has is the ground.
     */
    FROZEN(false, GroundKind.SNOW),

    /**
     * A toxic world: corroded, chemically stained rock with crusted sulphur flats and pools of acid sludge. Rocks
     * scatter; nothing grows.
     *
     * <p>Its ground is ordinary {@link GroundKind#STONE}, {@link GroundKind#SAND} for the sulphur flats and
     * {@link GroundKind#WATER} for the sludge, each drawn from the palette's own art
     * ({@code GenMappingRegistry.groundBlockId(GroundKind, SurfacePalette)}), so the sludge is impassable exactly as
     * water is and every rule about stone holds. It bears no open water: the pools are {@link #bearsSludge() sludge}.
     */
    TOXIC(false, GroundKind.STONE);

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

    /**
     * Inland ground: unworked wild ground, thin margins too small for a nature parcel, and the colony's own open
     * ground too (yards, verges, parks, through {@link CultivatedGround}). Lawn only where things grow.
     *
     * <p>A settlement's lawn used to stay green on every world, read as a colony that shipped the soil in. On a
     * desert it was most of the town floor and read as a mistake instead (owner: "the green grass on this sand world
     * is strange"); packed earth was tried and its olive tile still read as dead lawn against sand and stone.
     */
    public GroundKind inlandGround() {
        return this == VERDANT ? GroundKind.GRASS : approachGround;
    }

    /**
     * Whether pools of standing chemical sludge and crusted sulphur flats lie on this world's wild ground. The pools
     * are {@link GroundKind#WATER} cells drawn as sludge, impassable as water is; the flats are
     * {@link GroundKind#SAND} drawn as sulphur crust. Neither harms anyone: hazard is not a ground rule.
     */
    public boolean bearsSludge() {
        return this == TOXIC;
    }

    /** The field-stone set lying loose on this ground; the mapping's rock pools for this palette name the same set. */
    public RockDamage.Biome stones() {
        return switch (this) {
            case VERDANT -> RockDamage.Biome.MOSSED;
            case ARID -> RockDamage.Biome.SANDSTONE;
            case ROCK -> RockDamage.Biome.BASALT;
            case FROZEN -> RockDamage.Biome.FROST;
            case TOXIC -> RockDamage.Biome.TOXIC;
        };
    }
}
