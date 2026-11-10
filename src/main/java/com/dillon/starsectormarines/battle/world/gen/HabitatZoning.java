package com.dillon.starsectormarines.battle.world.gen;

/**
 * The selection-layer policy that decides how a world's settlements are built ({@link Habitat}): the built
 * counterpart to {@link SurfaceZoning} and {@link ClimateZoning}, and game-API-free for the same reason.
 *
 * <p>It reads the hazards first, because they are the campaign's own statement of what the air does: hard radiation
 * shields a town, and air that kills or is not there seals it. Shielding answers both, so radiation wins. A battle with
 * no market behind it has {@link Climate#NONE} and builds the open town it always did.
 */
public final class HabitatZoning {

    private HabitatZoning() { }

    /**
     * The habitat a world builds.
     *
     * @param surface what the world's ground is made of, from {@link SurfaceZoning}: for a palette whose ground implies
     *                its air, so a world with no market behind it still adapts. No palette does yet.
     * @param climate what the world's air does, from {@link ClimateZoning}; null reads as none
     */
    public static Habitat forWorld(SurfacePalette surface, Climate climate) {
        Climate air = climate == null ? Climate.NONE : climate;
        if (air.has(Climate.Hazard.RADIATION)) return Habitat.SHIELDED;
        if (air.has(Climate.Hazard.TOXIC) || air.has(Climate.Hazard.AIRLESS)) return Habitat.SEALED;
        return Habitat.OPEN;
    }

    /** {@link #forWorld} for a target profile; the open town for none. */
    public static Habitat forProfile(TargetProfile profile) {
        return profile == null ? Habitat.OPEN : forWorld(profile.surface(), profile.climate());
    }
}
