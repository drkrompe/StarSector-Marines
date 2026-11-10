package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FallenHullLookTest {

    @Test
    void aStructureWearsTheLookOfWhatItIsInTheClaim() {
        int stake = FallenHull.STAKE_ZONE_BASE + 3;
        Map<Integer, Long> pylons = Map.of(4, 21L);
        Map<Integer, Long> nodes = Map.of(FallenHull.HULL_ZONE, 10L, stake, 31L);
        Map<Integer, Long> bays = Map.of(FallenHull.HULL_ZONE, 11L, stake, 32L);
        assertEquals(FallenHullLook.FORGE_LIT, FallenHullLook.pick(10L, 10L, true, pylons, nodes, bays));
        assertEquals(FallenHullLook.FORGE_OUT, FallenHullLook.pick(10L, 10L, false, pylons, nodes, bays));
        assertEquals(FallenHullLook.STAKE_PLANTED, FallenHullLook.pick(21L, 10L, true, pylons, nodes, bays));
        assertEquals(FallenHullLook.STAKE_RAISED, FallenHullLook.pick(31L, 10L, true, pylons, nodes, bays));
        assertEquals(FallenHullLook.STAKE_STORE, FallenHullLook.pick(32L, 10L, true, pylons, nodes, bays));
        // The hull's own service bay is the network's, drawn as the network draws it.
        assertNull(FallenHullLook.pick(11L, 10L, true, pylons, nodes, bays));
    }
}
