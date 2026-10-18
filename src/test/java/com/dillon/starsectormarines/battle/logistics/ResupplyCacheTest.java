package com.dillon.starsectormarines.battle.logistics;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cache dropped on a cell and a cache riding a truck: one stock, one rule set, two ways of being placed. */
class ResupplyCacheTest {

    @Test
    void aGroundCacheServesFromItsCellCentreAndNeverMoves() {
        ResupplyCache cache = new ResupplyCache(10, 4, Faction.MARINE);
        assertFalse(cache.carried());
        assertEquals(10.5f, cache.centerX());
        assertEquals(4.5f, cache.centerY());
        assertTrue(cache.serving());
        assertThrows(IllegalStateException.class, () -> cache.follow(1f, 1f, true));
    }

    @Test
    void aCarriedCacheIsClosedUntilItsCarrierReportsAHalt() {
        ResupplyCache cache = ResupplyCache.carried(42L, 3f, 7f, Faction.MARINE, 24);
        assertTrue(cache.carried());
        assertEquals(42L, cache.carrierId);
        assertEquals(24, cache.stock);
        assertFalse(cache.serving(), "it has not been seen halted yet");

        cache.follow(9.25f, 2.5f, true);
        assertEquals(9.25f, cache.centerX());
        assertEquals(2.5f, cache.centerY());
        assertTrue(cache.serving());

        cache.follow(11f, 2.5f, false);
        assertFalse(cache.serving(), "a moving truck hands nothing out");
        assertEquals(24, cache.stock, "closing it costs nothing");
    }

    @Test
    void losingTheCarrierLosesTheStock() {
        ResupplyCache cache = ResupplyCache.carried(7L, 0f, 0f, Faction.MARINE, 24);
        cache.follow(0f, 0f, true);
        cache.lose();
        assertTrue(cache.depleted());
        assertFalse(cache.serving());
        assertEquals(0f, cache.stockFraction());
    }

    @Test
    void theServiceFindsTheCacheEachTruckCarries() {
        ResupplyService service = new ResupplyService();
        ResupplyCache ground = new ResupplyCache(1, 1, Faction.MARINE);
        ResupplyCache truck = ResupplyCache.carried(5L, 2f, 2f, Faction.MARINE, 24);
        service.add(ground);
        service.add(truck);
        assertSame(truck, service.carriedBy(5L));
        assertNull(service.carriedBy(6L));
        assertEquals(2, service.caches().size());
    }
}
