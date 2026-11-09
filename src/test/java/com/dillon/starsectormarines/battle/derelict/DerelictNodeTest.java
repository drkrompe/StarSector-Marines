package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictNodeTest {

    @Test
    void aZoneWhoseNodeFallsIsIsolatedOnceAndForGood() {
        SecurityNetwork network = new SecurityNetwork()
                .add(new SecurityZone(0, 10, 10, 8))
                .add(new SecurityZone(1, 30, 10, 8))
                .link(0, 1);
        Map<Integer, Long> nodes = Map.of(0, 100L, 1, 101L);

        assertEquals(List.of(0), DerelictNode.watch(network, nodes, id -> id != 100L));
        assertFalse(network.zone(0).nodeAlive());
        assertTrue(network.zone(1).nodeAlive());
        assertEquals(List.of(), DerelictNode.watch(network, nodes, id -> id != 100L));

        // Isolated: its alarm no longer reaches its neighbour, and the contingency's call passes it by.
        network.stir(0, 1);
        assertEquals(SecurityZone.State.DORMANT, network.zone(1).state());
        network.breachVault(2);
        assertEquals(SecurityZone.State.AWAKE, network.zone(1).state());
    }
}
