package com.dillon.starsectormarines.battle.cordon;

import java.util.List;

/**
 * What a field redoubt or the logistics base is built from ({@code cordon-nouns.md}, Field redoubt, Logistics base;
 * owner, 2026-10-24): the allies' own walled compound, or a work made from what the country had standing. In open
 * country every redoubt is the compound. A town's line mixes the compound with a fortified farm and a fortified school;
 * a city's mixes it with the buildings round a crossroads walled up and one fortified apartment block. A city's base
 * is the generated shed, a depot in a warehouse block or a depot in a car park.
 *
 * <p>The compound is the proper military post; the others are troops making do. Each redoubt's pick is a pure
 * function of the density, its place in the line and the map's seed ({@link #redoubt}), and the redoubt nearest the
 * base, the one the line's supply runs through first, is twice as likely to be the compound.
 */
public enum CordonConstruction {
    /** The allies' walled compound: the generated field redoubt or logistics base, untouched. */
    COMPOUND("compound"),
    /** Town: a farmhouse and barns making the faces, a stone yard wall round the rest. */
    FARM("farm"),
    /** Town: a school's courtyard block and hall, a ceramic yard wall round the playground. */
    SCHOOL("school"),
    /** City: the buildings on the corners of a crossroads, blast walls across the street arms. */
    BLOCK("block"),
    /** City: one fortified apartment block across the road behind the crossing. */
    TENEMENT("tenement"),
    /** City base: the generated shed and yard, untouched. */
    SHED("shed"),
    /** City base: a depot in a warehouse block, open-air mech gantries behind it. */
    WAREHOUSE("warehouse"),
    /** City base: a depot in a car park, the lot under asphalt, open-air mech gantries behind it. */
    CAR_PARK("car-park");

    /** The name a construction goes by in the map's layout and in reports. */
    public final String id;

    CordonConstruction(String id) {
        this.id = id;
    }

    /** Whether this is the generated work, read by the compound's own wall dressing. */
    public boolean generated() {
        return this == COMPOUND || this == SHED;
    }

    /** The construction named {@code id}, or {@code null}. */
    public static CordonConstruction byId(String id) {
        for (CordonConstruction c : values()) if (c.id.equals(id)) return c;
        return null;
    }

    /** The redoubt constructions a density chooses among, the compound first. */
    public static List<CordonConstruction> redoubtChoices(CordonPlan.Density density) {
        return switch (density) {
            case RURAL -> List.of(COMPOUND);
            case TOWN -> List.of(COMPOUND, FARM, SCHOOL);
            case URBAN -> List.of(COMPOUND, BLOCK, TENEMENT);
        };
    }

    /** The base constructions a density chooses among, the shed first. */
    public static List<CordonConstruction> baseChoices(CordonPlan.Density density) {
        return density == CordonPlan.Density.URBAN ? List.of(SHED, WAREHOUSE, CAR_PARK) : List.of(SHED);
    }

    private static final long REDOUBT_SALT = 0x5245444F55425453L;
    private static final long BASE_SALT = 0x42415345574F524BL;

    /**
     * What redoubt {@code index} (0-based, west to east) is built from on the map of {@code seed}. Every choice of the
     * density is equally likely, except that the redoubt {@code nearestBase} names (the one nearest the logistics base)
     * counts the compound twice.
     */
    public static CordonConstruction redoubt(CordonPlan.Density density, int index, int nearestBase, long seed) {
        List<CordonConstruction> choices = redoubtChoices(density);
        if (choices.size() == 1) return choices.get(0);
        int weight = choices.size() + (index == nearestBase ? 1 : 0);
        int roll = Math.floorMod(mix(seed ^ REDOUBT_SALT ^ (index + 1) * 0x9E3779B97F4A7C15L), weight);
        // The extra weight is the compound's: rolls past the last choice land on it.
        return roll < choices.size() ? choices.get(roll) : COMPOUND;
    }

    /** What the logistics base is built from on the map of {@code seed}, each choice of the density equally likely. */
    public static CordonConstruction base(CordonPlan.Density density, long seed) {
        List<CordonConstruction> choices = baseChoices(density);
        if (choices.size() == 1) return choices.get(0);
        return choices.get(Math.floorMod(mix(seed ^ BASE_SALT), choices.size()));
    }

    /** A 64-bit finaliser (MurmurHash3's): neighbouring seeds land far apart, so seeds 1, 2, 3 do not pick alike. */
    static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }
}
