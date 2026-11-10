package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.ServiceDucts.Transit;
import com.dillon.starsectormarines.battle.derelict.ServiceDucts.Vent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDuctsTest {

    /** Zones 0-1-2 in a row, 0 linked to 1 and 1 to 2; a vent by each node and one in zone 1's court. */
    private static SecurityNetwork row() {
        return new SecurityNetwork()
                .add(new SecurityZone(0, 10, 10, 8))
                .add(new SecurityZone(1, 30, 10, 8))
                .add(new SecurityZone(2, 50, 10, 8))
                .link(0, 1).link(1, 2);
    }

    private static final Vent NODE_0 = new Vent(0, 10, 12, 0);
    private static final Vent NODE_1 = new Vent(1, 30, 12, 1);
    private static final Vent COURT_1 = new Vent(2, 34, 4, 1);
    private static final Vent NODE_2 = new Vent(3, 50, 12, 2);

    private static ServiceDucts ducts() {
        return new ServiceDucts(List.of(NODE_0, NODE_1, COURT_1, NODE_2));
    }

    @Test
    void theDuctsAreShutUntilTheFacilityBringsThemUp() {
        ServiceDucts ducts = ducts();
        SecurityNetwork network = row();
        assertNull(ducts.plan(network, 0, 10f, 12f, 34, 4, 200f, 5f));
        ducts.open();
        assertTrue(ducts.usable(NODE_0, network));
        assertNotNull(ducts.plan(network, 0, 10f, 12f, 34, 4, 200f, 5f));
    }

    @Test
    void aFlockGoesInAtItsOwnVentAndUpAtTheVentNearestWhereItIsSentInALinkedZone() {
        ServiceDucts ducts = ducts();
        ducts.open();
        Transit transit = ducts.plan(row(), 0, 10f, 12f, 34, 5, 200f, 5f);
        assertEquals(NODE_0, transit.entry());
        assertEquals(COURT_1, transit.exit());
        assertEquals(ServiceDucts.transitSeconds(NODE_0, COURT_1), transit.seconds(), 1e-4f);
    }

    @Test
    void itTakesADuctOnlyWhenTheWayIsShorterThanTheOpenWayAndNeverIntoAnUnlinkedZone() {
        ServiceDucts ducts = ducts();
        ducts.open();
        assertNull(ducts.plan(row(), 0, 10f, 12f, 34, 5, 20f, 5f), "the open way is short: it flies");
        assertNotNull(ducts.plan(row(), 0, 10f, 12f, 34, 5, -1f, 5f), "no open way at all: any duct will do");
        Transit far = ducts.plan(row(), 0, 10f, 12f, 50, 12, -1f, 5f);
        assertNotEquals(2, far.exit().zoneId(), "zone 2 is not linked to zone 0");
    }

    @Test
    void aChargeCollapsesAVentForGoodAndAnOfflineZonesVentsAreShut() {
        ServiceDucts ducts = ducts();
        ducts.open();
        SecurityNetwork network = row();
        assertEquals(List.of(COURT_1), ducts.charge(34.5f, 5f));
        assertTrue(ducts.collapsed(COURT_1.id()));
        assertEquals(NODE_1, ducts.plan(network, 0, 10f, 12f, 34, 5, -1f, 5f).exit(), "the court vent is gone");
        network.zone(1).powerDown();
        assertNull(ducts.plan(network, 0, 10f, 12f, 34, 5, -1f, 5f), "zone 1 is dark: its vents are shut");
    }

    @Test
    void aTransitTakesItsDuctsLengthAtLeastTheShortestTransit() {
        assertEquals(ServiceDucts.MIN_TRANSIT_SECONDS, ServiceDucts.transitSeconds(NODE_1, COURT_1), 1e-4f);
        assertEquals(40f / ServiceDucts.DUCT_CELLS_PER_SECOND, ServiceDucts.transitSeconds(NODE_0, NODE_2), 1e-3f);
    }
}
