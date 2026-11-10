package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsVaultStoreTest {

    private static final SalvageCache.Recovered BANKED =
            new SalvageCache.Recovered("field-cache-0", SalvageResource.METALS, 40);

    @Test
    void theStoreHoldsOneFindPerLayerAtTheVaultUnderStableIds() {
        List<SalvageCache> store = RuinsVaultStore.of(4, 70, 33);

        assertEquals(List.of("vault-store-1", "vault-store-2", "vault-store-3", "vault-store-4"),
                store.stream().map(find -> find.id).toList());
        assertTrue(store.stream().allMatch(find -> find.cellX == 70 && find.cellY == 33));
        assertEquals(List.of("vault-store-1"), RuinsVaultStore.of(1, 0, 0).stream().map(find -> find.id).toList());
    }

    @Test
    void onlyTheDeepestFindIsATechCacheAndItSettlesAsOne() {
        List<SalvageCache> store = RuinsVaultStore.of(3, 0, 0);

        assertEquals(List.of(false, false, true), store.stream().map(SalvageCache::tech).toList());
        assertEquals(List.of(false, false, true), RuinsVaultStore.settled(List.of(), store, true).stream()
                .map(SalvageCache.Recovered::tech).toList());
    }

    @Test
    void theStoreComesHomeOnlyWhenTheHaulBoards() {
        List<SalvageCache> store = RuinsVaultStore.of(2, 5, 5);

        assertEquals(List.of("field-cache-0", "vault-store-1", "vault-store-2"),
                RuinsVaultStore.settled(List.of(BANKED), store, true).stream().map(SalvageCache.Recovered::id).toList());
        assertEquals(List.of(BANKED), RuinsVaultStore.settled(List.of(BANKED), store, false));
    }
}
