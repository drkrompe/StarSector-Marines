package com.dillon.starsectormarines.campaign.polity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketProductionSignalsTest {

    @Test
    void oneMarketSitsWhereItsIndustryAndDeficitsPutIt() {
        assertEquals(GroundProductionQuality.NONE,
                MarketProductionSignals.of(false, false, false, false).quality());
        assertEquals(GroundProductionQuality.ADVANCED,
                MarketProductionSignals.of(true, false, false, false).quality());
        assertEquals(GroundProductionQuality.ADVANCED_FULL,
                MarketProductionSignals.of(false, true, false, false).quality());
        assertEquals(GroundProductionQuality.BASIC,
                MarketProductionSignals.of(true, false, true, false).quality());
    }

    /** Vanilla reads ship quality off the best producing market, not off an average. */
    @Test
    void thePolitySitsWhereItsBestMarketDoes() {
        GroundProductionQuality best = MarketProductionSignals.bestQuality(List.of(
                MarketProductionSignals.of(false, false, false, false),
                MarketProductionSignals.of(true, false, true, true),
                MarketProductionSignals.of(false, true, false, false)));

        assertEquals(GroundProductionQuality.ADVANCED_FULL, best);
    }

    @Test
    void aPolityWithNoColoniesMakesNothing() {
        assertEquals(GroundProductionQuality.NONE,
                MarketProductionSignals.bestQuality(List.of()));
        assertEquals(GroundProductionQuality.NONE,
                MarketProductionSignals.bestQuality(null));
        assertEquals(GroundProductionQuality.NONE,
                MarketProductionSignals.bestQuality(Arrays.asList((MarketProductionSignals) null)));
        assertEquals(GroundProductionQuality.NONE,
                MarketProductionSignals.signals().bestQuality());
    }

    @Test
    void theStatedSetIsAProductionSignalsOfItsOwn() {
        ProductionSignals signals = MarketProductionSignals.signals(
                MarketProductionSignals.of(true, false, false, false),
                MarketProductionSignals.of(false, false, false, false));

        assertEquals(GroundProductionQuality.ADVANCED, signals.bestQuality());
    }
}
