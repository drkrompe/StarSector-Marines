package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.FlockTask;
import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;
import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Report;
import com.dillon.starsectormarines.battle.derelict.FlockTasking.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FlockTaskingTest {

    private static Report flock(float strength, boolean docked) {
        return new Report(1, 0, 20, 20, strength, docked, List.of(), -1L);
    }

    private static SecurityZone zone(SecurityZone.State state) {
        SecurityZone zone = new SecurityZone(0, 20, 20, 10);
        if (state == SecurityZone.State.ALERT) zone.hearRelay();
        if (state == SecurityZone.State.AWAKE) zone.stir(0);
        return zone;
    }

    private static Order decide(Report flock, SecurityZone zone, boolean contingency, List<Contact> contacts,
                                List<Contact> haul) {
        return FlockTasking.decide(flock, zone, contingency, contacts, haul, 5, 5);
    }

    @Test
    void aDormantZonesFlockStaysDockedAndAnAlertOneScreensItsNode() {
        assertNull(decide(flock(1f, true), zone(SecurityZone.State.DORMANT), false, List.of(), List.of()));
        assertEquals(FlockTask.RETURN,
                decide(flock(1f, false), zone(SecurityZone.State.DORMANT), false, List.of(), List.of()).task());
        Order alert = decide(flock(1f, true), zone(SecurityZone.State.ALERT), false, List.of(), List.of());
        assertEquals(FlockTask.SCREEN, alert.task());
        assertEquals(20, alert.anchorX());
    }

    @Test
    void anAwakeFlockStrikesInsidePursuesToTheLeashAndScreensBeyondIt() {
        SecurityZone zone = zone(SecurityZone.State.AWAKE);
        Order strike = decide(flock(1f, false), zone, false, List.of(new Contact(9, 25, 20, 0)), List.of());
        assertEquals(FlockTask.STRIKE, strike.task());
        assertEquals(9L, strike.targetUnitId());
        Order pursue = decide(flock(1f, false), zone, false,
                List.of(new Contact(9, 20 + 10 + FlockTasking.LEASH_CELLS, 20, 0)), List.of());
        assertEquals(FlockTask.PURSUE, pursue.task());
        Order screen = decide(flock(1f, false), zone, false,
                List.of(new Contact(9, 20 + 11 + FlockTasking.LEASH_CELLS, 20, 0)), List.of());
        assertEquals(FlockTask.SCREEN, screen.task());
        assertEquals(30, screen.anchorX(), "on the perimeter's edge toward the contact");
        assertEquals(20, screen.anchorY());
    }

    @Test
    void anAwakeFlockWithNothingSeenProbesWhereItsZoneWasStirred() {
        Order probe = decide(flock(1f, false), zone(SecurityZone.State.AWAKE), false, List.of(), List.of());
        assertEquals(FlockTask.PROBE, probe.task());
        assertEquals(5, probe.anchorX());
    }

    @Test
    void aWeakFlockGoesHomeEvenInTheContingency() {
        Order order = decide(flock(0.3f, false), zone(SecurityZone.State.AWAKE), true, List.of(),
                List.of(new Contact(3, 80, 80, 0)));
        assertEquals(FlockTask.RETURN, order.task());
    }

    @Test
    void theContingencySendsTheFlockAtTheHaulOffTheLeash() {
        Order order = decide(flock(1f, true), zone(SecurityZone.State.DORMANT), true,
                List.of(new Contact(1, 22, 20, 0)), List.of(new Contact(3, 80, 80, 0), new Contact(4, 90, 90, 0)));
        assertEquals(FlockTask.STRIKE, order.task());
        assertEquals(3L, order.targetUnitId());
    }
}
