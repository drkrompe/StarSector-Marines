package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.FacilityWake.Stage;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityWakeTest {

    private static SecurityNetwork row() {
        return new SecurityNetwork()
                .add(new SecurityZone(0, 10, 10, 8))
                .add(new SecurityZone(1, 30, 10, 8))
                .add(new SecurityZone(2, 50, 10, 8))
                .link(0, 1).link(1, 2);
    }

    @Test
    void theStageIsTheStirsLevelAndTheBreachIsTheContingency() {
        assertEquals(Stage.QUIET, FacilityWake.of(RuinsPressure.Level.QUIET, false));
        assertEquals(Stage.ROUSED, FacilityWake.of(RuinsPressure.Level.ROUSED, false));
        assertEquals(Stage.CONTINGENCY, FacilityWake.of(RuinsPressure.Level.QUIET, true));
        assertTrue(Stage.STIRRING.ventsOpen());
        assertFalse(Stage.STIRRING.baysOpen());
        assertTrue(Stage.ROUSED.baysOpen());
        assertFalse(Stage.ROUSED.bulkheadsOpen());
        assertTrue(Stage.HUNTING.bulkheadsOpen());
    }

    @Test
    void theStageRisesAndNeverFalls() {
        FacilityWake wake = new FacilityWake();
        assertTrue(wake.advance(Stage.ROUSED));
        assertFalse(wake.advance(Stage.STIRRING));
        assertEquals(Stage.ROUSED, wake.stage());
    }

    @Test
    void reserveBaysOpenAtRousedDeepestFirstOnceAndOnlyWhileStandingAndOnline() {
        FacilityWake wake = new FacilityWake();
        SecurityNetwork network = row();
        network.zone(1).powerDown();
        List<Integer> bays = List.of(2, 0, 1);
        wake.advance(Stage.STIRRING);
        assertEquals(List.of(), wake.release(network, bays, zone -> true), "not yet roused");
        wake.advance(Stage.ROUSED);
        assertEquals(List.of(0), wake.release(network, bays, zone -> zone != 2),
                "zone 1 is dark and the bay of zone 2 is destroyed");
        assertEquals(List.of(), wake.release(network, bays, zone -> zone != 2), "a bay opens once");
        network.zone(1).powerUp();
        assertEquals(List.of(1), wake.release(network, bays, zone -> zone != 2), "back online, it opens");
        assertTrue(wake.released(1));
        assertFalse(wake.released(2));
    }

    @Test
    void huntingAlertsEveryOnlineDormantZoneOnce() {
        FacilityWake wake = new FacilityWake();
        SecurityNetwork network = row();
        network.zone(2).powerDown();
        assertFalse(wake.sound(network));
        wake.advance(Stage.HUNTING);
        assertTrue(wake.sound(network));
        assertEquals(SecurityZone.State.ALERT, network.zone(0).state());
        assertEquals(SecurityZone.State.ALERT, network.zone(1).state());
        assertEquals(SecurityZone.State.DORMANT, network.zone(2).state(), "a dark zone hears nothing");
        assertFalse(wake.sound(network), "once");
    }
}
