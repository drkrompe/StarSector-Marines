package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WithdrawalExtractionTest {

    private static final int MARGIN = WithdrawalExtraction.EXTRACTION_MARGIN_CELLS;

    @Test
    void troopsOnOrBesideAPadExtractAndTheRestDoNot() {
        List<LandingPad> pads = List.of(new LandingPad(50, 50, 3, 2, LandingPad.Approach.NORTH));

        assertTrue(WithdrawalExtraction.extracts(pads, 50f, 50f));
        assertTrue(WithdrawalExtraction.extracts(pads, 50f + 3 + MARGIN, 50f - 2 - MARGIN));
        assertFalse(WithdrawalExtraction.extracts(pads, 50f + 3 + MARGIN + 0.5f, 50f));
        assertFalse(WithdrawalExtraction.extracts(pads, Float.NaN, 50f));
    }

    @Test
    void aBattleWithNoRecordedPadsExtractsNobodyCleanly() {
        assertFalse(WithdrawalExtraction.extracts(List.of(), 0f, 0f));
    }
}
