package com.dillon.starsectormarines.campaign.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * What the vault's prize pays when the haul boards ({@code ruins-expedition.md}, The vault and its prize). It is paid
 * beside vanilla's haul and the vault store, only on a win.
 *
 * <p>Owner pick {@code ruins-prize-forced} a: an AI core or a colony item comes out whole only when power reached the
 * vault's seal before the breach; breached on the long, unpowered hold it breaks into tech caches, one more than its
 * stake. Goods and tech pay the same either way. Nothing is lost outright, so a forced vault still pays.
 *
 * @param itemId      the core commodity or colony item that comes out whole; null when none does
 * @param techCaches  the tech caches it pays, the prize's own or the pieces of a broken one
 * @param goodsBundles how many extra supply bundles it pays
 */
public record RuinsVaultPayout(String itemId, List<RuinsTechCache> techCaches, int goodsBundles) {

    /** The id prefix of the prize's tech caches, numbered from 1. */
    public static final String CACHE_PREFIX = "vault-prize-";
    /** Supply bundles a goods prize pays. UNTUNED. */
    public static final int GOODS_BUNDLES = 3;
    /** Tech caches a tech prize pays. UNTUNED. */
    public static final int TECH_CACHES = 2;

    public RuinsVaultPayout {
        techCaches = List.copyOf(techCaches);
    }

    /** Whether the payout is the broken pieces of a core or item. */
    public boolean broken(RuinsVaultPrize prize) {
        return prize.breaksWhenForced() && itemId == null;
    }

    /**
     * @param prize          the site's prize ({@link RuinsVaultPrizes#draw})
     * @param poweredAtBreach whether power reached the vault's seal before the breach was held
     */
    public static RuinsVaultPayout of(long sectorSeed, String marketId, RuinsVaultPrize prize, boolean poweredAtBreach) {
        if (prize == null || marketId == null) throw new IllegalArgumentException("a payout needs a prize and a site");
        return switch (prize.family()) {
            case GOODS -> new RuinsVaultPayout(null, List.of(), GOODS_BUNDLES);
            case TECH -> new RuinsVaultPayout(null, caches(sectorSeed, marketId, TECH_CACHES), 0);
            case AI_CORE, COLONY_ITEM -> poweredAtBreach
                    ? new RuinsVaultPayout(prize.itemId(), List.of(), 0)
                    : new RuinsVaultPayout(null, caches(sectorSeed, marketId, prize.stakes() + 1), 0);
        };
    }

    private static List<RuinsTechCache> caches(long sectorSeed, String marketId, int count) {
        List<RuinsTechCache> caches = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) caches.add(RuinsTechCache.of(sectorSeed, marketId, CACHE_PREFIX + i));
        return caches;
    }
}
