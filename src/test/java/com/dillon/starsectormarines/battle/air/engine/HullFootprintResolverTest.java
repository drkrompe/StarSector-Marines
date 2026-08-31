package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirScale;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How big a hull is when nothing has loaded a game.
 *
 * <p>The in-game answer cannot be asked here — there is no {@code SettingsAPI}
 * to load a spec through, which is the entire reason the seam exists — so what
 * is pinned is the half that runs outside one: that a source is consulted at
 * all, that two hulls of different sizes come back different sizes, and that a
 * hull nothing could size admits it rather than passing off the fallback as a
 * measurement.
 */
class HullFootprintResolverTest {

    /**
     * Puts the real install back. The resolver's source is process-wide, so a
     * test that swaps it and walks away leaves every later test measuring a
     * stub.
     */
    @AfterEach
    void restoreTheInstall() {
        InstalledHullSpecs.install();
    }

    @Test
    void aHullIsSizedByWhateverCanReadItsSpec() {
        HullFootprintResolver.useHullDimensions(hullId ->
                switch (hullId) {
                    case "test_small" -> 66f;
                    case "test_large" -> 264f;
                    default -> 0f;
                });

        assertEquals(AirScale.cellsForHeightPx(66f),
                HullFootprintResolver.visualLengthCells("test_small"), 0.0001f);
        assertEquals(AirScale.cellsForHeightPx(264f),
                HullFootprintResolver.visualLengthCells("test_large"), 0.0001f);
        assertTrue(HullFootprintResolver.isMeasured("test_small"));
        assertTrue(HullFootprintResolver.isMeasured("test_large"));
    }

    @Test
    void aHullNothingCanSizeSaysSoRatherThanPassingOffTheFallback() {
        HullFootprintResolver.useHullDimensions(hullId -> 0f);

        assertEquals(AirScale.FALLBACK_LENGTH_CELLS,
                HullFootprintResolver.visualLengthCells("test_unreadable"), 0.0001f);
        assertFalse(HullFootprintResolver.isMeasured("test_unreadable"),
                "a stand-in that reports itself as a measurement is worse than no answer");
    }
}
