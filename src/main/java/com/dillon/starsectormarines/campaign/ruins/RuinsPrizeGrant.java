package com.dillon.starsectormarines.campaign.ruins;

import com.dillon.starsectormarines.battle.salvage.SalvageResource;

import java.util.ArrayList;
import java.util.List;

/**
 * One thing the vault's prize puts in the fleet's cargo when a won expedition settles ({@code ruins-expedition.md},
 * The vault and its prize). {@link #of} turns a {@link RuinsVaultPayout} into grants; {@code RuinsPrizeSettlement}
 * applies them through the game's cargo.
 *
 * @param kind     what kind of thing it is
 * @param id       the commodity or special item id, or the vanilla drop group a tech cache rolls
 * @param quantity how many, for a commodity; 1 otherwise
 * @param seed     the roll's seed, for a tech cache: the site and the find alone, so a replay rolls the same
 */
public record RuinsPrizeGrant(Kind kind, String id, int quantity, long seed) {

    public enum Kind {
        /** A commodity: an AI core, or a supply bundle of a goods prize. */
        COMMODITY,
        /** A vanilla special item: a colony item. */
        SPECIAL_ITEM,
        /** One tech cache: a roll of a vanilla drop group. */
        TECH_ROLL
    }

    public RuinsPrizeGrant {
        if (kind == null || id == null || quantity <= 0) throw new IllegalArgumentException("an empty grant");
    }

    /**
     * The grants a payout makes: the whole core or item, each tech cache's roll, and a goods prize's bundles, which
     * take turns over the field salvage resources at their bundle size, as the vault store does.
     */
    public static List<RuinsPrizeGrant> of(RuinsVaultPayout payout, RuinsVaultPrize prize, long sectorSeed,
                                           String marketId) {
        if (payout == null || prize == null || marketId == null) throw new IllegalArgumentException("a grant needs a payout");
        List<RuinsPrizeGrant> grants = new ArrayList<>();
        if (payout.itemId() != null) {
            grants.add(new RuinsPrizeGrant(prize.family() == RuinsVaultPrize.Family.COLONY_ITEM
                    ? Kind.SPECIAL_ITEM : Kind.COMMODITY, payout.itemId(), 1, 0L));
        }
        grants.addAll(techRolls(payout.techCaches(), sectorSeed, marketId));
        SalvageResource[] resources = SalvageResource.values();
        for (int i = 0; i < payout.goodsBundles(); i++) {
            SalvageResource resource = resources[i % resources.length];
            grants.add(new RuinsPrizeGrant(Kind.COMMODITY, resource.commodityId, resource.cacheQuantity, 0L));
        }
        return List.copyOf(grants);
    }

    /**
     * The rolls of the tech caches found along the delve ({@link RuinsFindTier.Place}): sealed stores, a guardian's
     * room, the vault store's deepest layer. Each find's group and seed come from the site and its id alone.
     */
    public static List<RuinsPrizeGrant> ofTechFinds(List<String> findIds, long sectorSeed, String marketId) {
        if (marketId == null) throw new IllegalArgumentException("a tech find needs a site");
        List<RuinsTechCache> caches = new ArrayList<>();
        for (String id : findIds) caches.add(RuinsTechCache.of(sectorSeed, marketId, id));
        return techRolls(caches, sectorSeed, marketId);
    }

    private static List<RuinsPrizeGrant> techRolls(List<RuinsTechCache> caches, long sectorSeed, String marketId) {
        long site = RuinsOccupants.mix(sectorSeed, marketId.hashCode());
        List<RuinsPrizeGrant> rolls = new ArrayList<>();
        for (RuinsTechCache cache : caches) {
            rolls.add(new RuinsPrizeGrant(Kind.TECH_ROLL, cache.dropGroup(), 1,
                    RuinsOccupants.mix(site, cache.findId().hashCode())));
        }
        return rolls;
    }
}
