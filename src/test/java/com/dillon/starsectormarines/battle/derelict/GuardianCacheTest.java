package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianCacheTest {

    @Test
    void theGuardiansCacheIsATechCacheHeldFromItsCourt() {
        SalvageCache cache = DerelictSecurity.guardianCache(new RuinsDelve.Area(10, 10, 20, 18), new int[]{15, 14});

        assertTrue(cache.tech());
        assertEquals(DerelictSecurity.GUARDIAN_CACHE_ID, cache.id);
        assertTrue(cache.holds(10.5f, 18.5f), "anywhere in the court");
        assertFalse(cache.holds(25.5f, 14.5f), "not beyond its margin");
    }
}
