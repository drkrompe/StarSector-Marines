package com.dillon.starsectormarines.battle.world.gen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The garrison estimate's dials, asked directly on the smallest facts that move them. */
class MarketGarrisonEstimateTest {

    private static final GarrisonStructureTable TABLE = new GarrisonStructureTable(Map.of(
            "heavybatteries", new GarrisonStructureTable.Contribution(0.5f, 0.5f, 1f),
            "militarybase", new GarrisonStructureTable.Contribution(0f, 0.2f, 0.2f)));

    private static MarketGarrison estimate(int size, boolean military, List<String> industries) {
        return MarketGarrisonEstimate.estimate(
                MarketGarrisonEstimate.Inputs.unstrained(size, 300f, 0f, military, industries), TABLE);
    }

    @Test
    void noMarketIsNoGarrison() {
        assertSame(MarketGarrison.NONE, estimate(0, false, List.of("heavybatteries")));
        assertSame(MarketGarrison.NONE, MarketGarrisonEstimate.estimate(null, TABLE));
    }

    @Test
    void everyGarrisonIsMixedAndThickensWithSize() {
        MarketGarrison outpost = estimate(3, false, List.of());
        MarketGarrison colony = estimate(MarketGarrisonEstimate.PROFESSIONAL_FROM_SIZE, false, List.of());
        assertEquals(0.2f, outpost.quality(), 1e-6f, "an outpost is a fifth elite, not all militia");
        assertEquals(1f / 3f, colony.quality(), 1e-6f);
        assertEquals(0f, outpost.heavy(), 1e-6f);
    }

    @Test
    void militaryMarketShiftsBulkToElite() {
        assertEquals(0.5f / 1.25f, estimate(3, true, List.of()).quality(), 1e-6f);
    }

    @Test
    void structuresAddEliteAndHeavyWeight() {
        MarketGarrison plain = estimate(6, false, List.of());
        MarketGarrison fortified = estimate(6, false, List.of("heavybatteries", "militarybase"));
        // bulk 1 + 0.5, elite 0.5 + 0.5 + 0.2, heavy 1 + 0.2
        assertEquals(1.2f / 2.7f, fortified.quality(), 1e-6f);
        assertEquals(1.2f / 3.9f, fortified.heavy(), 1e-6f);
        assertTrue(fortified.quality() > plain.quality());
    }

    @Test
    void unknownIndustryContributesNothing() {
        assertEquals(estimate(6, false, List.of()), estimate(6, false, List.of("some_modded_industry")));
    }

    @Test
    void shortagesThinEliteAndHeavyWeightToTheirFloor() {
        MarketGarrison starved = MarketGarrisonEstimate.estimate(new MarketGarrisonEstimate.Inputs(
                6, 300f, 0f, false, List.of("heavybatteries"), 0f, 0f, 0f, 1f), TABLE);
        // elite (0.5 + 0.5) * 0.5, heavy 1 * 0.5, bulk 1.5
        assertEquals(0.5f / 2f, starved.quality(), 1e-6f);
        assertEquals(0.5f / 2.5f, starved.heavy(), 1e-6f);
    }

    @Test
    void strengthIsGroundDefenceLessStationedTimesReadiness() {
        MarketGarrison garrison = MarketGarrisonEstimate.estimate(new MarketGarrisonEstimate.Inputs(
                6, 300f, 100f, false, List.of(), 1f, 1f, 1f, 0.25f), TABLE);
        assertEquals(50f, garrison.strength(), 1e-4f);
        assertEquals(0.25f, garrison.readiness(), 1e-6f);
        assertEquals(0f, MarketGarrisonEstimate.estimate(
                MarketGarrisonEstimate.Inputs.unstrained(6, 50f, 80f, false, List.of()), TABLE).strength());
    }
}
