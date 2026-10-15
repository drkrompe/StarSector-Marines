package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The polity's doctrine points move its own garrison's dials, and only the dials numbers does not already move. */
class PolityGarrisonDoctrineTest {

    private static final MarketGarrison COLONY = new MarketGarrison(200f, 0.3f, 0.1f, 1f);

    @Test
    void qualityAndHeavySupportRaiseTheirDials() {
        MarketGarrison moved = PolityGarrisonDoctrine.apply(COLONY, PolityDoctrine.of(2, 0, 1));
        assertEquals(0.5f, moved.quality(), 1e-6f);
        assertEquals(0.2f, moved.heavy(), 1e-6f);
        assertEquals(COLONY.strength(), moved.strength(), "numbers is the allied multiplier's, not applied twice");
        assertEquals(COLONY.readiness(), moved.readiness());
    }

    @Test
    void nothingSpentOrNoGarrisonPassesThrough() {
        assertSame(COLONY, PolityGarrisonDoctrine.apply(COLONY, PolityDoctrine.NONE));
        assertSame(COLONY, PolityGarrisonDoctrine.apply(COLONY, PolityDoctrine.of(0, 2, 0)));
        assertSame(MarketGarrison.NONE, PolityGarrisonDoctrine.apply(MarketGarrison.NONE, PolityDoctrine.of(2, 0, 1)));
    }
}
