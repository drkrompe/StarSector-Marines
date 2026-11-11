package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dillon.starsectormarines.battle.derelict.SecurityZone.State.ALERT;
import static com.dillon.starsectormarines.battle.derelict.SecurityZone.State.AWAKE;
import static com.dillon.starsectormarines.battle.derelict.SecurityZone.State.DORMANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityNetworkTest {

    /** Three zones in a row, 0-1-2, 1 linked to both. */
    private static SecurityNetwork row() {
        return new SecurityNetwork()
                .add(new SecurityZone(0, 10, 10, 8))
                .add(new SecurityZone(1, 30, 10, 8))
                .add(new SecurityZone(2, 50, 10, 8))
                .link(0, 1).link(1, 2);
    }

    @Test
    void aStirWakesTheZoneAndAlertsOnlyItsLinkedNeighbours() {
        SecurityNetwork network = row();
        network.stir(0, 100);
        assertEquals(AWAKE, network.zone(0).state());
        assertEquals(ALERT, network.zone(1).state());
        assertEquals(DORMANT, network.zone(2).state(), "a relay goes one hop");
    }

    @Test
    void aLoudWorkOutsideEveryPerimeterStirsTheNearestZone() {
        SecurityNetwork network = row();
        network.loud(70, 10, 5);
        assertEquals(AWAKE, network.zone(2).state());
        assertEquals(DORMANT, network.zone(0).state());
    }

    @Test
    void gunfireWakesOnlyTheZonesWhosePerimeterHoldsIt() {
        SecurityNetwork network = row();
        network.gunfire(40, 10, 5);
        assertEquals(DORMANT, network.zone(0).state());
        network.gunfire(52, 12, 5);
        assertEquals(AWAKE, network.zone(2).state());
    }

    @Test
    void aQuietAwakeZoneSettlesToAlertNeverToDormant() {
        SecurityZone zone = new SecurityZone(0, 0, 0, 5);
        zone.stir(0);
        zone.settle(SecurityZone.SETTLE_TICKS - 1);
        assertEquals(AWAKE, zone.state());
        zone.settle(SecurityZone.SETTLE_TICKS);
        assertEquals(ALERT, zone.state());
        zone.settle(SecurityZone.SETTLE_TICKS * 10);
        assertEquals(ALERT, zone.state());
    }

    @Test
    void aDeadNodeNeitherRelaysNorHearsARelay() {
        SecurityNetwork network = row();
        network.zone(1).loseNode();
        network.stir(1, 0);
        assertEquals(AWAKE, network.zone(1).state(), "it still wakes on its own senses");
        assertEquals(DORMANT, network.zone(0).state());
        network.stir(2, 0);
        assertEquals(DORMANT, network.zone(0).state());
    }

    @Test
    void theContingencyWakesEveryLiveZoneAndKeepsItAwake() {
        SecurityNetwork network = row();
        network.zone(2).loseNode();
        network.breachVault(0);
        assertTrue(network.contingency());
        assertEquals(AWAKE, network.zone(0).state());
        assertEquals(AWAKE, network.zone(1).state());
        assertEquals(DORMANT, network.zone(2).state(), "a dead node is out of the call");
        network.settle(SecurityZone.SETTLE_TICKS * 3);
        assertEquals(AWAKE, network.zone(0).state());
    }

    @Test
    void zoneAtTakesTheNearestNodeWherePerimetersOverlap() {
        SecurityNetwork network = new SecurityNetwork()
                .add(new SecurityZone(0, 0, 0, 10))
                .add(new SecurityZone(1, 12, 0, 10));
        assertEquals(1, network.zoneAt(8, 0).id);
        assertEquals(0, network.zoneAt(5, 0).id);
        assertFalse(network.zone(0).inside(11, 0));
        assertNull(network.zoneAt(40, 40));
    }

    @Test
    void aZoneKeptRoundSectionsHoldsTheGroundOffEachAndNoneBeyond() {
        SecurityZone hull = new SecurityZone(0, 35, 2, 6, List.of(new FallenHullSite.Rect(0, 0, 9, 4),
                new FallenHullSite.Rect(30, 0, 39, 4)));
        SecurityNetwork network = new SecurityNetwork().add(hull).add(new SecurityZone(1, 20, 8, 6));
        assertTrue(hull.inside(2, 8), "off the far section from its node");
        assertFalse(hull.inside(35, 12), "the perimeter runs off the plates, not round the node");
        assertEquals(0, network.zoneAt(12, 2).id, "a cell near a section is the hull's");
        assertEquals(1, network.zoneAt(15, 6).id, "where both reach, the nearer keeps it");
    }
}
