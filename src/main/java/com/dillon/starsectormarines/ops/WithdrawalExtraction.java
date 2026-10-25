package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.LandingPad;

import java.util.List;

/**
 * Who comes off the field unhurt when the company withdraws.
 *
 * <p>A marine standing on or beside a pad the company landed on boards and
 * leaves. Everyone else still on the ground fights their way back and comes
 * home wounded. A battle with no recorded pads extracts nobody cleanly.
 */
final class WithdrawalExtraction {

    /** Cells beyond a pad's footprint that still count as at the landing zone. */
    static final int EXTRACTION_MARGIN_CELLS = 10;

    private WithdrawalExtraction() {
    }

    static boolean extracts(List<LandingPad> pads, float x, float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) return false;
        for (LandingPad pad : pads) {
            if (Math.abs(x - pad.centerX) <= pad.halfWidth + EXTRACTION_MARGIN_CELLS
                    && Math.abs(y - pad.centerY) <= pad.halfHeight + EXTRACTION_MARGIN_CELLS) {
                return true;
            }
        }
        return false;
    }
}
