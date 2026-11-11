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
        assertEquals(null, Stage.QUIET.commsKey(), "a quiet site says nothing");
        assertEquals("derelictWakeBays", Stage.ROUSED.commsKey());
    }

    @Test
    void aReleasedFlockSweepsItsSleepingZoneACornerAtATime() {
        SecurityZone zone = new SecurityZone(0, 20, 20, 10);
        int[] first = FacilityWake.sweepPoint(zone, 0);
        int[] second = FacilityWake.sweepPoint(zone, FacilityWake.SWEEP_TICKS);
        assertEquals(26, first[0]);
        assertEquals(26, first[1]);
        assertEquals(14, second[0], "on to the next corner, counter-clockwise");
        assertEquals(26, second[1]);
        assertTrue(zone.inside(first[0], first[1]), "the sweep stays inside the perimeter");
        int[] round = FacilityWake.sweepPoint(zone, 4 * FacilityWake.SWEEP_TICKS);
        assertEquals(first[0], round[0], "and round again");
    }

    @Test
    void theStageRisesAndNeverFalls() {
        FacilityWake wake = new FacilityWake();
        assertTrue(wake.advance(Stage.ROUSED));
        assertFalse(wake.advance(Stage.STIRRING));
        assertEquals(Stage.ROUSED, wake.stage());
    }

    @Test
    void thePlantRestartingBringsTheWakeToStirringAtLeast() {
        FacilityWake quiet = new FacilityWake();
        assertTrue(quiet.plantRestarted());
        assertEquals(Stage.STIRRING, quiet.stage());
        FacilityWake roused = new FacilityWake();
        roused.advance(Stage.ROUSED);
        assertFalse(roused.plantRestarted());
        assertEquals(Stage.ROUSED, roused.stage());
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
