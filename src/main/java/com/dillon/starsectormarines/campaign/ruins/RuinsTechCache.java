package com.dillon.starsectormarines.campaign.ruins;

/**
 * One tech cache ({@link RuinsFindTier#TECH}): a find that holds the game's own equipment, never new items. Its
 * contents roll at settlement from one of vanilla's ruins drop groups, picked here from the site and the find alone,
 * so a find holds the same kind of thing on every visit.
 *
 * <p>The groups and their weights are vanilla's own share of them in every ruins salvage spec
 * ({@code salvage_entity_gen_data.csv}: {@code weapons2} five times {@code any_hullmod_low} and
 * {@code blueprints_low}). {@code weapons2} holds the fighter wings too. A cache rolls its group without vanilla's
 * {@code nothing} entry, so it is never empty.
 *
 * @param findId    the find's stable id, which the site's finds memory records once taken
 * @param dropGroup the vanilla drop group its contents roll from
 */
public record RuinsTechCache(String findId, String dropGroup) {

    public static final String WEAPONS = "weapons2";
    public static final String HULLMODS = "any_hullmod_low";
    public static final String BLUEPRINTS = "blueprints_low";

    private static final String[] GROUPS = {WEAPONS, HULLMODS, BLUEPRINTS};
    private static final int[] WEIGHTS = {5, 1, 1};
    private static final long SALT = 0x5445434843414348L;

    public RuinsTechCache {
        if (findId == null || findId.isBlank() || dropGroup == null) throw new IllegalArgumentException("a tech cache needs a find");
    }

    /** The cache a find holds: its drop group drawn from the site and the find's id. */
    public static RuinsTechCache of(long sectorSeed, String marketId, String findId) {
        if (marketId == null || findId == null) throw new IllegalArgumentException("a tech cache needs a site");
        long site = RuinsOccupants.mix(sectorSeed, marketId.hashCode());
        double roll = RuinsOccupants.unit(RuinsOccupants.mix(RuinsOccupants.mix(site, SALT), findId.hashCode()));
        double at = roll * (WEIGHTS[0] + WEIGHTS[1] + WEIGHTS[2]);
        for (int i = 0; i < GROUPS.length; i++) {
            if (at < WEIGHTS[i]) return new RuinsTechCache(findId, GROUPS[i]);
            at -= WEIGHTS[i];
        }
        return new RuinsTechCache(findId, GROUPS[GROUPS.length - 1]);
    }
}
