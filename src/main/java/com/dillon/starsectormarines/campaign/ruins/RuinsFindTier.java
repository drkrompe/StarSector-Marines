package com.dillon.starsectormarines.campaign.ruins;

/**
 * The two tiers of a ruins find ({@code ruins-expedition.md}, The vault and its prize; owner pick
 * {@code ruins-find-tiers} a). A find's tier is set by where it lies, never rolled.
 *
 * <ul>
 *   <li>{@link #SUPPLY}: the ordinary commodity bundles of field salvage, along the route.</li>
 *   <li>{@link #TECH}: a tech cache ({@link RuinsTechCache}), the game's own weapons, fighter wings, hullmods and
 *       blueprints from vanilla's ruins drop groups, in the places the works or a guardian keep.</li>
 * </ul>
 *
 * <p>Vanilla's haul is untouched beside both (Law 4).
 */
public enum RuinsFindTier {
    SUPPLY,
    TECH;

    /** Where a find lies on the delve. */
    public enum Place {
        /** Along the route, in the open or a side room. */
        ROUTE(SUPPLY),
        /** Behind a seal door of the works, powered or forced open. */
        SEALED_STORE(TECH),
        /** In a guardian's room, taken once its guardian falls. */
        GUARDIAN_ROOM(TECH),
        /** The vault store's tech share; the rest of the store is supply ({@link #vaultStoreTier}). */
        VAULT_STORE(TECH);

        /** The tier every find in this place holds. */
        public final RuinsFindTier tier;

        Place(RuinsFindTier tier) {
            this.tier = tier;
        }
    }

    /**
     * The tier of the vault store's find for one layer of a delve of {@code layers}: the deepest layer's find is a
     * tech cache, every other layer's is supply, so the store always holds exactly one tech cache.
     *
     * @param layer  the store's layer, from 1
     * @param layers the delve's layer count, at least 1
     */
    public static RuinsFindTier vaultStoreTier(int layer, int layers) {
        if (layers < 1 || layer < 1 || layer > layers) throw new IllegalArgumentException("no such layer");
        return layer == layers ? Place.VAULT_STORE.tier : SUPPLY;
    }
}
