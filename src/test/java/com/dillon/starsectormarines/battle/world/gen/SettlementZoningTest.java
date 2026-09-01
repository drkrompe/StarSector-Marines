package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The policy that decides what kind of place a market is. Asked directly, on
 * the two numbers it reads, so it can be reasoned about without a sector.
 */
class SettlementZoningTest {

    @Test
    void aSmallMarketIsAnOutpostSuppliedByShip() {
        for (int size = 1; size <= SettlementZoning.OUTPOST_MAX_SIZE; size++) {
            assertEquals(SettlementLink.LANDING, SettlementZoning.linkFor(size, false),
                    "market size " + size + " should read as an outpost");
        }
    }

    @Test
    void anythingLargerGrewWhereYouCouldDriveToIt() {
        for (int size = SettlementZoning.OUTPOST_MAX_SIZE + 1; size <= 10; size++) {
            assertEquals(SettlementLink.ROAD, SettlementZoning.linkFor(size, false),
                    "market size " + size + " should take a road");
        }
    }

    /**
     * No market is an absence of information rather than a claim of isolation,
     * so it takes the ordinary answer and not the outpost one.
     */
    @Test
    void noMarketIsNotAnOutpost() {
        assertEquals(SettlementLink.ROAD, SettlementZoning.linkFor(0, false));
        assertEquals(SettlementLink.ROAD, SettlementZoning.linkFor(-1, false));
    }

    @Test
    void aDecivilizedWorldIsGuaranteedNothingAtAnySize() {
        assertEquals(SettlementLink.NONE, SettlementZoning.linkFor(1, true));
        assertEquals(SettlementLink.NONE, SettlementZoning.linkFor(9, true));
        assertEquals(SettlementLink.NONE, SettlementZoning.linkFor(0, true));
    }

    @Test
    void densityRisesWithMarketSize() {
        float previous = -1f;
        for (int size = 1; size <= 10; size++) {
            float d = SettlementZoning.densityFor(size);
            assertTrue(d > previous, "density fell between size " + (size - 1) + " and " + size);
            assertTrue(d >= 0f && d <= 1f, "density out of range at size " + size + ": " + d);
            previous = d;
        }
    }

    /**
     * Even a metropolis has ground its roads never reached, and full density is
     * defined as no open ground at all.
     */
    @Test
    void theLargestMarketIsStillNotSaturated() {
        assertTrue(SettlementZoning.densityFor(10) < 1f);
        assertTrue(SettlementZoning.densityFor(99) < 1f, "an out-of-range size must not saturate either");
    }

    @Test
    void noMarketHasNoDensityToDerive() {
        assertEquals(0f, SettlementZoning.densityFor(0), 0f);
    }

    /**
     * The derived default, pinned at the sizes where it changes answer and on
     * either side of each of them.
     */
    @Test
    void howSettledAMarketIsFollowsItsSize() {
        assertEquals(PrecinctPlan.Sprawl.BALANCED, SettlementZoning.sprawlFor(0),
                "no market is an absence of information, not a wilderness");
        assertEquals(PrecinctPlan.Sprawl.REMOTE, SettlementZoning.sprawlFor(3),
                "the largest outpost is still an outpost");
        assertEquals(PrecinctPlan.Sprawl.BALANCED, SettlementZoning.sprawlFor(4),
                "one size above an outpost is a town");
        assertEquals(PrecinctPlan.Sprawl.BALANCED, SettlementZoning.sprawlFor(7),
                "the largest town is still a town with country around it");
        assertEquals(PrecinctPlan.Sprawl.DENSE, SettlementZoning.sprawlFor(8),
                "eight is where a market stops being a town");
        assertEquals(PrecinctPlan.Sprawl.DENSE, SettlementZoning.sprawlFor(10),
                "the top of the vanilla range is a conurbation");
    }
}
