package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WithdrawalExtractionTest {

    @Test
    void troopsInsideThePadsCircleExtractAndTheRestDoNot() {
        LandingPad pad = new LandingPad(50, 50, 3, 2, LandingPad.Approach.NORTH);
        List<LandingPad> pads = List.of(pad);
        float cx = WithdrawalExtraction.centreX(pad);
        float cy = WithdrawalExtraction.centreY(pad);
        float r = WithdrawalExtraction.radius(pad);

        assertTrue(WithdrawalExtraction.extracts(pads, cx, cy));
        assertTrue(WithdrawalExtraction.extracts(pads, cx + r - 0.01f, cy));
        assertFalse(WithdrawalExtraction.extracts(pads, cx + r + 0.01f, cy));
        // The corner of the bounding square lies outside the circle.
        assertFalse(WithdrawalExtraction.extracts(pads, cx + r * 0.8f, cy + r * 0.8f));
        assertFalse(WithdrawalExtraction.extracts(pads, Float.NaN, cy));
    }

    @Test
    void aBattleWithNoRecordedPadsExtractsNobodyCleanly() {
        assertFalse(WithdrawalExtraction.extracts(List.of(), 0f, 0f));
    }
}
