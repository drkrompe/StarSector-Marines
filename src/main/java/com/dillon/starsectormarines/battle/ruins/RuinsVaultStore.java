package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;

import java.util.ArrayList;
import java.util.List;

/**
 * The vault store ({@code ruins-expedition.md}, owner pick {@code ruins-vault-worth} a): the deepest find, a bundle per
 * layer of the delve, lying at the vault. Nobody recovers it by standing at it. It comes out with the haul, so it is
 * settled beside the banked finds only when the haul boards, and is left behind on a pull-out or a loss. Its ids are
 * stable per layer, so a site remembers it like any find.
 */
public final class RuinsVaultStore {

    /** The id prefix of the store's finds, numbered from 1. */
    public static final String ID_PREFIX = "vault-store-";

    private RuinsVaultStore() {}

    /** One find per layer of a delve with {@code layers} layers, at the vault's cell; resources take turns. */
    public static List<SalvageCache> of(int layers, int vaultX, int vaultY) {
        List<SalvageCache> store = new ArrayList<>();
        SalvageResource[] resources = SalvageResource.values();
        for (int i = 0; i < layers; i++) {
            SalvageResource resource = resources[i % resources.length];
            store.add(new SalvageCache(ID_PREFIX + (i + 1), vaultX, vaultY, resource, resource.cacheQuantity));
        }
        return List.copyOf(store);
    }

    /** The finds a battle settles: the banked finds and, when the haul boarded, the store after them. */
    public static List<SalvageCache.Recovered> settled(List<SalvageCache.Recovered> banked, List<SalvageCache> store,
                                                       boolean hauled) {
        if (!hauled || store.isEmpty()) return List.copyOf(banked);
        List<SalvageCache.Recovered> settled = new ArrayList<>(banked);
        for (SalvageCache find : store) settled.add(find.recovered());
        return List.copyOf(settled);
    }
}
