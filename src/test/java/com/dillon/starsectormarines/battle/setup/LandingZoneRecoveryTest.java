package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.setup.LandingZoneRecovery.Body;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LandingZoneRecoveryTest {
    private static LandingZoneRecovery zone() { return new LandingZoneRecovery(0f, 0f, 10f, 30); }

    @Test void theCompanyIsBackWhenEveryoneStandsInsideTheCircle() {
        var zone = zone();
        assertFalse(zone.observe(0, List.of(new Body(1, 2f, 0f), new Body(2, 20f, 0f)), 0));
        assertEquals(1, zone.coming());
        assertTrue(zone.observe(1, List.of(new Body(1, 2f, 0f), new Body(2, 9f, 0f)), 0));
        assertEquals(2, zone.inside());
    }

    @Test void aBodyStillGettingCloserKeepsTheCompanyWaiting() {
        var zone = zone();
        for (int tick = 0; tick <= 60; tick += 10) {
            assertFalse(zone.observe(tick, List.of(new Body(1, 0f, 0f), new Body(2, 80f - tick, 0f)), 0));
        }
    }

    @Test void aBodyThatStopsGettingCloserStopsHoldingTheCompany() {
        var zone = zone();
        var stuck = List.of(new Body(1, 0f, 0f), new Body(2, 40f, 0f));
        assertFalse(zone.observe(0, stuck, 0));
        assertFalse(zone.observe(29, List.of(new Body(1, 0f, 0f), new Body(2, 40.5f, 0f)), 0),
                "drifting under a cell is not getting closer, but the stall time has not run");
        assertTrue(zone.observe(30, stuck, 0));
        assertEquals(1, zone.stalled());
        assertFalse(zone.observe(31, List.of(new Body(1, 0f, 0f), new Body(2, 38f, 0f)), 0),
                "moving closer again puts it back on its way");
    }

    @Test void atLeastOneSurvivorMustBeBackAndThoseAboardCount() {
        var zone = zone();
        assertFalse(zone.observe(0, List.of(), 0));
        assertTrue(zone.observe(1, List.of(), 3));
        var far = List.of(new Body(1, 50f, 0f));
        zone.observe(2, far, 0);
        assertFalse(zone.observe(40, far, 0), "a lone stalled body is not a company back home");
    }
}
