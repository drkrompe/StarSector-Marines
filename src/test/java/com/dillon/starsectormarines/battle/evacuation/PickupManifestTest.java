package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PickupManifestTest {
    private static PickupManifest.Passenger passenger(long id) {
        return new PickupManifest.Passenger(id, Faction.MARINE, "soldier-" + id, null);
    }
    @Test void capacityAndIdentitySurviveTheLiftUntilReturn() {
        var manifest = new PickupManifest(2);
        assertFalse(manifest.hasPassengersAboard(), "An empty return flight must not delay survivors");
        assertTrue(manifest.board(passenger(1)));
        assertTrue(manifest.hasPassengersAboard(), "Boarding is not recovery");
        assertFalse(manifest.board(passenger(1)));
        assertTrue(manifest.board(passenger(2)));
        assertFalse(manifest.board(passenger(3)));
        assertEquals(PickupManifest.State.ABOARD, manifest.state());
        manifest.recover();
        manifest.lose();
        assertEquals(PickupManifest.State.RECOVERED, manifest.state());
        assertFalse(manifest.hasPassengersAboard());
        assertEquals("soldier-2", manifest.passengers().get(1).soldierId());
        assertFalse(manifest.board(passenger(3)));
    }
    @Test void destroyedLoadedAircraftCannotRecoverPassengersOrCountThemTwice() {
        var manifest = new PickupManifest(6, true);
        manifest.board(passenger(9));
        manifest.lose();
        manifest.recover();
        assertEquals(PickupManifest.State.LOST, manifest.state());
        assertFalse(manifest.hasPassengersAboard(), "Lost passengers are not waiting for extraction");
        assertTrue(manifest.lossInOwnedBoatLedger());
        assertEquals(1, manifest.passengers().size());
        assertFalse(manifest.board(passenger(10)));
    }
}
